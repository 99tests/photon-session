package com.preenos.photon.platforms;

import java.net.URL;
import java.util.HashMap;

import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.preenos.photon.DataStore;
import com.preenos.photon.PhotonDriver;


public abstract class PlatformManager {
	protected PhotonDriver driver;
	
	public abstract DesiredCapabilities setupCapabilities(URL hub, String platform, DataStore store);
	
	public void setupDriver(PhotonDriver driver) {
		this.driver=driver;
	}
	
	public abstract void logCheckpoint(String checkpoint);
	
	public abstract LogEntries getLogs();
}