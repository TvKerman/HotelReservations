CREATE TABLE bookings (
                          id               BIGSERIAL PRIMARY KEY,
                          user_id          BIGINT NOT NULL,
                          room_id          BIGINT NOT NULL,
                          check_in         DATE NOT NULL,
                          check_out        DATE NOT NULL,
                          status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                          idempotency_key  VARCHAR(64) NOT NULL,
                          created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                          CONSTRAINT chk_dates CHECK (check_out > check_in)
);
CREATE UNIQUE INDEX unique_idx_bookings_idempotency ON bookings(idempotency_key);
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_room_dates ON bookings(room_id, check_in, check_out);
CREATE INDEX idx_bookings_status ON bookings(status);