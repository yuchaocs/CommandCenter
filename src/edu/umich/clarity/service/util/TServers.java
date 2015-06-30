package edu.umich.clarity.service.util;

import java.io.IOException;

import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransportException;

public class TServers {
	// private static final Logger LOG = Logger.getLogger(TServers.class);

	public static void launchSingleThreadThriftServer(int port,
			TProcessor processor) throws IOException {
		TNonblockingServerTransport serverTransport;
		try {
			serverTransport = new TNonblockingServerSocket(port);
		} catch (TTransportException ex) {
			throw new IOException(ex);
		}
		TNonblockingServer.Args serverArgs = new TNonblockingServer.Args(
				serverTransport);
		serverArgs.processor(processor);
		TServer server = new TNonblockingServer(serverArgs);
		new Thread(new TServerRunnable(server)).start();
	}

	private static class TServerRunnable implements Runnable {
		private TServer server;

		public TServerRunnable(TServer server) {
			this.server = server;
		}

		@Override
		public void run() {
			this.server.serve();
		}

	}
}
