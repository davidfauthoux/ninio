package com.davfx.ninio.snmp;

import java.util.TreeMap;

import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.util.GlobalQueue;

public class TestSnmpServer {

	@Test
	public void test() throws Exception {
		TreeMap<Oid, String> map = new TreeMap<>();
		map.put(new Oid("1.1.1"), "val1.1.1");
		map.put(new Oid("1.1.2"), "val1.1.2");
		new SnmpServer(GlobalQueue.get(), new Address(Address.LOCALHOST, 8080), SnmpServerUtils.from(map));
		Thread.sleep(1000000);
	}

}
