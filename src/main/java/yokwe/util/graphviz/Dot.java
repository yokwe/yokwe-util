package yokwe.util.graphviz;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import yokwe.util.AutoIndentPrintWriter;

public class Dot {
//	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	interface Statement {
		public void output(AutoIndentPrintWriter out);
	}
	
	// graph : [ strict ] (graph | digraph) [ ID ] '{' stmt_list '}'
	public static class GraphBase implements Statement {
		private final String type;
		private final String id;
		private final String edgeop;

		private final List<Statement> list = new ArrayList<>();
		
		protected GraphBase(String type, String id, String edgeop) {
			this.type   = type;
			this.id     = id;
			this.edgeop = edgeop;
		}
		
		@Override
		public void output(AutoIndentPrintWriter out) {
			if (list.isEmpty()) return;
			
			if (type == null) {
				out.println("{");
			} else if (id == null) {
				out.println("%s {", type);
			} else {
				out.println("%s \"%s\" {", type, id);
			}
			for (var e : list) {
				e.output(out);
			}
			out.println("}");
		}
		@Override
		public String toString() {
			var sw = new StringWriter();
			try (var out = new AutoIndentPrintWriter(new PrintWriter(sw))) {
				output(out);
			}
			return sw.toString();
		}

		
		//
		// statement
		//
		
		// node_stmt : node_id [ attr_list ]
		public AttrBase node(String id, String name, String value) {
			var attr = new AttrBase(id, name, value);
			list.add(attr);
			return attr;
		}
		public AttrBase node(String id) {
			var attr = new AttrBase(id);
			list.add(attr);
			return attr;
		}
		// edge_stmt : (node_id | subgraph) edgeRHS [ attr_list ]
		// edgeRHS : edgeop (node_id | subgraph) [ edgeRHS ]
		public Edge edge(String[] nodes) {
			var edge = new Edge(edgeop, nodes);
			list.add(edge);
			return edge;
		}
		public Edge edge(String a, String b) {
			var arg = new String[] {a, b};
			return edge(arg);
		}
		public Edge edge(String a, String b, String c) {
			var arg = new String[] {a, b, c};
			return edge(arg);
		}
		public Edge edge(String a, String b, String c, String d) {
			var arg = new String[] {a, b, c, d};
			return edge(arg);
		}
		public Edge edge(String a, String b, String c, String d, String e) {
			var arg = new String[] {a, b, c, d, e};
			return edge(arg);
		}
		// attr
		// attr_stmt : (graph | node | edge) attr_list
		// attr_list :'[' [ a_list ] ']' [ attr_list ]
		// a_list : ID '=' ID [ (';' | ',') ] [ a_list ]
		public AttrBase graphAttr() {
			var attr = new AttrBase("graph", false);
			list.add(attr);
			return attr;
		}
		public AttrBase nodeAttr() {
			var attr = new AttrBase("node", false);
			list.add(attr);
			return attr;
		}
		public AttrBase edgeAttr() {
			var attr = new AttrBase("edge", false);
			list.add(attr);
			return attr;
		}
		//
		public AttrBase graphAttr(String name, String value) {
			var attr = new AttrBase("graph", name, value);
			list.add(attr);
			return attr;
		}
		public AttrBase nodeAttr(String name, String value) {
			var attr = new AttrBase("node", name, value);
			list.add(attr);
			return attr;
		}
		public AttrBase edgeAttr(String name, String value) {
			var attr = new AttrBase("edge", name, value);
			list.add(attr);
			return attr;
		}
		
		
		// ID : ID
		public GraphBase attr(String name, String value) {
			list.add(new Attr(name, value));
			return this;
		}
		// subgraph
		public GraphBase subgraph(String id) {
			var g = new GraphBase("subgraph", id, edgeop);
			list.add(g);
			return g;
		}
		public GraphBase subgraph() {
			var g = new GraphBase(null, null, edgeop);
			list.add(g);
			return g;
		}

	}
	public static class Graph extends GraphBase {
		public Graph(String id) {
			super("graph", id, "--");
		}

		public Graph() {
			this(null);
		}
	}
	public static class Digraph extends GraphBase {
		public Digraph(String id) {
			super("digraph", id, "->");
		}

		public Digraph() {
			this(null);
		}
	}
	
	public static class Attribute {
		public final String name;
		public final String value;
		
		public Attribute(String name, String value) {
			this.name  = name;
			this.value = value;
		}
		
		@Override
		public String toString() {
			return "\"" + name + "\" = \"" + value + "\"";
		}
	}
	public static class AttributeList {
		public List<Attribute> list = new ArrayList<>();
		
		public AttributeList() {};
		public AttributeList(String name, String value) {
			list.add(new Attribute(name, value));
		}
		
		public boolean isEmpty() {
			return list.isEmpty();
		}
		public AttributeList attr(String name, String value) {
			list.add(new Attribute(name, value));
			return this;
		}
		@Override
		public String toString() {
			if (list.isEmpty()) {
				return "";
			} else {
				return "[" + String.join(", ", list.stream().map(o -> o.toString()).toList()) + "]";
			}
		}
	}
	
	public static class Attr extends Attribute implements Statement {
		public Attr(String name, String value) {
			super(name, value);
		}

		@Override
		public void output(AutoIndentPrintWriter out) {
			out.println("\"" + name + "\" = \"" + value + "\"");
		}
	}
	
	public static class AttrBase extends AttributeList implements Statement {
		public final String id;
		public final boolean quoteID;
		
		public AttrBase(String id, String name, String value) {
			super(name, value);
			this.id      = id;
			this.quoteID = true;
		}
		public AttrBase(String id) {
			super();
			this.id      = id;
			this.quoteID = true;
		}
		public AttrBase(String id, boolean quoteID) {
			super();
			this.id      = id;
			this.quoteID = quoteID;
		}
		
		
		public AttrBase attr(String name, String value) {
			super.attr(name, value);
			return this;
		}
		
		@Override
		public void output(AutoIndentPrintWriter out) {
			var idString = quoteID ? ("\"" + id + "\"") : id;
			if (list.isEmpty()) {
				out.println(idString);
			} else {
				out.println(idString + " " + super.toString());
			}
		}
	}
	
	public static class Edge implements Statement {
		public final String subsep;
		public final List<String> nodeList = new ArrayList<>();
		public final AttributeList attrList = new AttributeList();
		
		public Edge(String subsep, String[] nodes, String name, String value) {
			this.subsep = subsep;
			for(var e: nodes) nodeList.add("\"" + e + "\"");
			if (name != null) {
				this.attrList.attr(name, value);
			}
		}
		public Edge(String subsep, String[] nodes) {
			this(subsep, nodes, null, null);
		}
		
		public Edge node(String name) {
			nodeList.add(name);
			return this;
		}
		public Edge attr(String name, String value) {
			this.attrList.attr(name, value);
			return this;
		}
		
		@Override
		public void output(AutoIndentPrintWriter out) {
			var sep = " " + subsep + " ";
			if (attrList.isEmpty()) {
				out.println(String.join(sep, nodeList));
			} else {
				out.println(String.join(sep, nodeList) + " " + attrList.toString());
			}
		}
	}
}
