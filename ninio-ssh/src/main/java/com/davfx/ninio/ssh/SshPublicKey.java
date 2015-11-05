package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

public interface SshPublicKey {
	String getAlgorithm();
	PublicKey getPublicKey();
	ByteBuffer getBlob();
	
	ByteBuffer sign(ByteBuffer b) throws GeneralSecurityException;
	boolean verify(ByteBuffer b, ByteBuffer signature) throws GeneralSecurityException;
}
