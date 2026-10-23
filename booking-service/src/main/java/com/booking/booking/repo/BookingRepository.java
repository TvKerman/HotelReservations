package com.booking.booking.repo;

import com.booking.booking.domain.Booking;
import com.booking.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Page<Booking> findByUserId(Long userId, Pageable pageable);
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);
    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    @Query(value = """
        SELECT count(*) FROM bookings
        WHERE room_id = :roomId
          AND status IN ('PENDING','CONFIRMED')
          AND check_in < :checkOut
          AND check_out > :checkIn
        """, nativeQuery = true)
    long countOverlappingActive(@Param("roomId") Long roomId,
                                @Param("checkIn") LocalDate checkIn,
                                @Param("checkOut") LocalDate checkOut);
}