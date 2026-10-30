package com.booking.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway - routing, security, traceId")
class GatewayIntegrationTest {

  static final String SECRET = "test-secret-at-least-32-bytes-long-1234567890";

  static WireMockServer wiremock;

  @BeforeAll
  static void startWiremock() {
    wiremock = new WireMockServer(options().dynamicPort());
    wiremock.start();
  }

  @AfterAll
  static void stopWiremock() {
    wiremock.stop();
  }

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String wmUri = "http://localhost:" + wiremock.port();

    registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-route");
    registry.add("spring.cloud.gateway.routes[0].uri", () -> wmUri);
    registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/auth/**");
    registry.add("spring.cloud.gateway.routes[0].filters[0]", () -> "StripPrefix=1");

    registry.add("spring.cloud.gateway.routes[1].id", () -> "hotel-route");
    registry.add("spring.cloud.gateway.routes[1].uri", () -> wmUri);
    registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/hotels/**");
    registry.add("spring.cloud.gateway.routes[1].filters[0]", () -> "StripPrefix=1");

    registry.add("spring.cloud.gateway.routes[2].id", () -> "booking-route");
    registry.add("spring.cloud.gateway.routes[2].uri", () -> wmUri);
    registry.add("spring.cloud.gateway.routes[2].predicates[0]", () -> "Path=/api/bookings/**");
    registry.add("spring.cloud.gateway.routes[2].filters[0]", () -> "StripPrefix=1");
  }

  @Autowired WebTestClient webClient;

  @BeforeEach
  void reset() {
    wiremock.resetAll();
  }

  static String validJwt() {
    var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    var claims =
        JwtClaimsSet.builder()
            .issuer("auth-service")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .subject("user@example.com")
            .claim("uid", 1L)
            .claim("role", "USER")
            .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Test
  @DisplayName("GET /api/hotels without token -> 401")
  void unauthorizedNoToken() {
    webClient.get().uri("/api/hotels").exchange().expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("GET /api/hotels with broken token -> 401")
  void unauthorizedBadToken() {
    webClient
        .get()
        .uri("/api/hotels")
        .header("Authorization", "Bearer broken.token.value")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("401 returns ErrorDTO with code UNAUTHORIZED")
  void unauthorizedHasErrorDto() {
    webClient
        .get()
        .uri("/api/hotels")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo("UNAUTHORIZED")
        .jsonPath("$.path")
        .isEqualTo("/api/hotels");
  }

  @Test
  @DisplayName("POST /api/auth/register without token -> passes through (201)")
  void authRegisterIsPublic() {
    wiremock.stubFor(
        post(urlEqualTo("/auth/register"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"accessToken\":\"x\"}")));

    webClient
        .post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"email\":\"a@b.com\",\"password\":\"secret\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.accessToken")
        .isEqualTo("x");

    wiremock.verify(postRequestedFor(urlEqualTo("/auth/register")));
  }

  @Test
  @DisplayName("POST /api/auth/login without token -> passes through (200)")
  void authLoginIsPublic() {
    wiremock.stubFor(
        post(urlEqualTo("/auth/login"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"accessToken\":\"x\"}")));

    webClient
        .post()
        .uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"email\":\"a@b.com\",\"password\":\"secret\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  @DisplayName("GET /api/hotels with valid token -> routed to hotel-service, 200")
  void authorizedRouted() {
    wiremock.stubFor(
        get(urlEqualTo("/hotels"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    webClient
        .get()
        .uri("/api/hotels")
        .header("Authorization", "Bearer " + validJwt())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");

    wiremock.verify(getRequestedFor(urlEqualTo("/hotels")));
  }

  @Test
  @DisplayName("StripPrefix: downstream sees /hotels, not /api/hotels")
  void stripPrefixWorks() {
    wiremock.stubFor(
        get(urlEqualTo("/bookings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    webClient
        .get()
        .uri("/api/bookings")
        .header("Authorization", "Bearer " + validJwt())
        .exchange()
        .expectStatus()
        .isOk();

    wiremock.verify(getRequestedFor(urlEqualTo("/bookings")));
    wiremock.verify(0, getRequestedFor(urlEqualTo("/api/bookings")));
  }

  @Test
  @DisplayName("X-Correlation-Id is echoed back when provided")
  void traceIdEchoed() {
    wiremock.stubFor(get(urlEqualTo("/hotels")).willReturn(aResponse().withStatus(200)));

    webClient
        .get()
        .uri("/api/hotels")
        .header("Authorization", "Bearer " + validJwt())
        .header("X-Correlation-Id", "trace-test-42")
        .exchange()
        .expectHeader()
        .valueEquals("X-Correlation-Id", "trace-test-42");
  }

  @Test
  @DisplayName("X-Correlation-Id is generated when not provided")
  void traceIdGenerated() {
    wiremock.stubFor(get(urlEqualTo("/hotels")).willReturn(aResponse().withStatus(200)));

    webClient
        .get()
        .uri("/api/hotels")
        .header("Authorization", "Bearer " + validJwt())
        .exchange()
        .expectHeader()
        .exists("X-Correlation-Id");
  }
}
