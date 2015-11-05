package com.davfx.ninio.snmp;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestOid {

	@Test
	public void test() throws Exception {
		Assertions.assertThat(new Oid("1.1.1").isPrefix(new Oid("1.1.1.2"))).isTrue();
		Assertions.assertThat(new Oid("1.1.1").isPrefix(new Oid("1.1.1"))).isTrue();
		Assertions.assertThat(new Oid("1.1.1").isPrefix(new Oid("1.1.2.2"))).isFalse();
		Assertions.assertThat(new Oid("1.1.1").isPrefix(new Oid("1.1.2"))).isFalse();
		Assertions.assertThat(new Oid("1.1.1").isPrefix(new Oid("1.1"))).isFalse();

		Assertions.assertThat(new Oid("1.1.1")).isEqualTo(new Oid("1.1.1"));
		Assertions.assertThat(new Oid("1.1.1")).isNotEqualTo(new Oid("1.1.2"));

		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.1.2"))).isEqualTo(new Integer(0).compareTo(new Integer(1)));
		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.2"))).isEqualTo(new Integer(0).compareTo(new Integer(1)));
		Assertions.assertThat(new Oid("1.1.1.2").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(1).compareTo(new Integer(0)));
		Assertions.assertThat(new Oid("1.1.2").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(1).compareTo(new Integer(0)));
		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(0).compareTo(new Integer(0)));
	}

}
