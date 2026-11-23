package com.hirehub.util;

import com.hirehub.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * PasswordRepairTool — Diagnoses and repairs truncated BCrypt password hashes in hirehub_db.
 */
public class PasswordRepairTool {

    public static void main(String[] args) {
        System.out.println("=== HireHub Password Repair Tool ===");

        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Inspect current users
            System.out.println("\n[1] Current users in database:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, email, role, LENGTH(password_hash) as len, LEFT(password_hash, 10) as prefix FROM users ORDER BY id ASC")) {
                while (rs.next()) {
                    System.out.println(String.format("  - ID: %3d | Role: %-9s | Email: %-30s | HashLen: %2d | Prefix: %s",
                            rs.getInt("id"), rs.getString("role"), rs.getString("email"), rs.getInt("len"), rs.getString("prefix")));
                }
            }

            // 2. Fix admin@hirehub.com (restore Admin@1234 hash)
            String adminHash = "$2a$12$s1Y5vC02Zcr7w53/StvonuqEVKLPYoNPlMcwU3uU9rHOK5SrD7uMa";
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = 'admin@hirehub.com'")) {
                ps.setString(1, adminHash);
                int updated = ps.executeUpdate();
                System.out.println("\n[2] admin@hirehub.com password restored to Admin@1234: " + (updated > 0 ? "SUCCESS" : "NOT FOUND"));
            }

            // 3. Sync bhaumikhinunia019@gmail.com hash from bhaumikhinunia077@gmail.com if available
            String studentHash077 = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE email = 'bhaumikhinunia077@gmail.com'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        studentHash077 = rs.getString("password_hash");
                    }
                }
            }

            if (studentHash077 != null && studentHash077.startsWith("$2a$")) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = 'bhaumikhinunia019@gmail.com'")) {
                    ps.setString(1, studentHash077);
                    int updated = ps.executeUpdate();
                    System.out.println("[3] bhaumikhinunia019@gmail.com password synced from bhaumikhinunia077@gmail.com: " + (updated > 0 ? "SUCCESS" : "NOT FOUND"));
                }
            } else {
                // If 077 is not present or invalid, set to Admin@1234 as fallback
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = 'bhaumikhinunia019@gmail.com'")) {
                    ps.setString(1, adminHash);
                    int updated = ps.executeUpdate();
                    System.out.println("[3] bhaumikhinunia019@gmail.com password reset to Admin@1234: " + (updated > 0 ? "SUCCESS" : "NOT FOUND"));
                }
            }

            // 4. Sync infotech@gmail.com hash from technostack@gmail.com if available
            String recruiterHash = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE email = 'technostack@gmail.com'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        recruiterHash = rs.getString("password_hash");
                    }
                }
            }

            if (recruiterHash != null && recruiterHash.startsWith("$2a$")) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = 'infotech@gmail.com'")) {
                    ps.setString(1, recruiterHash);
                    int updated = ps.executeUpdate();
                    System.out.println("[4] infotech@gmail.com password synced from technostack@gmail.com: " + (updated > 0 ? "SUCCESS" : "NOT FOUND"));
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = 'infotech@gmail.com'")) {
                    ps.setString(1, adminHash);
                    int updated = ps.executeUpdate();
                    System.out.println("[4] infotech@gmail.com password reset to Admin@1234: " + (updated > 0 ? "SUCCESS" : "NOT FOUND"));
                }
            }

            // 5. Verify final status
            System.out.println("\n[5] Repaired users status:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, email, role, LENGTH(password_hash) as len, LEFT(password_hash, 10) as prefix FROM users ORDER BY id ASC")) {
                while (rs.next()) {
                    boolean isValidBcrypt = rs.getInt("len") == 60 && rs.getString("prefix").startsWith("$2a$");
                    System.out.println(String.format("  - ID: %3d | Email: %-30s | Valid BCrypt: %s (%d chars)",
                            rs.getInt("id"), rs.getString("email"), isValidBcrypt ? "YES (PASS)" : "NO (CORRUPTED)", rs.getInt("len")));
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
