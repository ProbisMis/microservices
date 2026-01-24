package com.demo.programming.order_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Order Service.
 *
 * STUDY NOTES:
 * - @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * - Handles exceptions from ALL controllers in this service
 * - Returns consistent JSON error responses
 * - Exception handlers are matched from most specific to most general
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Standard error response structure.
     * Using Java record for immutability and automatic equals/hashCode/toString.
     */
    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path
    ) {}

    /**
     * Handles business logic violations.
     * Examples: "Product not in stock", "Order not found"
     *
     * STUDY NOTE: This catches IllegalArgumentException thrown from your services.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Business rule violation at {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles @Valid annotation failures.
     * Triggered when request body fails JSR-303 validation.
     *
     * STUDY NOTE: Returns field-level error messages for client-side form validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation failed at {}", request.getRequestURI());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("path", request.getRequestURI());
        response.put("errors", fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Custom exception for resource not found scenarios.
     * Maps to HTTP 404.
     *
     * STUDY NOTE: Create custom exceptions for specific error types.
     * This provides cleaner code than reusing IllegalArgumentException.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.info("Resource not found: {}", ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     *
     * STUDY NOTE: This is your safety net. Always include one.
     * - Log the full stack trace for debugging
     * - Return generic message to client (don't expose internals)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error at {}: ", request.getRequestURI(), ex);

        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(error);
    }

    /**
     * Custom exception class for "not found" scenarios.
     * Nested class for convenience - in production, put in separate file.
     */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
