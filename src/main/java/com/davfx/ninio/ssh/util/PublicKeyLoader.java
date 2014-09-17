package com.davfx.ninio.ssh.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

import com.davfx.ninio.ssh.DssSshPublicKey;
import com.davfx.ninio.ssh.RsaSshPublicKey;
import com.davfx.ninio.ssh.SshPacket;
import com.davfx.ninio.ssh.SshPublicKey;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

public final class PublicKeyLoader {
	public final SshPublicKey publicKey;

	public PublicKeyLoader(File file) throws IOException, GeneralSecurityException {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charsets.UTF_8))) {
			List<String> l = Splitter.on(' ').splitToList(r.readLine());
			String publicKeyAlgorithm = l.get(0);
			byte[] b = BaseEncoding.base64().decode(l.get(1));
			SshPacket k = new SshPacket(ByteBuffer.wrap(b));
			String s = k.readString();
			if (!s.equals(publicKeyAlgorithm)) {
				throw new IOException("Invalid algorithm: " + s);
			}
			if (publicKeyAlgorithm.equals("ssh-rsa")) {
				byte[] e = k.readBlob();
				byte[] n = k.readBlob();
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				publicKey = new RsaSshPublicKey((RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(new BigInteger(n), new BigInteger(e))));
			} else if (publicKeyAlgorithm.equals("ssh-dss")) {
				byte[] p = k.readBlob();
				byte[] q = k.readBlob();
				byte[] g = k.readBlob();
				byte[] y = k.readBlob();

				KeyFactory keyFactory = KeyFactory.getInstance("DSA");
				publicKey = new DssSshPublicKey((DSAPublicKey) keyFactory.generatePublic(new DSAPublicKeySpec(new BigInteger(y), new BigInteger(p), new BigInteger(q), new BigInteger(g))));
			} else {
				throw new IOException("Unknown algorithm: " + publicKeyAlgorithm);
			}
			// l.get(2) ignored (comment)
		}
	}
}
