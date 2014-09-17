package com.davfx.ninio.trash;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import com.davfx.ninio.http.Http;

public class UdpClient {
	public static void main(String[] args) throws Exception {
		try (DatagramSocket s = new DatagramSocket()) {
			byte[] b = "Hello from client".getBytes(Http.UTF8_CHARSET);
			s.send(new DatagramPacket(b, 0, b.length, InetAddress.getByName("localhost"), 8080));
			b = new byte[100_000];
			DatagramPacket p = new DatagramPacket(b, 0, b.length);
			s.receive(p);
			String r = new String(p.getData(), p.getOffset(), p.getLength(), Http.UTF8_CHARSET);
			System.out.println("RECEIVED: " + r);
		}
	}
}
