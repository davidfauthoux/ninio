#include <string.h>

#if defined(_WIN32)

#	include <winsock2.h>
#	include <ws2tcpip.h>

#	if !defined(close)
#		define close(fd) closesocket(fd)
#	endif

#else

#	include <netdb.h>
#	include <netinet/in.h>
#	include <sys/socket.h>
#	include <unistd.h>
#	include <sys/time.h>

#endif

#include "NativeRawSocket.h"

static int setintsockopt(int socket, int level, int option, int value);
static int settimeout(int socket, int option, int timeout);

static int setintsockopt(int socket, int level, int option, int value) {
	return setsockopt(socket, level, option, (void*)&value, sizeof(value));
}

static void milliseconds_to_timeval(int milliseconds, struct timeval *value) {
	int seconds = milliseconds / 1000;

	if(seconds > 0) {
		milliseconds-=(seconds*1000);
	}

	value->tv_sec = seconds;
	value->tv_usec = milliseconds * 1000;
}


static int settimeout(int socket, int option, int timeout) {
#if defined(_WIN32)
	return setintsockopt(socket, SOL_SOCKET, option, timeout);
#else
	struct timeval value;

	milliseconds_to_timeval(timeout, &value);

	return setsockopt(socket, SOL_SOCKET, option, (void*)&value, sizeof(value));
#endif
}

static struct sockaddr*
init_sockaddr_in(JNIEnv *env, struct sockaddr_in *sin, jbyteArray address) {
	jbyte *buf;

	memset(sin, 0, sizeof(struct sockaddr_in));
	sin->sin_family = PF_INET;
	buf = (*env)->GetByteArrayElements(env, address, NULL);
	memcpy(&sin->sin_addr, buf, sizeof(sin->sin_addr));
	(*env)->ReleaseByteArrayElements(env, address, buf, JNI_ABORT);
	return (struct sockaddr *)sin;
}


static struct sockaddr*
init_sockaddr_in6(JNIEnv *env, struct sockaddr_in6 *sin6, jbyteArray address, int scope_id) {
	jbyte *buf;

	memset(sin6, 0, sizeof(struct sockaddr_in6));
	sin6->sin6_family = PF_INET6;
	sin6->sin6_scope_id = scope_id;
	buf = (*env)->GetByteArrayElements(env, address, NULL);
	memcpy(&sin6->sin6_addr, buf, sizeof(sin6->sin6_addr));
	(*env)->ReleaseByteArrayElements(env, address, buf, JNI_ABORT);

	return (struct sockaddr *)sin6;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1libStartup
(JNIEnv *env, jclass cls) {
#if defined(_WIN32)
	WORD version = MAKEWORD(2, 0);
	WSADATA data;
	return WSAStartup(version, &data);
#else
	return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1libShutdown
(JNIEnv *env, jclass cls) {
#if defined(_WIN32)
	WSACleanup();
#endif
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1PF_1INET
(JNIEnv *env, jclass cls) {
	return PF_INET;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1PF_1INET6
(JNIEnv *env, jclass cls) {
	return PF_INET6;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1socket
(JNIEnv *env, jclass cls, jint family, jint protocol) {
	int s;
	
	s = socket(family, SOCK_RAW, protocol);
	settimeout(s, SO_SNDTIMEO, 0);
	settimeout(s, SO_RCVTIMEO, 0);
	setintsockopt(s, IPPROTO_IP, IP_HDRINCL, 0);
	/* setintsockopt(s, SOL_SOCKET, SO_SNDBUF, size); */
	/* setintsockopt(s, SOL_SOCKET, SO_RCVBUF, size); */
	return s;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1bind
(JNIEnv *env, jclass cls, jint socket, jint family, jbyteArray address, jint scope_id) {
	struct sockaddr *saddr;
	socklen_t socklen;
	union {
		struct sockaddr_in sin;
		struct sockaddr_in6 sin6;
	} sin;

	if (family == PF_INET) {
		socklen = sizeof(sin.sin);
		saddr = init_sockaddr_in(env, &sin.sin, address);
	} else if (family == PF_INET6) {
		socklen = sizeof(sin.sin6);
		saddr = init_sockaddr_in6(env, &sin.sin6, address, scope_id);
	} else {
		return -1;
	}

	return bind(socket, saddr, socklen);
}


JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1close
(JNIEnv *env, jclass cls, jint socket) {
	return close(socket);
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1recvfrom1
(JNIEnv *env, jclass cls, jint socket,jbyteArray data, jint offset, jint len, jint family) {
	int result;
	jbyte *buf;

	if (family != PF_INET && family != PF_INET6) {
		return -1;
	}

	buf = (*env)->GetByteArrayElements(env, data, NULL);

	result = recvfrom(socket, buf+offset, len, 0, NULL, NULL);

	(*env)->ReleaseByteArrayElements(env, data, buf, 0);

	return result;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1recvfrom2
(JNIEnv *env, jclass cls, jint socket, jbyteArray data, jint offset, jint len, jint family, jbyteArray address) {
	int result;
	jbyte *buf;
	union {
		struct sockaddr_in sin;
		struct sockaddr_in6 sin6;
	} sin;
	struct sockaddr *saddr;
	void *addr;
	socklen_t socklen;
	size_t addrlen;
	
	if (family == PF_INET) {
		socklen = sizeof(sin.sin);
		addrlen = sizeof(sin.sin.sin_addr);
		memset(&sin, 0, sizeof(struct sockaddr_in));
		sin.sin.sin_family = PF_INET;
		saddr = (struct sockaddr *)&sin.sin;
		addr = &sin.sin.sin_addr;
	} else if (family == PF_INET6) {
		socklen = sizeof(sin.sin6);
		addrlen = sizeof(sin.sin6.sin6_addr);
		memset(&sin.sin6, 0, sizeof(struct sockaddr_in6));
		sin.sin6.sin6_family = PF_INET6;
		addr = &sin.sin6.sin6_addr;
		saddr = (struct sockaddr *)&sin.sin6;
	} else {
		return -1;
	}

	buf = (*env)->GetByteArrayElements(env, data, NULL);

	result = recvfrom(socket, buf+offset, len, 0, saddr, &socklen);

	(*env)->ReleaseByteArrayElements(env, data, buf, 0);

	buf = (*env)->GetByteArrayElements(env, address, NULL);
	memcpy(buf, addr, addrlen);
	(*env)->ReleaseByteArrayElements(env, address, buf, 0);

	return result;
}

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_NativeRawSocket__1_1sendto
(JNIEnv *env, jclass cls, jint socket, jbyteArray data, jint offset, jint len, jint family, jbyteArray address, jint scope_id) {
	int result;
	jbyte *buf;
	union {
		struct sockaddr_in sin;
		struct sockaddr_in6 sin6;
	} sin;
	struct sockaddr *saddr;
	socklen_t socklen;

	if (family == PF_INET) {
		socklen = sizeof(sin.sin);
		saddr = init_sockaddr_in(env, &sin.sin, address);
	} else if (family == PF_INET6) {
		socklen = sizeof(sin.sin6);
		saddr = init_sockaddr_in6(env, &sin.sin6, address, scope_id);
	} else {
		return -1;
	}

	buf = (*env)->GetByteArrayElements(env, data, NULL);

	result = sendto(socket, buf+offset, len, 0, saddr, socklen);

	(*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);

	return result;
}
