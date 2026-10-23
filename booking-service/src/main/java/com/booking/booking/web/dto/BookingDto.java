package com.booking.booking.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BookingDto(Long id, Long userId, String userEmail,
                         Long hotelId, Long roomId,
                         LocalDate checkIn, LocalDate checkOut,
                         String status, Instant createdAt, Instant updatedAt) {}