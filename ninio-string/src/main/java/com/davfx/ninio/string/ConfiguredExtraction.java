package com.davfx.ninio.string;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public final class ConfiguredExtraction implements Extraction {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredExtraction.class);
	
	private final ExtractConfiguration root;
	private final boolean ignoreWhitespaces;
	
	public ConfiguredExtraction(Config config) {
		ignoreWhitespaces = config.getBoolean("whitespaces.ignore");
		root = ExtractConfiguration.build(config);
		ExtractConfiguration.listsToArrays(root);
	}

	@Override
	public void run(Reader reader, Handler handler) throws IOException {
		root.run(new LinkedList<Map<String, String>>(), new Extractor().on(reader).ignoreWhitespaces(ignoreWhitespaces), handler);
	}
	
    private static final class ExtractConfiguration {
   		List<String> fromList = new LinkedList<>();
   		String[] from;
   		
   		Map<String, List<String>> toLists = new HashMap<>();
   		Map<String, String[]> to = new HashMap<>();
   		
   		Map<String, String> keys = new HashMap<>();
   		Map<String, ExtractConfiguration> sub = new HashMap<>();
   		
   		private static List<String> getStringList(Config c, String key) {
   			ConfigValue v = c.getValue(key);
   			if (v.valueType() == ConfigValueType.LIST) {
   				List<String> l = c.getStringList(key);
   				if (l.isEmpty()) {
   					throw new RuntimeException("Cannot be empty: " + key);
   				}
   				return l;
   			} else {
   				List<String> l = new LinkedList<>();
   				l.add(c.getString(key));
   				return l;
   			}
   		}
   		
   		public static ExtractConfiguration build(Config config) {
   	   		ExtractConfiguration root = new ExtractConfiguration();
   			if (config.hasPath("extract")) {
	   	   		for (Config c : config.getConfigList("extract")) {
	   	   			List<String> f = getStringList(c, "from");
	   	   			List<String> t = getStringList(c, "to");
	   		   		
	   	   			//%% LOGGER.debug("from={}, to={}", f, t);
	   	   			
	   	   			for (String ff : f) {
			   			if (root.fromList.contains(ff)) {
			   				throw new RuntimeException("'from' conflict (" + ff + " duplicated)");
			   			}
	   	   			}
	   	   			root.fromList.addAll(f);
	   		   		
	   	   			if (c.hasPath("key")) {
	   		   			String key = c.getString("key");
	   			   		for (String from : f) {
	   			   			root.keys.put(from, key);
	   			   		}
	   	   			}
	
   		   			ExtractConfiguration sub = build(c);
   			   		for (String from : f) {
		   			   	//%%% if (root.toLists.containsKey(from) || root.sub.containsKey(from)) {
		   			   	//%%% throw new RuntimeException("'from' conflict (" + from + " duplicated)");
		   			   	//%%% }
		   				root.toLists.put(from, t);
		   				/*%%%%
   		   		   		for (String cFrom : sub.toLists.keySet()) {
   		   		   			sub.toLists.get(cFrom).addAll(t);
   		   		   		}
   		   		   		*/
   			   			root.sub.put(from, sub);
   			   		}
	   	   		}
   			}
   	   		
   	   		return root;
   		}
   		
   		public static void listsToArrays(ExtractConfiguration e) {
   			if (e.fromList != null) {
				for (String f : e.fromList) {
					for (List<String> l : e.toLists.values()) {
						for (String t : l) {
							if (t.startsWith(f)) {
								throw new RuntimeException("'to'/'from' conflict");
							}
						}
					}
				}
				/*%%%%%%
				for (List<String> l : e.toLists.values()) {
					for (String t : l) {
						for (String tt : l) {
							if (t == tt) {
								continue;
							}
							if (t.startsWith(tt)) {
								throw new RuntimeException("'to' conflict: " + t + " starts with " + tt + " / " + e.toLists);
							}
						}
					}
				}
				*/
	
				e.from = e.fromList.toArray(new String[e.fromList.size()]);
	   			e.fromList = null;
   			}
   			
   			if (e.toLists != null) {
	   			for (Map.Entry<String, List<String>> entry : e.toLists.entrySet()) {
	   				//%%%% if (!entry.getValue().isEmpty()) {
					e.to.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
	   				//%%% }
	   			}
	   			e.toLists = null;
   			}
	   			
   			for (ExtractConfiguration s : e.sub.values()) {
   				listsToArrays(s);
   			}
   		}
   		
   		public void run(Deque<Map<String, String>> keyValuesList, Extractor extractor, Handler handler) throws IOException {
    		Extractor main = extractor.from(from);

    		Map<String, String> keyValues = new LinkedHashMap<>();
    		keyValuesList.addLast(keyValues);
    		
    		while (true) {
        		LOGGER.trace("Search: from = {}", Arrays.asList(from));
	    		Extractor e = main.extract();
	    		if (e == null) {
	    			break;
	    		}
	    		
	    		String found = e.found();
	    		
	    		String[] t = to.get(found);
    			e.to(t);

	    		String key = keys.get(found);
	    		if (key != null) {
	    			e.accumulate();
	    		}

	    		LOGGER.trace("Found: key = {}, found = {}, to = {}", key, found, Arrays.asList(t));
	    		
	    		ExtractConfiguration c = sub.get(found);
	    		if (c.from.length == 0) {
	    			e.extract();
	    		} else {
	    			c.run(keyValuesList, e, handler);
	    		}

	    		if (key != null) {
	    			String value = e.accumulated();
	    			keyValues.put(key, value);
	    		} 
    		}
    		
    		handler.exit(new ArrayList<Map<String, String>>(keyValuesList));
    		
    		keyValuesList.removeLast();
   		}
    }
}
