package com.davfx.ninio.trash.punch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunchRendezvous {
	
	public static void main(String[] args) throws Exception {
		new PunchRendezvous(6666);
	}
	
	private static final Logger LOGGER = LoggerFactory
			.getLogger(PunchRendezvous.class);
	
	private final ServerSocket serverSocket;
	private final Map<String, InetSocketAddress> clients = new HashMap<String, InetSocketAddress>();
	private final Map<String, Integer> ports = new HashMap<String, Integer>();
	private boolean closed = false;
	
	public PunchRendezvous(int punchPort) throws IOException {
		serverSocket = new ServerSocket(punchPort);
		Executors.newSingleThreadExecutor().execute(new InnerAccept(serverSocket));
	}
	
	public void close() {
		try {
			serverSocket.close();
		} catch (IOException e) {
		}
		synchronized (this) {
			closed = true;
			notifyAll();
		}
	}
	
	private synchronized InetSocketAddress registerAndWaitForClient(InetSocketAddress server, long applicationId) throws IOException {
		String key = server.getAddress().getHostAddress() + ":" + String.valueOf(applicationId);

		//le serveur s'enregistre
		ports.put(key, server.getPort()); //NON pas la peine: //TO DO si serveur deja enregistre (old socket close)
		notifyAll();
		
		long initTime = System.currentTimeMillis();
		while (true) {
			if (closed)
				throw new IOException("Closed");
			InetSocketAddress client = clients.remove(key);
			if (client != null) {
				notifyAll(); //j'avertis le client en attente de case libre
				return client;
			}
			long waited = System.currentTimeMillis() - initTime;
			long toWait = READ_TIMEOUT - waited;
			if (toWait <= 0) {
				//temps depasse, donc je return null pour demander au serveur s'il est toujours la
				ports.remove(key);
				return null;
			}
			try {
				wait(toWait);
			} catch (InterruptedException e) {
			}
		}

//		try {
//			long initTime = System.currentTimeMillis();
//			while (true) {
//				if (closed)
//					throw new IOException("Closed");
//				InetSocketAddress client = clients.get(key);
//				if (client != null) {
//					clients.put(key, null); //j'avertis que j'ai pris le client (en le mettant a null)
//					notifyAll(); //j'avertis le client en attente
//					return client;
//				}
//				long waited = System.currentTimeMillis() - initTime;
//				long toWait = HttpConstants.Timeouts.KeepAlive.READ - waited;
//				if (toWait <= 0) {
//					//temps depasse, donc je return null pour demander au serveur s'il est toujours la
//					return null;
//				}
//				try {
//					wait(toWait);
//				} catch (InterruptedException e) {
//				}
//			}
//		} finally {
//			ports.remove(key);
//		}
	}
	
	private synchronized int notifyAndWaitForServer(InetSocketAddress client, InetAddress serverIp, long applicationId) throws IOException {
		String key = serverIp.getHostAddress() + ":" + String.valueOf(applicationId);

		long initTime;
		
		//on attend que la case pour ce client soit libre
		initTime = System.currentTimeMillis();
		while (clients.containsKey(key)) {
			if (closed)
				throw new IOException("Closed");
			long waited = System.currentTimeMillis() - initTime;
			long toWait = CONNECT_TIMEOUT - waited;
			if (toWait <= 0)
				throw new IOException("Clients launching simultaneous connection to: " + key + ", timeout raised");
			try {
				wait(toWait);
			} catch (InterruptedException e) {
			}
		}
		clients.put(key, client);
		notifyAll();
		
		initTime = System.currentTimeMillis();
		while (true) {
			if (closed)
				throw new IOException("Closed");
			Integer port = ports.remove(key);
			if (port != null)
				return port;
			long waited = System.currentTimeMillis() - initTime;
			long toWait = CONNECT_TIMEOUT - waited;
			if (toWait <= 0) {
				clients.remove(key);
				notifyAll();
				throw new IOException("Server not registered: " + key);
			}
			try {
				wait(toWait);
			} catch (InterruptedException e) {
			}
		}
		
//		try {
//			
//			Integer port;
//			initTime = System.currentTimeMillis();
//			while (true) {
//				if (closed)
//					throw new IOException("Closed");
//				port = ports.get(key);
//				if (port != null)
//					break;
//				long waited = System.currentTimeMillis() - initTime;
//				long toWait = HttpConstants.Timeouts.Short.CONNECT - waited;
//				if (toWait <= 0)
//					throw new IOException("Server not registered: " + key);
//				try {
//					wait(toWait);
//				} catch (InterruptedException e) {
//				}
//			}
//			
//			initTime = System.currentTimeMillis();
//			while (true) {
//				if (closed)
//					throw new IOException("Closed");
//				if (clients.get(key) == null) //server has taken client
//					return port;
//				long waited = System.currentTimeMillis() - initTime;
//				long toWait = HttpConstants.Timeouts.Long.CONNECT - waited;
//				if (toWait <= 0)
//					throw new IOException("Server too slow to respond: " + key + : " + timeout raised");
//				try {
//					wait(toWait);
//				} catch (InterruptedException e) {
//				}
//			} 
//			
//		} finally {
//			clients.remove(key);
//			notifyAll();
//		}
	}
	
	private final class InnerAccept implements Runnable {
		private final ServerSocket serverSocket;
		public InnerAccept(ServerSocket serverSocket) {
			this.serverSocket = serverSocket;
		}
		public void run() {
			try {
				while (true) {
					Socket socket = serverSocket.accept();
					Executors.newSingleThreadExecutor().execute(new InnerClient(socket));
				}
			} catch (IOException e) {
				LOGGER.error("Cannot accept more client", e);
			}
		}
	}
	
	private static final int READ_TIMEOUT = 2000;
	private static final int CONNECT_TIMEOUT = 2000;
	
	private final class InnerClient implements Runnable {
		private final Socket socket;
		public InnerClient(Socket socket) {
			this.socket = socket;
		}
		public void run() {
			try {
				try {
					socket.setSoTimeout(READ_TIMEOUT);
					String address = socket.getInetAddress().getHostName();
					if (address.equals("localhost")) address = "192.168.3.248";
					int port = socket.getPort();
				
					DataInputStream in = new DataInputStream(socket.getInputStream());
					DataOutputStream out = new DataOutputStream(socket.getOutputStream());
					
					//check if it is a server or a client
					int status = in.read();
					if (status < 0) {
						//this is a server which closed immediatly
					} else if (status == 0) {
						//this is a server
						System.out.println("Server connected: " + address + ":" + port);
						//read the application id
						long applicationId = in.readLong();
//						System.out.println("Application id: " + applicationId);
						
						InetSocketAddress clientSocketAddress;
						while (true) {
							clientSocketAddress = registerAndWaitForClient(new InetSocketAddress(address, port), applicationId);
							if (clientSocketAddress != null)
								break;
							//je check si le serveur est toujours la
							out.write(0); //je lui dis que c'est un check
							out.flush();
							if (in.read() < 0)
								throw new IOException("Server dead");
						}
						
						//write the client ip/port
						
						System.out.println("Writing client ip / port: " + clientSocketAddress);
						out.write(1); //je lui dis qu'un client est connecte
						out.write(clientSocketAddress.getAddress().getAddress());
						out.writeInt(clientSocketAddress.getPort());
						out.flush();
						
					} else if (status == 1) {
						//this is a client
						System.out.println("Client connected: " + address + ":" + port);
						//read the server ip / application id
						byte[] serverIp = new byte[4];
						in.readFully(serverIp);
						long applicationId = in.readLong();
//						System.out.println("Server ip / application id: " + serverIp + ":" + applicationId);
						
						int serverPort = notifyAndWaitForServer(new InetSocketAddress(address, port), InetAddress.getByAddress(serverIp), applicationId); //timeout si trop long, donc socket non conservee en memoire (ok)
						
						//write the server port
						
						System.out.println("Writing server port: " + serverPort);
						out.writeInt(serverPort);
						out.flush();
						
					}
					
				} finally {
					socket.close();
				}
			} catch (IOException e) {
				LOGGER.trace("Error on punch rendezvous", e);
			}
		}
	}
}
