package yokwe.util.json;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import yokwe.util.ToString;
import yokwe.util.UnexpectedException;
import yokwe.util.json.JSON.DateTimeFormat;
import yokwe.util.json.JSON.Ignore;
import yokwe.util.json.JSON.Name;
import yokwe.util.json.JSON.Optional;
import yokwe.util.json.FieldInfo;

class FieldInfo {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	private static Map<String, FieldInfo[]> map = new TreeMap<>();
	
	static FieldInfo[] getFieldInfoArray(Class<?> clazz) {
		var key = clazz.getTypeName();
		var ret = map.get(key);
		if (ret != null) return ret;
		
		var list = new ArrayList<FieldInfo>();
		for(var field: clazz.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			field.setAccessible(true); // allow access private field
			list.add(new FieldInfo(field));
		}
		var array = list.toArray(FieldInfo[]::new);
		map.put(key, array);
		return array;
	}
	
	Field             field;
	String            fieldName;
	Class<?>          type; // type of field
	String            typeName;
	String            jsonName;
	boolean           ignore;
	boolean           optional;
	DateTimeFormatter dateTimeFormatter;
	
	FieldInfo(Field field) {
		var jsonName       = field.getDeclaredAnnotation(Name.class);
		var ignore         = field.getDeclaredAnnotation(Ignore.class);
		var optional       = field.getDeclaredAnnotation(Optional.class);
		var dateTimeFormat = field.getDeclaredAnnotation(DateTimeFormat.class);

		this.field             = field;
		this.fieldName         = field.getName();
		this.type              = field.getType();
		this.typeName          = this.type.getTypeName();
		this.jsonName          = (jsonName == null) ? fieldName : jsonName.value();
		this.ignore            = ignore != null;
		this.optional          = optional != null;
		this.dateTimeFormatter = (dateTimeFormat == null) ? null : DateTimeFormatter.ofPattern(dateTimeFormat.value());
	}
	
	public Object get(Object object) {
		try {
			return field.get(object);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e.toString());
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	@Override
	public String toString() {
		return ToString.withFieldName(this);
	}
}