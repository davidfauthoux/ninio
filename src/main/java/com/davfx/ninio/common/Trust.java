package com.davfx.ninio.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class Trust {
	private final SSLContext sslContext;

	public Trust(File keysTrust, String keysTrustPassPhrase) throws IOException {
		this(keysTrust, keysTrustPassPhrase, keysTrust, keysTrustPassPhrase);
	}
	
	public Trust(File keys, String keysPassPhrase, File trust, String trustPassPhrase) throws IOException {
		try {
			KeyStore ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = new FileInputStream(keys)) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
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
			throw new IOException(e);
		}
	}
	
	public Trust(String keysResourceName, String keysTrustPassPhrase) throws IOException {
		this(keysResourceName, keysTrustPassPhrase, keysResourceName, keysTrustPassPhrase);
	}
	
	public Trust(String keysResourceName, String keysPassPhrase, String trustResourceName, String trustPassPhrase) throws IOException {
		try {
			KeyStore ksKeys = KeyStore.getInstance("JKS");
			try (InputStream in = Trust.class.getResourceAsStream(keysResourceName)) {
				ksKeys.load(in, keysPassPhrase.toCharArray());
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
			throw new IOException(e);
		}
	}
	
	public Trust() throws IOException {
		try {
			/*%%
			KeyStore ksTrust = KeyStore.getInstance("JKS");

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ksTrust);

			TrustManager defaultTrustManager = new SavingTrustManager((X509TrustManager) tmf.getTrustManagers()[0]);
			
			TrustManager[] t = new TrustManager[] { defaultTrustManager }
			*/
			
			TrustManager[] t = new TrustManager[] {
				new X509TrustManager() {
					@Override
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					@Override
					public void checkClientTrusted(X509Certificate[] certs, String authType) {
					}
					@Override
					public void checkServerTrusted(X509Certificate[] certs, String authType) {
					}
				}
			};
			
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, t, null);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	/*%%
	private static class SavingTrustManager implements X509TrustManager {

		private final X509TrustManager tm;
		private X509Certificate[] chain;

		SavingTrustManager(X509TrustManager tm) {
			this.tm = tm;
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			this.chain = chain;
			tm.checkServerTrusted(chain, authType);
		}
	}
	*/
	
	public SSLEngine createEngine(boolean clientMode) {
		SSLEngine engine = sslContext.createSSLEngine();
		engine.setUseClientMode(clientMode);
		return engine;
	}
}
