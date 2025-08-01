package yokwe.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


public class FTPUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static final String  DEFAULT_USERNAME = "anonymous";
	private static final String  DEFAULT_PASSWORD = "anonymous@example.com";
	
	private static final boolean USE_EPSV_WITH_IPV4     = true;
	private static final boolean USE_LOCAL_PASSIVE_MODE = true;
	
	//
	// returns null for FileNotFoundException
	//
	
	// downloadRaw() use url
	public static byte[] downloadRaw(URL url) {
		byte[] result = null;
		
		String protocol = url.getProtocol();
		if (!protocol.equals("ftp")) {
			logger.info("Unexpected protocol");
			logger.info("  url {}!", url);
			throw new UnexpectedException("Unexpected protocol");
		}

		final String host        = url.getHost();
		final String directoryName;
		final String fileName;
		{
			File file     = new File(url.getPath());
			directoryName = file.getParent();
			fileName      = file.getName();
		}
		
		final String username;
		final String password;
		{
			String userInfo = url.getUserInfo();
			if (userInfo == null) {
				username = DEFAULT_USERNAME;
				password = DEFAULT_PASSWORD;
			} else {
				int pos = userInfo.indexOf(':');
				if (pos != -1) {
					username = userInfo.substring(0, pos);
					password = userInfo.substring(pos + 1);
				} else {
					username = userInfo;
					password = null;
				}
			}
		}

		final int port;
		{
			int myPort = url.getPort();
			 if (myPort == -1) {
				 port = url.getDefaultPort();
			 } else {
				 port = myPort;
			 }
		}
		
		FTPClient ftpClient = new FTPClient();
		
		// output command and response log between server and client
//		ftpClient.addProtocolCommandListener(
//			new org.apache.commons.net.PrintCommandListener(
//				new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8)), true));
		
		try {
						
			// connect
			ftpClient.connect(host, port);
			checkReplyCode(ftpClient, "connect");
			
			// login
			ftpClient.login(username, password);
			checkReplyCode(ftpClient, "login");
			
			// before retrieve
			ftpClient.setUseEPSVwithIPv4(USE_EPSV_WITH_IPV4);
			if (USE_LOCAL_PASSIVE_MODE) ftpClient.enterLocalPassiveMode();

			// type binary
			ftpClient.type(FTP.BINARY_FILE_TYPE);
			checkReplyCode(ftpClient, "type");
			
			// change working directory
			ftpClient.changeWorkingDirectory(directoryName);
			checkReplyCode(ftpClient, "cwd");

			// use size to check existence of file
			String sizeString = ftpClient.getSize(fileName);
			if (sizeString != null) {
				 // file exists
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				boolean retrieveResult = ftpClient.retrieveFile(fileName, output);
				output.close();
				
				if (retrieveResult) {
					result = output.toByteArray();
				} else {
					logger.error("retrieve failed");
					logger.error("  url   {}", url);
					logger.error("  size  {}", sizeString);
					for(var string: ftpClient.getReplyStrings()) {

						logger.error("  reply {}", string);
					}
					throw new UnexpectedException("retrieve failed");
				}
			} else {
				// file does not exit
				logger.warn("file doesn't exist");
				logger.warn("  url  {}", url);
			}
			
			// logout
			ftpClient.logout();
			checkReplyCode(ftpClient, "logout");
		} catch (SocketException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			return null;
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		} finally {
			if (ftpClient.isConnected()) {
			    try {
					ftpClient.disconnect();
				} catch (IOException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.error("{} {}", exceptionName, e);
					throw new UnexpectedException(exceptionName, e);
				}
			}
		}
		
		return result;
	}
	private static void checkReplyCode(FTPClient ftp, String command) {
		int code = ftp.getReplyCode();
		if (!FTPReply.isPositiveCompletion(code)) {
			logger.error("Unexpected reply code");
			logger.error("  command  {}", command);
			for(var string: ftp.getReplyStrings()) {
				logger.error("  reply    {}", string);
			}
			throw new UnexpectedException("Unexpected reply code");
		}
	}
	// downloadRaw() use urlString
	public static byte[] downloadRaw(String urlString) {
		return downloadRaw(URLUtil.toURL(urlString));
	}
	
	// downloadString() use URL
	public static String downloadString(URL url, Charset charset) {
		var raw = downloadRaw(url);
		return (raw == null) ? null : new String(raw, charset);
	}
	public static String downloadString(URL url) {
		return downloadString(url, StandardCharsets.UTF_8);
	}
	// downloadString() use urlString
	public static String downloadString(String urlString, Charset charset) {
		return downloadString(URLUtil.toURL(urlString), charset);
	}
	public static String downloadString(String urlString) {
		return downloadString(URLUtil.toURL(urlString));
	}
}
