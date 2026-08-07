
package com.praveen.auditlog.retention;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Objects;

public final class HmacSha256ManifestSigner
        implements RetentionService.ManifestSigner {
    private static final String JCA_ALGORITHM = "HmacSHA256";
    private final String keyId;
    private final byte[] key;

    public HmacSha256ManifestSigner(String keyId, byte[] key) {
        this.keyId = Objects.requireNonNull(keyId);
        this.key = Objects.requireNonNull(key).clone();
        if (keyId.isBlank() || key.length < 32) {
            throw new IllegalArgumentException("Archive signing key id and 256-bit key are required");
        }
    }

    @Override
    public RetentionService.SignedValue sign(byte[] canonicalManifest) {
        return new RetentionService.SignedValue(
                "HMAC-SHA256", keyId, 1, mac(canonicalManifest)
        );
    }

    @Override
    public boolean verify(byte[] canonicalManifest, byte[] signature,
                          String candidateKeyId, int version) {
        return version == 1 && keyId.equals(candidateKeyId)
                && MessageDigest.isEqual(signature, mac(canonicalManifest));
    }

    private byte[] mac(byte[] value) {
        try {
            Mac mac = Mac.getInstance(JCA_ALGORITHM);
            mac.init(new SecretKeySpec(key, JCA_ALGORITHM));
            return mac.doFinal(value);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot sign archive manifest", error);
        }
    }
}

