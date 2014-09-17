package com.davfx.ninio.trash.punch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class PunchServer {
	public Socket accept() throws IOException {
		String punchHost = "127.0.0.1";
		int punchPort = 6666;
		
		long applicationId = 1234;

		Socket socket = PunchUtils.build(punchHost, punchPort);
			try {
				final SocketAddress localPoint = socket.getLocalSocketAddress();
				
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				
	//				System.out.println("Writing application id: " + applicationId);
				//write the application id
				out.write(0); //write I am a server
				out.writeLong(applicationId);
				out.flush();
				
				final InetSocketAddress clientSocketAddress;
				while (true) {
					int action = in.read();
					if (action < 0) {
						throw new IOException("Rendezvous dead");
					} else if (action == 0) {
						//check required
						//say I am not dead
						out.write(0);
						out.flush();
					} else {
						//wait and read for the client ip/port
							System.out.println("Reading client ip / port");
						byte[] clientIp = new byte[4];
						in.readFully(clientIp);
						int clientPort = in.readInt();
						InetAddress clientAddress = InetAddress.getByAddress(clientIp);
						try {
							clientSocketAddress = new InetSocketAddress(clientAddress, clientPort);
						} catch (Exception e) {
							throw new IOException("Invalid address: " + e);
						}
							System.out.println("Client ip / port: " + clientAddress + ":" + clientPort);
						break;
					}
				}
				
				return PunchUtils.connect(localPoint, clientSocketAddress);
				
			} finally {
				socket.close();
			}
	}
	
	public static void main(String[] args) throws Exception {
		Socket socket = new PunchServer().accept();
		System.out.println("Accepted: " + socket.getInetAddress());
		new DataOutputStream(socket.getOutputStream()).writeUTF("Hello");
		Thread.sleep(1000);
	}
}
