package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CanonicalEventSerializer serializer = new CanonicalEventSerializer(
            new CanonicalJsonAuditEventCanonicalizerV1());

    @Test void nestedPropertyOrderAndEquivalentNumbersAreCanonical() throws Exception {
        byte[] first = serializer.serializeJson(json.readTree("""
                {"outer":{"z":1.0,"a":1e3},"items":[{"b":2,"a":1},null]}
                """));
        byte[] second = serializer.serializeJson(json.readTree("""
                {"items":[{"a":1.00,"b":2.0},null],"outer":{"a":1000,"z":1}}
                """));
        assertThat(first).isEqualTo(second);
    }

    @Test void arrayOrderAndNullVersusAbsentRemainMeaningful() throws Exception {
        assertThat(serializer.serializeJson(json.readTree("[1,null,2]")))
                .isNotEqualTo(serializer.serializeJson(json.readTree("[2,null,1]")));
        assertThat(serializer.serializeJson(json.readTree("{\"value\":null}")))
                .isNotEqualTo(serializer.serializeJson(json.readTree("{}")));
    }

    @Test void unicodeEscapesAndSupplementaryCharactersAreStable() throws Exception {
        assertThat(serializer.serializeJson(json.readTree("{\"text\":\"caf\\u00e9 \\ud83d\\ude00\"}")))
                .isEqualTo(serializer.serializeJson(json.createObjectNode().put(
                        "text", "caf" + (char) 0x00e9 + " "
                                + new String(Character.toChars(0x1F600)))));

        ObjectNode invalid = json.createObjectNode().put(
                "text", new String(new char[]{(char) 0xD800}));
        assertThatThrownBy(() -> serializer.serializeJson(invalid))
                .isInstanceOf(CanonicalizationException.class);
    }

    @Test void numericSpellingDoesNotChangeCanonicalValue() throws Exception {
        for (String representation : new String[]{"1", "1.0", "1.00", "1e0", "1E+0"}) {
            assertThat(serializer.serializeJson(json.readTree(representation)))
                    .isEqualTo(serializer.serializeJson(json.readTree("1")));
        }
        assertThat(serializer.serializeJson(json.readTree("-0.0")))
                .isEqualTo(serializer.serializeJson(json.readTree("0")));
    }

    @Test void timestampPrecisionAndFutureVersionFailClosed() throws Exception {
        assertThatThrownBy(() -> serializer.serialize(event(
                Instant.parse("2026-08-07T12:00:00.123456789Z"), 1)))
                .isInstanceOf(CanonicalizationException.class);
        assertThatThrownBy(() -> serializer.serialize(event(
                Instant.parse("2026-08-07T12:00:00.123456Z"), 2)))
                .isInstanceOf(CanonicalizationException.class)
                .hasMessageContaining("version");
    }

    private CanonicalAuditEvent event(Instant recordedAt, int version) throws Exception {
        return new CanonicalAuditEvent(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "tenant-1", "tenant:tenant-1", 1, "ACCOUNT_UPDATED", 1,
                "producer-1", "actor-1", "USER", "JWT", "ACCOUNT", "account-1",
                Instant.parse("2026-08-07T12:00:00Z"), recordedAt,
                json.readTree("{\"result\":\"ok\"}"), new byte[32], "SHA-256", version);
    }
}
