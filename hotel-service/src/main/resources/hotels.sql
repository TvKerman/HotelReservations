CREATE TABLE hotels (
                        id          BIGSERIAL PRIMARY KEY,
                        name        VARCHAR(255) NOT NULL,
                        city        VARCHAR(100) NOT NULL,
                        stars       SMALLINT CHECK (stars BETWEEN 1 AND 5),
                        description TEXT,
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_hotels_city ON hotels(city);