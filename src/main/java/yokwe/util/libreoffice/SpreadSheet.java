package yokwe.util.libreoffice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XIndexAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.IndexOutOfBoundsException;
import com.sun.star.lang.Locale;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.sheet.XCellRangeData;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetDocument;
import com.sun.star.sheet.XSpreadsheets;
import com.sun.star.sheet.XSpreadsheets2;
import com.sun.star.table.CellContentType;
import com.sun.star.table.XCell;
import com.sun.star.table.XCellRange;
import com.sun.star.uno.AnyConverter;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.MalformedNumberFormatException;
import com.sun.star.util.XNumberFormats;
import com.sun.star.util.XNumberFormatsSupplier;

import yokwe.util.UnexpectedException;

public class SpreadSheet extends LibreOffice {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static final String FORMAT_STRING  = "@";
	public static final String FORMAT_DATE    = "YYYY-MM-DD";
	
	public static final String FORMAT_PERCENT = "#,##0.0%;[RED]-#,##0.0%";
	
	public static final String FORMAT_PERCENT0 = "#,##0%;[RED]-#,##0%";
	public static final String FORMAT_PERCENT2 = "#,##0.00%;[RED]-#,##0.00%";
	public static final String FORMAT_PERCENT3 = "#,##0.000%;[RED]-#,##0.000%";

	public static final String FORMAT_INTEGER = "#,##0.#####;[RED]-#,##0.#####";
	
	public static final String FORMAT_NUMBER0 = "#,##0;[RED]-#,##0";
	public static final String FORMAT_NUMBER2 = "#,##0.00;[RED]-#,##0.00";
	public static final String FORMAT_NUMBER3 = "#,##0.000;[RED]-#,##0.000";
	public static final String FORMAT_NUMBER4 = "#,##0.0000;[RED]-#,##0.0000";
	public static final String FORMAT_NUMBER5 = "#,##0.00000;[RED]-#,##0.00000";
	
	public static final String FORMAT_NUMBER_MILLION = "#,##0,,;[RED]-#,##0,,";
	
	public static final String FORMAT_NUMBER2_BLANK = "#,##0.00;[RED]-#,##0.00;\"\";@";
	
	public static final String FORMAT_USD  = "[$$-409]#,##0.00;[RED]-[$$-409]#,##0.00";
	public static final String FORMAT_USD5 = "[$$-409]#,##0.00000;[RED]-[$$-409]#,##0.00000";
	public static final String FORMAT_JPY  = "[$￥-411]#,##0;[RED]-[$￥-411]#,##0";
	public static final String FORMAT_JPY1 = "[$￥-411]#,##0.0;[RED]-[$￥-411]#,##0.0";

	public static final String FORMAT_USD_BLANK  = "[$$-409]#,##0.00;[RED]-[$$-409]#,##0.00;\"\";@";
	public static final String FORMAT_USD5_BLANK = "[$$-409]#,##0.00000;[RED]-[$$-409]#,##0.00000;\"\";@";
	public static final String FORMAT_JPY_BLANK  = "[$￥-411]#,##0;[RED]-[$￥-411]#,##0;\"\";@";
	public static final String FORMAT_JPY1_BLANK = "[$￥-411]#,##0.0;[RED]-[$￥-411]#,##0.0;\"\";@";
	public static final String FORMAT_INTEGER_BLANK = "#,##0.#####;[RED]-#,##0.#####;\"\";@";

	private static final Map<CellContentType, String> cellContentTypeMap = new HashMap<>();
	static {
		cellContentTypeMap.put(CellContentType.EMPTY,   "EMPTY");
		cellContentTypeMap.put(CellContentType.FORMULA, "FORMULA");
		cellContentTypeMap.put(CellContentType.TEXT,    "TEXT");
		cellContentTypeMap.put(CellContentType.VALUE,   "VALUE");
	}
	public static String toString(CellContentType type) {
		String ret = cellContentTypeMap.get(type);
		if (ret == null) {
			logger.info("Unexpected {}", type.toString());
			throw new UnexpectedException("Unexpected type");
		}
		return ret;
	}
	
	public static final LocalDate DATE_EPOCH = LocalDate.of(1899, 12, 30);
	public static double toDateNumber(String dateString) {
		LocalDate date = LocalDate.parse(dateString);
		return ChronoUnit.DAYS.between(DATE_EPOCH, date);
	}
	public static String toDateString(double dateNumber) {
		return DateTimeFormatter.ISO_LOCAL_DATE.format(DATE_EPOCH.plusDays((int)dateNumber));
	}
	
	
	public SpreadSheet(String urlString, boolean readOnly) {
		super(urlString, readOnly);
	}
	
	public static final String NEW_SPREADSHEET_URL = "private:factory/scalc";
	public SpreadSheet() {
		super(NEW_SPREADSHEET_URL, false);
	}

	
	private XSpreadsheetDocument getXSpreadsheetDocument() {
		XSpreadsheetDocument spreadsheetDocument = UnoRuntime.queryInterface(XSpreadsheetDocument.class, component);
		if (spreadsheetDocument == null) {
			logger.info("component {}", component.toString());
			throw new UnexpectedException("Unexpected component");
		}
		return spreadsheetDocument;
	}
	public XSpreadsheets2 getSheets() {
		XSpreadsheetDocument spreadsheetDocument = getXSpreadsheetDocument();
		XSpreadsheets        spreadSheets        = spreadsheetDocument.getSheets();
		XSpreadsheets2       spreadSheets2       = UnoRuntime.queryInterface(XSpreadsheets2.class, spreadSheets);
		if (spreadSheets2 == null) {
			logger.info("spreadSheets2 == null {}", component.toString());
			throw new UnexpectedException("spreadSheets2 == null");
		}
		return spreadSheets2;
	}


	public int getSheetCount() {
		XIndexAccess indexAccess = UnoRuntime.queryInterface(XIndexAccess.class, getSheets());
		return indexAccess.getCount();
	}
	public int getSheetIndex(String sheetName) {
		XNameAccess nameAccess = UnoRuntime.queryInterface(XNameAccess.class, getSheets());
		String[] names = nameAccess.getElementNames();
		for(int i = 0; i < names.length; i++) {
			if (names[i].equals(sheetName)) return i;
		}
		logger.info("No sheet {}", sheetName);
		throw new UnexpectedException("No sheet");
	}
	public List<String> getSheetNameList() {
		List<String> ret = new ArrayList<>();
		XNameAccess nameAccess = UnoRuntime.queryInterface(XNameAccess.class, getSheets());
		String[] names = nameAccess.getElementNames();
		for(String name: names) {
			ret.add(name);
		}
		return ret;
	}
	public String getSheetName(int index) {
		XNameAccess nameAccess = UnoRuntime.queryInterface(XNameAccess.class, getSheets());
		String[] names = nameAccess.getElementNames();
		if (0 <= index && index <= names.length) {
			return names[index];
		}
		logger.info("Index out of range {}", index);
		throw new UnexpectedException("Index out of range");
	}

	public XSpreadsheet getSheet(int index) {
		try {
			XIndexAccess indexAccess = UnoRuntime.queryInterface(XIndexAccess.class, getSheets());
			XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, indexAccess.getByIndex(index));
			return sheet;
		} catch (IndexOutOfBoundsException | WrappedTargetException e) {
			logger.info("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected exception");
		}
	}
	public XSpreadsheet getSheet(String name) {
		try {
			XNameAccess nameAccess = UnoRuntime.queryInterface(XNameAccess.class, getSheets());
			XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, nameAccess.getByName(name));
			return sheet;
		} catch (NoSuchElementException e) {
			logger.info("NoSuchElementException {} {}", e.toString(), name);
			throw new UnexpectedException("NoSuchElementException");
		} catch (WrappedTargetException e) {
			logger.info("WrappedTargetException {}", e.toString());
			throw new UnexpectedException("WrappedTargetException");
		}
	}

	public void importSheet(SpreadSheet oldSheet, String oldName, int newSheetPosition) {
		XSpreadsheets2 spreadSheets = getSheets();
		if (spreadSheets == null) {
			logger.info("component {}", component.toString());
			throw new UnexpectedException("Unexpected component");
		}
		
		try {
			XSpreadsheetDocument oldDoc = oldSheet.getXSpreadsheetDocument();
			spreadSheets.importSheet(oldDoc, oldName, newSheetPosition);
		} catch (IllegalArgumentException e) {
			logger.info("IllegalArgumentException {}", e.toString());
			throw new UnexpectedException("IllegalArgumentException");
		} catch (IndexOutOfBoundsException e) {
			logger.info("IndexOutOfBoundsException {}", e.toString());
			throw new UnexpectedException("IndexOutOfBoundsException");
		}
	}
	
	public void renameSheet(String oldSheetName, String newSheetName) {
		int index = getSheetIndex(oldSheetName);
		copySheet(oldSheetName, newSheetName, index);
		removeSheet(oldSheetName);
	}
	public void copySheet(String oldSheetName, String newSheetName, int newSheetPosition) {
		XSpreadsheets2 spreadSheets = getSheets();
		spreadSheets.copyByName(oldSheetName, newSheetName, (short)newSheetPosition);
	}
	public void removeSheet(String sheetName) {
		XSpreadsheets2 spreadSheets = getSheets();
		try {
			spreadSheets.removeByName(sheetName);
		} catch (NoSuchElementException e) {
			logger.info("NoSuchElementException {}", e.toString());
			throw new UnexpectedException("NoSuchElementException");
		} catch (WrappedTargetException e) {
			logger.info("WrappedTargetException {}", e.toString());
			throw new UnexpectedException("WrappedTargetException");
		}
	}
	public void removeSheet(int index) {
		String sheetName = getSheetName(index);
		removeSheet(sheetName);
	}


	// column, rowFirst and rowLast are zero based
	public static int getLastDataRow(XSpreadsheet spreadsheet, int column, int rowFirst, int rowLast) {
		try {
			XCellRange cellRange = spreadsheet.getCellRangeByPosition(column, 0, column, rowLast);
			XCellRangeData cellRangeData = UnoRuntime.queryInterface(XCellRangeData.class, cellRange);
			Object data[][] = cellRangeData.getDataArray();
			int ret = -1;
			for(int i = rowFirst; i < data.length; i++) {
				Object o = data[i][0];
				// if o is empty string return ret
				if (o instanceof String && ((String)o).length() == 0) return ret;
				ret = i;
			}
			return ret;
		} catch (IndexOutOfBoundsException e) {
			logger.info("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected exception");
		}
	}
	
	private static final Locale locale = new Locale();
	private static final String PROPERTY_NUMBER_FORMAT = "NumberFormat";
	private static final String PROPERTY_FORMAT_STRING = "FormatString";
	
	public XNumberFormats getNumberFormats() {
		XNumberFormatsSupplier xNumberFormatsSupplier = UnoRuntime.queryInterface(XNumberFormatsSupplier.class, component);
		return xNumberFormatsSupplier.getNumberFormats();
	}
	public static String getFormatString(XCell cell, XNumberFormats xNumberFormats) {
		try {
			XPropertySet   xPropertySet      = UnoRuntime.queryInterface(XPropertySet.class, cell);
			
			int            index             = AnyConverter.toInt(xPropertySet.getPropertyValue(PROPERTY_NUMBER_FORMAT));
			XPropertySet   numberFormatProps = xNumberFormats.getByKey(index);
			String         formatString      = AnyConverter.toString(numberFormatProps.getPropertyValue(PROPERTY_FORMAT_STRING));
			return formatString;
		} catch (IllegalArgumentException | UnknownPropertyException | WrappedTargetException e) {
			logger.info("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected exception");
		}
	}
	public String getFormatString(XCell cell) {
		return getFormatString(cell, getNumberFormats());
	}
	
	public static void setNumberFormat(XCell cell, String numberFormat, XNumberFormats xNumberFormats) {
		try {
			int index = xNumberFormats.queryKey(numberFormat, locale, false);
			if (index == -1) {
				index = xNumberFormats.addNew(numberFormat, locale);
			}
			
			XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, cell);
			xPropertySet.setPropertyValue(PROPERTY_NUMBER_FORMAT, Integer.valueOf(index));
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
				| WrappedTargetException | MalformedNumberFormatException e) {
			logger.info("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected exception");
		}
	}
	
	public static void setNumberFormat(XCellRange xCellRange, String numberFormat, XNumberFormats xNumberFormats) {
		try {
			int index = xNumberFormats.queryKey(numberFormat, locale, false);
			if (index == -1) {
				index = xNumberFormats.addNew(numberFormat, locale);
			}
			
			XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xCellRange);
			xPropertySet.setPropertyValue(PROPERTY_NUMBER_FORMAT, Integer.valueOf(index));
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
				| WrappedTargetException | MalformedNumberFormatException e) {
			logger.info("Exception {}", e.toString());
			throw new UnexpectedException("Unexpected exception");
		}
	}
	
	public void setNumberFormat(XCell cell, String numberFormat) {
		setNumberFormat(cell, numberFormat, getNumberFormats());
	}
	public void setNumberFormat(XCellRange xCellRange, String numberFormat) {
		setNumberFormat(xCellRange, numberFormat, getNumberFormats());
	}
}
