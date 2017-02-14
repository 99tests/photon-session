package com.preenos.photon.platforms;

import java.net.URL;
import java.util.HashMap;

import org.openqa.selenium.remote.DesiredCapabilities;

import com.preenos.photon.DataStore;
import com.preenos.photon.PhotonDriver;

public class PhotonPlatformManagerFactory {
	private static HashMap<String, PlatformManager> browserConfigs=new HashMap<String, PlatformManager>();
	private static HashMap<String, String> platforms=new HashMap<String, String>();
	
	static {		
		browserConfigs.put("chrome", new ChromeManager());
		browserConfigs.put("firefox", new FirefoxManager());
		browserConfigs.put("android-app", new AndroidManager());
		
		platforms.put("linux", "LINUX");
		platforms.put("windows", "WINDOWS");
		platforms.put("android", "ANDROID");
	}
	
	public static PlatformManager getPlatformManager(String browser, String platform) throws UnsupportedConfigException {
		String gridPlatform=platforms.get(platform);
		if(gridPlatform==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}
		
		PlatformManager browserConfig=browserConfigs.get(browser);
		if(browserConfig==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}

		return browserConfig;
	}
	
	/*
	public static DesiredCapabilities getCapabilities(URL hub, String browser, String platform, DataStore store) throws UnsupportedConfigException {
		String gridPlatform=platforms.get(platform);
		if(gridPlatform==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}
		
		PlatformManager browserConfig=browserConfigs.get(browser);
		if(browserConfig==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}

		return browserConfig.setupCapabilities(hub, gridPlatform, store);
	}
	
	public static void setupDriver(PhotonDriver driver, String browser, String platform, DataStore store) throws UnsupportedConfigException {
		String gridPlatform=platforms.get(platform);
		if(gridPlatform==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}
		
		PlatformManager browserConfig=browserConfigs.get(browser);
		if(browserConfig==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}
		
		browserConfig.setupDriver(driver);
	}*/
}
