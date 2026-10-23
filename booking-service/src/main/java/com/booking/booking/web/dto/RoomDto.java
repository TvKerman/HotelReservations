package com.booking.booking.web.dto;

import java.math.BigDecimal;

public record RoomDto(Long id, Long hotelId, String number, Short capacity,
                      BigDecimal price, Integer timesBooked) {}