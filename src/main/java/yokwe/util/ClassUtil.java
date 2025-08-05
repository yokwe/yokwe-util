package yokwe.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class ClassUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static class ClassInfo {
		public final Class<?>       clazz;
		public final String         name;
		public final Field[]        fields;
		public final Constructor<?> constructor;
		public final Class<?>[]     parameterTypes;
		public final Constructor<?> constructor0;
		
		private ClassInfo(Class<?> clazz_) {
			clazz  = clazz_;
			name   = clazz.getTypeName();
			
			{
				List<Field> list = new ArrayList<>();
				for(var field: clazz.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) continue;
					field.setAccessible(true);
					list.add(field);
				}
				fields = list.toArray(new Field[0]);
			}
			
			{
				Constructor<?> cntr       = null;
				Class<?>[]     paramTypes = null;
				Constructor<?> cntr0      = null;
				Constructor<?>[] list = clazz.getDeclaredConstructors();
				for(var e: list) {
					int parameterCount = e.getParameterCount();
					if (parameterCount == 0) {
						cntr0 = e;
						cntr0.setAccessible(true);
					}
					if (parameterCount == fields.length) {
						paramTypes = e.getParameterTypes();
						boolean hasSameType = true;
						for(int i = 0; i < fields.length; i++) {
							if (!paramTypes[i].equals(fields[i].getType())) {
								hasSameType = false;
							}
						}
						if (hasSameType) {
							cntr = e;
							cntr.setAccessible(true);
						}
					}
				}
				constructor    = cntr;
				parameterTypes = paramTypes;
				constructor0   = cntr0;
			}
		}
		private Object getInstance(Object... args) {
			if (args.length == 0) {
				if (constructor0 != null) {
					try {
						Object o = constructor0.newInstance();
						return o;
					} catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						String exceptionName = e.getClass().getSimpleName();
						logger.error("{} {}", exceptionName, e);
						throw new UnexpectedException(exceptionName, e);
					}
				} else {
					throw new UnexpectedException("Unexpected");
				}
			} else {
				// sanity check
				if (fields.length != args.length) {
					logger.info("clazz   {}", this.name);
					logger.info("fields  {}", fields.length);
					logger.info("args    {}", args.length);
					logger.info("fields  {}", Arrays.stream(fields).map(o -> {return o.getType().getName();}).collect(Collectors.toList()));
					logger.info("args    {}", Arrays.stream(args)  .map(o -> {return o.getClass().getName();}).collect(Collectors.toList()));
					throw new UnexpectedException("Unexpected");
				}
				for(int i = 0; i < fields.length; i++) {
					if (!isCompatible(fields[i].getType(), args[i].getClass())) {
						logger.info("clazz   {}", this.name);
						logger.info("fields  {}", Arrays.stream(fields).map(o -> {return o.getType().getName();}).collect(Collectors.toList()));
						logger.info("args    {}", Arrays.stream(args)  .map(o -> {return o.getClass().getName();}).collect(Collectors.toList()));
						
						logger.info("class   {}  -  {}  -  {}", i, fields[i].getType().getTypeName(), args[i].getClass().getTypeName());
						
						throw new UnexpectedException("Unexpected");
					}
				}
				
				if (constructor != null) {
					try {
						Object o = constructor.newInstance(args);
						return o;
					} catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						String exceptionName = e.getClass().getSimpleName();
						logger.error("{} {}", exceptionName, e);
						throw new UnexpectedException(exceptionName, e);
					}
				} else if (constructor0 != null) {
					try {
						Object o = constructor0.newInstance();
						for(int i = 0; i < fields.length; i++) {
							fields[i].set(o, args[i]);
						}
						return o;
					} catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						String exceptionName = e.getClass().getSimpleName();
						logger.error("{} {}", exceptionName, e);
						throw new UnexpectedException(exceptionName, e);
					}
				} else {
					throw new UnexpectedException("Unexpected");
				}
			}
		}
		private boolean isCompatible(Class<?> a, Class<?> b) {
			if (a.equals(b)) return true;
			// Byte
			if (a.equals(Byte.TYPE) && b.equals(Byte.class)) return true;
			if (a.equals(Byte.class) && b.equals(Byte.TYPE)) return true;
			// Short
			if (a.equals(Short.TYPE) && b.equals(Short.class)) return true;
			if (a.equals(Short.class) && b.equals(Short.TYPE)) return true;
			// Integer
			if (a.equals(Integer.TYPE) && b.equals(Integer.class)) return true;
			if (a.equals(Integer.class) && b.equals(Integer.TYPE)) return true;
			// Long
			if (a.equals(Long.TYPE) && b.equals(Long.class)) return true;
			if (a.equals(Long.class) && b.equals(Long.TYPE)) return true;
			// Float
			if (a.equals(Float.TYPE) && b.equals(Float.class)) return true;
			if (a.equals(Float.class) && b.equals(Float.TYPE)) return true;
			// Double
			if (a.equals(Double.TYPE) && b.equals(Double.class)) return true;
			if (a.equals(Double.class) && b.equals(Double.TYPE)) return true;
			// Char
			if (a.equals(Character.TYPE) && b.equals(Character.class)) return true;
			if (a.equals(Character.class) && b.equals(Character.TYPE)) return true;
			// Boolean
			if (a.equals(Boolean.TYPE) && b.equals(Boolean.class)) return true;
			if (a.equals(Boolean.class) && b.equals(Boolean.TYPE)) return true;
			
			return false;
		}
	}
	
	private static Map<String, ClassInfo> map = new TreeMap<>();
	//                 typename
	public static ClassInfo getClassInfo(Class<?> clazz) {
		var name = clazz.getTypeName();
		if (map.containsKey(name)) {
			return map.get(name);
		} else {
			var classInfo = new ClassInfo(clazz);
			map.put(name, classInfo);
			return classInfo;
		}
	}
	
	public static Object getInstance(Class<?> clazz, Object... args) {
		var classInfo = getClassInfo(clazz);
		return classInfo.getInstance(args);
	}
	
	
	//
	// enumerate class file using classLoader -- not for module
	//
    public static class FindClass {
        private final static char DOT = '.';
        private final static char SLASH = '/';
        private final static String CLASS_SUFFIX = ".class";

        public final static List<Class<?>> find(ClassLoader classLoader, final String packageName) {
            final String scannedPath = packageName.replace(DOT, SLASH);
            final Enumeration<URL> resources;
            try {
                resources = classLoader.getResources(scannedPath);
            } catch (IOException e) {
    			String exceptionName = e.getClass().getSimpleName();
    			logger.error("{} {}", exceptionName, e);
    			throw new UnexpectedException(exceptionName, e);
            }
            final List<Class<?>> classes = new LinkedList<Class<?>>();
            while (resources.hasMoreElements()) {
                final File file = new File(resources.nextElement().getFile());
                classes.addAll(findInFile(file, packageName));
            }
            return classes;
        }

        private final static List<Class<?>> findInFile(final File file, final String scannedPackage) {
            final List<Class<?>> classes = new LinkedList<Class<?>>();
            if (file.isDirectory()) {
                for (File nestedFile : file.listFiles()) {
                 	if (nestedFile.isDirectory()) {
                        classes.addAll(findInFile(nestedFile, scannedPackage + DOT + nestedFile.getName()));
                	} else {
                		classes.addAll(findInFile(nestedFile, scannedPackage));
                	}
                }
            } else if (file.getName().endsWith(CLASS_SUFFIX)) {
    	        final String resource = scannedPackage + DOT + file.getName();

    	        final int beginIndex = 0;
                final int endIndex = resource.length() - CLASS_SUFFIX.length();
                final String className = resource.substring(beginIndex, endIndex);
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                	//logger.warn("e = {}", e);
                }
            }
            return classes;
        }
    }
    
    public final static List<Class<?>> findClass(ClassLoader classLoader, final String packageName) {
    	return FindClass.find(classLoader, packageName);
    }
    public final static List<Class<?>> findClass(final String packageName) {
 //       var classLoader = Thread.currentThread().getContextClassLoader();
        var classLoader = ClassLoader.getPlatformClassLoader();
    	return FindClass.find(classLoader, packageName);
    }
    
    // Comparator for sorting class using type name
    public static void sort(List<Class<?>> list) {
    	Collections.sort(list, (a, b) -> a.getTypeName().compareTo(b.getTypeName()));
    }
    //
    // enumerate class file every package in named module
    //
    public final static List<Class<?>> findClassInModule(String moduleName) {
    	return findClassInModule(findModule(moduleName));
    }
    public final static List<Class<?>> findClassInModule(Module module) {
        var set = new HashSet<Class<?>>();
        for(var packageName: module.getPackages()) {
        	set.addAll(findClassInModule(module, packageName));
        }
        
        var list = new ArrayList<Class<?>>(set);
        sort(list);
        return list;
    }
    //
    // enumerate class file in named package in named module
    //
    public final static List<Class<?>> findClassInModule(String moduleName, String packageName) {
    	return findClassInModule(findModule(moduleName), packageName);
    }
    public final static List<Class<?>> findClassInModule(Module module, String packageName) {
		var url = module.getClassLoader().getResource(packageName.replace(".", "/"));
		
		List<Class<?>> list;
        if (url.toString().startsWith("file:/")) {
        	// enumerate file directory
        	list = enumerateFileDirectory(packageName, url);
        } else if (url.toString().startsWith("jar:file:///")) {
        	// enumerate jar file
        	list = enumerateJarFile(url);
        } else {
        	logger.error("Unexpected url");
        	logger.error("  url  {}", url.toString());
        	throw new UnexpectedException("Unexpected url");
        }
        
        sort(list);
        return list;
    }
    public static Module findModule(String moduleName) {
        var moduleOptional = ModuleLayer.boot().findModule(moduleName);
        if (moduleOptional.isEmpty()) {
        	logger.error("Unexpected module name");
        	logger.error("  moduleName  {}", moduleName);
        	throw new UnexpectedException("Unexpected module name");
        }
        return moduleOptional.get();
    }
    
    private static List<Class<?>> enumerateJarFile(URL url) {
    	// jar:file:///Users/hasegawa/git/yokwe-finance-data/target/yokwe-finance-data-2.0.0.jar!/yokwe/finance/data/
		var path = url.toString();
		if (path.startsWith("jar:file:///") && path.contains("!")) {
			//                                     012345678901
			path = path.substring(11);
			path = path.substring(0, path.indexOf("!"));
		} else {
        	logger.error("Unexpected path");
        	logger.error("  url   {}", url.toString());
        	logger.error("  path  {}", path);
        	throw new UnexpectedException("Unexpected path");
		}
//		logger.info("path         {}", path);
		
		try (var jarFile = new JarFile(path)) {
			URL[] urls = { toURL("jar:file:" + path +"!/") };
			var classLoader = URLClassLoader.newInstance(urls);
			var enumeration = jarFile.entries();
			
			// build ret
			var ret = new ArrayList<Class<?>>();
			for (var jarEntry : (Iterable<JarEntry>)enumeration::asIterator) {
				if (jarEntry.isDirectory()) continue;
				
				var name = jarEntry.getName();
		    	if (name.contains("$"))                continue;
		    	if (name.equals("module-info.class"))  continue;
		    	if (name.equals("package-info.class")) continue;
		    	if (!name.endsWith(".class"))          continue;
		    	
				var className = name.substring(0, name.length() - 6).replace("/", ".");
				var clazz = loadClass(classLoader, className);
				if (clazz == null) {
					logger.error("Unexpeced");
					logger.error("  className  {}", className);
					logger.error("  path       {}", path);
					throw new UnexpectedException("Unexpeced");
				}
				ret.add(clazz);
			}
			return ret;
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
    }
    private static List<Class<?>> enumerateFileDirectory(String packageName, URL url) {
    	// file:/Users/hasegawa/git/yokwe-finance-data/target/classes/yokwe/finance/data/util/
		var file = toFile(url);
    	if (!file.isDirectory()) {
    		logger.error("Unepxpected");
    		logger.error(" file  {}", file.getAbsolutePath());
        	throw new UnexpectedException("Unepxpected");
    	}
    	
		var classLoader = getClassLoader(file);
		return enumerateFileDirectory(classLoader, packageName, file);
    }
    private static List<Class<?>> enumerateFileDirectory(ClassLoader classLoader, String packageName, File file) {
    	// build ret
		var ret = new ArrayList<Class<?>>();
		for(var child: file.listFiles()) {
	    	var name = child.getName();
	    	// skip special file
	    	if (name.startsWith(".")) continue;

			if (child.isDirectory()) {
				var childPackageName = packageName + "." + name;
				ret.addAll(enumerateFileDirectory(classLoader, childPackageName, child));
			} else {
		    	if (name.contains("$"))                continue;
		    	if (name.equals("module-info.class"))  continue;
		    	if (name.equals("package-info.class")) continue;
		    	if (!name.endsWith(".class"))          continue;
		    	
				var className = packageName + "." + name.substring(0, name.length() - 6);
				var clazz = loadClass(classLoader, className);
				if (clazz == null) {
					logger.error("Unexpeced");
					logger.error("  className  {}", className);
					logger.error("  child      {}", child.getAbsolutePath());
					throw new UnexpectedException("Unexpeced");
				}
				ret.add(clazz);
			}
		}
		return ret;
    }
    
    private static URL toURL(String string) {
    	try {
			return new URI(string).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
    }
    private static File toFile(URL url) {
    	try {
			return Paths.get(url.toURI()).toFile();
		} catch (URISyntaxException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
    }
    private static URLClassLoader getClassLoader(File file) {
    	try {
    		if (file.isFile()) {
    			logger.error("Unexpeced file is not directory");
    			logger.error("  file  {}", file.getAbsolutePath());
    			throw new UnexpectedException("Unexpeced file is not directory");
    		}
    		var url = file.toURI().toURL();
//    		logger.info("url  {}", url.toString());
    		URL[] urls = { url };
    		var classLoader = URLClassLoader.newInstance(urls);
    		return classLoader;
    	} catch (MalformedURLException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
    	}
    }
    private static Class<?> loadClass(ClassLoader classLoader, String className) {
    	try {
			return classLoader.loadClass(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
    }

}
