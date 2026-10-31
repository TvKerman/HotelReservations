package com.booking.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.booking.gateway.error.GatewaySecurityErrorHandler;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter,
      GatewaySecurityErrorHandler errorHandler) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
        .authorizeExchange(
            ex ->
                ex.pathMatchers("/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .pathMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/api/*/v3/api-docs/**")
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .pathMatchers("/error", "/error/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .authenticationEntryPoint(errorHandler)
                    .accessDeniedHandler(errorHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
        .build();
  }

  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(@Value("${app.security.jwt.secret}") String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT secret must be at least 32 bytes");
    }
    var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
    var delegate = new JwtAuthenticationConverter();
    delegate.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");
          return (role == null || role.isBlank())
              ? List.of()
              : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
    return new ReactiveJwtAuthenticationConverterAdapter(delegate);
  }
}
