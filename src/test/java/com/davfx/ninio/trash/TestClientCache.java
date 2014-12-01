package com.davfx.ninio.trash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.remote.WaitingRemoteClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientConfigurator;
import com.davfx.ninio.remote.WaitingRemoteClientHandler;
import com.davfx.ninio.remote.telnet.TelnetRemoteConnectorFactory;
import com.davfx.ninio.telnet.TelnetClient;
import com.davfx.ninio.telnet.TelnetClientConfigurator;

public class TestClientCache {
	public static void main(String[] args) throws Exception {
		try (Queue q = new Queue(); WaitingRemoteClientCache ccc = new WaitingRemoteClientCache(new WaitingRemoteClientConfigurator(), q, new TelnetRemoteConnectorFactory(new TelnetClientConfigurator(q)))) {
			q.post(new Runnable() {
				@Override
				public void run() {
					ccc.get(new Address("localhost", TelnetClient.DEFAULT_PORT)).init("davidfauthoux", 2d).init("pass", 2d).connect(new WaitingRemoteClientHandler() {
						@Override
						public void failed(IOException e) {
							System.out.println("FAILED");
						}
						@Override
						public void close() {
							System.out.println("CLOSED");
						}
						@Override
						public void launched(String init, final Callback callback) {
							System.out.println("INIT --> " + init);
							new Thread(new Runnable() {
								@Override
								public void run() {
									for (String line : new String[] { "ls" }) {
										callback.send(line, 2d, new WaitingRemoteClientHandler.Callback.SendCallback() {
											@Override
											public void failed(IOException e) {
												System.out.println("#@ FAILED");
											}
											@Override
											public void received(String text) {
												System.out.println("#@@ " + text);
											}
										});
									}
									callback.close();
								}
							}).start();
						}
					});
				}
			});
			q.post(new Runnable() {
				@Override
				public void run() {
					ccc.get(new Address("localhost", TelnetClient.DEFAULT_PORT)).connect(new WaitingRemoteClientHandler() {
						@Override
						public void failed(IOException e) {
							System.out.println("2 FAILED");
						}
						@Override
						public void close() {
							System.out.println("2 CLOSED");
						}
						@Override
						public void launched(String init, final Callback callback) {
							System.out.println("2 INIT --> " + init);
							new Thread(new Runnable() {
								@Override
								public void run() {
									try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
										while (true) {
											String line = r.readLine();
											if (line == null) {
												break;
											}
											if (line.equals("^C")) {
												break;
											}
											callback.send(line, 2d, new WaitingRemoteClientHandler.Callback.SendCallback() {
												@Override
												public void failed(IOException e) {
													System.out.println("2 #@ FAILED");
												}
												@Override
												public void received(String text) {
													System.out.println("2 #@@ " + text);
												}
											});
										}
										callback.close();
									} catch (IOException ioe) {
										ioe.printStackTrace();
									}
								}
							}).start();
						}
					});
				}
			});
			Thread.sleep(30000);
		}
	}
}
