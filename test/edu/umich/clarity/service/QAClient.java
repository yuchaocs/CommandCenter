package edu.umich.clarity.service;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.thrift.TException;

import edu.umich.clarity.service.util.TClient;
import edu.umich.clarity.thrift.IPAService;
import edu.umich.clarity.thrift.QuerySpec;

public class QAClient {

    public static IPAService.Client qaClient;

    public static String QA_SERVICE_IP = "clarity28.eecs.umich.edu";

    public static final int QA_SERVICE_PORT = 7788;

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        try {
            qaClient = TClient.creatIPAClient(QA_SERVICE_IP, QA_SERVICE_PORT);
            QuerySpec query = new QuerySpec();
            query.setBudget(1000);
            String input = "what is the speed of the light?";
            query.setInput(input.getBytes());
            List<Long> timestamp = new LinkedList<Long>();
            //query.setTimestamp(timestamp);
            qaClient.submitQuery(query);
        } catch (TException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
