package com.davfx.ninio.core;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.core.NativeRawSocketTest
public class NativeRawSocketTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(NativeRawSocketTest.class);

	public static void main(String[] args) throws Exception {
		byte[] pingHost = new byte[] { 8, 8, 8, 8 };
		// ::1
		int id = 1234;

		try (Ninio ninio = Ninio.create()) {
			final NativeRawSocket s = new NativeRawSocket(NativeRawSocket.PF_INET, 1);

			byte[] sendData = new byte[16];

			ByteBuffer b = ByteBuffer.wrap(sendData);
			b.put((byte) 8); // requestType (Echo)
			b.put((byte) 0); // code
			int checksumPosition = b.position();
			b.putShort((short) 0); // checksum
			b.putShort((short) ((id >>> 16) & 0xFFFF)); // identifier
			b.putShort((short) (id & 0xFFFF)); // sequence
			long nt = System.nanoTime();
			b.putLong(nt);
			int endPosition = b.position();

			b.position(0);
			int checksum = 0;
			while (b.position() < endPosition) {
				checksum += b.getShort() & 0xFFFF;
			}
		    while((checksum & 0xFFFF0000) != 0) {
		    	checksum = (checksum & 0xFFFF) + (checksum >>> 16);
		    }

		    checksum = (~checksum & 0xffff);
			b.position(checksumPosition);
			b.putShort((short) (checksum & 0xFFFF));
			b.position(endPosition);
			b.flip();
			
			s.write(pingHost, b.array(), 0, b.capacity());
			
			byte[] recvData = new byte[84];
			byte[] srcAddress = new byte[4];
			
			int r = s.read(recvData, 0, recvData.length, srcAddress);
			Address a = new Address(srcAddress, 0);
			LOGGER.debug("Received raw packet: {} bytes from: {}", r, a);

			ByteBuffer bb = ByteBuffer.wrap(recvData, 0, r);
			int headerLength = (bb.get() & 0x0F) * 4;
			bb.position(headerLength);
			
			long now = System.nanoTime();
			int type = bb.get() & 0xFF; // type
			int code = bb.get() & 0xFF; // code
			bb.getShort(); // checksum
			short identifier = bb.getShort(); // identifier
			short sequence = bb.getShort(); // sequence
			long time = bb.getLong();
			id = (int) (((identifier & 0xFFFFL) << 16) | (sequence & 0xFFFFL));

			long deltaNano = now - time;
			LOGGER.info("Received ICMP packet [{}/{}] (ID {}): {} ns", type, code, id, deltaNano);

			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(1000);
						s.close();
					} catch (Exception e) {
						LOGGER.error("Error", e);
					}
				}
			}).start();
			s.read(recvData, 0, recvData.length, srcAddress);
		}
	}}
