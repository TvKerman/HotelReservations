package com.booking.hotel.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.booking.common.error.GlobalExceptionHandler;
import com.booking.hotel.config.SecurityConfig;
import com.booking.hotel.service.HotelService;
import com.booking.hotel.web.dto.CreateHotelRequest;
import com.booking.hotel.web.dto.HotelDto;
import com.booking.hotel.web.dto.HotelStatisticsDto;
import com.booking.hotel.web.dto.UpdateHotelRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class}) // <-- Добавили GlobalExceptionHandler
@TestPropertySource(
    properties = {"app.security.jwt.secret=dev-secret-change-me-please-1234567890-abcdef"})
@DisplayName("HotelController - HTTP layer")
class HotelControllerTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean HotelService service;

  private HotelDto dto() {
    return new HotelDto(1L, "Test", "Moscow", (short) 4, "desc", Instant.now());
  }

  @Test
  @DisplayName("GET /hotels without token -> 401")
  void listWithoutToken() throws Exception {
    mvc.perform(get("/hotels")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /hotels with token -> 200")
  void listWithToken() throws Exception {
    when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(dto())));

    mvc.perform(
            get("/hotels")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 10L))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.content[0].city").value("Moscow"));
  }

  @Test
  @DisplayName("POST /hotels as USER -> 403 (ADMIN only)")
  void createAsUser() throws Exception {
    mvc.perform(
            post("/hotels")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 10L))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateHotelRequest("X", "Y", (short) 3, ""))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /hotels as ADMIN -> 201")
  void createAsAdmin() throws Exception {
    when(service.create(any())).thenReturn(dto());

    mvc.perform(
            post("/hotels")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 20L))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new CreateHotelRequest("Test", "Moscow", (short) 4, "desc"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Test"));
  }

  @Test
  @DisplayName("POST /hotels with invalid body -> 400")
  void createInvalid() throws Exception {
    mvc.perform(
            post("/hotels")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 20L))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CreateHotelRequest("", "", (short) 0, ""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT /hotels/{id} as ADMIN -> 200")
  void updateAsAdmin() throws Exception {
    when(service.update(eq(1L), any())).thenReturn(dto());

    mvc.perform(
            put("/hotels/1")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 20L))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new UpdateHotelRequest("Test", "Moscow", (short) 4, "desc"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("DELETE /hotels/{id} as ADMIN -> 204")
  void deleteAsAdmin() throws Exception {
    mvc.perform(
            delete("/hotels/1")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 20L))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /hotels/{id}/statistics -> 200")
  void statistics() throws Exception {
    when(service.statistics(1L))
        .thenReturn(new HotelStatisticsDto(1L, "Test", 5, 10, 2.0, null, null, null, List.of()));

    mvc.perform(
            get("/hotels/1/statistics")
                .with(
                    jwt()
                        .jwt(j -> j.claim("uid", 10L))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRooms").value(5));
  }
}
