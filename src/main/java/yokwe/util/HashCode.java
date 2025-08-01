package yokwe.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashCode {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	public static final String DEFAULT_ALGORITHM = "md5";
	
	private static MessageDigest getMessageDigest(String algorithm) {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			return md;
		} catch (NoSuchAlgorithmException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	public static byte[] getHashCode(byte[] data, String algorithm) {
		MessageDigest md = getMessageDigest(algorithm);
		return md.digest(data);
	}
	public static byte[] getHashCode(byte[] data) {
		return getHashCode(data, DEFAULT_ALGORITHM);
	}
	
	public static byte[] getHashCode(File file, String algorithm) {
		try (FileInputStream fis = new FileInputStream(file)) {
			return getHashCode(fis, algorithm);
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static byte[] getHashCode(File data) {
		return getHashCode(data, DEFAULT_ALGORITHM);
	}
	
	public static byte[] getHashCode(InputStream is, String algorithm) {
		try {
			MessageDigest md = getMessageDigest(algorithm);
			
			byte buffer[] = new byte[1024 * 64];
			for(;;) {
				int len = is.read(buffer);
				if (len == -1) break;
				md.update(buffer, 0, len);
			}
			
			return md.digest();
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	public static String getHashHexString(File file) {
		return StringUtil.toHexString(getHashCode(file, DEFAULT_ALGORITHM));
	}
}
