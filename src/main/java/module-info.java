open module yokwe.util {
	exports yokwe.util;
	exports yokwe.util.graphviz;
	exports yokwe.util.http;
	exports yokwe.util.json;
	exports yokwe.util.libreoffice;
	exports yokwe.util.makefile;
	exports yokwe.util.selenium;
	exports yokwe.util.update;
	exports yokwe.util.yahoo.finance;

//	exports yokwe.util.stats;
//	exports yokwe.util.xml;
//	exports yokwe.util.finance;
//	exports yokwe.util.finance.online;

	requires commons.math3;
	requires org.apache.commons.net;
	
	requires transitive org.apache.httpcomponents.core5.httpcore5;
	requires org.apache.httpcomponents.core5.httpcore5.h2;
	requires org.apache.httpcomponents.client5.httpclient5;
	
	// json from jakarta ee
	requires transitive jakarta.json;
	requires transitive jakarta.json.bind;
	
	// xml bind from jakarta ee
	requires transitive java.xml;
	requires transitive jakarta.xml.bind;
	
//	// mail from jakarta ee
//	requires jakarta.mail;
	
	// selenium
	requires transitive org.seleniumhq.selenium.api;
	requires transitive org.seleniumhq.selenium.chrome_driver;
	requires transitive org.seleniumhq.selenium.safari_driver;
	requires transitive org.seleniumhq.selenium.http;
	requires transitive org.seleniumhq.selenium.json;
	requires transitive org.seleniumhq.selenium.os;
	requires transitive org.seleniumhq.selenium.remote_driver;
	requires transitive org.seleniumhq.selenium.support;
	//
	requires com.google.common;
	
	// logging
	requires transitive ch.qos.logback.classic;
	requires ch.qos.logback.core;
	requires transitive org.slf4j;
	// for SLF4JBridgeHandler.install()
	requires jul.to.slf4j;
	
	// libreoffice
	requires transitive org.libreoffice.uno;
	
}