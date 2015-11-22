package com.davfx.ninio.ssh;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.telnet.TelnetReady;
import com.davfx.ninio.telnet.TelnetSharingReadyFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Ssh {
	
	private static final Config CONFIG = ConfigFactory.load(Ssh.class.getClassLoader());
	
	private static final String DEFAULT_LOGIN = CONFIG.getString("ninio.ssh.defaultLogin");
	//%% private static final int UPLOAD_BUFFER_SIZE = CONFIG.getBytes("ninio.ssh.upload.buffer").intValue();
	
	public static final int DEFAULT_PORT = 22;

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = new SocketReadyFactory();
	private String login = DEFAULT_LOGIN;
	private String password = null;
	private SshPublicKey key = null;

	public Ssh() {
	}

	public Ssh withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Ssh to(Address address) {
		this.address = address;
		return this;
	}
	
	public Ssh override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public Ssh withLogin(String login) {
		this.login = login;
		return this;
	}
	public Ssh withPassword(String password) {
		this.password = password;
		return this;
	}
	
	private static SshPublicKey rsaKey(PrivateKey privateKey, PublicKey publicKey) {
		return new RsaSshPublicKey((RSAPrivateKey) privateKey, (RSAPublicKey) publicKey);
	}
	
	// RSA only
	public Ssh withKey(PrivateKey privateKey, PublicKey publicKey) {
		key = rsaKey(privateKey, publicKey);
		return this;
	}
	// RSA only
	public Ssh withKey(Trust trust, String alias, String password) {
		return withKey(trust.getPrivateKey(alias, password), trust.getPublicKey(alias));
	}

	public SshClient client() {
		return new SshClient(queue, readyFactory, address, login, password, key);
	}
	
	public void download(String filePath, FailableCloseableByteBufferHandler handler) {
		new ScpDownloadClient(client()).get(filePath, handler);
	}
	/*%%
	public void upload(String filePath, long fileSize, ReadyConnection handler) {
		new ScpDownloadClient(client()).put(filePath, fileSize, handler);
	}
	public void upload(String filePath, final File source, final Failable end) {
		upload(filePath, source.length(), new ReadyConnection() {
			@Override
			public void failed(IOException e) {
				end.failed(e);
			}
			@Override
			public void close() {
				end.failed(null);
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
			}
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				try (InputStream in = new FileInputStream(source)) {
					byte[] b = new byte[UPLOAD_BUFFER_SIZE];
					while (true) {
						int l = in.read(b);
						if (l < 0) {
							break;
						}
						write.handle(null, ByteBuffer.wrap(b, 0, l));
					}
					write.close();
				} catch (IOException e) {
					write.failed(e);
					end.failed(e);
				}
			}
		});
	}
	*/

	public static TelnetSharingReadyFactory sharing(final String login, final String password) {
		return new TelnetSharingReadyFactory() {
			@Override
			public String eol() {
				return SshClient.EOL;
			}
			@Override
			public TelnetReady create(Queue queue, Address address) {
				return new SshClient(queue, new SocketReadyFactory(), address, login, password, null);
			}
		};
	}
	public static TelnetSharingReadyFactory sharing(final String login, final PrivateKey privateKey, final PublicKey publicKey) {
		return new TelnetSharingReadyFactory() {
			@Override
			public String eol() {
				return SshClient.EOL;
			}
			@Override
			public TelnetReady create(Queue queue, Address address) {
				return new SshClient(queue, new SocketReadyFactory(), address, login, null, rsaKey(privateKey, publicKey));
			}
		};
	}
	public static TelnetSharingReadyFactory sharing(final String login, final Trust trust, final String alias, final String password) {
		return new TelnetSharingReadyFactory() {
			@Override
			public String eol() {
				return SshClient.EOL;
			}
			@Override
			public TelnetReady create(Queue queue, Address address) {
				return new SshClient(queue, new SocketReadyFactory(), address, login, null, rsaKey(trust.getPrivateKey(alias, password), trust.getPublicKey(alias)));
			}
		};
	}
}
