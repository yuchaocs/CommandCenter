package edu.umich.clarity.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.thrift.TException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by hailong on 6/29/15.
 */
public class StressClient {

    public static final String AUDIO_PATH = "/home/hailong/mulage-project/asr-mulage/input";
    private static final Logger LOG = Logger.getLogger(StressClient.class);
    private static double mean = 1500;
    private static int num_client = 1000;
    private static String SAMPLE_FILE = "poisson_sample_1.5_1000.csv";
    private static String SCHEDULER_IP = "localhost";
    private static int SCHEDULER_PORT = 8888;
    private static int WARMUP_COUNT = 20;

    public StressClient() {
        PropertyConfigurator.configure(System.getProperty("user.dir") + File.separator + "log4j.properties");
    }

    /**
     * @param args args[0]: scheduler_ip, args[1]: scheduler_port, args[2]: distribution_file, args[3]: query_num, args[4]: warm_up_query
     */
    public static void main(String[] args) {
        if (args.length == 5) {
            SCHEDULER_IP = args[0];
            SCHEDULER_PORT = Integer.valueOf(args[1]);
            SAMPLE_FILE = args[2];
            num_client = Integer.valueOf(args[3]);
            WARMUP_COUNT = Integer.valueOf(args[4]);
        }
        StressClient client = new StressClient();
//        client.genPoissonLoad(mean, num_client);
//        client.genPoissonLoad();
//        client.stablizePoissonSamples(mean, num_client);
        LOG.info("start to warm up the services...");
        client.genPoissonLoad(WARMUP_COUNT);
        SchedulerService.Client schedulerClient = null;
        try {
            TClient clientDelegate = new TClient();
            schedulerClient = clientDelegate.createSchedulerClient(SCHEDULER_IP, SCHEDULER_PORT);
            int finishedQueries = 0;
            while ((finishedQueries = schedulerClient.warmupCount()) != WARMUP_COUNT) {
                LOG.info("The number of warmed up queries is " + finishedQueries + ", sleep for 2s to check again");
                Thread.sleep(2000);
            }
            clientDelegate.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ex) {

        } catch (TException ex) {

        }
        LOG.info("start to evaluate the tail latency...");
        //TClient.close();
        client.genPoissonLoad(num_client);
    }

    /**
     * Generate the load that follows Poisson distribution.
     */
    public void genPoissonLoad(int num_client) {
        String NEXT_STAGE = "asr";
        List sampleEntries = null;
        try {
            CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    SAMPLE_FILE), ',');
            sampleEntries = reader.readAll();
        } catch (IOException ex) {

        }
        for (Object entry : sampleEntries) {
            String[] sample = (String[]) entry;
            int evaluateLength = sample.length > num_client ? num_client : sample.length;
            for (int i = 0; i < evaluateLength; i++) {
                try {
                    TClient clientDelegate = new TClient();
                    SchedulerService.Client schedulerClient = clientDelegate.createSchedulerClient(SCHEDULER_IP, SCHEDULER_PORT);
                    THostPort hostPort = schedulerClient.consultAddress(NEXT_STAGE);
                    clientDelegate.close();
                    clientDelegate = new TClient();
                    IPAService.Client serviceClient = clientDelegate.createIPAClient(hostPort.getIp(), hostPort.getPort());
                    QuerySpec query = new QuerySpec();
                    query.setName(Integer.toString(i));
                    query.setBudget(30000);
                    List<LatencySpec> timestamp = new LinkedList<LatencySpec>();
                    query.setTimestamp(timestamp);
                    serviceClient.submitQuery(query);
                    clientDelegate.close();
                    LOG.info("Sending query " + i);
                    Thread.sleep(Integer.valueOf(sample[i]));
                } catch (TException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void stablizePoissonSamples(double mean, int sampleNum) {
        PoissonDistribution poi_dist = new PoissonDistribution(mean);
        try {
            CSVWriter sampleWriter = new CSVWriter(new FileWriter(SAMPLE_FILE), ',', CSVWriter.NO_QUOTE_CHARACTER);
            ArrayList<String> csvEntry = new ArrayList<String>();
            for (int i = 0; i < sampleNum; i++) {
                csvEntry.add(poi_dist.sample() + "");
            }
            sampleWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
            sampleWriter.flush();
            sampleWriter.close();
        } catch (IOException ex) {

        }
    }

    /**
     * Generate the load that follows Poisson distribution.
     *
     * @param mean       the mean time interval to submit each query
     * @param num_client that submitting the query concurrently
     */
    public void genPoissonLoad(double mean, int num_client) {
        PoissonDistribution poi_dist = new PoissonDistribution(mean);
        for (int i = 0; i < num_client; i++) {
            new Thread(new ConcurrentClient(poi_dist.sample(), Integer.toString(i))).start();
        }
    }

    private class ConcurrentClient implements Runnable {
        private static final String NEXT_STAGE = "asr";
        private static final String SCHEDULER_IP = "localhost";
        private static final int SCHEDULER_PORT = 8888;
        private double nap_time;
        private File[] audioFiles;
        private String name;

        public ConcurrentClient(double nap_time, String name) {
            this.nap_time = nap_time;
            this.name = name;
            File audioDir = new File(AUDIO_PATH);
            this.audioFiles = audioDir.listFiles();
        }

        @Override
        public void run() {
            try {
                TClient clientDelegate = new TClient();
                SchedulerService.Client schedulerClient = clientDelegate.createSchedulerClient(SCHEDULER_IP, SCHEDULER_PORT);
                THostPort hostPort = schedulerClient.consultAddress(NEXT_STAGE);
                clientDelegate.close();
                clientDelegate = new TClient();
                IPAService.Client serviceClient = clientDelegate.createIPAClient(hostPort.getIp(), hostPort.getPort());
                QuerySpec query = new QuerySpec();
                query.setName(this.name);
                query.setBudget(30000);
                List<LatencySpec> timestamp = new LinkedList<LatencySpec>();
                query.setTimestamp(timestamp);
                Random randomGen = new Random();
                int randIndex = randomGen.nextInt(audioFiles.length);
                query.setInput(Files.readAllBytes(audioFiles[randIndex].toPath()));
                Thread.sleep(Math.round(nap_time));
                System.out.println("Sending intput file " + audioFiles[randIndex].getName());
                serviceClient.submitQuery(query);
                clientDelegate.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (TException e) {
                e.printStackTrace();
            }
        }
    }
}
