package com.davfx.ninio.string;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public final class StringHandler<T> {
	// Beware, with the current algorithm, this function cannot find '\'
	private static final int find(boolean skipBlocks, String s, char toFind, int from) {
		int open = 0;
		boolean escaping = false;
		for (int i = from; i < s.length(); i++) {
			if (escaping) {
				escaping = false;
				continue;
			}
			char c = s.charAt(i);
			if (c == '\\') {
				escaping = true;
				continue;
			}
			if ((open == 0) && (c == toFind)) {
				return i;
			} else if (skipBlocks && (c == '{')) {
				open++;
			} else if (skipBlocks && (c == '}')) {
				open--;
			}
		}
		return -1;
	}
	
	private final Map<String, StringInputFactory<T>> factories = new HashMap<>();

	public StringHandler() {
		factories.put("null", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new NullStringInput<T>();
			}
		});

		factories.put("before", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new BeforeStringInput<T>(inputs[0], inputs[1]);
			}
		});

		factories.put("after", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new AfterStringInput<T>(inputs[0], inputs[1]);
			}
		});

		factories.put("clean", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new CleanStringInput<T>(inputs[0]);
			}
		});

		factories.put("ifnull", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				IfNullStringInput<T> input = new IfNullStringInput<T>();
				for (StringInput<T> in : inputs) {
					input.add(in);
				}
				return input;
			}
		});

		factories.put("bitcompose", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new BitComposeStringInput<T>(inputs[0], inputs[1], inputs[2]);
			}
		});

		factories.put("compositeprefix", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new CompositePrefixStringInput<T>(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5]);
			}
		});
		factories.put("compositespecification", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new CompositeSpecificationStringInput<T>(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5]);
			}
		});
		factories.put("compositevalue", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new CompositeValueStringInput<T>(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5], inputs[6]);
			}
		});
		
		factories.put("removeprefix", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new RemovePrefixStringInput<T>(inputs[0], inputs[1]);
			}
		});
		factories.put("removesuffix", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new RemoveSuffixStringInput<T>(inputs[0], inputs[1]);
			}
		});
		factories.put("getprefix", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new GetPrefixStringInput<T>(inputs[0], inputs[1]);
			}
		});
		factories.put("getsuffix", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new GetSuffixStringInput<T>(inputs[0], inputs[1]);
			}
		});

		factories.put("find", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new FindStringInput<T>(inputs[0], inputs[1], inputs[2]);
			}
		});

		factories.put("replace", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new ReplaceStringInput<T>(inputs[0], inputs[1], inputs[2]);
			}
		});

		factories.put("ifeq", new StringInputFactory<T>() {
			@Override
			public StringInput<T> build(StringInput<T>[] inputs) {
				return new IfEqStringInput<T>(inputs[0], inputs[1], inputs[2], inputs[3]);
			}
		});
	}
	
	public StringHandler<T> add(String keyword, StringInputFactory<T> factory) {
		factories.put(keyword, factory);
		return this;
	}
	
	private StringInput<T> buildStringInput(String s) {
		int c = find(true, s, ':', 1); // 1 for '{'
		String keyword;
		String remaining;
		if (c < 0) {
			keyword = s.substring(1, s.length() - 1); // -1 for '}'
			remaining = null;
		} else {
			keyword = s.substring(1, c);
			remaining = s.substring(c + 1, s.length() - 1); // -1 for '}'
		}
		
		Deque<StringInput<T>> l = new LinkedList<StringInput<T>>();
		if (remaining != null) {
			int i = 0;
			while (true) {
				int d = find(true, remaining, ':', i);
				if (d < 0) {
					l.add(buildCompleteStringInput(remaining.substring(i)));
					break;
				}
				l.add(buildCompleteStringInput(remaining.substring(i, d)));
				i = d + 1;
			}
		}
		
		final StringInput<T> keywordInput = buildCompleteStringInput(keyword);
		
		@SuppressWarnings("unchecked")
		final StringInput<T>[] inputs = (StringInput<T>[]) new StringInput<?>[l.size()];
		l.toArray(inputs);

		return new StringInput<T>() {
			@Override
			public String get(T h) {
				String kw = keywordInput.get(h);
				StringInputFactory<T> factory = factories.get(kw);
				if (factory == null) {
					throw new RuntimeException("Invalid keyword: " + kw);
				}
				StringInput<T> input = factory.build(inputs);
				if (input == null) {
					throw new RuntimeException("Invalid factory: " + kw);
				}
				return input.get(h);
			}
		};
	}
	
	private StringInput<T> buildCompleteStringInput(String s) {
		AppendStringInput<T> inputs = new AppendStringInput<T>();
		int last = 0;
		while (true) {
			int a = find(false, s, '{', last);
			if (a < 0) {
				if (last < s.length()) {
					inputs.add(new EscapingStringInput<T>(s.substring(last)));
				}
				break;
			}
			if (last < a) {
				inputs.add(new EscapingStringInput<T>(s.substring(last, a)));
			}
			int b = find(true, s, '}', a + 1);
			inputs.add(buildStringInput(s.substring(a, b + 1)));
			last = b + 1;
		}
		return inputs;
	}
	
	public StringInput<T> build(String configuration) {
		return buildCompleteStringInput(configuration);
	}
}
