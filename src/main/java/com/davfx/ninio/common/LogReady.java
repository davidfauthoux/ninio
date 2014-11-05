package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class LogReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(LogReady.class);
	
	private static final Config CONFIG = ConfigUtils.load(LogReady.class);

	private final Ready wrappee;
	
	public static final class Scheduled {
		private AtomicLong countIn = new AtomicLong(0L);
		private AtomicLong countOut = new AtomicLong(0L);
		
		private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		public Scheduled(final String id) {
			executor.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					LOGGER.debug("Connection stream [{}]: in {} KB, out {} KB", id, countIn.get() / 1000d, countOut.get() / 1000d);
				}
			}, 0, (long) (ConfigUtils.getDuration(CONFIG, "ninio.log.check") * 1000d), TimeUnit.MILLISECONDS);
		}
	}
	
	private final Scheduled scheduled;
	public LogReady(Scheduled scheduled, Ready wrappee) {
		this.wrappee = wrappee;
		this.scheduled = scheduled;
	}
	
	@Override
	public void connect(Address address, final ReadyConnection connection) {
		wrappee.connect(address, new ReadyConnection() {
			@Override
			public void failed(IOException e) {
				connection.failed(e);
			}
			
			@Override
			public void close() {
				connection.close();
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				scheduled.countIn.addAndGet(buffer.remaining());
				connection.handle(address, buffer);
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						write.failed(e);
					}
					@Override
					public void close() {
						write.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						scheduled.countOut.addAndGet(buffer.remaining());
						write.handle(address, buffer);
					}
				});
			}
		});
	}
}
