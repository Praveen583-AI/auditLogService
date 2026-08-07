package com.praveen.auditlog.api.dto;

/**
 * Resource affected by the audited action.
 *
 * @param type resource category
 * @param id stable resource identifier in the producer's domain
 */
public record ResourceDto(
        String type,
        String id
) {
}
