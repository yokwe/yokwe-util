package yokwe.util;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class MarketHoliday {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static LocalDate goodFriday(int year) {
	    int g = year % 19;
	    int c = year / 100;
	    int h = (c - (int)(c / 4) - (int)((8 * c + 13) / 25) + 19 * g + 15) % 30;
	    int i = h - (int)(h / 28) * (1 - (int)(h / 28) * (int)(29 / (h + 1)) * (int)((21 - g) / 11));

	    int day   = i - ((year + (int)(year / 4) + i + 2 - c + (int)(c / 4)) % 7) + 28;
	    int month = 3;

	    if (31 < day) {
	        month++;
	        day -= 31;
	    }

	    return LocalDate.of(year, month, day).minusDays(2);
	}
	
	public static class Data {
		public String event;
		public String date;
		public String start;
		public String end;
		
		public Data(String event, String date, String start, String end) {
			this.event   = event;
			this.date    = date;
			this.start   = start;
			this.end     = end;
		}
		public Data() {
			event   = "";
			date    = "";
			start   = "";
			end     = "";
		}
		
		public Data(Data that) {
			this.event   = that.event;
			this.date    = that.date;
			this.start   = that.start;
			this.end     = that.end;
		}
		public Data(Data that, String event) {
			this.event   = event;
			this.date    = that.date;
			this.start   = that.start;
			this.end     = that.end;
		}
		
		@Override
		public String toString() {
			return String.format("%s %s %s %s",
				event, date, (start.isEmpty() ? "-" : start), (end.isEmpty() ? "-" : end));
		}
	}
	
	protected final int yearStartDefault;
	protected final int yearEndDefault;
	
	protected final Map<LocalDate, Data> marketHolidayMap;
	
	private void buildMarketHolidayMap(String path) {
		Pattern pat_YYYY_MM_DD  = Pattern.compile("^(20[0-9]{2})-([01]?[0-9])-([0-3]?[0-9])$");
		Pattern pat_MM_DD       = Pattern.compile("^([01]?[0-9])-([0-3]?[0-9])$");
		Pattern pat_MM_DDM      = Pattern.compile("^([01]?[0-9])-([0-4])M$");
		Pattern pat_MM_DDT      = Pattern.compile("^([01]?[0-9])-([0-4])T$");
		Pattern pat_MM_LM       = Pattern.compile("^([01]?[0-9])-LM$");
		Pattern pat_YYYY        = Pattern.compile("^(20[0-9]{2})$");
		Pattern pat_GOOD_FRIDAY = Pattern.compile("^GOOD_FRIDAY$");

		DateTimeFormatter format_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-M-d");

		List<Data> dataList = CSVUtil.read(Data.class).withCharset(StandardCharsets.UTF_8).file(Data.class, path);
		
		for(Data data: dataList) {
			if (data.event.length() == 0) continue;
			
			final int yearStart;
			final int yearEnd;
			
			if (data.start.length() != 0) {
				var m = pat_YYYY.matcher(data.start);
				if (m.matches()) {
					yearStart = Integer.parseInt(data.start);
				} else {
					logger.error("Unexpected start {}", data);
					throw new UnexpectedException("Unexpected");
				}
			} else {
				yearStart = yearStartDefault;
			}
			if (data.end.length() != 0) {
				var m = pat_YYYY.matcher(data.end);
				if (m.matches()) {
					yearEnd = Integer.parseInt(data.end);
				} else {
					logger.error("Unexpected start {}", data);
					throw new UnexpectedException("Unexpected");
				}
			} else {
				yearEnd = yearEndDefault;
			}
			
			Matcher m;
			
			m = pat_YYYY_MM_DD.matcher(data.date);
			if (m.matches()) {
				LocalDate date = LocalDate.parse(data.date, format_YYYY_MM_DD);
				marketHolidayMap.put(date, data);
				continue;
			}

			m = pat_MM_DD.matcher(data.date);
			if (m.matches()) {
				if (m.groupCount() != 2) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				int mm = Integer.parseInt(m.group(1));
				int dd = Integer.parseInt(m.group(2));
				
				for(int yyyy = yearStart; yyyy <= yearEnd; yyyy++) {
					LocalDate date = LocalDate.of(yyyy, mm, dd);
					marketHolidayMap.put(date, data);
				}
				continue;
			}

			m = pat_MM_DDM.matcher(data.date);
			if (m.matches()) {
				if (m.groupCount() != 2) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				int mm = Integer.parseInt(m.group(1));
				int dd = Integer.parseInt(m.group(2));
				
				if (mm < 1 || 12 < mm) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				if (dd < 1 || 4 < dd) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				for(int yyyy = yearStart; yyyy <= yearEnd; yyyy++) {
					LocalDate firstDateOfMonth = LocalDate.of(yyyy, mm, 1);
					LocalDate firstMonday = firstDateOfMonth.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
					LocalDate date = firstMonday.plusDays((dd - 1) * 7);
					marketHolidayMap.put(date, data);
				}
				continue;
			}
			
			m = pat_MM_DDT.matcher(data.date);
			if (m.matches()) {
				if (m.groupCount() != 2) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				int mm = Integer.parseInt(m.group(1));
				int dd = Integer.parseInt(m.group(2));
				
				if (mm < 1 || 12 < mm) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				if (dd < 1 || 4 < dd) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				for(int yyyy = yearStart; yyyy <= yearEnd; yyyy++) {
					LocalDate firstDateOfMonth = LocalDate.of(yyyy, mm, 1);
					LocalDate firstThrusday = firstDateOfMonth.with(TemporalAdjusters.firstInMonth(DayOfWeek.THURSDAY));
					LocalDate date = firstThrusday.plusDays((dd - 1) * 7);
					marketHolidayMap.put(date, data);
				}
				continue;
			}
			
			m = pat_MM_LM.matcher(data.date);
			if (m.matches()) {
				if (m.groupCount() != 1) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				
				int mm = Integer.parseInt(m.group(1));
				
				if (mm < 1 || 12 < mm) {
					logger.error("Unexpected date format {}", data);
					throw new UnexpectedException("Unexpected");
				}
				// last Monday
				for(int yyyy = yearStart; yyyy <= yearEnd; yyyy++) {
					LocalDate firstDateOfMonth = LocalDate.of(yyyy, mm, 1);
					LocalDate lastMonday = firstDateOfMonth.with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
					
					marketHolidayMap.put(lastMonday, data);
				}
				continue;
			}

			m = pat_GOOD_FRIDAY.matcher(data.date);
			if (m.matches()) {
				for(int yyyy = yearStart; yyyy <= yearEnd; yyyy++) {
					LocalDate goodFriday = goodFriday(yyyy);
					
					marketHolidayMap.put(goodFriday, data);
				}
				continue;
			}
			
			logger.error("Unexpected date format {}", data);
			throw new UnexpectedException("Unexpected");
		}
		
		// Observed
		processObserved();
		
		// remove out of range entry
		for(var i = marketHolidayMap.entrySet().iterator(); i.hasNext();) {
			var e = i.next();
			var key = e.getKey();
			int year = key.getYear();
			if (year < yearStartDefault || yearEndDefault < year) i.remove();
		}
		
		logger.info("holidayMap {} {} {}", yearStartDefault, yearEndDefault, marketHolidayMap.size());
	}
	
	protected abstract void processObserved();
	
	private MarketHoliday(int yearStartDefault, int yearEndDefault, String path) {
		this.yearStartDefault = yearStartDefault;
		this.yearEndDefault   = yearEndDefault;
		this.marketHolidayMap = new TreeMap<>();
		
		buildMarketHolidayMap(path);
	}
	
	protected void add(LocalDate date, String event) {
		DateTimeFormatter format = DateTimeFormatter.ofPattern("M-d");

		int year = date.getYear();
		if (yearStartDefault <= year && year <= yearEndDefault) {
			Data data = new Data(event, date.format(format), Integer.toString(year), Integer.toString(year));
			marketHolidayMap.put(date, data);
		} else {
			logger.error("Unexpected date");
			logger.error("  date       {}", date.toString());
			logger.error("  YEAR_START {}", yearStartDefault);
			logger.error("  YEAR_END   {}", yearEndDefault);
			throw new UnexpectedException("Unexpected date");
		}
	}
	
	public boolean isMarketHoliday(LocalDate date) {
		int year = date.getYear();
		if (yearStartDefault <= year && year <= yearEndDefault) {
			return marketHolidayMap.containsKey(date);
		} else {
			logger.error("Unexpected date");
			logger.error("  date       {}", date.toString());
			logger.error("  YEAR_START {}", yearStartDefault);
			logger.error("  YEAR_END   {}", yearEndDefault);
			throw new UnexpectedException("Unexpected date");
		}
	}	
	public boolean isWeekend(LocalDate date) {
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		switch(dayOfWeek) {
		case SATURDAY:
		case SUNDAY:
			return true;
		default:
			return false;
		}
	}
	public boolean isClosed(LocalDate date) {
		if (isWeekend(date))       return true;
		if (isMarketHoliday(date)) return true;
		return false;
	}

	protected static final int YEAR_START_DEFAULT = 2015;
	protected static final int YEAR_END_DEFAULT   = Year.now().getValue() + 1;
	
	public static class JP {
		public static final LocalTime MARKET_OPEN_TIME  = LocalTime.of(9, 0);
		public static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 0);
		
		private static final String PATH_MARKET_HOLIDAY = "/yokwe/util/market-holiday-jp.csv";
		private static final ZoneId ZONE_ID             = ZoneId.of("Asia/Tokyo");
		
		private static final MarketHoliday marketHoliday = new MyMarketHoliday();
		private static class MyMarketHoliday extends MarketHoliday {
			private MyMarketHoliday() {
				super(YEAR_START_DEFAULT, YEAR_END_DEFAULT, PATH_MARKET_HOLIDAY);
				
				// There is no observed holiday for 1/2 1/3 12/31
				for(int year = yearStartDefault; year <= yearEndDefault; year++) {
					LocalDate date0102 = LocalDate.parse(year + "-01-02");
					LocalDate date0103 = LocalDate.parse(year + "-01-03");
					LocalDate date1231 = LocalDate.parse(year + "-12-31");
					
					if (!isMarketHoliday(date0102)) add(date0102, "1月2日");
					if (!isMarketHoliday(date0103)) add(date0103, "1月3日");
					if (!isMarketHoliday(date1231)) add(date1231, "大晦日");
				}
			}
			
			protected void processObserved() {
				Map<LocalDate, Data> observedMap = new TreeMap<>();

				for(var i = marketHolidayMap.entrySet().iterator(); i.hasNext();) {
					var entry = i.next();
					var date  = entry.getKey();
					var data  = entry.getValue();
					
					if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
						var observedDate = date.plusDays(0);
						for(;;) {
							observedDate = observedDate.plusDays(1);
							if (marketHolidayMap.containsKey(observedDate)) continue;
							break;
						}
						var observedData = new Data(data, String.format("%s - 振替休日", data.event));
						
//						logger.info("Observed  {}  {}  {}", date, observedDate, observedData);
						observedMap.put(observedDate, observedData);
						i.remove();
					}					
				}
				marketHolidayMap.putAll(observedMap);
			}
		}
		
		private static LocalDate lastTradingDate = null;
		
		public static LocalDate getLastTradingDate() {
			if (lastTradingDate == null) {
				LocalDateTime now  = LocalDateTime.now(ZONE_ID);
				LocalDate     date = now.toLocalDate();
				LocalTime     time = now.toLocalTime();
				
				if (time.isBefore(MARKET_OPEN_TIME)) date = date.minusDays(1); // Move to yesterday if it is before market open

				if (marketHoliday.isClosed(date)) {
					date = getPreviousTradingDate(date);
				}
				
				lastTradingDate = date;
				logger.info("Last Trading Date {}", lastTradingDate);
			}
			return lastTradingDate;
		}
		public static LocalDate getNextTradingDate(LocalDate date) {
			date = date.plusDays(1);
			while(marketHoliday.isClosed(date)) {
				date = date.plusDays(1);
			}
			return date;
		}
		public static LocalDate getPreviousTradingDate(LocalDate date) {
			date = date.minusDays(1);
			while(marketHoliday.isClosed(date)) {
				date = date.minusDays(1);
			}
			return date;
		}
		public static boolean isClosed(LocalDate date) {
			return marketHoliday.isClosed(date);
		}
		public static boolean isMarketHoliday(LocalDate date) {
			return marketHoliday.isMarketHoliday(date);
		}
		public static boolean isWeekend(LocalDate date) {
			return marketHoliday.isWeekend(date);
		}
		
		public static boolean isClosed(String string) {
			return isClosed(LocalDate.parse(string));
		}
	}
	
	public static class US {
		public static final LocalTime MARKET_OPEN_TIME  = LocalTime.of(9, 30);
		public static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(16, 0);

		private static final String PATH_MARKET_HOLIDAY = "/yokwe/util/market-holiday-us.csv";
		private static final ZoneId ZONE_ID             = ZoneId.of("America/New_York");
		
		private static final MarketHoliday marketHoliday = new MyMarketHoliday();
		private static class MyMarketHoliday extends MarketHoliday {
			private MyMarketHoliday() {
				super(YEAR_START_DEFAULT, YEAR_END_DEFAULT, PATH_MARKET_HOLIDAY);
			}
			
			protected void processObserved() {
				Map<LocalDate, Data> observedMap = new TreeMap<>();

				for(var i = marketHolidayMap.entrySet().iterator(); i.hasNext();) {
					var entry = i.next();
					var date  = entry.getKey();
					var data  = entry.getValue();
					
					int adjust = 0;
					switch(date.getDayOfWeek()) {
					case SATURDAY:
						adjust = -1;
						break;
					case SUNDAY:
						adjust = 1;
						break;
					default:
						break;
					}
					if (adjust != 0) {
						var observedDate = date.plusDays(adjust);
						var observedData = new Data(data, String.format("%s - Observed", data.event));
						if (observedDate.getMonthValue() == 12 && observedDate.getDayOfMonth() == 31) {
							// See Rule 7.2 Holidays
							// https://nyseguide.srorules.com/rules/document?treeNodeId=csh-da-filter!WKUS-TAL-DOCS-PHC-%7B4A07B716-0F73-46CC-BAC2-43EB20902159%7D--WKUS_TAL_19401%23teid-15
							//
							//   The Exchange will not be open for business on New Year's Day, Martin Luther King Jr. Day,
							//   Presidents' Day, Good Friday, Memorial Day, Independence Day, Labor Day, Thanksgiving Day and Christmas Day.
							//
							//   When a holiday observed by the Exchange falls on a Saturday, the Exchange will not be open for business
							//   on the preceding Friday and when any holiday observed by the Exchange falls on a Sunday,
							//   the Exchange will not be open for business on the succeeding Monday, unless unusual business conditions exist,
							//   such as the ending of a monthly or yearly accounting period.
							i.remove();
						} else {
//							logger.info("Observed  {}  {}  {}", date, observedDate, observedData);
							observedMap.put(observedDate, observedData);
							i.remove();
						}
						
					}
				}
				marketHolidayMap.putAll(observedMap);
			}
		}

		private static LocalDate lastTradingDate = null;
		
		public static LocalDate getLastTradingDate() {
			if (lastTradingDate == null) {
				LocalDateTime now  = LocalDateTime.now(ZONE_ID);
				LocalDate     date = now.toLocalDate();
				LocalTime     time = now.toLocalTime();
				
				if (time.isBefore(MARKET_OPEN_TIME)) date = date.minusDays(1); // Move to yesterday if it is before market open
				
				if (marketHoliday.isClosed(date)) {
					date = getPreviousTradingDate(date);
				}
				
				lastTradingDate = date;
				logger.info("Last Trading Date {}", lastTradingDate);
			}
			return lastTradingDate;
		}
		public static LocalDate getNextTradingDate(LocalDate date) {
			date = date.plusDays(1);
			while(marketHoliday.isClosed(date)) {
				date = date.plusDays(1);
			}
			return date;
		}
		public static LocalDate getPreviousTradingDate(LocalDate date) {
			date = date.minusDays(1);
			while(marketHoliday.isClosed(date)) {
				date = date.minusDays(1);
			}
			return date;
		}
		public static boolean isClosed(LocalDate date) {
			return marketHoliday.isClosed(date);
		}
		public static boolean isMarketHoliday(LocalDate date) {
			return marketHoliday.isMarketHoliday(date);
		}
		public static boolean isWeekend(LocalDate date) {
			return marketHoliday.isWeekend(date);
		}

		public static boolean isClosed(String string) {
			return isClosed(LocalDate.parse(string));
		}
	}

	public static class HolidayDetail implements Comparable<HolidayDetail> {
		public String date;
		public String name;
		
		public HolidayDetail(String date, String name) {
			this.date = date;
			this.name = name;
		}
		public HolidayDetail() {
			this("", "");
		}
		
		@Override
		public String toString() {
			return ToString.withFieldName(this);
		}
		
		@Override
		public int compareTo(HolidayDetail that) {
			return this.date.compareTo(that.date);
		}
	}
	
	public static void main(String[] args) {
		logger.info("START");
		
		{
			logger.info("MARKET HOLIDAY JP");
			var list = new ArrayList<HolidayDetail>();
			
			JP.isClosed(LocalDate.of(2000, 1, 1));
			
			for(var entry: JP.marketHoliday.marketHolidayMap.entrySet()) {
				logger.info("{}  {}", entry.getKey(), entry.getValue().event);
				list.add(new HolidayDetail(entry.getKey().toString(), entry.getValue().event));
			}
			CSVUtil.write(HolidayDetail.class).file("tmp/market-holiday-jp.csv", list);
		}
		{
			logger.info("MARKET HOLIDAY US");
			var list = new ArrayList<HolidayDetail>();
			
			for(var entry: US.marketHoliday.marketHolidayMap.entrySet()) {
				logger.info("{}  {}", entry.getKey(), entry.getValue().event);
				list.add(new HolidayDetail(entry.getKey().toString(), entry.getValue().event));
			}
			CSVUtil.write(HolidayDetail.class).file("tmp/market-holiday-us.csv", list);
		}
		
		logger.info("END");
	}

}
