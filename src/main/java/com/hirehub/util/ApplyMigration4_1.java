package com.hirehub.util;

import com.hirehub.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotently applies the Phase 6 Step 4.1 database migration:
 * 1. Adds `is_active` to `users` table if missing.
 * 2. Creates `admin_audit_logs` table if missing.
 */
public class ApplyMigration4_1 {

    public static void apply() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Check if is_active column exists in users
            boolean columnExists = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'is_active'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    columnExists = true;
                }
            }

            if (!columnExists) {
                System.out.println("[Migration] Adding is_active column to users table...");
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER role, ADD INDEX idx_users_active (is_active)");
                System.out.println("[Migration] Added is_active column successfully.");
            } else {
                System.out.println("[Migration] is_active column already exists in users.");
            }

            // Ensure any existing rows have is_active = true
            stmt.executeUpdate("UPDATE users SET is_active = TRUE WHERE is_active IS NULL");

            // 2. Check if admin_audit_logs table exists
            boolean tableExists = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    tableExists = true;
                }
            }

            if (!tableExists) {
                System.out.println("[Migration] Creating admin_audit_logs table...");
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS admin_audit_logs (" +
                        "  id INT AUTO_INCREMENT PRIMARY KEY," +
                        "  admin_id INT NOT NULL," +
                        "  target_user_id INT NOT NULL," +
                        "  action VARCHAR(32) NOT NULL," +
                        "  reason VARCHAR(255) NULL," +
                        "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "  CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE CASCADE," +
                        "  CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE," +
                        "  INDEX idx_audit_admin (admin_id)," +
                        "  INDEX idx_audit_target (target_user_id)," +
                        "  INDEX idx_audit_created (created_at)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                System.out.println("[Migration] Created admin_audit_logs table successfully.");
            } else {
                System.out.println("[Migration] admin_audit_logs table already exists.");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Applying Phase 6 Step 4.1 Database Migration...");
        try {
            apply();
            System.out.println("Phase 6 Step 4.1 Migration completed successfully.");
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

