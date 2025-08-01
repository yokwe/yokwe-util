package yokwe.util;

import java.lang.StackWalker.StackFrame;

public class StackWalkerUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static final int OFFSET_CALLER = 2;
	public static StackFrame getCallerStackFrame(int offset) {
		int size = offset + 1;
		StackFrame[] array = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> s.limit(size).toArray(StackFrame[]::new));
		if (array == null) {
			logger.error("array == null");
			throw new UnexpectedException("array == null");
		}
		if (array.length != size) {
			logger.error("Unexpected array length");
			logger.error("  size   {}", size);
			logger.error("  array  {}", array.length);
			for(int i = 0; i < array.length; i++) {
				logger.error("  {}  {}", i, array[i].toString());
			}
			throw new UnexpectedException("Unexpected array length");
		}
		
		return array[offset];
	}
	public static StackFrame getCallerStackFrame() {
		// offset 0 -- getCallerStackFrame(offset)
		// offset 1 -- getCallerStackFrame()
		// offset 2 -- caller
		return getCallerStackFrame(OFFSET_CALLER);
	}
	public static String getCallerMethodName() {
		// offset 0 -- getCallerStackFrame(offset)
		// offset 1 -- getCallerStackFrame()
		// offset 2 -- caller
		return getCallerStackFrame(OFFSET_CALLER).getMethodName();
	}
//	public static Class<?> getCallerClass() {
//		//Class<?> callerClass = java.lang.invoke.MethodHandles.lookup().lookupClass();
//		Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
//		return callerClass;
//	}
	public static Class<?> getCallerClass() {
		// offset 0 -- getCallerStackFrame(2)
		// offset 1 -- getCallerStackFrame()
		// offset 2 -- caller
		return getCallerStackFrame(OFFSET_CALLER).getDeclaringClass();
	}

}
