package com.the99tests.photon.platforms;

import java.net.URL;
import java.util.HashMap;

import org.openqa.selenium.remote.DesiredCapabilities;

import com.the99tests.photon.DataStore;
import com.the99tests.photon.PhotonSession;

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
	
	public static String getGridPlatformName(String platform) throws UnsupportedConfigException {
		String gridPlatform=platforms.get(platform);
		if(gridPlatform==null) {
			throw new UnsupportedConfigException("Unknown platform '"+platform+"'");
		}
		
		return gridPlatform;
	}
	
	public static PlatformManager getPlatformManager(String browser, String platform) throws UnsupportedConfigException {
		
		
		PlatformManager browserConfig=browserConfigs.get(browser);
		if(browserConfig==null) {
			throw new UnsupportedConfigException("Unknown browser '"+browser+"'");
		}

		return browserConfig;
	}
}
