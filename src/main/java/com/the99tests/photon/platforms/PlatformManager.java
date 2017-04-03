package com.the99tests.photon.platforms;

import java.net.URL;
import java.util.HashMap;

import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.the99tests.photon.DataStore;
import com.the99tests.photon.PhotonSession;


public abstract class PlatformManager {
	protected RemoteWebDriver driver;
	
	public abstract DesiredCapabilities setupCapabilities(URL hub, String platform, DataStore store);
	
	protected <T extends RemoteWebDriver> void setupNativeDriver(T driver) {
		this.driver=driver;
	}
	
	public void setupDriver(URL hub, DesiredCapabilities capabilities) {
		driver=new RemoteWebDriver(hub, capabilities);
	}
	
	public <T extends RemoteWebDriver> T getNativeDriver() {
		return (T)driver;
	}
	
	public abstract void logCheckpoint(String checkpoint);
	
	public abstract LogEntries getLogs();
}