package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class OidTest {

	@Test
	public void testOidReadWrite() throws Exception {
		Oid o = new Oid("1.3.6.1.4.1.9.9.166.1.1.1.1.4.16413");
		ByteBuffer b = ByteBuffer.allocate(1024);
		new OidBerPacket(o).write(b);
		b.flip();
		Oid oid = new BerReader(b).readOid();
		Assertions.assertThat(oid).isEqualTo(o);
	}
	
	@Test
	public void test() throws Exception {
		Assertions.assertThat(new Oid("1.1.1").isPrefixOf(new Oid("1.1.1.2"))).isTrue();
		Assertions.assertThat(new Oid("1.1.1").isPrefixOf(new Oid("1.1.1"))).isTrue();
		Assertions.assertThat(new Oid("1.1.1").isPrefixOf(new Oid("1.1.2.2"))).isFalse();
		Assertions.assertThat(new Oid("1.1.1").isPrefixOf(new Oid("1.1.2"))).isFalse();
		Assertions.assertThat(new Oid("1.1.1").isPrefixOf(new Oid("1.1"))).isFalse();

		Assertions.assertThat(new Oid("1.1.1")).isEqualTo(new Oid("1.1.1"));
		Assertions.assertThat(new Oid("1.1.1")).isNotEqualTo(new Oid("1.1.2"));

		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.1.2"))).isEqualTo(new Integer(0).compareTo(new Integer(1)));
		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.2"))).isEqualTo(new Integer(0).compareTo(new Integer(1)));
		Assertions.assertThat(new Oid("1.1.1.2").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(1).compareTo(new Integer(0)));
		Assertions.assertThat(new Oid("1.1.2").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(1).compareTo(new Integer(0)));
		Assertions.assertThat(new Oid("1.1.1").compareTo(new Oid("1.1.1"))).isEqualTo(new Integer(0).compareTo(new Integer(0)));
	}

}
