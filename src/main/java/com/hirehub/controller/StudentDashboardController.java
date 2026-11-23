package com.hirehub.controller;

import com.hirehub.model.*;
import com.hirehub.service.AuthService;
import com.hirehub.service.StudentAnalyticsService;
import com.hirehub.service.StudentService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import com.hirehub.util.StudentNavigationHandler;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StudentDashboardController — Phase 5 Step 4C Student Intelligence Dashboard.
 *
 * <p>Integrates the Phase 5 Step 4B/4B.1 student analytics backend with an interactive,
 * polished UI featuring:
 * <ul>
 *   <li>Section A: Personalized greeting &amp; live profile readiness indicator.</li>
 *   <li>Section B: Overview KPI cards (Applications, Review, Shortlisted, Placed, Platform Jobs).</li>
 *   <li>Section C: Application status pipeline distribution with percentage progress bars.</li>
 *   <li>Section D: Native JavaFX application activity trend visualization.</li>
 *   <li>Section E: Deterministic resume readiness, rubric breakdown &amp; advisory disclaimer.</li>
 *   <li>Section F: Profile/resume recognized skills &amp; platform demand gap analysis.</li>
 *   <li>Section G: Ranked skill-matched job recommendations with application routing.</li>
 *   <li>Section H: Chronological career journey activity feed.</li>
 *   <li>Section I: Working quick action navigation to all student portal tools.</li>
 * </ul>
 *
 * <p>Enforces:
 * <ul>
 *   <li>Fail-closed role authorization for STUDENT users only.</li>
 *   <li>Session-derived student ID (never accepts untrusted user-supplied input).</li>
 *   <li>Asynchronous data loading on worker threads (never blocks JavaFX UI).</li>
 *   <li>Sequence tracking and cancellation to prevent stale async responses.</li>
 *   <li>Graceful degradation, fallback preservation of last valid snapshot, and Retry action.</li>
 * </ul>
 */
public class StudentDashboardController {

    private static final DateTimeFormatter MEMBER_MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TREND_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM");

    // ── Root Pane ───────────────────────────────────────────────────────────
    @FXML private BorderPane rootPane;

    // ── Left Sidebar Navigation Buttons ──────────────────────────────────────
    @FXML private Button navDashboardBtn;
    @FXML private Button navProfileBtn;
    @FXML private Button navBrowseJobsBtn;
    @FXML private Button navApplicationsBtn;
    @FXML private Button navResumeAnalyzerBtn;
    @FXML private Button navAIBtn;
    @FXML private Button navLogoutBtn;

    // ── Header Bar ───────────────────────────────────────────────────────────
    @FXML private Label welcomeLabel;
    @FXML private Label welcomeGreetingLabel;
    @FXML private Label headerUserNameLabel;
    @FXML private Label headerUserInitialsLabel;
    @FXML private Label memberSinceLabel;
    @FXML private Label lastRefreshedLabel;
    @FXML private Label headerProfileStatusPill;
    @FXML private Button refreshBtn;
    @FXML private ProgressIndicator loadingIndicator;

    // ── Profile Left Card Dynamic Bindings ──────────────────────────────────
    @FXML private Label profileCardInitialsLabel;
    @FXML private Label profileCardNameLabel;
    @FXML private Label profileCardDegreeLabel;
    @FXML private Label profileCardCollegeLabel;
    @FXML private Label profileCardGradYearLabel;

    // ── Banners ──────────────────────────────────────────────────────────────
    @FXML private HBox errorBannerBox;
    @FXML private Label errorDetailLabel;
    @FXML private Button retryBtn;
    @FXML private HBox initialLoadingBox;

    // ── Content Panes ────────────────────────────────────────────────────────
    @FXML private VBox dashboardPane;
    @FXML private VBox profilePane;

    // ── Section A: Welcome & Profile Readiness ──────────────────────────────
    @FXML private ProgressBar profileCompletionBar;
    @FXML private Label profileCompletionPctLabel;
    @FXML private Label profileStatusSummaryLabel;
    @FXML private HBox missingFieldsBox;
    @FXML private Label missingFieldsLabel;

    // ── Section B: Overview KPI Cards ────────────────────────────────────────
    @FXML private Label kpiTotalAppsValue;
    @FXML private Label kpiTotalAppsSub;
    @FXML private Label kpiPendingValue;
    @FXML private Label kpiPendingSub;
    @FXML private Label kpiShortlistedValue;
    @FXML private Label kpiShortlistedSub;
    @FXML private Label kpiSelectedValue;
    @FXML private Label kpiSelectedSub;
    @FXML private Label kpiOpenJobsValue;
    @FXML private Label kpiOpenJobsSub;

    // ── Section C: Application Pipeline ──────────────────────────────────────
    @FXML private VBox pipelineDataBox;
    @FXML private VBox pipelineEmptyBox;
    @FXML private Label pipelinePendingCount;
    @FXML private Label pipelinePendingPct;
    @FXML private ProgressBar pipelinePendingBar;
    @FXML private Label pipelineShortlistedCount;
    @FXML private Label pipelineShortlistedPct;
    @FXML private ProgressBar pipelineShortlistedBar;
    @FXML private Label pipelineSelectedCount;
    @FXML private Label pipelineSelectedPct;
    @FXML private ProgressBar pipelineSelectedBar;
    @FXML private Label pipelineRejectedCount;
    @FXML private Label pipelineRejectedPct;
    @FXML private ProgressBar pipelineRejectedBar;

    // ── Section D: Application Activity Trend ────────────────────────────────
    @FXML private LineChart<String, Number> trendLineChart;
    @FXML private CategoryAxis trendXAxis;
    @FXML private NumberAxis trendYAxis;
    @FXML private Label trendEmptyLabel;

    // ── Section E: Resume Readiness & Deterministic Scoring ──────────────────
    @FXML private Button openResumeAnalyzerBtn;
    @FXML private VBox resumeContentBox;
    @FXML private VBox resumeEmptyBox;
    @FXML private Label resumeFilenameLabel;
    @FXML private Label resumeUploadDateLabel;
    @FXML private Label resumeSizeLabel;
    @FXML private Label resumeOverallScoreLabel;
    @FXML private Label resumeConfidenceBadge;
    @FXML private Label resumeSkillsCountLabel;
    @FXML private Label resumeExpCountLabel;
    @FXML private Label resumeProjCountLabel;
    @FXML private Label resumeCertCountLabel;
    @FXML private Label resumeMetricsCountLabel;
    @FXML private Label rubricSkillsLabel;
    @FXML private ProgressBar rubricSkillsBar;
    @FXML private Label rubricExpLabel;
    @FXML private ProgressBar rubricExpBar;
    @FXML private Label rubricEduLabel;
    @FXML private ProgressBar rubricEduBar;
    @FXML private Label rubricCompLabel;
    @FXML private ProgressBar rubricCompBar;
    @FXML private HBox resumePersistedBox;
    @FXML private Label resumePersistedSummaryLabel;
    @FXML private Label resumeDisclaimerLabel;

    // ── Section F: Skill Insights ────────────────────────────────────────────
    @FXML private FlowPane currentSkillsFlowPane;
    @FXML private Label skillsEmptyLabel;
    @FXML private FlowPane demandSkillsFlowPane;
    @FXML private Label demandEmptyLabel;

    // ── Section G: Recommended Jobs ──────────────────────────────────────────
    @FXML private VBox recommendedJobsBox;
    @FXML private VBox recommendationsEmptyBox;

    // ── Section H: Recent Activity ───────────────────────────────────────────
    @FXML private VBox recentActivityBox;
    @FXML private Label activityEmptyLabel;

    // ── Profile Edit Pane (Preserved) ────────────────────────────────────────
    @FXML private TextField phoneField;
    @FXML private TextField collegeField;
    @FXML private TextField degreeField;
    @FXML private TextField gradYearField;
    @FXML private TextArea  skillsArea;
    @FXML private TextField resumePathField;
    @FXML private Button    saveProfileBtn;
    @FXML private Label     profileStatusLabel;
    @FXML private ProgressIndicator profileLoading;

    // ── Services & State ─────────────────────────────────────────────────────
    private final StudentAnalyticsService analyticsService;
    private final StudentService studentService;
    private final AuthService authService;

    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final AtomicLong activeRequestSeq = new AtomicLong(0L);

    private volatile Task<?> activeAnalyticsTask;
    private StudentDashboardSnapshot lastValidSnapshot;
    private User currentUser;
    private StudentProfile currentProfile;
    private boolean redirectScheduled = false;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public StudentDashboardController() {
        this(new StudentAnalyticsService(), new StudentService(), new AuthService());
    }

    public StudentDashboardController(StudentAnalyticsService analyticsService,
                                      StudentService studentService,
                                      AuthService authService) {
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
        this.studentService = Objects.requireNonNull(studentService, "studentService must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @FXML
    public void initialize() {
        if (!ensureStudentAuthorized()) {
            return;
        }

        currentUser = SessionManager.getInstance().getCurrentUser();
        String fullName = safeText(currentUser.getFullName(), "Student");
        welcomeLabel.setText("Student Dashboard");
        if (welcomeGreetingLabel != null) {
            welcomeGreetingLabel.setText("Welcome back, " + fullName);
        }
        if (headerUserNameLabel != null) {
            headerUserNameLabel.setText(fullName);
        }
        String initials = extractInitials(fullName, "ST");
        if (headerUserInitialsLabel != null) {
            headerUserInitialsLabel.setText(initials);
        }
        if (profileCardNameLabel != null) {
            profileCardNameLabel.setText(fullName);
        }
        if (profileCardInitialsLabel != null) {
            profileCardInitialsLabel.setText(initials);
        }
        if (currentUser.getCreatedAt() != null) {
            memberSinceLabel.setText("Member since " + currentUser.getCreatedAt().format(MEMBER_MONTH_FORMAT));
        }

        if (trendYAxis != null) {
            trendYAxis.setMinorTickCount(0);
            trendYAxis.setTickUnit(1);
        }

        if (profileStatusLabel != null) profileStatusLabel.setVisible(false);
        if (profileLoading != null) profileLoading.setVisible(false);

        showPane(dashboardPane);
        updateActiveNav(navDashboardBtn);
        loadProfileAsync();
        loadAnalyticsAsync(false);
    }

    private boolean ensureStudentAuthorized() {
        if (SessionManager.getInstance().hasRole(User.Role.STUDENT)) {
            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null && user.getId() > 0) {
                return true;
            }
        }
        cancelAllBackgroundTasks();
        hideStudentContent();
        failClosedToLogin();
        return false;
    }

    private void cancelAllBackgroundTasks() {
        if (activeAnalyticsTask != null && activeAnalyticsTask.isRunning()) {
            activeAnalyticsTask.cancel();
        }
    }

    private void hideStudentContent() {
        if (rootPane != null) {
            rootPane.setVisible(false);
            rootPane.setManaged(false);
        }
        if (dashboardPane != null) {
            dashboardPane.setVisible(false);
            dashboardPane.setManaged(false);
        }
        if (profilePane != null) {
            profilePane.setVisible(false);
            profilePane.setManaged(false);
        }
    }

    private void failClosedToLogin() {
        if (!redirectScheduled) {
            redirectScheduled = true;
            Platform.runLater(() -> NavigationManager.navigateTo("/fxml/login.fxml"));
        }
    }

    private boolean isSessionMatchingStudent(int studentId) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT)) return false;
        User user = SessionManager.getInstance().getCurrentUser();
        return user != null && user.getId() == studentId;
    }

    // -----------------------------------------------------------------------
    // Asynchronous Analytics Loading
    // -----------------------------------------------------------------------

    @FXML
    public void handleRefresh() {
        loadAnalyticsAsync(true);
    }

    @FXML
    public void handleRetry() {
        loadAnalyticsAsync(false);
    }

    public void loadAnalyticsAsync(boolean isRefresh) {
        if (!ensureStudentAuthorized()) return;
        final int studentId = currentUser.getId();

        if (isRefreshing.getAndSet(true)) {
            return; // Prevent duplicate concurrent requests
        }

        long seq = activeRequestSeq.incrementAndGet();

        if (activeAnalyticsTask != null && activeAnalyticsTask.isRunning()) {
            activeAnalyticsTask.cancel();
        }

        setLoadingUi(true, !isRefresh && lastValidSnapshot == null);

        Task<StudentDashboardSnapshot> task = new Task<>() {
            @Override
            protected StudentDashboardSnapshot call() {
                if (isCancelled()) return null;
                return analyticsService.loadAllTimeSnapshot(studentId);
            }
        };
        activeAnalyticsTask = task;

        task.setOnSucceeded(e -> {
            if (seq != activeRequestSeq.get() || task.isCancelled() || !isSessionMatchingStudent(studentId)) {
                return;
            }
            isRefreshing.set(false);
            setLoadingUi(false, false);

            StudentDashboardSnapshot snapshot = task.getValue();
            if (snapshot == null) {
                handleAnalyticsLoadFailure("Analytics backend returned an empty response.");
                return;
            }
            if (snapshot.hasError()) {
                handleAnalyticsLoadFailure(snapshot.getErrorMessage());
                return;
            }

            this.lastValidSnapshot = snapshot;
            renderSnapshot(snapshot);
        });

        task.setOnFailed(e -> {
            if (seq != activeRequestSeq.get() || task.isCancelled() || !isSessionMatchingStudent(studentId)) {
                return;
            }
            isRefreshing.set(false);
            setLoadingUi(false, false);

            Throwable ex = task.getException();
            String msg = ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Database or analytics error occurred while loading dashboard data.";
            handleAnalyticsLoadFailure(msg);
        });

        task.setOnCancelled(e -> {
            if (seq != activeRequestSeq.get()) return;
            isRefreshing.set(false);
            setLoadingUi(false, false);
        });

        Thread worker = new Thread(task, "student-analytics-loader-" + seq);
        worker.setDaemon(true);
        worker.start();
    }

    private void handleAnalyticsLoadFailure(String errorMessage) {
        String safeDetail = (errorMessage != null && !errorMessage.isBlank())
                ? errorMessage.trim()
                : "Could not load analytics from the database.";

        if (lastValidSnapshot != null) {
            String punctuated = safeDetail.endsWith(".") || safeDetail.endsWith("!") ? safeDetail : safeDetail + ".";
            if (errorDetailLabel != null) {
                errorDetailLabel.setText(punctuated + " Showing previously loaded data.");
            }
        } else {
            if (errorDetailLabel != null) {
                errorDetailLabel.setText(safeDetail);
            }
            if (lastRefreshedLabel != null) {
                lastRefreshedLabel.setText("Load failed — click Retry");
            }
        }
        showBanner(errorBannerBox);
    }

    private void setLoadingUi(boolean loading, boolean showInitialBanner) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(loading);
            loadingIndicator.setManaged(loading);
        }
        if (refreshBtn != null) {
            refreshBtn.setDisable(loading);
        }
        if (retryBtn != null) {
            retryBtn.setDisable(loading);
        }
        if (initialLoadingBox != null) {
            boolean showInit = loading && showInitialBanner;
            initialLoadingBox.setVisible(showInit);
            initialLoadingBox.setManaged(showInit);
        }
    }

    // -----------------------------------------------------------------------
    // Snapshot Rendering (Sections A - I)
    // -----------------------------------------------------------------------

    public void renderSnapshot(StudentDashboardSnapshot snapshot) {
        if (snapshot == null) return;
        hideBanner(errorBannerBox);

        // Section A: Header & Profile Readiness
        renderProfileReadiness(snapshot.getKpi());

        // Section B: Overview KPIs
        renderOverviewKpis(snapshot.getKpi());

        // Section C: Application Pipeline
        renderPipeline(snapshot.getStatusDistribution());

        // Section D: Application Activity Trend
        renderActivityTrend(snapshot.getApplicationTrends());

        // Section E: Resume Readiness & Deterministic Scoring
        renderResumeReadiness(snapshot.getResumeReadiness());

        // Section F: Skill Insights
        renderSkillInsights(snapshot.getSkillInsight());

        // Section G: Recommended Jobs
        renderJobRecommendations(snapshot.getRecommendations());

        // Section H: Recent Activity Feed
        renderRecentActivity(snapshot.getRecentActivities());

        if (lastRefreshedLabel != null) {
            lastRefreshedLabel.setText("Refreshed: " + LocalDateTime.now().format(TIME_FORMAT));
        }
    }

    private void renderProfileReadiness(StudentOverviewKPI kpi) {
        if (kpi == null) return;

        int score = kpi.getProfileCompletionScore();
        double pct = Math.min(1.0, Math.max(0.0, score / 6.0));
        int pctInt = (int) Math.round(pct * 100.0);

        if (profileCompletionBar != null) profileCompletionBar.setProgress(pct);
        if (profileCompletionPctLabel != null) {
            profileCompletionPctLabel.setText(pctInt + "% Complete (" + score + "/6 fields)");
        }

        if (kpi.isProfileComplete()) {
            if (headerProfileStatusPill != null) {
                headerProfileStatusPill.setText("Profile Complete");
                headerProfileStatusPill.getStyleClass().removeAll("stu-header-pill-warn");
                if (!headerProfileStatusPill.getStyleClass().contains("stu-header-pill-ok")) {
                    headerProfileStatusPill.getStyleClass().add("stu-header-pill-ok");
                }
            }
            if (profileStatusSummaryLabel != null) {
                profileStatusSummaryLabel.setText("✓ Profile is complete and optimized for matching");
                profileStatusSummaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #16A34A; -fx-font-weight: bold;");
            }
            if (missingFieldsBox != null) {
                missingFieldsBox.setVisible(false);
                missingFieldsBox.setManaged(false);
            }
        } else {
            if (headerProfileStatusPill != null) {
                headerProfileStatusPill.setText("Profile Incomplete");
                headerProfileStatusPill.getStyleClass().removeAll("stu-header-pill-ok");
                if (!headerProfileStatusPill.getStyleClass().contains("stu-header-pill-warn")) {
                    headerProfileStatusPill.getStyleClass().add("stu-header-pill-warn");
                }
            }
            if (profileStatusSummaryLabel != null) {
                profileStatusSummaryLabel.setText("⚠ Please complete missing fields for better job recommendations");
                profileStatusSummaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #B45309; -fx-font-weight: bold;");
            }
            if (missingFieldsBox != null) {
                missingFieldsBox.setVisible(true);
                missingFieldsBox.setManaged(true);
            }
            if (missingFieldsLabel != null) {
                List<String> missing = kpi.getMissingProfileFields();
                missingFieldsLabel.setText(missing != null && !missing.isEmpty() ? String.join(", ", missing) : "None");
            }
        }
    }

    private void renderOverviewKpis(StudentOverviewKPI kpi) {
        if (kpi == null) return;
        if (kpiTotalAppsValue != null) kpiTotalAppsValue.setText(String.valueOf(kpi.getTotalApplications()));
        if (kpiPendingValue != null) kpiPendingValue.setText(String.valueOf(kpi.getPendingCount()));
        if (kpiShortlistedValue != null) kpiShortlistedValue.setText(String.valueOf(kpi.getShortlistedCount()));
        if (kpiSelectedValue != null) kpiSelectedValue.setText(String.valueOf(kpi.getSelectedCount()));
        if (kpiOpenJobsValue != null) kpiOpenJobsValue.setText(String.valueOf(kpi.getOpenActiveJobs()));
        if (kpiOpenJobsSub != null) {
            int openJobs = kpi.getOpenActiveJobs();
            kpiOpenJobsSub.setText(openJobs == 1 ? "1 active opportunity" : openJobs + " active opportunities");
        }
    }

    private void renderPipeline(StudentStatusDistribution dist) {
        if (dist == null || dist.getTotal() == 0) {
            if (pipelineDataBox != null) {
                pipelineDataBox.setVisible(false);
                pipelineDataBox.setManaged(false);
            }
            if (pipelineEmptyBox != null) {
                pipelineEmptyBox.setVisible(true);
                pipelineEmptyBox.setManaged(true);
            }
            return;
        }

        if (pipelineEmptyBox != null) {
            pipelineEmptyBox.setVisible(false);
            pipelineEmptyBox.setManaged(false);
        }
        if (pipelineDataBox != null) {
            pipelineDataBox.setVisible(true);
            pipelineDataBox.setManaged(true);
        }

        if (pipelinePendingCount != null) pipelinePendingCount.setText(String.valueOf(dist.getPendingCount()));
        if (pipelinePendingPct != null) pipelinePendingPct.setText(String.format(Locale.US, " (%.1f%%)", dist.getPendingPct()));
        if (pipelinePendingBar != null) pipelinePendingBar.setProgress(dist.getPendingPct() / 100.0);

        if (pipelineShortlistedCount != null) pipelineShortlistedCount.setText(String.valueOf(dist.getShortlistedCount()));
        if (pipelineShortlistedPct != null) pipelineShortlistedPct.setText(String.format(Locale.US, " (%.1f%%)", dist.getShortlistedPct()));
        if (pipelineShortlistedBar != null) pipelineShortlistedBar.setProgress(dist.getShortlistedPct() / 100.0);

        if (pipelineSelectedCount != null) pipelineSelectedCount.setText(String.valueOf(dist.getSelectedCount()));
        if (pipelineSelectedPct != null) pipelineSelectedPct.setText(String.format(Locale.US, " (%.1f%%)", dist.getSelectedPct()));
        if (pipelineSelectedBar != null) pipelineSelectedBar.setProgress(dist.getSelectedPct() / 100.0);

        if (pipelineRejectedCount != null) pipelineRejectedCount.setText(String.valueOf(dist.getRejectedCount()));
        if (pipelineRejectedPct != null) pipelineRejectedPct.setText(String.format(Locale.US, " (%.1f%%)", dist.getRejectedPct()));
        if (pipelineRejectedBar != null) pipelineRejectedBar.setProgress(dist.getRejectedPct() / 100.0);
    }

    private void renderActivityTrend(List<StudentTrendPoint> points) {
        if (trendLineChart == null) return;

        if (points == null || points.isEmpty()) {
            trendLineChart.setVisible(false);
            trendLineChart.setManaged(false);
            if (trendEmptyLabel != null) {
                trendEmptyLabel.setVisible(true);
                trendEmptyLabel.setManaged(true);
            }
            return;
        }

        // Sort data chronologically and filter out nulls/null dates
        List<StudentTrendPoint> sortedPoints = points.stream()
                .filter(pt -> pt != null && pt.getDate() != null)
                .sorted(Comparator.comparing(StudentTrendPoint::getDate))
                .toList();

        if (sortedPoints.isEmpty()) {
            trendLineChart.setVisible(false);
            trendLineChart.setManaged(false);
            if (trendEmptyLabel != null) {
                trendEmptyLabel.setVisible(true);
                trendEmptyLabel.setManaged(true);
            }
            return;
        }

        if (trendEmptyLabel != null) {
            trendEmptyLabel.setVisible(false);
            trendEmptyLabel.setManaged(false);
        }
        trendLineChart.setVisible(true);
        trendLineChart.setManaged(true);

        // Disambiguate year boundaries when date range spans multiple years
        boolean multiYear = sortedPoints.stream()
                .map(pt -> pt.getDate().getYear())
                .distinct()
                .count() > 1;
        DateTimeFormatter axisDateFormatter = multiYear
                ? DateTimeFormatter.ofPattern("dd MMM ''yy")
                : TREND_DATE_FORMAT;

        // Clear and configure chronological categories on X-axis
        if (trendXAxis != null) {
            List<String> distinctCategories = sortedPoints.stream()
                    .map(pt -> pt.getDate().format(axisDateFormatter))
                    .distinct()
                    .toList();
            trendXAxis.setCategories(FXCollections.observableArrayList(distinctCategories));
        }

        XYChart.Series<String, Number> seriesApps = new XYChart.Series<>();
        seriesApps.setName("Applications");
        XYChart.Series<String, Number> seriesShort = new XYChart.Series<>();
        seriesShort.setName("Shortlisted");
        XYChart.Series<String, Number> seriesSel = new XYChart.Series<>();
        seriesSel.setName("Selected");

        for (StudentTrendPoint pt : sortedPoints) {
            String label = pt.getDate().format(axisDateFormatter);
            seriesApps.getData().add(new XYChart.Data<>(label, pt.getApplicationCount()));
            seriesShort.getData().add(new XYChart.Data<>(label, pt.getShortlistedCount()));
            seriesSel.getData().add(new XYChart.Data<>(label, pt.getSelectedCount()));
        }

        trendLineChart.getData().setAll(List.of(seriesApps, seriesShort, seriesSel));
    }

    private void renderResumeReadiness(StudentResumeReadiness readiness) {
        if (readiness == null || !readiness.isHasActiveResume()) {
            if (resumeContentBox != null) {
                resumeContentBox.setVisible(false);
                resumeContentBox.setManaged(false);
            }
            if (resumeEmptyBox != null) {
                resumeEmptyBox.setVisible(true);
                resumeEmptyBox.setManaged(true);
            }
            return;
        }

        if (resumeEmptyBox != null) {
            resumeEmptyBox.setVisible(false);
            resumeEmptyBox.setManaged(false);
        }
        if (resumeContentBox != null) {
            resumeContentBox.setVisible(true);
            resumeContentBox.setManaged(true);
        }

        if (resumeFilenameLabel != null) {
            resumeFilenameLabel.setText(readiness.getActiveResumeFileName() != null ? readiness.getActiveResumeFileName() : "Active Resume");
        }
        if (resumeUploadDateLabel != null) {
            resumeUploadDateLabel.setText("Uploaded: " + (readiness.getActiveResumeUploadedAt() != null
                    ? readiness.getActiveResumeUploadedAt().format(DATE_FORMAT) : "Unknown"));
        }
        if (resumeSizeLabel != null) {
            resumeSizeLabel.setText(formatFileSize(readiness.getActiveResumeFileSizeBytes()));
        }

        // Deterministic live score & confidence
        if (resumeOverallScoreLabel != null) {
            Integer overall = readiness.getOverallScore();
            resumeOverallScoreLabel.setText(overall != null ? overall + " / 100" : "— / 100");
        }
        if (resumeConfidenceBadge != null) {
            String conf = readiness.getEvaluationConfidence();
            if (conf != null && !conf.isBlank()) {
                resumeConfidenceBadge.setText(conf.toUpperCase() + " CONFIDENCE");
                resumeConfidenceBadge.getStyleClass().removeAll("stu-header-pill-ok", "stu-header-pill-warn");
                if ("HIGH".equalsIgnoreCase(conf)) {
                    resumeConfidenceBadge.getStyleClass().add("stu-header-pill-ok");
                } else {
                    resumeConfidenceBadge.getStyleClass().add("stu-header-pill-warn");
                }
            } else {
                resumeConfidenceBadge.setText("EVALUATION PENDING");
            }
        }

        // Structural parsed signals
        if (resumeSkillsCountLabel != null) resumeSkillsCountLabel.setText(readiness.getParsedSkillCount() + " Skills");
        if (resumeExpCountLabel != null) resumeExpCountLabel.setText(readiness.getParsedExperienceCount() + " Experiences");
        if (resumeProjCountLabel != null) resumeProjCountLabel.setText(readiness.getParsedProjectCount() + " Projects");
        if (resumeCertCountLabel != null) resumeCertCountLabel.setText(readiness.getParsedCertificationCount() + " Certifications");
        if (resumeMetricsCountLabel != null) resumeMetricsCountLabel.setText(readiness.getParsedMetricsCount() + " Metrics");

        // Rubric Breakdown (Skills: 40, Exp: 25, Edu: 15, Completeness: 10)
        int sk = readiness.getSkillsScore() != null ? readiness.getSkillsScore() : 0;
        if (rubricSkillsLabel != null) rubricSkillsLabel.setText(sk + " / 40 pts");
        if (rubricSkillsBar != null) rubricSkillsBar.setProgress(sk / 40.0);

        int ex = readiness.getExperienceScore() != null ? readiness.getExperienceScore() : 0;
        if (rubricExpLabel != null) rubricExpLabel.setText(ex + " / 25 pts");
        if (rubricExpBar != null) rubricExpBar.setProgress(ex / 25.0);

        int ed = readiness.getEducationScore() != null ? readiness.getEducationScore() : 0;
        if (rubricEduLabel != null) rubricEduLabel.setText(ed + " / 15 pts");
        if (rubricEduBar != null) rubricEduBar.setProgress(ed / 15.0);

        int comp = readiness.getCompletenessScore() != null ? readiness.getCompletenessScore() : 0;
        if (rubricCompLabel != null) rubricCompLabel.setText(comp + " / 10 pts");
        if (rubricCompBar != null) rubricCompBar.setProgress(comp / 10.0);

        // Persisted analysis summary & disclaimer
        if (resumePersistedSummaryLabel != null) {
            Integer persistedScore = readiness.getPersistedOverallScore();
            if (persistedScore != null) {
                String dateStr = readiness.getPersistedCreatedAt() != null ? readiness.getPersistedCreatedAt().format(DATE_FORMAT) : "recorded";
                resumePersistedSummaryLabel.setText("Previous Full Analysis: " + persistedScore + "/100 (Evaluated on " + dateStr + ")");
            } else {
                resumePersistedSummaryLabel.setText("Previous Full Analysis: None yet (use Resume Analyzer to evaluate against specific job targets)");
            }
        }
        if (resumeDisclaimerLabel != null) {
            resumeDisclaimerLabel.setText(readiness.getDisclaimerText() != null ? readiness.getDisclaimerText() : StudentResumeReadiness.DEFAULT_DISCLAIMER);
        }
    }

    private void renderSkillInsights(StudentSkillInsight insight) {
        if (currentSkillsFlowPane != null) {
            currentSkillsFlowPane.getChildren().clear();
            List<String> combined = insight != null ? insight.getCombinedSkills() : Collections.emptyList();
            if (combined == null || combined.isEmpty()) {
                if (skillsEmptyLabel != null) {
                    skillsEmptyLabel.setVisible(true);
                    skillsEmptyLabel.setManaged(true);
                }
            } else {
                if (skillsEmptyLabel != null) {
                    skillsEmptyLabel.setVisible(false);
                    skillsEmptyLabel.setManaged(false);
                }
                for (String sk : combined) {
                    Label chip = new Label(sk);
                    chip.getStyleClass().add("stu-skill-chip-current");
                    currentSkillsFlowPane.getChildren().add(chip);
                }
            }
        }

        if (demandSkillsFlowPane != null) {
            demandSkillsFlowPane.getChildren().clear();
            List<String> demand = insight != null ? insight.getInDemandMissingSkills() : Collections.emptyList();
            if (demand == null || demand.isEmpty()) {
                if (demandEmptyLabel != null) {
                    demandEmptyLabel.setVisible(true);
                    demandEmptyLabel.setManaged(true);
                }
            } else {
                if (demandEmptyLabel != null) {
                    demandEmptyLabel.setVisible(false);
                    demandEmptyLabel.setManaged(false);
                }
                for (String gap : demand) {
                    Label chip = new Label("+ " + gap);
                    chip.getStyleClass().add("stu-skill-chip-demand");
                    demandSkillsFlowPane.getChildren().add(chip);
                }
            }
        }
    }

    private void renderJobRecommendations(List<StudentJobRecommendation> recs) {
        if (recommendedJobsBox == null) return;
        recommendedJobsBox.getChildren().clear();

        if (recs == null || recs.isEmpty()) {
            if (recommendationsEmptyBox != null) {
                recommendationsEmptyBox.setVisible(true);
                recommendationsEmptyBox.setManaged(true);
            }
            return;
        }

        if (recommendationsEmptyBox != null) {
            recommendationsEmptyBox.setVisible(false);
            recommendationsEmptyBox.setManaged(false);
        }

        int count = 0;
        for (StudentJobRecommendation rec : recs) {
            if (count++ >= 5) break; // Display top 5

            VBox card = new VBox(8);
            card.getStyleClass().add("stu-rec-item");

            HBox topRow = new HBox(12);
            topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            VBox titlesBox = new VBox(2);
            HBox.setHgrow(titlesBox, Priority.ALWAYS);
            Label titleLbl = new Label(safeText(rec.getTitle(), "Job Opening"));
            titleLbl.getStyleClass().add("stu-rec-title");
            Label companyLbl = new Label(safeText(rec.getCompanyName(), "HireHub Employer"));
            companyLbl.getStyleClass().add("stu-rec-company");
            titlesBox.getChildren().addAll(titleLbl, companyLbl);

            // Match badge
            Label matchBadge = new Label();
            matchBadge.getStyleClass().add("stu-match-badge");
            if (rec.getTotalRequiredSkillCount() > 0) {
                int pct = (int) Math.round(rec.getMatchPercentage());
                matchBadge.setText(pct + "% MATCH (" + rec.getMatchingSkillCount() + "/" + rec.getTotalRequiredSkillCount() + ")");
                if (pct >= 75) {
                    matchBadge.getStyleClass().add("stu-match-high");
                } else if (pct >= 40) {
                    matchBadge.getStyleClass().add("stu-match-medium");
                } else {
                    matchBadge.getStyleClass().add("stu-match-unranked");
                }
            } else {
                matchBadge.setText("GENERAL OPENING");
                matchBadge.getStyleClass().add("stu-match-unranked");
            }

            topRow.getChildren().addAll(titlesBox, matchBadge);

            // Meta row: Job Type, Location, Deadline
            HBox metaRow = new HBox(12);
            metaRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label typeLbl = new Label("💼 " + safeText(rec.getJobType(), "Full-time"));
            typeLbl.getStyleClass().add("stu-rec-meta");
            Label locLbl = new Label("📍 " + safeText(rec.getLocation(), "Multiple Locations"));
            locLbl.getStyleClass().add("stu-rec-meta");

            Region metaSpacer = new Region();
            HBox.setHgrow(metaSpacer, Priority.ALWAYS);

            metaRow.getChildren().addAll(typeLbl, locLbl, metaSpacer);

            // Action / Applied Status
            if (rec.isAlreadyApplied()) {
                Label appliedBadge = new Label("✓ Already Applied");
                appliedBadge.getStyleClass().add("stu-applied-badge");
                metaRow.getChildren().add(appliedBadge);
            } else {
                Button viewJobBtn = new Button("View in Job Board");
                viewJobBtn.getStyleClass().add("secondary-button");
                viewJobBtn.setStyle("-fx-font-size: 11.5px; -fx-padding: 4 10;");
                viewJobBtn.setOnAction(e -> navigateTo("/fxml/job_board.fxml"));
                metaRow.getChildren().add(viewJobBtn);
            }

            // Skills row (if any)
            if ((rec.getMatchedSkills() != null && !rec.getMatchedSkills().isEmpty()) ||
                (rec.getMissingSkills() != null && !rec.getMissingSkills().isEmpty())) {
                FlowPane skillsPane = new FlowPane(6, 6);
                if (rec.getMatchedSkills() != null) {
                    for (String m : rec.getMatchedSkills()) {
                        Label s = new Label("✓ " + m);
                        s.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #15803D; -fx-background-color: #DCFCE7; -fx-padding: 2 6; -fx-background-radius: 4;");
                        skillsPane.getChildren().add(s);
                    }
                }
                if (rec.getMissingSkills() != null) {
                    for (String m : rec.getMissingSkills()) {
                        Label s = new Label("- " + m);
                        s.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #991B1B; -fx-background-color: #FEE2E2; -fx-padding: 2 6; -fx-background-radius: 4;");
                        skillsPane.getChildren().add(s);
                    }
                }
                card.getChildren().addAll(topRow, metaRow, skillsPane);
            } else {
                card.getChildren().addAll(topRow, metaRow);
            }

            recommendedJobsBox.getChildren().add(card);
        }
    }

    private void renderRecentActivity(List<StudentActivityEvent> activities) {
        if (recentActivityBox == null) return;
        recentActivityBox.getChildren().clear();

        if (activities == null || activities.isEmpty()) {
            if (activityEmptyLabel != null) {
                activityEmptyLabel.setVisible(true);
                activityEmptyLabel.setManaged(true);
            }
            return;
        }

        if (activityEmptyLabel != null) {
            activityEmptyLabel.setVisible(false);
            activityEmptyLabel.setManaged(false);
        }

        for (StudentActivityEvent act : activities) {
            HBox row = new HBox(12);
            row.getStyleClass().add("stu-activity-row");
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Badge
            Label badge = new Label(act.getBadgeText() != null ? act.getBadgeText() : "UPDATE");
            badge.getStyleClass().add("stu-activity-badge");
            if (act.getBadgeStyleClass() != null && !act.getBadgeStyleClass().isBlank()) {
                badge.getStyleClass().add(act.getBadgeStyleClass());
            }

            VBox textBox = new VBox(2);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            Label title = new Label(safeText(act.getTitle(), "Activity event"));
            title.setStyle("-fx-font-size: 12.5px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
            Label desc = new Label(safeText(act.getDescription(), ""));
            desc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B;");
            textBox.getChildren().addAll(title, desc);

            Label timeLbl = new Label(act.getTimestamp() != null ? act.getTimestamp().format(DATE_FORMAT) : "");
            timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

            row.getChildren().addAll(badge, textBox, timeLbl);
            recentActivityBox.getChildren().add(row);
        }
    }

    // -----------------------------------------------------------------------
    // Navigation Handlers
    // -----------------------------------------------------------------------

    @FXML public void handleNavDashboard(ActionEvent e) {
        showPane(dashboardPane);
        updateActiveNav(navDashboardBtn);
    }

    @FXML public void handleNavProfile(ActionEvent e) {
        showPane(profilePane);
        populateEditForm();
        updateActiveNav(navProfileBtn);
    }

    public void showProfileDirectly() {
        showPane(profilePane);
        populateEditForm();
        updateActiveNav(navProfileBtn);
    }

    private void updateActiveNav(Button activeBtn) {
        Button[] navButtons = {navDashboardBtn, navBrowseJobsBtn, navApplicationsBtn,
                               navResumeAnalyzerBtn, navAIBtn, navProfileBtn};
        for (Button b : navButtons) {
            if (b != null) {
                b.getStyleClass().remove("sidebar-nav-active");
            }
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("sidebar-nav-active")) {
            activeBtn.getStyleClass().add("sidebar-nav-active");
        }
    }

    @FXML public void handleNavBrowseJobs(ActionEvent e) {
        StudentNavigationHandler.navigateToBrowseJobs(navBrowseJobsBtn);
    }

    @FXML public void handleNavApplications(ActionEvent e) {
        StudentNavigationHandler.navigateToApplications(navApplicationsBtn);
    }

    @FXML public void handleNavResumeAnalyzer(ActionEvent e) {
        StudentNavigationHandler.navigateToResumeAnalyzer(navResumeAnalyzerBtn);
    }

    @FXML public void handleNavAI(ActionEvent e) {
        StudentNavigationHandler.navigateToAI(navAIBtn);
    }

    @FXML public void handleLogout(ActionEvent e) {
        cancelAllBackgroundTasks();
        StudentNavigationHandler.handleLogout(navLogoutBtn);
    }

    // -----------------------------------------------------------------------
    // Profile Edit & Persistence (Preserved)
    // -----------------------------------------------------------------------

    @FXML
    public void handleSaveProfile(ActionEvent e) {
        if (!ensureStudentAuthorized()) return;

        String phone      = phoneField != null ? phoneField.getText() : null;
        String college    = collegeField != null ? collegeField.getText() : null;
        String degree     = degreeField != null ? degreeField.getText() : null;
        String gradYearTx = gradYearField != null ? gradYearField.getText().trim() : "";
        String skills     = skillsArea != null ? skillsArea.getText() : null;
        String resume     = resumePathField != null ? resumePathField.getText() : null;

        int graduationYear = 0;
        try {
            if (!gradYearTx.isBlank()) graduationYear = Integer.parseInt(gradYearTx);
        } catch (NumberFormatException ex) {
            showError(profileStatusLabel, "Graduation year must be a 4-digit number.");
            return;
        }

        setProfileLoading(true);
        final int finalGradYear = graduationYear;
        final int studentId = currentUser.getId();

        Task<StudentService.ServiceResult> task = new Task<>() {
            @Override
            protected StudentService.ServiceResult call() {
                return studentService.saveProfile(
                        studentId, phone, college, degree,
                        finalGradYear, skills, resume);
            }
        };

        task.setOnSucceeded(ev -> {
            setProfileLoading(false);
            StudentService.ServiceResult result = task.getValue();
            if (result != null && result.isSuccess()) {
                showSuccess(profileStatusLabel, result.getMessage());
                loadProfileAsync();
                loadAnalyticsAsync(true); // Refresh analytics snapshot with newly updated profile
            } else {
                showError(profileStatusLabel, result != null ? result.getMessage() : "Failed to save profile.");
            }
        });

        task.setOnFailed(ev -> {
            setProfileLoading(false);
            showError(profileStatusLabel, "Unexpected error saving profile. Please try again.");
        });

        new Thread(task, "save-profile-thread").start();
    }

    private void loadProfileAsync() {
        if (currentUser == null) return;
        final int studentId = currentUser.getId();

        Task<Optional<StudentProfile>> task = new Task<>() {
            @Override
            protected Optional<StudentProfile> call() {
                return studentService.getProfile(studentId);
            }
        };

        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            currentProfile = task.getValue().orElse(null);
            populateEditForm();
        }));

        new Thread(task, "load-profile-thread").start();
    }

    private void populateEditForm() {
        if (currentProfile != null) {
            if (phoneField != null) phoneField.setText(nvl(currentProfile.getPhone()));
            if (collegeField != null) collegeField.setText(nvl(currentProfile.getCollege()));
            if (degreeField != null) degreeField.setText(nvl(currentProfile.getDegree()));
            if (gradYearField != null) {
                gradYearField.setText(currentProfile.getGraduationYear() > 0
                        ? String.valueOf(currentProfile.getGraduationYear()) : "");
            }
            if (skillsArea != null) skillsArea.setText(nvl(currentProfile.getSkills()));
            if (resumePathField != null) resumePathField.setText(nvl(currentProfile.getResumePath()));
            if (profileCardDegreeLabel != null) {
                profileCardDegreeLabel.setText((currentProfile.getDegree() != null && !currentProfile.getDegree().isBlank()) ? currentProfile.getDegree() : "Degree Details");
            }
            if (profileCardCollegeLabel != null) {
                profileCardCollegeLabel.setText((currentProfile.getCollege() != null && !currentProfile.getCollege().isBlank()) ? currentProfile.getCollege() : "—");
            }
            if (profileCardGradYearLabel != null) {
                profileCardGradYearLabel.setText(currentProfile.getGraduationYear() > 0 ? String.valueOf(currentProfile.getGraduationYear()) : "—");
            }
        }
        if (profileStatusLabel != null) profileStatusLabel.setVisible(false);
    }

    private void showPane(VBox pane) {
        if (dashboardPane != null) {
            dashboardPane.setVisible(false);
            dashboardPane.setManaged(false);
        }
        if (profilePane != null) {
            profilePane.setVisible(false);
            profilePane.setManaged(false);
        }
        if (pane != null) {
            pane.setVisible(true);
            pane.setManaged(true);
        }
    }

    private void navigateTo(String fxmlPath) {
        NavigationManager.navigateTo(welcomeLabel, fxmlPath);
    }

    private void setProfileLoading(boolean loading) {
        Platform.runLater(() -> {
            if (saveProfileBtn != null) saveProfileBtn.setDisable(loading);
            if (profileLoading != null) profileLoading.setVisible(loading);
        });
    }

    private void showBanner(HBox banner) {
        if (banner != null) {
            banner.setVisible(true);
            banner.setManaged(true);
        }
    }

    private void hideBanner(HBox banner) {
        if (banner != null) {
            banner.setVisible(false);
            banner.setManaged(false);
        }
    }

    private void showError(Label label, String msg) {
        if (label == null) return;
        Platform.runLater(() -> {
            label.getStyleClass().removeAll("success-label");
            if (!label.getStyleClass().contains("error-label")) label.getStyleClass().add("error-label");
            label.setText(msg);
            label.setVisible(true);
        });
    }

    private void showSuccess(Label label, String msg) {
        if (label == null) return;
        Platform.runLater(() -> {
            label.getStyleClass().removeAll("error-label");
            if (!label.getStyleClass().contains("success-label")) label.getStyleClass().add("success-label");
            label.setText(msg);
            label.setVisible(true);
        });
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 KB";
        if (bytes < 1024) return bytes + " B";
        long kb = bytes / 1024;
        if (kb < 1024) return kb + " KB";
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private String safeText(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "" : s;
    }

    private String extractInitials(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        } else if (parts[0].length() >= 2) {
            return parts[0].substring(0, 2).toUpperCase();
        } else if (!parts[0].isEmpty()) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return fallback;
    }

}
