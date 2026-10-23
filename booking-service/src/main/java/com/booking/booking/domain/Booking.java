package com.booking.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "user_email", nullable = false) private String userEmail;
    @Column(name = "hotel_id", nullable = false) private Long hotelId;
    @Column(name = "room_id", nullable = false) private Long roomId;
    @Column(name = "check_in", nullable = false) private LocalDate checkIn;
    @Column(name = "check_out", nullable = false) private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "idempotency_key", length = 64) private String idempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    @Column(nullable = false) private Long version;

    protected Booking() {}

    public Booking(Long userId, String userEmail, Long hotelId, Long roomId,
                   LocalDate checkIn, LocalDate checkOut,
                   BookingStatus status, String idempotencyKey) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist void onCreate() { this.createdAt = Instant.now(); this.updatedAt = createdAt; }
    @PreUpdate  void onUpdate() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public Long getHotelId() { return hotelId; }
    public Long getRoomId() { return roomId; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}