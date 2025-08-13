package yokwe.util.selenium;

import yokwe.util.Storage;

public interface StorageSelenium {
	public static final Storage storage = Storage.storage.getStorage("selenium");
	
	public static final Storage chromeForTesting = storage.getStorage("chrome-for-testing");
	
	public static void main(String[] args) {
		var logger = yokwe.util.LoggerUtil.getLogger();
		
		logger.info("START");
		
		logger.info("storage          {}", StorageSelenium.storage.getFile());
		logger.info("chromeForTesting {}", StorageSelenium.chromeForTesting.getFile());
		
		logger.info("STOP");
	}
}
