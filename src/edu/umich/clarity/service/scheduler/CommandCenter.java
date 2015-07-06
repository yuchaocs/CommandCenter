package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.PowerModel;
import edu.umich.clarity.service.util.ServiceInstance;
import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.analysis.function.Pow;
import org.apache.commons.math3.analysis.function.Power;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class CommandCenter implements SchedulerService.Iface {


    private static final double GLOBAL_POWER_BUDGET = 80.0;
    private static final String NODE_MANAGER_IP = "clarity28.eecs.umich.edu";
    private static final int NODE_MANAGER_PORT = 8060;
    private static final Logger LOG = Logger.getLogger(CommandCenter.class);
    private static final String SCHEDULER_IP = "localhost";
    private static final int SCHEDULER_PORT = 8888;
    private static final int BUDGET_ADJUST_INTERVAL = 100;
    private static final int RELINQUISH_ADJUST_INTERVAL = 150;
    private static final long LATENCY_BUDGET = 300;
    // TODO may need to DAG structure to store the application workflow
    private static final List<String> sirius_workflow = new LinkedList<String>();
    private static final String[] LATENCY_FILE_HEADER = {"asr_queuing", "asr_serving", "asr_instance", "imm_queuing", "imm_serving", "imm_instance", "qa_queuing", "qa_serving", "qa_instance", "total_queuing", "total_serving"};
    private static final String[] QUEUE_FILE_HEADER = {"service_name", "host", "port", "queue_length"};
    private static final double LATENCY_PERCENTILE = 0.99;
    private static final int POLLING_INTERVAL = 1000;
    private static final double ADJUST_THRESHOLD = 1000;
    private static ConcurrentMap<String, List<ServiceInstance>> serviceMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static ConcurrentMap<String, List<ServiceInstance>> candidateMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static ConcurrentMap<String, Double> budgetMap = new ConcurrentHashMap<String, Double>();
    private static CSVWriter latencyWriter = null;
    private static CSVWriter queueWriter = null;
    private static AtomicReference<Double> POWER_BUDGET = new AtomicReference<Double>();
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();
    private static final double DEFAULT_FREQUENCY = 1.2;

    private static List<Integer> candidatePortList = new ArrayList<Integer>();

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        CommandCenter commandCenter = new CommandCenter();
        SchedulerService.Processor<SchedulerService.Iface> processor = new SchedulerService.Processor<SchedulerService.Iface>(
                commandCenter);
        TServers.launchSingleThreadThriftServer(SCHEDULER_PORT, processor);
        LOG.info("starting command center at " + SCHEDULER_IP + ":"
                + SCHEDULER_PORT);
        commandCenter.initialize();
    }

    /**
     *
     */
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
        for (int i = 9075; i < 9080; i++) {
            candidatePortList.add(i);
        }
        for (int i = 9085; i < 9090; i++) {
            candidatePortList.add(i);
        }
        for (int i = 9095; i < 9100; i++) {
            candidatePortList.add(i);
        }
        POWER_BUDGET.set(GLOBAL_POWER_BUDGET);
        LOG.info("current workflow within command center is " + workflow);
        //LOG.info("launching the power budget managing thread with adjusting interval per " + BUDGET_ADJUST_INTERVAL + " queries and recycling interval per " + RELINQUISH_ADJUST_INTERVAL + " queries");
        new Thread(new powerBudgetAdjustRunnable()).start();
        // new Thread(new budgetAdjusterRunnable()).start();
        // new Thread(new powerBudgetAdjustRunnable()).start();
    }

    /**
     * @param serviceType
     * @return
     * @throws TException
     */
    @Override
    public THostPort consultAddress(String serviceType) throws TException {
        LOG.info("receive consulting about service " + serviceType);
        THostPort hostPort = null;
        List<ServiceInstance> service_list = serviceMap.get(serviceType);
        if (service_list != null && service_list.size() != 0)
            // hostPort = randomAssignService(service_list);
            hostPort = loadBalanceAssignService(service_list);
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
        LOG.info("The load balance policy chooses the service instance " + hostPort.getIp() + ":" + hostPort.getPort() + " with probability " + service_list.get(i).getLoadProb() / totalProb);
        return hostPort;
    }

    /**
     * @param message
     * @throws TException
     */
    @Override
    public void registerBackend(RegMessage message) throws TException {
        String appName = message.getApp_name();
        THostPort hostPort = message.getEndpoint();
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setHostPort(hostPort);
        serviceInstance.setServiceType(appName);
        serviceInstance.setCurrentFrequncy(message.getBudget());
        // regular instances, allow to register
        if (!candidatePortList.contains(hostPort.getPort())) {
            if (serviceMap.containsKey(appName)) {
                serviceMap.get(appName).add(serviceInstance);
            } else {
                List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
                serviceMap.put(appName, serviceInstanceList);
            }
            POWER_BUDGET.set(POWER_BUDGET.get() - PowerModel.getPowerPerFreq(message.getBudget()));
            LOG.info("putting it into the live instance list (current size: " + serviceMap.size() + ")");
            LOG.info("the current available power budget is " + POWER_BUDGET.get());
        } else { // candidate instances, put into the candidate list
            if (candidateMap.containsKey(appName)) {
                candidateMap.get(appName).add(serviceInstance);
            } else {
                List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
                candidateMap.put(appName, serviceInstanceList);
            }
            LOG.info("putting it into the candidate instance list (current size: " + candidateMap.size() + ")");
        }
    }

    /**
     * @param query
     * @throws TException
     */
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
                csvEntry.add("" + latencySpec.getInstance_id());
                LOG.info("Query: queuing time " + queuing_time
                        + "ms," + " serving time " + serving_time + "ms" + " running on " + latencySpec.getInstance_id());
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

    /**
     *
     */
    private class powerBudgetAdjustRunnable implements Runnable {
        private long removed_queries = 0;

        /**
         *
         */
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
                            if (instanceIp.equalsIgnoreCase(host) && instancePort == new Integer(port).intValue()) {
                                instance.getServing_latency().add(new Double(serving_time));
                                instance.getQueuing_latency().add(new Double(queuing_time));
                            }
                        }
                    }
                    removed_queries += 1;
                    if (removed_queries % BUDGET_ADJUST_INTERVAL == 0) {
                        LOG.info("adjust the power budget...");
                        adjustPowerBudget();
                        LOG.info("load balancing the future queries...");
                        loadBalance();
                    }
                    if (removed_queries % RELINQUISH_ADJUST_INTERVAL == 0) {
                        LOG.info("start to recycle the service instances...");
                        relinquishServiceInstance();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Every RELINQUISH_ADJUST_INTERVAL queries, if the queuing time of a service instance keeps zero and there are multiple instances of that service type available, stop one service instance and relinquish the power budget.
         */
        /**
         *
         */
        private void relinquishServiceInstance() {
            LOG.info("scanning the queuing time of the past " + RELINQUISH_ADJUST_INTERVAL + " queries");
            Map<String, List<Integer>> relinquishMap = new HashMap<String, List<Integer>>();
            for (String serviceType : serviceMap.keySet()) {
                relinquishMap.put(serviceType, new LinkedList<Integer>());
                List<ServiceInstance> serviceInstanceList = serviceMap.get(serviceType);
                if (serviceInstanceList.size() > 1) {
                    for (int i = 0; i < serviceInstanceList.size(); i++) {
                        ServiceInstance serviceInstance = serviceInstanceList.get(i);
                        double queuingTime = 0;
                        int latencyLength = serviceInstance.getQueuing_latency().size() - 1;
                        for (int index = 0; index < RELINQUISH_ADJUST_INTERVAL; index++) {
                            queuingTime += serviceInstance.getQueuing_latency().get(latencyLength - index);
                            if (queuingTime > 0) {
                                break;
                            }
                        }
                        if (queuingTime == 0) {
                            relinquishMap.get(serviceType).add(i);
                        }
                    }
                }
            }
            // shutdown the service instance and relinquish the gobal power budget
            for (String serviceType : relinquishMap.keySet()) {
                List<Integer> relinquishIndexList = relinquishMap.get(serviceType);
                if (relinquishIndexList.size() != 0) {
                    List<ServiceInstance> serviceInstanceList = serviceMap.get(serviceType);
                    for (Integer index : relinquishIndexList) {
                        ServiceInstance instance = serviceInstanceList.get(index);
                        double allocatedFreq = instance.getCurrentFrequncy();
                        POWER_BUDGET.set(POWER_BUDGET.get() + PowerModel.getPowerPerFreq(allocatedFreq));
                        serviceInstanceList.remove(index);
                        // instead of shutting down, put the recycled service instance into the candidate list
                        instance.getQueuing_latency().clear();
                        instance.getServing_latency().clear();
                        candidateMap.get(serviceType).add(instance);
                        LOG.info("recycling the service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " and the power budget recycled is " + PowerModel.getPowerPerFreq(allocatedFreq));
                        LOG.info("the current global power budget is " + POWER_BUDGET.get());
                        if (serviceInstanceList.size() == 1) {
                            break;
                        }
                    }
                } else {
                    LOG.info("no service instance can be recycled, wait for the next recycle interval");
                }
            }
        }

        /**
         *
         */
        private void adjustPowerBudget() {
            /**
             * TODO two approaches can be applied: 1. whenever there are enough power budget left, launch a new service instance of the one that has the longest estimated finish time; 2. if no power budget left, lower the frequency of the fastest service instance in order to increase the frequency of the slowest service instance.
             */
            // 1. pull real time queue length from each of the service instance
            // 2. estimate the serving time based on the queue length as well as the historical 99th percentile serving time
            LOG.info("estimating the serving time of each service instance based on the real time queue length and 99th serving time distrition");
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            Percentile percentile = new Percentile();
            for (String serviceType : serviceMap.keySet()) {
                for (ServiceInstance instance : serviceMap.get(serviceType)) {
                    try {
                        IPAService.Client client = TClient.creatIPAClient(instance.getHostPort().getIp(), instance.getHostPort().getPort());
                        instance.setCurrentQueueLength(client.reportQueueLength());
                        LOG.info("the real time queue length of service instance  " + serviceType + " running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " is " + instance.getCurrentQueueLength());
                        List<Double> latencyStatistic = instance.getServing_latency();
                        double percentileValue = 0;
                        if (latencyStatistic.size() == 0) {
                            percentileValue = instance.getServingTimePercentile();
                        } else {
                            double[] evaluateArray = new double[latencyStatistic.size()];
                            for (int i = 0; i < latencyStatistic.size(); i++) {
                                evaluateArray[i] = latencyStatistic.get(i).doubleValue();
                            }
                            percentileValue = percentile.evaluate(evaluateArray, LATENCY_PERCENTILE);
                            instance.setServingTimePercentile(percentileValue);
                        }
                        double estimatedServingTime = percentileValue * instance.getCurrentQueueLength();
                        instance.setEstimatedServingTime(estimatedServingTime);
                        LOG.info("the 99th serving time and estimated finish time is " + percentileValue + " and " + estimatedServingTime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (TException e) {
                        e.printStackTrace();
                    }
                }
                serviceInstanceList.addAll(serviceMap.get(serviceType));
            }
            // 3. sort the service instance based on the estimated serving time
            Collections.sort(serviceInstanceList, new ServingTimeComparator());
            relocatePowerBudget(serviceInstanceList);
        }

        /**
         * Recursively relocating the power budget across the service instances.
         *
         * @param serviceInstanceList
         */
        private void relocatePowerBudget(List<ServiceInstance> serviceInstanceList) {
            LOG.info("start ti relocate the power budget...");
            if (serviceInstanceList.size() == 1) {
                ServiceInstance instance = serviceInstanceList.get(0);
                if (POWER_BUDGET.get() > PowerModel.getPowerPerFreq(PowerModel.F0)) {
                    launchServiceInstance(instance.getServiceType(), instance.getQueuingTimePercentile(), DEFAULT_FREQUENCY);
                } else {
                    if (instance.getCurrentFrequncy() != PowerModel.FN) {
                        double currentFreq = instance.getCurrentFrequncy();
                        double nextLevelPower = PowerModel.getPowerPerFreq(currentFreq + 0.1);
                        if (nextLevelPower < POWER_BUDGET.get()) {
                            IPAService.Client client = null;
                            try {
                                client = TClient.creatIPAClient(instance.getHostPort().getIp(), instance.getHostPort().getPort());
                                client.updatBudget(currentFreq + 0.1);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (TException e) {
                                e.printStackTrace();
                            }
                            instance.setCurrentFrequncy(currentFreq + 0.1);
                            POWER_BUDGET.set(POWER_BUDGET.get() - nextLevelPower);
                            LOG.info("update service " + instance.getServiceType() + " instance@" + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " frequency to " + instance.getServiceType());
                            LOG.info("current global available power budget is " + POWER_BUDGET.get());
                        }
                    }
                }
            } else {
                double longestServingTime = serviceInstanceList.get(0).getEstimatedServingTime();
                double shortestServingTime = serviceInstanceList.get(serviceInstanceList.size() - 1).getEstimatedServingTime();
                if ((longestServingTime - shortestServingTime) < ADJUST_THRESHOLD) { // if the serving time variation is within the adjust threshold, then skip current adjust interval
                    LOG.info("the longest estimated serving time is " + longestServingTime + " and the shortest estimated serving time is " + shortestServingTime);
                    LOG.info("the difference is less than the ajust threshold " + ADJUST_THRESHOLD + ", skip current power budget adjust interval");
                } else {
                    // 4.whenever there are enough power budget left, launch a new instance of the slowest service
                    if (POWER_BUDGET.get() > PowerModel.getPowerPerFreq(PowerModel.F0)) {
                        LOG.info("the current global power budget " + POWER_BUDGET.get() + " is enough to launch a new service instance");
                        launchServiceInstance(serviceInstanceList.get(0).getServiceType(), serviceInstanceList.get(0).getQueuingTimePercentile(), DEFAULT_FREQUENCY);
                    } else { // 5. recycle the power budget from the fastest service instance and rellocate to the slowest service instance. The power budget should be enough to allow one level frequency increase.
                        LOG.info("the current global power budget " + POWER_BUDGET.get() + " is not enough to launch a new service instance, " + "trying to recycle the power budget...");
                        ServiceInstance instance = serviceInstanceList.get(0);
                        double currentFreq = instance.getCurrentFrequncy();
                        double requiredPower = PowerModel.getPowerPerFreq(currentFreq + 0.1) - PowerModel.getPowerPerFreq(currentFreq);
                        LOG.info("trying to increase the frequency of the slowest service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort());
                        LOG.info("the required power is " + requiredPower);
                        if (currentFreq == PowerModel.FN) {
                            serviceInstanceList.remove(0);
                            relocatePowerBudget(serviceInstanceList);
                        } else {
                            Map<ServiceInstance, Double> adjustInstance = new HashMap<ServiceInstance, Double>();
                            if (requiredPower <= POWER_BUDGET.get()) {
                                adjustInstance.put(instance, currentFreq + 0.1);
                                POWER_BUDGET.set(POWER_BUDGET.get() - requiredPower);
                            } else {
                                double sum = 0;
                                for (int i = (serviceInstanceList.size() - 1); i > 0; i--) {
                                    ServiceInstance fastestInstance = serviceInstanceList.get(i);
                                    double fastFreq = fastestInstance.getCurrentFrequncy();
                                    double fastPower = PowerModel.getPowerPerFreq(fastFreq);
                                    for (double j = (fastFreq - 0.1); j > (PowerModel.F0 - 0.1); j -= 0.1) {
                                        if (sum + (fastPower - PowerModel.getPowerPerFreq(j) + POWER_BUDGET.get()) >= requiredPower) {
                                            sum += fastPower - PowerModel.getPowerPerFreq(j);
                                            adjustInstance.put(fastestInstance, j);
                                            break;
                                        }
                                        if (j == PowerModel.F0) {
                                            sum += fastPower - PowerModel.getPowerPerFreq(j);
                                            adjustInstance.put(fastestInstance, PowerModel.F0);
                                        }
                                    }
                                    if ((sum + POWER_BUDGET.get()) >= requiredPower) {
                                        break;
                                    }
                                }
                                if ((sum + POWER_BUDGET.get()) >= requiredPower) {
                                    if (sum < requiredPower) {
                                        POWER_BUDGET.set(POWER_BUDGET.get() - (requiredPower - sum));
                                    }
                                } else {
                                    LOG.info("not enough power budget to recycle, keep the current power budget unadjusted...");
                                    adjustInstance.clear();
                                }
                            }
                            // update the global serviceMap and notify the service instance to update their power budget
                            for (ServiceInstance
                                    keyInstance : adjustInstance.keySet()) {
                                List<ServiceInstance> valueList = serviceMap.get(keyInstance.getServiceType());
                                for (ServiceInstance value : valueList) {
                                    if (value.getHostPort().equals(keyInstance.getHostPort())) {
                                        value.setCurrentFrequncy(adjustInstance.get(keyInstance));
                                        try {
                                            IPAService.Client client = TClient.creatIPAClient(keyInstance.getHostPort().getIp(), keyInstance.getHostPort().getPort());
                                            client.updatBudget(adjustInstance.get(keyInstance));
                                            LOG.info("the frequency of service instance running on " + keyInstance.getHostPort().getIp() + ":" + keyInstance.getHostPort().getPort() + " has been decreased to " + adjustInstance.get(keyInstance));
                                        } catch (IOException ex) {
                                            ex.printStackTrace();
                                        } catch (TException ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Invoke the node manager service to launch new service instances.
         *
         * @param serviceType
         * @param queuingTimePercentile
         * @param defaultFrequency
         */
        private void launchServiceInstance(String serviceType, double queuingTimePercentile, double defaultFrequency) {
//            ServiceInstance instance = new ServiceInstance();
//            instance.setServiceType(serviceType);
//            instance.setQueuingTimePercentile(queuingTimePercentile);
//            instance.setCurrentFrequncy(defaultFrequency);
//            NodeManagerService.Client client = null;
//            THostPort hostPort = null;
//            try {
//                client = TClient.creatNodeManagerClient(NODE_MANAGER_IP, NODE_MANAGER_PORT);
//                hostPort = client.launchServiceInstance(serviceType, defaultFrequency);
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            } catch (TException ex) {
//                ex.printStackTrace();
//            }
//            if (hostPort != null) {
//                POWER_BUDGET -= PowerModel.getPowerPerFreq(defaultFrequency);
//                instance.setHostPort(hostPort);
//                serviceMap.get(serviceType).add(instance);
//            } else {
//                LOG.info("The node manager has run out of service instance " + serviceType);
//            }
            if (candidateMap.get(serviceType).size() != 0) {
                List<ServiceInstance> instanceList = candidateMap.get(serviceType);
                ServiceInstance instance = instanceList.get(0);
                instanceList.remove(0);
                instance.setServiceType(serviceType);
                instance.setQueuingTimePercentile(queuingTimePercentile);
                instance.setCurrentFrequncy(defaultFrequency);
                serviceMap.get(serviceType).add(instance);
                POWER_BUDGET.set(POWER_BUDGET.get() - PowerModel.getPowerPerFreq(defaultFrequency));
                LOG.info("Launching new service instance " + serviceType + " on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " with frequency " + instance.getCurrentFrequncy() + "GHz");
            } else {
                LOG.info("The node manager has run out of service instance " + serviceType);
            }
        }

        /**
         *
         */
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

    /**
     *
     */
    private class LoadProbabilityComparator implements Comparator<ServiceInstance> {
        /**
         * @param instance1
         * @param instance2
         * @return
         */
        @Override
        public int compare(ServiceInstance instance1, ServiceInstance instance2) {
            return Double.compare(instance1.getLoadProb(), instance2.getLoadProb());
        }
    }

    /**
     *
     */
    private class ServingTimeComparator implements Comparator<ServiceInstance> {
        /**
         * @param instance1
         * @param instance2
         * @return
         */
        @Override
        public int compare(ServiceInstance instance1, ServiceInstance instance2) {
            return Double.compare(instance1.getEstimatedServingTime(), instance2.getEstimatedServingTime());
        }
    }
}
