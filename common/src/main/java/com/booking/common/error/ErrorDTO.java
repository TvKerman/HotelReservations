package com.booking.common.error;

import java.time.Instant;

public record ErrorDTO(
    Instant timestamp,
    int status,
    String error,
    ErrorCode code,
    String message,
    String path,
    String traceId) {
  public static ErrorDTO of(
      int status, String error, ErrorCode code, String message, String path, String traceId) {
    return new ErrorDTO(Instant.now(), status, error, code, message, path, traceId);
  }
}
