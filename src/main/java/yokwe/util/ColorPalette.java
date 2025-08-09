package yokwe.util;

import java.util.Arrays;


public final class ColorPalette {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static ColorPalette MONO = new ColorPalette(
			"#000000", "#ffffff");
	public static ColorPalette RGB = new ColorPalette(
		"#FF0000", "#00FF00", "#0000FF");
	
	// https://colorbrewer2.org/#type=qualitative&scheme=Paired&n=12
	public static ColorPalette SET3_N12 = new ColorPalette(
		"#a6cee3", "#1f78b4", "#b2df8a",
		"#33a02c", "#fb9a99", "#e31a1c",
		"#fdbf6f", "#ff7f00", "#cab2d6",
		"#6a3d9a", "#ffff99", "#b15928");
	
	public static ColorPalette PAIRED_N12 = new ColorPalette(
		"#a6cee3", "#1f78b4", "#b2df8a",
		"#33a02c", "#fb9a99", "#e31a1c",
		"#fdbf6f", "#ff7f00", "#cab2d6",
		"#6a3d9a", "#ffff99", "#b15928");
	
	
	public static final class Color {
		public final double r; // 0.0 - 1.0
		public final double g; // 0.0 - 1.0
		public final double b; // 0.0 - 1.0
		
		public Color(double r, double g, double b) {
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
		public Color(Color that) {
			this.r = that.r;
			this.g = that.g;
			this.b = that.b;
		}
		
		public static Color fromString(String string) {
			// #rgb
			if (string.length() == 4 && string.charAt(0) == '#') {
				var value = Integer.valueOf(string.substring(1), 16);
				
				int bits        = 4;
				int mask        = (1 << bits) - 1;
				var denominator = (double)mask;
				var r = ((value >> (bits * 2)) & mask) / denominator;
				var g = ((value >> (bits * 1)) & mask) / denominator;
				var b = ((value >> (bits * 0)) & mask) / denominator;
				return new Color(r, g, b);
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
				return new Color(r, g, b);
			}
			logger.error("Unexpected string");
			logger.error("  string  {}!", string);
			throw new UnexpectedException("Unexpected string");
		}
		public static Color[] fromString(String... strings) {
			return Arrays.stream(strings).map(o -> fromString(o)).toArray(Color[]::new);
		}
		
		public static Color interpolate(Color a, Color b, double ratioOfA) {
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
			return new Color(newR, newG, newB);
		}
		
		@Override
		public String toString() {
			int bits        = 8;
			int mask        = (1 << bits) - 1;
			var denominator = (double)mask;

			return String.format("#%02x%02x%02x", (int)(r * denominator), (int)(g * denominator), (int)(b * denominator));
		}
	}
	
	
	
	private final Color[] colors;
	
	public ColorPalette(Color[] colors) {
		this.colors = colors;
		
		// sanity check
		if (this.colors.length <= 1) {
			logger.error("Unexpected colors.length");
			logger.error("  colors.length  {}!", this.colors.length);
			throw new UnexpectedException("Unexpected colors.length");
		}
	}

	public ColorPalette(String... strings) {
		this(Arrays.stream(strings).map(o -> Color.fromString(o)).toArray(Color[]::new));
	}
	
	public Color getColor(int index) {
		// sanity check
		if (index < 0 || colors.length <= index) {
			logger.error("Unexpected index");
			logger.error("  index  {}", index);
			logger.error("  colors  {}", colors.length);
			throw new UnexpectedException("Unexpected index");
		}
		
		return colors[index];
	}
	public String toString(int index) {
		return getColor(index).toString();
	}
	
	public ColorPalette interpolate(int newColorSize) {
		// sanity check
		if (newColorSize <= 1) {
			logger.error("Unexpected newColorSize");
			logger.error("  newPaletteSize  {}!", newColorSize);
			throw new UnexpectedException("Unexpected newColorSize");
		}
		if (colors.length <= 1) {
			logger.error("Unexpected array.length");
			logger.error("  array.length  {}!", colors.length);
			throw new UnexpectedException("Unexpected array.length");
		}
		
		int oldColorSize = colors.length;
		Color[] newColors = new Color[newColorSize];
		
//		logger.info("XX  oldPaletteSize  {}  newPaletteSize  {}", oldPaletteSize, newPaletteSize);

		if (oldColorSize == newColorSize) {
			// special case  -- same size
			for(int i = 0; i < oldColorSize; i++) {
				newColors[i] = new Color(colors[i]);
			}
		} else {
			double oldDelta = 1.0 / (oldColorSize - 1);
			double newDelta = 1.0 / (newColorSize - 1);
			
			for(var i = 0; i < newColorSize; i++) {
				var newPos  = newDelta * i;
				var oldIndex = (int)((newPos / oldDelta) + 0.5);
				var oldPos  = oldIndex * oldDelta;
				var diffPos = newPos - oldPos;
				
//				logger.info("{}", String.format("##  %2d  newPos  %.5f  oldIndex  %2d  oldPos  %.5f  diffPos  %.5f  ratioOfA  %.5f",
//					i, newPos, oldIndex, oldPos, diffPos, diffPos / oldDelta));
				
				if (newColorSize <= oldColorSize || Math.abs(diffPos) < 0.00001) {
					newColors[i] = colors[oldIndex];
				} else if (0 < diffPos) {
					var a = colors[oldIndex];
					var b = colors[oldIndex + 1];
					var ratioOfA = 1.0 - Math.abs(diffPos / oldDelta);
					newColors[i] = Color.interpolate(a, b, ratioOfA);
				} else if (diffPos < 0) {
					var a = colors[oldIndex - 1];
					var b = colors[oldIndex];
					var ratioOfA = Math.abs(diffPos / oldDelta);
					newColors[i] = Color.interpolate(a, b, ratioOfA);
				} else {
					throw new UnexpectedException("Unexpected");
				}
			}
		}
		
		return new ColorPalette(newColors);
	}
	
	
	public static void main(String[] args) {
		var sb = new StringBuffer();
		
		sb.append("<htm><body>");
		
		var palette = ColorPalette.RGB;
		
		for (int n = 2; n < 24; n++){
			sb.append("n = " + n);
			sb.append("<table><tr>");
			var newPalette = palette.interpolate(n);
			
			for(int i = 0; i < n; i++) {
				sb.append("<td bgcolor=" + newPalette.toString(i) + ">XXXX</td>");
			}
			sb.append("</tr></table>");
		}
		
		sb.append("</body></html>");
		
		FileUtil.write().file("tmp/a.html", sb.toString());
	}

}
