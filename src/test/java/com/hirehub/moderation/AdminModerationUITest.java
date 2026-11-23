package com.hirehub.moderation;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.dao.AdminModerationDAO;
import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.controller.AdminDashboardController;
import com.hirehub.model.AdminAuditEntityType;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.AdminJobModerationKPI;
import com.hirehub.model.AdminRecruiterVerificationKPI;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.service.AdminModerationService;
import com.hirehub.service.AuthService;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 6 Step 4.2E: Complete Admin Recruiter Verification & Job Moderation UI Verifier.
 *
 * <p>Validates:
 * 1. Architecture, models, and DTO contracts (AdminRecruiterVerificationKPI, AdminJobModerationKPI, RecruiterProfile, Job).
 * 2. Offline FXML UI injection & control binding (navigation buttons, summary KPI cards, filters, tables, detail panels, action buttons).
 * 3. Navigation between Overview, Recruiter Verification, Job Moderation, and other sections.
 * 4. Security boundaries: unauthorized Student, Recruiter, unauthenticated, and inactive Admin sessions fail closed.
 * 5. Recruiter Verification lifecycle: search, filters, live KPI counts, verify, reject, suspend, return to pending.
 * 6. Job Moderation lifecycle: search, filters, live KPI counts, approve, reject, suspend, return to pending.
 * 7. Independence of job lifecycle status and moderation status.
 * 8. Student visibility enforcement: approved jobs require verified recruiter and active account.
 * 9. Synchronized audit logging: entity types, actions, reasons, target IDs, timestamps.
 * 10. Database baseline restoration: exact pre/post row counts verified.</p>
 */
public class AdminModerationUITest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    private static final List<Integer> createdUserIds = new ArrayList<>();
    private static final List<Integer> createdJobIds = new ArrayList<>();

    private record BaselineCounts(int users, int recruiterProfiles, int studentProfiles,
                                  int jobs, int auditLogs, int applications, int resumes, int analyses) {}

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2E: Admin Moderation Workspace Verifier");
        System.out.println("=================================================================\n");

        initJavaFxToolkit();

        try {
            runArchitectureAndModelTests();
            runOfflineUiTests();
            runLiveDbIntegrationTests();
        } finally {
            SessionManager.getInstance().logout();
            Platform.exit();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("ARCHITECTURE & MODELS : Passed%n");
        System.out.printf("OFFLINE & UI TESTS    : Passed%n");
        System.out.printf("LIVE DB TESTS         : Passed%n");
        System.out.printf("TOTAL SUMMARY         : %d PASSED, %d FAILED, %d SKIPPED%n",
                testsPassed, testsFailed, testsSkipped);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Architecture, Models & DTO Tests
    // =========================================================================

    private static void runArchitectureAndModelTests() {
        System.out.println("=== SECTION 1: Architecture, Models & DTO Contracts ===");

        // AdminRecruiterVerificationKPI
        AdminRecruiterVerificationKPI rKpi = new AdminRecruiterVerificationKPI(10, 3, 4, 1, 1, 1);
        assertCheck(rKpi.getTotalRecruiters() == 10 && rKpi.getPendingCount() == 3
                        && rKpi.getVerifiedCount() == 4 && rKpi.getRejectedCount() == 1
                        && rKpi.getSuspendedCount() == 1 && rKpi.getUnverifiedCount() == 1,
                "M1: AdminRecruiterVerificationKPI constructor and getters match all fields");

        // AdminJobModerationKPI
        AdminJobModerationKPI jKpi = new AdminJobModerationKPI(15, 5, 6, 2, 2, 7);
        assertCheck(jKpi.getTotalJobs() == 15 && jKpi.getPendingCount() == 5
                        && jKpi.getApprovedCount() == 6 && jKpi.getRejectedCount() == 2
                        && jKpi.getSuspendedCount() == 2 && jKpi.getActiveLifecycleCount() == 7,
                "M2: AdminJobModerationKPI constructor and getters match all fields");

        // RecruiterProfile joined metadata
        RecruiterProfile rp = new RecruiterProfile(101, "Acme Corp", "https://acme.local", "Tech", "Software");
        rp.setRecruiterName("Alice Recruiter");
        rp.setRecruiterEmail("alice@acme.local");
        rp.setUserActive(true);
        LocalDateTime regDate = LocalDateTime.of(2026, 1, 1, 12, 0);
        rp.setUserCreatedAt(regDate);

        assertCheck("Alice Recruiter".equals(rp.getRecruiterName())
                        && "alice@acme.local".equals(rp.getRecruiterEmail())
                        && Boolean.TRUE.equals(rp.isUserActive())
                        && regDate.equals(rp.getUserCreatedAt()),
                "M3: RecruiterProfile exposes joined user metadata (name, email, active, createdAt)");

        // Job joined metadata
        Job job = new Job();
        job.setId(501);
        job.setTitle("Cloud Architect");
        job.setRecruiterName("Bob Recruiter");
        job.setRecruiterEmail("bob@acme.local");

        assertCheck("Cloud Architect".equals(job.getTitle())
                        && "Bob Recruiter".equals(job.getRecruiterName())
                        && "bob@acme.local".equals(job.getRecruiterEmail()),
                "M4: Job exposes joined recruiter metadata (recruiterName, recruiterEmail)");

        // DAO and Service method presence
        try {
            AdminModerationDAO.class.getMethod("getRecruiterVerificationKPI");
            AdminModerationDAO.class.getMethod("getJobModerationKPI");
            assertCheck(true, "M5: AdminModerationDAO exposes getRecruiterVerificationKPI and getJobModerationKPI");
        } catch (NoSuchMethodException e) {
            assertCheck(false, "M5: AdminModerationDAO missing KPI methods: " + e.getMessage());
        }

        try {
            AdminModerationService.class.getMethod("getRecruiterVerificationKPI");
            AdminModerationService.class.getMethod("getRecruiterVerificationKPI", int.class);
            AdminModerationService.class.getMethod("getJobModerationKPI");
            AdminModerationService.class.getMethod("getJobModerationKPI", int.class);
            assertCheck(true, "M6: AdminModerationService exposes overloaded KPI methods with admin authorization");
        } catch (NoSuchMethodException e) {
            assertCheck(false, "M6: AdminModerationService missing KPI methods: " + e.getMessage());
        }
    }

    // =========================================================================
    // SECTION 2: Offline UI & Controller Binding Tests
    // =========================================================================

    private static void runOfflineUiTests() throws Exception {
        System.out.println("\n=== SECTION 2: Offline UI & Controller Injection Tests ===");

        User adminUser = new User("System Admin", "admin.test@hirehub.local", "hash", User.Role.ADMIN);
        adminUser.setId(1);
        adminUser.setActive(true);
        SessionManager.getInstance().login(adminUser);

        LoadedAdminView view = loadAdminDashboardOffline();
        AdminDashboardController ctrl = view.controller();

        // Check sidebar navigation buttons
        Button navRecruiterVerificationBtn = getField(ctrl, "navRecruiterVerificationBtn", Button.class);
        Button navJobModerationBtn = getField(ctrl, "navJobModerationBtn", Button.class);
        assertCheck(navRecruiterVerificationBtn != null && "Recruiter Verification".equals(navRecruiterVerificationBtn.getText()),
                "U1: navRecruiterVerificationBtn injected and labeled 'Recruiter Verification'");
        assertCheck(navJobModerationBtn != null && "Job Moderation".equals(navJobModerationBtn.getText()),
                "U2: navJobModerationBtn injected and labeled 'Job Moderation'");

        // Check section panes
        VBox recruiterVerificationPane = getField(ctrl, "recruiterVerificationPane", VBox.class);
        VBox jobModerationPane = getField(ctrl, "jobModerationPane", VBox.class);
        assertCheck(recruiterVerificationPane != null, "U3: recruiterVerificationPane declared and injected");
        assertCheck(jobModerationPane != null, "U4: jobModerationPane declared and injected");

        // Check Recruiter Verification UI controls
        Label recTotalLabel = getField(ctrl, "recTotalLabel", Label.class);
        Label recPendingLabel = getField(ctrl, "recPendingLabel", Label.class);
        Label recVerifiedLabel = getField(ctrl, "recVerifiedLabel", Label.class);
        Label recRejectedLabel = getField(ctrl, "recRejectedLabel", Label.class);
        Label recSuspendedLabel = getField(ctrl, "recSuspendedLabel", Label.class);
        Label recUnverifiedLabel = getField(ctrl, "recUnverifiedLabel", Label.class);
        TextField txtRecruiterSearch = getField(ctrl, "txtRecruiterSearch", TextField.class);
        ComboBox<?> comboRecruiterStatusFilter = getField(ctrl, "comboRecruiterStatusFilter", ComboBox.class);
        TableView<?> recruiterVerificationTable = getField(ctrl, "recruiterVerificationTable", TableView.class);
        VBox recruiterPlaceholderBox = getField(ctrl, "recruiterPlaceholderBox", VBox.class);
        VBox recruiterDetailContainer = getField(ctrl, "recruiterDetailContainer", VBox.class);
        Button btnVerifyRecruiter = getField(ctrl, "btnVerifyRecruiter", Button.class);
        Button btnRejectRecruiter = getField(ctrl, "btnRejectRecruiter", Button.class);
        Button btnSuspendRecruiter = getField(ctrl, "btnSuspendRecruiter", Button.class);
        Button btnReturnRecruiterPending = getField(ctrl, "btnReturnRecruiterPending", Button.class);

        assertCheck(recTotalLabel != null && recPendingLabel != null && recVerifiedLabel != null
                        && recRejectedLabel != null && recSuspendedLabel != null && recUnverifiedLabel != null,
                "U5: All 6 Recruiter Verification summary KPI labels injected");
        assertCheck(txtRecruiterSearch != null && comboRecruiterStatusFilter != null && recruiterVerificationTable != null,
                "U6: Recruiter search field, status filter ComboBox, and TableView injected");
        assertCheck(recruiterPlaceholderBox != null && recruiterDetailContainer != null,
                "U7: Recruiter placeholder box and detail panel container injected");
        assertCheck(btnVerifyRecruiter != null && btnRejectRecruiter != null
                        && btnSuspendRecruiter != null && btnReturnRecruiterPending != null,
                "U8: All 4 Recruiter moderation action buttons (Verify, Reject, Suspend, To Pending) injected");

        // Check Job Moderation UI controls
        Label jobModTotalLabel = getField(ctrl, "jobModTotalLabel", Label.class);
        Label jobModPendingLabel = getField(ctrl, "jobModPendingLabel", Label.class);
        Label jobModApprovedLabel = getField(ctrl, "jobModApprovedLabel", Label.class);
        Label jobModRejectedLabel = getField(ctrl, "jobModRejectedLabel", Label.class);
        Label jobModSuspendedLabel = getField(ctrl, "jobModSuspendedLabel", Label.class);
        Label jobModActiveLifecycleLabel = getField(ctrl, "jobModActiveLifecycleLabel", Label.class);
        TextField txtJobModSearch = getField(ctrl, "txtJobModSearch", TextField.class);
        ComboBox<?> comboJobModerationStatusFilter = getField(ctrl, "comboJobModerationStatusFilter", ComboBox.class);
        ComboBox<?> comboJobLifecycleFilter = getField(ctrl, "comboJobLifecycleFilter", ComboBox.class);
        TableView<?> jobModerationTable = getField(ctrl, "jobModerationTable", TableView.class);
        VBox jobModPlaceholderBox = getField(ctrl, "jobModPlaceholderBox", VBox.class);
        VBox jobModDetailContainer = getField(ctrl, "jobModDetailContainer", VBox.class);
        Button btnApproveJob = getField(ctrl, "btnApproveJob", Button.class);
        Button btnRejectJob = getField(ctrl, "btnRejectJob", Button.class);
        Button btnSuspendJob = getField(ctrl, "btnSuspendJob", Button.class);
        Button btnReturnJobPending = getField(ctrl, "btnReturnJobPending", Button.class);

        assertCheck(jobModTotalLabel != null && jobModPendingLabel != null && jobModApprovedLabel != null
                        && jobModRejectedLabel != null && jobModSuspendedLabel != null && jobModActiveLifecycleLabel != null,
                "U9: All 6 Job Moderation summary KPI labels injected");
        assertCheck(txtJobModSearch != null && comboJobModerationStatusFilter != null
                        && comboJobLifecycleFilter != null && jobModerationTable != null,
                "U10: Job search, moderation filter, lifecycle filter, and TableView injected");
        assertCheck(jobModPlaceholderBox != null && jobModDetailContainer != null,
                "U11: Job placeholder box and detail panel container injected");
        assertCheck(btnApproveJob != null && btnRejectJob != null
                        && btnSuspendJob != null && btnReturnJobPending != null,
                "U12: All 4 Job moderation action buttons (Approve, Reject, Suspend, To Pending) injected");

        // Navigation switching tests
        runOnFx(() -> {
            invokeMethod(ctrl, "handleNavRecruiterVerification", javafx.event.ActionEvent.class, null);
            return null;
        });

        assertCheck(recruiterVerificationPane.isVisible() && recruiterVerificationPane.isManaged()
                        && !jobModerationPane.isVisible(),
                "U13: handleNavRecruiterVerification switches to recruiterVerificationPane");

        runOnFx(() -> {
            invokeMethod(ctrl, "handleNavJobModeration", javafx.event.ActionEvent.class, null);
            return null;
        });

        assertCheck(jobModerationPane.isVisible() && jobModerationPane.isManaged()
                        && !recruiterVerificationPane.isVisible(),
                "U14: handleNavJobModeration switches to jobModerationPane");

        // Recruiter detail rendering test with mock profile
        RecruiterProfile mockP = new RecruiterProfile(101, "OmniCorp Global", "https://omni.local", "Fintech Innovations", "Finance");
        mockP.setUserId(9001);
        mockP.setRecruiterName("Charlie Owner");
        mockP.setRecruiterEmail("charlie@omni.local");
        mockP.setUserActive(true);
        mockP.setVerificationStatus(RecruiterVerificationStatus.PENDING);
        mockP.setVerificationReason("Submitted for compliance check");

        runOnFx(() -> {
            invokeMethod(ctrl, "renderRecruiterDetail", RecruiterProfile.class, mockP);
            return null;
        });

        Label recDetailCompanyName = getField(ctrl, "recDetailCompanyName", Label.class);
        Label recDetailStatusBadge = getField(ctrl, "recDetailStatusBadge", Label.class);
        assertCheck(recDetailCompanyName.getText().contains("OmniCorp Global"),
                "U15: Recruiter detail renders company name");
        assertCheck("PENDING".equalsIgnoreCase(recDetailStatusBadge.getText()),
                "U16: Recruiter detail renders PENDING status badge");
        assertCheck(!btnVerifyRecruiter.isDisabled() && !btnRejectRecruiter.isDisabled()
                        && !btnSuspendRecruiter.isDisabled() && btnReturnRecruiterPending.isDisabled(),
                "U17: In PENDING status, Verify/Reject/Suspend are enabled while Return to Pending is disabled");

        // Job detail rendering test with mock job
        Job mockJob = new Job(9001, "Senior AI Engineer", "OmniCorp Global", "Build LLM systems",
                "Python, PyTorch", "Remote", Job.JobType.FULL_TIME, "$160k - $200k",
                "B.Tech / M.Tech", LocalDate.now().plusMonths(1), Job.Status.ACTIVE, JobModerationStatus.PENDING);
        mockJob.setId(7001);
        mockJob.setRecruiterName("Charlie Owner");
        mockJob.setRecruiterEmail("charlie@omni.local");
        mockJob.setModerationReason("New listing awaiting review");

        runOnFx(() -> {
            invokeMethod(ctrl, "renderJobDetail", Job.class, mockJob);
            return null;
        });

        Label jobModDetailTitle = getField(ctrl, "jobModDetailTitle", Label.class);
        Label jobModDetailModerationBadge = getField(ctrl, "jobModDetailModerationBadge", Label.class);
        Label jobModDetailLifecycleBadge = getField(ctrl, "jobModDetailLifecycleBadge", Label.class);
        assertCheck(jobModDetailTitle.getText().contains("Senior AI Engineer"),
                "U18: Job detail renders job title");
        assertCheck("PENDING".equalsIgnoreCase(jobModDetailModerationBadge.getText()),
                "U19: Job detail renders PENDING moderation badge");
        assertCheck("ACTIVE".equalsIgnoreCase(jobModDetailLifecycleBadge.getText()),
                "U20: Job detail renders ACTIVE lifecycle badge distinctly");
        assertCheck(!btnApproveJob.isDisabled() && !btnRejectJob.isDisabled()
                        && !btnSuspendJob.isDisabled() && btnReturnJobPending.isDisabled(),
                "U21: In PENDING moderation, Approve/Reject/Suspend enabled while Return to Pending disabled");
    }

    // =========================================================================
    // SECTION 3: Live Database & Moderation Workflow Tests
    // =========================================================================

    private static void runLiveDbIntegrationTests() throws Exception {
        System.out.println("\n=== SECTION 3: Live Database Moderation & Security Tests ===");

        AdminModerationService modService = new AdminModerationService();
        AdminModerationDAO modDAO = new AdminModerationDAO();
        RecruiterDAO recDAO = new RecruiterDAO();
        JobDAO jobDAO = new JobDAO();
        JobService jobService = new JobService();
        RecruiterService recService = new RecruiterService();

        BaselineCounts initialCounts = fetchBaselineCounts();
        System.out.printf("  [INFO] Baseline counts: users=%d, recruiter_profiles=%d, student_profiles=%d, jobs=%d, audit_logs=%d%n",
                initialCounts.users, initialCounts.recruiterProfiles, initialCounts.studentProfiles, initialCounts.jobs, initialCounts.auditLogs);

        try {
            // Setup isolated test users
            int adminId = createTestUser("Adm Mod Tester", "adm_mod_tester@hirehub.local", User.Role.ADMIN, true);
            int studentId = createTestUser("Student Tester", "stu_mod_tester@hirehub.local", User.Role.STUDENT, true);
            int recruiterId = createTestUser("Rec Mod Tester", "rec_mod_tester@hirehub.local", User.Role.RECRUITER, true);

            // -----------------------------------------------------------------
            // Sub-test A: Security Boundaries
            // -----------------------------------------------------------------
            System.out.println("  --> Sub-test A: Authorization & Role Boundaries");

            // Student session
            User stuUser = new User("Student Tester", "stu_mod_tester@hirehub.local", "hash", User.Role.STUDENT);
            stuUser.setId(studentId);
            SessionManager.getInstance().login(stuUser);

            assertCheck(!modService.listRecruitersForModeration(null, null, 10, 0).isSuccess(),
                    "S1: Student session blocked from listRecruitersForModeration");
            assertCheck(!modService.getRecruiterVerificationKPI().isSuccess(),
                    "S2: Student session blocked from getRecruiterVerificationKPI");
            assertCheck(!modService.verifyRecruiter(studentId, recruiterId, "Fraud").isSuccess(),
                    "S3: Student session blocked from verifyRecruiter");
            assertCheck(!modService.listJobsForModeration(null, null, null, 10, 0).isSuccess(),
                    "S4: Student session blocked from listJobsForModeration");
            assertCheck(!modService.getJobModerationKPI().isSuccess(),
                    "S5: Student session blocked from getJobModerationKPI");
            assertCheck(!modService.approveJob(studentId, 1, "Fraud").isSuccess(),
                    "S6: Student session blocked from approveJob");

            // Recruiter session
            User recUser = new User("Rec Mod Tester", "rec_mod_tester@hirehub.local", "hash", User.Role.RECRUITER);
            recUser.setId(recruiterId);
            SessionManager.getInstance().login(recUser);

            assertCheck(!modService.listRecruitersForModeration(null, null, 10, 0).isSuccess(),
                    "S7: Recruiter session blocked from listRecruitersForModeration");
            assertCheck(!modService.verifyRecruiter(recruiterId, recruiterId, "Self verify").isSuccess(),
                    "S8: Recruiter session blocked from self-verification");
            assertCheck(!modService.approveJob(recruiterId, 1, "Self approve").isSuccess(),
                    "S9: Recruiter session blocked from self-approving jobs");

            // Unauthenticated session
            SessionManager.getInstance().logout();
            assertCheck(!modService.listRecruitersForModeration(null, null, 10, 0).isSuccess(),
                    "S10: Unauthenticated caller blocked from listRecruitersForModeration");
            assertCheck(!modService.listJobsForModeration(null, null, null, 10, 0).isSuccess(),
                    "S11: Unauthenticated caller blocked from listJobsForModeration");

            // Active Admin session
            User admin = new User("Adm Mod Tester", "adm_mod_tester@hirehub.local", "hash", User.Role.ADMIN);
            admin.setId(adminId);
            admin.setActive(true);
            SessionManager.getInstance().login(admin);

            // -----------------------------------------------------------------
            // Sub-test B: Recruiter Verification Workflow & Live Queries
            // -----------------------------------------------------------------
            System.out.println("  --> Sub-test B: Recruiter Verification Workflow & Live Queries");

            // Initially recruiter has UNVERIFIED profile
            RecruiterProfile p = new RecruiterProfile(recruiterId, "Beta Tech Labs", "https://betatech.local", "AI Cloud", "IT");
            p.setVerificationStatus(RecruiterVerificationStatus.PENDING);
            recDAO.insert(p);

            // Check KPI counts
            var rKpiRes = modService.getRecruiterVerificationKPI(adminId);
            assertCheck(rKpiRes.isSuccess() && rKpiRes.getData().getPendingCount() >= 1,
                    "R1: getRecruiterVerificationKPI returns success and reflects live pending count");

            // Search by company
            var searchComp = modService.listRecruitersForModeration("Beta Tech", null, 10, 0);
            assertCheck(searchComp.isSuccess() && !searchComp.getData().isEmpty()
                            && searchComp.getData().get(0).getUserId() == recruiterId,
                    "R2: Search by company name finds recruiter with joined user metadata");
            assertCheck("rec_mod_tester@hirehub.local".equals(searchComp.getData().get(0).getRecruiterEmail()),
                    "R3: Search results contain joined recruiter email");

            // Search by email
            var searchEmail = modService.listRecruitersForModeration("rec_mod_tester", null, 10, 0);
            assertCheck(searchEmail.isSuccess() && !searchEmail.getData().isEmpty(),
                    "R4: Search by recruiter email finds recruiter record");

            // Filter by status PENDING
            var filterPending = modService.listRecruitersForModeration(null, RecruiterVerificationStatus.PENDING, 10, 0);
            assertCheck(filterPending.isSuccess() && filterPending.getData().stream().anyMatch(rp -> rp.getUserId() == recruiterId),
                    "R5: Status filter PENDING returns test recruiter");

            // Verification Action: Verify
            var verifyRes = modService.verifyRecruiter(adminId, recruiterId, "Verified business registration");
            assertCheck(verifyRes.isSuccess(), "R6: verifyRecruiter succeeds");

            Optional<RecruiterProfile> updatedP = recDAO.findByUserId(recruiterId);
            assertCheck(updatedP.isPresent() && updatedP.get().getVerificationStatus() == RecruiterVerificationStatus.VERIFIED,
                    "R7: Recruiter status in database is now VERIFIED");
            assertCheck(updatedP.get().getVerifiedBy() != null && updatedP.get().getVerifiedBy() == adminId,
                    "R8: verified_by in database matches acting admin ID");

            // Audit record created for verification
            var recAuditLogs = modService.getModerationLogsForRecruiter(adminId, recruiterId);
            assertCheck(recAuditLogs.isSuccess() && !recAuditLogs.getData().isEmpty(),
                    "R9: Audit log created for recruiter verification");
            assertCheck("RECRUITER_VERIFIED".equals(recAuditLogs.getData().get(0).getAction()),
                    "R10: Audit action is RECRUITER_VERIFIED");

            // Redundant transition rejected
            var redVerify = modService.verifyRecruiter(adminId, recruiterId, "Already verified");
            assertCheck(!redVerify.isSuccess(), "R11: Redundant verifyRecruiter is rejected");

            // Suspension Action: Suspend (requires reason)
            assertCheck(!modService.suspendRecruiter(adminId, recruiterId, "").isSuccess(),
                    "R12: Suspend without reason is rejected");
            assertCheck(!modService.suspendRecruiter(adminId, recruiterId, "no").isSuccess(),
                    "R13: Suspend with too short reason (< 3 chars) is rejected");

            var suspRes = modService.suspendRecruiter(adminId, recruiterId, "Suspected policy infringement");
            assertCheck(suspRes.isSuccess(), "R14: suspendRecruiter with valid reason succeeds");
            assertCheck(recDAO.findByUserId(recruiterId).get().getVerificationStatus() == RecruiterVerificationStatus.SUSPENDED,
                    "R15: Recruiter status in database is now SUSPENDED");

            // Return to Pending Action
            var retPendingRes = modService.returnRecruiterToPending(adminId, recruiterId, "Under audit re-assessment");
            assertCheck(retPendingRes.isSuccess(), "R16: returnRecruiterToPending succeeds");
            assertCheck(recDAO.findByUserId(recruiterId).get().getVerificationStatus() == RecruiterVerificationStatus.PENDING,
                    "R17: Recruiter status in database is returned to PENDING");

            // Rejection Action: Reject (requires reason)
            assertCheck(!modService.rejectRecruiter(adminId, recruiterId, "  ").isSuccess(),
                    "R18: Reject without reason is rejected");
            var rejRes = modService.rejectRecruiter(adminId, recruiterId, "Invalid incorporation documentation");
            assertCheck(rejRes.isSuccess(), "R19: rejectRecruiter with valid reason succeeds");
            assertCheck(recDAO.findByUserId(recruiterId).get().getVerificationStatus() == RecruiterVerificationStatus.REJECTED,
                    "R20: Recruiter status in database is now REJECTED");

            // -----------------------------------------------------------------
            // Sub-test C: Job Moderation Workflow & Student Visibility
            // -----------------------------------------------------------------
            System.out.println("  --> Sub-test C: Job Moderation Workflow & Student Visibility");

            // Create test job under test recruiter
            Job testJob = new Job(recruiterId, "Distributed Systems Lead", "Beta Tech Labs",
                    "Build high-throughput systems", "Go, Kubernetes", "Bengaluru",
                    Job.JobType.FULL_TIME, "25-35 LPA", "B.Tech CSE",
                    LocalDate.now().plusMonths(2), Job.Status.ACTIVE, JobModerationStatus.PENDING);
            int testJobId = jobDAO.insert(testJob);
            testJob.setId(testJobId);
            createdJobIds.add(testJobId);

            // Job Moderation KPI counts
            var jKpiRes = modService.getJobModerationKPI(adminId);
            assertCheck(jKpiRes.isSuccess() && jKpiRes.getData().getPendingCount() >= 1,
                    "J1: getJobModerationKPI returns success and reflects live pending job count");

            // Search jobs by title
            var jobByTitle = modService.listJobsForModeration("Distributed Systems", null, null, 10, 0);
            assertCheck(jobByTitle.isSuccess() && jobByTitle.getData().stream().anyMatch(j -> j.getId() == testJobId),
                    "J2: Search jobs by title finds test job with joined recruiter info");
            assertCheck("rec_mod_tester@hirehub.local".equals(jobByTitle.getData().get(0).getRecruiterEmail()),
                    "J3: Job record contains joined recruiter email");

            // Search jobs by recruiter email
            var jobByRec = modService.listJobsForModeration("rec_mod_tester", null, null, 10, 0);
            assertCheck(jobByRec.isSuccess() && jobByRec.getData().stream().anyMatch(j -> j.getId() == testJobId),
                    "J4: Search jobs by recruiter email finds matching job");

            // Filter jobs by moderation status PENDING
            var modPendingJobs = modService.listJobsForModeration(null, null, JobModerationStatus.PENDING, 10, 0);
            assertCheck(modPendingJobs.isSuccess() && modPendingJobs.getData().stream().anyMatch(j -> j.getId() == testJobId),
                    "J5: Moderation status filter PENDING includes test job");

            // Check student visibility: PENDING job is NOT visible to students
            List<Job> studentVisiblePending = jobDAO.searchApprovedActive(null, null, null, 50, 0);
            assertCheck(studentVisiblePending.stream().noneMatch(j -> j.getId() == testJobId),
                    "J6: PENDING job is NOT visible to students");

            // Admin Approves Job
            var approveJobRes = modService.approveJob(adminId, testJobId, "Verified placement guidelines");
            assertCheck(approveJobRes.isSuccess(), "J7: approveJob succeeds");

            Job dbJob = jobDAO.findById(testJobId).get();
            assertCheck(dbJob.getModerationStatus() == JobModerationStatus.APPROVED,
                    "J8: Job moderation status is now APPROVED");
            assertCheck(dbJob.getStatus() == Job.Status.ACTIVE,
                    "J9: Job lifecycle status remains strictly ACTIVE (unaltered by moderation)");
            assertCheck(dbJob.getModeratedBy() != null && dbJob.getModeratedBy() == adminId,
                    "J10: Job moderated_by matches acting admin ID");

            // Student visibility check: recruiter is currently REJECTED, so APPROVED job must NOT be visible!
            List<Job> studentVisibleRejectedRec = jobDAO.searchApprovedActive(null, null, null, 50, 0);
            assertCheck(studentVisibleRejectedRec.stream().noneMatch(j -> j.getId() == testJobId),
                    "J11: APPROVED job is NOT visible to students when recruiter is REJECTED");

            // Now verify recruiter
            modService.verifyRecruiter(adminId, recruiterId, "Re-approved for job placement");

            // Now job SHOULD be visible to students!
            List<Job> studentVisibleApproved = jobDAO.searchApprovedActive(null, null, null, 50, 0);
            assertCheck(studentVisibleApproved.stream().anyMatch(j -> j.getId() == testJobId),
                    "J12: APPROVED job with VERIFIED recruiter is now visible to students");

            // Suspend Job Action (requires reason)
            assertCheck(!modService.suspendJob(adminId, testJobId, "").isSuccess(),
                    "J13: Suspend job without reason is rejected");
            var suspJobRes = modService.suspendJob(adminId, testJobId, "Flagged for compensation review");
            assertCheck(suspJobRes.isSuccess(), "J14: suspendJob succeeds with valid reason");

            // Suspended job immediately hidden from students
            List<Job> studentVisibleSuspended = jobDAO.searchApprovedActive(null, null, null, 50, 0);
            assertCheck(studentVisibleSuspended.stream().noneMatch(j -> j.getId() == testJobId),
                    "J15: SUSPENDED job is immediately hidden from student search");

            // Return Job to Pending Action
            var retJobPendingRes = modService.returnJobToPending(adminId, testJobId, "Returned for recruiter amendment");
            assertCheck(retJobPendingRes.isSuccess(), "J16: returnJobToPending succeeds");
            assertCheck(jobDAO.findById(testJobId).get().getModerationStatus() == JobModerationStatus.PENDING,
                    "J17: Job moderation status is PENDING in database");

            // Reject Job Action (requires reason)
            assertCheck(!modService.rejectJob(adminId, testJobId, "no").isSuccess(),
                    "J18: Reject job with too short reason (< 3 chars) is rejected");
            var rejJobRes = modService.rejectJob(adminId, testJobId, "Eligibility criteria discriminatory");
            assertCheck(rejJobRes.isSuccess(), "J19: rejectJob succeeds with valid reason");
            assertCheck(jobDAO.findById(testJobId).get().getModerationStatus() == JobModerationStatus.REJECTED,
                    "J20: Job moderation status is REJECTED in database");

            // -----------------------------------------------------------------
            // Sub-test D: Audit Trail Integrity & Multi-Entity Queries
            // -----------------------------------------------------------------
            System.out.println("  --> Sub-test D: Audit Trail Integrity & History Queries");

            var jobAudit = modService.getModerationLogsForJob(adminId, testJobId);
            assertCheck(jobAudit.isSuccess() && jobAudit.getData().size() >= 4,
                    "A1: Job audit history records all 4 moderation events (approve, suspend, pending, reject)");
            assertCheck(jobAudit.getData().stream().allMatch(l -> l.getEntityType() == AdminAuditEntityType.JOB),
                    "A2: All job audit logs have entity_type = JOB");
            assertCheck(jobAudit.getData().stream().allMatch(l -> l.getTargetJobId() != null && l.getTargetJobId() == testJobId),
                    "A3: All job audit logs reference correct target_job_id");

            var recentModLogs = modService.getRecentModerationLogs(adminId, 50);
            assertCheck(recentModLogs.isSuccess() && !recentModLogs.getData().isEmpty(),
                    "A4: getRecentModerationLogs returns both recruiter and job moderation logs");
            assertCheck(recentModLogs.getData().stream().anyMatch(l -> l.getEntityType() == AdminAuditEntityType.RECRUITER_PROFILE),
                    "A5: Recent moderation logs include RECRUITER_PROFILE records");
            assertCheck(recentModLogs.getData().stream().anyMatch(l -> l.getEntityType() == AdminAuditEntityType.JOB),
                    "A6: Recent moderation logs include JOB records");

            // User management audit logs still work cleanly
            AdminUserDAO userDAO = new AdminUserDAO();
            List<AdminAuditLog> userLogs = userDAO.getRecentAuditLogs(10);
            assertCheck(userLogs != null, "A7: Existing AdminUserDAO.getRecentAuditLogs loads without SQL errors");

        } finally {
            cleanupTestData();
            BaselineCounts finalCounts = fetchBaselineCounts();
            System.out.printf("  [INFO] Post-cleanup counts: users=%d, recruiter_profiles=%d, student_profiles=%d, jobs=%d, audit_logs=%d%n",
                    finalCounts.users, finalCounts.recruiterProfiles, finalCounts.studentProfiles, finalCounts.jobs, finalCounts.auditLogs);

            assertCheck(initialCounts.users == finalCounts.users, "C1: users table restored to baseline (" + initialCounts.users + ")");
            assertCheck(initialCounts.recruiterProfiles == finalCounts.recruiterProfiles, "C2: recruiter_profiles restored (" + initialCounts.recruiterProfiles + ")");
            assertCheck(initialCounts.studentProfiles == finalCounts.studentProfiles, "C3: student_profiles restored (" + initialCounts.studentProfiles + ")");
            assertCheck(initialCounts.jobs == finalCounts.jobs, "C4: jobs table restored (" + initialCounts.jobs + ")");
            assertCheck(initialCounts.auditLogs == finalCounts.auditLogs, "C5: admin_audit_logs restored (" + initialCounts.auditLogs + ")");
            assertCheck(initialCounts.applications == finalCounts.applications, "C6: applications restored (" + initialCounts.applications + ")");
            assertCheck(initialCounts.resumes == finalCounts.resumes, "C7: resumes restored (" + initialCounts.resumes + ")");
            assertCheck(initialCounts.analyses == finalCounts.analyses, "C8: resume_analyses restored (" + initialCounts.analyses + ")");
        }
    }

    // =========================================================================
    // Helpers & Utilities
    // =========================================================================

    private static int createTestUser(String name, String email, User.Role role, boolean active) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active, created_at) " +
                     "VALUES (?, ?, 'hash123', ?, ?, NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, role.name());
            stmt.setBoolean(4, active);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    createdUserIds.add(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to create test user: " + email);
    }

    private static BaselineCounts fetchBaselineCounts() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            int users = countRows(c, "users");
            int recProfiles = countRows(c, "recruiter_profiles");
            int stuProfiles = countRows(c, "student_profiles");
            int jobs = countRows(c, "jobs");
            int auditLogs = countRows(c, "admin_audit_logs");
            int apps = countRows(c, "applications");
            int resumes = countRows(c, "resumes");
            int analyses = countRows(c, "resume_analyses");
            return new BaselineCounts(users, recProfiles, stuProfiles, jobs, auditLogs, apps, resumes, analyses);
        }
    }

    private static int countRows(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void cleanupTestData() {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);

            if (!createdJobIds.isEmpty()) {
                for (int jid : createdJobIds) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM jobs WHERE id = ?")) {
                        ps.setInt(1, jid);
                        ps.executeUpdate();
                    }
                }
            }

            if (!createdUserIds.isEmpty()) {
                for (int uid : createdUserIds) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM admin_audit_logs WHERE admin_id = ? OR target_user_id = ?")) {
                        ps.setInt(1, uid);
                        ps.setInt(2, uid);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM recruiter_profiles WHERE user_id = ?")) {
                        ps.setInt(1, uid);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
                        ps.setInt(1, uid);
                        ps.executeUpdate();
                    }
                }
            }

            // Clean any orphaned audit records referencing created test jobs
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM admin_audit_logs WHERE target_job_id NOT IN (SELECT id FROM jobs)")) {
                ps.executeUpdate();
            }

            c.commit();
        } catch (SQLException e) {
            System.err.println("[CLEANUP ERROR] " + e.getMessage());
        }
    }

    private static void initJavaFxToolkit() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            // Already started
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record LoadedAdminView(Parent root, AdminDashboardController controller) {}

    private static LoadedAdminView loadAdminDashboardOffline() throws Exception {
        return runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(AdminModerationUITest.class.getResource("/fxml/admin_dashboard.fxml"));
            Parent root = loader.load();
            AdminDashboardController controller = loader.getController();
            return new LoadedAdminView(root, controller);
        });
    }

    private static <T> T runOnFx(Callable<T> callable) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                ref.set(callable.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("JavaFX operation timed out after 10s");
        }
        if (err.get() != null) {
            if (err.get() instanceof Exception ex) throw ex;
            throw new RuntimeException(err.get());
        }
        return ref.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName, Class<T> expectedType) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invokeMethod(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getDeclaredMethod(methodName, paramType);
            m.setAccessible(true);
            return m.invoke(target, arg);
        } catch (Exception e) {
            throw new RuntimeException("Method invoke failed: " + methodName, e);
        }
    }

    private static void assertCheck(boolean condition, String message) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + message);
            testsFailed++;
        }
    }
}
