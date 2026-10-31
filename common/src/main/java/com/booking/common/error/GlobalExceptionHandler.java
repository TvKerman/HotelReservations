package com.booking.common.error;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public static final String TRACE_HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "traceId";

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorDTO> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest req) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
    ErrorCode code = mapToCode(status);
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), status.value(), message);
    return ResponseEntity.status(status)
        .body(
            ErrorDTO.of(
                status.value(), status.name(), code, message, req.getRequestURI(), traceId(req)));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorDTO> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest req) {
    log.warn("{} {} -> 403: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            ErrorDTO.of(
                403,
                "FORBIDDEN",
                ErrorCode.FORBIDDEN,
                "Access denied",
                req.getRequestURI(),
                traceId(req)));
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ErrorDTO> handlePropertyReference(
      PropertyReferenceException ex, HttpServletRequest req) {
    log.warn("{} {} -> 400 BAD_REQUEST: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
    return ResponseEntity.badRequest()
        .body(
            ErrorDTO.of(
                400,
                "BAD_REQUEST",
                ErrorCode.BAD_REQUEST,
                "Invalid sort field: " + ex.getPropertyName(),
                req.getRequestURI(),
                traceId(req)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDTO> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    log.warn("{} {} -> 400 VALIDATION_FAILED: {}", req.getMethod(), req.getRequestURI(), message);
    return ResponseEntity.badRequest()
        .body(
            ErrorDTO.of(
                400,
                "BAD_REQUEST",
                ErrorCode.VALIDATION_FAILED,
                message,
                req.getRequestURI(),
                traceId(req)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorDTO> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest req) {
    log.warn("{} {} -> 400: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
    return ResponseEntity.badRequest()
        .body(
            ErrorDTO.of(
                400,
                "BAD_REQUEST",
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                req.getRequestURI(),
                traceId(req)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDTO> handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("{} {} -> 500: {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ErrorDTO.of(
                500,
                "INTERNAL_SERVER_ERROR",
                ErrorCode.INTERNAL_ERROR,
                "Internal error. See logs for traceId=" + traceId(req),
                req.getRequestURI(),
                traceId(req)));
  }

  private ErrorCode mapToCode(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
      case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
      case FORBIDDEN -> ErrorCode.FORBIDDEN;
      case NOT_FOUND -> ErrorCode.NOT_FOUND;
      case CONFLICT -> ErrorCode.CONFLICT;
      case SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
      default -> ErrorCode.INTERNAL_ERROR;
    };
  }

  private String traceId(HttpServletRequest req) {
    String header = req.getHeader(TRACE_HEADER);
    if (header != null && !header.isBlank()) return header;
    return MDC.get(MDC_KEY);
  }
}
