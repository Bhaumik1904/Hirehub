package com.hirehub.util;

import com.hirehub.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ApplyMigration4_2 — Idempotent migration runner for Phase 6 Step 4.2A:
 * Recruiter Verification, Job Moderation & Audit Log Extension.
 *
 * <p>Requirements enforced:
 * <ul>
 *   <li>Inspects information_schema for every column, index, and constraint before mutating.</li>
 *   <li>Maintains persistent migration tracking and legacy entity snapshots in MySQL.</li>
 *   <li>Grandfathers ONLY captured legacy recruiters as VERIFIED and legacy jobs as APPROVED.</li>
 *   <li>New recruiter profiles default to UNVERIFIED; new jobs default to PENDING.</li>
 *   <li>Re-running migration never modifies newly created unverified recruiters or pending jobs.</li>
 *   <li>Converts admin_audit_logs.target_user_id FK from ON DELETE CASCADE to ON DELETE SET NULL
 *       so disciplinary audit history is never erased when accounts are deleted.</li>
 *   <li>Fully restartable upon partial interruption; safe for repeated executions.</li>
 * </ul>
 * </p>
 */
public class ApplyMigration4_2 {

    public static final String MIGRATION_ID = "phase6_step4_2a";
    public static final String LEGACY_RECRUITER_PLACEHOLDER = "Legacy Recruiter Profile";

    public static void apply() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println("=================================================================");
            System.out.println("Applying Phase 6 Step 4.2A Migration: " + MIGRATION_ID);
            System.out.println("=================================================================");

            // 1. Ensure migration tracking tables exist
            ensureMigrationTrackingTables(conn);

            // 2. Capture legacy recruiter and job snapshots BEFORE schema alteration
            captureLegacySnapshots(conn);

            // 3. Alter recruiter_profiles table (verification columns, FK, indexes)
            applyRecruiterProfileSchema(conn);

            // 4. Alter jobs table (moderation columns, FK, indexes)
            applyJobModerationSchema(conn);

            // 5. Alter admin_audit_logs table (entity_type, target_job_id, safe FK)
            applyAuditLogSchema(conn);

            // 6. Grandfather ONLY captured legacy records
            grandfatherLegacyRecords(conn);

            // 7. Record migration completion in tracking table
            recordMigrationComplete(conn);

            System.out.println("=================================================================");
            System.out.println("Phase 6 Step 4.2A Migration applied successfully!");
            System.out.println("=================================================================");
        }
    }

    // -------------------------------------------------------------------------
    // Step 1: Migration Tracking Tables
    // -------------------------------------------------------------------------

    private static void ensureMigrationTrackingTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "  migration_id VARCHAR(64) PRIMARY KEY," +
                    "  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  details TEXT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS migration_legacy_records (" +
                    "  id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  migration_id VARCHAR(64) NOT NULL," +
                    "  entity_type VARCHAR(32) NOT NULL," +
                    "  entity_id INT NOT NULL," +
                    "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uq_migration_entity (migration_id, entity_type, entity_id)," +
                    "  INDEX idx_mlr_lookup (migration_id, entity_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
        System.out.println("[Migration] Tracking tables (schema_migrations, migration_legacy_records) verified.");
    }

    // -------------------------------------------------------------------------
    // Step 2: Capture Legacy Snapshots
    // -------------------------------------------------------------------------

    private static void captureLegacySnapshots(Connection conn) throws SQLException {
        int existingSnapshots = 0;
        String countSql = "SELECT COUNT(*) FROM migration_legacy_records WHERE migration_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setString(1, MIGRATION_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    existingSnapshots = rs.getInt(1);
                }
            }
        }

        if (existingSnapshots > 0) {
            System.out.printf("[Migration] Existing legacy snapshot found (%d records). Preserving persistent snapshot.%n",
                    existingSnapshots);
            return;
        }

        System.out.println("[Migration] Capturing initial legacy snapshot for " + MIGRATION_ID + "...");

        // 2a. Capture all existing recruiters from users table
        List<Integer> recruiterUserIds = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM users WHERE role = 'RECRUITER'")) {
            while (rs.next()) {
                recruiterUserIds.add(rs.getInt(1));
            }
        }

        // For each recruiter, ensure a recruiter_profiles row exists, then snapshot ID
        String checkProfileSql = "SELECT COUNT(*) FROM recruiter_profiles WHERE user_id = ?";
        String insertProfileSql = "INSERT INTO recruiter_profiles (user_id, company_name, company_description, updated_at) " +
                                  "VALUES (?, ?, 'Auto-created during Phase 6 Step 4.2A migration', NOW())";
        String snapshotSql = "INSERT IGNORE INTO migration_legacy_records (migration_id, entity_type, entity_id) VALUES (?, ?, ?)";

        int createdProfiles = 0;
        for (int recruiterId : recruiterUserIds) {
            boolean hasProfile = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkProfileSql)) {
                checkStmt.setInt(1, recruiterId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        hasProfile = true;
                    }
                }
            }

            if (!hasProfile) {
                try (PreparedStatement insStmt = conn.prepareStatement(insertProfileSql)) {
                    insStmt.setInt(1, recruiterId);
                    insStmt.setString(2, LEGACY_RECRUITER_PLACEHOLDER);
                    insStmt.executeUpdate();
                    createdProfiles++;
                    System.out.printf("[Migration] Created missing recruiter profile placeholder for user ID %d.%n", recruiterId);
                }
            }

            try (PreparedStatement snapStmt = conn.prepareStatement(snapshotSql)) {
                snapStmt.setString(1, MIGRATION_ID);
                snapStmt.setString(2, "RECRUITER");
                snapStmt.setInt(3, recruiterId);
                snapStmt.executeUpdate();
            }
        }
        System.out.printf("[Migration] Captured %d legacy recruiter account(s) (created %d placeholder profile(s)).%n",
                recruiterUserIds.size(), createdProfiles);

        // 2b. Capture all existing jobs
        List<Integer> jobIds = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM jobs")) {
            while (rs.next()) {
                jobIds.add(rs.getInt(1));
            }
        }

        for (int jobId : jobIds) {
            try (PreparedStatement snapStmt = conn.prepareStatement(snapshotSql)) {
                snapStmt.setString(1, MIGRATION_ID);
                snapStmt.setString(2, "JOB");
                snapStmt.setInt(3, jobId);
                snapStmt.executeUpdate();
            }
        }
        System.out.printf("[Migration] Captured %d legacy job listing(s).%n", jobIds.size());
    }

    // -------------------------------------------------------------------------
    // Step 3: Recruiter Profile Schema
    // -------------------------------------------------------------------------

    private static void applyRecruiterProfileSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!columnExists(conn, "recruiter_profiles", "verification_status")) {
                System.out.println("[Migration] Adding verification_status to recruiter_profiles...");
                stmt.executeUpdate(
                        "ALTER TABLE recruiter_profiles " +
                        "ADD COLUMN verification_status ENUM('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED') " +
                        "NOT NULL DEFAULT 'UNVERIFIED' AFTER industry");
            }

            if (!columnExists(conn, "recruiter_profiles", "verification_reason")) {
                System.out.println("[Migration] Adding verification_reason to recruiter_profiles...");
                stmt.executeUpdate(
                        "ALTER TABLE recruiter_profiles " +
                        "ADD COLUMN verification_reason VARCHAR(500) NULL AFTER verification_status");
            }

            if (!columnExists(conn, "recruiter_profiles", "verified_at")) {
                System.out.println("[Migration] Adding verified_at to recruiter_profiles...");
                stmt.executeUpdate(
                        "ALTER TABLE recruiter_profiles " +
                        "ADD COLUMN verified_at TIMESTAMP NULL AFTER verification_reason");
            }

            if (!columnExists(conn, "recruiter_profiles", "verified_by")) {
                System.out.println("[Migration] Adding verified_by to recruiter_profiles...");
                stmt.executeUpdate(
                        "ALTER TABLE recruiter_profiles " +
                        "ADD COLUMN verified_by INT NULL AFTER verified_at");
            }

            if (!indexExists(conn, "recruiter_profiles", "idx_rp_verification_status")) {
                System.out.println("[Migration] Adding index idx_rp_verification_status to recruiter_profiles...");
                stmt.executeUpdate("ALTER TABLE recruiter_profiles ADD INDEX idx_rp_verification_status (verification_status)");
            }

            if (!indexExists(conn, "recruiter_profiles", "idx_rp_verified_by")) {
                System.out.println("[Migration] Adding index idx_rp_verified_by to recruiter_profiles...");
                stmt.executeUpdate("ALTER TABLE recruiter_profiles ADD INDEX idx_rp_verified_by (verified_by)");
            }

            if (!foreignKeyExists(conn, "recruiter_profiles", "fk_rp_verified_by")) {
                System.out.println("[Migration] Adding FK fk_rp_verified_by to recruiter_profiles...");
                stmt.executeUpdate(
                        "ALTER TABLE recruiter_profiles " +
                        "ADD CONSTRAINT fk_rp_verified_by FOREIGN KEY (verified_by) REFERENCES users (id) " +
                        "ON DELETE SET NULL ON UPDATE CASCADE");
            }
        }
        System.out.println("[Migration] recruiter_profiles schema verified.");
    }

    // -------------------------------------------------------------------------
    // Step 4: Job Moderation Schema
    // -------------------------------------------------------------------------

    private static void applyJobModerationSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!columnExists(conn, "jobs", "moderation_status")) {
                System.out.println("[Migration] Adding moderation_status to jobs...");
                stmt.executeUpdate(
                        "ALTER TABLE jobs " +
                        "ADD COLUMN moderation_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED') " +
                        "NOT NULL DEFAULT 'PENDING' AFTER status");
            }

            if (!columnExists(conn, "jobs", "moderation_reason")) {
                System.out.println("[Migration] Adding moderation_reason to jobs...");
                stmt.executeUpdate(
                        "ALTER TABLE jobs " +
                        "ADD COLUMN moderation_reason VARCHAR(500) NULL AFTER moderation_status");
            }

            if (!columnExists(conn, "jobs", "moderated_at")) {
                System.out.println("[Migration] Adding moderated_at to jobs...");
                stmt.executeUpdate(
                        "ALTER TABLE jobs " +
                        "ADD COLUMN moderated_at TIMESTAMP NULL AFTER moderation_reason");
            }

            if (!columnExists(conn, "jobs", "moderated_by")) {
                System.out.println("[Migration] Adding moderated_by to jobs...");
                stmt.executeUpdate(
                        "ALTER TABLE jobs " +
                        "ADD COLUMN moderated_by INT NULL AFTER moderated_at");
            }

            if (!indexExists(conn, "jobs", "idx_jobs_moderation_status")) {
                System.out.println("[Migration] Adding index idx_jobs_moderation_status to jobs...");
                stmt.executeUpdate("ALTER TABLE jobs ADD INDEX idx_jobs_moderation_status (moderation_status)");
            }

            if (!indexExists(conn, "jobs", "idx_jobs_status_moderation")) {
                System.out.println("[Migration] Adding composite index idx_jobs_status_moderation to jobs...");
                stmt.executeUpdate("ALTER TABLE jobs ADD INDEX idx_jobs_status_moderation (status, moderation_status)");
            }

            if (!indexExists(conn, "jobs", "idx_jobs_moderated_by")) {
                System.out.println("[Migration] Adding index idx_jobs_moderated_by to jobs...");
                stmt.executeUpdate("ALTER TABLE jobs ADD INDEX idx_jobs_moderated_by (moderated_by)");
            }

            if (!foreignKeyExists(conn, "jobs", "fk_jobs_moderated_by")) {
                System.out.println("[Migration] Adding FK fk_jobs_moderated_by to jobs...");
                stmt.executeUpdate(
                        "ALTER TABLE jobs " +
                        "ADD CONSTRAINT fk_jobs_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id) " +
                        "ON DELETE SET NULL ON UPDATE CASCADE");
            }
        }
        System.out.println("[Migration] jobs schema verified.");
    }

    // -------------------------------------------------------------------------
    // Step 5: Audit Log Schema Extension & Safe Deletion Rule
    // -------------------------------------------------------------------------

    private static void applyAuditLogSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!columnExists(conn, "admin_audit_logs", "entity_type")) {
                System.out.println("[Migration] Adding entity_type to admin_audit_logs...");
                stmt.executeUpdate(
                        "ALTER TABLE admin_audit_logs " +
                        "ADD COLUMN entity_type ENUM('USER', 'RECRUITER_PROFILE', 'JOB') " +
                        "NOT NULL DEFAULT 'USER' AFTER id");
            }

            if (!columnExists(conn, "admin_audit_logs", "target_job_id")) {
                System.out.println("[Migration] Adding target_job_id to admin_audit_logs...");
                stmt.executeUpdate(
                        "ALTER TABLE admin_audit_logs " +
                        "ADD COLUMN target_job_id INT NULL AFTER target_user_id");
            }

            // Ensure target_user_id is nullable
            if (!isColumnNullable(conn, "admin_audit_logs", "target_user_id")) {
                System.out.println("[Migration] Modifying target_user_id to be NULLABLE in admin_audit_logs...");
                stmt.executeUpdate("ALTER TABLE admin_audit_logs MODIFY COLUMN target_user_id INT NULL");
            }

            // Ensure reason column supports up to 500 characters
            stmt.executeUpdate("ALTER TABLE admin_audit_logs MODIFY COLUMN reason VARCHAR(500) NULL");

            // Inspect FK on target_user_id: if CASCADE, drop and recreate with SET NULL
            String targetFkName = findForeignKey(conn, "admin_audit_logs", "target_user_id", "users");
            if (targetFkName != null) {
                String deleteRule = getForeignKeyDeleteRule(conn, targetFkName);
                if ("CASCADE".equalsIgnoreCase(deleteRule)) {
                    System.out.printf("[Migration] Dropping CASCADE FK %s on admin_audit_logs.target_user_id...%n", targetFkName);
                    stmt.executeUpdate("ALTER TABLE admin_audit_logs DROP FOREIGN KEY " + targetFkName);
                    targetFkName = null;
                }
            }

            if (targetFkName == null) {
                System.out.println("[Migration] Adding ON DELETE SET NULL FK on admin_audit_logs.target_user_id...");
                stmt.executeUpdate(
                        "ALTER TABLE admin_audit_logs " +
                        "ADD CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) " +
                        "ON DELETE SET NULL ON UPDATE CASCADE");
            }

            // Add FK for target_job_id
            if (!foreignKeyExists(conn, "admin_audit_logs", "fk_audit_job")) {
                System.out.println("[Migration] Adding FK fk_audit_job on admin_audit_logs.target_job_id...");
                stmt.executeUpdate(
                        "ALTER TABLE admin_audit_logs " +
                        "ADD CONSTRAINT fk_audit_job FOREIGN KEY (target_job_id) REFERENCES jobs (id) " +
                        "ON DELETE SET NULL ON UPDATE CASCADE");
            }

            if (!indexExists(conn, "admin_audit_logs", "idx_audit_job")) {
                System.out.println("[Migration] Adding index idx_audit_job to admin_audit_logs...");
                stmt.executeUpdate("ALTER TABLE admin_audit_logs ADD INDEX idx_audit_job (target_job_id)");
            }

            if (!indexExists(conn, "admin_audit_logs", "idx_audit_entity")) {
                System.out.println("[Migration] Adding index idx_audit_entity to admin_audit_logs...");
                stmt.executeUpdate("ALTER TABLE admin_audit_logs ADD INDEX idx_audit_entity (entity_type)");
            }
        }
        System.out.println("[Migration] admin_audit_logs schema verified.");
    }

    // -------------------------------------------------------------------------
    // Step 6: Grandfather Captured Legacy Records
    // -------------------------------------------------------------------------

    private static void grandfatherLegacyRecords(Connection conn) throws SQLException {
        // Grandfather only recruiters present in persistent migration_legacy_records
        String updateRecruitersSql =
                "UPDATE recruiter_profiles rp " +
                "JOIN migration_legacy_records mlr " +
                "  ON rp.user_id = mlr.entity_id " +
                " AND mlr.migration_id = ? " +
                " AND mlr.entity_type = 'RECRUITER' " +
                "SET rp.verification_status = 'VERIFIED', " +
                "    rp.verification_reason = 'Grandfathered legacy recruiter verified during Phase 6 Step 4.2A migration', " +
                "    rp.verified_at = COALESCE(rp.verified_at, NOW()) " +
                "WHERE rp.verification_status != 'VERIFIED'";

        int recruitersGrandfathered = 0;
        try (PreparedStatement stmt = conn.prepareStatement(updateRecruitersSql)) {
            stmt.setString(1, MIGRATION_ID);
            recruitersGrandfathered = stmt.executeUpdate();
        }
        System.out.printf("[Migration] Grandfathered %d legacy recruiter profile(s) as VERIFIED.%n",
                recruitersGrandfathered);

        // Grandfather only jobs present in persistent migration_legacy_records
        String updateJobsSql =
                "UPDATE jobs j " +
                "JOIN migration_legacy_records mlr " +
                "  ON j.id = mlr.entity_id " +
                " AND mlr.migration_id = ? " +
                " AND mlr.entity_type = 'JOB' " +
                "SET j.moderation_status = 'APPROVED', " +
                "    j.moderation_reason = 'Grandfathered legacy job approved during Phase 6 Step 4.2A migration', " +
                "    j.moderated_at = COALESCE(j.moderated_at, NOW()) " +
                "WHERE j.moderation_status != 'APPROVED'";

        int jobsGrandfathered = 0;
        try (PreparedStatement stmt = conn.prepareStatement(updateJobsSql)) {
            stmt.setString(1, MIGRATION_ID);
            jobsGrandfathered = stmt.executeUpdate();
        }
        System.out.printf("[Migration] Grandfathered %d legacy job(s) as APPROVED.%n",
                jobsGrandfathered);
    }

    // -------------------------------------------------------------------------
    // Step 7: Record Completion
    // -------------------------------------------------------------------------

    private static void recordMigrationComplete(Connection conn) throws SQLException {
        String sql = "INSERT INTO schema_migrations (migration_id, details) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE applied_at = NOW(), details = VALUES(details)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, MIGRATION_ID);
            stmt.setString(2, "Phase 6 Step 4.2A: Recruiter Verification, Job Moderation & Audit Log Extension");
            stmt.executeUpdate();
        }
        System.out.println("[Migration] Recorded migration progress in schema_migrations table.");
    }

    // -------------------------------------------------------------------------
    // Schema Inspection Helpers
    // -------------------------------------------------------------------------

    public static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static boolean isColumnNullable(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT IS_NULLABLE FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && "YES".equalsIgnoreCase(rs.getString(1));
            }
        }
    }

    public static boolean indexExists(Connection conn, String tableName, String indexName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, indexName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static boolean foreignKeyExists(Connection conn, String tableName, String fkName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                     "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? " +
                     "  AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, fkName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public static String findForeignKey(Connection conn, String tableName, String columnName, String referencedTable) throws SQLException {
        String sql = "SELECT rc.CONSTRAINT_NAME " +
                     "FROM information_schema.REFERENTIAL_CONSTRAINTS rc " +
                     "JOIN information_schema.KEY_COLUMN_USAGE kcu " +
                     "  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA " +
                     " AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                     "WHERE rc.CONSTRAINT_SCHEMA = DATABASE() " +
                     "  AND rc.TABLE_NAME = ? " +
                     "  AND kcu.COLUMN_NAME = ? " +
                     "  AND rc.REFERENCED_TABLE_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            stmt.setString(3, referencedTable);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    public static String getForeignKeyDeleteRule(Connection conn, String fkName) throws SQLException {
        String sql = "SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS " +
                     "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fkName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            apply();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

