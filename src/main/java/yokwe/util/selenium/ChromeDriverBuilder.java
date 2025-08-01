package yokwe.util.selenium;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.bridge.SLF4JBridgeHandler;

import yokwe.util.UnexpectedException;

public class ChromeDriverBuilder {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	// redirect java.util.logging to slf4j
	static {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}
	
	// path of chrome for testing
	private static final File BROWSER_FILE  = StorageSelenium.chromeForTesting.getFile("chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
	private static final File DRIVER_FILE   = StorageSelenium.chromeForTesting.getFile("chromedriver-mac-arm64/chromedriver");
	private static final File USER_DATA_DIR = StorageSelenium.chromeForTesting.getFile("user-data-dir");
	static {
		// sanity check
		boolean hasError = false;
		if (!BROWSER_FILE.exists()) {
			logger.error("browser file does not exist");
			logger.error("  {}", BROWSER_FILE.getAbsolutePath());
			hasError = true;
		}
		if (!DRIVER_FILE.exists()) {
			logger.error("driver file does not exist");
			logger.error("  {}", DRIVER_FILE.getAbsolutePath());
			hasError = true;
		}
		if (!USER_DATA_DIR.isDirectory()) {
			logger.error("user data dir does not exist");
			logger.error("  {}", USER_DATA_DIR.getAbsolutePath());
			hasError = true;
		}
		if (hasError) {
			throw new UnexpectedException("unpexpected");
		}
	}
	
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private ChromeDriverService service;
		private ChromeOptions       options;
		private File                browserFile;
		private File                driverFile;
		private File                userDataDir;
		
		private Map<String, Object>	prefsMap;
		private boolean				enableDownload;
		
		private Builder() {
			service        = new ChromeDriverService.Builder().build();
			options        = new ChromeOptions();
			browserFile    = BROWSER_FILE;
			driverFile     = DRIVER_FILE;
			userDataDir    = USER_DATA_DIR;
			prefsMap       = new TreeMap<>();
			enableDownload = true;
		}
		
		public Builder withBrowserFile(File file) {
			// sanity check
			if (!file.isFile()) {
				logger.error("no file");
				logger.error("  dir  {}!", file.getAbsolutePath());
				throw new UnexpectedException("no file");
			}
			
			browserFile = file;
			return this;
		}
		public Builder withDriverFile(File file) {
			// sanity check
			if (!file.isFile()) {
				logger.error("no file");
				logger.error("  dir  {}!", file.getAbsolutePath());
				throw new UnexpectedException("no file");
			}
			
			driverFile = file;
			return this;
		}
		public Builder withUserDataDir(File dir) {
			// sanity check
			if (!dir.isDirectory()) {
				logger.error("no directory");
				logger.error("  dir  {}!", dir.getAbsolutePath());
				throw new UnexpectedException("no directory");
			}
			
			userDataDir = dir;
			return this;
		}
		public Builder withArguments(String... args) {
			options.addArguments(args);
			return this;
		}
		public Builder withPrefs(String name, Object value) {
			prefsMap.put(name, value);
			return this;
		}
		public Builder withEnableDownload(boolean newValue) {
			enableDownload = newValue;
			return this;
		}
		
		public ChromeDriver build() {
			// build options
			{
				prefsMap.put("profile.default_content_settings.popups", 0);
				prefsMap.put("plugins.always_open_pdf_externally",      1);

				options.setExperimentalOption("prefs", prefsMap);
				options.setBinary(browserFile.getAbsolutePath());
				options.addArguments("--user-data-dir=" + userDataDir.getAbsolutePath());
				options.setEnableDownloads(enableDownload);
			}
			// build service
			{
				service.setExecutable(driverFile.getAbsolutePath());
			}
			
			// build driver
			return new ChromeDriver(service, options);
		}
	}
}
