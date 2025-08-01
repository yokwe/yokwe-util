package yokwe.util.http;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import yokwe.util.UnexpectedException;

//
// FIXME DownloadSync is OK. But DownloadAsync is not OK.
//
public final class DownloadAsync implements Download {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private HttpAsyncRequester requester = null;
	
	public DownloadAsync setRequesterBuilder(RequesterBuilder requesterBuilder) {
        H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();
        
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
        		.setSoTimeout(requesterBuilder.soTimeout, TimeUnit.SECONDS)
        		.build();
        
        TlsStrategy tlsStrategy = new H2ClientTlsStrategy(SSLContexts.createSystemDefault(), new SSLSessionVerifier() {
            @Override
            public TlsDetails verify(final NamedEndpoint endpoint, final SSLEngine sslEngine) throws SSLException {
                // IMPORTANT uncomment the following line when running Java 9 or older
                // in order to avoid the illegal reflective access operation warning
            	return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
            }
        });
        
		requester = H2RequesterBootstrap.bootstrap()
				.setH2Config(h2Config)
                .setIOReactorConfig(ioReactorConfig)
                .setMaxTotal(requesterBuilder.maxTotal)
                .setDefaultMaxPerRoute(requesterBuilder.defaultMaxPerRoute)
                .setVersionPolicy(requesterBuilder.versionPolicy)
                .setTlsStrategy(tlsStrategy)
                .create();
		
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
        
        requester.start(); // Need to start
        
        return this;
	}
	
	private final LinkedList<Task> taskQueue = new LinkedList<Task>();
	public DownloadAsync addTask(Task task) {
		taskQueue.add(task);
        return this;
	}
	
	private final List<Header> headerList = new ArrayList<>();
	public DownloadAsync clearHeader() {
		headerList.clear();
        return this;
	}
	public DownloadAsync addHeader(String name, String value) {
		headerList.add(new BasicHeader(name, value));
        return this;
	}
	public DownloadAsync setReferer(String value) {
		addHeader("Referer", value);
        return this;
	}
	public DownloadAsync setUserAgent(String value) {
		addHeader("User-Agent", value);
        return this;
	}
	
	private int threadCount = 1;
	public DownloadAsync setThreadCount(int newValue) {
		threadCount = newValue;
        return this;
	}
	
	private int connectionTimeout = 10;
	public DownloadAsync setConnectionTimeout(int newValue) {
		this.connectionTimeout = newValue;
		return this;
	}
	
	private int progressInterval = 1000;
	public DownloadAsync setProgressInterval(int newValue) {
		this.progressInterval = newValue;
		return this;
	}

	private ExecutorService executor      = null;
	private int 		    taskQueueSize = 0;
	private Worker[]        workerArray   = null;
	private CountDownLatch  stopLatch     = null;
	
	public void startProcessTask() {
		if (requester == null) {
			logger.warn("Set requester using default value of RequestBuilder");
			// Set requester using default value of RequestBuilder
			setRequesterBuilder(RequesterBuilder.custom());
		}
		taskQueueSize = taskQueue.size();
		
		executor = Executors.newFixedThreadPool(threadCount);
		
		stopLatch = new CountDownLatch(taskQueueSize);

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
			stopLatch.await();
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.warn("{} {}", exceptionName, e);
		} finally {
			executor      = null;
			stopLatch     = null;
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
					AsyncClientEndpoint clientEndpoint = requester.connect(target, Timeout.ofSeconds(connectionTimeout)).get(); // FIXME connectionTimeout
					
		            HttpRequest request = new BasicHttpRequest(task.method, task.uri);
		            headerList.forEach(o -> request.addHeader(o));
		            
		            AsyncEntityProducer asyncEnttityProducer = null;
		            if (task.entity != null) {
		            	asyncEnttityProducer = new BasicAsyncEntityProducer(task.entity, task.contentType);
		            }
		            
		            AsyncRequestProducer                                 requestProducer  = new BasicRequestProducer(request, asyncEnttityProducer);
		            AsyncResponseConsumer<Message<HttpResponse, byte[]>> responseConsumer = new BasicResponseConsumer<>(new BasicAsyncEntityConsumer());
		            FutureCallback<Message<HttpResponse, byte[]>>        futureCallback   = new FutureCallback<Message<HttpResponse, byte[]>>() {
		        	    @Override
		        	    public void completed(final Message<HttpResponse, byte[]> message) {
		        	        clientEndpoint.releaseAndReuse();
		        	        
		        	        Result result = new Result(task, message);
		        	        task.process(result);
		        	        stopLatch.countDown();
		        	    }

		        	    @Override
		        	    public void failed(final Exception e) {
		        	        clientEndpoint.releaseAndDiscard();
		        	        logger.warn("failed {}", task.uri);
		        			String exceptionName = e.getClass().getSimpleName();
		        			logger.warn("{} {}", exceptionName, e);
		        	        stopLatch.countDown();
		        	    }

		        	    @Override
		        	    public void cancelled() {
		        	        clientEndpoint.releaseAndDiscard();
		        	        logger.warn("cancelled {}", task.uri);
		        	        stopLatch.countDown();
		        	    }
		            };

		            clientEndpoint.execute(requestProducer, responseConsumer, futureCallback);
				} catch (InterruptedException | ExecutionException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.warn("{} {}", exceptionName, e);
				}
			}
		}
	}
}