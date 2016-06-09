package com.davfx.ninio.snmp.v3;

import java.util.Objects;

public final class AuthRemoteSpecification {
	public final String authLogin;
	public final String authPassword;
	public final String authDigestAlgorithm;
	public final String privLogin;
	public final String privPassword;
	public final String privEncryptionAlgorithm;
	
	public AuthRemoteSpecification(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm) {
		this.authLogin = authLogin;
		this.authPassword = authPassword;
		this.authDigestAlgorithm = authDigestAlgorithm;
		this.privLogin = privLogin;
		this.privPassword = privPassword;
		this.privEncryptionAlgorithm = privEncryptionAlgorithm;
	}

	@Override
	public int hashCode() {
		return Objects.hash(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AuthRemoteSpecification)) {
			return false;
		}
		AuthRemoteSpecification other = (AuthRemoteSpecification) obj;
		return Objects.equals(authLogin, other.authLogin)
			&& Objects.equals(authPassword, other.authPassword)
			&& Objects.equals(authDigestAlgorithm, other.authDigestAlgorithm)
			&& Objects.equals(privLogin, other.privLogin)
			&& Objects.equals(privPassword, other.privPassword)
			&& Objects.equals(privEncryptionAlgorithm, other.privEncryptionAlgorithm);
	}
}
