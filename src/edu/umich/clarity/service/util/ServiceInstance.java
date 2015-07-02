package edu.umich.clarity.service.util;

import edu.umich.clarity.thrift.LatencySpec;
import edu.umich.clarity.thrift.THostPort;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by hailong on 7/2/15.
 */
public class ServiceInstance {
    private THostPort hostPort;
    private List<Double> queuing_latency;
    private List<Double> serving_latency;
    private double loadProb;

    public ServiceInstance() {
        this.queuing_latency = new LinkedList<Double>();
        this.serving_latency = new LinkedList<Double>();
        this.loadProb = 1;
    }

    public List<Double> getQueuing_latency() {
        return queuing_latency;
    }

    public List<Double> getServing_latency() {
        return serving_latency;
    }

    public THostPort getHostPort() {
        return hostPort;
    }

    public void setHostPort(THostPort hostPort) {
        this.hostPort = hostPort;
    }

    public void setQueuing_latency(List<Double> queuing_latency) {
        this.queuing_latency = queuing_latency;
    }

    public void setServing_latency(List<Double> serving_latency) {
        this.serving_latency = serving_latency;
    }

    public double getLoadProb() {
        return loadProb;
    }

    public void setLoadProb(double loadProb) {
        this.loadProb = loadProb;
    }
}
