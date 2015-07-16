package edu.umich.clarity.service.util;

/**
 * Created by hailong on 7/7/15.
 */
public class BoostDecision {
    public static final String FREQUENCY_BOOST = "frequency";
    public static final String INSTANCE_BOOST = "instance";
    public static final String ADAPTIVE_BOOST = "adaptive";
    private String decision;
    private double requiredPower;
    private double loadProb;
    private double frequency;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public double getRequiredPower() {
        return requiredPower;
    }

    public void setRequiredPower(double requiredPower) {
        this.requiredPower = requiredPower;
    }

    public double getLoadProb() {
        return loadProb;
    }

    public double getFrequency() {
        return frequency;
    }

    public void setLoadProb(double loadProb) {
        this.loadProb = loadProb;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }
}
