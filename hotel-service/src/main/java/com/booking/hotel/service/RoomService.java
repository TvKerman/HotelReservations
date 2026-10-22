package com.booking.hotel.service;

import com.booking.hotel.domain.Hotel;
import com.booking.hotel.domain.Room;
import com.booking.hotel.repo.HotelRepository;
import com.booking.hotel.repo.RoomRepository;
import com.booking.hotel.web.dto.CreateRoomRequest;
import com.booking.hotel.web.dto.RoomDto;
import com.booking.hotel.web.dto.UpdateRoomRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class RoomService {

    private final RoomRepository rooms;
    private final HotelRepository hotels;

    public RoomService(RoomRepository rooms, HotelRepository hotels) {
        this.rooms = rooms;
        this.hotels = hotels;
    }

    @Transactional(readOnly = true)
    public List<RoomDto> roomsOf(Long hotelId) {
        return rooms.findByHotelId(hotelId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomDto> search(Long hotelId, Short minCapacity, BigDecimal maxPrice) {
        Specification<Room> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("hotel").get("id"), hotelId));
            if (minCapacity != null) ps.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
            if (maxPrice != null) ps.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return rooms.findAll(spec).stream().map(this::toDto).toList();
    }


    @Transactional(readOnly = true)
    public List<RoomDto> availableForHotel(Long hotelId) {
        if (!hotels.existsById(hotelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + hotelId);
        }
        return rooms.findAvailableOrdered(hotelId).stream().map(this::toDto).toList();
    }

    public RoomDto create(Long hotelId, CreateRoomRequest req) {
        Hotel h = hotels.findById(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + hotelId));
        if (rooms.existsByHotelIdAndNumber(hotelId, req.number())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists in this hotel");
        }
        Room r = new Room(h, req.number(), req.capacity(), req.price());
        rooms.save(r);
        return toDto(r);
    }

    public RoomDto update(Long hotelId, Long roomId, UpdateRoomRequest req) {
        Room r = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        if (!r.getHotel().getId().equals(hotelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not in this hotel");
        }
        if (!r.getNumber().equals(req.number()) && rooms.existsByHotelIdAndNumber(hotelId, req.number())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room number already exists in this hotel");
        }
        r.setNumber(req.number());
        r.setCapacity(req.capacity());
        r.setPrice(req.price());
        return toDto(r);
    }

    public void delete(Long hotelId, Long roomId) {
        Room r = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        if (!r.getHotel().getId().equals(hotelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not in this hotel");
        }
        rooms.delete(r);
    }

    public void incrementBooked(Long roomId) {
        Room r = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        r.setTimesBooked(r.getTimesBooked() + 1);
    }

    private RoomDto toDto(Room r) {
        return new RoomDto(r.getId(), r.getHotel().getId(), r.getNumber(), r.getCapacity(),
                r.getPrice(), r.getTimesBooked());
    }
}