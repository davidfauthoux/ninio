package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.google.common.primitives.Ints;

final class CipheringConnector implements Connecter.Connecting {

	private final Connecter.Connecting wrappee;
	private Cipher cipher = null;
	private Mac mac = null;
	private int sequence = 0;

	public CipheringConnector(Connecter.Connecting wrappee) {
		this.wrappee = wrappee;
	}

	/*
	 * Initial IV client to server: HASH (K || H || "A" || session_id) Initial
	 * IV server to client: HASH (K || H || "B" || session_id) Encryption key
	 * client to server: HASH (K || H || "C" || session_id) Encryption key
	 * server to client: HASH (K || H || "D" || session_id) Integrity key client
	 * to server: HASH (K || H || "E" || session_id) Integrity key server to
	 * client: HASH (K || H || "F" || session_id)
	 */
	public void init(String encryptionAlgorithm, String cipherAlgorithm, int keyLength, String macAlgorithm, byte[] K, byte[] H, byte[] sessionId) throws GeneralSecurityException {
		MessageDigest sha = MessageDigest.getInstance("SHA-1");

		sha.reset();
		sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('A').append(sessionId).finish());
		byte[] iv = sha.digest();

		sha.reset();
		sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('C').append(sessionId).finish());
		byte[] cipherKey = sha.digest();

		try {
			cipher = Cipher.getInstance(encryptionAlgorithm + "/" + cipherAlgorithm + "/NoPadding");

			iv = CipherUtils.expandKey(K, H, iv, sha, cipher.getBlockSize());
			cipherKey = CipherUtils.expandKey(K, H, cipherKey, sha, keyLength);

			iv = CipherUtils.shrinkKey(iv, cipher.getBlockSize());
			cipherKey = CipherUtils.shrinkKey(cipherKey, keyLength);

			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, encryptionAlgorithm), new IvParameterSpec(iv));

			sha.reset();
			sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('E').append(sessionId).finish());
			byte[] macKey = sha.digest();

			mac = Mac.getInstance(macAlgorithm);

			macKey = CipherUtils.expandKey(K, H, macKey, sha, mac.getMacLength());
			macKey = CipherUtils.shrinkKey(macKey, mac.getMacLength());

			mac.init(new SecretKeySpec(macKey, macAlgorithm));
		} catch (GeneralSecurityException e) {
			cipher = null;
			mac = null;
			throw e;
		}
	}

	@Override
	public void close() {
		wrappee.close();
	}

	@Override
	public void send(Address address, ByteBuffer buffer, Callback callback) {
		if ((cipher == null) || (mac == null)) {
			sequence++;
			wrappee.send(address, buffer, callback);
			return;
		}

		ByteBuffer sequenceBuffer = ByteBuffer.allocate(Ints.BYTES);
		sequenceBuffer.putInt(sequence);
		sequence++;
		sequenceBuffer.flip();

		mac.reset();
		mac.update(sequenceBuffer);
		int p = buffer.position();
		mac.update(buffer);
		buffer.position(p);

		byte[] calculatedMac = new byte[mac.getMacLength()];
		try {
			mac.doFinal(calculatedMac, 0);
		} catch (ShortBufferException e) {
			throw new RuntimeException(e);
		} catch (IllegalStateException e) {
			throw new RuntimeException(e);
		}

		ByteBuffer b = ByteBuffer.allocate(buffer.remaining() + calculatedMac.length);
		try {
			cipher.update(buffer, b);
		} catch (ShortBufferException e) {
			throw new RuntimeException(e);
		}
		b.put(calculatedMac);
		b.flip();

		wrappee.send(address, b, callback);
	}
}
