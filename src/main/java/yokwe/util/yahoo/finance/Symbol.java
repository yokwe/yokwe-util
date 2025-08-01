package yokwe.util.yahoo.finance;

public final class Symbol implements Comparable<Symbol> {
	public String symbol;
	public String type;
	public String exchange;
	public String sector;
	public String industry;
	public String name;
	
	public Symbol(
		String symbol,
		String type,
		String exchange,
		String sector,
		String industry,
		String name
		) {
		this.symbol   = symbol;
		this.type     = type;
		this.exchange = exchange;
		this.sector   = sector;
		this.industry = industry;
		this.name     = name;
	}
	
	@Override
	public String toString() {
		return String.format("{%s  %s  %s  \"%s\"  \"%s\" \"%s\"}", symbol, type, exchange, sector, industry, name);
	}
	
	@Override
	public int compareTo(Symbol that) {
		return this.symbol.compareTo(that.symbol);
	}
}
