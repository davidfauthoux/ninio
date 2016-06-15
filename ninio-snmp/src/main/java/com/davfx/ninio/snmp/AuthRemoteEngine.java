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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

public final class AuthRemoteEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthRemoteEngine.class);

	private static final int ENCRYPTION_MARGIN = 64;
	
	private int bootCount = 0;
	private int resetTime = 0;
	private byte[] id = new byte[0];

	public final AuthRemoteSpecification authRemoteSpecification;
	private final MessageDigest messageDigest;
	
	private int packetNumber = 0;
    private byte[] encryptionParameters = new byte[8];
    
    private final SecureRandom random = new SecureRandom();
    
    private final Cipher cipher;
    private final int privKeyLength;

    private long timeResetAt = 0L;
    private int time = 0;
    
    //%%% private boolean ready = false;
    
	public AuthRemoteEngine(AuthRemoteSpecification authRemoteSpecification) {
		this.authRemoteSpecification = authRemoteSpecification;

		try {
			messageDigest = MessageDigest.getInstance(authRemoteSpecification.authDigestAlgorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		try {
			cipher = Cipher.getInstance(authRemoteSpecification.privEncryptionAlgorithm + "/" + (authRemoteSpecification.privEncryptionAlgorithm.equals("AES") ? "CFB" : "CBC") + "/" + (authRemoteSpecification.privEncryptionAlgorithm.equals("AES") ? "NoPadding" : "PKCS5Padding"));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (NoSuchPaddingException e) {
			throw new RuntimeException(e);
		}
		
		privKeyLength = authRemoteSpecification.privEncryptionAlgorithm.equals("AES") ? 16 : 8;
	}
	
	/*%%
	public boolean isReady() {
		return ready;
	}
	
	public void setReady() {
		ready = true;
	}
	*/

	public String getAuthLogin() {
		return authRemoteSpecification.authLogin;
	}
	public String getPrivLogin() {
		return authRemoteSpecification.privLogin;
	}
	
	public int incPacketNumber() {
		int n = packetNumber;
		packetNumber++;
		return n;
	}
	
	public byte[] getEncryptionParameters() {
		return encryptionParameters;
	}
	
	public byte[] getId() {
		return id;
	}

	public void setId(byte[] id) {
		LOGGER.trace("Auth engine ID: {} -> {}", (this.id == null) ? null : BaseEncoding.base16().encode(this.id), BaseEncoding.base16().encode(id));
		this.id = id;
	}
	public void setEncryptionParameters(byte[] encryptionParameters) {
		this.encryptionParameters = encryptionParameters;
	}

	public int getBootCount() {
		return bootCount;
	}

	public void setBootCount(int bootCount) {
		LOGGER.trace("Auth engine boot count: {} -> {}", this.bootCount, bootCount);
		this.bootCount = bootCount;
	}

	public int getTime() {
		return time;
	}

	public void renewTime() {
		if (timeResetAt > 0L) {
			int oldTime = time;
			time = resetTime + ((int) ((System.currentTimeMillis() - timeResetAt) / 1000L));
			LOGGER.trace("Auth engine time: ({}) {} -> {}", resetTime, oldTime, time);
		}
	}
	public void resetTime(int resetTime) {
		LOGGER.trace("Auth engine reset time: {} ({}) -> {}", this.resetTime, time, resetTime);
		timeResetAt = System.currentTimeMillis();
		this.resetTime = resetTime;
		this.time = resetTime;
	}

	public byte[] getAuthKey() {
		return getKey(authRemoteSpecification.authPassword);
	}
	
	private byte[] getPrivKey() {
		return getKey(authRemoteSpecification.privPassword);
	}

	private byte[] getKey(String password) {
		byte[] passwordBytes = password.getBytes(Charsets.UTF_8);

		int passwordIndex = 0;

		int count = 0;
		// Use while loop until we've done 1 Megabyte
		while (count < (1024 * 1024)) {
			byte[] b = new byte[64];
			for (int i = 0; i < b.length; i++) {
				// Take the next octet of the password, wrapping to the
				// beginning of the password as necessary
				b[i] = passwordBytes[passwordIndex % passwordBytes.length];
				passwordIndex++;
			}
			messageDigest.update(b);
			count += b.length;
		}

		byte[] digest = messageDigest.digest();

		messageDigest.reset();
		messageDigest.update(digest);
		messageDigest.update(id);
		messageDigest.update(digest);
		return messageDigest.digest();
	}
	
	public byte[] hash(ByteBuffer message) {
		ByteBuffer messageDup = message.duplicate();

		byte[] digest = getAuthKey();

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
		for (int i = 0; i < digest.length; ++i) {
			k_ipad[i] = (byte) (digest[i] ^ 0x36);
			k_opad[i] = (byte) (digest[i] ^ 0x5c);
		}
		for (int i = digest.length; i < 64; ++i) {
			k_ipad[i] = 0x36;
			k_opad[i] = 0x5c;
		}

		/* perform inner MD */
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

	public ByteBuffer encrypt(ByteBuffer decryptedBuffer) {
		byte[] encryptionKey = getPrivKey();
		int salt = random.nextInt();
		byte[] iv;

		if (authRemoteSpecification.privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(getBootCount());
			ivb.putInt(getTime());
			ivb.putInt(0);
			ivb.putInt(salt);

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(0);
			bb.putInt(salt);
		} else {
			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			bb.putInt(getBootCount());
			bb.putInt(salt);

			iv = new byte[8];
			for (int i = 0; i < iv.length; i++) {
				iv[i] = (byte) (encryptionKey[iv.length + i] ^ encryptionParameters[i]);
			}
		}
		try {
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, 0, privKeyLength, authRemoteSpecification.privEncryptionAlgorithm), new IvParameterSpec(iv));
			ByteBuffer b = ByteBuffer.allocate(decryptedBuffer.remaining() + ENCRYPTION_MARGIN);
			cipher.doFinal(decryptedBuffer, b);
			b.flip();
			return b;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public ByteBuffer decrypt(ByteBuffer encryptedBuffer) {
		byte[] decryptionKey = getPrivKey();

		byte[] iv;

		if (authRemoteSpecification.privEncryptionAlgorithm.equals("AES")) {
			iv = new byte[16];
			ByteBuffer ivb = ByteBuffer.wrap(iv);
			ivb.putInt(getBootCount());
			ivb.putInt(getTime());

			ByteBuffer bb = ByteBuffer.wrap(encryptionParameters);
			ivb.putInt(bb.getInt());
			ivb.putInt(bb.getInt());
		} else {
			iv = new byte[8];
			for (int i = 0; i < 8; ++i) {
				iv[i] = (byte) (decryptionKey[8 + i] ^ encryptionParameters[i]);
			}
		}

		try {
			SecretKeySpec key = new SecretKeySpec(decryptionKey, 0, privKeyLength, authRemoteSpecification.privEncryptionAlgorithm);
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
