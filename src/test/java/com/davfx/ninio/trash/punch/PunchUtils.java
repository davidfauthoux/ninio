package com.davfx.ninio.trash.punch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*Reference:
	Peer-to-Peer Communication Across Network Address Translators (Bryan Ford,	Pyda Srisuresh, Dan Kegel)
	http://www.brynosaurus.com/pub/net/p2pnat/
*/

final class PunchUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PunchUtils.class);
	
	private static final int TIMEOUT = 16000; //ms
	
	private static class SocketHolder {
		Socket socket = null;
		ServerSocket serverSocket = null;
//		IOException connectError = null;
//		int countConnectError = 0; 
		IOException acceptError = null;
	}
	private static class ConnectionWait {
		boolean connecting = false;
	}
	
	private static final int DELAY_CONNECT = 200; //200ms //TODO 0?
	private static final int CONNECT_TIMEOUT = 2000;
	
	private PunchUtils() {
	}
	
	public static Socket build(String punchHost, int punchPort) throws IOException {
		InetSocketAddress a;
		try {
			a = new InetSocketAddress(punchHost, punchPort);
		} catch (Exception e) {
			throw new IOException("Invalid address: " + e);
		}
		
		Socket socket = new Socket();
		socket.setReuseAddress(true);
		socket.connect(a, CONNECT_TIMEOUT);
		return socket;
	}
	
	private static final class InnerConnectRunnable implements Runnable {
//		private final int delay;
		private final int step;
		private final SocketAddress localPoint;
		private final InetSocketAddress to;
		private final SocketHolder holder;
		private final ConnectionWait connectionWait;
		public InnerConnectRunnable(int step, SocketAddress localPoint, InetSocketAddress to, SocketHolder holder, ConnectionWait connectionWait) {
//			this.delay = delay;
			this.step = step;
			this.localPoint = localPoint;
			this.to = to;
			this.holder = holder;
			this.connectionWait = connectionWait;
		}
		public void run() {
//			try {
//				Thread.sleep(delay);
//			} catch (InterruptedException e) {
//			}
			try {
				Socket s = new Socket();
				s.setReuseAddress(true);
				s.bind(localPoint);
				System.out.println("Trying connection to: " + to);
				synchronized (connectionWait) {
					connectionWait.connecting = true;
					connectionWait.notifyAll();
				}
				s.connect(to, TIMEOUT);
				synchronized (holder) {
					if (holder.socket != null) {
						try {
							s.close();
						} catch (IOException ce) {
						}
						return;
					}
					LOGGER.trace("Punch connected (step=" + step + "): out " + to);
					holder.socket = s;
					holder.notifyAll();
				}
			} catch (IOException e) {
				LOGGER.trace("Punch failed (step=" + step + "): out " + to, e);
//				synchronized (holder) {
//					holder.countConnectError++;
//					holder.connectError = e;
//					holder.notifyAll();
//				}
			}
		}
	}
	
	private static final class InnerAcceptRunnable implements Runnable {
		private final SocketAddress localPoint;
		private final InetSocketAddress to;
		private final SocketHolder holder;
		private final ConnectionWait connectionWait;
		public InnerAcceptRunnable(SocketAddress localPoint, InetSocketAddress to, SocketHolder holder, ConnectionWait connectionWait) {
			this.localPoint = localPoint;
			this.to = to;
			this.holder = holder;
			this.connectionWait = connectionWait;
		}
		public void run() {
			try {
				synchronized (connectionWait) {
					while (!connectionWait.connecting) {
						try {
							connectionWait.wait();
		            } catch (InterruptedException ee) {
		            }
					}
				}
				//open a server socket to accept client
				ServerSocket serverSocket = new ServerSocket();
				try {
					synchronized (holder) {
						if (holder.socket != null) {
							return;
						}
						holder.serverSocket = serverSocket;
					}
					serverSocket.setReuseAddress(true);
					serverSocket.bind(localPoint);
					serverSocket.setSoTimeout(TIMEOUT);
					System.out.println("Accepting on: " + serverSocket.getLocalPort());
					Socket s = serverSocket.accept();
					synchronized (holder) {
						holder.serverSocket = null;
						if (holder.socket != null) {
							try {
								s.close();
							} catch (IOException ce) {
							}
							return;
						}
						LOGGER.trace("Punch connected: in " + to);
						holder.socket = s;
						holder.notifyAll();
					}
				} finally {
					try {
						serverSocket.close();
					} catch (IOException ce) {
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				synchronized (holder) {
					holder.acceptError = e;
					holder.notifyAll();
				}
			}
		}
	}
	
	public static Socket connect(final SocketAddress localPoint, final InetSocketAddress to) throws IOException {
		final SocketHolder holder = new SocketHolder();
		final ConnectionWait connectionWait = new ConnectionWait();
		
		Thread t = new Thread(new InnerAcceptRunnable(localPoint, to, holder, connectionWait));
		t.setName("PunchInAcceptor-" + t.getId());
		t.start();
		
		int step = 0;
		synchronized (holder) {
			while (true) {
				if (holder.socket != null) {
					if (holder.serverSocket != null) {
						try {
							holder.serverSocket.close();
						} catch (IOException ce) {
						}
					}
					return holder.socket;
				}
				
//				if (((holder.connectError != null) && (holder.countConnectError == c)) && (holder.acceptError != null)) //aucune connexion n'a reussi
//					throw holder.connectError;
				if (holder.acceptError != null)
					throw new IOException("Punch connection failed");
				
				Thread tt = new Thread(new InnerConnectRunnable(step, localPoint, to, holder, connectionWait)); //pas d'executor, ca n'a pas de sens (il faut que ce soit simultane)
				tt.setName("PunchInConnector-" + t.getId());
				tt.start();
				step++;
				
				try {
					if (DELAY_CONNECT > 0)
						holder.wait(DELAY_CONNECT);
					else
						holder.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
