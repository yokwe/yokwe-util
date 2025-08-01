package yokwe.util.http;

public interface Download {
	public Download setRequesterBuilder(RequesterBuilder requesterBuilder);
	
	public Download addTask(Task task);
	
	public Download clearHeader();
	public Download addHeader(String name, String value);
	public Download setReferer(String value);
	public Download setUserAgent(String value);
	
	public Download setThreadCount(int newValue);
	public Download setConnectionTimeout(int newValue); // in seconds
	public Download setProgressInterval(int newValue);
	
	public void startProcessTask();
	public void waitProcessTask();
	public void showRunCount();
	default void startAndWait() {
		startProcessTask();
		waitProcessTask();
	}
}	
