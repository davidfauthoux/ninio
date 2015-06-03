package com.davfx.ninio.trash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.ScriptRunner;

public class TestReusableScript {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TestReusableScript.class);
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));

		ScriptRunner engine = new ExecutorScriptRunner();
		engine.prepare("var a = 'aaa';"
				+ "var b = null;"
				+ "var o = {oo:'ooo',pp:'ppp'};"
				+ "var f = function() { java.lang.System.out.println('(1) aaa=' + a + ', (1) bbb='+b + ', (1) o='+JSON.stringify(o));};"
				+ "var ff = function() { f(); java.lang.System.out.println('(2) aaa=' + a + ', (2) bbb='+b + ', (2) o='+JSON.stringify(o));}", null, null);

		System.out.println("-------------");

		{
			ScriptRunner.Engine e = engine.engine();
			e.eval("o.oo = '------';"
					+ "var c = 'ccc';"
					+ "b = 'bbbbb';"
					+ "f();ff();"
					+ "java.lang.System.out.println('ccc=' + c);", null, null);
		}
		System.out.println("-------------");
		
		{
			ScriptRunner.Engine e = engine.engine();
			e.eval("var a = 'aaa---';"
					+ "f();ff();"
					+ "java.lang.System.out.println('ccc=' + c);", null, null);
		}

		System.out.println("-------------");
		
		Thread.sleep(1000);
		System.exit(0);
	}
}
