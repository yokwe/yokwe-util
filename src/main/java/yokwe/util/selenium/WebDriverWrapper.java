package yokwe.util.selenium;

import java.io.File;
import java.io.FileFilter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import yokwe.util.FileUtil;
import yokwe.util.UnexpectedException;

public final class WebDriverWrapper<T extends WebDriver & Interactive & JavascriptExecutor > implements WebDriver, Interactive, JavascriptExecutor {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofSeconds(60);
	
	private static final Point     DEFAULT_POSITION  = new Point(0, 0);
	private static final Dimension DEFAULT_DIMENSION = new Dimension(1400, 800);
	
	private final T driver;
	
	public static class Factory {
		public static WebDriverWrapper<SafariDriver> createSafari() {
			return new WebDriverWrapper<SafariDriver>(SafariDriverBuilder.builder().build());
		}
		public static WebDriverWrapper<ChromeDriver> createChrome() {
			return new WebDriverWrapper<ChromeDriver>(ChromeDriverBuilder.builder().build());
		}
		public static WebDriverWrapper<ChromeDriver> createChromeHeadless() {
			return new WebDriverWrapper<ChromeDriver>(ChromeDriverBuilder.builder().withArguments("--headless=new").build());
		}
	}
	
	public WebDriverWrapper(T driver, Point position, Dimension dimension) {
		this.driver = driver;
		this.setPosition(position);
		this.setSize(dimension);
	}
	public WebDriverWrapper(T driver) {
		this(driver, DEFAULT_POSITION, DEFAULT_DIMENSION);
	}
	
	
	//
	// utility methods
	//
	public void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			//
		}
	}
	//
	public void savePage(File file) {
		FileUtil.write().file(file, driver.getPageSource());
	}
	//
	public void getAndWait(String url) {
		get(url);
		wait.pageTransition();
	}
	//
	public void click(By locator) {
		wait.untilPresenceOfElement(locator);
		wait.untilClickable(locator).click();
	}
	public void clickAndWait(By locator) {
		click(locator);
		wait.pageTransition();
	}
	//
	public void sendKey(By locator, String string) {
		wait.untilPresenceOfElement(locator);
		wait.untilClickable(locator).sendKeys(string);
	}
	public void sendKeyAndWait(By locator, String string) {
		sendKey(locator, string);
		wait.pageTransition();
	}
	//
	public void moveToElement(By locator) {
		new Actions(driver).moveToElement(wait.untilPresenceOfElement(locator)).perform();
	}
	public void moveToElementAndWait(By locator) {
		moveToElement(locator);
		wait.pageTransition();
	}
	//
	public void submit(By locator) {
		wait.untilPresenceOfElement(locator).submit();
	}
	public void submitAndWait(By locator) {
		submit(locator);
		wait.pageTransition();
	}
	//
	public Select select(By locator) {
		return new Select(wait.untilPresenceOfElement(locator));
	}
	//
	public Object executeScriptAndWait(String script, Object... args) {
		var ret = executeScript(script, args);
		wait.pageTransition();
		return ret;
	}
	//
	public void setSize(Dimension dimension) {
		manage().window().setSize(dimension);
	}
	public void setPosition(Point position) {
		manage().window().setPosition(position);
	}
	//
	public File downloadFile(File dir, FileFilter fileFilter, Runnable download) {
		// sanity check
		if (!dir.isDirectory()) {
			logger.error("dir is not diretory");
			logger.error("  dir  {}!", dir.getAbsolutePath());
			throw new UnexpectedException("dir is not diretory");
		}
		
		// delete file from download directory using fileFilter
		for(var file: dir.listFiles(fileFilter)) file.delete();
		
		download.run();
		
		sleep(Duration.ofSeconds(1));
		File[] files = dir.listFiles(fileFilter);
		
		if (files.length == 1) {
			var file = files[0];
			wait.untilDownloadFinish(file);
			return file;
		} else {
			logger.error("Unexpected download file");
			logger.error("  files  {}", files.length);
			for(var i = 0; i < files.length; i++) {
				logger.error("  files  {}  {}", i, files[i].getPath());
			}
			throw new UnexpectedException("Unexpected download file");
		}
	}
	
	//
	// switchToWindow
	//
	public final SwitchToWindow switchToWindow = new SwitchToWindow();
	public final class SwitchToWindow {
		private static ExpectedCondition<String> getWindoHandleTitleContains(String string_) {
			return new ExpectedCondition<String>() {
				private String string = string_;
				
				@Override
				public String apply(WebDriver driver) {
					WindowInfo windowInfo = new WindowInfo(driver);
					return windowInfo.getWindowHandleTitleContains(string);
				}

				@Override
				public String toString() {
					return "get window title contains " + string;
				}
			};
		}
		public void titleContains(String string, Duration timeout) {
			if (driver.getTitle().contains(string)) return;  // no need to switch window
			var handle = wait.untilExpectedCondition(getWindoHandleTitleContains(string), timeout);
			driver.switchTo().window(handle);
		}
		public void titleContains(String string) {
			titleContains(string, DEFAULT_TIMEOUT_DURATION);
		}
	}
	
	
	//
	// wait
	//
	public final Wait wait = new Wait();
	public final class Wait {
		private static final Duration DEFAULT_WAIT_SLEEP     = Duration.ofMillis(200);
		private static final Duration DEFAULT_PAGE_TRANSTION = Duration.ofSeconds(1);
		private static final Duration DEFAULT_FILE_TRANSTION = Duration.ofSeconds(1);
		//
		// untilCondition
		//
		public <E> E untilExpectedCondition(ExpectedCondition<E> isTrue, Duration timeout) {
			return new WebDriverWait(driver, timeout, DEFAULT_WAIT_SLEEP).until(isTrue);
		}
		public <E> E untilExpectedCondition(ExpectedCondition<E> isTrue) {
			return untilExpectedCondition(isTrue, DEFAULT_TIMEOUT_DURATION);
		}
		//
		// untilPresenceOfElement
		//
		public WebElement untilPresenceOfElement(By locator) {
			return untilExpectedCondition(ExpectedConditions.presenceOfElementLocated(locator));
		}
		//
		// untilClickable
		//
		public WebElement untilClickable(By locator) {
			return untilExpectedCondition(ExpectedConditions.elementToBeClickable(locator));
		}
		//
		// untilTitleContains
		//
		public Boolean untilTitleContains(String string) {
			return untilExpectedCondition(ExpectedConditions.titleContains(string));
		}
		//
		// pageTransition
		//
		private static ExpectedCondition<Boolean> pageTransition_(Duration duration_) {
			return new ExpectedCondition<Boolean>() {
				private Duration      duration      = duration_;
				private LocalDateTime oldPageTime   = null;
				private int           oldPageLength = -1;
				
				@Override
				public Boolean apply(WebDriver driver) {
					var newPageTime   = LocalDateTime.now();
					var newPageLength = driver.getPageSource().length();
					
					if (newPageLength != oldPageLength) {
						// page has changed
						// update oldPageTime and oldPageLength
						oldPageTime   = newPageTime;
						oldPageLength = newPageLength;
					}
					return 0 < Duration.between(oldPageTime, newPageTime).compareTo(duration);
				}

				@Override
				public String toString() {
					return "wait finish page transitin using page length  duration" + duration.toString();
				}
			};
		}
		public Boolean pageTransition(Duration duration, Duration timeout) {
			return untilExpectedCondition(pageTransition_(duration), timeout);
		}
		public Boolean pageTransition(Duration timeout) {
			return untilExpectedCondition(pageTransition_(DEFAULT_PAGE_TRANSTION), timeout);
		}
		public Boolean pageTransition() {
			return pageTransition(DEFAULT_PAGE_TRANSTION, DEFAULT_TIMEOUT_DURATION);
		}
		//
		// readyStateComplete
		//
		private static ExpectedCondition<Boolean> dcumentReadyStateComplete_() {
			return new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver driver) {
					return ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete");
				}
				
				@Override
				public String toString() {
					return "wait document.readyState become complete";
				}
			};
		}
		public Boolean documentReadyStateComplete() {
			return untilExpectedCondition(dcumentReadyStateComplete_(), DEFAULT_TIMEOUT_DURATION);
		}
		//
		// jQuery not active
		//
		private static ExpectedCondition<Boolean> jQueryNotActive_() {
			return new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver driver) {
					return (Boolean)((JavascriptExecutor) driver).executeScript("jQuery.active == 0");
				}
				
				@Override
				public String toString() {
					return "wait jQuery not active";
				}
			};
		}
		public Boolean jQueryNotActive() {
			return untilExpectedCondition(jQueryNotActive_(), DEFAULT_TIMEOUT_DURATION);
		}
		public Boolean jQueryNotActive(Duration timeout) {
			return untilExpectedCondition(jQueryNotActive_(), timeout);
		}
		//
		// untilPageContains
		//
		private static ExpectedCondition<Boolean> pageContains(String string_) {
			return new ExpectedCondition<Boolean>() {
				private String string = string_;
				
				@Override
				public Boolean apply(WebDriver driver) {
					return driver.getPageSource().contains(string);
				}

				@Override
				public String toString() {
					return "wait page contains " + string;
				}
			};
		}
		public Boolean untilPageContains(String page) {
			return untilExpectedCondition(pageContains(page));
		}
		//
		// untilPresenseOfWindow
		//
		public static ExpectedCondition<Boolean> presenceOfWindow(String string_) {
			return new ExpectedCondition<Boolean>() {
				private String string = string_;
				
				@Override
				public Boolean apply(WebDriver driver) {
					WindowInfo windowInfo = new WindowInfo(driver);
					return windowInfo.titleContains(string);
				}

				@Override
				public String toString() {
					return "wait window that page title contains " + string;
				}
			};
		}
		public Boolean untilPresenceOfWindow(String string) {
			return untilExpectedCondition(presenceOfWindow(string));
		}
		//
		// untilAlertIsPresent
		//
		public Alert untilAlertIsPresent() {
			return untilExpectedCondition(ExpectedConditions.alertIsPresent());
		}
		//
		// untilDownloadFinish
		//
		public static ExpectedCondition<Boolean> downloadFinish(File file_, Duration duration_) {
			return new ExpectedCondition<Boolean>() {
				private File          file          = file_;
				private Duration      duration      = duration_;
				private LocalDateTime oldFileTime   = null;
				private long          oldFileLength = -1;
				
				@Override
				public Boolean apply(WebDriver driver) {
					var newFileTime   = LocalDateTime.now();
					var newFileLength = file.length();

					if (newFileLength != oldFileLength) {
						// file has changed
						// update oldFileTime and oldFileLength
						oldFileTime   = newFileTime;
						oldFileLength = newFileLength;
					}
					return 0 < Duration.between(oldFileTime, newFileTime).compareTo(duration);
				}

				@Override
				public String toString() {
					return "wait download finish " + file.getPath();
				}
			};
		}
		public Boolean untilDownloadFinish(File file, Duration duration) {
			return untilExpectedCondition(downloadFinish(file, duration));
		}
		public Boolean untilDownloadFinish(File file) {
			return untilDownloadFinish(file, DEFAULT_FILE_TRANSTION);
		}
	}
	
	
	//
	// check
	//
	public final Check check = new Check();
	public final class Check {
		public void titleContains(String expect) {
			var actual = driver.getTitle();
			if (actual.contains(expect)) return;
			logger.error("Unexpected window title");
			logger.error("  expect  {}!", expect);
			logger.error("  actual  {}!", actual);
			throw new UnexpectedException("Unexpected window title");
		}
		public void pageContains(String expect) {
			var actual = driver.getPageSource();
			if (actual.contains(expect)) return;
			logger.error("Unexpected page source");
			logger.error("  expect  {}!", expect);
			logger.error("  actual  {}!", actual);
			throw new UnexpectedException("Unexpected page source");
		}
	}
	
	
	//
	// export to WebDriver
	//
	@Override
	public void get(String url) {
		driver.get(url);
	}
	@Override
	public String getCurrentUrl() {
		return driver.getCurrentUrl();
	}
	@Override
	public String getTitle() {
		return driver.getTitle();
	}
	@Override
	public List<WebElement> findElements(By by) {
		return driver.findElements(by);
	}
	@Override
	public WebElement findElement(By by) {
		return driver.findElement(by);
	}
	@Override
	public String getPageSource() {
		return driver.getPageSource();
	}
	@Override
	public void close() {
		driver.close();
	}
	@Override
	public void quit() {
		driver.quit();
	}
	@Override
	public Set<String> getWindowHandles() {
		return driver.getWindowHandles();
	}
	@Override
	public String getWindowHandle() {
		return driver.getWindowHandle();
	}
	@Override
	public TargetLocator switchTo() {
		return driver.switchTo();
	}
	@Override
	public Navigation navigate() {
		return driver.navigate();
	}
	@Override
	public Options manage() {
		return driver.manage();
	}
	//
	// export to Interactive
	//
	@Override
	public void perform(Collection<Sequence> actions) {
		driver.perform(actions);
	}
	@Override
	public void resetInputState() {
		driver.resetInputState();
	}
	//
	// export to JavascriptExecutor
	//
	@Override
	public Object executeScript(String script, Object... args) {
		return driver.executeScript(script, args);
	}
	@Override
	public Object executeAsyncScript(String script, Object... args) {
		return driver.executeAsyncScript(script, args);
	}
}
