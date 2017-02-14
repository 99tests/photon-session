package com.preenos.photon;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.preenos.photon.platforms.PlatformManager;
import com.preenos.photon.platforms.PhotonPlatformManagerFactory;
import com.preenos.photon.platforms.UnsupportedConfigException;

import redis.clients.jedis.Jedis;

public class PhotonDriver extends RemoteWebDriver {
	private static DataStore store;
    private static String HUB;
    private static String taskId;
    private static String runId;
    private static String dataDir;
    private static String platform;
    private static String browser;
    private static PlatformManager platformManager;
    private static MessageQueue messageQueue;
    
    public static boolean isLocal() {
    	String taskId=System.getProperty("photonTaskId");
    	return (taskId==null);
    }
    
    public static PhotonDriver createDriver(URL hub, Capabilities desiredCapabilities) throws IOException, TimeoutException, UnsupportedConfigException {
    	if(isLocal()) {
    		return new PhotonDriver(hub, desiredCapabilities);
    	} else {
    		setupPhotonEnvironment();
    		
    		platformManager=PhotonPlatformManagerFactory.getPlatformManager(browser, platform);
    		
    		URL hubUrl=new URL("http://"+HUB+":4444/wd/hub");
    		desiredCapabilities=getTaskCapabilities(hubUrl);
    		
    		PhotonDriver driver=new PhotonDriver(hubUrl, desiredCapabilities);
    		platformManager.setupDriver(driver);
    		driver.sendSessionInfo();
    		return driver;
    	}
    }
    
    private PhotonDriver(URL hub, Capabilities desiredCapabilities) {
    	super(hub, desiredCapabilities);
    	this.beginTest();
    }
    
    public static void setupPhotonEnvironment() throws IOException, TimeoutException {
    	taskId = System.getProperty("photonTaskId");
    	if(taskId==null) {  		
    		return;
    	}
    	
		store = new DataStore(taskId);
        HUB = store.getConfigProperty("HUB");
        runId = store.getTaskProperty("testrun_id");
        
        String config=store.getTaskProperty("platform");
        String params[]=config.split("_");
        browser=params[0];
        platform=params[1];

        dataDir="/tmp/"+taskId;
        
        messageQueue=new MessageQueue(runId, taskId);
    }
    
    public static DesiredCapabilities getTaskCapabilities(URL hubUrl) throws MalformedURLException, UnsupportedConfigException {
    	String config=store.getTaskProperty("platform");
        String params[]=config.split("_");
        String browser=params[0];
        String platform=params[1];
        
        return platformManager.setupCapabilities(hubUrl, platform, store);
    }
    
    public void sendSessionInfo() {
    	store.setTaskProperty("session_id", this.getSessionId().toString());
    	try {
	        HttpResponse<JsonNode> jsonResponse = 
	        		Unirest.get("http://"+HUB+":4444/grid/api/testsession?session="+this.getSessionId().toString()).asJson();
	        JSONObject sessionInfo=jsonResponse.getBody().getObject();
	        String nodeUrl=sessionInfo.getString("proxyId");
	        System.out.println("Session Id: "+nodeUrl);
	
	        store.setTaskProperty("node_url", nodeUrl);
	
	        JSONObject data=new JSONObject();
	        data.put("session_id", this.getSessionId().toString());
	        data.put("node_url", nodeUrl);
	        messageQueue.sendStatus("script_session_established", data);
    	} catch(UnirestException e) {
    		e.printStackTrace();
    	}
    }
    
	public PhotonDriver() throws IOException, TimeoutException {
		store = new DataStore(taskId);
        HUB = store.getConfigProperty("HUB");
        runId = store.getTaskProperty("testrun_id");
        


        dataDir="/tmp/"+taskId;
        
        messageQueue=new MessageQueue(runId, taskId);
	}
	
    public void checkpoint(String slug) {
    	if(isLocal()) {
    		File f=this.getScreenshotAs(OutputType.FILE);
    		try {
    			FileUtils.copyFile(f, new File("photon_"+slug+".png"));
    	    } catch(IOException e) {
    	        System.out.println("Failed to take screenshot");
    	    }
    		return;
    	}
    	
    	platformManager.logCheckpoint(slug);

        File f=this.getScreenshotAs(OutputType.FILE);
        try {
           FileUtils.copyFile(f, new File(dataDir+"/"+slug+".png"));
        } catch(IOException e) {
            System.out.println("Failed to take screenshot");
        } 
        
        store.addTaskStage(slug);

        JSONObject details=new JSONObject();
        details.put("stage", slug);
        details.put("screenshot", dataDir+"/"+slug+".png");
        
        messageQueue.sendStatus("test_checkpoint", details);
    }

    public void beginTest() {
    	if(isLocal())
    		return;
        checkpoint("begin_test");
    }

    public void endTest() {
    	if(isLocal())
    		return;
        checkpoint("end_test");
    }
    
    public void quit() {
    	this.endTest();
    	if(isLocal()) {
    		super.quit();
    		return;
    	}
    	
		LogEntries logEntries = platformManager.getLogs();
		
		List<String> lines=new ArrayList<String>();

		if(logEntries!=null) {
	        for (LogEntry entry : logEntries) {
				lines.add(entry.toString());
	        }
		} else {
			lines.add("This platform does not support logs");
		}
        
		Path logFile=Paths.get(dataDir+"/browser.log");
		try {
			Files.write(logFile, lines, Charset.forName("UTF-8"));	   
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		JSONObject details=new JSONObject();
        details.put("path", dataDir+"/browser.log");
        messageQueue.sendStatus("test_log", details);

		store.setTaskProperty("test_complete", "true");
		messageQueue.sendStatus("test_complete", null);
       
        super.quit();

		store.setTaskProperty("test_teardown_failed", "false");

		messageQueue.sendStatus("script_complete", null);
        
		try {
			messageQueue.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
