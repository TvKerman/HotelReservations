package com.booking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.booking.booking.client.HotelClient;
import com.booking.booking.client.HotelResilientClient;
import com.booking.booking.domain.Booking;
import com.booking.booking.domain.BookingStatus;
import com.booking.booking.repo.BookingRepository;
import com.booking.booking.web.dto.CreateBookingRequest;
import com.booking.booking.web.dto.RoomDto;
import com.booking.common.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService - business logic")
class BookingServiceTest {

  @Mock BookingRepository bookings;
  @Mock HotelClient hotelClient;
  @Mock HotelResilientClient resilientClient;

  @InjectMocks BookingService service;

  static final Long USER_ID = 1L;
  static final String EMAIL = "user@example.com";
  static final Long HOTEL_ID = 100L;
  static final Long ROOM_ID = 200L;
  static final LocalDate IN = LocalDate.now().plusDays(1);
  static final LocalDate OUT = LocalDate.now().plusDays(3);

  @Nested
  @DisplayName("create: happy path")
  class CreateHappyPath {
    @Test
    @DisplayName("should return CONFIRMED and call hotel increment")
    void shouldReturnConfirmed() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT);
      when(bookings.countOverlappingActive(ROOM_ID, IN, OUT)).thenReturn(0L);
      when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

      var result = service.create(req, USER_ID, EMAIL, null);

      assertThat(result.status()).isEqualTo("CONFIRMED");
      verify(resilientClient).incrementBooked(HOTEL_ID, ROOM_ID);
      verify(resilientClient, never()).decrementBooked(any(), any());
    }
  }

  @Nested
  @DisplayName("create: saga compensation")
  class SagaCompensation {
    @Test
    @DisplayName("hotel fails -> CANCELLED")
    void hotelFails() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT);
      when(bookings.countOverlappingActive(ROOM_ID, IN, OUT)).thenReturn(0L);
      when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
      doThrow(new RuntimeException("hotel down"))
          .when(resilientClient)
          .incrementBooked(HOTEL_ID, ROOM_ID);

      var result = service.create(req, USER_ID, EMAIL, null);

      assertThat(result.status()).isEqualTo("CANCELLED");
    }
  }

  @Nested
  @DisplayName("create: idempotency")
  class Idempotency {
    @Test
    @DisplayName("same key -> returns existing without new save or hotel call")
    void sameKey() {
      var existing =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, "k1");
      when(bookings.findByIdempotencyKey("k1")).thenReturn(Optional.of(existing));

      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT);
      var result = service.create(req, USER_ID, EMAIL, "k1");

      assertThat(result.status()).isEqualTo("CONFIRMED");
      verify(bookings, never()).saveAndFlush(any());
      verify(resilientClient, never()).incrementBooked(any(), any());
    }
  }

  @Nested
  @DisplayName("create: validation")
  class CreateValidation {
    @Test
    @DisplayName("checkOut <= checkIn -> 400")
    void reversedDates() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, OUT, IN);
      assertThatThrownBy(() -> service.create(req, USER_ID, EMAIL, null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("checkOut must be after checkIn");
    }

    @Test
    @DisplayName("checkIn in past -> 400")
    void pastCheckIn() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, LocalDate.now().minusDays(1), OUT);
      assertThatThrownBy(() -> service.create(req, USER_ID, EMAIL, null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("cannot be in the past");
    }

    @Test
    @DisplayName("existing overlap -> 409")
    void overlap() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT);
      when(bookings.countOverlappingActive(ROOM_ID, IN, OUT)).thenReturn(1L);
      assertThatThrownBy(() -> service.create(req, USER_ID, EMAIL, null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("already booked");
    }
  }

  @Nested
  @DisplayName("create: auto-select")
  class AutoSelect {
    @Test
    @DisplayName("roomId=null -> picks first non-overlapping from available")
    void autoPicksFirst() {
      var req = new CreateBookingRequest(HOTEL_ID, null, IN, OUT);
      when(hotelClient.getAvailableRooms(HOTEL_ID))
          .thenReturn(
              List.of(
                  new RoomDto(ROOM_ID, HOTEL_ID, "101", (short) 2, null, 0),
                  new RoomDto(ROOM_ID + 1, HOTEL_ID, "102", (short) 2, null, 0)));
      when(bookings.countOverlappingActive(anyLong(), any(), any())).thenReturn(0L);
      when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

      var result = service.create(req, USER_ID, EMAIL, null);

      assertThat(result.roomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("roomId=null, all overlap -> 409")
    void noAvailable() {
      var req = new CreateBookingRequest(HOTEL_ID, null, IN, OUT);
      when(hotelClient.getAvailableRooms(HOTEL_ID))
          .thenReturn(List.of(new RoomDto(ROOM_ID, HOTEL_ID, "101", (short) 2, null, 0)));
      when(bookings.countOverlappingActive(anyLong(), any(), any())).thenReturn(1L);
      assertThatThrownBy(() -> service.create(req, USER_ID, EMAIL, null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("No rooms available");
    }
  }

  @Nested
  @DisplayName("cancel")
  class Cancel {
    @Test
    @DisplayName("own booking -> CANCELLED + decrement")
    void cancelOwn() {
      var b =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      when(bookings.findById(1L)).thenReturn(Optional.of(b));

      var result = service.cancel(1L, USER_ID, false);

      assertThat(result.status()).isEqualTo("CANCELLED");
      verify(resilientClient).decrementBooked(HOTEL_ID, ROOM_ID);
    }

    @Test
    @DisplayName("other user's booking -> 403")
    void cancelOthers() {
      var b =
          new Booking(
              999L, "other@x.com", HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      when(bookings.findById(1L)).thenReturn(Optional.of(b));

      assertThatThrownBy(() -> service.cancel(1L, USER_ID, false))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("ADMIN can cancel any booking")
    void adminCancel() {
      var b =
          new Booking(
              999L, "other@x.com", HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      when(bookings.findById(1L)).thenReturn(Optional.of(b));

      var result = service.cancel(1L, USER_ID, true);
      assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("already cancelled -> 409")
    void alreadyCancelled() {
      var b =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CANCELLED, null);
      when(bookings.findById(1L)).thenReturn(Optional.of(b));

      assertThatThrownBy(() -> service.cancel(1L, USER_ID, false))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("already cancelled");
    }
  }

  @Nested
  @DisplayName("get")
  class Get {
    @Test
    @DisplayName("not found -> 404")
    void notFound() {
      when(bookings.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.get(99L, USER_ID, false))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Booking not found");
    }

    @Test
    @DisplayName("other user's -> 403")
    void otherUser() {
      var b =
          new Booking(
              999L, "other@x.com", HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      when(bookings.findById(1L)).thenReturn(Optional.of(b));

      assertThatThrownBy(() -> service.get(1L, USER_ID, false))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Access denied");
    }
  }
}
