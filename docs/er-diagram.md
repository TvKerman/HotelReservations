# Модель данных

Система использует три независимые базы данных. Ниже приведены схемы и ключевые ограничения.

## `booking_auth`

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar email UK "unique"
        varchar password_hash "BCrypt, cost 10"
        varchar role "USER | ADMIN"
        timestamptz created_at
    }
```

Ограничения:
```sql
CREATE UNIQUE INDEX ... ON users(email);
```
Уникальность email обеспечивает идемпотентность регистрации и однозначность аутентификации.

## `booking_hotel`

```mermaid
erDiagram
    HOTELS ||--o{ ROOMS : "1 : N"
    HOTELS {
        bigint id PK
        varchar name
        varchar city "index"
        smallint stars "CHECK 1..5"
        text description
        timestamptz created_at
    }
    ROOMS {
        bigint id PK
        bigint hotel_id FK "ON DELETE CASCADE"
        varchar number "UK(hotel_id, number)"
        smallint capacity "CHECK > 0"
        numeric price "CHECK >= 0"
        integer times_booked "index"
        bigint version "optimistic lock"
        timestamptz created_at
    }
```

Индексы и ограничения:
```sql
CREATE UNIQUE INDEX ... ON rooms(hotel_id, number);
CREATE INDEX idx_rooms_hotel_id     ON rooms(hotel_id);
CREATE INDEX idx_rooms_times_booked ON rooms(times_booked);
CREATE INDEX idx_rooms_price        ON rooms(price);
CREATE INDEX idx_hotels_city        ON hotels(city);
CREATE INDEX idx_hotels_stars       ON hotels(stars);
```

Колонка `times_booked` индексируется, так как используется в `ORDER BY` при выборе номера в `booking-service`. Поле `version` обеспечивает оптимистичную блокировку при обновлении счётчика под конкурентной нагрузкой.

## `booking_booking`

```mermaid
erDiagram
    BOOKINGS {
        bigint id PK
        bigint user_id "index, ref booking_auth.users"
        varchar user_email "денормализация для истории"
        bigint hotel_id "ref booking_hotel.hotels"
        bigint room_id "index, ref booking_hotel.rooms"
        date check_in
        date check_out
        varchar status "PENDING | CONFIRMED | CANCELLED"
        varchar idempotency_key "partial UK"
        daterange period "GENERATED, GIST-index"
        bigint version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
```

Ключевые ограничения:

```sql
-- Инвариант «одна активная бронь номера на непересекающиеся даты»
ALTER TABLE bookings ADD CONSTRAINT no_overlapping_active_bookings
    EXCLUDE USING gist (room_id WITH =, period WITH &&)
    WHERE (status <> 'CANCELLED');

-- Идемпотентность создания
CREATE UNIQUE INDEX unique_idx_bookings_idempotency
    ON bookings(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Производительность выборок
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_room_id ON bookings(room_id);
CREATE INDEX idx_bookings_status  ON bookings(status);
CREATE INDEX idx_bookings_period  ON bookings USING gist (period);
```

Колонка `period` — генерируемая:
```sql
period DATERANGE GENERATED ALWAYS AS (daterange(check_in, check_out, '[)')) STORED
```

Расширение `btree_gist` создаётся в миграции `V1__create_bookings.sql`.

### Обоснование ключевых решений

**`EXCLUDE USING gist` вместо проверки в коде.** Проверка «есть ли пересечение с активной бронью» на уровне приложения подвержена race condition: два потока могут одновременно выполнить `SELECT` и получить «свободно», после чего оба выполнят `INSERT`. Ограничение `EXCLUDE` — декларативный механизм PostgreSQL, гарантирующий атомарность на уровне БД независимо от количества конкурентных транзакций.

**Частичный уникальный индекс на `idempotency_key`.** Использование `WHERE idempotency_key IS NOT NULL` позволяет оставить существующие записи без ключа (созданные до введения механизма идемпотентности) без нарушения ограничения. Полный уникальный индекс на nullable-колонке в PostgreSQL допускает любое количество `NULL`, но частичный индекс более явно выражает намерение и снижает размер индекса.

**`daterange` вместо двух `DATE` для проверки пересечений.** `EXCLUDE` работает с оператором пересечения `&&`, определённым для `daterange`. Использование range-типа также упрощает чтение и поддерживает полуоткрытый интервал `[)` — дата выезда не считается занятой, что соответствует стандартной гостиничной практике.

**Отсутствие внешних ключей между БД.** Разные базы данных физически не могут содержать FK между собой. Ссылочная целостность обеспечивается:
- `booking.room_id` — проверка на стороне `hotel-service` при `increment-booked`;
- `booking.user_id` — значение из подписанного JWT.

Денормализованное поле `user_email` в `bookings` сохраняет email на момент бронирования. Это осознанное решение: если пользователь сменит email в будущем, исторические брони сохранят исходное значение.
