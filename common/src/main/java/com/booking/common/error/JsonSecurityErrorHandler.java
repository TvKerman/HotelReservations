package com.booking.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String TRACE_HEADER = "X-Correlation-Id";

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(request, response, 401, ErrorCode.UNAUTHORIZED, "Unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, 403, ErrorCode.FORBIDDEN, "Access denied");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorDTO body = ErrorDTO.of(status,
                status == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
                code, message,
                request.getRequestURI(),
                request.getHeader(TRACE_HEADER));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}