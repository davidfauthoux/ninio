package com.davfx.ninio.trash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.davfx.ninio.telnet.TelnetClient;
import com.davfx.ninio.telnet.util.WaitingTelnetClient;
import com.davfx.ninio.telnet.util.WaitingTelnetClientHandler;

public class TestTelnetClient {
	public static void main(String[] args) throws Exception {
		new WaitingTelnetClient(new TelnetClient()).withTimeout(1d).connect(new WaitingTelnetClientHandler() {
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
								callback.send(line, new WaitingTelnetClientHandler.Callback.SendCallback() {
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
