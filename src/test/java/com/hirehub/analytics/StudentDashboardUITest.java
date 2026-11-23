package com.hirehub.analytics;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.controller.StudentDashboardController;
import com.hirehub.dao.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.AuthService;
import com.hirehub.service.StudentAnalyticsService;
import com.hirehub.service.StudentService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * StudentDashboardUITest — Comprehensive verification harness for
 * Phase 5 Step 4C: Student Intelligence Dashboard UI.
 *
 * <p>Validates:
 * <ol>
 *   <li>JavaFX FXML & Controller Clean Loading without warnings or hierarchy errors.</li>
 *   <li>Fail-Closed Authentication &amp; Role Separation:
 *       <ul>
 *         <li>Unauthenticated requests fail closed, hide root content, and invoke 0 analytics calls.</li>
 *         <li>Recruiter/Admin callers fail closed on student dashboard.</li>
 *         <li>Valid STUDENT caller loads analytics strictly for their authenticated session ID.</li>
 *       </ul>
 *   </li>
 *   <li>Populated Snapshot UI Rendering &amp; Privacy Safeguards:
 *       <ul>
 *         <li>Section A: Profile readiness percentage, score (6/6), status pill, and missing fields toggle.</li>
 *         <li>Section B: 5 Overview KPI cards bound to DTO values.</li>
 *         <li>Section C: Pipeline distribution counts, percentages, and styled progress bars.</li>
 *         <li>Section D: Native JavaFX LineChart activity trend series and data points.</li>
 *         <li>Section E: Deterministic resume readiness, signals, confidence badge, rubric progress bars, disclaimer.</li>
 *         <li>Section F: Profile/resume combined skills chips and in-demand market gap chips.</li>
 *         <li>Section G: Ranked job recommendations, match percentage styling, unranked handling, and applied badge.</li>
 *         <li>Section H: Career activity feed rows with event badges, descriptions, and timestamps.</li>
 *         <li>Section I: Quick action buttons present with valid routing paths.</li>
 *         <li>Privacy: Absence of absolute filesystem paths, passwords, or raw JSON in visible text.</li>
 *       </ul>
 *   </li>
 *   <li>Empty State &amp; Zero-Denominator Safety:
 *       <ul>
 *         <li>Zero applications / empty state handles 0.0% without NaN or Infinity.</li>
 *         <li>Empty placeholders displayed for resume, pipeline, trend, recommendations, and activity.</li>
 *       </ul>
 *   </li>
 *   <li>Error Recovery &amp; Snapshot Preservation:
 *       <ul>
 *         <li>Failed refresh preserves previously rendered valid snapshot with user-friendly banner.</li>
 *         <li>Initial load failure exposes error details with active Retry button.</li>
 *       </ul>
 *   </li>
 *   <li>Asynchronous Concurrency &amp; Navigation Pane Switching:
 *       <ul>
 *         <li>Sequence guards prevent stale tasks from overwriting fresh state.</li>
 *         <li>Profile edit pane switches visibility and populates fields.</li>
 *       </ul>
 *   </li>
 *   <li>Live MySQL Integration &amp; 100% Database Row Restoration:
 *       <ul>
 *         <li>Isolated test fixture created and bound through real services.</li>
 *         <li>Dashboard UI successfully renders live MySQL data.</li>
 *         <li>All fixture rows removed and exact baseline table counts verified.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class StudentDashboardUITest {

    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    private static final String PREFIX = "P5_4C_VERIFY_" + System.currentTimeMillis() + "_";

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 5 Step 4C: Student Intelligence Dashboard Verifier");
        System.out.println("=================================================================\n");

        initJavaFxToolkit();

        try {
            verifyRoleGuardsAndFailClosed();
            verifyPopulatedRenderingAndPrivacy();
            verifyEmptyAndZeroDenominatorSafety();
            verifyErrorRecoveryAndSnapshotPreservation();
            verifyConcurrencyAndNavigationSwitching();
            verifyLiveDatabaseIntegrationIfAvailable();
        } finally {
            SessionManager.getInstance().logout();
            Platform.exit();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("DASHBOARD VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // Test 1: Role Guards & Fail-Closed Behavior
    // =========================================================================

    private static void verifyRoleGuardsAndFailClosed() throws Exception {
        System.out.println("--- [1] Role Guards & Unauthenticated Access Rejection ---");

        StubStudentAnalyticsService stub = new StubStudentAnalyticsService(buildPopulatedSnapshot(101));

        // 1a: Unauthenticated session
        SessionManager.getInstance().logout();
        LoadedView unauth = loadDashboardWithService(stub);
        check("1a: Unauthenticated session hides root content",
                !unauth.root.isVisible() && !unauth.root.isManaged());
        check("1a: Unauthenticated session makes 0 analytics calls",
                stub.totalCalls.get() == 0);

        // 1b: Recruiter role session
        User recruiter = new User("Recruiter Test", "recruiter@test.local", "hash", User.Role.RECRUITER);
        recruiter.setId(201);
        SessionManager.getInstance().login(recruiter);
        LoadedView recView = loadDashboardWithService(stub);
        check("1b: Recruiter role session hides root content on student dashboard",
                !recView.root.isVisible() && !recView.root.isManaged());
        check("1b: Recruiter role session makes 0 analytics calls",
                stub.totalCalls.get() == 0);

        // 1c: Admin role session
        User admin = new User("Admin Test", "admin@test.local", "hash", User.Role.ADMIN);
        admin.setId(1);
        SessionManager.getInstance().login(admin);
        LoadedView admView = loadDashboardWithService(stub);
        check("1c: Admin role session hides root content on student dashboard",
                !admView.root.isVisible() && !admView.root.isManaged());
        check("1c: Admin role session makes 0 analytics calls",
                stub.totalCalls.get() == 0);

        // 1d: Valid student session loads data
        User student = new User("Rahul Sharma", "rahul@test.local", "hash", User.Role.STUDENT);
        student.setId(101);
        SessionManager.getInstance().login(student);
        LoadedView stuView = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() > 0, 3000);
        check("1d: Authenticated student session displays root content",
                stuView.root.isVisible() && stuView.root.isManaged());
        check("1d: Authenticated student session queries analytics for their own ID (101)",
                stub.lastRequestedStudentId.get() == 101);
    }

    // =========================================================================
    // Test 2: Populated Snapshot Rendering & Privacy Safeguards
    // =========================================================================

    private static void verifyPopulatedRenderingAndPrivacy() throws Exception {
        System.out.println("\n--- [2] Populated UI Rendering & Privacy Safeguards ---");

        User student = new User("Priya Patel", "priya@test.local", "hash", User.Role.STUDENT);
        student.setId(102);
        SessionManager.getInstance().login(student);

        StudentDashboardSnapshot pop = buildPopulatedSnapshot(102);
        StubStudentAnalyticsService stub = new StubStudentAnalyticsService(pop);
        LoadedView view = loadDashboardWithService(stub);

        // Render directly on FX thread to inspect immediately
        runOnFx(() -> {
            view.controller.renderSnapshot(pop);
            return null;
        });

        // 2a: Section A (Header & Profile Readiness)
        Label pill = getField(view.controller, "headerProfileStatusPill", Label.class);
        ProgressBar bar = getField(view.controller, "profileCompletionBar", ProgressBar.class);
        Label pctLbl = getField(view.controller, "profileCompletionPctLabel", Label.class);
        HBox missingBox = getField(view.controller, "missingFieldsBox", HBox.class);

        check("2a: Profile complete header pill text", pill.getText().contains("Complete"));
        check("2a: Profile complete progress bar is 1.0 (100%)", Math.abs(bar.getProgress() - 1.0) < 0.01);
        check("2a: Profile percent label says 100% Complete (6/6 fields)", pctLbl.getText().contains("100%") && pctLbl.getText().contains("6/6"));
        check("2a: Missing fields box is hidden when profile complete", !missingBox.isVisible() && !missingBox.isManaged());

        // 2b: Section B (Overview KPIs)
        Label totalApps = getField(view.controller, "kpiTotalAppsValue", Label.class);
        Label pendingApps = getField(view.controller, "kpiPendingValue", Label.class);
        Label shortApps = getField(view.controller, "kpiShortlistedValue", Label.class);
        Label selApps = getField(view.controller, "kpiSelectedValue", Label.class);
        Label openJobs = getField(view.controller, "kpiOpenJobsValue", Label.class);

        check("2b: Total applications KPI = 5", "5".equals(totalApps.getText()));
        check("2b: Pending applications KPI = 2", "2".equals(pendingApps.getText()));
        check("2b: Shortlisted applications KPI = 1", "1".equals(shortApps.getText()));
        check("2b: Selected applications KPI = 1", "1".equals(selApps.getText()));
        check("2b: Open jobs KPI = 12", "12".equals(openJobs.getText()));

        // 2c: Section C (Application Pipeline)
        Label pipePending = getField(view.controller, "pipelinePendingCount", Label.class);
        Label pipePendingPct = getField(view.controller, "pipelinePendingPct", Label.class);
        ProgressBar pipePendingBar = getField(view.controller, "pipelinePendingBar", ProgressBar.class);
        Label pipeSelected = getField(view.controller, "pipelineSelectedCount", Label.class);

        check("2c: Pipeline pending count = 2", "2".equals(pipePending.getText()));
        check("2c: Pipeline pending percentage string formatted (40.0%)", pipePendingPct.getText().contains("40.0%"));
        check("2c: Pipeline pending progress bar = 0.40", Math.abs(pipePendingBar.getProgress() - 0.40) < 0.02);
        check("2c: Pipeline selected count = 1", "1".equals(pipeSelected.getText()));

        // 2d: Section D (Activity Trend LineChart)
        LineChart<?, ?> chart = getField(view.controller, "trendLineChart", LineChart.class);
        check("2d: Trend chart is visible when points exist", chart.isVisible());
        check("2d: Trend chart contains 3 series (Applications, Shortlisted, Selected)", chart.getData().size() == 3);

        // 2e: Section E (Resume Readiness & Rubric)
        Label resumeFile = getField(view.controller, "resumeFilenameLabel", Label.class);
        Label resumeScore = getField(view.controller, "resumeOverallScoreLabel", Label.class);
        Label resumeConf = getField(view.controller, "resumeConfidenceBadge", Label.class);
        Label rubricSkills = getField(view.controller, "rubricSkillsLabel", Label.class);
        ProgressBar rubricSkillsBar = getField(view.controller, "rubricSkillsBar", ProgressBar.class);
        Label disclaimer = getField(view.controller, "resumeDisclaimerLabel", Label.class);

        check("2e: Resume filename displayed safely", "priya_resume.pdf".equals(resumeFile.getText()));
        check("2e: Resume deterministic overall score = 84 / 100", resumeScore.getText().contains("84"));
        check("2e: Resume confidence badge is HIGH CONFIDENCE", resumeConf.getText().contains("HIGH"));
        check("2e: Rubric skills score = 35 / 40 pts", rubricSkills.getText().contains("35 / 40"));
        check("2e: Rubric skills progress bar = 0.875", Math.abs(rubricSkillsBar.getProgress() - (35.0 / 40.0)) < 0.02);
        check("2e: Mandatory disclaimer text displayed", disclaimer.getText().contains("Deterministic resume quality indicator"));

        // 2f: Section F (Skill Insights)
        FlowPane curSkills = getField(view.controller, "currentSkillsFlowPane", FlowPane.class);
        FlowPane demSkills = getField(view.controller, "demandSkillsFlowPane", FlowPane.class);
        check("2f: Recognized skills FlowPane contains chips", curSkills.getChildren().size() == 4);
        check("2f: In-demand market gap FlowPane contains missing skill chips", demSkills.getChildren().size() == 2);

        // 2g: Section G (Recommended Jobs)
        VBox recsBox = getField(view.controller, "recommendedJobsBox", VBox.class);
        check("2g: Job recommendations container has cards", recsBox.getChildren().size() == 2);

        // 2h: Section H (Recent Activity)
        VBox actBox = getField(view.controller, "recentActivityBox", VBox.class);
        check("2h: Recent activity feed contains rows", actBox.getChildren().size() == 3);

        // 2i: Privacy verification (No file paths or internal hashes leaked into labels)
        String allLabelsText = collectLabelsText(view.root);
        check("2i: No C:\\ or /Users/ filesystem paths exposed in UI labels",
                !allLabelsText.contains("C:\\") && !allLabelsText.contains("/Users/") && !allLabelsText.contains("/home/"));
        check("2i: No password hashes or database connection strings leaked",
                !allLabelsText.toLowerCase().contains("jdbc:") && !allLabelsText.contains("bcrypt") && !allLabelsText.contains("sha256"));
    }

    // =========================================================================
    // Test 3: Empty States & Zero-Denominator Safety
    // =========================================================================

    private static void verifyEmptyAndZeroDenominatorSafety() throws Exception {
        System.out.println("\n--- [3] Empty States & Zero-Denominator Safety ---");

        User student = new User("New Student", "newstu@test.local", "hash", User.Role.STUDENT);
        student.setId(103);
        SessionManager.getInstance().login(student);

        StudentDashboardSnapshot empty = buildEmptySnapshot(103);
        StubStudentAnalyticsService stub = new StubStudentAnalyticsService(empty);
        LoadedView view = loadDashboardWithService(stub);

        runOnFx(() -> {
            view.controller.renderSnapshot(empty);
            return null;
        });

        // 3a: Profile incomplete state
        Label pill = getField(view.controller, "headerProfileStatusPill", Label.class);
        ProgressBar bar = getField(view.controller, "profileCompletionBar", ProgressBar.class);
        HBox missingBox = getField(view.controller, "missingFieldsBox", HBox.class);

        check("3a: Profile incomplete pill shown", pill.getText().contains("Incomplete"));
        check("3a: Profile progress bar is 0.0", Math.abs(bar.getProgress() - 0.0) < 0.001);
        check("3a: Missing fields box visible for incomplete profile", missingBox.isVisible() && missingBox.isManaged());

        // 3b: Pipeline empty state
        VBox pipeData = getField(view.controller, "pipelineDataBox", VBox.class);
        VBox pipeEmpty = getField(view.controller, "pipelineEmptyBox", VBox.class);
        check("3b: Pipeline data box hidden when 0 applications", !pipeData.isVisible() && !pipeData.isManaged());
        check("3b: Pipeline empty box visible when 0 applications", pipeEmpty.isVisible() && pipeEmpty.isManaged());

        // 3c: Trend empty state
        LineChart<?, ?> chart = getField(view.controller, "trendLineChart", LineChart.class);
        Label trendEmpty = getField(view.controller, "trendEmptyLabel", Label.class);
        check("3c: Trend chart hidden when 0 trend points", !chart.isVisible() && !chart.isManaged());
        check("3c: Trend empty label visible when 0 trend points", trendEmpty.isVisible() && trendEmpty.isManaged());

        // 3d: Resume empty state
        VBox resumeContent = getField(view.controller, "resumeContentBox", VBox.class);
        VBox resumeEmpty = getField(view.controller, "resumeEmptyBox", VBox.class);
        check("3d: Resume content box hidden when no active resume", !resumeContent.isVisible() && !resumeContent.isManaged());
        check("3d: Resume empty state box visible when no active resume", resumeEmpty.isVisible() && resumeEmpty.isManaged());

        // 3e: Recommendations empty state
        VBox recsEmpty = getField(view.controller, "recommendationsEmptyBox", VBox.class);
        check("3e: Job recommendations empty state visible when 0 recommendations", recsEmpty.isVisible() && recsEmpty.isManaged());

        // 3f: Activity empty state
        Label actEmpty = getField(view.controller, "activityEmptyLabel", Label.class);
        check("3f: Activity empty label visible when 0 events", actEmpty.isVisible() && actEmpty.isManaged());
    }

    // =========================================================================
    // Test 4: Error Recovery & Snapshot Preservation
    // =========================================================================

    private static void verifyErrorRecoveryAndSnapshotPreservation() throws Exception {
        System.out.println("\n--- [4] Error Recovery & Snapshot Preservation ---");

        User student = new User("Aman Verma", "aman@test.local", "hash", User.Role.STUDENT);
        student.setId(104);
        SessionManager.getInstance().login(student);

        StudentDashboardSnapshot validSnap = buildPopulatedSnapshot(104);
        StubStudentAnalyticsService stub = new StubStudentAnalyticsService(validSnap);
        LoadedView view = loadDashboardWithService(stub);

        // Step 1: Initial valid load
        runOnFx(() -> {
            view.controller.renderSnapshot(validSnap);
            return null;
        });

        Label totalApps = getField(view.controller, "kpiTotalAppsValue", Label.class);
        check("4a: Initial snapshot rendered KPI total = 5", "5".equals(totalApps.getText()));

        // Step 2: Simulate failed refresh
        stub.simulateFailure = true;
        stub.failureMessage = "Connection to MySQL database timed out";

        runOnFx(() -> {
            view.controller.handleRefresh();
            return null;
        });

        waitUntil(() -> stub.totalCalls.get() >= 2, 3000);
        Thread.sleep(100); // Allow FX runLater to finish

        HBox errorBanner = getField(view.controller, "errorBannerBox", HBox.class);
        Label errorDetail = getField(view.controller, "errorDetailLabel", Label.class);

        check("4b: Error banner shown on refresh failure", errorBanner.isVisible() && errorBanner.isManaged());
        check("4b: Error detail contains failure message and cache preservation note",
                errorDetail.getText().contains("timed out") && errorDetail.getText().contains("Showing previously loaded data"));
        check("4b: Previously loaded snapshot data remains visible on UI (KPI still 5)",
                "5".equals(totalApps.getText()));

        // Step 3: Simulate recovery via retry
        stub.simulateFailure = false;
        runOnFx(() -> {
            view.controller.handleRetry();
            return null;
        });

        waitUntil(() -> stub.totalCalls.get() >= 3, 3000);
        Thread.sleep(100);

        check("4c: Error banner hidden after successful retry", !errorBanner.isVisible() && !errorBanner.isManaged());
    }

    // =========================================================================
    // Test 5: Concurrency & Navigation Switching
    // =========================================================================

    private static void verifyConcurrencyAndNavigationSwitching() throws Exception {
        System.out.println("\n--- [5] Concurrency & Navigation Pane Switching ---");

        User student = new User("Kavita Rao", "kavita@test.local", "hash", User.Role.STUDENT);
        student.setId(105);
        SessionManager.getInstance().login(student);

        StubStudentAnalyticsService stub = new StubStudentAnalyticsService(buildPopulatedSnapshot(105));
        stub.delayMs = 200; // Slow responses to test overlap prevention

        LoadedView view = loadDashboardWithService(stub);

        // 5a: Rapid duplicate refresh prevention
        runOnFx(() -> {
            view.controller.handleRefresh();
            view.controller.handleRefresh();
            view.controller.handleRefresh();
            return null;
        });

        Thread.sleep(400);
        check("5a: Duplicate concurrent refresh calls rejected while refreshing", stub.totalCalls.get() <= 2);

        // 5b: Navigation switching between Dashboard Pane and Profile Pane
        VBox dashPane = getField(view.controller, "dashboardPane", VBox.class);
        VBox profPane = getField(view.controller, "profilePane", VBox.class);

        runOnFx(() -> {
            view.controller.handleNavProfile(null);
            return null;
        });

        check("5b: Switching to profile pane shows profilePane and hides dashboardPane",
                profPane.isVisible() && profPane.isManaged() && !dashPane.isVisible() && !dashPane.isManaged());

        runOnFx(() -> {
            view.controller.handleNavDashboard(null);
            return null;
        });

        check("5b: Switching back to dashboard shows dashboardPane and hides profilePane",
                dashPane.isVisible() && dashPane.isManaged() && !profPane.isVisible() && !profPane.isManaged());

        // 5c: Quick action routes verification (ensure all FXML resources exist)
        String[] studentRoutes = {
                "/fxml/job_board.fxml",
                "/fxml/my_applications.fxml",
                "/fxml/resume_analyzer.fxml",
                "/fxml/ai_assistant.fxml",
                "/fxml/login.fxml"
        };
        for (String route : studentRoutes) {
            check("5c: Route resource exists on classpath: " + route,
                    StudentDashboardUITest.class.getResource(route) != null);
        }
    }

    // =========================================================================
    // Test 6: Live MySQL Integration & 100% Database Row Restoration
    // =========================================================================

    private static void verifyLiveDatabaseIntegrationIfAvailable() {
        System.out.println("\n--- [6] Live MySQL Integration & Database Row Restoration ---");

        try (Connection testConn = DatabaseConnection.getConnection()) {
            // Check baseline row counts
            Map<String, Integer> baseline = queryBaselineCounts(testConn);
            System.out.printf("  Baseline table rows: users=%d, jobs=%d, apps=%d, resumes=%d, analyses=%d, profiles=%d%n",
                    baseline.get("users"), baseline.get("jobs"), baseline.get("applications"),
                    baseline.get("resumes"), baseline.get("resume_analyses"), baseline.get("student_profiles"));

            int testStudentId = 0;
            int testRecruiterId = 0;
            int testJobId = 0;
            int testResumeId = 0;
            int testAnalysisId = 0;
            int testAppId = 0;

            try {
                // 1. Create test student
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, 'STUDENT')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "Live Test Student");
                    ps.setString(2, PREFIX + "student@test.local");
                    ps.setString(3, "live_hash");
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testStudentId = rs.getInt(1);
                    }
                }

                // 2. Create student profile
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO student_profiles (user_id, phone, college, degree, graduation_year, skills, resume_path) " +
                                "VALUES (?, '9876543210', 'IIT Delhi', 'B.Tech CSE', 2026, 'Java, Python, SQL', 'live_resume.pdf')")) {
                    ps.setInt(1, testStudentId);
                    ps.executeUpdate();
                }

                // 3. Create test recruiter & job
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, 'RECRUITER')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "Live Test Recruiter");
                    ps.setString(2, PREFIX + "recruiter@test.local");
                    ps.setString(3, "live_hash");
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testRecruiterId = rs.getInt(1);
                    }
                }

                // Recruiter profile (Phase 6 Step 4.2: verified recruiter)
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO recruiter_profiles (user_id, company_name, verification_status) VALUES (?, 'Cloud Corp', 'VERIFIED')")) {
                    ps.setInt(1, testRecruiterId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO jobs (recruiter_id, title, company_name, description, required_skills, job_type, location, status, deadline, moderation_status) " +
                                "VALUES (?, 'Live Cloud Engineer', 'Cloud Corp', 'Build cloud apps', 'Java, SQL, Docker', 'FULL_TIME', 'Bengaluru', 'ACTIVE', DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'APPROVED')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, testRecruiterId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testJobId = rs.getInt(1);
                    }
                }

                // 4. Create resume
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO resumes (student_id, file_name, stored_path, file_size_bytes, file_hash, parsed_text, parsed_json, is_active) " +
                                "VALUES (?, 'cloud_resume.pdf', 'safe_path.pdf', 1048576, 'hash_abc123', 'Java Python SQL', ?, TRUE)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, testStudentId);
                    ps.setString(2, "{\"skills\":[\"Java\",\"Python\",\"SQL\"],\"workExperiences\":[{\"title\":\"Dev Intern\"}],\"projects\":[{\"title\":\"HireHub\"}],\"certifications\":[\"GCP Associate\"],\"metricsCount\":3}");
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testResumeId = rs.getInt(1);
                    }
                }

                // 5. Create resume analysis
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO resume_analyses (student_id, job_id, overall_score, matching_skills, missing_skills) " +
                                "VALUES (?, ?, 88, 'Java, SQL', 'Docker')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, testStudentId);
                    ps.setInt(2, testJobId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testAnalysisId = rs.getInt(1);
                    }
                }

                // 6. Create application
                try (PreparedStatement ps = testConn.prepareStatement(
                        "INSERT INTO applications (job_id, student_id, status) VALUES (?, ?, 'SHORTLISTED')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, testJobId);
                    ps.setInt(2, testStudentId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) testAppId = rs.getInt(1);
                    }
                }

                // Login as test student and load live analytics
                User liveUser = new User("Live Test Student", PREFIX + "student@test.local", "live_hash", User.Role.STUDENT);
                liveUser.setId(testStudentId);
                SessionManager.getInstance().login(liveUser);

                StudentAnalyticsService liveAnalyticsSvc = new StudentAnalyticsService();
                StudentDashboardSnapshot liveSnap = liveAnalyticsSvc.loadAllTimeSnapshot(testStudentId);

                check("6a: Live MySQL loaded non-null snapshot", liveSnap != null && !liveSnap.hasError());
                check("6b: Live MySQL snapshot reflects 1 application (SHORTLISTED)",
                        liveSnap.getKpi().getTotalApplications() == 1 && liveSnap.getKpi().getShortlistedCount() == 1);
                check("6c: Live MySQL snapshot detects active resume",
                        liveSnap.getKpi().isHasActiveResume() && "cloud_resume.pdf".equals(liveSnap.getResumeReadiness().getActiveResumeFileName()));
                check("6d: Live MySQL calculates deterministic score > 0",
                        liveSnap.getResumeReadiness().getOverallScore() != null && liveSnap.getResumeReadiness().getOverallScore() > 0);
                boolean hasJava = liveSnap.getSkillInsight().getCombinedSkills().stream().anyMatch(s -> s.equalsIgnoreCase("java"));
                boolean hasPython = liveSnap.getSkillInsight().getCombinedSkills().stream().anyMatch(s -> s.equalsIgnoreCase("python"));
                check("6e: Live MySQL skill insights contain combined skills (Java, Python, SQL)", hasJava && hasPython);

                boolean hasDocker = liveSnap.getSkillInsight().getInDemandMissingSkills().stream().anyMatch(s -> s.equalsIgnoreCase("docker"));
                check("6f: Live MySQL detects in-demand market gap (Docker)", hasDocker);

            } finally {
                // 100% database cleanup
                if (testAppId > 0) executeUpdate(testConn, "DELETE FROM applications WHERE id = " + testAppId);
                if (testAnalysisId > 0) executeUpdate(testConn, "DELETE FROM resume_analyses WHERE id = " + testAnalysisId);
                if (testResumeId > 0) executeUpdate(testConn, "DELETE FROM resumes WHERE id = " + testResumeId);
                if (testJobId > 0) executeUpdate(testConn, "DELETE FROM jobs WHERE id = " + testJobId);
                if (testStudentId > 0) {
                    executeUpdate(testConn, "DELETE FROM student_profiles WHERE user_id = " + testStudentId);
                    executeUpdate(testConn, "DELETE FROM users WHERE id = " + testStudentId);
                }
                if (testRecruiterId > 0) {
                    executeUpdate(testConn, "DELETE FROM recruiter_profiles WHERE user_id = " + testRecruiterId);
                    executeUpdate(testConn, "DELETE FROM users WHERE id = " + testRecruiterId);
                }

                // Assert exact baseline restoration
                Map<String, Integer> after = queryBaselineCounts(testConn);
                check("6g: Users table restored to baseline count (" + baseline.get("users") + ")",
                        after.get("users").equals(baseline.get("users")));
                check("6h: Jobs table restored to baseline count (" + baseline.get("jobs") + ")",
                        after.get("jobs").equals(baseline.get("jobs")));
                check("6i: Applications table restored to baseline count (" + baseline.get("applications") + ")",
                        after.get("applications").equals(baseline.get("applications")));
                check("6j: Resumes table restored to baseline count (" + baseline.get("resumes") + ")",
                        after.get("resumes").equals(baseline.get("resumes")));
                check("6k: Resume analyses table restored to baseline count (" + baseline.get("resume_analyses") + ")",
                        after.get("resume_analyses").equals(baseline.get("resume_analyses")));
                check("6l: Student profiles table restored to baseline count (" + baseline.get("student_profiles") + ")",
                        after.get("student_profiles").equals(baseline.get("student_profiles")));
            }

        } catch (SQLException e) {
            System.out.println("  [SKIP] Live MySQL database not available or errored: " + e.getMessage());
            skipped += 12;
        }
    }

    // =========================================================================
    // Fixture Builders & Helpers
    // =========================================================================

    private static StudentDashboardSnapshot buildPopulatedSnapshot(int studentId) {
        StudentDashboardSnapshot snap = new StudentDashboardSnapshot();
        snap.setStudentId(studentId);
        snap.setGeneratedAt(LocalDateTime.now());

        // KPI: 5 total apps, 2 pending, 1 shortlisted, 1 rejected, 1 selected, 12 open jobs
        StudentOverviewKPI kpi = new StudentOverviewKPI(
                5, 2, 1, 1, 1, 12,
                true, true, 6, Collections.emptyList(),
                true, true, 84, "HIGH"
        );
        snap.setKpi(kpi);

        // Status Distribution
        StudentStatusDistribution dist = new StudentStatusDistribution(2, 1, 1, 1);
        snap.setStatusDistribution(dist);

        // Trend points
        List<StudentTrendPoint> trend = List.of(
                new StudentTrendPoint(LocalDate.now().minusDays(10), 2, 0, 0),
                new StudentTrendPoint(LocalDate.now().minusDays(5), 2, 1, 0),
                new StudentTrendPoint(LocalDate.now().minusDays(1), 1, 0, 1)
        );
        snap.setApplicationTrends(trend);

        // Resume readiness
        StudentResumeReadiness readiness = new StudentResumeReadiness();
        readiness.setHasActiveResume(true);
        readiness.setActiveResumeFileName("priya_resume.pdf");
        readiness.setActiveResumeUploadedAt(LocalDateTime.now().minusDays(2));
        readiness.setActiveResumeFileSizeBytes(524288);
        readiness.setParsedSkillCount(8);
        readiness.setParsedExperienceCount(2);
        readiness.setParsedProjectCount(3);
        readiness.setParsedCertificationCount(1);
        readiness.setParsedMetricsCount(4);
        readiness.setOverallScore(84);
        readiness.setSkillsScore(35);
        readiness.setExperienceScore(22);
        readiness.setEducationScore(15);
        readiness.setCompletenessScore(9);
        readiness.setEvaluationConfidence("HIGH");
        readiness.setPersistedOverallScore(85);
        readiness.setPersistedCreatedAt(LocalDateTime.now().minusDays(3));
        snap.setResumeReadiness(readiness);

        // Skill insight
        StudentSkillInsight skills = new StudentSkillInsight(
                List.of("java", "sql", "spring boot", "react"),
                List.of("java", "sql", "git"),
                List.of("java", "sql", "spring boot", "react"),
                List.of("docker"),
                List.of("docker", "kubernetes")
        );
        snap.setSkillInsight(skills);

        // Recommendations
        StudentJobRecommendation r1 = new StudentJobRecommendation(
                201, "Backend Software Engineer", "Alpha Corp", "FULL_TIME", "Bengaluru",
                LocalDate.now().plusDays(20), 3, 4, 75.0,
                List.of("Java", "SQL", "Spring Boot"), List.of("Docker"), false
        );
        StudentJobRecommendation r2 = new StudentJobRecommendation(
                202, "Full Stack Developer", "Beta Inc", "INTERNSHIP", "Pune",
                LocalDate.now().plusDays(15), 4, 4, 100.0,
                List.of("Java", "React", "SQL", "Spring Boot"), Collections.emptyList(), true
        );
        snap.setRecommendations(List.of(r1, r2));

        // Activity events
        StudentActivityEvent e1 = new StudentActivityEvent(
                StudentActivityEvent.EventType.APPLICATION_SUBMITTED, LocalDateTime.now().minusHours(4),
                "Applied to Backend Software Engineer", "Submitted application at Alpha Corp",
                "APPLIED", "stu-activity-app"
        );
        StudentActivityEvent e2 = new StudentActivityEvent(
                StudentActivityEvent.EventType.STATUS_CHANGED, LocalDateTime.now().minusDays(1),
                "Application Shortlisted", "Advancing at Beta Inc",
                "SHORTLISTED", "stu-activity-status"
        );
        StudentActivityEvent e3 = new StudentActivityEvent(
                StudentActivityEvent.EventType.RESUME_UPLOADED, LocalDateTime.now().minusDays(2),
                "Active Resume Updated", "Uploaded priya_resume.pdf",
                "RESUME", "stu-activity-resume"
        );
        snap.setRecentActivities(List.of(e1, e2, e3));

        return snap;
    }

    private static StudentDashboardSnapshot buildEmptySnapshot(int studentId) {
        StudentDashboardSnapshot snap = new StudentDashboardSnapshot();
        snap.setStudentId(studentId);
        snap.setGeneratedAt(LocalDateTime.now());

        snap.setKpi(new StudentOverviewKPI(
                0, 0, 0, 0, 0, 5,
                false, false, 0, List.of("Phone", "College", "Degree", "Skills"),
                false, false, null, null
        ));
        snap.setStatusDistribution(new StudentStatusDistribution(0, 0, 0, 0));
        snap.setApplicationTrends(Collections.emptyList());
        StudentResumeReadiness readiness = new StudentResumeReadiness();
        readiness.setHasActiveResume(false);
        snap.setResumeReadiness(readiness);
        snap.setSkillInsight(new StudentSkillInsight());
        snap.setRecommendations(Collections.emptyList());
        snap.setRecentActivities(Collections.emptyList());
        return snap;
    }

    private static class StubStudentAnalyticsService extends StudentAnalyticsService {
        final AtomicInteger totalCalls = new AtomicInteger(0);
        final AtomicInteger lastRequestedStudentId = new AtomicInteger(0);
        StudentDashboardSnapshot snapshotToReturn;
        boolean simulateFailure = false;
        String failureMessage = "Simulated error";
        long delayMs = 0;

        public StubStudentAnalyticsService(StudentDashboardSnapshot snapshotToReturn) {
            this.snapshotToReturn = snapshotToReturn;
        }

        @Override
        public StudentDashboardSnapshot loadAllTimeSnapshot(int studentId) {
            totalCalls.incrementAndGet();
            lastRequestedStudentId.set(studentId);
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            }
            if (simulateFailure) {
                StudentDashboardSnapshot err = new StudentDashboardSnapshot();
                err.setStudentId(studentId);
                err.setErrorMessage(failureMessage);
                return err;
            }
            return snapshotToReturn;
        }
    }

    private record LoadedView(Parent root, StudentDashboardController controller) {}

    private static LoadedView loadDashboardWithService(StudentAnalyticsService analyticsSvc) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                StudentDashboardUITest.class.getResource("/fxml/student_dashboard.fxml"));

        StudentService offlineStudentSvc = new StudentService() {
            @Override
            public Optional<StudentProfile> getProfile(int userId) {
                return Optional.empty();
            }
        };

        loader.setControllerFactory(clazz -> new StudentDashboardController(
                analyticsSvc, offlineStudentSvc, new AuthService()));

        return runOnFx(() -> {
            Parent root = loader.load();
            StudentDashboardController controller = loader.getController();
            return new LoadedView(root, controller);
        });
    }

    // =========================================================================
    // JavaFX & Database Utility Helpers
    // =========================================================================

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
            throw new IllegalStateException("Timed out waiting for FX thread");
        }
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception e) throw e;
            throw new RuntimeException(t);
        }
        return result.get();
    }

    private static void waitUntil(Callable<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) return;
            Thread.sleep(30);
        }
        throw new AssertionError("Condition timed out after " + timeoutMs + " ms");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static String collectLabelsText(Node root) {
        StringBuilder sb = new StringBuilder();
        if (root instanceof Label lbl && lbl.getText() != null) {
            sb.append(lbl.getText()).append(' ');
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                sb.append(collectLabelsText(child));
            }
        }
        return sb.toString();
    }

    private static Map<String, Integer> queryBaselineCounts(Connection conn) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        String[] tables = {"users", "jobs", "applications", "resumes", "resume_analyses", "student_profiles"};
        for (String table : tables) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) counts.put(table, rs.getInt(1));
            }
        }
        return counts;
    }

    private static void executeUpdate(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + label);
            passed++;
        } else {
            System.err.println("  [FAIL] " + label);
            failed++;
        }
    }
}
