package org.rapidoid.net.impl;

/*
 * #%L
 * rapidoid-net
 * %%
 * Copyright (C) 2014 - 2015 Nikolche Mihajlovski and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.rapidoid.annotation.Authors;
import org.rapidoid.annotation.Inject;
import org.rapidoid.annotation.Since;
import org.rapidoid.config.Conf;
import org.rapidoid.log.Log;
import org.rapidoid.net.Protocol;
import org.rapidoid.net.TCPServer;
import org.rapidoid.net.TCPServerInfo;
import org.rapidoid.util.Rnd;
import org.rapidoid.util.U;

@Authors("Nikolche Mihajlovski")
@Since("2.0.0")
public class RapidoidServerLoop extends AbstractLoop<TCPServer> implements TCPServer, TCPServerInfo {

	private volatile RapidoidWorker[] ioWorkers;

	private RapidoidWorker currentWorker;

	@Inject(optional = true)
	private int port = 8080;

	@Inject(optional = true)
	private String address = "0.0.0.0";

	@Inject(optional = true)
	private int workers = Conf.cpus();

	@Inject(optional = true)
	private boolean blockingAccept = false;

	protected final Protocol protocol;

	private final Class<? extends RapidoidHelper> helperClass;

	private final Class<? extends DefaultExchange<?>> exchangeClass;

	private ServerSocketChannel serverSocketChannel;

	private final Selector selector;

	public RapidoidServerLoop(Protocol protocol, Class<? extends DefaultExchange<?>> exchangeClass,
			Class<? extends RapidoidHelper> helperClass) {
		super("server");
		this.protocol = protocol;
		this.exchangeClass = exchangeClass;
		this.helperClass = U.or(helperClass, RapidoidHelper.class);

		try {
			this.selector = Selector.open();
		} catch (IOException e) {
			Log.error("Cannot open selector!", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	protected final void beforeLoop() {
		validate();

		try {
			openSocket();
		} catch (IOException e) {
			throw U.rte("Cannot open socket!", e);
		}
	}

	private void validate() {
		U.must(workers <= RapidoidWorker.MAX_IO_WORKERS, "Too many workers! Maximum = %s",
				RapidoidWorker.MAX_IO_WORKERS);
	}

	private void openSocket() throws IOException {
		U.notNull(protocol, "protocol");
		U.notNull(helperClass, "helperClass");

		Log.info("Initializing server", "port", port, "accept", blockingAccept ? "blocking" : "non-blocking");

		serverSocketChannel = ServerSocketChannel.open();

		if ((serverSocketChannel.isOpen()) && (selector.isOpen())) {

			serverSocketChannel.configureBlocking(blockingAccept);

			ServerSocket socket = serverSocketChannel.socket();

			Log.info("Opening port to listen", "port", port);

			InetSocketAddress addr = new InetSocketAddress(address, port);

			socket.setReceiveBufferSize(16 * 1024);
			socket.setReuseAddress(true);
			socket.bind(addr, 1024);

			Log.info("Opened server socket", "address", addr);

			if (!blockingAccept) {
				Log.info("Registering accept selector");
				serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			}

			Log.info("Waiting for connections...");

			initWorkers();

		} else {
			throw U.rte("Cannot open socket!");
		}
	}

	private void initWorkers() {
		ioWorkers = new RapidoidWorker[workers];

		for (int i = 0; i < ioWorkers.length; i++) {

			RapidoidWorkerThread workerThread = new RapidoidWorkerThread(i, protocol, exchangeClass, helperClass);
			workerThread.start();

			ioWorkers[i] = workerThread.getWorker();

			if (i > 0) {
				ioWorkers[i - 1].next = ioWorkers[i];
			}
		}

		ioWorkers[ioWorkers.length - 1].next = ioWorkers[0];
		currentWorker = ioWorkers[0];

		for (RapidoidWorker worker : ioWorkers) {
			worker.waitToStart();
		}
	}

	@Override
	public synchronized TCPServer start() {
		new Thread(this, "server").start();

		return super.start();
	}

	@Override
	public synchronized TCPServer shutdown() {
		stopLoop();

		if (ioWorkers != null) {
			for (RapidoidWorker worker : ioWorkers) {
				worker.stopLoop();
			}
		}

		if (serverSocketChannel != null && selector != null && serverSocketChannel.isOpen() && selector.isOpen()) {
			try {
				selector.close();
				serverSocketChannel.close();
			} catch (IOException e) {
				Log.warn("Cannot close socket or selector!", e);
			}
		}

		return super.shutdown();
	}

	public synchronized RapidoidConnection newConnection() {
		int rndWorker = Rnd.rnd(ioWorkers.length);
		return ioWorkers[rndWorker].newConnection();
	}

	public synchronized void process(RapidoidConnection conn) {
		conn.worker.process(conn);
	}

	@Override
	public synchronized String process(String input) {
		if (ioWorkers == null) {
			initWorkers();
		}

		RapidoidConnection conn = newConnection();
		conn.setInitial(false);
		conn.input.append(input);
		conn.setProtocol(protocol);
		process(conn);
		return conn.output.asText();
	}

	public Protocol getProtocol() {
		return protocol;
	}

	@Override
	public TCPServerInfo info() {
		return this;
	}

	@Override
	public long messagesProcessed() {
		long total = 0;

		for (int i = 0; i < ioWorkers.length; i++) {
			total += ioWorkers[i].getMessagesProcessed();
		}

		return total;
	}

	@Override
	protected void insideLoop() {
		if (blockingAccept) {
			processBlocking();
		} else {
			processNonBlocking();
		}
	}

	private void processNonBlocking() {
		try {
			selector.select(50);
		} catch (IOException e) {
			Log.error("Select failed!", e);
		}

		try {
			Set<SelectionKey> selectedKeys = selector.selectedKeys();
			synchronized (selectedKeys) {

				Iterator<?> iter = selectedKeys.iterator();

				while (iter.hasNext()) {
					SelectionKey key = (SelectionKey) iter.next();
					iter.remove();

					acceptChannel((ServerSocketChannel) key.channel());
				}
			}
		} catch (ClosedSelectorException e) {
			// do nothing
		}
	}

	private void processBlocking() {
		acceptChannel(serverSocketChannel);
	}

	private void acceptChannel(ServerSocketChannel serverChannel) {
		try {
			SocketChannel channel = serverSocketChannel.accept();
			currentWorker.accept(channel);
			currentWorker = currentWorker.next;
		} catch (IOException e) {
			Log.error("Acceptor error!", e);
		}
	}

}
