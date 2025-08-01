package yokwe.util.json;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import yokwe.util.GenericInfo;
import yokwe.util.UnexpectedException;

public class Unmarshal {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	//
	// getInstance
	//
	public static <E> E getInstance(Class<E> clazz, String jsonString) {
		return getInstance(clazz, new StringReader(jsonString));
	}
	public static <E> E getInstance(Class<E> clazz, Reader reader) {
		return unmarshal(clazz, getJsonValue(reader));
	}
	
	
	//
	// getList
	//
	public static <E> List<E> getList(Class<E> clazz, String jsonString) {
		return getList(clazz, new StringReader(jsonString));
	}
	public static <E> List<E> getList(Class<E> clazz, Reader reader) {
		var jsonValue = getJsonValue(reader);
		var valueType = jsonValue.getValueType();
		if (valueType == ValueType.ARRAY) {
			return getList(clazz, jsonValue.asJsonArray());
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect ARRAY");
			logger.error("  valueType {}", valueType);
			logger.error("  jsonValue {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	private static <E> List<E> getList(Class<E> clazz, JsonArray jsonArray) {
		var jsonArraySize = jsonArray.size();
		
		List<E> ret = new ArrayList<>(jsonArraySize);
		for(var i = 0; i < jsonArraySize; i++) {
			ret.add(unmarshal(clazz, jsonArray.get(i)));
		}
		return ret;
	}
	
	
	//
	// unmarshal
	//
	private static <E> E unmarshal(Class<E> clazz, JsonValue jsonValue) {
		var typeName = clazz.getTypeName();
		var valueType = jsonValue.getValueType();

		// process null value
		if (valueType == ValueType.NULL) {
			if (clazz.isPrimitive()) {
				logger.error("Unexpected clazz is primitive but jsonValue is NULL");
				logger.error("  clazz      {}", typeName);
				logger.error("  valueType  {}", valueType);
				logger.error("  jsonValue  {}", jsonValue);
				throw new UnexpectedException("Unexpected clazz is primitive but jsonValue is NULL");
			}
			return null;
		}
		
		// enum
		if (clazz.isEnum()) {
			if (valueType == ValueType.STRING) {
				return unmarshalEnum(clazz, jsonValue);
			} else {
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  clazz      {}", typeName);
				logger.error("  valueType  {}", valueType);
				logger.error("  jsonValue  {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		
		// array
		if (clazz.isArray()) {
			if (valueType == ValueType.ARRAY) {
				var ret = unmarshalArray(clazz, jsonValue.asJsonArray());
				return (E)ret;
			} else {
				logger.error("Unexpected valueType");
				logger.error("  expect ARRAY");
				logger.error("  clazz      {}", typeName);
				logger.error("  valueType  {}", valueType);
				logger.error("  jsonValue  {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		
		// common java class
		{
			var function = functionMap.get(clazz.getTypeName());
			if (function != null) {
				var o = function.apply(jsonValue);
				@SuppressWarnings("unchecked")
				E ret = (E)o;
				return ret;
			}
		}
		
		// process object
		if (valueType == ValueType.OBJECT) {
			return unmarshalObject(clazz, jsonValue.asJsonObject());
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect OBJECT");
			logger.error("  clazz      {}", typeName);
			logger.error("  valueType  {}", valueType);
			logger.error("  jsonValue  {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	

	
	//
	// unmarshalObject
	//
	private static <E> E unmarshalObject(Class<E> clazz, JsonObject jsonObject) {
		// sanity check
		if (clazz.isArray()) {
			logger.error("Unexpected clazz is array");
			logger.error("  clazz      {}", clazz.getTypeName());
			throw new UnexpectedException("Unexpected clazz is array");
		}
		if (clazz.isEnum()) {
			logger.error("Unexpected clazz is enum");
			logger.error("  clazz      {}", clazz.getTypeName());
			throw new UnexpectedException("Unexpected clazz is enum");
		}
		
		// order of jsonObject field is not significant
		// invoke default constructor of E and set field value from jsonObject
		try {
			var fieldInfoArray   = FieldInfo.getFieldInfoArray(clazz);
			
			// sanity check
			{
				var foundError = false;
				
				var jsonObjectKeySet = jsonObject.keySet();				
				for(var fieldInfo: fieldInfoArray) {
					if (fieldInfo.ignore) continue;
					
					if (!jsonObjectKeySet.contains(fieldInfo.jsonName)) {
						if (fieldInfo.optional) {
							// OK
						} else {
							foundError = true;
							logger.error("field not found in jsonObject");
							logger.error("  fieldName  {}", fieldInfo.fieldName);
							logger.error("  jsonName   {}", fieldInfo.jsonName);
							logger.error("  type       {}", fieldInfo.typeName);
							logger.error("  jsonObject {}", jsonObject.toString());
						}
					}
				}
				var fieldNameSet = Arrays.stream(fieldInfoArray).map(o -> o.jsonName).collect(Collectors.toSet());
				for(var jsonName: jsonObjectKeySet) {
					if (!fieldNameSet.contains(jsonName)) {
						foundError = true;
						logger.error("jsonObject jsonName not found in field");
						logger.error("  clazz      {}", clazz.getTypeName());
						logger.error("  jsonName   {}", jsonName);
						logger.error("  jsonObject {}", jsonObject.toString());
					}
				}
				if (foundError) {
					throw new UnexpectedException("found error");
				}
			}
			
			// invoke default constructor
			E ret;
			{
				var constructor = clazz.getDeclaredConstructor();
				if (constructor == null) {
					logger.error("no default constructor");
					logger.error("  {}", clazz.getTypeName());
					throw new UnexpectedException("no default constructor");
				}
				constructor.setAccessible(true); // enable invoke private constructor
				ret = constructor.newInstance();
			}
			
			// set object field using fieldInfoArray
			for(var fieldInfo: fieldInfoArray) {
				if (fieldInfo.ignore) continue;
				
				var key = fieldInfo.jsonName;
				if (jsonObject.containsKey(key)) {
					var jsonValue = jsonObject.get(key);
					Object fieldValue;
					
					// special for LocalDateTime, LocalDate, LocalTime and Map
					if (fieldInfo.type.equals(LocalDateTime.class)) {
						fieldValue = unmarshalLocalDateTime(jsonValue, fieldInfo.dateTimeFormatter);
					} else if (fieldInfo.type.equals(LocalDate.class)) {
						fieldValue = unmarshalLocalDate(jsonValue, fieldInfo.dateTimeFormatter);
					} else if (fieldInfo.type.equals(LocalTime.class)) {
						fieldValue = unmarshalLocalTime(jsonValue, fieldInfo.dateTimeFormatter);
					} else if (fieldInfo.type.equals(java.util.Map.class)) {
						fieldValue = unmarshalMap(fieldInfo.field, jsonValue);
					} else {
						fieldValue = unmarshal(fieldInfo.type, jsonValue);
					}
					
					fieldInfo.field.set(ret, fieldValue);
				}
			}
			
			return ret;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
				InvocationTargetException | NoSuchMethodException | SecurityException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e.toString());
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	private static LocalDateTime unmarshalLocalDateTime(JsonValue jsonValue, DateTimeFormatter dateTimeFormatter) {
		var valueType = jsonValue.getValueType();
		if (valueType == ValueType.STRING) {
			var string = jsonValueToString(jsonValue);
			if (dateTimeFormatter == null) {
				return LocalDateTime.parse(string);
			} else {
				return LocalDateTime.parse(string, dateTimeFormatter);
			}
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect STRING");
			logger.error("  valueType {}", valueType);
			logger.error("  jsonValue {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	private static LocalDate unmarshalLocalDate(JsonValue jsonValue, DateTimeFormatter dateTimeFormatter) {
		var valueType = jsonValue.getValueType();
		if (valueType == ValueType.STRING) {
			var string = jsonValueToString(jsonValue);
			if (dateTimeFormatter == null) {
				return LocalDate.parse(string);
			} else {
				return LocalDate.parse(string, dateTimeFormatter);
			}
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect STRING");
			logger.error("  valueType {}", valueType);
			logger.error("  jsonValue {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	private static LocalTime unmarshalLocalTime(JsonValue jsonValue, DateTimeFormatter dateTimeFormatter) {
		var valueType = jsonValue.getValueType();
		if (valueType == ValueType.STRING) {
			var string = jsonValueToString(jsonValue);
			if (dateTimeFormatter == null) {
				return LocalTime.parse(string);
			} else {
				return LocalTime.parse(string, dateTimeFormatter);
			}
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect STRING");
			logger.error("  valueType {}", valueType);
			logger.error("  jsonValue {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	public static Map<String, ?> unmarshalMap(Field field, JsonValue jsonValue) {
		GenericInfo genericInfo = new GenericInfo(field);
		// sanity check
		if (genericInfo.classArguments.length != 2) {
			logger.error("Unexptected genericInfo.classArguments");
			logger.error("  length {}", genericInfo.classArguments.length);
			throw new UnexpectedException("Unexptected genericInfo.classArguments");
		}
		Class<?> mapKeyClass   = genericInfo.classArguments[0];
		Class<?> mapValueClass = genericInfo.classArguments[1];
		// sanity check
		if (!mapKeyClass.equals(String.class)) {
			logger.error("Unexpected map key class");
			logger.error("unexpeced fieldName");
			logger.error("  field  {}", field);
			logger.error("  key    {}", mapKeyClass.getTypeName());
			throw new UnexpectedException("Unexpected map key class");
		}
		
		return unmarshalMap(mapValueClass, jsonValue);
	}
	public static <V> Map<String, V> unmarshalMap(Class<V> mapValueClass, JsonValue jsonValue) {
		if (jsonValue.getValueType() == ValueType.NULL) return null;
		
		var ret = new TreeMap<String, V>();
		
		// build ret
		{
			var jsonObject = jsonValue.asJsonObject();
			for(var entry: jsonObject.entrySet()) {
				var key   = entry.getKey();
				var value = entry.getValue();
				
				ret.put(key, unmarshal(mapValueClass, value));
			}
		}

		return ret;
	}
	
	
	//
	// unmarshalArray
	//
	private static <E> E unmarshalArray(Class<E> clazz, JsonArray jsonArray) {
		// sanity check
		if (!clazz.isArray()) {
			logger.error("Unexpected clazz is not array");
			logger.error("  clazz      {}", clazz.getTypeName());
			throw new UnexpectedException("Unexpected clazz is not array");
		}
		
		var componentType = clazz.getComponentType();
		var size          = jsonArray.size();
		@SuppressWarnings("unchecked")
		E ret = (E)Array.newInstance(componentType, size);
		
		for(int i = 0; i < size; i++) {
			Array.set(ret, i, unmarshal(componentType, jsonArray.get(i)));
		}
		return ret;
	}
	
	
		
	//
	// Functions convert JsonValue to Object
	//
	private static Map<String, Function<JsonValue, Object>> functionMap = new TreeMap<>();
	static {
		functionMap.put(Boolean.class.getTypeName(),   new Functions.BooleanClass());
		functionMap.put(Double.class.getTypeName(),    new Functions.DoubleClass());
		functionMap.put(Float.class.getTypeName(),     new Functions.FloatClass());
		functionMap.put(Long.class.getTypeName(),      new Functions.LongClass());
		functionMap.put(Integer.class.getTypeName(),   new Functions.IntegerClass());
		functionMap.put(Short.class.getTypeName(),     new Functions.ShortClass());
		functionMap.put(Byte.class.getTypeName(),      new Functions.ByteClass());
		functionMap.put(Character.class.getTypeName(), new Functions.CharacterClass());
		//
		functionMap.put(Boolean.TYPE.getTypeName(),   new Functions.BooleanClass());
		functionMap.put(Double.TYPE.getTypeName(),    new Functions.DoubleClass());
		functionMap.put(Float.TYPE.getTypeName(),     new Functions.FloatClass());
		functionMap.put(Long.TYPE.getTypeName(),      new Functions.LongClass());
		functionMap.put(Integer.TYPE.getTypeName(),   new Functions.IntegerClass());
		functionMap.put(Short.TYPE.getTypeName(),     new Functions.ShortClass());
		functionMap.put(Byte.TYPE.getTypeName(),      new Functions.ByteClass());
		functionMap.put(Character.TYPE.getTypeName(), new Functions.CharacterClass());
		//
		functionMap.put(String.class.getTypeName(),        new Functions.StringClass());
		//
		functionMap.put(LocalDateTime.class.getTypeName(), new Functions.LocalDateTimeClass());
		functionMap.put(LocalDate.class.getTypeName(),     new Functions.LocalDateClass());
		functionMap.put(LocalTime.class.getTypeName(),     new Functions.LocalTimeClass());
		//
		functionMap.put(BigDecimal.class.getTypeName(),    new Functions.BigDecimalClass());
	}
	private static class Functions {
		private static class BooleanClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				switch(valueType) {
				case TRUE:  return Boolean.TRUE;
				case FALSE: return Boolean.FALSE;
				default:
					break;
				}
				logger.error("Unexpected valueType");
				logger.error("  expect TRUE or FALSE");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class DoubleClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Double.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class FloatClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Float.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class LongClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Long.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class IntegerClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Integer.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class ShortClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Short.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class ByteClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return Byte.valueOf(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class CharacterClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.STRING)  {
					var string = jsonValueToString(jsonValue);
					if (string.length() != 1) {
						logger.error("Unexpected string");
						logger.error("  expect length of string is 1");
						logger.error("  {}!", string);
						throw new UnexpectedException("Unexpected string");
					}
					char c = string.charAt(0);
					return Character.valueOf(c);
				}
				
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		//
		private static class StringClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.STRING) return jsonValueToString(jsonValue);
				
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class LocalDateTimeClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.STRING) return LocalDateTime.parse(jsonValueToString(jsonValue));
				
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class LocalDateClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.STRING) return LocalDate.parse(jsonValueToString(jsonValue));
				
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		private static class LocalTimeClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.STRING) return LocalTime.parse(jsonValueToString(jsonValue));
				
				logger.error("Unexpected valueType");
				logger.error("  expect STRING");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
		//
		private static class BigDecimalClass implements Function<JsonValue, Object> {
			@Override
			public Object apply(JsonValue jsonValue) {
				var valueType = jsonValue.getValueType();
				if (valueType == ValueType.NUMBER) return new BigDecimal(jsonValue.toString());
				
				logger.error("Unexpected valueType");
				logger.error("  expect NUMBER");
				logger.error("  valueType {}", valueType);
				logger.error("  jsonValue {}", jsonValue);
				throw new UnexpectedException("Unexpected valueType");
			}
		}
	}
	
	
	
	//
	// enum
	//
	private static Map<String, Map<String, Object>> enumValueMap = new TreeMap<>();
	private static <E> E unmarshalEnum(Class<?> clazz, JsonValue jsonValue) {
		// sanity check
		if (!clazz.isEnum()) {
			logger.error("Unexpected clazz is not enum");
			logger.error("  clazz      {}", clazz.getTypeName());
			throw new UnexpectedException("Unexpected clazz is not enum");
		}
		
		var typeName = clazz.getTypeName();
		var map = enumValueMap.get(typeName);
		if (map == null) {
			map = new TreeMap<String, Object>();
			for(var e: clazz.getEnumConstants()) {
				map.put(e.toString(), e);
			}
			enumValueMap.put(typeName, map);
		}
		
		var valueType = jsonValue.getValueType();
		if (valueType == ValueType.STRING) {
			var string = jsonValueToString(jsonValue);
			var o = map.get(string);
			if (o != null) {
				@SuppressWarnings("unchecked")
				E ret = (E)o;
				return ret;
			}
			logger.error("Unexpected enum string");
			logger.error("  clazz     {}", typeName);
			logger.error("  string    {}!", string);
			logger.error("  map       {}!", map.keySet());
			throw new UnexpectedException("Unexpected enum string");
		} else {
			logger.error("Unexpected valueType");
			logger.error("  expect STRING");
			logger.error("  valueType {}", valueType);
			logger.error("  jsonValue {}", jsonValue);
			throw new UnexpectedException("Unexpected valueType");
		}
	}
	
	
	
	//
	// utility methods
	//
	private static JsonValue getJsonValue(Reader reader) {
		try (JsonReader jsonReader = Json.createReader(reader)) {
			return jsonReader.readValue();
		} catch(JsonException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	private static String jsonValueToString(JsonValue jsonValue) {
		var string = jsonValue.toString();
		if (jsonValue.getValueType() == ValueType.STRING) {			
			// sanity check
			var first = string.charAt(0);
			var last  = string.charAt(string.length() - 1);
			if (first != '"' || last != '"') {
				// unexpected
				logger.error("Unexpected first or last character");
				logger.error("  string {}!", string);
				logger.error("  first  {}", first);
				logger.error("  last   {}", last);
				throw new UnexpectedException("Unexpected first or last character");
			}
			
			// remove first and last character of string
			return string.substring(1, string.length() - 1);
		} else {
			return string;
		}
	}
}
