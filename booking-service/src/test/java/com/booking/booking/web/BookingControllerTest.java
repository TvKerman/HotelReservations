package com.booking.booking.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.booking.booking.config.SecurityConfig;
import com.booking.booking.service.BookingService;
import com.booking.booking.web.dto.BookingDto;
import com.booking.booking.web.dto.CreateBookingRequest;
import com.booking.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class}) // <-- Добавили GlobalExceptionHandler
@TestPropertySource(
    properties = {"app.security.jwt.secret=dev-secret-change-me-please-1234567890-abcdef"})
@DisplayName("BookingController - HTTP layer")
class BookingControllerTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean BookingService service;

  LocalDate in = LocalDate.now().plusDays(1);
  LocalDate out = LocalDate.now().plusDays(3);

  private BookingDto sampleDto() {
    return new BookingDto(
        1L, 10L, "u@x.com", 100L, 200L, in, out, "CONFIRMED", Instant.now(), Instant.now());
  }

  @Test
  @DisplayName("POST /bookings without token -> 401")
  void createWithoutToken() throws Exception {
    mvc.perform(
            post("/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateBookingRequest(100L, 200L, in, out))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /bookings with USER JWT -> 201")
  void createWithJwt() throws Exception {
    when(service.create(any(), eq(10L), eq("u@x.com"), eq("k1"))).thenReturn(sampleDto());

    mvc.perform(
            post("/bookings")
                .with(jwt().jwt(j -> j.claim("uid", 10L).claim("role", "USER").subject("u@x.com")))
                .header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateBookingRequest(100L, 200L, in, out))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.roomId").value(200));
  }

  @Test
  @DisplayName("POST /bookings with reversed dates -> 400")
  void createInvalidDates() throws Exception {
    mvc.perform(
            post("/bookings")
                .with(jwt().jwt(j -> j.claim("uid", 10L).claim("role", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateBookingRequest(100L, 200L, out, in))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /bookings USER -> 200 with page")
  void listUser() throws Exception {
    when(service.list(eq(10L), eq(false), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(sampleDto())));

    mvc.perform(get("/bookings").with(jwt().jwt(j -> j.claim("uid", 10L).claim("role", "USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
  }

  @Test
  @DisplayName("GET /bookings ADMIN -> calls list with admin=true")
  void listAdmin() throws Exception {
    when(service.list(eq(20L), eq(true), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(sampleDto())));

    mvc.perform(get("/bookings").with(jwt().jwt(j -> j.claim("uid", 20L).claim("role", "ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PATCH /bookings/{id}/cancel with USER -> 200")
  void cancel() throws Exception {
    when(service.cancel(eq(1L), eq(10L), eq(false)))
        .thenReturn(
            new BookingDto(
                1L,
                10L,
                "u@x.com",
                100L,
                200L,
                in,
                out,
                "CANCELLED",
                Instant.now(),
                Instant.now()));

    mvc.perform(
            patch("/bookings/1/cancel")
                .with(jwt().jwt(j -> j.claim("uid", 10L).claim("role", "USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }
}
