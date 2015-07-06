package edu.umich.clarity.service;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

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
    }
}
