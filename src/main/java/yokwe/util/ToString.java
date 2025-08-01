package yokwe.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Function;

public class ToString {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static class ClassInfo {
		private static Map<String, ClassInfo> map = new HashMap<>();

		private static class FieldInfo {
			final Field  field;
			final String name;
			
			FieldInfo(Field field) {
				this.field = field;
				this.name  = field.getName();
			}
			
			Object get(Object o) {
				try {
					return field.get(o);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.error("{} {}", exceptionName, e);
					throw new UnexpectedException(exceptionName, e);
				}
			}
		}
		
		private static ClassInfo getInstance(Class<?> clazz) {
			var key = clazz.getTypeName();
			var ret = map.get(key);
			if (ret != null) return ret;
			
			var value = new ClassInfo(clazz);
			map.put(key, value);
			return value;
		}
		
		private final FieldInfo[]  fieldInfos;
		
		private ClassInfo(Class<?> clazz) {			
			var list = new ArrayList<FieldInfo>();
			for(var field: clazz.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true); // allow access private field
				list.add(new FieldInfo(field));
			}
			
			this.fieldInfos = list.toArray(FieldInfo[]::new);
		}
	}
	
	private static final Map<String, Function<Object, String>> functionMap = new TreeMap<>();
	static {
		// special for java.* class
		functionMap.put(Boolean.class.getTypeName(),    o -> Boolean.toString((boolean)o));
		functionMap.put(Double.class.getTypeName(),     o -> Double.toString((double)o));
		functionMap.put(Float.class.getTypeName(),      o -> Float.toString((float)o));
		functionMap.put(Integer.class.getTypeName(),    o -> Integer.toString((int)o));
		functionMap.put(Long.class.getTypeName(),       o -> Long.toString((long)o));
		functionMap.put(Short.class.getTypeName(),      o -> Short.toString((short)o));
		functionMap.put(Byte.class.getTypeName(),       o -> Byte.toString((byte)o));
		functionMap.put(Character.class.getTypeName(),  o -> "'" + String.valueOf((char)o).replace("\\", "\\\\").replace("'", "\\\'") + "'");
		functionMap.put(String.class.getTypeName(),     o -> "\"" + (String)o.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
		functionMap.put(BigDecimal.class.getTypeName(), o -> ((BigDecimal)o).toPlainString());
	}
	
	
	public static final class Options {
		private boolean      withFieldName;
		private List<String> includePackageList;
		private List<String> excludePackageList;
		
		private Options() {
			this.withFieldName      = true;
			this.includePackageList = new ArrayList<>();
			this.excludePackageList = new ArrayList<>();
		}
		
		private Options(Options that) {
			this.withFieldName      = that.withFieldName;
			this.includePackageList = that.includePackageList;
			this.excludePackageList = that.excludePackageList;
		}
		public Options withFieldName(boolean value) {
			withFieldName = value;
			return this;
		}
		public Options includePackage(String... stringArray) {
			for(var string: stringArray) {
				includePackageList.add(string);
			}
			return this;
		}
		public Options excludePackage(String... stringArray) {
			for(var string: stringArray) {
				excludePackageList.add(string);
			}
			return this;
		}
		
		public boolean matchIncludePackage(String typeName) {
			for(var packagePrefix: includePackageList) {
				if (typeName.startsWith(packagePrefix)) return true;
			}
			return false;
		}
		public boolean matchExcludePackage(String typeName) {
			for(var packagePrefix: excludePackageList) {
				if (typeName.startsWith(packagePrefix)) return true;
			}
			return false;
		}
	}
	
	public static final Options DEFAULT_OPTIONS;
	static {
		DEFAULT_OPTIONS = new Options();
		DEFAULT_OPTIONS.withFieldName(true);
		DEFAULT_OPTIONS.excludePackage("java.", "javax.", "jdk.", "sun.", "com.sun.");
	}
	
	
	public static String withFieldName(Object o) {
		return withOptions(o, new Options(DEFAULT_OPTIONS).withFieldName(true));
	}
	public static String withoutFieldName(Object o) {
		return withOptions(o, new Options(DEFAULT_OPTIONS).withFieldName(false));
	}
	public static String withOptions(Object o, Options options) {
		if (o == null) return "null";
		
		var clazz     = o.getClass();
		if (clazz.isArray()) return toStringArray(o, options);
		if (clazz.isEnum())  return o.toString();

		var typeName  = clazz.getTypeName();
		var function  = functionMap.get(typeName);
		if (function != null) return function.apply(o);
		
		if (options.matchIncludePackage(typeName)) return toStringObject(o, options);
		if (options.matchExcludePackage(typeName)) return o.toString();
				
		return toStringObject(o, options);
	}
	
	private static String toStringArray(Object o, Options options) {
		var stringJoiner = new StringJoiner(", ", "[", "]");
		
		var length = Array.getLength(o);
		for(int i = 0; i < length; i++) {
			var element = Array.get(o, i);
			stringJoiner.add(withOptions(element, options));
		}
		
		return stringJoiner.toString();
	}
	
	private static String toStringObject(Object o, Options options) {
		var stringJoiner = new StringJoiner(", ", "{", "}");
		
		for(var fieldInfo: ClassInfo.getInstance(o.getClass()).fieldInfos) {
			if (options.withFieldName) {
				stringJoiner.add(fieldInfo.name + ": " + withOptions(fieldInfo.get(o), options));
			} else {
				stringJoiner.add(withOptions(fieldInfo.get(o), options));
			}
		}
		
		return stringJoiner.toString();
	}
}
