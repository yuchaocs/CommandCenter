package edu.umich.clarity.service;

import com.opencsv.CSVReader;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.io.FileReader;
import java.io.IOException;

/**
 * Created by hailong on 7/6/15.
 */
public class TestPercentile {
    private static final double LATENCY_PERCENTILE = 99;

    public static void main(String[] args) {
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
    }
}