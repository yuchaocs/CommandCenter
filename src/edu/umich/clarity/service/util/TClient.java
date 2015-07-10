package edu.umich.clarity.service.util;

import java.io.IOException;
import java.net.InetSocketAddress;

import edu.umich.clarity.thrift.NodeManagerService;
import org.apache.log4j.Logger;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import edu.umich.clarity.thrift.IPAService;
import edu.umich.clarity.thrift.SchedulerService;

/**
 * Helper class for initializing the Thrift client for various IPA services.
 * TODO change the design to use instance creating service client so that after the client can be safely closed.
 *
 * @author hailong
 */
public class TClient {
    private final int TIMEOUT = 0;
    private static final Logger LOG = Logger.getLogger(TClient.class);
    private TTransport transport = null;

    public IPAService.Client createIPAClient(InetSocketAddress socket)
            throws IOException {
        return createIPAClient(socket.getAddress().getHostAddress(),
                socket.getPort());
    }

    public IPAService.Client createIPAClient(String host, int port)
            throws IOException {
        TTransport trans = new TFramedTransport(
                new TSocket(host, port, TIMEOUT));
        try {
            trans.open();
        } catch (TTransportException te) {
            LOG.error("Error creating IPA client to " + host + ":" + port);
        }
        TProtocol proto = new TBinaryProtocol(trans);
        this.transport = trans;
        IPAService.Client client = new IPAService.Client(proto);
        return client;
    }

    public SchedulerService.Client createSchedulerClient(
            InetSocketAddress socket) throws IOException {
        return createSchedulerClient(socket.getAddress().getHostAddress(),
                socket.getPort());
    }

    public SchedulerService.Client createSchedulerClient(String host,
                                                         int port) throws IOException {
        TTransport trans = new TFramedTransport(
                new TSocket(host, port, TIMEOUT));
        try {
            trans.open();
        } catch (TTransportException te) {
            LOG.error("Error creating Scheduler client to " + host + ":" + port);
        }
        TProtocol proto = new TBinaryProtocol(trans);
        this.transport = trans;
        SchedulerService.Client client = new SchedulerService.Client(proto);
        return client;
    }

    public NodeManagerService.Client createNodeManagerClient(String host,
                                                             int port) throws IOException {
        TTransport trans =
                new TSocket(host, port, TIMEOUT);
        try {
            trans.open();
        } catch (TTransportException te) {
            LOG.error("Error creating Scheduler client to " + host + ":" + port);
        }
        TProtocol proto = new TBinaryProtocol(trans);
        this.transport = trans;
        NodeManagerService.Client client = new NodeManagerService.Client(proto);
        return client;
    }

    public void close() {
        transport.close();
    }
}
