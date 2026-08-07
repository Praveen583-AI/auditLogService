package com.praveen.auditlog.api.dto;

/**
 * Actor asserted by the authenticated producer.
 *
 * @param id stable actor identifier in the producer's domain
 * @param type actor category, such as USER or SERVICE
 */
public record ActorDto(
        String id,
        String type
) {
}
