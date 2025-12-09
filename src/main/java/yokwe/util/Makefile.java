package yokwe.util;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Makefile implements Comparable<Makefile> {
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
				if (newValue instanceof Storage.LoadSaveFile) {
					var loadSave = (Storage.LoadSaveFile)newValue;
					outputList.add(loadSave.getFile());
				} else if (newValue instanceof Storage.LoadSaveDirectory) {
					var loadSave = (Storage.LoadSaveDirectory)newValue;
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
			return new Makefile(clazz, inputList, outputList);
		}
	}
	
	public static Builder builder() {
		var callerClass = StackWalkerUtil.getCallerStackFrame(StackWalkerUtil.OFFSET_CALLER).getDeclaringClass();
		return new Builder(callerClass);
	}
	
	public final Class<?>   clazz;
	public final List<File> inputList;
	public final List<File> outputList;
	
	private Makefile(Class<?> clazz, List<File> inputs, List<File> outputs) {
		this.clazz      = clazz;
		this.inputList  = inputs;
		this.outputList = outputs;
	}
	
	@Override
	public String toString() {
		return ToString.withFieldName(this);
	}
	@Override
	public int compareTo(Makefile that) {
		return this.clazz.getTypeName().compareTo(that.clazz.getTypeName());
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
			
			Collections.sort(list);
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
		
		Collections.sort(list);
		return list;
	}
}