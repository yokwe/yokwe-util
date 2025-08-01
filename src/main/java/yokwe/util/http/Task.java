package yokwe.util.http;

import java.net.URI;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;

public class Task {
	public final URI    uri;
	public final Consumer<Result> consumer;
	
	public final Method      method;
	public final byte[]      entity;
	public final ContentType contentType;
	
	
	public static Task get(Consumer<Result> consumer, URI uri) {
		return new Task(consumer, uri, Method.GET, null, null);
	}

	public static Task post(Consumer<Result> consumer, URI uri, String content, ContentType contentType) {
		return new Task(consumer, uri, Method.POST, content.getBytes(contentType.getCharset()), contentType);
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