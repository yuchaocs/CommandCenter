package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.*;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.thrift.TException;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class CommandCenter implements SchedulerService.Iface {

    public static final String LATENCY_TYPE = "average";
    // save for future use
    // private static final String NODE_MANAGER_IP = "clarity28.eecs.umich.edu";
    // private static final int NODE_MANAGER_PORT = 8060;
    private static final Logger LOG = Logger.getLogger(CommandCenter.class);
    // best effort or guarantee
    private static final long LATENCY_BUDGET = 100;
    private static final List<String> sirius_workflow = new LinkedList<String>();
    // headers for the CSV result files
    private static final String[] TOTAL_LATENCY_FILE_HEADER = {"query_id", "total_queuing", "total_serving"};
    private static final String[] SERVICE_LATENCY_FILE_HEADER = {"query_id", "asr_queuing", "asr_serving", "asr_instance", "imm_queuing", "imm_serving", "imm_instance", "qa_queuing", "qa_serving", "qa_instance"};
    private static final String[] FREQUENCY_FILE_HEADER = {"adjust_id", "service_instance", "timestamp", "frequency"};
    private static final String[] EXPECTED_DELAY_FILE_HEADER = {"time", "service_instance", "expected_delay"};
    private static final String[] PEGASUS_POWER_FILE_HEADER = {"adjust_id", "global_power"};
    private static final double DEFAULT_FREQUENCY = 1.8;
    private static final int MINIMUM_QUEUE_LENGTH = 3;
    //public static boolean VANILLA_MODE = false;
    public static boolean VANILLA_MODE;
    //private static double GLOBAL_POWER_BUDGET = 9.48 * 3;
    private static double GLOBAL_POWER_BUDGET;
    //private static int SCHEDULER_PORT = 8888;
    private static int SCHEDULER_PORT;
    // the interval to adjust power budget (per queries)
    //private static int ADJUST_BUDGET_INTERVAL = 50;
    private static int ADJUST_BUDGET_INTERVAL;
    // the interval to withdraw the idle service instances (per queries)
    //private static int WITHDRAW_BUDGET_INTERVAL = ADJUST_BUDGET_INTERVAL * 3;
    private static int WITHDRAW_BUDGET_INTERVAL;
    // the number of queries to warm up the services
    //private static int WARMUP_COUNT = 20;
    private static int WARMUP_COUNT;
    private static AtomicInteger warmupCount = new AtomicInteger(0);
    // latency threshold between instances before stopping power adjustment
    //private static double ADJUST_THRESHOLD = 1000;
    private static double ADJUST_THRESHOLD;
    // the tail latency target
    //private static double LATENCY_PERCENTILE = 99;
    private static double LATENCY_PERCENTILE;
    private static ConcurrentMap<String, List<ServiceInstance>> serviceMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static Map<String, Double> frequencyStat = new HashMap<String, Double>();
    private static ConcurrentMap<String, List<ServiceInstance>> candidateMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static CSVWriter latencyWriter = null;
    private static CSVWriter serviceLatencyWriter = null;
    private static CSVWriter frequencyWriter = null;
    private static CSVWriter expectedDelayWriter = null;
    private static CSVWriter pegasusPowerWriter = null;
    private static CSVReader speedupReader = null;
    private static AtomicReference<Double> POWER_BUDGET = new AtomicReference<Double>();
    private static List<Integer> candidatePortList = new ArrayList<Integer>();
    private static Map<String, Map<Double, Double>> speedupSheet = new HashMap<String, Map<Double, Double>>();
    private static List<Double> freqRangeList = new LinkedList<Double>();
    //private static String BOOSTING_DECISION = BoostDecision.ADAPTIVE_BOOST;
    private static String BOOSTING_DECISION;
    private static long initialAdjustTimestamp;
    //private static boolean WITHDRAW_SERVICE_INSTANCE = true;
    private static boolean WITHDRAW_SERVICE_INSTANCE;
    private static int ADAPTIVE_ADJUST_ROUND = 0;
    // private static boolean WITHDRAW_SERVICE_INSTANCE = false;
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();

    // pegasus
    private static List<Long> end2endQueryLatency = new LinkedList<Long>();
    //private static double qosTarget = 21.0;
    private static double qosTarget;
    private static final double MAX_PACKAGE_POWER = 32.5 / 0.125;
    private static double currentPackagePower = 27 / 0.125;
    private static int waitRound = 0;

    public CommandCenter() {
        PropertyConfigurator.configure(System.getProperty("user.dir") + File.separator + "log4j.properties");
    }

    /**
     * @param args args[0]: port, args[1]: adjust_interval, args[2]: withdraw_interval, args[3]: warm_up_account, args[4]: adjust_threshold, args[5]: tail_percentile, args[6]: global_power_budget, args[7]: execution_mode
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        CommandCenter commandCenter = new CommandCenter();
        if (args.length == 11) {
            SCHEDULER_PORT = Integer.valueOf(args[0]);
            ADJUST_BUDGET_INTERVAL = Integer.valueOf(args[1]);
            WITHDRAW_BUDGET_INTERVAL = Integer.valueOf(args[2]);
            WARMUP_COUNT = Integer.valueOf(args[3]);
            ADJUST_THRESHOLD = Double.valueOf(args[4]);
            LATENCY_PERCENTILE = Double.valueOf(args[5]);
            GLOBAL_POWER_BUDGET = Double.valueOf(args[6]);
            if (args[7].equalsIgnoreCase("vanilla")) {
                VANILLA_MODE = true;
            } else if (args[7].equalsIgnoreCase("recycle")) {
                VANILLA_MODE = false;
            }
            BOOSTING_DECISION = args[8];
            if (args[9].equalsIgnoreCase("withdraw")) {
                WITHDRAW_SERVICE_INSTANCE = true;
            } else if (args[9].equalsIgnoreCase("no-withdraw")) {
                WITHDRAW_SERVICE_INSTANCE = false;
            }
            qosTarget = Double.valueOf(args[10]);
            LOG.info("launching the command center with " + args[7] + " mode");
            LOG.info("global power budget: " + GLOBAL_POWER_BUDGET + ", adjust interval: " + ADJUST_BUDGET_INTERVAL + ", adjust threshold: " + ADJUST_THRESHOLD + ", latency percentile: " + LATENCY_PERCENTILE);
            LOG.info("boosting policy: " + BOOSTING_DECISION + ", withdraw mode: " + args[9] + ", withdraw interval " + WITHDRAW_BUDGET_INTERVAL);
        }
        SchedulerService.Processor<SchedulerService.Iface> processor = new SchedulerService.Processor<SchedulerService.Iface>(
                commandCenter);
        TServers.launchSingleThreadThriftServer(SCHEDULER_PORT, processor);
        LOG.info("starting command center at port " + SCHEDULER_PORT);
        commandCenter.initialize();
    }

    /**
     *
     */
    public void initialize() {
        DecimalFormat dFormat = new DecimalFormat("#.#");
        for (double i = 1.2; i < 2.5; i += 0.1) {
            freqRangeList.add(Double.valueOf((dFormat.format(i))));
        }

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
            speedupReader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator + "freq.csv"), ',', '\n', 1);
            latencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "query_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            serviceLatencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "service_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            frequencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "frequency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            expectedDelayWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "expected_delay.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            pegasusPowerWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "pegasus_power.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            latencyWriter.writeNext(TOTAL_LATENCY_FILE_HEADER);
            latencyWriter.flush();
            frequencyWriter.writeNext(FREQUENCY_FILE_HEADER);
            frequencyWriter.flush();
            expectedDelayWriter.writeNext(EXPECTED_DELAY_FILE_HEADER);
            expectedDelayWriter.flush();
            pegasusPowerWriter.writeNext(PEGASUS_POWER_FILE_HEADER);
            pegasusPowerWriter.flush();
            serviceLatencyWriter.writeNext(SERVICE_LATENCY_FILE_HEADER);
            serviceLatencyWriter.flush();
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

        // build the speedup sheet
        String[] nextLine;
        int index = 0;
        try {
            while ((nextLine = speedupReader.readNext()) != null) {
                Map<Double, Double> speedup = new HashMap<Double, Double>();
                for (int i = 0; i < nextLine.length; i++) {
                    speedup.put(freqRangeList.get(i), new Double(nextLine[i]));
                }
                speedupSheet.put(sirius_workflow.get(index), speedup);
                index++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

//        for (String serviceType : speedupSheet.keySet()) {
//            System.out.println(serviceType + ":" + speedupSheet.get(serviceType).get(FREQ_RANGE[5]));
//        }

        LOG.info("the global power budget is set to " + POWER_BUDGET.get().doubleValue());
        LOG.info("current workflow within command center is " + workflow);
        //LOG.info("launching the power budget managing thread with adjusting interval per " + ADJUST_BUDGET_INTERVAL + " queries and recycling interval per " + WITHDRAW_BUDGET_INTERVAL + " queries");
        if (!VANILLA_MODE)
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
        // LOG.info("receive consulting about service " + serviceType);
        THostPort hostPort = null;
        List<ServiceInstance> service_list = serviceMap.get(serviceType);
        if (service_list != null && service_list.size() != 0)
            if (!VANILLA_MODE)
                hostPort = loadBalanceAssignService(service_list);
            else
                hostPort = randomAssignService(service_list);
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
     * The load is distributed according to the probability of each service instance
     *
     * @param service_list the candidate list of particular service type
     * @return the chosen service instance
     */
    private THostPort loadBalanceAssignService(List<ServiceInstance> service_list) {
        THostPort hostPort = null;
        // Collections.sort(service_list, new LoadProbabilityComparator());
        int scale = 10000;
        List<Integer> thresHold = new LinkedList<Integer>();
        for (int i = 0; i < service_list.size(); i++) {
            ServiceInstance instance = service_list.get(i);
            int sum = 0;
            for (int j = 0; j < i + 1; j++) {
                sum += service_list.get(j).getLoadProb() * scale;
            }
            thresHold.add(sum);
        }

        Random rand = new Random();
        int index = rand.nextInt(scale);

        for (int i = 0; i < thresHold.size(); i++) {
            if (index <= thresHold.get(i)) {
                hostPort = service_list.get(i).getHostPort();
                break;
            }
        }
        // in case the double value doesn't add up to 1
        if (index > thresHold.get(thresHold.size() - 1)) {
            hostPort = service_list.get(thresHold.size() - 1).getHostPort();
        }
        // LOG.info("The load balance policy chooses the service instance " + hostPort.getIp() + ":" + hostPort.getPort() + " with probability " + service_list.get(i).getLoadProb() / totalProb);
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
        DecimalFormat dFormat = new DecimalFormat("#.#");
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setHostPort(hostPort);
        serviceInstance.setServiceType(appName);
        serviceInstance.setCurrentFrequncy(Double.valueOf(dFormat.format(message.getBudget())));
        synchronized (this) {
            // regular instances, allow to register
            if (!candidatePortList.contains(hostPort.getPort())) {
                if (serviceMap.containsKey(appName)) {
                    double loadProb = 1 / (serviceMap.get(appName).size() + 1);
                    serviceMap.get(appName).add(serviceInstance);
                    for (ServiceInstance instance : serviceMap.get(appName)) {
                        instance.setLoadProb(loadProb);
                    }
                } else {
                    // List<ServiceInstance> serviceInstanceList = new CopyOnWriteArrayList<ServiceInstance>();
                    List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
                    serviceInstanceList.add(serviceInstance);
                    serviceMap.put(appName, serviceInstanceList);
                }
                POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() - PowerModel.getPowerPerFreq(message.getBudget())));
                LOG.info("putting it into the live instance list (current size for " + appName + ": " + serviceMap.get(appName).size() + ")");
                LOG.info("the current available power budget is " + POWER_BUDGET.get().doubleValue());
            } else { // candidate instances, put into the candidate list
                if (candidateMap.containsKey(appName)) {
                    candidateMap.get(appName).add(serviceInstance);
                } else {
                    // List<ServiceInstance> serviceInstanceList = new CopyOnWriteArrayList<ServiceInstance>();
                    List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
                    serviceInstanceList.add(serviceInstance);
                    candidateMap.put(appName, serviceInstanceList);
                }
//                LOG.info("putting it into the candidate instance list (current size for " + appName + ": " + candidateMap.get(appName).size() + ")");
            }
        }
    }

    /**
     * @param query
     * @throws TException
     */
    @Override
    public void enqueueFinishedQuery(QuerySpec query) throws TException {
        try {
            if (warmupCount.incrementAndGet() > WARMUP_COUNT) {
                finishedQueryQueue.put(query);
                /**
                 * there are three timestamps for each stage, the first timestamp is
                 * when the query entering the queue, the second timestamp is when
                 * the query get served, the third one is when the serving iss done.
                 */
                ArrayList<String> csvEntry = new ArrayList<String>();
                ArrayList<String> serviceCSVEntry = new ArrayList<String>();
                long total_queuing = 0;
                long total_serving = 0;
                csvEntry.add(query.getName());
                serviceCSVEntry.add(query.getName());
                for (int i = 0; i < query.getTimestamp().size(); i++) {
                    LatencySpec latencySpec = query.getTimestamp().get(i);
                    long queuing_time = latencySpec.getServing_start_time() - latencySpec.getQueuing_start_time();
                    total_queuing += queuing_time;
                    long serving_time = latencySpec.getServing_end_time() - latencySpec.getServing_start_time();
                    total_serving += serving_time;
                    serviceCSVEntry.add("" + queuing_time);
                    serviceCSVEntry.add("" + serving_time);
                    serviceCSVEntry.add("" + latencySpec.getInstance_id());
//                    LOG.info("Query " + query.getName() + ": queuing time " + queuing_time
//                            + "ms," + " serving time " + serving_time + "ms" + " running on " + latencySpec.getInstance_id());
                }
//                LOG.info("Query " + query.getName() + ": total queuing "
//                        + total_queuing + "ms" + " total serving " + total_serving
//                        + "ms" + " at all stages with total latency "
//                        + (total_queuing + total_serving) + "ms");
                csvEntry.add("" + total_queuing);
                csvEntry.add("" + total_serving);
                serviceLatencyWriter.writeNext(serviceCSVEntry.toArray(new String[serviceCSVEntry.size()]));
                serviceLatencyWriter.flush();
                latencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                latencyWriter.flush();
            }
            if (warmupCount.get() == WARMUP_COUNT) {
                initialAdjustTimestamp = System.currentTimeMillis();
                LOG.info("starting to processing the queries at " + initialAdjustTimestamp);
                for (Map.Entry<String, List<ServiceInstance>> entry : serviceMap.entrySet()) {
                    for (ServiceInstance instance : entry.getValue()) {
                        String instanceId = instance.getServiceType() + "_" + instance.getHostPort().getIp() + "_" + instance.getHostPort().getPort();
                        frequencyStat.put(instanceId, instance.getCurrentFrequncy());
                        instance.setRenewTimestamp(initialAdjustTimestamp);
                    }
                }
            }
//                long initial_timestamp = System.currentTimeMillis();
//                // write the initial frequency setting of each service
//                for (Map.Entry<String, List<ServiceInstance>> entry : serviceMap.entrySet()) {
//                    for (ServiceInstance instance : entry.getValue()) {
//                        ArrayList<String> csvEntry = new ArrayList<String>();
//                        csvEntry.add("" + instance.getServiceType() + "_" + instance.getHostPort().getIp() + "_" + instance.getHostPort().getPort());
//                        csvEntry.add("" + initial_timestamp);
//                        csvEntry.add("" + instance.getCurrentFrequncy());
//                        frequencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
//                        frequencyWriter.flush();
//                    }
//                }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int warmupCount() throws TException {
        return warmupCount.get();
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
                    long totalLatency = 0;
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
                                instance.setQueriesBetweenWithdraw(instance.getQueriesBetweenWithdraw() + 1);
                                instance.setQueriesBetweenAdjust(instance.getQueriesBetweenAdjust() + 1);
                            }
                        }
                        totalLatency += queuing_time + serving_time;
                    }
                    end2endQueryLatency.add(totalLatency);
                    removed_queries += 1;
                    if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.PEGASUS_BOOST)) {
                        performPegasus(removed_queries);
                    } else {
                        //LOG.info(removed_queries + " query latency statistics are returned to command center");
                        if (removed_queries % ADJUST_BUDGET_INTERVAL == 0) {
                            long currentTimestamp = System.currentTimeMillis();
                            for (Map.Entry<String, Double> entry : frequencyStat.entrySet()) {
                                ArrayList<String> csvEntry = new ArrayList<String>();
                                csvEntry.add("" + ADAPTIVE_ADJUST_ROUND);
                                csvEntry.add(entry.getKey());
                                csvEntry.add("" + (currentTimestamp - initialAdjustTimestamp));
                                csvEntry.add("" + entry.getValue());
                                frequencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                                try {
                                    frequencyWriter.flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            frequencyStat.clear();
                            initialAdjustTimestamp = currentTimestamp;
                            if (WITHDRAW_SERVICE_INSTANCE) {
                                if (removed_queries % WITHDRAW_BUDGET_INTERVAL == 0) {
                                    withdrawServiceInstance();
                                    for (Map.Entry<String, List<ServiceInstance>> serviceEntry : serviceMap.entrySet()) {
                                        for (ServiceInstance instance : serviceEntry.getValue()) {
                                            instance.setQueriesBetweenWithdraw(0);
                                            instance.setRenewTimestamp(initialAdjustTimestamp);
                                        }
                                    }
                                }
                            }
                            adjustPowerBudget();
                            for (Map.Entry<String, List<ServiceInstance>> serviceEntry : serviceMap.entrySet()) {
                                for (ServiceInstance instance : serviceEntry.getValue()) {
                                    instance.setQueriesBetweenAdjust(0);
                                }
                            }
                            for (Map.Entry<String, List<ServiceInstance>> entry : serviceMap.entrySet()) {
                                for (ServiceInstance instance : entry.getValue()) {
                                    String instanceId = instance.getServiceType() + "_" + instance.getHostPort().getIp() + "_" + instance.getHostPort().getPort();

                                    frequencyStat.put(instanceId, instance.getCurrentFrequncy());
                                }
                            }
                        }
                    }
                    if (removed_queries % 1000 == 0) {
                        LOG.info("stop processing queries at " + System.currentTimeMillis());
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

//        private void withdrawServiceInstance() {
//            LOG.info("==================================================");
//            LOG.info("start to withdraw the service instances...");
//            LOG.info("scanning the queuing time of the past " + WITHDRAW_BUDGET_INTERVAL + " queries");
//            LOG.info("==================================================");
//        }

        /**
         * Every WITHDRAW_BUDGET_INTERVAL queries, if the queuing time of a service instance keeps zero and there are multiple instances of that service type available, stop one service instance and relinquish the power budget.
         * FIXME withdraw logic is too rigid that no service instance can be withdrew
         * new withdraw logic: if a service instance spends more than half of the withdraw interval in idle state, then withdraw it. Also in order to not be too aggressive, withdraw at most one instance of each service type.
         */
        private void withdrawServiceInstance() {
            long currentTimestamp = System.currentTimeMillis();
            LOG.info("==================================================");
            LOG.info("start to withdraw the service instances...");
            LOG.info("scanning the queuing time of the past " + WITHDRAW_BUDGET_INTERVAL + " queries");
            Map<String, List<Integer>> withdrawMap = new HashMap<String, List<Integer>>();
            for (String serviceType : serviceMap.keySet()) {
                List<ServiceInstance> serviceInstanceList = serviceMap.get(serviceType);
                if (serviceInstanceList.size() > 1) {
                    withdrawMap.put(serviceType, new LinkedList<Integer>());
                    for (int i = 0; i < serviceInstanceList.size(); i++) {
                        ServiceInstance serviceInstance = serviceInstanceList.get(i);
                        double servingTime = 0;
                        int statisticLength = serviceInstance.getServing_latency().size() - serviceInstance.getQueriesBetweenWithdraw();
                        if (statisticLength <= serviceInstance.getServing_latency().size() - 1) {
                            for (int index = statisticLength; index < serviceInstance.getServing_latency().size(); index++) {
                                servingTime += serviceInstance.getServing_latency().get(index);
                            }
                        }
                        if (Double.compare(servingTime, new Double((currentTimestamp - serviceInstance.getRenewTimestamp()) / 2.0)) < 0) {
                            withdrawMap.get(serviceType).add(i);
                            break;
                        }
                    }
                }
            }
            // shutdown the service instance and relinquish the gobal power budget
            if (withdrawMap.size() != 0) {
                int withdrawNum = 0;
                for (String serviceType : withdrawMap.keySet()) {
                    List<Integer> withdrawIndexList = withdrawMap.get(serviceType);
                    List<ServiceInstance> serviceInstanceList = serviceMap.get(serviceType);
                    for (Integer index : withdrawIndexList) {
                        ServiceInstance instance = serviceInstanceList.get(index);
                        double allocatedFreq = instance.getCurrentFrequncy();
                        POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() + PowerModel.getPowerPerFreq(allocatedFreq)));
                        serviceInstanceList.remove(index.intValue());
                        Collections.sort(serviceInstanceList, new LatencyComparator(LATENCY_TYPE));
                        ServiceInstance fastestInstance = serviceInstanceList.get(serviceInstanceList.size() - 1);
                        fastestInstance.setLoadProb(fastestInstance.getLoadProb() + instance.getLoadProb());
//                        double servingTime = 0;
//                        int statisticLength = instance.getServing_latency().size() - instance.getQueriesBetweenWithdraw();
//                        if (statisticLength <= instance.getServing_latency().size() - 1) {
//                            for (int indexTemp = statisticLength; indexTemp < instance.getServing_latency().size(); indexTemp++) {
//                                servingTime += instance.getServing_latency().get(indexTemp);
//                            }
//                        }
//                        LOG.info("the cumulated serving time is " + servingTime + " compared to half of the elapsed time " + new Double((currentTimestamp - instance.getRenewTimestamp()) / 2.0));
                        instance.getQueuing_latency().clear();
                        instance.getServing_latency().clear();
                        instance.setQueriesBetweenWithdraw(0);
                        instance.setQueriesBetweenAdjust(0);
                        candidateMap.get(serviceType).add(instance);
                        LOG.info("withdrawing the service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " and the power budget recycled is " + PowerModel.getPowerPerFreq(allocatedFreq));
                        LOG.info("the current global power budget is " + POWER_BUDGET.get().doubleValue());
                        withdrawNum++;
                        if (serviceInstanceList.size() == 1) {
                            break;
                        }
                    }
                }
                if (withdrawNum == 0) {
                    LOG.info("no service instance can be recycled, wait for the next recycle interval");
                }
            } else {
                LOG.info("no service instance can be recycled, wait for the next recycle interval");
            }
            LOG.info("==================================================");
        }

        /**
         * This method re-implements the pegasus paper control logic
         */
        private void performPegasus(long finishedQueryNum) {
            if (waitRound == 0) {
                long instantaneousLatency = end2endQueryLatency.get(end2endQueryLatency.size() - 1);
                long avgLatency = 0;
                if (finishedQueryNum >= ADJUST_BUDGET_INTERVAL) {
                    int start_index = end2endQueryLatency.size() - ADJUST_BUDGET_INTERVAL;
                    long totalLatency = 0;
                    for (int i = start_index; i < end2endQueryLatency.size(); i++) {
                        totalLatency += end2endQueryLatency.get(i);
                    }
                    avgLatency = totalLatency / ADJUST_BUDGET_INTERVAL;
                }
                double powerTarget = 0;
                if (avgLatency > qosTarget) {
                    // max power, wait 10 round
                    powerTarget = MAX_PACKAGE_POWER;
                    waitRound = 10;
                } else if (instantaneousLatency > 1.35 * qosTarget) {
                    // max power
                    powerTarget = MAX_PACKAGE_POWER;
                } else if (instantaneousLatency > qosTarget) {
                    // increase power by 7%
                    powerTarget = currentPackagePower * 1.07;
                } else if (0.85 * qosTarget <= instantaneousLatency && instantaneousLatency <= qosTarget) {
                    // keep current power
                    powerTarget = currentPackagePower;
                } else if (instantaneousLatency < 0.85 * qosTarget) {
                    // lower power by 1%
                    powerTarget = currentPackagePower * 0.99;
                } else if (instantaneousLatency < 0.6 * qosTarget) {
                    // lower power by 3%
                    powerTarget = currentPackagePower * 0.97;
                }
                currentPackagePower = powerTarget;
                // enforce the power target
                String command = "sudo ./writeRAPL " + Math.round(powerTarget);
                LOG.info("change the pp0 power to " + powerTarget + "watts");
                execSystemCommand(command);
                ArrayList<String> csvEntry = new ArrayList<String>();
                csvEntry.add("" + finishedQueryNum);
                csvEntry.add("" + powerTarget);
                pegasusPowerWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                try {
                    pegasusPowerWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                waitRound--;
            }
        }

        /**
         * The policy for adjusting the power budget is to:
         * 1. find out the slowest service instance(based on the 99th queuing latency) within the service topology
         * 2. predict the tail latency(tail latency / power) benefits of two control knobs(increase frequency or launch instance)
         * 3. recycling the power if the global power budget is not enough to satisfy the required power
         */
        private void adjustPowerBudget() {
            LOG.info("==================================================");
            LOG.info("adjust the power budget...");
            LOG.info("ranking the service instance based on the estimated delay((avg_queuing_time + avg_serving_time)*queue_length)");
            ADAPTIVE_ADJUST_ROUND++;
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            Percentile percentile = new Percentile();
            for (String serviceType : serviceMap.keySet()) {
                for (ServiceInstance instance : serviceMap.get(serviceType)) {
                    double estimatedLatency = 0;
                    if (instance.getQueuing_latency().size() != 0) {
                        int start_index = instance.getServing_latency().size() - instance.getQueriesBetweenAdjust();
                        //LOG.info("start index of the query list " + start_index);
                        //start_index = start_index > -1 ? start_index : 0;
                        List<Double> servingLatencyStatistic = instance.getServing_latency().subList(start_index, instance.getServing_latency().size());
                        List<Double> queuingLatencyStatistic = instance.getQueuing_latency().subList(start_index, instance.getServing_latency().size());
                        double servingPercentileValue = 0;
                        double queuingPercentileValue = 0;
                        if (queuingLatencyStatistic.size() != 0) {
                            int statLength = queuingLatencyStatistic.size();
                            double[] evaluateServingArray = new double[statLength];
                            double[] evaluateQueuingArray = new double[statLength];
                            double totalQueuing = 0;
                            double totalServing = 0;
                            for (int i = 0; i < statLength; i++) {
                                evaluateServingArray[i] = servingLatencyStatistic.get(i).doubleValue();
                                evaluateQueuingArray[i] = queuingLatencyStatistic.get(i).doubleValue();
                                totalServing += servingLatencyStatistic.get(i).doubleValue();
                                totalQueuing += queuingLatencyStatistic.get(i).doubleValue();
                            }
                            TClient clientDelegate = new TClient();
                            int currentQueueLength = 0;
                            try {
                                IPAService.Client serviceClient = clientDelegate.createIPAClient(instance.getHostPort().getIp(), instance.getHostPort().getPort());
                                currentQueueLength = serviceClient.reportQueueLength();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (TException e) {
                                e.printStackTrace();
                            }
                            clientDelegate.close();
                            instance.setCurrentQueueLength(currentQueueLength);
                            instance.setQueuingTimeAvg(totalQueuing / statLength);
                            instance.setServingTimeAvg(totalServing / statLength);
                            servingPercentileValue = percentile.evaluate(evaluateServingArray, LATENCY_PERCENTILE);
                            queuingPercentileValue = percentile.evaluate(evaluateQueuingArray, LATENCY_PERCENTILE);
                            instance.setServingTimePercentile(servingPercentileValue);
                            instance.setQueuingTimePercentile(queuingPercentileValue);
                            estimatedLatency = instance.getCurrentQueueLength() == 0 ? (instance.getQueuingTimeAvg() + instance.getServingTimeAvg()) : instance.getCurrentQueueLength() * (instance.getQueuingTimeAvg() + instance.getServingTimeAvg());
                            LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " with " + servingLatencyStatistic.size() + " finished queries" + ":");
                            LOG.info("average queuing time: " + instance.getQueuingTimeAvg() + "; queue length: " + currentQueueLength + "; 99th queuing time: " + instance.getQueuingTimePercentile() + "; average serving time: " + (totalServing / statLength) + "; 99th serving time: " + instance.getServingTimePercentile() + "; estimated queuing time: " + estimatedLatency);

                        } else {
                            instance.setQueuingTimeAvg(0);
                            instance.setCurrentQueueLength(0);
                            instance.setServingTimeAvg(0);
                            instance.setServingTimePercentile(0);
                            instance.setQueuingTimePercentile(0);
                            LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " received 0 queries during last adjust interval");
                        }
                    } else {
                        instance.setQueuingTimeAvg(0);
                        instance.setCurrentQueueLength(0);
                        instance.setServingTimeAvg(0);
                        instance.setServingTimePercentile(0);
                        instance.setQueuingTimePercentile(0);
                        LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " received 0 queries after it is started");
                    }
                    ArrayList<String> csvEntry = new ArrayList<String>();
                    csvEntry.add("" + ADAPTIVE_ADJUST_ROUND);
                    csvEntry.add(instance.getServiceType() + "_" + instance.getHostPort().getIp() + "_" + instance.getHostPort().getPort());
                    csvEntry.add("" + estimatedLatency);
                    expectedDelayWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                    try {
                        expectedDelayWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                serviceInstanceList.addAll(serviceMap.get(serviceType));
            }
            LOG.info("==================================================");
            // sort the service instance based on the 99th queuing latency
            Collections.sort(serviceInstanceList, new LatencyComparator(LATENCY_TYPE));
            String instanceRanking = "";
            String freqList = "";
            String loadProb = "";
            for (int i = 0; i < serviceInstanceList.size(); i++) {
                ServiceInstance instance = serviceInstanceList.get(i);
                instanceRanking += instance.getServiceType() + "@" + instance.getHostPort().getPort() + "-->";
                freqList += instance.getServiceType() + "@" + instance.getCurrentFrequncy() + "-->";
                loadProb += instance.getServiceType() + "@" + instance.getLoadProb() + "-->";
            }
            LOG.info("service instance ranking from slowest to fastest");
            LOG.info(instanceRanking);
            LOG.info(freqList);
            LOG.info(loadProb);
            LOG.info("global available power budget is " + POWER_BUDGET.get().doubleValue());
            double slowestQueuingTimeAvg = serviceInstanceList.get(0).getQueuingTimeAvg();
            double slowestServingTimeAvg = serviceInstanceList.get(0).getServingTimeAvg();
            double fastestQueuingTimeAvg = serviceInstanceList.get(serviceInstanceList.size() - 1).getQueuingTimeAvg();
            double fastestServingTimeAvg = serviceInstanceList.get(serviceInstanceList.size() - 1).getServingTimeAvg();
            int slowestQueueLength = serviceInstanceList.get(0).getCurrentQueueLength() == 0 ? 1 : serviceInstanceList.get(0).getCurrentQueueLength();
            int fastestQueueLength = serviceInstanceList.get(serviceInstanceList.size() - 1).getCurrentQueueLength() == 0 ? 1 : serviceInstanceList.get(serviceInstanceList.size() - 1).getCurrentQueueLength();
            Double queuingTimeDifference = (slowestQueuingTimeAvg + slowestServingTimeAvg) * slowestQueueLength - (fastestQueuingTimeAvg + fastestServingTimeAvg) * fastestQueueLength;
            if (Double.compare(queuingTimeDifference, ADJUST_THRESHOLD) > 0) {
                ServiceInstance slowestInstance = serviceInstanceList.get(0);
//                LOG.info("the slowest service instance is " + slowestInstance.getServiceType() + " running on " + slowestInstance.getHostPort().getIp() + ":" + slowestInstance.getHostPort().getPort() + " with estimated queuing delay " + slowestInstance.getQueuingTimeAvg());
                // predict the tail latency of increasing frequency and launching new service instance
                BoostDecision decision = predictBoostDecision(slowestInstance);
                serviceInstanceList.remove(0);
                // relocate the power budget, if true perform the boosting decision
                boolean accommodateDecision = true;
                if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.FREQUENCY_BOOST) || BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.INSTANCE_BOOST)) {
                    accommodateDecision = relocatePowerBudget(serviceInstanceList, decision.getRequiredPower());
                } else if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.ADAPTIVE_BOOST)) {
                    while (!relocatePowerBudget(serviceInstanceList, decision.getRequiredPower())) {
                        int freqIndex = freqRangeList.indexOf(decision.getFrequency());
                        if (freqIndex > 0) {
                            if (decision.getDecision().equalsIgnoreCase(BoostDecision.FREQUENCY_BOOST)) {
                                int slowFreqIndex = freqRangeList.indexOf(slowestInstance.getCurrentFrequncy());
                                if (freqIndex == (slowFreqIndex + 1)) {
                                    accommodateDecision = false;
                                    break;
                                }
                            }
                            double currentPower = PowerModel.getPowerPerFreq(decision.getFrequency());
                            double reducedPower = PowerModel.getPowerPerFreq(freqRangeList.get(freqIndex - 1));
                            decision.setRequiredPower(decision.getRequiredPower() - (currentPower - reducedPower));
                            decision.setFrequency(freqRangeList.get(freqIndex - 1));
                        } else {
                            accommodateDecision = false;
                            break;
                        }
                        LOG.info("the recycled power is not capable to fully perform the boosting decision");
                        LOG.info("decreasing the frequency from " + freqRangeList.get(freqIndex) + "--->" + decision.getFrequency() + "GHz");
                    }
                }
                if (accommodateDecision) {
                    if (decision.getDecision().equalsIgnoreCase(BoostDecision.FREQUENCY_BOOST)) {
                        IPAService.Client client = null;
                        double oldFreq = slowestInstance.getCurrentFrequncy();
                        try {
                            TClient clientDelegate = new TClient();
                            client = clientDelegate.createIPAClient(slowestInstance.getHostPort().getIp(), slowestInstance.getHostPort().getPort());
                            client.updatBudget(decision.getFrequency());
                            clientDelegate.close();
                            slowestInstance.setCurrentFrequncy(decision.getFrequency());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TException e) {
                            e.printStackTrace();
                        }
                        LOG.info("adjusting the frequency of service instance " + slowestInstance.getServiceType() + " running on " + slowestInstance.getHostPort().getIp() + ":" + slowestInstance.getHostPort().getPort() + " from " + oldFreq + " ---> " + decision.getFrequency());
                    } else if (decision.getDecision().equalsIgnoreCase(BoostDecision.INSTANCE_BOOST)) {
                        //slowestInstance.setCurrentFrequncy(decision.getFrequency());
                        launchServiceInstance(slowestInstance, decision.getFrequency());
                    }
                } else {
                    LOG.info("recycling failed, not enough power budget to perform the stage boosting");
                }
            } else {
                LOG.info("the service instances are already balanced, skip current adjusting interval");
            }
            LOG.info("==================================================");
        }

        private BoostDecision predictBoostDecision(ServiceInstance instance) {
            BoostDecision decision = new BoostDecision();
            Percentile percentile = new Percentile();
            double tailLatencyFreq = 0;
            double tailLatencyInstance = 0;
            double requiredPowerInstance = 0;
            double requiredPowerFreq = 0;
            double speedup = 0;
            // this is also the base power for increasing frequency
            requiredPowerInstance = PowerModel.getPowerPerFreq(instance.getCurrentFrequncy());
            if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.ADAPTIVE_BOOST)) {
/**
 * 1. calculate the tail latency of launching a new service instance as well as the required power
 * 2. assuming the new service instance would half the queuing time of the slowest instance but not changing the serving time
 * 3. evaluating the frequency equaling the power of a new instance
 * 4. calculate the tail latency of the whole service stage
 */
                int originIndex = freqRangeList.indexOf(instance.getCurrentFrequncy());
                int index = originIndex + 1;
                for (; index < freqRangeList.size(); index++) {
                    if ((PowerModel.getPowerPerFreq(freqRangeList.get(index)) - requiredPowerInstance) > requiredPowerInstance) {
                        break;
                    }
                }
                if ((index - 1) > originIndex) {
                    requiredPowerFreq = PowerModel.getPowerPerFreq(freqRangeList.get(index - 1)) - requiredPowerInstance;
                    speedup = speedupSheet.get(instance.getServiceType()).get(instance.getCurrentFrequncy()) - speedupSheet.get(instance.getServiceType()).get(freqRangeList.get(index - 1));
                } else {
                    decision.setDecision(BoostDecision.INSTANCE_BOOST);
                    decision.setFrequency(instance.getCurrentFrequncy());
                    decision.setRequiredPower(requiredPowerInstance);
                    LOG.info("service boosting decision: (instance boosting), the slowest service instance already running at maximum frequency");
                    return decision;
                }
                if (instance.getCurrentQueueLength() < MINIMUM_QUEUE_LENGTH) {
                    double currentFreq = instance.getCurrentFrequncy();
                    int increaseByHalf = (freqRangeList.size() - freqRangeList.indexOf(currentFreq)) / 2;
                    double increasedFreq = freqRangeList.get(freqRangeList.indexOf(currentFreq) + increaseByHalf);
                    // double increasedFreq = freqRangeList.get(freqRangeList.indexOf(currentFreq) + 1);
                    requiredPowerFreq = PowerModel.getPowerPerFreq(increasedFreq) - PowerModel.getPowerPerFreq(currentFreq);
                    decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                    decision.setRequiredPower(requiredPowerFreq);
                    decision.setFrequency(increasedFreq);
                    LOG.info("service boosting decision: (frequency boosting), the slowest service instance has " + instance.getCurrentQueueLength() + " queries queued up, increase the frequency from " + currentFreq + " ---> " + increasedFreq + "GHz");
                    return decision;
                }
                List<Double> totalLatencyFreq = new LinkedList<Double>();
                List<Double> totalLatencyInstance = new LinkedList<Double>();
                // iterate through the service instance list to predict the tail latency
                for (ServiceInstance historyInstance : serviceMap.get(instance.getServiceType())) {
                    if (historyInstance.getQueuing_latency().size() != 0) {
                        //Double statNum = ADJUST_BUDGET_INTERVAL * historyInstance.getLoadProb();
                        //int start_index = historyInstance.getQueuing_latency().size() - statNum.intValue() - 1;
                        int start_index = historyInstance.getQueuing_latency().size() - historyInstance.getQueriesBetweenAdjust();
                        //start_index = start_index > -1 ? start_index : 0;
                        List<Double> servingLatencyStatistic = historyInstance.getServing_latency().subList(start_index, historyInstance.getServing_latency().size());
                        List<Double> queuingLatencyStatistic = historyInstance.getQueuing_latency().subList(start_index, historyInstance.getQueuing_latency().size());
                        int statLength = queuingLatencyStatistic.size();
                        for (int i = 0; i < statLength; i++) {
                            if (historyInstance.getHostPort().equals(instance.getHostPort())) {
                                // frequency boosting
                                totalLatencyFreq.add((servingLatencyStatistic.get(i).doubleValue() + queuingLatencyStatistic.get(i).doubleValue()) * (1 - speedup));
                                totalLatencyInstance.add(servingLatencyStatistic.get(i).doubleValue() + queuingLatencyStatistic.get(i).doubleValue() / 2.0);
                                // instance boosting
                            } else {
                                totalLatencyFreq.add(servingLatencyStatistic.get(i).doubleValue() + queuingLatencyStatistic.get(i).doubleValue());
                                totalLatencyInstance.add(servingLatencyStatistic.get(i).doubleValue() + queuingLatencyStatistic.get(i).doubleValue());
                            }
                        }
                    }
                }
                double[] totalLatencyFreqArray = new double[totalLatencyFreq.size()];
                double[] totalLatencyInstanceArray = new double[totalLatencyInstance.size()];
                for (int i = 0; i < totalLatencyFreq.size(); i++) {
                    totalLatencyFreqArray[i] = totalLatencyFreq.get(i);
                    totalLatencyInstanceArray[i] = totalLatencyInstance.get(i);
                }
                tailLatencyFreq = percentile.evaluate(totalLatencyFreqArray, LATENCY_PERCENTILE);
                tailLatencyInstance = percentile.evaluate(totalLatencyInstanceArray, LATENCY_PERCENTILE);
                if (tailLatencyFreq < tailLatencyInstance) {
                    LOG.info("service boosting decision: (frequency boosting)" + " with tail latency " + tailLatencyFreq + " compared to " + tailLatencyInstance + " if launching new instance");
                    decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                    decision.setFrequency(freqRangeList.get(index - 1));
                    decision.setRequiredPower(requiredPowerFreq);
                } else {
                    LOG.info("service boosting decision: (instance boosting)" + " with tail latency " + tailLatencyInstance + " compared to " + tailLatencyFreq + " if increasing the frequency");
                    decision.setDecision(BoostDecision.INSTANCE_BOOST);
                    decision.setFrequency(instance.getCurrentFrequncy());
                    decision.setRequiredPower(requiredPowerInstance);
                }
                // 1. calculate the tail latency of frequency boosting
                // a. using the speedup sheet to estimate the serving time speedup
                // b. reducing the 99th tail latency of both queuing and serving time

                // 2. calculate the tail latency of instance boosting
                // a. reducing the 99th tail latency of queuing time to 50%
            } else if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.FREQUENCY_BOOST)) {
                int originIndex = freqRangeList.indexOf(instance.getCurrentFrequncy());
                int index = originIndex + 1;
                for (; index < freqRangeList.size(); index++) {
                    if ((PowerModel.getPowerPerFreq(freqRangeList.get(index)) - requiredPowerInstance) > requiredPowerInstance) {
                        break;
                    }
                }
                if ((index - 1) > originIndex) {
                    requiredPowerFreq = PowerModel.getPowerPerFreq(freqRangeList.get(index - 1)) - requiredPowerInstance;
                    decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                    decision.setFrequency(freqRangeList.get(index - 1));
                    decision.setRequiredPower(requiredPowerFreq);
                    LOG.info("service boosting decision: (always frequency boosting), increasing the frequency from " + instance.getCurrentFrequncy() + "--->" + freqRangeList.get(index - 1) + "GHz");
                } else {
                    decision.setDecision(BoostDecision.INSTANCE_BOOST);
                    decision.setRequiredPower(requiredPowerInstance);
                    LOG.info("service boosting decision: (instance boosting), the slowest service instance already running at maximum frequency");
                }
            } else if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.INSTANCE_BOOST)) {
                decision.setDecision(BoostDecision.INSTANCE_BOOST);
                decision.setFrequency(instance.getCurrentFrequncy());
                decision.setRequiredPower(requiredPowerInstance);
                LOG.info("service boosting decision: (always instance boosting)");
            }
            return decision;
        }

        /**
         * Recursively relocating the power budget across the service instances.
         *
         * @param serviceInstanceList
         * @param requiredPower
         */
        private boolean relocatePowerBudget(List<ServiceInstance> serviceInstanceList, double requiredPower) {
            boolean success = false;
            LOG.info("==================================================");
            LOG.info("start to relocate the power budget...");
            if (serviceInstanceList.size() != 0) {
                if (POWER_BUDGET.get().doubleValue() >= requiredPower) {
                    POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() - requiredPower));
                    LOG.info("global power budget is enough to perform the stage boosting, current global power budget is " + POWER_BUDGET.get().doubleValue());
                    success = true;
                } else {
                    LOG.info("recycling power budget from existing service instances");
                    LOG.info("the number of existing service instances is " + serviceInstanceList.size() + " and the required power budget is " + requiredPower);
                    Map<ServiceInstance, Double> adjustInstance = new HashMap<ServiceInstance, Double>();
                    double sum = 0;
                    DecimalFormat dFormat = new DecimalFormat("#.#");
                    for (int i = (serviceInstanceList.size() - 1); i > -1; i--) {
                        ServiceInstance fastestInstance = serviceInstanceList.get(i);
                        double fastFreq = fastestInstance.getCurrentFrequncy();
                        double fastPower = PowerModel.getPowerPerFreq(fastFreq);
//                        LOG.info("evaluating the service instance " + fastestInstance.getServiceType() + " with current frequency " + fastFreq + " and base power " + fastPower + " and the index of the frequency range is " + freqRangeList.indexOf(fastFreq));
                        LOG.info("evaluating the service instance " + fastestInstance.getServiceType() + " with current frequency " + fastFreq + " and current power " + fastPower);
                        for (int j = (freqRangeList.indexOf(fastFreq) - 1); j > -1; j--) {
                            if ((sum + fastPower - PowerModel.getPowerPerFreq(freqRangeList.get(j)) + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                                sum += fastPower - PowerModel.getPowerPerFreq(freqRangeList.get(j));
                                adjustInstance.put(fastestInstance, freqRangeList.get(j));
                                break;
                            }
                            if (j == 0) {
                                sum += fastPower - PowerModel.getPowerPerFreq(freqRangeList.get(j));
                                adjustInstance.put(fastestInstance, freqRangeList.get(j));
                            }
                        }
                        if ((sum + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                            break;
                        }
                    }
                    if ((sum + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                        if (sum < requiredPower) {
                            POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() - (requiredPower - sum)));
                        } else if (sum > requiredPower) {
                            POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() + (sum - requiredPower)));
                        }
                    } else {
                        LOG.info("not enough power budget to recycle, keep the current power budget unadjusted...");
                        adjustInstance.clear();
                        LOG.info("the global available power budget is " + POWER_BUDGET.get().doubleValue());
                        return success;
                    }
                    // update the global serviceMap and notify the service instance to update their power budget
                    for (ServiceInstance
                            keyInstance : adjustInstance.keySet()) {
                        double oldFreq = keyInstance.getCurrentFrequncy();
                        keyInstance.setCurrentFrequncy(adjustInstance.get(keyInstance));
                        try {
                            TClient clientDelegate = new TClient();
                            IPAService.Client client = clientDelegate.createIPAClient(keyInstance.getHostPort().getIp(), keyInstance.getHostPort().getPort());
                            client.updatBudget(adjustInstance.get(keyInstance));
                            clientDelegate.close();
                            LOG.info("the frequency of service instance running on " + keyInstance.getHostPort().getIp() + ":" + keyInstance.getHostPort().getPort() + " has been decreased from " + oldFreq + " ---> " + keyInstance.getCurrentFrequncy() + "GHz");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        } catch (TException ex) {
                            ex.printStackTrace();
                        }
                    }
                    LOG.info("the global available power budget is " + POWER_BUDGET.get().doubleValue());
                    success = true;
                }
            } else {
                LOG.info("no service instance available to recycle");
            }
            return success;
        }

        /**
         * Invoke the node manager service to launch new service instances.
         *
         * @param instance
         */
        private void launchServiceInstance(ServiceInstance instance, double decidedFreq) {
//            ServiceInstance instance = new ServiceInstance();
//            instance.setServiceType(serviceType);
//            instance.setQueuingTimePercentile(queuingTimePercentile);
//            instance.setCurrentFrequncy(defaultFrequency);
//            NodeManagerService.Client client = null;
//            THostPort hostPort = null;
//            try {
//                client = TClient.createNodeManagerClient(NODE_MANAGER_IP, NODE_MANAGER_PORT);
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
            double loadProb = instance.getLoadProb() / 2.0;
            instance.setLoadProb(loadProb);
            String serviceType = instance.getServiceType();
            if (candidateMap.get(serviceType).size() != 0) {
                List<ServiceInstance> instanceList = candidateMap.get(serviceType);
                ServiceInstance candidateInstance = instanceList.get(0);
                candidateInstance.setServiceType(serviceType);
                candidateInstance.setCurrentFrequncy(decidedFreq);
                candidateInstance.setLoadProb(loadProb);
                instanceList.remove(0);
                candidateInstance.setRenewTimestamp(System.currentTimeMillis());
                serviceMap.get(serviceType).add(candidateInstance);
                int stealedQueries = 0;
                try {
                    TClient clientDelegate = new TClient();
                    IPAService.Client client = clientDelegate.createIPAClient(candidateInstance.getHostPort().getIp(), candidateInstance.getHostPort().getPort());
                    client.updatBudget(candidateInstance.getCurrentFrequncy());
                    if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.ADAPTIVE_BOOST)) {
                        stealedQueries = client.stealParentInstance(instance.getHostPort());
                    }
                    clientDelegate.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                } catch (TException ex) {
                    ex.printStackTrace();
                }
                LOG.info("launching new service instance " + serviceType + " on " + candidateInstance.getHostPort().getIp() + ":" + candidateInstance.getHostPort().getPort() + " with frequency " + candidateInstance.getCurrentFrequncy() + "GHz");
                if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.ADAPTIVE_BOOST)) {
                    LOG.info("stealed " + stealedQueries + " queries from parent service " + serviceType + " running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort());
                }
                LOG.info("updating the load probability of the parent service instance to " + loadProb);
            } else {
                LOG.info("The node manager has run out of service instance " + serviceType);
            }
        }

        private String execSystemCommand(String command) {
            String result = null;
            try {
                Process p = Runtime.getRuntime().exec(command);
                BufferedReader stdInput = new BufferedReader(new
                        InputStreamReader(p.getInputStream()));

                BufferedReader stdError = new BufferedReader(new
                        InputStreamReader(p.getErrorStream()));
                while ((result = stdInput.readLine()) != null) {
                    LOG.info(result);
                }
                while ((result = stdError.readLine()) != null) {
                    LOG.error(result);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }

        /**
         *
         */
        private void loadBalance() {
            LOG.info("==================================================");
            LOG.info("load balancing the future queries...");
            Percentile percentile = new Percentile();
            for (String serviceType : serviceMap.keySet()) {
                List<Double> speedList = new LinkedList<Double>();
                double totalSpeed = 0;
                List<ServiceInstance> serviceInstancesList = serviceMap.get(serviceType);
                for (int i = 0; i < serviceInstancesList.size(); i++) {
                    ServiceInstance instance = serviceInstancesList.get(i);
                    List<Double> queuingStatistic = instance.getQueuing_latency();
                    double percentileValue = 0;
                    double preProbability = 0;
                    if (queuingStatistic.size() != 0) {
                        double[] evaluateArray = new double[queuingStatistic.size()];
                        for (int j = 0; j < queuingStatistic.size(); j++) {
                            evaluateArray[j] = queuingStatistic.get(j).doubleValue();
                        }
                        percentileValue = percentile.evaluate(evaluateArray, LATENCY_PERCENTILE);

                    } else {
                        percentileValue = instance.getQueuingTimePercentile();
                    }
                    preProbability = instance.getLoadProb();
                    double preSpeed = preProbability / percentileValue;
                    speedList.add(preSpeed);
                    totalSpeed += preSpeed;
                }
                for (int i = 0; i < serviceInstancesList.size(); i++) {
                    ServiceInstance instance = serviceInstancesList.get(i);
                    instance.setLoadProb((1.0 / totalSpeed) / (1.0 / speedList.get(i)));
                    LOG.info("the load probability of service instance " + instance.getServiceType() + " running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " is " + instance.getLoadProb());
                }
            }
            LOG.info("==================================================");
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
    private class LatencyComparator implements Comparator<ServiceInstance> {
        private String latencyType;

        public LatencyComparator(String latencyType) {
            this.latencyType = latencyType;
        }

        /**
         * @param instance1
         * @param instance2
         * @return the order from the largest to the smallest
         */
        @Override
        public int compare(ServiceInstance instance1, ServiceInstance instance2) {
            int compareResult = 0;
            if (latencyType.equalsIgnoreCase("tail")) {
                compareResult = Double.compare(instance2.getQueuingTimePercentile(), instance1.getQueuingTimePercentile());
            } else if (latencyType.equalsIgnoreCase("average")) {
                Double queuingTimeAve_Instance2 = instance2.getQueuingTimeAvg();
                Double servingTimeAve_Instance2 = instance2.getServingTimeAvg();
                Double queuingTimeAve_Instance1 = instance1.getQueuingTimeAvg();
                Double servingTimeAve_Instance1 = instance1.getServingTimeAvg();
                double estimatedLatency_Instance1 = 0;
                double estimatedLatency_Instance2 = 0;
                if (instance1.getCurrentQueueLength() != 0) {
                    estimatedLatency_Instance1 = instance1.getCurrentQueueLength() * (queuingTimeAve_Instance1 + servingTimeAve_Instance1);
                } else {
                    estimatedLatency_Instance1 = queuingTimeAve_Instance1 + servingTimeAve_Instance1;
                }
                if (instance2.getCurrentQueueLength() != 0) {
                    estimatedLatency_Instance2 = instance2.getCurrentQueueLength() * (queuingTimeAve_Instance2 + servingTimeAve_Instance2);
                } else {
                    estimatedLatency_Instance2 = queuingTimeAve_Instance2 + servingTimeAve_Instance2;
                }
                compareResult = Double.compare(estimatedLatency_Instance2, estimatedLatency_Instance1);
            }
            return compareResult;
        }
    }
}
