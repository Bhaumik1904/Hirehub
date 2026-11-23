package com.hirehub.moderation;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.AdminModerationService;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AdminModerationServiceTest — automated verification suite for
 * Phase 6 Step 4.2C.1: AdminModerationService (Admin-Only Recruiter Verification & Job Moderation).
 *
 * <p>Validates:
 * <ul>
 *   <li>Offline architecture, reflection, and SessionManager authorization boundaries.</li>
 *   <li>Fail-closed role security: unauthenticated, student, and recruiter callers are blocked.</li>
 *   <li>Identity mismatch and non-positive ID validation.</li>
 *   <li>Live database stale-session protection (deactivated admin in DB is blocked).</li>
 *   <li>Atomic recruiter verification transitions: VERIFIED, REJECTED, SUSPENDED, PENDING.</li>
 *   <li>Atomic job moderation transitions: APPROVED, REJECTED, SUSPENDED, PENDING.</li>
 *   <li>Reason enforcement: mandatory min 3 chars for REJECTED/SUSPENDED, max 500 chars limit.</li>
 *   <li>Lifecycle independence: job approval does not alter job lifecycle status (DRAFT/ACTIVE/CLOSED).</li>
 *   <li>Recruiter verification independence: job moderation does not alter recruiter profile status.</li>
 *   <li>Audit log integrity: 1:1 transaction sync, correct entity type, actor admin ID, and target ID.</li>
 *   <li>Full data query APIs and exact restoration of the database baseline.</li>
 * </ul>
 * </p>
 */
public class AdminModerationServiceTest {

    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2C.1: AdminModerationService Verifier");
        System.out.println("=================================================================");

        AdminModerationServiceTest verifier = new AdminModerationServiceTest();

        try {
            verifier.runOfflineArchitectureSuite();
            verifier.runLiveDatabaseSuite();
        } catch (Exception e) {
            System.err.println("[FATAL ERROR] Unexpected exception during verifier execution:");
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("STEP 4.2C.1 VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Architecture, Reflection & Session Guards
    // =========================================================================

    private void runOfflineArchitectureSuite() {
        System.out.println("\n--- [1] Running Offline Architecture & Reflection Suite ---");

        // 1. Reflection check on AdminModerationService methods
        checkMethod(AdminModerationService.class, "verifyRecruiter", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "rejectRecruiter", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "suspendRecruiter", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "returnRecruiterToPending", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "resetRecruiterToPending", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "moderateRecruiter", int.class, int.class,
                RecruiterVerificationStatus.class, String.class);

        checkMethod(AdminModerationService.class, "approveJob", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "rejectJob", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "suspendJob", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "returnJobToPending", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "resetJobToPending", int.class, int.class, String.class);
        checkMethod(AdminModerationService.class, "moderateJob", int.class, int.class,
                JobModerationStatus.class, String.class);

        checkMethod(AdminModerationService.class, "listRecruitersForModeration",
                String.class, RecruiterVerificationStatus.class, int.class, int.class);
        checkMethod(AdminModerationService.class, "countRecruitersForModeration",
                String.class, RecruiterVerificationStatus.class);
        checkMethod(AdminModerationService.class, "getRecruiterProfileByUserId", int.class);

        checkMethod(AdminModerationService.class, "listJobsForModeration",
                String.class, Job.Status.class, JobModerationStatus.class, int.class, int.class);
        checkMethod(AdminModerationService.class, "countJobsForModeration",
                String.class, Job.Status.class, JobModerationStatus.class);
        checkMethod(AdminModerationService.class, "getJobById", int.class);

        checkMethod(AdminModerationService.class, "getModerationLogsForRecruiter", int.class);
        checkMethod(AdminModerationService.class, "getModerationLogsForJob", int.class);
        checkMethod(AdminModerationService.class, "getRecentModerationLogs", int.class);

        // 2. Offline Session Authorization Guards
        SessionManager session = SessionManager.getInstance();
        AdminModerationService service = new AdminModerationService();

        // 2A: Unauthenticated caller
        session.logout();
        assertFalse("Unauthenticated caller blocked from verifyRecruiter",
                service.verifyRecruiter(1, 10, "Test").isSuccess());
        assertFalse("Unauthenticated caller blocked from rejectRecruiter",
                service.rejectRecruiter(1, 10, "Violation").isSuccess());
        assertFalse("Unauthenticated caller blocked from suspendRecruiter",
                service.suspendRecruiter(1, 10, "Suspicious").isSuccess());
        assertFalse("Unauthenticated caller blocked from returnRecruiterToPending",
                service.returnRecruiterToPending(1, 10, "Review").isSuccess());
        assertFalse("Unauthenticated caller blocked from approveJob",
                service.approveJob(1, 20, "Test").isSuccess());
        assertFalse("Unauthenticated caller blocked from rejectJob",
                service.rejectJob(1, 20, "Spam").isSuccess());
        assertFalse("Unauthenticated caller blocked from suspendJob",
                service.suspendJob(1, 20, "Violation").isSuccess());
        assertFalse("Unauthenticated caller blocked from returnJobToPending",
                service.returnJobToPending(1, 20, "Review").isSuccess());
        assertFalse("Unauthenticated caller blocked from listRecruitersForModeration",
                service.listRecruitersForModeration(null, null, 10, 0).isSuccess());
        assertFalse("Unauthenticated caller blocked from countRecruitersForModeration",
                service.countRecruitersForModeration(null, null).isSuccess());
        assertFalse("Unauthenticated caller blocked from getRecruiterProfileByUserId",
                service.getRecruiterProfileByUserId(10).isSuccess());
        assertFalse("Unauthenticated caller blocked from listJobsForModeration",
                service.listJobsForModeration(null, null, null, 10, 0).isSuccess());
        assertFalse("Unauthenticated caller blocked from countJobsForModeration",
                service.countJobsForModeration(null, null, null).isSuccess());
        assertFalse("Unauthenticated caller blocked from getJobById",
                service.getJobById(20).isSuccess());
        assertFalse("Unauthenticated caller blocked from getModerationLogsForRecruiter",
                service.getModerationLogsForRecruiter(10).isSuccess());
        assertFalse("Unauthenticated caller blocked from getModerationLogsForJob",
                service.getModerationLogsForJob(20).isSuccess());
        assertFalse("Unauthenticated caller blocked from getRecentModerationLogs",
                service.getRecentModerationLogs(10).isSuccess());

        // 2B: Student caller
        User studentUser = new User(101, "Student Mock", "student@test.local", "hash", User.Role.STUDENT, true, LocalDateTime.now());
        session.login(studentUser);
        assertFalse("Student session blocked from verifyRecruiter",
                service.verifyRecruiter(101, 10, "Test").isSuccess());
        assertFalse("Student session blocked from approveJob",
                service.approveJob(101, 20, "Test").isSuccess());
        assertFalse("Student session blocked from listRecruitersForModeration",
                service.listRecruitersForModeration(null, null, 10, 0).isSuccess());
        assertFalse("Student session blocked from listJobsForModeration",
                service.listJobsForModeration(null, null, null, 10, 0).isSuccess());

        // 2C: Recruiter caller
        User recruiterUser = new User(201, "Recruiter Mock", "recruiter@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now());
        session.login(recruiterUser);
        assertFalse("Recruiter session blocked from verifyRecruiter",
                service.verifyRecruiter(201, 10, "Test").isSuccess());
        assertFalse("Recruiter session blocked from approveJob",
                service.approveJob(201, 20, "Test").isSuccess());
        assertFalse("Recruiter session blocked from listRecruitersForModeration",
                service.listRecruitersForModeration(null, null, 10, 0).isSuccess());
        assertFalse("Recruiter session blocked from listJobsForModeration",
                service.listJobsForModeration(null, null, null, 10, 0).isSuccess());

        // 2D: Deactivated Admin in session
        User inactiveAdmin = new User(301, "Inactive Admin", "deact_admin@test.local", "hash", User.Role.ADMIN, false, LocalDateTime.now());
        session.login(inactiveAdmin);
        assertFalse("Deactivated admin in session blocked from verifyRecruiter",
                service.verifyRecruiter(301, 10, "Test").isSuccess());
        assertFalse("Deactivated admin in session blocked from approveJob",
                service.approveJob(301, 20, "Test").isSuccess());
        assertFalse("Deactivated admin in session blocked from listRecruitersForModeration",
                service.listRecruitersForModeration(null, null, 10, 0).isSuccess());

        // 2E: Active Admin session — Parameter & Identity Guards (evaluated before DB calls)
        User activeAdminMock = new User(99, "Admin Mock", "admin@test.local", "hash", User.Role.ADMIN, true, LocalDateTime.now());
        session.login(activeAdminMock);

        // Identity mismatch: passed adminId != session user ID
        AdminModerationService.ServiceResult<Void> mismatchRes1 = service.verifyRecruiter(100, 10, "Test");
        assertFalse("Admin identity mismatch blocked in verifyRecruiter", mismatchRes1.isSuccess());
        assertTrue("Mismatch message indicates identity mismatch",
                mismatchRes1.getMessage().toLowerCase().contains("mismatch"));

        AdminModerationService.ServiceResult<Void> mismatchRes2 = service.approveJob(100, 20, "Test");
        assertFalse("Admin identity mismatch blocked in approveJob", mismatchRes2.isSuccess());
        assertTrue("Mismatch message indicates identity mismatch",
                mismatchRes2.getMessage().toLowerCase().contains("mismatch"));

        // Non-positive admin ID
        assertFalse("Negative adminId rejected in verifyRecruiter",
                service.verifyRecruiter(-1, 10, "Test").isSuccess());
        assertFalse("Zero adminId rejected in verifyRecruiter",
                service.verifyRecruiter(0, 10, "Test").isSuccess());
        assertFalse("Negative adminId rejected in approveJob",
                service.approveJob(-1, 20, "Test").isSuccess());
        assertFalse("Zero adminId rejected in approveJob",
                service.approveJob(0, 20, "Test").isSuccess());

        // Null moderation status
        assertFalse("Null status rejected in moderateRecruiter",
                service.moderateRecruiter(99, 10, null, "Test").isSuccess());
        assertFalse("Null status rejected in moderateJob",
                service.moderateJob(99, 20, null, "Test").isSuccess());

        // Transition to UNVERIFIED rejected
        assertFalse("Transition to UNVERIFIED rejected in moderateRecruiter",
                service.moderateRecruiter(99, 10, RecruiterVerificationStatus.UNVERIFIED, "Test").isSuccess());

        session.logout();
    }

    // =========================================================================
    // SECTION 2: Live Database Suite (GAP-04 Part 2 Moderation & Audit)
    // =========================================================================

    private void runLiveDatabaseSuite() {
        System.out.println("\n--- [2] Running Live Database Moderation & Audit Suite ---");

        SessionManager session = SessionManager.getInstance();
        AdminModerationService service = new AdminModerationService();

        BaselineCounts baseline = readCounts();
        System.out.printf("  [INFO] Initial Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d%n",
                baseline.users, baseline.recruiterProfiles, baseline.jobs, baseline.auditLogs);

        int testAdmin1Id = -1;
        int testAdmin2Id = -1;
        int testRecruiter1Id = -1;
        int testRecruiter2Id = -1;
        int testJob1Id = -1;
        int testJob2Id = -1;

        try {
            // -----------------------------------------------------------------
            // Setup Test Fixtures
            // -----------------------------------------------------------------
            testAdmin1Id = createTestUser("SVC4_2C1_Admin1", "svc4_admin1@hirehub.local", User.Role.ADMIN, true);
            testAdmin2Id = createTestUser("SVC4_2C1_Admin2", "svc4_admin2@hirehub.local", User.Role.ADMIN, true);
            testRecruiter1Id = createTestUser("SVC4_2C1_Recruiter1", "svc4_rec1@hirehub.local", User.Role.RECRUITER, true);
            testRecruiter2Id = createTestUser("SVC4_2C1_Recruiter2", "svc4_rec2@hirehub.local", User.Role.RECRUITER, true);

            assertTrue("Created test Admin 1", testAdmin1Id > 0);
            assertTrue("Created test Admin 2", testAdmin2Id > 0);
            assertTrue("Created test Recruiter 1", testRecruiter1Id > 0);
            assertTrue("Created test Recruiter 2", testRecruiter2Id > 0);

            createRecruiterProfile(testRecruiter1Id, "SVC4_2C1_Corp1", RecruiterVerificationStatus.PENDING);
            createRecruiterProfile(testRecruiter2Id, "SVC4_2C1_Corp2", RecruiterVerificationStatus.UNVERIFIED);

            testJob1Id = createJob(testRecruiter1Id, "SVC4_2C1_Job1_Active", Job.Status.ACTIVE, JobModerationStatus.PENDING);
            testJob2Id = createJob(testRecruiter1Id, "SVC4_2C1_Job2_Draft", Job.Status.DRAFT, JobModerationStatus.PENDING);

            assertTrue("Created test Job 1 (ACTIVE lifecycle, PENDING moderation)", testJob1Id > 0);
            assertTrue("Created test Job 2 (DRAFT lifecycle, PENDING moderation)", testJob2Id > 0);

            // -----------------------------------------------------------------
            // Test 1: Stale Session Protection (Deactivated Admin in DB)
            // -----------------------------------------------------------------
            System.out.println("  -> Testing Stale Session Protection...");
            // Login Admin 2 with isActive = true in session
            User admin2Session = new User(testAdmin2Id, "SVC4_2C1_Admin2", "svc4_admin2@hirehub.local",
                    "hash", User.Role.ADMIN, true, LocalDateTime.now());
            session.login(admin2Session);

            // Deactivate Admin 2 in DB behind the scenes
            setDbUserActive(testAdmin2Id, false);

            AdminModerationService.ServiceResult<Void> staleResult =
                    service.verifyRecruiter(testAdmin2Id, testRecruiter1Id, "Stale session verification attempt");
            assertFalse("Stale session (deactivated in DB) is blocked from verifyRecruiter", staleResult.isSuccess());
            assertTrue("Error message indicates administrator account is deactivated",
                    staleResult.getMessage().toLowerCase().contains("deactivated"));

            // Ensure no audit log was created by the blocked attempt
            assertEquals("No audit log written on stale session rejection",
                    0, countAuditLogsByAdmin(testAdmin2Id));

            session.logout();

            // Re-activate Admin 2 for clean cleanup
            setDbUserActive(testAdmin2Id, true);

            // -----------------------------------------------------------------
            // Login as verified active Admin 1
            // -----------------------------------------------------------------
            User admin1Session = new User(testAdmin1Id, "SVC4_2C1_Admin1", "svc4_admin1@hirehub.local",
                    "hash", User.Role.ADMIN, true, LocalDateTime.now());
            session.login(admin1Session);

            // -----------------------------------------------------------------
            // Test 2: Input & Reason Validation on Live Recruiter Operations
            // -----------------------------------------------------------------
            System.out.println("  -> Testing Recruiter Moderation Validation & Operations...");

            // Non-positive recruiter ID
            assertFalse("Non-positive recruiterId rejected in verifyRecruiter",
                    service.verifyRecruiter(testAdmin1Id, -5, "Reason").isSuccess());
            assertFalse("Zero recruiterId rejected in verifyRecruiter",
                    service.verifyRecruiter(testAdmin1Id, 0, "Reason").isSuccess());

            // Nonexistent recruiter ID
            AdminModerationService.ServiceResult<Void> missingRecRes =
                    service.verifyRecruiter(testAdmin1Id, 999999, "Reason");
            assertFalse("Nonexistent recruiter ID rejected in verifyRecruiter", missingRecRes.isSuccess());
            assertTrue("Message indicates recruiter profile not found",
                    missingRecRes.getMessage().toLowerCase().contains("not found"));

            // Reject without reason
            AdminModerationService.ServiceResult<Void> rejNoReason =
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, null);
            assertFalse("Reject recruiter without reason is rejected", rejNoReason.isSuccess());
            assertTrue("Message indicates reason required", rejNoReason.getMessage().toLowerCase().contains("reason"));

            // Reject with blank/too short reason
            assertFalse("Reject recruiter with whitespace reason is rejected",
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, "   ").isSuccess());
            assertFalse("Reject recruiter with 2-char reason is rejected",
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, "no").isSuccess());

            // Suspend without reason
            AdminModerationService.ServiceResult<Void> suspNoReason =
                    service.suspendRecruiter(testAdmin1Id, testRecruiter1Id, null);
            assertFalse("Suspend recruiter without reason is rejected", suspNoReason.isSuccess());
            assertTrue("Message indicates reason required", suspNoReason.getMessage().toLowerCase().contains("reason"));

            // Overly long reason (> 500 characters)
            String longReason = "A".repeat(501);
            assertFalse("Reason exceeding 500 chars rejected in verifyRecruiter",
                    service.verifyRecruiter(testAdmin1Id, testRecruiter1Id, longReason).isSuccess());
            assertFalse("Reason exceeding 500 chars rejected in rejectRecruiter",
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, longReason).isSuccess());

            // -----------------------------------------------------------------
            // Test 3: Successful Recruiter Verification Lifecycle
            // -----------------------------------------------------------------
            // Verify Recruiter 1 (starts PENDING -> VERIFIED)
            AdminModerationService.ServiceResult<Void> verifyRes =
                    service.verifyRecruiter(testAdmin1Id, testRecruiter1Id, "Verified legitimate organization credentials");
            assertTrue("verifyRecruiter succeeded", verifyRes.isSuccess());

            // Assert database status
            assertEquals("Recruiter 1 status is VERIFIED in DB",
                    RecruiterVerificationStatus.VERIFIED, getRecruiterStatus(testRecruiter1Id));
            assertEquals("Recruiter 1 verified_by matches Admin 1 in DB",
                    Integer.valueOf(testAdmin1Id), getRecruiterVerifiedBy(testRecruiter1Id));

            // Assert exactly 1 audit log created
            List<AdminAuditLog> rec1Logs = service.getModerationLogsForRecruiter(testAdmin1Id, testRecruiter1Id).getData();
            assertEquals("Exactly 1 audit log created for Recruiter 1", 1, rec1Logs.size());
            assertEquals("Audit log entity_type is RECRUITER_PROFILE",
                    AdminAuditEntityType.RECRUITER_PROFILE, rec1Logs.get(0).getEntityType());
            assertEquals("Audit log admin_id matches Admin 1", testAdmin1Id, rec1Logs.get(0).getAdminId());
            assertEquals("Audit log action is RECRUITER_VERIFIED", "RECRUITER_VERIFIED", rec1Logs.get(0).getAction());
            assertTrue("Audit log reason contains expected text",
                    rec1Logs.get(0).getReason().contains("legitimate organization credentials"));

            // Repeated verify (idempotent / already verified guard)
            AdminModerationService.ServiceResult<Void> doubleVerifyRes =
                    service.verifyRecruiter(testAdmin1Id, testRecruiter1Id, "Verifying again");
            assertFalse("verifyRecruiter on already verified recruiter is rejected", doubleVerifyRes.isSuccess());
            assertTrue("Message indicates already verified",
                    doubleVerifyRes.getMessage().toLowerCase().contains("already verified"));
            assertEquals("Audit log count unchanged after repeated verify",
                    1, service.getModerationLogsForRecruiter(testAdmin1Id, testRecruiter1Id).getData().size());

            // Suspend Recruiter 1 (VERIFIED -> SUSPENDED)
            AdminModerationService.ServiceResult<Void> suspRes =
                    service.suspendRecruiter(testAdmin1Id, testRecruiter1Id, "Suspended due to compliance inquiry");
            assertTrue("suspendRecruiter succeeded", suspRes.isSuccess());
            assertEquals("Recruiter 1 status is SUSPENDED in DB",
                    RecruiterVerificationStatus.SUSPENDED, getRecruiterStatus(testRecruiter1Id));

            // Repeated suspend guard
            assertFalse("suspendRecruiter on already suspended recruiter is rejected",
                    service.suspendRecruiter(testAdmin1Id, testRecruiter1Id, "Suspending again").isSuccess());

            // Return to PENDING review (SUSPENDED -> PENDING)
            AdminModerationService.ServiceResult<Void> returnPendingRes =
                    service.returnRecruiterToPending(testAdmin1Id, testRecruiter1Id, "Reopening review on dispute");
            assertTrue("returnRecruiterToPending succeeded", returnPendingRes.isSuccess());
            assertEquals("Recruiter 1 status is PENDING in DB",
                    RecruiterVerificationStatus.PENDING, getRecruiterStatus(testRecruiter1Id));

            // Repeated return to pending guard
            assertFalse("returnRecruiterToPending on already pending recruiter is rejected",
                    service.returnRecruiterToPending(testAdmin1Id, testRecruiter1Id, "Pending again").isSuccess());

            // Reject Recruiter 1 (PENDING -> REJECTED)
            AdminModerationService.ServiceResult<Void> rejectRes =
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, "Failed domain identity validation");
            assertTrue("rejectRecruiter succeeded", rejectRes.isSuccess());
            assertEquals("Recruiter 1 status is REJECTED in DB",
                    RecruiterVerificationStatus.REJECTED, getRecruiterStatus(testRecruiter1Id));

            // Repeated reject guard
            assertFalse("rejectRecruiter on already rejected recruiter is rejected",
                    service.rejectRecruiter(testAdmin1Id, testRecruiter1Id, "Rejecting again").isSuccess());

            // Total audit logs for Recruiter 1: VERIFIED + SUSPENDED + PENDING + REJECTED = 4
            assertEquals("Total audit logs for Recruiter 1 is exactly 4",
                    4, service.getModerationLogsForRecruiter(testAdmin1Id, testRecruiter1Id).getData().size());

            // -----------------------------------------------------------------
            // Test 4: Job Moderation Operations, Lifecycle & Verification Independence
            // -----------------------------------------------------------------
            System.out.println("  -> Testing Job Moderation & Independence Protections...");

            // Non-positive job ID
            assertFalse("Non-positive jobId rejected in approveJob",
                    service.approveJob(testAdmin1Id, -1, "Test").isSuccess());
            assertFalse("Zero jobId rejected in approveJob",
                    service.approveJob(testAdmin1Id, 0, "Test").isSuccess());

            // Nonexistent job ID
            AdminModerationService.ServiceResult<Void> missingJobRes =
                    service.approveJob(testAdmin1Id, 999999, "Test");
            assertFalse("Nonexistent job ID rejected in approveJob", missingJobRes.isSuccess());
            assertTrue("Message indicates job not found",
                    missingJobRes.getMessage().toLowerCase().contains("not found"));

            // Reject job without reason
            AdminModerationService.ServiceResult<Void> jobRejNoReason =
                    service.rejectJob(testAdmin1Id, testJob1Id, null);
            assertFalse("Reject job without reason is rejected", jobRejNoReason.isSuccess());
            assertTrue("Message indicates reason required", jobRejNoReason.getMessage().toLowerCase().contains("reason"));

            // Suspend job without reason
            AdminModerationService.ServiceResult<Void> jobSuspNoReason =
                    service.suspendJob(testAdmin1Id, testJob1Id, null);
            assertFalse("Suspend job without reason is rejected", jobSuspNoReason.isSuccess());
            assertTrue("Message indicates reason required", jobSuspNoReason.getMessage().toLowerCase().contains("reason"));

            // Approve Job 2 (Job 2 was created as DRAFT lifecycle, PENDING moderation)
            AdminModerationService.ServiceResult<Void> approveJobRes =
                    service.approveJob(testAdmin1Id, testJob2Id, "Approved verified role posting");
            assertTrue("approveJob succeeded on Job 2", approveJobRes.isSuccess());

            // CRITICAL ARCHITECTURAL ASSERTION: Lifecycle status MUST NOT change on moderation approval!
            assertEquals("Job 2 moderation_status is APPROVED in DB",
                    JobModerationStatus.APPROVED, getJobModerationStatus(testJob2Id));
            assertEquals("Job 2 lifecycle status remains DRAFT in DB (unaffected by moderation)",
                    Job.Status.DRAFT, getJobLifecycleStatus(testJob2Id));

            // CRITICAL ARCHITECTURAL ASSERTION: Recruiter verification MUST NOT change on job approval!
            assertEquals("Recruiter 1 verification status remains REJECTED (unaffected by job moderation)",
                    RecruiterVerificationStatus.REJECTED, getRecruiterStatus(testRecruiter1Id));

            // Assert exactly 1 audit log created for Job 2
            List<AdminAuditLog> job2Logs = service.getModerationLogsForJob(testAdmin1Id, testJob2Id).getData();
            assertEquals("Exactly 1 audit log created for Job 2", 1, job2Logs.size());
            assertEquals("Audit log entity_type is JOB", AdminAuditEntityType.JOB, job2Logs.get(0).getEntityType());
            assertEquals("Audit log target_job_id matches Job 2", Integer.valueOf(testJob2Id), job2Logs.get(0).getTargetJobId());
            assertEquals("Audit log target_user_id matches owning recruiter", Integer.valueOf(testRecruiter1Id), job2Logs.get(0).getTargetUserId());
            assertEquals("Audit log action is JOB_APPROVED", "JOB_APPROVED", job2Logs.get(0).getAction());

            // Repeated approve guard
            assertFalse("approveJob on already approved job is rejected",
                    service.approveJob(testAdmin1Id, testJob2Id, "Approving again").isSuccess());

            // Suspend Job 2 (APPROVED -> SUSPENDED)
            AdminModerationService.ServiceResult<Void> suspJobRes =
                    service.suspendJob(testAdmin1Id, testJob2Id, "Suspended due to report of invalid compensation");
            assertTrue("suspendJob succeeded on Job 2", suspJobRes.isSuccess());
            assertEquals("Job 2 moderation_status is SUSPENDED in DB",
                    JobModerationStatus.SUSPENDED, getJobModerationStatus(testJob2Id));
            assertEquals("Job 2 lifecycle status remains DRAFT in DB",
                    Job.Status.DRAFT, getJobLifecycleStatus(testJob2Id));

            // Repeated suspend guard
            assertFalse("suspendJob on already suspended job is rejected",
                    service.suspendJob(testAdmin1Id, testJob2Id, "Suspending again").isSuccess());

            // Return Job 2 to PENDING (SUSPENDED -> PENDING)
            AdminModerationService.ServiceResult<Void> returnJobPendingRes =
                    service.returnJobToPending(testAdmin1Id, testJob2Id, "Reopening job review upon revision");
            assertTrue("returnJobToPending succeeded on Job 2", returnJobPendingRes.isSuccess());
            assertEquals("Job 2 moderation_status is PENDING in DB",
                    JobModerationStatus.PENDING, getJobModerationStatus(testJob2Id));

            // Reject Job 1 (Job 1 has ACTIVE lifecycle, PENDING moderation)
            AdminModerationService.ServiceResult<Void> rejectJob1Res =
                    service.rejectJob(testAdmin1Id, testJob1Id, "Contains misleading role qualifications");
            assertTrue("rejectJob succeeded on Job 1", rejectJob1Res.isSuccess());
            assertEquals("Job 1 moderation_status is REJECTED in DB",
                    JobModerationStatus.REJECTED, getJobModerationStatus(testJob1Id));
            assertEquals("Job 1 lifecycle status remains ACTIVE in DB (unaffected by rejection)",
                    Job.Status.ACTIVE, getJobLifecycleStatus(testJob1Id));

            // Repeated reject guard
            assertFalse("rejectJob on already rejected job is rejected",
                    service.rejectJob(testAdmin1Id, testJob1Id, "Rejecting again").isSuccess());

            // -----------------------------------------------------------------
            // Test 5: Moderation Data Retrieval APIs
            // -----------------------------------------------------------------
            System.out.println("  -> Testing Moderation Data Queries & Pagination...");

            // List recruiters for moderation
            AdminModerationService.ServiceResult<List<RecruiterProfile>> recListRes =
                    service.listRecruitersForModeration("SVC4_2C1", null, 10, 0);
            assertTrue("listRecruitersForModeration succeeded", recListRes.isSuccess());
            assertEquals("listRecruitersForModeration found 2 test recruiters", 2, recListRes.getData().size());

            // Filter by status REJECTED (should find Recruiter 1)
            AdminModerationService.ServiceResult<List<RecruiterProfile>> recFiltRes =
                    service.listRecruitersForModeration("SVC4_2C1", RecruiterVerificationStatus.REJECTED, 10, 0);
            assertTrue("listRecruitersForModeration with status filter succeeded", recFiltRes.isSuccess());
            assertEquals("Status filter finds exactly 1 rejected recruiter", 1, recFiltRes.getData().size());
            assertEquals("Found Recruiter 1 user ID", testRecruiter1Id, recFiltRes.getData().get(0).getUserId());

            // Count recruiters for moderation
            AdminModerationService.ServiceResult<Integer> countRecRes =
                    service.countRecruitersForModeration("SVC4_2C1", null);
            assertTrue("countRecruitersForModeration succeeded", countRecRes.isSuccess());
            assertEquals("countRecruitersForModeration matches 2", Integer.valueOf(2), countRecRes.getData());

            // Get single recruiter profile
            AdminModerationService.ServiceResult<RecruiterProfile> singleRecRes =
                    service.getRecruiterProfileByUserId(testRecruiter1Id);
            assertTrue("getRecruiterProfileByUserId succeeded", singleRecRes.isSuccess());
            assertEquals("Profile company matches", "SVC4_2C1_Corp1", singleRecRes.getData().getCompanyName());

            // List jobs for moderation
            AdminModerationService.ServiceResult<List<Job>> jobListRes =
                    service.listJobsForModeration("SVC4_2C1", null, null, 10, 0);
            assertTrue("listJobsForModeration succeeded", jobListRes.isSuccess());
            assertEquals("listJobsForModeration found 2 test jobs", 2, jobListRes.getData().size());

            // Filter jobs by moderation status REJECTED (Job 1)
            AdminModerationService.ServiceResult<List<Job>> jobFiltRes =
                    service.listJobsForModeration("SVC4_2C1", null, JobModerationStatus.REJECTED, 10, 0);
            assertTrue("listJobsForModeration with moderation filter succeeded", jobFiltRes.isSuccess());
            assertEquals("Filter finds exactly 1 rejected job", 1, jobFiltRes.getData().size());
            assertEquals("Found Job 1 ID", testJob1Id, jobFiltRes.getData().get(0).getId());

            // Count jobs for moderation
            AdminModerationService.ServiceResult<Integer> countJobRes =
                    service.countJobsForModeration("SVC4_2C1", null, null);
            assertTrue("countJobsForModeration succeeded", countJobRes.isSuccess());
            assertEquals("countJobsForModeration matches 2", Integer.valueOf(2), countJobRes.getData());

            // Get single job
            AdminModerationService.ServiceResult<Job> singleJobRes = service.getJobById(testJob1Id);
            assertTrue("getJobById succeeded", singleJobRes.isSuccess());
            assertEquals("Job 1 title matches", "SVC4_2C1_Job1_Active", singleJobRes.getData().getTitle());

            // Recent moderation logs
            AdminModerationService.ServiceResult<List<AdminAuditLog>> recentLogsRes =
                    service.getRecentModerationLogs(20);
            assertTrue("getRecentModerationLogs succeeded", recentLogsRes.isSuccess());
            assertTrue("Recent logs contain recorded moderation actions", recentLogsRes.getData().size() >= 5);

            session.logout();

        } finally {
            // -----------------------------------------------------------------
            // Fixture Teardown & Baseline Verification
            // -----------------------------------------------------------------
            System.out.println("  -> Cleaning up test fixtures and restoring database baseline...");

            if (testAdmin1Id > 0) deleteAuditLogsForAdmin(testAdmin1Id);
            if (testAdmin2Id > 0) deleteAuditLogsForAdmin(testAdmin2Id);

            if (testJob1Id > 0) deleteJob(testJob1Id);
            if (testJob2Id > 0) deleteJob(testJob2Id);

            if (testRecruiter1Id > 0) deleteRecruiterProfile(testRecruiter1Id);
            if (testRecruiter2Id > 0) deleteRecruiterProfile(testRecruiter2Id);

            if (testAdmin1Id > 0) deleteUser(testAdmin1Id);
            if (testAdmin2Id > 0) deleteUser(testAdmin2Id);
            if (testRecruiter1Id > 0) deleteUser(testRecruiter1Id);
            if (testRecruiter2Id > 0) deleteUser(testRecruiter2Id);

            BaselineCounts after = readCounts();
            System.out.printf("  [INFO] After-teardown counts: users=%d (exp %d), recruiter_profiles=%d (exp %d), jobs=%d (exp %d), audit_logs=%d (exp %d)%n",
                    after.users, baseline.users, after.recruiterProfiles, baseline.recruiterProfiles,
                    after.jobs, baseline.jobs, after.auditLogs, baseline.auditLogs);

            assertEquals("Database users restored to exact baseline", baseline.users, after.users);
            assertEquals("Database recruiter_profiles restored to exact baseline", baseline.recruiterProfiles, after.recruiterProfiles);
            assertEquals("Database jobs restored to exact baseline", baseline.jobs, after.jobs);
            assertEquals("Database admin_audit_logs restored to exact baseline", baseline.auditLogs, after.auditLogs);
            assertEquals("Database applications row count restored", baseline.applications, after.applications);
            assertEquals("Database resumes row count restored", baseline.resumes, after.resumes);
            assertEquals("Database resume_analyses row count restored", baseline.resumeAnalyses, after.resumeAnalyses);
            assertEquals("Database student_profiles row count restored", baseline.studentProfiles, after.studentProfiles);
        }
    }

    // =========================================================================
    // DATABASE HELPERS
    // =========================================================================

    private static class BaselineCounts {
        int users;
        int recruiterProfiles;
        int jobs;
        int auditLogs;
        int applications;
        int resumes;
        int resumeAnalyses;
        int studentProfiles;
    }

    private BaselineCounts readCounts() {
        BaselineCounts c = new BaselineCounts();
        c.users = queryCount("SELECT COUNT(*) FROM users");
        c.recruiterProfiles = queryCount("SELECT COUNT(*) FROM recruiter_profiles");
        c.jobs = queryCount("SELECT COUNT(*) FROM jobs");
        c.auditLogs = queryCount("SELECT COUNT(*) FROM admin_audit_logs");
        c.applications = queryCount("SELECT COUNT(*) FROM applications");
        c.resumes = queryCount("SELECT COUNT(*) FROM resumes");
        c.resumeAnalyses = queryCount("SELECT COUNT(*) FROM resume_analyses");
        c.studentProfiles = queryCount("SELECT COUNT(*) FROM student_profiles");
        return c;
    }

    private int queryCount(String sql) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error executing count query: " + sql, e);
        }
        return -1;
    }

    private int createTestUser(String fullName, String email, User.Role role, boolean isActive) {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active, created_at) " +
                     "VALUES (?, ?, 'dummy_hash', ?, ?, NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, fullName);
            stmt.setString(2, email);
            stmt.setString(3, role.name());
            stmt.setBoolean(4, isActive);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating test user: " + email, e);
        }
        return -1;
    }

    private void createRecruiterProfile(int userId, String companyName, RecruiterVerificationStatus status) {
        String sql = "INSERT INTO recruiter_profiles (user_id, company_name, verification_status) " +
                     "VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, companyName);
            stmt.setString(3, status.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating recruiter profile for user: " + userId, e);
        }
    }

    private int createJob(int recruiterId, String title, Job.Status status, JobModerationStatus modStatus) {
        String sql = "INSERT INTO jobs (recruiter_id, title, company_name, description, required_skills, " +
                     "location, job_type, status, moderation_status, created_at) " +
                     "VALUES (?, ?, 'Corp Test', 'Desc', 'Java', 'Remote', 'FULL_TIME', ?, ?, NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, recruiterId);
            stmt.setString(2, title);
            stmt.setString(3, status.name());
            stmt.setString(4, modStatus.name());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating test job: " + title, e);
        }
        return -1;
    }

    private void setDbUserActive(int userId, boolean active) {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error setting user active status: " + userId, e);
        }
    }

    private RecruiterVerificationStatus getRecruiterStatus(int userId) {
        String sql = "SELECT verification_status FROM recruiter_profiles WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return RecruiterVerificationStatus.fromString(rs.getString("verification_status"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading recruiter verification status: " + userId, e);
        }
        return null;
    }

    private Integer getRecruiterVerifiedBy(int userId) {
        String sql = "SELECT verified_by FROM recruiter_profiles WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt("verified_by");
                    return rs.wasNull() ? null : val;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading recruiter verified_by: " + userId, e);
        }
        return null;
    }

    private JobModerationStatus getJobModerationStatus(int jobId) {
        String sql = "SELECT moderation_status FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return JobModerationStatus.fromString(rs.getString("moderation_status"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading job moderation status: " + jobId, e);
        }
        return null;
    }

    private Job.Status getJobLifecycleStatus(int jobId) {
        String sql = "SELECT status FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Job.Status.valueOf(rs.getString("status"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading job lifecycle status: " + jobId, e);
        }
        return null;
    }

    private int countAuditLogsByAdmin(int adminId) {
        return queryCount("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id = " + adminId);
    }

    private void deleteAuditLogsForAdmin(int adminId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM admin_audit_logs WHERE admin_id = ?")) {
            stmt.setInt(1, adminId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting audit logs for admin: " + adminId, e);
        }
    }

    private void deleteJob(int jobId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM jobs WHERE id = ?")) {
            stmt.setInt(1, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting job: " + jobId, e);
        }
    }

    private void deleteRecruiterProfile(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM recruiter_profiles WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting recruiter profile: " + userId, e);
        }
    }

    private void deleteUser(int userId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting user: " + userId, e);
        }
    }

    // =========================================================================
    // ASSERTION HELPERS
    // =========================================================================

    private void checkMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            if (m != null) {
                passed++;
                System.out.println("  [PASS] Method " + clazz.getSimpleName() + "." + methodName + " exists with matching signature");
            }
        } catch (NoSuchMethodException e) {
            failed++;
            System.err.println("  [FAIL] Missing method: " + clazz.getSimpleName() + "." + methodName);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + message);
        } else {
            failed++;
            System.err.println("  [FAIL] " + message);
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) {
            passed++;
            System.out.println("  [PASS] " + message);
            return;
        }
        if (expected != null && expected.equals(actual)) {
            passed++;
            System.out.println("  [PASS] " + message + " (value=" + actual + ")");
        } else {
            failed++;
            System.err.println("  [FAIL] " + message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
