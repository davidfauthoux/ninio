package com.davfx.ninio.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.dns.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Shorts;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class DnsClient implements DnsConnecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(DnsClient.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(DnsClient.class.getPackage().getName());
	private static final boolean SYSTEM = CONFIG.getBoolean("system");
	private static final ImmutableMap<String, String> HOSTS4;
	private static final ImmutableMap<String, String> HOSTS6;
	static {
		{
			ImmutableMap.Builder<String, String> m = ImmutableMap.builder();
			for (Config c : CONFIG.getConfigList("hosts.v4")) {
				m.put(c.getString("host"), c.getString("ip"));
			}
			HOSTS4 = m.build();
		}
		{
			ImmutableMap.Builder<String, String> m = ImmutableMap.builder();
			for (Config c : CONFIG.getConfigList("hosts.v6")) {
				m.put(c.getString("host"), c.getString("ip"));
			}
			HOSTS6 = m.build();
		}
	}

	public static final int DEFAULT_PORT = 53;

	private static final Address DEFAULT_DNS_ADDRESS;
	static {
		byte[] a;
		try {
			a = InetAddress.getByName(CONFIG.getString("default")).getAddress();
		} catch (UnknownHostException e) {
			throw new ConfigException.BadValue("default", "Invalid", e);
		}
		DEFAULT_DNS_ADDRESS = new Address(a, DEFAULT_PORT);
	}

	public static interface Builder extends NinioBuilder<DnsConnecter> {
		@Deprecated
		Builder with(Executor executor);

		Builder to(Address dnsAddress);
		Builder with(NinioBuilder<Connecter> connecterFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Address dnsAddress = DEFAULT_DNS_ADDRESS;
			private NinioBuilder<Connecter> connecterFactory = UdpSocket.builder();
			
			@Deprecated
			@Override
			public Builder with(Executor executor) {
				return this;
			}
			
			@Override
			public Builder to(Address dnsAddress) {
				this.dnsAddress = dnsAddress;
				return this;
			}
			
			@Override
			public Builder with(NinioBuilder<Connecter> connecterFactory) {
				this.connecterFactory = connecterFactory;
				return this;
			}

			@Override
			public DnsConnecter create(NinioProvider ninioProvider) {
				return new DnsClient(ninioProvider.executor(), dnsAddress, connecterFactory.create(ninioProvider));
			}
		};
	}
	
	private final Executor executor;
	private final Address dnsAddress;
	private final Connecter connecter;
	
	private final InstanceMapper instanceMapper;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();

	private DnsClient(Executor executor, Address dnsAddress, Connecter connecter) {
		this.executor = executor;
		this.dnsAddress = dnsAddress;
		this.connecter = connecter;
		instanceMapper = new InstanceMapper(requestIdProvider);
	}
	
	@Override
	public DnsRequestBuilder request() {
		return new DnsRequestBuilder() {
			private Instance instance = null;
			private String host;
			private ProtocolFamily family;
			
			@Override
			public DnsRequestBuilder resolve(String host, ProtocolFamily family) {
				this.host = host;
				this.family = family;
				return this;
			}

			public void cancel() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							instance.cancel();
						}
					}
				});
			}
			
			@Override
			public Cancelable receive(final DnsReceiver c) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							throw new IllegalStateException();
						}

						if (family == StandardProtocolFamily.INET6) {
							String r = HOSTS6.get(host);
							if (r != null) {
								host = r;
							}
						} else {
							String r = HOSTS4.get(host);
							if (r != null) {
								host = r;
							}
						}

						byte[] parsedIp;
						
						if (host == null) {
							parsedIp = new byte[] { };
						} else {
							try {
								parsedIp = InetAddresses.forString(host).getAddress();
							} catch (IllegalArgumentException e) {
								parsedIp = null;
							}

							if ((parsedIp == null) && SYSTEM) {
								LOGGER.info("Sync resolution: {}", host);
								try {
									parsedIp = InetAddress.getByName(host).getAddress();
								} catch (UnknownHostException e) {
									parsedIp = null;
								}
							}
						}
						
						if (parsedIp == null) {
							instance = new Instance(connecter, dnsAddress, instanceMapper, host, family);
						} else {
							c.received(parsedIp);
							return;
						}
						
						instance.receiver = c;
						instance.launch();
					}
				});

				return this;
			}
		};
	}
	@Override
	public void connect(final DnsConnection callback) {
		connecter.connect(new Connection() {
			@Override
			public void received(final Address address, final ByteBuffer packet) {
				executor.execute(new Runnable() {
					private String readName(ByteBuffer packet, ByteBuffer buffer) {
						if (buffer.remaining() > 1) {
							int position = buffer.position();
							int pointer = buffer.getShort() & 0xFFFF;
							if ((pointer & 0xC000) == 0xC000) {
								pointer &= 0x3FFF;
								ByteBuffer bb = packet.duplicate();
								bb.position(pointer);
								return readName(packet, bb);
							}
							buffer.position(position);
						}
						
						StringBuilder s = new StringBuilder();
						while (true) {
							int n = buffer.get() & 0xFF;
							if (n == 0) {
								break;
							}

							if (buffer.remaining() > 0) {
								if ((n & 0xC0) == 0xC0) {
									int l = buffer.get() & 0xFF;
									int pointer = ((n & 0x3F) << 16) | l;
									ByteBuffer bb = packet.duplicate();
									bb.position(pointer);
									if (s.length() > 0) {
										s.append('.');
									}
									s.append(readName(packet, bb));
									break;
								}
							}

							byte[] b = new byte[n];
							buffer.get(b);
							if (s.length() > 0) {
								s.append('.');
							}
							s.append(new String(b, Charsets.UTF_8));
						}
						return s.toString();
					}
					
					@Override
					public void run() {
						ByteBuffer buffer = packet.duplicate();
						
						short instanceId = buffer.getShort();
						int flags = buffer.getShort() & 0xFFFF;
						int questions = buffer.getShort() & 0xFFFF;
						int answers = buffer.getShort() & 0xFFFF;
						int authorityRRs = buffer.getShort() & 0xFFFF;
						int addtionalRRs = buffer.getShort() & 0xFFFF;
						LOGGER.trace("flags={}, questions={}, answers={}, authorityRRs={}, addtionalRRs={}", flags, questions, answers, authorityRRs, addtionalRRs);

						for (int i = 0; i < questions; i++) {
							String questionName = readName(packet, buffer);
							@SuppressWarnings("unused")
							int type = buffer.getShort() & 0xFFFF;
							@SuppressWarnings("unused")
							int clazz = buffer.getShort() & 0xFFFF;
							LOGGER.trace("Question#{}: {}", i, questionName);
						}
						
						List<String> nameServers = new LinkedList<>();
						Map<String, List<byte[]>> ips4 = new HashMap<>();
						Map<String, List<byte[]>> ips6 = new HashMap<>();
						Map<String, String> nameToCnames = new HashMap<>();
						for (int i = 0; i < (answers + authorityRRs + addtionalRRs); i++) {
							String name = readName(packet, buffer);
							int type = buffer.getShort() & 0xFFFF;
							@SuppressWarnings("unused")
							int clazz = buffer.getShort() & 0xFFFF;
							@SuppressWarnings("unused")
							long ttl = buffer.getInt() & 0xFFFFFFFFL;
							int n = buffer.getShort() & 0xFFFF;
							byte[] b = new byte[n];
							buffer.get(b);
							LOGGER.trace("Answer#{}: {} ({} bytes)", i, name, b.length);
							if (type == 0x1) {
								try {
									LOGGER.trace("A {}: {}", name, InetAddress.getByAddress(b));
								} catch (UnknownHostException e) {
								}
								List<byte[]> l = ips4.get(name);
								if (l == null) {
									l = new LinkedList<>();
									ips4.put(name, l);
								}
								l.add(b);
							} else if (type == 0x1C) {
								try {
									LOGGER.trace("AAAA {}: {}", name, InetAddress.getByAddress(b));
								} catch (UnknownHostException e) {
								}
								List<byte[]> l = ips6.get(name);
								if (l == null) {
									l = new LinkedList<>();
									ips6.put(name, l);
								}
								l.add(b);
							} else if (type == 0x2) {
								ByteBuffer bb = ByteBuffer.wrap(b);
								String nsName = readName(packet, bb);
								LOGGER.trace("NS {}: {}", name, nsName);
								nameServers.add(nsName);
							} else if (type == 0x5) {
								ByteBuffer bb = ByteBuffer.wrap(b);
								String cname = readName(packet, bb);
								nameToCnames.put(name, cname);
								LOGGER.trace("CNAME {}: {}", name, cname);
							} else if (type == 0x6) {
								ByteBuffer bb = ByteBuffer.wrap(b);
								String primaryNS = readName(packet, bb);
								String adminMB = readName(packet, bb);
								@SuppressWarnings("unused")
								long serialNumber = bb.getInt() & 0xFFFFFFFFL;
								@SuppressWarnings("unused")
								long refreshInterval = bb.getInt() & 0xFFFFFFFFL;
								@SuppressWarnings("unused")
								long retryInterval = bb.getInt() & 0xFFFFFFFFL;
								@SuppressWarnings("unused")
								long expirationLimit = bb.getInt() & 0xFFFFFFFFL;
								@SuppressWarnings("unused")
								long minimumTTL = bb.getInt() & 0xFFFFFFFFL;
								LOGGER.trace("SOA {}: primaryNS={}, adminMB={}", name, primaryNS, adminMB);
								//%% nameServers.add(primaryNS);
							} else if (type == 0x15) {
								ByteBuffer bb = ByteBuffer.wrap(b);
								String mx = readName(packet, bb);
								int preference = bb.getShort() & 0xFFFF;
								LOGGER.trace("MX {}: ({}) {}", name, preference, mx);
							} else {
								// Ignored
							}
						}
						
						instanceMapper.handle(instanceId, nameServers, ips4, ips6, nameToCnames);
					}
				});
			}
			
			@Override
			public void failed(IOException ioe) {
				if (callback != null) {
					callback.failed(ioe);
				}
			}
			
			@Override
			public void connected(Address address) {
				if (callback != null) {
					callback.connected(address);
				}
			}
			
			@Override
			public void closed() {
				if (callback != null) {
					callback.closed();
				}
			}
		});
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				instanceMapper.close();
			}
		});
		
		connecter.close();
	}
	
	private static final class RequestIdProvider {
		public static final short IGNORE_ID = (short) 0;
		public static final int INITIAL_ID = IGNORE_ID + 1;
		public static final int MAX_ID = Short.MAX_VALUE;
		
		private static int NEXT = INITIAL_ID;
		
		private static final Object LOCK = new Object();

		public RequestIdProvider() {
		}
		
		public short get() {
			synchronized (LOCK) {
				int n = NEXT;
				NEXT++;
				if (NEXT > MAX_ID) {
					NEXT = INITIAL_ID;
				}
				return (short) n;
			}
		}
	}
	
	private static final class InstanceMapper {
		private final RequestIdProvider requestIdProvider;
		private final Map<Short, Instance> instances = new HashMap<>();
		
		public InstanceMapper(RequestIdProvider requestIdProvider) {
			this.requestIdProvider = requestIdProvider;
		}
		
		public void map(Instance instance) {
			instances.remove(instance.instanceId);
			
			short instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached");
				return;
			}
			
			instances.put(instanceId, instance);
			
			LOGGER.trace("New instance ID = {}", instanceId);
			instance.instanceId = instanceId;
		}
		
		public void unmap(Instance instance) {
			instances.remove(instance.instanceId);
			instance.instanceId = RequestIdProvider.IGNORE_ID;
		}
		
		public void close() {
			for (Instance i : instances.values()) {
				i.close();
			}
			instances.clear();
		}

		public void handle(short instanceId, List<String> nameServers, Map<String, List<byte[]>> ips4, Map<String, List<byte[]>> ips6, Map<String, String> nameToCnames) {
			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(nameServers, ips4, ips6, nameToCnames);
		}
	}
	
	private static final class Instance {
		private final Connecter connector;
		private final InstanceMapper instanceMapper;
		
		private DnsReceiver receiver;
		
		private String host;
		//%% private String nsRequest = null;
		private final ProtocolFamily family;
		private Address dnsAddress;
		public short instanceId = RequestIdProvider.IGNORE_ID;
		
		private final Random random = new Random(System.currentTimeMillis());

		public Instance(Connecter connector, Address dnsAddress, InstanceMapper instanceMapper, String host, ProtocolFamily family) {
			this.connector = connector;
			this.dnsAddress = dnsAddress;
			this.instanceMapper = instanceMapper;
			this.host = host;
			this.family = family;
		}
		
		public void launch() {
			instanceMapper.map(this);
			write();
		}
		
		public void close() {
			dnsAddress = null;
			receiver = null;
		}
		
		public void cancel() {
			instanceMapper.unmap(this);
			dnsAddress = null;
			receiver = null;
		}
		
		private void write() {
			SendCallback sendCallback = new SendCallback() {
				@Override
				public void sent() {
				}
				@Override
				public void failed(IOException ioe) {
					fail(ioe);
				}
			};
			
			List<String> split = Splitter.on('.').splitToList(host); //%% (nsRequest == null) ? host : nsRequest);
			List<byte[]> asBytes = new LinkedList<>();
			int n = 0;
			for (String s : split) {
				byte[] bytes = s.getBytes(Charsets.UTF_8);
				asBytes.add(bytes);
				n += 1 + bytes.length;
			}
			ByteBuffer bb = ByteBuffer.allocate(Shorts.BYTES + Shorts.BYTES + (Shorts.BYTES * 4) + n + 1 + Shorts.BYTES + Shorts.BYTES);
			bb.putShort(instanceId);
			bb.putShort((short) 0x0100);
			bb.putShort((short) 0x0001);
			bb.putShort((short) 0x0000);
			bb.putShort((short) 0x0000);
			bb.putShort((short) 0x0000);
			for (byte[] b : asBytes) {
				bb.put((byte) b.length);
				bb.put(b);
			}
			bb.put((byte) 0);
			//%% bb.putShort((short) ((nsRequest == null) ? ((family == StandardProtocolFamily.INET6) ? 0x1C : 0x1) : 0xFF)); // Type 'A' or 'AAAA'
			bb.putShort((short) ((family == null) ? 0xFF : ((family == StandardProtocolFamily.INET6) ? 0x1C : 0x1))); // Type 'A' or 'AAAA'
			bb.putShort((short) 0x0001);
			bb.flip();

			connector.send(dnsAddress, bb, sendCallback);
		}
	
		private void fail(IOException e) {
			dnsAddress = null;
			if (receiver != null) {
				receiver.failed(e);
			}
			receiver = null;
		}
		
		private byte[] get(Map<String, List<byte[]>> ips, String h) {
			List<byte[]> l = ips.get(h);
			if (l == null) {
				return null;
			}
			return l.get(random.nextInt(l.size()));
		}
		private byte[] get(Map<String, List<byte[]>> ips4, Map<String, List<byte[]>> ips6, String h) {
			byte[] ip = get(ips4, h);
			if (ip == null) {
				ip = get(ips6, h);
			}
			return ip;
		}
		
		private void handle(List<String> nameServers, Map<String, List<byte[]>> ips4, Map<String, List<byte[]>> ips6, Map<String, String> nameToCnames) {
			if (dnsAddress == null) {
				return;
			}
			
			/*%%
			if (nsRequest != null) {
				byte[] nsIp = get(ips4, nsRequest);
				if (nsIp == null) {
					nsIp = get(ips6, nsRequest);
				}

				nsRequest = null;
				
				if (nsIp == null) {
					fail(new IOException("No follow-up name servers"));
				} else {
					instanceMapper.map(this);
					dnsAddress = new Address(nsIp, DEFAULT_PORT);
					write();
				}

				return;
			}
			*/
			
			String h = host;
			boolean cnamed = false;
			while (true) {
				String hh = nameToCnames.get(h);
				if (hh == null) {
					break;
				} else {
					h = hh;
					cnamed = true;
				}
			}
			host = h;
			
			byte[] ip = get(ips4, ips6, h);
			
			if (ip != null) {
				dnsAddress = null;
				if (receiver != null) {
					receiver.received(ip);
				}
				receiver = null;
			} else if (nameServers.isEmpty()) {
				if (cnamed) {
					instanceMapper.map(this);
					write();
				} else {
					fail(new IOException("No IP resolved"));
				}
			} else {
				List<byte[]> availableNameServers = new LinkedList<>();
				for (String ns : nameServers) {
					byte[] nsIp = get(ips4, ips6, ns);
					if (nsIp != null) {
						availableNameServers.add(nsIp);
					}
				}
				if (availableNameServers.isEmpty()) {
					//%% if (nameServers.isEmpty()) {
					fail(new IOException("No follow-up name servers"));
					/*%% } else {
						instanceMapper.map(this);
						nsRequest = nameServers.get(random.nextInt(nameServers.size()));
						write();
					}*/
				} else {
					instanceMapper.map(this);
					dnsAddress = new Address(availableNameServers.get(random.nextInt(availableNameServers.size())), DEFAULT_PORT);
					write();
				}
			}
		}
	}
}
