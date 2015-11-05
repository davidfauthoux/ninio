package com.davfx.ninio.ssh;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

final class Sha1RsaSignatureVerifier implements SignatureVerifier {
	public Sha1RsaSignatureVerifier() {
	}

	public boolean verify(SshPacket packet, byte[] H, byte[] sig, int off, int len) throws GeneralSecurityException {
		byte[] ee = packet.readBlob();
		byte[] n = packet.readBlob();

		Signature signature = Signature.getInstance("SHA1withRSA");
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		RSAPublicKeySpec rsaPubKeySpec = new RSAPublicKeySpec(new BigInteger(n), new BigInteger(ee));
		PublicKey pubKey = keyFactory.generatePublic(rsaPubKeySpec);
		signature.initVerify(pubKey);
		signature.update(H);

		return signature.verify(sig, off, len);
	}
}
