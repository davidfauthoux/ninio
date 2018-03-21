package com.davfx.ninio.snmp;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public final class EncryptionEngine {

    private final MessageDigest messageDigest;

    private final LoadingCache<String, byte[]> digestCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(new CacheLoader<String, byte[]>() {
                @Override
                public byte[] load(String password) throws Exception {
                    byte[] passwordBytes = password.getBytes(Charsets.UTF_8);

                    int passwordIndex = 0;

                    int count = 0;
                    // Use while loop until we've done 1 Megabyte
                    while (count < (1024 * 1024)) {
                        byte[] b = new byte[64];
                        for (int i = 0; i < b.length; i++) {
                            // Take the next octet of the password, wrapping to the
                            // beginning of the password as necessary
                            b[i] = passwordBytes[passwordIndex % passwordBytes.length];
                            passwordIndex++;
                        }
                        messageDigest.update(b);
                        count += b.length;
                    }
                    return messageDigest.digest();
                }
            });

    public EncryptionEngine(AuthRemoteSpecification authRemoteSpecification) {
        try {
            messageDigest = MessageDigest.getInstance(authRemoteSpecification.authDigestAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getDigest(final String password) {
        return digestCache.getUnchecked(password);
    }
}
