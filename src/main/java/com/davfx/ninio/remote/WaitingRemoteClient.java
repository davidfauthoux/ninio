package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Closeable;
import com.davfx.util.DateUtils;

public final class WaitingRemoteClient implements Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WaitingRemoteClient.class);
	
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
			private String previous = null;
			private Date sendTime = null;
			
			private Date timeoutDate = null;
			private Date dateToSend = null;
			/*%%%
			private void setDateToSend(Date now, double time) {
				dateToSend = DateUtils.from(DateUtils.from(now) + time);
			}*/
			
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
				Date now = new Date();

				if ((currentCallback != null) && (timeoutDate != null) && now.after(timeoutDate)) {
					if (clientCallback != null) {
						Callback c = clientCallback;
						clientCallback = null;
						c.close();
					}
					IOException f = new IOException("Timeout");
					WaitingRemoteClientHandler.Callback.SendCallback cc = currentCallback;
					currentCallback = null;
					cc.failed(f);
				}

				text.append(line);
			
				if ((dateToSend != null) && now.after(dateToSend)) {
					if (text.length() > 0) {
						if (currentCallback != null) {
							String r = text.toString();
							text.setLength(0);
							WaitingRemoteClientHandler.Callback.SendCallback cc = currentCallback;
							currentCallback = null;
							previous = r;
							cc.received(r);
						} else {
							if (!line.isEmpty()) {
								LOGGER.warn("Received result too late (previous result has been cut), consider increasing response time ({} -> {}). Previous was: {}, current is: {}", sendTime, dateToSend, previous, text);
							}
						}
					}
				}
				
				/*%%% if (!line.isEmpty()) {
					setDateToSend(now);
				}*/
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
					public void send(String line, double timeToResponse, SendCallback c) {
						String r = null;
						WaitingRemoteClientHandler.Callback.SendCallback cc = null;
						if (currentCallback != null) {
							cc = currentCallback;
							if (text.length() > 0) {
								r = text.toString();
								text.setLength(0);
							}
						}
						
						currentCallback = c;

						Date now = new Date();
						dateToSend = DateUtils.from(DateUtils.from(now) + timeToResponse);
						sendTime = now;
						//%% setDateToSend(now);
						timeoutDate = (configurator.timeout == 0d) ? null : DateUtils.from(DateUtils.from(now) + configurator.timeout);
						callback.send(line + eol);
						
						if (r != null) {
							cc.received(r);
						}
					}
				});
			}
		});
	}
}
