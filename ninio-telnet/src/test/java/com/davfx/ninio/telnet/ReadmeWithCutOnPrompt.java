package com.davfx.ninio.telnet;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

import com.davfx.ninio.core.Address;

public final class ReadmeWithCutOnPrompt {
	public static void main(String[] args) throws Exception {
		final String login = "davidfauthoux";// "<your-login>";
		final String password = "orod,ove"; //"<your-password>";
		
		final Deque<String> prompts = new LinkedList<>();
		prompts.add("login: ");
		prompts.add("Password:");
		prompts.add(login + "$");
		final Deque<String> commands = new LinkedList<>();
		commands.add(login);
		commands.add(password);
		commands.add("ls");
		new CutOnPromptClient(new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).client(), 0, new CutOnPromptClient.Handler() {
			private String command = null;
			private final Object lock = new Object();
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void close() {
				System.out.println("Closed");
			}
			@Override
			public void connected(final Write write) {
				write.setPrompt(prompts.removeFirst());
				new Thread(new Runnable() {
					@Override
					public void run() {
						while (true) {
							String c;
							synchronized (lock) {
								while (true) {
									if (command != null) {
										c = command;
										command = null;
										break;
									}
									try {
										lock.wait();
									} catch (InterruptedException e) {
									}
								}
							}
							if (c.isEmpty()) {
								System.out.println("CLOSE");
								write.close();
								break;
							} else {
								if (!prompts.isEmpty()) {
									String prompt = prompts.removeFirst();
									System.out.println("PROMPT CHANGED " + prompt);
									write.setPrompt(prompt);
								}
								System.out.println("--> " + c);
								write.write(c + TelnetSpecification.EOL);
							}
						}
					}
				}).start();
			}
			
			@Override
			public void handle(String result) {
				System.out.println("<-- " + result);

				if (commands.isEmpty()) {
					synchronized (lock) {
						command = "";
						lock.notifyAll();
					}
				} else {
					String c = commands.removeFirst();
					synchronized (lock) {
						command = c;
						lock.notifyAll();
					}
				}
			}
		});

		Thread.sleep(10000);
	}
}
