package com.click2sync.rc.srv.sapb1;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServiceLogger {
	
	public static void log(Object message) {
		
		String dateprefix = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(new Date());
		System.out.println(dateprefix+" - "+message);
		
	}
	
	public static void error(String message) {
		
		String dateprefix = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(new Date());
		System.err.println(dateprefix+" - "+message);
		
	}
	
	public static void error(Exception e) {
		
		String dateprefix = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(new Date());
		System.err.println(dateprefix+" - "+e.getMessage());
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		System.err.println(dateprefix+" - "+sw.toString());
		
	}

}
