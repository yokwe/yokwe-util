package yokwe.util.json;

import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

public class JSON {
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Name {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Ignore {
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Optional {
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface DateTimeFormat {
		String value();
	}
	
	
	//
	// unmarshal
	//
	public static <E> E unmarshal(Class<E> clazz, String jsonString) {
		return Unmarshal.getInstance(clazz, jsonString);
	}
	public static <E> E unmarshal(Class<E> clazz, Reader reader) {
		return Unmarshal.getInstance(clazz, reader);
	}
	
	//
	// getList
	//
	public static <E> List<E> getList(Class<E> clazz, String jsonString) {
		return Unmarshal.getList(clazz, jsonString);
	}
	public static <E> List<E> getList(Class<E> clazz, Reader reader) {
		return Unmarshal.getList(clazz, reader);
	}
	
	//
	// marshal
	//
	public static final class Options {
		private List<String> includePackageList;
		private List<String> excludePackageList;
		
		private Options() {
			this.includePackageList = new ArrayList<>();
			this.excludePackageList = new ArrayList<>();
		}
		
		private Options(Options that) {
			this.includePackageList = that.includePackageList;
			this.excludePackageList = that.excludePackageList;
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
		DEFAULT_OPTIONS.excludePackage("java.", "javax.", "jdk.", "sun.", "com.sun.");
	}

	public static String marshal(Object object) {
		return Marshal.toString(object);
	}
	
	
	
	//
	// toJSONString
	//
	public static String toJSONString(Object object) {
		return Marshal.toString(object);
	}
}