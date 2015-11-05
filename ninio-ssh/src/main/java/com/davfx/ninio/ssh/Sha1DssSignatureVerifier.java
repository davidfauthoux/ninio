package com.davfx.ninio.ssh;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.DSAPublicKeySpec;

final class Sha1DssSignatureVerifier implements SignatureVerifier {
	public Sha1DssSignatureVerifier() {
	}
	
	public boolean verify(SshPacket packet, byte[] H, byte[] sig, int off, int len) throws GeneralSecurityException {
		byte[] p = packet.readBlob();
		byte[] q = packet.readBlob();
		byte[] g = packet.readBlob();
		byte[] f = packet.readBlob();

		Signature signature = Signature.getInstance("SHA1withDSA");
		KeyFactory keyFactory = KeyFactory.getInstance("DSA");
		DSAPublicKeySpec dsaPubKeySpec = new DSAPublicKeySpec(new BigInteger(f), new BigInteger(p),
			     new BigInteger(q),
			     new BigInteger(g));
		PublicKey pubKey = keyFactory.generatePublic(dsaPubKeySpec);
		signature.initVerify(pubKey);
		signature.update(H);

		// ASN.1
		int first = ((sig[0] & 0x80) != 0 ? 1 : 0);
		int second = ((sig[20] & 0x80) != 0 ? 1 : 0);

		int length = sig.length + 6 + first + second;
		byte[] tmp = new byte[length];
		
		tmp[0] = (byte) 0x30;
		tmp[1] = (byte) 0x2c;
		tmp[2] = (byte) 0x02;
		tmp[3] = (byte) 0x14;
		
		tmp[1] += first;
		tmp[1] += second;
		tmp[3] += first;
		
		System.arraycopy(sig, off, tmp, 4 + first, 20);
		
		tmp[4 + tmp[3]] = (byte) 0x02;
		tmp[5 + tmp[3]] = (byte) 0x14;
		tmp[5 + tmp[3]] += second;
		
		System.arraycopy(sig, off + 20, tmp, 6 + tmp[3] + second, 20);

		return signature.verify(tmp);
	}
}
