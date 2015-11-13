package com.davfx.ninio.telnet;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.telnet.CutOnPromptClient.NextCommand;

public final class ReadmeWithCutOnPrompt {
	public static void main(String[] args) throws Exception {
		final String login = "<your-login>";
		final String password = "<your-password>";
		
		List<CutOnPromptClient.NextCommand> commands = new LinkedList<>();
		commands.add(new CutOnPromptClient.NextCommand("login:", login));
		commands.add(new CutOnPromptClient.NextCommand("Password:", password));
		commands.add(new CutOnPromptClient.NextCommand(login + "$", "ls"));
		final Iterator<CutOnPromptClient.NextCommand> commandsIterator = commands.iterator();
		new CutOnPromptClient(new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).client(), new CutOnPromptClient.Handler() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void close() {
				System.out.println("Closed");
			}
			@Override
			public NextCommand handle(String result) {
				System.out.println(result);
				if (commandsIterator.hasNext()) {
					return commandsIterator.next();
				} else {
					return null;
				}
			}
		});

		Thread.sleep(1000);
	}
}
