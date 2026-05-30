package yokwe.util.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;

import yokwe.util.UnexpectedException;

public class Task {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	public final URI    uri;
	public final Consumer<Result> consumer;

	public final Method      method;
	public final byte[]      entity;
	public final ContentType contentType;


	public static Task get(Consumer<Result> consumer, URI uri) {
		return new Task(consumer, uri, Method.GET, null, null);
	}

	public static Task post(Consumer<Result> consumer, URI uri, String content, ContentType contentType) {
		Charset charset = contentType.getCharset();
		if (charset == null) {
			if (contentType.getMimeType().equals("application/json")) {
				charset = StandardCharsets.UTF_8;
			} else {
				logger.error("contentType  {}", contentType);
				throw new UnexpectedException("Unexpected contentType");
			}
		}

		return new Task(consumer, uri, Method.POST, content.getBytes(charset), contentType);
	}
	public static Task post(Consumer<Result> consumer, URI uri, byte[] content, ContentType contentType) {
		return new Task(consumer, uri, Method.POST, content, contentType);
	}
	private Task(Consumer<Result> consumer, URI uri, Method method, byte[] entity, ContentType contentType) {
		this.consumer    = consumer;
		this.uri         = uri;
		this.method      = method;
		this.entity      = entity;
		this.contentType = contentType;
	}

	public void process(Result result) {
		consumer.accept(result);
	}
}