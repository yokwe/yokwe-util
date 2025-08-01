package yokwe.util.libreoffice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sun.star.lang.IndexOutOfBoundsException;
import com.sun.star.sheet.XCellRangeData;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.table.XCellRange;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.XNumberFormats;

import yokwe.util.DoubleUtil;
import yokwe.util.UnexpectedException;

public class Sheet {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface SheetName {
		String value();
	}

	// First row is zero
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface HeaderRow {
		int value();
	}

	// First row is zero
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface DataRow {
		int value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface ColumnName {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface NumberFormat {
		String value();
	}
	
	private static final int HASHCODE_CLASS_INTEGER    = Integer.class.hashCode();
	private static final int HASHCODE_CLASS_DOUBLE     = Double.class.hashCode();
	private static final int HASHCODE_CLASS_LONG       = Long.class.hashCode();
	private static final int HASHCODE_CLASS_STRING     = String.class.hashCode();
	private static final int HASHCODE_CLASS_BIGDECIMAL = BigDecimal.class.hashCode();
	private static final int HASHCODE_CLASS_LOCALDATE  = LocalDate.class.hashCode();
	private static final int HASHCODE_INT              = Integer.TYPE.hashCode();
	private static final int HASHCODE_DOUBLE           = Double.TYPE.hashCode();
	private static final int HASHCODE_LONG             = Long.TYPE.hashCode();
		
	public static <E extends Sheet> String getSheetName(Class<E> clazz) {
		SheetName sheetName = clazz.getDeclaredAnnotation(SheetName.class);
		if (sheetName == null) {
			logger.error("No SheetName annotation = {}", clazz.getName());
			throw new UnexpectedException("No SheetName annotation");
		}
		
		return sheetName.value();
	}

	
	private static class ColumnInfo {
		public static final int MAX_COLUMN = 99;
		
		public final String   name;
		public final int      index;
		
		public final Field       field;
		public final int         fieldType;
		public final Map<String, Enum<?>> fieldEnumMap;

		public final String   numberFormat;
		public final boolean  isDate;
		public final boolean  isInteger;
		
		public ColumnInfo(String name, int index, Field field) {
			this.name         = name;
			this.index        = index;
			
			this.field        = field;
			this.fieldType    = field.getType().hashCode();
			{
				Class<?> clazz = field.getType();
				if (clazz.isEnum()) {
					fieldEnumMap = new TreeMap<>();
					
					@SuppressWarnings("unchecked")
					Class<Enum<?>> enumClazz = (Class<Enum<?>>)clazz;
					for(Enum<?> e: enumClazz.getEnumConstants()) {
						fieldEnumMap.put(e.toString(), e);
					}
				} else {
					fieldEnumMap = null;
				}
			}
			
			NumberFormat numberFormat = field.getDeclaredAnnotation(NumberFormat.class);
			this.numberFormat = (numberFormat == null) ? null : numberFormat.value();
			this.isDate       = SpreadSheet.FORMAT_DATE.equals(this.numberFormat);
			this.isInteger    = SpreadSheet.FORMAT_INTEGER.equals(this.numberFormat);
		}
		
		public static List<ColumnInfo> getColumnInfoList(XSpreadsheet xSpreadsheet, int headerRow, Field[] fields) {
			try {
				// build fieldMap
				Map<String, Field> fieldMap = new TreeMap<>();
				for(Field field: fields) {
					ColumnName columnName = field.getDeclaredAnnotation(ColumnName.class);
					if (columnName == null) continue;
					fieldMap.put(columnName.value(), field);
				}
				
				XCellRange cellRange = xSpreadsheet.getCellRangeByPosition(0, headerRow, MAX_COLUMN, headerRow);
				XCellRangeData cellRangeData = UnoRuntime.queryInterface(XCellRangeData.class, cellRange);
				Object data[][] = cellRangeData.getDataArray();

				// build columnInfoList
				List<ColumnInfo> columnInfoList = new ArrayList<>();
				int dataSize = data[0].length;
				for(int index = 0; index < dataSize; index++) {
					String name = data[0][index].toString();
					if (name.length() == 0) continue;
					
					Field field = fieldMap.get(name);
					if (field == null) {
						logger.warn("No field {}", name);
						continue;
					}
					
					ColumnInfo columnInfo = new ColumnInfo(name, index, field);
					columnInfoList.add(columnInfo);
				}
				
				return columnInfoList;
			} catch (IndexOutOfBoundsException e) {
				logger.error("Exception {}", e.toString());
				throw new UnexpectedException("Unexpected");
			}
		}
		public static ColumnInfo findByName(List<ColumnInfo> columnInfoList, String name) {
			for(ColumnInfo columnInfo: columnInfoList) {
				if (columnInfo.name.equals(name)) return columnInfo;
			}
			logger.error("Unknown name {}", name);
			throw new UnexpectedException("Unknown name");
		}
	}
	private static class RowRange {
		public static final int MAX_ROW = 9999;
		
		public final int      rowBegin;
		public final int      rowEnd;
		public final int      rowSize;
		public final String[] keys;
		
		public RowRange(int rowBegin, int rowEnd, String[] keys) {
			this.rowBegin = rowBegin;
			this.rowEnd   = rowEnd;
			this.rowSize  = rowEnd - rowBegin + 1;
			this.keys     = keys;
		}
		
		public static List<RowRange> getRowRangeList(XSpreadsheet xSpreadsheet, int keyColumn, int dataRow) {
			try {
				XCellRange cellRange = xSpreadsheet.getCellRangeByPosition(keyColumn, dataRow, keyColumn, dataRow + MAX_ROW);
				XCellRangeData cellRangeData = UnoRuntime.queryInterface(XCellRangeData.class, cellRange);
				Object data[][] = cellRangeData.getDataArray();
				
				// Build rowRangeList
				List<RowRange> rowRangeList = new ArrayList<>();
				int row = 0;
				for(;;) {
					if (MAX_ROW <= row) break; // reached to end

					int rowBegin = -1;
					int rowEnd   = -1;
					List<String> keyList = new ArrayList<>();
					
					// Skip empty value
					for(; row < MAX_ROW; row++) {
						String value = data[row][0].toString();
						if (0 < value.length()) break;
					}
					if (MAX_ROW <= row) break; // reached to end
					rowBegin = dataRow + row;
					
					// add until empty value
					for(; row < MAX_ROW; row++) {
						String value = data[row][0].toString();
						if (value.length() == 0) break;
						keyList.add(value);
					}
					rowEnd = dataRow + row - 1;
					
					RowRange rowRange = new RowRange(rowBegin, rowEnd, keyList.toArray(new String[0]));
					rowRangeList.add(rowRange);
				}
				return rowRangeList;
			} catch (IndexOutOfBoundsException e) {
				logger.error("Exception {}", e.toString());
				throw new UnexpectedException("Unexpected");
			}
		}

		public static List<RowRange> getRowRangeList(int rowBegin, int rowEnd) {
			List<RowRange> rowRangeList = new ArrayList<>();
			
			RowRange rowRange = new RowRange(rowBegin, rowEnd, null);
			rowRangeList.add(rowRange);
			
			return rowRangeList;
		}
	}
	public static <E extends Sheet> void fillSheet(SpreadSheet spreadSheet, Map<String, E> dataMap, String keyColumnName, String sheetName) {
		final XSpreadsheet   xSpreadsheet   = spreadSheet.getSheet(sheetName);
		final XNumberFormats xNumberFormats = spreadSheet.getNumberFormats();
		final int            headerRow;
		final int            dataRow;
		final Field[]        fields;
		
		{
			E o = dataMap.values().iterator().next();
			HeaderRow headerRowAnnotation = o.getClass().getDeclaredAnnotation(HeaderRow.class);
			DataRow   dataRowAnnotation   = o.getClass().getDeclaredAnnotation(DataRow.class);
			
			if (headerRowAnnotation == null) {
				logger.error("No HeaderRow annotation = {}", o.getClass().getName());
				throw new UnexpectedException("No HeaderRow annotation");
			}
			if (dataRowAnnotation == null) {
				logger.error("No DataRow annotation = {}", o.getClass().getName());
				throw new UnexpectedException("No DataRow annotation");
			}
			headerRow = headerRowAnnotation.value();
			dataRow   = dataRowAnnotation.value();
			fields    = o.getClass().getDeclaredFields();
		}

		{
			List<ColumnInfo> columnInfoList = ColumnInfo.getColumnInfoList(xSpreadsheet, headerRow, fields);
			
			// Build rowRangeList
			ColumnInfo     keyColumn    = ColumnInfo.findByName(columnInfoList, keyColumnName);
			List<RowRange> rowRangeList = RowRange.getRowRangeList(xSpreadsheet, keyColumn.index, dataRow);
			
			// Remove keyColumn to prevent from update
			columnInfoList.remove(keyColumn);
			
			// Build fillMap
			Map<String, Object> fillMap = new HashMap<>();
			for(RowRange rowRange: rowRangeList) {
				final int rowBegin = rowRange.rowBegin;
				final int rowSize  = rowRange.rowSize;
				
				for(int i = 0; i < rowSize; i++) {
					E data = dataMap.get(rowRange.keys[i]);
					if (data == null) {
						logger.warn("no entry in dataMap  key = {}", rowRange.keys[i]);
						continue;
					}
					buildFillMap(columnInfoList, rowBegin + i, data, fillMap);
				}
			}
			
			// Apply fillMap
			applyFillMap(xSpreadsheet, xNumberFormats, columnInfoList, rowRangeList, fillMap);
		}
	}

	public static <E extends Sheet> void fillSheet(SpreadSheet spreadSheet, List<E> dataList, String sheetName) {
		if (dataList.isEmpty()) {
			logger.warn("dataList is empty");
			return;
		}
		
		final XSpreadsheet   xSpreadsheet   = spreadSheet.getSheet(sheetName);
		final XNumberFormats xNumberFormats = spreadSheet.getNumberFormats();
		final int            headerRow;
		final int            dataRow;
		final Field[]        fields;
		
		{
			E o = dataList.iterator().next();
			HeaderRow      headerRowAnnotation    = o.getClass().getDeclaredAnnotation(HeaderRow.class);
			DataRow        dataRowAnnotation      = o.getClass().getDeclaredAnnotation(DataRow.class);
			
			if (headerRowAnnotation == null) {
				logger.error("No HeaderRow annotation = {}", o.getClass().getName());
				throw new UnexpectedException("No HeaderRow annotation");
			}
			if (dataRowAnnotation == null) {
				logger.error("No DataRow annotation = {}", o.getClass().getName());
				throw new UnexpectedException("No DataRow annotation");
			}
			headerRow = headerRowAnnotation.value();
			dataRow   = dataRowAnnotation.value();
			fields    = o.getClass().getDeclaredFields();
		}
		
		{
			List<ColumnInfo> columnInfoList = ColumnInfo.getColumnInfoList(xSpreadsheet, headerRow, fields);
			
			// Build rowRangeList
			List<RowRange> rowRangeList = RowRange.getRowRangeList(dataRow, dataRow + dataList.size() - 1);
			
			// Build fillMap
			Map<String, Object> fillMap = new HashMap<>();
			for(RowRange rowRange: rowRangeList) {
				final int rowBegin = rowRange.rowBegin;
				final int rowSize  = rowRange.rowSize;
				
				for(int i = 0; i < rowSize; i++) {
					E data = dataList.get(i);
					buildFillMap(columnInfoList, rowBegin + i, data, fillMap);
				}
			}
			
			// Apply fillMap
			applyFillMap(xSpreadsheet, xNumberFormats, columnInfoList, rowRangeList, fillMap);
		}
	}
	
	private static <E extends Sheet> void buildFillMap(List<ColumnInfo> columnInfoList, int row, E data, Map<String, Object> fillMap) {
		try {
			for(ColumnInfo columnInfo: columnInfoList) {
				String fillMapKey = row + "-" + columnInfo.index;
				
				// Type of value must be String or Double
				Object value;
				{
					Object o = columnInfo.field.get(data);
					if (o == null) {
						value = "";
					} else {
						if (columnInfo.fieldType == HASHCODE_CLASS_STRING) {
							String string = o.toString();
							if (columnInfo.isDate && string.length() == SpreadSheet.FORMAT_DATE.length()) {
								value = Double.valueOf(SpreadSheet.toDateNumber(string)); // Convert to double for date number
							} else {
								value = string;
							}
						} else if (columnInfo.fieldType == HASHCODE_CLASS_DOUBLE) {
							value = Double.valueOf((Double)o);
						} else if (columnInfo.fieldType == HASHCODE_CLASS_BIGDECIMAL) {
							value = Double.valueOf(((BigDecimal)o).doubleValue());
						} else if (columnInfo.fieldType == HASHCODE_CLASS_LOCALDATE) {
							String string = o.toString();
							if (columnInfo.isDate && string.length() == SpreadSheet.FORMAT_DATE.length()) {
								value = Double.valueOf(SpreadSheet.toDateNumber(string)); // Convert to double for date number
							} else {
								value = string;
							}
						} else if (columnInfo.fieldType == HASHCODE_CLASS_INTEGER) {
							value = Double.valueOf((Integer)o);
						} else if (columnInfo.fieldType == HASHCODE_CLASS_LONG) {
							value = Double.valueOf((Long)o);
						} else if (columnInfo.fieldType == HASHCODE_DOUBLE) {
							value = columnInfo.field.getDouble(data);
						} else if (columnInfo.fieldType == HASHCODE_INT) {
							value = Double.valueOf(columnInfo.field.getInt(data));  // Convert to double for numeric value
						} else if (columnInfo.fieldType == HASHCODE_LONG) {
							value = Double.valueOf(columnInfo.field.getLong(data)); // Convert to double for numeric value
						} else {
							logger.error("Unknow field type = {}", columnInfo.field.getType().getName());
							throw new UnexpectedException("Unexpected");
						}
					}
				}
				fillMap.put(fillMapKey, value);
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			logger.error("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected");
		}
	}

	private static <E extends Sheet> void applyFillMap(XSpreadsheet xSpreadsheet, XNumberFormats xNumberFormats, List<ColumnInfo> columnInfoList, List<RowRange> rowRangeList, Map<String, Object> fillMap) {
		try {
			for(RowRange rowRange: rowRangeList) {
				final int rowBegin = rowRange.rowBegin;
				final int rowEnd   = rowRange.rowEnd;
				final int rowSize  = rowRange.rowSize;

				// set number format to range of cell
				for(ColumnInfo columnInfo: columnInfoList) {
					// left top right bottom
					XCellRange xCellRange = xSpreadsheet.getCellRangeByPosition(columnInfo.index, rowBegin, columnInfo.index, rowEnd);
					
					// apply numberFormat
					if (columnInfo.numberFormat != null) {
						SpreadSheet.setNumberFormat(xCellRange, columnInfo.numberFormat, xNumberFormats);
					}
				}
				
				// set data to cell
				List<List<ColumnInfo>> listList = new ArrayList<>();
				{
					// find continuous ColumnInfo.idex and store them to list
					List<ColumnInfo> list = new ArrayList<>();
					int nextIndex = -1;
					for(ColumnInfo e: columnInfoList) {
						if (e.index == nextIndex || nextIndex == -1) {
							list.add(e);
							nextIndex = e.index + 1;
						} else {
							if (!list.isEmpty()) {
								listList.add(list);
								list.clear();
							}
							nextIndex = -1;
						}
					}
					if (!list.isEmpty()) {
						listList.add(list);
					}
				}
				for(List<ColumnInfo> list: listList) {
					int columnSize  = list.size();
					int columnBegin = list.get(0).index;
					int columnEnd   = columnBegin + columnSize - 1;
					
					// Sanity check
					for(int i = 0; i < columnSize; i++) {
						if (list.get(i).index != (columnBegin + i)) {
							logger.error("Unexpected index");
							for(ColumnInfo ee: list) {
								logger.error("  {}  {}", ee.index, ee.name);
							}
							throw new UnexpectedException("Unexpected index");
						}
					}
					
					// call setDataArray with data[rowSize][columnSize]
					XCellRange     xCellRange     = xSpreadsheet.getCellRangeByPosition(columnBegin, rowBegin, columnEnd, rowEnd);
					XCellRangeData xCellRangeData = UnoRuntime.queryInterface(XCellRangeData.class, xCellRange);
					Object data[][] = new Object[rowSize][columnSize]; // row column
					for(int i = 0; i < rowSize; i++) {
						for(int j = 0; j < columnSize; j++) {
							String fillMapKey = (rowBegin + i) + "-" + (columnBegin + j);
							Object value = fillMap.get(fillMapKey);
							data[i][j] = (value == null) ? "*NA*" : value;
						}
					}
					xCellRangeData.setDataArray(data);
				}
			}
		} catch (IndexOutOfBoundsException e) {
			logger.error("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected");
		}
	}
	public static <E extends Sheet> void fillSheet(SpreadSheet spreadSheet, List<E> dataList) {
		if (dataList.isEmpty()) {
			logger.warn("dataList is empty");
			return;
		}
		
		E o = dataList.iterator().next();
		String sheetName = getSheetName(o.getClass());
		fillSheet(spreadSheet, dataList, sheetName);
	}
	
	public static <E extends Sheet> List<E> extractSheet(SpreadSheet spreadSheet, Class<E> clazz, String sheetName) {
		final XSpreadsheet   xSpreadsheet   = spreadSheet.getSheet(sheetName);
		final int            headerRow;
		final int            dataRow;
		final Field[]        fields;
		
		{
			HeaderRow      headerRowAnnotation    = clazz.getDeclaredAnnotation(HeaderRow.class);
			DataRow        dataRowAnnotation      = clazz.getDeclaredAnnotation(DataRow.class);
			
			if (headerRowAnnotation == null) {
				logger.error("No HeaderRow annotation = {}", clazz.getName());
				throw new UnexpectedException("No HeaderRow annotation");
			}
			if (dataRowAnnotation == null) {
				logger.error("No DataRow annotation = {}", clazz.getName());
				throw new UnexpectedException("No DataRow annotation");
			}
			headerRow = headerRowAnnotation.value();
			dataRow   = dataRowAnnotation.value();
			fields    = clazz.getDeclaredFields();
		}

		List<ColumnInfo> columnInfoList = ColumnInfo.getColumnInfoList(xSpreadsheet, headerRow, fields);
		List<RowRange>   rowRangeList   = RowRange.getRowRangeList(xSpreadsheet, 0, dataRow); // assume column 0 as key column
		
		{
			try {
				// key is row-columnIndex
				Map<String, Object> extractMap = new HashMap<>();
				
				// build extractMap
				for(RowRange rowRange: rowRangeList) {
					final int rowBegin = rowRange.rowBegin;
					final int rowEnd   = rowRange.rowEnd;
					final int rowSize  = rowRange.rowSize;

					for(ColumnInfo columnInfo: columnInfoList) {
						// left top right bottom
						XCellRange xCellRange = xSpreadsheet.getCellRangeByPosition(columnInfo.index, rowBegin, columnInfo.index, rowEnd);
												
						// extract data
						XCellRangeData xCellRangeData = UnoRuntime.queryInterface(XCellRangeData.class, xCellRange);
						Object[][] data = xCellRangeData.getDataArray();
						
						// put data into extractMap
						for(int i = 0; i < rowSize; i++) {
							String extractMapKey = (rowBegin + i) + "-" + columnInfo.index;
							extractMap.put(extractMapKey, data[i][0]);
						}
					}
				}
				
				// build dataList
				List<E> dataList = new ArrayList<>();
				for(RowRange rowRange: rowRangeList) {
					final int rowBegin = rowRange.rowBegin;
					final int rowSize  = rowRange.rowSize;

					for(int i = 0; i < rowSize; i++) {
						E data = clazz.getDeclaredConstructor().newInstance();
						boolean dataHasError = false;
						
						for(ColumnInfo columnInfo: columnInfoList) {
							Field   field     = columnInfo.field;
							int     fieldType = columnInfo.fieldType;
							boolean isDate    = columnInfo.isDate;
							boolean isInteger = columnInfo.isInteger;
							
							String extractMapKey = (rowBegin + i) + "-" + columnInfo.index;
							Object o = extractMap.get(extractMapKey);
							int    oType = o.getClass().hashCode();
							
							// skip unknown type and set true to dataHasError
							if (o.getClass().getName().equals("com.sun.star.uno.Any")) {
								dataHasError = true;
								continue;
							}
							
							if (oType == HASHCODE_CLASS_STRING) {
								String value = (String)o;
								boolean isEmpty = value.length() == 0;
								
								if (isEmpty) {
									if (fieldType == HASHCODE_CLASS_STRING) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_CLASS_DOUBLE) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_CLASS_BIGDECIMAL) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_CLASS_LOCALDATE) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_CLASS_INTEGER) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_CLASS_LONG) {
										field.set(data, null);
									} else if (fieldType == HASHCODE_DOUBLE) {
										field.setDouble(data, 0);
									} else if (fieldType == HASHCODE_INT) {
										field.setInt(data, 0);
									} else if (fieldType == HASHCODE_LONG) {
										field.setLong(data, 0);
									} else if (columnInfo.fieldEnumMap != null) {
										field.set(data, null);
									} else {
										logger.error("Unknow field type = {}", columnInfo.field.getType().getName());
										throw new UnexpectedException("Unexpected");
									}
								} else {
									if (fieldType == HASHCODE_CLASS_STRING) {
										field.set(data, value);
									} else if (fieldType == HASHCODE_CLASS_DOUBLE) {
										field.set(data, Double.valueOf(value));
									} else if (fieldType == HASHCODE_CLASS_BIGDECIMAL) {
										field.set(data, new BigDecimal(value));
									} else if (fieldType == HASHCODE_CLASS_LOCALDATE) {
										field.set(data, LocalDate.parse(value));
									} else if (fieldType == HASHCODE_CLASS_INTEGER) {
										field.set(data, Integer.valueOf(value));
									} else if (fieldType == HASHCODE_CLASS_LONG) {
										field.set(data, Long.valueOf(value));
									} else if (fieldType == HASHCODE_DOUBLE) {
										field.setDouble(data, Double.valueOf(value));
									} else if (fieldType == HASHCODE_INT) {
										field.setInt(data, Integer.valueOf(value));
									} else if (fieldType == HASHCODE_LONG) {
										field.setLong(data, Integer.valueOf(value));
									} else if (columnInfo.fieldEnumMap != null) {
										if (columnInfo.fieldEnumMap.containsKey(value)) {
											Enum<?> enumValue = columnInfo.fieldEnumMap.get(value);
											field.set(data, enumValue);
										} else {
											logger.error("Unknow enum value = {}  {}", columnInfo.field.getType().getName(), value);
											throw new UnexpectedException("Unexpected");											
										}
									} else {
										logger.error("Unknow field type = {}", columnInfo.field.getType().getName());
										throw new UnexpectedException("Unexpected");
									}
								}
							} else if (oType == HASHCODE_CLASS_DOUBLE) {
								double value = (Double)o;
								
								if (fieldType == HASHCODE_CLASS_STRING) {
									if (isDate) {
										field.set(data, SpreadSheet.toDateString(value));
									} else if (isInteger) {
										field.set(data, String.valueOf((long)value));
									} else {
										field.set(data, String.valueOf(value));
									}
								} else if (fieldType == HASHCODE_CLASS_DOUBLE) {
									field.set(data, (Double)value);
								} else if (fieldType == HASHCODE_CLASS_BIGDECIMAL) {
									field.set(data, DoubleUtil.toBigDecimal(value));
								} else if (fieldType == HASHCODE_CLASS_LOCALDATE) {
									if (isDate) {
										field.set(data, SpreadSheet.toDateString(value));
									} else {
										logger.error("Unexpected NumberFormat annotation = {}", columnInfo.field.getType().getName());
										throw new UnexpectedException("Unexpected");
									}
								} else if (fieldType == HASHCODE_CLASS_INTEGER) {
									field.set(data, (Integer)((int)value));
								} else if (fieldType == HASHCODE_CLASS_LONG) {
									field.set(data, (Long)((long)value));
								} else if (fieldType == HASHCODE_DOUBLE) {
									field.setDouble(data, value);
								} else if (fieldType == HASHCODE_INT) {
									field.setInt(data, (int)value);
								} else if (fieldType == HASHCODE_LONG) {
									field.setLong(data, (long)value);
								} else {
									logger.error("Unknow field type = {}", columnInfo.field.getType().getName());
									throw new UnexpectedException("Unexpected");
								}
							} else {
								logger.error("Unknow oType = {}  {}", o.getClass().getName());
								throw new UnexpectedException("Unexpected");
							}
						}
						if (dataHasError) {
							logger.warn("hasError  at row  {}", rowBegin + i + 1);
						} else {
							dataList.add(data);
						}
					}
				}
				
				return dataList;
			} catch (IndexOutOfBoundsException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
				logger.error("Exception {}", e.toString());
				throw new UnexpectedException("Unexpected");
			}
		}
	}

	public static <E extends Sheet> List<E> extractSheet(SpreadSheet spreadSheet, Class<E> clazz) {
		String sheetName = getSheetName(clazz);
		return extractSheet(spreadSheet, clazz, sheetName);
	}
}
