package com.the99tests.photon;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;
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

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PhotonSession {
	@SuppressWarnings("unused") // convenience URL for testers
	private final String PLAYGROUND_HUB_URL="http://52.66.6.164:4444/wd/hub";
	
	private static final String PLAYGROUND_HUB="52.66.6.164";
	private static final String DAEMON_API_BASE="http://35.154.244.239";
	private static final String PREENOS_PRODUCTION_API_BASE="https://99tests.com";
	private static final String PREENOS_STAGING_API_BASE="http://testfolio.in";
	private static final String PREENOS_DEV_API_BASE="http://55.55.55.55:3000";

	
	private static final MediaType JSON=MediaType.parse("application/json;charset=utf8");
	private static final MediaType ZIP=MediaType.parse("application/zip");
	
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
    private String rdaRequestId;
    private ArrayList<String> rdaCheckpoints=new ArrayList<String>();
    private String logType;
    private String apiEmail;
    private String apiKey;
    private String apiBase;
    private String enterpriseCycle;
    private String workflow;
    private String automationDevice;
    private boolean shouldVerifyCheckpoints;
    private JSONObject expectedCheckpoints;
    private HashMap<String, Integer> checkpointCounts=new HashMap<String, Integer>();
    
    public static abstract class PhotonSuite<T extends RemoteWebDriver> extends PhotonSuiteBase {
    	protected T driver;
    	protected PhotonSession session;

    	public enum PhotonTestEnvironment {
    		LOCAL,
    		PLAYGROUND,
    		RDA_SUBMISSION
    	}
    	
    	protected PhotonTestEnvironment getEnvironment() {
    		return PhotonTestEnvironment.LOCAL;
    	}
    	
    	
    	    	
    	protected abstract T setupLocalWebDriver() throws Exception;
    	protected abstract T setupPlaygroundWebDriver() throws Exception;
    	protected HashMap<String, Object> getAPICredentials() { return null; }
    	protected HashMap<String, Object> getWorkflowDetails() { return null; }
    	protected HashMap<String, Object> getAutomationDeviceInfo() { return null; }
    	protected HashMap<String, Object> getPhotonSessionSettings() { return null; }

    	
        @BeforeSuite
        protected final void setupSuite() throws Exception {
        	session=new PhotonSession();
        	if(!session.isLocal()) {
        		session.setupPhotonSession();
    			driver=session.getNativeDriver();
    			return;
        	}
        	
        	HashMap<String, Object> credentials=getAPICredentials();
        	if(credentials!=null) {
        		session.apiEmail=(String) credentials.get("email");
        		session.apiKey=(String) credentials.get("apiKey");
        	}
        	HashMap<String, Object> workflowInfo=getWorkflowDetails();
        	if(workflowInfo!=null) {
        		session.enterpriseCycle=(String)workflowInfo.get("enterpriseCycle");
        		session.workflow=(String)workflowInfo.get("workflow");
        	}
        	HashMap<String, Object> deviceInfo=getAutomationDeviceInfo();
        	if(deviceInfo!=null) {
        		session.automationDevice=(String) deviceInfo.get("automationDeviceId");
        		session.logType=(String) deviceInfo.get("logType");
        	}
        	HashMap<String, Object> settings=getPhotonSessionSettings();
        	PhotonTestEnvironment testEnvironment=(PhotonTestEnvironment) settings.get("testEnvironment");
        	String environment=(String) settings.get("environment");
        	if(environment==null) {
        		environment="production";
        	}
        	String verifyCheckpointSetting=(String)settings.get("checkpointVerification");
        	
        	System.out.println("Environment is "+environment);
    		if(environment.equals("production")) {
    			session.apiBase=PREENOS_PRODUCTION_API_BASE;
    		} 
	    	if(environment.equals("staging")) {
	    	    session.apiBase=PREENOS_STAGING_API_BASE;
	    	}
	    	if(environment.equals("dev")) {
	    	    session.apiBase=PREENOS_DEV_API_BASE;
	    	}
        	
	    	if(testEnvironment==PhotonTestEnvironment.LOCAL) {
	    		if(verifyCheckpointSetting=="on") {
	    			if(credentials==null) {
	    				quit("Checkpoint verification is on, but no credentials were provided. Please provide API credentials in getAPICredentials method.");
	    			}
	    			if(workflowInfo==null) {
		    			quit("Checkpoint verification is on but no workflow details were provided. Please provide workflow details in getWorkflowDetails method");
		    		}
	    			session.setupCheckpointVerification();
	    		}
	    		driver=setupLocalWebDriver();
	    		session.setupLocalSession(driver);
	    	}
	    	if(testEnvironment==PhotonTestEnvironment.PLAYGROUND) {
	    		session.setupCheckpointVerification();
	    		driver=setupPlaygroundWebDriver();
	    		session.setupTestPlaygroundSession(driver);
	    	}
	    	if(testEnvironment==PhotonTestEnvironment.RDA_SUBMISSION) {
	    		if(credentials==null) {
	    			quit("Please provide API credentials in getAPICredentials method");
	    		}
	    		if(workflowInfo==null) {
	    			quit("Please provide workflow details in getWorkflowDetails method");
	    		}
	    		if(deviceInfo==null) {
	    			quit("Please provide automation device info via the getAutomationDeviceInfo method");
	    		}
	    		    	
	    		session.setupCheckpointVerification();
	    		driver=setupLocalWebDriver();
	    		session.setupTestRunSubmission(driver);
	    	}
        }
        
        protected final void quit(String message) {
        	System.out.println(message);
        	System.out.println("Tests aborted");
        	System.exit(1);
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
    
    private boolean isRDASubmission() {
    	return rdaRequestId!=null;
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
    	
    	OkHttpClient client = new OkHttpClient();
    	Request request=new Request.Builder().url(uri).build();
    	Response responseObject=client.newCall(request).execute();
    	String response=responseObject.body().string();
        JSONObject sessionInfo=new JSONObject(response);
        
        String privateNodeUrl=new URL(sessionInfo.getString("proxyId")).getHost();
        
        JSONObject params=new JSONObject();
        params.put("private_ip", privateNodeUrl);
        
        RequestBody body=RequestBody.create(JSON, params.toString());
        
        
        request=new Request.Builder()
        	.url(DAEMON_API_BASE+"/publicip/")
        	.post(body)
        	.build();
        responseObject=client.newCall(request).execute();
        response=responseObject.body().string();
        
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
    
    private void setupTestRunSubmission(RemoteWebDriver webDriver) throws ClientProtocolException, IOException, InterruptedException {
    	/*
    	 * TODO: Create test run on the server with parameters
    	 * 
    	 * Setup request id
    	 */
    	driver=webDriver;
    	
    	OkHttpClient client=new OkHttpClient();
    	
    	RequestBody formBody=new FormBody.Builder()
    		.add("enterprisecycle_id", enterpriseCycle)
    		.add("bdd_id", workflow)
    		.add("automation_id", automationDevice)
    		.build();
    	
    	Request request=new Request.Builder()
    		.addHeader("X-Auth-User", apiEmail)
        	.addHeader("X-Auth-Key", apiKey)   
        	.url(apiBase+"/api/v1/testrun_requests")
        	.post(formBody)
        	.build();
    	
    	
    	Response response=client.newCall(request).execute();
    	if(response.isSuccessful()) {
    		String content=response.body().string();
    		System.out.println(content);
	    	JSONObject testrunInfo=new JSONObject(content);
	    	rdaRequestId=testrunInfo.getString("request_id");
	        System.out.println("-----------------------------------------");
	        System.out.println("99tests - Real Device Automation Submission");
	        System.out.println("Request ID is "+rdaRequestId);
	        System.out.println("Session started");
	        System.out.println("-----------------------------------------");
	        Thread.sleep(5000);
    	} else {
    		String content=response.body().string();
    		try {
		    	JSONObject testrunInfo=new JSONObject(content);
	    		quit("Unable to create a testrun request: "+testrunInfo.getString("error"));
    		} catch(JSONException e) {
    			quit("Unable to create a testrun request: call failed with response code "+response.code());
    		}
    	}
    }
    
    private void setupCheckpointVerification() throws IOException {
    	OkHttpClient client=new OkHttpClient();
    	
    	Request request=new Request.Builder()
    		.addHeader("X-Auth-User", apiEmail)
        	.addHeader("X-Auth-Key", apiKey)   
        	.url(apiBase+"/api/v1/workflows/"+workflow+"/checkpoints")
        	.get()
        	.build();
    	
    	
    	Response response=client.newCall(request).execute();
    	if(response.isSuccessful()) {
    		String content=response.body().string();
    		expectedCheckpoints=new JSONObject(content);
    		Iterator<String> checkpointKeys=expectedCheckpoints.keys();
    		while(checkpointKeys.hasNext()) {
    			String key=checkpointKeys.next();
    			if(key==null)
    				break;
    			checkpointCounts.put(key, 0);
    		}
    	} else {
    		String content=response.body().string();
    		try {
		    	JSONObject testrunInfo=new JSONObject(content);
	    		quit("Failed to get checkpoints: "+testrunInfo.getString("error"));
    		} catch(JSONException e) {
    			quit("failed to get checkpoints: call failed with response code "+response.code());
    		}  	
    	}
    	shouldVerifyCheckpoints=true;
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
	    	
	    	OkHttpClient client=new OkHttpClient();
	    	
	    	Request request=new Request.Builder()
	    		.url(uri)
	    		.build();
	    	
	    	Response responseObject=client.newCall(request).execute();
	    	String response=responseObject.body().string();
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
	}
	
    public void checkpoint(String slug) throws Exception {
    	Thread.sleep(2000);
    	if(shouldVerifyCheckpoints) {
    		String[] checkpointParts=slug.split("_");
    		String checkpointName=checkpointParts[checkpointParts.length-1];
    		JSONObject checkpointInfo=expectedCheckpoints.getJSONObject(checkpointName);
    		if(checkpointInfo==null) {
    			quit("Checkpoint verification failed - Unknown checkpoint '"+checkpointName+"'");
    		} else {
    			int count=checkpointCounts.get(checkpointName)+1;
    			if(count>checkpointInfo.getInt("count")) {
        			quit("Checkpoint verification failed - Checkpoint '"+checkpointName+"' should only occur "+(count-1)+" times");
    			}
    			checkpointCounts.put(checkpointName, count);
    			
    			System.out.println(">>>>> Checkpoint "+checkpointName+" ("+count+"/"+checkpointInfo.getInt("count")+")");
    			System.out.println(">>>>> Do "+checkpointInfo.getString("do"));
    			System.out.println(">>>>> Expect "+checkpointInfo.getString("expect"));
    		} 
    		
    	}
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
    		
    		if(isRDASubmission()) {
    			rdaCheckpoints.add(slug);
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
    
    public void debug() throws InterruptedException {
    	while(true) {
    		Thread.sleep(5000);
    	}
    }

    public void beginTest() throws Exception {
    	if(isLocal())
    		return;
        checkpoint("begin_test");
    }

    public void endTest() throws Exception {
    	if(isLocal())
    		return;
        checkpoint("end_test");
    }
    
    public void submitRDAResults() throws IOException {
    	String testOutputZipPath="rda_submission.zip";
    	File f=new File(testOutputZipPath);
    	f.delete();
    	ZipOutputStream zipFileStream=new ZipOutputStream(new FileOutputStream(testOutputZipPath));
    	HashMap<String, Object> entries=new HashMap<String, Object>();
    	for(String rdaCheckpoint: rdaCheckpoints) {
    		Path checkpointFile=Paths.get("photon_"+rdaCheckpoint+".png");
    		String checkpointZipEntry=rdaCheckpoint+".png";
    		if(entries.get(checkpointFile.toString())!=null) {
    			continue;
    		}
    		ZipEntry entry=new ZipEntry(checkpointZipEntry);
    		byte[] bytes=Files.readAllBytes(checkpointFile);
    		zipFileStream.putNextEntry(entry);
    		zipFileStream.write(bytes);
    		zipFileStream.closeEntry();
    		entries.put(checkpointFile.toString(), "exists");
    	}
    	Path logFile=Paths.get("browser.log");
    	ZipEntry entry=new ZipEntry(logFile.toString());
		byte[] bytes=Files.readAllBytes(logFile);
		zipFileStream.putNextEntry(entry);
		zipFileStream.write(bytes);
		zipFileStream.closeEntry();
		
		zipFileStream.finish();
		zipFileStream.close();
		
		Stream<Path> matches=Files.find(Paths.get("target/"), 1, (path, basicFileAttributes) -> {
			return path.toString().endsWith(".zip");
		});
		
		Path p=matches.findFirst().get();
		System.out.println("Path of package is "+p.toString());
		
		String rdaCheckpointString="";
		for(String checkpoint: rdaCheckpoints) {
			if(rdaCheckpointString.length()==0) {
				rdaCheckpointString=checkpoint;
			} else {
				rdaCheckpointString=rdaCheckpointString+","+checkpoint;
			}
		}
		
		OkHttpClient client=new OkHttpClient.Builder()
			.connectTimeout(0, TimeUnit.SECONDS)
			.readTimeout(0, TimeUnit.SECONDS)
			.build();
		
		RequestBody requestBody = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("stages", rdaCheckpointString)
			.addFormDataPart("status", "true")
			.addFormDataPart("test_complete", "true")
			.addFormDataPart("task_status", "complete")
			.addFormDataPart("test_output", "testoutput.zip", 
					RequestBody.create(ZIP, new File(testOutputZipPath.toString())))
			.addFormDataPart("script", "script.zip",
					RequestBody.create(ZIP, new File(p.toString())))
			.build();
		
		Request request=new Request.Builder()
			.addHeader("X-Auth-User", apiEmail)
	        .addHeader("X-Auth-Key", apiKey)
	        .url(apiBase+"/api/v1/testrun_requests/"+rdaRequestId)
	        .put(requestBody)
	        .build();
		
		Response responseObject=client.newCall(request).execute();
    	
    	if(responseObject.isSuccessful()) {
    		System.out.println("---------------------------------------");
    		System.out.println("Test output and results submitted");
    		System.out.println("Please visit the testruns tab  on 99tests.com to check for testrun approval");
    		System.out.println("---------------------------------------");
    	} else {
    		String content=responseObject.body().string();
    		try {
    			JSONObject object=new JSONObject(content);
    			quit("RDA Submission failed: "+object.getString("error"));
    		} catch(JSONException e) {
    			quit("RDA Submission failed: Call failed with error code: "+responseObject.code());
    		}
    	}
    }
    
    public void closeSession() {
    	if(shouldVerifyCheckpoints) {
    		Iterator<String> checkpointKeys=expectedCheckpoints.keys();
    		while(checkpointKeys.hasNext()) {
    			String key=checkpointKeys.next();
    			if(key==null)
    				break;
    			int count=checkpointCounts.get(key);
    			int expectedCount=expectedCheckpoints.getJSONObject(key).getInt("count");
    			if(count!=expectedCount) {
    				quit("Checkpoint verification failed - checkpoint count mismatch - Checkpoint '"+
    						key+"' should occur "+expectedCount+" times, but occured only "+count+" times.");
    			}
    		}
    	}
    	
    	
    	LogEntries logEntries=null;
    	if(!isLocal()) {
    		logEntries = platformManager.getLogs();
    	} else {
    		if(isRDASubmission())
    			logEntries = driver.manage().logs().get(logType);
    	}
		
		List<String> lines=new ArrayList<String>();

		if(logEntries!=null) {
	        for (LogEntry entry : logEntries) {
				lines.add(entry.toString());
	        }
		} else {
			lines.add("This platform does not support logs");
		}
		
    	if(isLocal()) {
    		Path logFile=Paths.get("browser.log");
    		try {
				Files.write(logFile, lines, Charset.forName("UTF-8"));
			} catch (IOException e) {
				e.printStackTrace();
			}
    		if(isRDASubmission())
				try {
					submitRDAResults();
				} catch (IOException e) {
					e.printStackTrace();
				}
    		return;
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
    
    private void quit(String message) {
    	if(driver!=null) 
    		driver.close();
    	System.out.println(message);
    	System.out.println("Tests aborted");
    	System.exit(1);
    }
    //Select drop down menu by visible text
    public void selectByVisibleText(WebElement we, String VisibleText){
    	Select select = new Select(we);
    	select.selectByVisibleText(VisibleText);
    }
    //Open a new Tab/Window and switch to it.
    public void openNewTab()
	{   
		
		String CH = driver.getWindowHandle();
		
		JavascriptExecutor js =(JavascriptExecutor)driver;
		js.executeScript("window.open();");
		
		ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());
		for(String t: tabs)
		{
			if(t.equals(CH))
			{
				int Cix= tabs.indexOf(CH);
				
				driver.switchTo().window(tabs.get(Cix+1));
				break;
			}
		}
	}

    
    
    //Switch to a tab by its index.
    public void switchToTab(int p )
	{
		ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());
		driver.switchTo().window(tabs.get(p-1));
	}
    
    //Verify the response of all the links in current page.
    public boolean verifyIfAllLinksActive()
	{
    	List<WebElement> links=driver.findElements(By.tagName("a"));
		System.out.println("Total links are "+links.size());
		ArrayList<String> Urls = new ArrayList<String>();
		for(WebElement e: links)
		{
			
			String url= e.getAttribute("href");	
			Urls.add(url);
			
		}
		boolean value = true;
        for (String linkUrl: Urls) {
			try {
				URL url = new URL(linkUrl);

				HttpURLConnection httpURLConnect = (HttpURLConnection) url.openConnection();

				httpURLConnect.setConnectTimeout(3000);

				httpURLConnect.connect();
				
				boolean result = httpURLConnect.getResponseCode() == 200;
				
				if (result== true) {
					
				}
				else
				{
					System.out.println(linkUrl + " - " + httpURLConnect.getResponseMessage() + " - "
							+ HttpURLConnection.HTTP_NOT_FOUND);
					value = false;
					
				}
			} catch (Exception e) {

			} 
		}
		return value;
		
		
    } 
    
    
  //Scroll down
    public void scrollToBottom()
    {
    	((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
    }
    
  //Scroll down by pixel
    public void scrollDownByPixel(int pixel)
    {
    	String pixel1 = "window.scrollBy(0,";
    	String pixel2 = ")";
    	((JavascriptExecutor) driver).executeScript(pixel1 + pixel+ pixel2);
    }
    
  //Scroll Up by pixel
    public void scrollUpByPixel(int pixel)
    {
    	String pixel1 = "window.scrollBy(0,";
    	String pixel2 = ")";
    	((JavascriptExecutor) driver).executeScript(pixel1 + -pixel + pixel2);
    }
  //Scroll up to header
    public void scrollToTop()
    {
    	((JavascriptExecutor) driver).executeScript("window.scrollTo(0, -document.body.scrollHeight)");
    }  
    
  //Mouse Hover on an element
    public void mouseHoverOn(WebElement webelement)
    {
    	Actions action = new Actions(driver);
    	action.moveToElement(webelement).build().perform();
    }
    
    //file upload under send keys
    public String getTestDataPath(String fileName){
    	
		return Paths.get(".").toAbsolutePath().normalize().toString()+"/testdata/"+fileName;
    	
    }
  

    
}

