package com.the99tests.photon;

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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
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

import com.the99tests.photon.platforms.PhotonPlatformManagerFactory;
import com.the99tests.photon.platforms.PlatformManager;
import com.the99tests.photon.platforms.UnsupportedConfigException;

import redis.clients.jedis.Jedis;

public class PhotonSession {
	private static DataStore store;
    private static String HUB;
    private static String taskId;
    private static String runId;
    private static String dataDir;
    private static String platform;
    private static String browser;
    private static PlatformManager platformManager;
    private static MessageQueue messageQueue;
    private static RemoteWebDriver driver;
    
    public static boolean isLocal() {
    	String taskId=System.getProperty("photonTaskId");
    	return (taskId==null);
    }
    
    public static RemoteWebDriver setupPhotonSession() throws IOException, TimeoutException, UnsupportedConfigException {
		setupPhotonEnvironment();
		
		platformManager=PhotonPlatformManagerFactory.getPlatformManager(browser, platform);
		
		URL hubUrl=new URL("http://"+HUB+":4444/wd/hub");
		Capabilities desiredCapabilities=getTaskCapabilities(hubUrl);
		
		driver=new RemoteWebDriver(hubUrl, desiredCapabilities);
		platformManager.setupDriver(driver);
		sendSessionInfo();
		return driver;
    }
    
    public static void setupLocalSession(RemoteWebDriver webDriver) {
    	driver=webDriver;
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
    
    public static void sendSessionInfo() {
    	store.setTaskProperty("session_id", driver.getSessionId().toString());
    	try {
	    	String uri="http://"+HUB+":4444/grid/api/testsession?session="+driver.getSessionId().toString();
	    	String response=Request.Get(uri).execute().returnContent().asString();
	        JSONObject sessionInfo=new JSONObject(response);


	        String nodeUrl=sessionInfo.getString("proxyId");
	        System.out.println("Session Id: "+nodeUrl);
	
	        store.setTaskProperty("node_url", nodeUrl);
	
	        JSONObject data=new JSONObject();
	        data.put("session_id", driver.getSessionId().toString());
	        data.put("node_url", nodeUrl);
	        messageQueue.sendStatus("script_session_established", data);
    	} catch(ClientProtocolException e) {
    		e.printStackTrace();
    	} catch(IOException e) {
    		e.printStackTrace();
    	}
    	
    }
    
	public PhotonSession() throws IOException, TimeoutException {
		store = new DataStore(taskId);
        HUB = store.getConfigProperty("HUB");
        runId = store.getTaskProperty("testrun_id");
        


        dataDir="/tmp/"+taskId;
        
        messageQueue=new MessageQueue(runId, taskId);
	}
	
    public static void checkpoint(String slug) {
    	if(isLocal()) {
    		File f=driver.getScreenshotAs(OutputType.FILE);
    		try {
    			FileUtils.copyFile(f, new File("photon_"+slug+".png"));
    	    } catch(IOException e) {
    	        System.out.println("Failed to take screenshot");
    	    }
    		return;
    	}
    	
    	platformManager.logCheckpoint(slug);

        File f=driver.getScreenshotAs(OutputType.FILE);
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

    public static void beginTest() {
    	if(isLocal())
    		return;
        checkpoint("begin_test");
    }

    public static void endTest() {
    	if(isLocal())
    		return;
        checkpoint("end_test");
    }
    
    public static void closeSession() {
    	if(isLocal()) {
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
			e1.printStackTrace();
		}

		JSONObject details=new JSONObject();
        details.put("path", dataDir+"/browser.log");
        messageQueue.sendStatus("test_log", details);

		store.setTaskProperty("test_complete", "true");
		messageQueue.sendStatus("test_complete", null);
       
		store.setTaskProperty("test_teardown_failed", "false");

		messageQueue.sendStatus("script_complete", null);
        
		try {
			messageQueue.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
    }
}
