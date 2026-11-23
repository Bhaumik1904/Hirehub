package com.hirehub.database;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.AdminModerationDAO;
import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;

import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phase 6 Step 4.2B.2: DAO Implementation & Database Mapping Verifier.
 *
 * <p>Validates:
 * <ol>
 *   <li>Model & Reflection Architecture:
 *       Method signatures on {@link RecruiterDAO}, {@link JobDAO},
 *       {@link AdminUserDAO}, and {@link AdminModerationDAO}.</li>
 *   <li>RecruiterDAO Live Persistence:
 *       Full mapping of verification columns, admin status updates,
 *       recruiter tamper protection, and administrative search/filter queries.</li>
 *   <li>JobDAO Live Persistence:
 *       Full mapping of moderation columns, backward compatibility of
 *       {@code findOpenActiveJobs} and {@code searchActive}, approved-only queries,
 *       recruiter update tamper protection, and administrative job search/filtering.</li>
 *   <li>AdminModerationDAO Transactional Atomicity:
 *       Synchronized status updates and multi-entity audit logging for recruiters
 *       and jobs with rollback on failure.</li>
 *   <li>AdminUserDAO Extended Audit Integration:
 *       Job-specific and recruiter-specific audit log retrieval with entity type mapping.</li>
 *   <li>100% Database Teardown restoring initial baseline row counts across all tables.</li>
 * </ol>
 * </p>
 */
public class DataAccessMappingTest {

    private static final String PREFIX = "DAO4_2B2_TEST_";
    private static final String TEST_PASSWORD = "DaoTestPassword@123";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    public static void main(String[] args) {
        DataAccessMappingTest verifier = new DataAccessMappingTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2B.2: DAO & Database Mapping Verifier");
        System.out.println("=================================================================\n");

        runOfflineArchitectureSuite();
        runLiveDatabaseSuite();
        runStudentJobVisibilitySecuritySuite();

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("STEP 4.2B.2 VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Architecture & Reflection Suite
    // =========================================================================

    private void runOfflineArchitectureSuite() {
        System.out.println("--- [1] Running Offline Architecture & Reflection Suite ---");

        // 1. RecruiterDAO contract
        checkMethod(RecruiterDAO.class, "insert", RecruiterProfile.class);
        checkMethod(RecruiterDAO.class, "update", RecruiterProfile.class);
        checkMethod(RecruiterDAO.class, "saveOrUpdate", RecruiterProfile.class);
        checkMethod(RecruiterDAO.class, "deleteByUserId", int.class);
        checkMethod(RecruiterDAO.class, "findByUserId", int.class);
        checkMethod(RecruiterDAO.class, "findById", int.class);
        checkMethod(RecruiterDAO.class, "existsByUserId", int.class);
        checkMethod(RecruiterDAO.class, "updateVerificationStatus", int.class, RecruiterVerificationStatus.class, String.class, Integer.class);
        checkMethod(RecruiterDAO.class, "updateVerificationStatus", int.class, RecruiterVerificationStatus.class, String.class, Integer.class, LocalDateTime.class);
        checkMethod(RecruiterDAO.class, "searchRecruitersForAdmin", String.class, RecruiterVerificationStatus.class, int.class, int.class);
        checkMethod(RecruiterDAO.class, "countRecruitersForAdmin", String.class, RecruiterVerificationStatus.class);

        // 2. JobDAO contract
        checkMethod(JobDAO.class, "insert", Job.class);
        checkMethod(JobDAO.class, "update", Job.class);
        checkMethod(JobDAO.class, "updateForRecruiter", Job.class, int.class);
        checkMethod(JobDAO.class, "updateStatus", int.class, Job.Status.class);
        checkMethod(JobDAO.class, "updateStatusForRecruiter", int.class, int.class, Job.Status.class);
        checkMethod(JobDAO.class, "delete", int.class);
        checkMethod(JobDAO.class, "deleteForRecruiter", int.class, int.class);
        checkMethod(JobDAO.class, "findById", int.class);
        checkMethod(JobDAO.class, "findByIdAndRecruiter", int.class, int.class);
        checkMethod(JobDAO.class, "findByRecruiterId", int.class);
        checkMethod(JobDAO.class, "searchActive", String.class, String.class, String.class);
        checkMethod(JobDAO.class, "searchApprovedActive", String.class, String.class, String.class);
        checkMethod(JobDAO.class, "searchApprovedActive", String.class, String.class, String.class, int.class, int.class);
        checkMethod(JobDAO.class, "findOpenActiveJobs");
        checkMethod(JobDAO.class, "findOpenApprovedActiveJobs");
        checkMethod(JobDAO.class, "countActiveByRecruiter", int.class);
        checkMethod(JobDAO.class, "updateModerationStatus", int.class, JobModerationStatus.class, String.class, Integer.class);
        checkMethod(JobDAO.class, "updateModerationStatus", int.class, JobModerationStatus.class, String.class, Integer.class, LocalDateTime.class);
        checkMethod(JobDAO.class, "searchJobsForAdmin", String.class, Job.Status.class, JobModerationStatus.class, int.class, int.class);
        checkMethod(JobDAO.class, "countJobsForAdmin", String.class, Job.Status.class, JobModerationStatus.class);

        // 3. AdminUserDAO contract
        checkMethod(AdminUserDAO.class, "searchUsers", String.class, User.Role.class, Boolean.class, int.class, int.class);
        checkMethod(AdminUserDAO.class, "countUsers", String.class, User.Role.class, Boolean.class);
        checkMethod(AdminUserDAO.class, "findById", int.class);
        checkMethod(AdminUserDAO.class, "countActiveAdmins");
        checkMethod(AdminUserDAO.class, "updateAccountStatusWithAudit", int.class, int.class, boolean.class, String.class, String.class);
        checkMethod(AdminUserDAO.class, "getAuditLogsForUser", int.class);
        checkMethod(AdminUserDAO.class, "getAuditLogsForJob", int.class);
        checkMethod(AdminUserDAO.class, "getRecentAuditLogs", int.class);

        // 4. AdminModerationDAO contract
        checkMethod(AdminModerationDAO.class, "updateRecruiterVerificationWithAudit", int.class, int.class, RecruiterVerificationStatus.class, String.class);
        checkMethod(AdminModerationDAO.class, "updateJobModerationWithAudit", int.class, int.class, JobModerationStatus.class, String.class);
        checkMethod(AdminModerationDAO.class, "getAuditLogsForRecruiter", int.class);
        checkMethod(AdminModerationDAO.class, "getAuditLogsForJob", int.class);
        checkMethod(AdminModerationDAO.class, "getRecentModerationLogs", int.class);
    }

    // =========================================================================
    // SECTION 2: Live Database Persistence Suite
    // =========================================================================

    private void runLiveDatabaseSuite() {
        System.out.println("\n--- [2] Running Live Database Persistence & Mapping Suite ---");

        RecruiterDAO recruiterDAO = new RecruiterDAO();
        JobDAO jobDAO = new JobDAO();
        AdminUserDAO adminUserDAO = new AdminUserDAO();
        AdminModerationDAO adminModerationDAO = new AdminModerationDAO();

        // Ensure clean slate prior to recording baseline
        teardownFixtures();

        BaselineCounts baseline = readCounts();
        System.out.printf("  [INFO] Initial Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d%n",
                baseline.users, baseline.recruiterProfiles, baseline.jobs, baseline.auditLogs);

        int testAdminId = -1;
        int testRec1Id = -1;
        int testRec2Id = -1;
        int job1Id = -1;
        int job2Id = -1;
        int job3Id = -1;

        try {
            // -----------------------------------------------------------------
            // 1. Fixture Creation
            // -----------------------------------------------------------------
            testAdminId = insertUser(PREFIX + "admin@test.local", "Admin DAO Tester", User.Role.ADMIN, true);
            testRec1Id = insertUser(PREFIX + "rec1@test.local", "Recruiter Alpha", User.Role.RECRUITER, true);
            testRec2Id = insertUser(PREFIX + "rec2@test.local", "Recruiter Beta", User.Role.RECRUITER, true);

            assertTrue("Created test Admin (id=" + testAdminId + ")", testAdminId > 0);
            assertTrue("Created test Recruiter 1 (id=" + testRec1Id + ")", testRec1Id > 0);
            assertTrue("Created test Recruiter 2 (id=" + testRec2Id + ")", testRec2Id > 0);

            // -----------------------------------------------------------------
            // 2. RecruiterDAO Live Suite
            // -----------------------------------------------------------------
            System.out.println("  -> Testing RecruiterDAO live operations...");

            // Insert Profile 1 with PENDING verification status
            RecruiterProfile p1 = new RecruiterProfile(testRec1Id, PREFIX + "Corp1", "https://corp1.test",
                    "Enterprise software solutions", "Technology");
            p1.setVerificationStatus(RecruiterVerificationStatus.PENDING);
            recruiterDAO.insert(p1);
            assertTrue("Recruiter 1 profile inserted with id=" + p1.getId(), p1.getId() > 0);

            // Insert Profile 2 with default/null verification status (defaults to UNVERIFIED)
            RecruiterProfile p2 = new RecruiterProfile(testRec2Id, PREFIX + "Corp2", "https://corp2.test",
                    "Financial analytics", "Finance");
            recruiterDAO.insert(p2);
            assertTrue("Recruiter 2 profile inserted with id=" + p2.getId(), p2.getId() > 0);

            // Verify mapping on findByUserId
            Optional<RecruiterProfile> fetchedP1 = recruiterDAO.findByUserId(testRec1Id);
            assertTrue("Recruiter 1 found by user ID", fetchedP1.isPresent());
            assertEquals("Recruiter 1 company name mapped", PREFIX + "Corp1", fetchedP1.get().getCompanyName());
            assertEquals("Recruiter 1 verification status is PENDING", RecruiterVerificationStatus.PENDING, fetchedP1.get().getVerificationStatus());
            assertNull("Recruiter 1 verification reason is null initially", fetchedP1.get().getVerificationReason());
            assertNull("Recruiter 1 verified_at is null initially", fetchedP1.get().getVerifiedAt());
            assertNull("Recruiter 1 verified_by is null initially", fetchedP1.get().getVerifiedBy());
            assertFalse("Recruiter 1 isVerified() returns false for PENDING", fetchedP1.get().isVerified());

            Optional<RecruiterProfile> fetchedP2 = recruiterDAO.findByUserId(testRec2Id);
            assertTrue("Recruiter 2 found by user ID", fetchedP2.isPresent());
            assertEquals("Recruiter 2 verification status defaults to UNVERIFIED",
                    RecruiterVerificationStatus.UNVERIFIED, fetchedP2.get().getVerificationStatus());
            assertFalse("Recruiter 2 isVerified() returns false for UNVERIFIED", fetchedP2.get().isVerified());

            // Test findById
            Optional<RecruiterProfile> byIdP1 = recruiterDAO.findById(p1.getId());
            assertTrue("Recruiter 1 found by profile ID", byIdP1.isPresent());
            assertEquals("Profile ID lookup matches user ID", testRec1Id, byIdP1.get().getUserId());

            // Test existsByUserId
            assertTrue("existsByUserId returns true for Recruiter 1", recruiterDAO.existsByUserId(testRec1Id));
            assertFalse("existsByUserId returns false for nonexistent ID", recruiterDAO.existsByUserId(999999));

            // Test updateVerificationStatus to VERIFIED
            boolean verifiedSuccess = recruiterDAO.updateVerificationStatus(
                    testRec1Id, RecruiterVerificationStatus.VERIFIED, "Corporate registration verified", testAdminId);
            assertTrue("updateVerificationStatus(VERIFIED) returned true", verifiedSuccess);

            Optional<RecruiterProfile> afterVerP1 = recruiterDAO.findByUserId(testRec1Id);
            assertTrue("Fetched Recruiter 1 after verification", afterVerP1.isPresent());
            assertEquals("Recruiter 1 status updated to VERIFIED", RecruiterVerificationStatus.VERIFIED, afterVerP1.get().getVerificationStatus());
            assertEquals("Recruiter 1 verification reason stored", "Corporate registration verified", afterVerP1.get().getVerificationReason());
            assertNotNull("Recruiter 1 verified_at is populated", afterVerP1.get().getVerifiedAt());
            assertEquals("Recruiter 1 verified_by matches testAdminId", Integer.valueOf(testAdminId), afterVerP1.get().getVerifiedBy());
            assertTrue("Recruiter 1 isVerified() returns true", afterVerP1.get().isVerified());

            // Test updateVerificationStatus to REJECTED
            boolean rejectedSuccess = recruiterDAO.updateVerificationStatus(
                    testRec2Id, RecruiterVerificationStatus.REJECTED, "Invalid tax documentation", testAdminId);
            assertTrue("updateVerificationStatus(REJECTED) returned true", rejectedSuccess);

            Optional<RecruiterProfile> afterRejP2 = recruiterDAO.findByUserId(testRec2Id);
            assertTrue("Fetched Recruiter 2 after rejection", afterRejP2.isPresent());
            assertEquals("Recruiter 2 status is REJECTED", RecruiterVerificationStatus.REJECTED, afterRejP2.get().getVerificationStatus());
            assertEquals("Recruiter 2 rejection reason stored", "Invalid tax documentation", afterRejP2.get().getVerificationReason());
            assertEquals("Recruiter 2 verified_by matches testAdminId", Integer.valueOf(testAdminId), afterRejP2.get().getVerifiedBy());
            assertFalse("Recruiter 2 isVerified() returns false", afterRejP2.get().isVerified());

            // Test recruiter self-update tamper protection: recruiter cannot modify verification columns via update()
            RecruiterProfile p2TamperAttempt = afterRejP2.get();
            p2TamperAttempt.setCompanyName(PREFIX + "Corp2_Renamed");
            p2TamperAttempt.setVerificationStatus(RecruiterVerificationStatus.VERIFIED);
            p2TamperAttempt.setVerificationReason("Self-approved hack attempt");
            recruiterDAO.update(p2TamperAttempt);

            Optional<RecruiterProfile> afterTamperP2 = recruiterDAO.findByUserId(testRec2Id);
            assertTrue("Fetched Recruiter 2 after self-update", afterTamperP2.isPresent());
            assertEquals("Recruiter self-update changed company name", PREFIX + "Corp2_Renamed", afterTamperP2.get().getCompanyName());
            assertEquals("Recruiter self-update CANNOT alter verification status (remains REJECTED)",
                    RecruiterVerificationStatus.REJECTED, afterTamperP2.get().getVerificationStatus());
            assertEquals("Recruiter self-update CANNOT alter verification reason",
                    "Invalid tax documentation", afterTamperP2.get().getVerificationReason());

            // Test searchRecruitersForAdmin & countRecruitersForAdmin
            List<RecruiterProfile> searchByName = recruiterDAO.searchRecruitersForAdmin(
                    PREFIX + "Corp2_Renamed", null, 10, 0);
            assertEquals("Search by company name found exactly 1 match", 1, searchByName.size());
            assertEquals("Search by company name matched Recruiter 2", testRec2Id, searchByName.get(0).getUserId());

            List<RecruiterProfile> searchByStatusVer = recruiterDAO.searchRecruitersForAdmin(
                    PREFIX, RecruiterVerificationStatus.VERIFIED, 10, 0);
            assertEquals("Search by VERIFIED filter found exactly 1 match", 1, searchByStatusVer.size());
            assertEquals("VERIFIED filter matched Recruiter 1", testRec1Id, searchByStatusVer.get(0).getUserId());

            List<RecruiterProfile> searchByStatusRej = recruiterDAO.searchRecruitersForAdmin(
                    PREFIX, RecruiterVerificationStatus.REJECTED, 10, 0);
            assertEquals("Search by REJECTED filter found exactly 1 match", 1, searchByStatusRej.size());
            assertEquals("REJECTED filter matched Recruiter 2", testRec2Id, searchByStatusRej.get(0).getUserId());

            int countVer = recruiterDAO.countRecruitersForAdmin(PREFIX, RecruiterVerificationStatus.VERIFIED);
            assertEquals("countRecruitersForAdmin for VERIFIED matches 1", 1, countVer);

            int countRej = recruiterDAO.countRecruitersForAdmin(PREFIX, RecruiterVerificationStatus.REJECTED);
            assertEquals("countRecruitersForAdmin for REJECTED matches 1", 1, countRej);

            // -----------------------------------------------------------------
            // 3. JobDAO Live Suite
            // -----------------------------------------------------------------
            System.out.println("  -> Testing JobDAO live operations...");

            // Job 1: Active, Pending moderation, future deadline
            Job job1 = new Job(testRec1Id, PREFIX + "Software Engineer", PREFIX + "Corp1",
                    "Full-stack development with Java and React", "Java, Spring, MySQL", "New York, NY",
                    Job.JobType.FULL_TIME, "$100k-$130k", "CS degree",
                    LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            job1.setModerationStatus(JobModerationStatus.PENDING);
            job1Id = jobDAO.insert(job1);
            assertTrue("Job 1 inserted with id=" + job1Id, job1Id > 0);

            // Job 2: Active, Approved moderation, future deadline
            Job job2 = new Job(testRec1Id, PREFIX + "Data Analyst", PREFIX + "Corp1",
                    "Data analysis and dashboard reporting", "SQL, Python, Tableau", "Remote",
                    Job.JobType.FULL_TIME, "$90k-$110k", "Any STEM degree",
                    LocalDate.now().plusMonths(2), Job.Status.ACTIVE);
            job2.setModerationStatus(JobModerationStatus.APPROVED);
            job2Id = jobDAO.insert(job2);
            assertTrue("Job 2 inserted with id=" + job2Id, job2Id > 0);

            // Job 3: Draft, Pending moderation
            Job job3 = new Job(testRec2Id, PREFIX + "Financial Intern", PREFIX + "Corp2_Renamed",
                    "Internship in portfolio modeling", "Excel, Finance", "Boston, MA",
                    Job.JobType.INTERNSHIP, "$30/hr", "Finance major",
                    null, Job.Status.DRAFT);
            job3Id = jobDAO.insert(job3);
            assertTrue("Job 3 inserted with id=" + job3Id, job3Id > 0);

            // Verify mapping on findById
            Optional<Job> fetchedJ1 = jobDAO.findById(job1Id);
            assertTrue("Job 1 found by ID", fetchedJ1.isPresent());
            assertEquals("Job 1 moderation status is PENDING", JobModerationStatus.PENDING, fetchedJ1.get().getModerationStatus());
            assertNull("Job 1 moderation reason is null", fetchedJ1.get().getModerationReason());
            assertNull("Job 1 moderated_at is null", fetchedJ1.get().getModeratedAt());
            assertNull("Job 1 moderated_by is null", fetchedJ1.get().getModeratedBy());
            assertFalse("Job 1 isApproved() returns false for PENDING", fetchedJ1.get().isApproved());

            Optional<Job> fetchedJ2 = jobDAO.findById(job2Id);
            assertTrue("Job 2 found by ID", fetchedJ2.isPresent());
            assertEquals("Job 2 moderation status is APPROVED", JobModerationStatus.APPROVED, fetchedJ2.get().getModerationStatus());
            assertTrue("Job 2 isApproved() returns true for APPROVED", fetchedJ2.get().isApproved());

            // Test findByIdAndRecruiter
            assertTrue("findByIdAndRecruiter finds Job 1 for testRec1Id",
                    jobDAO.findByIdAndRecruiter(job1Id, testRec1Id).isPresent());
            assertFalse("findByIdAndRecruiter fails for testRec2Id trying to access Job 1",
                    jobDAO.findByIdAndRecruiter(job1Id, testRec2Id).isPresent());

            final int fJob1Id = job1Id;
            final int fJob2Id = job2Id;
            final int fJob3Id = job3Id;
            final int fRec1Id = testRec1Id;

            // Test Backward Compatibility of findOpenActiveJobs and searchActive
            List<Job> openActive = jobDAO.findOpenActiveJobs();
            boolean openHasJ1 = openActive.stream().anyMatch(j -> j.getId() == fJob1Id);
            boolean openHasJ2 = openActive.stream().anyMatch(j -> j.getId() == fJob2Id);
            boolean openHasJ3 = openActive.stream().anyMatch(j -> j.getId() == fJob3Id);
            assertTrue("Backward compatibility: findOpenActiveJobs includes Active Job 1 (even when PENDING moderation)", openHasJ1);
            assertTrue("Backward compatibility: findOpenActiveJobs includes Active Job 2", openHasJ2);
            assertFalse("Backward compatibility: findOpenActiveJobs excludes Draft Job 3", openHasJ3);

            List<Job> searchActiveAll = jobDAO.searchActive(PREFIX, "", "");
            assertEquals("searchActive returns both Active jobs (Job 1 and Job 2)", 2, searchActiveAll.size());

            // Test Approved-Only Student Queries
            List<Job> approvedOpen = jobDAO.findOpenApprovedActiveJobs();
            boolean appHasJ1 = approvedOpen.stream().anyMatch(j -> j.getId() == fJob1Id);
            boolean appHasJ2 = approvedOpen.stream().anyMatch(j -> j.getId() == fJob2Id);
            assertFalse("findOpenApprovedActiveJobs EXCLUDES unapproved Job 1", appHasJ1);
            assertTrue("findOpenApprovedActiveJobs INCLUDES approved Job 2", appHasJ2);

            List<Job> approvedSearch = jobDAO.searchApprovedActive(PREFIX, "", "");
            assertEquals("searchApprovedActive returns ONLY approved Job 2", 1, approvedSearch.size());
            assertEquals("searchApprovedActive matched Job 2", job2Id, approvedSearch.get(0).getId());

            // Test Recruiter Update Tamper Protection: recruiter cannot modify moderation columns via updateForRecruiter
            Job j1Edit = fetchedJ1.get();
            j1Edit.setTitle(PREFIX + "Senior Software Engineer");
            j1Edit.setModerationStatus(JobModerationStatus.APPROVED);
            j1Edit.setModerationReason("Recruiter self-approval attempt");
            int updateRows = jobDAO.updateForRecruiter(j1Edit, testRec1Id);
            assertEquals("updateForRecruiter returned 1 modified row", 1, updateRows);

            Optional<Job> afterEditJ1 = jobDAO.findById(job1Id);
            assertTrue("Fetched Job 1 after recruiter update", afterEditJ1.isPresent());
            assertEquals("Title successfully updated by recruiter", PREFIX + "Senior Software Engineer", afterEditJ1.get().getTitle());
            assertEquals("Moderation status CANNOT be altered by recruiter (remains PENDING)",
                    JobModerationStatus.PENDING, afterEditJ1.get().getModerationStatus());
            assertNull("Moderation reason remains null", afterEditJ1.get().getModerationReason());

            // Test updateModerationStatus to APPROVED
            boolean j1Approved = jobDAO.updateModerationStatus(
                    job1Id, JobModerationStatus.APPROVED, "Content verified and compliant", testAdminId);
            assertTrue("updateModerationStatus(APPROVED) returned true", j1Approved);

            Optional<Job> afterModJ1 = jobDAO.findById(job1Id);
            assertTrue("Fetched Job 1 after moderation approval", afterModMod(afterModJ1));
            assertEquals("Job 1 moderation status is APPROVED", JobModerationStatus.APPROVED, afterModJ1.get().getModerationStatus());
            assertEquals("Job 1 moderation reason recorded", "Content verified and compliant", afterModJ1.get().getModerationReason());
            assertNotNull("Job 1 moderated_at recorded", afterModJ1.get().getModeratedAt());
            assertEquals("Job 1 moderated_by matches admin ID", Integer.valueOf(testAdminId), afterModJ1.get().getModeratedBy());
            assertTrue("Job 1 isApproved() returns true", afterModJ1.get().isApproved());

            // Now Job 1 appears in approved listings
            assertTrue("Job 1 now appears in findOpenApprovedActiveJobs",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == fJob1Id));

            // Test updateModerationStatus to SUSPENDED (Administrative Takedown)
            boolean j1Suspended = jobDAO.updateModerationStatus(
                    job1Id, JobModerationStatus.SUSPENDED, "Salary range deceptive - administrative takedown", testAdminId);
            assertTrue("updateModerationStatus(SUSPENDED) returned true", j1Suspended);

            Optional<Job> afterSuspJ1 = jobDAO.findById(job1Id);
            assertTrue("Fetched Job 1 after suspension", afterSuspJ1.isPresent());
            assertEquals("Job 1 status is SUSPENDED", JobModerationStatus.SUSPENDED, afterSuspJ1.get().getModerationStatus());
            assertFalse("Job 1 is no longer in findOpenApprovedActiveJobs after suspension",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == fJob1Id));

            // Test searchJobsForAdmin & countJobsForAdmin
            List<Job> adminSearchSuspended = jobDAO.searchJobsForAdmin(
                    PREFIX, null, JobModerationStatus.SUSPENDED, 10, 0);
            assertEquals("searchJobsForAdmin(SUSPENDED) finds Job 1", 1, adminSearchSuspended.size());
            assertEquals("searchJobsForAdmin matched Job 1 ID", job1Id, adminSearchSuspended.get(0).getId());

            List<Job> adminSearchApproved = jobDAO.searchJobsForAdmin(
                    PREFIX, null, JobModerationStatus.APPROVED, 10, 0);
            assertEquals("searchJobsForAdmin(APPROVED) finds Job 2", 1, adminSearchApproved.size());
            assertEquals("searchJobsForAdmin matched Job 2 ID", job2Id, adminSearchApproved.get(0).getId());

            List<Job> adminSearchDraft = jobDAO.searchJobsForAdmin(
                    PREFIX, Job.Status.DRAFT, null, 10, 0);
            assertEquals("searchJobsForAdmin(DRAFT) finds Job 3", 1, adminSearchDraft.size());
            assertEquals("searchJobsForAdmin matched Job 3 ID", job3Id, adminSearchDraft.get(0).getId());

            int countSusp = jobDAO.countJobsForAdmin(PREFIX, null, JobModerationStatus.SUSPENDED);
            assertEquals("countJobsForAdmin(SUSPENDED) matches 1", 1, countSusp);

            int countDraft = jobDAO.countJobsForAdmin(PREFIX, Job.Status.DRAFT, null);
            assertEquals("countJobsForAdmin(DRAFT) matches 1", 1, countDraft);

            // -----------------------------------------------------------------
            // 4. AdminModerationDAO Transactional Suite
            // -----------------------------------------------------------------
            System.out.println("  -> Testing AdminModerationDAO transactional operations...");

            // Recruiter Moderation with Audit
            boolean modRecSuccess = adminModerationDAO.updateRecruiterVerificationWithAudit(
                    testAdminId, testRec1Id, RecruiterVerificationStatus.SUSPENDED, "Regulatory compliance hold");
            assertTrue("updateRecruiterVerificationWithAudit returned true", modRecSuccess);

            Optional<RecruiterProfile> modRecP1 = recruiterDAO.findByUserId(testRec1Id);
            assertTrue("Recruiter 1 fetched after audit moderation", modRecP1.isPresent());
            assertEquals("Recruiter 1 verification status is SUSPENDED",
                    RecruiterVerificationStatus.SUSPENDED, modRecP1.get().getVerificationStatus());
            assertEquals("Recruiter 1 verified_by matches admin", Integer.valueOf(testAdminId), modRecP1.get().getVerifiedBy());

            List<AdminAuditLog> recAuditLogs = adminModerationDAO.getAuditLogsForRecruiter(testRec1Id);
            assertFalse("Recruiter audit logs list is not empty", recAuditLogs.isEmpty());
            AdminAuditLog recLog = recAuditLogs.get(0);
            assertEquals("Recruiter audit log entity_type is RECRUITER_PROFILE",
                    AdminAuditEntityType.RECRUITER_PROFILE, recLog.getEntityType());
            assertEquals("Recruiter audit log action is RECRUITER_SUSPENDED", "RECRUITER_SUSPENDED", recLog.getAction());
            assertEquals("Recruiter audit log reason matches", "Regulatory compliance hold", recLog.getReason());
            assertEquals("Recruiter audit log admin_id matches", testAdminId, recLog.getAdminId());
            assertEquals("Recruiter audit log target_user_id matches", Integer.valueOf(testRec1Id), recLog.getTargetUserId());

            // Job Moderation with Audit
            boolean modJobSuccess = adminModerationDAO.updateJobModerationWithAudit(
                    testAdminId, job2Id, JobModerationStatus.REJECTED, "Policy violation: deceptive title");
            assertTrue("updateJobModerationWithAudit returned true", modJobSuccess);

            Optional<Job> modJ2 = jobDAO.findById(job2Id);
            assertTrue("Job 2 fetched after audit moderation", modJ2.isPresent());
            assertEquals("Job 2 moderation status is REJECTED",
                    JobModerationStatus.REJECTED, modJ2.get().getModerationStatus());
            assertEquals("Job 2 moderated_by matches admin", Integer.valueOf(testAdminId), modJ2.get().getModeratedBy());

            List<AdminAuditLog> jobAuditLogs = adminModerationDAO.getAuditLogsForJob(job2Id);
            assertFalse("Job audit logs list is not empty", jobAuditLogs.isEmpty());
            AdminAuditLog jobLog = jobAuditLogs.get(0);
            assertEquals("Job audit log entity_type is JOB", AdminAuditEntityType.JOB, jobLog.getEntityType());
            assertEquals("Job audit log action is JOB_REJECTED", "JOB_REJECTED", jobLog.getAction());
            assertEquals("Job audit log reason matches", "Policy violation: deceptive title", jobLog.getReason());
            assertEquals("Job audit log admin_id matches", testAdminId, jobLog.getAdminId());
            assertEquals("Job audit log target_job_id matches", Integer.valueOf(job2Id), jobLog.getTargetJobId());
            assertEquals("Job audit log correctly auto-linked recruiter target_user_id",
                    Integer.valueOf(testRec1Id), jobLog.getTargetUserId());

            // Atomic Rollback Verification on Non-Existent Entity
            boolean failedJobMod = adminModerationDAO.updateJobModerationWithAudit(
                    testAdminId, 9999999, JobModerationStatus.APPROVED, "Non-existent job test");
            assertFalse("updateJobModerationWithAudit returns false on non-existent job", failedJobMod);

            boolean failedRecMod = adminModerationDAO.updateRecruiterVerificationWithAudit(
                    testAdminId, 9999999, RecruiterVerificationStatus.VERIFIED, "Non-existent recruiter test");
            assertFalse("updateRecruiterVerificationWithAudit returns false on non-existent recruiter", failedRecMod);

            // Recent Moderation Logs
            List<AdminAuditLog> recentMod = adminModerationDAO.getRecentModerationLogs(10);
            assertTrue("getRecentModerationLogs returns at least 2 entries", recentMod.size() >= 2);
            boolean hasJobEntity = recentMod.stream().anyMatch(l -> l.getEntityType() == AdminAuditEntityType.JOB);
            boolean hasRecEntity = recentMod.stream().anyMatch(l -> l.getEntityType() == AdminAuditEntityType.RECRUITER_PROFILE);
            assertTrue("recentMod contains JOB entity audit entry", hasJobEntity);
            assertTrue("recentMod contains RECRUITER_PROFILE entity audit entry", hasRecEntity);

            // -----------------------------------------------------------------
            // 5. AdminUserDAO Extended Audit Integration
            // -----------------------------------------------------------------
            System.out.println("  -> Testing AdminUserDAO extended audit integration...");

            List<AdminAuditLog> userDAOJobLogs = adminUserDAO.getAuditLogsForJob(job2Id);
            assertEquals("AdminUserDAO.getAuditLogsForJob retrieves job audit log", 1, userDAOJobLogs.size());
            assertEquals("AdminUserDAO.getAuditLogsForJob maps entity_type to JOB",
                    AdminAuditEntityType.JOB, userDAOJobLogs.get(0).getEntityType());

            List<AdminAuditLog> userDAORecLogs = adminUserDAO.getAuditLogsForUser(testRec1Id);
            assertFalse("AdminUserDAO.getAuditLogsForUser retrieves logs for testRec1Id", userDAORecLogs.isEmpty());
            boolean hasRecProfileLog = userDAORecLogs.stream().anyMatch(
                    l -> l.getEntityType() == AdminAuditEntityType.RECRUITER_PROFILE);
            assertTrue("AdminUserDAO.getAuditLogsForUser correctly mapped RECRUITER_PROFILE entity", hasRecProfileLog);

        } catch (SQLException e) {
            fail("Live database suite encountered unexpected SQLException: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // -----------------------------------------------------------------
            // 6. 100% Teardown & Baseline Verification
            // -----------------------------------------------------------------
            System.out.println("  -> Executing teardown and verifying database baseline preservation...");
            teardownFixtures();

            BaselineCounts afterTeardown = readCounts();
            System.out.printf("  [INFO] After-teardown counts: users=%d (exp %d), recruiter_profiles=%d (exp %d), jobs=%d (exp %d), audit_logs=%d (exp %d)%n",
                    afterTeardown.users, baseline.users,
                    afterTeardown.recruiterProfiles, baseline.recruiterProfiles,
                    afterTeardown.jobs, baseline.jobs,
                    afterTeardown.auditLogs, baseline.auditLogs);

            assertEquals("Database users row count restored to baseline", baseline.users, afterTeardown.users);
            assertEquals("Database recruiter_profiles row count restored to baseline", baseline.recruiterProfiles, afterTeardown.recruiterProfiles);
            assertEquals("Database jobs row count restored to baseline", baseline.jobs, afterTeardown.jobs);
            assertEquals("Database admin_audit_logs row count restored to baseline", baseline.auditLogs, afterTeardown.auditLogs);
            assertEquals("Database applications row count restored", baseline.applications, afterTeardown.applications);
            assertEquals("Database resumes row count restored", baseline.resumes, afterTeardown.resumes);
            assertEquals("Database resume_analyses row count restored", baseline.resumeAnalyses, afterTeardown.resumeAnalyses);
            assertEquals("Database student_profiles row count restored", baseline.studentProfiles, afterTeardown.studentProfiles);
        }
    }

    private boolean afterModMod(Optional<Job> opt) {
        return opt.isPresent();
    }

    // =========================================================================
    // SECTION 3: Student Job Visibility Security Suite (Step 4.2B.2.1)
    // =========================================================================

    private void runStudentJobVisibilitySecuritySuite() {
        System.out.println("\n--- [3] Running Student Job Visibility Security Suite (Step 4.2B.2.1) ---");

        teardownFixtures();

        BaselineCounts baseline = readCounts();
        System.out.printf("  [INFO] Initial Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d%n",
                baseline.users, baseline.recruiterProfiles, baseline.jobs, baseline.auditLogs);

        RecruiterDAO recruiterDAO = new RecruiterDAO();
        JobDAO jobDAO = new JobDAO();

        try {
            // Setup Security Test Recruiters
            int adminId = insertUser(PREFIX + "sec_admin@test.local", "Sec Admin", User.Role.ADMIN, true);
            assertTrue("Created Security Test Admin (id=" + adminId + ")", adminId > 0);

            // Recruiter A: Active user, VERIFIED profile
            int recAId = insertUser(PREFIX + "sec_rec_ver@test.local", "Recruiter A (Verified)", User.Role.RECRUITER, true);
            RecruiterProfile profA = new RecruiterProfile(recAId, PREFIX + "Corp_A_Verified", "https://corpa.test", "Desc A", "Tech");
            profA.setVerificationStatus(RecruiterVerificationStatus.VERIFIED);
            profA.setVerifiedAt(LocalDateTime.now());
            profA.setVerifiedBy(adminId);
            profA.setVerificationReason("Verified Corporate");
            recruiterDAO.insert(profA);
            assertTrue("Recruiter A profile inserted", profA.getId() > 0);

            // Recruiter B: Active user, UNVERIFIED profile
            int recBId = insertUser(PREFIX + "sec_rec_unver@test.local", "Recruiter B (Unverified)", User.Role.RECRUITER, true);
            RecruiterProfile profB = new RecruiterProfile(recBId, PREFIX + "Corp_B_Unverified", "https://corpb.test", "Desc B", "Retail");
            profB.setVerificationStatus(RecruiterVerificationStatus.UNVERIFIED);
            recruiterDAO.insert(profB);
            assertTrue("Recruiter B profile inserted", profB.getId() > 0);

            // Recruiter C: Active user, PENDING profile
            int recCId = insertUser(PREFIX + "sec_rec_pend@test.local", "Recruiter C (Pending)", User.Role.RECRUITER, true);
            RecruiterProfile profC = new RecruiterProfile(recCId, PREFIX + "Corp_C_Pending", "https://corpc.test", "Desc C", "Finance");
            profC.setVerificationStatus(RecruiterVerificationStatus.PENDING);
            recruiterDAO.insert(profC);
            assertTrue("Recruiter C profile inserted", profC.getId() > 0);

            // Recruiter D: Active user, REJECTED profile
            int recDId = insertUser(PREFIX + "sec_rec_rej@test.local", "Recruiter D (Rejected)", User.Role.RECRUITER, true);
            RecruiterProfile profD = new RecruiterProfile(recDId, PREFIX + "Corp_D_Rejected", "https://corpd.test", "Desc D", "Healthcare");
            profD.setVerificationStatus(RecruiterVerificationStatus.REJECTED);
            profD.setVerificationReason("Fraudulent documentation");
            profD.setVerifiedBy(adminId);
            profD.setVerifiedAt(LocalDateTime.now());
            recruiterDAO.insert(profD);
            assertTrue("Recruiter D profile inserted", profD.getId() > 0);

            // Recruiter E: Active user, SUSPENDED profile
            int recEId = insertUser(PREFIX + "sec_rec_susp@test.local", "Recruiter E (Suspended)", User.Role.RECRUITER, true);
            RecruiterProfile profE = new RecruiterProfile(recEId, PREFIX + "Corp_E_Suspended", "https://corpe.test", "Desc E", "Consulting");
            profE.setVerificationStatus(RecruiterVerificationStatus.SUSPENDED);
            profE.setVerificationReason("Policy violation");
            profE.setVerifiedBy(adminId);
            profE.setVerifiedAt(LocalDateTime.now());
            recruiterDAO.insert(profE);
            assertTrue("Recruiter E profile inserted", profE.getId() > 0);

            // Recruiter F: DEACTIVATED user (is_active = false), VERIFIED profile
            int recFId = insertUser(PREFIX + "sec_rec_deact@test.local", "Recruiter F (Deactivated)", User.Role.RECRUITER, false);
            RecruiterProfile profF = new RecruiterProfile(recFId, PREFIX + "Corp_F_Deact", "https://corpf.test", "Desc F", "Education");
            profF.setVerificationStatus(RecruiterVerificationStatus.VERIFIED);
            profF.setVerifiedAt(LocalDateTime.now());
            profF.setVerifiedBy(adminId);
            recruiterDAO.insert(profF);
            assertTrue("Recruiter F profile inserted", profF.getId() > 0);

            // Recruiter G: Active user (is_active = true), NO profile created
            int recGId = insertUser(PREFIX + "sec_rec_noprof@test.local", "Recruiter G (No Profile)", User.Role.RECRUITER, true);
            assertTrue("Recruiter G user inserted without profile", recGId > 0);

            // Setup 15 Jobs representing the 15 Test Scenarios
            // Scenario 1: Active recruiter, verified profile, active approved job, valid deadline -> VISIBLE
            Job jobS1 = new Job(recAId, PREFIX + "JOB_S01_VALID", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS1.setModerationStatus(JobModerationStatus.APPROVED);
            int idS1 = jobDAO.insert(jobS1);
            assertTrue("Job S1 inserted (id=" + idS1 + ")", idS1 > 0);

            // Scenario 2: Recruiter profile is UNVERIFIED -> HIDDEN
            Job jobS2 = new Job(recBId, PREFIX + "JOB_S02_REC_UNVERIFIED", PREFIX + "Corp_B_Unverified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS2.setModerationStatus(JobModerationStatus.APPROVED);
            int idS2 = jobDAO.insert(jobS2);
            assertTrue("Job S2 inserted (id=" + idS2 + ")", idS2 > 0);

            // Scenario 3: Recruiter profile is PENDING -> HIDDEN
            Job jobS3 = new Job(recCId, PREFIX + "JOB_S03_REC_PENDING", PREFIX + "Corp_C_Pending", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS3.setModerationStatus(JobModerationStatus.APPROVED);
            int idS3 = jobDAO.insert(jobS3);
            assertTrue("Job S3 inserted (id=" + idS3 + ")", idS3 > 0);

            // Scenario 4: Recruiter profile is REJECTED -> HIDDEN
            Job jobS4 = new Job(recDId, PREFIX + "JOB_S04_REC_REJECTED", PREFIX + "Corp_D_Rejected", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS4.setModerationStatus(JobModerationStatus.APPROVED);
            int idS4 = jobDAO.insert(jobS4);
            assertTrue("Job S4 inserted (id=" + idS4 + ")", idS4 > 0);

            // Scenario 5: Recruiter profile is SUSPENDED -> HIDDEN
            Job jobS5 = new Job(recEId, PREFIX + "JOB_S05_REC_SUSPENDED", PREFIX + "Corp_E_Suspended", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS5.setModerationStatus(JobModerationStatus.APPROVED);
            int idS5 = jobDAO.insert(jobS5);
            assertTrue("Job S5 inserted (id=" + idS5 + ")", idS5 > 0);

            // Scenario 6: Recruiter account is deactivated -> HIDDEN
            Job jobS6 = new Job(recFId, PREFIX + "JOB_S06_REC_DEACTIVATED", PREFIX + "Corp_F_Deact", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS6.setModerationStatus(JobModerationStatus.APPROVED);
            int idS6 = jobDAO.insert(jobS6);
            assertTrue("Job S6 inserted (id=" + idS6 + ")", idS6 > 0);

            // Scenario 7: Recruiter account is active but has no recruiter profile -> HIDDEN
            Job jobS7 = new Job(recGId, PREFIX + "JOB_S07_REC_NO_PROFILE", PREFIX + "Corp_G_None", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS7.setModerationStatus(JobModerationStatus.APPROVED);
            int idS7 = jobDAO.insert(jobS7);
            assertTrue("Job S7 inserted (id=" + idS7 + ")", idS7 > 0);

            // Scenario 8: Job moderation is PENDING -> HIDDEN
            Job jobS8 = new Job(recAId, PREFIX + "JOB_S08_MOD_PENDING", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS8.setModerationStatus(JobModerationStatus.PENDING);
            int idS8 = jobDAO.insert(jobS8);
            assertTrue("Job S8 inserted (id=" + idS8 + ")", idS8 > 0);

            // Scenario 9: Job moderation is REJECTED -> HIDDEN
            Job jobS9 = new Job(recAId, PREFIX + "JOB_S09_MOD_REJECTED", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS9.setModerationStatus(JobModerationStatus.REJECTED);
            int idS9 = jobDAO.insert(jobS9);
            assertTrue("Job S9 inserted (id=" + idS9 + ")", idS9 > 0);

            // Scenario 10: Job moderation is SUSPENDED -> HIDDEN
            Job jobS10 = new Job(recAId, PREFIX + "JOB_S10_MOD_SUSPENDED", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.ACTIVE);
            jobS10.setModerationStatus(JobModerationStatus.SUSPENDED);
            int idS10 = jobDAO.insert(jobS10);
            assertTrue("Job S10 inserted (id=" + idS10 + ")", idS10 > 0);

            // Scenario 11: Job lifecycle is DRAFT -> HIDDEN
            Job jobS11 = new Job(recAId, PREFIX + "JOB_S11_LIFE_DRAFT", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.DRAFT);
            jobS11.setModerationStatus(JobModerationStatus.APPROVED);
            int idS11 = jobDAO.insert(jobS11);
            assertTrue("Job S11 inserted (id=" + idS11 + ")", idS11 > 0);

            // Scenario 12: Job lifecycle is CLOSED -> HIDDEN
            Job jobS12 = new Job(recAId, PREFIX + "JOB_S12_LIFE_CLOSED", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().plusMonths(1), Job.Status.CLOSED);
            jobS12.setModerationStatus(JobModerationStatus.APPROVED);
            int idS12 = jobDAO.insert(jobS12);
            assertTrue("Job S12 inserted (id=" + idS12 + ")", idS12 > 0);

            // Scenario 13: Job deadline has expired (yesterday) -> HIDDEN
            Job jobS13 = new Job(recAId, PREFIX + "JOB_S13_EXPIRED", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now().minusDays(1), Job.Status.ACTIVE);
            jobS13.setModerationStatus(JobModerationStatus.APPROVED);
            int idS13 = jobDAO.insert(jobS13);
            assertTrue("Job S13 inserted (id=" + idS13 + ")", idS13 > 0);

            // Scenario 14: Job deadline is today -> VISIBLE
            Job jobS14 = new Job(recAId, PREFIX + "JOB_S14_TODAY", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", LocalDate.now(), Job.Status.ACTIVE);
            jobS14.setModerationStatus(JobModerationStatus.APPROVED);
            int idS14 = jobDAO.insert(jobS14);
            assertTrue("Job S14 inserted (id=" + idS14 + ")", idS14 > 0);

            // Scenario 15: Job deadline is NULL -> VISIBLE
            Job jobS15 = new Job(recAId, PREFIX + "JOB_S15_NULL_DEADLINE", PREFIX + "Corp_A_Verified", "Desc", "Java", "Remote",
                    Job.JobType.FULL_TIME, "$100k", "BS CS", null, Job.Status.ACTIVE);
            jobS15.setModerationStatus(JobModerationStatus.APPROVED);
            int idS15 = jobDAO.insert(jobS15);
            assertTrue("Job S15 inserted (id=" + idS15 + ")", idS15 > 0);

            // -----------------------------------------------------------------
            // VERIFY 15 SCENARIOS ON findOpenApprovedActiveJobs()
            // -----------------------------------------------------------------
            List<Job> openApproved = jobDAO.findOpenApprovedActiveJobs();
            final int fidS1 = idS1, fidS2 = idS2, fidS3 = idS3, fidS4 = idS4, fidS5 = idS5;
            final int fidS6 = idS6, fidS7 = idS7, fidS8 = idS8, fidS9 = idS9, fidS10 = idS10;
            final int fidS11 = idS11, fidS12 = idS12, fidS13 = idS13, fidS14 = idS14, fidS15 = idS15;

            assertTrue("Scenario 1: S1 (verified active approved valid) is VISIBLE in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS1));
            assertFalse("Scenario 2: S2 (UNVERIFIED recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS2));
            assertFalse("Scenario 3: S3 (PENDING recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS3));
            assertFalse("Scenario 4: S4 (REJECTED recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS4));
            assertFalse("Scenario 5: S5 (SUSPENDED recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS5));
            assertFalse("Scenario 6: S6 (DEACTIVATED recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS6));
            assertFalse("Scenario 7: S7 (NO PROFILE recruiter) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS7));
            assertFalse("Scenario 8: S8 (PENDING job moderation) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS8));
            assertFalse("Scenario 9: S9 (REJECTED job moderation) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS9));
            assertFalse("Scenario 10: S10 (SUSPENDED job moderation) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS10));
            assertFalse("Scenario 11: S11 (DRAFT job lifecycle) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS11));
            assertFalse("Scenario 12: S12 (CLOSED job lifecycle) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS12));
            assertFalse("Scenario 13: S13 (EXPIRED job deadline) is HIDDEN in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS13));
            assertTrue("Scenario 14: S14 (TODAY job deadline) is VISIBLE in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS14));
            assertTrue("Scenario 15: S15 (NULL job deadline) is VISIBLE in findOpenApprovedActiveJobs",
                    openApproved.stream().anyMatch(j -> j.getId() == fidS15));

            // -----------------------------------------------------------------
            // VERIFY 15 SCENARIOS ON searchApprovedActive(PREFIX, "", "")
            // -----------------------------------------------------------------
            List<Job> searchedApproved = jobDAO.searchApprovedActive(PREFIX, "", "");
            assertEquals("searchApprovedActive returns exactly 3 visible jobs matching PREFIX", 3, searchedApproved.size());
            assertTrue("searchApprovedActive contains Job S1", searchedApproved.stream().anyMatch(j -> j.getId() == fidS1));
            assertTrue("searchApprovedActive contains Job S14", searchedApproved.stream().anyMatch(j -> j.getId() == fidS14));
            assertTrue("searchApprovedActive contains Job S15", searchedApproved.stream().anyMatch(j -> j.getId() == fidS15));
            assertFalse("searchApprovedActive excludes S2", searchedApproved.stream().anyMatch(j -> j.getId() == fidS2));
            assertFalse("searchApprovedActive excludes S3", searchedApproved.stream().anyMatch(j -> j.getId() == fidS3));
            assertFalse("searchApprovedActive excludes S4", searchedApproved.stream().anyMatch(j -> j.getId() == fidS4));
            assertFalse("searchApprovedActive excludes S5", searchedApproved.stream().anyMatch(j -> j.getId() == fidS5));
            assertFalse("searchApprovedActive excludes S6", searchedApproved.stream().anyMatch(j -> j.getId() == fidS6));
            assertFalse("searchApprovedActive excludes S7", searchedApproved.stream().anyMatch(j -> j.getId() == fidS7));
            assertFalse("searchApprovedActive excludes S8", searchedApproved.stream().anyMatch(j -> j.getId() == fidS8));
            assertFalse("searchApprovedActive excludes S9", searchedApproved.stream().anyMatch(j -> j.getId() == fidS9));
            assertFalse("searchApprovedActive excludes S10", searchedApproved.stream().anyMatch(j -> j.getId() == fidS10));
            assertFalse("searchApprovedActive excludes S11", searchedApproved.stream().anyMatch(j -> j.getId() == fidS11));
            assertFalse("searchApprovedActive excludes S12", searchedApproved.stream().anyMatch(j -> j.getId() == fidS12));
            assertFalse("searchApprovedActive excludes S13", searchedApproved.stream().anyMatch(j -> j.getId() == fidS13));

            // -----------------------------------------------------------------
            // VERIFY KEYWORD SEARCH CANNOT EXPOSE HIDDEN JOBS
            // -----------------------------------------------------------------
            for (int i = 2; i <= 13; i++) {
                String title = PREFIX + String.format("JOB_S%02d", i);
                List<Job> hiddenSearch = jobDAO.searchApprovedActive(title, "", "");
                assertEquals("Keyword search for hidden job " + title + " returns 0 results", 0, hiddenSearch.size());
            }
            assertEquals("Keyword search for visible job S01 returns exactly 1 result", 1,
                    jobDAO.searchApprovedActive(PREFIX + "JOB_S01", "", "").size());
            assertEquals("Keyword search for visible job S14 returns exactly 1 result", 1,
                    jobDAO.searchApprovedActive(PREFIX + "JOB_S14", "", "").size());
            assertEquals("Keyword search for visible job S15 returns exactly 1 result", 1,
                    jobDAO.searchApprovedActive(PREFIX + "JOB_S15", "", "").size());

            // -----------------------------------------------------------------
            // VERIFY PAGINATION DOES NOT BYPASS VISIBILITY RESTRICTIONS
            // -----------------------------------------------------------------
            List<Job> page1 = jobDAO.searchApprovedActive(PREFIX, "", "", 2, 0);
            assertEquals("Page 1 (limit 2, offset 0) returns 2 visible jobs", 2, page1.size());
            assertTrue("Page 1 all jobs are in {S1, S14, S15}",
                    page1.stream().allMatch(j -> j.getId() == fidS1 || j.getId() == fidS14 || j.getId() == fidS15));

            List<Job> page2 = jobDAO.searchApprovedActive(PREFIX, "", "", 2, 2);
            assertEquals("Page 2 (limit 2, offset 2) returns 1 visible job", 1, page2.size());
            assertTrue("Page 2 job is in {S1, S14, S15}",
                    page2.stream().allMatch(j -> j.getId() == fidS1 || j.getId() == fidS14 || j.getId() == fidS15));

            List<Job> page3 = jobDAO.searchApprovedActive(PREFIX, "", "", 2, 4);
            assertEquals("Page 3 (limit 2, offset 4) returns 0 jobs", 0, page3.size());

            // -----------------------------------------------------------------
            // VERIFY RECRUITER-OWNED QUERIES STILL RETURN RECRUITER'S MANAGEABLE JOBS
            // -----------------------------------------------------------------
            List<Job> recAJobs = jobDAO.findByRecruiterId(recAId);
            assertEquals("Recruiter A findByRecruiterId returns all 9 jobs owned by Recruiter A", 9, recAJobs.size());
            assertTrue("Recruiter A can find own draft job (S11) via findByIdAndRecruiter",
                    jobDAO.findByIdAndRecruiter(idS11, recAId).isPresent());
            assertTrue("Recruiter A can find own closed job (S12) via findByIdAndRecruiter",
                    jobDAO.findByIdAndRecruiter(idS12, recAId).isPresent());
            assertTrue("Recruiter A can find own suspended job (S10) via findByIdAndRecruiter",
                    jobDAO.findByIdAndRecruiter(idS10, recAId).isPresent());
            assertTrue("Recruiter A can find own expired job (S13) via findByIdAndRecruiter",
                    jobDAO.findByIdAndRecruiter(idS13, recAId).isPresent());
            List<Job> recBJobs = jobDAO.findByRecruiterId(recBId);
            assertEquals("Recruiter B can find own job (S2) via findByRecruiterId", 1, recBJobs.size());

            // -----------------------------------------------------------------
            // VERIFY ADMIN JOB SEARCHES STILL RETURN ALL MODERATED & UNMODERATED RECORDS
            // -----------------------------------------------------------------
            List<Job> adminAll = jobDAO.searchJobsForAdmin(PREFIX, null, null, 50, 0);
            assertEquals("Admin search returns all 15 test jobs", 15, adminAll.size());
            assertEquals("Admin countJobsForAdmin matches 15", 15, jobDAO.countJobsForAdmin(PREFIX, null, null));

            assertEquals("Admin search for PENDING moderation finds exactly 1 job (S8)", 1,
                    jobDAO.searchJobsForAdmin(PREFIX, null, JobModerationStatus.PENDING, 50, 0).size());
            assertEquals("Admin search for REJECTED moderation finds exactly 1 job (S9)", 1,
                    jobDAO.searchJobsForAdmin(PREFIX, null, JobModerationStatus.REJECTED, 50, 0).size());
            assertEquals("Admin search for SUSPENDED moderation finds exactly 1 job (S10)", 1,
                    jobDAO.searchJobsForAdmin(PREFIX, null, JobModerationStatus.SUSPENDED, 50, 0).size());
            assertEquals("Admin search for DRAFT lifecycle finds exactly 1 job (S11)", 1,
                    jobDAO.searchJobsForAdmin(PREFIX, Job.Status.DRAFT, null, 50, 0).size());
            assertEquals("Admin search for CLOSED lifecycle finds exactly 1 job (S12)", 1,
                    jobDAO.searchJobsForAdmin(PREFIX, Job.Status.CLOSED, null, 50, 0).size());

            // -----------------------------------------------------------------
            // VERIFY NO DUPLICATE JOBS RETURNED BECAUSE OF JOINS
            // -----------------------------------------------------------------
            long distinctSearchCount = searchedApproved.stream().map(Job::getId).distinct().count();
            assertEquals("No duplicate jobs in searchApprovedActive results", (long) searchedApproved.size(), distinctSearchCount);

            long distinctOpenCount = openApproved.stream().map(Job::getId).distinct().count();
            assertEquals("No duplicate jobs in findOpenApprovedActiveJobs results", (long) openApproved.size(), distinctOpenCount);

        } catch (SQLException e) {
            fail("Security test suite encountered unexpected SQLException: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("  -> Executing security suite teardown and verifying database baseline...");
            teardownFixtures();

            BaselineCounts after = readCounts();
            System.out.printf("  [INFO] After-teardown counts: users=%d (exp %d), recruiter_profiles=%d (exp %d), jobs=%d (exp %d), audit_logs=%d (exp %d)%n",
                    after.users, baseline.users, after.recruiterProfiles, baseline.recruiterProfiles,
                    after.jobs, baseline.jobs, after.auditLogs, baseline.auditLogs);

            assertEquals("Security Suite: Database users row count restored to baseline", baseline.users, after.users);
            assertEquals("Security Suite: Database recruiter_profiles row count restored to baseline", baseline.recruiterProfiles, after.recruiterProfiles);
            assertEquals("Security Suite: Database jobs row count restored to baseline", baseline.jobs, after.jobs);
            assertEquals("Security Suite: Database admin_audit_logs row count restored to baseline", baseline.auditLogs, after.auditLogs);
            assertEquals("Security Suite: Database applications row count restored", baseline.applications, after.applications);
            assertEquals("Security Suite: Database resumes row count restored", baseline.resumes, after.resumes);
            assertEquals("Security Suite: Database resume_analyses row count restored", baseline.resumeAnalyses, after.resumeAnalyses);
            assertEquals("Security Suite: Database student_profiles row count restored", baseline.studentProfiles, after.studentProfiles);
        }
    }

    // =========================================================================
    // Database Helpers & Fixture Management
    // =========================================================================

    private int insertUser(String email, String fullName, User.Role role, boolean isActive) {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, fullName);
            stmt.setString(2, email.toLowerCase());
            stmt.setString(3, PasswordUtil.hashPassword(TEST_PASSWORD));
            stmt.setString(4, role.name());
            stmt.setBoolean(5, isActive);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            fail("Failed to insert test user: " + email + " — " + e.getMessage());
        }
        return -1;
    }

    private void teardownFixtures() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Delete audit logs associated with test users or test jobs
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM admin_audit_logs WHERE admin_id IN (SELECT id FROM users WHERE email LIKE ?) " +
                    "   OR target_user_id IN (SELECT id FROM users WHERE email LIKE ?) " +
                    "   OR target_job_id IN (SELECT id FROM jobs WHERE title LIKE ?)")) {
                ps.setString(1, PREFIX + "%");
                ps.setString(2, PREFIX + "%");
                ps.setString(3, PREFIX + "%");
                ps.executeUpdate();
            }

            // 2. Delete test jobs
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM jobs WHERE title LIKE ? " +
                    "   OR recruiter_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                ps.setString(1, PREFIX + "%");
                ps.setString(2, PREFIX + "%");
                ps.executeUpdate();
            }

            // 3. Delete test recruiter profiles
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM recruiter_profiles WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?) " +
                    "   OR company_name LIKE ?")) {
                ps.setString(1, PREFIX + "%");
                ps.setString(2, PREFIX + "%");
                ps.executeUpdate();
            }

            // 4. Delete test users
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE email LIKE ?")) {
                ps.setString(1, PREFIX + "%");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Teardown error: " + e.getMessage());
        }
    }

    private record BaselineCounts(int users, int auditLogs, int recruiterProfiles, int jobs,
                                  int applications, int resumes, int resumeAnalyses, int studentProfiles) {}

    private BaselineCounts readCounts() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new BaselineCounts(
                    count(conn, "users"),
                    count(conn, "admin_audit_logs"),
                    count(conn, "recruiter_profiles"),
                    count(conn, "jobs"),
                    count(conn, "applications"),
                    count(conn, "resumes"),
                    count(conn, "resume_analyses"),
                    count(conn, "student_profiles")
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read database counts: " + e.getMessage(), e);
        }
    }

    private int count(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // =========================================================================
    // Assertion & Reporting Utilities
    // =========================================================================

    private void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            passed++;
        } else {
            System.err.println("  [FAIL] " + message);
            failed++;
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) {
            assertTrue(message + " (expected null, got null)", true);
        } else if (expected != null && expected.equals(actual)) {
            assertTrue(message + " (value=" + actual + ")", true);
        } else {
            System.err.printf("  [FAIL] %s: expected [%s] but got [%s]%n", message, expected, actual);
            failed++;
        }
    }

    private void assertNull(String message, Object actual) {
        assertTrue(message, actual == null);
    }

    private void assertNotNull(String message, Object actual) {
        assertTrue(message, actual != null);
    }

    private void fail(String message) {
        System.err.println("  [FAIL] " + message);
        failed++;
    }

    private void checkMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            assertTrue(clazz.getSimpleName() + "." + methodName + " exists with matching signature", m != null);
        } catch (NoSuchMethodException e) {
            fail(clazz.getSimpleName() + "." + methodName + " does NOT exist with requested parameter types");
        }
    }
}
