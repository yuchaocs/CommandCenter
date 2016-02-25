package edu.umich.clarity.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.thrift.TException;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.sun.faban.driver.HttpTransport;

/**
 * Created by hailong on 6/29/15.
 */
public class NutchStressClient {
    // common definition
    public static final String AUDIO_PATH = "/home/hailong/mulage-project/asr-mulage/input";
    private static final Logger LOG = Logger.getLogger(NutchStressClient.class);
    private static String SCHEDULER_IP = "clarity26.eecs.umich.edu";
    // private static String SCHEDULER_IP;
    private static int SCHEDULER_PORT = 8888;
    //private static int SCHEDULER_PORT;
    private static int WARMUP_COUNT = 39;
    //private static int WARMUP_COUNT;
    //private static int num_client = 10000;
    private static int num_client = 500;
    private static final String LOAD_TYPE_EXPONENTIAL = "exponential";
    private static final String LOAD_TYPE_POISSON = "poisson";
    private static final String LOAD_TYPE_BURST = "burst";
    private static String loadType = LOAD_TYPE_POISSON;
    // private static String loadType;
    // for poisson load
    //private static double poisson_mean = 1400;
    private static double poisson_mean;
    private static String POISSON_SAMPLE_FILE = "poisson_sample_.3_1000.csv";
    //private static String POISSON_SAMPLE_FILE = "poisson_sample_.8_1000.csv";
    //private static String POISSON_SAMPLE_FILE = "poisson_sample_1.4_10000.csv";
    // private static String POISSON_SAMPLE_FILE;

    // for burst load
    //private static double burst_high_mean = 600;
    private static double burst_high_mean;
    //private static double burst_low_mean = 1000;
    private static double burst_low_mean;
    //private static String BURST_HIGH_SAMPLE_FILE = "poisson_sample_.9_1000.csv";
    private static String BURST_HIGH_SAMPLE_FILE;
    //private static String BURST_LOW_SAMPLE_FILE = "poisson_sample_1.4_1000.csv";
    private static String BURST_LOW_SAMPLE_FILE;
    //private static int BURST_SWITCH_NUM = 200;
    private static int BURST_SWITCH_NUM;
    //private static float BURST_RATIO = 0.5f;
    private static float BURST_RATIO;
    private static String OPERATION = "load";
    //private static String OPERATION = "sample";
    //private static String OPERATION;

    public static final String FRONT_END = "http://clarity26.eecs.umich.edu:8080";
    public static final String SEARCH_TERM_FILE = "terms_en.out";
    public static final String RANDOM_SEARCH_TERM_FILE = "search_terms_1000.csv";

    public NutchStressClient() {
        // PropertyConfigurator.configure(System.getProperty("user.dir") + File.separator + "log4j.properties");
        LOG.info("the operation mode is " + OPERATION);
        LOG.info("the load type is " + loadType + " with warm up count " + WARMUP_COUNT);
        LOG.info("the number of queries will be generated is " + num_client);
    }

    /**
     * @param args args[0]: scheduler_ip, args[1]: scheduler_port, args[2]: distribution_file, args[3]: query_num, args[4]: warm_up_query
     */
    public static void main(String[] args) {
        /*
        if (args.length == 11) {
            SCHEDULER_IP = args[0];
            SCHEDULER_PORT = Integer.valueOf(args[1]);
            POISSON_SAMPLE_FILE = args[2];
            num_client = Integer.valueOf(args[3]);
            WARMUP_COUNT = Integer.valueOf(args[4]);
            loadType = args[5];
            OPERATION = args[6];
            BURST_HIGH_SAMPLE_FILE = args[7];
            BURST_LOW_SAMPLE_FILE = args[8];
            BURST_SWITCH_NUM = Integer.valueOf(args[9]);
            BURST_RATIO = Float.valueOf(args[10]);
        }
        */

        NutchStressClient client = new NutchStressClient();
        if (OPERATION.equalsIgnoreCase("load")) {
            // LOG.info("start to warm up the services...");
            System.out.println("start to warm up the services...");
            client.warmupNutch();
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
            // LOG.info("start to evaluate the latency with " + loadType + " distribution");
            System.out.println("start to evaluate the latency with " + loadType + " distribution");
            if (loadType.equalsIgnoreCase(NutchStressClient.LOAD_TYPE_BURST)) {
                client.genBurstLoad(num_client);
            } else if (loadType.equalsIgnoreCase(NutchStressClient.LOAD_TYPE_POISSON)) {
                client.genPoissonNutchLoad(num_client);
            }
        } else if (OPERATION.equalsIgnoreCase("sample")) {
            LOG.info("start to generate sample files with " + loadType + " distribution");
            if (loadType.equalsIgnoreCase(NutchStressClient.LOAD_TYPE_BURST)) {
                client.genBurstSamples(burst_high_mean, burst_low_mean, num_client / 2);
            } else if (loadType.equalsIgnoreCase(NutchStressClient.LOAD_TYPE_POISSON)) {
                client.genPoissonSamples(poisson_mean, num_client);
            }
        }
    }

    public void genBurstLoad(int num_client) {
        String NEXT_STAGE = "asr";
        List highSampleEntries = null;
        List lowSampleEntries = null;
        try {
            CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    BURST_HIGH_SAMPLE_FILE), ',');
            highSampleEntries = reader.readAll();
            reader.close();
            reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    BURST_LOW_SAMPLE_FILE), ',');
            lowSampleEntries = reader.readAll();
            reader.close();
            LOG.info("using the high sample file: " + BURST_HIGH_SAMPLE_FILE);
            LOG.info("using the low sample file: " + BURST_LOW_SAMPLE_FILE);
        } catch (IOException ex) {

        }
        String[] highSample = (String[]) highSampleEntries.get(0);
        String[] lowSample = (String[]) lowSampleEntries.get(0);
        int evaluateLength = (lowSample.length + highSample.length) > num_client ? num_client : (lowSample.length + highSample.length);
        boolean sendingHighSample = false;
        int lowSampleCounter = 0;
        int highSampleCounter = 0;
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
                if ((i % BURST_SWITCH_NUM) < Math.round(BURST_SWITCH_NUM * BURST_RATIO)) {
                    LOG.info("Sending high load query " + i + " and sleep for " + Integer.valueOf(highSample[highSampleCounter]) + " ms");
                    Thread.sleep(Integer.valueOf(highSample[highSampleCounter]));
                    highSampleCounter++;
                } else {
                    LOG.info("Sending low load query " + i + " and sleep for " + Integer.valueOf(lowSample[lowSampleCounter]) + " ms");
                    Thread.sleep(Integer.valueOf(lowSample[lowSampleCounter]));
                    lowSampleCounter++;
                }
//                if (i % BURST_SWITCH_NUM == 0) {
//                    sendingHighSample = !sendingHighSample;
//                }
//                if (sendingHighSample) {
//                    LOG.info("Sending high load query " + i + " and sleep for " + Integer.valueOf(highSample[highSampleCounter]) + " ms");
//                    Thread.sleep(Integer.valueOf(highSample[highSampleCounter]));
//                    highSampleCounter++;
//                } else {
//                    LOG.info("Sending low load query " + i + " and sleep for " + Integer.valueOf(lowSample[lowSampleCounter]) + " ms");
//                    Thread.sleep(Integer.valueOf(lowSample[lowSampleCounter]));
//                    lowSampleCounter++;
//                }
            } catch (TException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void warmupNutch() {
        String search_term = null;
        try {
            FileReader fileReader = new FileReader(SEARCH_TERM_FILE);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            int counter = 0;
            while ((search_term = bufferedReader.readLine()) != null && counter < WARMUP_COUNT) {
                String url = FRONT_END + "/search.jsp?query=" + search_term + "&lang=en";
                HttpTransport http = HttpTransport.newInstance();
                http.fetchURL(url);
                counter++;
            }
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {

        }

    }

    /**
     * Generate the load that follows Poisson distribution.
     */
    public void genPoissonNutchLoad(int num_client) {
        // String NEXT_STAGE = "tokenizer";
        List sampleEntries = null;
        List searchEntries = null;
        try {
            CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    POISSON_SAMPLE_FILE), ',');
            sampleEntries = reader.readAll();
            LOG.info("using the sample file: " + POISSON_SAMPLE_FILE);
            reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    RANDOM_SEARCH_TERM_FILE), ',');
            searchEntries = reader.readAll();
            LOG.info("using the search term file: " + RANDOM_SEARCH_TERM_FILE);
        } catch (IOException ex) {

        }
        String[] sample = (String[]) sampleEntries.get(0);
        String[] searchSample = (String[]) searchEntries.get(0);
        int evaluateLength = sample.length > num_client ? num_client : sample.length;
        for (int i = 0; i < evaluateLength; i++) {
            try {
                String url = FRONT_END + "/search.jsp?query=" + searchSample[i] + "&lang=en";
                HttpTransport http = HttpTransport.newInstance();
                http.fetchURL(url);
                LOG.info("Sending query " + i + " and sleep for " + Integer.valueOf(sample[i]) + " ms");
                System.out.println("Sending query " + i + " and sleep for " + Integer.valueOf(sample[i]) + " ms");
                Thread.sleep(Integer.valueOf(sample[i]));
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Generate the load that follows Poisson distribution.
     */
    public void genPoissonLoad(int num_client) {
        String NEXT_STAGE = "tokenizer";
        List sampleEntries = null;
        try {
            CSVReader reader = new CSVReader(new FileReader(System.getProperty("user.dir") + File.separator +
                    POISSON_SAMPLE_FILE), ',');
            LOG.info("using the sample file: " + POISSON_SAMPLE_FILE);
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
                    LOG.info("Sending query " + i + " and sleep for " + Integer.valueOf(sample[i]) + " ms");
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


    private void genPoissonSamples(double mean, int sampleNum) {
        PoissonDistribution poi_dist = new PoissonDistribution(mean);
        try {
            CSVWriter sampleWriter = new CSVWriter(new FileWriter(POISSON_SAMPLE_FILE), ',', CSVWriter.NO_QUOTE_CHARACTER);
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

    private void genBurstSamples(double high_mean, double low_mean, int sampleNum) {
        PoissonDistribution burstLowDist = new PoissonDistribution(low_mean);
        PoissonDistribution burstHighDist = new PoissonDistribution(high_mean);
        try {
            CSVWriter lowSampleWriter = new CSVWriter(new FileWriter(BURST_LOW_SAMPLE_FILE), ',', CSVWriter.NO_QUOTE_CHARACTER);
            CSVWriter highSampleWriter = new CSVWriter(new FileWriter(BURST_HIGH_SAMPLE_FILE), ',', CSVWriter.NO_QUOTE_CHARACTER);
            ArrayList<String> csvEntry = new ArrayList<String>();
            for (int i = 0; i < sampleNum; i++) {
                csvEntry.add(burstLowDist.sample() + "");
            }
            lowSampleWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
            lowSampleWriter.flush();
            lowSampleWriter.close();
            csvEntry.clear();
            for (int i = 0; i < sampleNum; i++) {
                csvEntry.add(burstHighDist.sample() + "");
            }
            highSampleWriter.writeNext(csvEntry.toArray(new String[csvEntry.size()]));
            highSampleWriter.flush();
            highSampleWriter.close();
        } catch (IOException ex) {

        }
    }

    /**
     * Generate the load that follows Poisson distribution.
     *
     * @param mean       the poisson_mean time interval to submit each query
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
