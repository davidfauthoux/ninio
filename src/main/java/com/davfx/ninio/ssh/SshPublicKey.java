package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.PublicKey;

public interface SshPublicKey {
	String getAlgorithm();
	PublicKey getPublicKey();
	ByteBuffer getBlob();
}
