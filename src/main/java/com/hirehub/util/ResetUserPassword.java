package com.hirehub.util;

import com.hirehub.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * ResetUserPassword — CLI tool to reset any user's password securely with BCrypt.
 *
 * <p>Usage:</p>
 * <pre>
 *   .\verify_phase4.ps1 -MainClass "com.hirehub.util.ResetUserPassword"
 * </pre>
 */
public class ResetUserPassword {

    public static void main(String[] args) {
        String email = (args.length > 0 && !args[0].isBlank()) ? args[0].trim().toLowerCase() : null;
        String newPassword = (args.length > 1 && !args[1].isBlank()) ? args[1] : null;

        if (email == null) {
            System.out.println("=== HireHub User Password Reset Tool ===");
            System.out.println("Usage:");
            System.out.println("  .\\verify_phase4.ps1 -MainClass \"com.hirehub.util.ResetUserPassword\" -args \"user@email.com\", \"NewPass@123\"");
            System.out.println("\nListing all registered accounts in hirehub_db:");

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id, full_name, email, role, is_active FROM users ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(String.format("  - ID: %3d | %-9s | %-32s | %s",
                            rs.getInt("id"), rs.getString("role"), rs.getString("email"), rs.getString("full_name")));
                }
            } catch (Exception e) {
                System.err.println("DB Error: " + e.getMessage());
            }
            return;
        }

        if (newPassword == null) {
            System.err.println("[ERROR] Please provide the new password as the second argument.");
            System.exit(1);
        }

        String strengthErr = PasswordUtil.validatePasswordStrength(newPassword);
        if (strengthErr != null) {
            System.err.println("[ERROR] Password does not meet strength requirements: " + strengthErr);
            System.exit(1);
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String newHash = PasswordUtil.hashPassword(newPassword);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE email = ?")) {
                ps.setString(1, newHash);
                ps.setString(2, email);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    System.out.println("[SUCCESS] Password successfully reset for " + email + "!");
                    boolean verified = PasswordUtil.verifyPassword(newPassword, newHash);
                    System.out.println("[VERIFIED] BCrypt verification check: " + (verified ? "PASS ✓" : "FAIL ✗"));
                } else {
                    System.err.println("[NOT FOUND] No user found with email: " + email);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Database operation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
