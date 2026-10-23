CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
                          id               BIGSERIAL PRIMARY KEY,
                          user_id          BIGINT       NOT NULL,
                          user_email       VARCHAR(255) NOT NULL,
                          hotel_id         BIGINT       NOT NULL,
                          room_id          BIGINT       NOT NULL,
                          check_in         DATE         NOT NULL,
                          check_out        DATE         NOT NULL,
                          status           VARCHAR(20)  NOT NULL,
                          idempotency_key  VARCHAR(64),
                          created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          version          BIGINT       NOT NULL DEFAULT 0,
                          period           DATERANGE    GENERATED ALWAYS AS (daterange(check_in, check_out, '[)')) STORED,
                          CONSTRAINT chk_dates  CHECK (check_out > check_in),
                          CONSTRAINT chk_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED'))
);

CREATE UNIQUE INDEX unique_idx_bookings_idempotency
    ON bookings(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_bookings_user_id   ON bookings(user_id);
CREATE INDEX idx_bookings_room_id   ON bookings(room_id);
CREATE INDEX idx_bookings_status    ON bookings(status);
CREATE INDEX idx_bookings_period    ON bookings USING gist (period);

ALTER TABLE bookings ADD CONSTRAINT no_overlapping_active_bookings
    EXCLUDE USING gist (room_id WITH =, period WITH &&)
    WHERE (status <> 'CANCELLED');