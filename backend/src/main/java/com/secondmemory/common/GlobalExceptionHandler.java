package com.secondmemory.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ApiError> handleProviderError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String hint = switch (status) {
            case 401, 403 -> "Check the configured provider API key and access permissions.";
            case 404 -> "Check the configured provider base URL and model name.";
            case 429 -> "The provider quota or rate limit was reached. Check quota and retry later.";
            case 400 -> "The provider rejected the request. Check the model and recording format.";
            default -> "The AI provider could not complete the request. Retry later.";
        };
        // Never forward provider response bodies: they can contain request data or credentials.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_PROVIDER_ERROR", "AI provider HTTP " + status + ". " + hint, Instant.now()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiError> handleProviderConnection(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiError("AI_PROVIDER_UNAVAILABLE",
                        "Could not reach the AI provider or the request timed out. Check connectivity and retry.", Instant.now()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message, Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", ex.getMessage(), Instant.now()));
    }
}
