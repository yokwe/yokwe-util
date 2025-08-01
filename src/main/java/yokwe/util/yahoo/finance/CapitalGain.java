package yokwe.util.yahoo.finance;

import java.time.LocalDate;

public final class CapitalGain implements Comparable<CapitalGain> {
	public LocalDate  date;
	public String     detail;

	public CapitalGain(
		LocalDate  date,
		String     detail) {
		this.date   = date;
		this.detail = detail;
	}
		
	@Override
	public String toString() {
		return String.format(
			"{%s  %s}",
			date,
			detail);
	}

	@Override
	public int compareTo(CapitalGain that) {
		return this.date.compareTo(that.date);
	}

}
