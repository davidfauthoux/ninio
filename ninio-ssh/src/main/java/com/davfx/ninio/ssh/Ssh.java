package com.davfx.ninio.ssh;

import java.security.GeneralSecurityException;
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
import com.davfx.ninio.util.GlobalQueue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Ssh {
	
	private static final Config CONFIG = ConfigFactory.load(Ssh.class.getClassLoader());
	
	public static final int DEFAULT_PORT = 22;

	private Queue queue = null;
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = new SocketReadyFactory();
	private String login = CONFIG.getString("ninio.ssh.defaultLogin");
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
	
	// RSA only
	public Ssh withKey(PrivateKey privateKey, PublicKey publicKey) {
		key = new RsaSshPublicKey((RSAPrivateKey) privateKey, (RSAPublicKey) publicKey);
		return this;
	}
	// RSA only
	public Ssh withKey(Trust trust, String alias, String password) throws GeneralSecurityException {
		return withKey(trust.getPrivateKey(alias, password), trust.getPublicKey(alias));
	}

	public SshClient client() {
		Queue q = queue;
		if (q == null) {
			q = GlobalQueue.get();
		}
		return new SshClient(q, readyFactory, address, login, password, key);
	}
	
	public void download(String filePath, final FailableCloseableByteBufferHandler handler) {
		new ScpDownloadClient(client()).get(filePath, handler);
	}
}
