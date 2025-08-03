package yokwe.util.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import yokwe.util.FileUtil;
import yokwe.util.UnexpectedException;

public class HttpUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private static final HttpRequester requester;
	static {
		SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(30, TimeUnit.SECONDS)
                .build();
		
		requester = RequesterBootstrap.bootstrap()
                .setSocketConfig(socketConfig)
                .setMaxTotal(100)
                .setDefaultMaxPerRoute(50)
                .create();
		
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
	}
	
    private static final HttpCoreContext httpContext = HttpCoreContext.create();

	
	private static final boolean DEFAULT_TRACE      = false;
	private static final String  DEFAULT_TRACE_DIR  = "tmp/http";
	private static final Charset DEFAULT_CHARSET    = StandardCharsets.UTF_8;
	private static final boolean DEFAULT_RAW_DATA   = false;
	public  static final String  DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit";
	private static final String  DEFAULT_CONNECTION = "keep-alive";

	public static class Context {
		boolean trace;
		String  traceDir;
		Charset charset;
		boolean rawData;

		Map<String, String> headerMap;
		
		// For POST
		String postBody;
		String postContentType;
		
		private Context() {
			trace      = DEFAULT_TRACE;
			traceDir   = DEFAULT_TRACE_DIR;
			charset    = DEFAULT_CHARSET;
			rawData    = DEFAULT_RAW_DATA;

			headerMap  =  new TreeMap<>();
			
			postBody        = null;
			postContentType = null;
			
			if (DEFAULT_USER_AGENT != null) headerMap.put("User-Agent", DEFAULT_USER_AGENT);
			if (DEFAULT_CONNECTION != null) headerMap.put("Connection", DEFAULT_CONNECTION);
		}
	}
	
	public static class Result {
		public final String              url;
		public final String              result;
		public final byte[]              rawData;
		public final Map<String, String> headerMap;
		public final String              timestamp;
		public final String              path;
		
		public final HttpResponse        response;
		public final int                 code;
		public final String              reasonPhrase;
		public final ProtocolVersion     version;
		
		public Result (Context context, String url, String result, byte[] rawData,
				HttpResponse response) {
			this.url       = url;
			this.result    = result;
			this.rawData   = rawData;
			this.headerMap = new TreeMap<>();
			Arrays.asList(response.getHeaders()).stream().forEach(o -> this.headerMap.put(o.getName(), o.getValue()));
			this.timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
			
			if (context.trace) {
				this.path = String.format("%s/%s", context.traceDir, timestamp);
				
				if (result != null) {
					FileUtil.write().file(this.path, result);
				} else {
					FileUtil.rawWrite().file(this.path, rawData);
				}
			} else {
				this.path = null;
			}
			
			this.response     = response;
			this.code         = response.getCode();
			this.reasonPhrase = response.getReasonPhrase();
			this.version      = response.getVersion();
		}
		
		@Override
		public String toString() {
			return String.format("{%s %s %d %s %s %d}", timestamp, url, code, reasonPhrase, version, rawData.length);
		}
	}
	
	public static HttpUtil getInstance() {
		return new HttpUtil();
	}
	
	private final Context context;
	private HttpUtil() {
		this.context = new Context();
	}
	
	public HttpUtil withTrace(boolean newValue) {
		context.trace = newValue;
		return this;
	}
	public HttpUtil withTraceDir(String newValue) {
		context.traceDir = newValue;
		return this;
	}
	public HttpUtil withCharset(String newValue) {
		context.charset = Charset.forName(newValue);
		return this;
	}
	public HttpUtil withCharset(Charset newValue) {
		context.charset = newValue;
		return this;
	}
	public HttpUtil withRawData(boolean newValue) {
		context.rawData = newValue;
		return this;
	}
	
	public HttpUtil withReferer(String newValue) {
		withHeader("Referer", newValue);
		return this;
	}
	public HttpUtil withUserAgent(String newValue) {
		withHeader("User-Agent", newValue);
		return this;
	}
	public HttpUtil withCookie(String newValue) {
		withHeader("Cookie", newValue);
		return this;
	}
	public HttpUtil withConnection(String newValue) {
		withHeader("Connection", newValue);
		return this;
	}
	public HttpUtil withHeader(String name, String value) {
		if (value == null) {
			if (context.headerMap.containsKey(name)) {
				context.headerMap.remove(name);
			}
		} else {
			context.headerMap.put(name, value);
		}
		return this;
	}
	public HttpUtil withPost(String body, String contentType) {
		context.postBody        = body;
		context.postContentType = contentType;
		return this;
	}
	
	private static class MyResponse {
		HttpResponse response;
		Charset      charset;
		byte[]       content;
		
		MyResponse(ClassicHttpResponse response) {
			this.response = response;
			
			HttpEntity entity = response.getEntity();
//			logger.debug("entity       {}", entity);			
//			logger.debug("entiry contentTyppe {}", entity.getContentEncoding());
//			logger.debug("entiry contentLenth {}", entity.getContentLength());
//			logger.debug("entiry contentType  {}", entity.getContentType());
//			logger.debug("response code {}", response.getCode());
//			for(var e: response.getHeaders()) {
//				logger.debug("response header       {} {}!", e.getName(), e.getValue());
//			}
			
			if (entity == null) {
				charset = null;
				content = null;
			} else if (entity.isChunked() == false && entity.getContentLength() == 0 && entity.getContentType() == null) {
				charset = null;
				content = null;
			} else {
				String contentTypeString = entity.getContentType();
				ContentType conentType = ContentType.parse(contentTypeString);
				charset = conentType == null ? null : conentType.getCharset();

				InputStream is = null;

				try {
					String contentEncoding = entity.getContentEncoding();
					if (contentEncoding == null) {
						is = entity.getContent();
					} else if (contentEncoding.equalsIgnoreCase("gzip")){
						is = new GZIPInputStream(entity.getContent());
					} else if (contentEncoding.equalsIgnoreCase("defalte")){
						is = new DeflaterInputStream(entity.getContent());
					} else {
						logger.error("Unexpected contentEncoding");
						logger.error("  entity {}", entity);
						throw new UnexpectedException("Unexpected contentEncoding");
					}
					
					content = is.readAllBytes();
					is.close();
					is = null;
				} catch (IOException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.error("{} {}", exceptionName, e);
					throw new UnexpectedException(exceptionName, e);
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							String exceptionName = e.getClass().getSimpleName();
							logger.error("{} {}", exceptionName, e);
							throw new UnexpectedException(exceptionName, e);
						}
					}
				}
			}
//			logger.info("response {} {}", charset, content.length);
		}
	}
	
	private Result download(String url) {
		URI                uri     = URI.create(url);
		HttpHost           target  = HttpHost.create(uri);
        ClassicHttpRequest request = new BasicClassicHttpRequest((context.postBody != null) ? Method.POST : Method.GET, uri);

		for(var e: context.headerMap.entrySet()) {
			request.setHeader(e.getKey(), e.getValue());
		}
		String postBody        = context.postBody;
		String postContentType = context.postContentType;
		context.postBody        = null;
		context.postContentType = null;
		
		if (postBody != null) {
			ContentType contentType = ContentType.parse(postContentType);
			HttpEntity entity = HttpEntities.create(postBody, contentType);
			request.setEntity(entity);
		}

		int retryCount = 0;
		for(;;) {
			try {
				MyResponse   myResponse   = requester.execute(target, request, Timeout.ofSeconds(5), httpContext, o -> new MyResponse(o));
				HttpResponse response     = myResponse.response;
		        int          code         = response.getCode();
		        String       reasonPhrase = response.getReasonPhrase();
		        
				if (code == HttpStatus.SC_TOO_MANY_REQUESTS) { // 429 Too Many Requests
					if (retryCount < 10) {
						retryCount++;
						logger.warn("retry {} {} {}  {}", retryCount, code, reasonPhrase, url);
						Thread.sleep(1000 * retryCount * retryCount); // sleep 1 * retryCount * retryCount sec
						continue;
					}
				}
				if (code == HttpStatus.SC_FORBIDDEN) { // 403
					if (retryCount < 10) {
						retryCount++;
						logger.warn("retry {} {} {}  {}", retryCount, code, reasonPhrase, url);
						Thread.sleep(1000 * retryCount * retryCount); // sleep 1 * retryCount * retryCount sec
						continue;
					}
				}
				if (code == HttpStatus.SC_SERVICE_UNAVAILABLE) { // 503
					if (retryCount < 10) {
						retryCount++;
						logger.warn("retry {} {} {}  {}", retryCount, code, reasonPhrase, url);
						Thread.sleep(1000 * retryCount * retryCount); // sleep 1 * retryCount * retryCount sec
						continue;
					}
				}
				
				retryCount = 0;
				if (code == HttpStatus.SC_NOT_FOUND) { // 404
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
				if (code == HttpStatus.SC_BAD_REQUEST) { // 400
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
				if (code == HttpStatus.SC_MOVED_TEMPORARILY) { // 302
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					Header location = response.getHeader("Location");
					if (location != null) {
						logger.warn("  {} {}!", location.getName(), location.getValue());
					}
					return null;
				}
				if (code == HttpStatus.SC_SERVER_ERROR) { // 500
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
				if (code == HttpStatus.SC_UNAUTHORIZED) { // 401
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
		        if (code == HttpStatus.SC_OK) {
	    			byte[] rawData = myResponse.content;
					
	    			final String result;
					if (context.rawData) {
						result = null;
					} else {
						if (rawData == null) {
							result = null;
						} else {
							Charset charset = myResponse.charset == null ? context.charset : myResponse.charset;
							result = new String(rawData, charset);
						}
					}

	    			Result ret = new Result(context, url, result, rawData, response);
					
					if (ret.path != null) {
						logger.info(String.format("%s %7d %s", ret.timestamp, ret.rawData.length, ret.url));
					}
					return ret;
		        }
		        
				// Other code
				logger.error("statusLine = {} {} {}", code, reasonPhrase, response.getVersion());
				logger.error("url {}", url);
				logger.error("code {}", code);
				{
					if (context.rawData) {
						logger.error("entity RAW_DATA");
					} else {
		    			byte[]  rawData = myResponse.content;
		    			if (rawData == null) {
		    				logger.error("entity  rawData == null");
		    			} else {
							Charset charset = myResponse.charset == null ? context.charset : myResponse.charset;
					    	logger.error("entity  {}", new String(rawData, charset));
		    			}
					}
				}
				throw new UnexpectedException("download");
			} catch (SocketTimeoutException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.warn("{}", exceptionName);
				return null;
			} catch (NoHttpResponseException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.warn("{}", exceptionName);
				return null;
			} catch (IOException | HttpException | InterruptedException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	public String downloadString(String url) {
		HttpUtil.Result result = this.withRawData(false).download(url);
		if (result == null || result.result == null) {
			logger.error("Unexpected");
			logger.error("  url     {}", url);
			logger.error("  result  {}", result);
			throw new UnexpectedException("Unexpected");
		}
		return result.result;
	}
	public byte[] downloadRaw(String url) {
		HttpUtil.Result result = this.withRawData(true).download(url);
		if (result == null || result.rawData == null) {
			logger.error("Unexpected");
			logger.error("  url     {}", url);
			logger.error("  result  {}", result);
			throw new UnexpectedException("Unexpected");
		}
		return result.rawData;
	}
}
