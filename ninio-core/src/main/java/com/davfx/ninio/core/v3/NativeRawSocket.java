package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;

import com.davfx.util.LibraryLoader;

public final class NativeRawSocket {

	private native static int __PF_INET();
	private native static int __PF_INET6();
	private native static int __libStartup();
	private native static void __libShutdown();
	private native static int __socket(int protocolFamily, int protocol);
	private native static int __bind(int socket, int family, byte[] address, int scope_id);
	private native static int __close(int socket);
	private native static int __recvfrom1(int socket, byte[] data, int offset, int length, int family);
	private native static int __recvfrom2(int socket, byte[] data, int offset, int length, int family, byte[] address);
	private native static int __sendto(int socket, byte[] data, int offset, int length, int family, byte[] address, int scope_id);

	public static final int PF_INET;
	public static final int PF_INET6;

	static {
    	LibraryLoader.load(NativeRawSocket.class.getClassLoader(), "lib/NativeRawSocket");
		if (__libStartup() != 0) {
			throw new UnsatisfiedLinkError("NativeRawSocket");
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				__libShutdown();
			}
		});

		PF_INET = __PF_INET();
		PF_INET6 = __PF_INET6();
	}

	private final int socket;
	private final int family;

	public NativeRawSocket(int protocolFamily, int protocol) throws IOException {
		socket = __socket(protocolFamily, protocol);

		if (socket < 0) {
			throwIOException(socket);
		}

		family = protocolFamily;
	}

	private static void throwIOException(int err) throws IOException {
		throw new IOException("Error: " + err);
	}

	private int getScopeId(InetAddress address) {
		if ((family == PF_INET6) && (address instanceof Inet6Address)) {
			return ((Inet6Address) address).getScopeId();
		}
		return 0;
	}



	public void bind(InetAddress address) throws IOException {
		int scope_id = getScopeId(address);

		int result = __bind(socket, family, address.getAddress(), scope_id);
		if (result != 0) {
			throwIOException(result);
		}
	}


	public void close() throws IOException {
		int result = __close(socket);

		if (result != 0) {
			throwIOException(result);
		}
	}

	public int read(byte[] data, int offset, int length, byte[] address) throws IOException {
		if (offset < 0 || length < 0 || length > data.length - offset)
			throw new IllegalArgumentException("Invalid offset or length");

		if ((address != null) && (((family == PF_INET) && (address.length != 4)) || ((family == PF_INET6) && (address.length != 16)))) {
			throw new IllegalArgumentException("Invalid address length");
		}

		int result = (address == null) ? __recvfrom1(socket, data, offset, length, family) : __recvfrom2(socket, data, offset, length, family, address);

		if (result < 0) {
			throwIOException(result);
		}

		return result;
	}

	public int write(InetAddress address, byte[] data, int offset, int length) throws IOException {
		int scope_id = getScopeId(address);

		if ((offset < 0) || (length < 0) || (length > data.length - offset)) {
			throw new IllegalArgumentException("Invalid offset or length");
		}

		int result = __sendto(socket, data, offset, length, family, address.getAddress(), scope_id);

		if (result < 0) {
			throwIOException(result);
		}

		return result;
	}

}
