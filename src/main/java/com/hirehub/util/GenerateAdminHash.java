package com.hirehub.util;

/**
 * GenerateAdminHash — One-shot utility to produce a BCrypt hash
 * for the initial administrator seed password.
 *
 * Run this class directly (not as part of the main app) to get a
 * fresh BCrypt hash for use in database/schema.sql.
 *
 * Usage:
 *   Compile with the jbcrypt jar on classpath, then run:
 *   java -cp "target/classes;path/to/jbcrypt-0.4.jar" com.hirehub.util.GenerateAdminHash
 */
public class GenerateAdminHash {

    /** The initial admin password. Change this before deploying. */
    private static final String ADMIN_PASSWORD = "Admin@1234";

    public static void main(String[] args) {
        System.out.println("=== HireHub Admin Hash Generator ===");
        System.out.println("Password : " + ADMIN_PASSWORD);

        String hash = PasswordUtil.hashPassword(ADMIN_PASSWORD);

        System.out.println("BCrypt   : " + hash);
        System.out.println();
        System.out.println("-- Paste into database/schema.sql:");
        System.out.println("INSERT IGNORE INTO users (full_name, email, password_hash, role)");
        System.out.println("VALUES (");
        System.out.println("    'System Administrator',");
        System.out.println("    'admin@hirehub.com',");
        System.out.println("    '" + hash + "',");
        System.out.println("    'ADMIN'");
        System.out.println(");");

        // Self-verify: ensures PasswordUtil.verifyPassword accepts its own output
        boolean verified = PasswordUtil.verifyPassword(ADMIN_PASSWORD, hash);
        System.out.println();
        System.out.println("Self-verify: " + (verified ? "PASS ✓" : "FAIL ✗"));

        if (!verified) {
            System.err.println("ERROR: Hash verification failed. Do NOT use this hash.");
            System.exit(1);
        }
    }
}
