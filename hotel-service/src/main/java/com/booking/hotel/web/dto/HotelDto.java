package com.booking.hotel.web.dto;

import java.time.Instant;

public record HotelDto(Long id, String name, String city, Short stars,
                       String description, Instant createdAt) {}