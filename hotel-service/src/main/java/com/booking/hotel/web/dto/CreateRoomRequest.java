package com.booking.hotel.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateRoomRequest(
        @NotBlank @Size(max = 50) String number,
        @NotNull @Min(1) @Max(20) Short capacity,
        @NotNull @DecimalMin("0.00") BigDecimal price
) {}