package com.booking.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

@Component
public class HotelResilientClient {

    private final HotelClient client;

    public HotelResilientClient(HotelClient client) {
        this.client = client;
    }

    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public void incrementBooked(Long hotelId, Long roomId) {
        client.incrementBooked(hotelId, roomId);
    }

    @Retry(name = "hotelClient")
    @CircuitBreaker(name = "hotelClient")
    public void decrementBooked(Long hotelId, Long roomId) {
        client.decrementBooked(hotelId, roomId);
    }
}