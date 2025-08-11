package yokwe.util;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static List<ProcessHandle> killOwnProcess(String... names) {
		var commandSet = Arrays.stream(names).collect(Collectors.toSet());
		return killOwnProcess(commandSet);
	}
	public static List<ProcessHandle> killOwnProcess(Set<String> commandSet) {
		var username = System.getProperty("user.name");
		return killProcess(username, commandSet);
	}
	public static List<ProcessHandle> killProcess(String username, Set<String> commandSet) {
		var listA = ProcessHandle.allProcesses().filter(o -> commandSet.contains(o.info().command().orElse("")) && o.info().user().orElse("").equals(username) && o.isAlive()).toList();
		if (listA.isEmpty()) return listA;
		
		for(var e: listA) e.destroy();
		ThreadUtil.sleep(Duration.ofSeconds(1));
	
		var listB = ProcessHandle.allProcesses().filter(o -> commandSet.contains(o.info().command().orElse("")) && o.info().user().orElse("").equals(username) && o.isAlive()).toList();
		if (listB.isEmpty()) return listA;
		
		for(var e: listB) e.destroyForcibly();
		ThreadUtil.sleep(Duration.ofSeconds(1));
	
		var listC = ProcessHandle.allProcesses().filter(o -> commandSet.contains(o.info().command().orElse("")) && o.info().user().orElse("").equals(username) && o.isAlive()).toList();
		if (listC.isEmpty()) return listB;
		
		logger.error("Unexpected");
		logger.error("  username  {}", username);
		for(var e: listC) logger.error("  {}", toString(e));
		throw new UnexpectedException("Unexpected");
	}
	
	public static String toString(ProcessHandle processHandle) {
		return String.format("%s  %s", processHandle.toString(), processHandle.info().command().orElse(""));
	}
}
