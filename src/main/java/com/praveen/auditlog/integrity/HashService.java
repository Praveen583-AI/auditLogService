package com.praveen.auditlog.integrity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class HashService {

    public static final String ALGORITHM = "SHA-256";

    private final CanonicalEventSerializer serializer;

    public HashService(CanonicalEventSerializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public byte[] hash(CanonicalAuditEvent event) {
        return sha256(serializer.serialize(event));
    }

    public byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }

    public String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }
}
