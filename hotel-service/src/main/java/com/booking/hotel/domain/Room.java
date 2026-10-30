package com.booking.hotel.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}))
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id", nullable = false)
  private Hotel hotel;

  @Column(nullable = false, length = 50)
  private String number;

  @Column(nullable = false)
  private Short capacity;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "times_booked", nullable = false)
  private Integer timesBooked = 0;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Room() {}

  public Room(Hotel hotel, String number, Short capacity, BigDecimal price) {
    this.hotel = hotel;
    this.number = number;
    this.capacity = capacity;
    this.price = price;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Hotel getHotel() {
    return hotel;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public Short getCapacity() {
    return capacity;
  }

  public void setCapacity(Short capacity) {
    this.capacity = capacity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public Integer getTimesBooked() {
    return timesBooked;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setTimesBooked(Integer t) {
    this.timesBooked = t;
  }
}
