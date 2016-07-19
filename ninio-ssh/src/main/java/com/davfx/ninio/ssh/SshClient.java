package com.davfx.ninio.ssh;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

public final class SshClient implements Connecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshClient.class);

	private static final String CLIENT_HEADER = "SSH-2.0-ninio";

	public static interface Builder extends NinioBuilder<Connecter> {
		Builder with(TcpSocket.Builder builder); //TODO rename "on" ?
		Builder with(Executor executor);
		Builder login(String login, String password);
		Builder login(String login, SshPublicKey publicKey);
		Builder exec(String exec);
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
			private TcpSocket.Builder builder = null;
			
			private String login = null;
			private String password = null;
			private SshPublicKey publicKey = null;
			private String exec = null;
			
			private Executor executor = null;

			@Override
			public Builder exec(String exec) {
				this.exec = exec;
				return this;
			}
			
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
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
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
				
				return new SshClient(builder.create(queue), login, password, publicKey, exec, executor);
			}
		};
	}
	
	private final Connecter connecter;
	private final String login;
	private final String password;
	private final SshPublicKey publicKey;
	private final String exec;
	private final Executor executor;
	
	private String serverHeader;
	private byte[] clientCookie;
	private byte[] serverCookie;
	private final List<String> clientExchange = new LinkedList<>();
	private final List<String> serverExchange = new LinkedList<>();
	
	private ZlibUncompressingReceiverClosing uncompressingCloseableByteBufferHandler;
	private UncipheringReceiverClosing uncipheringCloseableByteBufferHandler;
	private CipheringConnector cipheringCloseableByteBufferHandler;
	private ZlibCompressingConnector compressingCloseableByteBufferHandler;

	private Connected connecting;
	
	private SshClient(Connecter connecter, String finalLogin, String finalPassword, SshPublicKey finalPublicKey, String finalExec, Executor e) {
		this.connecter = connecter;
		this.login = finalLogin;
		this.password = finalPassword;
		this.publicKey = finalPublicKey;
		this.exec = finalExec;
		this.executor = e;
	}
	
	//TODO proteger l'appel multiple au callback
	//TODO abruptlyClose on error
	
	@Override
	public void connect(final Connection callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final SendCallback sendCallback = new NopConnecterConnectingCallback(); //TODO fail
				
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
		
				final SecureRandom random = new SecureRandom();
		
				Connection sshReceiver = new Connection() {
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
					public void received(final Address address, final ByteBuffer buffer) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								while (lengthToRead > 0L) {
									int l = buffer.remaining();
									if (lengthToRead >= l) {
										lengthToRead -= l;
										callback.received(address, buffer);
										return;
									}
									ByteBuffer b = buffer.duplicate();
									b.limit(b.position() + ((int) lengthToRead));
									lengthToRead = 0L;
									callback.received(address, b);
									buffer.position(buffer.position() + l);
								}
								
								try {
									SshPacket packet = new SshPacket(buffer);
		
									int command = packet.readByte();
		
									LOGGER.trace("Command: {}", command);
									
									if (command == SSH_MSG_KEXINIT) {
										serverCookie = new byte[16];
										for (int i = 0; i < serverCookie.length; i++) {
											serverCookie[i] = (byte) packet.readByte();
										}
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
										serverExchange.add(packet.readString());
		
										for (int i = 0; i < 5; i++) {
											int c = packet.readByte();
											if (c != 0) {
												LOGGER.warn("Should be zero: {}", c);
											}
										}
		
										String keyExchangeAlgorithm = selectServerClientCommonConfiguration(0, serverExchange, clientExchange);
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
										
										encryptionKeyExchangeAlgorithm = selectServerClientCommonConfiguration(1, serverExchange, clientExchange);
										
										if (groupKeyExchange) {
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEX_DH_GEX_REQUEST);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MIN);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_PREFERRED);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MAX);
											connecting.send(null, b.finish(), sendCallback);
										} else {
											keyExchange.init(p, g);
		
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEXDH_INIT);
											b.writeMpInt(keyExchange.getE());
											connecting.send(null, b.finish(), sendCallback);
										}
		
									} else if (groupKeyExchange && (command == SSH_MSG_KEX_DH_GEX_GROUP)) {
										p = packet.readMpInt();
										g = packet.readMpInt();
										keyExchange.init(p, g);
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_KEX_DH_GEX_INIT);
										b.writeMpInt(keyExchange.getE());
										connecting.send(null, b.finish(), sendCallback);
		
									} else if ((groupKeyExchange && (command == SSH_MSG_KEX_DH_GEX_REPLY)) || (command == SSH_MSG_KEXDH_REPLY)) {
										byte[] K_S = packet.readBlob();
										byte[] f = packet.readMpInt();
										byte[] sig = packet.readBlob();
		
										K = keyExchange.getK(f);
		
										SshPacketBuilder h = new SshPacketBuilder();
										h.writeString(CLIENT_HEADER);
										h.writeString(serverHeader);
		
										SshPacketBuilder ch = new SshPacketBuilder();
										ch.writeByte(SSH_MSG_KEXINIT);
										ch.append(clientCookie);
										for (String s : clientExchange) {
											ch.writeString(s);
										}
										ch.writeByte(0);
										ch.writeInt(0);
										h.writeBlob(ch.finish());
		
										SshPacketBuilder sh = new SshPacketBuilder();
										sh.writeByte(SSH_MSG_KEXINIT);
										sh.append(serverCookie);
										for (String s : serverExchange) {
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
										connecting.send(null, b.finish(), sendCallback);
		
									} else if (command == SSH_MSG_NEWKEYS) {
										if (sessionId == null) {
											throw new IOException("Aborted key exchange");
										}
										
										String clientToServerEncryptionAlgorithmConfiguration = selectServerClientCommonConfiguration(2, serverExchange, clientExchange);
										String serverToClientEncryptionAlgorithmConfiguration = selectServerClientCommonConfiguration(3, serverExchange, clientExchange);
										String clientToServerMacAlgorithmConfiguration = selectServerClientCommonConfiguration(4, serverExchange, clientExchange);
										String serverToClientMacAlgorithmConfiguration = selectServerClientCommonConfiguration(5, serverExchange, clientExchange);
		
										// String clientToServerCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(6, serverExchange, clientExchange);
										// String serverToClientCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(7, serverExchange, clientExchange);
		
										uncipheringCloseableByteBufferHandler.init(getEncryptionAlgorithm(serverToClientEncryptionAlgorithmConfiguration), getCipherAlgorithm(serverToClientEncryptionAlgorithmConfiguration), getKeyLength(serverToClientEncryptionAlgorithmConfiguration), getMacAlgorithm(serverToClientMacAlgorithmConfiguration), K, H, sessionId);
										cipheringCloseableByteBufferHandler.init(getEncryptionAlgorithm(clientToServerEncryptionAlgorithmConfiguration), getCipherAlgorithm(clientToServerEncryptionAlgorithmConfiguration), getKeyLength(clientToServerEncryptionAlgorithmConfiguration), getMacAlgorithm(clientToServerMacAlgorithmConfiguration), K, H, sessionId);
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_SERVICE_REQUEST);
										b.writeString("ssh-userauth");
										connecting.send(null, b.finish(), sendCallback);
									} else if (command == SSH_MSG_SERVICE_ACCEPT) {
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										b.writeString("none");
										connecting.send(null, b.finish(), sendCallback);
									} else if (command == SSH_MSG_USERAUTH_FAILURE) {
										List<String> methods = Splitter.on(',').splitToList(packet.readString());
										int partialSuccess = packet.readByte();
		
										LOGGER.trace("Authentication methods: {}, partial success = {}", methods, partialSuccess);
										
										if (passwordWritten) {
											throw new IOException("Bad authentication");
										}
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										if (password != null) {
											if (!methods.contains("password")) {
												throw new IOException("Paswword authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("password");
											b.writeByte(0);
											b.writeString(password);
										} else if (publicKey != null) {
											if (!methods.contains("publickey")) {
												throw new IOException("Public key authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("publickey");
											b.writeByte(0);
											b.writeString(publicKey.getAlgorithm());
											b.writeBlob(publicKey.getBlob());
										} else {
											throw new IOException("No password/public key provided");
										}
										connecting.send(null, b.finish(), sendCallback);
										passwordWritten = true;
									} else if (command == SSH_MSG_USERAUTH_PK_OK) {
										String alg = publicKey.getAlgorithm();
		
										SshPacketBuilder toSign = new SshPacketBuilder();
										toSign.writeBlob(sessionId);
										toSign.writeByte(SSH_MSG_USERAUTH_REQUEST);
										toSign.writeString(login);
										toSign.writeString("ssh-connection");
										toSign.writeString("publickey");
										toSign.writeByte(1);
										toSign.writeString(alg);
										toSign.writeBlob(publicKey.getBlob());
		
										ByteBuffer signature = publicKey.sign(toSign.finish());
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										b.writeString("publickey");
										b.writeByte(1);
										b.writeString(alg);
										b.writeBlob(publicKey.getBlob());
										b.writeBlob(signature);
										
										connecting.send(null, b.finish(), sendCallback);
										passwordWritten = true;
									} else if (command == SSH_MSG_USERAUTH_SUCCESS) {
										int channelId = 0;
										long windowSize = Integer.MAX_VALUE * 2L;
										int maxPacketSize = 64 * 1024;
		
										String clientToServerCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(6, serverExchange, clientExchange);
										String serverToClientCompressionAlgorithmConfiguration = selectServerClientCommonConfiguration(7, serverExchange, clientExchange);
										if (clientToServerCompressionAlgorithmConfiguration.equals("zlib@openssh.com")) {
											compressingCloseableByteBufferHandler.init();
										}
										if (serverToClientCompressionAlgorithmConfiguration.equals("zlib@openssh.com")) {
											uncompressingCloseableByteBufferHandler.init();
										}
		
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_OPEN);
										b.writeString("session");
										b.writeInt(channelId);
										b.writeInt(windowSize);
										b.writeInt(maxPacketSize);
										connecting.send(null, b.finish(), sendCallback);
									} else if (command == SSH_MSG_CHANNEL_OPEN_CONFIRMATION) { // Without this command, the shell would have no prompt
										if (exec == null) {
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
											connecting.send(null, b.finish(), sendCallback);
										} else {
											if (!channelOpen) {
												channelOpen = true;
												int channelId = 0;
												SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_REQUEST);
												b.writeInt(channelId);
												b.writeString("exec");
												b.writeByte(1); // With reply
												b.writeString(exec);
												connecting.send(null, b.finish(), sendCallback);
											} else {
												callback.connected(null);
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
											connecting.send(null, b.finish(), sendCallback);
										} else {
											callback.connected(null);
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
											callback.received(null, b);
										} else {
											lengthToRead -= buffer.remaining();
											callback.received(null, buffer);
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
											callback.received(null, b);
										} else {
											lengthToRead -= buffer.remaining();
											callback.received(null, buffer);
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
										connecting.close();
										callback.closed();
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
									connecting.close();
									callback.failed(new IOException("Fatal error", eee));
								}
							}
						});
					}
					
					@Override
					public void closed() {
						callback.closed();
					}
					@Override
					public void failed(IOException ioe) {
						callback.failed(ioe);
					}
					@Override
					public void connected(Address address) {
					}
				};
		
				uncompressingCloseableByteBufferHandler = new ZlibUncompressingReceiverClosing(sshReceiver);
				SshPacketReceiverClosing sshPacketInputHandler = new SshPacketReceiverClosing(uncompressingCloseableByteBufferHandler);
				uncipheringCloseableByteBufferHandler = new UncipheringReceiverClosing(sshPacketInputHandler);
				
				ReadingSshHeaderReceiverClosing readingSshHeaderCloseableByteBufferHandler = new ReadingSshHeaderReceiverClosing(new ReadingSshHeaderReceiverClosing.Handler() {
					@Override
					public void handle(final String header) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
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
								connecting.send(null, b.finish(), sendCallback);
							}
						});
					}
				}, uncipheringCloseableByteBufferHandler);
		
		
				connecter.connect(readingSshHeaderCloseableByteBufferHandler);

				cipheringCloseableByteBufferHandler = new CipheringConnector(connecter);
				SshPacketConnector sshPacketOuputHandler = new SshPacketConnector(cipheringCloseableByteBufferHandler);
				compressingCloseableByteBufferHandler = new ZlibCompressingConnector(sshPacketOuputHandler);
				connecting = compressingCloseableByteBufferHandler;
				
				connecter.send(null, ByteBuffer.wrap((CLIENT_HEADER + SshSpecification.EOL).getBytes(SshSpecification.CHARSET)), sendCallback);
			}
		});
	}

	@Override
	public void send(final Address address, final ByteBuffer buffer, final SendCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				int channelId = 0;
				SshPacketBuilder b = new SshPacketBuilder().writeByte(SSH_MSG_CHANNEL_DATA);
				b.writeInt(channelId);
				b.writeBlob(buffer);
				connecting.send(address, b.finish(), callback);
			}
		});
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				connecting.close();
			}
		});
	}

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
