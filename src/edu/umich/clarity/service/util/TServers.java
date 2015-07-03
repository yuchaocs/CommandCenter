package edu.umich.clarity.service.util;

import java.io.IOException;

import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.*;

public class TServers {
    // private static final Logger LOG = Logger.getLogger(TServers.class);
    public static final int workerCount = 16;

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

    public static void launchBlockingThreadedThriftServer(int port,
                                                          TProcessor processor) throws IOException {
        TServerTransport serverTransport = null;
        try {
            serverTransport = new TServerSocket(port);
        } catch (TTransportException e) {
            e.printStackTrace();
        }
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        args.minWorkerThreads(workerCount);
        TServer server = new TThreadPoolServer(args);
        new Thread(new TServerRunnable(server)).start();
    }
}
