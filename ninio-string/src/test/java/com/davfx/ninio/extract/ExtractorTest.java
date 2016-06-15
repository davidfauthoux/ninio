package com.davfx.ninio.extract;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ExtractorTest {
    @Test
    public void testFromString() throws Exception {
    	try (Reader r = new StringReader("Hello world, I'm happy. Hellu world, I'm hippo. Hellu world, I'm hippo.")) {
    		Extractor extractor = new Extractor().on(r);
    		Extractor e = extractor.from("lu", "lo").to("ppy", "ppo");
    		
    		Extractor e0 = e.extract();
    		Assertions.assertThat(e0.found()).isEqualTo("lo");
    		Assertions.assertThat(e0.from("I").extract().contents()).isEqualTo("'m ha");
    		
    		Extractor e1 = e.extract();
    		Assertions.assertThat(e1.found()).isEqualTo("lu");
    		Assertions.assertThat(e1.from("I").extract().contents()).isEqualTo("'m hi");
    		
    		Extractor e2 = e.extract();
    		Assertions.assertThat(e2.found()).isEqualTo("lu");
    		Assertions.assertThat(e2.from("I").extract().contents()).isEqualTo("'m hi");
    		
    		Assertions.assertThat(e.extract()).isNull();
    	}
    }

    /*%%%%
    private static final class ExtractConfiguration {
   		List<String> from = new LinkedList<>();
   		Map<String, List<String>> to = new HashMap<>();
   		Map<String, String> equal = new HashMap<>();
   		Map<String, ExtractConfiguration> sub = new HashMap<>();
   		
   		public static ExtractConfiguration build(Config config) {
   	   		ExtractConfiguration root = new ExtractConfiguration();
   	   		for (Config c : config.getConfigList("extract")) {
   	   			List<String> f = c.getStringList("from");
   		   		root.from.addAll(f);
   		   		if (c.hasPath("equal")) {
   			   		for (String ff : f) {
   		   		   		root.to.put(ff, c.getStringList("to"));
   			   			root.equal.put(ff, c.getString("equal"));
   			   		}
   		   		} else {
   		   			ExtractConfiguration cc = build(c);
   			   		for (String ff : f) {
   		   		   		root.to.put(ff, c.getStringList("to"));
   		   		   		for (String cff : cc.to.keySet()) {
   		   		   			cc.to.get(cff).addAll(c.getStringList("to"));
   		   		   		}
   			   			root.sub.put(ff, cc);
   			   		}
   		   		}
   	   		}
   	   		return root;
   		}
   		
   		public void run(Extractor extractor) throws IOException {
    		Extractor e = extractor.from(from.toArray(new String[from.size()]));
    		
    		while (true) {
	    		Extractor e0 = e.extract();
	    		if (e0 == null) {
	    			break;
	    		}
	    		List<String> t = to.get(e0.found());
	    		String eq = equal.get(e0.found());
	    		ExtractConfiguration c = sub.get(e0.found());
	    		Extractor e1 = e0.accumulate().to(t.toArray(new String[t.size()]));
	    		if (eq != null) {
	    			Assertions.assertThat(e1.accumulated()).isEqualTo(eq);
	    		} else {
	    			c.run(e1);
	    		}
    		}
   		}
    }
    
    @Test
    public void testFromConfig() throws Exception {
    	Config config = ConfigFactory.load("extractortest0.conf");
   		Assertions.assertThat(config.getString("test")).isEqualTo("test");
   		
   		ExtractConfiguration root = ExtractConfiguration.build(config);
	   		
    	try (Reader r = new StringReader(config.getString("r"))) {
    		root.run(new Extractor().on(r));
    	}
    }*/
    
    @Test
    public void testConfig() throws Exception {
    	Config config = ConfigFactory.load("extractortest1.conf");
   		
   		new ConfiguredExtraction(config).run(new StringReader(config.getString("r")), new ConfiguredExtraction.Handler() {
			int count = 0;
			String[] r = new String[] {
				"[{key_b=cd}, {}, {key_0=1, key_3=4, key_7=8}]",
				"[{key_b=cd}, {key_i=j, key_m=n}]",
				"[{key_b=cd, key_u=vwxy}]"
			};
			@Override
			public void exit(List<Map<String, String>> keyValuesList) {
    			Assertions.assertThat(keyValuesList.toString()).isEqualTo(r[count++]);
			}
		});
    }
}
