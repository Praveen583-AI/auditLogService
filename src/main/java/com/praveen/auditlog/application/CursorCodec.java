package com.praveen.auditlog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class CursorCodec {

    private static final int VERSION = 1;
    private final ObjectMapper objectMapper;

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Cursor cursor) {
        if (cursor.version() != VERSION) {
            throw new IllegalArgumentException("Unsupported cursor version");
        }
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(cursor)
            );
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode cursor", error);
        }
    }

    public Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            Cursor cursor = objectMapper.readValue(bytes, Cursor.class);
            if (cursor.version() != VERSION) {
                throw new IllegalArgumentException("Unsupported cursor version");
            }
            return cursor;
        } catch (IllegalArgumentException | IOException error) {
            throw new IllegalArgumentException("Invalid cursor", error);
        }
    }

    public record Cursor(
            int version,
            Mode mode,
            String filterFingerprint,
            Instant recordedAt,
            String chainId,
            Long sequenceNumber,
            UUID eventId
    ) {
        public static Cursor singleChain(String fingerprint, String chainId, long sequence) {
            return new Cursor(
                    VERSION, Mode.SINGLE_CHAIN, fingerprint, null,
                    chainId, sequence, null
            );
        }

        public static Cursor crossChain(
                String fingerprint,
                Instant recordedAt,
                String chainId,
                long sequence,
                UUID eventId
        ) {
            return new Cursor(
                    VERSION, Mode.CROSS_CHAIN, fingerprint, recordedAt,
                    chainId, sequence, eventId
            );
        }

        public Cursor {
            if (filterFingerprint == null || filterFingerprint.isBlank()) {
                throw new IllegalArgumentException("Cursor fingerprint is required");
            }
            if (mode == Mode.SINGLE_CHAIN) {
                if (chainId == null || sequenceNumber == null) {
                    throw new IllegalArgumentException("Incomplete single-chain cursor");
                }
            } else if (recordedAt == null || chainId == null
                    || sequenceNumber == null || eventId == null) {
                throw new IllegalArgumentException("Incomplete cross-chain cursor");
            }
        }
    }

    public enum Mode {
        SINGLE_CHAIN,
        CROSS_CHAIN
    }
}
