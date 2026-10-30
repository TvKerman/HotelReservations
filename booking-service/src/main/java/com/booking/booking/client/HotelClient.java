package com.booking.booking.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.booking.booking.web.dto.RoomDto;

@FeignClient(name = "hotel-service", path = "/hotels")
public interface HotelClient {

  @GetMapping("/{hotelId}/rooms/available")
  List<RoomDto> getAvailableRooms(@PathVariable("hotelId") Long hotelId);

  @PostMapping("/{hotelId}/rooms/{roomId}/increment-booked")
  void incrementBooked(@PathVariable("hotelId") Long hotelId, @PathVariable("roomId") Long roomId);

  @PostMapping("/{hotelId}/rooms/{roomId}/decrement-booked")
  void decrementBooked(@PathVariable("hotelId") Long hotelId, @PathVariable("roomId") Long roomId);
}
