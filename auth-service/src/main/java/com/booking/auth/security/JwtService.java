package com.booking.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import com.booking.auth.domain.User;

@Service
public class JwtService {

  private final JwtEncoder encoder;
  private final Duration ttl;

  public JwtService(
      @Value("${app.security.jwt.secret}") String secret,
      @Value("${app.security.jwt.ttl:PT1H}") Duration ttl) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits)");
    }
    var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
    this.ttl = ttl;
  }

  public String generate(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("auth-service")
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .subject(user.getEmail())
            .claim("uid", user.getId())
            .claim("role", user.getRole().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public Duration ttl() {
    return ttl;
  }
}
