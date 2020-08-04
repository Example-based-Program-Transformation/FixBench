/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.remoting;

import hudson.remoting.Channel.Mode;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import javax.crypto.spec.IvParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLClassLoader;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Entry point for running a {@link Channel}. This is the main method of the slave JVM.
 *
 * <p>
 * This class also defines several methods for
 * starting a channel on a fresh JVM.
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {
    public Mode mode = Mode.BINARY;

    // no-op, but left for backward compatibility
    @Option(name="-ping")
    public boolean ping = true;

    @Option(name="-slaveLog", usage="create local slave error log")
    public File slaveLog = null;

    @Option(name="-text",usage="encode communication with the master with base64. " +
            "Useful for running slave over 8-bit unsafe protocol like telnet")
    public void setTextMode(boolean b) {
        mode = b?Mode.TEXT:Mode.BINARY;
        System.out.println("Running in "+mode.name().toLowerCase(Locale.ENGLISH)+" mode");
    }

    @Option(name="-jnlpUrl",usage="instead of talking to the master via stdin/stdout, " +
            "emulate a JNLP client by making a TCP connection to the master. " +
            "Connection parameters are obtained by parsing the JNLP file.")
    public URL slaveJnlpURL = null;

    @Option(name="-jnlpCredentials",metaVar="USER:PASSWORD",usage="HTTP BASIC AUTH header to pass in for making HTTP requests.")
    public String slaveJnlpCredentials = null;

    @Option(name="-secret", metaVar="HEX_SECRET", usage="Slave connection secret to use instead of -jnlpCredentials.")
    public String secret;

    @Option(name="-cp",aliases="-classpath",metaVar="PATH",
            usage="add the given classpath elements to the system classloader.")
    public void addClasspath(String pathList) throws Exception {
        Method $addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        $addURL.setAccessible(true);

        for(String token : pathList.split(File.pathSeparator))
            $addURL.invoke(ClassLoader.getSystemClassLoader(),new File(token).toURI().toURL());

        // fix up the system.class.path to pretend that those jar files
        // are given through CLASSPATH or something.
        // some tools like JAX-WS RI and Hadoop relies on this.
        System.setProperty("java.class.path",System.getProperty("java.class.path")+File.pathSeparatorChar+pathList);
    }

    @Option(name="-tcp",usage="instead of talking to the master via stdin/stdout, " +
            "listens to a random local port, write that port number to the given file, " +
            "then wait for the master to connect to that port.")
    public File tcpPortFile=null;


    @Option(name="-auth",metaVar="user:pass",usage="If your Jenkins is security-enabled, specify a valid user name and password.")
    public String auth = null;

    /**
     * @since 2.24
     */
    @Option(name="-jar-cache",metaVar="DIR",usage="Cache directory that stores jar files sent from the master")
    public File jarCache = new File(System.getProperty("user.home"),".jenkins/cache/jars");

    public InetSocketAddress connectionTarget = null;

    @Option(name="-connectTo",usage="make a TCP connection to the given host and port, then start communication.",metaVar="HOST:PORT")
    public void setConnectTo(String target) {
        String[] tokens = target.split(":");
        if(tokens.length!=2) {
            System.err.println("Illegal parameter: "+target);
            System.exit(1);
        }
        connectionTarget = new InetSocketAddress(tokens[0],Integer.valueOf(tokens[1]));
    }

    /**
     * Bypass HTTPS security check by using free-for-all trust manager.
     *
     * @param _
     *      This is ignored.
     */
    @Option(name="-noCertificateCheck")
    public void setNoCertificateCheck(boolean _) throws NoSuchAlgorithmException, KeyManagementException {
        System.out.println("Skipping HTTPS certificate checks altogether. Note that this is not secure at all.");
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new NoCheckTrustManager()}, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        // bypass host name check, too.
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
    }

    @Option(name="-noReconnect",usage="Doesn't try to reconnect when a communication fail, and exit instead")
    public boolean noReconnect = false;

    public static void main(String... args) throws Exception {
        Launcher launcher = new Launcher();
        CmdLineParser parser = new CmdLineParser(launcher);
        try {
            parser.parseArgument(args);
            launcher.run();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("java -jar slave.jar [options...]");
            parser.printUsage(System.err);
            System.err.println();
        }
    }

    public void run() throws Exception {
        if (slaveLog!=null) {
            System.setErr(new PrintStream(new TeeOutputStream(System.err,new FileOutputStream(slaveLog))));
        }
        if(auth!=null) {
            final int idx = auth.indexOf(':');
            if(idx<0)   throw new CmdLineException(null, "No ':' in the -auth option");
            Authenticator.setDefault(new Authenticator() {
                @Override public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(auth.substring(0,idx), auth.substring(idx+1).toCharArray());
                }
            });
        }
        if(connectionTarget!=null) {
            runAsTcpClient();
            System.exit(0);
        } else
        if(slaveJnlpURL!=null) {
            List<String> jnlpArgs = parseJnlpArguments();
            if (this.noReconnect) {
                jnlpArgs.add("-noreconnect");
            }
            try {
                hudson.remoting.jnlp.Main._main(jnlpArgs.toArray(new String[jnlpArgs.size()]));
            } catch (CmdLineException e) {
                System.err.println("JNLP file "+slaveJnlpURL+" has invalid arguments: "+jnlpArgs);
                System.err.println("Most likely a configuration error in the master");
                System.err.println(e.getMessage());
                System.exit(1);
            }
        } else
        if(tcpPortFile!=null) {
            runAsTcpServer();
            System.exit(0);
        } else {
            runWithStdinStdout();
            System.exit(0);
        }
    }

    /**
     * Parses the connection arguments from JNLP file given in the URL.
     */
    public List<String> parseJnlpArguments() throws ParserConfigurationException, SAXException, IOException, InterruptedException {
        if (secret != null) {
            slaveJnlpURL = new URL(slaveJnlpURL + "?encrypt=true");
            if (slaveJnlpCredentials != null) {
                throw new IOException("-jnlpCredentials and -secret are mutually exclusive");
            }
        }
        while (true) {
            try {
                URLConnection con = slaveJnlpURL.openConnection();
                if (con instanceof HttpURLConnection && slaveJnlpCredentials != null) {
                    HttpURLConnection http = (HttpURLConnection) con;
                    String userPassword = slaveJnlpCredentials;
                    String encoding = Base64.encode(userPassword.getBytes("UTF-8"));
                    http.setRequestProperty("Authorization", "Basic " + encoding);
                }
                con.connect();

                if (con instanceof HttpURLConnection) {
                    HttpURLConnection http = (HttpURLConnection) con;
                    if(http.getResponseCode()>=400)
                        // got the error code. report that (such as 401)
                        throw new IOException("Failed to load "+slaveJnlpURL+": "+http.getResponseCode()+" "+http.getResponseMessage());
                }

                Document dom;

                // check if this URL points to a .jnlp file
                String contentType = con.getHeaderField("Content-Type");
                String expectedContentType = secret == null ? "application/x-java-jnlp-file" : "application/octet-stream";
                InputStream input = con.getInputStream();
                if (secret != null) {
                    byte[] payload = toByteArray(input);
                    // the first 16 bytes (128bit) are initialization vector

                    try {
                        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
                        cipher.init(Cipher.DECRYPT_MODE,
                                new SecretKeySpec(fromHexString(secret.substring(0, Math.min(secret.length(), 32))), "AES"),
                                new IvParameterSpec(payload,0,16));
                        byte[] decrypted = cipher.doFinal(payload,16,payload.length-16);
                        input = new ByteArrayInputStream(decrypted);
                    } catch (GeneralSecurityException x) {
                        throw (IOException)new IOException("Failed to decrypt the JNLP file. Invalid secret key?").initCause(x);
                    }
                }
                if(contentType==null || !contentType.startsWith(expectedContentType)) {
                    // load DOM anyway, but if it fails to parse, that's probably because this is not an XML file to begin with.
                    try {
                        dom = loadDom(slaveJnlpURL, input);
                    } catch (SAXException e) {
                        throw new IOException(slaveJnlpURL+" doesn't look like a JNLP file; content type was "+contentType);
                    } catch (IOException e) {
                        throw new IOException(slaveJnlpURL+" doesn't look like a JNLP file; content type was "+contentType);
                    }
                } else {
                    dom = loadDom(slaveJnlpURL, input);
                }

                // exec into the JNLP launcher, to fetch the connection parameter through JNLP.
                NodeList argElements = dom.getElementsByTagName("argument");
                List<String> jnlpArgs = new ArrayList<String>();
                for( int i=0; i<argElements.getLength(); i++ )
                        jnlpArgs.add(argElements.item(i).getTextContent());
                if (slaveJnlpCredentials != null) {
                    jnlpArgs.add("-credentials");
                    jnlpArgs.add(slaveJnlpCredentials);
                }
                // force a headless mode
                jnlpArgs.add("-headless");
                return jnlpArgs;
            } catch (SSLHandshakeException e) {
                if(e.getMessage().contains("PKIX path building failed")) {
                    // invalid SSL certificate. One reason this happens is when the certificate is self-signed
                    IOException x = new IOException("Failed to validate a server certificate. If you are using a self-signed certificate, you can use the -noCertificateCheck option to bypass this check.");
                    x.initCause(e);
                    throw x;
                } else
                    throw e;
            } catch (IOException e) {
                if (this.noReconnect)
                    throw (IOException)new IOException("Failing to obtain "+slaveJnlpURL).initCause(e);

                System.err.println("Failing to obtain "+slaveJnlpURL);
                e.printStackTrace(System.err);
                System.err.println("Waiting 10 seconds before retry");
                Thread.sleep(10*1000);
                // retry
            }
        }
    }

    private byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        while ((c = input.read()) != -1) {
            baos.write(c);
        }
        return baos.toByteArray();
    }

    // from hudson.Util
    private static byte[] fromHexString(String data) {
        byte[] r = new byte[data.length() / 2];
        for (int i = 0; i < data.length(); i += 2)
            r[i / 2] = (byte) Integer.parseInt(data.substring(i, i + 2), 16);
        return r;
    }

    private static Document loadDom(URL slaveJnlpURL, InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return db.parse(is, slaveJnlpURL.toExternalForm());
    }

    /**
     * Listens on an ephemeral port, record that port number in a port file,
     * then accepts one TCP connection.
     */
    private void runAsTcpServer() throws IOException, InterruptedException {
        // if no one connects for too long, assume something went wrong
        // and avoid hanging forever
        ServerSocket ss = new ServerSocket(0,1);
        ss.setSoTimeout(30*1000);

        // write a port file to report the port number
        FileWriter w = new FileWriter(tcpPortFile);
        w.write(String.valueOf(ss.getLocalPort()));
        w.close();

        // accept just one connection and that's it.
        // when we are done, remove the port file to avoid stale port file
        Socket s;
        try {
            s = ss.accept();
            ss.close();
        } finally {
            tcpPortFile.delete();
        }

        runOnSocket(s);
    }

    private void runOnSocket(Socket s) throws IOException, InterruptedException {
        // this prevents a connection from silently terminated by the router in between or the other peer
        // and that goes without unnoticed. However, the time out is often very long (for example 2 hours
        // by default in Linux) that this alone is enough to prevent that.
        s.setKeepAlive(true);
        // we take care of buffering on our own
        s.setTcpNoDelay(true);
        main(new BufferedInputStream(new SocketInputStream(s)),
             new BufferedOutputStream(new SocketOutputStream(s)), mode,ping,
             new FileSystemJarCache(jarCache,true));
    }

    /**
     * Connects to the given TCP port and then start running
     */
    private void runAsTcpClient() throws IOException, InterruptedException {
        // if no one connects for too long, assume something went wrong
        // and avoid hanging forever
        Socket s = new Socket(connectionTarget.getAddress(),connectionTarget.getPort());

        runOnSocket(s);
    }

    private void runWithStdinStdout() throws IOException, InterruptedException {
        // use stdin/stdout for channel communication
        ttyCheck();

        if (isWindows()) {
            /*
                To prevent the dead lock between GetFileType from _ioinit in C runtime and blocking read that ChannelReaderThread
                would do on stdin, load the crypto DLL first.

                This is a band-aid solution to the problem. Still searching for more fundamental fix. 

                02f1e750 7c90d99a ntdll!KiFastSystemCallRet
                02f1e754 7c810f63 ntdll!NtQueryVolumeInformationFile+0xc
                02f1e784 77c2c9f9 kernel32!GetFileType+0x7e
                02f1e7e8 77c1f01d msvcrt!_ioinit+0x19f
                02f1e88c 7c90118a msvcrt!__CRTDLL_INIT+0xac
                02f1e8ac 7c91c4fa ntdll!LdrpCallInitRoutine+0x14
                02f1e9b4 7c916371 ntdll!LdrpRunInitializeRoutines+0x344
                02f1ec60 7c9164d3 ntdll!LdrpLoadDll+0x3e5
                02f1ef08 7c801bbd ntdll!LdrLoadDll+0x230
                02f1ef70 7c801d72 kernel32!LoadLibraryExW+0x18e
                02f1ef84 7c801da8 kernel32!LoadLibraryExA+0x1f
                02f1efa0 77de8830 kernel32!LoadLibraryA+0x94
                02f1f05c 6d3eb1be ADVAPI32!CryptAcquireContextA+0x512
                WARNING: Stack unwind information not available. Following frames may be wrong.
                02f1f13c 6d99c844 java_6d3e0000!Java_sun_security_provider_NativeSeedGenerator_nativeGenerateSeed+0x6e

                see http://weblogs.java.net/blog/kohsuke/archive/2009/09/28/reading-stdin-may-cause-your-jvm-hang
                for more details
             */
            new SecureRandom().nextBoolean();
        }

        // this will prevent programs from accidentally writing to System.out
        // and messing up the stream.
        OutputStream os = new StandardOutputStream();
        System.setOut(System.err);

        // System.in/out appear to be already buffered (at least that was the case in Linux and Windows as of Java6)
        // so we are not going to double-buffer these.
        main(System.in, os, mode, ping, new FileSystemJarCache(jarCache,true));
    }

    private static void ttyCheck() {
        try {
            Method m = System.class.getMethod("console");
            Object console = m.invoke(null);
            if(console!=null) {
                // we seem to be running from interactive console. issue a warning.
                // but since this diagnosis could be wrong, go on and do what we normally do anyway. Don't exit.
                System.out.println(
                        "WARNING: Are you running slave agent from an interactive console?\n" +
                        "If so, you are probably using it incorrectly.\n" +
                        "See http://wiki.jenkins-ci.org/display/JENKINS/Launching+slave.jar+from+from+console");
            }
        } catch (LinkageError e) {
            // we are probably running on JDK5 that doesn't have System.console()
            // we can't check
        } catch (InvocationTargetException e) {
            // this is impossible
            throw new AssertionError(e);
        } catch (NoSuchMethodException e) {
            // must be running on JDK5
        } catch (IllegalAccessException e) {
            // this is impossible
            throw new AssertionError(e);
        }
    }

    public static void main(InputStream is, OutputStream os) throws IOException, InterruptedException {
        main(is,os,Mode.BINARY);
    }

    public static void main(InputStream is, OutputStream os, Mode mode) throws IOException, InterruptedException {
        main(is,os,mode,false);
    }

    /**
     * @deprecated
     *      Use {@link #main(InputStream, OutputStream, Mode, boolean, JarCache)}
     */
    public static void main(InputStream is, OutputStream os, Mode mode, boolean performPing) throws IOException, InterruptedException {
        main(is, os, mode, performPing,
                new FileSystemJarCache(new File(System.getProperty("user.home"),".jenkins/cache/jars"),true));
    }
    /**
     * @since 2.24
     */
    public static void main(InputStream is, OutputStream os, Mode mode, boolean performPing, JarCache cache) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        Channel channel = new Channel("channel", executor,
                ClassicCommandTransport.create(mode,is,os,null,null,new Capability()), false, null, cache);
        System.err.println("channel started");
        long timeout = 1000 * Long.parseLong(
                System.getProperty("hudson.remoting.Launcher.pingTimeoutSec", "240")),
             interval = 1000 * Long.parseLong(
                System.getProperty("hudson.remoting.Launcher.pingIntervalSec", "600"));
        if (performPing && timeout > 0 && interval > 0) {
            new PingThread(channel, timeout, interval) {
                @Override
                protected void onDead() {
                    System.err.println("Ping failed. Terminating");
                    System.exit(-1);
                }
            }.start();
        }
        channel.join();
        System.err.println("channel stopped");
    }

    /**
     * {@link X509TrustManager} that performs no check at all.
     */
    private static class NoCheckTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    public static boolean isWindows() {
        return File.pathSeparatorChar==';';
    }

    private static String computeVersion() {
        Properties props = new Properties();
        try {
            InputStream is = Launcher.class.getResourceAsStream("hudson-version.properties");
            if(is!=null)
                props.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return props.getProperty("version", "?");
    }

    /**
     * Version number of Hudson this slave.jar is from.
     */
    public static final String VERSION = computeVersion();
}
