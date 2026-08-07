package com.praveen.auditlog.integrity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class Sha256AuditEventHasher {

    public static final String ALGORITHM = "SHA-256";

    private final AuditEventCanonicalizer canonicalizer;

    public Sha256AuditEventHasher(AuditEventCanonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public byte[] digest(CanonicalAuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return digest.digest(canonicalizer.canonicalize(event));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }

    public String digestHex(CanonicalAuditEvent event) {
        return java.util.HexFormat.of().formatHex(digest(event));
    }
}
