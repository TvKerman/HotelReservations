CREATE TABLE rooms (
                       id           BIGSERIAL PRIMARY KEY,
                       hotel_id     BIGINT NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
                       number       VARCHAR(50) NOT NULL,
                       capacity     SMALLINT NOT NULL CHECK (capacity > 0),
                       price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
                       times_booked INTEGER NOT NULL DEFAULT 0,
                       version      BIGINT NOT NULL DEFAULT 0,
                       created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                       UNIQUE (hotel_id, number)
);
CREATE INDEX idx_rooms_hotel_id ON rooms(hotel_id);
CREATE INDEX idx_rooms_times_booked ON rooms(times_booked);