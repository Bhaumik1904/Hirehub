package com.hirehub.database;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseMigrationSchemaTest — Dedicated automated verification test suite
 * for Phase 6 Step 4.2A: Database Migration & Schema Setup.
 *
 * <p>Validates:
 * <ol>
 *   <li>Schema tests: recruiter verification columns, job moderation columns, audit log extension,
 *       indexes, and foreign keys with ON DELETE SET NULL.</li>
 *   <li>Default value tests: new recruiter profiles default to UNVERIFIED, new jobs default to PENDING,
 *       and existing job lifecycle enum values remain unchanged.</li>
 *   <li>Legacy migration tests: existing legacy recruiters grandfathered as VERIFIED, existing legacy jobs
 *       grandfathered as APPROVED, existing profile data preserved.</li>
 *   <li>Re-run safety tests: running migration again does NOT verify newly registered recruiters or
 *       approve newly posted jobs.</li>
 *   <li>Audit log tests: supports USER and JOB entities, nullable target_user_id, deleting referenced
 *       user/job preserves audit history with SET NULL behavior.</li>
 *   <li>Reviewer/moderator SET NULL tests: deleting a reviewer leaves verified_by and moderated_by NULL.</li>
 *   <li>Migration recovery & idempotency: repeated executions succeed with no duplicate constraint errors.</li>
 *   <li>100% teardown restoring database row counts to exact baseline.</li>
 * </ol>
 * </p>
 */
public class DatabaseMigrationSchemaTest {

    private static final String TEST_PREFIX = "STEP4_2A_TEST_";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    // Baseline counts
    private int baselineUsers;
    private int baselineRecruiters;
    private int baselineJobs;
    private int baselineAuditLogs;
    private int baselineApplications;

    // Track test IDs for teardown
    private final List<Integer> createdUserIds = new ArrayList<>();
    private final List<Integer> createdJobIds = new ArrayList<>();
    private final List<Integer> createdAuditLogIds = new ArrayList<>();

    public static void main(String[] args) {
        DatabaseMigrationSchemaTest verifier = new DatabaseMigrationSchemaTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2A: Migration & Schema Verification Suite");
        System.out.println("=================================================================");

        try (Connection conn = DatabaseConnection.getConnection()) {
            recordBaseline(conn);

            // Execute migration first to ensure database is at Step 4.2A
            System.out.println("\n--> Applying Step 4.2A Migration via ApplyMigration4_2...");
            ApplyMigration4_2.apply();

            // Run Verification Suites
            testSchemaIntegrity(conn);
            testDefaultValues(conn);
            testLegacyGrandfatheringAndReRunSafety(conn);
            testAuditLogBehaviors(conn);
            testReviewerSetNullBehaviors(conn);
            testMigrationRecoveryAndTracking(conn);

        } catch (Exception e) {
            System.err.println("[FATAL] Verifier execution failed with exception: " + e.getMessage());
            e.printStackTrace();
            failed++;
        } finally {
            teardownAndVerifyBaseline();
        }

        printSummary();
    }

    // -------------------------------------------------------------------------
    // Baseline Tracking
    // -------------------------------------------------------------------------

    private void recordBaseline(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            baselineUsers = getCount(stmt, "SELECT COUNT(*) FROM users");
            baselineRecruiters = getCount(stmt, "SELECT COUNT(*) FROM recruiter_profiles");
            baselineJobs = getCount(stmt, "SELECT COUNT(*) FROM jobs");
            baselineAuditLogs = getCount(stmt, "SELECT COUNT(*) FROM admin_audit_logs");
            baselineApplications = getCount(stmt, "SELECT COUNT(*) FROM applications");
        }
        System.out.printf("[INFO] Baseline row counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, apps=%d%n",
                baselineUsers, baselineRecruiters, baselineJobs, baselineAuditLogs, baselineApplications);
    }

    private int getCount(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // -------------------------------------------------------------------------
    // Suite 1: Schema Integrity Tests
    // -------------------------------------------------------------------------

    private void testSchemaIntegrity(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 1] Schema & Constraint Integrity Tests ---");

        // 1. Recruiter Profiles Columns
        assertCondition(ApplyMigration4_2.columnExists(conn, "recruiter_profiles", "verification_status"),
                "recruiter_profiles.verification_status column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "recruiter_profiles", "verification_reason"),
                "recruiter_profiles.verification_reason column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "recruiter_profiles", "verified_at"),
                "recruiter_profiles.verified_at column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "recruiter_profiles", "verified_by"),
                "recruiter_profiles.verified_by column exists");

        // 2. Recruiter Profiles Indexes & FK
        assertCondition(ApplyMigration4_2.indexExists(conn, "recruiter_profiles", "idx_rp_verification_status"),
                "recruiter_profiles index idx_rp_verification_status exists");
        assertCondition(ApplyMigration4_2.indexExists(conn, "recruiter_profiles", "idx_rp_verified_by"),
                "recruiter_profiles index idx_rp_verified_by exists");
        assertCondition(ApplyMigration4_2.foreignKeyExists(conn, "recruiter_profiles", "fk_rp_verified_by"),
                "recruiter_profiles FK fk_rp_verified_by exists");

        String rpFkRule = ApplyMigration4_2.getForeignKeyDeleteRule(conn, "fk_rp_verified_by");
        assertCondition("SET NULL".equalsIgnoreCase(rpFkRule),
                "fk_rp_verified_by has ON DELETE SET NULL (actual: " + rpFkRule + ")");

        // 3. Jobs Columns
        assertCondition(ApplyMigration4_2.columnExists(conn, "jobs", "moderation_status"),
                "jobs.moderation_status column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "jobs", "moderation_reason"),
                "jobs.moderation_reason column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "jobs", "moderated_at"),
                "jobs.moderated_at column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "jobs", "moderated_by"),
                "jobs.moderated_by column exists");

        // 4. Jobs Indexes & FK
        assertCondition(ApplyMigration4_2.indexExists(conn, "jobs", "idx_jobs_moderation_status"),
                "jobs index idx_jobs_moderation_status exists");
        assertCondition(ApplyMigration4_2.indexExists(conn, "jobs", "idx_jobs_status_moderation"),
                "jobs composite index idx_jobs_status_moderation exists");
        assertCondition(ApplyMigration4_2.indexExists(conn, "jobs", "idx_jobs_moderated_by"),
                "jobs index idx_jobs_moderated_by exists");
        assertCondition(ApplyMigration4_2.foreignKeyExists(conn, "jobs", "fk_jobs_moderated_by"),
                "jobs FK fk_jobs_moderated_by exists");

        String jobFkRule = ApplyMigration4_2.getForeignKeyDeleteRule(conn, "fk_jobs_moderated_by");
        assertCondition("SET NULL".equalsIgnoreCase(jobFkRule),
                "fk_jobs_moderated_by has ON DELETE SET NULL (actual: " + jobFkRule + ")");

        // 5. Admin Audit Logs Columns & FKs
        assertCondition(ApplyMigration4_2.columnExists(conn, "admin_audit_logs", "entity_type"),
                "admin_audit_logs.entity_type column exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "admin_audit_logs", "target_job_id"),
                "admin_audit_logs.target_job_id column exists");
        assertCondition(ApplyMigration4_2.isColumnNullable(conn, "admin_audit_logs", "target_user_id"),
                "admin_audit_logs.target_user_id is NULLABLE");

        String auditTargetRule = ApplyMigration4_2.getForeignKeyDeleteRule(conn, "fk_audit_target");
        assertCondition("SET NULL".equalsIgnoreCase(auditTargetRule),
                "fk_audit_target has ON DELETE SET NULL (actual: " + auditTargetRule + ")");

        assertCondition(ApplyMigration4_2.foreignKeyExists(conn, "admin_audit_logs", "fk_audit_job"),
                "admin_audit_logs FK fk_audit_job exists");
        String auditJobRule = ApplyMigration4_2.getForeignKeyDeleteRule(conn, "fk_audit_job");
        assertCondition("SET NULL".equalsIgnoreCase(auditJobRule),
                "fk_audit_job has ON DELETE SET NULL (actual: " + auditJobRule + ")");

        assertCondition(ApplyMigration4_2.indexExists(conn, "admin_audit_logs", "idx_audit_job"),
                "admin_audit_logs index idx_audit_job exists");
        assertCondition(ApplyMigration4_2.indexExists(conn, "admin_audit_logs", "idx_audit_entity"),
                "admin_audit_logs index idx_audit_entity exists");

        // 6. Migration Tracking Tables
        assertCondition(ApplyMigration4_2.columnExists(conn, "schema_migrations", "migration_id"),
                "schema_migrations tracking table exists");
        assertCondition(ApplyMigration4_2.columnExists(conn, "migration_legacy_records", "migration_id"),
                "migration_legacy_records tracking table exists");
    }

    // -------------------------------------------------------------------------
    // Suite 2: Default Value Tests
    // -------------------------------------------------------------------------

    private void testDefaultValues(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 2] Default Value Tests ---");

        // 1. Create a new recruiter user
        int recruiterUserId = createTestUser(conn, "New Recruiter", "new_recruiter@test.local", "RECRUITER");
        createdUserIds.add(recruiterUserId);

        // 2. Insert recruiter profile with NO verification_status specified
        String insertProfileSql = "INSERT INTO recruiter_profiles (user_id, company_name) VALUES (?, 'New Company Inc')";
        try (PreparedStatement stmt = conn.prepareStatement(insertProfileSql)) {
            stmt.setInt(1, recruiterUserId);
            stmt.executeUpdate();
        }

        // Query back verification_status
        String checkProfileSql = "SELECT verification_status, verification_reason, verified_at, verified_by " +
                                 "FROM recruiter_profiles WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkProfileSql)) {
            stmt.setInt(1, recruiterUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Newly created recruiter profile found");
                String vStatus = rs.getString("verification_status");
                assertCondition("UNVERIFIED".equals(vStatus),
                        "New recruiter profile verification_status defaults to UNVERIFIED (actual: " + vStatus + ")");
                assertCondition(rs.getString("verification_reason") == null, "verification_reason defaults to NULL");
                assertCondition(rs.getTimestamp("verified_at") == null, "verified_at defaults to NULL");
                assertCondition(rs.getObject("verified_by") == null, "verified_by defaults to NULL");
            }
        }

        // 3. Insert a new job with NO moderation_status specified
        String insertJobSql = "INSERT INTO jobs (recruiter_id, title, company_name, description, status) " +
                              "VALUES (?, 'Software Engineer', 'New Company Inc', 'Job description', 'ACTIVE')";
        int jobId = 0;
        try (PreparedStatement stmt = conn.prepareStatement(insertJobSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, recruiterUserId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) jobId = rs.getInt(1);
            }
        }
        assertCondition(jobId > 0, "Test job inserted successfully");
        createdJobIds.add(jobId);

        // Query back moderation_status
        String checkJobSql = "SELECT status, moderation_status, moderation_reason, moderated_at, moderated_by " +
                             "FROM jobs WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkJobSql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Newly created job found");
                String status = rs.getString("status");
                String modStatus = rs.getString("moderation_status");
                assertCondition("ACTIVE".equals(status), "Job status preserved as ACTIVE");
                assertCondition("PENDING".equals(modStatus),
                        "New job moderation_status defaults to PENDING (actual: " + modStatus + ")");
                assertCondition(rs.getString("moderation_reason") == null, "moderation_reason defaults to NULL");
                assertCondition(rs.getTimestamp("moderated_at") == null, "moderated_at defaults to NULL");
                assertCondition(rs.getObject("moderated_by") == null, "moderated_by defaults to NULL");
            }
        }

        // 4. Verify existing job lifecycle enum values
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE jobs SET status = 'DRAFT' WHERE id = " + jobId);
            stmt.executeUpdate("UPDATE jobs SET status = 'CLOSED' WHERE id = " + jobId);
            stmt.executeUpdate("UPDATE jobs SET status = 'ACTIVE' WHERE id = " + jobId);
            assertCondition(true, "Job lifecycle statuses (ACTIVE, DRAFT, CLOSED) all valid and supported");
        }
    }

    // -------------------------------------------------------------------------
    // Suite 3: Legacy Grandfathering & Re-Run Safety Tests
    // -------------------------------------------------------------------------

    private void testLegacyGrandfatheringAndReRunSafety(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 3] Legacy Grandfathering & Re-Run Safety Tests ---");

        // 1. Verify legacy recruiter (user ID 22) is VERIFIED and preserved
        String legacyRecruiterSql = "SELECT rp.verification_status, rp.company_name " +
                                    "FROM users u " +
                                    "JOIN recruiter_profiles rp ON u.id = rp.user_id " +
                                    "WHERE u.email = 'infotech@gmail.com'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(legacyRecruiterSql)) {
            assertCondition(rs.next(), "Legacy recruiter InfoTech found in database");
            String vStatus = rs.getString("verification_status");
            String compName = rs.getString("company_name");
            assertCondition("VERIFIED".equals(vStatus),
                    "Legacy recruiter was grandfathered as VERIFIED (actual: " + vStatus + ")");
            assertCondition("InfoTech".equals(compName),
                    "Legacy recruiter company details preserved as InfoTech (actual: " + compName + ")");
        }

        // 2. Verify legacy job (id 1) is APPROVED and preserved
        String legacyJobSql = "SELECT status, moderation_status, title FROM jobs WHERE id = 1";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(legacyJobSql)) {
            if (rs.next()) {
                String modStatus = rs.getString("moderation_status");
                String status = rs.getString("status");
                assertCondition("APPROVED".equals(modStatus),
                        "Legacy job ID 1 was grandfathered as APPROVED (actual: " + modStatus + ")");
                assertCondition("CLOSED".equals(status),
                        "Legacy job ID 1 status preserved as CLOSED (actual: " + status + ")");
            } else {
                System.out.println("[WARN] No legacy job id=1 found in current database; skipping legacy job check.");
            }
        }

        // 3. RE-RUN MIGRATION: Test re-run safety!
        System.out.println("[Suite 3] Re-running ApplyMigration4_2.apply() to test re-run safety...");
        ApplyMigration4_2.apply();

        // 4. Verify that the NEW recruiter created in Suite 2 remains UNVERIFIED!
        int newRecruiterId = createdUserIds.get(0);
        String checkNewRecruiterSql = "SELECT verification_status FROM recruiter_profiles WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkNewRecruiterSql)) {
            stmt.setInt(1, newRecruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "New recruiter profile found after migration re-run");
                String vStatus = rs.getString("verification_status");
                assertCondition("UNVERIFIED".equals(vStatus),
                        "Re-running migration did NOT verify new recruiter; remains UNVERIFIED (actual: " + vStatus + ")");
            }
        }

        // 5. Verify that the NEW job created in Suite 2 remains PENDING!
        int newJobId = createdJobIds.get(0);
        String checkNewJobSql = "SELECT moderation_status FROM jobs WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkNewJobSql)) {
            stmt.setInt(1, newJobId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "New job found after migration re-run");
                String modStatus = rs.getString("moderation_status");
                assertCondition("PENDING".equals(modStatus),
                        "Re-running migration did NOT approve new job; remains PENDING (actual: " + modStatus + ")");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Suite 4: Audit Log Extension & Target Deletion Safety Tests
    // -------------------------------------------------------------------------

    private void testAuditLogBehaviors(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 4] Audit Log Extension & Target Deletion Safety Tests ---");

        // 1. Create test admin
        int adminUserId = createTestUser(conn, "Audit Admin", "audit_admin@test.local", "ADMIN");
        createdUserIds.add(adminUserId);

        int targetUserId = createTestUser(conn, "Audit Target User", "audit_target@test.local", "STUDENT");
        createdUserIds.add(targetUserId);

        // Insert USER audit log
        String insertUserAuditSql = "INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, action, reason) " +
                                    "VALUES ('USER', ?, ?, 'DEACTIVATE_USER', 'Policy violation')";
        int userAuditId = 0;
        try (PreparedStatement stmt = conn.prepareStatement(insertUserAuditSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, adminUserId);
            stmt.setInt(2, targetUserId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) userAuditId = rs.getInt(1);
            }
        }
        assertCondition(userAuditId > 0, "Inserted USER audit log (id=" + userAuditId + ")");
        createdAuditLogIds.add(userAuditId);

        // Insert JOB audit log (with null target_user_id and valid target_job_id)
        int targetJobId = createdJobIds.get(0);
        String insertJobAuditSql = "INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, target_job_id, action, reason) " +
                                   "VALUES ('JOB', ?, NULL, ?, 'SUSPEND_JOB', 'Job posting suspended for review')";
        int jobAuditId = 0;
        try (PreparedStatement stmt = conn.prepareStatement(insertJobAuditSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, adminUserId);
            stmt.setInt(2, targetJobId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) jobAuditId = rs.getInt(1);
            }
        }
        assertCondition(jobAuditId > 0, "Inserted JOB audit log (id=" + jobAuditId + ")");
        createdAuditLogIds.add(jobAuditId);

        // 2. CRITICAL AUDIT TEST: Delete the target user and verify audit row survives with target_user_id = NULL
        int ephemeralUserId = createTestUser(conn, "Ephemeral User", "ephemeral_user@test.local", "STUDENT");
        int ephemeralAuditId = 0;
        try (PreparedStatement stmt = conn.prepareStatement(insertUserAuditSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, adminUserId);
            stmt.setInt(2, ephemeralUserId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) ephemeralAuditId = rs.getInt(1);
            }
        }
        assertCondition(ephemeralAuditId > 0, "Inserted ephemeral audit log (id=" + ephemeralAuditId + ")");
        createdAuditLogIds.add(ephemeralAuditId);

        // Delete the ephemeral user
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM users WHERE id = " + ephemeralUserId);
        }

        // Verify the audit log row was NOT cascaded!
        String checkSurvivingAuditSql = "SELECT target_user_id, action FROM admin_audit_logs WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkSurvivingAuditSql)) {
            stmt.setInt(1, ephemeralAuditId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Audit log entry survived after target user deletion!");
                Integer survivingTarget = (Integer) rs.getObject("target_user_id");
                assertCondition(survivingTarget == null,
                        "Audit log target_user_id was set to NULL on delete (actual: " + survivingTarget + ")");
            }
        }

        // 3. Delete ephemeral job and verify job audit survives with target_job_id = NULL
        int ephemeralJobId = 0;
        String insertEphemeralJobSql = "INSERT INTO jobs (recruiter_id, title, company_name, description, status) " +
                                       "VALUES (?, 'Ephemeral Job', 'Test Co', 'Desc', 'DRAFT')";
        try (PreparedStatement stmt = conn.prepareStatement(insertEphemeralJobSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, createdUserIds.get(0));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) ephemeralJobId = rs.getInt(1);
            }
        }

        int ephemeralJobAuditId = 0;
        try (PreparedStatement stmt = conn.prepareStatement(insertJobAuditSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, adminUserId);
            stmt.setInt(2, ephemeralJobId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) ephemeralJobAuditId = rs.getInt(1);
            }
        }
        createdAuditLogIds.add(ephemeralJobAuditId);

        // Delete the ephemeral job
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM jobs WHERE id = " + ephemeralJobId);
        }

        // Verify the job audit row survived!
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT target_job_id, action FROM admin_audit_logs WHERE id = ?")) {
            stmt.setInt(1, ephemeralJobAuditId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Job audit log entry survived after job deletion!");
                Integer survivingJob = (Integer) rs.getObject("target_job_id");
                assertCondition(survivingJob == null,
                        "Job audit target_job_id was set to NULL on delete (actual: " + survivingJob + ")");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Suite 5: Reviewer & Moderator ON DELETE SET NULL Tests
    // -------------------------------------------------------------------------

    private void testReviewerSetNullBehaviors(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 5] Reviewer & Moderator SET NULL Tests ---");

        // 1. Create an ephemeral reviewer admin
        int reviewerAdminId = createTestUser(conn, "Reviewer Admin", "reviewer_admin@test.local", "ADMIN");

        // 2. Set reviewer on recruiter_profiles
        int recruiterUserId = createdUserIds.get(0);
        String updateRecruiterReviewerSql =
                "UPDATE recruiter_profiles SET verified_by = ?, verified_at = NOW(), verification_status = 'VERIFIED' " +
                "WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateRecruiterReviewerSql)) {
            stmt.setInt(1, reviewerAdminId);
            stmt.setInt(2, recruiterUserId);
            stmt.executeUpdate();
        }

        // 3. Set moderator on jobs
        int testJobId = createdJobIds.get(0);
        String updateJobModeratorSql =
                "UPDATE jobs SET moderated_by = ?, moderated_at = NOW(), moderation_status = 'APPROVED' " +
                "WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateJobModeratorSql)) {
            stmt.setInt(1, reviewerAdminId);
            stmt.setInt(2, testJobId);
            stmt.executeUpdate();
        }

        // 4. Delete the reviewer admin user
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM users WHERE id = " + reviewerAdminId);
        }

        // 5. Verify verified_by became NULL in recruiter_profiles
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT verified_by, verification_status FROM recruiter_profiles WHERE user_id = ?")) {
            stmt.setInt(1, recruiterUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Recruiter profile found after reviewer deletion");
                assertCondition(rs.getObject("verified_by") == null,
                        "recruiter_profiles.verified_by was set to NULL on reviewer deletion");
                assertCondition("VERIFIED".equals(rs.getString("verification_status")),
                        "verification_status remains VERIFIED even after reviewer user deletion");
            }
        }

        // 6. Verify moderated_by became NULL in jobs
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT moderated_by, moderation_status FROM jobs WHERE id = ?")) {
            stmt.setInt(1, testJobId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "Job found after moderator deletion");
                assertCondition(rs.getObject("moderated_by") == null,
                        "jobs.moderated_by was set to NULL on moderator deletion");
                assertCondition("APPROVED".equals(rs.getString("moderation_status")),
                        "moderation_status remains APPROVED even after moderator user deletion");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Suite 6: Migration Recovery & Tracking Tests
    // -------------------------------------------------------------------------

    private void testMigrationRecoveryAndTracking(Connection conn) throws SQLException {
        System.out.println("\n--- [Suite 6] Migration Recovery & Tracking Tests ---");

        // 1. Check schema_migrations
        String checkMigrationSql = "SELECT details, applied_at FROM schema_migrations WHERE migration_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkMigrationSql)) {
            stmt.setString(1, ApplyMigration4_2.MIGRATION_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                assertCondition(rs.next(), "schema_migrations contains entry for " + ApplyMigration4_2.MIGRATION_ID);
                assertCondition(rs.getString("details") != null, "Migration details recorded");
                assertCondition(rs.getTimestamp("applied_at") != null, "Migration applied_at timestamp present");
            }
        }

        // 2. Check migration_legacy_records
        String checkLegacyRecordsSql = "SELECT entity_type, COUNT(*) AS cnt FROM migration_legacy_records " +
                                       "WHERE migration_id = ? GROUP BY entity_type";
        int recruiterCount = 0;
        int jobCount = 0;
        try (PreparedStatement stmt = conn.prepareStatement(checkLegacyRecordsSql)) {
            stmt.setString(1, ApplyMigration4_2.MIGRATION_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("entity_type");
                    int cnt = rs.getInt("cnt");
                    if ("RECRUITER".equals(type)) recruiterCount = cnt;
                    if ("JOB".equals(type)) jobCount = cnt;
                }
            }
        }
        assertCondition(recruiterCount > 0, "migration_legacy_records contains legacy recruiter snapshot (" + recruiterCount + ")");
        assertCondition(jobCount > 0, "migration_legacy_records contains legacy job snapshot (" + jobCount + ")");

        // 3. Run migration third time to guarantee 100% idempotency
        System.out.println("[Suite 6] Running ApplyMigration4_2.apply() third time for idempotency verification...");
        ApplyMigration4_2.apply();
        assertCondition(true, "Third consecutive migration execution completed cleanly with zero errors");
    }

    // -------------------------------------------------------------------------
    // Teardown & Baseline Restoration
    // -------------------------------------------------------------------------

    private void teardownAndVerifyBaseline() {
        System.out.println("\n--- [Teardown] Cleaning up test fixtures & restoring baseline ---");
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Delete created audit logs
            if (!createdAuditLogIds.isEmpty()) {
                for (int id : createdAuditLogIds) {
                    stmt.executeUpdate("DELETE FROM admin_audit_logs WHERE id = " + id);
                }
            }

            // Also delete any remaining audit logs referencing test prefix
            stmt.executeUpdate("DELETE FROM admin_audit_logs WHERE reason LIKE '%" + TEST_PREFIX + "%'");

            // 2. Delete created jobs
            if (!createdJobIds.isEmpty()) {
                for (int id : createdJobIds) {
                    stmt.executeUpdate("DELETE FROM jobs WHERE id = " + id);
                }
            }

            // 3. Delete recruiter_profiles and users
            if (!createdUserIds.isEmpty()) {
                for (int userId : createdUserIds) {
                    stmt.executeUpdate("DELETE FROM recruiter_profiles WHERE user_id = " + userId);
                    stmt.executeUpdate("DELETE FROM users WHERE id = " + userId);
                }
            }

            // Cleanup any test user matching prefix
            stmt.executeUpdate("DELETE FROM recruiter_profiles WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%" + TEST_PREFIX + "%')");
            stmt.executeUpdate("DELETE FROM users WHERE email LIKE '%" + TEST_PREFIX + "%'");

            // 4. Verify post-teardown row counts against initial baseline
            int currentUsers = getCount(stmt, "SELECT COUNT(*) FROM users");
            int currentRecruiters = getCount(stmt, "SELECT COUNT(*) FROM recruiter_profiles");
            int currentJobs = getCount(stmt, "SELECT COUNT(*) FROM jobs");
            int currentAuditLogs = getCount(stmt, "SELECT COUNT(*) FROM admin_audit_logs");
            int currentApps = getCount(stmt, "SELECT COUNT(*) FROM applications");

            System.out.printf("[INFO] Post-teardown counts: users=%d (baseline: %d), recruiter_profiles=%d (baseline: %d), " +
                              "jobs=%d (baseline: %d), audit_logs=%d (baseline: %d), apps=%d (baseline: %d)%n",
                    currentUsers, baselineUsers, currentRecruiters, baselineRecruiters,
                    currentJobs, baselineJobs, currentAuditLogs, baselineAuditLogs, currentApps, baselineApplications);

            assertCondition(currentUsers == baselineUsers, "Database users row count restored to baseline (" + baselineUsers + ")");
            assertCondition(currentRecruiters == baselineRecruiters, "Database recruiter_profiles row count restored to baseline (" + baselineRecruiters + ")");
            assertCondition(currentJobs == baselineJobs, "Database jobs row count restored to baseline (" + baselineJobs + ")");
            assertCondition(currentAuditLogs == baselineAuditLogs, "Database admin_audit_logs row count restored to baseline (" + baselineAuditLogs + ")");
            assertCondition(currentApps == baselineApplications, "Database applications row count restored to baseline (" + baselineApplications + ")");

        } catch (Exception e) {
            System.err.println("[ERROR] Teardown failed: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private int createTestUser(Connection conn, String name, String email, String role) throws SQLException {
        String testEmail = TEST_PREFIX + System.currentTimeMillis() + "_" + email;
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active) " +
                     "VALUES (?, ?, '$2a$12$DummyHashForTestingOnlyNotRealHashValue12345678', ?, TRUE)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, TEST_PREFIX + name);
            stmt.setString(2, testEmail);
            stmt.setString(3, role);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to create test user: " + testEmail);
    }

    private void assertCondition(boolean condition, String description) {
        if (condition) {
            System.out.println("  [PASS] " + description);
            passed++;
        } else {
            System.err.println("  [FAIL] " + description);
            failed++;
        }
    }

    private void printSummary() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("STEP 4.2A MIGRATION VERIFIER SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
