package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.davfx.ninio.util.MemoryCache;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

final class EncryptionEngine {
	private static final int ENCRYPTION_MARGIN = 64;
	private final SecureRandom random = new SecureRandom();
	private final MessageDigest messageDigest;
	private final Cipher cipher;
	private final int privKeyLength;
	private final MemoryCache<String, byte[]> cache;
	private final String privEncryptionAlgorithm;

	public EncryptionEngine(String authDigestAlgorithm, String privEncryptionAlgorithm, double cacheDuration) {
		this.privEncryptionAlgorithm = privEncryptionAlgorithm;
		
		try {
			messageDigest = MessageDigest.getInstance(authDigestAlgorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		if (privEncryptionAlgorithm != null) {
			boolean isAes = privEncryptionAlgorithm.equals("AES");

			try {
				cipher = Cipher.getInstance(privEncryptionAlgorithm + "/" + (isAes ? "CFB" : "CBC") + "/" + (isAes ? "NoPadding" : "PKCS5Padding"));
			} catch (NoSuchAlgorithmException|NoSuchPaddingException e) {
				throw new RuntimeException(e);
			}

			privKeyLength = isAes ? 16 : 8;
		} else {
			cipher = null;
			privKeyLength = 8;
		}
		
		cache = MemoryCache.<String, byte[]> builder().expireAfterAccess(cacheDuration).build();
	}

	public byte[] regenerateKey(byte[] id, String password) {
		if (password == null) {
			return null;
		}
		
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
	
	public byte[] hash(byte[] authKey, ByteBuffer message) {
		ByteBuffer messageDup = message.duplicate();

		byte[] newDigest;
		byte[] k_ipad = new byte[64]; /* inner padding - key XORd with ipad */
		byte[] k_opad = new byte[64]; /* outer padding - key XORd with opad */

		/*
		 * the HMAC_MD transform looks like:
		 * 
		 * MD(K XOR opad, MD(K XOR ipad, msg))
		 * 
		 * where K is an n byte key ipad is the byte 0x36 repeated 64 times opad
		 * is the byte 0x5c repeated 64 times and text is the data being
		 * protected
		 */
		/* start out by storing key, ipad and opad in pads */
		for (int i = 0; i < authKey.length; ++i) {
			k_ipad[i] = (byte) (authKey[i] ^ 0x36);
			k_opad[i] = (byte) (authKey[i] ^ 0x5c);
		}
		for (int i = authKey.length; i < 64; ++i) {
			k_ipad[i] = 0x36;
			k_opad[i] = 0x5c;
		}

		/* perform inner MD */
		messageDigest.reset();
		messageDigest.update(k_ipad); /* start with inner pad */
		messageDigest.update(messageDup); /* then text of msg */
		newDigest = messageDigest.digest(); /* finish up 1st pass */
		/* perform outer MD */
		messageDigest.reset(); /* init md5 for 2nd pass */
		messageDigest.update(k_opad); /* start with outer pad */
		messageDigest.update(newDigest); /* then results of 1st hash */
		newDigest = messageDigest.digest(); /* finish up 2nd pass */

		// copy the digest into the message (12 bytes only!)
		byte[] k = new byte[12];
		System.arraycopy(newDigest, 0, k, 0, k.length);
		return k;
	}
	
	public ByteBuffer encrypt(int bootCount, int time, byte[] encryptionParameters, byte[] privKey, ByteBuffer decryptedBuffer) {
		int salt = random.nextInt();
		byte[] iv;

		if (privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(bootCount);
			ivb.putInt(time);
			ivb.putInt(0);
			ivb.putInt(salt);

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(0);
			bb.putInt(salt);
		} else {
			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(bootCount);
			bb.putInt(salt);

			iv = new byte[8];
			for (int i = 0; i < iv.length; i++) {
				iv[i] = (byte) (privKey[iv.length + i] ^ encryptionParameters[i]);
			}
		}
		try {
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(privKey, 0, privKeyLength, privEncryptionAlgorithm), new IvParameterSpec(iv));
			ByteBuffer b = ByteBuffer.allocate(decryptedBuffer.remaining() + ENCRYPTION_MARGIN);
			cipher.doFinal(decryptedBuffer, b);
			b.flip();
			return b;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public ByteBuffer decrypt(int bootCount, int time, byte[] encryptionParameters, byte[] privKey, ByteBuffer encryptedBuffer) {
		byte[] iv;

		if (privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(bootCount);
			ivb.putInt(time);

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			ivb.putInt(bb.getInt());
			ivb.putInt(bb.getInt());
		} else {
			iv = new byte[8];
			for (int i = 0; i < 8; ++i) {
				iv[i] = (byte) (privKey[8 + i] ^ encryptionParameters[i]);
			}
		}

		try {
			SecretKeySpec key = new SecretKeySpec(privKey, 0, privKeyLength, privEncryptionAlgorithm);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
			ByteBuffer b = ByteBuffer.allocate(encryptedBuffer.remaining() + ENCRYPTION_MARGIN);
			cipher.doFinal(encryptedBuffer, b);
			b.flip();
			return b;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

}
