package com.booking.hotel.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record HotelStatisticsDto(
        Long hotelId,
        String hotelName,
        int totalRooms,
        int totalBookings,
        double avgBookingsPerRoom,
        BigDecimal avgPrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<RoomBookingStat> topRooms
) {
    public record RoomBookingStat(Long roomId, String number, int timesBooked) {}
}