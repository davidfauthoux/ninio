package com.davfx.ninio.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

public class ConfScan {
	private static Config load(File f) throws IOException {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charsets.UTF_8))) {
			StringBuilder b = new StringBuilder();
			while (true) {
				String line = r.readLine();
				if (line == null) {
					return ConfigFactory.parseString(b.toString());
				}
				String l = line.trim();
				if (l.startsWith("include ")) {
					//b.append(loadConfig(clazz, l.substring("include ".length()).trim(), true));
				} else {
					b.append(line);
				}
				b.append("\n");
			}
		}
	}

	private static void print(String packageName, String root, ConfigObject o, Set<String> keys) {
		for (String k : o.keySet()) {
			ConfigValue v = o.get(k);
			if (v instanceof ConfigObject) {
				print(packageName, root + k + ".", (ConfigObject) v, keys);
			} else {
				String kk = root + k;
				if (!kk.startsWith(packageName + ".")) {
					System.out.println("INVALID " + kk);
				} else {
					keys.add((root + k).substring((packageName + ".").length()));
				}
			}
		}
	}
	
	private static void scan(File dir, Set<String> keys) throws IOException {
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				scan(f, keys);
			} else if (f.getName().endsWith(".java")) {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charsets.UTF_8))) {
					while (true) {
						String line = r.readLine();
						if (line == null) {
							break;
						}
						String l = line.trim();
						Iterator<String> ii = keys.iterator();
						while (ii.hasNext()) {
							String k = ii.next();
							if (l.contains("\"" + k + "\"")) {
								System.out.println("FOUND " + k + " IN " + f);
								ii.remove();
							}
						}
					}
				}
			}
		}
	}
	
	private static void scan(File dir, String packageName) throws IOException {
		Set<String> keys = new HashSet<>();
		print(packageName, "", load(new File(dir, "src/main/resources/" + packageName + ".conf")).root(), keys);
		scan(new File(dir, "src/main/java"), keys);
		System.out.println(keys);
	}
	private static void scan(String packageName) throws IOException {
		System.out.println("*** " + packageName + " ***");
		scan(new File("../ninio-" + packageName), "com.davfx.ninio." + packageName);
	}
	
	public static void main(String[] args) throws IOException {
		scan("core");
		scan("csv");
		scan("http");
		scan("ping");
		scan("proxy");
		scan("script");
		scan("snmp");
		scan("sort");
		scan("ssh");
		scan("string");
		scan("telnet");
		scan("util");
	}
}
