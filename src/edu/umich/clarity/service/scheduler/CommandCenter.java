package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.IPAService.Client;
import edu.umich.clarity.thrift.*;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandCenter implements SchedulerService.Iface {

    private static final Logger LOG = Logger.getLogger(CommandCenter.class);
    private static final String SCHEDULER_IP = "localhost";
    private static final int SCHEDULER_PORT = 8888;
    private static final int QUERY_INTERVAL = 100;
    private static final long LATENCY_BUDGET = 300;
    // TODO may need to DAG structure to store the application workflow
    private static final List<String> sirius_workflow = new LinkedList<String>();
    private static ConcurrentMap<String, Map<THostPort, Client>> serviceMap = new ConcurrentHashMap<String, Map<THostPort, Client>>();
    private static ConcurrentMap<String, Double> budgetMap = new ConcurrentHashMap<String, Double>();
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();
    private static CSVWriter csvWriter = null;
    private static final String FILE_HEADER = "asrq,asrs,immq,imms,qaq,qas";

    public void initialize() {
        sirius_workflow.add("gen");
        sirius_workflow.add("asr");
        sirius_workflow.add("im");
        sirius_workflow.add("qa");
        String workflow = "";
        for (String name : sirius_workflow) {
            workflow += name + "->";
        }
        try {
            csvWriter = new CSVWriter(new FileWriter("query_latency.csv"), ',');
            csvWriter.writeNext(FILE_HEADER.split(","));
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("current workflow within command center is " + workflow);
        // new Thread(new budgetAdjusterRunnable()).start();
        new Thread(new latencyStatisticRunnable()).start();
    }

    @Override
    public RegReply registerBackend(RegMessage message) throws TException {
        String appName = message.getApp_name();
        THostPort hostPort = message.getEndpoint();
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        if (!appName.equalsIgnoreCase("gen")) {
            Client serviceClient = null;
            try {
                serviceClient = TClient.creatIPAClient(hostPort.getIp(),
                        hostPort.getPort());
            } catch (IOException e) {
                e.printStackTrace();
            }
            double service_budget = 0;
            if (serviceMap.containsKey(appName)) {
                serviceMap.get(appName).put(hostPort, serviceClient);
                service_budget = budgetMap.get(appName);

            } else {
                Map<THostPort, Client> serviceList = new HashMap<THostPort, Client>();
                serviceList.put(hostPort, serviceClient);
                serviceMap.put(appName, serviceList);
                double total_budget = 0;
                for (String key : budgetMap.keySet()) {
                    total_budget += budgetMap.get(key);
                }
                double left_budget = LATENCY_BUDGET - total_budget
                        - message.getBudget();
                service_budget = left_budget >= 0 ? Math.min(left_budget,
                        message.getBudget()) : 0;
                Map<THostPort, Client> clientMap = new HashMap<THostPort, Client>();
                clientMap.put(hostPort, serviceClient);
                serviceMap.put(appName, clientMap);
                budgetMap.put(appName, service_budget);
            }
            serviceClient.updatBudget(service_budget);
            LOG.info("assigning budget: " + service_budget + "ms "
                    + "to service stage " + appName + " running on "
                    + hostPort.getIp() + ":" + hostPort.getPort());
        }
        RegReply regReply = new RegReply();
        String decService = serviceWorkflow(appName);
        if (decService != null) {
            LOG.info("identifying next service stage is " + decService);
            List<THostPort> serviceList = new LinkedList<THostPort>();
            if (serviceMap.get(decService) != null) {
                for (THostPort hostport : serviceMap.get(decService).keySet()) {
                    serviceList.add(hostport);
                }
            }
            regReply.setService_list(serviceList);
            LOG.info("replying " + regReply.getService_list().size()
                    + " downstream services to " + appName + " running on "
                    + hostPort.getIp() + ":" + hostPort.getPort());
        } else {
            regReply.setFinal_stage(true);
        }
        return regReply;
    }

    private String serviceWorkflow(String serviceName) {
        String decService = null;
        // this is the dummy workflow
        int index = sirius_workflow.indexOf(serviceName);
        if (index < sirius_workflow.size() - 1) {
            decService = sirius_workflow.get(index + 1);
        }
        return decService;
    }

    @Override
    public void enqueueFinishedQuery(QuerySpec query) throws TException {
        try {
            finishedQueryQueue.put(query);
            /**
             * there are three timestamps for each stage, the first timestamp is
             * when the query entering the queue, the second timestamp is when
             * the query get served, the third one is when the serving iss done.
             */
            long total_queuing = 0;
            long total_serving = 0;
            for (int i = 0; i < query.getTimestamp().size(); i += 3) {
                long queuing_time = query.getTimestamp().get(i + 1)
                        - query.getTimestamp().get(i);
                total_queuing += queuing_time;
                long serving_time = query.getTimestamp().get(i + 2)
                        - query.getTimestamp().get(i + 1);
                total_serving += serving_time;
                LOG.info("Query: " + query.getName() + " queuing " + queuing_time
                        + "ms" + " serving " + serving_time + "ms" + " @stage "
                        + sirius_workflow.get(i / 3));
            }

            LOG.info("Query: " + query.getName() + " total queuing "
                    + total_queuing + "ms" + " total serving " + total_serving
                    + "ms" + " at all stages with total latency "
                    + (total_queuing + total_serving) + "ms");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class latencyStatisticRunnable implements Runnable {

        @Override
        public void run() {
            LOG.info("starting the helper thread to scan the finished query queue for logging latency statistics");
            while (true) {
                try {
                    QuerySpec query = finishedQueryQueue.take();
                    String csvEntry = "";
                    for (int i = 0; i < query.getTimestamp().size(); i += 3) {
                        long queuing_time = query.getTimestamp().get(i + 1)
                                - query.getTimestamp().get(i);
                        long serving_time = query.getTimestamp().get(i + 2)
                                - query.getTimestamp().get(i + 1);
                        csvEntry += queuing_time + "," + serving_time;
                        if ((i + 3) < query.getTimestamp().size())
                            csvEntry += ",";
                    }
                    csvWriter.writeNext(csvEntry.split(","));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class budgetAdjusterRunnable implements Runnable {
        long removed_queries = 0;
        long[] stage_latency = new long[sirius_workflow.size()];
        long total_latency = 0;

        @Override
        public void run() {
            LOG.info("starting the helper thread to scan the finished query queue and update stage budget every "
                    + QUERY_INTERVAL + " queries");
            while (true) {
                try {
                    QuerySpec query = finishedQueryQueue.take();
                    for (int i = 0; i < query.getTimestamp().size(); i += 3) {
                        long queuing_time = query.getTimestamp().get(i + 1)
                                - query.getTimestamp().get(i);
                        long serving_time = query.getTimestamp().get(i + 2)
                                - query.getTimestamp().get(i + 1);
                        stage_latency[i / 3] += queuing_time + serving_time;
                        total_latency += queuing_time + serving_time;
                    }
                    removed_queries += 1;
                    if (removed_queries % QUERY_INTERVAL == 0) {
                        for (int i = 0; i < stage_latency.length; i++) {
                            double stage_budget = stage_latency[i] * 1.0
                                    / total_latency * LATENCY_BUDGET;
                            for (THostPort hostport : serviceMap.get(
                                    sirius_workflow.get(i)).keySet()) {
                                Client client = serviceMap.get(
                                        sirius_workflow.get(i)).get(hostport);
                                try {
                                    LOG.info("updating "
                                            + "sirius_workflow.get(i)"
                                            + " stage at " + hostport.getIp()
                                            + ":" + hostport.getPort()
                                            + " with budget " + stage_budget);
                                    client.updatBudget(stage_budget);
                                } catch (TException e) {
                                    e.printStackTrace();
                                }
                            }
                            stage_latency[i] = 0;
                        }
                        total_latency = 0;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CommandCenter commandCenter = new CommandCenter();
        SchedulerService.Processor<SchedulerService.Iface> processor = new SchedulerService.Processor<SchedulerService.Iface>(
                commandCenter);
        TServers.launchSingleThreadThriftServer(SCHEDULER_PORT, processor);
        LOG.info("starting command center at " + SCHEDULER_IP + ":"
                + SCHEDULER_PORT);
        commandCenter.initialize();
    }
}
