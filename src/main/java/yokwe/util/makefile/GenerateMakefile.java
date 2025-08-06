package yokwe.util.makefile;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.TreeSet;

import yokwe.util.ClassUtil;
import yokwe.util.FileUtil;

public class GenerateMakefile {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	public static void main(String[] args) {
		logger.info("START");
		
		var moduleName = "yokwe.finance.data";
		var makeFile   = new File("tmp/update-data.make");
		
		var module = ClassUtil.findModule(moduleName);
		var string = generate(module);
		
		logger.info("save  {}  {}", string.length(), makeFile.getAbsoluteFile());
		FileUtil.write().file(makeFile, string);
		
		logger.info("STOP");
	}
	
	public static String generate(Module module) {
		var list = Makefile.scanModule(module);

		var groupNameSet = new TreeSet<String>();
		list.stream().forEach(o -> groupNameSet.add(o.group));
		var groupUpdateSet = new TreeSet<String>();
		groupNameSet.forEach(o -> groupUpdateSet.add("update-" + o));
		
		var sw = new StringWriter();		
		try (var out = new PrintWriter(sw)) {
			out.println("#");
			out.println("# module " + module.getDescriptor().toNameAndVersion());
			out.println("#");
			out.println();
			out.println(".PHONY: update-all " + String.join(" ", groupUpdateSet));
			out.println();
			out.println("#");
			out.println("# update-all");
			out.println("#");
			out.println("update-all: \\");
			out.println("\t" + String.join(" \\\n\t", groupUpdateSet));
			out.println();
			out.println();
			
			for(var group: groupNameSet) {
				var makeList = list.stream().filter(o -> o.group.equals(group)).toList();
				var outFileSet = new TreeSet<String>();
				for(var e: makeList) {
					Arrays.stream(e.outputs).forEach(o -> outFileSet.add(o.getAbsolutePath()));
				}
				
				out.println("#");
				out.println("# " + group);
				out.println("#");
				out.println("update-" + group + ": \\");
				out.println("\t" + String.join(" \\\n\t", outFileSet));
				out.println();
				
				for(var e: makeList) {
					var iList = Arrays.stream(e.inputs).map(o -> o.getAbsolutePath()).toList();
					var oList = Arrays.stream(e.outputs).map(o -> o.getAbsolutePath()).toList();
					
					if (iList.isEmpty()) {
						out.println(String.join(" ", oList) + ":");
					} else {
						out.println(String.join(" ", oList) + ": \\");
						out.println("\t" + String.join(" \\\n\t", iList));
					}
					out.println("\tant " + e.target);
				}
				
				out.println();
				out.println();
			}
		}
		
		return sw.toString();
	}
	
}
