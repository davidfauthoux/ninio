package com.davfx.ninio.snmp;

import java.util.Objects;

public final class Auth {
	public final String login;
	public final String authPassword;
	public final String authDigestAlgorithm;
	public final String privPassword;
	public final String privEncryptionAlgorithm;
	
	public Auth(String login, String authPassword, String authDigestAlgorithm, String privPassword, String privEncryptionAlgorithm) {
		this.login = login;
		this.authPassword = authPassword;
		this.authDigestAlgorithm = authDigestAlgorithm;
		this.privPassword = privPassword;
		this.privEncryptionAlgorithm = privEncryptionAlgorithm;
	}

	@Override
	public String toString() {
		return "{login=" + login + ", authPassword=" + authPassword + ", authDigestAlgorithm=" + authDigestAlgorithm + ", privPassword=" + privPassword + ", privEncryptionAlgorithm=" + privEncryptionAlgorithm + "}";
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(login, authPassword, authDigestAlgorithm, privPassword, privEncryptionAlgorithm);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Auth)) {
			return false;
		}
		Auth other = (Auth) obj;
		return Objects.equals(login, other.login)
			&& Objects.equals(authPassword, other.authPassword)
			&& Objects.equals(authDigestAlgorithm, other.authDigestAlgorithm)
			&& Objects.equals(privPassword, other.privPassword)
			&& Objects.equals(privEncryptionAlgorithm, other.privEncryptionAlgorithm);
	}
}
