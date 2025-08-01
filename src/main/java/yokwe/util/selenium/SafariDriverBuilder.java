package yokwe.util.selenium;

import java.time.Duration;

import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariDriverService;
import org.openqa.selenium.safari.SafariOptions;
import org.slf4j.bridge.SLF4JBridgeHandler;


public class SafariDriverBuilder {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	// redirect java.util.logging to slf4j
	static {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}
	
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		SafariOptions options;
		
		private Builder() {
			options = new SafariOptions();
		}
		
		public SafariDriver build() {
			// build options
			
			var service = new SafariDriverService.Builder().build();		
			service.setExecutable("/usr/bin/safaridriver");
			
			var driver = new SafariDriver(service, options);
//			driver.requireDownloadsEnabled(options);
			return driver;
		}
	}
	
	public static void main(String[] args) {
		logger.info("START");
		
		{
			var options = new SafariOptions();
			logger.info("capabilities  {}", options.getCapabilityNames());
			
			logger.info("driver");
			var driver = new WebDriverWrapper<SafariDriver>(SafariDriverBuilder.builder().build());
			try {
				logger.info("get");
				driver.get("https://sonybank.jp/pages/db/dbca0100/?lang=ja");
				logger.info("wait");
				driver.wait.pageTransition();
				logger.info("sleep");
				driver.sleep(Duration.ofSeconds(10));
			} finally {
				driver.quit();
			}
			
			logger.info("done");
		}
		
		logger.info("STOP");
	}
}
