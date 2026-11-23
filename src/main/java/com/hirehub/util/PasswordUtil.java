package com.hirehub.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * PasswordUtil — Utility class for secure password hashing and verification.
 *
 * <p>Uses BCrypt, an adaptive hashing algorithm specifically designed for
 * password storage. BCrypt automatically handles salt generation.</p>
 *
 * <p>Plain-text passwords are NEVER stored or logged.</p>
 */
public class PasswordUtil {

    /**
     * BCrypt work factor (cost factor).
     * Higher = slower hashing = more secure.
     * 12 is a good balance for 2024-era hardware (~250ms per hash).
     */
    private static final int BCRYPT_ROUNDS = 12;

    // Prevent instantiation
    private PasswordUtil() {}

    /**
     * Hashes a plain-text password using BCrypt.
     *
     * @param plainPassword the raw password from the user
     * @return a BCrypt hash string (60 characters) safe to store in the DB
     * @throws IllegalArgumentException if the password is null or blank
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword  the password entered by the user during login
     * @param hashedPassword the hash stored in the database
     * @return true if the password matches the hash, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // BCrypt throws this for malformed hashes — treat as mismatch
            System.err.println("[PasswordUtil] Invalid hash format: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a password meets minimum security requirements.
     *
     * @param password the candidate password
     * @return a human-readable error message, or null if the password is acceptable
     */
    public static String validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one digit.";
        }
        return null; // password is valid
    }
}
