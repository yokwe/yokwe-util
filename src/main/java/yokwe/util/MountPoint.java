package yokwe.util;

public final class MountPoint {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static final String ENV_MOUNT_POINT = "YOKWE_MOUNT_POINT";
	
	private static final String OS_NAME_FREEBSD = "FreeBSD";
	private static final String OS_NAME_MACOS   = "Mac OS X";
	
	private static final String MOUNT_POINT_FREEBSD = "/mnt";
	private static final String MOUNT_POINT_MACOS   = "/Volumes";

	public  static final String MOUNT_POINT;
	static {
		String envMountPoint = System.getenv(ENV_MOUNT_POINT);
		if (envMountPoint != null) {
			MOUNT_POINT = envMountPoint;
		} else {
			String osName = System.getProperty("os.name");
			
			if (osName.equals(OS_NAME_FREEBSD)) {
				MOUNT_POINT = MOUNT_POINT_FREEBSD;
			} else if (osName.equals(OS_NAME_MACOS)) {
				MOUNT_POINT = MOUNT_POINT_MACOS;
			} else {
				logger.error("Unexpected OS_NAME");
				logger.error("  os.name {}", osName);
				throw new UnexpectedException("Unexpected OS_NAME");
			}
		}
		
		logger.info("MOUNT_POINT {}", MOUNT_POINT);
		if (!FileUtil.isDirectory(MOUNT_POINT)) {
			logger.error("No directory");
			throw new UnexpectedException("No directory");
		}
	}
	
	public static String getMountPoint(String prefix) {
		String path = String.format("%s/%s", MOUNT_POINT, prefix);
		if (!FileUtil.isDirectory(path)) {
			logger.error("No directory");
			logger.error("  path {}", path);
			throw new UnexpectedException("No directory");
		}
		return path;
	}
}
