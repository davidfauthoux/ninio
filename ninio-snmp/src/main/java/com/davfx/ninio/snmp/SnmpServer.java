package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueReady;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.util.GlobalQueue;
import com.davfx.util.Pair;

// Syntax: snmp[bulk]walk -v2c -c<anything> -On <ip>:6161 <oid>
// snmpbulkwalk -v2c -cpublic -On 127.0.0.1:6161 1.1.2
public final class SnmpServer implements AutoCloseable, Closeable {
	
	public static interface Handler {
		interface Callback {
			boolean handle(Oid oid, String value);
		}
		void from(Oid oid, Callback callback);
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpServer.class);
	private static final int NO_SUCH_NAME_ERROR = 2;
	
	private final Queue queue;
	private CloseableByteBufferHandler write = null;
	private boolean closed = false;
	
	public SnmpServer(Address address, Handler handler) {
		this(GlobalQueue.get(), address, handler);
	}
	public SnmpServer(Queue queue, Address address, Handler handler) {
		this(queue, new DatagramReady(queue.getSelector(), queue.allocator()).bind(), address, handler);
	}
	public SnmpServer(Queue queue, Ready ready, Address address, final Handler handler) {
		this.queue = queue;

		new QueueReady(queue, ready).connect(address, new ReadyConnection() {
			private BerPacket ber(String s) {
				return new BytesBerPacket(BerPacketUtils.bytes(s));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
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

					handler.from(oid, new Handler.Callback() {
						@Override
						public boolean handle(Oid handleOid, String value) {
							// if (!oid.isPrefix(handleOid)) {
							// return false;
							// }
							next.add(new Pair<>(handleOid, ber(value)));
							return false;
						}
					});

					if (next.isEmpty()) {
						LOGGER.trace("GET {}: None", oid);
						write(address, build(requestId, community, NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GET {}: {}", oid, next);
					write(address, build(requestId, community, 0, 0, next));
					return;
				}
				
				if (request == BerConstants.GETNEXT) {
					final List<Pair<Oid, BerPacket>> next = new LinkedList<>();

					handler.from(oid, new Handler.Callback() {
						@Override
						public boolean handle(Oid handleOid, String value) {
							if (handleOid.equals(oid)) {
								// Skipped
							} else {
								next.add(new Pair<>(handleOid, ber(value)));
							}
							return false;
						}
					});

					if (next.isEmpty()) {
						LOGGER.trace("GETNEXT {}: No next", oid);
						write(address, build(requestId, community, NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GETNEXT {}: {}", oid, next);
					write(address, build(requestId, community, 0, 0, next));
					return;
				}
				
				if (request == BerConstants.GETBULK) {
					final List<Pair<Oid, BerPacket>> next = new LinkedList<>();

					handler.from(oid, new Handler.Callback() {
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

					if (next.isEmpty()) {
						LOGGER.trace("GETBULK {}: No next", oid);
						write(address, build(requestId, community, NO_SUCH_NAME_ERROR, 0, null));
						return;
					}

					LOGGER.trace("GETBULK {}: {}", oid, next);
					write(address, build(requestId, community, 0, 0, next));
					return;
				}
			}
			
			private void write(final Address address, final ByteBuffer b) {
				write.handle(address, b);
			}
			
			private ByteBuffer build(int requestId, String community, int errorStatus, int errorIndex, Iterable<Pair<Oid, BerPacket>> oidValues) {
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
			public void failed(IOException e) {
				LOGGER.error("Failed", e);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				LOGGER.trace("Connected");
				if (closed) {
					write.close();
				} else {
					SnmpServer.this.write = write;
				}
			}
			
			@Override
			public void close() {
			}
		});
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				closed = true;
				if (write != null) {
					write.close();
				}
			}
		});
	}
	
	/*%%%%
	public static void main(String[] args) throws Exception {
		Config config = ConfigFactory.load();
		
		final Fields fields = new Fields();
		for (Config c : config.getConfigList("fields")) {
			try (InputStream in = Main.class.getResourceAsStream(c.getString("resource"))) {
				if (in == null) {
					throw new IOException("Resource not found: " + c.getString("resource"));
				}
				String penKey;
				int pen;
				if (c.getString("pen").isEmpty()) {
					penKey = c.getString("keys.pen");
					pen = -1;
				} else {
					penKey = null;
					pen = c.getInt("pen");
				}
				fields.load(in, pen, (pen < 0) ? null : c.getString("keys.code"), c.getString("keys.name"), c.getString("keys.type"), c.getString("keys.semantics"), penKey);
			}
		}

		Mapping<PacketFieldAsValue> mapping = new Mapping<>();
		PacketField field = fields.of("INTERFACE_ID");
		mapping.of("a").put("a1_key", new PacketFieldAsValue(fields.as(field), PacketValue.of("myInterfaceId_a1")));
		mapping.of("a").put("a2_key", new PacketFieldAsValue(fields.as(field), PacketValue.of("myInterfaceId_a2")));
		mapping.of("b").put("b1_key", new PacketFieldAsValue(fields.as(field), PacketValue.of("myInterfaceId_b1")));
		mapping.of("b").put("b2_key", new PacketFieldAsValue(fields.as(field), PacketValue.of("myInterfaceId_b2")));
		
		final StringBuilder b = new StringBuilder();
		mapping.walk(new Mapping.WalkCallback<PacketFieldAsValue>() {
			@Override
			public boolean child(WalkLevel level, String key) {
				b.append(Arrays.toString(ROOT_OID) + "." + level + ":" + key + "\n");
				return true;
			}
			@Override
			public void value(WalkLevel level, PacketFieldAsValue value) {
				b.append(Arrays.toString(ROOT_OID) + "." + level + ":" + value.as.toString(value.value) + "\n");
			}
		});
		System.out.println(b);
		
		Address address = new Address(Address.LOCALHOST, 6161);
		try (SnmpServer server = new SnmpServer(fields, address, mapping)) {
			Thread.sleep(50000);
		}
	}
	*/
}
