INSERT INTO hotels (id, name, city, stars, description) VALUES
    (1000, 'Grand Plaza',       'Moscow',           5, 'Luxury hotel in the heart of Moscow'),
    (1001, 'City Inn',          'Moscow',           3, 'Affordable hotel near the center'),
    (1002, 'Nevsky Palace',     'Saint Petersburg', 4, 'Historic hotel on Nevsky Prospekt'),
    (1003, 'Kazan Riverside',   'Kazan',            4, 'Modern hotel overlooking the river')
ON CONFLICT (id) DO NOTHING;

INSERT INTO rooms (id, hotel_id, number, capacity, price, times_booked) VALUES

    (1000, 1000, '101', 2, 200.00, 0),
    (1001, 1000, '102', 2, 220.00, 1),
    (1002, 1000, '103', 4, 400.00, 3),
    (1003, 1000, '104', 1, 150.00, 5),
    (1004, 1000, '105', 3, 280.00, 2),

    (1010, 1001, '201', 1,  80.00, 0),
    (1011, 1001, '202', 2, 120.00, 1),
    (1012, 1001, '203', 2, 130.00, 4),

    (1020, 1002, '301', 2, 180.00, 0),
    (1021, 1002, '302', 4, 350.00, 2),
    (1022, 1002, '303', 2, 200.00, 7),

    (1030, 1003, '401', 2, 160.00, 0),
    (1031, 1003, '402', 3, 240.00, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval('hotels_id_seq', (SELECT MAX(id) FROM hotels));
SELECT setval('rooms_id_seq',  (SELECT MAX(id) FROM rooms));
