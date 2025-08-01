package yokwe.util;

public class StringUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	//
	// removeBOM
	//
	public static final char BOM_CHAR_BE = '\uFEFF';
	public static final char BOM_CHAR_LE = '\uFFFE';
	public static boolean isBOM(char c) {
		return c == BOM_CHAR_BE || c == BOM_CHAR_LE;
	}
	public static String removeBOM(String string) {
		// handle special case first
		if (string.isEmpty()) return string;
		
		return (isBOM(string.charAt(0))) ? string.substring(1) : string;
	}
	
	//
	// toHexString
	//
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String toHexString(byte[] bytes) {
	    char[] charArray = new char[bytes.length * 2];
	    for (int i = 0; i < bytes.length; i++) {
	        int b = bytes[i] & 0xFF;
	        charArray[i * 2 + 0] = HEX_ARRAY[(b >>> 4) & 0x0F];
	        charArray[i * 2 + 1] = HEX_ARRAY[(b >>> 0) & 0x0F];
	    }
	    return new String(charArray);
	}
	public static String toHexString(String string) {
	    char[] charArray = new char[string.length() * 4];
		for(int i = 0; i < string.length(); i++) {
			int c = string.charAt(i) & 0xFFFF;
			charArray[i * 4 + 0] = HEX_ARRAY[(c >>> 12) & 0x0F];
			charArray[i * 4 + 1] = HEX_ARRAY[(c >>>  8) & 0x0F];
			charArray[i * 4 + 2] = HEX_ARRAY[(c >>>  4) & 0x0F];
			charArray[i * 4 + 4] = HEX_ARRAY[(c >>>  0) & 0x0F];
		}
		return "(" + string.length() + ")" + new String(charArray);
	}

	//
	// replaceCharacter
	//
	public static String replaceCharacter(String fromString, String toString, String string) {
		// sanity check
		{
			if (fromString.length() != toString.length()) {
				logger.error("length not equal");
				logger.error("  from {}  !{}!", fromString.length(), fromString);
				logger.error("  to   {}  !{}!", toString.length(), toString);
				throw new UnexpectedException("length not equal");
			}				
		}
		
		StringBuilder result = new StringBuilder(string.length());
		
		for(var c: string.toCharArray()) {
			var index = fromString.indexOf(c);
			if (index == -1) {
				result.append(c);
			} else {
				result.append(toString.charAt(index));
			}
		}
		
		return result.toString();
	}
	private static final String HALFWIDTH_STRING = "" +
			"ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
			"abcdefghijklmnopqrstuvwxyz" +
			"`1234567890-=" +
			"~!@#$%^&*()_+" +
			"[]\\{}|" +
			";':\"" +
			",./<>?" +
			"ｱｲｳｴｵ" +
			"ｶｷｸｹｵ" +
			"ｻｼｽｾﾄ" + 
			"ﾀﾁﾂﾃﾄ" +
			"ﾅﾆﾇﾈﾉ" +
			"ﾊﾋﾌﾍﾎ" +
			"ﾏﾐﾑﾒﾓ" +
			"ﾔﾕﾖ" +
			"ﾗﾘﾙﾚﾛ" +
			"ﾜｦﾝ" +
			" ";
	private static final String FULLWIDTH_STRING = "" +
			"ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ" +
			"ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ" +
			"｀１２３４５６７８９０ー＝" +
			"〜！＠＃＄％＾＆＊（）＿＋" +
			"「」￥『』｜" +
			"；’：”" +
			"、。・＜＞？" +
			"アイウエオ" +
			"カキクケコ" +
			"サシスセソ" +
			"タチツテト" +
			"ナニヌネノ" +
			"ハヒフヘホ" +
			"マミムメモ" +
			"ヤユヨ" +
			"ラリルレロ" +
			"ワヲン" +
			"　";
	
	//
	// toFullWidth
	//
	public static String toFullWidth(String string) {
		return replaceCharacter(HALFWIDTH_STRING, FULLWIDTH_STRING, string);
	}
	//
	// toHalfWidth
	//
	public static String toHalfWidth(String string) {
		return replaceCharacter(HALFWIDTH_STRING, FULLWIDTH_STRING, string);
	}
}
