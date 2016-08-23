package com.davfx.ninio.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.primitives.Shorts;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class DnsClient implements DnsConnecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DnsClient.class);
	
	public static final int DEFAULT_PORT = 53;

	private static final Config CONFIG = ConfigUtils.load(DnsClient.class);
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
		Builder with(Executor executor);
		Builder to(Address dnsAddress);
		Builder with(UdpSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private Address dnsAddress = DEFAULT_DNS_ADDRESS;
			private UdpSocket.Builder connectorFactory = UdpSocket.builder();
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder to(Address dnsAddress) {
				this.dnsAddress = dnsAddress;
				return this;
			}
			
			@Override
			public Builder with(UdpSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public DnsClient create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				if (connectorFactory == null) {
					throw new NullPointerException("connectorFactory");
				}
				if (dnsAddress == null) {
					throw new NullPointerException("dnsAddress");
				}
				return new DnsClient(queue, executor, connectorFactory.create(queue), dnsAddress);
			}
		};
	}
	
	private final Queue queue;
	private final Connecter connecter;
	private final Address dnsAddress;

	public DnsClient(Queue queue, Executor executor, Connecter connecter, Address dnsAddress) {
		this.queue = queue;
		this.connecter = connecter;
		this.dnsAddress = dnsAddress;
		
		connecter.connect(new Connection() {
			private String readName(ByteBuffer packet, ByteBuffer buffer) {
				int position = buffer.position();
				int pointer = buffer.getShort() & 0xFFFF;
				System.out.println("pointer??? " + Integer.toHexString(pointer));
				if ((pointer & 0xC000) == 0xC000) {
					pointer &= 0x3FFF;
					ByteBuffer bb = packet.duplicate();
					bb.position(pointer);
					return readName(packet, bb);
				}
				buffer.position(position);
				
				StringBuilder s = new StringBuilder();
				while (true) {
					int n = buffer.get() & 0xFF;
					if (n == 0) {
						break;
					}
					System.out.println("==========n = " + n);
					byte[] b = new byte[n];
					buffer.get(b);
					if (s.length() > 0) {
						s.append('.');
					}
					s.append(new String(b, Charsets.UTF_8));
				}
				return s.toString();
			}
			private String readString(ByteBuffer packet, ByteBuffer buffer) {
				if (true) {
					int n = buffer.get() & 0xFF;
					byte[] b = new byte[n];
					buffer.get(b);
					return new String(b, Charsets.UTF_8);
				}
				int position = buffer.position();
				int pointer = buffer.getShort() & 0xFFFF;
				System.out.println("pointer??? " + Integer.toHexString(pointer));
				if ((pointer & 0xC000) == 0xC000) {
					pointer &= 0x3FFF;
					ByteBuffer bb = packet.duplicate();
					bb.position(pointer);
					return readString(packet, bb);
				}
				buffer.position(position);
				
				StringBuilder s = new StringBuilder();
				while (true) {
					int n = buffer.get() & 0xFF;
					if (n == 0) {
						break;
					}
					s.append((char) n);
				}
				return s.toString();
			}
			@Override
			public void received(Address address, ByteBuffer buffer) {
				System.out.println(Arrays.toString(buffer.array()));
				
				int transactionId = buffer.getShort() & 0xFFFF;
				int flags = buffer.getShort() & 0xFFFF;
				System.out.println("flags = " + Integer.toHexString(flags));
				int questions = buffer.getShort() & 0xFFFF;
				int answers = buffer.getShort() & 0xFFFF;
				int authorityRRs = buffer.getShort() & 0xFFFF;
				int addtionalRRs = buffer.getShort() & 0xFFFF;
				System.out.println("questions = " + questions);
				System.out.println("answers = " + answers);
				System.out.println("authorityRRs = " + authorityRRs);
				System.out.println("addtionalRRs = " + addtionalRRs);

				for (int i = 0; i < questions; i++) {
					System.out.println("QUESTION #" + i);
					System.out.println("-----NAME " + readName(buffer, buffer));
					int type = buffer.getShort() & 0xFFFF;
					int clazz = buffer.getShort() & 0xFFFF;
					System.out.println("type = " + type);
					System.out.println("clazz = " + clazz);
				}
				
				for (int i = 0; i < (answers + authorityRRs + addtionalRRs); i++) {
					System.out.println("ANSWER #" + i);
					System.out.println("-----NAME " + readName(buffer, buffer));
					int type = buffer.getShort() & 0xFFFF;
					int clazz = buffer.getShort() & 0xFFFF;
					System.out.println("type = " + type);
					System.out.println("clazz = " + clazz);
					long ttl = buffer.getInt() & 0xFFFFFFFFL;
					System.out.println("ttl = " + ttl);
					int n = buffer.getShort() & 0xFFFF;
					byte[] b = new byte[n];
					buffer.get(b);
					System.out.println("-------------------type="+type);
					if (type == 0x6) {
						System.out.println(type + " SOA (" + b.length + ")------>>>>>>>>");
						ByteBuffer bb = ByteBuffer.wrap(b);
						String primaryNS = readString(buffer, bb);
						String adminMB = readString(buffer, bb);
						long serialNumber = bb.getInt() & 0xFFFFFFFFL;
						long refreshInterval = bb.getInt() & 0xFFFFFFFFL;
						long retryInterval = bb.getInt() & 0xFFFFFFFFL;
						long expirationLimit = bb.getInt() & 0xFFFFFFFFL;
						long minimumTTL = bb.getInt() & 0xFFFFFFFFL;
						System.out.println("serialNumber = " + serialNumber);
						System.out.println("refreshInterval = " + refreshInterval);
						System.out.println("retryInterval = " + retryInterval);
						System.out.println("expirationLimit = " + expirationLimit);
						System.out.println("minimumTTL = " + minimumTTL);
						System.out.println(type + " SOA (" + b.length + ")------> " + primaryNS + "/" + adminMB);
					} else {
						System.out.println(type + "------> " + new String(b, Charsets.UTF_8) + " / " + Arrays.toString(b));

					}
				}
			}
			
			@Override
			public void connected(Address address) {
			}
			
			@Override
			public void closed() {
			}
			
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
		});
	}
	
	@Override
	public Cancelable resolve(final String host, final DnsReceiver callback) {
		List<String> split = Splitter.on('.').splitToList(host);
		List<byte[]> asBytes = new LinkedList<>();
		int n = 0;
		for (String s : split) {
			byte[] bytes = s.getBytes(Charsets.UTF_8);
			asBytes.add(bytes);
			n += 1 + bytes.length;
		}
		ByteBuffer bb = ByteBuffer.allocate(Shorts.BYTES + Shorts.BYTES + (Shorts.BYTES * 4) + n + 1 + Shorts.BYTES + Shorts.BYTES);
		bb.putShort((short) 0x023F);
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
		bb.putShort((short) 0x00ff);
		bb.putShort((short) 0x0001);
		bb.flip();
		connecter.send(dnsAddress, bb, new SendCallback() {
			@Override
			public void failed(IOException e) {
				callback.failed(e);
			}
			
			@Override
			public void sent() {
				/*
				try {
					callback.received(InetAddress.getByName(host).getAddress());
				} catch (UnknownHostException e) {
					callback.failed(e);
				}
				*/
			}
		});
		
		return new Cancelable() {
			@Override
			public void cancel() {
			}
		};
	}
	
	@Override
	public void close() {
	}
}
