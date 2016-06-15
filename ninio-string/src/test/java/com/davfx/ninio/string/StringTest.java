package com.davfx.ninio.string;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class StringTest {

	@Test
	public void testAfter() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{after:012:uvw012xyz}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("axyzbc");
	}

	// append is not tested, it's intrisic
	
	@Test
	public void testBefore() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{before:012:uvw012xyz}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("auvwbc");
	}

	@Test
	public void testBitCompose() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{bitcompose:8:111:222}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("a" + ((111 << 8) | 222) + "bc");
	}

	@Test
	public void testClean() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{clean:$xy#z@}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("axyzbc");
	}

	@Test
	public void testFind() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{find:5:3:012345556789}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("a555bc");

		input = h.build("a{find:5:4:012345556789}bc");
		
		Assertions.assertThat(input.get(null)).isNull();
	}

	@Test
	public void testGetPrefix() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{getprefix:zzz:zzz0123456}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("azzzbc");
	}

	@Test
	public void testGetSuffix() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{getsuffix:zzz:0123456zzz}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("azzzbc");
	}

	@Test
	public void testIfEq() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{ifeq:0123456:0123456:eq:neq}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("aeqbc");
		
		input = h.build("a{ifeq:0123456:0123457:eq:neq}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("aneqbc");
	}

	@Test
	public void testIfNull() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{ifnull:{getsuffix:zzz:0123456zzz}:n}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("azzzbc");
		
		input = h.build("a{ifnull:{getsuffix:zzz:0123456xxx}:n}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("anbc");
	}

	@Test
	public void testNull() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{null}bc");
		
		Assertions.assertThat(input.get(null)).isNull();
	}

	@Test
	public void testRemovePrefix() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{removeprefix:zzz:zzz0123456}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("a0123456bc");
	}

	@Test
	public void testRemoveSuffix() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{removesuffix:zzz:0123456zzz}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("a0123456bc");
	}

	@Test
	public void testReplace() {
		StringHandler<String> h = new StringHandler<>();
		StringInput<String> input = h.build("a{replace:xxx:zzz:0123xxx456}bc");
		
		Assertions.assertThat(input.get(null)).isEqualTo("a0123zzz456bc");
	}

    @Test
    public void testCompositeHuawei() throws Exception {
		StringHandler<String> h = new StringHandler<>();

		StringInput<String> input;
		
		input = h.build("{compositeprefix:/:\\::,:=:true:eNB_666/Cell\\:a=aa,b=bb/TRX\\:c=cc}");
		Assertions.assertThat(input.get(null)).isEqualTo("eNB_666");
		input = h.build("{compositespecification:/:\\::,:=:true:eNB_666/Cell\\:a=aa,b=bb/TRX\\:c=cc}");
		Assertions.assertThat(input.get(null)).isEqualTo("Cell/TRX");
		input = h.build("{compositevalue:/:\\::,:=:true:a:eNB_666/Cell\\:a=aa,b=bb/TRX\\:c=cc}");
		Assertions.assertThat(input.get(null)).isEqualTo("aa");
		input = h.build("{compositevalue:/:\\::,:=:true:b:eNB_666/Cell\\:a=aa,b=bb/TRX\\:c=cc}");
		Assertions.assertThat(input.get(null)).isEqualTo("bb");
		input = h.build("{compositevalue:/:\\::,:=:true:c:eNB_666/Cell\\:a=aa,b=bb/TRX\\:c=cc}");
		Assertions.assertThat(input.get(null)).isEqualTo("cc");
    }
    
    @Test
    public void testCompositeNSN() throws Exception {
		StringHandler<String> h = new StringHandler<>();

		StringInput<String> input;
		
		input = h.build("{compositeprefix:{null}:{null}:/:-:true:PLMN-PLMN/NDB-666/CEL-777}");
		Assertions.assertThat(input.get(null)).isNull();
		input = h.build("{compositespecification:{null}:{null}:/:-:true:PLMN-PLMN/NDB-666/CEL-777}");
		Assertions.assertThat(input.get(null)).isNull();
		input = h.build("{compositevalue:{null}:{null}:/:-:true:PLMN:PLMN-PLMN/NDB-666/CEL-777}");
		Assertions.assertThat(input.get(null)).isEqualTo("PLMN");
		input = h.build("{compositevalue:{null}:{null}:/:-:true:NDB:PLMN-PLMN/NDB-666/CEL-777}");
		Assertions.assertThat(input.get(null)).isEqualTo("666");
		input = h.build("{compositevalue:{null}:{null}:/:-:true:CEL:PLMN-PLMN/NDB-666/CEL-777}");
		Assertions.assertThat(input.get(null)).isEqualTo("777");
    }
	
	
	private static final class Inner {
		private final String separator;
		public Inner(String separator) {
			this.separator = separator;
		}
	}
	
	@Test
	public void testGenerics() {
		StringHandler<Inner> h = new StringHandler<Inner>().add("", new StringInputFactory<Inner>() {
			@Override
			public StringInput<Inner> build(final StringInput<Inner>[] inputs) {
				return new StringInput<Inner>() {
					@Override
					public String get(Inner h) {
						StringBuilder b = new StringBuilder();
						for (StringInput<Inner> i : inputs) {
							String s = i.get(h);
							b.append(s).append(h.separator);
						}
						return b.toString();
					}
				};
			}
		});
		StringInput<Inner> input = h.build("_{:012:345:678}_");
		
		Assertions.assertThat(input.get(new Inner("a"))).isEqualTo("_012a345a678a_");
	}
}
