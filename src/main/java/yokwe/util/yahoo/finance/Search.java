package yokwe.util.yahoo.finance;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import yokwe.util.http.HttpUtil;
import yokwe.util.json.JSON;

public class Search {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	//
	// operation relate to download end point
	//
	
	private static final String  URL            = "https://query1.finance.yahoo.com/v1/finance/search";
	private static final Charset CHARSET_UTF_8  = StandardCharsets.UTF_8;
	
	public static class RAW {
		public static class Result {
			@JSON.Name("explains")                       @JSON.Ignore public String[] explains;
			@JSON.Name("count")                                       public int      count;
			@JSON.Name("quotes")                                      public Quote[]  quotes;
			@JSON.Name("news")                           @JSON.Ignore public String[] news;
			@JSON.Name("nav")                            @JSON.Ignore public String[] nav;
			@JSON.Name("lists")                          @JSON.Ignore public String[] lists;
			@JSON.Name("researchReports")                @JSON.Ignore public String[] researchReports;
			@JSON.Name("screenerFieldResults")           @JSON.Ignore public String[] screenerFieldResults;
			@JSON.Name("totalTime")                                   public int      totalTime;
			@JSON.Name("timeTakenForQuotes")             @JSON.Ignore public int      timeTakenForQuotes;
			@JSON.Name("timeTakenForNews")               @JSON.Ignore public int      timeTakenForNews;
			@JSON.Name("timeTakenForAlgowatchlist")      @JSON.Ignore public int      timeTakenForAlgowatchlist;
			@JSON.Name("timeTakenForPredefinedScreener") @JSON.Ignore public int      timeTakenForPredefinedScreener;
			@JSON.Name("timeTakenForCrunchbase")         @JSON.Ignore public int      timeTakenForCrunchbase;
			@JSON.Name("timeTakenForNav")                @JSON.Ignore public int      timeTakenForNav;
			@JSON.Name("timeTakenForResearchReports")    @JSON.Ignore public int      timeTakenForResearchReports;
			@JSON.Name("timeTakenForScreenerField")      @JSON.Ignore public int      timeTakenForScreenerField;
			@JSON.Name("timeTakenForCulturalAssets")     @JSON.Ignore public int      timeTakenForCulturalAssets;
			
			@Override
			public String toString() {
				return String.format("{%d  %d  %s}", count, totalTime, (quotes == null) ? "null" : Arrays.stream(quotes).toList());
			}
		}
		public static class Quote {
			@JSON.Name("symbol")                        public String     symbol;
			@JSON.Name("prevTicker")       @JSON.Ignore public String     prevTicker;
			@JSON.Name("tickerChangeDate") @JSON.Ignore public String     tickerChangeDate;
			
			@JSON.Name("quoteType")                     public String     type;
			@JSON.Name("typeDisp")       @JSON.Ignore   public String     typeDisp;
			
			@JSON.Name("exchange")                      public String     exchange;
			@JSON.Name("exchDisp")                      public String     exchDisp;
			
			@JSON.Name("shortname")      @JSON.Optional public String     shortname;
			@JSON.Name("longname")       @JSON.Optional public String     longname;
			@JSON.Name("prevName")       @JSON.Ignore   public String     prevName;
			@JSON.Name("nameChangeDate") @JSON.Ignore   public String     nameChangeDate;

			
			@JSON.Name("index")          @JSON.Ignore   public String     index;
			@JSON.Name("score")          @JSON.Ignore   public BigDecimal score;
			@JSON.Name("isYahooFinance")                public boolean    isYahooFinance;

			@JSON.Name("sector")         @JSON.Ignore   public String     sector;         // only for EQUITY
			@JSON.Name("sectorDisp")     @JSON.Optional public String     sectorDisp;     // only for EQUITY
			@JSON.Name("industry")       @JSON.Ignore   public String     industry;       // only for EQUITY
			@JSON.Name("industryDisp")   @JSON.Optional public String     industryDisp;   // only for EQUITY
			
			@JSON.Name("newListingDate") @JSON.Ignore   public String     newListingDate; // only for EQUITY
			@JSON.Name("dispSecIndFlag") @JSON.Ignore   public String     dispSecIndFlag; // only for EQUITY
			
			// dispSecIndFlag
			@Override
			public String toString() {
				return String.format("{%s  %s  \"%s\"  \"%s\"  \"%s\"  \"%s\"  \"%s\"  %s}", symbol, type, exchange, exchDisp, longname, sector, industry, isYahooFinance);
			}
		}
	}
	
	private static String getURL(String q) {
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		map.put("q",  q);
		map.put("quotesCount", "3");
		map.put("newsCount",   "0");
		map.put("listsCount",  "0");
		String queryString = map.entrySet().stream().map(o -> o.getKey() + "=" + URLEncoder.encode(o.getValue(), CHARSET_UTF_8)).collect(Collectors.joining("&"));
		
		return String.format("%s?%s", URL, queryString);
	}
	
	private static String getString(String key) {
		String url = getURL(key);
		return HttpUtil.getInstance().downloadString(url);
	}
	
	private static final Map<String, String> exchangeMap = new TreeMap<>();
	static {
		// USA
		exchangeMap.put("NYQ", "NYSE");
		exchangeMap.put("PCX", "ARCA");     // NYSE ARCA
		exchangeMap.put("ASE", "AMERICAN"); // NYSE AMERICAN

		exchangeMap.put("NAS", "NASDAQ");
		exchangeMap.put("NMS", "NASDAQ");
		exchangeMap.put("NCM", "NASDAQ");
		exchangeMap.put("NGM", "NASDAQ");
		exchangeMap.put("NIM", "NASDAQ");
		
		exchangeMap.put("BTS", "BATS");
		
		exchangeMap.put("PNK", "OTC");    // pink -- over the counter
		// JAPAN
		exchangeMap.put("JPX", "JPX");
		exchangeMap.put("OSA", "JPX");
		// INDEX
		exchangeMap.put("SNP", "S&P");
		exchangeMap.put("CXI", "CBOE");
		// EUROPE
		exchangeMap.put("FRA", "FRANKFURT");
	}
	private static String getExchange(RAW.Quote quote) {
		if (exchangeMap.containsKey(quote.exchange)) return exchangeMap.get(quote.exchange);
		logger.warn("getExchange  {}  {}", quote.exchange, quote.exchDisp);
		return null;
	}
	private static final Set<String> exchangeSet = exchangeMap.values().stream().collect(Collectors.toSet());
	public static boolean isValidExchange(String value) {
		return exchangeSet.contains(value);
	}
	
	public static Symbol getSymbol(String key) {
		String string = getString(key);
		
		RAW.Result raw = JSON.unmarshal(RAW.Result.class, string);
		if (raw.quotes == null) {
			logger.warn("raw.quotes is null");
//			logger.warn("  string  {}", string);
			return null;
		}
		if (raw.quotes.length == 0) {
//			logger.warn("raw.quotes.length is zero");
//			logger.warn("  string  {}", string);
			return null;
		}
		for(var e: raw.quotes) {
			if (e.symbol.equals(key)) {
				if (e.longname.isEmpty() && e.shortname.isEmpty()) {
					logger.warn("no longname and no shortname");
					logger.warn("  key     {}", key);
					logger.warn("  quote   {}", e);
					return null;
				}
				var name = e.longname.isEmpty() ? e.shortname : e.longname;
				var exchange = getExchange(e);
				if (exchange == null) {
					return null;
				}
				return new Symbol(e.symbol, e.type, exchange, e.sectorDisp, e.industryDisp, name);
			}
		}
		logger.warn("key not found in quotes");
		logger.warn("  key     {}", key);
		for(var e: raw.quotes) {
			logger.warn("  quote   {}", e);
		}
		return null;
	}
	
//	public static void test(String key) {
//		Symbol symbol = getSymbol(key);
//		logger.info("symbol  {}  --  {}", key, symbol);
//	}
//	public static void main(String[] args) {
//		logger.info("START");
//		
//		// US - STOCK ETF
////		test("QQQ");  // NASDAQ
////		test("IBM");  // NYSE
////		test("ZECP"); // BATS
//		
//		// INDEX
////		test("^N225");
////		test("^GSPC");
////		test("^NDX");
////		test("^VIX");
//		
//		// FUND
////		test("JP3027710007"); // JPX ETF
////		test("IE0030804631"); // OTC MUTUALFUND
////		test("LU0159489490"); // FRANKFURT
//		
//		// JPX - STOCK ETF
////		test("1301.T");
////		test("JP3257200000"); // 1301.T
////		test("JP3048810000"); // 2971.T
//		test("2593.T");  // ITO EN
//		test("2593P.T"); // ITO EN PREF
//		
//		logger.info("STOP");
//	}

}
