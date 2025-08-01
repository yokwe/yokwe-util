package yokwe.util.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import org.apache.hc.core5.http.ContentType;

import yokwe.util.UnexpectedException;

public class FileTask {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static enum Mode {
		BINARY,
		TEXT
	}
	
	private static class MyConsumer implements Consumer<Result> {
		private final File file;
		private final Mode mode;
		private final Charset defaultCharset;
		
		public MyConsumer(File file, Mode mode, Charset defaultCharset) {
			this.file           = file;
			this.mode           = mode;
			this.defaultCharset = defaultCharset;
		}
		
		@Override
		public void accept(Result result) {
			switch (mode) {
			case BINARY:
				saveAsBinaryFile(result);
				break;
			case TEXT:
				saveAsTextFile(result);
				break;
			default:
				logger.error("Unexpected mode");
				logger.error("  mode {}", mode);
				throw new UnexpectedException("Unexpected mode");
			}
		}
		
		private void saveAsBinaryFile(Result result) {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			
			try (FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(result.body);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		private void saveAsTextFile(Result result) {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			
			Charset charset = result.charset;
			if (charset == null) {
				if (defaultCharset != null) {
					charset = defaultCharset;
				} else {
					logger.error("charset is null");
					logger.error("  uri         {}", result.task.uri);
					logger.error("  contentType {}", result.contentType);
					throw new UnexpectedException("charset is null");
				}
			}
			try (FileWriter fw = new FileWriter(file)) {
				fw.write(new String(result.body, charset));
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	private static Task get(String uriString, File file, Mode mode, Charset defaultCharset) {
		return Task.get(new MyConsumer(file, mode, defaultCharset), URI.create(uriString));
	}
	
	private static Task post(String uriString, File file, Mode mode, Charset defaultCharset, String content, String contentTypeString) {
		return Task.post(new MyConsumer(file, mode, defaultCharset), URI.create(uriString), content, ContentType.parse(contentTypeString));
	}
	
	public static Task getRaw(String uriString, File file) {
		return get(uriString, file, Mode.BINARY, null);
	}
	public static Task get(String uriString, File file) {
		return get(uriString, file, Mode.TEXT, null);
	}
	public static Task get(String uriString, File file, Charset defaultCharset) {
		return get(uriString, file, Mode.TEXT, defaultCharset);
	}
	
	public static Task post(String uriString, File file, String content, String contentTypeString) {
		return post(uriString, file, Mode.TEXT, null, content, contentTypeString);
	}
	
	private static final String CONTENT_TYPE_WWW_FORM = "application/x-www-form-urlencoded; charset=UTF-8";
	public static Task post(String uriString, File file, String content) {
		return post(uriString, file, Mode.TEXT, null, content, CONTENT_TYPE_WWW_FORM);
	}
}