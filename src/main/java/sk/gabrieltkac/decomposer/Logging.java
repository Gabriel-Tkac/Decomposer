package sk.gabrieltkac.decomposer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Logging {
   
   public static String traceException(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String sStackTrace = sw.toString();
		return sStackTrace;
	}
   
   public static void logException(Exception e, boolean printFullTrace, String logFilepath) {
	   DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yy'T'hh-mm");
	   String dateStr = LocalDateTime.now().format(formatter);
	   try {
		   File log = new File(logFilepath + "-" + dateStr + ".log");
		   FileWriter fw = new FileWriter(log);
		   PrintWriter pw = new PrintWriter(fw);
		   
		   String strExc = traceException(e);
		   
		   if (printFullTrace)
			   pw.println(strExc);
		   pw.println();
		   
		   // First line, containing basic exception description
		   String start = strExc.substring(0, strExc.indexOf("at "));
		   System.out.println(e.getClass().getName() + ": ");
		   pw.println(e.getClass().getName());
		   
		   // Lines taken by splitting the initial trace
		   List<String> firstLineSplitted = parseFirstLineRecursively(start, new ArrayList<String>());
		   for (String s: firstLineSplitted) {
			   pw.println(s);
			   System.out.println(s);
		   }
		   
		   // Lines containing causes of exception
		   List<String> recursiveCauses = parseCauseRecursively(strExc, new ArrayList<String>());
		   for (String s: recursiveCauses) {
			   System.out.println(s);
			   pw.print(s);
		   }  
			   
		   // Lines including references to our package
		   List<String> recursiveTraces = parseTraceRecursively(strExc, new ArrayList<String>());
		   for (String s: recursiveTraces) {
			   System.out.println(s);
			   pw.print(s);
		   }   
		   
		   pw.close();
	   }
	   catch (IOException ex) {
		   System.out.println("Chyba pri zapise log suboru " + traceException(ex));
	   }
   }
   
   static List<String> parseTraceRecursively(String in, List<String> out) {
	   if (in.indexOf("sk.") == -1)
		   return out;
	   in = in.substring(in.indexOf("sk."));
	   if (in.indexOf("at ") == -1)
		   return out;
	   
	   String strOfInterest = in.substring(0, in.indexOf("at "));
	   out.add(strOfInterest);
	   in = in.substring(in.indexOf("at "));
   
	   out = parseTraceRecursively(in, out);
	   
	   return out;
   }
   
   static List<String> parseCauseRecursively(String in, List<String> out) {
	   if (in.indexOf("Caused by:") == -1)
		   return out;
	   in = in.substring(in.indexOf("Caused by:"));
	   if (in.indexOf("at ") == -1)
		   return out;
	   
	   String strOfInterest = in.substring(0, in.indexOf("at "));
	   out.add(strOfInterest);
	   in = in.substring(in.indexOf("at "));
   
	   out = parseTraceRecursively(in, out);
	   
	   return out;
   }
   
   static List<String> parseFirstLineRecursively(String in, List<String> out) {
	   
	   if (in.indexOf(": ") == -1)
		   return out;
	   out.add(in.substring(0, in.indexOf(": ")));
	   in = in.substring(in.indexOf(": ")+2);
	   
	   out = parseFirstLineRecursively(in, out);
	   
	   return out;
   }
}
