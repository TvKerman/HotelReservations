package com.booking.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.booking.booking.client.HotelClient;
import com.booking.booking.client.HotelResilientClient;
import com.booking.booking.domain.Booking;
import com.booking.booking.domain.BookingStatus;
import com.booking.booking.repo.BookingRepository;
import com.booking.booking.web.dto.BookingDto;
import com.booking.booking.web.dto.CreateBookingRequest;
import com.booking.booking.web.dto.RoomDto;

@Service
public class BookingService {

  private static final Logger log = LoggerFactory.getLogger(BookingService.class);

  private final BookingRepository bookings;
  private final HotelClient hotelClient;
  private final HotelResilientClient resilientClient;

  public BookingService(
      BookingRepository bookings, HotelClient hotelClient, HotelResilientClient resilientClient) {
    this.bookings = bookings;
    this.hotelClient = hotelClient;
    this.resilientClient = resilientClient;
  }

  @Transactional
  public BookingDto create(
      CreateBookingRequest req, Long userId, String userEmail, String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = bookings.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
        log.info(
            "Idempotent replay for key={}, bookingId={}", idempotencyKey, existing.get().getId());
        return toDto(existing.get());
      }
    }

    if (!req.checkOut().isAfter(req.checkIn())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkOut must be after checkIn");
    }
    if (req.checkIn().isBefore(LocalDate.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkIn cannot be in the past");
    }

    Long roomId = req.roomId();
    if (roomId == null) {
      List<RoomDto> available = hotelClient.getAvailableRooms(req.hotelId());
      roomId =
          available.stream()
              .filter(r -> !hasConflict(r.id(), req.checkIn(), req.checkOut()))
              .map(RoomDto::id)
              .findFirst()
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.CONFLICT, "No rooms available for the requested dates"));
    } else {
      if (hasConflict(roomId, req.checkIn(), req.checkOut())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Room is already booked for these dates");
      }
    }

    var booking =
        new Booking(
            userId,
            userEmail,
            req.hotelId(),
            roomId,
            req.checkIn(),
            req.checkOut(),
            BookingStatus.PENDING,
            idempotencyKey);

    try {
      bookings.saveAndFlush(booking);
    } catch (DataIntegrityViolationException e) {
      if (idempotencyKey != null) {
        var existing = bookings.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return toDto(existing.get());
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Room is already booked for these dates");
    } catch (CannotAcquireLockException e) {
      log.warn("Deadlock during concurrent booking for room {}, treating as conflict", roomId);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking conflict, please retry");
    }

    try {
      resilientClient.incrementBooked(req.hotelId(), roomId);
    } catch (Exception e) {
      log.warn(
          "Reserve failed for bookingId={}, marking CANCELLED: {}",
          booking.getId(),
          e.getMessage());
      booking.setStatus(BookingStatus.CANCELLED);
      return toDto(booking);
    }

    try {
      booking.setStatus(BookingStatus.CONFIRMED);
      bookings.saveAndFlush(booking);
    } catch (CannotAcquireLockException e) {
      log.warn("Deadlock on confirm for bookingId={}", booking.getId());
      try {
        resilientClient.decrementBooked(req.hotelId(), roomId);
      } catch (Exception ignore) {
      }
      booking.setStatus(BookingStatus.CANCELLED);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking conflict, please retry");
    } catch (Exception e) {
      log.error("Confirm failed for bookingId={}, compensating", booking.getId(), e);
      try {
        resilientClient.decrementBooked(req.hotelId(), roomId);
      } catch (Exception ignore) {
        log.error("Compensation also failed for bookingId={}", booking.getId(), ignore);
      }
      booking.setStatus(BookingStatus.CANCELLED);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Booking confirmation failed");
    }

    log.info(
        "Booking created: id={}, userId={}, roomId={}, status={}",
        booking.getId(),
        userId,
        roomId,
        booking.getStatus());
    return toDto(booking);
  }

  @Transactional(readOnly = true)
  public Page<BookingDto> list(
      Long userId, boolean admin, BookingStatus status, Pageable pageable) {
    Page<Booking> page;
    if (admin) {
      page = status != null ? bookings.findByStatus(status, pageable) : bookings.findAll(pageable);
    } else {
      page =
          status != null
              ? bookings.findByUserIdAndStatus(userId, status, pageable)
              : bookings.findByUserId(userId, pageable);
    }
    return page.map(this::toDto);
  }

  @Transactional(readOnly = true)
  public BookingDto get(Long id, Long userId, boolean admin) {
    var b =
        bookings
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    if (!admin && !Objects.equals(b.getUserId(), userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    return toDto(b);
  }

  @Transactional
  public BookingDto cancel(Long id, Long userId, boolean admin) {
    var b =
        bookings
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    if (!admin && !Objects.equals(b.getUserId(), userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    if (b.getStatus() == BookingStatus.CANCELLED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already cancelled");
    }
    b.setStatus(BookingStatus.CANCELLED);
    try {
      resilientClient.decrementBooked(b.getHotelId(), b.getRoomId());
    } catch (Exception e) {
      log.warn("Decrement on cancel failed for bookingId={}: {}", b.getId(), e.getMessage());
    }
    return toDto(b);
  }

  private boolean hasConflict(Long roomId, LocalDate checkIn, LocalDate checkOut) {
    return bookings.countOverlappingActive(roomId, checkIn, checkOut) > 0;
  }

  private BookingDto toDto(Booking b) {
    return new BookingDto(
        b.getId(),
        b.getUserId(),
        b.getUserEmail(),
        b.getHotelId(),
        b.getRoomId(),
        b.getCheckIn(),
        b.getCheckOut(),
        b.getStatus().name(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }
}
