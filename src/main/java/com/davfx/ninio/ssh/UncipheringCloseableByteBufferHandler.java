package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;

final class UncipheringCloseableByteBufferHandler implements CloseableByteBufferHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(UncipheringCloseableByteBufferHandler.class);

	private final CloseableByteBufferHandler wrappee;
	private Cipher cipher = null;
	private Mac mac = null;
	private int sequence = 0;

	private ByteBuffer lengthBuffer = ByteBuffer.wrap(new byte[Integer.BYTES]);
	private int count = 0;

	private ByteBuffer firstBuffer = null;
	private ByteBuffer remainingBuffer = null;
	private ByteBuffer uncipheredFirstBuffer = null;
	private ByteBuffer uncipheredRemainingBuffer = null;
	private ByteBuffer macBuffer = null;
	private int state = 0;

	public UncipheringCloseableByteBufferHandler(CloseableByteBufferHandler wrappee) {
		this.wrappee = wrappee;
	}

    /*
	    Initial IV client to server:     HASH (K || H || "A" || session_id)
	    Initial IV server to client:     HASH (K || H || "B" || session_id)
	    Encryption key client to server: HASH (K || H || "C" || session_id)
	    Encryption key server to client: HASH (K || H || "D" || session_id)
	    Integrity key client to server:  HASH (K || H || "E" || session_id)
	    Integrity key server to client:  HASH (K || H || "F" || session_id)
	  */
	public void init(String encryptionAlgorithm, String cipherAlgorithm, int keyLength, String macAlgorithm, byte[] K, byte[] H, byte[] sessionId) throws GeneralSecurityException {
		MessageDigest sha = MessageDigest.getInstance("SHA-1");

		sha.reset();
		sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('B').append(sessionId).finish());
		byte[] iv = sha.digest();

		sha.reset();
		sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('D').append(sessionId).finish());
		byte[] cipherKey = sha.digest();

		try {
			cipher = Cipher.getInstance(encryptionAlgorithm + "/" + cipherAlgorithm + "/NoPadding");

			iv = CipherUtils.expandKey(K, H, iv, sha, cipher.getBlockSize());
			cipherKey = CipherUtils.expandKey(K, H, cipherKey, sha, keyLength);

			iv = CipherUtils.shrinkKey(iv, cipher.getBlockSize());
			cipherKey = CipherUtils.shrinkKey(cipherKey, keyLength);

			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, encryptionAlgorithm), new IvParameterSpec(iv));

			sha.reset();
			sha.update(new SshPacketBuilder().writeMpInt(K).append(H).writeByte('F').append(sessionId).finish());
			byte[] macKey = sha.digest();

			mac = Mac.getInstance(macAlgorithm);

			macKey = CipherUtils.expandKey(K, H, macKey, sha, mac.getMacLength());
			macKey = CipherUtils.shrinkKey(macKey, mac.getMacLength());

			mac.init(new SecretKeySpec(macKey, macAlgorithm));
			
			firstBuffer = ByteBuffer.wrap(new byte[cipher.getBlockSize()]);
			macBuffer = ByteBuffer.wrap(new byte[mac.getMacLength()]);
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
	public void handle(Address address, ByteBuffer b) {
		if ((cipher == null) || (mac == null)) {
			int p = b.position();
			while (b.hasRemaining()) {
				if (count == 0) {
					ByteBufferUtils.transfer(b, lengthBuffer);
					if (lengthBuffer.position() == lengthBuffer.capacity()) {
						lengthBuffer.flip();
						count = lengthBuffer.getInt();
						lengthBuffer.rewind();
						sequence++;
					}
				}
				if (count > 0) {
					int c = Math.min(count, b.remaining());
					b.position(b.position() + c);
					count -= c;
				}
			}
			b.position(p);
			wrappee.handle(address, b);
			return;
		}

		while (b.hasRemaining()) {
			if (state == 0) {
				ByteBufferUtils.transfer(b, firstBuffer);
				if (firstBuffer.position() == firstBuffer.capacity()) {
					firstBuffer.flip();

					uncipheredFirstBuffer = ByteBuffer.allocate(firstBuffer.remaining());
					try {
						cipher.update(firstBuffer, uncipheredFirstBuffer);
					} catch (ShortBufferException e) {
						throw new RuntimeException(e);
					}
					uncipheredFirstBuffer.flip();
					firstBuffer.rewind();
					int firstLength = uncipheredFirstBuffer.getInt() + 4;
					remainingBuffer = ByteBuffer.wrap(new byte[firstLength - uncipheredFirstBuffer.capacity()]);
					uncipheredFirstBuffer.rewind();
					state = 1;
				}
			}

			if (state == 1) {
				ByteBufferUtils.transfer(b, remainingBuffer);
				if (remainingBuffer.position() == remainingBuffer.capacity()) {
					remainingBuffer.flip();

					uncipheredRemainingBuffer = ByteBuffer.allocate(remainingBuffer.remaining());
					try {
						cipher.update(remainingBuffer, uncipheredRemainingBuffer);
					} catch (ShortBufferException e) {
						throw new RuntimeException(e);
					}
					uncipheredRemainingBuffer.flip();
					remainingBuffer = null;
					state = 2;
				}
			}

			if (state == 2) {
				ByteBufferUtils.transfer(b, macBuffer);
				if (macBuffer.position() == macBuffer.capacity()) {
					macBuffer.flip();

					ByteBuffer sequenceBuffer = ByteBuffer.allocate(Integer.BYTES);
					sequenceBuffer.putInt(sequence);
					sequence++;
					sequenceBuffer.flip();

					mac.reset();
					mac.update(sequenceBuffer);
					int p = uncipheredFirstBuffer.position();
					mac.update(uncipheredFirstBuffer);
					uncipheredFirstBuffer.position(p);
					p = uncipheredRemainingBuffer.position();
					mac.update(uncipheredRemainingBuffer);
					uncipheredRemainingBuffer.position(p);

					byte[] calculatedMac = new byte[mac.getMacLength()];
					try {
						mac.doFinal(calculatedMac, 0);
					} catch (ShortBufferException e) {
						throw new RuntimeException(e);
					} catch (IllegalStateException e) {
						throw new RuntimeException(e);
					}

					boolean valid = ByteBuffer.wrap(calculatedMac).equals(macBuffer);

					state = 0;

					ByteBuffer fbb = uncipheredFirstBuffer;
					ByteBuffer rbb = uncipheredRemainingBuffer;
					uncipheredFirstBuffer = null;
					uncipheredRemainingBuffer = null;
					macBuffer.rewind();

					if (!valid) {
						LOGGER.error("Invalid MAC");
						wrappee.close();
					} else {
						wrappee.handle(address, fbb);
						wrappee.handle(address, rbb);
					}
				}
			}
		}
	}
}
