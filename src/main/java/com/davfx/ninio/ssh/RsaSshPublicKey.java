package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

public final class RsaSshPublicKey implements SshPublicKey {
	private final RSAPublicKey publicKey;
	public RsaSshPublicKey(RSAPublicKey publicKey) {
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
}
