package com.booking.hotel.service;

import com.booking.hotel.domain.Hotel;
import com.booking.hotel.repo.HotelRepository;
import com.booking.hotel.repo.RoomRepository;
import com.booking.hotel.web.dto.CreateHotelRequest;
import com.booking.hotel.web.dto.UpdateHotelRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HotelService - business logic")
class HotelServiceTest {

    @Mock HotelRepository hotels;
    @Mock RoomService rooms;
    @InjectMocks HotelService service;

    @Nested
    @DisplayName("get")
    class Get {
        @Test
        @DisplayName("returns DTO when found")
        void found() {
            var h = new Hotel("Test", "Moscow", (short) 4, "desc");
            when(hotels.findById(1L)).thenReturn(Optional.of(h));

            var dto = service.get(1L);

            assertThat(dto.name()).isEqualTo("Test");
            assertThat(dto.city()).isEqualTo("Moscow");
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() {
            when(hotels.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.get(99L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Hotel not found");
        }
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("persists hotel and returns DTO")
        void created() {
            var req = new CreateHotelRequest("New", "SPb", (short) 5, "desc");
            when(hotels.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

            var dto = service.create(req);

            assertThat(dto.name()).isEqualTo("New");
            assertThat(dto.city()).isEqualTo("SPb");
            verify(hotels).save(any(Hotel.class));
        }
    }

    @Nested
    @DisplayName("update")
    class Update {
        @Test
        @DisplayName("updates fields")
        void updated() {
            var h = new Hotel("Old", "Moscow", (short) 3, "x");
            when(hotels.findById(1L)).thenReturn(Optional.of(h));

            var req = new UpdateHotelRequest("New", "SPb", (short) 5, "desc");
            var dto = service.update(1L, req);

            assertThat(dto.name()).isEqualTo("New");
            assertThat(dto.city()).isEqualTo("SPb");
            assertThat(dto.stars()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("404 when missing")
        void missing() {
            when(hotels.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() ->
                    service.update(99L, new UpdateHotelRequest("x", "y", (short) 1, ""))
            ).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {
        @Test
        @DisplayName("deletes when exists")
        void deleted() {
            when(hotels.existsById(1L)).thenReturn(true);
            service.delete(1L);
            verify(hotels).deleteById(1L);
        }

        @Test
        @DisplayName("404 when missing")
        void missing() {
            when(hotels.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Hotel not found");
        }
    }

    @Nested
    @DisplayName("statistics")
    class Statistics {
        @Test
        @DisplayName("computes totals from rooms")
        void computes() {
            var h = new Hotel("Test", "Moscow", (short) 4, "");
            org.springframework.test.util.ReflectionTestUtils.setField(h, "id", 1L);
            when(hotels.findById(1L)).thenReturn(Optional.of(h));
            when(rooms.roomsOf(1L)).thenReturn(List.of());

            var stats = service.statistics(1L);

            assertThat(stats.hotelId()).isEqualTo(1L);
            assertThat(stats.hotelName()).isEqualTo("Test");
            assertThat(stats.totalRooms()).isZero();
            assertThat(stats.totalBookings()).isZero();
            assertThat(stats.avgBookingsPerRoom()).isZero();
        }

        @Test
        @DisplayName("404 when hotel missing")
        void missing() {
            when(hotels.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.statistics(99L))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
}