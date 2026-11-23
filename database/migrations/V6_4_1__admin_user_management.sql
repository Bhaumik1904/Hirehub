-- =============================================================================
-- HireHub — Phase 6 Step 4.1 Database Migration:
-- Admin User Management & Account Moderation (GAP-04 — Part 1)
--
-- 1. Adds `is_active` BOOLEAN NOT NULL DEFAULT TRUE column to `users` table.
-- 2. Creates `admin_audit_logs` table for persistent account moderation audit trail.
-- Idempotent: safe to run multiple times.
-- =============================================================================

USE hirehub_db;

-- 1. Add is_active column to users table if not exists
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'hirehub_db'
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'is_active'
);

SET @sql_col = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER role, ADD INDEX idx_users_active (is_active)',
    'SELECT "Column is_active already exists in users"'
);
PREPARE stmt_col FROM @sql_col;
EXECUTE stmt_col;
DEALLOCATE PREPARE stmt_col;

-- Ensure all existing users are active (default is TRUE, but ensure explicit true)
UPDATE users SET is_active = TRUE WHERE is_active IS NULL;

-- 2. Create admin_audit_logs table if not exists
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    admin_id INT NOT NULL,
    target_user_id INT NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_audit_admin (admin_id),
    INDEX idx_audit_target (target_user_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

