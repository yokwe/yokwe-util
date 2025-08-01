package yokwe.util.yahoo.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Price implements Comparable<Price> {
	public LocalDate  date;
	public BigDecimal open;
	public BigDecimal high;
	public BigDecimal low;
	public BigDecimal close;
	public long       volume;
	
	public Price(
		LocalDate  date,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		long       volume
		) {
		this.date     = date;
		this.open     = open;
		this.high     = high;
		this.low      = low;
		this.close    = close;
		this.volume   = volume;
	}
	
	@Override
	public String toString() {
		return String.format(
			"{%s  %s  %s  %s  %s  %d}",
			date,
			open.toPlainString(),
			high.toPlainString(),
			low.toPlainString(),
			close.toPlainString(),
			volume);
	}
	
	@Override
	public int compareTo(Price that) {
		return this.date.compareTo(that.date);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Price) {
			Price that = (Price)o;
			return
				this.date.equals(that.date) &&
				this.open.compareTo(that.open) == 0 &&
				this.high.compareTo(that.high) == 0 &&
				this.low.compareTo(that.low) == 0 &&
				this.volume == that.volume;
		} else {
			return false;
		}
	}
}
