package yokwe.util.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import yokwe.util.UnexpectedException;

public final class DownloadSync implements Download {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private HttpRequester requester = null;
	
	public DownloadSync setRequesterBuilder(RequesterBuilder requesterBuilder) {
		SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(requesterBuilder.soTimeout, TimeUnit.SECONDS)
                .build();
		
		requester = RequesterBootstrap.bootstrap()
                .setSocketConfig(socketConfig)
                .setMaxTotal(requesterBuilder.maxTotal)
                .setDefaultMaxPerRoute(requesterBuilder.defaultMaxPerRoute)
                .create();
		
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
        
        return this;
	}
	
	private final LinkedList<Task> taskQueue = new LinkedList<Task>();
	public DownloadSync addTask(Task task) {
		taskQueue.add(task);
		return this;
	}
	
	private final List<Header> headerList = new ArrayList<>();
	public DownloadSync clearHeader() {
		headerList.clear();
		return this;
	}
	public DownloadSync addHeader(String name, String value) {
		headerList.add(new BasicHeader(name, value));
		return this;
	}
	public DownloadSync setReferer(String value) {
		addHeader("Referer", value);
		return this;
	}
	public DownloadSync setUserAgent(String value) {
		addHeader("User-Agent", value);
		return this;
	}
	
	private int threadCount = 1;
	public DownloadSync setThreadCount(int newValue) {
		threadCount = newValue;
		return this;
	}
	
	private int connectionTimeout = 30;
	public DownloadSync setConnectionTimeout(int newValue) {
		this.connectionTimeout = newValue;
		return this;
	}
	
	private int progressInterval = 1000;
	public DownloadSync setProgressInterval(int newValue) {
		this.progressInterval = newValue;
		return this;
	}
	
	private ExecutorService executor      = null;
	private int 		    taskQueueSize = 0;
	private Worker[]        workerArray   = null;
	public void startProcessTask() {
		if (requester == null) {
			logger.warn("Set requester using default value of RequestBuilder");
			// Set requester using default value of RequestBuilder
			setRequesterBuilder(RequesterBuilder.custom());
		}
		taskQueueSize = taskQueue.size();
		
		executor = Executors.newFixedThreadPool(threadCount);
		
		workerArray = new Worker[threadCount];
		for(int i = 0; i < threadCount; i++) {
			Worker workder = new Worker(String.format("WORKER-%02d", i));
			workerArray[i] = workder;
		}
		
		for(Worker worker: workerArray) {
			executor.execute(worker);
		}
	}
	public void waitProcessTask() {
		try {
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.warn("{} {}", exceptionName, e);
		} finally {
			executor      = null;
			taskQueueSize = 0;
		}
	}
	public void showRunCount() {
		logger.info("== Worker runCount");
		for(int i = 0; i < threadCount;) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%s ", workerArray[i].name));
			for(int j = 0; j < 10; j++) {
				if (i < threadCount) {
					sb.append(String.format("%4d", workerArray[i++].runCount));
				}
			}
			logger.info("{}", sb.toString());
		}
	}
	public void startAndWait() {
		startProcessTask();
		waitProcessTask();
	}
	
	private class Worker implements Runnable {
		private String name;
		private int    runCount;
		public Worker(String name) {
			this.name      = name;
			this.runCount  = 0;
		}
		
		@Override
		public void run() {
			if (requester == null) {
				throw new UnexpectedException("requester == null");
			}
			Thread.currentThread().setName(name);

	        final HttpCoreContext coreContext = HttpCoreContext.create();
	        
			for(;;) {
				final int  count;
				final Task task;
				synchronized (taskQueue) {
					count = taskQueueSize - taskQueue.size();
					task  = taskQueue.poll();
				}
				if (task == null) break;
				
				if ((count % progressInterval) == 0) {
					logger.info("{}", String.format("%4d / %4d  %s", count, taskQueueSize, task.uri));
				}
				runCount++;

	            try {
					HttpHost target = HttpHost.create(task.uri);
					
		            ClassicHttpRequest request = new BasicClassicHttpRequest(task.method, task.uri);
		            headerList.forEach(o -> request.addHeader(o));
		            if (task.entity != null) {
		            	HttpEntity httpEntity = HttpEntities.create(task.entity, task.contentType);
		            	request.setEntity(httpEntity);
		            }
		            
		            HttpClientResponseHandler<Result> responseHandler = new HttpClientResponseHandler<Result>() {
		        		@Override
		        		public Result handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
		        			return new Result(task, response);
		        		}
		            };

		            Result result = requester.execute(target, request, Timeout.ofSeconds(connectionTimeout), coreContext, responseHandler);
		            task.process(result);

				} catch (HttpException | IOException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.warn("{} {}", exceptionName, e);
				}
			}
		}
	}
}