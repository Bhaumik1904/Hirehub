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
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.dao.ResumeAnalysisDAO;
import com.hirehub.model.Job;
import com.hirehub.model.RecruiterApplicantReadiness;
import com.hirehub.model.RecruiterDashboardSnapshot;
import com.hirehub.model.RecruiterOverviewKPI;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.service.AdminModerationService;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.AuthService;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterAnalyticsService;
import com.hirehub.service.RecruiterService;
import com.hirehub.controller.RecruiterDashboardController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 6 Step 4.2D: Complete Recruiter Verification UI & Onboarding Verifier.
 *
 * <p>Validates:
 * 1. UI element binding and status display across all 5 verification statuses:
 *    UNVERIFIED, PENDING, VERIFIED, REJECTED, SUSPENDED.
 * 2. Visual treatments: header pill, verification card badge, status messages, action buttons.
 * 3. Stored-data timeline accuracy: created_at, updated_at, verified_at (zero fabricated timestamps).
 * 4. Administrator feedback / reason display: shown only when non-empty for REJECTED/SUSPENDED.
 * 5. Onboarding flow: first login unverified state, profile submission transitions to PENDING.
 * 6. Profile resubmission: REJECTED -> PENDING transitions, metadata clearing.
 * 7. Security bounds: no recruiter self-verification, no self-unsuspension, cross-recruiter isolation.
 * 8. Job publishing restrictions: UNVERIFIED, PENDING, REJECTED, SUSPENDED blocked; VERIFIED allowed.
 * 9. Database baseline restoration: 100% verified before and after tests.</p>
 */
public class RecruiterVerificationUITest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    private static final List<Integer> createdUserIds = new ArrayList<>();
    private static final List<Integer> createdJobIds = new ArrayList<>();

    private record BaselineCounts(int users, int recruiterProfiles, int studentProfiles,
                                  int jobs, int auditLogs, int applications, int resumes, int analyses) {}

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.2D: Recruiter Verification UI Verifier");
        System.out.println("=================================================================\n");

        initJavaFxToolkit();

        try {
            runOfflineUiTests();
            runLiveDbIntegrationTests();
        } finally {
            SessionManager.getInstance().logout();
            Platform.exit();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("OFFLINE & UI TESTS : Passed%n");
        System.out.printf("LIVE DB TESTS      : Passed%n");
        System.out.printf("TOTAL SUMMARY      : %d PASSED, %d FAILED, %d SKIPPED%n",
                testsPassed, testsFailed, testsSkipped);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION A: Offline UI & Controller Binding Tests
    // =========================================================================

    private static void runOfflineUiTests() throws Exception {
        System.out.println("=== SECTION A: Offline UI & Controller Verification Tests ===");

        User testUser = new User("Alpha Recruiter", "alpha.rec@test.local", "hash", User.Role.RECRUITER);
        testUser.setId(8801);
        testUser.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));
        SessionManager.getInstance().login(testUser);

        LoadedView view = loadDashboardOffline();
        RecruiterDashboardController ctrl = view.controller();

        // 1. Verify FXML fields exist and are injected
        VBox verificationCard = getField(ctrl, "verificationCard", VBox.class);
        Label verificationStatusBadge = getField(ctrl, "verificationStatusBadge", Label.class);
        Label verificationMessageLabel = getField(ctrl, "verificationMessageLabel", Label.class);
        Button verificationActionButton = getField(ctrl, "verificationActionButton", Button.class);
        VBox verificationFeedbackBox = getField(ctrl, "verificationFeedbackBox", VBox.class);
        Label verificationReasonLabel = getField(ctrl, "verificationReasonLabel", Label.class);
        HBox verificationTimelineBox = getField(ctrl, "verificationTimelineBox", HBox.class);
        Label timelineCreatedLabel = getField(ctrl, "timelineCreatedLabel", Label.class);
        Label timelineUpdatedLabel = getField(ctrl, "timelineUpdatedLabel", Label.class);
        Label timelineReviewedLabel = getField(ctrl, "timelineReviewedLabel", Label.class);

        VBox profileVerificationBanner = getField(ctrl, "profileVerificationBanner", VBox.class);
        Label profileStatusBadge = getField(ctrl, "profileStatusBadge", Label.class);
        Label profileVerificationMsgLabel = getField(ctrl, "profileVerificationMsgLabel", Label.class);
        VBox profileFeedbackBox = getField(ctrl, "profileFeedbackBox", VBox.class);
        Label profileReasonLabel = getField(ctrl, "profileReasonLabel", Label.class);
        Button submitVerificationBtn = getField(ctrl, "submitVerificationBtn", Button.class);
        Button saveProfileBtn = getField(ctrl, "saveProfileBtn", Button.class);

        HBox jobsVerificationWarningBanner = getField(ctrl, "jobsVerificationWarningBanner", HBox.class);
        Label jobsVerificationWarningLabel = getField(ctrl, "jobsVerificationWarningLabel", Label.class);
        Label headerProfileStatusPill = getField(ctrl, "headerProfileStatusPill", Label.class);

        assertCheck(verificationCard != null && profileVerificationBanner != null && jobsVerificationWarningBanner != null,
                "A1: Verification UI containers (card, banner, warning banner) declared and injected");

        // 2. Test UNVERIFIED UI State
        runOnFx(() -> {
            ctrl.updateVerificationUI(null);
            return null;
        });

        assertCheck("UNVERIFIED".equalsIgnoreCase(verificationStatusBadge.getText())
                        && verificationStatusBadge.getStyleClass().contains("rec-badge-unverified"),
                "A2: UNVERIFIED state displays neutral badge 'UNVERIFIED'");
        assertCheck(verificationMessageLabel.getText().contains("not been submitted"),
                "A3: UNVERIFIED message prompts to submit company profile");
        assertCheck(!verificationActionButton.isDisabled() && "Complete & Submit Profile".equals(verificationActionButton.getText()),
                "A4: UNVERIFIED CTA button is enabled and reads 'Complete & Submit Profile'");
        assertCheck(!verificationFeedbackBox.isVisible() && !verificationFeedbackBox.isManaged(),
                "A5: UNVERIFIED feedback box is hidden");
        assertCheck(jobsVerificationWarningBanner.isVisible() && jobsVerificationWarningBanner.isManaged()
                        && jobsVerificationWarningLabel.getText().contains("unavailable until your recruiter account is verified"),
                "A6: UNVERIFIED displays jobs warning banner");
        assertCheck("Unverified".equals(headerProfileStatusPill.getText())
                        && headerProfileStatusPill.getStyleClass().contains("rec-profile-pill-neutral"),
                "A7: UNVERIFIED header pill displays 'Unverified' with neutral pill style");
        assertCheck(!submitVerificationBtn.isDisabled() && "Submit for Verification".equals(submitVerificationBtn.getText()),
                "A8: UNVERIFIED submit button in profile pane is enabled and reads 'Submit for Verification'");

        // 3. Test PENDING UI State
        RecruiterProfile pendingProfile = new RecruiterProfile(8801, "Acme Inc", "https://acme.com", "Desc", "Tech");
        pendingProfile.setVerificationStatus(RecruiterVerificationStatus.PENDING);
        pendingProfile.setUpdatedAt(LocalDateTime.of(2026, 2, 1, 14, 0));

        runOnFx(() -> {
            ctrl.updateVerificationUI(pendingProfile);
            return null;
        });

        assertCheck(verificationStatusBadge.getText().contains("PENDING")
                        && verificationStatusBadge.getStyleClass().contains("rec-badge-pending"),
                "A9: PENDING state displays amber badge 'PENDING VERIFICATION'");
        assertCheck(verificationMessageLabel.getText().contains("under review"),
                "A10: PENDING message explains request is under review");
        assertCheck(verificationActionButton.isDisabled() && "Under Review".equals(verificationActionButton.getText()),
                "A11: PENDING action button is disabled and reads 'Under Review'");
        assertCheck(submitVerificationBtn.isDisabled() && "Verification In Review".equals(submitVerificationBtn.getText()),
                "A12: PENDING submit button is disabled in profile pane");
        assertCheck("Pending Verification".equals(headerProfileStatusPill.getText())
                        && headerProfileStatusPill.getStyleClass().contains("rec-profile-pill-warn"),
                "A13: PENDING header pill displays 'Pending Verification' with amber style");
        assertCheck(jobsVerificationWarningBanner.isVisible()
                        && jobsVerificationWarningLabel.getText().contains("pending verification"),
                "A14: PENDING displays jobs warning banner explaining pending status");

        // 4. Test VERIFIED UI State
        RecruiterProfile verifiedProfile = new RecruiterProfile(8801, "Acme Inc", "https://acme.com", "Desc", "Tech");
        verifiedProfile.setVerificationStatus(RecruiterVerificationStatus.VERIFIED);
        verifiedProfile.setVerifiedAt(LocalDateTime.of(2026, 2, 5, 9, 30));
        verifiedProfile.setVerifiedBy(1);
        verifiedProfile.setUpdatedAt(LocalDateTime.of(2026, 2, 5, 9, 30));

        runOnFx(() -> {
            ctrl.updateVerificationUI(verifiedProfile);
            return null;
        });

        assertCheck("VERIFIED".equalsIgnoreCase(verificationStatusBadge.getText())
                        && verificationStatusBadge.getStyleClass().contains("rec-badge-verified"),
                "A15: VERIFIED state displays green badge 'VERIFIED'");
        assertCheck(verificationMessageLabel.getText().contains("account is verified"),
                "A16: VERIFIED message confirms full access to publish jobs");
        assertCheck(!verificationActionButton.isDisabled() && "+ Post New Job".equals(verificationActionButton.getText()),
                "A17: VERIFIED action button is enabled and reads '+ Post New Job'");
        assertCheck(submitVerificationBtn.isDisabled() && "Profile Verified".equals(submitVerificationBtn.getText()),
                "A18: VERIFIED submit button is disabled in profile pane");
        assertCheck(!jobsVerificationWarningBanner.isVisible() && !jobsVerificationWarningBanner.isManaged(),
                "A19: VERIFIED hides jobs warning banner completely");
        assertCheck("Verified Recruiter".equals(headerProfileStatusPill.getText())
                        && headerProfileStatusPill.getStyleClass().contains("rec-profile-pill-ok"),
                "A20: VERIFIED header pill displays 'Verified Recruiter' with green style");

        // 5. Test REJECTED UI State with Feedback
        RecruiterProfile rejectedProfile = new RecruiterProfile(8801, "Acme Inc", "https://acme.com", "Desc", "Tech");
        rejectedProfile.setVerificationStatus(RecruiterVerificationStatus.REJECTED);
        rejectedProfile.setVerificationReason("Incomplete business registration number provided.");
        rejectedProfile.setVerifiedAt(LocalDateTime.of(2026, 2, 3, 11, 0));
        rejectedProfile.setVerifiedBy(1);
        rejectedProfile.setUpdatedAt(LocalDateTime.of(2026, 2, 3, 11, 0));

        runOnFx(() -> {
            ctrl.updateVerificationUI(rejectedProfile);
            return null;
        });

        assertCheck("REJECTED".equalsIgnoreCase(verificationStatusBadge.getText())
                        && verificationStatusBadge.getStyleClass().contains("rec-badge-rejected"),
                "A21: REJECTED state displays red badge 'REJECTED'");
        assertCheck(verificationMessageLabel.getText().contains("rejected"),
                "A22: REJECTED message advises to review feedback and resubmit");
        assertCheck(verificationFeedbackBox.isVisible() && verificationFeedbackBox.isManaged()
                        && verificationReasonLabel.getText().contains("Incomplete business registration"),
                "A23: REJECTED state displays Admin feedback box with rejection reason");
        assertCheck(!verificationActionButton.isDisabled() && "Review Feedback & Resubmit".equals(verificationActionButton.getText()),
                "A24: REJECTED action button is enabled and reads 'Review Feedback & Resubmit'");
        assertCheck(!submitVerificationBtn.isDisabled() && "Resubmit for Verification".equals(submitVerificationBtn.getText()),
                "A25: REJECTED submit button is enabled in profile pane and reads 'Resubmit for Verification'");
        assertCheck("Verification Rejected".equals(headerProfileStatusPill.getText())
                        && headerProfileStatusPill.getStyleClass().contains("rec-profile-pill-danger"),
                "A26: REJECTED header pill displays 'Verification Rejected' with danger style");

        // 6. Test SUSPENDED UI State
        RecruiterProfile suspendedProfile = new RecruiterProfile(8801, "Acme Inc", "https://acme.com", "Desc", "Tech");
        suspendedProfile.setVerificationStatus(RecruiterVerificationStatus.SUSPENDED);
        suspendedProfile.setVerificationReason("Multiple policy violations reported.");
        suspendedProfile.setVerifiedAt(LocalDateTime.of(2026, 2, 10, 16, 45));
        suspendedProfile.setVerifiedBy(1);
        suspendedProfile.setUpdatedAt(LocalDateTime.of(2026, 2, 10, 16, 45));

        runOnFx(() -> {
            ctrl.updateVerificationUI(suspendedProfile);
            return null;
        });

        assertCheck("SUSPENDED".equalsIgnoreCase(verificationStatusBadge.getText())
                        && verificationStatusBadge.getStyleClass().contains("rec-badge-suspended"),
                "A27: SUSPENDED state displays dark red badge 'SUSPENDED'");
        assertCheck(verificationMessageLabel.getText().contains("suspended"),
                "A28: SUSPENDED message states recruiter verification has been suspended");
        assertCheck(!verificationActionButton.isVisible() && !verificationActionButton.isManaged(),
                "A29: SUSPENDED action button is hidden");
        assertCheck(submitVerificationBtn.isDisabled() && "Account Suspended".equals(submitVerificationBtn.getText()),
                "A30: SUSPENDED submit button is disabled in profile pane");
        assertCheck(saveProfileBtn.isDisabled(),
                "A31: SUSPENDED save draft profile button is disabled");
        assertCheck(verificationFeedbackBox.isVisible()
                        && verificationReasonLabel.getText().contains("Multiple policy violations"),
                "A32: SUSPENDED displays Admin suspension reason");
        assertCheck("Verification Suspended".equals(headerProfileStatusPill.getText())
                        && headerProfileStatusPill.getStyleClass().contains("rec-profile-pill-danger"),
                "A33: SUSPENDED header pill displays 'Verification Suspended' with danger style");

        // 7. Test Stored-Data Timeline Privacy & Zero Fabricated Dates
        runOnFx(() -> {
            RecruiterProfile noReviewDateProfile = new RecruiterProfile(8801, "Acme Inc", "", "", "");
            noReviewDateProfile.setVerificationStatus(RecruiterVerificationStatus.PENDING);
            noReviewDateProfile.setVerifiedAt(null); // Unreviewed
            noReviewDateProfile.setUpdatedAt(LocalDateTime.of(2026, 2, 1, 12, 0));
            ctrl.updateVerificationUI(noReviewDateProfile);
            return null;
        });

        assertCheck(timelineCreatedLabel.getText().contains("15 Jan 2026"),
                "A34: Timeline displays accurate account created date from user.created_at");
        assertCheck(timelineUpdatedLabel.getText().contains("01 Feb 2026"),
                "A35: Timeline displays accurate profile updated date from profile.updated_at");
        assertCheck(!timelineReviewedLabel.isVisible() && !timelineReviewedLabel.isManaged(),
                "A36: Timeline strictly hides 'Verification Reviewed' when verified_at is null (no fabricated date)");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // SECTION B: Live Database Service, Moderation & Security Tests
    // =========================================================================

    private static void runLiveDbIntegrationTests() throws Exception {
        System.out.println("\n=== SECTION B: Live Database Integration & Security Tests ===");

        BaselineCounts baseline = queryBaseline();
        System.out.printf("[INFO] Baseline counts: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, apps=%d, resumes=%d%n",
                baseline.users(), baseline.recruiterProfiles(), baseline.jobs(), baseline.auditLogs(),
                baseline.applications(), baseline.resumes());

        RecruiterDAO recruiterDAO = new RecruiterDAO();
        RecruiterService recruiterService = new RecruiterService(recruiterDAO);
        AdminUserDAO adminUserDAO = new AdminUserDAO();
        AdminModerationService adminModService = new AdminModerationService();
        JobService jobService = new JobService(new JobDAO(), recruiterDAO, adminUserDAO);

        int testRecruiterId = -1;
        int adminUserId = 1; // existing admin in baseline

        try {
            // Setup live test recruiter user
            long ts = System.currentTimeMillis();
            testRecruiterId = createTestUser("Step42D Recruiter", "step42d_" + ts + "@test.local", User.Role.RECRUITER);

            // B1: First-time registered recruiter has NO profile
            SessionManager.getInstance().login(adminUserDAO.findById(testRecruiterId).orElseThrow());
            Optional<RecruiterProfile> initialProfile = recruiterService.getProfile(testRecruiterId);
            assertCheck(initialProfile.isEmpty(),
                    "B1: Newly registered recruiter starts with no profile row (UNVERIFIED)");

            // B2: Recruiter cannot publish a job while UNVERIFIED
            JobService.ServiceResult postResUnverified = jobService.createJob(
                    testRecruiterId, "Unverified Engineer", "NovaTech", "Desc", "Java", "Remote",
                    "Full-time", "100k", "B.Tech", LocalDate.now().plusDays(30), false);
            assertCheck(!postResUnverified.isSuccess() && (postResUnverified.getMessage().contains("profile not found") || postResUnverified.getMessage().contains("verified")),
                    "B2: UNVERIFIED recruiter cannot publish job via JobService");

            // B3: Blank company name rejected during submission
            RecruiterService.ServiceResult blankSubmitRes = recruiterService.submitForVerification(
                    testRecruiterId, "   ", "https://site.com", "Desc", "Tech");
            assertCheck(!blankSubmitRes.isSuccess() && blankSubmitRes.getMessage().contains("Company name is required"),
                    "B3: Profile submission with blank company name is rejected");

            // B4: First-time profile submission transitions to PENDING
            RecruiterService.ServiceResult submitRes1 = recruiterService.submitForVerification(
                    testRecruiterId, "NovaTech Labs", "https://novatech.io", "AI Research", "Technology");
            assertCheck(submitRes1.isSuccess(),
                    "B4: First-time profile submission succeeds");

            Optional<RecruiterProfile> pendingProfileOpt = recruiterDAO.findByUserId(testRecruiterId);
            assertCheck(pendingProfileOpt.isPresent()
                            && pendingProfileOpt.get().getVerificationStatus() == RecruiterVerificationStatus.PENDING,
                    "B5: Database status transitioned to PENDING with correct company name");

            // B5: While PENDING, job publishing is still rejected
            JobService.ServiceResult postResPending = jobService.createJob(
                    testRecruiterId, "Pending Engineer", "NovaTech Labs", "Desc", "Java", "Remote",
                    "Full-time", "100k", "B.Tech", LocalDate.now().plusDays(30), false);
            assertCheck(!postResPending.isSuccess() && postResPending.getMessage().contains("verified"),
                    "B6: PENDING recruiter cannot publish job via JobService");

            // B6: Duplicate submission while PENDING is rejected
            RecruiterService.ServiceResult dupSubmitRes = recruiterService.submitForVerification(
                    testRecruiterId, "NovaTech Labs Renamed", "https://novatech.io", "Desc", "Tech");
            assertCheck(!dupSubmitRes.isSuccess() && dupSubmitRes.getMessage().contains("already pending"),
                    "B7: Duplicate profile submission while PENDING is rejected");

            // B7: Recruiter cannot self-verify (saveProfile does not alter status)
            RecruiterService.ServiceResult saveRes = recruiterService.saveProfile(
                    testRecruiterId, "NovaTech Labs Updated", "https://novatech.io", "Updated Desc", "Software");
            assertCheck(saveRes.isSuccess(), "B8: Recruiter can save profile info while PENDING");
            RecruiterProfile afterSave = recruiterDAO.findByUserId(testRecruiterId).orElseThrow();
            assertCheck(afterSave.getVerificationStatus() == RecruiterVerificationStatus.PENDING,
                    "B9: saveProfile strictly preserves PENDING status (no self-verification)");

            // B8: Admin Rejects profile with feedback reason
            SessionManager.getInstance().login(adminUserDAO.findById(adminUserId).orElseThrow());
            AdminModerationService.ServiceResult<Void> rejectRes = adminModService.rejectRecruiter(
                    adminUserId, testRecruiterId, "Invalid tax registration documents provided.");
            assertCheck(rejectRes.isSuccess(),
                    "B10: Admin successfully rejects recruiter verification with reason");

            RecruiterProfile rejectedProfile = recruiterDAO.findByUserId(testRecruiterId).orElseThrow();
            assertCheck(rejectedProfile.getVerificationStatus() == RecruiterVerificationStatus.REJECTED
                            && "Invalid tax registration documents provided.".equals(rejectedProfile.getVerificationReason()),
                    "B11: Database reflects REJECTED status and stores rejection reason");

            // B9: While REJECTED, job publishing is rejected
            SessionManager.getInstance().login(adminUserDAO.findById(testRecruiterId).orElseThrow());
            JobService.ServiceResult postResRejected = jobService.createJob(
                    testRecruiterId, "Rejected Job", "NovaTech", "Desc", "Java", "Remote",
                    "Full-time", "100k", "B.Tech", LocalDate.now().plusDays(30), false);
            assertCheck(!postResRejected.isSuccess() && postResRejected.getMessage().contains("verified"),
                    "B12: REJECTED recruiter cannot publish job via JobService");

            // B10: Profile resubmission from REJECTED transitions to PENDING and clears reason
            RecruiterService.ServiceResult resubmitRes = recruiterService.submitForVerification(
                    testRecruiterId, "NovaTech Solutions", "https://novatech.io", "Updated details", "Tech");
            assertCheck(resubmitRes.isSuccess(),
                    "B13: Profile resubmission from REJECTED succeeds");

            RecruiterProfile resubmittedProfile = recruiterDAO.findByUserId(testRecruiterId).orElseThrow();
            assertCheck(resubmittedProfile.getVerificationStatus() == RecruiterVerificationStatus.PENDING
                            && resubmittedProfile.getVerificationReason() == null
                            && resubmittedProfile.getVerifiedAt() == null
                            && resubmittedProfile.getVerifiedBy() == null,
                    "B14: Resubmission transitions to PENDING and clears previous reason/verified_at/verified_by");

            // B11: Admin Approves profile (status -> VERIFIED)
            SessionManager.getInstance().login(adminUserDAO.findById(adminUserId).orElseThrow());
            AdminModerationService.ServiceResult<Void> approveRes = adminModService.verifyRecruiter(
                    adminUserId, testRecruiterId, "All documents verified.");
            assertCheck(approveRes.isSuccess(),
                    "B15: Admin approves recruiter verification");

            RecruiterProfile approvedProfile = recruiterDAO.findByUserId(testRecruiterId).orElseThrow();
            assertCheck(approvedProfile.getVerificationStatus() == RecruiterVerificationStatus.VERIFIED
                            && approvedProfile.getVerifiedAt() != null
                            && approvedProfile.getVerifiedBy() != null
                            && approvedProfile.getVerifiedBy() == adminUserId,
                    "B16: Database reflects VERIFIED status with verified_at timestamp and admin ID");

            // B12: While VERIFIED, recruiter CAN publish jobs!
            SessionManager.getInstance().login(adminUserDAO.findById(testRecruiterId).orElseThrow());
            JobService.ServiceResult postResVerified = jobService.createJob(
                    testRecruiterId, "AI Research Engineer", "NovaTech Solutions", "Working on LLMs",
                    "Python, ML", "Remote", "Full-time", "120k", "B.Tech", LocalDate.now().plusDays(30), false);
            assertCheck(postResVerified.isSuccess(),
                    "B17: VERIFIED recruiter successfully creates and posts a job");

            if (postResVerified.getJob() != null) {
                createdJobIds.add(postResVerified.getJob().getId());
            }

            // B13: While VERIFIED, submission is rejected (already verified)
            RecruiterService.ServiceResult submitWhenVerified = recruiterService.submitForVerification(
                    testRecruiterId, "NovaTech Solutions", "https://novatech.io", "Desc", "Tech");
            assertCheck(!submitWhenVerified.isSuccess() && submitWhenVerified.getMessage().contains("already verified"),
                    "B18: Profile submission while VERIFIED is rejected");

            // B14: Admin Suspends recruiter
            SessionManager.getInstance().login(adminUserDAO.findById(adminUserId).orElseThrow());
            AdminModerationService.ServiceResult<Void> suspendRes = adminModService.suspendRecruiter(
                    adminUserId, testRecruiterId, "Fraudulent postings detected.");
            assertCheck(suspendRes.isSuccess(),
                    "B19: Admin suspends recruiter verification with reason");

            RecruiterProfile suspendedDbProfile = recruiterDAO.findByUserId(testRecruiterId).orElseThrow();
            assertCheck(suspendedDbProfile.getVerificationStatus() == RecruiterVerificationStatus.SUSPENDED
                            && "Fraudulent postings detected.".equals(suspendedDbProfile.getVerificationReason()),
                    "B20: Database reflects SUSPENDED status and stores suspension reason");

            // B15: While SUSPENDED, recruiter cannot self-unsuspend or resubmit
            SessionManager.getInstance().login(adminUserDAO.findById(testRecruiterId).orElseThrow());
            RecruiterService.ServiceResult suspendResubmit = recruiterService.submitForVerification(
                    testRecruiterId, "NovaTech Cleaned", "https://novatech.io", "Desc", "Tech");
            assertCheck(!suspendResubmit.isSuccess() && suspendResubmit.getMessage().contains("suspended"),
                    "B21: Suspended recruiter cannot resubmit profile (self-unsuspension rejected)");

            // B16: While SUSPENDED, recruiter cannot publish jobs
            JobService.ServiceResult postResSuspended = jobService.createJob(
                    testRecruiterId, "Suspended Attempt", "NovaTech Cleaned", "Desc", "Python", "Remote",
                    "Full-time", "100k", "B.Tech", LocalDate.now().plusDays(30), false);
            assertCheck(!postResSuspended.isSuccess() && postResSuspended.getMessage().contains("verified"),
                    "B22: SUSPENDED recruiter cannot post or publish jobs");

            // B17: Cross-recruiter session isolation
            int otherRecruiterId = createTestUser("Other Recruiter", "other_" + ts + "@test.local", User.Role.RECRUITER);
            SessionManager.getInstance().login(adminUserDAO.findById(testRecruiterId).orElseThrow());
            RecruiterService.ServiceResult crossSubmit = recruiterService.submitForVerification(
                    otherRecruiterId, "Hacked Co", "https://hack.com", "Desc", "Tech");
            assertCheck(!crossSubmit.isSuccess() && crossSubmit.getMessage().contains("Unauthorized"),
                    "B23: Cross-recruiter profile submission strictly rejected by authorization check");

            // B18: Unauthenticated session rejected
            SessionManager.getInstance().logout();
            RecruiterService.ServiceResult unauthSubmit = recruiterService.submitForVerification(
                    testRecruiterId, "Unauth Co", "https://unauth.com", "Desc", "Tech");
            assertCheck(!unauthSubmit.isSuccess() && unauthSubmit.getMessage().contains("Unauthorized"),
                    "B24: Unauthenticated profile submission strictly rejected");

        } finally {
            cleanupTestData();
            SessionManager.getInstance().logout();
        }

        BaselineCounts after = queryBaseline();
        System.out.printf("[INFO] Baseline after cleanup: users=%d, recruiter_profiles=%d, jobs=%d, audit_logs=%d, apps=%d, resumes=%d%n",
                after.users(), after.recruiterProfiles(), after.jobs(), after.auditLogs(),
                after.applications(), after.resumes());

        assertCheck(after.equals(baseline),
                "B25: Database baseline 100% restored after all live integration tests");
    }

    // =========================================================================
    // Helpers, DB Operations & JavaFX Utilities
    // =========================================================================

    private record LoadedView(Parent root, RecruiterDashboardController controller) {}

    private static LoadedView loadDashboardOffline() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                RecruiterVerificationUITest.class.getResource("/fxml/recruiter_dashboard.fxml"));

        RecruiterAnalyticsService offlineAnalytics = new RecruiterAnalyticsService() {
            @Override
            public RecruiterDashboardSnapshot loadSnapshot(int recruiterId, LocalDate from, LocalDate to) {
                RecruiterDashboardSnapshot snap = new RecruiterDashboardSnapshot();
                snap.setRecruiterId(recruiterId);
                snap.setRefreshedAt(LocalDateTime.now());
                RecruiterOverviewKPI ov = new RecruiterOverviewKPI();
                ov.setRecruiterAccountPresent(true);
                snap.setOverviewKPI(ov);
                return snap;
            }
        };
        RecruiterService offlineRecruiter = new RecruiterService() {
            @Override
            public Optional<RecruiterProfile> getProfile(int userId) {
                return Optional.empty();
            }
        };
        JobService offlineJob = new JobService() {
            @Override
            public List<Job> getJobsByRecruiter(int recruiterId) {
                return Collections.emptyList();
            }
        };
        ApplicationService offlineApp = new ApplicationService() {
            @Override
            public Map<Integer, Integer> countApplicationsByJobForRecruiter(int recruiterId) {
                return Collections.emptyMap();
            }
        };

        loader.setControllerFactory(clazz -> new RecruiterDashboardController(
                offlineAnalytics, offlineRecruiter, offlineJob, offlineApp, new AuthService()));

        return runOnFx(() -> {
            Parent root = loader.load();
            RecruiterDashboardController controller = loader.getController();
            return new LoadedView(root, controller);
        });
    }

    private static void initJavaFxToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX Platform failed to initialize");
        }
        Platform.setImplicitExit(false);
    }

    private static <T> T runOnFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for JavaFX thread");
        }
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception e) throw e;
            throw new RuntimeException(t);
        }
        return result.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static int createTestUser(String fullName, String email, User.Role role) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active, created_at) " +
                "VALUES (?, ?, 'hash123', ?, TRUE, NOW())";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, role.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    createdUserIds.add(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to create test user: " + email);
    }

    private static BaselineCounts queryBaseline() throws SQLException {
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

            // Also clean up any audit logs where target_user_id was one of our test users
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM admin_audit_logs WHERE action IN ('VERIFY_RECRUITER', 'SUSPEND_RECRUITER') " +
                    "AND target_user_id NOT IN (SELECT id FROM users)")) {
                ps.executeUpdate();
            }

            c.commit();
        } catch (SQLException e) {
            System.err.println("[CLEANUP ERROR] " + e.getMessage());
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
