-- Local-only accounts for the user-domain authentication store.
INSERT IGNORE INTO freshmart_user.users (phone, login_name, password_hash, nickname, status)
VALUES
  ('13600001001', 'admin-test-01', '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '本地测试管理员', 'ACTIVE'),
  ('13800001001', 'consumer-test-01', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户01', 'ACTIVE'),
  ('13800001002', 'consumer-test-02', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户02', 'ACTIVE'),
  ('13800001003', 'consumer-test-03', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户03', 'ACTIVE'),
  ('13800001004', 'consumer-test-04', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户04', 'ACTIVE'),
  ('13800001005', 'consumer-test-05', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户05', 'ACTIVE'),
  ('13800001006', 'consumer-test-06', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户06', 'ACTIVE'),
  ('13800001007', 'consumer-test-07', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户07', 'ACTIVE'),
  ('13800001008', 'consumer-test-08', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户08', 'ACTIVE'),
  ('13800001009', 'consumer-test-09', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户09', 'ACTIVE'),
  ('13800001010', 'consumer-test-10', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试用户10', 'ACTIVE'),
  ('13900001001', 'merchant-test-01', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家01', 'ACTIVE'),
  ('13900001002', 'merchant-test-02', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家02', 'ACTIVE'),
  ('13900001003', 'merchant-test-03', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家03', 'ACTIVE'),
  ('13900001004', 'merchant-test-04', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家04', 'ACTIVE'),
  ('13900001005', 'merchant-test-05', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家05', 'ACTIVE'),
  ('13900001006', 'merchant-test-06', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家06', 'ACTIVE'),
  ('13900001007', 'merchant-test-07', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家07', 'ACTIVE'),
  ('13900001008', 'merchant-test-08', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家08', 'ACTIVE'),
  ('13900001009', 'merchant-test-09', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家09', 'ACTIVE'),
  ('13900001010', 'merchant-test-10', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试商家10', 'ACTIVE'),
  ('13700001001', 'rider-test-01', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员01', 'ACTIVE'),
  ('13700001002', 'rider-test-02', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员02', 'ACTIVE'),
  ('13700001003', 'rider-test-03', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员03', 'ACTIVE'),
  ('13700001004', 'rider-test-04', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员04', 'ACTIVE'),
  ('13700001005', 'rider-test-05', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员05', 'ACTIVE'),
  ('13700001006', 'rider-test-06', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员06', 'ACTIVE'),
  ('13700001007', 'rider-test-07', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员07', 'ACTIVE'),
  ('13700001008', 'rider-test-08', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员08', 'ACTIVE'),
  ('13700001009', 'rider-test-09', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员09', 'ACTIVE'),
  ('13700001010', 'rider-test-10', '$2a$10$p/T1yo6nQC4vyFvY7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', '测试配送员10', 'ACTIVE');

UPDATE freshmart_user.users
SET password_hash = '$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym', status = 'ACTIVE'
WHERE login_name = 'admin-test-01'
   OR login_name LIKE 'consumer-test-%'
   OR login_name LIKE 'merchant-test-%'
   OR login_name LIKE 'rider-test-%';

INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'ADMIN' FROM freshmart_user.users WHERE login_name = 'admin-test-01';
INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'OPERATIONS' FROM freshmart_user.users WHERE login_name = 'admin-test-01';
INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'FINANCE' FROM freshmart_user.users WHERE login_name = 'admin-test-01';
INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'CONSUMER' FROM freshmart_user.users WHERE login_name LIKE 'consumer-test-%';
INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'MERCHANT' FROM freshmart_user.users WHERE login_name LIKE 'merchant-test-%';
INSERT IGNORE INTO freshmart_user.user_role_assignments (user_id, role_code)
SELECT id, 'RIDER' FROM freshmart_user.users WHERE login_name LIKE 'rider-test-%';

INSERT INTO freshmart_merchant.merchants (owner_user_id, name, status, service_area_json)
SELECT user.id, CONCAT('本地测试生鲜商家', RIGHT(user.login_name, 2)), 'ACTIVE', JSON_OBJECT('scope', 'LOCAL_TEST')
FROM freshmart_user.users user
LEFT JOIN freshmart_merchant.merchants merchant ON merchant.owner_user_id = user.id
WHERE user.login_name LIKE 'merchant-test-%' AND merchant.id IS NULL;

INSERT INTO freshmart_merchant.merchant_applications (merchant_id, applicant_user_id, status, review_note, reviewed_at)
SELECT merchant.id, merchant.owner_user_id, 'APPROVED', '本地测试账号预置审核通过', CURRENT_TIMESTAMP
FROM freshmart_merchant.merchants merchant
LEFT JOIN freshmart_merchant.merchant_applications application ON application.merchant_id = merchant.id
JOIN freshmart_user.users user ON user.id = merchant.owner_user_id
WHERE user.login_name LIKE 'merchant-test-%' AND application.id IS NULL;

INSERT IGNORE INTO freshmart_delivery.rider_profiles (user_id, employee_no, status)
SELECT id, CONCAT('TEST-RIDER-', RIGHT(login_name, 2)), 'ACTIVE'
FROM freshmart_user.users WHERE login_name LIKE 'rider-test-%';
