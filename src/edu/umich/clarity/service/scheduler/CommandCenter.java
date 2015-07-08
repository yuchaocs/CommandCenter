package edu.umich.clarity.service.scheduler;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.*;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class CommandCenter implements SchedulerService.Iface {

    public static final String LATENCY_TYPE = "tail";

    public static final boolean VANILLA_MODE = true;
    private static final double GLOBAL_POWER_BUDGET = 9.48 * 3;
    // save for future use
    private static final String NODE_MANAGER_IP = "clarity28.eecs.umich.edu";
    private static final int NODE_MANAGER_PORT = 8060;

    private static final Logger LOG = Logger.getLogger(CommandCenter.class);
    private static final String SCHEDULER_IP = "localhost";
    private static final int SCHEDULER_PORT = 8888;
    // the interval to adjust power budget (per queries)
    private static final int ADJUST_BUDGET_INTERVAL = 10;
    // the interval to withdraw the idle service instances (per queries)
    private static final int WITHDRAW_BUDGET_INTERVAL = 108;
    // best effort or guarantee
    private static final long LATENCY_BUDGET = 300;
    private static final List<String> sirius_workflow = new LinkedList<String>();
    // headers for the CSV result files
    private static final String[] LATENCY_FILE_HEADER = {"asr_queuing", "asr_serving", "asr_instance", "imm_queuing", "imm_serving", "imm_instance", "qa_queuing", "qa_serving", "qa_instance", "total_queuing", "total_serving"};
    private static final String[] QUEUE_FILE_HEADER = {"service_name", "host", "port", "queue_length"};
    // the tail latency target
    private static final double LATENCY_PERCENTILE = 99;
    // latency threshold between instances before stopping power adjustment
    private static final double ADJUST_THRESHOLD = 1000;
    private static final double DEFAULT_FREQUENCY = 1.2;
    private static ConcurrentMap<String, List<ServiceInstance>> serviceMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static ConcurrentMap<String, List<ServiceInstance>> candidateMap = new ConcurrentHashMap<String, List<ServiceInstance>>();
    private static ConcurrentMap<String, Double> budgetMap = new ConcurrentHashMap<String, Double>();
    private static CSVWriter latencyWriter = null;
    private static CSVReader speedupReader = null;
    private static AtomicReference<Double> POWER_BUDGET = new AtomicReference<Double>();
    private static List<Integer> candidatePortList = new ArrayList<Integer>();
    private static Map<String, Map<Double, Double>> speedupSheet = new HashMap<String, Map<Double, Double>>();
    private static List<Double> freqRangeList = new LinkedList<Double>();
    private BlockingQueue<QuerySpec> finishedQueryQueue = new LinkedBlockingQueue<QuerySpec>();
//    private static final String[] FREQ_RANGE =
//            {
//                    "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0", "2.1", "2.2", "2.3", "2.4"
//            };

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
            speedupReader = new CSVReader(new FileReader("freq.csv"), ',', '\n', 1);
            latencyWriter = new CSVWriter(new FileWriter("query_latency.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
            latencyWriter.writeNext(LATENCY_FILE_HEADER);
            latencyWriter.flush();
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
        List<Double> thresHold = new LinkedList<Double>();
        for (int i = 0; i < service_list.size(); i++) {
            ServiceInstance instance = service_list.get(i);
            double sum = 0;
            for (int j = 0; j < i + 1; j++) {
                sum += service_list.get(j).getLoadProb();
            }
            thresHold.add(sum);
        }

        Random rand = new Random();
        double index = rand.nextDouble();

        for (int i = 0; i < thresHold.size(); i++) {
            if (index < thresHold.get(i)) {
                hostPort = service_list.get(i).getHostPort();
                break;
            }
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
        LOG.info("receiving register message from service stage " + appName
                + " running on " + hostPort.getIp() + ":" + hostPort.getPort());
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setHostPort(hostPort);
        serviceInstance.setServiceType(appName);
        serviceInstance.setCurrentFrequncy(message.getBudget());
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
                    List<ServiceInstance> serviceInstanceList = new CopyOnWriteArrayList<ServiceInstance>();
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
                    List<ServiceInstance> serviceInstanceList = new CopyOnWriteArrayList<ServiceInstance>();
                    serviceInstanceList.add(serviceInstance);
                    candidateMap.put(appName, serviceInstanceList);
                }
                LOG.info("putting it into the candidate instance list (current size for " + appName + ": " + candidateMap.get(appName).size() + ")");
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
                    LOG.info(removed_queries + " query latency statistics are returned to command center");
                    if (removed_queries % ADJUST_BUDGET_INTERVAL == 0) {
                        adjustPowerBudget();
                        // loadBalance();
                    }
                    if (removed_queries % WITHDRAW_BUDGET_INTERVAL == 0) {
                        relinquishServiceInstance();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Every WITHDRAW_BUDGET_INTERVAL queries, if the queuing time of a service instance keeps zero and there are multiple instances of that service type available, stop one service instance and relinquish the power budget.
         */
        private void relinquishServiceInstance() {
            LOG.info("==================================================");
            LOG.info("start to recycle the service instances...");
            LOG.info("scanning the queuing time of the past " + WITHDRAW_BUDGET_INTERVAL + " queries");
            Map<String, List<Integer>> relinquishMap = new HashMap<String, List<Integer>>();
            for (String serviceType : serviceMap.keySet()) {
                List<ServiceInstance> serviceInstanceList = serviceMap.get(serviceType);
                if (serviceInstanceList.size() > 1) {
                    relinquishMap.put(serviceType, new LinkedList<Integer>());
                    for (int i = 0; i < serviceInstanceList.size(); i++) {
                        ServiceInstance serviceInstance = serviceInstanceList.get(i);
                        double queuingTime = 0;
                        int statisticLength = serviceInstance.getQueuing_latency().size();
                        if (statisticLength != 0) {
                            for (int index = 0; index < statisticLength && index < WITHDRAW_BUDGET_INTERVAL / serviceInstanceList.size(); index++) {
                                queuingTime += serviceInstance.getQueuing_latency().get(statisticLength - 1 - index);
                                if (queuingTime > 0) {
                                    break;
                                }
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
                        POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() + PowerModel.getPowerPerFreq(allocatedFreq)));
                        serviceInstanceList.remove(index);
                        // instead of shutting down, put the recycled service instance into the candidate list
                        Collections.sort(serviceInstanceList, new ServingTimeComparator(LATENCY_TYPE));
                        ServiceInstance fastestInstance = serviceInstanceList.get(serviceInstanceList.size() - 1);
                        fastestInstance.setLoadProb(instance.getLoadProb() + fastestInstance.getLoadProb());
                        instance.getQueuing_latency().clear();
                        instance.getServing_latency().clear();
                        candidateMap.get(serviceType).add(instance);
                        LOG.info("recycling the service instance running on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " and the power budget recycled is " + PowerModel.getPowerPerFreq(allocatedFreq));
                        LOG.info("the current global power budget is " + POWER_BUDGET.get().doubleValue());
                        if (serviceInstanceList.size() == 1) {
                            break;
                        }
                    }
                } else {
                    LOG.info("no service instance can be recycled, wait for the next recycle interval");
                }
            }
            LOG.info("==================================================");
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
            LOG.info("ranking the service instance based on the 99th queuing latency");
            List<ServiceInstance> serviceInstanceList = new LinkedList<ServiceInstance>();
            Percentile percentile = new Percentile();
            for (String serviceType : serviceMap.keySet()) {
                for (ServiceInstance instance : serviceMap.get(serviceType)) {
                    List<Double> servingLatencyStatistic = instance.getServing_latency();
                    List<Double> queuingLatencyStatistic = instance.getQueuing_latency();
                    double servingPercentileValue = 0;
                    double queuingPercentileValue = 0;
                    if (servingLatencyStatistic.size() != 0 && queuingLatencyStatistic.size() != 0) {
                        Double statNum = ADJUST_BUDGET_INTERVAL * instance.getLoadProb();
                        int statLength = servingLatencyStatistic.size();
                        double[] evaluateServingArray = new double[statNum.intValue()];
                        double[] evaluateQueuingArray = new double[statNum.intValue()];
                        double totalQueuing = 0;
                        double totalServing = 0;
                        for (int i = 0; i < statNum.intValue(); i++) {
                            evaluateServingArray[i] = servingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                            evaluateQueuingArray[i] = queuingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                            totalServing += servingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                            totalQueuing += queuingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                        }
                        instance.setQueuingTimeAvg(totalQueuing / statNum.intValue());
                        instance.setServingTimeAvg(totalServing / statNum.intValue());
                        servingPercentileValue = percentile.evaluate(evaluateServingArray, LATENCY_PERCENTILE);
                        queuingPercentileValue = percentile.evaluate(evaluateQueuingArray, LATENCY_PERCENTILE);
                        instance.setServingTimePercentile(servingPercentileValue);
                        instance.setQueuingTimePercentile(queuingPercentileValue);
                    }
                }
                serviceInstanceList.addAll(serviceMap.get(serviceType));
            }
            // sort the service instance based on the 99th queuing latency
            Collections.sort(serviceInstanceList, new ServingTimeComparator(LATENCY_TYPE));
            if ((serviceInstanceList.get(0).getQueuingTimePercentile() - serviceInstanceList.get(serviceInstanceList.size() - 1).getQueuingTimePercentile()) > ADJUST_THRESHOLD) {
                ServiceInstance slowestInstance = serviceInstanceList.get(0);
                LOG.info("the slowest service instance is " + slowestInstance.getServiceType() + " running on " + slowestInstance.getHostPort().getIp() + ":" + slowestInstance.getHostPort().getPort() + " with 99th queuing latency " + slowestInstance.getQueuingTimePercentile());
                // predict the tail latency of increasing frequency and launching new service instance
                BoostDecision decision = predictBoostDecision(slowestInstance);
                serviceInstanceList.remove(0);
                // relocate the power budget, if true perform the boosting decision
                if (relocatePowerBudget(serviceInstanceList, decision.getRequiredPower())) {
                    if (decision.getDecision().equalsIgnoreCase(BoostDecision.FREQUENCY_BOOST)) {
                        IPAService.Client client = null;
                        double oldFreq = slowestInstance.getCurrentFrequncy();
                        try {
                            client = TClient.creatIPAClient(slowestInstance.getHostPort().getIp(), slowestInstance.getHostPort().getPort());
                            client.updatBudget(decision.getFrequency());
                            slowestInstance.setCurrentFrequncy(decision.getFrequency());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TException e) {
                            e.printStackTrace();
                        }
                        LOG.info("adjusting the frequency of service instance " + slowestInstance.getServiceType() + " running on " + slowestInstance.getHostPort().getIp() + ":" + slowestInstance.getHostPort().getPort() + " from " + oldFreq + " ---> " + decision.getFrequency());
                    } else if (decision.getDecision().equalsIgnoreCase(BoostDecision.INSTANCE_BOOST)) {
                        launchServiceInstance(slowestInstance);
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
            /**
             * 1. calculate the tail latency of launching a new service instance as well as the required power
             * 2. assuming the new service instance would half the queuing time of the slowest instance but not changing the serving time
             * 3. evaluating the frequency equaling the power of a new instance
             * 4. calculate the tail latency of the whole service stage
             */
            // this is also the base power for increasing frequency
            requiredPowerInstance = PowerModel.getPowerPerFreq(instance.getCurrentFrequncy());
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
                LOG.info("the slowest service instance already running at maximum frequency");
                tailLatencyFreq = Double.MAX_VALUE;
                decision.setDecision(BoostDecision.INSTANCE_BOOST);
                decision.setRequiredPower(requiredPowerInstance);
                return decision;
            }
            double[] totalLatencyFreq = new double[ADJUST_BUDGET_INTERVAL];
            double[] totalLatencyInstance = new double[ADJUST_BUDGET_INTERVAL];
            // iterate through the service instance list to predict the tail latency
            int totalNum = 0;
            for (ServiceInstance historyInstance : serviceMap.get(instance.getServiceType())) {
                Double statNum = ADJUST_BUDGET_INTERVAL * historyInstance.getLoadProb();
                List<Double> servingLatencyStatistic = historyInstance.getServing_latency();
                List<Double> queuingLatencyStatistic = historyInstance.getQueuing_latency();
                int statLength = servingLatencyStatistic.size();
                for (int i = 0; i < statNum.intValue(); i++) {
                    if (historyInstance.getHostPort().equals(instance.getHostPort())) {
                        // frequency boosting
                        totalLatencyFreq[totalNum] = (servingLatencyStatistic.get(statLength - 1 - i).doubleValue() + queuingLatencyStatistic.get(statLength - 1 - i).doubleValue()) * (1 - speedup);
                        totalLatencyInstance[totalNum] = servingLatencyStatistic.get(statLength - 1 - i).doubleValue() + queuingLatencyStatistic.get(statLength - 1 - i).doubleValue() / 2.0;
                        // instance boosting
                    } else {
                        totalLatencyFreq[totalNum] = servingLatencyStatistic.get(statLength - 1 - i).doubleValue() + queuingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                        totalLatencyInstance[totalNum] = servingLatencyStatistic.get(statLength - 1 - i).doubleValue() + queuingLatencyStatistic.get(statLength - 1 - i).doubleValue();
                    }
                    totalNum++;
                }

            }
            tailLatencyFreq = percentile.evaluate(totalLatencyFreq, LATENCY_PERCENTILE);
            tailLatencyInstance = percentile.evaluate(totalLatencyInstance, LATENCY_PERCENTILE);
            if (tailLatencyFreq < tailLatencyInstance) {
                LOG.info("stage boosting decision: frequency boosting " + " with tail latency " + tailLatencyFreq + " compared to " + tailLatencyInstance + " if launching new instance");
                decision.setDecision(BoostDecision.FREQUENCY_BOOST);
                decision.setFrequency(freqRangeList.get(index - 1));
                decision.setRequiredPower(requiredPowerFreq);
            } else {
                LOG.info("stage boosting decision: instance boosting " + " with tail latency " + tailLatencyInstance + " compared to " + tailLatencyFreq + " if increasing the frequency");
                decision.setDecision(BoostDecision.INSTANCE_BOOST);
                decision.setRequiredPower(requiredPowerInstance);
            }
            // 1. calculate the tail latency of frequency boosting
            // a. using the speedup sheet to estimate the serving time speedup
            // b. reducing the 99th tail latency of both queuing and serving time

            // 2. calculate the tail latency of instance boosting
            // a. reducing the 99th tail latency of queuing time to 50%
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
            LOG.info("start to relocate the power budget...");
            if (serviceInstanceList.size() != 0) {
                if (POWER_BUDGET.get().doubleValue() >= requiredPower) {
                    POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() - requiredPower));
                    LOG.info("global power budget is enough to perform the stage boosting, current global power budget is " + POWER_BUDGET.get().doubleValue());
                    success = true;
                } else {
                    LOG.info("recycling power budget from existing service instances");
                    Map<ServiceInstance, Double> adjustInstance = new HashMap<ServiceInstance, Double>();
                    double sum = 0;
                    for (int i = (serviceInstanceList.size() - 1); i > -1; i--) {
                        ServiceInstance fastestInstance = serviceInstanceList.get(i);
                        double fastFreq = fastestInstance.getCurrentFrequncy();
                        double fastPower = PowerModel.getPowerPerFreq(fastFreq);
                        for (double j = (fastFreq - 0.1); j > (PowerModel.F0 - 0.1); j -= 0.1) {
                            if (sum + (fastPower - PowerModel.getPowerPerFreq(j) + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                                sum += fastPower - PowerModel.getPowerPerFreq(j);
                                adjustInstance.put(fastestInstance, j);
                                break;
                            }
                            if (j == PowerModel.F0) {
                                sum += fastPower - PowerModel.getPowerPerFreq(j);
                                adjustInstance.put(fastestInstance, PowerModel.F0);
                            }
                        }
                        if ((sum + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                            break;
                        }
                    }
                    if ((sum + POWER_BUDGET.get().doubleValue()) >= requiredPower) {
                        if (sum < requiredPower) {
                            POWER_BUDGET.set(new Double(POWER_BUDGET.get().doubleValue() - (requiredPower - sum)));
                        }
                    } else {
                        LOG.info("not enough power budget to recycle, keep the current power budget unadjusted...");
                        adjustInstance.clear();
                        return success;
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
        private void launchServiceInstance(ServiceInstance instance) {
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
            double loadProb = instance.getLoadProb() / 2.0;
            instance.setLoadProb(loadProb);
            String serviceType = instance.getServiceType();
            if (candidateMap.get(serviceType).size() != 0) {
                List<ServiceInstance> instanceList = candidateMap.get(serviceType);
                ServiceInstance candidateInstance = instanceList.get(0);
                instanceList.remove(0);
                candidateInstance.setServiceType(serviceType);
                candidateInstance.setCurrentFrequncy(instance.getCurrentFrequncy());
                candidateInstance.setLoadProb(loadProb);
                serviceMap.get(serviceType).add(instance);
                LOG.info("Launching new service instance " + serviceType + " on " + instance.getHostPort().getIp() + ":" + instance.getHostPort().getPort() + " with frequency " + instance.getCurrentFrequncy() + "GHz");
                LOG.info("updating the load probability of the parent service instance to " + loadProb);
            } else {
                LOG.info("The node manager has run out of service instance " + serviceType);
            }
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
    private class ServingTimeComparator implements Comparator<ServiceInstance> {
        private String latencyType;

        public ServingTimeComparator(String latencyType) {
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
                compareResult = Double.compare(instance2.getQueuingTimeAvg(), instance1.getQueuingTimeAvg());
            }
            return compareResult;
        }
    }
}
