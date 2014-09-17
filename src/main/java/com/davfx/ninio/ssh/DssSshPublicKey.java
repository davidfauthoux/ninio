package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;

public final class DssSshPublicKey implements SshPublicKey {
	private final DSAPublicKey publicKey;
	public DssSshPublicKey(DSAPublicKey publicKey) {
		this.publicKey = publicKey;
	}
	
	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}
	
	@Override
	public String getAlgorithm() {
		return "ssh-dss";
	}
	
	@Override
	public ByteBuffer getBlob() {
		SshPacketBuilder k = new SshPacketBuilder();
		k.writeString(getAlgorithm());
		k.writeBlob(publicKey.getParams().getP().toByteArray());
		k.writeBlob(publicKey.getParams().getQ().toByteArray());
		k.writeBlob(publicKey.getParams().getG().toByteArray());
		k.writeBlob(publicKey.getY().toByteArray());
		return k.finish();
	}
}
