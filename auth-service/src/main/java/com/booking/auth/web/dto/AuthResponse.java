package com.booking.auth.web.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {}