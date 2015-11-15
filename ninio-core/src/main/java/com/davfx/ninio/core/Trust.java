package com.davfx.ninio.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Enumeration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Trust {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Trust.class);
	
	private final KeyStore ksKeys;
	private final SSLContext sslContext;

	public Trust(File keysTrust, String keysTrustPassPhrase) {
		this(keysTrust, keysTrustPassPhrase, keysTrust, keysTrustPassPhrase);
	}
	
	public Trust(File keys, String keysPassPhrase, File trust, String trustPassPhrase) {
		try {
			ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = new FileInputStream(keys)) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
			}
			Enumeration<String> e = ksKeys.aliases();
			while (e.hasMoreElements()) {
				String alias = e.nextElement();
				LOGGER.trace("Alias in key store: {}", alias);
			}
			KeyStore ksTrust = KeyStore.getInstance("JKS");
			try (InputStream in = new FileInputStream(trust)) {
				ksTrust.load(in, trustPassPhrase.toCharArray());
			}

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ksKeys, keysPassPhrase.toCharArray());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ksTrust);

			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Trust(String keysResourceName, String keysTrustPassPhrase) {
		this(keysResourceName, keysTrustPassPhrase, keysResourceName, keysTrustPassPhrase);
	}
	
	public Trust(String keysResourceName, String keysPassPhrase, String trustResourceName, String trustPassPhrase) {
		try {
			ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = Trust.class.getResourceAsStream(keysResourceName)) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
			}
			Enumeration<String> e = ksKeys.aliases();
			while (e.hasMoreElements()) {
				String alias = e.nextElement();
				LOGGER.trace("Alias in key store: {}", alias);
			}
			KeyStore ksTrust = KeyStore.getInstance("JKS");
			try (InputStream in = Trust.class.getResourceAsStream(trustResourceName)) {
				ksTrust.load(in, trustPassPhrase.toCharArray());
			}

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ksKeys, keysPassPhrase.toCharArray());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ksTrust);

			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Trust() {
		ksKeys = null;
		try {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, null, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public PrivateKey getPrivateKey(String alias, String password) throws GeneralSecurityException {
		return (PrivateKey) ksKeys.getKey(alias, password.toCharArray());
	}
	public PublicKey getPublicKey(String alias) throws GeneralSecurityException {
		return ksKeys.getCertificate(alias).getPublicKey();
	}
	
	SSLEngine createEngine(boolean clientMode) {
		SSLEngine engine = sslContext.createSSLEngine();
		engine.setUseClientMode(clientMode);
		return engine;
	}
}
