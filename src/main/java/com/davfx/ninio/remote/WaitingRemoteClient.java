package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.Date;

import com.davfx.ninio.common.Closeable;
import com.davfx.util.DateUtils;

public final class WaitingRemoteClient implements Closeable {
	private final RemoteConnector client;
	private final WaitingRemoteClientConfigurator configurator;
	private final String eol;
	
	public WaitingRemoteClient(WaitingRemoteClientConfigurator configurator, RemoteConnector client) {
		this.client = client;
		this.configurator = configurator;
		eol = client.getEol();
	}
	
	@Override
	public void close() {
		client.close();
	}
	
	public void connect(final WaitingRemoteClientHandler clientHandler) {
		client.connect(new RemoteClientHandler() {
			private Callback clientCallback = null;
			private WaitingRemoteClientHandler.Callback.SendCallback currentCallback = null;
			private final StringBuilder text = new StringBuilder();
			
			private Date timeoutDate = null;
			private Date dateToSend = null;
			private void setDateToSend(Date now) {
				dateToSend = DateUtils.from(DateUtils.from(now) + configurator.endOfCommandTime);
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
			public void launched(final Callback callback) {
				clientCallback = callback;
				clientHandler.launched("", new WaitingRemoteClientHandler.Callback() {
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
						timeoutDate = (configurator.timeout == 0d) ? null : DateUtils.from(DateUtils.from(now) + configurator.timeout);
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
