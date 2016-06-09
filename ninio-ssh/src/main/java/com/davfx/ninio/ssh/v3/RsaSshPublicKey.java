package com.davfx.ninio.ssh.v3;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public final class RsaSshPublicKey implements SshPublicKey {
	private final RSAPrivateKey privateKey;
	private final RSAPublicKey publicKey;
	public RsaSshPublicKey(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
	}
	
	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}
	
	@Override
	public String getAlgorithm() {
		return "ssh-rsa";
	}
	
	@Override
	public ByteBuffer getBlob() {
		SshPacketBuilder k = new SshPacketBuilder();
		k.writeString(getAlgorithm());
		k.writeBlob(publicKey.getPublicExponent().toByteArray());
		k.writeBlob(publicKey.getModulus().toByteArray());
		return k.finish();
	}
	
	@Override
	public ByteBuffer sign(ByteBuffer b) throws GeneralSecurityException {
		Signature s = Signature.getInstance("SHA1withRSA");
		s.initSign(privateKey);
		s.update(b.array(), b.position(), b.remaining());

		SshPacketBuilder k = new SshPacketBuilder();
		k.writeString(getAlgorithm());
		k.writeBlob(s.sign());
		return k.finish();
	}
	
	@Override
	public boolean verify(ByteBuffer b, ByteBuffer signature) throws GeneralSecurityException {
		Signature s = Signature.getInstance("SHA1withRSA");
		s.initVerify(publicKey);
		s.update(b.array(), b.position(), b.remaining());

		return s.verify(signature.array(), signature.position(), signature.remaining());
	}
}
