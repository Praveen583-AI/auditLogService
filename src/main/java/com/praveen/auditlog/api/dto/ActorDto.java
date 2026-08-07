package com.praveen.auditlog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Actor asserted by the authenticated producer.
 */
public record ActorDto(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 50) String type
) {
}
