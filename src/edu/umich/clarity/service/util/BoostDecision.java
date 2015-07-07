package edu.umich.clarity.service.util;

/**
 * Created by hailong on 7/7/15.
 */
public class BoostDecision {
    public static final String FREQUENCY_BOOST = "frequency";
    public static final String INSTANCE_BOOST = "instance";
    private String decision;
    private double requiredPower;

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
}
