package yokwe.util;

import java.io.File;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Storage {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static void initialize() {};
	
	private static final String DATA_PATH_FILE = "../yokwe-base/data/DataPathLocation";
	private static String getDataPath() {
		logger.info("DATA_PATH_FILE  !{}!", DATA_PATH_FILE);
		// Sanity check
		if (!FileUtil.canRead(DATA_PATH_FILE)) {
			throw new UnexpectedException("Cannot read file");
		}
		
		String dataPath = FileUtil.read().file(DATA_PATH_FILE);
		logger.info("DATA_PATH       !{}!", dataPath);
		// Sanity check
		if (dataPath.isEmpty()) {
			logger.error("Empty dataPath");
			throw new UnexpectedException("Empty dataPath");
		}		
		if (!FileUtil.isDirectory(dataPath)) {
			logger.error("Not directory");
			throw new UnexpectedException("Not directory");
		}		
		return dataPath;
	}
	private static final String DATA_PATH = getDataPath();
	
	public static final Storage storage = new Storage(new File(DATA_PATH));

	private final File file;
	private Storage(File file) {
		this.file = file;
		// sanity check
		{
			if (!file.exists()) {
				logger.error("file does not exit");
				logger.error("  file  {}", file.getPath());
				throw new UnexpectedException("file does not exit");
			}
			if (!file.isDirectory()) {
				logger.error("file is not directory");
				logger.error("  file  {}", file.getPath());
				throw new UnexpectedException("file is not directory");
			}
		}
	}
	public Storage(Storage storage, String name) {
		this(storage.getFile(name));
	}
	public Storage(Storage storage, String... names) {
		this(storage.getFile(names));
	}
	//
	@Override
	public String toString() {
		return file.getPath();
	}
	//
	public File getFile() {
		return file;
	}
	public File getFile(String name) {
		return new File(file, name);
	}
	public File getFile(String... names) {
		var file = this.file;
		for(var name: names) {
			file = new File(file, name);
		}
		return file;
	}
	public Storage getStorage(String name) {
		return new Storage(getFile(name));
	}
	public Storage getStorage(String... names) {
		return new Storage(getFile(names));
	}
	
	
	//
	// LoadSaveFileXXX
	//
	public interface LoadSave {}
	
	public abstract static class LoadSaveFileGeneric<T> implements LoadSave {
		protected final Storage  storage;
		protected final String   name;
		protected final File     file;
		
		public LoadSaveFileGeneric(Storage storage, String name) {
			this.storage = storage;
			this.name    = name;
			this.file    = storage.getFile(name);
		}
		public File getFile() {
			return file;
		}
		public File getFile(String newName) {
			return storage.getFile(newName);
		}
		public File getOldFile() {
			return storage.getFile(name + "-old");
		}
		public void copyToOldFile() {
			if (file.canRead()) {
				FileUtil.copy(getFile(), getOldFile());
			}
		}

		public abstract T      load();
		public abstract void   save(T value);
		public abstract String read();
		public abstract void   write(String value);
	}
	public static class LoadSaveFileString extends LoadSaveFileGeneric<String> {
		public LoadSaveFileString(Storage storage, String name) {
			super(storage, name);
		}

		@Override
		public String load() {
			return read();
		}
		@Override
		public void save(String string) {
			write(string);
		}
		@Override
		public String read() {
			return FileUtil.read().file(getFile());
		}
		@Override
		public void write(String string) {
			FileUtil.write().file(getFile(), string);
		}
	}
	public static class LoadSaveFileList<E extends Comparable<E>> extends LoadSaveFileGeneric<List<E>> {
		protected final Class<E> clazz;
		
		public LoadSaveFileList(Class<E> clazz, Storage storage, String name) {
			super(storage, name);
			this.clazz = clazz;
		}
		
		@Override
		public List<E> load() {
			return CSVUtil.read(clazz).file(file);
		}
		@Override
		public void save(List<E> list) {
			Collections.sort(list);
			CSVUtil.write(clazz).file(file, list);
		}
		@Override
		public String read() {
			return file.canRead() ? FileUtil.read().file(file) : null;
		}
		@Override
		public void write(String string) {
			FileUtil.write().file(file, string);
		}
		
		
		public List<E> load(Reader reader) {
			return CSVUtil.read(clazz).file(reader);
		}
		public List<E> getList() {
			var list = load();
			return list == null ? new ArrayList<>() : list;
		}
		public List<E> getList(Reader reader) {
			var list = load(reader);
			return list == null ? new ArrayList<>() : list;
		}
		
		public void save(Collection<E> collection) {
			save(new ArrayList<E>(collection));
		}
		
		public String read(List<E> list) {
			var sw = new StringWriter();
			Collections.sort(list);
			CSVUtil.write(clazz).file(sw, list);
			return sw.toString();
		}
	}
	
	
	//
	// LoadSaveDirectoryXXX
	//
	public abstract static class LoadSaveDirectoryGeneric<T> implements LoadSave {
		protected final Storage                  storage;
		protected final String                   prefix;
		protected final Function<String, String> opName;
		protected final File                     dir;
		
		public LoadSaveDirectoryGeneric(Storage storage, String prefix, Function<String, String> opName) {
			this.storage = storage;
			this.prefix  = prefix;
			this.opName  = opName;
			this.dir     = storage.getFile(prefix);
		}
		public String getFilename(String name) {
			return opName.apply(name);
		}
		public File getFile(String name) {
			return new File(dir, getFilename(name));
		}

		public abstract T      load(String name);
		public abstract void   save(String name, T value);
		public abstract String read(String name);
		public abstract void   write(String name, String value);

		public void touch() {
			FileUtil.touch(getTouchFile());
		}
		public File getTouchFile() {
			return storage.getFile(prefix + ".touch");
		}
		//
		// delistUknonwFiles
		//
		public File getDir() {
			return dir;
		}
		public File getDirDelist() {
			return storage.getFile(prefix + "-delist");
		}
		public void delistUnknownFile(Collection<String> validNameCollection) {
			delistUnknownFile(validNameCollection, false);
		}
		public void delistUnknownFile(Collection<String> validNameCollection, boolean dryRun) {
			Set<String> validFilenameSet = validNameCollection.stream().map(o -> getFilename(o)).collect(Collectors.toSet());
			FileUtil.moveUnknownFile(validFilenameSet, getDir(), getDirDelist(), dryRun);
		}
	}
	public static class LoadSaveDirectoryString extends LoadSaveDirectoryGeneric<String> {
		public LoadSaveDirectoryString(Storage storage, String prefix, Function<String, String> opName) {
			super(storage, prefix, opName);
		}

		@Override
		public String load(String name) {
			return read(name);
		}

		@Override
		public void save(String name, String value) {
			write(name, value);
		}

		@Override
		public String read(String name) {
			return FileUtil.read().file(getFile(name));
		}

		@Override
		public void write(String name, String value) {
			FileUtil.write().file(getFile(name), value);
		}
	}
	public static class LoadSaveDirectoryList<E extends Comparable<E>> extends LoadSaveDirectoryGeneric<List<E>> {
		private final Class<E> clazz;
		
		public LoadSaveDirectoryList(Class<E> clazz, Storage storage, String prefix, Function<String, String> opName) {
			super(storage, prefix, opName);
			this.clazz = clazz;
		}

		@Override
		public List<E> load(String name) {
			return CSVUtil.read(clazz).file(getFile(name));
		}
		@Override
		public void save(String name, List<E> list) {
			Collections.sort(list);
			CSVUtil.write(clazz).file(getFile(name), list);
		}
		@Override
		public String read(String name) {
			return FileUtil.read().file(getFile(name));
		}
		@Override
		public void write(String name, String value) {
			FileUtil.write().file(getFile(name), value);
		}

		
		public List<E> getList(String name) {
			var list = load(name);
			return list == null ? new ArrayList<>() : list;
		}
		
		// reader
		public List<E> load(Reader reader) {
			return CSVUtil.read(clazz).file(reader);
		}
		public List<E> getList(Reader reader) {
			var list = load(reader);
			return list == null ? new ArrayList<>() : list;
		}
		
		public void save(String name, Collection<E> collection) {
			save(name, new ArrayList<E>(collection));
		}
	}
}
