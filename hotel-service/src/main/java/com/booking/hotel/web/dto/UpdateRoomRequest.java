package com.booking.hotel.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record UpdateRoomRequest(
    @NotBlank @Size(max = 50) String number,
    @NotNull @Min(1) @Max(20) Short capacity,
    @NotNull @DecimalMin("0.00") BigDecimal price) {}
