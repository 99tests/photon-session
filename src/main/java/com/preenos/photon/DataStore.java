package com.preenos.photon;

import redis.clients.jedis.Jedis;

public class DataStore {
    private Jedis jedis;
    private String taskId;

    
    public DataStore(String taskId) {
    	this.taskId=taskId;
		
    }
    
    private void connect() {
    	jedis = new Jedis("localhost");
    }
    
    private void disconnect() {
    	jedis.close();
    }
    
    
    public String getConfigProperty(String property) {
    	this.connect();
    	String value=jedis.hget("PHOTON_CONFIG", property);
    	this.disconnect();
        return value;
    }
    
    public void setTaskProperty(String property, String value) {
    	this.connect();
    	jedis.hset(taskId, property, value);
    	this.disconnect();
    }
    
    public String getTaskProperty(String property) {
    	this.connect();
    	String value=jedis.hget(taskId, property);
    	this.disconnect();
    	return value; 
    }
    
    public void addTaskStage(String stage) {
    	this.connect();
	    String stages=jedis.hget(taskId, "stages");
	    if(stages==null) {
	        jedis.hset(taskId, "stages", stage);
	    } else {
	        jedis.hset(taskId, "stages", stages+", "+stage);
	    }
	    this.disconnect();
    }
}
