package com.booking.hotel.web.dto;

import jakarta.validation.constraints.*;

public record UpdateHotelRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String city,
        @NotNull @Min(1) @Max(5) Short stars,
        @Size(max = 5000) String description
) {}