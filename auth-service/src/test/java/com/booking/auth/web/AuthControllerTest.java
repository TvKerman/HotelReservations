package com.booking.auth.web;

import com.booking.auth.config.SecurityConfig;
import com.booking.auth.domain.Role;
import com.booking.auth.domain.User;
import com.booking.auth.repo.UserRepository;
import com.booking.auth.security.JwtService;
import com.booking.auth.web.dto.LoginRequest;
import com.booking.auth.web.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-at-least-32-bytes-long-1234567890",
        "app.security.jwt.ttl=PT1H"
})
@DisplayName("AuthController - HTTP layer")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserRepository users;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean JwtService jwtService;

    @Test
    @DisplayName("POST /auth/register - valid -> 201 with token")
    void registerValid() throws Exception {
        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(jwtService.generate(any())).thenReturn("jwt-token");
        when(jwtService.ttl()).thenReturn(Duration.ofHours(1));
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.com", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /auth/register - short password -> 400")
    void registerShortPassword() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.com", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - invalid email -> 400")
    void registerInvalidEmail() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("not-an-email", "secret123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - duplicate -> 409")
    void registerDuplicate() throws Exception {
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterRequest("a@b.com", "secret123"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /auth/login - wrong password -> 401")
    void loginWrongPassword() throws Exception {
        var user = new User("a@b.com", "hashed", Role.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.com", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login - correct -> 200")
    void loginCorrect() throws Exception {
        var user = new User("a@b.com", "hashed", Role.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generate(user)).thenReturn("jwt-token");
        when(jwtService.ttl()).thenReturn(Duration.ofHours(1));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("a@b.com", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /auth/login - unknown email -> 401")
    void loginUnknownUser() throws Exception {
        when(users.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("nobody@x.com", "x"))))
                .andExpect(status().isUnauthorized());
    }
}