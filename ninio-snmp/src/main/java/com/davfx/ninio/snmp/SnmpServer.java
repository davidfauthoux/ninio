package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.UdpSocket;

// Syntax: snmp[bulk]walk -v2c -c<anything> -On <ip>:6161 <oid>
// snmpbulkwalk -v2c -cpublic -On 127.0.0.1:6161 1.1.2
public final class SnmpServer implements Disconnectable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpServer.class);
	
	public static interface Builder extends NinioBuilder<Disconnectable> {
		Builder with(UdpSocket.Builder connectorFactory);

		Builder handle(SnmpServerHandler handler);
	}

	public static Builder builder() {
		return new Builder() {
			private UdpSocket.Builder connectorFactory = UdpSocket.builder();
			
			private SnmpServerHandler handler = null;
			
			@Override
			public Builder handle(SnmpServerHandler handler) {
				this.handler = handler;
				return this;
			}
			
			@Override
			public Builder with(UdpSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public Disconnectable create(Queue queue) {
				return new SnmpServer(connectorFactory.create(queue), handler);
			}
		};
	}

	private final Connecter connecter;
	
	private SnmpServer(final Connecter connecter, final SnmpServerHandler handler) {
		this.connecter = connecter;
		
		connecter.connect(new Connection() {
			@Override
			public void connected(Address address) {
				handler.connected(address);
			}
			
			@Override
			public void closed() {
				handler.closed();
			}
			
			@Override
			public void failed(IOException ioe) {
				handler.failed(ioe);
			}
			
			@Override
			public void received(Address address, ByteBuffer buffer) {
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

				LOGGER.trace("Request with community: {} and oid: {}", community, oid);
				
				if (request == BerConstants.GET) {
					final List<SnmpResult> next = new LinkedList<>();

					if (handler != null) {
						handler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(SnmpResult result) {
								// if (!oid.isPrefix(handleOid)) {
								// return false;
								// }
								if (result.oid.equals(oid)) {
									next.add(result);
									return false;
								}
								return true;
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GET {}: None", oid);
						connecter.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null), new Nop());
						return;
					}

					LOGGER.trace("GET {}: {}", oid, next);
					connecter.send(address, build(requestId, community, 0, 0, next), new Nop());
					return;
				}
				
				if (request == BerConstants.GETNEXT) {
					final List<SnmpResult> next = new LinkedList<>();

					if (handler != null) {
						handler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(SnmpResult result) {
								if (result.oid.equals(oid)) {
									// Skipped
									return true;
								} else {
									if (next.isEmpty()) {
										next.add(result);
									}
									return false;
								}
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GETNEXT {}: No next", oid);
						connecter.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null), new Nop());
						return;
					}

					LOGGER.trace("GETNEXT {}: {}", oid, next);
					connecter.send(address, build(requestId, community, 0, 0, next), new Nop());
					return;
				}
				
				if (request == BerConstants.GETBULK) {
					final List<SnmpResult> next = new LinkedList<>();

					if (handler != null) {
						handler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(SnmpResult result) {
								// if (!oid.isPrefix(handleOid)) {
								// return false;
								// }
								if (result.oid.equals(oid)) {
									// Skipped
								} else {
									next.add(result);
								}
								return next.size() < bulkLength;
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GETBULK {}: No next", oid);
						connecter.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null), new Nop());
						return;
					}

					LOGGER.trace("GETBULK {}: {}", oid, next);
					connecter.send(address, build(requestId, community, 0, 0, next), new Nop());
					return;
				}
			}
		});
	}

	private static BerPacket ber(String s) {
		return new BytesBerPacket(BerPacketUtils.bytes(s));
	}
	private static ByteBuffer build(int requestId, String community, int errorStatus, int errorIndex, Iterable<SnmpResult> oidValues) {
		SequenceBerPacket oidSequence = new SequenceBerPacket(BerConstants.SEQUENCE);
		if (oidValues != null) {
			for (SnmpResult ov : oidValues) {
				oidSequence.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(new OidBerPacket(ov.oid))
					.add(ber(ov.value)));
			}
		}

		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
		.add(new IntegerBerPacket(BerConstants.VERSION_2C))
		.add(new BytesBerPacket(BerPacketUtils.bytes(community)))
		.add(new SequenceBerPacket(BerConstants.RESPONSE)
			.add(new IntegerBerPacket(requestId))
			.add(new IntegerBerPacket(errorStatus))
			.add(new IntegerBerPacket(errorIndex))
			.add(oidSequence));

		ByteBuffer buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();

		return buffer;
	}

	@Override
	public void close() {
		connecter.close();
	}
	
}
