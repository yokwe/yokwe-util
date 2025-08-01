package yokwe.util.json;

import java.io.StringWriter;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import yokwe.util.GenericInfo;
import yokwe.util.UnexpectedException;

public class Marshal {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static String toString(Object object) {
		StringWriter writer = new StringWriter();
		
		try (JsonGenerator gen = Json.createGenerator(writer)) {
			marshal(gen, object);
		} catch (IllegalArgumentException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	
		return writer.toString();
	}
	
	private static void marshal(JsonGenerator gen, Object object) {
		marshal(gen, object, null);
	}
	private static void marshal(JsonGenerator gen, Object object, String name) {
		if (object == null) {
			if (name == null) {
				gen.writeNull();
			} else {
				gen.writeNull(name);
			}
			return;
		}
		
		var clazz = object.getClass();
		if (clazz.isEnum()) {
			var string = object.toString();
			if (name == null) {
				gen.write(string);
			} else {
				gen.write(name, string);
			}
			return;
		}
		if (clazz.isArray()) {
			marshalArray(gen, object, name);
			return;
		}
		
		// process common java class
		var typeName  = clazz.getTypeName();
		var function  = functionMap.get(typeName);
		if (function != null) {
			function.apply(gen, object, name);
			return;
		}
		
		if (JSON.DEFAULT_OPTIONS.matchIncludePackage(typeName)) {
			marshalObject(gen, object, name);
			return;
		}
		if (JSON.DEFAULT_OPTIONS.matchExcludePackage(typeName)) {
			var value = object.toString();
			if (name == null) {
				gen.write(value);
			} else {
				gen.write(name, value);
			}
			return;
		}
		
		marshalObject(gen, object, name);
	}
	
	private static void marshalArray(JsonGenerator gen, Object object, String name) {
		if (name == null) {
			gen.writeStartArray();
		} else {
			gen.writeStartArray(name);
		}
		
		int arrayLength = Array.getLength(object);
		for(var i = 0; i < arrayLength; i++) {
			var element = Array.get(object, i);
			marshal(gen, element);
		}
		
		gen.writeEnd();
	}
	private static void marshalMap(JsonGenerator gen, Object object, FieldInfo fieldInfo) {
		GenericInfo genericInfo = new GenericInfo(fieldInfo.field);
		// sanity check
		if (genericInfo.classArguments.length != 2) {
			logger.error("Unexptected genericInfo.classArguments");
			logger.error("  length {}", genericInfo.classArguments.length);
			throw new UnexpectedException("Unexptected genericInfo.classArguments");
		}
		Class<?> mapKeyClass   = genericInfo.classArguments[0];
//		Class<?> mapValueClass = genericInfo.classArguments[1];
		// sanity check
		if (!mapKeyClass.equals(String.class)) {
			logger.error("Unexpected map key class");
			logger.error("unexpeced fieldName");
			logger.error("  field  {}", fieldInfo.field);
			logger.error("  key    {}", mapKeyClass.getTypeName());
			throw new UnexpectedException("Unexpected map key class");
		}

		gen.writeStartObject(fieldInfo.jsonName);
		
		var map = (Map<?, ?>)object;
		for(var entry: map.entrySet()) {
			var key   = entry.getKey();
			var value = entry.getValue();
			marshal(gen, value, key.toString());
		}
		
		gen.writeEnd();
	}
	
	private static void marshalObject(JsonGenerator gen, Object object, String name) {
		if (name == null) {
			gen.writeStartObject();
		} else {
			gen.writeStartObject(name);
		}
		
		var clazz = object.getClass();
		var fieldInfoArray = FieldInfo.getFieldInfoArray(clazz);

		for(var fieldInfo: fieldInfoArray) {
			if (fieldInfo.ignore) continue;
			
			if (fieldInfo.type.equals(java.util.Map.class)) {
				marshalMap(gen, fieldInfo.get(object), fieldInfo);
			} else {
				marshal(gen, fieldInfo.get(object), fieldInfo.jsonName);
			}
		}
		
		gen.writeEnd();
	}
	
	
	static interface  MarshalObject {
		public void apply(JsonGenerator gen, Object object, String name);
	}
	
	private static Map<String, MarshalObject> functionMap = new TreeMap<>();
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
		functionMap.put(BigDecimal.class.getTypeName(),    new Functions.BigDecimalClass());
	}
	private static class Functions {
		private static class BooleanClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Boolean.class)) {
					var value = (Boolean)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Boolean");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class DoubleClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Double.class)) {
					var value = (Double)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Double");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class FloatClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Float.class)) {
					var value = (Float)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Float");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class LongClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Long.class)) {
					var value = (Long)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Long");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class IntegerClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Integer.class)) {
					var value = (Integer)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Integer");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  clazz  {}", Integer.class.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class ShortClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Short.class)) {
					var value = (Short)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Short");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class ByteClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Byte.class)) {
					var value = (Byte)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Byte");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class CharacterClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(Character.class)) {
					var value = (Character)object;
					var string = String.valueOf(value);
					if (name == null) {
						gen.write(string);
					} else {
						gen.write(name, string);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect Character");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class StringClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(String.class)) {
					var value = (String)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect String");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
		private static class BigDecimalClass implements MarshalObject {
			@Override
			public void apply(JsonGenerator gen, Object object, String name) {
				var clazz = object.getClass();
				if (clazz.equals(BigDecimal.class)) {
					var value = (BigDecimal)object;
					if (name == null) {
						gen.write(value);
					} else {
						gen.write(name, value);
					}
					return;
				}
				logger.error("Unexpected object class");
				logger.error("  expect BigDecimal");
				logger.error("  clazz  {}", clazz.getTypeName());
				logger.error("  object {}", object.toString());
				throw new UnexpectedException("Unexpected object class");
			}
		}
	}
}
