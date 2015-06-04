package com.davfx.ninio.snmp.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.DatagramReady;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.TcpdumpSyncDatagramReady;
import com.davfx.ninio.snmp.BerConstants;
import com.davfx.ninio.snmp.BerPacketUtils;
import com.davfx.ninio.snmp.BerReader;
import com.davfx.ninio.snmp.BytesBerPacket;
import com.davfx.ninio.snmp.IntegerBerPacket;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.OidBerPacket;
import com.davfx.ninio.snmp.SequenceBerPacket;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class EchoSnmpServer {
	private static final Config CONFIG = ConfigUtils.load(EchoSnmpServer.class);

	private static final Logger LOGGER = LoggerFactory.getLogger(EchoSnmpServer.class);
	
	private static final int NO_SUCH_NAME_ERROR = 2;

	private final TcpdumpSyncDatagramReady.Receiver tcpdump;

	private final Address address;
	private final Queue queue;

	public EchoSnmpServer(Queue queue, TcpdumpSyncDatagramReady.Receiver tcpdump, Address address) {
		this.queue = queue;
		this.tcpdump = tcpdump;
		this.address = address;
	}
	
	public EchoSnmpServer start() {
		Ready ready;
		if (tcpdump == null) {
			ready = new DatagramReady(queue.getSelector(), queue.allocator()).bind();
		} else {
			ready = new TcpdumpSyncDatagramReady(tcpdump).bind();
		}

		new QueueReady(queue, ready).connect(address, new ReadyConnection() {
			private CloseableByteBufferHandler write;
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				LOGGER.trace("Received request");
				
				int requestId;
				String community;
				// int bulkLength;
				int request;
				Oid oid;
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
							// bulkLength = 
							ber.readInteger();
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


				if (request == BerConstants.GET) {
					LOGGER.debug("Getting from: {}", oid);
					write.handle(address, build(requestId, community, 0, 0, oid, "GET " + oid));
					return;
				}
				
				if (request == BerConstants.GETNEXT) {
					LOGGER.debug("Getting next from: {}", oid);
					Oid next = next(oid);
					LOGGER.debug("Next: {}", next);
					if (next == null) {
						write.handle(address, build(requestId, community, NO_SUCH_NAME_ERROR, 0, null, null));
						return;
					}
					write.handle(address, build(requestId, community, 0, 0, next, "GETNEXT " + oid));
					return;
				}
				
				if (request == BerConstants.GETBULK) {
					LOGGER.debug("Getting bulk from: {}", oid);
					Oid next = next(oid);
					LOGGER.debug("Next: {}", next);
					if (next == null) {
						write.handle(address, build(requestId, community, NO_SUCH_NAME_ERROR, 0, null, null));
						return;
					}
					write.handle(address, build(requestId, community, 0, 0, next, "GETBULK " + oid));
					return;
				}
			}
			
			private Oid next(Oid oid) {
				if (oid.getRaw().length == 0) {
					return null;
				}
				int[] b = new int[oid.getRaw().length];
				System.arraycopy(oid.getRaw(), 0, b, 0, oid.getRaw().length);
				if (b[b.length - 1] > 10) {
					return null;
				}
				b[b.length - 1]++;
				return new Oid(b);
			}
			
			private ByteBuffer build(int requestId, String community, int errorStatus, int errorIndex, Oid oid, String value) {
				SequenceBerPacket oidSequence = new SequenceBerPacket(BerConstants.SEQUENCE);
				if (oid != null) {
					oidSequence.add(new SequenceBerPacket(BerConstants.SEQUENCE)
						.add(new OidBerPacket(oid))
						.add(new BytesBerPacket(BerPacketUtils.bytes(value))));
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
			public void failed(IOException e) {
				LOGGER.error("Failed", e);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				LOGGER.debug("Connected");
				this.write = write;
			}
			
			@Override
			public void close() {
			}
		});
		
		return this;
	}

	public static void main(String[] args) throws Exception {
		Address address = new Address(CONFIG.getString("snmp.server.host"), CONFIG.getInt("snmp.server.port"));
		TcpdumpSyncDatagramReady.Receiver tcpdump = CONFIG.getString("snmp.server.tcpdump.interface").isEmpty() ? null : new TcpdumpSyncDatagramReady.Receiver(new TcpdumpSyncDatagramReady.DestinationPortRule(address.getPort()), CONFIG.getString("snmp.server.tcpdump.interface"));
		EchoSnmpServer server = new EchoSnmpServer(new Queue(), tcpdump, address);
		server.start();
	}
}
