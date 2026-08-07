package com.praveen.auditlog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CursorCodecTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private final CursorCodec codec = new CursorCodec(objectMapper);

    @Test
    void malformedBase64HasDistinctOutcome() {
        assertThatThrownBy(() -> codec.decode("%%%not-base64%%%"))
                .isInstanceOfSatisfying(
                        InvalidCursorException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(
                                error.reason()
                        ).isEqualTo(
                                InvalidCursorException.Reason.MALFORMED
                        )
                );
    }

    @Test
    void cursorFromAnotherEndpointHasContextMismatchOutcome() {
        CursorCodec.Cursor exportCursor = new CursorCodec.Cursor(
                1,
                CursorCodec.Purpose.AUDIT_EXPORT,
                CursorCodec.Mode.CROSS_CHAIN,
                "another-endpoint-fingerprint",
                Instant.parse("2026-08-07T14:30:12.123456Z"),
                "tenant:tenant-1",
                4L,
                UUID.fromString("00000000-0000-0000-0000-000000000004")
        );
        AuditQueryService service = new AuditQueryService(
                mock(JdbcTemplate.class), objectMapper, codec
        );

        assertThatThrownBy(() -> service.search(
                "tenant-1", specification(), 10,
                codec.encode(exportCursor)
        )).isInstanceOfSatisfying(
                InvalidCursorException.class,
                error -> org.assertj.core.api.Assertions.assertThat(
                        error.reason()
                ).isEqualTo(
                        InvalidCursorException.Reason.CONTEXT_MISMATCH
                )
        );
    }

    @Test
    void unsupportedCursorVersionHasDistinctOutcome() throws Exception {
        String json = """
                {
                  "version": 0,
                  "purpose": "AUDIT_EVENT_SEARCH",
                  "mode": "CROSS_CHAIN",
                  "filterFingerprint": "placeholder",
                  "recordedAt": "2026-08-07T14:30:12.123456Z",
                  "chainId": "tenant:tenant-1",
                  "sequenceNumber": 4,
                  "eventId": "00000000-0000-0000-0000-000000000004"
                }
                """;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOfSatisfying(
                        InvalidCursorException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(
                                error.reason()
                        ).isEqualTo(
                                InvalidCursorException.Reason.VERSION_UNSUPPORTED
                        )
                );
    }

    private AuditEventSpecification specification() {
        return new AuditEventSpecification(
                null, null, null, null, null, null, null
        );
    }

}
