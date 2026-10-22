package com.booking.hotel.web;

import com.booking.hotel.service.RoomService;
import com.booking.hotel.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/hotels/{hotelId}/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public List<RoomDto> list(@PathVariable Long hotelId,
                              @RequestParam(required = false) Short minCapacity,
                              @RequestParam(required = false) BigDecimal maxPrice) {
        return rooms.search(hotelId, minCapacity, maxPrice);
    }

    @GetMapping("/available")
    public List<RoomDto> available(@PathVariable Long hotelId) {
        return rooms.availableForHotel(hotelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto create(@PathVariable Long hotelId, @Valid @RequestBody CreateRoomRequest req) {
        return rooms.create(hotelId, req);
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto update(@PathVariable Long hotelId, @PathVariable Long roomId,
                          @Valid @RequestBody UpdateRoomRequest req) {
        return rooms.update(hotelId, roomId, req);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long hotelId, @PathVariable Long roomId) {
        rooms.delete(hotelId, roomId);
    }

    @PostMapping("/{roomId}/increment-booked")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public void increment(@PathVariable Long hotelId, @PathVariable Long roomId) {
        rooms.incrementBooked(roomId);
    }
}