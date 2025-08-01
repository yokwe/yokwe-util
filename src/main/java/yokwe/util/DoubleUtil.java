package yokwe.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class DoubleUtil {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	//
	// BigDecimal
	//
	private static final int          DOUBLE_PRECISION      = 15; // precision of double type
	private static final RoundingMode DOUBLE_ROUNDING_MODE  = RoundingMode.HALF_EVEN;
	private static final MathContext  DOUBLE_MATH_CONTEXT   = new MathContext(DOUBLE_PRECISION, DOUBLE_ROUNDING_MODE);

	public static BigDecimal toBigDecimal(double value) {
	    if (Double.isInfinite(value)) {
	    	logger.error("value is infinite");
	    	throw new UnexpectedException("value is infinite");
	    }
	    if (Double.isNaN(value)) {
	    	return null;
//	    	logger.error("value is NaN");
//	    	throw new UnexpectedException("value is NaN");
	    }
	    // round to double precision and remove trailing zero
	    return BigDecimal.valueOf(value).round(DOUBLE_MATH_CONTEXT).stripTrailingZeros();
	}

}
