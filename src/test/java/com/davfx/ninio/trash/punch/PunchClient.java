package com.davfx.ninio.trash.punch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class PunchClient {
	private static final int READ_TIMEOUT = 2000;
	private static final int MAX_TRY = 10;
	
	public Socket connect(String serverHost) throws IOException {
		String punchHost = "192.168.3.248";
		int punchPort = 6666;
		
		long applicationId = 1234;
		
			int count = 0;
			while (true) {
				Socket socket = PunchUtils.build(punchHost, punchPort);
				
				try {
					
					socket.setSoTimeout(READ_TIMEOUT);
					final SocketAddress localPoint = socket.getLocalSocketAddress();
					
					DataInputStream in = new DataInputStream(socket.getInputStream());
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					
					//write the server ip / application id
					byte[] serverIpBytes = InetAddress.getByName(serverHost).getAddress();
					
					out.write(1); //write I am a client
					out.write(serverIpBytes);
					out.writeLong(applicationId);
					out.flush();
					
					//read for the server port
					int serverPort = in.readInt();
					final InetSocketAddress serverSocketAddress = new InetSocketAddress(serverHost, serverPort);
	
					try {
						return PunchUtils.connect(localPoint, serverSocketAddress);
					} catch (IOException e) {
						count++;
						if (count == MAX_TRY)
							throw e;
					}
					
				} finally {
					try {
						socket.close();
					} catch (IOException ce) {
					}
				}
			}
	}
	
	
	public static void main(String[] args) throws Exception {
		Socket socket = new PunchClient().connect("192.168.3.248");
		System.out.println("Accepted: " + socket.getInetAddress());
		System.out.println(new DataInputStream(socket.getInputStream()).readUTF());
	}
}
