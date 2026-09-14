INSERT IGNORE INTO users (phone, login_name, password_hash, nickname, role, user_type, status)
VALUES ('13600001001', 'admin-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '本地测试管理员', 'ADMIN', 'ADMIN', 'ACTIVE');

INSERT IGNORE INTO user_role_assignments (user_id, role_code)
SELECT id, 'ADMIN'
FROM users
WHERE login_name = 'admin-test-01';
