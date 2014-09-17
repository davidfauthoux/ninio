package com.davfx.ninio.trash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

public final class EchoServer {
	public static void main(String[] args) throws IOException {
		new EchoServer(8080);
	}
	public EchoServer(int port) throws IOException {
		try (ServerSocket ss = new ServerSocket(port)) {
			while (true) {
				Socket s = ss.accept();
				final Writer out = new OutputStreamWriter(s.getOutputStream(), Charset.forName("UTF-8"));
				final BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), Charset.forName("UTF-8")));
				Executors.newSingleThreadExecutor().execute(new Runnable() {
					@Override
					public void run() {
						try {
							while (true) {
								String line = in.readLine();
								System.out.println("LINE " + line);
								if (line == null) {
									break;
								}
								out.write("ECHO " + line + "\n");
								out.flush();
							}
						} catch (IOException ioe) {
						}
					}
				});
			}
		}
	}
}
