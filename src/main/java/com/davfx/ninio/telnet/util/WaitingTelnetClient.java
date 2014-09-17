package com.davfx.ninio.telnet.util;

import java.io.IOException;
import java.util.Date;

import com.davfx.ninio.telnet.TelnetClientHandler;
import com.davfx.ninio.telnet.TelnetConnector;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;

public final class WaitingTelnetClient {
	private static final Config CONFIG = ConfigUtils.load(WaitingTelnetClient.class);
	private final TelnetConnector client;
	private double endOfCommandTime = ConfigUtils.getDuration(CONFIG, "telnet.endOfCommandTime");
	private double timeout = ConfigUtils.getDuration(CONFIG, "telnet.timeout");
	private final String eol;
	
	public WaitingTelnetClient(TelnetConnector client) {
		this.client = client;
		eol = client.getEol();
	}
	
	public WaitingTelnetClient withEndOfCommandTime(double endOfCommandTime) {
		this.endOfCommandTime = endOfCommandTime;
		return this;
	}
	public WaitingTelnetClient withTimeout(double timeout) {
		this.timeout = timeout;
		return this;
	}

	public void connect(WaitingTelnetClientHandler clientHandler) {
		client.connect(new TelnetClientHandler() {
			private Callback clientCallback = null;
			private WaitingTelnetClientHandler.Callback.SendCallback currentCallback = null;
			private final StringBuilder text = new StringBuilder();
			
			private Date timeoutDate = null;
			private Date dateToSend = null;
			private void setDateToSend(Date now) {
				dateToSend = DateUtils.from(DateUtils.from(now) + endOfCommandTime);
			}
			
			@Override
			public void failed(IOException e) {
				clientHandler.failed(e);
			}
			
			@Override
			public void close() {
				clientHandler.close();
			}
			
			@Override
			public void received(String line) {
				String r = null;
				IOException f = null;
				
				text.append(line);
				Date now = new Date();
				if ((dateToSend != null) && now.after(dateToSend)) {
					if (currentCallback != null) {
						if (text.length() > 0) {
							r = text.toString();
							text.setLength(0);
							timeoutDate = null;
						}
					}
					setDateToSend(now);
				} else if (dateToSend == null) {
					setDateToSend(now);
				}

				if ((timeoutDate != null) && now.after(timeoutDate)) {
					if (currentCallback != null) {
						if (clientCallback != null) {
							clientCallback.close();
							clientCallback = null;
						}
						f = new IOException("Timeout");
					}
					timeoutDate = null;
				}

				if (f != null) {
					currentCallback.failed(f);
					currentCallback = null;
				} else if (r != null) {
					currentCallback.received(r);
				}
			}
			
			@Override
			public void launched(Callback callback) {
				clientCallback = callback;
				clientHandler.launched("", new WaitingTelnetClientHandler.Callback() {
					@Override
					public void close() {
						clientCallback = null;
						callback.close();
					}
					@Override
					public void send(String line, SendCallback c) {
						String r = null;
						if (currentCallback != null) {
							if (text.length() > 0) {
								r = text.toString();
								text.setLength(0);
							}
						}
						
						currentCallback = c;

						Date now = new Date();
						setDateToSend(now);
						timeoutDate = (timeout == 0d) ? null : DateUtils.from(DateUtils.from(now) + timeout);
						callback.send(line + eol);
						
						if (r != null) {
							currentCallback.received(r);
						}
					}
				});
			}
		});
	}
}
