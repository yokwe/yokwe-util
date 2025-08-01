package yokwe.util.yahoo.finance;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import yokwe.util.CSVUtil;
import yokwe.util.http.HttpUtil;

public class Download {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	//
	// operation relate to download end point
	//

	private static final String  URL            = "https://query1.finance.yahoo.com/v7/finance/download/";
	private static final Charset CHARSET_UTF_8  = StandardCharsets.UTF_8;
	
	public static class RAW {
		public static class Price {
			@CSVUtil.ColumnName("Date")       LocalDate  date;
			@CSVUtil.ColumnName("Open")       String     open;
			@CSVUtil.ColumnName("High")       String     high;
			@CSVUtil.ColumnName("Low")        String     low;
			@CSVUtil.ColumnName("Close")      String     close;
			@CSVUtil.ColumnName("Adj Close")  String     adjClose;
			@CSVUtil.ColumnName("Volume")     String     volume;
		}
		public static class Dividend {
			@CSVUtil.ColumnName("Date")          LocalDate  date;
			@CSVUtil.ColumnName("Dividends")     BigDecimal amount;
		}
		public static class Split {
			@CSVUtil.ColumnName("Date")          LocalDate  date;
			@CSVUtil.ColumnName("Stock Splits")  String     detail; // FIXME after check format, change type
		}
		public static class CapitalGain {
			@CSVUtil.ColumnName("Date")          LocalDate  date;
			@CSVUtil.ColumnName("Capital Gains") String     detail;  // FIXME after check format, change type
		}

	}
		
	public enum Interval {
		DAILY("1d"),
		WEEKLY("1wk"),
		MONTHLY("1mo");
		
		public final String value;
		private Interval(String value) {
			this.value = value;
		}
	}
	public enum Events {
		PRICE    ("history"), // historical price
		DIVIDEND ("div"),
		SPLIT    ("split"),
		CAPITAL  ("capitalGain");
		
		public final String value;
		
		private Events(String value) {
			this.value = value;
		}

	}

	private static String getURL(String symbol, LocalDate period1, LocalDate period2, Interval interval, Events events) {
		String url = URL + symbol;
		
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("period1",  String.valueOf(period1.atStartOfDay().toEpochSecond(ZoneOffset.UTC)));
		map.put("period2",  String.valueOf(period2.atStartOfDay().toEpochSecond(ZoneOffset.UTC)));
		map.put("interval", interval.value);
		map.put("events",   events.value);
		map.put("includeAdjustedClose", "true");
		String queryString = map.entrySet().stream().map(o -> o.getKey() + "=" + URLEncoder.encode(o.getValue(), CHARSET_UTF_8)).collect(Collectors.joining("&"));
		
		return String.format("%s?%s", url, queryString);
	}
	
	private static String getString(String symbol, LocalDate period1, LocalDate period2, Interval interval, Events event) {
		String url = getURL(symbol, period1, period2, interval, event);
		return HttpUtil.getInstance().downloadString(url);
	}
		
	public static List<Price> getPrice(String symbol, LocalDate period1, LocalDate period2, Interval interval) {
		String string = getString(symbol, period1, period2, interval, Events.PRICE);
		if (string == null) return null;
		
		List<Price> list = new ArrayList<>();
		{
			List<RAW.Price> rawList = CSVUtil.read(RAW.Price.class).file(new StringReader(string));
			
			Price[] array = new Price[rawList.size()];
			int index = 0;
			for(var e: rawList) {
				BigDecimal open   = e.open.equals("null")   ? null : new BigDecimal(e.open);
				BigDecimal high   = e.high.equals("null")   ? null : new BigDecimal(e.high);
				BigDecimal low    = e.low.equals("null")    ? null : new BigDecimal(e.low);
				BigDecimal close  = e.close.equals("null")  ? null : new BigDecimal(e.close);
				long       volume = e.volume.equals("null") ? -1   : Long.valueOf(e.volume);
				
				array[index++] = new Price(e.date, open, high, low, close, volume);
			}
			//
			int startIndex = 0;
			for(int i = 0; i < array.length; i++) {
				if (array[i].open == null) {
					startIndex++;
					continue;
				}
				break;
			}
			//
			int stopIndexPlusOne = array.length;
			for(int i = array.length - 1; 0 <= i; i--) {
				if (array[i].open == null) {
					stopIndexPlusOne--;
					continue;
				}
				break;
			}
			
			BigDecimal open   = array[startIndex].open;
			BigDecimal high   = array[startIndex].high;
			BigDecimal low    = array[startIndex].low;
			BigDecimal close  = array[startIndex].close;
			for(int i = startIndex; i < stopIndexPlusOne; i++) {
				Price price = array[i];
				if (price.open  == null) price.open   = open;
				if (price.high  == null) price.high   = high;
				if (price.low   == null) price.low    = low;
				if (price.close == null) price.close  = close;
				if (price.volume == -1)  price.volume = 0;
				
				if (price.open == null || price.high == null || price.low == null || price.close == null || price.volume == -1) {
					logger.warn("XXXX  {}", price);
					continue;
				}
				
				list.add(price);
			}
		}
		
		return list;
	}
	public static List<Price> getPrice(String symbol, LocalDate period1, LocalDate period2) {
		return getPrice(symbol, period1, period2, Interval.DAILY);
	}
	
	public static List<Dividend> getDividend(String symbol, LocalDate period1, LocalDate period2, Interval interval) {
		String string = getString(symbol, period1, period2, interval, Events.DIVIDEND);
		if (string == null) return null;	

		List<Dividend> list = new ArrayList<>();
		{
			List<RAW.Dividend> rawList = CSVUtil.read(RAW.Dividend.class).file(new StringReader(string));
			
			for(var e: rawList) {
				BigDecimal amount = e.amount.stripTrailingZeros();
				list.add(new Dividend(e.date, amount));
			}
		}
		
		return list;
	}
	public static List<Dividend> getDividend(String symbol, LocalDate period1, LocalDate period2) {
		return getDividend(symbol, period1, period2, Interval.DAILY);
	}
	
	public static List<Split> getSplit(String symbol, LocalDate period1, LocalDate period2, Interval interval) {
		String string = getString(symbol, period1, period2, interval, Events.SPLIT);
		if (string == null) return null;	

		List<Split> list = new ArrayList<>();
		{
			List<RAW.Split> rawList = CSVUtil.read(RAW.Split.class).file(new StringReader(string));
			
			for(var e: rawList) {
				list.add(new Split(e.date, e.detail));
			}
		}
		
		return list;
	}
	public static List<Split> getSplit(String symbol, LocalDate period1, LocalDate period2) {
		return getSplit(symbol, period1, period2, Interval.DAILY);
	}
	
	public static List<CapitalGain> getCapitalGain(String symbol, LocalDate period1, LocalDate period2, Interval interval) {
		String string = getString(symbol, period1, period2, interval, Events.CAPITAL);
		if (string == null) return null;	
		
		List<CapitalGain> list = new ArrayList<>();
		{
			List<RAW.CapitalGain> rawList = CSVUtil.read(RAW.CapitalGain.class).file(new StringReader(string));
			
			for(var e: rawList) {
				list.add(new CapitalGain(e.date, e.detail));
			}
		}
		
		return list;
	}
	public static List<CapitalGain> getCapitalGain(String symbol, LocalDate period1, LocalDate period2) {
		return getCapitalGain(symbol, period1, period2, Interval.DAILY);
	}
	
	
//	static void testPrice() {
//		String methodName = ClassUtil.getCallerMethodName();
//		
//		String symbol = "AMZN";
//		int year = 2023;
//		int month = 6;
//		LocalDate period1 = LocalDate.of(year, month, 01);
//		LocalDate period2 = LocalDate.of(year, month, 10);
//		LocalDate period3 = LocalDate.of(year, month, 20);
//		
//		var list12 = getPrice(symbol, period1, period2);
//		var list23 = getPrice(symbol, period2, period3);
//		
//		for(var e: list12) {
//			logger.info("{}  list12  {}  {}", methodName, symbol, e);
//		}
//		for(var e: list23) {
//			logger.info("{}  list23  {}  {}", methodName, symbol, e);
//		}
//	}
//	
//	static void testDividend() {
//		String methodName = ClassUtil.getCallerMethodName();
//
//		String symbol = "QQQ";
//		LocalDate period1 = LocalDate.of(2000, 1, 1);
//		LocalDate period2 = LocalDate.now();
//		
//		var list12 = getDividend(symbol, period1, period2);
//		
//		for(var e: list12) {
//			logger.info("{}  list12  {}  {}", methodName, symbol, e);
//		}
//	}
//	
//	static void testSplit() {
//		String methodName = ClassUtil.getCallerMethodName();
//
//		// CRF 2008-12-23 1:2
//		// CRF 2014-12-29 1:4
//		// AMZN 2022-06-06 20:1
//		String symbol = "AMZN";
//		int year = 2022;
//		int month = 5;
//		LocalDate period1 = LocalDate.of(year, month + 0, 1);
//		LocalDate period2 = LocalDate.of(year, month + 1, 1);
//		LocalDate period3 = LocalDate.of(year, month + 2, 1);
//		
//		var list12 = getSplit(symbol, period1, period2);
//		var list23 = getSplit(symbol, period2, period3);
//		
//		for(var e: list12) {
//			logger.info("{}  list12  {}  {}", methodName, symbol, e);
//		}
//		for(var e: list23) {
//			logger.info("{}  list23  {}  {}", methodName, symbol, e);
//		}
//	}
//	static void testCapitalGain() {
//		String methodName = ClassUtil.getCallerMethodName();
//		
//		String symbol = "CRF";
//		LocalDate period1 = LocalDate.of(1980, 1, 1);
//		LocalDate period2 = LocalDate.now();
//		
//		var list12 = getCapitalGain(symbol, period1, period2);
//		
//		for(var e: list12) {
//			logger.info("{}  list12  {}  {}", methodName, symbol, e);
//		}
//	}
//	public static void main(String[] args) {
//		logger.info("START");
//		
//		testPrice();
//		testDividend();
//		testSplit();
//		testCapitalGain();
//		
//		logger.info("STOP");
//	}
}
