package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;

public final class InternalSnmpCacheServerReadyFactory implements ReadyFactory {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSnmpCacheServerReadyFactory.class);

	private final ReadyFactory wrappee;

	public InternalSnmpCacheServerReadyFactory(ReadyFactory wrappee) {
		this.wrappee = wrappee;
		LOGGER.debug("InternalSnmpCacheServerReadyFactory in place");
	}
	
	@Override
	public Ready create() {
		LOGGER.debug("Ready created");
		final Ready wrappeeReady = wrappee.create();
		return new Ready() {
			@Override
			public void connect(Address address, final ReadyConnection connection) {
				LOGGER.debug("Connecting to: {}", address);
				wrappeeReady.connect(address, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						connection.failed(e);
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
							public void handle(Address address, ByteBuffer sourceBuffer) {
								ByteBuffer buffer = sourceBuffer.duplicate();
								int requestId;
								String community;
								final int bulkLength;
								int request;
								final Oid oid;
								try {
									BerReader ber = new BerReader(buffer);
									ber.beginReadSequence();
									{
										ber.readInteger(); // Version
										community = BerPacketUtils.string(ber.readBytes());
										request = ber.beginReadSequence();
										{
											requestId = ber.readInteger();
											ber.readInteger(); // Non-repeater, not used
											bulkLength = ber.readInteger();
											ber.beginReadSequence();
											{
												ber.beginReadSequence();
												{
													oid = ber.readOid();
													ber.readNull();
												}
												ber.endReadSequence();
											}
											ber.endReadSequence();
										}
										ber.endReadSequence();
									}
									ber.endReadSequence();
								} catch (IOException e) {
									LOGGER.error("Invalid packet", e);
									return;
								}

								LOGGER.debug("Request with community: {} and oid: {}", community, oid);
								
								//TODO Do not send if request/oid already sent recently, and register requestId<->connection
								
								write.handle(address, sourceBuffer);
							}
						});
					}

					@Override
					public void handle(Address address, ByteBuffer sourceBuffer) {
						ByteBuffer buffer = sourceBuffer.duplicate();
						int requestId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						try {
							Version2cPacketParser parser = new Version2cPacketParser(buffer);
							requestId = parser.getRequestId();
							errorStatus = parser.getErrorStatus();
							errorIndex = parser.getErrorIndex();
							results = parser.getResults();
						} catch (Exception e) {
							LOGGER.error("Invalid packet", e);
							return;
						}
						
						LOGGER.debug("Response for {}: {}", requestId, results);
						
						//TODO Put in cache and look for requestIds/connection that want this response
						
						connection.handle(address, sourceBuffer);
					}
					
					@Override
					public void close() {
						connection.close();
					}
				});
			}
		};
	}
}
