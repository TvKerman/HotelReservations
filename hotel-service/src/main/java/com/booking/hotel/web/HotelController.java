package com.booking.hotel.web;

import com.booking.hotel.service.HotelService;
import com.booking.hotel.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hotels")
public class HotelController {

    private final HotelService hotels;

    public HotelController(HotelService hotels) {
        this.hotels = hotels;
    }

    @GetMapping
    public Page<HotelDto> search(@RequestParam(required = false) String city,
                                 @RequestParam(required = false) Short minStars,
                                 @RequestParam(required = false) Short maxStars,
                                 @PageableDefault(size = 20) Pageable pageable) {
        return hotels.search(city, minStars, maxStars, pageable);
    }

    @GetMapping("/{id}")
    public HotelDto get(@PathVariable Long id) {
        return hotels.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public HotelDto create(@Valid @RequestBody CreateHotelRequest req) {
        return hotels.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelDto update(@PathVariable Long id, @Valid @RequestBody UpdateHotelRequest req) {
        return hotels.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        hotels.delete(id);
    }

    @GetMapping("/{id}/statistics")
    public HotelStatisticsDto statistics(@PathVariable Long id) {
        return hotels.statistics(id);
    }
}