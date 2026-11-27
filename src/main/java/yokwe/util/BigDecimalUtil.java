package yokwe.util;

import java.math.BigDecimal;

public class BigDecimalUtil {
	public static BigDecimal normalize(BigDecimal value) {
		return value.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
	}
}
