package com.booking.booking.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long hotelId,
        Long roomId,
        @NotNull @FutureOrPresent LocalDate checkIn,
        @NotNull @Future LocalDate checkOut
) {
    @AssertTrue(message = "checkOut must be after checkIn")
    @JsonIgnore
    public boolean isDatesOrderValid() {
        if (checkIn == null || checkOut == null) return true;
        return checkOut.isAfter(checkIn);
    }
}