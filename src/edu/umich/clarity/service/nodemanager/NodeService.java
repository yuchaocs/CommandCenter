package edu.umich.clarity.service.nodemanager;

import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.IPAService;
import edu.umich.clarity.thrift.NodeManagerService;
import edu.umich.clarity.thrift.THostPort;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by hailong on 7/3/15.
 */
public class NodeService implements NodeManagerService.Iface {
    private static final Logger LOG = Logger.getLogger(NodeService.class);
    private String nmAddress;

    private String[] serviceTypeArray = {"asr", "imm", "qa"};
    private int[] servicePortArray = {9075, 9085, 9095};
    private Map<String, List<Integer>> serviceCandidate = new HashMap<String, List<Integer>>();

    public NodeService(String nmAddress) {
        this.nmAddress = nmAddress;
        for (int i = 0; i < serviceTypeArray.length; i++) {
            List<Integer> portList = new LinkedList<Integer>();
            for (int j = 0; j < 5; j++) {
                portList.add(servicePortArray[i] + j);
            }
            serviceCandidate.put(serviceTypeArray[i], portList);
        }
    }

    @Override
    public THostPort launchServiceInstance(String serviceType, double budget) throws TException {
        THostPort hostPort = null;
        List<Integer> portList = serviceCandidate.get(serviceType);
        if (portList.size() != 0) {
            hostPort.setIp(this.nmAddress);
            hostPort.setPort(portList.get(0));
            portList.remove(0);
        }
        return hostPort;
    }

    public static void main(String[] args) throws IOException, TException {
        NodeService nmService = new NodeService(args[0]);
        NodeManagerService.Processor<NodeManagerService.Iface> processor = new NodeManagerService.Processor<NodeManagerService.Iface>(
                nmService);
        TServers.launchBlockingThreadedThriftServer(new Integer(args[1]), processor);
        LOG.info("starting node manager service at " + args[0] + ":" + args[1]);
    }
}
