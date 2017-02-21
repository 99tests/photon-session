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
	
	public void setupDriver(RemoteWebDriver driver) {
		this.driver=driver;
	}
	
	public abstract void logCheckpoint(String checkpoint);
	
	public abstract LogEntries getLogs();
}