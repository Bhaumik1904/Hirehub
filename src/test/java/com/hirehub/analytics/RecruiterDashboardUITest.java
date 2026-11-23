package com.hirehub.analytics;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.controller.RecruiterDashboardController;
import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.dao.UserDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.RecruiterActivityEvent;
import com.hirehub.model.RecruiterApplicantReadiness;
import com.hirehub.model.RecruiterDashboardSnapshot;
import com.hirehub.model.RecruiterJobPerformance;
import com.hirehub.model.RecruiterJobTypeStat;
import com.hirehub.model.RecruiterOverviewKPI;
import com.hirehub.model.RecruiterPendingApplicant;
import com.hirehub.model.RecruiterPipelineKPI;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterStatusDistribution;
import com.hirehub.model.RecruiterTrendPoint;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.AuthService;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterAnalyticsService;
import com.hirehub.service.RecruiterService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Comprehensive verification suite for Phase 5 Step 3C:
 * Recruiter Intelligence Dashboard UI, async reliability, security isolation, and live DB binding.
 */
public class RecruiterDashboardUITest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== HireHub Phase 5 Step 3C Recruiter Dashboard UI Verification ===");
        initJavaFxToolkit();

        try {
            verifyRoleGuardsFailClosed();
            verifyFilterPropagation();
            verifyPopulatedRenderingAndPrivacy();
            verifyEmptyAndZeroDenominatorRendering();
            verifyErrorRecoveryAndCachePreservation();
            verifyOverlapPreventionAndStaleCancellation();
            verifyNavigationPanePreservation();
            verifyLiveDatabaseEndToEndIfAvailable();
        } finally {
            SessionManager.getInstance().logout();
            Platform.exit();
        }

        System.out.println();
        System.out.printf("SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                testsPassed, testsFailed, testsSkipped);
        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // Test 1: Role Guards & Fail-Closed Behavior
    // =========================================================================

    private static void verifyRoleGuardsFailClosed() throws Exception {
        System.out.println("\n--- [1] Role & Unauthenticated Access Rejection ---");

        StubAnalyticsService stub = new StubAnalyticsService(buildEmptySnapshot(999));

        SessionManager.getInstance().logout();
        LoadedView unauth = loadDashboardWithService(stub);
        assertTrue(!unauth.root.isVisible() && !unauth.root.isManaged() && stub.totalCalls.get() == 0,
                "1a: Unauthenticated session fails closed with zero analytics calls");

        User student = new User("Student Viewer", "stu@test.local", "hash", User.Role.STUDENT);
        student.setId(501);
        SessionManager.getInstance().login(student);
        LoadedView stuView = loadDashboardWithService(stub);
        assertTrue(!stuView.root.isVisible() && !stuView.root.isManaged() && stub.totalCalls.get() == 0,
                "1b: STUDENT session fails closed with zero analytics calls");

        User admin = new User("Admin Viewer", "adm@test.local", "hash", User.Role.ADMIN);
        admin.setId(502);
        SessionManager.getInstance().login(admin);
        LoadedView admView = loadDashboardWithService(stub);
        assertTrue(!admView.root.isVisible() && !admView.root.isManaged() && stub.totalCalls.get() == 0,
                "1c: ADMIN session fails closed with zero analytics calls");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 2 & 3: Authenticated Recruiter ID & Date Filter Propagation
    // =========================================================================

    private static void verifyFilterPropagation() throws Exception {
        System.out.println("\n--- [2] Recruiter ID & Date Filter Propagation ---");

        final int recruiterId = 777;
        User rec = new User("Anya Verma", "anya@novatech.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        rec.setCreatedAt(LocalDateTime.now().minusMonths(4));
        SessionManager.getInstance().login(rec);

        StubAnalyticsService stub = new StubAnalyticsService(buildPopulatedSnapshot(recruiterId), true);
        LoadedView view = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() >= 1 && !view.controller.isRefreshInFlight(), 4000);

        assertTrue(view.root.isVisible() && view.root.isManaged()
                        && stub.lastRecruiterId.get() == recruiterId
                        && stub.lastFrom.get() == null && stub.lastTo.get() == null
                        && countScrollPanes(view.root) == 1,
                "2a: Authenticated recruiter loads default All Time snapshot with single outer ScrollPane");

        // Switch to 30 Days
        int baselineCalls = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handlePreset30();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > baselineCalls && !view.controller.isRefreshInFlight(), 4000);
        LocalDate expectedFrom30 = LocalDate.now().minusDays(29);
        LocalDate expectedTo = LocalDate.now();
        String gran30 = runOnFx(() -> getField(view.controller, "trendGranularityBadge", Label.class).getText());
        assertTrue(stub.lastRecruiterId.get() == recruiterId
                        && expectedFrom30.equals(stub.lastFrom.get())
                        && expectedTo.equals(stub.lastTo.get())
                        && "Daily".equals(gran30),
                "2b: 30 Days preset propagates 30-day window and renders Daily granularity");

        // Switch to 60 Days
        int callsBefore60 = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handlePreset60();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > callsBefore60 && !view.controller.isRefreshInFlight(), 4000);
        LocalDate expectedFrom60 = LocalDate.now().minusDays(59);
        String gran60 = runOnFx(() -> getField(view.controller, "trendGranularityBadge", Label.class).getText());
        assertTrue(expectedFrom60.equals(stub.lastFrom.get()) && "Weekly".equals(gran60),
                "2c: 60 Days preset propagates 60-day window and renders Weekly granularity");

        // Switch to 200 Days
        int callsBefore200 = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handlePreset200();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > callsBefore200 && !view.controller.isRefreshInFlight(), 4000);
        LocalDate expectedFrom200 = LocalDate.now().minusDays(199);
        String gran200 = runOnFx(() -> getField(view.controller, "trendGranularityBadge", Label.class).getText());
        assertTrue(expectedFrom200.equals(stub.lastFrom.get()) && "Monthly".equals(gran200),
                "2d: 200 Days preset propagates 200-day window and renders Monthly granularity");

        // Switch back to All Time
        int callsBeforeAll = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handlePresetAllTime();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > callsBeforeAll && !view.controller.isRefreshInFlight(), 4000);
        assertTrue(stub.lastFrom.get() == null && stub.lastTo.get() == null,
                "2e: All Time preset resets range bounds to null (unbounded)");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 3: Populated DTO Binding Across Sections A-I + Privacy Boundaries
    // =========================================================================

    private static void verifyPopulatedRenderingAndPrivacy() throws Exception {
        System.out.println("\n--- [3] Populated DTO Binding & Candidate Privacy Check ---");

        final int recruiterId = 801;
        User rec = new User("Vikram Rao", "vikram@acme.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        rec.setCreatedAt(LocalDateTime.now().minusMonths(8));
        SessionManager.getInstance().login(rec);

        RecruiterDashboardSnapshot snapshot = buildPopulatedSnapshot(recruiterId);
        StubAnalyticsService stub = new StubAnalyticsService(snapshot);
        LoadedView view = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() >= 1 && !view.controller.isRefreshInFlight(), 4000);

        boolean kpisMatch = runOnFx(() -> {
            String totalJobs = getField(view.controller, "kpiTotalJobsValue", Label.class).getText();
            String openJobs = getField(view.controller, "kpiOpenAcceptingValue", Label.class).getText();
            String zeroOpen = getField(view.controller, "kpiZeroApplicantValue", Label.class).getText();
            String periodApps = getField(view.controller, "kpiPeriodAppsValue", Label.class).getText();
            String lifetimePend = getField(view.controller, "kpiLifetimePendingValue", Label.class).getText();
            String shortlisted = getField(view.controller, "kpiShortlistedValue", Label.class).getText();
            String selected = getField(view.controller, "kpiSelectedValue", Label.class).getText();
            Label expWarn = getField(view.controller, "kpiExpiredWarningLabel", Label.class);
            String rateRev = getField(view.controller, "rateReviewLabel", Label.class).getText();
            String rateSl = getField(view.controller, "rateShortlistLabel", Label.class).getText();
            String rateUnique = getField(view.controller, "rateUniqueHireLabel", Label.class).getText();

            return "4".equals(totalJobs)
                    && "2".equals(openJobs)
                    && "1".equals(zeroOpen)
                    && "8".equals(periodApps)
                    && "3".equals(lifetimePend)
                    && "2".equals(shortlisted)
                    && "1".equals(selected)
                    && expWarn.isVisible() && expWarn.getText().contains("1 expired ACTIVE")
                    && "62.5%".equals(rateRev)
                    && "25.0%".equals(rateSl)
                    && "16.7%".equals(rateUnique);
        });
        assertTrue(kpisMatch, "3a: Overview KPIs, expired ACTIVE pill, and conversion rates match DTOs");

        boolean chartsAndTablesMatch = runOnFx(() -> {
            PieChart pie = getField(view.controller, "statusPieChart", PieChart.class);
            LineChart<?, ?> line = getField(view.controller, "applicationTrendChart", LineChart.class);
            BarChart<?, ?> bar = getField(view.controller, "jobTypeBarChart", BarChart.class);
            VBox jobTypesBox = getField(view.controller, "jobTypeSummaryListBox", VBox.class);
            @SuppressWarnings("unchecked")
            TableView<RecruiterJobPerformance> perfTable =
                    (TableView<RecruiterJobPerformance>) getField(view.controller, "jobPerformanceTable", TableView.class);
            VBox pendingBox = getField(view.controller, "pendingApplicantsBox", VBox.class);
            VBox activityBox = getField(view.controller, "recentActivityBox", VBox.class);
            Label aiScore = getField(view.controller, "readinessAvgAiScoreLabel", Label.class);
            Label advisory = getField(view.controller, "readinessAdvisoryLabel", Label.class);

            return pie.isVisible() && pie.getData().size() == 4
                    && line.isVisible() && line.getData().size() == 3
                    && line.getData().get(0).getData().size() == 3
                    && "09 Nov".equals(line.getData().get(0).getData().get(1).getXValue())
                    && bar.isVisible() && jobTypesBox.getChildren().size() == 3
                    && perfTable.isVisible() && perfTable.getItems().size() == 2
                    && perfTable.getPrefHeight() > 100
                    && pendingBox.getChildren().size() == 2
                    && activityBox.getChildren().size() == 3
                    && "84.5 / 100".equals(aiScore.getText())
                    && advisory.getText().contains("Advisory only");
        });
        assertTrue(chartsAndTablesMatch,
                "3b: Charts preserve chronological order/zero buckets, all 3 job types render, job perf/activity/pending/readiness bind accurately");

        // Privacy check on Pending Applicants & Recent Activity text nodes
        boolean privacyOk = runOnFx(() -> {
            VBox pendingBox = getField(view.controller, "pendingApplicantsBox", VBox.class);
            VBox activityBox = getField(view.controller, "recentActivityBox", VBox.class);
            String combined = (collectLabelsText(pendingBox) + " " + collectLabelsText(activityBox)).toLowerCase();
            return !combined.contains("@")
                    && !combined.contains(".pdf")
                    && !combined.contains("resume_path")
                    && !combined.contains("cover")
                    && !combined.contains("password")
                    && !combined.contains("+91");
        });
        assertTrue(privacyOk,
                "3c: Pending Applicants & Recent Activity omit candidate email, phone, cover letter, password, and resume path");

        maybeCaptureScreenshot(view.root, "recruiter_populated.png");
        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 4: Empty Dashboard & Zero-Denominator & Unavailable AI State
    // =========================================================================

    private static void verifyEmptyAndZeroDenominatorRendering() throws Exception {
        System.out.println("\n--- [4] Empty Portfolio, Zero-Denominator & Unavailable AI Rendering ---");

        final int recruiterId = 802;
        User rec = new User("New Recruiter", "newrec@acme.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        SessionManager.getInstance().login(rec);

        StubAnalyticsService stub = new StubAnalyticsService(buildEmptySnapshot(recruiterId));
        LoadedView view = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() >= 1 && !view.controller.isRefreshInFlight(), 4000);

        boolean emptyValid = runOnFx(() -> {
            HBox emptyBanner = getField(view.controller, "emptyDashboardBox", HBox.class);
            PieChart pie = getField(view.controller, "statusPieChart", PieChart.class);
            VBox pieEmpty = getField(view.controller, "pipelineEmptyBox", VBox.class);
            LineChart<?, ?> line = getField(view.controller, "applicationTrendChart", LineChart.class);
            VBox trendEmpty = getField(view.controller, "trendEmptyBox", VBox.class);
            BarChart<?, ?> bar = getField(view.controller, "jobTypeBarChart", BarChart.class);
            VBox barEmpty = getField(view.controller, "jobTypeEmptyBox", VBox.class);
            VBox jobTypesList = getField(view.controller, "jobTypeSummaryListBox", VBox.class);
            VBox perfEmpty = getField(view.controller, "jobPerformanceEmptyBox", VBox.class);
            VBox pendEmpty = getField(view.controller, "pendingEmptyBox", VBox.class);
            VBox actEmpty = getField(view.controller, "activityEmptyBox", VBox.class);
            VBox readinessUnavail = getField(view.controller, "readinessUnavailableBox", VBox.class);
            Label aiScore = getField(view.controller, "readinessAvgAiScoreLabel", Label.class);
            Label reviewRate = getField(view.controller, "rateReviewLabel", Label.class);
            Label shortlistRate = getField(view.controller, "rateShortlistLabel", Label.class);
            Label uniqueHireRate = getField(view.controller, "rateUniqueHireLabel", Label.class);

            return emptyBanner.isVisible() && emptyBanner.isManaged()
                    && !pie.isVisible() && pieEmpty.isVisible()
                    && !line.isVisible() && trendEmpty.isVisible()
                    && !bar.isVisible() && barEmpty.isVisible()
                    && jobTypesList.getChildren().size() == 3
                    && perfEmpty.isVisible()
                    && pendEmpty.isVisible()
                    && actEmpty.isVisible()
                    && readinessUnavail.isVisible()
                    && "Not available".equals(aiScore.getText())
                    && "N/A".equals(reviewRate.getText())
                    && "N/A".equals(shortlistRate.getText())
                    && "N/A".equals(uniqueHireRate.getText());
        });
        assertTrue(emptyValid,
                "4: Empty portfolio renders empty placeholders, hides charts, shows 'N/A' for zero-denominator rates, and 'Not available' for missing AI score");

        maybeCaptureScreenshot(view.root, "recruiter_empty.png");
        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 5: Failed Refresh Error Banner, Cache Preservation & Retry Recovery
    // =========================================================================

    private static void verifyErrorRecoveryAndCachePreservation() throws Exception {
        System.out.println("\n--- [5] Failed Refresh Handling & Cache Preservation ---");

        final int recruiterId = 803;
        User rec = new User("Meera Iyer", "meera@acme.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        SessionManager.getInstance().login(rec);

        RecruiterDashboardSnapshot valid = buildPopulatedSnapshot(recruiterId);
        StubAnalyticsService stub = new StubAnalyticsService(valid);
        LoadedView view = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() >= 1 && !view.controller.isRefreshInFlight(), 4000);

        // Next call fails with SQL-like exception -> error banner must appear & valid snapshot must stay visible
        stub.throwNextError.set(new RuntimeException("Simulated DB connection lost"));
        int callsBeforeFail = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handleRefresh();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > callsBeforeFail && !view.controller.isRefreshInFlight(), 4000);

        boolean errorWithCachePreserved = runOnFx(() -> {
            HBox errBanner = getField(view.controller, "errorBannerBox", HBox.class);
            Label errDetail = getField(view.controller, "errorDetailLabel", Label.class);
            Button retryBtn = getField(view.controller, "retryBtn", Button.class);
            String cachedApps = getField(view.controller, "kpiPeriodAppsValue", Label.class).getText();
            return errBanner.isVisible() && errBanner.isManaged()
                    && retryBtn.isVisible()
                    && errDetail.getText().contains("Simulated DB connection lost")
                    && errDetail.getText().contains("Showing previously loaded data.")
                    && "8".equals(cachedApps);
        });
        assertTrue(errorWithCachePreserved,
                "5a: Subsequent refresh failure displays error banner + Retry while preserving cached valid metrics");

        maybeCaptureScreenshot(view.root, "recruiter_error.png");

        // Retry succeeds -> banner clears cleanly
        stub.throwNextError.set(null);
        int callsBeforeRetry = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handleRetry();
            return null;
        });
        waitUntil(() -> stub.totalCalls.get() > callsBeforeRetry && !view.controller.isRefreshInFlight(), 4000);
        boolean recovered = runOnFx(() -> !getField(view.controller, "errorBannerBox", HBox.class).isVisible());
        assertTrue(recovered, "5b: Clicking Retry clears error banner on recovery");

        // Initial load returning snapshot.hasError() keeps KPI placeholder "—"
        RecruiterDashboardSnapshot initErrSnap = new RecruiterDashboardSnapshot();
        initErrSnap.setRecruiterId(recruiterId);
        initErrSnap.setLoadError("Database unreachable on startup");
        StubAnalyticsService errInitStub = new StubAnalyticsService(initErrSnap);
        LoadedView errInitView = loadDashboardWithService(errInitStub);
        waitUntil(() -> errInitStub.totalCalls.get() >= 1 && !errInitView.controller.isRefreshInFlight(), 4000);
        boolean initErrHandled = runOnFx(() -> {
            HBox errBanner = getField(errInitView.controller, "errorBannerBox", HBox.class);
            String kpiPlaceholder = getField(errInitView.controller, "kpiTotalJobsValue", Label.class).getText();
            String lastRef = getField(errInitView.controller, "lastRefreshedLabel", Label.class).getText();
            return errBanner.isVisible()
                    && "—".equals(kpiPlaceholder)
                    && lastRef.contains("Load failed");
        });
        assertTrue(initErrHandled, "5c: Initial load error shows banner cleanly without overwriting KPI placeholders with 0");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 6: Overlap Prevention & Stale / Cancelled Response Safety
    // =========================================================================

    private static void verifyOverlapPreventionAndStaleCancellation() throws Exception {
        System.out.println("\n--- [6] Overlap Prevention & Stale/Cancelled Response Safety ---");

        final int recruiterId = 804;
        User rec = new User("Karan Mehta", "karan@acme.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        SessionManager.getInstance().login(rec);

        StubAnalyticsService stub = new StubAnalyticsService(buildPopulatedSnapshot(recruiterId), true);
        LoadedView view = loadDashboardWithService(stub);
        waitUntil(() -> stub.totalCalls.get() >= 1 && !view.controller.isRefreshInFlight(), 4000);

        // Part A: Rapid duplicate handleRefresh calls while request #1 is blocked
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        stub.enteredLatch.set(entered);
        stub.blockLatch.set(release);

        int callsBefore = stub.totalCalls.get();
        runOnFx(() -> {
            view.controller.handleRefresh();
            return null;
        });
        entered.await(3, TimeUnit.SECONDS);

        boolean disabledWhileRunning = runOnFx(() -> {
            Button refreshBtn = getField(view.controller, "refreshBtn", Button.class);
            view.controller.handleRefresh(); // overlapping call must be dropped!
            view.controller.handleRefresh(); // second overlapping call must be dropped!
            return refreshBtn.isDisabled() && view.controller.isRefreshInFlight();
        });
        Thread.sleep(80);
        int callsWhileBlocked = stub.totalCalls.get() - callsBefore;
        release.countDown();
        waitUntil(() -> !view.controller.isRefreshInFlight(), 4000);
        boolean reEnabledAfterFinish = runOnFx(() -> !getField(view.controller, "refreshBtn", Button.class).isDisabled());

        assertTrue(disabledWhileRunning && callsWhileBlocked == 1 && reEnabledAfterFinish,
                "6a: Overlapping duplicate refresh requests are blocked while active task runs");

        // Part B: Stale slower request finishing after newer preset must NOT overwrite newer state
        stub.enteredLatch.set(null);
        stub.blockLatch.set(null);
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch slowRelease = new CountDownLatch(1);
        RecruiterDashboardSnapshot slowStaleSnap = buildPopulatedSnapshot(recruiterId);
        slowStaleSnap.setTrendGranularity(RecruiterTrendPoint.TrendGranularity.DAILY);
        slowStaleSnap.getOverviewKPI().setPeriodTotalApplications(999); // distinctive stale marker

        RecruiterDashboardSnapshot fastNewSnap = buildPopulatedSnapshot(recruiterId);
        fastNewSnap.setTrendGranularity(RecruiterTrendPoint.TrendGranularity.WEEKLY);
        fastNewSnap.getOverviewKPI().setPeriodTotalApplications(42);

        stub.customLoader = (id, from, to) -> {
            if (from != null && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 30) {
                slowStarted.countDown();
                try {
                    slowRelease.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                return slowStaleSnap;
            }
            return fastNewSnap;
        };

        runOnFx(() -> {
            view.controller.handlePreset30();
            return null;
        });
        slowStarted.await(3, TimeUnit.SECONDS);

        // Now switch to 60 Days while 30 Days is blocked!
        runOnFx(() -> {
            view.controller.handlePreset60();
            return null;
        });
        waitUntil(() -> !view.controller.isRefreshInFlight(), 4000);

        String after60Apps = runOnFx(() -> getField(view.controller, "kpiPeriodAppsValue", Label.class).getText());
        // Release stale 30-day task AFTER 60-day already rendered
        slowRelease.countDown();
        Thread.sleep(150);

        boolean newerPreserved = runOnFx(() -> {
            String appsNow = getField(view.controller, "kpiPeriodAppsValue", Label.class).getText();
            String granNow = getField(view.controller, "trendGranularityBadge", Label.class).getText();
            return "42".equals(after60Apps) && "42".equals(appsNow) && "Weekly".equals(granNow);
        });
        assertTrue(newerPreserved,
                "6b: Cancelled/stale slow response finishing late cannot overwrite newer 60-day snapshot");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 7: Preserved Navigation Panes & Jobs Table Contract
    // =========================================================================

    private static void verifyNavigationPanePreservation() throws Exception {
        System.out.println("\n--- [7] Preserved Profile & Manage Jobs Panes ---");

        final int recruiterId = 805;
        User rec = new User("Sneha Roy", "sneha@acme.io", "hash", User.Role.RECRUITER);
        rec.setId(recruiterId);
        SessionManager.getInstance().login(rec);

        // Use stub analytics and keep empty DB jobs/profile safe even offline
        LoadedView view = loadDashboardWithService(new StubAnalyticsService(buildEmptySnapshot(recruiterId)));
        Thread.sleep(120);

        boolean panesOk = runOnFx(() -> {
            VBox dash = getField(view.controller, "dashboardPane", VBox.class);
            VBox prof = getField(view.controller, "profilePane", VBox.class);
            VBox jobs = getField(view.controller, "jobsPane", VBox.class);
            @SuppressWarnings("unchecked")
            TableView<Job> jobTable = (TableView<Job>) getField(view.controller, "jobTable", TableView.class);

            view.controller.showProfile();
            boolean profileShown = !dash.isVisible() && prof.isVisible() && !jobs.isVisible();

            view.controller.showJobs();
            boolean jobsShown = !dash.isVisible() && !prof.isVisible() && jobs.isVisible()
                    && jobTable.getColumns().size() == 7;

            view.controller.showDashboard();
            boolean dashRestored = dash.isVisible() && !prof.isVisible() && !jobs.isVisible();

            return profileShown && jobsShown && dashRestored;
        });
        assertTrue(panesOk,
                "7: Sidebar navigation switches between Dashboard, Company Profile, and Manage Jobs (7 preserved columns)");

        SessionManager.getInstance().logout();
    }

    // =========================================================================
    // Test 8: Live MySQL End-to-End Integration Check (If DB Reachable)
    // =========================================================================

    private static void verifyLiveDatabaseEndToEndIfAvailable() throws Exception {
        System.out.println("\n--- [8] Live MySQL Recruiter Intelligence Dashboard Check ---");

        try (Connection c = DatabaseConnection.getConnection()) {
            if (c == null || c.isClosed()) {
                System.out.println("  [SKIP] Live DB unavailable");
                testsSkipped++;
                return;
            }
        } catch (SQLException e) {
            System.out.println("  [SKIP] Live DB unreachable in current mode: " + e.getMessage());
            testsSkipped++;
            return;
        }

        int baseUsers = countRows("users");
        int baseJobs = countRows("jobs");
        int baseApps = countRows("applications");

        int recId = -1;
        int stuId = -1;
        try {
            UserDAO userDAO = new UserDAO();
            RecruiterDAO recruiterDAO = new RecruiterDAO();
            JobDAO jobDAO = new JobDAO();
            ApplicationDAO appDAO = new ApplicationDAO();

            String suffix = String.valueOf(System.nanoTime());
            User rec = new User("Live Step3C Recruiter", "rec3c_" + suffix + "@test.hirehub",
                    PasswordUtil.hashPassword("Pass@123"), User.Role.RECRUITER);
            recId = userDAO.insertUser(rec);
            rec.setId(recId);

            User stu = new User("Live Step3C Student", "stu3c_" + suffix + "@test.hirehub",
                    PasswordUtil.hashPassword("Pass@123"), User.Role.STUDENT);
            stuId = userDAO.insertUser(stu);
            stu.setId(stuId);

            RecruiterProfile profile = new RecruiterProfile(recId, "Orbital Labs",
                    "https://orbital.example", "Aerospace software", "Software");
            recruiterDAO.saveOrUpdate(profile);

            Job activeJob = new Job(recId, "Live Java Role", "Orbital Labs", "Desc", "Java",
                    "Bengaluru", Job.JobType.FULL_TIME, "12 LPA", "BTech", LocalDate.now().plusDays(15), Job.Status.ACTIVE);
            jobDAO.insert(activeJob);

            Job expiredJob = new Job(recId, "Expired Intern Role", "Orbital Labs", "Desc", "Python",
                    "Remote", Job.JobType.INTERNSHIP, "25k", "Any", LocalDate.now().minusDays(4), Job.Status.ACTIVE);
            jobDAO.insert(expiredJob);

            appDAO.insert(activeJob.getId(), stuId, "");
            int a2Id = appDAO.insert(expiredJob.getId(), stuId, "");
            appDAO.updateStatusForRecruiter(a2Id, recId, Application.Status.SHORTLISTED);

            // Load production FXMLLoader (default 0-arg constructor -> real RecruiterAnalyticsService -> MySQL!)
            SessionManager.getInstance().login(rec);
            FXMLLoader loader = new FXMLLoader(RecruiterDashboardUITest.class
                    .getResource("/fxml/recruiter_dashboard.fxml"));
            Parent[] rootHolder = new Parent[1];
            RecruiterDashboardController[] ctrlHolder = new RecruiterDashboardController[1];
            runOnFx(() -> {
                rootHolder[0] = loader.load();
                ctrlHolder[0] = loader.getController();
                return null;
            });

            waitUntil(() -> ctrlHolder[0].getLastValidSnapshot() != null
                    && !ctrlHolder[0].isRefreshInFlight(), 6000);

            boolean liveMatches = runOnFx(() -> {
                RecruiterDashboardSnapshot s = ctrlHolder[0].getLastValidSnapshot();
                if (s == null || s.hasError()) return false;
                String company = getField(ctrlHolder[0], "companyNameHeaderLabel", Label.class).getText();
                String totalJobs = getField(ctrlHolder[0], "kpiTotalJobsValue", Label.class).getText();
                String openJobs = getField(ctrlHolder[0], "kpiOpenAcceptingValue", Label.class).getText();
                String periodApps = getField(ctrlHolder[0], "kpiPeriodAppsValue", Label.class).getText();
                String shortlisted = getField(ctrlHolder[0], "kpiShortlistedValue", Label.class).getText();
                Label expPill = getField(ctrlHolder[0], "kpiExpiredWarningLabel", Label.class);
                String aiScore = getField(ctrlHolder[0], "readinessAvgAiScoreLabel", Label.class).getText();
                VBox pendBox = getField(ctrlHolder[0], "pendingApplicantsBox", VBox.class);
                return company.contains("Orbital Labs")
                        && "2".equals(totalJobs)
                        && "1".equals(openJobs)
                        && "2".equals(periodApps)
                        && "1".equals(shortlisted)
                        && expPill.isVisible() && expPill.getText().contains("1 expired ACTIVE")
                        && "Not available".equals(aiScore)
                        && pendBox.getChildren().size() == 1;
            });
            assertTrue(liveMatches,
                    "8a: Live MySQL recruiter dashboard renders accurate counts, expired active badge, pending queue, and 'Not available' AI score");

            // Also verify switching to 30 Days works against real MySQL
            runOnFx(() -> {
                ctrlHolder[0].handlePreset30();
                return null;
            });
            waitUntil(() -> !ctrlHolder[0].isRefreshInFlight()
                    && ctrlHolder[0].getActivePreset() == RecruiterDashboardController.FilterPreset.DAYS_30
                    && ctrlHolder[0].getLastValidSnapshot() != null
                    && ctrlHolder[0].getLastValidSnapshot().getTrendGranularity() == RecruiterTrendPoint.TrendGranularity.DAILY,
                    5000);
            assertTrue("Daily".equals(runOnFx(() ->
                            getField(ctrlHolder[0], "trendGranularityBadge", Label.class).getText())),
                    "8b: Live MySQL 30 Days filter reloads with Daily granularity");

        } finally {
            SessionManager.getInstance().logout();
            cleanupFixtures(stuId, recId);
            int afterUsers = countRows("users");
            int afterJobs = countRows("jobs");
            int afterApps = countRows("applications");
            assertTrue(afterUsers == baseUsers && afterJobs == baseJobs && afterApps == baseApps,
                    String.format("8c: Live DB cleanup restored exact row counts (users=%d, jobs=%d, apps=%d)",
                            afterUsers, afterJobs, afterApps));
        }
    }

    // =========================================================================
    // Test Fixture Builders & Stubs
    // =========================================================================

    private static RecruiterDashboardSnapshot buildPopulatedSnapshot(int recruiterId) {
        RecruiterDashboardSnapshot snap = new RecruiterDashboardSnapshot();
        snap.setRecruiterId(recruiterId);
        snap.setTrendGranularity(RecruiterTrendPoint.TrendGranularity.WEEKLY);
        snap.setDateRangeLabel("All Time");
        snap.setRefreshedAt(LocalDateTime.now());

        RecruiterOverviewKPI ov = new RecruiterOverviewKPI();
        ov.setRecruiterAccountPresent(true);
        ov.setRecruiterName("Vikram Rao");
        ov.setCompanyName("NovaTech Systems");
        ov.setIndustry("Cloud Software");
        ov.setProfileCompleted(true);
        ov.setProfileCompletenessRate(100.0);
        ov.setTotalJobs(4);
        ov.setActiveStatusJobs(3);
        ov.setOpenAcceptingJobs(2);
        ov.setExpiredActiveJobs(1);
        ov.setClosingWithin7DaysJobs(1);
        ov.setDraftJobs(0);
        ov.setClosedJobs(1);
        ov.setZeroApplicantOpenJobs(1);
        ov.setLifetimeTotalApplications(12);
        ov.setLifetimePendingApplications(3);
        ov.setJobsCreatedInPeriod(3);
        ov.setPeriodTotalApplications(8);
        ov.setPeriodPendingApplications(3);
        ov.setPeriodShortlistedApplications(2);
        ov.setPeriodSelectedApplications(1);
        ov.setPeriodRejectedApplications(2);
        snap.setOverviewKPI(ov);

        RecruiterPipelineKPI pipe = new RecruiterPipelineKPI();
        pipe.setTotalApplications(8);
        pipe.setPendingApplications(3);
        pipe.setShortlistedApplications(2);
        pipe.setSelectedApplications(1);
        pipe.setRejectedApplications(2);
        pipe.setReviewedApplications(5);
        pipe.setUniqueApplicants(6);
        pipe.setUniqueSelectedApplicants(1);
        pipe.setJobsWithApplications(3);
        pipe.setReviewCompletionRate(62.5);
        pipe.setShortlistRate(25.0);
        pipe.setSelectionRate(12.5);
        pipe.setRejectionRate(25.0);
        pipe.setAdvancementRate(37.5);
        pipe.setUniqueHireRate(16.7);
        snap.setPipelineKPI(pipe);

        List<RecruiterStatusDistribution> dists = new ArrayList<>();
        RecruiterStatusDistribution s1 = new RecruiterStatusDistribution("PENDING", 3, 3);
        s1.setPercentage(37.5);
        RecruiterStatusDistribution s2 = new RecruiterStatusDistribution("SHORTLISTED", 2, 2);
        s2.setPercentage(25.0);
        RecruiterStatusDistribution s3 = new RecruiterStatusDistribution("SELECTED", 1, 1);
        s3.setPercentage(12.5);
        RecruiterStatusDistribution s4 = new RecruiterStatusDistribution("REJECTED", 2, 2);
        s4.setPercentage(25.0);
        dists.addAll(Arrays.asList(s1, s2, s3, s4));
        snap.setStatusDistribution(dists);

        RecruiterJobPerformance p1 = new RecruiterJobPerformance();
        p1.setJobId(11);
        p1.setTitle("Senior Backend Engineer");
        p1.setCompanyName("NovaTech Systems");
        p1.setJobType("FULL_TIME");
        p1.setLocation("Bengaluru");
        p1.setDeadline(LocalDate.now().plusDays(12));
        p1.setStatus("ACTIVE");
        p1.setExpiredActive(false);
        p1.setPeriodApplications(5);
        p1.setPendingCount(2);
        p1.setShortlistedCount(2);
        p1.setSelectedCount(1);
        p1.setRejectedCount(0);
        p1.setLifetimeApplications(7);
        p1.setShortlistRate(40.0);
        p1.setSelectionRate(20.0);

        RecruiterJobPerformance p2 = new RecruiterJobPerformance();
        p2.setJobId(12);
        p2.setTitle("Summer QA Intern");
        p2.setCompanyName("NovaTech Systems");
        p2.setJobType("INTERNSHIP");
        p2.setLocation("Remote");
        p2.setDeadline(LocalDate.now().minusDays(3));
        p2.setStatus("ACTIVE");
        p2.setExpiredActive(true);
        p2.setPeriodApplications(3);
        p2.setPendingCount(1);
        p2.setShortlistedCount(0);
        p2.setSelectedCount(0);
        p2.setRejectedCount(2);
        p2.setLifetimeApplications(5);
        p2.setShortlistRate(0.0);
        p2.setSelectionRate(0.0);
        snap.setJobPerformance(Arrays.asList(p1, p2));

        snap.setJobTypeStats(Arrays.asList(
                new RecruiterJobTypeStat("INTERNSHIP", 2, 1, 1, 0, 1, 0, 3, 1, 0, 2),
                new RecruiterJobTypeStat("FULL_TIME", 2, 2, 1, 1, 0, 0, 5, 1, 1, 0),
                new RecruiterJobTypeStat("PART_TIME", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        ));

        snap.setApplicationTrend(Arrays.asList(
                new RecruiterTrendPoint(RecruiterTrendPoint.TrendGranularity.WEEKLY,
                        LocalDate.now().minusDays(14), LocalDate.now().minusDays(8), "02 Nov", 3, 1, 0, 1),
                new RecruiterTrendPoint(RecruiterTrendPoint.TrendGranularity.WEEKLY,
                        LocalDate.now().minusDays(7), LocalDate.now().minusDays(1), "09 Nov", 0, 0, 0, 0),
                new RecruiterTrendPoint(RecruiterTrendPoint.TrendGranularity.WEEKLY,
                        LocalDate.now(), LocalDate.now(), "12 Nov", 5, 1, 1, 1)
        ));

        snap.setRecentActivity(Arrays.asList(
                new RecruiterActivityEvent("JOB_POSTED", 11, "NovaTech Systems", "Senior Backend Engineer",
                        "Posted listing Senior Backend Engineer", LocalDateTime.now().minusDays(5)),
                new RecruiterActivityEvent("APPLICATION_RECEIVED", 11, "Aarav Sharma", "Senior Backend Engineer",
                        "Aarav Sharma applied to Senior Backend Engineer", LocalDateTime.now().minusDays(2)),
                new RecruiterActivityEvent("APPLICATION_REVIEWED", 12, "Priya Nair", "Summer QA Intern",
                        "Reviewed Priya Nair (Shortlisted)", LocalDateTime.now().minusHours(5))
        ));

        snap.setPendingApplicants(Arrays.asList(
                new RecruiterPendingApplicant(901, 11, "Senior Backend Engineer", "FULL_TIME",
                        "Aarav Sharma", "NIT Trichy", "B.Tech CSE", LocalDateTime.now().minusDays(9), 9),
                new RecruiterPendingApplicant(902, 12, "Summer QA Intern", "INTERNSHIP",
                        "Priya Nair", "BITS Pilani", "B.E. CS", LocalDateTime.now().minusHours(3), 0)
        ));

        RecruiterApplicantReadiness r = new RecruiterApplicantReadiness();
        r.setReadinessDataAvailable(true);
        r.setAiAnalysisAvailable(true);
        r.setTotalApplicationsInPeriod(8);
        r.setUniqueApplicants(6);
        r.setApplicantsWithCompleteProfile(5);
        r.setApplicantsWithActiveResume(5);
        r.setApplicantsWithAnyResume(6);
        r.setApplicationsWithJobAiAnalysis(4);
        r.setUniqueApplicantsWithJobAiScore(4);
        r.setAvgJobAiScore(84.5);
        r.setProfileCompletionRate(83.3);
        r.setActiveResumeCoverageRate(83.3);
        r.setAnyResumeCoverageRate(100.0);
        r.setJobAiCoverageRate(66.7);
        r.setAdvisoryDisclaimer(RecruiterApplicantReadiness.DEFAULT_ADVISORY_DISCLAIMER);
        snap.setApplicantReadiness(r);

        return snap;
    }

    private static RecruiterDashboardSnapshot buildEmptySnapshot(int recruiterId) {
        RecruiterDashboardSnapshot snap = new RecruiterDashboardSnapshot();
        snap.setRecruiterId(recruiterId);
        snap.setTrendGranularity(RecruiterTrendPoint.TrendGranularity.MONTHLY);
        snap.setDateRangeLabel("All Time");
        snap.setRefreshedAt(LocalDateTime.now());

        RecruiterOverviewKPI ov = new RecruiterOverviewKPI();
        ov.setRecruiterAccountPresent(true);
        ov.setRecruiterName("Empty Recruiter");
        snap.setOverviewKPI(ov);
        snap.setPipelineKPI(new RecruiterPipelineKPI());
        snap.setStatusDistribution(Arrays.asList(
                new RecruiterStatusDistribution("PENDING", 0, 0),
                new RecruiterStatusDistribution("SHORTLISTED", 0, 0),
                new RecruiterStatusDistribution("SELECTED", 0, 0),
                new RecruiterStatusDistribution("REJECTED", 0, 0)
        ));
        snap.setJobTypeStats(Arrays.asList(
                new RecruiterJobTypeStat("INTERNSHIP", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new RecruiterJobTypeStat("FULL_TIME", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new RecruiterJobTypeStat("PART_TIME", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        ));
        RecruiterApplicantReadiness r = new RecruiterApplicantReadiness();
        r.setReadinessDataAvailable(false);
        r.setAiAnalysisAvailable(false);
        r.setUnavailabilityReason("No applications received yet in the selected period.");
        r.setAvgJobAiScore(null);
        snap.setApplicantReadiness(r);
        return snap;
    }

    @FunctionalInterface
    interface SnapshotLoaderFn {
        RecruiterDashboardSnapshot apply(int recruiterId, LocalDate from, LocalDate to);
    }

    private static final class StubAnalyticsService extends RecruiterAnalyticsService {
        private final RecruiterDashboardSnapshot defaultSnap;
        private final boolean deriveGranularityFromRange;
        final AtomicInteger totalCalls = new AtomicInteger(0);
        final AtomicInteger lastRecruiterId = new AtomicInteger(-1);
        final AtomicReference<LocalDate> lastFrom = new AtomicReference<>();
        final AtomicReference<LocalDate> lastTo = new AtomicReference<>();
        final AtomicReference<RuntimeException> throwNextError = new AtomicReference<>();
        final AtomicReference<CountDownLatch> enteredLatch = new AtomicReference<>();
        final AtomicReference<CountDownLatch> blockLatch = new AtomicReference<>();
        volatile SnapshotLoaderFn customLoader;

        StubAnalyticsService(RecruiterDashboardSnapshot defaultSnap) {
            this(defaultSnap, false);
        }

        StubAnalyticsService(RecruiterDashboardSnapshot defaultSnap, boolean deriveGranularityFromRange) {
            this.defaultSnap = defaultSnap;
            this.deriveGranularityFromRange = deriveGranularityFromRange;
        }

        @Override
        public RecruiterDashboardSnapshot loadSnapshot(int recruiterId, LocalDate from, LocalDate to) {
            totalCalls.incrementAndGet();
            lastRecruiterId.set(recruiterId);
            lastFrom.set(from);
            lastTo.set(to);

            CountDownLatch entered = enteredLatch.get();
            if (entered != null) {
                entered.countDown();
            }
            CountDownLatch blocker = blockLatch.get();
            if (blocker != null) {
                try {
                    blocker.await(4, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            }

            RuntimeException err = throwNextError.get();
            if (err != null) {
                throw err;
            }
            if (customLoader != null) {
                return customLoader.apply(recruiterId, from, to);
            }
            if (deriveGranularityFromRange && defaultSnap != null) {
                defaultSnap.setFilterFromDate(from);
                defaultSnap.setFilterToDate(to);
                defaultSnap.setTrendGranularity(determineTrendGranularity(from, to));
                defaultSnap.setDateRangeLabel(buildRangeLabel(from, to));
            }
            return defaultSnap;
        }
    }

    private record LoadedView(Parent root, RecruiterDashboardController controller) {}

    private static LoadedView loadDashboardWithService(RecruiterAnalyticsService service) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                RecruiterDashboardUITest.class.getResource("/fxml/recruiter_dashboard.fxml"));
        RecruiterService offlineRecruiterSvc = new RecruiterService() {
            @Override
            public java.util.Optional<RecruiterProfile> getProfile(int userId) {
                return java.util.Optional.empty();
            }
        };
        JobService offlineJobSvc = new JobService() {
            @Override
            public List<Job> getJobsByRecruiter(int recruiterId) {
                return java.util.Collections.emptyList();
            }
        };
        ApplicationService offlineAppSvc = new ApplicationService() {
            @Override
            public java.util.Map<Integer, Integer> countApplicationsByJobForRecruiter(int recruiterId) {
                return java.util.Collections.emptyMap();
            }
        };
        loader.setControllerFactory(clazz -> new RecruiterDashboardController(
                service, offlineRecruiterSvc, offlineJobSvc, offlineAppSvc, new AuthService()));
        return runOnFx(() -> {
            Parent root = loader.load();
            RecruiterDashboardController controller = loader.getController();
            return new LoadedView(root, controller);
        });
    }

    // =========================================================================
    // JavaFX, Reflection & Optional Screenshot Helpers
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
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
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

    private static int countScrollPanes(Node root) {
        int count = (root instanceof ScrollPane) ? 1 : 0;
        if (root instanceof ScrollPane sp && sp.getContent() != null) {
            count += countScrollPanes(sp.getContent());
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countScrollPanes(child);
            }
        }
        return count;
    }

    private static String collectLabelsText(Node root) {
        StringBuilder sb = new StringBuilder();
        if (root instanceof Label lbl && lbl.getText() != null) {
            sb.append(lbl.getText()).append(' ');
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                sb.append(collectLabelsText(child));
            }
        }
        return sb.toString();
    }

    private static void maybeCaptureScreenshot(Parent root, String filename) throws Exception {
        String captureDir = System.getProperty("hirehub.captureDir");
        if (captureDir == null || captureDir.isBlank()) {
            return;
        }
        Path outDir = Path.of(captureDir);
        Files.createDirectories(outDir);
        runOnFx(() -> {
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1280, 920);
            var cssUrl = RecruiterDashboardUITest.class.getResource("/css/style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            WritableImage img = scene.snapshot(null);
            writePng(img, outDir.resolve(filename));
            if ("recruiter_populated.png".equals(filename) && root.lookup("#mainScrollPane") instanceof ScrollPane sp) {
                sp.setVvalue(0.5);
                root.applyCss();
                root.layout();
                writePng(scene.snapshot(null), outDir.resolve("recruiter_populated_mid.png"));
                sp.setVvalue(1.0);
                root.applyCss();
                root.layout();
                writePng(scene.snapshot(null), outDir.resolve("recruiter_populated_bottom.png"));
            }
            stage.close();
            return null;
        });
    }

    private static void writePng(WritableImage img, Path file) throws IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        PixelReader reader = img.getPixelReader();
        byte[] raw = new byte[h * (1 + w * 4)];
        int pos = 0;
        for (int y = 0; y < h; y++) {
            raw[pos++] = 0; // filter none
            for (int x = 0; x < w; x++) {
                int argb = reader.getArgb(x, y);
                raw[pos++] = (byte) ((argb >> 16) & 0xFF);
                raw[pos++] = (byte) ((argb >> 8) & 0xFF);
                raw[pos++] = (byte) (argb & 0xFF);
                raw[pos++] = (byte) ((argb >> 24) & 0xFF);
            }
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream def = new DeflaterOutputStream(compressed)) {
            def.write(raw);
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(png)) {
            out.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream ihdrOut = new DataOutputStream(ihdr);
            ihdrOut.writeInt(w);
            ihdrOut.writeInt(h);
            ihdrOut.writeByte(8);
            ihdrOut.writeByte(6); // RGBA
            ihdrOut.writeByte(0);
            ihdrOut.writeByte(0);
            ihdrOut.writeByte(0);
            writeChunk(out, "IHDR", ihdr.toByteArray());
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
        Files.write(file, png.toByteArray());
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }

    private static void cleanupFixtures(int studentId, int recruiterId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (studentId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE student_id = ?")) {
                    ps.setInt(1, studentId);
                    ps.executeUpdate();
                }
            }
            if (recruiterId > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE a FROM applications a JOIN jobs j ON a.job_id = j.id WHERE j.recruiter_id = ?")) {
                    ps.setInt(1, recruiterId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM jobs WHERE recruiter_id = ?")) {
                    ps.setInt(1, recruiterId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM recruiter_profiles WHERE user_id = ?")) {
                    ps.setInt(1, recruiterId);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id IN (?, ?)")) {
                ps.setInt(1, studentId);
                ps.setInt(2, recruiterId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("  [WARN] Cleanup SQL error: " + e.getMessage());
        }
    }

    private static int countRows(String table) throws SQLException {
        String sql = switch (table) {
            case "users" -> "SELECT COUNT(*) FROM users";
            case "jobs" -> "SELECT COUNT(*) FROM jobs";
            case "applications" -> "SELECT COUNT(*) FROM applications";
            default -> throw new IllegalArgumentException("Unsupported table: " + table);
        };
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (condition) {
            System.out.println("  [PASS] " + label);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] " + label);
            testsFailed++;
        }
    }
}
