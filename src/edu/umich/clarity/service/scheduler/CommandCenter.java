package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.PowerModel;
import edu.umich.clarity.service.util.ServiceInstance;
import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
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
    private static final String[] LATENCY_FILE_HEADER = {"asr_queuing", "asr_serving", "imm_queuing", "imm_serving", "qa_queuing", "qa_serving", "total_queuing", "total_serving"};
    private static final String[] QUEUE_FILE_HEADER = {"service_name", "host", "port", "queue_length"};
    private static final double LATENCY_PERCENTILE = 0.99;
    private static final int POLLING_INTERVAL = 1000;
    private static final double ADJUST_THRESHOLD = 1000;
    private static ConcurrentMap<String, List<ServiceInstance>> serviceMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static ConcurrentMap<String, Double> budgetMap = new ConcurrentHashMap<String, Double>();
    private static CSVWriter latencyWriter = null;
    private static CSVWriter queueWriter = null;
    private static volatile double POWER_BUDGET = 80;
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
            latencyWriter = new CSVWriter(new FileWriter("query_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            latencyWriter.writeNext(LATENCY_FILE_HEADER);
            latencyWriter.flush();
            queueWriter = new CSVWriter(new FileWriter("queue_length.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            queueWriter.writeNext(QUEUE_FILE_HEADER);
            queueWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("current workflow within command center is " + workflow);
        new Thread(new powerBudgetAdjustRunnable()).start();
        // new Thread(new budgetAdjusterRunnable()).start();
        // new Thread(new powerBudgetAdjustRunnable()).start();
    }

    @Override
    public THostPort consultAddress(String serviceType) throws TException {
        THostPort hostPort = null;
        List<ServiceInstance> service_list = serviceMap.get(serviceType);
        if (service_list != null && service_list.size() != 0)
            // hostPort = randomAssignService(service_list);
            hostPort = loadBalanceAssignService(service_list);
        LOG.info("receive consulting about service " + serviceType + " and returning " + hostPort.getIp() + ":" + hostPort.getPort());
        return hostPort;
    }

    /**
     * Randomly choose a service of the required type.
     *
     * @param service_list the service candidates
     * @return the chosen service
     */
    private THostPort randomAssignService(List<ServiceInstance> service_list) {
        THostPort hostPort;
        Random rand = new Random();
        hostPort = service_list.get(rand.nextInt(service_list.size())).getHostPort();
        return hostPort;
    }

    /**
     * Based on the 99th percentile queuing time distribution of each service instance to balance the queue length.
     *
     * @param service_list the candidate list of particular service type
     * @return the chosen service instance
     */
    private THostPort loadBalanceAssignService(List<ServiceInstance> service_list) {
        THostPort hostPort;
        Collections.sort(service_list, new LoadProbabilityComparator());

        double totalProb = 0;
        for (ServiceInstance instance : service_list) {
            totalProb += instance.getLoadProb();
        }

        Random rand = new Random();
        double index = rand.nextDouble() * totalProb;

        int i = 0;
        totalProb = 0;
        for (ServiceInstance instance : service_list) {
            totalProb += instance.getLoadProb();
            if (totalProb > index) {
                break;
            }
            i++;
        }
        hostPort = service_list.get(i).getHostPort();
        return hostPort;
    }

    @Override
    public void registerBackend(RegMessage message) throws TException {
        String appName = message.getApp_name();
        THostPort hostPort = message.getEndpoint();
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        if (serviceMap.containsKey(appName)) {
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setHostPort(hostPort);
            serviceInstance.setServiceType(appName);
            serviceMap.get(appName).add(serviceInstance);
        } else {
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setHostPort(hostPort);
            serviceInstance.setServiceType(appName);
            serviceInstanceList.add(serviceInstance);
            serviceMap.put(appName, serviceInstanceList);
        }
        POWER_BUDGET -= PowerModel.getPowerPerFreq(message.getBudget());
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
            for (int i = 0; i < query.getTimestamp().size(); i++) {
                LatencySpec latencySpec = query.getTimestamp().get(i);
                long queuing_time = latencySpec.getServing_start_time() - latencySpec.getQueuing_start_time();
                total_queuing += queuing_time;
                long serving_time = latencySpec.getServing_end_time() - latencySpec.getServing_start_time();
                total_serving += serving_time;
                csvEntry.add("" + queuing_time);
                csvEntry.add("" + serving_time);
                LOG.info("Query: queuing time " + queuing_time
                        + "ms," + " serving time " + serving_time + "ms" + " @stage "
                        + sirius_workflow.get(i));
            }
            LOG.info("Query: total queuing "
                    + total_queuing + "ms" + " total serving " + total_serving
                    + "ms" + " at all stages with total latency "
                    + (total_queuing + total_serving) + "ms");
            csvEntry.add("" + total_queuing);
            csvEntry.add("" + total_serving);
            latencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
            latencyWriter.flush();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class powerBudgetAdjustRunnable implements Runnable {
        private long removed_queries = 0;

        @Override
        public void run() {
            LOG.info("starting the helper thread for adjusting power budget across stages");
            while (true) {
                try {
                    QuerySpec query = finishedQueryQueue.take();
                    for (int i = 0; i < query.getTimestamp().size(); i++) {
                        LatencySpec latencySpec = query.getTimestamp().get(i);
                        long queuing_time = latencySpec.getServing_start_time() - latencySpec.getQueuing_start_time();
                        long serving_time = latencySpec.getServing_end_time() - latencySpec.getServing_start_time();
                        String serviceType = latencySpec.getInstance_id().split("_")[0];
                        String host = latencySpec.getInstance_id().split("_")[1];
                        String port = latencySpec.getInstance_id().split("_")[2];
                        for (ServiceInstance instance : serviceMap.get(serviceType)) {
                            String instanceIp = instance.getHostPort().getIp();
                            int instancePort = instance.getHostPort().getPort();
                            if (instanceIp.equalsIgnoreCase(host) && instancePort == new Integer(port)) {
                                instance.getServing_latency().add(new Double(serving_time));
                                instance.getQueuing_latency().add(new Double(queuing_time));
                            }
                        }
                    }
                    removed_queries += 1;
                    if (removed_queries % QUERY_INTERVAL == 0) {
                        adjustPowerBudget();
                        loadBalance();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void adjustPowerBudget() {
            /**
             * TODO two approaches can be applied: 1. whenever there are enough power budget left, launch a new service instance of the one that has the longest estimated finish time; 2. if no power budget left, lower the frequency of the fastest service instance in order to increase the frequency of the slowest service instance.
             */
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            for (String serviceType : serviceMap.keySet()) {
                for (ServiceInstance instance : serviceMap.get(serviceType)) {
                    try {
                        IPAService.Client client = TClient.creatIPAClient(instance.getHostPort().getIp(), instance.getHostPort().getPort());
                        instance.setCurrentQueueLength(client.reportQueueLength());
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (TException e) {
                        e.printStackTrace();
                    }
                }
                serviceInstanceList.addAll(serviceMap.get(serviceType));
            }
        }

        private void loadBalance() {
            Percentile percentile = new Percentile();
            for (String serviceType : serviceMap.keySet()) {
                List<Double> speedList = new LinkedList<Double>();
                double totalSpeed = 0;
                List<ServiceInstance> serviceInstancesList = serviceMap.get(serviceType);
                for (int i = 0; i < serviceInstancesList.size(); i++) {
                    ServiceInstance instance = serviceInstancesList.get(i);
                    List<Double> queuingStatistic = instance.getQueuing_latency();
                    double[] evaluateArray = new double[queuingStatistic.size()];
                    for (int j = 0; j < queuingStatistic.size(); j++) {
                        evaluateArray[j] = queuingStatistic.get(j).doubleValue();
                    }
                    double percentileValue = percentile.evaluate(evaluateArray, LATENCY_PERCENTILE);
                    double preProbability = instance.getLoadProb();
                    double preSpeed = preProbability / percentileValue;
                    speedList.add(preSpeed);
                    totalSpeed += preSpeed;
                }
                for (int i = 0; i < serviceInstancesList.size(); i++) {
                    ServiceInstance instance = serviceInstancesList.get(i);
                    instance.setLoadProb((1.0 / totalSpeed) / (1.0 / speedList.get(i)));
                }
            }
        }
    }

    private class LoadProbabilityComparator implements Comparator<ServiceInstance> {
        @Override
        public int compare(ServiceInstance instance1, ServiceInstance instance2) {
            return Double.compare(instance1.getLoadProb(), instance2.getLoadProb());
        }
    }

    private class ServingTimeComparator implements Comparator<ServiceInstance> {
        @Override
        public int compare(ServiceInstance instance1, ServiceInstance instance2) {

            return Double.compare(instance1.getLoadProb(), instance2.getLoadProb());
        }
    }

    /**
     * TODO command center need a new service running on each node to launch additional service instance when necessary
     */
}
