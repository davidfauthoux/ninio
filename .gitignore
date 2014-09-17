package com.davfx.ninio.trash;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import com.davfx.ninio.http.Http;

public class UdpServer {
	public static void main(String[] args) throws Exception {
		try (DatagramSocket s = new DatagramSocket(8080)) {
			while (true) {
				byte[] b = new byte[100_000];
				DatagramPacket p = new DatagramPacket(b, 0, b.length);
				s.receive(p);
				String r = new String(p.getData(), p.getOffset(), p.getLength(), Http.UTF8_CHARSET);
				System.out.println("RECEIVED: " + r);
	
				b = "Hello from server".getBytes(Http.UTF8_CHARSET);
				s.send(new DatagramPacket(b, 0, b.length, p.getSocketAddress()));
			}
		}
	}
}
