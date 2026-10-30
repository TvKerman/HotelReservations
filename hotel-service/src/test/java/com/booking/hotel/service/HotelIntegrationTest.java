package com.booking.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.booking.hotel.AbstractIntegrationTest;
import com.booking.hotel.repo.HotelRepository;
import com.booking.hotel.repo.RoomRepository;
import com.booking.hotel.web.dto.*;

@DisplayName("Hotel integration (real Postgres)")
class HotelIntegrationTest extends AbstractIntegrationTest {

  @Autowired HotelService hotelService;
  @Autowired RoomService roomService;
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;

  @BeforeEach
  void clean() {
    hotels.deleteAll(); // CASCADE удалит rooms
  }

  @Nested
  @DisplayName("hotel CRUD")
  class HotelCrud {
    @Test
    @DisplayName("create, update, delete round-trip")
    void roundTrip() {
      var created =
          hotelService.create(new CreateHotelRequest("Test", "Moscow", (short) 4, "desc"));
      assertThat(created.id()).isNotNull();

      var updated =
          hotelService.update(
              created.id(), new UpdateHotelRequest("Updated", "SPb", (short) 5, "d2"));
      assertThat(updated.name()).isEqualTo("Updated");
      assertThat(updated.city()).isEqualTo("SPb");

      hotelService.delete(created.id());
      assertThat(hotels.findById(created.id())).isEmpty();
    }
  }

  @Nested
  @DisplayName("search filters")
  class Search {
    @BeforeEach
    void seed() {
      hotelService.create(new CreateHotelRequest("A", "Moscow", (short) 5, ""));
      hotelService.create(new CreateHotelRequest("B", "Moscow", (short) 3, ""));
      hotelService.create(new CreateHotelRequest("C", "SPb", (short) 5, ""));
    }

    @Test
    @DisplayName("by city")
    void byCity() {
      var page =
          hotelService.search(
              "Moscow", null, null, org.springframework.data.domain.PageRequest.of(0, 10));
      assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("by stars")
    void byStars() {
      var page =
          hotelService.search(
              null, (short) 5, (short) 5, org.springframework.data.domain.PageRequest.of(0, 10));
      assertThat(page.getTotalElements()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("rooms")
  class Rooms {
    Long hotelId;

    @BeforeEach
    void seed() {
      hotelId = hotelService.create(new CreateHotelRequest("H", "M", (short) 4, "")).id();
      roomService.create(
          hotelId, new CreateRoomRequest("101", (short) 2, new BigDecimal("100.00")));
      roomService.create(
          hotelId, new CreateRoomRequest("102", (short) 2, new BigDecimal("150.00")));
      roomService.create(
          hotelId, new CreateRoomRequest("103", (short) 4, new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("duplicate number -> 409")
    void duplicate() {
      assertThatThrownBy(
              () ->
                  roomService.create(
                      hotelId, new CreateRoomRequest("101", (short) 2, BigDecimal.TEN)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("available sorted by times_booked asc, id asc")
    void availableSorted() {
      // увеличим times_booked у "101" и "103"
      roomService.incrementBooked(
          rooms.findByHotelId(hotelId).stream()
              .filter(r -> r.getNumber().equals("101"))
              .findFirst()
              .orElseThrow()
              .getId());
      roomService.incrementBooked(
          rooms.findByHotelId(hotelId).stream()
              .filter(r -> r.getNumber().equals("101"))
              .findFirst()
              .orElseThrow()
              .getId());
      roomService.incrementBooked(
          rooms.findByHotelId(hotelId).stream()
              .filter(r -> r.getNumber().equals("103"))
              .findFirst()
              .orElseThrow()
              .getId());

      var sorted = roomService.availableForHotel(hotelId);
      assertThat(sorted).extracting(RoomDto::number).containsExactly("102", "103", "101");
    }

    @Test
    @DisplayName("increment/decrement")
    void incrementDecrement() {
      var room = rooms.findByHotelId(hotelId).get(0);
      roomService.incrementBooked(room.getId());
      assertThat(rooms.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);

      roomService.decrementBooked(room.getId());
      assertThat(rooms.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(0);
    }
  }
}
