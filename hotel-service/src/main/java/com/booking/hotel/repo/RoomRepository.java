package com.booking.hotel.repo;

import com.booking.hotel.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    List<Room> findByHotelId(Long hotelId);

    boolean existsByHotelIdAndNumber(Long hotelId, String number);

    @Query("select r from Room r where r.hotel.id = :hotelId order by r.timesBooked asc, r.id asc")
    List<Room> findAvailableOrdered(@Param("hotelId") Long hotelId);

    @Query("select avg(r.price) from Room r where r.hotel.id = :hotelId")
    BigDecimal avgPrice(@Param("hotelId") Long hotelId);
}