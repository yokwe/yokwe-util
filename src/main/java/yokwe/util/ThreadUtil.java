package yokwe.util;

public class ThreadUtil {
	public static void sleep(int millis) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			//
		}
	}
}
