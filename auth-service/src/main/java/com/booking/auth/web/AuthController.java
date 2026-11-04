package com.booking.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.booking.auth.domain.Role;
import com.booking.auth.domain.User;
import com.booking.auth.repo.UserRepository;
import com.booking.auth.security.JwtService;
import com.booking.auth.web.dto.AuthResponse;
import com.booking.auth.web.dto.LoginRequest;
import com.booking.auth.web.dto.RegisterRequest;
import com.booking.common.error.BusinessException;
import com.booking.common.error.ErrorCode;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(
      UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
    if (users.existsByEmail(req.email())) {
      throw new BusinessException(
          HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
    }
    User user = new User(req.email(), passwordEncoder.encode(req.password()), Role.USER);
    users.save(user);
    return toResponse(user);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    User user =
        users
            .findByEmail(req.email())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.INVALID_CREDENTIALS,
                        "Invalid credentials"));
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }
    return toResponse(user);
  }

  private AuthResponse toResponse(User user) {
    return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.ttl().toSeconds());
  }
}
