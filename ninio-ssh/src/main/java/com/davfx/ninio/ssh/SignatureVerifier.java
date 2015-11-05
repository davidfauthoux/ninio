package com.davfx.ninio.ssh;

import java.security.GeneralSecurityException;

public interface SignatureVerifier {
	boolean verify(SshPacket packet, byte[] H, byte[] sig, int off, int len) throws GeneralSecurityException;
}
