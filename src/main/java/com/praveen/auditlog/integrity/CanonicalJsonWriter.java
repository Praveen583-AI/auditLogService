package com.praveen.auditlog.integrity;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class CanonicalJsonWriter {

    private static final int MAX_NUMERIC_PRECISION = 1_000;
    private static final int MAX_ABSOLUTE_SCALE = 1_000;
    private static final int MAX_CANONICAL_NUMBER_LENGTH = 4_096;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CanonicalJsonWriter() {
    }

    static byte[] write(JsonNode value) {
        StringBuilder output = new StringBuilder();
        append(value, output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(JsonNode value, StringBuilder output) {
        if (value == null || value.isMissingNode()) {
            throw new CanonicalizationException("missing JSON node cannot be canonicalized");
        }
        if (value.isNull()) {
            output.append("null");
        } else if (value.isObject()) {
            appendObject(value, output);
        } else if (value.isArray()) {
            appendArray(value, output);
        } else if (value.isTextual()) {
            appendString(value.textValue(), output);
        } else if (value.isBoolean()) {
            output.append(value.booleanValue());
        } else if (value.isIntegralNumber()) {
            output.append(value.bigIntegerValue().toString());
        } else if (value.isFloatingPointNumber()) {
            appendDecimal(value, output);
        } else {
            throw new CanonicalizationException(
                    "unsupported JSON node type: " + value.getNodeType()
            );
        }
    }

    private static void appendObject(JsonNode value, StringBuilder output) {
        List<String> names = new ArrayList<>();
        Iterator<String> fields = value.fieldNames();
        fields.forEachRemaining(names::add);
        Collections.sort(names);

        output.append('{');
        boolean first = true;
        for (String name : names) {
            if (!first) {
                output.append(',');
            }
            first = false;
            appendString(name, output);
            output.append(':');
            append(value.get(name), output);
        }
        output.append('}');
    }

    private static void appendArray(JsonNode value, StringBuilder output) {
        output.append('[');
        for (int index = 0; index < value.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            append(value.get(index), output);
        }
        output.append(']');
    }

    private static void appendDecimal(JsonNode value, StringBuilder output) {
        if ((value.isDouble() || value.isFloat())
                && !Double.isFinite(value.doubleValue())) {
            throw new CanonicalizationException("non-finite number is not supported");
        }

        BigDecimal decimal = value.decimalValue();
        if (decimal.signum() == 0) {
            output.append('0');
            return;
        }

        BigDecimal normalized = decimal.stripTrailingZeros();
        if (normalized.precision() > MAX_NUMERIC_PRECISION
                || Math.abs((long) normalized.scale()) > MAX_ABSOLUTE_SCALE) {
            throw new CanonicalizationException("number exceeds canonical limits");
        }

        String representation = normalized.toPlainString();
        if (representation.length() > MAX_CANONICAL_NUMBER_LENGTH) {
            throw new CanonicalizationException("canonical number exceeds size limit");
        }
        output.append(representation);
    }

    private static void appendString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20) {
                        appendUnicodeEscape(current, output);
                    } else if (Character.isHighSurrogate(current)) {
                        if (index + 1 >= value.length()
                                || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new CanonicalizationException("unpaired high surrogate");
                        }
                        output.append(current);
                        output.append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(current)) {
                        throw new CanonicalizationException("unpaired low surrogate");
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }

    private static void appendUnicodeEscape(char value, StringBuilder output) {
        output.append("\\u");
        output.append(HEX[(value >>> 12) & 0x0f]);
        output.append(HEX[(value >>> 8) & 0x0f]);
        output.append(HEX[(value >>> 4) & 0x0f]);
        output.append(HEX[value & 0x0f]);
    }
}
