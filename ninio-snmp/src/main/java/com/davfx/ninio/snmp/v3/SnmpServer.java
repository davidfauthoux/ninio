package com.davfx.ninio.snmp.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.ConnectorFactory;
import com.davfx.ninio.core.v3.DatagramConnectorFactory;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.snmp.BerConstants;
import com.davfx.ninio.snmp.BerPacket;
import com.davfx.ninio.snmp.BerPacketUtils;
import com.davfx.ninio.snmp.BerReader;
import com.davfx.ninio.snmp.BytesBerPacket;
import com.davfx.ninio.snmp.IntegerBerPacket;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.OidBerPacket;
import com.davfx.ninio.snmp.SequenceBerPacket;
import com.davfx.util.Pair;

// Syntax: snmp[bulk]walk -v2c -c<anything> -On <ip>:6161 <oid>
// snmpbulkwalk -v2c -cpublic -On 127.0.0.1:6161 1.1.2
public final class SnmpServer implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpServer.class);
	
	private ConnectorFactory connectorFactory = new DatagramConnectorFactory();
	private SnmpServerHandler handler = null;

	private Connector connector = null;

	public SnmpServer() {
	}
	
	public SnmpServer with(ConnectorFactory connectorFactory) {
		this.connectorFactory = connectorFactory;
		return this;
	}
	
	public SnmpServer handle(SnmpServerHandler handler) {
		this.handler = handler;
		return this;
	}
	
	public SnmpServer connect(Address bindAddress) {
		Connector thisConnector = connector;
		connector = null;
		if (thisConnector != null) {
			thisConnector.disconnect();
		}
		connector = connectorFactory.create(bindAddress);
		thisConnector = connector;
		final Connector thisThisConnector = thisConnector;
		final SnmpServerHandler thisHandler = handler;
		
		thisConnector.receiving(new Receiver() {
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
					final List<Pair<Oid, BerPacket>> next = new LinkedList<>();

					if (thisHandler != null) {
						thisHandler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(Oid handleOid, String value) {
								// if (!oid.isPrefix(handleOid)) {
								// return false;
								// }
								if (handleOid.equals(oid)) {
									next.add(new Pair<>(handleOid, ber(value)));
									return false;
								}
								return true;
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GET {}: None", oid);
						thisThisConnector.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GET {}: {}", oid, next);
					thisThisConnector.send(address, build(requestId, community, 0, 0, next));
					return;
				}
				
				if (request == BerConstants.GETNEXT) {
					final List<Pair<Oid, BerPacket>> next = new LinkedList<>();

					if (thisHandler != null) {
						thisHandler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(Oid handleOid, String value) {
								if (handleOid.equals(oid)) {
									// Skipped
									return true;
								} else {
									if (next.isEmpty()) {
										next.add(new Pair<>(handleOid, ber(value)));
									}
									return false;
								}
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GETNEXT {}: No next", oid);
						thisThisConnector.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GETNEXT {}: {}", oid, next);
					thisThisConnector.send(address, build(requestId, community, 0, 0, next));
					return;
				}
				
				if (request == BerConstants.GETBULK) {
					final List<Pair<Oid, BerPacket>> next = new LinkedList<>();

					if (thisHandler != null) {
						thisHandler.from(oid, new SnmpServerHandler.Callback() {
							@Override
							public boolean handle(Oid handleOid, String value) {
								// if (!oid.isPrefix(handleOid)) {
								// return false;
								// }
								if (handleOid.equals(oid)) {
									// Skipped
								} else {
									next.add(new Pair<>(handleOid, ber(value)));
								}
								return next.size() < bulkLength;
							}
						});
					}

					if (next.isEmpty()) {
						LOGGER.trace("GETBULK {}: No next", oid);
						thisThisConnector.send(address, build(requestId, community, BerConstants.NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GETBULK {}: {}", oid, next);
					thisThisConnector.send(address, build(requestId, community, 0, 0, next));
					return;
				}
			}
		});
		
		thisThisConnector.connect();
		
		return this;
	}

	private static BerPacket ber(String s) {
		return new BytesBerPacket(BerPacketUtils.bytes(s));
	}
	private static ByteBuffer build(int requestId, String community, int errorStatus, int errorIndex, Iterable<Pair<Oid, BerPacket>> oidValues) {
		SequenceBerPacket oidSequence = new SequenceBerPacket(BerConstants.SEQUENCE);
		if (oidValues != null) {
			for (Pair<Oid, BerPacket> ov : oidValues) {
				oidSequence.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(new OidBerPacket(ov.first))
					.add(ov.second));
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
		Connector thisConnector = connector;
		connector = null;
		if (thisConnector != null) {
			thisConnector.disconnect();
		}
	}
	
}
