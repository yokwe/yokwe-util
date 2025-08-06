package yokwe.util.makefile;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import yokwe.util.ClassUtil;
import yokwe.util.StackWalkerUtil;
import yokwe.util.Storage;
import yokwe.util.ToString;
import yokwe.util.UnexpectedException;

public class Makefile {
	static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static class Builder {
		private Class<?>   clazz;
		private List<File> inputList  = new ArrayList<>();
		private List<File> outputList = new ArrayList<>();
		
		private Builder(Class<?> clazz) {
			this.clazz = clazz;
		}
		
		public Builder input(Storage.LoadSave... newValues) {
			for(var newValue: newValues) {
				if (newValue instanceof Storage.LoadSaveFileGeneric) {
					var loadSave = (Storage.LoadSaveFileGeneric<?>)newValue;
					inputList.add(loadSave.getFile());
				} else if (newValue instanceof Storage.LoadSaveDirectoryGeneric) {
					var loadSave = (Storage.LoadSaveDirectoryGeneric<?>)newValue;
					inputList.add(loadSave.getTouchFile());
				} else {
					logger.error("Unexpected newValues");
					logger.error("  newValues  {}", Arrays.stream(newValues).toList());
					throw new UnexpectedException("Unexpected newValues");
				}
			}
			return this;
		}
		public Builder output(Storage.LoadSave... newValues) {
			for(var newValue: newValues) {
				if (newValue instanceof Storage.LoadSaveFileGeneric) {
					var loadSave = (Storage.LoadSaveFileGeneric<?>)newValue;
					outputList.add(loadSave.getFile());
				} else if (newValue instanceof Storage.LoadSaveDirectoryGeneric) {
					var loadSave = (Storage.LoadSaveDirectoryGeneric<?>)newValue;
					outputList.add(loadSave.getTouchFile());
				} else {
					logger.error("Unexpected newValues");
					logger.error("  newValues  {}", Arrays.stream(newValues).toList());
					throw new UnexpectedException("Unexpected newValues");
				}
			}
			return this;
		}
		public Makefile build() {
			return new Makefile(clazz, inputList.toArray(File[]::new), outputList.toArray(File[]::new));
		}
	}
	
	public static Builder builder() {
		var callerClass = StackWalkerUtil.getCallerStackFrame(StackWalkerUtil.OFFSET_CALLER).getDeclaringClass();
		return new Builder(callerClass);
	}
	
	public Class<?> clazz;
	public String   group;
	public String   target;
	public File[]   inputs;
	public File[]   outputs;
	
	private Makefile(Class<?> clazz, File[] inputs, File[] outputs) {
		this.clazz   = clazz;
		this.inputs  = inputs;
		this.outputs = outputs;
		
		var names = clazz.getCanonicalName().toLowerCase().split("\\.");
		var name1 = names[names.length - 3];
		var name2 = names[names.length - 2];
		var name  = names[names.length - 1];
		
		this.group   = (name2.equals("jp") || name2.equals("us")) ? (name1 + "-" + name2) : name2;
		this.target  = toTarget(group, name);
	}
	
	private String toTarget(String group, String name) {
		var buffer = new StringBuilder(group.length() + name.length() + 10);
		buffer.append(group);
		
		var string = new String(name);
		for(;;) {
			boolean modified = false;
			for(var token: tokens) {
				if (!string.startsWith(token)) continue;
				buffer.append("-").append(token);
				string = string.substring(token.length());
				modified = true;
				break;
			}
			if (string.isEmpty()) break;
			if (modified) continue;
			
			logger.error("Unexpected string");
			logger.error("  group   {}", group);
			logger.error("  name    {}", name);
			logger.error("  string  {}", string);
			throw new UnexpectedException("Unexpected string");
		}
		
		return buffer.toString();
	}
	private static String[] tokens = {
		"update", "stock", "info", "fund", "div", "price", "nisa", "etf", "etn", "infra",
		"kessan", "reit", "code", "name", "detail", "json", "list", "ohlcv", "value", "jreit",
		"trading", "jp", "us", "company", "all", "fx", "rate", "2", "intra", "day", "report",
	};
	
	@Override
	public String toString() {
		return ToString.withFieldName(this);
	}
	
	public static List<Makefile> scanModule(Module module) {
		try {
			var list = new ArrayList<Makefile>();
			
			for(var clazz: ClassUtil.findClassInModule(module)) {
				for(var field: clazz.getDeclaredFields()) {
					field.setAccessible(true);
					if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(Makefile.class)) {
						var makefile = (Makefile)field.get(null);
						list.add(makefile);
					}
				}
			}
			
			return list;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static List<Makefile> scanModule(Module... modules) {
		var list = new ArrayList<Makefile>();
		
		for(var module: modules) {
			list.addAll(scanModule(module));
		}
		
		return list;
	}
}