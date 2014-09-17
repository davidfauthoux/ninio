package com.davfx.ninio.ssh;

import java.security.MessageDigest;

final class CipherUtils {
	private CipherUtils() {
	}
	
	  /*
	   * RFC 4253  7.2. Output from Key Exchange
	   * If the key length needed is longer than the output of the HASH, the
	   * key is extended by computing HASH of the concatenation of K and H and
	   * the entire key so far, and appending the resulting bytes (as many as
	   * HASH generates) to the key.  This process is repeated until enough
	   * key material is available; the key is taken from the beginning of
	   * this value.  In other words:
	   *   K1 = HASH(K || H || X || session_id)   (X is e.g., "A")
	   *   K2 = HASH(K || H || K1)
	   *   K3 = HASH(K || H || K1 || K2)
	   *   ...
	   *   key = K1 || K2 || K3 || ...
	   */
	public static byte[] expandKey(byte[] K, byte[] H, byte[] key, MessageDigest hash, int requiredLength) {
		byte[] result = key;
		while (result.length < requiredLength) {
			SshPacketBuilder buf = new SshPacketBuilder();
			buf.writeMpInt(K);
			buf.append(H);
			buf.append(result);
			hash.update(buf.finish());
			byte[] d = hash.digest();

			byte[] tmp = new byte[result.length + d.length];
			System.arraycopy(result, 0, tmp, 0, result.length);
			System.arraycopy(d, 0, tmp, result.length, d.length);
			result = tmp;
		}
		return result;
	}
	
	public static byte[] shrinkKey(byte[] key, int requiredLength) {
		if (key.length > requiredLength) {
			byte[] tmp = new byte[requiredLength];
			System.arraycopy(key, 0, tmp, 0, tmp.length);
			key = tmp;
		}
		return key;
	}

}
