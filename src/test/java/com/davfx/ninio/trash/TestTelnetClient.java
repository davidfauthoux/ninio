package com.davfx.ninio.trash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.davfx.ninio.remote.WaitingRemoteClient;
import com.davfx.ninio.remote.WaitingRemoteClientConfigurator;
import com.davfx.ninio.remote.WaitingRemoteClientHandler;
import com.davfx.ninio.remote.telnet.TelnetRemoteConnector;
import com.davfx.ninio.telnet.TelnetClientConfigurator;

public class TestTelnetClient {
	public static void main(String[] args) throws Exception {
		new WaitingRemoteClient(new WaitingRemoteClientConfigurator().withTimeout(1d), new TelnetRemoteConnector(new TelnetClientConfigurator())).connect(new WaitingRemoteClientHandler() {
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
								callback.send(line, new WaitingRemoteClientHandler.Callback.SendCallback() {
									@Override
									public void failed(IOException e) {
										System.out.println("# FAILED");
									}
									@Override
									public void received(String text) {
										System.out.println("#@@ " + text);
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
		/*
		new TelnetClient().connect(new TelnetClientHandler() {
			@Override
			public void failed(IOException e) {
				System.out.println("FAILED");
			}
			@Override
			public void close() {
				System.out.println("CLOSED");
			}
			@Override
			public void received(String line) {
				System.out.print(line);
			}
			
			@Override
			public void launched(Callback callback) {
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
								callback.send(line + TelnetClient.EOL);
							}
							callback.close();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
				}).start();
			}
		});*/
	}
}
