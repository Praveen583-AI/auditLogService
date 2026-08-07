package com.praveen.auditlog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Resource affected by the audited action.
 */
public record ResourceDto(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
        String type,

        @NotBlank
        @Size(max = 255)
        String id
) {
}
