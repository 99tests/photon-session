package com.the99tests.photon.platforms;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.the99tests.photon.DataStore;
import com.the99tests.photon.PhotonSession;

public class AndroidManager extends PlatformManager {

	@Override
	public DesiredCapabilities setupCapabilities(URL hub, String platform, DataStore store) {
		DesiredCapabilities capabilities=new DesiredCapabilities();
		capabilities.setCapability("deviceName", store.getTaskProperty("device"));
		capabilities.setCapability("browserName", store.getTaskProperty("device"));
		capabilities.setCapability("platformName", "ANDROID");
		capabilities.setCapability("platformVersion", "4.4.2");
		capabilities.setCapability("app", store.getTaskProperty("app_url"));
		return capabilities;
	}
	
	@Override
	public void setupDriver(RemoteWebDriver driver) {
		super.setupDriver(driver);
		driver.manage().timeouts().implicitlyWait(120, TimeUnit.SECONDS);
	}

	@Override
	public void logCheckpoint(String checkpoint) {
	}
	
	@Override
	public LogEntries getLogs() {
		return driver.manage().logs().get("logcat");
	}
}
