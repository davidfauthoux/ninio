package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;

public final class SshClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshClient.class);

	private static final String CLIENT_HEADER = "SSH-2.0-ninio";
	
	public static final String EOL = "\n";

	private String exec = null;

	private final Queue queue;
	private final ReadyFactory readyFactory;
	private final Address address;
	
	private final String login;
	private final String password;
	private final SshPublicKey publicKey;

	public SshClient(Queue queue, ReadyFactory readyFactory, Address address, String login, String password, SshPublicKey publicKey) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		this.address = address;
		this.login = login;
		this.password = password;
		this.publicKey = publicKey;
	}

	public SshClient exec(String exec) {
		this.exec = exec;
		return this;
	}

	public void connect(final ReadyConnection clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create(queue);
				ready.connect(address, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}

					@Override
					public void close() {
						read.close();
					}

					private CloseableByteBufferHandler write;
					private CloseableByteBufferHandler read;
					private final SecureRandom random = new SecureRandom();
					private final DiffieHellmanGroupKeyExchange keyExchange = new DiffieHellmanGroupKeyExchange();
					private boolean groupKeyExchange;
					private String serverHeader;
					private byte[] clientCookie;
					private byte[] serverCookie;
					private final List<String> clientExchange = new LinkedList<>();
					private final List<String> serverExchange = new LinkedList<>();
					private CipheringCloseableByteBufferHandler cipheringCloseableByteBufferHandler;
					private UncipheringCloseableByteBufferHandler uncipheringCloseableByteBufferHandler;
					private ZlibCompressingCloseableByteBufferHandler compressingCloseableByteBufferHandler;
					private ZlibUncompressingCloseableByteBufferHandler uncompressingCloseableByteBufferHandler;
					private byte[] K;
					private byte[] H;
					private byte[] p;
					private byte[] g;
					private byte[] sessionId;
					private boolean passwordWritten = false;
					private String encryptionKeyExchangeAlgorithm;
					
					{
						clientExchange.add("diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1");
						clientExchange.add("ssh-rsa,ssh-dss");
						clientExchange.add("aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
						clientExchange.add("aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
						clientExchange.add("hmac-md5,hmac-sha1,hmac-sha2-256"); //,hmac-sha1-96,hmac-md5-96");
						clientExchange.add("hmac-md5,hmac-sha1,hmac-sha2-256"); //,hmac-sha1-96,hmac-md5-96");
						clientExchange.add("zlib@openssh.com,none");
						clientExchange.add("zlib@openssh.com,none");
						clientExchange.add("");
						clientExchange.add("");
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
							@Override
							public void close() {
								write.close();
							}

							@Override
							public void handle(Address address, ByteBuffer buffer) {
								try {
									SshPacket packet = new SshPacket(buffer);
	
									int command = packet.readByte();

									LOGGER.debug("Command: {}", command);
									
									if (command == SshUtils.SSH_MSG_KEXINIT) {
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
											SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_KEX_DH_GEX_REQUEST);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MIN);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_PREFERRED);
											b.writeInt(DiffieHellmanGroupKeyExchange.GROUP_EXCHANGE_MAX);
											write.handle(null, b.finish());
										} else {
											keyExchange.init(p, g);

											SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_KEXDH_INIT);
											b.writeMpInt(keyExchange.getE());
											write.handle(null, b.finish());
										}

									} else if (groupKeyExchange && (command == SshUtils.SSH_MSG_KEX_DH_GEX_GROUP)) {
										p = packet.readMpInt();
										g = packet.readMpInt();
										keyExchange.init(p, g);

										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_KEX_DH_GEX_INIT);
										b.writeMpInt(keyExchange.getE());
										write.handle(null, b.finish());

									} else if ((groupKeyExchange && (command == SshUtils.SSH_MSG_KEX_DH_GEX_REPLY)) || (command == SshUtils.SSH_MSG_KEXDH_REPLY)) {
										byte[] K_S = packet.readBlob();
										byte[] f = packet.readMpInt();
										byte[] sig = packet.readBlob();
	
										K = keyExchange.getK(f);
	
										SshPacketBuilder h = new SshPacketBuilder();
										h.writeString(CLIENT_HEADER);
										h.writeString(serverHeader);
	
										SshPacketBuilder ch = new SshPacketBuilder();
										ch.writeByte(SshUtils.SSH_MSG_KEXINIT);
										ch.append(clientCookie);
										for (String s : clientExchange) {
											ch.writeString(s);
										}
										ch.writeByte(0);
										ch.writeInt(0);
										h.writeBlob(ch.finish());
	
										SshPacketBuilder sh = new SshPacketBuilder();
										sh.writeByte(SshUtils.SSH_MSG_KEXINIT);
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
										
										boolean signatureOk;
										if (alg.equals("ssh-rsa")) {
											signatureOk = new Sha1RsaSignatureVerifier().verify(ksb, H, sig, off, len);
										} else if (alg.equals("ssh-dss")) {
											signatureOk = new Sha1DssSignatureVerifier().verify(ksb, H, sig, off, len);
										} else {
											throw new IOException("Unknown key exchange algorithm: " + alg);
										}

										if (!signatureOk) {
											throw new IOException("Bad signature");
										}
	
										sessionId = H;
	
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_NEWKEYS);
										write.handle(null, b.finish());
	
									} else if (command == SshUtils.SSH_MSG_NEWKEYS) {
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
	
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_SERVICE_REQUEST);
										b.writeString("ssh-userauth");
										write.handle(null, b.finish());
									} else if (command == SshUtils.SSH_MSG_SERVICE_ACCEPT) {
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										b.writeString("none");
										write.handle(null, b.finish());
									} else if (command == SshUtils.SSH_MSG_USERAUTH_FAILURE) {
										List<String> methods = Splitter.on(',').splitToList(packet.readString());

										LOGGER.debug("Authentication methods: {}", methods);
										
										if (passwordWritten) {
											throw new IOException("Bad authentication");
										}
	
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										if (password != null) {
											if (!methods.contains("password")) {
												throw new IOException("Paswword authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("password");
											b.writeByte(0);
											b.writeString(password);
										// } else if ((publicKey != null) && (publicKeyAlgorithm != null)) {
										} else if (publicKey != null) {
											LOGGER.debug("Using key authentication");
											if (!methods.contains("publickey")) {
												throw new IOException("Public key authentication method not accepted by server, methods are: " + methods);
											}
											
											b.writeString("publickey");
											b.writeByte(0);
											b.writeString(publicKey.getAlgorithm());
											b.writeString(publicKey.getAlgorithm());
											//TODO b.writeBlob(publicKey.getBlob());
										} else {
											throw new IOException("No password/public key provided");
										}
										write.handle(null, b.finish());
										passwordWritten = true;
									} else if (command == SshUtils.SSH_MSG_USERAUTH_PK_OK) {
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										b.writeString("publickey");
										b.writeByte(1);
										String alg = publicKey.getAlgorithm();
										b.writeString(alg);
										b.writeString(publicKey.getAlgorithm());
										b.writeBlob(publicKey.getBlob()); //TODO any blob?

										ByteBuffer bb = b.finish();
										SshPacketBuilder toSign = new SshPacketBuilder();
										toSign.writeBlob(sessionId);
										toSign.writeBlob(bb);

										ByteBuffer signature = publicKey.sign(toSign.finish());

										b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_USERAUTH_REQUEST);
										b.writeString(login);
										b.writeString("ssh-connection");
										b.writeString("publickey");
										b.writeByte(1);
										b.writeString(alg);
										b.writeBlob(publicKey.getBlob());
										b.writeBlob(signature);
										
										write.handle(null, b.finish());
										passwordWritten = true;
									} else if (command == SshUtils.SSH_MSG_USERAUTH_SUCCESS) {
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

										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_CHANNEL_OPEN);
										b.writeString("session");
										b.writeInt(channelId);
										b.writeInt(windowSize);
										b.writeInt(maxPacketSize);
										write.handle(null, b.finish());
									} else if (command == SshUtils.SSH_MSG_CHANNEL_OPEN_CONFIRMATION) {
										/*
											int channelId = 0;
											SshPacketBuilder b = new SshPacketBuilder(SshUtils.SSH_MSG_CHANNEL_REQUEST);
											b.writeInt(channelId);
											b.writeString("pty-req");
											b.writeByte(1); // With reply
											b.writeString("vt100");
											b.writeInt(80);
											b.writeInt(24);
											b.writeInt(640);
											b.writeInt(480);
											b.writeString(""); // Terminal mode
											write.handle(null, b.finish());
										} else if (command == SshUtils.SSH_MSG_CHANNEL_SUCCESS) {
										*/
										
										int channelId = 0;
										SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_CHANNEL_REQUEST);
										b.writeInt(channelId);
										if (exec == null) {
											b.writeString("shell");
										} else {
											b.writeString("exec");
										}
										b.writeByte(0); // No reply
										if (exec != null) {
											b.writeString(exec);
										}
										write.handle(null, b.finish());
										
										clientHandler.connected(new FailableCloseableByteBufferHandler() {
											@Override
											public void close() {
												write.close();
											}
											@Override
											public void failed(IOException e) {
												close();
											}
											@Override
											public void handle(Address address, ByteBuffer buffer) {
												int channelId = 0;
												SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_CHANNEL_DATA);
												b.writeInt(channelId);
												b.writeBlob(buffer);
												write.handle(null, b.finish());
											}
										});
									} else if (command == SshUtils.SSH_MSG_CHANNEL_WINDOW_ADJUST) {
										// Ignored
									} else if (command == SshUtils.SSH_MSG_CHANNEL_DATA) {
										packet.readInt(); // Channel ID
										int length = (int) packet.readInt();
										ByteBuffer b = buffer.duplicate();
										b.limit(b.position() + length);
										clientHandler.handle(null, b);
									} else if (command == SshUtils.SSH_MSG_CHANNEL_EXTENDED_DATA) {
										packet.readInt(); // Channel ID
										long code = packet.readInt(); // Code
										LOGGER.debug("Extended data code: {}", code);
										int length = (int) packet.readInt();
										ByteBuffer b = buffer.duplicate();
										b.limit(b.position() + length);
										clientHandler.handle(null, b);
									} else if (command == SshUtils.SSH_MSG_CHANNEL_REQUEST) {
										packet.readInt();
										String message = packet.readString();
										LOGGER.debug("Ignored channel request: {}", message);
									} else if (command == SshUtils.SSH_MSG_GLOBAL_REQUEST) {
										// Ignored
										String message = packet.readString();
										LOGGER.debug("Ignored global request: {}", message);
									} else if (command == SshUtils.SSH_MSG_CHANNEL_EOF) {
										// Ignored
									} else if (command == SshUtils.SSH_MSG_CHANNEL_CLOSE) {
										write.close();
										clientHandler.close();
									} else if (command == SshUtils.SSH_MSG_DISCONNECT) {
										packet.readInt();
										String message = packet.readString();
										throw new IOException("Disconnected: " + message);
									} else if (command == SshUtils.SSH_MSG_CHANNEL_FAILURE) {
										throw new IOException("Failure");
									} else {
										throw new IOException("Unknown command: " + command);
									}
								} catch (Exception eee) {
									LOGGER.error("Fatal error", eee);
									write.close();
									clientHandler.failed(new IOException("Fatal error", eee));
								}
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

								SshPacketBuilder b = new SshPacketBuilder().writeByte(SshUtils.SSH_MSG_KEXINIT);
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
