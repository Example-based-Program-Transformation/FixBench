package org.fiteagle.core.aaa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import net.iharder.Base64;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.fiteagle.core.config.FiteaglePreferences;
import org.fiteagle.core.userdatabase.User;
import org.fiteagle.core.userdatabase.UserDB.RecordNotFoundException;
import org.fiteagle.core.userdatabase.UserDBManager;

public class CertificateAuthority {

 
  
  FiteaglePreferences preferences = null;
  private KeyStoreManagement keyStoreManagement = new KeyStoreManagement();
  
  public X509Certificate createCertificate(User newUser) throws Exception{
    X509Certificate caCert = keyStoreManagement.getCACert();
    X500Name issuer = new JcaX509CertificateHolder(caCert).getSubject();
    PrivateKey caPrivateKey = keyStoreManagement.getCAPrivateKey();
    ContentSigner contentsigner = new JcaContentSignerBuilder("SHA1WithRSAEncryption").build(caPrivateKey);
    
    X500Name subject = createX500Name(newUser);
    SubjectPublicKeyInfo  subjectsPublicKeyInfo = getPublicKey(newUser);
    X509v3CertificateBuilder ca_gen = new X509v3CertificateBuilder(issuer, new BigInteger( new SecureRandom().generateSeed(256)), new Date(), new Date(System.currentTimeMillis()+ 31500000000L), subject, subjectsPublicKeyInfo); 
    BasicConstraints ca_constraint = new BasicConstraints(false);
    ca_gen.addExtension(X509Extension.basicConstraints, true, ca_constraint);
    GeneralNames subjectAltName = new GeneralNames(
        new GeneralName(GeneralName.uniformResourceIdentifier, "urn:publicid:IDN+emulab.net+user+stoller"));

    X509Extension extension = new X509Extension(false, new DEROctetString(subjectAltName));
    ca_gen.addExtension(X509Extension.subjectAlternativeName, false, extension.getParsedValue()); 
    X509CertificateHolder holder = (X509CertificateHolder) ca_gen.build(contentsigner);
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(holder.getEncoded()));
  }

  private SubjectPublicKeyInfo getPublicKey(User newUser) throws Exception {
    KeyDecoder keyDecoder = new KeyDecoder();
    PublicKey key =keyDecoder.decodePublicKey(newUser.getPublicKeys().get(0));
    SubjectPublicKeyInfo subPubInfo = new SubjectPublicKeyInfo((ASN1Sequence) ASN1Sequence.fromByteArray(key.getEncoded()));
    return subPubInfo;
  }

  private X500Name createX500Name(User newUser) {
    X500Principal prince = new X500Principal("CN="+newUser.getUID());
    X500Name x500Name = new X500Name(prince.getName());
    return x500Name;
  }

 
 
  
  public void saveCertificate(String name, X509Certificate certificate) throws Exception{
    FileOutputStream fos = new FileOutputStream(name);
    fos.write(getCertficateEncoded(certificate).getBytes());
    fos.close();
  }

  private String getCertficateEncoded(X509Certificate cert) throws Exception{
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    bout.write("-----BEGIN CERTIFICATE-----\n".getBytes());
    bout.write(Base64.encodeBytesToBytes(cert.getEncoded(),0,cert.getEncoded().length,Base64.DO_BREAK_LINES));
    bout.write("\n-----END CERTIFICATE-----\n".getBytes());
    String encodedCert = new String(bout.toByteArray());
    bout.close();
    return encodedCert;
  }
  
  private String getCertificateBodyEncoded(X509Certificate cert) throws Exception{
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    bout.write(Base64.encodeBytesToBytes(cert.getEncoded(),0,cert.getEncoded().length,Base64.DO_BREAK_LINES));
    String encodedCert = new String(bout.toByteArray());
    bout.close();
    return encodedCert;
  }
  
  public String getUserCertificateAsString(String certString) {
      CertificateFactory cf = getCertifcateFactory();
      
      X509Certificate userCert =getX509Certificate( cf,  certString);
 
      User user = getUserFromCert(userCert);
      String alias =  user.getUID();
      X509Certificate storedCertificate = getStoredCertificate( alias);
      
      try {
        return getCertificateBodyEncoded(storedCertificate);
      } catch (Exception e) {
        throw new EncodeCertificateException();
      }
    }
    
    private X509Certificate getStoredCertificate(String alias) {
        return keyStoreManagement.getStoredCertificate(alias);
    }

 

    private X509Certificate getX509Certificate(CertificateFactory cf, String certString){
      InputStream in =  new ByteArrayInputStream(certString.getBytes());
      try{
        return (X509Certificate) cf.generateCertificate(in);
      }catch(Exception e){
        throw new GenerateCertificateException();
      }
    }
    private User getUserFromCert(X509Certificate userCert) {
    
    try {
      UserDBManager userDBManager; userDBManager = new UserDBManager();
      return userDBManager.getUserFromCert(userCert);
    } catch (SQLException e) {
      throw new RecordNotFoundException();
    }
    
      
  }

    private CertificateFactory getCertifcateFactory(){
      try{
        return CertificateFactory.getInstance("X.509");
      } catch(CertificateException e){
        throw new CertificateFactoryNotCreatedException();
      }
    }
    
    public class CertificateFactoryNotCreatedException extends RuntimeException {
      private static final long serialVersionUID = 1L;
      
    }
    
    public class GenerateCertificateException extends RuntimeException {
      private static final long serialVersionUID = 1L;
    }
    public class EncodeCertificateException extends RuntimeException {
      private static final long serialVersionUID = 1L;
    }
  }
 