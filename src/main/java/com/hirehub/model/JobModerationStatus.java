package com.hirehub.model;

/**
 * Represents the administrative moderation status of a job posting.
 *
 * <p>Mirrors the MySQL ENUM('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')
 * defined in {@code jobs.moderation_status}.</p>
 *
 * <p><b>Important architectural distinction:</b> This enum governs compliance
 * and administrative moderation. It is strictly independent from {@link Job.Status},
 * which represents the recruiter job posting lifecycle (ACTIVE, DRAFT, CLOSED).</p>
 */
public enum JobModerationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /**
     * Returns a human-friendly display label for user interfaces.
     */
    public String label() {
        return switch (this) {
            case PENDING   -> "Pending Review";
            case APPROVED  -> "Approved";
            case REJECTED  -> "Rejected";
            case SUSPENDED -> "Suspended";
        };
    }

    /**
     * Null-safe parser that maps a database string to its corresponding enum value.
     * Returns {@code null} if the input is null or blank.
     * Defaults to {@code PENDING} if an unrecognized string is supplied (fail-closed).
     */
    public static JobModerationStatus fromString(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return JobModerationStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}

