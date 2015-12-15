package com.davfx.ninio.proxy;

import java.util.Objects;

import com.davfx.ninio.core.Address;

final class AddressConnecterTypeKey {
	public final Address address;
	public final String connecterType;
	public AddressConnecterTypeKey(Address address, String connecterType) {
		this.address = address;
		this.connecterType = connecterType;
	}
	@Override
	public int hashCode() {
		if (address == null) {
			return connecterType.hashCode();
		}
		return Objects.hash(address, connecterType);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof AddressConnecterTypeKey)) {
			return false;
		}
		AddressConnecterTypeKey other = (AddressConnecterTypeKey) obj;
		return Objects.equals(address, other.address) && connecterType.equals(other.connecterType);
	}
}