/*
 * Copyright 2004-2005 Daniel F. Savarese
 * Copyright 2009 Savarese Software Research Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.savarese.com/software/ApacheLicense-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef __COM_DAVFX_NINIO_CORE_NATIVE_RAW_SOCKET_H
#define __COM_DAVFX_NINIO_CORE_NATIVE_RAW_SOCKET_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1libStartup
(JNIEnv *, jclass);

JNIEXPORT void JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1libShutdown
(JNIEnv *, jclass);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1PF_1INET
(JNIEnv *, jclass);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1PF_1INET6
(JNIEnv *, jclass);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1socket
(JNIEnv *, jclass, jint, jint);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1bind
(JNIEnv *, jclass, jint, jint, jbyteArray, jint);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1close
(JNIEnv *, jclass, jint);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1recvfrom1
(JNIEnv *, jclass, jint, jbyteArray, jint, jint, jint);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1recvfrom2
(JNIEnv *, jclass, jint, jbyteArray, jint, jint, jint, jbyteArray);

JNIEXPORT jint JNICALL
Java_com_davfx_ninio_core_v3_NativeRawSocket__1_1sendto
(JNIEnv *, jclass, jint, jbyteArray, jint, jint, jint, jbyteArray, jint);

#ifdef __cplusplus
}
#endif

#endif
