INSERT INTO users (id, email, password_hash, role) VALUES
    (1000, 'admin@example.com', '$2a$10$Afo8mQEYDMSIPWPxtZHWYeHPqmUzQHV.RPMF8W4XUqroag7zCUfcO', 'ADMIN'),
    (1001, 'user@example.com',  '$2a$10$Afo8mQEYDMSIPWPxtZHWYeHPqmUzQHV.RPMF8W4XUqroag7zCUfcO', 'USER'),
    (1002, 'user2@example.com', '$2a$10$Afo8mQEYDMSIPWPxtZHWYeHPqmUzQHV.RPMF8W4XUqroag7zCUfcO', 'USER')
ON CONFLICT DO NOTHING;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
