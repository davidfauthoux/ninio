package com.davfx.ninio.snmp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.davfx.ninio.util.MemoryCache;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

final class EncryptionEngine {
	private final MessageDigest messageDigest;
	private final MemoryCache<String, byte[]> cache;
    
	public EncryptionEngine(String authDigestAlgorithm, double cacheDuration) {
		try {
			messageDigest = MessageDigest.getInstance(authDigestAlgorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		cache = MemoryCache.<String, byte[]> builder().expireAfterAccess(cacheDuration).build();
	}

	public byte[] regenerateKey(byte[] id, String password) {
		if (id == null) {
			id = new byte[] {};
		}
		
		String k = BaseEncoding.base64().encode(id) + " " + password; // Space is a safe separator (not in the regular Base64 characters)
		byte[] d = cache.get(k);
		if (d == null) {
			byte[] passwordBytes = password.getBytes(Charsets.UTF_8);

			int count = 0;
			int s = 1024 * 1024; // 1 MiB to be done
			while (count < s) {
				int l = passwordBytes.length;
				if ((count + l) > s) {
					l = s - count;
				}
				messageDigest.update(passwordBytes, 0, l);
				count += l;
			}
	
			byte[] digest = messageDigest.digest();
	
			messageDigest.reset();
			messageDigest.update(digest);
			messageDigest.update(id);
			messageDigest.update(digest);
			d = messageDigest.digest();
			cache.put(k, d);
		}
		return d;
	}
}
