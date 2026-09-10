UPDATE users
SET password_hash = '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym'
WHERE login_name LIKE 'consumer-test-%'
   OR login_name LIKE 'merchant-test-%'
   OR login_name LIKE 'rider-test-%';
