package edu.umich.clarity.service.util;

import java.io.IOException;
import java.net.InetSocketAddress;

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
 * 
 * @author hailong
 *
 */
public class TClient {
	private static final int TIMEOUT = 0;
	private static final Logger LOG = Logger.getLogger(TClient.class);

	public static IPAService.Client creatIPAClient(InetSocketAddress socket)
			throws IOException {
		return creatIPAClient(socket.getAddress().getHostAddress(),
				socket.getPort());
	}

	public static IPAService.Client creatIPAClient(String host, int port)
			throws IOException {
		TTransport trans = new TFramedTransport(
				new TSocket(host, port, TIMEOUT));
		try {
			trans.open();
		} catch (TTransportException te) {
			LOG.error("Error creating IPA client to " + host + ":" + port);
		}
		TProtocol proto = new TBinaryProtocol(trans);
		IPAService.Client client = new IPAService.Client(proto);
		return client;
	}

	public static SchedulerService.Client creatSchedulerClient(
			InetSocketAddress socket) throws IOException {
		return creatSchedulerClient(socket.getAddress().getHostAddress(),
				socket.getPort());
	}

	public static SchedulerService.Client creatSchedulerClient(String host,
			int port) throws IOException {
		TTransport trans = new TFramedTransport(
				new TSocket(host, port, TIMEOUT));
		try {
			trans.open();
		} catch (TTransportException te) {
			LOG.error("Error creating Scheduler client to " + host + ":" + port);
		}
		TProtocol proto = new TBinaryProtocol(trans);
		SchedulerService.Client client = new SchedulerService.Client(proto);
		return client;
	}
}
