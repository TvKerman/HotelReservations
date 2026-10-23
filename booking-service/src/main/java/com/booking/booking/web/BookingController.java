package com.booking.booking.web;

import com.booking.booking.domain.BookingStatus;
import com.booking.booking.service.BookingService;
import com.booking.booking.web.dto.BookingDto;
import com.booking.booking.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody CreateBookingRequest req,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                             @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("uid");
        String email = jwt.getSubject();
        return bookings.create(req, userId, email, idempotencyKey);
    }

    @GetMapping
    public Page<BookingDto> list(@RequestParam(required = false) String status,
                                 @PageableDefault(size = 20) Pageable pageable,
                                 @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("uid");
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        BookingStatus st = status != null ? BookingStatus.valueOf(status.toUpperCase()) : null;
        return bookings.list(userId, admin, st, pageable);
    }

    @GetMapping("/{id}")
    public BookingDto get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("uid");
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        return bookings.get(id, userId, admin);
    }

    @PatchMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("uid");
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        return bookings.cancel(id, userId, admin);
    }
}