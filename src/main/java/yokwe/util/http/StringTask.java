package yokwe.util.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ContentType;

import yokwe.util.UnexpectedException;

public class StringTask {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static class MyConsumer implements Consumer<Result> {
		private Consumer<String> consumer;
		private Charset          defaultCharset;
		
		public MyConsumer(Consumer<String> consumer, Charset defaultCharset) {
			this.consumer        = consumer;
			this.defaultCharset  = defaultCharset;
		}
		
		@Override
		public void accept(Result result) {
			String page;
			if (result.body == null) {
				page = "";
			} else {
				Charset myCharset = result.charset;
				
				if (myCharset == null) {
					if (defaultCharset == null) {
						logger.error("defaultCharset is null");
						logger.error("  uri         {}", result.task.uri);
						logger.error("  header {}", Arrays.asList(result.head.getHeaders()));
						throw new UnexpectedException("defaultCharset is null");
					} else {
						myCharset = defaultCharset;
					}
				}
				page = new String(result.body, myCharset);
			}
			consumer.accept(page);
		}
	}
	
	public static Task get(String uriString, Consumer<String> consumer, Charset defaultCharset) {
		return Task.get(new MyConsumer(consumer, defaultCharset), URI.create(uriString));
	}
	
	public static Task get(String uriString, Consumer<String> consumer) {
		return get(uriString, consumer, null);
	}
	
	public static Task post(String uriString, Consumer<String> consumer, String content, String contentTypeString) {
		return Task.post(new MyConsumer(consumer, null), URI.create(uriString), content, ContentType.parse(contentTypeString));
	}
	
	private static final String CONTENT_TYPE_WWW_FORM = "application/x-www-form-urlencoded; charset=UTF-8";
	public static Task post(String uriString, Consumer<String> consumer, String content) {
		return post(uriString, consumer, content, CONTENT_TYPE_WWW_FORM);
	}
}