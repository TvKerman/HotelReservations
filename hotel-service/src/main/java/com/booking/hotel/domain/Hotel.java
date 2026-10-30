package com.booking.hotel.domain;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "hotels")
public class Hotel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 100)
  private String city;

  @Column(nullable = false)
  private Short stars;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Hotel() {}

  public Hotel(String name, String city, Short stars, String description) {
    this.name = name;
    this.city = city;
    this.stars = stars;
    this.description = description;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public Short getStars() {
    return stars;
  }

  public void setStars(Short stars) {
    this.stars = stars;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
