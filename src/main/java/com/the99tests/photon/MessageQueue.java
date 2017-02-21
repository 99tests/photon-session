package com.the99tests.photon;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class MessageQueue {
    private final static String QUEUE_NAME = "photon";
    private final static String EXCHANGE_NAME = "sneakers";
    private String runId;
    private String taskId;
    private Connection amqpConnection;
    private Channel amqpChannel;
    
    public MessageQueue(String runId, String taskId) throws IOException, TimeoutException {
    	this.runId=runId;
    	this.taskId=taskId;
    	
    	ConnectionFactory factory=new ConnectionFactory();
        factory.setHost("localhost");
        amqpConnection=factory.newConnection();
        amqpChannel=amqpConnection.createChannel();
    }

    public void sendStatus(String status, JSONObject object) {
        JSONObject message=new JSONObject();
        message.put("testrun_id", runId);
        message.put("task_id", taskId);
        message.put("status", status);
        message.put("details", object);

        try {
            amqpChannel.basicPublish(EXCHANGE_NAME, QUEUE_NAME, new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .deliveryMode(2)
                .build(),
                message.toString().getBytes()); 
        } catch(IOException e) {
            System.out.println("Failed to send message '"+status+"': "+e.toString());
        }
    }
    
    public void close() throws IOException, TimeoutException {
    	amqpChannel.close();
        amqpConnection.close();
    }
}
