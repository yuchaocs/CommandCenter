package edu.umich.clarity.service.util;

import java.text.DecimalFormat;

/**
 * Created by hailong on 7/1/15.
 */
public class PowerModel {
    public static final DecimalFormat sFormat = new DecimalFormat("#.#");
    public static final DecimalFormat dFormat = new DecimalFormat("#.##");
    public static final double P0 = Double.valueOf(dFormat.format(1.65));
    public static final double F0 = Double.valueOf(sFormat.format(1.2));
    public static final double FN = Double.valueOf(sFormat.format(2.5));

    public static double getPowerPerFreq(double freq) {
        double estimated_power = 0.0;
        if (Double.compare(freq, FN) < 0) {
            double dynP0 = P0 / 1.2;
            double staP0 = 0.2 * dynP0;
            double dynP = dynP0 * Math.pow(freq / F0, 2.7);
            double staP = staP0 * (freq / F0);
            estimated_power = dynP + staP;
        } else {
            estimated_power = 2.0;
        }
        return Double.valueOf(dFormat.format(estimated_power));
    }

    public static void main(String[] args) {
        for (double freq = 1.2; freq < 2.6; freq += 0.1) {
            System.out.println(Double.valueOf(dFormat.format(freq)) + ":" + PowerModel.getPowerPerFreq(freq));
        }
    }
}
