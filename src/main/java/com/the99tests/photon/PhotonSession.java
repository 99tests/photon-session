package com.the99tests.photon;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.json.JSONObject;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.reflections.Reflections;
import org.testng.TestNG;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.collections.Lists;
import org.testng.reporters.ExitCodeListener;
import org.testng.reporters.VerboseReporter;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import com.the99tests.photon.platforms.PhotonPlatformManagerFactory;
import com.the99tests.photon.platforms.PlatformManager;
import com.the99tests.photon.platforms.UnsupportedConfigException;

public class PhotonSession {
	@SuppressWarnings("unused") // convenience URL for testers
	private final String PLAYGROUND_HUB_URL="http://52.66.6.164:4444/wd/hub";
	
	private final String PLAYGROUND_HUB="52.66.6.164";
	private final String DAEMON_API_BASE="http://35.154.244.239";
	
	private DataStore store;
    private String HUB;
    private String taskId;
    private String runId;
    private String dataDir;
    private String platform;
    private String browser;
    private PlatformManager platformManager;
    private MessageQueue messageQueue;
    private RemoteWebDriver driver;
    
    public static abstract class PhotonSuite<T extends RemoteWebDriver> extends PhotonSuiteBase {
    	protected T driver;
    	protected PhotonSession session;

    	public enum PhotonTestEnvironment {
    		LOCAL,
    		PLAYGROUND
    	}
    	
    	protected PhotonTestEnvironment getEnvironment() {
    		return PhotonTestEnvironment.LOCAL;
    	}
    	
    	protected abstract T setupLocalWebDriver() throws Exception;
    	protected abstract T setupPlaygroundWebDriver() throws Exception;
    	
        @BeforeSuite
        protected final void setupSuite() throws Exception {
        	session=new PhotonSession();
        	if(session.isLocal()) {
    	    	if(getEnvironment()==PhotonTestEnvironment.LOCAL) {
    	    		driver=setupLocalWebDriver();
    	    		session.setupLocalSession(driver);
    	    	}
    	    	if(getEnvironment()==PhotonTestEnvironment.PLAYGROUND) {
    	    		driver=setupPlaygroundWebDriver();
    	    		session.setupTestPlaygroundSession(driver);
    	    	}
        	} else {
        		session.setupPhotonSession();
    			driver=session.getNativeDriver();
        	}
        }
        
        @AfterSuite
        protected final void teardownSuite() {
        	session.closeSession();
        	driver.close();
        }
    }
    
    public static class PhotonTestRunner {
    	public static Set<? extends Class<?>> getTestSuiteClasses() {
    		Reflections reflections=new Reflections("com.the99tests.photon.tests");
    		Set<? extends Class<?>> suites=reflections.getSubTypesOf(PhotonSession.PhotonSuite.class);
    		return suites;
    	}
    	
    	public static void main(String[] args) throws IOException {
    		TestNG testng = new TestNG();
    		
    		Set<? extends Class<?>> suiteClasses=getTestSuiteClasses();
    		if(suiteClasses.size()!=1) {
    			System.out.println("ERROR: Each test much have exactly one test suite");
    			System.exit(1);
    		}
    		Class<?> suiteClass=suiteClasses.iterator().next();
    		
    		XmlSuite suite = new XmlSuite();
    		suite.setName(suiteClass.getSimpleName()+" Suite");

    		XmlTest test = new XmlTest(suite);
    		test.setName(suiteClass.getSimpleName()+" Test");
    		
    		List<XmlClass> classes = new ArrayList<XmlClass>();
    		classes.add(new XmlClass(suiteClass.getName()));
    		test.setXmlClasses(classes) ;
    		
    		testng.setUseDefaultListeners(false);
    		testng.addListener(new VerboseReporter());
    		testng.addListener(new ExitCodeListener());
    		List<XmlSuite> suites = Lists.newArrayList();
    		suites.add(suite);//path to xml..
    		testng.setXmlSuites(suites);
    		testng.run();
    		
    		System.out.println(testng.getStatus());
    		if(testng.hasFailure() || testng.hasSkip()) {
    			System.out.println("FAILED");
    			System.exit(1);
    		} else {
    			System.out.println("PASSED");
    			System.exit(0);
    		}
    	}
    }
    
    private boolean isLocal() {
    	String taskId=System.getProperty("photonTaskId");
    	return (taskId==null);
    }
    
    private <T extends RemoteWebDriver> T getNativeDriver() {
    	return platformManager.getNativeDriver();
    }
    
    private void setupPhotonSession() throws IOException, TimeoutException, UnsupportedConfigException {
		setupPhotonEnvironment();
		
		platformManager=PhotonPlatformManagerFactory.getPlatformManager(browser, platform);
		
		URL hubUrl=new URL("http://"+HUB+":4444/wd/hub");
		DesiredCapabilities desiredCapabilities=getTaskCapabilities(hubUrl);
		
		platformManager.setupDriver(hubUrl, desiredCapabilities);
		driver=platformManager.getNativeDriver();
		sendSessionInfo();
    }
    
    private void setupTestPlaygroundSession(RemoteWebDriver webDriver) throws ClientProtocolException, IOException, InterruptedException {
    	driver=webDriver;
    	String uri="http://"+PLAYGROUND_HUB+":4444/grid/api/testsession?session="+driver.getSessionId().toString();
    	String response=Request.Get(uri).execute().returnContent().asString();
        JSONObject sessionInfo=new JSONObject(response);
        
        String privateNodeUrl=new URL(sessionInfo.getString("proxyId")).getHost();
        
        JSONObject params=new JSONObject();
        params.put("private_ip", privateNodeUrl);
        
        response=Request.Post(DAEMON_API_BASE+"/publicip/")
        		.bodyString(params.toString(), ContentType.APPLICATION_JSON)
        		.execute().returnContent().asString();
        
        JSONObject publicIpInfo=new JSONObject(response);

        System.out.println("-----------------------------------------");
        System.out.println("99tests - Test Playground Session");
        System.out.println("You can view the test executing live via VNC");
        System.out.println("VNC Url: vnc://"+publicIpInfo.getString("publicip"));
        System.out.println("VNC password: 99tests123");
        System.out.println("-----------------------------------------");
        Thread.sleep(5000);
    }
        
    private void setupLocalSession(RemoteWebDriver webDriver) {
    	driver=webDriver;
    }
    
    private void setupTestRunSubmission(int enterpriseCycle, int device, String apiKey, String apiSecret) {
    	/*
    	 * TODO: Create test run on the server with parameters
    	 * 
    	 * Setup request id
    	 */
    }
        
    private void setupPhotonEnvironment() throws IOException, TimeoutException {
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
        
        System.out.println("Platform is "+platform+", browser is "+browser);

        dataDir="/tmp/"+taskId;
        
        messageQueue=new MessageQueue(runId, taskId);
    }
    
    private DesiredCapabilities getTaskCapabilities(URL hubUrl) throws MalformedURLException, UnsupportedConfigException {
        return platformManager.setupCapabilities(hubUrl, 
        		PhotonPlatformManagerFactory.getGridPlatformName(platform), store);
    }
    
    private void sendSessionInfo() {
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
		/*store = new DataStore(taskId);
        HUB = store.getConfigProperty("HUB");
        runId = store.getTaskProperty("testrun_id");
        


        dataDir="/tmp/"+taskId;
        
        messageQueue=new MessageQueue(runId, taskId);*/
	}
	
    public void checkpoint(String slug) {
    	if(isLocal()) {
    		/*
    		 * TODO: if submission is turned on upload screenshot to S3/upload to platform
    		 */
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
    
    public void closeSession() {
    	if(isLocal()) {
    		/*
    		 * TODO: if submission is turned on, then upload log files as well
    		 */
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
