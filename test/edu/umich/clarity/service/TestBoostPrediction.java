package edu.umich.clarity.service;

import com.opencsv.CSVReader;
import edu.umich.clarity.service.util.PowerModel;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by hailong on 7/7/15.
 */
public class TestBoostPrediction {
    private static List<Double> qa_queuing_latency = new LinkedList<Double>();
    private static List<Double> qa_serving_latency = new LinkedList<Double>();
    private static Map<String, Double> freqSpeedup = new HashMap<String, Double>();
    private static final String[] FREQ_RANGE =

            {
                    "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0", "2.1", "2.2", "2.3", "2.4"
            };

    public static void main(String[] args) {
        Percentile percentile = new Percentile();
        int index = 0;
        try {
            CSVReader reader = new CSVReader(new FileReader("query_latency.csv"), ',', '\n', 1);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                if (index > 99)
                    break;
                else {
                    qa_queuing_latency.add(new Double(nextLine[6]));
                    qa_serving_latency.add(new Double(nextLine[7]));
                    index++;
                }
            }
        } catch (IOException ex) {

        }

        double mulMean = 1;
        int omitNum = 0;
        for (int i = 0; i < qa_queuing_latency.size(); i++) {
            if (qa_queuing_latency.get(i) > 5 && qa_serving_latency.get(i) > 5) {
                mulMean *= qa_queuing_latency.get(i) / qa_serving_latency.get(i);
                //System.out.println(mulMean);
            } else
                omitNum++;
            //
        }

        double geoAvg = Math.pow(mulMean, 1.0 / (qa_queuing_latency.size() - omitNum));

        System.out.println("queuing to serving ratio (geo mean): " + geoAvg);

        double queuingTotal = 0;
        double servingTotal = 0;
        for (int i = 0; i < qa_queuing_latency.size(); i++) {
            queuingTotal += qa_queuing_latency.get(i);
            servingTotal += qa_serving_latency.get(i);
        }
        System.out.println("queuing to serving ratio (arg mean): " + queuingTotal / servingTotal);
        // read the speedup sheet
        index = 0;
        try {
            CSVReader reader = new CSVReader(new FileReader("freq.csv"), ',', '\n', 1);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                if (index > 1) {
                    for (int i = 0; i < nextLine.length; i++) {
                        freqSpeedup.put(FREQ_RANGE[i], new Double(nextLine[i]));
                    }
                }
                index++;
            }
        } catch (IOException ex) {

        }
//        for (String key : freqSpeedup.keySet()) {
//            System.out.println(key + ":" + freqSpeedup.get(key));
//        }
        // evaluate launching a new instance
        String curFreq = "1.8";
        double requiredPower = PowerModel.getPowerPerFreq(new Double(curFreq));
        System.out.println("required power for a new service instance " + requiredPower);
        double[] evaluateArray = new double[qa_queuing_latency.size()];
        for (int i = 0; i < qa_queuing_latency.size(); i++) {
            evaluateArray[i] = qa_queuing_latency.get(i) / 2.0 + qa_serving_latency.get(i);
        }
        double tailLatencyInstance = percentile.evaluate(evaluateArray, 99);
        System.out.println("predicted tail latency for launching service instance is " + tailLatencyInstance + ":" + tailLatencyInstance / requiredPower);
        // evaluate increasing to higher frequency
        double basePower = PowerModel.getPowerPerFreq(1.8);
        index = 7;
        for (; index < FREQ_RANGE.length; index++) {
            if ((PowerModel.getPowerPerFreq(new Double(FREQ_RANGE[index])) - basePower) > requiredPower) {
                break;
            }
        }
        double requiredPowerFreq = PowerModel.getPowerPerFreq(new Double(FREQ_RANGE[index - 1])) - basePower;
        // System.out.println("chosen frequency level " + FREQ_RANGE[index - 1] + " with required power " + (PowerModel.getPowerPerFreq(new Double(FREQ_RANGE[index - 1])) - basePower));
        double speedup = freqSpeedup.get("1.8") - freqSpeedup.get(FREQ_RANGE[index - 1]);
        // System.out.println("the speedup is " + (freqSpeedup.get("1.8") - freqSpeedup.get(FREQ_RANGE[index - 1])));
        evaluateArray = new double[qa_queuing_latency.size()];
        for (int i = 0; i < qa_queuing_latency.size(); i++) {
            evaluateArray[i] = (qa_queuing_latency.get(i) + qa_serving_latency.get(i)) * (1 - speedup);
        }
        double tailLatencyFreq = percentile.evaluate(evaluateArray, 99);
        System.out.println("predicted tail latency for increasing the frequency of service instance is " + tailLatencyFreq + ":" + tailLatencyFreq / requiredPowerFreq);
    }
}
