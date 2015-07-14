package edu.umich.clarity.service.ipa;

import edu.umich.clarity.service.util.QueryComparator;
import edu.umich.clarity.service.util.TServers;
import edu.umich.clarity.thrift.*;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class QAService implements IPAService.Iface {
    private static final String SERVICE_NAME = "qa";
    private static final String SERVICE_IP = "localhost";
    private static final int SERVICE_PORT = 7788;
    private static final String SCHEDULER_IP = "localhost";
    private static final int SCHEDULER_PORT = 8888;
    private static final Logger LOG = Logger.getLogger(QAService.class);
    private static List<THostPort> service_list;
    private static String DOWNSTREAM_SERVICE_IP;
    private static int DOWNSTREAM_SERVICE_PORT;
    private static SchedulerService.Client scheduler_client;
    private static IPAService.Client service_client;
    private double budget = 100;
    private BlockingQueue<QuerySpec> queryQueue = new PriorityBlockingQueue<QuerySpec>(
            500, new QueryComparator<QuerySpec>());

    public static void main(String[] args) throws IOException, TException {
        QAService qaService = new QAService();
        IPAService.Processor<IPAService.Iface> processor = new IPAService.Processor<IPAService.Iface>(
                qaService);
        TServers.launchSingleThreadThriftServer(SERVICE_PORT, processor);
        LOG.info("starting " + SERVICE_NAME + " service at " + SCHEDULER_IP
                + ":" + SCHEDULER_PORT);
        // qaService.initialize();
    }

    @Override
    public int reportQueueLength() throws TException {
        return 0;
    }
    //    public void initialize() {
//        // RegReply regReply = null;
//        try {
//            scheduler_client = TClient.creatSchedulerClient(SCHEDULER_IP,
//                    SCHEDULER_PORT);
//        } catch (IOException ex) {
//            LOG.error("Error creating thrift scheduler client"
//                    + ex.getMessage());
//        }
//        try {
//            THostPort hostPort = new THostPort(SERVICE_IP, SERVICE_PORT);
//            RegMessage regMessage = new RegMessage(SERVICE_NAME, hostPort,
//                    budget);
//            LOG.info("registering to command center runnig at " + SCHEDULER_IP
//                    + ":" + SCHEDULER_PORT);
//            LOG.info("service stage " + SERVICE_NAME
//                    + " successfully registered itself at " + SERVICE_IP + ":"
//                    + SERVICE_PORT);
//        try {
//            if (service_list != null && service_list.size() != 0) {
//                THostPort hostPort = randomAssignService(service_list);
//                DOWNSTREAM_SERVICE_IP = hostPort.getIp();
//                DOWNSTREAM_SERVICE_PORT = hostPort.getPort();
//                service_client = TClient.creatIPAClient(DOWNSTREAM_SERVICE_IP,
//                        DOWNSTREAM_SERVICE_PORT);
//            } else {
//                LOG.info("no downstream service candidates are found by command center");
//                if (regReply.final_stage) {
//                    LOG.info("reaching the final service stage of the workflow");
//                }
//            }
//        } catch (IOException ex) {
//            LOG.error("Error creating thrift scheduler client"
//                    + ex.getMessage());
//        }
//        new Thread(new processQueryRunnable(regReply.final_stage)).start();
//    }

    /**
     * randomly choose the downstream service candidates
     *
     * @param service_list
     * @return the chosen service candidate
     */
    private THostPort randomAssignService(List<THostPort> service_list) {
        THostPort hostPort;
        Random rand = new Random();
        hostPort = service_list.get(rand.nextInt(service_list.size()));
        return hostPort;
    }

    @Override
    public void stealParentInstance(THostPort hostPort) throws TException {

    }

    @Override
    public List<QuerySpec> stealQueuedQueries() throws TException {
        return null;
    }

    @Override
    public void updatBudget(double budget) throws TException {
        this.budget = budget;
        LOG.info("service " + SERVICE_NAME + " at " + SERVICE_IP + ":"
                + SERVICE_PORT + " update its budget to " + budget);
    }

    @Override
    public void submitQuery(QuerySpec query) throws TException {
        // timestamp the query when it is enqueued (start)
        LatencySpec latencySpec = new LatencySpec();
        query.getTimestamp().add(latencySpec);
        try {
            queryQueue.put(query);
        } catch (InterruptedException e) {
            LOG.error("failed to enqueue the query " + e.getMessage());
        }
    }

    private class processQueryRunnable implements Runnable {
        private boolean final_stage;

        public processQueryRunnable(boolean final_stage) {
            // initialize the processing logic instance
            this.final_stage = final_stage;
        }

        @Override
        public void run() {
            LOG.info("starting the helper thread to scan the incoming queue to process the query");
            while (true) {
                try {
                    QuerySpec query = queryQueue.take();
                    // timestamp the query when it is enqueued (end)
                    // this is also the timestamp for the start of serving
                    // (start)
                    long queuing_start_time = query.getTimestamp().get(
                            query.getTimestamp().size() - 1).getQueuing_start_time();
                    long process_start_time = System.currentTimeMillis();
                    query.getTimestamp().add(query.getTimestamp().get(
                            query.getTimestamp().size() - 1).setServing_start_time(process_start_time));
                    LOG.info("the queuing time for the query is "
                            + (process_start_time - queuing_start_time) + "ms");
                    /**
                     * TODO 1. use the latency model to predict the serving time
                     * 2. based on the queuing and serving time to see if the
                     * query processing would be within the QoS budget 3. based
                     * on the DVFS and query performance model, trading off the
                     * latency and energy efficiency 4. set the DVFS to the
                     * appropriate level 5. logic to process the query 6. change
                     * DVFS to the base setting 7. update the budget of queries
                     * waiting in the queue
                     */
                    Thread.sleep(100);
                    // transform the byte buffer to string
                    // new String(query.bufferForInput().array());
                    long process_end_time = System.currentTimeMillis();
                    LOG.info("the serving time for the query is "
                            + (process_end_time - process_start_time) + "ms");
                    // timestamp the query when it is served (end)
                    query.getTimestamp().add(query.getTimestamp().get(
                            query.getTimestamp().size() - 1).setServing_end_time(process_end_time));
                    // update the query budget
                    query.setBudget(query.getBudget()
                            - (process_end_time - process_start_time));
                    // update the budget of all queries waiting in the queue
                    if (queryQueue.size() != 0) {
                        List<QuerySpec> waiting_queries = new ArrayList<QuerySpec>();
                        queryQueue.drainTo(waiting_queries);
                        for (QuerySpec waiting_query : waiting_queries) {
                            waiting_query.setBudget(waiting_query.getBudget()
                                    - (process_end_time - process_start_time));
                        }
                        queryQueue.addAll(waiting_queries);
                    }
                    if (final_stage) {
                        LOG.info("enqueing query to command center at "
                                + SCHEDULER_IP + ":" + SCHEDULER_PORT);
                        try {
                            scheduler_client.enqueueFinishedQuery(query);
                        } catch (TException e) {
                            LOG.error("Error failed to submit query to command center at "
                                    + SCHEDULER_IP
                                    + ":"
                                    + SCHEDULER_PORT
                                    + e.getMessage());
                        }
                    } else {
                        LOG.info("submitting query in downstream service stage at "
                                + DOWNSTREAM_SERVICE_IP
                                + ":"
                                + DOWNSTREAM_SERVICE_PORT);
                        try {
                            service_client.submitQuery(query);
                        } catch (TException e) {
                            LOG.error("Error failed to submit query to downstream service at "
                                    + DOWNSTREAM_SERVICE_IP
                                    + ":"
                                    + DOWNSTREAM_SERVICE_PORT + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    LOG.error("failed to pop the query from the queue"
                            + e.getMessage());
                }
            }
        }
    }
}
