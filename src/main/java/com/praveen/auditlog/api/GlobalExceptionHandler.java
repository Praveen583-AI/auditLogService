package com.praveen.auditlog.api;

import com.praveen.auditlog.application.ChainNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::safeViolation)
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request is invalid.",
                correlationId(request),
                violations
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ApiError> invalidRequest(
            Exception ignored,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request is invalid.",
                correlationId(request),
                List.of()
        );
    }

    @ExceptionHandler(ChainNotFoundException.class)
    public ResponseEntity<ApiError> chainNotFound(
            ChainNotFoundException ignored,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "CHAIN_NOT_FOUND",
                "The audit chain was not found.",
                correlationId(request),
                List.of()
        );
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiError> idempotencyConflict(
            IdempotencyKeyReusedException ignored,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "The Idempotency-Key has already been used with a different request.",
                correlationId(request),
                List.of()
        );
    }

    @ExceptionHandler({
            PayloadTooLargeException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiError> payloadTooLarge(
            Exception ignored,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_LIMIT_EXCEEDED",
                "The request exceeds the permitted size.",
                correlationId(request),
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(
            Exception ignored,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                correlationId(request),
                List.of()
        );
    }

    private ApiError.FieldViolation safeViolation(FieldError error) {
        return new ApiError.FieldViolation(
                error.getField(),
                violationCode(error.getCode()),
                "The field is invalid."
        );
    }

    private String violationCode(String validationCode) {
        if ("NotNull".equals(validationCode) || "NotBlank".equals(validationCode)) {
            return "REQUIRED";
        }
        if ("Size".equals(validationCode)) {
            return "MAX_LENGTH_EXCEEDED";
        }
        if ("Pattern".equals(validationCode)) {
            return "INVALID_FORMAT";
        }
        return "INVALID_VALUE";
    }

    private String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader(CORRELATION_HEADER);
        if (supplied != null && SAFE_CORRELATION_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            String correlationId,
            List<ApiError.FieldViolation> violations
    ) {
        if (status.is5xxServerError()) {
            LOGGER.error(
                    "audit_request_failed status={} code={} correlationId={} violationCount={}",
                    status.value(), code, correlationId, violations.size()
            );
        } else {
            LOGGER.warn(
                    "audit_request_rejected status={} code={} correlationId={} violationCount={}",
                    status.value(), code, correlationId, violations.size()
            );
        }

        ApiError body = new ApiError(code, message, correlationId, violations);
        return ResponseEntity.status(status)
                .header(CORRELATION_HEADER, correlationId)
                .body(body);
    }
}
