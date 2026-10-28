INSERT INTO bookings (id, user_id, user_email, hotel_id, room_id, check_in, check_out, status) VALUES
    (1000, 1001, 'user@example.com',  1000, 1001, '2030-03-01', '2030-03-05', 'CONFIRMED'),
    (1001, 1001, 'user@example.com',  1001, 1012, '2030-03-10', '2030-03-12', 'CONFIRMED'),
    (1002, 1002, 'user2@example.com', 1000, 1002, '2030-03-15', '2030-03-18', 'PENDING'),
    (1003, 1002, 'user2@example.com', 1000, 1001, '2030-03-20', '2030-03-22', 'CANCELLED'),
    (1004, 1001, 'user@example.com',  1002, 1021, '2030-03-05', '2030-03-08', 'CONFIRMED')
ON CONFLICT DO NOTHING;

SELECT setval('bookings_id_seq', (SELECT MAX(id) FROM bookings));