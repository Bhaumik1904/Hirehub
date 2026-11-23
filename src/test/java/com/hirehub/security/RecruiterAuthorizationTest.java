package com.hirehub.security;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.controller.ApplicantManagementController;
import com.hirehub.controller.JobFormController;
import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.UserDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.AdminDashboardSnapshot;
import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.User;
import com.hirehub.service.AdminAnalyticsService;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.JobService;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RecruiterAuthorizationTest — Automated security, isolation, timer-lifecycle,
 * and regression verification suite for Phase 5 Step 3A (Recruiter Security Hardening).
 *
 * <p>Covers all 14 verification scenarios using:
 * <ul>
 *   <li>Offline controller / FXML / session-guard / PauseTransition lifecycle tests</li>
 *   <li>Isolated two-recruiter + student MySQL fixture tests with guaranteed cleanup</li>
 * </ul>
 *
 * Run via: {@code verify_phase4.ps1 -MainClass com.hirehub.util.RecruiterAuthorizationTest}
 */
public class RecruiterAuthorizationTest {

    private static int testsPassed  = 0;
    private static int testsFailed  = 0;
    private static int testsSkipped = 0;

    private static final int DB_CASE_COUNT = 10;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 5 Step 3A: Recruiter Security Hardening Verifier");
        System.out.println("=================================================================\n");

        initJavaFxToolkit();

        try {
            // Offline / Controller / Lifecycle tests (always run)
            testUnauthorizedControllerAccessOffline();
            testForgedControllerParametersOffline();
            testJobFormPauseTransitionLifecycle();
            testBulkCountApiPresence();

            // Live DB Fixture tests (Cases 4-14)
            boolean dbAvailable = checkDbConnection();
            if (!dbAvailable) {
                System.out.println("\n  [WARN] MySQL unavailable - skipping " + DB_CASE_COUNT + " DB fixture checks.");
                testsSkipped += DB_CASE_COUNT;
            } else {
                runIsolatedDatabaseSecuritySuite();
            }
        } catch (Throwable t) {
            System.err.println("\n  [FATAL] Verifier aborted unexpectedly: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        } finally {
            SessionManager.getInstance().logout();
        }

        System.out.println("\n=================================================================");
        System.out.printf("  STEP 3A SECURITY SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                testsPassed, testsFailed, testsSkipped);
        System.out.println("=================================================================");

        System.exit(testsFailed > 0 ? 1 : 0);
    }

    // =========================================================================
    // JavaFX Bootstrap
    // =========================================================================

    private static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already started
        }
        Platform.setImplicitExit(false);
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }

    private static <T> T runOnFx(FxCallable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(10, TimeUnit.SECONDS);
    }

    private static boolean checkDbConnection() {
        try {
            return DatabaseConnection.testConnection();
        } catch (Exception e) {
            System.err.println("  [WARN] DB check error: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // Section A — Controller Role Guards, Parameter Validation & Timer
    // =========================================================================

    private static void testUnauthorizedControllerAccessOffline() throws Exception {
        System.out.println("[Section A1] Role Guards on Recruiter FXML Controllers (Cases 1, 2, 3)");

        // Case 1: Student session
        User student = new User("Sec Student", "student_offline@test.local", "hash", User.Role.STUDENT);
        student.setId(9001);
        SessionManager.getInstance().login(student);

        assertCondition(verifyFxmlFailsClosed("/fxml/recruiter_dashboard.fxml"),
                "Case 1a: Student session fails closed on recruiter_dashboard.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/job_form.fxml"),
                "Case 1b: Student session fails closed on job_form.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/applicant_management.fxml"),
                "Case 1c: Student session fails closed on applicant_management.fxml");

        // Case 2: Admin session
        User admin = new User("Sec Admin", "admin_offline@test.local", "hash", User.Role.ADMIN);
        admin.setId(9002);
        SessionManager.getInstance().login(admin);

        assertCondition(verifyFxmlFailsClosed("/fxml/recruiter_dashboard.fxml"),
                "Case 2a: Admin session fails closed on recruiter_dashboard.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/job_form.fxml"),
                "Case 2b: Admin session fails closed on job_form.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/applicant_management.fxml"),
                "Case 2c: Admin session fails closed on applicant_management.fxml");

        // Case 3: Unauthenticated session
        SessionManager.getInstance().logout();

        assertCondition(verifyFxmlFailsClosed("/fxml/recruiter_dashboard.fxml"),
                "Case 3a: Unauthenticated session fails closed on recruiter_dashboard.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/job_form.fxml"),
                "Case 3b: Unauthenticated session fails closed on job_form.fxml");
        assertCondition(verifyFxmlFailsClosed("/fxml/applicant_management.fxml"),
                "Case 3c: Unauthenticated session fails closed on applicant_management.fxml");
    }

    private static boolean verifyFxmlFailsClosed(String fxmlPath) throws Exception {
        return runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource(fxmlPath));
            Parent root = loader.load();
            return !root.isVisible() && !root.isManaged();
        });
    }

    private static void testForgedControllerParametersOffline() throws Exception {
        System.out.println("\n[Section A2] Untrusted init() Parameter & Null/Foreign Job Guards (Case 12 offline)");

        User recruiterA = new User("Recruiter A", "rec_a_offline@test.local", "hash", User.Role.RECRUITER);
        recruiterA.setId(9101);
        SessionManager.getInstance().login(recruiterA);

        Job ownedJob = new Job(9101, "Owned Role", "Acme", "Desc", "Java", "Remote",
                Job.JobType.FULL_TIME, "10LPA", "BTech", LocalDate.now().plusDays(10), Job.Status.ACTIVE);
        ownedJob.setId(5001);

        Job foreignJob = new Job(9202, "Foreign Role", "OtherCo", "Desc", "Python", "Remote",
                Job.JobType.FULL_TIME, "12LPA", "BTech", LocalDate.now().plusDays(10), Job.Status.ACTIVE);
        foreignJob.setId(5002);

        boolean jobFormMismatchedRecruiterBlocked = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/job_form.fxml"));
            Parent root = loader.load();
            JobFormController ctrl = loader.getController();
            ctrl.init(9202, null);
            return !root.isVisible() && !root.isManaged();
        });
        assertCondition(jobFormMismatchedRecruiterBlocked,
                "Case 12a: JobFormController rejects mismatched recruiterId parameter");

        boolean jobFormForeignJobBlocked = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/job_form.fxml"));
            Parent root = loader.load();
            JobFormController ctrl = loader.getController();
            ctrl.init(9101, foreignJob);
            return !root.isVisible() && !root.isManaged();
        });
        assertCondition(jobFormForeignJobBlocked,
                "Case 12b: JobFormController rejects foreign jobToEdit parameter");

        boolean applicantMismatchedRecruiterBlocked = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/applicant_management.fxml"));
            Parent root = loader.load();
            ApplicantManagementController ctrl = loader.getController();
            ctrl.init(9202, ownedJob);
            return !root.isVisible() && !root.isManaged();
        });
        assertCondition(applicantMismatchedRecruiterBlocked,
                "Case 12c: ApplicantManagementController rejects mismatched recruiterId parameter");

        boolean applicantNullJobBlocked = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/applicant_management.fxml"));
            Parent root = loader.load();
            ApplicantManagementController ctrl = loader.getController();
            ctrl.init(9101, null);
            return !root.isVisible() && !root.isManaged();
        });
        assertCondition(applicantNullJobBlocked,
                "Case 12d: ApplicantManagementController rejects null job parameter");

        boolean applicantForeignJobBlocked = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/applicant_management.fxml"));
            Parent root = loader.load();
            ApplicantManagementController ctrl = loader.getController();
            ctrl.init(9101, foreignJob);
            return !root.isVisible() && !root.isManaged();
        });
        assertCondition(applicantForeignJobBlocked,
                "Case 12e: ApplicantManagementController rejects foreign job parameter");

        boolean fallbackMethodRemoved = Arrays.stream(ApplicantManagementController.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("getEffectiveRecruiterId"));
        assertCondition(fallbackMethodRemoved,
                "Unsafe getEffectiveRecruiterId() fallback removed from ApplicantManagementController");
    }

    private static void testJobFormPauseTransitionLifecycle() throws Exception {
        System.out.println("\n[Section A3] JobFormController PauseTransition Lifecycle");

        boolean hasNoUtilTimer = Arrays.stream(JobFormController.class.getDeclaredFields())
                .noneMatch(f -> f.getType().getName().equals("java.util.Timer"));
        boolean hasPauseTransition = Arrays.stream(JobFormController.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(PauseTransition.class));

        assertCondition(hasNoUtilTimer, "JobFormController contains no java.util.Timer field");
        assertCondition(hasPauseTransition, "JobFormController uses JavaFX PauseTransition for redirect");

        User recruiterA = new User("Recruiter A", "rec_timer@test.local", "hash", User.Role.RECRUITER);
        recruiterA.setId(9101);
        SessionManager.getInstance().login(recruiterA);

        boolean cancelStopsTransition = runOnFx(() -> {
            FXMLLoader loader = new FXMLLoader(RecruiterAuthorizationTest.class.getResource("/fxml/job_form.fxml"));
            loader.load();
            JobFormController ctrl = loader.getController();
            ctrl.init(9101, null);

            Method sched = JobFormController.class.getDeclaredMethod("scheduleDashboardRedirect", int.class);
            sched.setAccessible(true);
            sched.invoke(ctrl, 9101);

            Field ptField = JobFormController.class.getDeclaredField("redirectTransition");
            ptField.setAccessible(true);
            PauseTransition pt = (PauseTransition) ptField.get(ctrl);
            boolean wasRunning = pt != null && pt.getStatus() == Animation.Status.RUNNING;

            ctrl.handleCancel(null);
            PauseTransition afterCancel = (PauseTransition) ptField.get(ctrl);
            boolean stoppedAndCleared = afterCancel == null && pt.getStatus() == Animation.Status.STOPPED;
            return wasRunning && stoppedAndCleared;
        });

        assertCondition(cancelStopsTransition,
                "Pressing Back/Cancel immediately stops and clears PauseTransition");
    }

    private static void testBulkCountApiPresence() {
        System.out.println("\n[Section A4] Dashboard Bulk Count & Recruiter-Scoped API Contract");
        try {
            Method daoBulk = ApplicationDAO.class.getMethod("countByJobsForRecruiter", int.class);
            Method svcBulk = ApplicationService.class.getMethod("countApplicationsByJobForRecruiter", int.class);
            Method jobFind = JobService.class.getMethod("getJobByIdForRecruiter", int.class, int.class);
            Method appUpd  = ApplicationService.class.getMethod("updateApplicationStatusForRecruiter",
                    int.class, int.class, Application.Status.class);
            assertCondition(daoBulk != null && svcBulk != null && jobFind != null && appUpd != null,
                    "Recruiter-scoped DAO & Service bulk/atomic methods exposed cleanly");
        } catch (NoSuchMethodException e) {
            fail("Missing required recruiter-scoped method: " + e.getMessage());
        }
    }

    // =========================================================================
    // Section B — Isolated Database Two-Recruiter Fixture Verification
    // =========================================================================

    private static void runIsolatedDatabaseSecuritySuite() throws Exception {
        System.out.println("\n[Section B] Isolated Two-Recruiter DB Fixture Security Tests (Cases 4-14)");

        UserDAO userDAO = new UserDAO();
        JobDAO jobDAO = new JobDAO();
        ApplicationDAO applicationDAO = new ApplicationDAO();
        JobService jobService = new JobService();
        ApplicationService applicationService = new ApplicationService();

        int baselineUsers = countRows("users");
        int baselineJobs = countRows("jobs");
        int baselineApps = countRows("applications");
        int baselineProfiles = countRows("recruiter_profiles");

        long ts = System.currentTimeMillis();
        String passHash = PasswordUtil.hashPassword("Step3ASecure#2026");

        int recAId = -1;
        int recBId = -1;
        int studentId = -1;
        int jobAId = -1;
        int jobBActiveId = -1;
        int jobBDraftId = -1;

        try {
            User recA = new User("Sec3A Recruiter A", "sec3a_reca_" + ts + "@hirehub.test", passHash, User.Role.RECRUITER);
            recAId = userDAO.insertUser(recA);
            recA.setId(recAId);

            User recB = new User("Sec3A Recruiter B", "sec3a_recb_" + ts + "@hirehub.test", passHash, User.Role.RECRUITER);
            recBId = userDAO.insertUser(recB);
            recB.setId(recBId);

            User student = new User("Sec3A Student", "sec3a_stu_" + ts + "@hirehub.test", passHash, User.Role.STUDENT);
            studentId = userDAO.insertUser(student);
            student.setId(studentId);

            // Phase 6 Step 4.2: recruiter profiles must exist and be VERIFIED for recruiter job operations
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO recruiter_profiles (user_id, company_name, company_website, verification_status) "
                     + "VALUES (?, 'Alpha Corp', 'https://alpha.example.com', 'VERIFIED'), "
                     + "(?, 'Beta Corp', 'https://beta.example.com', 'VERIFIED')")) {
                ps.setInt(1, recAId);
                ps.setInt(2, recBId);
                ps.executeUpdate();
            }

            LocalDate futureDeadline = LocalDate.now().plusDays(21);

            SessionManager.getInstance().login(recA);
            JobService.ServiceResult crA = jobService.createJob(
                    recAId, "Sec3A Job A", "Alpha Corp", "Alpha job desc",
                    "Java, SQL", "Remote", "Full-Time", "12 LPA", "B.Tech", futureDeadline, false);
            Job jobA = crA.getJob();
            jobAId = jobA != null ? jobA.getId() : -1;

            SessionManager.getInstance().login(recB);
            JobService.ServiceResult crBAct = jobService.createJob(
                    recBId, "Sec3A Job B Active", "Beta Corp", "Beta secret active desc",
                    "Python, ML", "Bengaluru", "Full-Time", "18 LPA", "B.Tech", futureDeadline, false);
            Job jobBActive = crBAct.getJob();
            jobBActiveId = jobBActive != null ? jobBActive.getId() : -1;

            JobService.ServiceResult crBDraft = jobService.createJob(
                    recBId, "Sec3A Job B Draft", "Beta Corp", "Beta confidential draft desc",
                    "Go, K8s", "Hyderabad", "Internship", "40k", "Final Year", futureDeadline, true);
            Job jobBDraft = crBDraft.getJob();
            jobBDraftId = jobBDraft != null ? jobBDraft.getId() : -1;
            SessionManager.getInstance().logout();

            assertCondition(jobAId > 0 && jobBActiveId > 0 && jobBDraftId > 0,
                    "Fixture Setup: Created isolated Recruiter A job and Recruiter B active/draft jobs");

            // Phase 6 Step 4.2C.3: Active jobs must be APPROVED for students to submit applications
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE jobs SET moderation_status = 'APPROVED' WHERE id IN (?, ?)")) {
                ps.setInt(1, jobAId);
                ps.setInt(2, jobBActiveId);
                ps.executeUpdate();
            }

            SessionManager.getInstance().login(student);
            ApplicationService.ServiceResult applyA = applicationService.apply(studentId, jobAId, "Cover letter for A");
            ApplicationService.ServiceResult applyB = applicationService.apply(studentId, jobBActiveId, "Secret cover letter for B");
            SessionManager.getInstance().logout();
            assertCondition(applyA.isSuccess() && applyB.isSuccess(),
                    "Fixture Setup + Case 14a: Student submitted isolated applications cleanly");

            List<Application> ownerAppsB = applicationService.getApplicationsForRecruiterJob(jobBActiveId, recBId);
            List<Application> ownerAppsA = applicationService.getApplicationsForRecruiterJob(jobAId, recAId);
            Application appA = ownerAppsA.isEmpty() ? null : ownerAppsA.get(0);
            Application appB = ownerAppsB.isEmpty() ? null : ownerAppsB.get(0);
            if (appA == null || appB == null) {
                throw new IllegalStateException("Fixture setup could not resolve inserted applications.");
            }

            // Case 4: Recruiter A accesses own job
            SessionManager.getInstance().login(recA);
            Optional<Job> ownJobLookup = jobService.getJobByIdForRecruiter(jobAId, recAId);
            assertCondition(ownJobLookup.isPresent() && ownJobLookup.get().getId() == jobAId,
                    "Case 4a: Recruiter A retrieves own job via getJobByIdForRecruiter");
            JobService.ServiceResult ownEdit = jobService.updateJob(
                    recAId, jobAId, "Sec3A Job A Updated", "Alpha Corp", "Updated desc",
                    "Java, SQL", "Remote", "Full-Time", "14 LPA", "B.Tech", futureDeadline, Job.Status.ACTIVE);
            assertCondition(ownEdit.isSuccess(),
                    "Case 4b: Recruiter A edits own job cleanly");

            // Case 5: Recruiter A cannot retrieve Recruiter B's active or draft job via recruiter-scoped lookup
            Optional<Job> stealBActive = jobService.getJobByIdForRecruiter(jobBActiveId, recAId);
            Optional<Job> stealBDraft  = jobService.getJobByIdForRecruiter(jobBDraftId, recAId);
            Optional<Job> daoStealB    = jobDAO.findByIdAndRecruiter(jobBDraftId, recAId);
            assertCondition(stealBActive.isEmpty() && stealBDraft.isEmpty() && daoStealB.isEmpty(),
                    "Case 5: Recruiter A cannot retrieve Recruiter B's active or draft job via scoped lookup");

            // Case 6: Recruiter A cannot edit Recruiter B's job
            JobService.ServiceResult crossEditParams = jobService.updateJob(
                    recAId, jobBActiveId, "Hacked Title", "Evil Corp", "Tampered",
                    "None", "Nowhere", "Full-Time", "0", "None", futureDeadline, Job.Status.ACTIVE);
            Job forgedJobEntity = new Job(recAId, "Hacked Entity", "Evil Corp", "Tampered",
                    "None", "Nowhere", Job.JobType.FULL_TIME, "0", "None", futureDeadline, Job.Status.ACTIVE);
            forgedJobEntity.setId(jobBActiveId);
            JobService.ServiceResult crossEditEntity = jobService.updateJobForRecruiter(forgedJobEntity, recAId);
            int daoUpdateRows = jobDAO.updateForRecruiter(forgedJobEntity, recAId);
            assertCondition(!crossEditParams.isSuccess() && !crossEditEntity.isSuccess() && daoUpdateRows == 0,
                    "Case 6: Cross-recruiter job edit rejected atomically at Service & DAO layers");

            // Case 7: Recruiter A cannot close or activate Recruiter B's job
            JobService.ServiceResult crossClose = jobService.closeJob(recAId, jobBActiveId);
            JobService.ServiceResult crossActivate = jobService.activateJob(recAId, jobBDraftId);
            JobService.ServiceResult crossTransition = jobService.updateJobStatusForRecruiter(
                    jobBActiveId, recAId, Job.Status.CLOSED);
            int daoStatusRows = jobDAO.updateStatusForRecruiter(jobBDraftId, recAId, Job.Status.ACTIVE);
            assertCondition(!crossClose.isSuccess() && !crossActivate.isSuccess()
                            && !crossTransition.isSuccess() && daoStatusRows == 0,
                    "Case 7: Cross-recruiter job status transitions rejected atomically");

            // Case 8: Recruiter A cannot delete Recruiter B's job
            JobService.ServiceResult crossDelete1 = jobService.deleteJob(recAId, jobBActiveId);
            JobService.ServiceResult crossDelete2 = jobService.deleteJobForRecruiter(jobBDraftId, recAId);
            int daoDeleteRows = jobDAO.deleteForRecruiter(jobBActiveId, recAId);
            assertCondition(!crossDelete1.isSuccess() && !crossDelete2.isSuccess() && daoDeleteRows == 0,
                    "Case 8a: Cross-recruiter job deletion rejected atomically");

            // Case 9: Recruiter A cannot retrieve applications belonging to Recruiter B
            List<Application> crossApps1 = applicationService.getApplicationsForRecruiterJob(jobBActiveId, recAId);
            List<Application> crossApps2 = applicationService.getApplicationsByJob(recAId, jobBActiveId);
            Optional<Application> crossSingleApp = applicationService.getApplicationByIdForRecruiter(appB.getId(), recAId);
            Optional<Application> daoSingleApp = applicationDAO.findByIdAndRecruiter(appB.getId(), recAId);
            assertCondition(crossApps1.isEmpty() && crossApps2.isEmpty()
                            && crossSingleApp.isEmpty() && daoSingleApp.isEmpty(),
                    "Case 9: Cross-recruiter applicant list and single-application lookup return empty");

            // Case 10: Recruiter A cannot update Recruiter B's application status
            ApplicationService.ServiceResult crossShortlist =
                    applicationService.updateApplicationStatus(recAId, appB.getId(), Application.Status.SHORTLISTED);
            ApplicationService.ServiceResult crossSelect =
                    applicationService.updateApplicationStatusForRecruiter(appB.getId(), recAId, Application.Status.SELECTED);
            ApplicationService.ServiceResult crossReject =
                    applicationService.updateApplicationStatusForRecruiter(appB.getId(), recAId, Application.Status.REJECTED);
            int daoAppRows = applicationDAO.updateStatusForRecruiter(appB.getId(), recAId, Application.Status.REJECTED);
            assertCondition(!crossShortlist.isSuccess() && !crossSelect.isSuccess()
                            && !crossReject.isSuccess() && daoAppRows == 0,
                    "Case 10a: Cross-recruiter application status updates rejected atomically");

            // Positive check: Recruiter A updates own application status
            ApplicationService.ServiceResult ownShortlist =
                    applicationService.updateApplicationStatusForRecruiter(appA.getId(), recAId, Application.Status.SHORTLISTED);
            assertCondition(ownShortlist.isSuccess(),
                    "Case 10b: Owner Recruiter A successfully updates own application status");

            // Case 11: Recruiter A cannot obtain application counts for Recruiter B's job
            SessionManager.getInstance().login(recA);
            int crossScopedCount = applicationService.countApplicationsForJob(jobBActiveId, recAId);
            @SuppressWarnings("deprecation")
            int crossLegacyCount = applicationService.countApplicationsForJob(jobBActiveId);
            Map<Integer, Integer> bulkCountsA = applicationService.countApplicationsByJobForRecruiter(recAId);
            SessionManager.getInstance().logout();
            @SuppressWarnings("deprecation")
            int unauthLegacyCount = applicationService.countApplicationsForJob(jobAId);

            assertCondition(crossScopedCount == 0
                            && crossLegacyCount == 0
                            && unauthLegacyCount == 0
                            && !bulkCountsA.containsKey(jobBActiveId)
                            && bulkCountsA.getOrDefault(jobAId, 0) == 1,
                    "Case 11: Recruiter A cannot obtain counts for Recruiter B; bulk count matches only Recruiter A");

            // Case 12 DB: Forged in-memory jobToEdit / job with spoofed recruiterId == recAId and id == jobBActiveId
            final int finalRecAId = recAId;
            SessionManager.getInstance().login(recA);
            Job forgedCopy = new Job(finalRecAId, "Spoofed Job B", "Alpha Corp", "Spoof",
                    "Java", "Remote", Job.JobType.FULL_TIME, "12", "All", futureDeadline, Job.Status.ACTIVE);
            forgedCopy.setId(jobBActiveId);

            boolean appCtrlBlockedForgedJob = runForgedApplicantControllerTest(finalRecAId, forgedCopy);
            boolean formCtrlBlockedForgedJob = runForgedJobFormControllerTest(finalRecAId, forgedCopy);
            assertCondition(appCtrlBlockedForgedJob && formCtrlBlockedForgedJob,
                    "Case 12f: Controllers block forged Job entities (spoofed recruiterId + foreign jobId) via DB verification");
            SessionManager.getInstance().logout();

            // Case 13: Verify zero unintended mutations occurred on Recruiter B's records
            SessionManager.getInstance().login(recB);
            Optional<Job> verifyBAct = jobService.getJobByIdForRecruiter(jobBActiveId, recBId);
            Optional<Job> verifyBDraft = jobService.getJobByIdForRecruiter(jobBDraftId, recBId);
            Optional<Application> verifyAppB = applicationService.getApplicationByIdForRecruiter(appB.getId(), recBId);

            boolean untouchedB = verifyBAct.isPresent()
                    && "Sec3A Job B Active".equals(verifyBAct.get().getTitle())
                    && verifyBAct.get().getStatus() == Job.Status.ACTIVE
                    && verifyBDraft.isPresent()
                    && verifyBDraft.get().getStatus() == Job.Status.DRAFT
                    && verifyAppB.isPresent()
                    && verifyAppB.get().getStatus() == Application.Status.PENDING;
            assertCondition(untouchedB,
                    "Case 13: Invalid cross-recruiter operations left all Recruiter B job and application records untouched");

            // Positive delete test for Recruiter A's own temporary job (after application check)
            SessionManager.getInstance().login(recA);
            JobService.ServiceResult ownDelete = jobService.deleteJobForRecruiter(jobAId, recAId);
            assertCondition(ownDelete.isSuccess() && jobService.getJobByIdForRecruiter(jobAId, recAId).isEmpty(),
                    "Case 8b: Recruiter A successfully deletes own owned job");
            SessionManager.getInstance().logout();

            // Case 14: Student public job search & Admin analytics still work cleanly
            final int expectedBActiveId = jobBActiveId;
            final int expectedBDraftId = jobBDraftId;
            List<Job> studentVisible = jobService.searchJobs("Sec3A Job B", "", "");
            boolean seesActiveNotDraft = studentVisible.stream().anyMatch(j -> j.getId() == expectedBActiveId)
                    && studentVisible.stream().noneMatch(j -> j.getId() == expectedBDraftId);
            SessionManager.getInstance().login(student);
            List<Application> studentApps = applicationService.getStudentApplications(studentId);
            SessionManager.getInstance().logout();
            boolean seesOwnAppB = studentApps.stream().anyMatch(a -> a.getJobId() == expectedBActiveId);
            AdminDashboardSnapshot adminSnapshot = new AdminAnalyticsService().loadAllTimeSnapshot();

            assertCondition(seesActiveNotDraft && seesOwnAppB && !adminSnapshot.hasError(),
                    "Case 14b: Student job search/applications and Admin analytics remain intact");

        } finally {
            SessionManager.getInstance().logout();
            cleanupFixtures(studentId, recAId, recBId);

            int afterUsers = countRows("users");
            int afterJobs = countRows("jobs");
            int afterApps = countRows("applications");
            int afterProfiles = countRows("recruiter_profiles");
            assertCondition(afterUsers == baselineUsers && afterJobs == baselineJobs
                    && afterApps == baselineApps && afterProfiles == baselineProfiles,
                    String.format("Cleanup verification: row counts restored (users=%d, jobs=%d, applications=%d, recruiter_profiles=%d)",
                            afterUsers, afterJobs, afterApps, afterProfiles));
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean runForgedApplicantControllerTest(int recAId, Job forgedCopy) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                RecruiterAuthorizationTest.class.getResource("/fxml/applicant_management.fxml"));
        Parent[] rootHolder = new Parent[1];
        ApplicantManagementController[] ctrlHolder = new ApplicantManagementController[1];

        runOnFx(() -> {
            rootHolder[0] = loader.load();
            ctrlHolder[0] = loader.getController();
            ctrlHolder[0].init(recAId, forgedCopy);
            return null;
        });

        long deadline = System.currentTimeMillis() + 3000;
        boolean hidden = false;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(60);
            hidden = runOnFx(() -> {
                Parent root = rootHolder[0];
                Field tableField = ApplicantManagementController.class.getDeclaredField("applicantsTable");
                tableField.setAccessible(true);
                TableView<Application> tbl = (TableView<Application>) tableField.get(ctrlHolder[0]);
                return !root.isVisible() && !root.isManaged() && (tbl == null || tbl.getItems().isEmpty());
            });
            if (hidden) break;
        }
        return hidden;
    }

    private static boolean runForgedJobFormControllerTest(int recAId, Job forgedCopy) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                RecruiterAuthorizationTest.class.getResource("/fxml/job_form.fxml"));
        Parent[] rootHolder = new Parent[1];
        JobFormController[] ctrlHolder = new JobFormController[1];

        runOnFx(() -> {
            rootHolder[0] = loader.load();
            ctrlHolder[0] = loader.getController();
            ctrlHolder[0].init(recAId, forgedCopy);
            return null;
        });

        long deadline = System.currentTimeMillis() + 3000;
        boolean hidden = false;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(60);
            hidden = runOnFx(() -> {
                Parent root = rootHolder[0];
                return !root.isVisible() && !root.isManaged();
            });
            if (hidden) break;
        }
        return hidden;
    }

    private static void cleanupFixtures(int studentId, int recAId, int recBId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (studentId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE student_id = ?")) {
                    ps.setInt(1, studentId);
                    ps.executeUpdate();
                }
            }
            if (recAId > 0 || recBId > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE a FROM applications a JOIN jobs j ON a.job_id = j.id WHERE j.recruiter_id IN (?, ?)")) {
                    ps.setInt(1, recAId);
                    ps.setInt(2, recBId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM jobs WHERE recruiter_id IN (?, ?)")) {
                    ps.setInt(1, recAId);
                    ps.setInt(2, recBId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM recruiter_profiles WHERE user_id IN (?, ?)")) {
                    ps.setInt(1, recAId);
                    ps.setInt(2, recBId);
                    ps.executeUpdate();
                }
            }
            if (studentId > 0 || recAId > 0 || recBId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id IN (?, ?, ?)")) {
                    ps.setInt(1, studentId);
                    ps.setInt(2, recAId);
                    ps.setInt(3, recBId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("  [WARN] Fixture cleanup encountered SQL error: " + e.getMessage());
        }
    }

    private static int countRows(String table) throws SQLException {
        String sql = switch (table) {
            case "users" -> "SELECT COUNT(*) FROM users";
            case "jobs" -> "SELECT COUNT(*) FROM jobs";
            case "applications" -> "SELECT COUNT(*) FROM applications";
            case "recruiter_profiles" -> "SELECT COUNT(*) FROM recruiter_profiles";
            default -> throw new IllegalArgumentException("Unsupported table: " + table);
        };
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // =========================================================================
    // Assertions
    // =========================================================================

    private static void assertCondition(boolean condition, String label) {
        if (condition) {
            System.out.println("  [PASS] " + label);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + label);
            testsFailed++;
        }
    }

    private static void fail(String label) {
        System.err.println("  [FAIL] " + label);
        testsFailed++;
    }
}
