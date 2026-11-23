package com.hirehub.moderation;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.JobDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterService;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RecruiterJobModerationTest — automated verification suite for
 * Phase 6 Step 4.2C.2: RecruiterService & JobService Moderation Enforcement and Publishing Security.
 *
 * <p>Validates:
 * <ul>
 *   <li>Offline architecture, reflection, and fail-closed session authorization.</li>
 *   <li>Role enforcement: unauthenticated, student, and admin callers fail safe on recruiter job operations.</li>
 *   <li>Parameter validation: recruiter identity mismatch and non-positive IDs fail safe.</li>
 *   <li>Live database stale-session protection (deactivated recruiter in DB blocked even with active session).</li>
 *   <li>Profile existence guard (active recruiter user without recruiter_profiles row blocked).</li>
 *   <li>Recruiter verification lifecycle: only VERIFIED recruiters may create, edit, or publish jobs;
 *       UNVERIFIED, PENDING, REJECTED, SUSPENDED recruiters are strictly blocked.</li>
 *   <li>Job moderation lifecycle: newly created jobs strictly receive PENDING moderation status;
 *       only APPROVED jobs can be activated/published; PENDING, REJECTED, and SUSPENDED jobs cannot be activated.</li>
 *   <li>Reactivation protection: REJECTED and SUSPENDED jobs cannot be reactivated or reopened.</li>
 *   <li>Moderation metadata immutability: recruiter updates cannot alter moderation_status, moderation_reason,
 *       moderated_by, or moderated_at.</li>
 *   <li>Recruiter profile protection: recruiter updates cannot alter verification_status, verified_by, etc.</li>
 *   <li>Tenant isolation: cross-recruiter view, edit, activate, close, and delete operations are strictly blocked.</li>
 *   <li>Student visibility regression: queries enforce active recruiter, verified profile, approved moderation,
 *       active status, and unexpired deadline.</li>
 *   <li>100% database fixture teardown with exact restoration of baseline row counts.</li>
 * </ul>
 * </p>
 */
public class RecruiterJobModerationTest {

    private static int offlinePassed = 0;
    private static int offlineFailed = 0;
    private static int offlineSkipped = 0;

    private static int livePassed = 0;
    private static int liveFailed = 0;
    private static int liveSkipped = 0;

    private static boolean inLiveSuite = false;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2C.2: Recruiter & Job Moderation Verifier");
        System.out.println("=================================================================");

        RecruiterJobModerationTest verifier = new RecruiterJobModerationTest();

        try {
            inLiveSuite = false;
            verifier.runOfflineArchitectureSuite();

            inLiveSuite = true;
            verifier.runLiveDatabaseSuite();
        } catch (Exception e) {
            System.err.println("[FATAL ERROR] Unexpected exception during verifier execution:");
            e.printStackTrace();
            if (inLiveSuite) {
                liveFailed++;
            } else {
                offlineFailed++;
            }
        } finally {
            SessionManager.getInstance().logout();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("OFFLINE TESTS: %d PASSED, %d FAILED, %d SKIPPED%n",
                offlinePassed, offlineFailed, offlineSkipped);
        System.out.printf("LIVE DB TESTS: %d PASSED, %d FAILED, %d SKIPPED%n",
                livePassed, liveFailed, liveSkipped);
        System.out.printf("TOTAL SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                offlinePassed + livePassed, offlineFailed + liveFailed, offlineSkipped + liveSkipped);
        System.out.println("=================================================================");

        if (offlineFailed > 0 || liveFailed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Architecture, Reflection & Session Guards
    // =========================================================================

    private void runOfflineArchitectureSuite() {
        System.out.println("\n--- [1] Running Offline Architecture, Reflection & Authorization Suite ---");

        // 1. Reflection check on JobService methods
        checkMethod(JobService.class, "createJob", int.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, String.class, LocalDate.class, boolean.class);
        checkMethod(JobService.class, "updateJob", int.class, int.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, String.class, LocalDate.class, Job.Status.class);
        checkMethod(JobService.class, "updateJobForRecruiter", Job.class, int.class);
        checkMethod(JobService.class, "activateJob", int.class, int.class);
        checkMethod(JobService.class, "closeJob", int.class, int.class);
        checkMethod(JobService.class, "updateJobStatusForRecruiter", int.class, int.class, Job.Status.class);
        checkMethod(JobService.class, "deleteJob", int.class, int.class);
        checkMethod(JobService.class, "deleteJobForRecruiter", int.class, int.class);
        checkMethod(JobService.class, "getJobById", int.class);
        checkMethod(JobService.class, "getJobByIdForRecruiter", int.class, int.class);
        checkMethod(JobService.class, "getJobsByRecruiter", int.class);
        checkMethod(JobService.class, "searchJobs", String.class, String.class, String.class);
        checkMethod(JobService.class, "getActiveJobCount", int.class);

        // 2. Reflection check on RecruiterService methods
        checkMethod(RecruiterService.class, "saveProfile", int.class, String.class, String.class, String.class, String.class);
        checkMethod(RecruiterService.class, "saveProfileForAdmin", int.class, String.class, String.class, String.class, String.class);
        checkMethod(RecruiterService.class, "getProfile", int.class);
        checkMethod(RecruiterService.class, "getProfileForAdmin", int.class);

        // 3. Fail-Closed Authentication Guards (Logged Out)
        SessionManager session = SessionManager.getInstance();
        session.logout();

        JobService jobService = new JobService();
        RecruiterService recruiterService = new RecruiterService();

        Job sampleJob = new Job(201, "Test Engineer", "Tech Corp", "Desc", "Java", "Remote",
                Job.JobType.FULL_TIME, "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT);
        sampleJob.setId(501);

        assertFalse("Unauthenticated caller blocked from createJob(params)",
                jobService.createJob(201, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Unauthenticated caller blocked from updateJob(params)",
                jobService.updateJob(201, 501, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());
        assertFalse("Unauthenticated caller blocked from updateJobForRecruiter(Job, id)",
                jobService.updateJobForRecruiter(sampleJob, 201).isSuccess());
        assertFalse("Unauthenticated caller blocked from activateJob(recruiterId, jobId)",
                jobService.activateJob(201, 501).isSuccess());
        assertFalse("Unauthenticated caller blocked from closeJob(recruiterId, jobId)",
                jobService.closeJob(201, 501).isSuccess());
        assertFalse("Unauthenticated caller blocked from updateJobStatusForRecruiter(..., ACTIVE)",
                jobService.updateJobStatusForRecruiter(501, 201, Job.Status.ACTIVE).isSuccess());
        assertFalse("Unauthenticated caller blocked from deleteJob(recruiterId, jobId)",
                jobService.deleteJob(201, 501).isSuccess());
        assertFalse("Unauthenticated caller blocked from deleteJobForRecruiter(jobId, recruiterId)",
                jobService.deleteJobForRecruiter(501, 201).isSuccess());
        assertFalse("Unauthenticated caller blocked from getJobByIdForRecruiter",
                jobService.getJobByIdForRecruiter(501, 201).isPresent());
        assertTrue("Unauthenticated caller returns empty list from getJobsByRecruiter",
                jobService.getJobsByRecruiter(201).isEmpty());

        assertFalse("Unauthenticated caller blocked from recruiterService.saveProfile",
                recruiterService.saveProfile(201, "Tech Corp", "https://tech.local", "Desc", "IT").isSuccess());
        assertFalse("Unauthenticated caller blocked from recruiterService.getProfile",
                recruiterService.getProfile(201).isPresent());
        assertFalse("Unauthenticated caller blocked from recruiterService.saveProfileForAdmin",
                recruiterService.saveProfileForAdmin(201, "Tech Corp", "https://tech.local", "Desc", "IT").isSuccess());
        assertFalse("Unauthenticated caller blocked from recruiterService.getProfileForAdmin",
                recruiterService.getProfileForAdmin(201).isPresent());

        // 4. Role Guards — Student Caller
        User studentUser = new User(101, "Student Mock", "student@test.local", "hash", User.Role.STUDENT, true, LocalDateTime.now());
        session.login(studentUser);

        assertFalse("Student caller blocked from jobService.createJob(params)",
                jobService.createJob(101, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Student caller blocked from jobService.updateJob(params)",
                jobService.updateJob(101, 501, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());
        assertFalse("Student caller blocked from jobService.updateJobForRecruiter",
                jobService.updateJobForRecruiter(sampleJob, 101).isSuccess());
        assertFalse("Student caller blocked from jobService.activateJob",
                jobService.activateJob(101, 501).isSuccess());
        assertFalse("Student caller blocked from jobService.closeJob",
                jobService.closeJob(101, 501).isSuccess());
        assertFalse("Student caller blocked from jobService.deleteJob",
                jobService.deleteJob(101, 501).isSuccess());
        assertFalse("Student caller blocked from recruiterService.saveProfile",
                recruiterService.saveProfile(101, "Tech Corp", "https://tech.local", "Desc", "IT").isSuccess());
        assertFalse("Student caller blocked from recruiterService.getProfile",
                recruiterService.getProfile(101).isPresent());
        assertFalse("Student caller blocked from recruiterService.saveProfileForAdmin",
                recruiterService.saveProfileForAdmin(101, "Tech Corp", "https://tech.local", "Desc", "IT").isSuccess());

        // 5. Role Guards — Admin Caller
        User adminUser = new User(301, "Admin Mock", "admin@test.local", "hash", User.Role.ADMIN, true, LocalDateTime.now());
        session.login(adminUser);

        assertFalse("Admin caller blocked from jobService.createJob(params) (recruiter-only operation)",
                jobService.createJob(301, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Admin caller blocked from jobService.updateJob(params)",
                jobService.updateJob(301, 501, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());
        assertFalse("Admin caller blocked from jobService.updateJobForRecruiter",
                jobService.updateJobForRecruiter(sampleJob, 301).isSuccess());
        assertFalse("Admin caller blocked from jobService.activateJob",
                jobService.activateJob(301, 501).isSuccess());
        assertFalse("Admin caller blocked from jobService.closeJob",
                jobService.closeJob(301, 501).isSuccess());
        assertFalse("Admin caller blocked from jobService.deleteJob",
                jobService.deleteJob(301, 501).isSuccess());
        assertFalse("Admin caller blocked from recruiterService.saveProfile (requires RECRUITER role)",
                recruiterService.saveProfile(301, "Tech Corp", "https://tech.local", "Desc", "IT").isSuccess());
        assertFalse("Admin caller blocked from recruiterService.getProfile (requires RECRUITER role)",
                recruiterService.getProfile(301).isPresent());

        // 6. Recruiter Identity Mismatch & Non-Positive ID Guards
        User recruiterSession = new User(201, "Recruiter Session", "rec201@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now());
        session.login(recruiterSession);

        // Mismatched recruiter ID
        assertFalse("Recruiter ID mismatch blocked in createJob(params)",
                jobService.createJob(202, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in updateJob(params)",
                jobService.updateJob(202, 501, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in updateJobForRecruiter",
                jobService.updateJobForRecruiter(sampleJob, 202).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in activateJob",
                jobService.activateJob(202, 501).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in updateJobStatusForRecruiter",
                jobService.updateJobStatusForRecruiter(501, 202, Job.Status.ACTIVE).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in closeJob",
                jobService.closeJob(202, 501).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in deleteJob",
                jobService.deleteJob(202, 501).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in deleteJobForRecruiter",
                jobService.deleteJobForRecruiter(501, 202).isSuccess());
        assertFalse("Recruiter ID mismatch blocked in recruiterService.saveProfile",
                recruiterService.saveProfile(202, "Other Corp", "https://other.local", "Desc", "IT").isSuccess());
        assertFalse("Recruiter ID mismatch blocked in recruiterService.getProfile",
                recruiterService.getProfile(202).isPresent());

        // Non-positive IDs
        assertFalse("Non-positive recruiterId (0) blocked in createJob",
                jobService.createJob(0, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Non-positive recruiterId (-1) blocked in createJob",
                jobService.createJob(-1, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
        assertFalse("Non-positive jobId (0) blocked in updateJob",
                jobService.updateJob(201, 0, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());
        assertFalse("Non-positive jobId (0) blocked in activateJob",
                jobService.activateJob(201, 0).isSuccess());
        assertFalse("Non-positive jobId (-1) blocked in activateJob",
                jobService.activateJob(201, -1).isSuccess());
        assertFalse("Non-positive jobId (0) blocked in closeJob",
                jobService.closeJob(201, 0).isSuccess());
        assertFalse("Non-positive jobId (-1) blocked in deleteJob",
                jobService.deleteJob(201, -1).isSuccess());
        assertFalse("Non-positive recruiterId (0) blocked in recruiterService.saveProfile",
                recruiterService.saveProfile(0, "Company", "https://site.com", "Desc", "IT").isSuccess());

        // Null parameters
        assertFalse("Null job rejected in updateJobForRecruiter",
                jobService.updateJobForRecruiter(null, 201).isSuccess());
        assertFalse("Null status rejected in updateJob(params)",
                jobService.updateJob(201, 501, "Title", "Company", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), null).isSuccess());
        assertFalse("Null status rejected in updateJobStatusForRecruiter",
                jobService.updateJobStatusForRecruiter(501, 201, null).isSuccess());

        session.logout();
    }

    // =========================================================================
    // SECTION 2: Live Database Moderation & Authorization Suite
    // =========================================================================

    private void runLiveDatabaseSuite() throws Exception {
        System.out.println("\n--- [2] Running Live Database Moderation & Authorization Suite ---");

        SessionManager session = SessionManager.getInstance();
        JobService jobService = new JobService();
        RecruiterService recruiterService = new RecruiterService();
        JobDAO jobDAO = new JobDAO();

        BaselineCounts baseline = readCounts();
        System.out.printf("  [INFO] Initial Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, applications=%d%n",
                baseline.users, baseline.recruiterProfiles, baseline.jobs, baseline.auditLogs, baseline.applications);

        int deactRecruiterId = -1;
        int noProfileRecruiterId = -1;
        int unverifiedRecId = -1;
        int pendingRecId = -1;
        int rejectedRecId = -1;
        int suspendedRecId = -1;
        int verifiedRec1Id = -1;
        int verifiedRec2Id = -1;

        int job1Id = -1;
        int job2Id = -1;
        int job3Id = -1;

        try {
            // -----------------------------------------------------------------
            // Test 1: Deactivated Recruiter with Stale Session
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 1] Testing deactivated recruiter with stale in-memory session...");
            deactRecruiterId = createTestUser("Deactivated Recruiter", "deact_rec@test.local", User.Role.RECRUITER, false);
            createRecruiterProfile(deactRecruiterId, "Deact Corp", RecruiterVerificationStatus.VERIFIED);

            // In memory, session user has isActive=true, but in DB users.is_active=FALSE
            User staleSessionUser = new User(deactRecruiterId, "Deactivated Recruiter", "deact_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now());
            session.login(staleSessionUser);

            assertFalse("Stale session deactivated recruiter blocked from createJob(params)",
                    jobService.createJob(deactRecruiterId, "Title", "Deact Corp", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("Stale session deactivated recruiter blocked from activateJob",
                    jobService.activateJob(deactRecruiterId, 999).isSuccess());
            assertFalse("Stale session deactivated recruiter blocked from saveProfile",
                    recruiterService.saveProfile(deactRecruiterId, "Deact Corp", "https://deact.local", "Desc", "IT").isSuccess());
            assertFalse("Stale session deactivated recruiter blocked from getProfile",
                    recruiterService.getProfile(deactRecruiterId).isPresent());

            session.logout();

            // -----------------------------------------------------------------
            // Test 2: Active Recruiter User with Missing Recruiter Profile
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 2] Testing active recruiter user with missing recruiter_profiles row...");
            noProfileRecruiterId = createTestUser("No Profile Recruiter", "noprofile_rec@test.local", User.Role.RECRUITER, true);
            User noProfileUser = new User(noProfileRecruiterId, "No Profile Recruiter", "noprofile_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now());
            session.login(noProfileUser);

            assertFalse("Recruiter without profile blocked from createJob(params)",
                    jobService.createJob(noProfileRecruiterId, "Title", "NoProf Corp", "Desc", "Java", "Remote", "Full-Time", "10 LPA", "B.Tech", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("Recruiter without profile blocked from activateJob",
                    jobService.activateJob(noProfileRecruiterId, 999).isSuccess());

            session.logout();

            // -----------------------------------------------------------------
            // Test 3: Recruiter Verification Status Lifecycle
            // (UNVERIFIED, PENDING, REJECTED, SUSPENDED, VERIFIED)
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 3] Testing recruiter verification states (UNVERIFIED, PENDING, REJECTED, SUSPENDED, VERIFIED)...");

            unverifiedRecId = createTestUser("Unverified Rec", "unver_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(unverifiedRecId, "Unver Corp", RecruiterVerificationStatus.UNVERIFIED);

            pendingRecId = createTestUser("Pending Rec", "pend_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(pendingRecId, "Pend Corp", RecruiterVerificationStatus.PENDING);

            rejectedRecId = createTestUser("Rejected Rec", "rej_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(rejectedRecId, "Rej Corp", RecruiterVerificationStatus.REJECTED);

            suspendedRecId = createTestUser("Suspended Rec", "susp_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(suspendedRecId, "Susp Corp", RecruiterVerificationStatus.SUSPENDED);

            verifiedRec1Id = createTestUser("Verified Rec 1", "ver1_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(verifiedRec1Id, "Ver1 Corp", RecruiterVerificationStatus.VERIFIED);

            // Test UNVERIFIED
            session.login(new User(unverifiedRecId, "Unverified Rec", "unver_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));
            assertFalse("UNVERIFIED recruiter blocked from createJob(params)",
                    jobService.createJob(unverifiedRecId, "Title", "Corp", "Desc", "Java", "Remote", "Full-Time", "10", "Deg", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("UNVERIFIED recruiter blocked from activateJob",
                    jobService.activateJob(unverifiedRecId, 999).isSuccess());
            session.logout();

            // Test PENDING
            session.login(new User(pendingRecId, "Pending Rec", "pend_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));
            assertFalse("PENDING recruiter blocked from createJob(params)",
                    jobService.createJob(pendingRecId, "Title", "Corp", "Desc", "Java", "Remote", "Full-Time", "10", "Deg", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("PENDING recruiter blocked from activateJob",
                    jobService.activateJob(pendingRecId, 999).isSuccess());
            session.logout();

            // Test REJECTED
            session.login(new User(rejectedRecId, "Rejected Rec", "rej_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));
            assertFalse("REJECTED recruiter blocked from createJob(params)",
                    jobService.createJob(rejectedRecId, "Title", "Corp", "Desc", "Java", "Remote", "Full-Time", "10", "Deg", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("REJECTED recruiter blocked from activateJob",
                    jobService.activateJob(rejectedRecId, 999).isSuccess());
            session.logout();

            // Test SUSPENDED
            session.login(new User(suspendedRecId, "Suspended Rec", "susp_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));
            assertFalse("SUSPENDED recruiter blocked from createJob(params)",
                    jobService.createJob(suspendedRecId, "Title", "Corp", "Desc", "Java", "Remote", "Full-Time", "10", "Deg", LocalDate.now().plusDays(10), true).isSuccess());
            assertFalse("SUSPENDED recruiter blocked from activateJob",
                    jobService.activateJob(suspendedRecId, 999).isSuccess());
            session.logout();

            // Test VERIFIED — should succeed with draft creation
            session.login(new User(verifiedRec1Id, "Verified Rec 1", "ver1_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));

            JobService.ServiceResult cr1 = jobService.createJob(
                    verifiedRec1Id, "Senior Java Developer", "Ver1 Corp", "Build microservices",
                    "Java, Spring", "Remote", "Full-Time", "25 LPA", "B.Tech", LocalDate.now().plusDays(30), true);

            assertTrue("VERIFIED recruiter succeeds in createJob(params)", cr1.isSuccess());
            Job verifiedJobDraft = cr1.getJob();
            job1Id = verifiedJobDraft != null ? verifiedJobDraft.getId() : -1;
            assertTrue("Created job received generated key > 0", job1Id > 0);

            // Verify newly created job moderation state is strictly PENDING
            JobModerationStatus modStatus = getJobModerationStatus(job1Id);
            assertEquals("Newly created job strictly receives PENDING moderation status",
                    JobModerationStatus.PENDING, modStatus);
            Job.Status lifeStatus = getJobLifecycleStatus(job1Id);
            assertEquals("Newly created draft job receives DRAFT lifecycle status",
                    Job.Status.DRAFT, lifeStatus);

            // -----------------------------------------------------------------
            // Test 4: Job Publishing / Activation Guarded by Moderation Status
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 4] Testing job publishing / activation rules across moderation states...");

            // 4A: Attempt to activate PENDING job -> BLOCKED
            assertFalse("Activating PENDING job is blocked in activateJob",
                    jobService.activateJob(verifiedRec1Id, job1Id).isSuccess());
            assertFalse("updateJobStatusForRecruiter to ACTIVE on PENDING job is blocked",
                    jobService.updateJobStatusForRecruiter(job1Id, verifiedRec1Id, Job.Status.ACTIVE).isSuccess());
            assertEquals("Job status remains DRAFT after blocked activation attempt",
                    Job.Status.DRAFT, getJobLifecycleStatus(job1Id));

            // 4B: Attempt to create job with asDraft=false (requested ACTIVE)
            JobService.ServiceResult activeCreateRes = jobService.createJob(verifiedRec1Id, "Frontend Engineer", "Ver1 Corp",
                    "React frontend", "React, TypeScript", "Bengaluru", "Full-Time", "18 LPA", "B.Tech", LocalDate.now().plusDays(30), false);
            assertTrue("createJob with asDraft=false succeeds", activeCreateRes.isSuccess());
            Job activeReqJob = activeCreateRes.getJob();
            job2Id = activeReqJob != null ? activeReqJob.getId() : -1;
            assertTrue("Job 2 generated key > 0", job2Id > 0);
            assertEquals("createJob with asDraft=false preserves lifecycle status ACTIVE",
                    Job.Status.ACTIVE, getJobLifecycleStatus(job2Id));
            assertEquals("Job 2 has moderation_status = PENDING",
                    JobModerationStatus.PENDING, getJobModerationStatus(job2Id));

            // 4C: Set Job 1 to REJECTED in database (simulating admin moderation)
            setJobModeration(job1Id, JobModerationStatus.REJECTED, "Job does not meet requirements", 1);
            assertFalse("Activating REJECTED job is blocked in activateJob",
                    jobService.activateJob(verifiedRec1Id, job1Id).isSuccess());
            assertFalse("updateJobStatusForRecruiter to ACTIVE on REJECTED job is blocked",
                    jobService.updateJobStatusForRecruiter(job1Id, verifiedRec1Id, Job.Status.ACTIVE).isSuccess());

            // Verify updateJob cannot reactivate rejected job
            Job rejJobToUpdate = jobService.getJobById(job1Id).orElseThrow();
            rejJobToUpdate.setStatus(Job.Status.ACTIVE);
            assertFalse("updateJobForRecruiter cannot transition REJECTED job to ACTIVE",
                    jobService.updateJobForRecruiter(rejJobToUpdate, verifiedRec1Id).isSuccess());
            assertEquals("Job 1 remains DRAFT after rejected activation attempt",
                    Job.Status.DRAFT, getJobLifecycleStatus(job1Id));

            // 4D: Set Job 1 to SUSPENDED in database
            setJobModeration(job1Id, JobModerationStatus.SUSPENDED, "Suspicious activity", 1);
            assertFalse("Activating SUSPENDED job is blocked in activateJob",
                    jobService.activateJob(verifiedRec1Id, job1Id).isSuccess());
            assertFalse("updateJobStatusForRecruiter to ACTIVE on SUSPENDED job is blocked",
                    jobService.updateJobStatusForRecruiter(job1Id, verifiedRec1Id, Job.Status.ACTIVE).isSuccess());
            assertFalse("updateJobForRecruiter cannot transition SUSPENDED job to ACTIVE",
                    jobService.updateJobForRecruiter(rejJobToUpdate, verifiedRec1Id).isSuccess());
            assertEquals("Job 1 remains DRAFT after suspended activation attempt",
                    Job.Status.DRAFT, getJobLifecycleStatus(job1Id));

            // 4E: Set Job 1 to APPROVED in database
            setJobModeration(job1Id, JobModerationStatus.APPROVED, "Verified listing", 1);

            // Test activation with expired deadline -> BLOCKED
            setJobDeadline(job1Id, LocalDate.now().minusDays(2));
            assertFalse("Activating APPROVED job with expired deadline is blocked",
                    jobService.activateJob(verifiedRec1Id, job1Id).isSuccess());
            assertEquals("Job 1 remains DRAFT when deadline is expired",
                    Job.Status.DRAFT, getJobLifecycleStatus(job1Id));

            // Test activation with valid future deadline -> SUCCEEDS!
            setJobDeadline(job1Id, LocalDate.now().plusDays(15));
            assertTrue("Activating APPROVED job with valid deadline succeeds",
                    jobService.activateJob(verifiedRec1Id, job1Id).isSuccess());
            assertEquals("Job 1 lifecycle status is now ACTIVE",
                    Job.Status.ACTIVE, getJobLifecycleStatus(job1Id));
            assertEquals("Job 1 moderation status remains APPROVED",
                    JobModerationStatus.APPROVED, getJobModerationStatus(job1Id));

            // 4F: Recruiter closes active job -> SUCCEEDS!
            assertTrue("Recruiter can close active approved job",
                    jobService.closeJob(verifiedRec1Id, job1Id).isSuccess());
            assertEquals("Job 1 lifecycle status is now CLOSED",
                    Job.Status.CLOSED, getJobLifecycleStatus(job1Id));
            assertEquals("Job 1 moderation status remains APPROVED",
                    JobModerationStatus.APPROVED, getJobModerationStatus(job1Id));

            // -----------------------------------------------------------------
            // Test 5: Moderation Metadata Immutability & Tampering Protection
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 5] Testing moderation metadata immutability against recruiter tampering...");

            // Job 2 has moderation_status = PENDING
            assertEquals("Job 2 moderation status is PENDING",
                    JobModerationStatus.PENDING, getJobModerationStatus(job2Id));

            Job forgedJob = jobService.getJobById(job2Id).orElseThrow();
            forgedJob.setTitle("Tampered Title");
            forgedJob.setModerationStatus(JobModerationStatus.APPROVED);
            forgedJob.setModerationReason("Tampered by recruiter");
            forgedJob.setModeratedBy(1);
            forgedJob.setModeratedAt(LocalDateTime.now());

            assertTrue("updateJobForRecruiter succeeds for job content fields",
                    jobService.updateJobForRecruiter(forgedJob, verifiedRec1Id).isSuccess());

            Job fetchedAfterUpdate = jobService.getJobById(job2Id).orElseThrow();
            assertEquals("Title was updated", "Tampered Title", fetchedAfterUpdate.getTitle());
            assertEquals("Moderation status in DB was NOT modified by updateJob",
                    JobModerationStatus.PENDING, getJobModerationStatus(job2Id));
            assertEquals("Moderation reason in DB was NOT modified by updateJob",
                    null, getJobModerationReason(job2Id));
            assertEquals("Moderated by in DB was NOT modified by updateJob",
                    null, getJobModeratedBy(job2Id));

            // Profile tampering protection
            RecruiterProfile recProfile = recruiterService.getProfile(verifiedRec1Id).orElseThrow();
            recProfile.setVerificationStatus(RecruiterVerificationStatus.REJECTED);
            recProfile.setVerifiedBy(999);

            assertTrue("recruiterService.saveProfile succeeds for profile info",
                    recruiterService.saveProfile(verifiedRec1Id, "Top Recruiter Corp", "https://top.local", "Desc", "IT").isSuccess());
            assertEquals("Recruiter verification_status was NOT tampered by saveProfile",
                    RecruiterVerificationStatus.VERIFIED, getRecruiterVerificationStatus(verifiedRec1Id));
            assertEquals("Recruiter verified_by was NOT tampered by saveProfile",
                    null, getRecruiterVerifiedBy(verifiedRec1Id));

            // -----------------------------------------------------------------
            // Test 6: Tenant Isolation & Atomic Ownership Enforcement
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 6] Testing cross-recruiter tenant isolation and atomic ownership...");

            verifiedRec2Id = createTestUser("Verified Rec 2", "ver2_rec@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(verifiedRec2Id, "Ver2 Corp", RecruiterVerificationStatus.VERIFIED);

            // Log in as Recruiter 2
            session.login(new User(verifiedRec2Id, "Verified Rec 2", "ver2_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));

            // Recruiter 2 attempts to view Recruiter 1's job via scoped lookup
            Optional<Job> crossLookup = jobService.getJobByIdForRecruiter(job1Id, verifiedRec2Id);
            assertFalse("Recruiter 2 cannot view Recruiter 1's job via getJobByIdForRecruiter",
                    crossLookup.isPresent());

            // Recruiter 2 attempts to edit Recruiter 1's job
            Job crossEditJob = new Job(verifiedRec2Id, "Hacked Job", "Ver2 Corp", "Hacked", "Hacking", "Remote",
                    Job.JobType.FULL_TIME, "0", "None", LocalDate.now().plusDays(10), Job.Status.DRAFT);
            crossEditJob.setId(job1Id);
            assertFalse("Cross-recruiter updateJobForRecruiter is blocked",
                    jobService.updateJobForRecruiter(crossEditJob, verifiedRec2Id).isSuccess());
            assertFalse("Cross-recruiter param updateJob is blocked",
                    jobService.updateJob(verifiedRec2Id, job1Id, "Hacked", "Ver2 Corp", "Desc", "Java", "Remote", "Full-Time", "0", "None", LocalDate.now().plusDays(10), Job.Status.DRAFT).isSuccess());

            // Recruiter 2 attempts to activate Recruiter 1's job
            assertFalse("Cross-recruiter activateJob is blocked",
                    jobService.activateJob(verifiedRec2Id, job1Id).isSuccess());
            assertFalse("Cross-recruiter updateJobStatusForRecruiter(ACTIVE) is blocked",
                    jobService.updateJobStatusForRecruiter(job1Id, verifiedRec2Id, Job.Status.ACTIVE).isSuccess());

            // Recruiter 2 attempts to close Recruiter 1's job
            assertFalse("Cross-recruiter closeJob is blocked",
                    jobService.closeJob(verifiedRec2Id, job1Id).isSuccess());
            assertFalse("Cross-recruiter updateJobStatusForRecruiter(CLOSED) is blocked",
                    jobService.updateJobStatusForRecruiter(job1Id, verifiedRec2Id, Job.Status.CLOSED).isSuccess());

            // Recruiter 2 attempts to delete Recruiter 1's job
            assertFalse("Cross-recruiter deleteJob is blocked",
                    jobService.deleteJob(verifiedRec2Id, job1Id).isSuccess());
            assertFalse("Cross-recruiter deleteJobForRecruiter is blocked",
                    jobService.deleteJobForRecruiter(job1Id, verifiedRec2Id).isSuccess());

            // Verify Job 1 remains intact in DB
            assertTrue("Job 1 exists and owned by Recruiter 1",
                    jobService.getJobById(job1Id).orElseThrow().getRecruiterId() == verifiedRec1Id);

            // Log back in as Recruiter 1 and verify own delete works
            session.login(new User(verifiedRec1Id, "Verified Rec 1", "ver1_rec@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now()));
            Optional<Job> ownLookup = jobService.getJobByIdForRecruiter(job1Id, verifiedRec1Id);
            assertTrue("Recruiter 1 can view own job via getJobByIdForRecruiter",
                    ownLookup.isPresent() && ownLookup.get().getId() == job1Id);

            assertTrue("Recruiter 1 can delete own job via deleteJobForRecruiter",
                    jobService.deleteJobForRecruiter(job1Id, verifiedRec1Id).isSuccess());
            assertFalse("Job 1 is no longer present after delete",
                    jobService.getJobByIdForRecruiter(job1Id, verifiedRec1Id).isPresent());
            job1Id = -1; // marked deleted

            // -----------------------------------------------------------------
            // Test 7: Student Job Visibility Regression Protection
            // -----------------------------------------------------------------
            System.out.println("\n  [TEST 7] Testing student job visibility regression (searchApprovedActive & findOpenApprovedActiveJobs)...");

            // Create Job 3 under Verified Recruiter 1
            JobService.ServiceResult cr3 = jobService.createJob(
                    verifiedRec1Id, "Visible Position", "Ver1 Corp", "Great job",
                    "Java, SQL", "Remote", "Full-Time", "20 LPA", "B.Tech", LocalDate.now().plusDays(20), true);
            assertTrue("Job 3 creation succeeds", cr3.isSuccess());
            job3Id = cr3.getJob().getId();

            final int targetJobId = job3Id;

            // When Job 3 is DRAFT and PENDING:
            List<Job> studentSearch1 = jobDAO.searchApprovedActive("Visible Position", null, null);
            assertFalse("Draft PENDING job not visible to student searchApprovedActive",
                    studentSearch1.stream().anyMatch(j -> j.getId() == targetJobId));
            List<Job> studentOpen1 = jobDAO.findOpenApprovedActiveJobs();
            assertFalse("Draft PENDING job not visible to student findOpenApprovedActiveJobs",
                    studentOpen1.stream().anyMatch(j -> j.getId() == targetJobId));

            // Set Job 3 to ACTIVE and APPROVED in DB
            setJobLifecycle(job3Id, Job.Status.ACTIVE);
            setJobModeration(job3Id, JobModerationStatus.APPROVED, "Approved for testing", 1);
            setJobDeadline(job3Id, LocalDate.now().plusDays(20));

            // Now with VERIFIED recruiter, APPROVED job, ACTIVE status, future deadline:
            List<Job> studentSearch2 = jobDAO.searchApprovedActive("Visible Position", null, null);
            assertTrue("Approved active job with verified recruiter visible to student searchApprovedActive",
                    studentSearch2.stream().anyMatch(j -> j.getId() == targetJobId));
            List<Job> studentOpen2 = jobDAO.findOpenApprovedActiveJobs();
            assertTrue("Approved active job with verified recruiter visible to student findOpenApprovedActiveJobs",
                    studentOpen2.stream().anyMatch(j -> j.getId() == targetJobId));

            // Change recruiter to SUSPENDED -> student visibility should disappear immediately
            setRecruiterVerification(verifiedRec1Id, RecruiterVerificationStatus.SUSPENDED);
            List<Job> studentSearch3 = jobDAO.searchApprovedActive("Visible Position", null, null);
            assertFalse("Job becomes invisible to student search when recruiter is SUSPENDED",
                    studentSearch3.stream().anyMatch(j -> j.getId() == targetJobId));
            List<Job> studentOpen3 = jobDAO.findOpenApprovedActiveJobs();
            assertFalse("Job becomes invisible to student findOpenApproved when recruiter is SUSPENDED",
                    studentOpen3.stream().anyMatch(j -> j.getId() == targetJobId));

            // Restore recruiter to VERIFIED
            setRecruiterVerification(verifiedRec1Id, RecruiterVerificationStatus.VERIFIED);

            // Change job moderation to REJECTED -> student visibility should disappear immediately
            setJobModeration(job3Id, JobModerationStatus.REJECTED, "Violation", 1);
            List<Job> studentSearch4 = jobDAO.searchApprovedActive("Visible Position", null, null);
            assertFalse("Job becomes invisible to student search when job is REJECTED",
                    studentSearch4.stream().anyMatch(j -> j.getId() == targetJobId));

            // Restore job moderation to APPROVED, but expire the deadline
            setJobModeration(job3Id, JobModerationStatus.APPROVED, "Re-approved", 1);
            setJobDeadline(job3Id, LocalDate.now().minusDays(1));
            List<Job> studentOpenExpired = jobDAO.findOpenApprovedActiveJobs();
            assertFalse("Approved active job with expired deadline excluded from findOpenApprovedActiveJobs",
                    studentOpenExpired.stream().anyMatch(j -> j.getId() == targetJobId));

            session.logout();

        } finally {
            // -----------------------------------------------------------------
            // TEARDOWN: Guaranteed fixture cleanup and baseline check
            // -----------------------------------------------------------------
            System.out.println("\n  [TEARDOWN] Cleaning up all test fixtures and restoring baseline counts...");
            session.logout();

            if (job1Id > 0) deleteJob(job1Id);
            if (job2Id > 0) deleteJob(job2Id);
            if (job3Id > 0) deleteJob(job3Id);

            if (deactRecruiterId > 0) deleteRecruiterProfile(deactRecruiterId);
            if (noProfileRecruiterId > 0) deleteRecruiterProfile(noProfileRecruiterId);
            if (unverifiedRecId > 0) deleteRecruiterProfile(unverifiedRecId);
            if (pendingRecId > 0) deleteRecruiterProfile(pendingRecId);
            if (rejectedRecId > 0) deleteRecruiterProfile(rejectedRecId);
            if (suspendedRecId > 0) deleteRecruiterProfile(suspendedRecId);
            if (verifiedRec1Id > 0) deleteRecruiterProfile(verifiedRec1Id);
            if (verifiedRec2Id > 0) deleteRecruiterProfile(verifiedRec2Id);

            if (deactRecruiterId > 0) deleteUser(deactRecruiterId);
            if (noProfileRecruiterId > 0) deleteUser(noProfileRecruiterId);
            if (unverifiedRecId > 0) deleteUser(unverifiedRecId);
            if (pendingRecId > 0) deleteUser(pendingRecId);
            if (rejectedRecId > 0) deleteUser(rejectedRecId);
            if (suspendedRecId > 0) deleteUser(suspendedRecId);
            if (verifiedRec1Id > 0) deleteUser(verifiedRec1Id);
            if (verifiedRec2Id > 0) deleteUser(verifiedRec2Id);

            BaselineCounts finalCounts = readCounts();
            System.out.printf("  [INFO] Final Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, applications=%d%n",
                    finalCounts.users, finalCounts.recruiterProfiles, finalCounts.jobs, finalCounts.auditLogs, finalCounts.applications);

            assertEquals("Database users row count perfectly restored to baseline",
                    baseline.users, finalCounts.users);
            assertEquals("Database recruiter_profiles row count perfectly restored to baseline",
                    baseline.recruiterProfiles, finalCounts.recruiterProfiles);
            assertEquals("Database jobs row count perfectly restored to baseline",
                    baseline.jobs, finalCounts.jobs);
            assertEquals("Database admin_audit_logs row count perfectly restored to baseline",
                    baseline.auditLogs, finalCounts.auditLogs);
            assertEquals("Database applications row count perfectly restored to baseline",
                    baseline.applications, finalCounts.applications);
            assertEquals("Database resumes row count perfectly restored to baseline",
                    baseline.resumes, finalCounts.resumes);
            assertEquals("Database resume_analyses row count perfectly restored to baseline",
                    baseline.resumeAnalyses, finalCounts.resumeAnalyses);
            assertEquals("Database student_profiles row count perfectly restored to baseline",
                    baseline.studentProfiles, finalCounts.studentProfiles);
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

    private void setRecruiterVerification(int userId, RecruiterVerificationStatus status) {
        String sql = "UPDATE recruiter_profiles SET verification_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error setting recruiter verification: " + userId, e);
        }
    }

    private RecruiterVerificationStatus getRecruiterVerificationStatus(int userId) {
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

    private void setJobModeration(int jobId, JobModerationStatus status, String reason, int adminId) {
        String sql = "UPDATE jobs SET moderation_status = ?, moderation_reason = ?, moderated_by = ?, moderated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, reason);
            stmt.setInt(3, adminId);
            stmt.setInt(4, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating job moderation: " + jobId, e);
        }
    }

    private void setJobLifecycle(int jobId, Job.Status status) {
        String sql = "UPDATE jobs SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating job lifecycle status: " + jobId, e);
        }
    }

    private void setJobDeadline(int jobId, LocalDate deadline) {
        String sql = "UPDATE jobs SET deadline = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, deadline != null ? java.sql.Date.valueOf(deadline) : null);
            stmt.setInt(2, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating job deadline: " + jobId, e);
        }
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

    private String getJobModerationReason(int jobId) {
        String sql = "SELECT moderation_reason FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("moderation_reason");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading job moderation reason: " + jobId, e);
        }
        return null;
    }

    private Integer getJobModeratedBy(int jobId) {
        String sql = "SELECT moderated_by FROM jobs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt("moderated_by");
                    return rs.wasNull() ? null : val;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading job moderated_by: " + jobId, e);
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
                if (inLiveSuite) livePassed++; else offlinePassed++;
                System.out.println("  [PASS] Method " + clazz.getSimpleName() + "." + methodName + " exists with matching signature");
            }
        } catch (NoSuchMethodException e) {
            if (inLiveSuite) liveFailed++; else offlineFailed++;
            System.err.println("  [FAIL] Missing method: " + clazz.getSimpleName() + "." + methodName);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (condition) {
            if (inLiveSuite) livePassed++; else offlinePassed++;
            System.out.println("  [PASS] " + message);
        } else {
            if (inLiveSuite) liveFailed++; else offlineFailed++;
            System.err.println("  [FAIL] " + message);
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) {
            if (inLiveSuite) livePassed++; else offlinePassed++;
            System.out.println("  [PASS] " + message);
            return;
        }
        if (expected != null && expected.equals(actual)) {
            if (inLiveSuite) livePassed++; else offlinePassed++;
            System.out.println("  [PASS] " + message + " (value=" + actual + ")");
        } else {
            if (inLiveSuite) liveFailed++; else offlineFailed++;
            System.err.println("  [FAIL] " + message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
