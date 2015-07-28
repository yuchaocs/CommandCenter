package edu.umich.clarity.service;

import com.opencsv.CSVReader;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by hailong on 7/6/15.
 */
public class TestPercentile {
    private static final double LATENCY_PERCENTILE = 99;

    public static void main(String[] args) {
        String s = null;
        try {
            // run the Unix "ps -ef" command
            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec("ps -ef");
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));
            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            // read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
        } catch (IOException e) {
            System.out.println("exception happened - here's what I know: ");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void oneTest() {
        Percentile percentile = new Percentile(LATENCY_PERCENTILE);
        double[] evaluateArray = {12153, 3352, 14975, 3558, 5079, 13970, 2701, 11271, 9539, 6814, 10379, 12810, 6956, 8395, 4753};
        percentile.setData(evaluateArray);
        double percentileValue = percentile.evaluate();
        System.out.println("percentile value: " + percentileValue);
        try {
            CSVReader reader = new CSVReader(new FileReader("query_latency.csv"), ',', '\n', 1);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // nextLine[] is an array of values from the line
                System.out.print(nextLine.length + " ");
                System.out.print(nextLine[0]);
            }
        } catch (IOException ex) {

        }
        List<Double> freqRangeList = new LinkedList<Double>();
        DecimalFormat dFormat = new DecimalFormat("#.#");
        for (double i = 1.2; i < 2.5; i += 0.1) {
            freqRangeList.add(Double.valueOf((dFormat.format(i))));
        }
        for (Double freq : freqRangeList) {
            System.out.println(freq);
        }
    }
}