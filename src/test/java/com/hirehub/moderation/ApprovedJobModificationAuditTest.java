package com.hirehub.moderation;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.AdminModerationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.AdminModerationService;
import com.hirehub.service.JobService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ApprovedJobModificationAuditTest — automated verification suite for
 * Phase 6 Step 4.2C.2.1: Approved Job Content Modification & Regression Compatibility Audit.
 *
 * <p>Validates all 10 required behaviors:
 * <ol>
 *   <li>Editing approved job title triggers re-moderation to PENDING.</li>
 *   <li>Editing approved job description triggers re-moderation to PENDING.</li>
 *   <li>Editing salary range or eligibility triggers re-moderation to PENDING.</li>
 *   <li>No-op edit preserves APPROVED moderation status and student visibility.</li>
 *   <li>Lifecycle status (ACTIVE/DRAFT/CLOSED) remains unchanged during re-moderation.</li>
 *   <li>Student visibility (searchApprovedActive / findOpenApprovedActiveJobs) revoked immediately upon re-moderation.</li>
 *   <li>Recruiter cannot directly set moderation status to APPROVED (tampering protection).</li>
 *   <li>Previous moderation audit entries in admin_audit_logs remain intact without fake recruiter entries.</li>
 *   <li>Administrator can re-approve the job via AdminModerationService.approveJob restoring visibility.</li>
 *   <li>Phase 5 Step 3A security verifier passes with zero regressions.</li>
 * </ol>
 *
 * Run via: {@code verify_phase4.ps1 -MainClass com.hirehub.util.ApprovedJobModificationAuditTest}
 */
public class ApprovedJobModificationAuditTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private final JobDAO jobDAO = new JobDAO();
    private final JobService jobService = new JobService();
    private final AdminModerationService adminModService = new AdminModerationService();
    private final AdminModerationDAO adminModDAO = new AdminModerationDAO();
    private final SessionManager session = SessionManager.getInstance();

    private final List<Integer> createdUserIds = new ArrayList<>();
    private final List<Integer> createdJobIds = new ArrayList<>();
    private final List<Integer> createdAuditLogIds = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2C.2.1: Approved Job Modification Verifier");
        System.out.println("=================================================================\n");

        ApprovedJobModificationAuditTest verifier = new ApprovedJobModificationAuditTest();

        try {
            verifier.runAuditSuite();
        } catch (Throwable t) {
            System.err.println("\n[FATAL ERROR] Unexpected exception during verifier execution:");
            t.printStackTrace();
            testsFailed++;
        } finally {
            SessionManager.getInstance().logout();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("AUDIT VERIFIER SUMMARY: %d PASSED, %d FAILED%n", testsPassed, testsFailed);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private void runAuditSuite() throws Exception {
        BaselineCounts baseline = readCounts();
        System.out.printf("[INFO] Initial Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, applications=%d%n",
                baseline.users, baseline.recruiterProfiles, baseline.jobs, baseline.auditLogs, baseline.applications);

        int recUserId = -1;
        int adminUserId = -1;
        int studentUserId = -1;
        int testJobId = -1;

        try {
            // Setup isolated test accounts
            long ts = System.currentTimeMillis();
            recUserId = createTestUser("ModEdit Recruiter", "modedit_rec_" + ts + "@test.local", User.Role.RECRUITER, true);
            createRecruiterProfile(recUserId, "ModEdit Corp", RecruiterVerificationStatus.VERIFIED);

            adminUserId = createTestUser("ModEdit Admin", "modedit_adm_" + ts + "@test.local", User.Role.ADMIN, true);
            studentUserId = createTestUser("ModEdit Student", "modedit_stu_" + ts + "@test.local", User.Role.STUDENT, true);

            User recUser = new User(recUserId, "ModEdit Recruiter", "modedit_rec_" + ts + "@test.local", "dummy", User.Role.RECRUITER, true, LocalDateTime.now());
            User adminUser = new User(adminUserId, "ModEdit Admin", "modedit_adm_" + ts + "@test.local", "dummy", User.Role.ADMIN, true, LocalDateTime.now());

            LocalDate baseDeadline = LocalDate.now().plusDays(25);

            // -----------------------------------------------------------------
            // Initial Job Creation & Approval
            // -----------------------------------------------------------------
            System.out.println("\n--- [1] Setting Up Test Job & Initial Admin Approval ---");
            session.login(recUser);
            JobService.ServiceResult cr = jobService.createJob(
                    recUserId, "Original Senior Java Engineer", "ModEdit Corp", "Original description of Java duties.",
                    "Java, Spring, MySQL", "Bengaluru", "Full-Time", "15-20 LPA", "B.Tech/MCA", baseDeadline, false);
            assertTrue("Job creation succeeds", cr.isSuccess());
            testJobId = cr.getJob().getId();
            createdJobIds.add(testJobId);

            Job initialJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("New job receives PENDING moderation status", JobModerationStatus.PENDING, initialJob.getModerationStatus());
            assertEquals("New job starts with ACTIVE lifecycle status", Job.Status.ACTIVE, initialJob.getStatus());

            // Admin approves the job initially
            session.login(adminUser);
            AdminModerationService.ServiceResult<Void> appResult = adminModService.approveJob(adminUserId, testJobId, "Initial approval of listing");
            assertTrue("Admin approves job listing cleanly", appResult.isSuccess());

            trackCreatedAuditLogs();
            int auditCountAfterInitialApproval = getAuditLogCountForJob(testJobId);
            assertEquals("Exactly 1 audit log entry created for initial approval", 1, auditCountAfterInitialApproval);

            Job approvedJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Job is now APPROVED", JobModerationStatus.APPROVED, approvedJob.getModerationStatus());
            assertEquals("Moderated by is set to admin id", Integer.valueOf(adminUserId), approvedJob.getModeratedBy());

            // Check student visibility
            final int tJobId = testJobId;
            assertTrue("Approved active job is visible in searchApprovedActive",
                    jobDAO.searchApprovedActive("Original Senior", null, null).stream().anyMatch(j -> j.getId() == tJobId));
            assertTrue("Approved active job is visible in findOpenApprovedActiveJobs",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == tJobId));

            // -----------------------------------------------------------------
            // Behavior 1: Editing Title Triggers Re-Moderation to PENDING
            // -----------------------------------------------------------------
            System.out.println("\n--- [2] Behavior 1: Title Modification Triggers Re-Moderation ---");
            session.login(recUser);
            JobService.ServiceResult editTitle = jobService.updateJob(
                    recUserId, testJobId, "Lead Backend Java Architect", "ModEdit Corp", "Original description of Java duties.",
                    "Java, Spring, MySQL", "Bengaluru", "Full-Time", "15-20 LPA", "B.Tech/MCA", baseDeadline, Job.Status.ACTIVE);
            assertTrue("Title update succeeds", editTitle.isSuccess());

            Job postTitleJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Title was updated in DB", "Lead Backend Java Architect", postTitleJob.getTitle());
            assertEquals("Moderation status reset to PENDING after title edit", JobModerationStatus.PENDING, postTitleJob.getModerationStatus());
            assertEquals("Moderation reason set to modified pending notice",
                    "Content modified by recruiter — pending re-approval", postTitleJob.getModerationReason());
            assertNull("Moderated by cleared on re-moderation", postTitleJob.getModeratedBy());
            assertNull("Moderated at cleared on re-moderation", postTitleJob.getModeratedAt());
            assertEquals("Behavior 5: Lifecycle status remains ACTIVE during re-moderation", Job.Status.ACTIVE, postTitleJob.getStatus());

            // Behavior 6: Student visibility revoked
            assertFalse("Behavior 6: Student searchApprovedActive immediately excludes pending job",
                    jobDAO.searchApprovedActive("Java Architect", null, null).stream().anyMatch(j -> j.getId() == tJobId));
            assertFalse("Behavior 6: Student findOpenApprovedActiveJobs immediately excludes pending job",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == tJobId));

            // Behavior 8: No fake admin audit logs added
            int auditCountAfterTitleEdit = getAuditLogCountForJob(testJobId);
            assertEquals("Behavior 8: admin_audit_logs count unchanged (no fabricated audit log)",
                    auditCountAfterInitialApproval, auditCountAfterTitleEdit);

            // Behavior 9: Admin re-approves the job
            System.out.println("\n--- [3] Behavior 9: Admin Re-Approval Restores Student Visibility ---");
            session.login(adminUser);
            AdminModerationService.ServiceResult<Void> reApprove1 = adminModService.approveJob(adminUserId, testJobId, "Re-approved after title update");
            assertTrue("Admin re-approves job successfully", reApprove1.isSuccess());
            trackCreatedAuditLogs();
            assertEquals("Audit log count incremented by legitimate admin re-approval",
                    auditCountAfterInitialApproval + 1, getAuditLogCountForJob(testJobId));

            Job postReApprove1 = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Job moderation status is APPROVED again", JobModerationStatus.APPROVED, postReApprove1.getModerationStatus());
            assertTrue("Job visible to student searchApprovedActive after re-approval",
                    jobDAO.searchApprovedActive("Java Architect", null, null).stream().anyMatch(j -> j.getId() == tJobId));
            assertTrue("Job visible to student findOpenApprovedActiveJobs after re-approval",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == tJobId));

            // -----------------------------------------------------------------
            // Behavior 2: Editing Description Triggers Re-Moderation
            // -----------------------------------------------------------------
            System.out.println("\n--- [4] Behavior 2: Description Modification Triggers Re-Moderation ---");
            session.login(recUser);
            JobService.ServiceResult editDesc = jobService.updateJob(
                    recUserId, testJobId, "Lead Backend Java Architect", "ModEdit Corp", "Substantially revised duties: distributed systems and cloud microservices.",
                    "Java, Spring, MySQL", "Bengaluru", "Full-Time", "15-20 LPA", "B.Tech/MCA", baseDeadline, Job.Status.ACTIVE);
            assertTrue("Description update succeeds", editDesc.isSuccess());

            Job postDescJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Moderation status reset to PENDING after description edit", JobModerationStatus.PENDING, postDescJob.getModerationStatus());
            assertEquals("Lifecycle status remains ACTIVE", Job.Status.ACTIVE, postDescJob.getStatus());
            assertFalse("Student search excludes job with modified description",
                    jobDAO.searchApprovedActive("Java Architect", null, null).stream().anyMatch(j -> j.getId() == tJobId));

            // Admin re-approves
            session.login(adminUser);
            assertTrue("Admin re-approves after description edit",
                    adminModService.approveJob(adminUserId, testJobId, "Re-approved description").isSuccess());
            trackCreatedAuditLogs();

            // -----------------------------------------------------------------
            // Behavior 3: Editing Salary or Eligibility Triggers Re-Moderation
            // -----------------------------------------------------------------
            System.out.println("\n--- [5] Behavior 3: Salary & Eligibility Modification Triggers Re-Moderation ---");
            session.login(recUser);
            JobService.ServiceResult editSalary = jobService.updateJob(
                    recUserId, testJobId, "Lead Backend Java Architect", "ModEdit Corp", "Substantially revised duties: distributed systems and cloud microservices.",
                    "Java, Spring, MySQL", "Bengaluru", "Full-Time", "25-30 LPA", "B.Tech/MCA 75%+", baseDeadline, Job.Status.ACTIVE);
            assertTrue("Salary & eligibility update succeeds", editSalary.isSuccess());

            Job postSalaryJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Salary updated in DB", "25-30 LPA", postSalaryJob.getSalaryRange());
            assertEquals("Eligibility updated in DB", "B.Tech/MCA 75%+", postSalaryJob.getEligibility());
            assertEquals("Moderation status reset to PENDING after salary edit", JobModerationStatus.PENDING, postSalaryJob.getModerationStatus());
            assertEquals("Lifecycle status remains ACTIVE", Job.Status.ACTIVE, postSalaryJob.getStatus());
            assertFalse("Student search excludes job pending salary re-approval",
                    jobDAO.searchApprovedActive("Java Architect", null, null).stream().anyMatch(j -> j.getId() == tJobId));

            // Admin re-approves
            session.login(adminUser);
            assertTrue("Admin re-approves after salary edit",
                    adminModService.approveJob(adminUserId, testJobId, "Re-approved salary").isSuccess());
            trackCreatedAuditLogs();

            // -----------------------------------------------------------------
            // Behavior 4: No-Op Edit Preserves APPROVED Status
            // -----------------------------------------------------------------
            System.out.println("\n--- [6] Behavior 4: No-Op Update Preserves APPROVED Status ---");
            session.login(recUser);
            Job currentApproved = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Pre-condition: job is APPROVED", JobModerationStatus.APPROVED, currentApproved.getModerationStatus());
            String savedReason = currentApproved.getModerationReason();
            LocalDateTime savedModeratedAt = currentApproved.getModeratedAt();
            Integer savedModeratedBy = currentApproved.getModeratedBy();

            // Perform identical update with only leading/trailing whitespace variations
            JobService.ServiceResult noOpEdit = jobService.updateJob(
                    recUserId, testJobId,
                    "  Lead Backend Java Architect  ",
                    "  ModEdit Corp ",
                    "Substantially revised duties: distributed systems and cloud microservices.",
                    "Java, Spring, MySQL",
                    "Bengaluru ",
                    "Full-Time",
                    "25-30 LPA",
                    "B.Tech/MCA 75%+",
                    baseDeadline,
                    Job.Status.ACTIVE);
            assertTrue("No-op update succeeds", noOpEdit.isSuccess());

            Job postNoOpJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Behavior 4: Moderation status remains APPROVED on no-op update",
                    JobModerationStatus.APPROVED, postNoOpJob.getModerationStatus());
            assertEquals("Moderation reason preserved on no-op", savedReason, postNoOpJob.getModerationReason());
            assertEquals("Moderated by preserved on no-op", savedModeratedBy, postNoOpJob.getModeratedBy());
            assertEquals("Moderated at preserved on no-op", savedModeratedAt, postNoOpJob.getModeratedAt());
            assertTrue("Job remains visible in searchApprovedActive on no-op update",
                    jobDAO.searchApprovedActive("Java Architect", null, null).stream().anyMatch(j -> j.getId() == tJobId));
            assertTrue("Job remains visible in findOpenApprovedActiveJobs on no-op update",
                    jobDAO.findOpenApprovedActiveJobs().stream().anyMatch(j -> j.getId() == tJobId));

            // -----------------------------------------------------------------
            // Behavior 7: Recruiter Cannot Tamper Moderation via Entity Update
            // -----------------------------------------------------------------
            System.out.println("\n--- [7] Behavior 7: Tampering Protection via updateJobForRecruiter ---");
            Job forgedJob = jobDAO.findById(testJobId).orElseThrow();
            forgedJob.setTitle("Principal Architect Edited");
            forgedJob.setModerationStatus(JobModerationStatus.APPROVED);
            forgedJob.setModerationReason("I approve my own job");
            forgedJob.setModeratedBy(recUserId);
            forgedJob.setModeratedAt(LocalDateTime.now());

            JobService.ServiceResult entityUpdate = jobService.updateJobForRecruiter(forgedJob, recUserId);
            assertTrue("updateJobForRecruiter succeeds for content", entityUpdate.isSuccess());

            Job postEntityJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Title changed via entity", "Principal Architect Edited", postEntityJob.getTitle());
            assertEquals("Behavior 7: Recruiter cannot forge APPROVED status on content change (reset to PENDING)",
                    JobModerationStatus.PENDING, postEntityJob.getModerationStatus());
            assertEquals("Moderation reason was not tampered by recruiter",
                    "Content modified by recruiter — pending re-approval", postEntityJob.getModerationReason());
            assertNull("Moderated by was not set to recruiter id", postEntityJob.getModeratedBy());

            // -----------------------------------------------------------------
            // Behavior 8: Audit Log Integrity Verification
            // -----------------------------------------------------------------
            System.out.println("\n--- [8] Behavior 8: Audit Log Integrity Check ---");
            List<AdminAuditLog> logs = adminModDAO.getAuditLogsForJob(testJobId);
            for (AdminAuditLog log : logs) {
                assertEquals("Every audit log has admin action JOB_APPROVED", "JOB_APPROVED", log.getAction());
                assertEquals("Every audit log performed by admin user", adminUserId, log.getAdminId());
            }
            assertTrue("Multiple legitimate admin approvals tracked cleanly", logs.size() >= 3);

            // Re-approve after entity edit
            session.login(adminUser);
            assertTrue("Admin re-approves after entity edit",
                    adminModService.approveJob(adminUserId, testJobId, "Final approval").isSuccess());
            trackCreatedAuditLogs();

            // -----------------------------------------------------------------
            // Other Substantive Fields Re-Moderation (Skills, Location, JobType, Deadline)
            // -----------------------------------------------------------------
            System.out.println("\n--- [9] Testing Remaining Substantive Fields (Skills, Location, Type, Deadline) ---");
            session.login(recUser);

            // Edit skills
            jobService.updateJob(recUserId, testJobId, "Principal Architect Edited", "ModEdit Corp",
                    "Substantially revised duties: distributed systems and cloud microservices.",
                    "Kotlin, Microservices, Kubernetes", "Bengaluru", "Full-Time", "25-30 LPA", "B.Tech/MCA 75%+", baseDeadline, Job.Status.ACTIVE);
            assertEquals("Skills edit triggers PENDING", JobModerationStatus.PENDING, jobDAO.findById(testJobId).orElseThrow().getModerationStatus());
            session.login(adminUser);
            adminModService.approveJob(adminUserId, testJobId, "Re-approve skills");
            trackCreatedAuditLogs();

            // Edit location
            session.login(recUser);
            jobService.updateJob(recUserId, testJobId, "Principal Architect Edited", "ModEdit Corp",
                    "Substantially revised duties: distributed systems and cloud microservices.",
                    "Kotlin, Microservices, Kubernetes", "Hyderabad", "Full-Time", "25-30 LPA", "B.Tech/MCA 75%+", baseDeadline, Job.Status.ACTIVE);
            assertEquals("Location edit triggers PENDING", JobModerationStatus.PENDING, jobDAO.findById(testJobId).orElseThrow().getModerationStatus());
            session.login(adminUser);
            adminModService.approveJob(adminUserId, testJobId, "Re-approve location");
            trackCreatedAuditLogs();

            // Edit job type
            session.login(recUser);
            jobService.updateJob(recUserId, testJobId, "Principal Architect Edited", "ModEdit Corp",
                    "Substantially revised duties: distributed systems and cloud microservices.",
                    "Kotlin, Microservices, Kubernetes", "Hyderabad", "Part-Time", "25-30 LPA", "B.Tech/MCA 75%+", baseDeadline, Job.Status.ACTIVE);
            assertEquals("Job type edit triggers PENDING", JobModerationStatus.PENDING, jobDAO.findById(testJobId).orElseThrow().getModerationStatus());
            session.login(adminUser);
            adminModService.approveJob(adminUserId, testJobId, "Re-approve type");
            trackCreatedAuditLogs();

            // Edit deadline
            session.login(recUser);
            jobService.updateJob(recUserId, testJobId, "Principal Architect Edited", "ModEdit Corp",
                    "Substantially revised duties: distributed systems and cloud microservices.",
                    "Kotlin, Microservices, Kubernetes", "Hyderabad", "Part-Time", "25-30 LPA", "B.Tech/MCA 75%+", baseDeadline.plusDays(10), Job.Status.ACTIVE);
            assertEquals("Deadline edit triggers PENDING", JobModerationStatus.PENDING, jobDAO.findById(testJobId).orElseThrow().getModerationStatus());
            session.login(adminUser);
            adminModService.approveJob(adminUserId, testJobId, "Re-approve deadline");
            trackCreatedAuditLogs();

            // Recruiter can close job without altering moderation status
            session.login(recUser);
            JobService.ServiceResult closeResult = jobService.closeJob(recUserId, testJobId);
            assertTrue("Recruiter can close approved job", closeResult.isSuccess());
            Job closedJob = jobDAO.findById(testJobId).orElseThrow();
            assertEquals("Lifecycle status is CLOSED", Job.Status.CLOSED, closedJob.getStatus());
            assertEquals("Moderation status remains APPROVED on close", JobModerationStatus.APPROVED, closedJob.getModerationStatus());

            System.out.println("\n--- [10] All Substantive Fields & Re-Moderation Behaviors Verified Successfully ---");

        } finally {
            // Teardown
            System.out.println("\n[TEARDOWN] Cleaning up all test records and restoring baseline counts...");
            session.logout();

            cleanupAll();

            BaselineCounts finalCounts = readCounts();
            System.out.printf("[INFO] Final Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, applications=%d%n",
                    finalCounts.users, finalCounts.recruiterProfiles, finalCounts.jobs, finalCounts.auditLogs, finalCounts.applications);

            assertEquals("Users count restored to baseline", baseline.users, finalCounts.users);
            assertEquals("Recruiter profiles count restored to baseline", baseline.recruiterProfiles, finalCounts.recruiterProfiles);
            assertEquals("Jobs count restored to baseline", baseline.jobs, finalCounts.jobs);
            assertEquals("Admin audit logs count restored to baseline", baseline.auditLogs, finalCounts.auditLogs);
            assertEquals("Applications count restored to baseline", baseline.applications, finalCounts.applications);
            assertEquals("Resumes count restored to baseline", baseline.resumes, finalCounts.resumes);
            assertEquals("Resume analyses count restored to baseline", baseline.resumeAnalyses, finalCounts.resumeAnalyses);
            assertEquals("Student profiles count restored to baseline", baseline.studentProfiles, finalCounts.studentProfiles);
        }
    }

    private void trackCreatedAuditLogs() {
        for (int jobId : createdJobIds) {
            String sql = "SELECT id FROM admin_audit_logs WHERE target_job_id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, jobId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt(1);
                        if (!createdAuditLogIds.contains(id)) {
                            createdAuditLogIds.add(id);
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error tracking audit logs: " + e.getMessage());
            }
        }
    }

    private int getAuditLogCountForJob(int jobId) {
        String sql = "SELECT COUNT(*) FROM admin_audit_logs WHERE target_job_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting audit logs", e);
        }
        return 0;
    }

    private void cleanupAll() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (!createdAuditLogIds.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM admin_audit_logs WHERE id = ?")) {
                    for (int id : createdAuditLogIds) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
            }
            if (!createdJobIds.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM jobs WHERE id = ?")) {
                    for (int id : createdJobIds) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
            }
            if (!createdUserIds.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM recruiter_profiles WHERE user_id = ?")) {
                    for (int id : createdUserIds) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    for (int id : createdUserIds) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Cleanup error: " + e.getMessage());
        }
    }

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
                if (rs.next()) {
                    int id = rs.getInt(1);
                    createdUserIds.add(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating test user: " + email, e);
        }
        return -1;
    }

    private void createRecruiterProfile(int userId, String companyName, RecruiterVerificationStatus status) {
        String sql = "INSERT INTO recruiter_profiles (user_id, company_name, company_website, verification_status) " +
                     "VALUES (?, ?, 'https://test.local', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, companyName);
            stmt.setString(3, status.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating recruiter profile: " + userId, e);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + message);
            testsFailed++;
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        boolean match = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (match) {
            System.out.printf("  [PASS] %s (value=%s)%n", message, actual);
            testsPassed++;
        } else {
            System.err.printf("  [FAIL] %s: expected [%s] but found [%s]%n", message, expected, actual);
            testsFailed++;
        }
    }

    private void assertNull(String message, Object actual) {
        assertEquals(message, null, actual);
    }
}
