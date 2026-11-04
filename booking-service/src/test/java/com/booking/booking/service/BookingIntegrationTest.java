package com.booking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

import com.booking.booking.AbstractIntegrationTest;
import com.booking.booking.client.HotelClient;
import com.booking.booking.client.HotelResilientClient;
import com.booking.booking.domain.Booking;
import com.booking.booking.domain.BookingStatus;
import com.booking.booking.repo.BookingRepository;
import com.booking.booking.web.dto.CreateBookingRequest;

@DisplayName("Booking integration (real Postgres + EXCLUDE constraint)")
class BookingIntegrationTest extends AbstractIntegrationTest {

  @Autowired BookingService service;
  @Autowired BookingRepository bookings;
  @MockBean HotelClient hotelClient;
  @MockBean HotelResilientClient resilientClient;

  static final Long USER_ID = 10L;
  static final String EMAIL = "integration@example.com";
  static final Long HOTEL_ID = 1000L;
  static final Long ROOM_ID = 2000L;
  static final LocalDate IN = LocalDate.now().plusDays(30);
  static final LocalDate OUT = LocalDate.now().plusDays(32);

  @BeforeEach
  void cleanDb() {
    bookings.deleteAll();
    reset(hotelClient, resilientClient);
  }

  @Nested
  @DisplayName("saga with real DB")
  class Saga {
    @Test
    @DisplayName("happy path: booking persisted as CONFIRMED, increment called")
    void happyPath() {
      var dto =
          service.create(
              new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT), USER_ID, EMAIL, null);

      assertThat(dto.status()).isEqualTo("CONFIRMED");
      var saved = bookings.findById(dto.id()).orElseThrow();
      assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
      verify(resilientClient).incrementBooked(HOTEL_ID, ROOM_ID);
    }

    @Test
    @DisplayName("hotel fails: booking persisted as CANCELLED (compensation)")
    void compensation() {
      doThrow(new RuntimeException("hotel down"))
          .when(resilientClient)
          .incrementBooked(HOTEL_ID, ROOM_ID);

      var dto =
          service.create(
              new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT), USER_ID, EMAIL, null);

      assertThat(dto.status()).isEqualTo("CANCELLED");
      var saved = bookings.findById(dto.id()).orElseThrow();
      assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
  }

  @Nested
  @DisplayName("idempotency (real DB)")
  class Idempotency {
    @Test
    @DisplayName("same key twice -> same booking, one DB row")
    void sameKey() {
      var req = new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT);
      var first = service.create(req, USER_ID, EMAIL, "k-unique-1");
      var second = service.create(req, USER_ID, EMAIL, "k-unique-1");

      assertThat(first.id()).isEqualTo(second.id());
      assertThat(bookings.count()).isEqualTo(1);
      verify(resilientClient, times(1)).incrementBooked(anyLong(), anyLong());
    }
  }

  @Nested
  @DisplayName("DB EXCLUDE constraint")
  class ExcludeConstraint {
    @Test
    @DisplayName("overlapping INSERT is rejected by DB even if service check bypassed")
    void overlapRejectedByDb() {
      var existing =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      bookings.saveAndFlush(existing);

      var overlapping =
          new Booking(
              USER_ID + 1,
              "other@x.com",
              HOTEL_ID,
              ROOM_ID,
              IN.plusDays(1),
              OUT.plusDays(1),
              BookingStatus.CONFIRMED,
              null);

      assertThatThrownBy(() -> bookings.saveAndFlush(overlapping))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CANCELLED booking does not block new overlapping booking")
    void cancelledDoesNotBlock() {
      var cancelled =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CANCELLED, null);
      bookings.saveAndFlush(cancelled);

      var newBooking =
          new Booking(USER_ID, EMAIL, HOTEL_ID, ROOM_ID, IN, OUT, BookingStatus.CONFIRMED, null);
      bookings.saveAndFlush(newBooking);

      assertThat(bookings.count()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("parallel bookings on the same room")
  class Parallel {
    @Test
    @DisplayName("10 threads -> exactly 1 CONFIRMED, rest fail")
    void parallelNoOverbooking() throws Exception {
      int threads = 10;
      var executor = Executors.newFixedThreadPool(threads);
      var confirmed = new AtomicInteger(0);
      var failed = new AtomicInteger(0);
      var startGate = new CountDownLatch(1);
      var doneGate = new CountDownLatch(threads);

      for (int i = 0; i < threads; i++) {
        executor.submit(
            () -> {
              try {
                startGate.await();
                service.create(
                    new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT), USER_ID, EMAIL, null);
                confirmed.incrementAndGet();
              } catch (Exception e) {
                failed.incrementAndGet();
              } finally {
                doneGate.countDown();
              }
            });
      }
      startGate.countDown();
      doneGate.await(20, TimeUnit.SECONDS);
      executor.shutdownNow();

      assertThat(confirmed.get()).isEqualTo(1);
      assertThat(failed.get()).isEqualTo(9);
      assertThat(bookings.count()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("cancel with real DB")
  class Cancel {
    @Test
    @DisplayName("cancel confirmed booking -> CANCELLED in DB + decrement + slot re-usable")
    void cancelConfirmed() {
      var created =
          service.create(
              new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT), USER_ID, EMAIL, null);
      var dto = service.cancel(created.id(), USER_ID, false);

      assertThat(dto.status()).isEqualTo("CANCELLED");
      var saved = bookings.findById(created.id()).orElseThrow();
      assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
      verify(resilientClient).decrementBooked(HOTEL_ID, ROOM_ID);

      var again =
          service.create(
              new CreateBookingRequest(HOTEL_ID, ROOM_ID, IN, OUT), USER_ID, EMAIL, null);
      assertThat(again.status()).isEqualTo("CONFIRMED");
    }
  }
}
