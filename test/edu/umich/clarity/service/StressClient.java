package edu.umich.clarity.service;

import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.thrift.IPAService;
import edu.umich.clarity.thrift.QuerySpec;
import edu.umich.clarity.thrift.SchedulerService;
import edu.umich.clarity.thrift.THostPort;
import org.apache.commons.math3.distribution.PoissonDistribution;

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
    private static final double mean = 100;
    private static final int num_client = 16;

    public static void main(String[] args) {
        StressClient client = new StressClient();
        client.genPoissonLoad(mean, num_client);
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
            new Thread(new ConcurrentClient(poi_dist.sample())).start();
        }
    }

    private class ConcurrentClient implements Runnable {
        private static final String ASR_SERVICE_IP = "clarity28.eecs.umich.edu";
        private static final int ASR_SERVICE_PORT = 9093;
        private IPAService.Client stressClient;
        private double nap_time;
        private File[] audioFiles;

        public ConcurrentClient(double nap_time) {
            this.nap_time = nap_time;
            File audioDir = new File(AUDIO_PATH);
            this.audioFiles = audioDir.listFiles();
        }

        @Override
        public void run() {
            try {
                stressClient = TClient.creatIPAClient(ASR_SERVICE_IP, ASR_SERVICE_PORT);
                QuerySpec query = new QuerySpec();
                query.setBudget(30000);
                List<Long> timestamp = new LinkedList<Long>();
                query.setTimestamp(timestamp);
                Random randomGen = new Random();
                query.setInput(Files.readAllBytes(audioFiles[randomGen.nextInt(audioFiles.length)].toPath()));
                Thread.sleep(Math.round(nap_time));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
