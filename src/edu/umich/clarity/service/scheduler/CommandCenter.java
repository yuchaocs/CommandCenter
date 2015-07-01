package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.QuerySpec;
import edu.umich.clarity.thrift.RegMessage;
import edu.umich.clarity.thrift.SchedulerService;
import edu.umich.clarity.thrift.THostPort;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
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
    private static final String[] FILE_HEADER = {"asr_queuing", "asr_serving", "imm_queuing", "imm_serving", "qa_queuing", "qa_serving", "total_queuing", "total_serving"};
    private static ConcurrentMap<String, List<THostPort>> serviceMap = new ConcurrentHashMap<String, List<THostPort>>();
    private static ConcurrentMap<String, Double> budgetMap = new ConcurrentHashMap<String, Double>();
    private static CSVWriter csvWriter = null;
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();

    public static void main(String[] args) throws IOException {
        CommandCenter commandCenter = new CommandCenter();
        SchedulerService.Processor<SchedulerService.Iface> processor = new SchedulerService.Processor<SchedulerService.Iface>(
                commandCenter);
        TServers.launchSingleThreadThriftServer(SCHEDULER_PORT, processor);
        LOG.info("starting command center at " + SCHEDULER_IP + ":"
                + SCHEDULER_PORT);
        commandCenter.initialize();
    }

    public void initialize() {
        sirius_workflow.add("asr");
        sirius_workflow.add("imm");
        sirius_workflow.add("qa");
        String workflow = "";
        for (int i = 0; i < sirius_workflow.size(); i++) {
            workflow += sirius_workflow.get(i);
            if ((i + 1) < sirius_workflow.size()) {
                workflow += "->";
            }
        }
        try {
            csvWriter = new CSVWriter(new FileWriter("query_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            csvWriter.writeNext(FILE_HEADER);
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("current workflow within command center is " + workflow);
        // new Thread(new budgetAdjusterRunnable()).start();
        // new Thread(new latencyStatisticRunnable()).start();
    }

    @Override
    public THostPort consultAddress(String serviceType) throws TException {
        THostPort hostPort = null;
        List<THostPort> service_list = serviceMap.get(serviceType);
        if (service_list != null && service_list.size() != 0)
            hostPort = randomAssignService(service_list);
        return hostPort;
    }

    /**
     * Randomly choose a service of the required type.
     *
     * @param service_list the service candidates
     * @return the chosen service
     */
    private THostPort randomAssignService(List<THostPort> service_list) {
        THostPort hostPort;
        Random rand = new Random();
        hostPort = service_list.get(rand.nextInt(service_list.size()));
        return hostPort;
    }

    @Override
    public void registerBackend(RegMessage message) throws TException {
        String appName = message.getApp_name();
        THostPort hostPort = message.getEndpoint();
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        if (serviceMap.containsKey(appName)) {
            serviceMap.get(appName).add(hostPort);
        } else {
            List<THostPort> serviceList = new LinkedList<THostPort>();
            serviceList.add(hostPort);
            serviceMap.put(appName, serviceList);
        }
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
            ArrayList<String> csvEntry = new ArrayList<String>();
            long total_queuing = 0;
            long total_serving = 0;
            for (int i = 0; i < query.getTimestamp().size(); i += 3) {
                long queuing_time = query.getTimestamp().get(i + 1)
                        - query.getTimestamp().get(i);
                total_queuing += queuing_time;
                long serving_time = query.getTimestamp().get(i + 2)
                        - query.getTimestamp().get(i + 1);
                total_serving += serving_time;
                csvEntry.add("" + queuing_time);
                csvEntry.add("" + serving_time);
                LOG.info("Query: queuing time " + queuing_time
                        + "ms," + " serving time " + serving_time + "ms" + " @stage "
                        + sirius_workflow.get(i / 3));
            }
            LOG.info("Query: total queuing "
                    + total_queuing + "ms" + " total serving " + total_serving
                    + "ms" + " at all stages with total latency "
                    + (total_queuing + total_serving) + "ms");
            csvEntry.add("" + total_queuing);
            csvEntry.add("" + total_serving);
            csvWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
            csvWriter.flush();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
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
                    ArrayList<String> csvEntry = new ArrayList<String>();
                    long total_queuing = 0;
                    long total_serving = 0;
                    for (int i = 0; i < query.getTimestamp().size(); i += 3) {
                        long queuing_time = query.getTimestamp().get(i + 1)
                                - query.getTimestamp().get(i);
                        total_queuing += queuing_time;
                        long serving_time = query.getTimestamp().get(i + 2)
                                - query.getTimestamp().get(i + 1);
                        total_serving += serving_time;
                        csvEntry.add("" + queuing_time);
                        csvEntry.add("" + serving_time);
                    }
                    csvEntry.add("" + total_queuing);
                    csvEntry.add("" + total_serving);
                    csvWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                    csvWriter.flush();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
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
//                            for (THostPort hostport : serviceMap.get(
//                                    sirius_workflow.get(i)).keySet()) {
//                                Client client = serviceMap.get(
//                                        sirius_workflow.get(i)).get(hostport);
//                                try {
//                                    LOG.info("updating "
//                                            + "sirius_workflow.get(i)"
//                                            + " stage at " + hostport.getIp()
//                                            + ":" + hostport.getPort()
//                                            + " with budget " + stage_budget);
//                                    client.updatBudget(stage_budget);
//                                } catch (TException e) {
//                                    e.printStackTrace();
//                                }
//                            }
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
}
