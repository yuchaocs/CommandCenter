package edu.umich.clarity.service;

import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.thrift.*;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.thrift.TException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by hailong on 6/29/15.
 */
public class StressClient {
    public static final String AUDIO_PATH = "/home/hailong/mulage-project/asr-mulage/input";
    private static final double mean = 1000;
    private static final int num_client = 500;

    public static void main(String[] args) {
        StressClient client = new StressClient();
        // client.genPoissonLoad(mean, num_client);
        client.genPoissonLoad();
    }

    /**
     * Generate the load that follows Poisson distribution.
     */
    public void genPoissonLoad() {
        String NEXT_STAGE = "asr";
        String SCHEDULER_IP = "localhost";
        int SCHEDULER_PORT = 8888;
        PoissonDistribution poi_dist = new PoissonDistribution(this.mean);
        for (int i = 0; i < this.num_client; i++) {
            try {
                SchedulerService.Client schedulerClient = TClient.creatSchedulerClient(SCHEDULER_IP, SCHEDULER_PORT);
                THostPort hostPort = schedulerClient.consultAddress(NEXT_STAGE);
                IPAService.Client serviceClient = TClient.creatIPAClient(hostPort.getIp(), hostPort.getPort());
                QuerySpec query = new QuerySpec();
                query.setName(Integer.toString(i));
                query.setBudget(30000);
                List<LatencySpec> timestamp = new LinkedList<LatencySpec>();
                query.setTimestamp(timestamp);
                serviceClient.submitQuery(query);
                System.out.println("Sending query " + i);
                Thread.sleep(Math.round(poi_dist.sample()));
            } catch (TException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        private IPAService.Client serviceClient;
        private SchedulerService.Client schedulerClient;
        private double nap_time;
        private File[] audioFiles;
        private String name;

        public ConcurrentClient(double nap_time, String name) {
            this.nap_time = nap_time;
            this.name = name;
            File audioDir = new File(AUDIO_PATH);
            this.audioFiles = audioDir.listFiles();
            try {
                schedulerClient = TClient.creatSchedulerClient(SCHEDULER_IP, SCHEDULER_PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                THostPort hostPort = schedulerClient.consultAddress(NEXT_STAGE);
                serviceClient = TClient.creatIPAClient(hostPort.getIp(), hostPort.getPort());
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
