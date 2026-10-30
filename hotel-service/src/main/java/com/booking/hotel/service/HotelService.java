package com.booking.hotel.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.booking.hotel.domain.Hotel;
import com.booking.hotel.repo.HotelRepository;
import com.booking.hotel.web.dto.*;

@Service
@Transactional
public class HotelService {

  private final HotelRepository hotels;
  private final RoomService rooms;

  public HotelService(HotelRepository hotels, RoomService rooms) {
    this.hotels = hotels;
    this.rooms = rooms;
  }

  @Transactional(readOnly = true)
  public Page<HotelDto> search(String city, Short minStars, Short maxStars, Pageable pageable) {
    Specification<Hotel> spec =
        (root, q, cb) -> {
          List<Predicate> ps = new ArrayList<>();
          if (city != null && !city.isBlank()) {
            ps.add(cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
          }
          if (minStars != null) ps.add(cb.greaterThanOrEqualTo(root.get("stars"), minStars));
          if (maxStars != null) ps.add(cb.lessThanOrEqualTo(root.get("stars"), maxStars));
          return cb.and(ps.toArray(new Predicate[0]));
        };
    return hotels.findAll(spec, pageable).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public HotelDto get(Long id) {
    return hotels
        .findById(id)
        .map(this::toDto)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + id));
  }

  public HotelDto create(CreateHotelRequest req) {
    Hotel h = new Hotel(req.name(), req.city(), req.stars(), req.description());
    hotels.save(h);
    return toDto(h);
  }

  public HotelDto update(Long id, UpdateHotelRequest req) {
    Hotel h =
        hotels
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + id));
    h.setName(req.name());
    h.setCity(req.city());
    h.setStars(req.stars());
    h.setDescription(req.description());
    return toDto(h);
  }

  public void delete(Long id) {
    if (!hotels.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + id);
    }
    hotels.deleteById(id);
  }

  @Transactional(readOnly = true)
  public HotelStatisticsDto statistics(Long hotelId) {
    Hotel h =
        hotels
            .findById(hotelId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hotel not found: " + hotelId));
    var roomList = rooms.roomsOf(hotelId);
    int total = roomList.size();
    int totalBookings = roomList.stream().mapToInt(RoomDto::timesBooked).sum();
    double avg = total == 0 ? 0 : (double) totalBookings / total;
    var prices = roomList.stream().map(RoomDto::price).toList();
    var top =
        roomList.stream()
            .sorted((a, b) -> Integer.compare(b.timesBooked(), a.timesBooked()))
            .limit(5)
            .map(r -> new HotelStatisticsDto.RoomBookingStat(r.id(), r.number(), r.timesBooked()))
            .toList();
    return new HotelStatisticsDto(
        h.getId(),
        h.getName(),
        total,
        totalBookings,
        avg,
        prices.stream()
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .divide(
                java.math.BigDecimal.valueOf(Math.max(total, 1)),
                2,
                java.math.RoundingMode.HALF_UP),
        prices.stream().min(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO),
        prices.stream().max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO),
        top);
  }

  private HotelDto toDto(Hotel h) {
    return new HotelDto(
        h.getId(), h.getName(), h.getCity(), h.getStars(), h.getDescription(), h.getCreatedAt());
  }
}
