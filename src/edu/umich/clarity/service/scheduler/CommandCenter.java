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

/**
 *
 */
public class CommandCenter implements SchedulerService.Iface {

    public static final DecimalFormat dFormat = new DecimalFormat("#.##");

    public static final String LATENCY_TYPE = "average";
    // save for future use
    // private static final String NODE_MANAGER_IP = "clarity28.eecs.umich.edu";
    // private static final int NODE_MANAGER_PORT = 8060;
    private static final Logger LOG = Logger.getLogger(CommandCenter.class);
    // best effort or guarantee
    private static final long LATENCY_BUDGET = 100;
    private static final List<String> sirius_workflow = new LinkedList<String>();
    // headers for the CSV result files
    private static final String[] QUERY_LATENCY_FILE_HEADER = {"adjust_id", "total_queuing", "total_serving", "total_latency", "percentile_latency", "global_power"};
    private static final String[] SERVICE_LATENCY_FILE_HEADER = {"query_id", "asr_queuing", "asr_serving", "asr_instance", "imm_queuing", "imm_serving", "imm_instance", "qa_queuing", "qa_serving", "qa_instance"};
    private static final String[] POWER_FILE_HEADER = {"adjust_id", "service_stage", "service_instance", "frequency", "power"};
    private static final String[] STAGE_LATENCY_FILE_HEADER = {"adjust_id", "stage_name", "total_queuing", "total_serving", "total_latency"};
    private static final String[] PEGASUS_POWER_FILE_HEADER = {"adjust_id", "elapse_time", "package_power"};

    // private static final double DEFAULT_FREQUENCY = 1.8;
    // private static final int MINIMUM_QUEUE_LENGTH = 3;

    private static final double MAX_PACKAGE_POWER = (14 + 40) / 0.125;
    //public static boolean VANILLA_MODE = false;
    public static boolean VANILLA_MODE;
    //private static double GLOBAL_POWER_CONSUMPTION = 9.48 * 3;
    private static double GLOBAL_POWER_CONSUMPTION = 0;
    //private static int SCHEDULER_PORT = 8888;
    private static int SCHEDULER_PORT;
    // the interval to adjust power budget (per queries)
    //private static int ADJUST_QOS_INTERVAL = 50;
    private static int ADJUST_QOS_INTERVAL;
    // the interval to withdraw the idle service instances (per queries)
    //private static int WITHDRAW_BUDGET_INTERVAL = ADJUST_QOS_INTERVAL * 3;
    // private static int WITHDRAW_BUDGET_INTERVAL;
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
    // <stage, <service_instance, [queue_time, service_time]>
    private static Map<String, HashMap<ServiceInstance, ArrayList<Double>>> stageQueryHist = new HashMap<String, HashMap<ServiceInstance, ArrayList<Double>>>();

    private static Map<String, Double> stageQoSRatio = new HashMap<String, Double>();

    // private static Map<String, Double> frequencyStat = new HashMap<String, Double>();
    private static ConcurrentMap<String, List<ServiceInstance>> candidateMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    // private static CSVWriter queryLatencyWriter = null;
    private static CSVWriter serviceLatencyWriter = null;
    private static CSVWriter queryLatencyWriter = null;
    private static CSVWriter powerWriter = null;
    private static CSVWriter stageLatencyWriter = null;
    private static CSVWriter pegasusPowerWriter = null;

    private static CSVReader speedupReader = null;
    private static CSVReader workflowReader = null;
    // private static AtomicReference<Double> POWER_BUDGET = new AtomicReference<Double>();
    private static List<Integer> candidatePortList = new ArrayList<Integer>();
    private static Map<String, Map<Double, Double>> speedupSheet = new HashMap<String, Map<Double, Double>>();
    private static List<Double> freqRangeList = new LinkedList<Double>();
    //private static String BOOSTING_DECISION = BoostDecision.ADAPTIVE_BOOST;
    private static long initialAdjustTimestamp;
    private static long initialPegasusTimestamp;
    //private static boolean WITHDRAW_SERVICE_INSTANCE = true;

    private static boolean WITHDRAW_SERVICE_INSTANCE;
    private static int ADJUST_ROUND = 0;

    // pegasus
    private static String BOOSTING_DECISION;
    private static List<Double> end2endQueryLatency = new LinkedList<Double>();

    //private static double QoSTarget = 21.0;
    private static long QoSTarget = 2000;
    // private static double upperThreshold;
    // private static double midThreshold = 0.85;
    // private static double lowerThreshold;

    private static double currentPackagePower = (14 + 40) / 0.125;
    private static int waitRound = 0;
    // private static boolean WITHDRAW_SERVICE_INSTANCE = false;
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();

    public CommandCenter() {
        PropertyConfigurator.configure(System.getProperty("user.dir") + File.separator + "log4j.properties");
    }

    /**
     * Guard the QoS while minimizing the power.
     *
     * @param args args[0]: port, args[1]: adjust_interval, args[3]: warm_up_account, args[4]: adjust_threshold, args[5]: tail_percentile, args[10]: QoSTarget, args[11]: upperThreshold, args[12]: midThreshold, args[13]: lowerThreshold
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        CommandCenter commandCenter = new CommandCenter();
        if (args.length == 11) {
            SCHEDULER_PORT = Integer.valueOf(args[0]);
            ADJUST_QOS_INTERVAL = Integer.valueOf(args[1]);
            WARMUP_COUNT = Integer.valueOf(args[3]);
            ADJUST_THRESHOLD = Double.valueOf(args[4]);
            LATENCY_PERCENTILE = Double.valueOf(args[5]);
            if (args[7].equalsIgnoreCase("vanilla")) {
                VANILLA_MODE = true;
            } else if (args[7].equalsIgnoreCase("mulage")) {
                VANILLA_MODE = false;
            }
            BOOSTING_DECISION = args[8];
            QoSTarget = Long.valueOf(args[10]);

            // WITHDRAW_BUDGET_INTERVAL = Integer.valueOf(args[2]);
            // the global power consumption of sirius applications
            // GLOBAL_POWER_CONSUMPTION = Double.valueOf(args[6]);
            /*
            if (args[9].equalsIgnoreCase("withdraw")) {
                WITHDRAW_SERVICE_INSTANCE = true;
            } else if (args[9].equalsIgnoreCase("no-withdraw")) {
                WITHDRAW_SERVICE_INSTANCE = false;
            }
            */
            // upperThreshold = Double.valueOf(args[11]);
            // midThreshold = Double.valueOf(args[12]);
            // lowerThreshold = Double.valueOf(args[13]);
            LOG.info("the command center is running in " + args[7] + " mode, with " + BOOSTING_DECISION + " boosting decision");
            LOG.info("QoS target is " + QoSTarget + ", with latency percentile " + LATENCY_PERCENTILE + "%");
            LOG.info("adjust interval is " + ADJUST_QOS_INTERVAL + ", with adjust threshold " + ADJUST_THRESHOLD);
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

//        sirius_workflow.add("asr");
//        sirius_workflow.add("imm");
//        sirius_workflow.add("qa");
        //sirius_workflow.add("qa");
        try {
            workflowReader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator + "workflow.csv"), ',', '\n', 1);
            speedupReader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator + "freq.csv"), ',', '\n', 1);
            queryLatencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "query_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            serviceLatencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "service_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            powerWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "power.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            stageLatencyWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "stage_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            queryLatencyWriter.writeNext(QUERY_LATENCY_FILE_HEADER);
            queryLatencyWriter.flush();
            serviceLatencyWriter.writeNext(SERVICE_LATENCY_FILE_HEADER);
            serviceLatencyWriter.flush();
            powerWriter.writeNext(POWER_FILE_HEADER);
            powerWriter.flush();
            stageLatencyWriter.writeNext(STAGE_LATENCY_FILE_HEADER);
            stageLatencyWriter.flush();
            pegasusPowerWriter = new CSVWriter(new FileWriter(System.getProperty("user.dir") + File.separator + "pegasus_power.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            pegasusPowerWriter.writeNext(PEGASUS_POWER_FILE_HEADER);
            pegasusPowerWriter.flush();
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
        // POWER_BUDGET.set(GLOBAL_POWER_CONSUMPTION);

        String[] nextLine;
        int index = 0;
        try {
            while ((nextLine = workflowReader.readNext()) != null) {
                for (int i = 0; i < nextLine.length; i++) {
                    sirius_workflow.add(nextLine[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String workflow = "";
        for (int i = 0; i < sirius_workflow.size(); i++) {
            workflow += sirius_workflow.get(i);
            if ((i + 1) < sirius_workflow.size()) {
                workflow += "->";
            }
        }

        // build the speedup sheet
        index = 0;
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
        // LOG.info("the global power consumption is " + POWER_BUDGET.get().doubleValue());
        LOG.info("current workflow within command center is " + workflow);
        //LOG.info("launching the power budget managing thread with adjusting interval per " + ADJUST_QOS_INTERVAL + " queries and recycling interval per " + WITHDRAW_BUDGET_INTERVAL + " queries");
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
            hostPort = loadBalanceAssignService(service_list);
            /*
            if (!VANILLA_MODE)
                hostPort = loadBalanceAssignService(service_list);
            else
                hostPort = randomAssignService(service_list);
            */
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
                    stageQoSRatio.put(appName, 0.0);
                    stageQueryHist.put(appName, new HashMap<ServiceInstance, ArrayList<Double>>());
                }
                GLOBAL_POWER_CONSUMPTION += PowerModel.getPowerPerFreq(message.getBudget());
                LOG.info("putting it into the live instance list (current size for " + appName + ": " + serviceMap.get(appName).size() + ")");
                LOG.info("current global power consumption is " + GLOBAL_POWER_CONSUMPTION);
            } else { // candidate instances, put into the candidate list
                if (candidateMap.containsKey(appName)) {
                    candidateMap.get(appName).add(serviceInstance);
                } else {
                    // List<ServiceInstance> serviceInstanceList = new CopyOnWriteArrayList<ServiceInstance>();
                    List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
                    serviceInstanceList.add(serviceInstance);
                    candidateMap.put(appName, serviceInstanceList);
                }
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
                // ArrayList<String> csvEntry = new ArrayList<String>();
                ArrayList<String> serviceCSVEntry = new ArrayList<String>();
                // long total_queuing = 0;
                // long total_serving = 0;
                // csvEntry.add(query.getName());
                serviceCSVEntry.add(query.getName());
                for (int i = 0; i < query.getTimestamp().size(); i++) {
                    LatencySpec latencySpec = query.getTimestamp().get(i);
                    long queuing_time = latencySpec.getServing_start_time() - latencySpec.getQueuing_start_time();
                    // total_queuing += queuing_time;
                    long serving_time = latencySpec.getServing_end_time() - latencySpec.getServing_start_time();
                    // total_serving += serving_time;
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
                // csvEntry.add("" + total_queuing);
                // csvEntry.add("" + total_serving);
                serviceLatencyWriter.writeNext(serviceCSVEntry.toArray(new String[serviceCSVEntry.size()]));
                serviceLatencyWriter.flush();
                // queryLatencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                // queryLatencyWriter.flush();
                /*
                if (warmupCount.get() % 1000 == 0) {
                     LOG.info("1000 responses have been received, ");
                }
                */
            }
            /*
            if (warmupCount.get() == WARMUP_COUNT) {
                initialAdjustTimestamp = System.currentTimeMillis();
                // initialPegasusTimestamp = initialAdjustTimestamp;
                LOG.info("starting to processing the queries at " + initialAdjustTimestamp);
                for (Map.Entry<String, List<ServiceInstance>> entry : serviceMap.entrySet()) {
                    for (ServiceInstance instance : entry.getValue()) {
                        String instanceId = instance.getServiceType() + "_" + instance.getHostPort().getIp() + "_" + instance.getHostPort().getPort();
                        frequencyStat.put(instanceId, instance.getCurrentFrequncy());
                        instance.setRenewTimestamp(initialAdjustTimestamp);
                    }
                }
            }
            */
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
        private long processedResponses = 0;

        /**
         *
         */
        @Override
        public void run() {
            LOG.info("starting the helper thread for adjusting power budget across stages");
            while (processedResponses < 1000) {
                if (warmupCount.get() > WARMUP_COUNT) {
                    try {
                        LOG.info("sleep for " + ADJUST_QOS_INTERVAL + " ms before performing QoS management");
                        Thread.sleep(ADJUST_QOS_INTERVAL);
                        // long totalLatency = 0;
                        int finishedQueueSize = finishedQueryQueue.size();
                        LOG.info("" + finishedQueueSize + " responses have been received during the past interval");
                        if (finishedQueueSize > 0) {
                            double totalLatency = 0;
                            double totalQueuingTime = 0;
                            double totalServingTime = 0;
                            Percentile percentile = new Percentile();
                            double[] queryDelayArray = new double[finishedQueueSize];
                            for (int queryNum = 0; queryNum < finishedQueueSize; queryNum++) {
                                QuerySpec query = finishedQueryQueue.take();
                                double tempLatency = totalLatency;
                                for (int i = 0; i < query.getTimestamp().size(); i++) {
                                    LatencySpec latencySpec = query.getTimestamp().get(i);
                                    double queuing_time = latencySpec.getServing_start_time() - latencySpec.getQueuing_start_time();
                                    double serving_time = latencySpec.getServing_end_time() - latencySpec.getServing_start_time();
                                    totalLatency += queuing_time + serving_time;
                                    totalQueuingTime += queuing_time;
                                    totalServingTime += serving_time;
                                    String serviceType = latencySpec.getInstance_id().split("_")[0];
                                    String host = latencySpec.getInstance_id().split("_")[1];
                                    String port = latencySpec.getInstance_id().split("_")[2];
                                    for (ServiceInstance instance : serviceMap.get(serviceType)) {
                                        String instanceIp = instance.getHostPort().getIp();
                                        int instancePort = instance.getHostPort().getPort();
                                        if (instanceIp.equalsIgnoreCase(host) && instancePort == new Integer(port).intValue()) {
                                            instance.getServing_latency().add(serving_time);
                                            instance.getQueuing_latency().add(queuing_time);
                                            // instance.setQueriesBetweenWithdraw(instance.getQueriesBetweenWithdraw() + 1);
                                            instance.setQueriesBetweenAdjust(instance.getQueriesBetweenAdjust() + 1);
                                            double histStageLatency = stageQoSRatio.get(instance.getServiceType());
                                            stageQoSRatio.put(instance.getServiceType(), histStageLatency + serving_time + queuing_time);
                                            if (stageQueryHist.get(serviceType).get(instance) == null) {
                                                ArrayList<Double> queryLatency = new ArrayList<Double>();
                                                queryLatency.add(queuing_time / finishedQueueSize);
                                                queryLatency.add(serving_time / finishedQueueSize);
                                                stageQueryHist.get(serviceType).put(instance, queryLatency);
                                            } else {
                                                if (stageQueryHist.get(serviceType).get(instance).size() == 0) {
                                                    stageQueryHist.get(serviceType).get(instance).add(queuing_time / finishedQueueSize);
                                                    stageQueryHist.get(serviceType).get(instance).add(serving_time / finishedQueueSize);
                                                } else {
                                                    double historyQueueLatency = stageQueryHist.get(serviceType).get(instance).get(0);
                                                    double historyServiceLatency = stageQueryHist.get(serviceType).get(instance).get(1);
                                                    stageQueryHist.get(serviceType).get(instance).set(0, historyQueueLatency + queuing_time / finishedQueueSize);
                                                    stageQueryHist.get(serviceType).get(instance).set(1, historyServiceLatency + serving_time / finishedQueueSize);
                                                }
                                            }
                                        }
                                    }
                                }
                                queryDelayArray[queryNum] = totalLatency - tempLatency;
                                processedResponses++;
                            }
                            if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.PEGASUS_BOOST))
                                end2endQueryLatency.add(totalLatency);

                            ArrayList<String> csvEntry = new ArrayList<String>();
                            csvEntry.add("" + ADJUST_ROUND);
                            csvEntry.add("" + dFormat.format((totalQueuingTime / finishedQueueSize)));
                            csvEntry.add("" + dFormat.format((totalServingTime / finishedQueueSize)));
                            csvEntry.add("" + dFormat.format((totalLatency / finishedQueueSize)));
                            csvEntry.add("" + dFormat.format(percentile.evaluate(queryDelayArray, LATENCY_PERCENTILE)));
                            csvEntry.add("" + dFormat.format(GLOBAL_POWER_CONSUMPTION));
                            queryLatencyWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));

                            for (String stage : stageQueryHist.keySet()) {
                                ArrayList<String> stageCSVEntry = new ArrayList<String>();
                                double queuingTime = 0;
                                double servingTime = 0;
                                double latency = 0;
                                for (ServiceInstance histInstance : stageQueryHist.get(stage).keySet()) {
                                    ArrayList<String> powerCSVEntry = new ArrayList<String>();
                                    ArrayList<Double> histStats = stageQueryHist.get(stage).get(histInstance);
                                    queuingTime += histStats.get(0);
                                    servingTime += histStats.get(1);
                                    latency += histStats.get(0) + histStats.get(1);
                                    powerCSVEntry.add("" + ADJUST_ROUND);
                                    powerCSVEntry.add("" + stage);
                                    powerCSVEntry.add("" + histInstance.getServiceType() + "_" + histInstance.getHostPort().getIp() + "_" + histInstance.getHostPort().getPort());
                                    powerCSVEntry.add("" + histInstance.getCurrentFrequncy());
                                    powerCSVEntry.add("" + dFormat.format(PowerModel.getPowerPerFreq(histInstance.getCurrentFrequncy())));
                                    powerWriter.writeNext(powerCSVEntry.toArray(new String[powerCSVEntry.size()]));
                                }
                                stageCSVEntry.add("" + ADJUST_ROUND);
                                stageCSVEntry.add("" + stage);
                                stageCSVEntry.add("" + dFormat.format(queuingTime));
                                stageCSVEntry.add("" + dFormat.format(servingTime));
                                stageCSVEntry.add("" + dFormat.format(latency));
                                stageLatencyWriter.writeNext(stageCSVEntry.toArray(new String[stageCSVEntry.size()]));
                            }

                            try {
                                queryLatencyWriter.flush();
                                stageLatencyWriter.flush();
                                powerWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            for (String stage : stageQoSRatio.keySet()) {
                                stageQoSRatio.put(stage, stageQoSRatio.get(stage) / totalLatency * QoSTarget);
                            }
                            if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.ADAPTIVE_BOOST)) {
                                performMulage(totalLatency / finishedQueueSize, percentile.evaluate(queryDelayArray, LATENCY_PERCENTILE));
                            } else if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.PEGASUS_BOOST)) {
                                performPegasus();
                            } else {
                                ADJUST_ROUND++;
                            }
                            // calculate the global power consumption
                            GLOBAL_POWER_CONSUMPTION = 0;
                            for (String stage : serviceMap.keySet()) {
                                for (ServiceInstance instance : serviceMap.get(stage)) {
                                    GLOBAL_POWER_CONSUMPTION += PowerModel.getPowerPerFreq(instance.getCurrentFrequncy());
                                }
                            }
                            // clear up the data structure for next adjustment
                            for (String serviceType : stageQueryHist.keySet()) {
                                stageQueryHist.get(serviceType).clear();
                            }
                            for (String stage : stageQoSRatio.keySet()) {
                                stageQoSRatio.put(stage, 0.0);
                            }
                            if (BOOSTING_DECISION.equalsIgnoreCase(BoostDecision.PEGASUS_BOOST))
                                end2endQueryLatency.clear();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    LOG.info("warming up the application before entering the management mode");
                    try {
                        Thread.sleep(ADJUST_QOS_INTERVAL * 2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            LOG.info("1000 responses have been received, shutting down the command center");
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
        private void withdrawServiceInstance(ServiceInstance instance) {
            LOG.info("==================================================");
            LOG.info("start to withdraw the service instances...");

            // shutdown the service instance and relinquish the gobal power budget

            List<ServiceInstance> serviceInstanceList = serviceMap.get(instance.getServiceType());
            // double allocatedFreq = instance.getCurrentFrequncy();
            serviceInstanceList.remove(instance);
            double incrementalProb = instance.getLoadProb() / serviceInstanceList.size();
            for (int i = 0; i < serviceInstanceList.size(); i++) {
                if (i == serviceInstanceList.size() - 1) {
                    serviceInstanceList.get(i).setLoadProb(serviceInstanceList.get(i).getLoadProb() + 1 - i * incrementalProb);
                } else {
                    serviceInstanceList.get(i).setLoadProb(serviceInstanceList.get(i).getLoadProb() + incrementalProb);
                }
            }
            // Collections.sort(serviceInstanceList, new LatencyComparator(LATENCY_TYPE));
            // ServiceInstance fastestInstance = serviceInstanceList.get(serviceInstanceList.size() - 1);
            // fastestInstance.setLoadProb(fastestInstance.getLoadProb() + instance.getLoadProb());

            instance.getQueuing_latency().clear();
            instance.getServing_latency().clear();
            // instance.setQueriesBetweenWithdraw(0);
            instance.setQueriesBetweenAdjust(0);
            candidateMap.get(instance.getServiceType()).add(instance);
            LOG.info("withdrawing the service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort());
            // LOG.info("the current global power consumption is " + POWER_BUDGET.get().doubleValue());
            LOG.info("==================================================");
        }

        /**
         * This method re-implements the pegasus paper control logic
         */
        private void performPegasus() {
            if (end2endQueryLatency.size() != 0) {
                double instantaneousLatency = end2endQueryLatency.get(end2endQueryLatency.size() - 1);
                double avgLatency = 0;
                for (double end2endLatency : end2endQueryLatency) {
                    avgLatency += end2endLatency;
                }
                avgLatency = avgLatency / end2endQueryLatency.size();
                if (waitRound == 0) {
                    double powerTarget = 0;
                    if (Double.compare(avgLatency, QoSTarget) > 0) {
                        // max power, wait 10 round
                        powerTarget = MAX_PACKAGE_POWER;
                        waitRound = 10;
                    } else if (Double.compare(instantaneousLatency, 1.35 * QoSTarget) > 0) {
                        // max power
                        powerTarget = MAX_PACKAGE_POWER;
                    } else if (Double.compare(instantaneousLatency, QoSTarget) > 0) {
                        // increase power by 7%
                        powerTarget = currentPackagePower * 1.07;
                        if (powerTarget > MAX_PACKAGE_POWER) {
                            powerTarget = MAX_PACKAGE_POWER;
                        }
                    } else if (Double.compare(0.85 * QoSTarget, instantaneousLatency) <= 0 && Double.compare(instantaneousLatency, QoSTarget) <= 0) {
                        // keep current power
                        powerTarget = currentPackagePower;
                    } else if (Double.compare(instantaneousLatency, 0.85 * QoSTarget) < 0) {
                        // lower power by 1%
                        powerTarget = currentPackagePower * 0.99;
                    } else if (Double.compare(instantaneousLatency, 0.6 * QoSTarget) < 0) {
                        // lower power by 3%
                        powerTarget = currentPackagePower * 0.97;
                    }
                    if (Double.compare(powerTarget, ((40 + 5) / 0.125)) < 0) {
                        powerTarget = (40 + 5) / 0.125;
                    }
                    currentPackagePower = powerTarget;
                    // enforce the power target
                    String command = "sudo ./writeRAPL " + Math.round(powerTarget);
                    execSystemCommand(command);
                } else {
                    waitRound--;
                }
                ArrayList<String> csvEntry = new ArrayList<String>();
                csvEntry.add("" + ADJUST_ROUND);
                csvEntry.add("" + avgLatency);
                csvEntry.add("" + (currentPackagePower * 0.125 - 40));
                pegasusPowerWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
                try {
                    pegasusPowerWriter.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                LOG.info("change the pp0 power to " + currentPackagePower + "watts");
            } else {
                LOG.info("no query has been returned in the previous interval");
            }
            ADJUST_ROUND++;
        }

        /**
         * The policy for guarding the QoS while minimizing the power consumption is to:
         * 1. find out the slowest service instance (based on queue_length * (avg_queue_latency + avg_service_latency)) within the service topology
         * 2. adjust the frequency of slowest service instance similar to pegasus
         * 2.1 if the highest frequency is reached, launch a new service instance with middle frequency level
         * 2.2 if the lowest frequency is reached, withdraw the service instance
         * <p/>
         * debug log format: serviceLatencyWriter
         * (adjust round, instance_id, current_frequency, query_latency)
         * <p/>
         * result log format: queryLatencyWriter
         * (adjust_round, measured_latency, percentile_latency, power_consumption)
         *
         * @param measuredLatency measured average latency during the moving window
         * @param percentile      percentile latency durin the past adjust interval
         */
        private void performMulage(double measuredLatency, double percentile) {
            LOG.info("==================================================");
            LOG.info("adjust the power budget...");
            LOG.info("ranking the service instance based on the estimated delay((avg_queuing_time + avg_serving_time)*queue_length)");
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            // double currentPowerConsumption = 0;
            for (String serviceType : serviceMap.keySet()) {
                for (ServiceInstance instance : serviceMap.get(serviceType)) {
                    // double estimatedLatency = 0;
                    // currentPowerConsumption += PowerModel.getPowerPerFreq(instance.getCurrentFrequncy());
                    if (instance.getQueuing_latency().size() != 0) {
                        int start_index = instance.getServing_latency().size() - instance.getQueriesBetweenAdjust();
                        //LOG.info("start index of the query list " + start_index);
                        //start_index = start_index > -1 ? start_index : 0;
                        List<Double> servingLatencyStatistic = instance.getServing_latency().subList(start_index, instance.getServing_latency().size());
                        List<Double> queuingLatencyStatistic = instance.getQueuing_latency().subList(start_index, instance.getServing_latency().size());
                        // double servingPercentileValue = 0;
                        // double queuingPercentileValue = 0;
                        if (queuingLatencyStatistic.size() != 0) {
                            int statLength = queuingLatencyStatistic.size();
                            // double[] evaluateServingArray = new double[statLength];
                            // double[] evaluateQueuingArray = new double[statLength];
                            double totalQueuing = 0;
                            double totalServing = 0;
                            for (int i = 0; i < statLength; i++) {
                                // evaluateServingArray[i] = servingLatencyStatistic.get(i).doubleValue();
                                // evaluateQueuingArray[i] = queuingLatencyStatistic.get(i).doubleValue();
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
                            LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " with " + servingLatencyStatistic.size() + " finished queries" + ":");
                            LOG.info("average queuing time: " + instance.getQueuingTimeAvg() + "; average serving time: " + (totalServing / statLength) + "; current queue length: " + currentQueueLength);
                        } else {
                            instance.setQueuingTimeAvg(0);
                            instance.setCurrentQueueLength(0);
                            instance.setServingTimeAvg(0);
                            // instance.setServingTimePercentile(0);
                            // instance.setQueuingTimePercentile(0);
                            LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " received 0 queries during last adjust interval");
                        }
                    } else {
                        instance.setQueuingTimeAvg(0);
                        instance.setCurrentQueueLength(0);
                        instance.setServingTimeAvg(0);
                        // instance.setServingTimePercentile(0);
                        // instance.setQueuingTimePercentile(0);
                        LOG.info("service " + serviceType + " running on " + instance.getHostPort().getPort() + " received 0 queries after it is started");
                    }
                }
                serviceInstanceList.addAll(serviceMap.get(serviceType));
            }
            LOG.info("adjust round " + ADJUST_ROUND + ":" + " the measured avgerage and percentile latency is " + measuredLatency + " and " + percentile);
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

            LOG.info("measured latency QoS is " + measuredLatency + " and the stable range is " + ADJUST_THRESHOLD * QoSTarget + " <= Measured QoS <= " + QoSTarget);
            // 1. QoS is violated, applying service boosting techniques
            if (Double.compare(measuredLatency, QoSTarget) > 0) {
                LOG.info("the QoS is violated, increase the power consumption of the slowest stage");
                ServiceInstance slowestInstance = serviceInstanceList.get(0);
                BoostDecision decision = predictBoostDecision(slowestInstance, measuredLatency);
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
                    if (candidateMap.get(slowestInstance.getServiceType()).size() != 0) {
                        launchServiceInstance(slowestInstance, decision.getFrequency());
                    } else {
                        LOG.info("node manager has ran out of service instances, skip current adjustment");
                    }
                }
            } else if (Double.compare(measuredLatency, QoSTarget) <= 0 && Double.compare(measuredLatency, ADJUST_THRESHOLD * QoSTarget) >= 0) {
                // 2. QoS is within the stable range, leave it without further actions
                LOG.info("the QoS is within the stable range, skip current adjusting interval");
            } else if (Double.compare(measuredLatency, ADJUST_THRESHOLD * QoSTarget) < 0) {
                // 3. QoS is overfitted, reduce frequency or withdraw instance to save power
                LOG.info("the QoS is overfitted, reduce the power consumption of the fastest stage");
                powerConserve(serviceInstanceList);
            }
            LOG.info("==================================================");
            ADJUST_ROUND++;
        }

        /**
         * Compare the boosting decisions and choose the one that satisfies QoS target while consumes least power.
         *
         * @param instance
         * @param measuredLatency
         * @return boosting decision (frequency boosting / instance boosting)
         */
        private BoostDecision predictBoostDecision(ServiceInstance instance, double measuredLatency) {
            BoostDecision decision = new BoostDecision();
            Percentile percentile = new Percentile();
            double tailLatencyFreq = 0;
            double tailLatencyInstance = 0;
            double requiredPowerInstance = 0;
            double requiredPowerFreq = 0;
            double speedup = 0;

            double stageQoSTarget = stageQoSRatio.get(instance.getServiceType());

            int originIndex = freqRangeList.indexOf(instance.getCurrentFrequncy());
            // already reach the max frequency, launch a new instance
            if (originIndex == freqRangeList.size() - 1) {
                decision.setDecision(BoostDecision.INSTANCE_BOOST);
                decision.setFrequency(instance.getCurrentFrequncy());
                LOG.info("service boosting decision: (instance boosting), the slowest service instance already running at maximum frequency");
            } else {
                // prepare the history of query statistics across instances
                List<Double> targetQueueStats = new LinkedList<Double>();
                List<Double> targetServeStats = new LinkedList<Double>();
                double constantLatencyStats = 0;
                double totalHistoryQuery = 0;
                for (ServiceInstance historyInstance : serviceMap.get(instance.getServiceType())) {
                    if (historyInstance.getQueuing_latency().size() != 0) {
                        int start_index = historyInstance.getQueuing_latency().size() - historyInstance.getQueriesBetweenAdjust();
                        totalHistoryQuery += historyInstance.getQueriesBetweenAdjust();
                        List<Double> servingLatencyStatistic = historyInstance.getServing_latency().subList(start_index, historyInstance.getServing_latency().size());
                        List<Double> queuingLatencyStatistic = historyInstance.getQueuing_latency().subList(start_index, historyInstance.getQueuing_latency().size());
                        int statLength = queuingLatencyStatistic.size();
                        for (int i = 0; i < statLength; i++) {
                            if (historyInstance.getHostPort().equals(instance.getHostPort())) {
                                // frequency boosting
                                targetQueueStats.add(queuingLatencyStatistic.get(i).doubleValue());
                                // instance boosting
                                targetServeStats.add(servingLatencyStatistic.get(i).doubleValue());
                            } else {
                                constantLatencyStats += servingLatencyStatistic.get(i).doubleValue() + queuingLatencyStatistic.get(i).doubleValue();
                            }
                        }
                    }
                }
                // 1. predict the latency of instance boosting
                double instanceBoostingDelay = 0;
                for (int i = 0; i < targetQueueStats.size(); i++) {
                    instanceBoostingDelay += targetServeStats.get(i).doubleValue() + targetQueueStats.get(i).doubleValue() / 2.0;
                }
                instanceBoostingDelay = (instanceBoostingDelay + constantLatencyStats) / totalHistoryQuery;
                // 2. predict the latency of frequency boosting
                double frequencyBoostingDelay = 0;
                int frequencyIndex = 0;
                for (int index = originIndex + 1; index < freqRangeList.size(); index++) {
                    frequencyIndex = index;
                    frequencyBoostingDelay = 0;
                    speedup = speedupSheet.get(instance.getServiceType()).get(instance.getCurrentFrequncy()) - speedupSheet.get(instance.getServiceType()).get(freqRangeList.get(index));
                    for (int i = 0; i < targetQueueStats.size(); i++) {
                        frequencyBoostingDelay += (targetServeStats.get(i).doubleValue() + targetQueueStats.get(i).doubleValue()) * (1 - speedup);
                    }
                    frequencyBoostingDelay = (frequencyBoostingDelay + constantLatencyStats) / totalHistoryQuery;
                    if (frequencyBoostingDelay < stageQoSTarget) {
                        break;
                    }
                }
                // 3. choose the boosting that satisfies the QoS target with least power consumption
                // otherwise, choose the close to the QoS target
                LOG.info("predicted QoS with instance boosting is " + instanceBoostingDelay + " while with frequency boosting is " + frequencyBoostingDelay);
                if (instanceBoostingDelay <= stageQoSTarget && frequencyBoostingDelay <= stageQoSTarget) {
                    if (PowerModel.getPowerPerFreq(freqRangeList.get(frequencyIndex)) <= PowerModel.getPowerPerFreq(instance.getCurrentFrequncy())) {
                        decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                        decision.setFrequency(freqRangeList.get(frequencyIndex));
                        LOG.info("service boosting decision: (frequency boosting), increase the frequency from " + instance.getCurrentFrequncy() + " ---> " + freqRangeList.get(frequencyIndex) + "GHz");
                    } else {
                        decision.setDecision(BoostDecision.INSTANCE_BOOST);
                        decision.setFrequency(instance.getCurrentFrequncy());
                        LOG.info("service boosting decision: (instance boosting), launch a new instance with frequency " + instance.getCurrentFrequncy() + "GHz");
                    }
                } else {
                    if (instanceBoostingDelay <= frequencyBoostingDelay) {
                        decision.setDecision(BoostDecision.INSTANCE_BOOST);
                        decision.setFrequency(instance.getCurrentFrequncy());
                        LOG.info("service boosting decision: (instance boosting), launch a new instance with frequency " + instance.getCurrentFrequncy() + "GHz");
                    } else {
                        decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                        decision.setFrequency(freqRangeList.get(frequencyIndex));
                        LOG.info("service boosting decision: (frequency boosting), increase the frequency from " + instance.getCurrentFrequncy() + " ---> " + freqRangeList.get(frequencyIndex) + "GHz");
                    }
                }
            }
            return decision;
        }

        /**
         * Recursively relocating the power budget across the service instances.
         *
         * @param serviceInstanceList
         */
        private void powerConserve(List<ServiceInstance> serviceInstanceList) {
            LOG.info("==================================================");
            LOG.info("start to reduce the power consumption...");
            if (serviceInstanceList.size() != 0) {
                List<ServiceInstance> instanceWithdraw = new LinkedList<ServiceInstance>();
                List<ServiceInstance> instanceReduceFreq = new LinkedList<ServiceInstance>();
                List<Integer> freqTarget = new LinkedList<Integer>();
                for (String stage : stageQoSRatio.keySet()) {
                    LOG.info("stage: " + stage + " with QoS budget of " + stageQoSRatio.get(stage));
                }
                // 1. iterate through the service instances (from fastest to slowest)
                // 2. if instance is already running at slowest frequency
                // 2.1 if instance is not the only instance within its stage
                // 2.1 withdraw the instance if it is not violated the QoS target, otherwise skip current instance
                // 2.2 reduce the frequency of the instance until it reaches the slowest or violates the QoS
                for (ServiceInstance instance : serviceInstanceList) {
                    if (freqRangeList.indexOf(instance.getCurrentFrequncy()) == 0) {
                        if (serviceMap.get(instance.getServiceType()).size() > 1) {
                            double stageLatency = 0;
                            for (ServiceInstance histInstance : stageQueryHist.get(instance.getServiceType()).keySet()) {
                                if (histInstance.equals(instance)) {
                                    stageLatency += stageQueryHist.get(instance.getServiceType()).get(histInstance).get(0) * 2.0 + stageQueryHist.get(instance.getServiceType()).get(histInstance).get(1);
                                } else {
                                    stageLatency += stageQueryHist.get(instance.getServiceType()).get(histInstance).get(0) + stageQueryHist.get(instance.getServiceType()).get(histInstance).get(1);
                                }
                            }
                            if (Double.compare(stageLatency, stageQoSRatio.get(instance.getServiceType())) < 0) {
                                instanceWithdraw.add(instance);
                                double oldValue = stageQueryHist.get(instance.getServiceType()).get(instance).get(0);
                                stageQueryHist.get(instance.getServiceType()).get(instance).set(0, oldValue * 2.0);
                            } else {
                                continue;
                            }
                        } else {
                            // there is only one instance left for this stage
                            continue;
                        }
                    } else {
                        // reduce the frequency of the instance without violating the QoS
                        int originIndex = freqRangeList.indexOf(instance.getCurrentFrequncy()) - 1;
                        for (; originIndex > -1; originIndex--) {
                            double stageLatency = 0;
                            double speedup = speedupSheet.get(instance.getServiceType()).get(instance.getCurrentFrequncy()) - speedupSheet.get(instance.getServiceType()).get(freqRangeList.get(originIndex));
                            for (ServiceInstance histInstance : stageQueryHist.get(instance.getServiceType()).keySet()) {
                                if (histInstance.equals(instance)) {
                                    stageLatency += (stageQueryHist.get(instance.getServiceType()).get(histInstance).get(0) + stageQueryHist.get(instance.getServiceType()).get(histInstance).get(1)) / (1 - speedup);
                                } else {
                                    stageLatency += stageQueryHist.get(instance.getServiceType()).get(histInstance).get(0) + stageQueryHist.get(instance.getServiceType()).get(histInstance).get(1);
                                }
                            }
                            if (Double.compare(stageLatency, stageQoSRatio.get(instance.getServiceType())) < 0) {
                                continue;
                            } else {
                                break;
                            }
                        }
                        if (originIndex != freqRangeList.indexOf(instance.getCurrentFrequncy()) - 1) {
                            if (originIndex < 0) {
                                originIndex = 0;
                            }
                            instanceReduceFreq.add(instance);
                            freqTarget.add(originIndex);
                            double oldQueueValue = stageQueryHist.get(instance.getServiceType()).get(instance).get(0);
                            double oldServiceValue = stageQueryHist.get(instance.getServiceType()).get(instance).get(1);
                            double speedup = speedupSheet.get(instance.getServiceType()).get(instance.getCurrentFrequncy()) - speedupSheet.get(instance.getServiceType()).get(freqRangeList.get(originIndex));
                            stageQueryHist.get(instance.getServiceType()).get(instance).set(0, oldQueueValue / (1 - speedup));
                            stageQueryHist.get(instance.getServiceType()).get(instance).set(1, oldServiceValue / (1 - speedup));
                        }
                    }
                }

                // perform the power conserve decisions
                for (ServiceInstance instance : instanceWithdraw) {
                    if (serviceMap.get(instance.getServiceType()).size() > 1) {
                        withdrawServiceInstance(instance);
                    }
                }
                LOG.info("==================================================");
                LOG.info("start to reduce the frequency of service instances...");
                for (int index = 0; index < instanceReduceFreq.size(); index++) {
                    ServiceInstance instance = instanceReduceFreq.get(index);
                    double oldFreq = instance.getCurrentFrequncy();
                    instance.setCurrentFrequncy(freqRangeList.get(freqTarget.get(index)));
                    try {
                        TClient clientDelegate = new TClient();
                        IPAService.Client client = clientDelegate.createIPAClient(instance.getHostPort().getIp(), instance.getHostPort().getPort());
                        client.updatBudget(freqRangeList.get(freqTarget.get(index)));
                        clientDelegate.close();
                        LOG.info("the frequency of service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " has been decreased from " + oldFreq + " ---> " + instance.getCurrentFrequncy() + "GHz");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (TException ex) {
                        ex.printStackTrace();
                    }
                }
            } else {
                LOG.info("no service instance available to recycle");
            }
        }

        /**
         * Invoke the node manager service to launch new service instances.
         *
         * @param instance
         */
        private void launchServiceInstance(ServiceInstance instance, double decidedFreq) {
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
                    stealedQueries = client.stealParentInstance(instance.getHostPort());
                    clientDelegate.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                } catch (TException ex) {
                    ex.printStackTrace();
                }
                LOG.info("launching new service instance " + serviceType + " on " + candidateInstance.getHostPort().getIp() + ":" + candidateInstance.getHostPort().getPort() + " with frequency " + candidateInstance.getCurrentFrequncy() + "GHz");
                LOG.info("stealed " + stealedQueries + " queries from parent service " + serviceType + " running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort());
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

        ////////////////////////////////////////////////////////////////////////////////////////////
        // not used
        ///////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Recursively relocating the power budget across the service instances.
         *
         * @param serviceInstanceList
         * @param requiredPower
         */
        /*
        private boolean adjustFrequency(List<ServiceInstance> serviceInstanceList, double requiredPower) {
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
        */

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
