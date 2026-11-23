-- =============================================================================
-- HireHub — Default Administrator Account Seed
-- Credentials: admin@hirehub.com / Admin@1234
-- =============================================================================

USE hirehub_db;

INSERT IGNORE INTO users (full_name, email, password_hash, role)
VALUES (
    'System Administrator',
    'admin@hirehub.com',
    '$2a$12$s1Y5vC02Zcr7w53/StvonuqEVKLPYoNPlMcwU3uU9rHOK5SrD7uMa',
    'ADMIN'
);

UPDATE users
SET    password_hash = '$2a$12$s1Y5vC02Zcr7w53/StvonuqEVKLPYoNPlMcwU3uU9rHOK5SrD7uMa'
WHERE  email = 'admin@hirehub.com'
  AND  password_hash = '$2a$12$92IBq9dF3v3XDv7OQaIHO.6rDXjOvVCkLZoxsBknVlKrmgKdz3NKi';
