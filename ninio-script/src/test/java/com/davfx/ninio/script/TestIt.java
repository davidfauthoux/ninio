package com.davfx.ninio.script;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

public class TestIt {
	public static void main(String[] args) throws Exception {
		ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("js");
		/*
		scriptEngine.eval(""
				+ "var capture = function(e, script) {"
					+ "if (e == null) {"
						+ "e = eval;"
					+ "}"
					+ "e(script);"
					+ "return function(followScript) {"
						+ "eval(followScript);"
					+ "};"
				+ "};"
				+ "");
				*/
		/*
		scriptEngine.eval(""
				+ "var capture = (function() {"
					+ "var v = 'vvv';"
					+ "function main() {"
						+ "java.lang.System.out.println('main ' + v);"
					+ "}"
					+ "return function(f) {"
						+ "eval(f);"
					+ "};"
				+ "})();"
				+ "");
*/
		/*
		Object recapture = ((Invocable) scriptEngine).invokeFunction("capture", null, "var v = 'vvv';"
					+ "function main() {"
						+ "java.lang.System.out.println('main ' + v);"
					+ "}");
		Thread.sleep(1000);
		recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println(v);"
				+ "main();"
				+ "w = v;");
		Thread.sleep(1000);
		recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println('w=' + w);");
		*/
/*
		Object recapture = ((Invocable) scriptEngine).invokeFunction("capture", null, "var i = 'ii';");
		System.out.println("1-");
		recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println(i); var j = 'jj';");
		System.out.println("2-");
		recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println(i); java.lang.System.out.println(j);");
*/
		
		scriptEngine.eval(""
				+ "var capture = function($) {"
					+ "java.lang.System.out.println(JSON.stringify($));"
					+ "if ($ == null) { $ = {}; }"
					+ "$.a = 'aa';"
					+ "return $;"
				+ "};"
				+ "");
		Object a = ((Invocable) scriptEngine).invokeFunction("capture", new Object[] { null });
		System.out.println(a);
		Object b = ((Invocable) scriptEngine).invokeFunction("capture", a);
		System.out.println(b);
/*		
		scriptEngine.eval("(function() {"
				+ "function main() {"
				+ "}"
				+ "})();"
				+ "");
		
		
		ScriptContext context0 = new SimpleScriptContext();
		scriptEngine.eval("var a = 'aa';", context0);
		Object f = scriptEngine.eval("function f() { java.lang.System.out.println(a); }", context0);
		scriptEngine.eval("java.lang.System.out.println(a);", context0);
		
		ScriptContext context1 = new SimpleScriptContext();
		//scriptEngine.eval("java.lang.System.out.println(a);", context1);
		scriptEngine.setContext(context0);
		((Invocable) scriptEngine).invokeFunction("f");
*/
		
		
		
		
		
		
		
		
		
		
		
/*		
	Object recapture = ((Invocable) scriptEngine).invokeFunction("capture", null, "var i = 'ii';");
	Thread.sleep(1000);
	System.out.println("1-");
	recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println(i); var $ = { 'b' : i };");
	Thread.sleep(1000);
	System.out.println("2-");
	recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println($.b); $.a = 'aa';");
	Thread.sleep(1000);
	System.out.println("3-");
	recapture = ((Invocable) scriptEngine).invokeFunction("capture", recapture, "java.lang.System.out.println($.a);");
	Thread.sleep(1000);
	System.out.println("4-");
	((Invocable) scriptEngine).invokeFunction("capture", null, "java.lang.System.out.println($.a);");
*/

		
		
		
		
		
		
		/*
		scriptEngine.eval(""
				+ "var f = capture('var v = \\'vvv\\';"
					+ "function main() {"
						+ "java.lang.System.out.println(\\'main \\' + v);"
					+ "}"
				+ "');"
				+ "var g = capture('f(\\'"
					+ "java.lang.System.out.println(v);"
					+ "main();"
					+ "w = v;"
				+ "\\')');"
				+ "g('java.lang.System.out.println(\\'w=\\' + w);');"
				+ "");
		*/
		
		
		
		
		
		
		
		/*SOUCI
		scriptEngine.eval("var g = function(f) {"
						+ "eval(\"var data='datadata';\");"
						+ "f();"
					+ "};"
					+ "g(function() {"
						+ "eval('java.lang.System.out.println(data);');"
					+ "});");
					*/
/*
		scriptEngine.eval("var func = function() {"
				+ "var g = function(f) {"
				+ "eval(\"this.data='datadata';\");"
				+ "f();"
			+ "};"
			+ "g(function() {"
				+ "eval('java.lang.System.out.println(this.data);');"
			+ "});"
			+ "};"
			+ "func.call({});"
			+ "eval('java.lang.System.out.println(data);');"
			+ "");
*/
		
		/*
		scriptEngine.eval("var func = function(f) {"
								+ "eval(\"data='datadata';\");"
								+ "f();"
							+ "};"
						+ "func.call({}, function() {"
							+ "eval('java.lang.System.out.println(data);');"
						+ "});"
		+ "eval('java.lang.System.out.println(data);');"
		+ "");
*/
/*		
		scriptEngine.eval("var o = { m:'c', f: function(f) {"
				+ "this.m='b';"
				+ "java.lang.System.out.println('m='+this.m);"
				+ "return this.m;"
			+ "},"
			+ "m_:'a' };"
		+ "java.lang.System.out.println('o.f()='+o.f());"
		+ "java.lang.System.out.println('___ m='+m);"
+ "");
*/
		
		/*
		scriptEngine.eval(""
			+ "var g = {"
				+ "run: function(f) {"
						+ "eval(\"data='datadata';\");"
						+ "f();"
					+ "}"
			+ "};"
			+ "g.run(function() {"
				+ "eval('java.lang.System.out.println(this.data);');"
			+ "});"
			//+ "eval('java.lang.System.out.println(data);');"
			+ "");
*/
		//scriptEngine.eval("g()();");
		
//		scriptEngine.eval("(function() { function f() {}; var g={}; })(); g;");
		
		/*
		scriptEngine.eval("Object.prototype.each = function(callback) {"
					+ "var k;"
					+ "for (k in this) {"
						+ "if (!(this[k] instanceof Function)) {"
							+ "callback(this[k], k);"
						+ "}"
					+ "}"
			+ "};"
			+ "Array.prototype.each = function(callback) {"
					+ "var k;"
					+ "for (k = 0; k < this.length; k++) {"
						+ "callback(this[k], k);"
					+"};"
			+ "};");
		scriptEngine.eval("var o = {'a':'aa', 'b':'bb'}; o.each(function(v, k) { java.lang.System.out.println(v + '=' + k ); });");
		scriptEngine.eval("var a = ['aa', 'bb']; a.each(function(v, k) { java.lang.System.out.println(v + '=' + k ); });");
		*/

	}
}
