package com.davfx.ninio.trash;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.script.ReusableScriptEngine;

public class TestReusableScript {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TestReusableScript.class);
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));

		ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
		LOGGER.debug("Script engine {}/{}", scriptEngine.getFactory().getEngineName(), scriptEngine.getFactory().getEngineVersion());

		ReusableScriptEngine engine = new ReusableScriptEngine(scriptEngine);
		engine.prepare("var a = 'aaa';"
				+ "var b = null;"
				+ "var o = {oo:'ooo',pp:'ppp'};"
				+ "var f = function() { java.lang.System.out.println('(1) aaa=' + a + ', (1) bbb='+b + ', (1) o='+JSON.stringify(o));};"
				+ "var ff = function() { f(); java.lang.System.out.println('(2) aaa=' + a + ', (2) bbb='+b + ', (2) o='+JSON.stringify(o));}");

		ReusableScriptEngine.EngineGenerator engineGenerator = engine.finish();

		System.out.println("-------------");

		{
			ReusableScriptEngine.Engine e = engineGenerator.get();
			e.bind("b", "bbb");
			e.eval("o.oo = '------';");
			e.eval("var c = 'ccc';");
			e.eval("f();ff();");
			e.eval("java.lang.System.out.println('ccc=' + c);");
		}
		System.out.println("-------------");
		
		{
			ReusableScriptEngine.Engine e2 = engineGenerator.get();
			e2.bind("o", "qqqqqq");
			e2.bind("b", "bbb---");
			e2.eval("var a = 'aaa---';");
			e2.eval("f();ff();");
		}

		System.out.println("-------------");
		
		{
			ReusableScriptEngine.Engine e2 = engineGenerator.get();
			e2.bind("b", "bbb2222");
			e2.eval("a = 'aaa2222';");
			e2.eval("f();ff();");
			e2.eval("java.lang.System.out.println('ccc=' + c);");
		}

		System.out.println("-------------");

		{
			ReusableScriptEngine.Engine e2 = engineGenerator.get();
			e2.bind("b", "bbb2222");
			e2.eval("var a = 'aaa3333';");
			e2.eval("f();ff();");
			e2.eval("java.lang.System.out.println('ccc=' + c);");
		}

		System.out.println("-------------");
		
		{
			ReusableScriptEngine.Engine e3 = engineGenerator.get();
			e3.eval("f();ff();");
			e3.eval("java.lang.System.out.println('ccc=' + c);");
		}

		System.exit(0);
	}
}
