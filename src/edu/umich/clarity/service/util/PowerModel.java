package edu.umich.clarity.service.util;

/**
 * Created by hailong on 7/1/15.
 */
public class PowerModel {

    public static final double P0 = 1.65;
    public static final double F0 = 1.2;
    public static final double FN = 2.4;

    public static double getPowerPerFreq(double freq) {
        double estimated_power = 0.0;
        double dynP0 = P0 / 1.2;
        double staP0 = 0.2 * dynP0;
        double dynP = dynP0 * Math.pow(freq / F0, 2.7);
        double staP = staP0 * (freq / F0);
        estimated_power = dynP + staP;
        return estimated_power;
    }

    public static void main(String[] args) {
        for (double freq = 1.2; freq < 2.5; freq += 0.1) {
            System.out.printf("%.1f ", freq);
            System.out.printf("%.2f\n", PowerModel.getPowerPerFreq(freq));
        }
    }
}
