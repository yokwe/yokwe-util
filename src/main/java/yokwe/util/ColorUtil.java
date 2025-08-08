package yokwe.util;

import java.util.Arrays;

public class ColorUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static RGB[] MONO_N2 = RGB.fromString(
		"#000000", "#ffffff");
	public static RGB[] RG_N2 = RGB.fromString(
		"#00FF00", "#ff0000");

	public static RGB[] SET3_N3 = RGB.fromString(
		"#8dd3c7", "#ffffb3", "#bebada");
		
	public static RGB[] SET3_N4 = RGB.fromString(
		"#8dd3c7", "#ffffb3", "#bebada", "#fb8072");
	
	// https://colorbrewer2.org/#type=qualitative&scheme=Paired&n=12
	public static RGB[] SET3_N12 = RGB.fromString(
		"#a6cee3", "#1f78b4", "#b2df8a",
		"#33a02c", "#fb9a99", "#e31a1c",
		"#fdbf6f", "#ff7f00", "#cab2d6",
		"#6a3d9a", "#ffff99", "#b15928");
	
	public static RGB[] PAIRED_N12 = RGB.fromString(
		"#a6cee3", "#1f78b4", "#b2df8a",
		"#33a02c", "#fb9a99", "#e31a1c",
		"#fdbf6f", "#ff7f00", "#cab2d6",
		"#6a3d9a", "#ffff99", "#b15928");
	
	
	public static RGB interpolate(RGB[] palette, int indexTotal, int index) {
		return interpolate(palette, indexTotal)[index];
	}
	
	public static class RGB {
		public final double r; // 0.0 - 1.0
		public final double g; // 0.0 - 1.0
		public final double b; // 0.0 - 1.0
		
		public RGB(double r, double g, double b) {
			this.r = r;
			this.g = g;
			this.b = b;
			
			// sanity check
			if (r < 0.0 || 1.0 < r || g < 0.0 || 1.0 < g || b < 0.0 || 1.0 < b) {
				logger.error("Unexpected value");
				logger.error("  r  g  b  {}!", r, g, b);
				throw new UnexpectedException("Unexpected value");
			}
		}
		public RGB(RGB that) {
			this.r = that.r;
			this.g = that.g;
			this.b = that.b;
		}
		
		public static RGB fromString(String string) {
			// #rgb
			if (string.length() == 4 && string.charAt(0) == '#') {
				var value = Integer.valueOf(string.substring(1), 16);
				
				int bits        = 4;
				int mask        = (1 << bits) - 1;
				var denominator = (double)mask;
				var r = ((value >> (bits * 2)) & mask) / denominator;
				var g = ((value >> (bits * 1)) & mask) / denominator;
				var b = ((value >> (bits * 0)) & mask) / denominator;
				return new RGB(r, g, b);
			}
			// #rrggbb
			if (string.length() == 7 && string.charAt(0) == '#') {
				var value = Integer.valueOf(string.substring(1), 16);
				
				int bits        = 8;
				int mask        = (1 << bits) - 1;
				var denominator = (double)mask;
				var r = ((value >> (bits * 2)) & mask) / denominator;
				var g = ((value >> (bits * 1)) & mask) / denominator;
				var b = ((value >> (bits * 0)) & mask) / denominator;
				return new RGB(r, g, b);
			}
			logger.error("Unexpected string");
			logger.error("  string  {}!", string);
			throw new UnexpectedException("Unexpected string");
		}
		public static RGB[] fromString(String... strings) {
			return Arrays.stream(strings).map(o -> fromString(o)).toArray(RGB[]::new);
		}
		
		@Override
		public String toString() {
			int bits        = 8;
			int mask        = (1 << bits) - 1;
			var denominator = (double)mask;

			return String.format("#%02x%02x%02x", (int)(r * denominator), (int)(g * denominator), (int)(b * denominator));
		}
	}
	
	public static RGB interpolate(RGB a, RGB b, double ratioOfA) {
		// sanity check
		if (ratioOfA < 0.0 || 1.0 < ratioOfA) {
			logger.error("Unexpected ratioOfA");
			logger.error("  ratioOfA  {}!", ratioOfA);
			throw new UnexpectedException("Unexpected ratioOfA");
		}
		var ratioOfB = 1.0 - ratioOfA;
		var newR = a.r * ratioOfA + b.r * ratioOfB;
		var newB = a.b * ratioOfA + b.b * ratioOfB;
		var newG = a.g * ratioOfA + b.g * ratioOfB;
		return new RGB(newR, newG, newB);
	}
	
	public static RGB[] interpolate(RGB[] palette, int newPaletteSize) {
		// sanity check
		if (newPaletteSize <= 1) {
			logger.error("Unexpected newPaletteSize");
			logger.error("  newPaletteSize  {}!", newPaletteSize);
			throw new UnexpectedException("Unexpected newPaletteSize");
		}
		if (palette.length <= 1) {
			logger.error("Unexpected array.length");
			logger.error("  array.length  {}!", palette.length);
			throw new UnexpectedException("Unexpected array.length");
		}
		
		int oldPaletteSize = palette.length;
		RGB[] ret = new RGB[newPaletteSize];
		ret[0] = palette[0];
		ret[newPaletteSize - 1] = palette[palette.length - 1];
		
		logger.info("XX  oldPaletteSize  {}  newPaletteSize  {}", oldPaletteSize, newPaletteSize);

		if (oldPaletteSize == newPaletteSize) {
			// special case  -- same size
			for(int i = 0; i < oldPaletteSize; i++) {
				ret[i] = new RGB(palette[i]);
			}
		} else {
			double oldDelta = 1.0 / (oldPaletteSize - 1);
			double newDelta = 1.0 / (newPaletteSize - 1);
			
			for(var i = 0; i < newPaletteSize; i++) {
				var newPos  = newDelta * i;
				var oldIndex = (int)((newPos / oldDelta) + 0.5);
//				var oldIndex = (newPos / oldD) + 0.5;
				var oldPos  = oldIndex * oldDelta;
				var diffPos = newPos - oldPos;
				
//				logger.info("##  {}  newPos  {}  oldIndex  {}  oldPos  {}  diffPos  {}", i, newPos, oldIndex, oldPos, diffPos);
				logger.info("{}", String.format("##  %2d  newPos  %.5f  oldIndex  %2d  oldPos  %.5f  diffPos  %.5f  ratioOfA  %.5f",
						i, newPos, oldIndex, oldPos, diffPos, diffPos / oldDelta));
				
				if (newPaletteSize <= oldPaletteSize || Math.abs(diffPos) < 0.00001) {
					ret[i] = palette[oldIndex];
				} else if (0 < diffPos) {
					var a = palette[oldIndex];
					var b = palette[oldIndex + 1];
					var ratioOfA = 1.0 - Math.abs(diffPos / oldDelta);
					ret[i] = interpolate(a, b, ratioOfA);
				} else if (diffPos < 0) {
					var a = palette[oldIndex - 1];
					var b = palette[oldIndex];
					var ratioOfA = Math.abs(diffPos / oldDelta);
					ret[i] = interpolate(a, b, ratioOfA);
				} else {
					throw new UnexpectedException("Unexpected");
				}
			}
		}
		
		return ret;
	}
	
	public static void main(String[] args) {
		var sb = new StringBuffer();
		
		sb.append("<htm><body>");
		
		for (int n = 2; n < 24; n++){
			sb.append("n = " + n);
			sb.append("<table><tr>");
//			var palette = interpolate(BW_N2, n);
			var palette = interpolate(RG_N2, n);
//			var palette = interpolate(SET3_N12, n);
			
			for(int i = 0; i < n; i++) {
				sb.append("<td bgcolor=" + palette[i].toString() + ">XXXX</td>");
			}
			sb.append("</tr></table>");
		}
		
		sb.append("</body></html>");
		
		FileUtil.write().file("tmp/a.html", sb.toString());
	}
}
