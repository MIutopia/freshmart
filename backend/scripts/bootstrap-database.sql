CREATE DATABASE IF NOT EXISTS freshmart CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'freshmart'@'localhost' IDENTIFIED BY 'replace-with-a-strong-local-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON freshmart.* TO 'freshmart'@'localhost';
FLUSH PRIVILEGES;
