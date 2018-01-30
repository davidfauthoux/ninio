package com.davfx.ninio.snmp;

import java.util.Objects;

public final class AuthRemoteSpecification {
	public final String login;
	public final String authPassword;
	public final String authDigestAlgorithm;
	public final String privPassword;
	public final String privEncryptionAlgorithm;
	public final String contextName;
	
	public AuthRemoteSpecification(String login, String authPassword, String authDigestAlgorithm, String privPassword, String privEncryptionAlgorithm, String contextName) {
		this.login = login;
		this.authPassword = authPassword;
		this.authDigestAlgorithm = authDigestAlgorithm;
		this.privPassword = privPassword;
		this.privEncryptionAlgorithm = privEncryptionAlgorithm;
		this.contextName = contextName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(login, authPassword, authDigestAlgorithm, privPassword, privEncryptionAlgorithm, contextName);
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
		return Objects.equals(login, other.login)
			&& Objects.equals(authPassword, other.authPassword)
			&& Objects.equals(authDigestAlgorithm, other.authDigestAlgorithm)
			&& Objects.equals(privPassword, other.privPassword)
			&& Objects.equals(privEncryptionAlgorithm, other.privEncryptionAlgorithm)
			&& Objects.equals(contextName, other.contextName);
	}
}
