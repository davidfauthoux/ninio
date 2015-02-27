package com.davfx.ninio.snmp.util;

import java.io.IOException;
import java.util.Date;

import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

final class CacheFailSnmpClientHandler implements SnmpClientHandler {
	
	private static final Config CONFIG = ConfigUtils.load(CacheFailSnmpClientHandler.class);
	private static final double EXPIRATION = ConfigUtils.getDuration(CONFIG, "snmp.cache.fail.expiration");
	
	private final SnmpClientHandler wrappee;
	private Date failedExpiration = null;
	private IOException failedError = null;

	public CacheFailSnmpClientHandler(SnmpClientHandler wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void failed(IOException e) {
		wrappee.failed(e);
	}
	
	@Override
	public void launched(final Callback callback) {
		wrappee.launched(new Callback() {
			@Override
			public void close() {
				callback.close();
			}
			
			@Override
			public void get(Oid oid, final GetCallback getCallback) {
				final Date now = new Date();
				
				if (failedExpiration != null) {
					if (failedExpiration.before(now)) {
						failedExpiration = null;
						failedError = null;
					}
				}
				
				if (failedError != null) {
					getCallback.failed(failedError);
					return;
				}
				
				callback.get(oid, new GetCallback() {
					@Override
					public void failed(IOException e) {
						failedExpiration = new Date(System.currentTimeMillis() + ((long) (EXPIRATION * 1000d)));
						failedError = e;
						getCallback.failed(e);
					}
					
					@Override
					public void close() {
						getCallback.close();
					}
					
					@Override
					public void result(Result result) {
						getCallback.result(result);
					}
				});
			}
		});
	}
}
