package com.booking.auth.security;

import com.booking.auth.domain.Role;
import com.booking.auth.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService - token generation")
class JwtServiceTest {

    static final String SECRET = "test-secret-at-least-32-bytes-long-1234567890";

    @Test
    @DisplayName("generates valid JWT with uid, role, sub")
    void generatesValidToken() {
        var service = new JwtService(SECRET, Duration.ofHours(1));
        var user = new User("a@b.com", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 42L);

        var token = service.generate(user);

        var decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("a@b.com");
        assertThat(jwt.<Long>getClaim("uid")).isEqualTo(42L);
        assertThat(jwt.<String>getClaim("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("auth-service");
    }

    @Test
    @DisplayName("rejects short secret (<32 bytes)")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtService("short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("respects custom ttl")
    void customTtl() {
        var service = new JwtService(SECRET, Duration.ofMinutes(15));
        assertThat(service.ttl()).isEqualTo(Duration.ofMinutes(15));
    }
}