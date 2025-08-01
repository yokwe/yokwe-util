package yokwe.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class CSVUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface ColumnName {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface DecimalPlaces {
		int value();
	}

	public static final int BUFFER_SIZE = 64 * 1024;
	public static final LocalDate     NULL_LOCAL_DATE      = LocalDate.of(1900, 1, 1);
	public static final LocalTime     NULL_LOCAL_TIME      = LocalTime.of(0, 0, 0);
	public static final LocalDateTime NULL_LOCAL_DATE_TIME = LocalDateTime.of(NULL_LOCAL_DATE, NULL_LOCAL_TIME);

	private static class ClassInfo {
		private static Map<String, ClassInfo> map = new TreeMap<>();
		
		static ClassInfo get(Class<?> clazz) {
			String key = clazz.getName();
			if (map.containsKey(key)) {
				return map.get(key);
			} else {
				ClassInfo value = new ClassInfo(clazz);
				map.put(key, value);
				return value;
			}
		}

		final Class<?>       clazz;
		final FieldInfo[]    fieldInfos;
		final String[]       names;
		
		ClassInfo(Class<?> value) {
			clazz = value;
			
			List<FieldInfo> list = new ArrayList<>();
			for(Field field: clazz.getDeclaredFields()) {
				// Skip static field
				if (Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true); // to access protected and private file, call setAccessble(true) of the field
				list.add(new FieldInfo(field));
			}
			fieldInfos = list.toArray(new FieldInfo[0]);
			
			names = new String[fieldInfos.length];
			for(int i = 0; i < names.length; i++) {
				names[i] = fieldInfos[i].name;
			}
		}
	}
	private static class FieldInfo {
		final Field    field;
		final String   name;
		final Class<?> type;
		final String   typeName;
		final String   format;
		
		final Map<String, Enum<?>> enumMap;
		final Method               getInstance;

		
		FieldInfo(Field value) {
			field      = value;
			
			ColumnName columnName = field.getDeclaredAnnotation(ColumnName.class);
			name = (columnName == null) ? field.getName() : columnName.value();

			type      = field.getType();
			typeName  = type.getName();
			
			DecimalPlaces decimalPlaces = field.getDeclaredAnnotation(DecimalPlaces.class);
			if (decimalPlaces == null) {
				format = null;
			} else {
				switch(typeName) {
				case "double":
				case "real":
					int digits = decimalPlaces.value();
					if (digits <= 0) {
						logger.error("Unexpected digits value");
						logger.error("  digits {}", digits);
						logger.error("  field  {}", field.toString());
						throw new UnexpectedException("Unexpected digits value");
					}
					format = String.format("%%.%df", digits);
					break;
				default:
					logger.error("Unexpected field type for DecimalPlaces annotation");
					logger.error("  field  !{}!", field.toString());
					throw new UnexpectedException("Unexpected field type for DecimalPlaces annotation");
				}
			}
			
			if (type.isEnum()) {
				enumMap = new TreeMap<>();
				
				@SuppressWarnings("unchecked")
				Class<Enum<?>> enumClazz = (Class<Enum<?>>)type;
				for(Enum<?> e: enumClazz.getEnumConstants()) {
					enumMap.put(e.toString(), e);
				}
			} else {
				enumMap = null;
			}
			
			{
				Method method = null;
				try {
					method = type.getDeclaredMethod("getInstance", String.class);
					// Sanity check
					int modifiers = method.getModifiers();
					if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
						// accept this method
					} else {
						// reject this method
						method = null;
					}
				} catch (NoSuchMethodException e) {
					//
				} catch (SecurityException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.error("{} {}", exceptionName, e);
					throw new UnexpectedException(exceptionName, e);
				}
				this.getInstance = method;
			}

		}
	}
	
	public static String[] parseLine(BufferedReader br, char separator) {
		try {			
			// Peek one char to check end of stream
			{
				// record stream position
				br.mark(1);
				int firstChar = br.read();
				if (firstChar == -1) return null;
				if (StringUtil.isBOM((char)firstChar)) {
					// firstChar is BOM. skip firstChar
				} else {
					// firstChar is not BOM. reset stream position.
					br.reset();
				}
			}
			
			List<String> list  = new ArrayList<>();
			StringBuffer field = new StringBuffer();
			boolean endOfRecord = false;
			for(;;) {
				if (endOfRecord) break;
				
				int fieldFirstChar = br.read();
				if (fieldFirstChar == -1) {
					// end of record -- last field of last line without \r\n
					field.setLength(0);
					list.add("");
					break;
				} else if (fieldFirstChar == '\r') {
					// end of record -- last field of record has no contents
					int c2 = br.read();
					if (c2 == -1) {
						logger.error("Unexpected end of stream");
						logger.error("  list   !{}!", list);
						logger.error("  field  !{}!", field.toString());
						throw new UnexpectedException("Unexpected end of stream");
					} else if (c2 == '\n') {
						field.setLength(0);
						list.add("");
						break;
					} else {
						logger.error("Unexpected char {}", String.format("%X", c2));
						throw new UnexpectedException("Unexpected char");
					}
				} else if (fieldFirstChar == '\n') {
					// end of record -- last field of record has no contents
					field.setLength(0);
					list.add("");
					break;
				} else if (fieldFirstChar == separator) {
					// end of field -- empty field
					field.setLength(0);
					list.add("");
//					logger.debug("emp field  \"\"");
				} else if (fieldFirstChar == '"') {
					// quoted field
					field.setLength(0);
					
					for(;;) {
						int c = br.read();
						if (c == -1) {
							logger.error("Unexpected end of stream");
							logger.error("  list   !{}!", list);
							logger.error("  field  !{}!", field.toString());
							throw new UnexpectedException("Unexpected end of stream");
						} else if (c == '"') {
							// end of field, end of record or double quote
							int c2 = br.read();
							if (c2 == -1) {
								// Special handling of last record with no \n
								endOfRecord = true;
								break;
							} else if (c2 == separator) {
								// end of field
								break;
							} else if (c2 == '\r') {
								// end of record
								int c3 = br.read();
								if (c3 == -1) {
									logger.error("Unexpected end of stream");
									logger.error("  list   !{}!", list);
									logger.error("  field  !{}!", field.toString());
									throw new UnexpectedException("Unexpected end of stream");
								} else if (c3 == '\n') {
									endOfRecord = true;
									break;
								} else {
									logger.error("Unexpected char {}", String.format("%X", c3));
									throw new UnexpectedException("Unexpected char");
								}
							} else if (c2 == '\n') {
								// end of record
								endOfRecord = true;
								break;
							} else if (c2 == '"') {
								// double quote
								field.append('"');
							} else {
								logger.error("Unexpected back slash escape  {}", c2);
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected back slash escape");
							}
						} else if (c == '\\') {
							// back slash escape
							int c2 = br.read();
							if (c2 == -1) {
								logger.error("Unexpected end of stream");
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected end of stream");
							} else if (c2 == 'n') {
								// \n
								field.append('\n');
							} else if (c2 == 'r') {
								// \r
								field.append('\r');
							} else {
								logger.error("Unexpected back slash escape  {}", c2);
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected back slash escape");
							}
						} else {
							field.append((char)c);
						}
					}
					// append field to list
					list.add(field.toString());
//					logger.debug("quo field  {}!", field.toString());
				} else {
					// ordinary field
					field.setLength(0);
					
					field.append((char)fieldFirstChar);
					for(;;) {
						int c = br.read();
						if (c == -1) {
							// Special handling of last record with no \n
							endOfRecord = true;
							break;
						} else if (c == separator) {
							// end of field
							break;
						} else if (c == '\r') {
							// end of record
							int c2 = br.read();
							if (c2 == -1) {
								logger.error("Unexpected end of stream");
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected end of stream");
							} else if (c2 == '\n') {
								endOfRecord = true;
								break;
							} else {
								logger.error("Unexpected char {}", String.format("%X", c2));
								throw new UnexpectedException("Unexpected char");
							}
						} else if (c == '\n') {
							// end of record
							endOfRecord = true;
							break;
						} else {
							field.append((char)c);
						}
					}
					// append field to list
					list.add(field.toString());
//					logger.debug("ord field  {}!", field.toString());
				}
			}
			
			// Remove byte order mark from list element
			for(String string: list) {
				string = StringUtil.removeBOM(string);
			}
//			logger.debug("list  {}", list);
			return list.toArray(new String[0]);
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static String[] parseLine(String string, char separator) {
		StringReader   sr = new StringReader(string);
		BufferedReader br = new BufferedReader(sr);
		return parseLine(br, separator);
	}
	
	
	private static class Context {
		private boolean withHeader = true;
		private char    separator  = ',';
		private Charset charset    = Charset.defaultCharset();
	}
	
	public static <E> Read<E> read(Class<E> clazz) {
		return new Read<E>(clazz);
	}
	public static class Read<E> {
		private final Context   context;
		private final ClassInfo classInfo;
				
		private Read(Class<E> clazz) {
			context   = new Context();
			classInfo = ClassInfo.get(clazz);
		}
		public Read<E> withHeader(boolean newValue) {
			context.withHeader = newValue;
			return this;
		}
		public Read<E> withSeparator(char newValue) {
			context.separator = newValue;
			return this;
		}
		public Read<E> withCharset(Charset newValue) {
			context.charset = newValue;
			return this;
		}

		private void readHeader(BufferedReader br, char separator) {
			String[] names = parseLine(br, separator);
			if (names == null) {
				logger.error("Unexpected EOF");
				throw new UnexpectedException("Unexpected EOF");
			}
			
			// Sanity check
			if (classInfo.names.length != names.length) {
				logger.error("Unexpected length  {}  {}  {}", classInfo.names.length, names.length, Arrays.asList(names));
				logger.error("classInfo  {}", classInfo.clazz.getName());
				logger.error("====");
				for(int j = 0; j < classInfo.fieldInfos.length; j++) {
					logger.info("  clasInfo   {}  {}", j, classInfo.names[j]);
				}
				logger.error("====");
				for(int j = 0; j < names.length; j++) {
					logger.info("  names      {}  {}", j, names[j]);
				}
				throw new UnexpectedException("Unexpected length");
			}
			for(int i = 0; i < names.length; i++) {
				if (names[i].equals(classInfo.names[i])) continue;
				logger.error("Unexpected name  {}  {}  {}", i, names[i], classInfo.names[i]);
				logger.error("classInfo  {}", classInfo.clazz.getName());
				logger.error("====");
				for(int j = 0; j < classInfo.fieldInfos.length; j++) {
					logger.info("  clasInfo   {}  {}  {}", j, classInfo.names[j], StringUtil.toHexString(classInfo.names[j]));
				}
				logger.error("====");
				for(int j = 0; j < names.length; j++) {
					logger.info("  names      {}  {}  {}", j, names[j], StringUtil.toHexString(names[j]));
				}
				throw new UnexpectedException("Unexpected name");
			}
		}

		private interface GetArg<E> {
			public E get(String string);
		}
		
		private static Map<String, GetArg<?>> getArgMap = new TreeMap<>();
		static {
			getArgMap.put(java.math.BigDecimal.class.getTypeName(),    (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);});
			getArgMap.put(java.lang.Integer.class.getTypeName(),       (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Integer.parseInt(s);});
			getArgMap.put(java.lang.Integer.TYPE.getTypeName(),        (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Integer.parseInt(s);});
			getArgMap.put(java.lang.Long.class.getTypeName(),          (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Long.parseLong(s);});
			getArgMap.put(java.lang.Long.TYPE.getTypeName(),           (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Long.parseLong(s);});
			getArgMap.put(java.lang.Double.class.getTypeName(),        (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Double.parseDouble(s);});
			getArgMap.put(java.lang.Double.TYPE.getTypeName(),         (String s) -> {s = s.replace(",", ""); return s.isEmpty() ? 0 : Double.parseDouble(s);});
			// special for boolean  1 for true other for false
			getArgMap.put(java.lang.Boolean.class.getTypeName(),       (String s) -> {return Boolean.valueOf(s.equals("1"));});
			getArgMap.put(java.lang.Boolean.TYPE.getTypeName(),        (String s) -> {return s.equals("1");});
			getArgMap.put(java.lang.String.class.getTypeName(),        (String s) -> {return s;});
			getArgMap.put(java.time.LocalDateTime.class.getTypeName(), (String s) -> {
				if (s.isEmpty() || s.equals("0")) return NULL_LOCAL_DATE_TIME;
				if (s.matches("\\d++")) return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneOffset.UTC);
				return LocalDateTime.parse(s);
			});
			getArgMap.put(java.time.LocalDate.class.getTypeName(), (String s) -> {
				if (s.isEmpty() || s.equals("0")) return NULL_LOCAL_DATE;
				if (s.matches("\\d++")) return LocalDate.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneOffset.UTC);
				return LocalDate.parse(s);
			});
			getArgMap.put(java.time.LocalTime.class.getTypeName(), (String s) -> {
				if (s.isEmpty() || s.equals("0")) return NULL_LOCAL_TIME;
				return LocalTime.parse(s);
			});
		}
		
		private static Object getArg(FieldInfo fieldInfo, String value) {
			if (fieldInfo.enumMap != null) {
				if (fieldInfo.enumMap.containsKey(value)) {
					return fieldInfo.enumMap.get(value);
				}
				logger.error("Unknow enum value  {}  {}", fieldInfo.typeName, value);
				throw new UnexpectedException("Unknow enum value");
			}
			if (fieldInfo.getInstance != null) {
				try {
					return fieldInfo.getInstance.invoke(null, value);
				} catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.error("{} {}", exceptionName, e);
					throw new UnexpectedException(exceptionName, e);
				}
			}
			if (getArgMap.containsKey(fieldInfo.typeName)) {
				return getArgMap.get(fieldInfo.typeName).get(value);
			}
			logger.error("Unknow field type  {}  {}", fieldInfo.typeName, value);
			throw new UnexpectedException("Unknow enum value");
		}
		
		private E read(BufferedReader br) {
			String[] values = parseLine(br, context.separator);
			if (values == null) return null;
			
			FieldInfo[] fieldInfos = classInfo.fieldInfos;
			// sanity check
			if (fieldInfos.length != values.length) {
				logger.error("fieldInfos {}", fieldInfos.length);
				logger.error("values     {}", values.length);
				throw new UnexpectedException("fieldInfos.length != values.length");
			}
			
			try {
				// build args
				Object[] args = new Object[classInfo.fieldInfos.length];
				for(int i = 0; i < args.length; i++) {
					FieldInfo fieldInfo = classInfo.fieldInfos[i];
					String    value     = values[i];
					args[i] = getArg(fieldInfo, value);
				}
				
				@SuppressWarnings("unchecked")
				E data = (E) ClassUtil.getInstance(classInfo.clazz, args);
				return data;
			} catch (IllegalArgumentException | SecurityException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				logger.error("  values {}", Arrays.stream(values).toList());
				throw new UnexpectedException(exceptionName, e);
			}
		}

		public List<E> file(Reader reader) {
			try (BufferedReader br = new BufferedReader(reader, BUFFER_SIZE)) {
				// Peek one char to check end of stream and byte order mark
				{
					br.mark(1);
					int firstChar = br.read();
					switch(firstChar) {
					case -1:
						// end of stream.
						return null;
					case 0xFEFF:
						// byte order mark
						break;
					default:
						// resets the stream to the most recent mark.
						br.reset();
						break;
					}
				}

				if (context.withHeader) {
					readHeader(br, context.separator);
				}
				
				List<E> ret = new ArrayList<>();
				for(;;) {
					E e = (E)read(br);
					if (e == null) break;
					ret.add(e);
				}
				return ret;
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public List<E> file(File file) {
			if (!file.exists()) return null;
			if (file.length() == 0) return null;
			try {
				return file(new FileReader(file, context.charset));
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			} catch (UnexpectedException e) {
				logger.error("file {}", file.getPath());
				throw e;
			}
		}
		public List<E> file(String path) {
			return file(new File(path));
		}
		public List<E> file(InputStream is) {
			return file(new InputStreamReader(is, context.charset));
		}
		// read csv file in resource using class loader of clazz
		public List<E> file(Class<?> clazz, String resourceName) {
			// NOTE: Use gerResource() instead of getResourceAsStream()
			URL url = clazz.getResource(resourceName);
			if (url == null) {
				logger.error("no resource");
				logger.error("  url == null");
				logger.error("  resourceName {}", resourceName);
				throw new UnexpectedException("no resource");
			}

			InputStream is = null;
			try {
				is = url.openStream();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
			if (is == null) {
				logger.error("no resource");
				logger.error("  is == null");
				logger.error("  resourceName {}", resourceName);
				logger.error("  url          {}", url);
				throw new UnexpectedException("no resource");
			}
			return file(is);
		}
	}
	
	public static <E> Write<E> write(Class<E> clazz) {
		return new Write<E>(clazz);
	}
	public static class Write<E> {
		private final Context   context;
		private final ClassInfo classInfo;
				
		private Write(Class<E> clazz) {
			context   = new Context();
			classInfo = ClassInfo.get(clazz);
		}
		public Write<E> withHeader(boolean newValue) {
			context.withHeader = newValue;
			return this;
		}
		
		private void writeHeader(BufferedWriter bw) {
			try {
				FieldInfo[] fieldInfos = classInfo.fieldInfos;
				
				bw.write(fieldInfos[0].name);
				for(int i = 1; i < fieldInfos.length; i++) {
					bw.write(",");
					bw.write(fieldInfos[i].name);
				}
				bw.newLine();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		private void writeField(BufferedWriter bw, String value) throws IOException {
			if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") || value.contains(";")) {
				bw.write("\"");
				
				char[] charArray = value.toCharArray();
				for(int i = 0; i < charArray.length; i++) {
					char c = charArray[i];
					switch(c) {
					case '"':
						bw.write('"');
						bw.write('"');
						break;
					case '\n':
						bw.write('\\');
						bw.write('n');
						break;
					case '\r':
						bw.write('\\');
						bw.write('r');
						break;
					default:
						bw.write(c);
						break;
					}
				}
				
				bw.write("\"");						
			} else {
				bw.write(value);
			}
		}
		private void write(BufferedWriter bw, E value) {
			FieldInfo[] fieldInfos = classInfo.fieldInfos;
			
			try {
				for(int i = 0; i < fieldInfos.length; i++) {
					if (1 <= i) bw.write(",");
					
					FieldInfo fieldInfo = fieldInfos[i];
					
					// sanity check
					{
						Object fieldValue = fieldInfo.field.get(value);
						if (fieldValue == null) {
							logger.error("field has null value");
							logger.error("  clazz  {}", fieldInfo.typeName);
							logger.error("  field  {}", fieldInfo.name);
							throw new UnexpectedException("field has null value");
						}
					}

					switch (fieldInfo.typeName) {
					case "real":
					case "double":
					{
						if (fieldInfo.format != null) {
							double doubleValue = fieldInfo.field.getDouble(value);
							writeField(bw, String.format(fieldInfo.format, doubleValue));
						} else {
							writeField(bw, fieldInfo.field.get(value).toString());
						}
					}
						break;
					case "java.math.BigDecimal":
					{
						// To avoid scientific expression of value, need to use toPlainString().
						BigDecimal bigDecimalValue = (BigDecimal)fieldInfo.field.get(value);
						writeField(bw, bigDecimalValue.toPlainString());
					}
						break;
					case "java.lang.Boolean":
					case "boolean":
					{
						// special for boolean  1 for true 0 for false
						boolean fieldValue = (boolean)fieldInfo.field.get(value);
						writeField(bw, fieldValue ? "1" : "0");
					}
						break;
					default:
					{
						Object fieldValue = fieldInfo.field.get(value);
						if (fieldValue == null) {
							logger.error("field has null value");
							logger.error("  clazz  {}", fieldInfo.typeName);
							logger.error("  field  {}", fieldInfo.name);
							throw new UnexpectedException("field has null value");
						}
						writeField(bw, fieldValue.toString());
					}
						break;
					}
				}
				bw.newLine();
			} catch (IllegalArgumentException | IllegalAccessException | IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}

		public void file(Writer writer, Collection<E> collection) {
			try (BufferedWriter bw = new BufferedWriter(writer, BUFFER_SIZE)) {
				if (context.withHeader) {
					writeHeader(bw);
				}
				for(E e: collection) {
					write(bw, e);
				}
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(File file, Collection<E> collection) {
			// Create parent folder if not exists
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			
			try {
				file(new FileWriter(file), collection);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(String path, Collection<E> collection) {
			file(new File(path), collection);
		}
	}

}
