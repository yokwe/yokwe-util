package yokwe.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	public static class ProcessInfo {
		public final ProcessHandle processHandle;

		public final long   pid;
		public final String user;
		public final String command;
		public final String args;

		public static ProcessInfo getInstance(ProcessHandle processHandle) {
			if (!processHandle.isAlive()) {
				return null;
			}
			var info = processHandle.info();
			if (info.user().isEmpty()) {
				return null;
			}
			if (info.command().isEmpty()) {
				return null;
			}
			if (info.arguments().isEmpty()) {
				return null;
			}

			var pid     = processHandle.pid();
			var user    = info.user().get();
			var command = info.command().get();
			var args    = String.join(" ", info.arguments().get());

			return new ProcessInfo(processHandle, pid, user, command, args);
		}
		public static ProcessInfo getInstance() {
			return getInstance(ProcessHandle.current());
		}

		private ProcessInfo(ProcessHandle processHandle, long pid, String user, String command, String args) {
			this.processHandle = processHandle;
			this.pid           = pid;
			this.user          = user;
			this.command       = command;
			this.args          = args;
		}

		@Override
		public String toString() {
			return String.format("{%d  %s  %s}", pid, user, command);
		}

		public boolean isJavaCommand() {
			return command.endsWith("/bin/java");
		}
		public String lastArg() {
			int pos = args.lastIndexOf(" ");
			return pos != -1 ? args.substring(pos + 1) : "?";
		}
		public boolean destroy() {
			return processHandle.destroy();
		}
		public boolean destroyForcibly() {
			return processHandle.destroyForcibly();
		}
	}

	public static List<ProcessInfo> listProcess() {
		var ret = new ArrayList<ProcessInfo>();
		for(var e: ProcessHandle.allProcesses().toList()) {
			var info = ProcessInfo.getInstance(e);
			if (info != null) {
				ret.add(info);
			}
		}
		return ret;
	}
	public static List<ProcessInfo> listSameProcess() {
		var self = ProcessInfo.getInstance();

		return listProcess().stream().filter(o -> o.pid != self.pid && o.user.equals(self.user) && o.command.equals(self.command) && o.args.equals(self.args)).toList();
	}


	public static List<ProcessInfo> killOwnProcess(String... names) {
		var commandSet = Arrays.stream(names).collect(Collectors.toSet());
		return killOwnProcess(commandSet);
	}
	public static List<ProcessInfo> killOwnProcess(Set<String> commandSet) {
		var self = ProcessInfo.getInstance();
		return killProcess(self.user, commandSet);
	}
	public static List<ProcessInfo> killProcess(String username, Set<String> commandSet) {
		var listA = listProcess().stream().filter(o -> o.user.equals(username)).filter(o -> commandSet.contains(o.command)).toList();
		if (listA.isEmpty()) {
			return listA;
		}

		for(var e: listA) {
			e.destroy();
		}
		ThreadUtil.sleep(Duration.ofSeconds(1));

		var listB = listProcess().stream().filter(o -> o.user.equals(username)).filter(o -> commandSet.contains(o.command)).toList();
		if (listB.isEmpty()) {
			return listA;
		}

		for(var e: listB) {
			e.destroyForcibly();
		}
		ThreadUtil.sleep(Duration.ofSeconds(1));

		var listC = listProcess().stream().filter(o -> o.user.equals(username)).filter(o -> commandSet.contains(o.command)).toList();
		if (listC.isEmpty()) {
			return listB;
		}

		logger.error("Unexpected");
		logger.error("  username  {}", username);
		for(var e: listC) {
			logger.error("  {}", e);
		}
		throw new UnexpectedException("Unexpected");
	}
}
