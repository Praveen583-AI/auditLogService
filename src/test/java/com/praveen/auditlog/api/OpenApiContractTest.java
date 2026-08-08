package com.praveen.auditlog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.praveen.auditlog.api.dto.AuditEventResponse;
import com.praveen.auditlog.api.dto.CreateAuditEventRequest;
import com.praveen.auditlog.application.AuditQueryService;
import com.praveen.auditlog.application.VerificationResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void publicPathsAndMethodsMatchTheRunningController() throws IOException {
        Map<String, Object> document = loadContract();
        Map<String, Object> paths = map(document.get("paths"));

        Set<String> documented = paths.entrySet().stream()
                .flatMap(path -> map(path.getValue()).keySet().stream()
                        .filter(this::isHttpMethod)
                        .map(method -> method.toUpperCase(Locale.ROOT)
                                + " " + path.getKey()))
                .collect(Collectors.toSet());

        assertThat(documented).isEqualTo(controllerOperations());
        assertThat(paths.keySet()).noneMatch(path ->
                path.contains("export") || path.contains("jobs")
                        || path.startsWith("/internal"));
    }

    @Test
    void publicSchemasMatchSerializedRecordFields() throws IOException {
        Map<String, Object> schemas = map(map(loadContract()
                .get("components")).get("schemas"));

        assertProperties(schemas, "CreateAuditEventRequest",
                CreateAuditEventRequest.class);
        assertProperties(schemas, "AuditEventResponse",
                AuditEventResponse.class);
        assertProperties(schemas, "AuditEventPage",
                AuditQueryService.Page.class);
        assertProperties(schemas, "AuditEventView",
                AuditQueryService.AuditEventView.class);
        assertProperties(schemas, "VerificationResult",
                VerificationResult.class);
        assertProperties(schemas, "ApiError", ApiError.class);

        assertThat(enumValues(schemas, "VerificationFailureReason"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(
                                VerificationResult.FailureReason.values())
                        .map(Enum::name)
                        .toList());
        assertThat(enumValues(schemas, "VerificationResult", "status"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(
                                VerificationResult.Status.values())
                        .map(Enum::name)
                        .toList());
    }

    @Test
    void responseCodesMatchTheImplementedExceptionMappings()
            throws IOException {
        Map<String, Object> paths = map(loadContract().get("paths"));

        assertThat(responseCodes(paths, "/v1/audit/events", "post"))
                .containsExactlyInAnyOrder(
                        "200", "201", "400", "401", "403",
                        "409", "500", "503"
                );
        assertThat(responseCodes(paths, "/v1/audit/events", "get"))
                .containsExactlyInAnyOrder(
                        "200", "400", "401", "403", "500"
                );
        assertThat(responseCodes(
                paths,
                "/v1/audit/events/chains/{chainId}/verification",
                "get"
        )).containsExactlyInAnyOrder(
                "200", "401", "403", "404", "500"
        );
    }

    @Test
    void documentedExamplesMatchApplicationSerialization()
            throws IOException {
        Map<String, Object> document = loadContract();

        AuditEventResponse receipt = new AuditEventResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "tenant:tenant-7",
                815,
                Instant.parse("2026-08-07T14:30:12.456789Z"),
                "a".repeat(64),
                "SHA-256",
                1
        );
        assertExample(document, "CreatedReceipt", receipt);
        assertExample(document, "ReplayedReceipt", receipt);

        VerificationResult valid = VerificationResult.valid(5, 1L, 5L);
        Object verificationExample = map(map(map(map(document.get("paths"))
                .get("/v1/audit/events/chains/{chainId}/verification"))
                .get("get")).get("responses"));
        verificationExample = map(map(map(map(verificationExample)
                .get("200")).get("content")).get("application/json"))
                .get("examples");
        verificationExample = map(map(verificationExample).get("valid"))
                .get("value");
        JsonNode actualVerification = wireJson(valid);
        JsonNode documentedVerification = wireJson(verificationExample);
        assertThat(actualVerification).isEqualTo(documentedVerification);

        JsonNode payload = json.createObjectNode()
                .set("changedFields", json.createArrayNode().add("address"));
        AuditQueryService.AuditEventView event =
                new AuditQueryService.AuditEventView(
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000002"),
                        "tenant:tenant-7", 2, "ACCOUNT_UPDATED",
                        "authenticated-actor-42", "ACCOUNT", "account-123",
                        Instant.parse("2026-08-07T14:31:00Z"),
                        Instant.parse("2026-08-07T14:31:00.123456Z"),
                        payload, "b".repeat(64)
                );
        AuditQueryService.Page page = new AuditQueryService.Page(
                List.of(event),
                "eyJ2ZXJzaW9uIjoxLCJwdXJwb3NlIjoiQVVESVRfRVZFTlRfU0VBUkNIIn0"
        );
        Object pageExample = map(map(map(map(map(document.get("paths"))
                .get("/v1/audit/events")).get("get"))
                .get("responses")).get("200"));
        pageExample = map(map(pageExample).get("content"))
                .get("application/json");
        pageExample = map(map(map(pageExample).get("examples"))
                .get("crossChainPage")).get("value");
        JsonNode actualPage = wireJson(page);
        JsonNode documentedPage = wireJson(pageExample);
        assertThat(actualPage).isEqualTo(documentedPage);
    }

    @Test
    void everyLocalReferenceResolves() throws IOException {
        Map<String, Object> document = loadContract();
        assertLocalReferencesResolve(document, document);
    }

    private Set<String> controllerOperations() {
        String base = AuditEventController.class
                .getAnnotation(RequestMapping.class).value()[0];
        return Arrays.stream(AuditEventController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .map(method -> operation(base, method))
                .collect(Collectors.toSet());
    }

    private String operation(String base, Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return "GET " + base + firstPath(get.value());
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        return "POST " + base + firstPath(post.value());
    }

    private String firstPath(String[] paths) {
        return paths.length == 0 ? "" : paths[0];
    }

    private void assertProperties(
            Map<String, Object> schemas,
            String schemaName,
            Class<?> recordType
    ) {
        Set<String> documented = map(map(schemas.get(schemaName))
                .get("properties")).keySet();
        Set<String> serialized = Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(documented).as(schemaName).isEqualTo(serialized);
    }

    private Set<String> responseCodes(
            Map<String, Object> paths,
            String path,
            String method
    ) {
        return map(map(map(paths.get(path)).get(method)).get("responses"))
                .keySet();
    }

    private List<String> enumValues(
            Map<String, Object> schemas,
            String schemaName
    ) {
        return list(map(schemas.get(schemaName)).get("enum"));
    }

    private List<String> enumValues(
            Map<String, Object> schemas,
            String schemaName,
            String property
    ) {
        Map<String, Object> properties = map(map(schemas.get(schemaName))
                .get("properties"));
        return list(map(properties.get(property)).get("enum"));
    }

    private void assertExample(
            Map<String, Object> document,
            String name,
            Object expected
    ) {
        Map<String, Object> examples = map(map(document.get("components"))
                .get("examples"));
        Object value = map(examples.get(name)).get("value");
        JsonNode actual = wireJson(expected);
        JsonNode documented = wireJson(value);
        assertThat(actual)
                .as(name)
                .isEqualTo(documented);
    }

    private void assertLocalReferencesResolve(
            Object value,
            Map<String, Object> document
    ) {
        if (value instanceof Map<?, ?> values) {
            values.forEach((key, child) -> {
                if ("$ref".equals(key)) {
                    String reference = String.valueOf(child);
                    assertThat(reference).startsWith("#/");
                    Object resolved = document;
                    for (String segment : reference.substring(2).split("/")) {
                        resolved = map(resolved).get(segment
                                .replace("~1", "/")
                                .replace("~0", "~"));
                        assertThat(resolved).as(reference).isNotNull();
                    }
                } else {
                    assertLocalReferencesResolve(child, document);
                }
            });
        } else if (value instanceof List<?> values) {
            values.forEach(child ->
                    assertLocalReferencesResolve(child, document));
        }
    }

    private JsonNode wireJson(Object value) {
        try {
            return json.readTree(json.writeValueAsBytes(value));
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Cannot normalize contract example JSON", error
            );
        }
    }

    private boolean isHttpMethod(String value) {
        return Set.of("get", "post", "put", "patch", "delete")
                .contains(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadContract() throws IOException {
        try (var input = Files.newInputStream(Path.of(
                "openapi", "audit-api.yaml"))) {
            return new Yaml().load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value == null
                ? new LinkedHashMap<>()
                : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        return (List<String>) value;
    }
}
