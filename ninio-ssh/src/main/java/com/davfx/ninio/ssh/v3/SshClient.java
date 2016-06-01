package com.davfx.ninio.ssh.v3;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.ssh.RsaSshPublicKey;
import com.davfx.ninio.ssh.SshPublicKey;
import com.davfx.ninio.telnet.v3.TelnetSpecification;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

public final class SshClient {
	
	public static void main(String[] args) throws Exception {
		Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");
		RsaSshPublicKey publicKey = new RsaSshPublicKey((RSAPrivateKey) trust.getPrivateKey("test-alias", "test-password"), (RSAPublicKey) trust.getPublicKey("test-alias"));

		try (Ninio ninio = Ninio.create()) {
			Connector c = ninio.create(SshClient.builder().login("davidfauthoux", publicKey).with(Executors.newSingleThreadExecutor()).to(new Address(Address.LOCALHOST, SshSpecification.DEFAULT_PORT)).receiving(new Receiver() {
				private int n = 0;
				@Override
				public void received(Connector connector, Address address, ByteBuffer buffer) {
					System.out.println(n + " ---> "+ new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), Charsets.UTF_8));
					switch (n) {
					case 0:
						connector.send(null, ByteBuffer.wrap(("ls" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					}
					n++;
				}
			}).failing(new Failing() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			}).closing(new Closing() {
				@Override
				public void closed() {
					System.out.println("CLOSED");
				}
			}).connecting(new Connecting() {
				@Override
				public void connected(Address to, Connector connector) {
					System.out.println("CONNECTED");
				}
			}).with(TcpSocket.builder()));
			try {
				Thread.sleep(100000);
			} finally {
				c.close();
			}
		}
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SshClient.class);

	private static final String CLIENT_HEADER = "SSH-2.0-ninio";

	public static interface Builder extends NinioBuilder<Connector> {
		Builder with(TcpSocket.Builder builder);
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
		Builder with(Executor executor);
		Builder to(Address connectAddress);
		Builder login(String login, String password);
		Builder login(String login, SshPublicKey publicKey);
	}

	private static final class ExchangeHolder {
		public String serverHeader;
		public byte[] clientCookie;
		public byte[] serverCookie;
		public final List<String> clientExchange = new LinkedList<>();
		public final List<String> serverExchange = new LinkedList<>();
		
		public ZlibUncompressingReceiverClosing uncompressingCloseableByteBufferHandler;
		public UncipheringReceiverClosing uncipheringCloseableByteBufferHandler;
		public CipheringConnector cipheringCloseableByteBufferHandler;
		public ZlibCompressingConnector compressingCloseableByteBufferHandler;

		public Connector rawConnector;
		public Connector clientConnector;
		
		public ExchangeHolder() {
			clientExchange.add("diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1");
			clientExchange.add("ssh-rsa");
			clientExchange.add("aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
			clientExchange.add("aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
			clientExchange.add("hmac-md5,hmac-sha1,hmac-sha2-256"); //,hmac-sha1-96,hmac-md5-96");
			clientExchange.add("hmac-md5,hmac-sha1,hmac-sha2-256"); //,hmac-sha1-96,hmac-md5-96");
			clientExchange.add("zlib@openssh.com,none");
			clientExchange.add("zlib@openssh.com,none");
			clientExchange.add("");
			clientExchange.add("");
		}
	}

	// https://tools.ietf.org/html/draft-ietf-secsh-assignednumbers-12
	private static final int SSH_MSG_DISCONNECT = 1;
	// private static final int SSH_MSG_IGNORE = 2;
	// private static final int SSH_MSG_UNIMPLEMENTED = 3;
	// private static final int SSH_MSG_DEBUG = 4;
	private static final int SSH_MSG_SERVICE_REQUEST = 5;
	private static final int SSH_MSG_SERVICE_ACCEPT = 6;
	private static final int SSH_MSG_KEXINIT = 20;
	private static final int SSH_MSG_NEWKEYS = 21;
	private static final int SSH_MSG_KEXDH_INIT = 30;
	private static final int SSH_MSG_KEXDH_REPLY = 31;
	private static final int SSH_MSG_KEX_DH_GEX_GROUP = 31;
	private static final int SSH_MSG_KEX_DH_GEX_INIT = 32;
	private static final int SSH_MSG_KEX_DH_GEX_REPLY = 33;
	private static final int SSH_MSG_KEX_DH_GEX_REQUEST = 34;
	private static final int SSH_MSG_GLOBAL_REQUEST = 80;
	// private static final int SSH_MSG_REQUEST_SUCCESS = 81;
	// private static final int SSH_MSG_REQUEST_FAILURE = 82;
	private static final int SSH_MSG_CHANNEL_OPEN = 90;
	private static final int SSH_MSG_CHANNEL_OPEN_CONFIRMATION = 91;
	// private static final int SSH_MSG_CHANNEL_OPEN_FAILURE = 92;
	private static final int SSH_MSG_CHANNEL_WINDOW_ADJUST = 93;
	private static final int SSH_MSG_CHANNEL_DATA = 94;
	private static final int SSH_MSG_CHANNEL_EXTENDED_DATA = 95;
	private static final int SSH_MSG_CHANNEL_EOF = 96;
	private static final int SSH_MSG_CHANNEL_CLOSE = 97;
	private static final int SSH_MSG_CHANNEL_REQUEST = 98;
	private static final int SSH_MSG_CHANNEL_SUCCESS = 99;
	private static final int SSH_MSG_CHANNEL_FAILURE = 100;

	private static final int SSH_MSG_USERAUTH_REQUEST = 50;
	private static final int SSH_MSG_USERAUTH_FAILURE = 51;
	private static final int SSH_MSG_USERAUTH_SUCCESS = 52;
	// private static final int SSH_MSG_USERAUTH_BANNER = 53;
	// private static final int SSH_MSG_USERAUTH_INFO_REQUEST = 60;
	// private static final int SSH_MSG_USERAUTH_INFO_RESPONSE = 61;
	private static final int SSH_MSG_USERAUTH_PK_OK = 60;

	public static Builder builder() {
		return new Builder() {
			private Receiver receiver = null;
			private Closing closing = null;
			private Failing failing = null;
			private Connecting connecting = null;
			private TcpSocket.Builder builder = null;
			
			private String login = null;
			private String password = null;
			private SshPublicKey publicKey = null;
			private String exec = null;
			
			private Address connectAddress = null;
			
			private Executor executor = null;

			@Override
			public Builder login(String login, SshPublicKey publicKey) {
				this.login = login;
				password = null;
				this.publicKey = publicKey;
				return this;
			}
			@Override
			public Builder login(String login, String password) {
				this.login = login;
				publicKey = null;
				this.password = password;
				return this;
			}
			
			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}

			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}

			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				if (login == null) {
					throw new NullPointerException("login");
				}
				if ((password == null) && (publicKey == null)) {
					throw new NullPointerException("password | publicKey");
				}
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				
				final String finalLogin = login;
				final String finalPassword = password;
				final SshPublicKey finalPublicKey = publicKey;
				final String finalExec = exec;

				final SecureRandom random = new SecureRandom();
				final ExchangeHolder exchangeHolder = new ExchangeHolder();

				final Receiver r = receiver;
				final Closing c = closing;
				final Failing clientFailing = failing;
				final Connecting clientConnecting = connecting;
				
				final Address clientConnectAddress = connectAddress;
				
				final Executor e = executor;
				
				final Receiver rawReceiver = new Receiver() {
					@Override
					public void received(Connector connector, Address address, ByteBuffer buffer) {
						if (r != null) {
							r.received(connector, address, buffer);
						}
					}
				};
				
				final Closing rawClosing = new Closing() {
					@Override
					public void closed() {
						if (c != null) {
							c.closed();
						}
					}
				};
				
				Receiver sshReceiver = new Receiver() {
					private final DiffieHellmanGroupKeyExchange keyExchange = new DiffieHellmanGroupKeyExchange();
					private boolean groupKeyExchange;
					private byte[] K;
					private byte[] H;
					private byte[] p;
					private byte[] g;
					private byte[] sessionId;
					private boolean passwordWritten = false;
					private String encryptionKeyExchangeAlgorithm;
					private boolean channelOpen = false;
					
					private long lengthToRead = 0L;
					
					@Override
					public void received(Connector receivingConnector, final Address address, final ByteBuffer buffer) {
						e.execute(new Runnable() {
							@Override
							public void run() {
								while (lengthToRead > 0L) {
									int l = buffer.remaining();
									if (lengthToRead >= l) {
										lengthToRead -= l;
										rawReceiver.received(exchangeHolder.clientConnector, address, buffer);
										return;
									}
									ByteBuffer b = buffer.duplicate();
									b.limit(b.position() + ((int) lengthToRead));
									lengthToRead = 0L;
									rawReceiver.received(exchangeHolder.clientConnector, address, b);
									buffer.position(buffer.position() + l);
								}
								
								try {
									SshPacket packet = new SshPacket(buffer);
		
									int command = packet.readByte();
		
									LOGGER.trace("Command: {}", command);
									
									if (command == SSH_MSG_KEXINIT) {
										exchangeHolder.serverCookie = new byte[16];
										for (int i = 0; i < exchangeHolder.serverCookie.length; i++) {
											exchangeHolder.serverCookie[i] = (byte) packet.readByte();
										}
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
										exchangeHolder.serverExchange.add(packet.readString());
		
										for (int i = 0; i < 5; i++) {
											int c = packet.readByte();
											if (c != 0) {
												LOGGER.warn("Should be zero: {}", c);
											}
										}
		
										String keyExchangeAlgorithm = selectServerClientCommonConfiguration(0, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										if (keyExchangeAlgorithm.equals("diffie-hellman-group1-sha1")) {
											groupKeyExchange = false;
											p = DiffieHellmanGroupKeyExchange.DiffieHellmanGroup1.p;
											g = DiffieHellmanGroupKeyExchange.DiffieHellmanGroup1.g;
										} else if (keyExchangeAlgorithm.equals("diffie-hellman-group14-sha1")) {
											groupKeyExchange = false;
											p = DiffieHellmanGroupKeyExchange.DiffieHellmanGroup14.p;
											g = DiffieHellmanGroupKeyExchange.DiffieHellmanGroup14.g;
										} else {
											groupKeyExchange = true;
										}
										
										encryptionKeyExchangeAlgorithm = selectServerClientCommonConfiguration(1, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										
										if (groupKeyExchange) {
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEX_DH_GEX_REQUEST);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MIN);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_PREFERRED);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MAX);
											exchangeHolder.rawConnector.send(null, b.finish());
										} else {
											keyExchange.init(p, g);
		
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEXDH_INIT);
											b.writeMpInt(keyExchange.getE());
											exchangeHolder.rawConnector.send(null, b.finish());
										}
		
									} else if (groupKeyExchange && (command == SSH_MSG_KEX_DH_GEX_GROUP)) {
										p = packet.readMpInt();
										g = packet.readMpInt();
										keyExchange.init(p, g);
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEX_DH_GEX_INIT);
										b.writeMpInt(keyExchange.getE());
										exchangeHolder.rawConnector.send(null, b.finish());
		
									} else if ((groupKeyExchange && (command == SSH_MSG_KEX_DH_GEX_REPLY)) || (command == SSH_MSG_KEXDH_REPLY)) {
										byte[] K_S = packet.readBlob();
										byte[] f = packet.readMpInt();
										byte[] sig = packet.readBlob();
		
										K = keyExchange.getK(f);
		
										SshPacketBuilder h = new SshPacketBuilder();
										h.writeString(CLIENT_HEADER);
										h.writeString(exchangeHolder.serverHeader);
		
										SshPacketBuilder ch = new SshPacketBuilder();
										ch.writeByte(SSH_MSG_KEXINIT);
										ch.append(exchangeHolder.clientCookie);
										for (String s : exchangeHolder.clientExchange) {
											ch.writeString(s);
										}
										ch.writeByte(0);
										ch.writeInt(0);
										h.writeBlob(ch.finish());
		
										SshPacketBuilder sh = new SshPacketBuilder();
										sh.writeByte(SSH_MSG_KEXINIT);
										sh.append(exchangeHolder.serverCookie);
										for (String s : exchangeHolder.serverExchange) {
											sh.writeString(s);
										}
										sh.writeByte(0);
										sh.writeInt(0);
										h.writeBlob(sh.finish());
		
										h.writeBlob(K_S);
										if (groupKeyExchange) {
											h.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MIN);
											h.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_PREFERRED);
											h.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MAX);
										    h.writeMpInt(p);
										    h.writeMpInt(g);
										}
										h.writeMpInt(keyExchange.getE());
										h.writeMpInt(f);
										h.writeMpInt(K);
		
										MessageDigest sha = MessageDigest.getInstance("SHA-1");
										sha.update(h.finish());
										H = sha.digest();
		
										SshPacket ksb = new SshPacket(ByteBuffer.wrap(K_S));
										String alg = ksb.readString();
										if (!alg.equals(encryptionKeyExchangeAlgorithm)) {
											// Alright, hu?
										}
		
										int off = 0;
										int len = sig.length;
										if ((sig[0] == 0) && (sig[1] == 0) && (sig[2] == 0)) {
											SshPacket sp = new SshPacket(ByteBuffer.wrap(sig));
											off = (int) sp.readInt();
											for (int o = 0; o < off; o++) {
												sp.readByte();
											}
											off = Ints.BYTES + off + Ints.BYTES;
											len = (int) sp.readInt();
										}
										ByteBuffer signed = ByteBuffer.wrap(sig, off, len);
										
										if (alg.equals("ssh-rsa")) {
											byte[] ee = ksb.readBlob();
											byte[] n = ksb.readBlob();
											KeyFactory keyFactory = KeyFactory.getInstance("RSA");
											RSAPublicKeySpec rsaPubKeySpec = new RSAPublicKeySpec(new BigInteger(n), new BigInteger(ee));
											RSAPublicKey pubKey = (RSAPublicKey) keyFactory.generatePublic(rsaPubKeySpec);
		
											if (!new RsaSshPublicKey(null, pubKey).verify(ByteBuffer.wrap(H), signed)) {
												throw new IOException("Bad signature");
											}
										} else {
											throw new IOException("Unknown key exchange algorithm: " + alg);
										}
		
										sessionId = H;
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_NEWKEYS);
										exchangeHolder.rawConnector.send(null, b.finish());
		
									} else if (command == SSH_MSG_NEWKEYS) {
										if (sessionId == null) {
											throw new IOException("Aborted key exchange");
										}
										
										String clientToServerEncryptionAlgorithmConfiguration = selectServerClientCommonConfiguration(2, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										String serverToClientEncryptionAlgorithmConfiguration = selectServerClientCommonConfiguration(3, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										String clientToServerMacAlgorithmConfiguration = selectServerClientCommonConfiguration(4, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										String serverToClientMacAlgorithmConfiguration = selectServerClientCommonConfiguration(5, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
		
										// String clientToServerCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(6, serverExchange, clientExchange);
										// String serverToClientCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(7, serverExchange, clientExchange);
		
										exchangeHolder.uncipheringCloseableByteBufferHandler.init(getEncryptionAlgorithm(serverToClientEncryptionAlgorithmConfiguration), getCipherAlgorithm(serverToClientEncryptionAlgorithmConfiguration), getKeyLength(serverToClientEncryptionAlgorithmConfiguration), getMacAlgorithm(serverToClientMacAlgorithmConfiguration), K, H, sessionId);
										exchangeHolder.cipheringCloseableByteBufferHandler.init(getEncryptionAlgorithm(clientToServerEncryptionAlgorithmConfiguration), getCipherAlgorithm(clientToServerEncryptionAlgorithmConfiguration), getKeyLength(clientToServerEncryptionAlgorithmConfiguration), getMacAlgorithm(clientToServerMacAlgorithmConfiguration), K, H, sessionId);
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_SERVICE_REQUEST);
										b.writeString("ssh-userauth");
										exchangeHolder.rawConnector.send(null, b.finish());
									} else if (command == SSH_MSG_SERVICE_ACCEPT) {
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(finalLogin);
										b.writeString("ssh-connection");
										b.writeString("none");
										exchangeHolder.rawConnector.send(null, b.finish());
									} else if (command == SSH_MSG_USERAUTH_FAILURE) {
										List<String> methods = Splitter.on(',').splitToList(packet.readString());
										int partialSuccess = packet.readByte();
		
										LOGGER.trace("Authentication methods: {}, partial success = {}", methods, partialSuccess);
										
										if (passwordWritten) {
											throw new IOException("Bad authentication");
										}
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(finalLogin);
										b.writeString("ssh-connection");
										if (finalPassword != null) {
											if (!methods.contains("password")) {
												throw new IOException("Paswword authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("password");
											b.writeByte(0);
											b.writeString(finalPassword);
										} else if (finalPublicKey != null) {
											if (!methods.contains("publickey")) {
												throw new IOException("Public key authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("publickey");
											b.writeByte(0);
											b.writeString(finalPublicKey.getAlgorithm());
											b.writeBlob(finalPublicKey.getBlob());
										} else {
											throw new IOException("No password/public key provided");
										}
										exchangeHolder.rawConnector.send(null, b.finish());
										passwordWritten = true;
									} else if (command == SSH_MSG_USERAUTH_PK_OK) {
										String alg = finalPublicKey.getAlgorithm();
		
										SshPacketBuilder toSign = new SshPacketBuilder();
										toSign.writeBlob(sessionId);
										toSign.writeByte(SSH_MSG_USERAUTH_REQUEST);
										toSign.writeString(finalLogin);
										toSign.writeString("ssh-connection");
										toSign.writeString("publickey");
										toSign.writeByte(1);
										toSign.writeString(alg);
										toSign.writeBlob(finalPublicKey.getBlob());
		
										ByteBuffer signature = finalPublicKey.sign(toSign.finish());
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(finalLogin);
										b.writeString("ssh-connection");
										b.writeString("publickey");
										b.writeByte(1);
										b.writeString(alg);
										b.writeBlob(finalPublicKey.getBlob());
										b.writeBlob(signature);
										
										exchangeHolder.rawConnector.send(null, b.finish());
										passwordWritten = true;
									} else if (command == SSH_MSG_USERAUTH_SUCCESS) {
										int channelId = 0;
										long windowSize = Integer.MAX_VALUE * 2L;
										int maxPacketSize = 64 * 1024;
		
										String clientToServerCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(6, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										String serverToClientCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(7, exchangeHolder.serverExchange, exchangeHolder.clientExchange);
										if (clientToServerCompressionAlgorithmConfiguration.equals("zlib@openssh.com")) {
											exchangeHolder.compressingCloseableByteBufferHandler.init();
										}
										if (serverToClientCompressionAlgorithmConfiguration.equals("zlib@openssh.com")) {
											exchangeHolder.uncompressingCloseableByteBufferHandler.init();
										}
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_OPEN);
										b.writeString("session");
										b.writeInt(channelId);
										b.writeInt(windowSize);
										b.writeInt(maxPacketSize);
										exchangeHolder.rawConnector.send(null, b.finish());
									} else if (command == SSH_MSG_CHANNEL_OPEN_CONFIRMATION) { // Without this command, the shell would have no prompt
										if (finalExec == null) {
											int channelId = 0;
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_REQUEST);
											b.writeInt(channelId);
											b.writeString("pty-req");
											b.writeByte(1); // With reply
											b.writeString("vt100");
											b.writeInt(80);
											b.writeInt(24);
											b.writeInt(640);
											b.writeInt(480);
											byte[] terminalModes = {};
											b.writeBlob(terminalModes);
											exchangeHolder.rawConnector.send(null, b.finish());
										} else {
											if (!channelOpen) {
												channelOpen = true;
												int channelId = 0;
												SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_REQUEST);
												b.writeInt(channelId);
												b.writeString("exec");
												b.writeByte(1); // With reply
												b.writeString(finalExec);
												exchangeHolder.rawConnector.send(null, b.finish());
											} else {
												clientConnecting.connected(clientConnectAddress, exchangeHolder.clientConnector);
											}
										}
									} else if (command == SSH_MSG_CHANNEL_SUCCESS) {
										if (!channelOpen) {
											channelOpen = true;
											int channelId = 0;
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_REQUEST);
											b.writeInt(channelId);
											b.writeString("shell");
											b.writeByte(1); // With reply
											exchangeHolder.rawConnector.send(null, b.finish());
										} else {
											clientConnecting.connected(clientConnectAddress, exchangeHolder.clientConnector);
										}
									} else if (command == SSH_MSG_CHANNEL_WINDOW_ADJUST) {
										// Ignored
									} else if (command == SSH_MSG_CHANNEL_DATA) {
										packet.readInt(); // Channel ID
										lengthToRead = packet.readInt();
										LOGGER.trace("Data length: {} / {}", lengthToRead, buffer.remaining());
										if (lengthToRead <= buffer.remaining()) {
											ByteBuffer b = buffer.duplicate();
											b.limit(b.position() + ((int) lengthToRead));
											lengthToRead = 0L;
											rawReceiver.received(exchangeHolder.clientConnector, null, b);
										} else {
											lengthToRead -= buffer.remaining();
											rawReceiver.received(exchangeHolder.clientConnector, null, buffer);
										}
									} else if (command == SSH_MSG_CHANNEL_EXTENDED_DATA) {
										packet.readInt(); // Channel ID
										long code = packet.readInt(); // Code
										LOGGER.trace("Extended data code: {}", code);
										lengthToRead = packet.readInt();
										LOGGER.trace("Extended data length: {}", lengthToRead);
										if (lengthToRead <= buffer.remaining()) {
											ByteBuffer b = buffer.duplicate();
											b.limit(b.position() + ((int) lengthToRead));
											lengthToRead = 0L;
											rawReceiver.received(exchangeHolder.clientConnector, null, b);
										} else {
											lengthToRead -= buffer.remaining();
											rawReceiver.received(exchangeHolder.clientConnector, null, buffer);
										}
									} else if (command == SSH_MSG_CHANNEL_REQUEST) {
										packet.readInt();
										String message = packet.readString();
										LOGGER.trace("Ignored channel request: {}", message);
									} else if (command == SSH_MSG_GLOBAL_REQUEST) {
										String message = packet.readString();
										LOGGER.trace("Ignored global request: {}", message);
									} else if (command == SSH_MSG_CHANNEL_EOF) {
										// Ignored
									} else if (command == SSH_MSG_CHANNEL_CLOSE) {
										exchangeHolder.rawConnector.close();
										rawClosing.closed();
									} else if (command == SSH_MSG_DISCONNECT) {
										packet.readInt();
										String message = packet.readString();
										throw new IOException("Disconnected: " + message);
									} else if (command == SSH_MSG_CHANNEL_FAILURE) {
										throw new IOException("Failure");
									} else {
										throw new IOException("Unknown command: " + command);
									}
								} catch (Exception eee) {
									LOGGER.error("Fatal error", eee);
									exchangeHolder.rawConnector.close();
									clientFailing.failed(new IOException("Fatal error", eee));
								}
							}
						});
					}
				};

				exchangeHolder.uncompressingCloseableByteBufferHandler = new ZlibUncompressingReceiverClosing(sshReceiver, rawClosing);
				SshPacketReceiverClosing sshPacketInputHandler = new SshPacketReceiverClosing(exchangeHolder.uncompressingCloseableByteBufferHandler, exchangeHolder.uncompressingCloseableByteBufferHandler);
				exchangeHolder.uncipheringCloseableByteBufferHandler = new UncipheringReceiverClosing(sshPacketInputHandler, sshPacketInputHandler);
				
				ReadingSshHeaderReceiverClosing readingSshHeaderCloseableByteBufferHandler = new ReadingSshHeaderReceiverClosing(new ReadingSshHeaderReceiverClosing.Handler() {
					@Override
					public void handle(final String header) {
						e.execute(new Runnable() {
							@Override
							public void run() {
								exchangeHolder.serverHeader = header;
		
								exchangeHolder.clientCookie = new byte[16];
								random.nextBytes(exchangeHolder.clientCookie);
		
								SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEXINIT);
								for (int i = 0; i < exchangeHolder.clientCookie.length; i++) {
									b.writeByte(exchangeHolder.clientCookie[i]);
								}
								for (String s : exchangeHolder.clientExchange) {
									b.writeString(s);
								}
								b.writeByte(0);
								b.writeInt(0);
								exchangeHolder.rawConnector.send(null, b.finish());
							}
						});
					}
				}, exchangeHolder.uncipheringCloseableByteBufferHandler, exchangeHolder.uncipheringCloseableByteBufferHandler);


				Connector rawConnector = builder
						.failing(clientFailing)
						.connecting(null) // Cleared to be consistent with other handlers set
						.closing(readingSshHeaderCloseableByteBufferHandler)
						.receiving(readingSshHeaderCloseableByteBufferHandler)
						.to(connectAddress)
						.create(queue);
				
				exchangeHolder.cipheringCloseableByteBufferHandler = new CipheringConnector(rawConnector);
				SshPacketConnector sshPacketOuputHandler = new SshPacketConnector(exchangeHolder.cipheringCloseableByteBufferHandler);
				exchangeHolder.compressingCloseableByteBufferHandler = new ZlibCompressingConnector(sshPacketOuputHandler);
				exchangeHolder.rawConnector = exchangeHolder.compressingCloseableByteBufferHandler;
				exchangeHolder.clientConnector = new Connector() {
					@Override
					public void close() {
						exchangeHolder.rawConnector.close();
					}
					@Override
					public Connector send(Address address, ByteBuffer buffer) {
						int channelId = 0;
						SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_DATA);
						b.writeInt(channelId);
						b.writeBlob(buffer);
						exchangeHolder.rawConnector.send(address, b.finish());
						return this;
					}
				};

				
				rawConnector.send(null, ByteBuffer.wrap((CLIENT_HEADER + SshSpecification.EOL).getBytes(SshSpecification.CHARSET)));

				return exchangeHolder.clientConnector;
			}
		};
	}
	
	private SshClient() {
	}
	/*
	@Override
	public void connect(final ReadyConnection clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create();
				ready.connect(address, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}

					@Override
					public void close() {
						read.close();
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						read.handle(address, buffer);
					}

					@Override
					public void connected(FailableCloseableByteBufferHandler hw) {
						CloseableByteBufferHandler w = hw;
						
						cipheringCloseableByteBufferHandler = new CipheringCloseableByteBufferHandler(w);
						w = cipheringCloseableByteBufferHandler;

						w = new SshPacketOuputHandler(w);
						
						compressingCloseableByteBufferHandler = new ZlibCompressingCloseableByteBufferHandler(w);
						w = compressingCloseableByteBufferHandler;
						
						write = w;

						CloseableByteBufferHandler handler = new CloseableByteBufferHandler() {
							
							private long lengthToRead = 0L;
							
							@Override
							public void close() {
								write.close();
							}

							@Override
							public void handle(Address address, ByteBuffer buffer) {

							}
						};
		
						uncompressingCloseableByteBufferHandler = new ZlibUncompressingCloseableByteBufferHandler(handler);
						handler = uncompressingCloseableByteBufferHandler;
						
						handler = new SshPacketInputHandler(handler);
						
						uncipheringCloseableByteBufferHandler = new UncipheringCloseableByteBufferHandler(handler);
						handler = uncipheringCloseableByteBufferHandler;

						read = new ReadingSshHeaderCloseableByteBufferHandler(new ReadingSshHeaderCloseableByteBufferHandler.Handler() {
							@Override
							public void handle(String header) {
								serverHeader = header;

								clientCookie = new byte[16];
								random.nextBytes(clientCookie);

								SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEXINIT);
								for (int i = 0; i < clientCookie.length; i++) {
									b.writeByte(clientCookie[i]);
								}
								for (String s : clientExchange) {
									b.writeString(s);
								}
								b.writeByte(0);
								b.writeInt(0);
								write.handle(null, b.finish());
							}
						}, handler);

						hw.handle(null, ByteBuffer.wrap((CLIENT_HEADER + EOL).getBytes(Charsets.UTF_8)));
					}
				});
			}
		});
	}
	*/
	private static String getEncryptionAlgorithm(String alg) {
		String s = Splitter.on('-').splitToList(alg).get(0);
		if (s.equals("aes128")) {
			return "AES";
		} else if (s.equals("3des")) {
			return "DESede";
		} else if (s.equals("blowfish")) {
			return "Blowfish";
		} else {
			return null;
		}
	}
	private static String getCipherAlgorithm(String alg) {
		return Splitter.on('-').splitToList(alg).get(1).toUpperCase();
	}
	private static int getKeyLength(String alg) {
		String s = Splitter.on('-').splitToList(alg).get(0);
		if (s.equals("aes128")) {
			return 16;
		} else if (s.equals("3des")) {
			return 24;
		} else if (s.equals("blowfish")) {
			return 16;
		} else {
			return 0;
		}
	}
	private static String getMacAlgorithm(String alg) {
		if (alg.equals("hmac-md5")) {
			return "HmacMD5";
		} else if (alg.equals("hmac-sha1")) {
			return "HmacSHA1";
		} else if (alg.equals("hmac-sha2-256")) {
			return "HmacSHA256";
		} else {
			return null;
		}
	}
	
	private static String selectServerClientCommonConfiguration(int index, List<String> serverExchange, List<String> clientExchange) throws IOException {
		List<String> s = Splitter.on(',').splitToList(serverExchange.get(index));
		List<String> c = Splitter.on(',').splitToList(clientExchange.get(index));
		for (String ss : c) {
			if (s.contains(ss)) {
				LOGGER.trace("Common configuration #{}: {}", index, ss);
				return ss;
			}
		}
		throw new IOException("No common configuration between server and client for index: " + index);
	}

}
