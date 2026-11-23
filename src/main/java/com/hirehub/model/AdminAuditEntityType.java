package com.hirehub.model;

/**
 * Represents the target entity type audited by administrative actions.
 *
 * <p>Mirrors the MySQL ENUM('USER', 'RECRUITER_PROFILE', 'JOB')
 * defined in {@code admin_audit_logs.entity_type}.</p>
 */
public enum AdminAuditEntityType {
    USER,
    RECRUITER_PROFILE,
    JOB;

    /**
     * Null-safe parser that maps a database string to its corresponding enum value.
     * Returns {@code null} if the input is null or blank.
     * Defaults to {@code USER} if an unrecognized string is supplied.
     */
    public static AdminAuditEntityType fromString(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return AdminAuditEntityType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}

