package com.hirehub.model;

/**
 * Represents the verification status of a recruiter profile.
 *
 * <p>Mirrors the MySQL ENUM('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')
 * defined in {@code recruiter_profiles.verification_status}.</p>
 */
public enum RecruiterVerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED,
    SUSPENDED;

    /**
     * Returns a human-friendly display label for user interfaces.
     */
    public String label() {
        return switch (this) {
            case UNVERIFIED -> "Unverified";
            case PENDING    -> "Pending Verification";
            case VERIFIED   -> "Verified";
            case REJECTED   -> "Rejected";
            case SUSPENDED  -> "Suspended";
        };
    }

    /**
     * Null-safe parser that maps a database string to its corresponding enum value.
     * Returns {@code null} if the input is null or blank.
     * Defaults to {@code UNVERIFIED} if an unrecognized string is supplied (fail-closed).
     */
    public static RecruiterVerificationStatus fromString(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return RecruiterVerificationStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNVERIFIED;
        }
    }
}

