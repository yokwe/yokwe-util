package yokwe.util;

public final class LoggerUtil {
	public static org.slf4j.Logger getLogger() {
//		Class<?> callerClass = java.lang.invoke.MethodHandles.lookup().lookupClass();
//		Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		Class<?> callerClass = StackWalkerUtil.getCallerStackFrame(StackWalkerUtil.OFFSET_CALLER).getDeclaringClass();
		
		return org.slf4j.LoggerFactory.getLogger(callerClass);
	}
	public static FormatLogger getFormatLogger() {
//		Class<?> callerClass = java.lang.invoke.MethodHandles.lookup().lookupClass();
//		Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		Class<?> callerClass = StackWalkerUtil.getCallerStackFrame(StackWalkerUtil.OFFSET_CALLER).getDeclaringClass();
		
		var logger = org.slf4j.LoggerFactory.getLogger(callerClass);
		return new FormatLogger(logger);
	}
	
	
}
