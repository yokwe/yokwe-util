package yokwe.util.selenium;

import java.io.File;

import yokwe.util.FileUtil;
import yokwe.util.UnexpectedException;

public interface StorageSelenium {
	// interface requires logger must be public static final
	public static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public String getPath();
	public String getPath(String path);
	public String getPath(String prefix, String path);
	
	default public File getFile() {
		return new File(getPath());
	}
	default public File getFile(String path) {
		return new File(getPath(path));
	}
	default public File getFile(String prefix, String path) {
		return new File(getPath(prefix, path));
	}
	
	
	
	public static final String SELENIUM_DATA_PATH_FILE = "data/SeleniumDataPathLocation";
	private static String getSeleniumDataPath() {		
		logger.info("SELELIUM_DATA_PATH_FILE  !{}!", SELENIUM_DATA_PATH_FILE);
		// Sanity check
		if (!FileUtil.canRead(SELENIUM_DATA_PATH_FILE)) {
			logger.error("Cannot read file");
			throw new UnexpectedException("Cannot read file");
		}
		
		String dataPath = FileUtil.read().file(SELENIUM_DATA_PATH_FILE);
		logger.info("SELENIUM_DATA_PATH       !{}!", dataPath);
		// Sanity check
		if (dataPath.isEmpty()) {
			logger.error("Empty dataPath");
			throw new UnexpectedException("Empty dataPath");
		}		
		if (!FileUtil.isDirectory(dataPath)) {
			logger.error("Not directory");
			throw new UnexpectedException("Not directory");
		}		
		return dataPath;
	}
	public static final String SELENIUM_DATA_PATH = getSeleniumDataPath();
	public class Impl implements StorageSelenium {
		org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

		private final String basePath;
		
		public Impl(String basePath) {
			if (!FileUtil.isDirectory(basePath)) {
				logger.error("Not directory");
				logger.error("  basePath  {}!", basePath);
				throw new UnexpectedException("Not directory");
			}

			this.basePath = basePath;
		}
		public Impl(String parent, String prefix) {
			this(parent + "/" + prefix);
		}
		public Impl(StorageSelenium parent, String prefix) {
			this(parent.getPath(), prefix);
		}
		
		@Override
		public String getPath() {
			return basePath;
		}

		@Override
		public String getPath(String path) {
			return basePath + "/" + path;
		}

		@Override
		public String getPath(String prefix, String path) {
			return basePath + "/" + prefix + "/" + path;
		}
	}
	
	
	public static final StorageSelenium root             = new Impl(SELENIUM_DATA_PATH);
	public static final StorageSelenium chromeForTesting = new Impl(root, "chrome-for-testing");
	
	public static void main(String[] args) {
		logger.info("START");
		
		logger.info("root             {}", StorageSelenium.root.getPath());
		logger.info("chromeForTesting {}", StorageSelenium.chromeForTesting.getPath());
		
		logger.info("STOP");
	}

}
