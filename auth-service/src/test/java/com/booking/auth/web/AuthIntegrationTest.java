package com.booking.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.booking.auth.AbstractIntegrationTest;
import com.booking.auth.domain.Role;
import com.booking.auth.repo.UserRepository;
import com.booking.auth.web.dto.LoginRequest;
import com.booking.auth.web.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@DisplayName("Auth integration (real Postgres + real JWT)")
class AuthIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;

  @BeforeEach
  void clean() {
    users.deleteAll();
  }

  @Test
  @DisplayName("register -> login -> token contains role USER")
  void fullFlow() throws Exception {
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new RegisterRequest("new@example.com", "secret123"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    assertThat(users.findByEmail("new@example.com")).isPresent();
    assertThat(users.findByEmail("new@example.com").get().getRole()).isEqualTo(Role.USER);

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest("new@example.com", "secret123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  @DisplayName("duplicate email -> 409")
  void duplicate() throws Exception {
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new RegisterRequest("dup@example.com", "secret123"))))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new RegisterRequest("dup@example.com", "secret123"))))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("wrong password -> 401")
  void wrongPassword() throws Exception {
    mvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new RegisterRequest("u@example.com", "secret123"))))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest("u@example.com", "wrong"))))
        .andExpect(status().isUnauthorized());
  }
}
