package com.hirehub.controller;

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
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.AuthService;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterAnalyticsService;
import com.hirehub.service.RecruiterService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for the Phase 5 Step 3C Recruiter Intelligence Dashboard,
 * Company Profile, and Manage Jobs portal.
 */
public class RecruiterDashboardController {

    public enum FilterPreset {
        DAYS_30(30, "30 Days"),
        DAYS_60(60, "60 Days"),
        DAYS_200(200, "200 Days"),
        ALL_TIME(0, "All Time");

        private final int days;
        private final String displayLabel;

        FilterPreset(int days, String displayLabel) {
            this.days = days;
            this.displayLabel = displayLabel;
        }

        public int getDays() {
            return days;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter MEMBER_MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final double PERF_TABLE_HEADER_HEIGHT = 38.0;
    private static final double PERF_TABLE_ROW_HEIGHT = 40.0;

    // Root & Shell
    @FXML private BorderPane rootPane;
    @FXML private ScrollPane mainScrollPane;
    @FXML private Button navDashboardBtn;
    @FXML private Button navProfileBtn;
    @FXML private Button navJobsBtn;
    @FXML private Button navPostJobBtn;
    @FXML private Button navLogoutBtn;

    // Header
    @FXML private Label welcomeLabel;
    @FXML private Label recruiterInitialsLabel;
    @FXML private Label headerProfileStatusPill;
    @FXML private Label companyNameHeaderLabel;
    @FXML private Label memberSinceLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label lastRefreshedLabel;

    // Panes
    @FXML private VBox dashboardPane;
    @FXML private VBox profilePane;
    @FXML private VBox jobsPane;

    // Section A: Toolbar, Filters & Banners
    @FXML private ToggleGroup filterPresetGroup;
    @FXML private ToggleButton preset30Btn;
    @FXML private ToggleButton preset60Btn;
    @FXML private ToggleButton preset200Btn;
    @FXML private ToggleButton presetAllBtn;
    @FXML private Label reportingPeriodLabel;
    @FXML private Label trendGranularityBadge;
    @FXML private Label scopeInfoLabel;
    @FXML private Button refreshBtn;
    @FXML private HBox errorBannerBox;
    @FXML private Label errorDetailLabel;
    @FXML private Button retryBtn;
    @FXML private HBox initialLoadingBox;
    @FXML private HBox emptyDashboardBox;
    @FXML private Label emptyDashboardSubLabel;

    // Section B: Overview KPIs
    @FXML private Label kpiTotalJobsValue;
    @FXML private Label kpiJobStatusBreakdownLabel;
    @FXML private Label kpiOpenAcceptingValue;
    @FXML private Label kpiOpenContextLabel;
    @FXML private Label kpiExpiredWarningLabel;
    @FXML private Label kpiJobsCreatedValue;
    @FXML private Label kpiCompanyProfileLabel;
    @FXML private Label kpiZeroApplicantValue;
    @FXML private Label kpiZeroApplicantSubLabel;
    @FXML private Label kpiPeriodAppsValue;
    @FXML private Label kpiPeriodAppsSubLabel;
    @FXML private Label kpiLifetimePendingValue;
    @FXML private Label kpiPeriodPendingSubLabel;
    @FXML private Label kpiShortlistedValue;
    @FXML private Label kpiShortlistRateSubLabel;
    @FXML private Label kpiSelectedValue;
    @FXML private Label kpiSelectionRateSubLabel;

    // Section C: Pipeline & Status Distribution
    @FXML private Label pipelineDistSubLabel;
    @FXML private PieChart statusPieChart;
    @FXML private VBox pipelineEmptyBox;
    @FXML private VBox statusPillListBox;
    @FXML private Label pipelineFunnelSubLabel;
    @FXML private Label funnelReviewedCountLabel;
    @FXML private ProgressBar funnelReviewedBar;
    @FXML private Label funnelPendingCountLabel;
    @FXML private ProgressBar funnelPendingBar;
    @FXML private Label funnelShortlistedCountLabel;
    @FXML private ProgressBar funnelShortlistedBar;
    @FXML private Label funnelSelectedCountLabel;
    @FXML private ProgressBar funnelSelectedBar;
    @FXML private Label funnelRejectedCountLabel;
    @FXML private ProgressBar funnelRejectedBar;
    @FXML private Label rateReviewLabel;
    @FXML private Label rateReviewHintLabel;
    @FXML private Label rateShortlistLabel;
    @FXML private Label rateShortlistHintLabel;
    @FXML private Label rateSelectionLabel;
    @FXML private Label rateSelectionHintLabel;
    @FXML private Label rateRejectionLabel;
    @FXML private Label rateRejectionHintLabel;
    @FXML private Label rateAdvancementLabel;
    @FXML private Label rateAdvancementHintLabel;
    @FXML private Label rateUniqueHireLabel;
    @FXML private Label rateUniqueHireHintLabel;

    // Section E: Application Trends
    @FXML private Label trendSubLabel;
    @FXML private Label trendHeaderGranularityLabel;
    @FXML private LineChart<String, Number> applicationTrendChart;
    @FXML private CategoryAxis trendXAxis;
    @FXML private NumberAxis trendYAxis;
    @FXML private VBox trendEmptyBox;

    // Section F: Job Type Distribution
    @FXML private Label jobTypeSubLabel;
    @FXML private BarChart<String, Number> jobTypeBarChart;
    @FXML private CategoryAxis jobTypeXAxis;
    @FXML private NumberAxis jobTypeYAxis;
    @FXML private VBox jobTypeEmptyBox;
    @FXML private VBox jobTypeSummaryListBox;

    // Section D: Job Performance Table
    @FXML private Label jobPerformanceCountLabel;
    @FXML private TableView<RecruiterJobPerformance> jobPerformanceTable;
    @FXML private TableColumn<RecruiterJobPerformance, String> colPerfTitle;
    @FXML private TableColumn<RecruiterJobPerformance, String> colPerfType;
    @FXML private TableColumn<RecruiterJobPerformance, String> colPerfStatus;
    @FXML private TableColumn<RecruiterJobPerformance, String> colPerfDeadline;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfPeriodApps;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfPending;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfShortlisted;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfSelected;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfRejected;
    @FXML private TableColumn<RecruiterJobPerformance, Integer> colPerfLifetime;
    @FXML private TableColumn<RecruiterJobPerformance, String> colPerfRates;
    @FXML private TableColumn<RecruiterJobPerformance, Void> colPerfAction;
    @FXML private VBox jobPerformanceEmptyBox;

    // Section H & G: Pending Applicants & Recent Hiring Activity
    @FXML private Label pendingQueueBadge;
    @FXML private VBox pendingApplicantsBox;
    @FXML private VBox pendingEmptyBox;
    @FXML private Label recentActivityCountLabel;
    @FXML private VBox recentActivityBox;
    @FXML private VBox activityEmptyBox;

    // Section I: Applicant Readiness
    @FXML private Label readinessPeriodLabel;
    @FXML private VBox readinessUnavailableBox;
    @FXML private Label readinessUnavailableLabel;
    @FXML private GridPane readinessContentGrid;
    @FXML private Label readinessProfileValueLabel;
    @FXML private ProgressBar readinessProfileBar;
    @FXML private Label readinessActiveResumeValueLabel;
    @FXML private ProgressBar readinessActiveResumeBar;
    @FXML private Label readinessAnyResumeValueLabel;
    @FXML private ProgressBar readinessAnyResumeBar;
    @FXML private Label readinessAiCovValueLabel;
    @FXML private ProgressBar readinessAiCovBar;
    @FXML private Label readinessAvgAiScoreLabel;
    @FXML private Label readinessAvgAiSubLabel;
    @FXML private HBox readinessDisclaimerBox;
    @FXML private Label readinessAdvisoryLabel;

    // Company Profile Controls (Preserved)
    @FXML private TextField companyNameField;
    @FXML private TextField industryField;
    @FXML private TextField companyWebsiteField;
    @FXML private TextArea companyDescField;
    @FXML private Button saveProfileBtn;
    @FXML private ProgressIndicator profileLoading;
    @FXML private Label profileStatusLabel;

    // Recruiter Verification UI Elements (Phase 6 Step 4.2D)
    @FXML private VBox verificationCard;
    @FXML private Label verificationStatusBadge;
    @FXML private Label verificationMessageLabel;
    @FXML private Button verificationActionButton;
    @FXML private VBox verificationFeedbackBox;
    @FXML private Label verificationReasonLabel;
    @FXML private HBox verificationTimelineBox;
    @FXML private Label timelineCreatedLabel;
    @FXML private Label timelineUpdatedLabel;
    @FXML private Label timelineReviewedLabel;

    @FXML private VBox profileVerificationBanner;
    @FXML private Label profileStatusBadge;
    @FXML private Label profileVerificationMsgLabel;
    @FXML private VBox profileFeedbackBox;
    @FXML private Label profileReasonLabel;
    @FXML private Button submitVerificationBtn;

    @FXML private HBox jobsVerificationWarningBanner;
    @FXML private Label jobsVerificationWarningLabel;

    // Manage Jobs Table & Actions (Preserved)
    @FXML private TableView<Job> jobTable;
    @FXML private TableColumn<Job, String> colTitle;
    @FXML private TableColumn<Job, String> colType;
    @FXML private TableColumn<Job, String> colLocation;
    @FXML private TableColumn<Job, String> colStatus;
    @FXML private TableColumn<Job, String> colDeadline;
    @FXML private TableColumn<Job, Integer> colApplicants;
    @FXML private TableColumn<Job, Void> colActions;
    @FXML private Label jobTableStatus;
    @FXML private Button editJobBtn;
    @FXML private Button closeJobBtn;
    @FXML private Button deleteJobBtn;
    @FXML private Button viewApplicantsBtn;

    // Services
    private final RecruiterAnalyticsService analyticsService;
    private final RecruiterService recruiterService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final AuthService authService;

    // State & Threading Guards
    private final ObservableList<Job> jobsList = FXCollections.observableArrayList();
    private final ObservableList<RecruiterJobPerformance> jobPerformanceList = FXCollections.observableArrayList();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final AtomicLong activeRequestSeq = new AtomicLong(0L);
    private final Map<Integer, Integer> jobAppCountMap = new HashMap<>();
    private final Map<String, String> activePieLegendColors = new HashMap<>();

    private volatile Task<?> activeAnalyticsTask;
    private volatile Task<?> activeJobLookupTask;
    private FilterPreset activePreset = FilterPreset.ALL_TIME;
    private RecruiterDashboardSnapshot lastValidSnapshot;
    private RecruiterProfile currentProfile;
    private boolean redirectScheduled = false;

    public RecruiterDashboardController() {
        this(new RecruiterAnalyticsService(), new RecruiterService(),
                new JobService(), new ApplicationService(), new AuthService());
    }

    public RecruiterDashboardController(RecruiterAnalyticsService analyticsService) {
        this(analyticsService, new RecruiterService(),
                new JobService(), new ApplicationService(), new AuthService());
    }

    public RecruiterDashboardController(RecruiterAnalyticsService analyticsService,
                                        RecruiterService recruiterService,
                                        JobService jobService,
                                        ApplicationService applicationService,
                                        AuthService authService) {
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
        this.recruiterService = Objects.requireNonNull(recruiterService, "recruiterService must not be null");
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
    }

    @FXML
    public void initialize() {
        if (!ensureRecruiterAuthorized()) {
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        welcomeLabel.setText("Welcome back, " + safeText(user.getFullName(), "Recruiter"));
        if (recruiterInitialsLabel != null) {
            recruiterInitialsLabel.setText(extractInitials(user.getFullName(), "RC"));
        }
        if (user.getCreatedAt() != null) {
            memberSinceLabel.setText("Member since " + user.getCreatedAt().format(MEMBER_MONTH_FORMAT));
        }

        configureIntegerAxis(trendYAxis);
        configureIntegerAxis(jobTypeYAxis);
        if (statusPieChart != null) {
            statusPieChart.needsLayoutProperty().addListener(
                    (obs, was, now) -> syncPieLegendColors(activePieLegendColors));
        }
        setupJobPerformanceTable();
        setupJobTable();
        updatePresetButtonStyles(activePreset);
        readinessAdvisoryLabel.setText(RecruiterApplicantReadiness.DEFAULT_ADVISORY_DISCLAIMER);
        updateVerificationUI(null);
        updateActiveNav(navDashboardBtn);

        loadProfileAsync();
        loadAnalyticsAsync(false);
    }

    // =========================================================================
    // Navigation Handlers
    // =========================================================================

    @FXML
    public void showDashboard() {
        if (!ensureRecruiterAuthorized()) return;
        switchPane(dashboardPane, navDashboardBtn);
        loadAnalyticsAsync(false);
    }

    @FXML
    public void showProfile() {
        if (!ensureRecruiterAuthorized()) return;
        switchPane(profilePane, navProfileBtn);
        hideStatus(profileStatusLabel);
        loadProfileAsync();
    }

    @FXML
    public void showJobs() {
        if (!ensureRecruiterAuthorized()) return;
        switchPane(jobsPane, navJobsBtn);
        hideStatus(jobTableStatus);
        loadJobsAsync();
    }

    @FXML
    public void handlePostJob() {
        if (!ensureRecruiterAuthorized()) return;
        RecruiterVerificationStatus status = (currentProfile != null && currentProfile.getVerificationStatus() != null)
                ? currentProfile.getVerificationStatus()
                : RecruiterVerificationStatus.UNVERIFIED;

        if (status != RecruiterVerificationStatus.VERIFIED) {
            showPublishingRestrictedAlert(status);
            return;
        }
        updateActiveNav(navPostJobBtn);
        openJobForm(null);
    }

    @FXML
    public void handleVerificationAction() {
        if (!ensureRecruiterAuthorized()) return;
        RecruiterVerificationStatus status = (currentProfile != null && currentProfile.getVerificationStatus() != null)
                ? currentProfile.getVerificationStatus()
                : RecruiterVerificationStatus.UNVERIFIED;

        if (status == RecruiterVerificationStatus.VERIFIED) {
            openJobForm(null);
        } else {
            showProfile();
        }
    }

    @FXML
    public void handleLogout() {
        cancelAllBackgroundTasks();
        authService.logout();
        NavigationManager.navigateTo("/fxml/login.fxml");
    }

    // =========================================================================
    // Filter & Refresh Handlers (Section A)
    // =========================================================================

    @FXML
    public void handlePreset30() {
        selectPreset(FilterPreset.DAYS_30);
    }

    @FXML
    public void handlePreset60() {
        selectPreset(FilterPreset.DAYS_60);
    }

    @FXML
    public void handlePreset200() {
        selectPreset(FilterPreset.DAYS_200);
    }

    @FXML
    public void handlePresetAllTime() {
        selectPreset(FilterPreset.ALL_TIME);
    }

    @FXML
    public void handleRefresh() {
        if (!ensureRecruiterAuthorized()) return;
        loadAnalyticsAsync(false);
    }

    @FXML
    public void handleRetry() {
        if (!ensureRecruiterAuthorized()) return;
        hideBanner(errorBannerBox);
        loadAnalyticsAsync(false);
    }

    public FilterPreset getActivePreset() {
        return activePreset;
    }

    public boolean isRefreshInFlight() {
        return isRefreshing.get();
    }

    public RecruiterDashboardSnapshot getLastValidSnapshot() {
        return lastValidSnapshot;
    }

    private void selectPreset(FilterPreset newPreset) {
        if (!ensureRecruiterAuthorized()) return;
        boolean changed = this.activePreset != newPreset;
        this.activePreset = newPreset;
        updatePresetButtonStyles(newPreset);
        if (changed) {
            loadAnalyticsAsync(true);
        } else {
            loadAnalyticsAsync(false);
        }
    }

    private void updatePresetButtonStyles(FilterPreset preset) {
        ToggleButton[] buttons = {preset30Btn, preset60Btn, preset200Btn, presetAllBtn};
        FilterPreset[] presets = {
                FilterPreset.DAYS_30, FilterPreset.DAYS_60, FilterPreset.DAYS_200, FilterPreset.ALL_TIME
        };
        for (int i = 0; i < buttons.length; i++) {
            ToggleButton btn = buttons[i];
            if (btn == null) continue;
            boolean selected = presets[i] == preset;
            btn.setSelected(selected);
            btn.getStyleClass().remove("rec-preset-btn-active");
            if (selected) {
                btn.getStyleClass().add("rec-preset-btn-active");
            }
        }
    }

    // =========================================================================
    // Async Analytics Loading & Thread-Safety
    // =========================================================================

    private void loadAnalyticsAsync(boolean replaceInFlight) {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        if (recruiterId <= 0) return;

        if (!replaceInFlight) {
            if (!isRefreshing.compareAndSet(false, true)) {
                return;
            }
        } else {
            Task<?> prior = activeAnalyticsTask;
            if (prior != null && !prior.isDone()) {
                prior.cancel(true);
            }
            isRefreshing.set(true);
        }

        final long seq = activeRequestSeq.incrementAndGet();
        final FilterPreset requestedPreset = this.activePreset;

        setLoadingUi(true, lastValidSnapshot == null);
        hideBanner(errorBannerBox);

        Task<RecruiterDashboardSnapshot> task = new Task<>() {
            @Override
            protected RecruiterDashboardSnapshot call() {
                if (isCancelled()) {
                    return null;
                }
                return fetchSnapshotForPreset(recruiterId, requestedPreset);
            }
        };
        activeAnalyticsTask = task;

        task.setOnSucceeded(e -> {
            if (seq != activeRequestSeq.get() || task.isCancelled() || !isSessionMatchingRecruiter(recruiterId)) {
                return;
            }
            isRefreshing.set(false);
            setLoadingUi(false, false);
            RecruiterDashboardSnapshot snapshot = task.getValue();
            if (snapshot == null) {
                handleAnalyticsLoadFailure("Analytics backend returned an empty response.");
                return;
            }
            if (snapshot.hasError()) {
                handleAnalyticsLoadFailure(snapshot.getLoadError());
                return;
            }
            this.lastValidSnapshot = snapshot;
            renderSnapshot(snapshot);
        });

        task.setOnFailed(e -> {
            if (seq != activeRequestSeq.get() || task.isCancelled() || !isSessionMatchingRecruiter(recruiterId)) {
                return;
            }
            isRefreshing.set(false);
            setLoadingUi(false, false);
            Throwable ex = task.getException();
            String msg = ex != null && hasText(ex.getMessage())
                    ? ex.getMessage()
                    : "Database or analytics error occurred while loading dashboard data.";
            handleAnalyticsLoadFailure(msg);
        });

        task.setOnCancelled(e -> {
            if (seq != activeRequestSeq.get()) {
                return;
            }
            isRefreshing.set(false);
            setLoadingUi(false, false);
        });

        Thread worker = new Thread(task, "recruiter-analytics-loader-" + seq);
        worker.setDaemon(true);
        worker.start();
    }

    private RecruiterDashboardSnapshot fetchSnapshotForPreset(int recruiterId, FilterPreset preset) {
        return switch (preset) {
            case DAYS_30 -> analyticsService.loadLastNDaysSnapshot(recruiterId, 30);
            case DAYS_60 -> analyticsService.loadLastNDaysSnapshot(recruiterId, 60);
            case DAYS_200 -> analyticsService.loadLastNDaysSnapshot(recruiterId, 200);
            case ALL_TIME -> analyticsService.loadAllTimeSnapshot(recruiterId);
        };
    }

    private void handleAnalyticsLoadFailure(String errorMessage) {
        String safeDetail = hasText(errorMessage)
                ? errorMessage.trim()
                : "Could not load analytics from the database.";
        if (lastValidSnapshot != null) {
            String punctuated = safeDetail.endsWith(".") || safeDetail.endsWith("!") ? safeDetail : safeDetail + ".";
            errorDetailLabel.setText(punctuated + " Showing previously loaded data.");
        } else {
            errorDetailLabel.setText(safeDetail);
            lastRefreshedLabel.setText("Load failed — click Retry");
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

    // =========================================================================
    // Snapshot Rendering (Sections A - I)
    // =========================================================================

    private void renderSnapshot(RecruiterDashboardSnapshot snapshot) {
        hideBanner(errorBannerBox);
        hideBanner(initialLoadingBox);

        renderHeaderAndFilters(snapshot);
        RecruiterOverviewKPI overview = snapshot.getOverviewKPI();
        boolean isEmptyPortfolio = overview == null
                || (overview.getTotalJobs() == 0
                && overview.getLifetimeTotalApplications() == 0
                && overview.getPeriodTotalApplications() == 0);
        if (emptyDashboardBox != null) {
            emptyDashboardBox.setVisible(isEmptyPortfolio);
            emptyDashboardBox.setManaged(isEmptyPortfolio);
        }

        renderOverviewKpis(snapshot);
        renderPipelineSection(snapshot);
        renderApplicationTrends(snapshot);
        renderJobTypeDistribution(snapshot);
        renderJobPerformance(snapshot);
        renderPendingApplicants(snapshot);
        renderRecentActivity(snapshot);
        renderApplicantReadiness(snapshot);
    }

    private void renderHeaderAndFilters(RecruiterDashboardSnapshot snapshot) {
        RecruiterOverviewKPI overview = snapshot.getOverviewKPI();
        if (overview != null) {
            if (hasText(overview.getRecruiterName())) {
                welcomeLabel.setText("Welcome back, " + overview.getRecruiterName());
            }
            if (hasText(overview.getCompanyName())) {
                String industryText = hasText(overview.getIndustry()) ? " • " + overview.getIndustry() : "";
                companyNameHeaderLabel.setText(overview.getCompanyName() + industryText);
            } else {
                companyNameHeaderLabel.setText("Company Profile: Pending setup");
            }
            updateHeaderProfileStatusPill(currentProfile);
        }

        String rangeLabel = hasText(snapshot.getDateRangeLabel())
                ? snapshot.getDateRangeLabel()
                : activePreset.getDisplayLabel();
        reportingPeriodLabel.setText(rangeLabel);

        String granularityLabel = formatGranularity(snapshot.getTrendGranularity());
        trendGranularityBadge.setText(granularityLabel);
        trendHeaderGranularityLabel.setText(granularityLabel);

        LocalDateTime refreshed = snapshot.getRefreshedAt() != null
                ? snapshot.getRefreshedAt()
                : LocalDateTime.now();
        lastRefreshedLabel.setText("Last updated: " + refreshed.format(DATETIME_FORMAT));
    }

    private void renderOverviewKpis(RecruiterDashboardSnapshot snapshot) {
        RecruiterOverviewKPI k = snapshot.getOverviewKPI();
        if (k == null) {
            return;
        }
        kpiTotalJobsValue.setText(String.valueOf(k.getTotalJobs()));
        kpiJobStatusBreakdownLabel.setText(String.format(
                "%d Active • %d Draft • %d Closed",
                k.getActiveStatusJobs(), k.getDraftJobs(), k.getClosedJobs()));

        kpiOpenAcceptingValue.setText(String.valueOf(k.getOpenAcceptingJobs()));
        String closingNote = k.getClosingWithin7DaysJobs() > 0
                ? String.format("%d closing within 7 days", k.getClosingWithin7DaysJobs())
                : "Accepting new applicants";
        kpiOpenContextLabel.setText(closingNote);

        if (k.getExpiredActiveJobs() > 0) {
            kpiExpiredWarningLabel.setText(k.getExpiredActiveJobs() + " expired ACTIVE");
            kpiExpiredWarningLabel.setVisible(true);
            kpiExpiredWarningLabel.setManaged(true);
        } else {
            kpiExpiredWarningLabel.setVisible(false);
            kpiExpiredWarningLabel.setManaged(false);
        }

        kpiJobsCreatedValue.setText(String.valueOf(k.getJobsCreatedInPeriod()));
        String companyText = hasText(k.getCompanyName()) ? k.getCompanyName() : "Company not set";
        kpiCompanyProfileLabel.setText(companyText
                + (k.isProfileCompleted() ? " (Complete)" : String.format(" (%.0f%% setup)", k.getProfileCompletenessRate())));

        kpiZeroApplicantValue.setText(String.valueOf(k.getZeroApplicantOpenJobs()));
        kpiZeroApplicantSubLabel.setText(k.getZeroApplicantOpenJobs() > 0
                ? "Open jobs with zero applicants"
                : "All open listings have applicants");

        kpiPeriodAppsValue.setText(String.valueOf(k.getPeriodTotalApplications()));
        kpiPeriodAppsSubLabel.setText(String.format("%d lifetime total applications", k.getLifetimeTotalApplications()));

        kpiLifetimePendingValue.setText(String.valueOf(k.getLifetimePendingApplications()));
        kpiPeriodPendingSubLabel.setText(String.format("%d submitted in selected period", k.getPeriodPendingApplications()));

        RecruiterPipelineKPI pipe = snapshot.getPipelineKPI();
        int periodTotal = k.getPeriodTotalApplications();
        kpiShortlistedValue.setText(String.valueOf(k.getPeriodShortlistedApplications()));
        kpiShortlistRateSubLabel.setText(periodTotal > 0 && pipe != null
                ? String.format("%.1f%% shortlist rate", pipe.getShortlistRate())
                : "No applications in period");

        kpiSelectedValue.setText(String.valueOf(k.getPeriodSelectedApplications()));
        kpiSelectionRateSubLabel.setText(periodTotal > 0 && pipe != null
                ? String.format("%.1f%% selection rate (%d unique)", pipe.getSelectionRate(), pipe.getUniqueSelectedApplicants())
                : "No selections in period");
    }

    private void renderPipelineSection(RecruiterDashboardSnapshot snapshot) {
        RecruiterPipelineKPI pipe = snapshot.getPipelineKPI();
        List<RecruiterStatusDistribution> distributions = snapshot.getStatusDistribution() != null
                ? snapshot.getStatusDistribution()
                : Collections.emptyList();

        int total = pipe != null ? pipe.getTotalApplications() : 0;
        int unique = pipe != null ? pipe.getUniqueApplicants() : 0;
        pipelineDistSubLabel.setText(String.format("%d applications • %d unique applicants", total, unique));
        pipelineFunnelSubLabel.setText(total > 0
                ? String.format("Across %d jobs receiving applications", pipe.getJobsWithApplications())
                : "Conversion ratios show N/A until applications exist");

        // PieChart
        statusPieChart.getData().clear();
        statusPillListBox.getChildren().clear();

        if (total <= 0) {
            statusPieChart.setVisible(false);
            statusPieChart.setManaged(false);
            pipelineEmptyBox.setVisible(true);
            pipelineEmptyBox.setManaged(true);
        } else {
            pipelineEmptyBox.setVisible(false);
            pipelineEmptyBox.setManaged(false);
            statusPieChart.setVisible(true);
            statusPieChart.setManaged(true);

            List<PieChart.Data> slices = new ArrayList<>();
            for (RecruiterStatusDistribution dist : distributions) {
                if (dist.getApplicationCount() > 0) {
                    String label = formatStatusName(dist.getStatus()) + " (" + dist.getApplicationCount() + ")";
                    slices.add(new PieChart.Data(label, dist.getApplicationCount()));
                }
            }
            statusPieChart.setData(FXCollections.observableArrayList(slices));
            applyPieSliceColors(distributions);
        }

        for (RecruiterStatusDistribution dist : distributions) {
            statusPillListBox.getChildren().add(buildStatusPillRow(dist, total));
        }

        // Funnel Bars & Conversion Rates
        int pending = pipe != null ? pipe.getPendingApplications() : 0;
        int reviewed = pipe != null ? pipe.getReviewedApplications() : 0;
        int shortlisted = pipe != null ? pipe.getShortlistedApplications() : 0;
        int selected = pipe != null ? pipe.getSelectedApplications() : 0;
        int rejected = pipe != null ? pipe.getRejectedApplications() : 0;

        funnelReviewedCountLabel.setText(reviewed + " / " + total);
        funnelReviewedBar.setProgress(safeRatio(reviewed, total));

        funnelPendingCountLabel.setText(String.valueOf(pending));
        funnelPendingBar.setProgress(safeRatio(pending, total));

        funnelShortlistedCountLabel.setText(String.valueOf(shortlisted));
        funnelShortlistedBar.setProgress(safeRatio(shortlisted, total));

        funnelSelectedCountLabel.setText(String.valueOf(selected));
        funnelSelectedBar.setProgress(safeRatio(selected, total));

        funnelRejectedCountLabel.setText(String.valueOf(rejected));
        funnelRejectedBar.setProgress(safeRatio(rejected, total));

        rateReviewLabel.setText(formatRateOrNA(pipe != null ? pipe.getReviewCompletionRate() : 0.0, total));
        rateReviewHintLabel.setText(total > 0 ? reviewed + " / " + total + " apps" : "No apps in window");

        rateShortlistLabel.setText(formatRateOrNA(pipe != null ? pipe.getShortlistRate() : 0.0, total));
        rateShortlistHintLabel.setText(total > 0 ? shortlisted + " / " + total + " apps" : "No apps in window");

        rateSelectionLabel.setText(formatRateOrNA(pipe != null ? pipe.getSelectionRate() : 0.0, total));
        rateSelectionHintLabel.setText(total > 0 ? selected + " / " + total + " apps" : "No apps in window");

        rateRejectionLabel.setText(formatRateOrNA(pipe != null ? pipe.getRejectionRate() : 0.0, total));
        rateRejectionHintLabel.setText(total > 0 ? rejected + " / " + total + " apps" : "No apps in window");

        rateAdvancementLabel.setText(formatRateOrNA(pipe != null ? pipe.getAdvancementRate() : 0.0, total));
        rateAdvancementHintLabel.setText(total > 0 ? (shortlisted + selected) + " / " + total + " apps" : "No apps in window");

        rateUniqueHireLabel.setText(formatRateOrNA(pipe != null ? pipe.getUniqueHireRate() : 0.0, unique));
        int uniqueSelected = pipe != null ? pipe.getUniqueSelectedApplicants() : 0;
        rateUniqueHireHintLabel.setText(unique > 0 ? uniqueSelected + " / " + unique + " candidates" : "No candidates");
    }

    private HBox buildStatusPillRow(RecruiterStatusDistribution dist, int periodTotal) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rec-status-pill-row");

        Label badge = new Label(formatStatusName(dist.getStatus()));
        badge.getStyleClass().add(statusBadgeClass(dist.getStatus()));
        badge.setMinWidth(Region.USE_PREF_SIZE);

        Label uniqueLabel = new Label(dist.getUniqueApplicants() + " unique");
        uniqueLabel.getStyleClass().add("rec-section-sub");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String pctText = periodTotal > 0 ? String.format("%.1f%%", dist.getPercentage()) : "—";
        Label countLabel = new Label(dist.getApplicationCount() + " (" + pctText + ")");
        countLabel.getStyleClass().add("rec-filter-label");
        countLabel.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(badge, uniqueLabel, spacer, countLabel);
        return row;
    }

    private void applyPieSliceColors(List<RecruiterStatusDistribution> distributions) {
        activePieLegendColors.clear();
        List<String> colors = new ArrayList<>();
        for (RecruiterStatusDistribution dist : distributions) {
            if (dist.getApplicationCount() > 0) {
                String hex = statusHexColor(dist.getStatus());
                colors.add(hex);
                String sliceLabel = formatStatusName(dist.getStatus()) + " (" + dist.getApplicationCount() + ")";
                activePieLegendColors.put(sliceLabel, hex);
            }
        }
        int idx = 0;
        for (PieChart.Data slice : statusPieChart.getData()) {
            if (idx < colors.size()) {
                String hex = colors.get(idx);
                if (slice.getNode() != null) {
                    slice.getNode().setStyle("-fx-pie-color: " + hex + ";");
                } else {
                    slice.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            newNode.setStyle("-fx-pie-color: " + hex + ";");
                        }
                    });
                }
            }
            idx++;
        }
        syncPieLegendColors(activePieLegendColors);
        Platform.runLater(() -> syncPieLegendColors(activePieLegendColors));
    }

    private void syncPieLegendColors(Map<String, String> nameToHex) {
        if (statusPieChart == null || nameToHex.isEmpty()) return;
        for (Node item : statusPieChart.lookupAll(".chart-legend-item")) {
            if (item instanceof Label lbl && lbl.getGraphic() != null) {
                String hex = nameToHex.get(lbl.getText());
                if (hex != null) {
                    lbl.getGraphic().setStyle("-fx-background-color: " + hex + "; -fx-pie-color: " + hex + ";");
                }
            }
        }
    }

    private void renderApplicationTrends(RecruiterDashboardSnapshot snapshot) {
        List<RecruiterTrendPoint> points = snapshot.getApplicationTrend() != null
                ? snapshot.getApplicationTrend()
                : Collections.emptyList();

        boolean hasAnyVolume = false;
        for (RecruiterTrendPoint pt : points) {
            if (pt.getApplicationsCount() > 0 || pt.getShortlistedCount() > 0
                    || pt.getSelectedCount() > 0 || pt.getRejectedCount() > 0) {
                hasAnyVolume = true;
                break;
            }
        }

        applicationTrendChart.getData().clear();
        if (points.isEmpty() || !hasAnyVolume) {
            applicationTrendChart.setVisible(false);
            applicationTrendChart.setManaged(false);
            trendEmptyBox.setVisible(true);
            trendEmptyBox.setManaged(true);
            trendSubLabel.setText(formatGranularity(snapshot.getTrendGranularity())
                    + " timeline • No applications submitted in this period");
            return;
        }

        trendEmptyBox.setVisible(false);
        trendEmptyBox.setManaged(false);
        applicationTrendChart.setVisible(true);
        applicationTrendChart.setManaged(true);
        trendSubLabel.setText(String.format("%s granularity • %d time buckets",
                formatGranularity(snapshot.getTrendGranularity()), points.size()));

        List<String> categories = new ArrayList<>(points.size());
        XYChart.Series<String, Number> appsSeries = new XYChart.Series<>();
        appsSeries.setName("Applications");

        XYChart.Series<String, Number> shortlistedSeries = new XYChart.Series<>();
        shortlistedSeries.setName("Shortlisted");

        XYChart.Series<String, Number> selectedSeries = new XYChart.Series<>();
        selectedSeries.setName("Selected");

        for (RecruiterTrendPoint pt : points) {
            String label = pt.getPeriodLabel();
            categories.add(label);
            appsSeries.getData().add(new XYChart.Data<>(label, pt.getApplicationsCount()));
            shortlistedSeries.getData().add(new XYChart.Data<>(label, pt.getShortlistedCount()));
            selectedSeries.getData().add(new XYChart.Data<>(label, pt.getSelectedCount()));
        }

        trendXAxis.getCategories().clear();
        trendXAxis.setCategories(FXCollections.observableArrayList(categories));
        applicationTrendChart.setData(FXCollections.observableArrayList(
                appsSeries, shortlistedSeries, selectedSeries));
    }

    private void renderJobTypeDistribution(RecruiterDashboardSnapshot snapshot) {
        List<RecruiterJobTypeStat> stats = snapshot.getJobTypeStats() != null
                ? snapshot.getJobTypeStats()
                : Collections.emptyList();

        // Preserve all canonical Job.JobType categories in stable display order even when 0
        Map<String, RecruiterJobTypeStat> byType = new HashMap<>();
        for (RecruiterJobTypeStat stat : stats) {
            if (stat != null && hasText(stat.getJobType())) {
                byType.put(stat.getJobType().trim().toUpperCase(Locale.ROOT), stat);
            }
        }

        int totalPortfolioJobs = 0;
        int totalPeriodApps = 0;
        jobTypeSummaryListBox.getChildren().clear();

        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Jobs");
        XYChart.Series<String, Number> openSeries = new XYChart.Series<>();
        openSeries.setName("Open Accepting");
        XYChart.Series<String, Number> appSeries = new XYChart.Series<>();
        appSeries.setName("Period Apps");

        List<String> typeCategories = new ArrayList<>();
        for (Job.JobType type : Job.JobType.values()) {
            RecruiterJobTypeStat s = byType.getOrDefault(type.name(),
                    new RecruiterJobTypeStat(type.name(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            String typeLabel = formatJobType(type.name());
            typeCategories.add(typeLabel);
            totalPortfolioJobs += s.getTotalJobs();
            totalPeriodApps += s.getPeriodApplications();

            totalSeries.getData().add(new XYChart.Data<>(typeLabel, s.getTotalJobs()));
            openSeries.getData().add(new XYChart.Data<>(typeLabel, s.getOpenAcceptingJobs()));
            appSeries.getData().add(new XYChart.Data<>(typeLabel, s.getPeriodApplications()));

            jobTypeSummaryListBox.getChildren().add(buildJobTypeSummaryRow(typeLabel, s));
        }

        jobTypeBarChart.getData().clear();
        if (totalPortfolioJobs == 0 && totalPeriodApps == 0) {
            jobTypeBarChart.setVisible(false);
            jobTypeBarChart.setManaged(false);
            jobTypeEmptyBox.setVisible(true);
            jobTypeEmptyBox.setManaged(true);
            jobTypeSubLabel.setText("No job postings yet across canonical types");
        } else {
            jobTypeEmptyBox.setVisible(false);
            jobTypeEmptyBox.setManaged(false);
            jobTypeBarChart.setVisible(true);
            jobTypeBarChart.setManaged(true);
            jobTypeSubLabel.setText(String.format("%d total jobs • %d period applications",
                    totalPortfolioJobs, totalPeriodApps));
            jobTypeXAxis.getCategories().clear();
            jobTypeXAxis.setCategories(FXCollections.observableArrayList(typeCategories));
            jobTypeBarChart.setData(FXCollections.observableArrayList(
                    totalSeries, openSeries, appSeries));
        }
    }

    private HBox buildJobTypeSummaryRow(String typeLabel, RecruiterJobTypeStat stat) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rec-type-pill-card");

        Label title = new Label(typeLabel);
        title.getStyleClass().add("rec-filter-label");
        title.setMinWidth(82);

        String details = String.format("%d total (%d open", stat.getTotalJobs(), stat.getOpenAcceptingJobs())
                + (stat.getExpiredActiveJobs() > 0 ? ", " + stat.getExpiredActiveJobs() + " exp" : "")
                + String.format(", %d draft, %d closed)", stat.getDraftJobs(), stat.getClosedJobs());
        Label detailLabel = new Label(details);
        detailLabel.getStyleClass().add("rec-section-sub");
        detailLabel.setWrapText(true);
        HBox.setHgrow(detailLabel, Priority.ALWAYS);

        Label appsPill = new Label(stat.getPeriodApplications() + " apps");
        appsPill.getStyleClass().add(stat.getPeriodApplications() > 0 ? "rec-badge-shortlisted" : "rec-badge-pending");
        appsPill.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(title, detailLabel, appsPill);
        return row;
    }

    private void renderJobPerformance(RecruiterDashboardSnapshot snapshot) {
        List<RecruiterJobPerformance> perfList = snapshot.getJobPerformance() != null
                ? snapshot.getJobPerformance()
                : Collections.emptyList();
        jobPerformanceList.setAll(perfList);
        jobPerformanceCountLabel.setText(perfList.size() + (perfList.size() == 1 ? " job" : " jobs"));

        if (perfList.isEmpty()) {
            jobPerformanceTable.setVisible(false);
            jobPerformanceTable.setManaged(false);
            jobPerformanceEmptyBox.setVisible(true);
            jobPerformanceEmptyBox.setManaged(true);
        } else {
            jobPerformanceEmptyBox.setVisible(false);
            jobPerformanceEmptyBox.setManaged(false);
            jobPerformanceTable.setVisible(true);
            jobPerformanceTable.setManaged(true);
            updatePerformanceTableHeight(perfList.size());
        }
    }

    private void renderPendingApplicants(RecruiterDashboardSnapshot snapshot) {
        List<RecruiterPendingApplicant> pending = snapshot.getPendingApplicants() != null
                ? snapshot.getPendingApplicants()
                : Collections.emptyList();

        pendingApplicantsBox.getChildren().clear();
        pendingQueueBadge.setText(pending.size() + " pending");

        if (pending.isEmpty()) {
            pendingEmptyBox.setVisible(true);
            pendingEmptyBox.setManaged(true);
            return;
        }
        pendingEmptyBox.setVisible(false);
        pendingEmptyBox.setManaged(false);

        for (RecruiterPendingApplicant item : pending) {
            pendingApplicantsBox.getChildren().add(buildPendingApplicantRow(item));
        }
    }

    private HBox buildPendingApplicantRow(RecruiterPendingApplicant item) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rec-pending-row");

        VBox infoCol = new VBox(3);
        HBox.setHgrow(infoCol, Priority.ALWAYS);

        HBox topLine = new HBox(8);
        topLine.setAlignment(Pos.CENTER_LEFT);
        // Privacy boundary: strictly use DTO-exposed applicantName/college/degree/jobTitle/jobType/appliedAt/daysPending
        Label nameLabel = new Label(safeText(item.getApplicantName(), "Applicant"));
        nameLabel.getStyleClass().add("rec-filter-label");

        StringBuilder academic = new StringBuilder();
        if (hasText(item.getDegree())) {
            academic.append(item.getDegree().trim());
        }
        if (hasText(item.getCollege())) {
            if (!academic.isEmpty()) academic.append(" • ");
            academic.append(item.getCollege().trim());
        }
        topLine.getChildren().add(nameLabel);
        if (!academic.isEmpty()) {
            Label academicLabel = new Label(academic.toString());
            academicLabel.getStyleClass().add("rec-section-sub");
            topLine.getChildren().add(academicLabel);
        }

        String jobMeta = safeText(item.getJobTitle(), "Job #" + item.getJobId())
                + (item.getJobType() != null ? " • " + formatJobType(item.getJobType()) : "");
        Label jobLabel = new Label(jobMeta);
        jobLabel.getStyleClass().add("rec-section-sub");

        String appliedText = item.getAppliedAt() != null
                ? "Applied " + item.getAppliedAt().format(DATETIME_FORMAT)
                : "Applied date unavailable";
        Label appliedLabel = new Label(appliedText);
        appliedLabel.getStyleClass().add("rec-rate-hint");

        infoCol.getChildren().addAll(topLine, jobLabel, appliedLabel);

        long days = Math.max(0L, item.getDaysPending());
        Label daysPill = new Label(days == 0 ? "Today" : days + "d pending");
        daysPill.getStyleClass().add(days >= 7 ? "rec-badge-expired" : "rec-badge-shortlisted");
        daysPill.setMinWidth(Region.USE_PREF_SIZE);

        Button reviewBtn = new Button("Review");
        reviewBtn.getStyleClass().add("rec-row-action-btn");
        reviewBtn.setMinWidth(Region.USE_PREF_SIZE);
        reviewBtn.setOnAction(e -> handleOpenApplicantsForJobId(item.getJobId()));

        row.getChildren().addAll(infoCol, daysPill, reviewBtn);
        return row;
    }

    private void renderRecentActivity(RecruiterDashboardSnapshot snapshot) {
        List<RecruiterActivityEvent> events = snapshot.getRecentActivity() != null
                ? snapshot.getRecentActivity()
                : Collections.emptyList();

        recentActivityBox.getChildren().clear();
        recentActivityCountLabel.setText(events.size() + (events.size() == 1 ? " event" : " events"));

        if (events.isEmpty()) {
            activityEmptyBox.setVisible(true);
            activityEmptyBox.setManaged(true);
            return;
        }
        activityEmptyBox.setVisible(false);
        activityEmptyBox.setManaged(false);

        for (RecruiterActivityEvent ev : events) {
            recentActivityBox.getChildren().add(buildActivityRow(ev));
        }
    }

    private HBox buildActivityRow(RecruiterActivityEvent ev) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("rec-feed-row");

        Label typeBadge = new Label(formatActivityType(ev.getEventType()));
        typeBadge.getStyleClass().add(activityBadgeClass(ev.getEventType()));
        typeBadge.setMinWidth(Region.USE_PREF_SIZE);

        VBox textCol = new VBox(2);
        HBox.setHgrow(textCol, Priority.ALWAYS);
        Label summary = new Label(safeText(ev.getSummary(), formatActivityType(ev.getEventType())));
        summary.getStyleClass().add("rec-filter-label");
        summary.setWrapText(true);
        textCol.getChildren().add(summary);

        if (hasText(ev.getJobTitle())) {
            Label jobRef = new Label(ev.getJobTitle());
            jobRef.getStyleClass().add("rec-section-sub");
            textCol.getChildren().add(jobRef);
        }

        String timeText = ev.getOccurredAt() != null
                ? ev.getOccurredAt().format(DATETIME_FORMAT)
                : "Timestamp unavailable";
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("rec-rate-hint");
        timeLabel.setMinWidth(Region.USE_PREF_SIZE);

        row.getChildren().addAll(typeBadge, textCol, timeLabel);
        return row;
    }

    private void renderApplicantReadiness(RecruiterDashboardSnapshot snapshot) {
        RecruiterApplicantReadiness r = snapshot.getApplicantReadiness();
        if (r == null) {
            readinessContentGrid.setVisible(false);
            readinessContentGrid.setManaged(false);
            readinessUnavailableBox.setVisible(true);
            readinessUnavailableBox.setManaged(true);
            readinessUnavailableLabel.setText("Applicant readiness statistics are not available.");
            readinessAdvisoryLabel.setText(RecruiterApplicantReadiness.DEFAULT_ADVISORY_DISCLAIMER);
            return;
        }

        String disclaimer = hasText(r.getAdvisoryDisclaimer())
                ? r.getAdvisoryDisclaimer()
                : RecruiterApplicantReadiness.DEFAULT_ADVISORY_DISCLAIMER;
        readinessAdvisoryLabel.setText(disclaimer);
        readinessDisclaimerBox.setVisible(true);
        readinessDisclaimerBox.setManaged(true);

        if (!r.isReadinessDataAvailable()) {
            readinessContentGrid.setVisible(false);
            readinessContentGrid.setManaged(false);
            readinessUnavailableBox.setVisible(true);
            readinessUnavailableBox.setManaged(true);
            readinessUnavailableLabel.setText(hasText(r.getUnavailabilityReason())
                    ? r.getUnavailabilityReason()
                    : "No applicants in the selected period to compute readiness coverage.");
            readinessAvgAiScoreLabel.setText("Not available");
            readinessAvgAiScoreLabel.getStyleClass().removeAll("rec-ai-score-value", "rec-ai-score-na");
            readinessAvgAiScoreLabel.getStyleClass().add("rec-ai-score-na");
            readinessPeriodLabel.setText("Readiness metrics populate when candidates apply in the active period.");
            return;
        }

        readinessUnavailableBox.setVisible(false);
        readinessUnavailableBox.setManaged(false);
        readinessContentGrid.setVisible(true);
        readinessContentGrid.setManaged(true);

        int uniqueApps = r.getUniqueApplicants();
        readinessPeriodLabel.setText(String.format(
                "Based on %d unique applicants across %d applications in scope",
                uniqueApps, r.getTotalApplicationsInPeriod()));

        readinessProfileBar.setProgress(clampProgress(r.getProfileCompletionRate()));
        readinessProfileValueLabel.setText(String.format(
                "%.1f%% (%d / %d)", r.getProfileCompletionRate(), r.getApplicantsWithCompleteProfile(), uniqueApps));

        readinessActiveResumeBar.setProgress(clampProgress(r.getActiveResumeCoverageRate()));
        readinessActiveResumeValueLabel.setText(String.format(
                "%.1f%% (%d / %d)", r.getActiveResumeCoverageRate(), r.getApplicantsWithActiveResume(), uniqueApps));

        readinessAnyResumeBar.setProgress(clampProgress(r.getAnyResumeCoverageRate()));
        readinessAnyResumeValueLabel.setText(String.format(
                "%.1f%% (%d / %d)", r.getAnyResumeCoverageRate(), r.getApplicantsWithAnyResume(), uniqueApps));

        readinessAiCovBar.setProgress(clampProgress(r.getJobAiCoverageRate()));
        readinessAiCovValueLabel.setText(String.format(
                "%.1f%% (%d / %d)", r.getJobAiCoverageRate(), r.getUniqueApplicantsWithJobAiScore(), uniqueApps));

        readinessAvgAiScoreLabel.getStyleClass().removeAll("rec-ai-score-value", "rec-ai-score-na");
        if (r.isAiAnalysisAvailable() && r.getAvgJobAiScore() != null) {
            readinessAvgAiScoreLabel.setText(String.format("%.1f / 100", r.getAvgJobAiScore()));
            readinessAvgAiScoreLabel.getStyleClass().add("rec-ai-score-value");
            readinessAvgAiSubLabel.setText(String.format(
                    "Across %d job-specific analyses (%d unique candidates evaluated)",
                    r.getApplicationsWithJobAiAnalysis(), r.getUniqueApplicantsWithJobAiScore()));
        } else {
            readinessAvgAiScoreLabel.setText("Not available");
            readinessAvgAiScoreLabel.getStyleClass().add("rec-ai-score-na");
            readinessAvgAiSubLabel.setText(hasText(r.getUnavailabilityReason())
                    ? r.getUnavailabilityReason()
                    : "No job-specific AI fit analyses recorded for this scope.");
        }
    }

    // =========================================================================
    // Section D Table Setup & Verified Drilldown
    // =========================================================================

    private void setupJobPerformanceTable() {
        jobPerformanceTable.setItems(jobPerformanceList);
        jobPerformanceTable.setFixedCellSize(PERF_TABLE_ROW_HEIGHT);

        colPerfTitle.setCellValueFactory(c -> new SimpleStringProperty(safeText(c.getValue().getTitle(), "Untitled Job")));
        colPerfType.setCellValueFactory(c -> new SimpleStringProperty(formatJobType(c.getValue().getJobType())));
        colPerfStatus.setCellValueFactory(c -> new SimpleStringProperty(safeText(c.getValue().getStatus(), "")));
        colPerfDeadline.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDeadline() != null ? c.getValue().getDeadline().format(DATE_FORMAT) : "—"));
        colPerfPeriodApps.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getPeriodApplications()).asObject());
        colPerfPending.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getPendingCount()).asObject());
        colPerfShortlisted.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getShortlistedCount()).asObject());
        colPerfSelected.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getSelectedCount()).asObject());
        colPerfRejected.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getRejectedCount()).asObject());
        colPerfLifetime.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getLifetimeApplications()).asObject());

        colPerfRates.setCellValueFactory(c -> {
            RecruiterJobPerformance p = c.getValue();
            if (p.getPeriodApplications() <= 0) {
                return new SimpleStringProperty("—");
            }
            return new SimpleStringProperty(String.format("%.0f%%/%.0f%%",
                    p.getShortlistRate(), p.getSelectionRate()));
        });
        colPerfRates.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                RecruiterJobPerformance perf = getTableRow() != null ? getTableRow().getItem() : null;
                if (perf != null && perf.getPeriodApplications() > 0) {
                    setTooltip(new Tooltip(String.format("Shortlist: %.1f%% | Selection: %.1f%%",
                            perf.getShortlistRate(), perf.getSelectionRate())));
                } else {
                    setTooltip(new Tooltip("No applications in selected reporting period"));
                }
            }
        });

        colPerfStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String statusStr, boolean empty) {
                super.updateItem(statusStr, empty);
                if (empty || statusStr == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                RecruiterJobPerformance perf = getTableRow() != null ? getTableRow().getItem() : null;
                boolean expiredActive = perf != null && perf.isExpiredActive();
                Label pill = new Label(expiredActive ? "ACTIVE (Expired)" : statusStr);
                if (expiredActive) {
                    pill.getStyleClass().add("rec-badge-expired");
                } else {
                    switch (statusStr) {
                        case "ACTIVE" -> pill.getStyleClass().add("rec-badge-selected");
                        case "DRAFT" -> pill.getStyleClass().add("rec-badge-pending");
                        default -> pill.getStyleClass().add("rec-badge-rejected");
                    }
                }
                setGraphic(pill);
                setText(null);
            }
        });

        colPerfAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Applicants");

            {
                btn.getStyleClass().add("rec-row-action-btn");
                btn.setOnAction(e -> {
                    RecruiterJobPerformance perf = getTableRow() != null ? getTableRow().getItem() : null;
                    if (perf != null) {
                        handleOpenApplicantsForJobId(perf.getJobId());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        jobPerformanceTable.setRowFactory(tv -> {
            TableRow<RecruiterJobPerformance> row = new TableRow<>() {
                @Override
                protected void updateItem(RecruiterJobPerformance item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("rec-row-expired");
                    if (!empty && item != null && item.isExpiredActive()) {
                        getStyleClass().add("rec-row-expired");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleOpenApplicantsForJobId(row.getItem().getJobId());
                }
            });
            return row;
        });
    }

    private void updatePerformanceTableHeight(int rowCount) {
        int visibleRows = Math.max(rowCount, 1);
        double height = PERF_TABLE_HEADER_HEIGHT + visibleRows * PERF_TABLE_ROW_HEIGHT + 6;
        jobPerformanceTable.setPrefHeight(height);
        jobPerformanceTable.setMinHeight(height);
        jobPerformanceTable.setMaxHeight(height);
    }

    /**
     * Resolves verified ownership of the target job from JobService before opening Applicant Management.
     */
    private void handleOpenApplicantsForJobId(int jobId) {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        if (recruiterId <= 0 || jobId <= 0) return;

        Task<?> prior = activeJobLookupTask;
        if (prior != null && !prior.isDone()) {
            prior.cancel(true);
        }

        Task<Optional<Job>> lookupTask = new Task<>() {
            @Override
            protected Optional<Job> call() {
                if (isCancelled()) return Optional.empty();
                return jobService.getJobByIdForRecruiter(jobId, recruiterId);
            }
        };
        activeJobLookupTask = lookupTask;

        lookupTask.setOnSucceeded(e -> {
            if (lookupTask.isCancelled() || !isSessionMatchingRecruiter(recruiterId)) {
                return;
            }
            Optional<Job> ownedJob = lookupTask.getValue();
            if (ownedJob.isPresent()) {
                openApplicantManagement(ownedJob.get());
            } else {
                errorDetailLabel.setText("Job #" + jobId + " is unavailable or no longer owned by this recruiter.");
                showBanner(errorBannerBox);
            }
        });

        lookupTask.setOnFailed(e -> {
            if (lookupTask.isCancelled() || !isSessionMatchingRecruiter(recruiterId)) {
                return;
            }
            Throwable ex = lookupTask.getException();
            errorDetailLabel.setText("Could not open applicant management: "
                    + (ex != null && hasText(ex.getMessage()) ? ex.getMessage() : "lookup failed."));
            showBanner(errorBannerBox);
        });

        Thread worker = new Thread(lookupTask, "recruiter-job-lookup-" + jobId);
        worker.setDaemon(true);
        worker.start();
    }

    // =========================================================================
    // Preserved Company Profile Flow
    // =========================================================================

    private void loadProfileAsync() {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        if (recruiterId <= 0) return;

        Task<Optional<RecruiterProfile>> task = new Task<>() {
            @Override
            protected Optional<RecruiterProfile> call() {
                return recruiterService.getProfile(recruiterId);
            }
        };

        task.setOnSucceeded(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            Optional<RecruiterProfile> opt = task.getValue();
            if (opt.isPresent()) {
                currentProfile = opt.get();
                if (companyNameField != null) {
                    companyNameField.setText(safeText(currentProfile.getCompanyName(), ""));
                    industryField.setText(safeText(currentProfile.getIndustry(), ""));
                    companyWebsiteField.setText(safeText(currentProfile.getCompanyWebsite(), ""));
                    companyDescField.setText(safeText(currentProfile.getCompanyDescription(), ""));
                }
                if (hasText(currentProfile.getCompanyName()) && companyNameHeaderLabel != null) {
                    String ind = hasText(currentProfile.getIndustry()) ? " • " + currentProfile.getIndustry() : "";
                    companyNameHeaderLabel.setText(currentProfile.getCompanyName() + ind);
                }
            } else {
                currentProfile = null;
            }
            updateVerificationUI(currentProfile);
        });

        Thread worker = new Thread(task, "recruiter-profile-loader");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void handleSaveProfile() {
        if (!ensureRecruiterAuthorized()) return;
        User user = SessionManager.getInstance().getCurrentUser();
        final int recruiterId = user.getId();
        String companyName = companyNameField.getText().trim();
        String website = companyWebsiteField.getText().trim();
        String description = companyDescField.getText().trim();
        String industry = industryField.getText().trim();
        if (companyName.isEmpty()) {
            showStatus(profileStatusLabel, "Company name is required.", true);
            return;
        }

        RecruiterProfile profile = currentProfile != null ? currentProfile : new RecruiterProfile();
        profile.setUserId(recruiterId);
        profile.setCompanyName(companyName);
        profile.setIndustry(industry);
        profile.setCompanyWebsite(website);
        profile.setCompanyDescription(description);

        setProfileLoading(true);
        Task<RecruiterService.ServiceResult> task = new Task<>() {
            @Override
            protected RecruiterService.ServiceResult call() {
                return recruiterService.saveProfile(recruiterId, companyName, website, description, industry);
            }
        };
        task.setOnSucceeded(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            setProfileLoading(false);
            RecruiterService.ServiceResult res = task.getValue();
            showStatus(profileStatusLabel, res.getMessage(), !res.isSuccess());
            if (res.isSuccess()) {
                loadProfileAsync();
                loadAnalyticsAsync(true);
            } else {
                updateVerificationUI(currentProfile);
            }
        });
        task.setOnFailed(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            setProfileLoading(false);
            showStatus(profileStatusLabel, "Failed to save company profile.", true);
            updateVerificationUI(currentProfile);
        });

        Thread worker = new Thread(task, "recruiter-profile-save");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void handleSubmitVerification() {
        if (!ensureRecruiterAuthorized()) return;
        User user = SessionManager.getInstance().getCurrentUser();
        final int recruiterId = user.getId();
        String companyName = companyNameField.getText() != null ? companyNameField.getText().trim() : "";
        String website = companyWebsiteField.getText() != null ? companyWebsiteField.getText().trim() : "";
        String description = companyDescField.getText() != null ? companyDescField.getText().trim() : "";
        String industry = industryField.getText() != null ? industryField.getText().trim() : "";
        if (companyName.isEmpty()) {
            showStatus(profileStatusLabel, "Company name is required.", true);
            return;
        }

        setProfileLoading(true);
        Task<RecruiterService.ServiceResult> task = new Task<>() {
            @Override
            protected RecruiterService.ServiceResult call() {
                return recruiterService.submitForVerification(recruiterId, companyName, website, description, industry);
            }
        };
        task.setOnSucceeded(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            setProfileLoading(false);
            RecruiterService.ServiceResult res = task.getValue();
            showStatus(profileStatusLabel, res.getMessage(), !res.isSuccess());
            if (res.isSuccess()) {
                loadProfileAsync();
                loadAnalyticsAsync(true);
            } else {
                updateVerificationUI(currentProfile);
            }
        });
        task.setOnFailed(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            setProfileLoading(false);
            showStatus(profileStatusLabel, "Failed to submit company profile for verification.", true);
            updateVerificationUI(currentProfile);
        });

        Thread worker = new Thread(task, "recruiter-profile-submit-verification");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Updates all verification UI badges, banners, messages, timelines, and button states.
     */
    public void updateVerificationUI(RecruiterProfile profile) {
        updateHeaderProfileStatusPill(profile);

        RecruiterVerificationStatus status = (profile != null && profile.getVerificationStatus() != null)
                ? profile.getVerificationStatus()
                : RecruiterVerificationStatus.UNVERIFIED;
        String reason = (profile != null) ? profile.getVerificationReason() : null;

        // 1. Overview Dashboard Verification Card
        if (verificationStatusBadge != null) {
            verificationStatusBadge.setText(status.label().toUpperCase(Locale.ROOT));
            verificationStatusBadge.getStyleClass().removeAll(
                    "rec-badge-unverified", "rec-badge-pending", "rec-badge-verified",
                    "rec-badge-rejected", "rec-badge-suspended"
            );
            verificationStatusBadge.getStyleClass().add(switch (status) {
                case VERIFIED -> "rec-badge-verified";
                case PENDING -> "rec-badge-pending";
                case REJECTED -> "rec-badge-rejected";
                case SUSPENDED -> "rec-badge-suspended";
                case UNVERIFIED -> "rec-badge-unverified";
            });
        }

        if (verificationMessageLabel != null) {
            verificationMessageLabel.setText(switch (status) {
                case UNVERIFIED -> "Your company profile has not been submitted for verification. Submit your company details to enable job posting.";
                case PENDING -> "Your verification request is currently under review by the HireHub administration team.";
                case VERIFIED -> "Your recruiter account is verified. You have full access to publish jobs and manage applicants.";
                case REJECTED -> "Your verification request was rejected. Please review administrator feedback, update your profile, and resubmit.";
                case SUSPENDED -> "Your recruiter verification has been suspended by an administrator. Job posting and modifications are restricted.";
            });
        }

        if (verificationActionButton != null) {
            switch (status) {
                case UNVERIFIED -> {
                    verificationActionButton.setVisible(true);
                    verificationActionButton.setManaged(true);
                    verificationActionButton.setDisable(false);
                    verificationActionButton.setText("Complete & Submit Profile");
                }
                case PENDING -> {
                    verificationActionButton.setVisible(true);
                    verificationActionButton.setManaged(true);
                    verificationActionButton.setDisable(true);
                    verificationActionButton.setText("Under Review");
                }
                case VERIFIED -> {
                    verificationActionButton.setVisible(true);
                    verificationActionButton.setManaged(true);
                    verificationActionButton.setDisable(false);
                    verificationActionButton.setText("+ Post New Job");
                }
                case REJECTED -> {
                    verificationActionButton.setVisible(true);
                    verificationActionButton.setManaged(true);
                    verificationActionButton.setDisable(false);
                    verificationActionButton.setText("Review Feedback & Resubmit");
                }
                case SUSPENDED -> {
                    verificationActionButton.setVisible(false);
                    verificationActionButton.setManaged(false);
                }
            }
        }

        if (verificationFeedbackBox != null) {
            boolean showFeedback = (status == RecruiterVerificationStatus.REJECTED || status == RecruiterVerificationStatus.SUSPENDED)
                    && hasText(reason);
            verificationFeedbackBox.setVisible(showFeedback);
            verificationFeedbackBox.setManaged(showFeedback);
            if (verificationReasonLabel != null && showFeedback) {
                verificationReasonLabel.setText(reason.trim());
            }
        }

        // Stored-Data Timeline
        if (timelineCreatedLabel != null) {
            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null && user.getCreatedAt() != null) {
                timelineCreatedLabel.setText("Account Created: " + user.getCreatedAt().format(DATE_FORMAT));
            } else {
                timelineCreatedLabel.setText("Account Created: —");
            }
        }
        if (timelineUpdatedLabel != null) {
            if (profile != null && profile.getUpdatedAt() != null) {
                timelineUpdatedLabel.setText("Profile Updated: " + profile.getUpdatedAt().format(DATE_FORMAT));
            } else {
                timelineUpdatedLabel.setText("Profile Updated: —");
            }
        }
        if (timelineReviewedLabel != null) {
            if (profile != null && profile.getVerifiedAt() != null) {
                timelineReviewedLabel.setText("Verification Reviewed: " + profile.getVerifiedAt().format(DATE_FORMAT));
                timelineReviewedLabel.setVisible(true);
                timelineReviewedLabel.setManaged(true);
            } else {
                timelineReviewedLabel.setText("Verification Reviewed: —");
                timelineReviewedLabel.setVisible(false);
                timelineReviewedLabel.setManaged(false);
            }
        }

        // 2. Profile Pane Verification Elements
        if (profileStatusBadge != null) {
            profileStatusBadge.setText(status.label().toUpperCase(Locale.ROOT));
            profileStatusBadge.getStyleClass().removeAll(
                    "rec-badge-unverified", "rec-badge-pending", "rec-badge-verified",
                    "rec-badge-rejected", "rec-badge-suspended"
            );
            profileStatusBadge.getStyleClass().add(switch (status) {
                case VERIFIED -> "rec-badge-verified";
                case PENDING -> "rec-badge-pending";
                case REJECTED -> "rec-badge-rejected";
                case SUSPENDED -> "rec-badge-suspended";
                case UNVERIFIED -> "rec-badge-unverified";
            });
        }

        if (profileVerificationMsgLabel != null) {
            profileVerificationMsgLabel.setText(switch (status) {
                case UNVERIFIED -> "Submit your company profile to request administrator verification.";
                case PENDING -> "Your company profile is under review by HireHub administrators.";
                case VERIFIED -> "Your company profile is verified. Changes will update your profile information.";
                case REJECTED -> "Your verification request was rejected. Please review administrator feedback below and resubmit.";
                case SUSPENDED -> "Your recruiter account is suspended. Changes cannot be resubmitted. Please contact an administrator.";
            });
        }

        if (profileFeedbackBox != null) {
            boolean showFeedback = (status == RecruiterVerificationStatus.REJECTED || status == RecruiterVerificationStatus.SUSPENDED)
                    && hasText(reason);
            profileFeedbackBox.setVisible(showFeedback);
            profileFeedbackBox.setManaged(showFeedback);
            if (profileReasonLabel != null && showFeedback) {
                profileReasonLabel.setText(reason.trim());
            }
        }

        if (submitVerificationBtn != null) {
            switch (status) {
                case UNVERIFIED -> {
                    submitVerificationBtn.setText("Submit for Verification");
                    submitVerificationBtn.setDisable(false);
                }
                case REJECTED -> {
                    submitVerificationBtn.setText("Resubmit for Verification");
                    submitVerificationBtn.setDisable(false);
                }
                case PENDING -> {
                    submitVerificationBtn.setText("Verification In Review");
                    submitVerificationBtn.setDisable(true);
                }
                case VERIFIED -> {
                    submitVerificationBtn.setText("Profile Verified");
                    submitVerificationBtn.setDisable(true);
                }
                case SUSPENDED -> {
                    submitVerificationBtn.setText("Account Suspended");
                    submitVerificationBtn.setDisable(true);
                }
            }
        }

        if (saveProfileBtn != null) {
            saveProfileBtn.setDisable(status == RecruiterVerificationStatus.SUSPENDED);
        }

        // 3. Jobs Pane Warning Banner
        if (jobsVerificationWarningBanner != null) {
            boolean restricted = status != RecruiterVerificationStatus.VERIFIED;
            jobsVerificationWarningBanner.setVisible(restricted);
            jobsVerificationWarningBanner.setManaged(restricted);
            if (jobsVerificationWarningLabel != null && restricted) {
                jobsVerificationWarningLabel.setText(switch (status) {
                    case UNVERIFIED -> "Job posting is unavailable until your recruiter account is verified by an administrator.";
                    case PENDING -> "Your account is pending verification. Job posting will be enabled once approved.";
                    case REJECTED -> "Your verification was rejected. Update your company profile and resubmit to enable job posting.";
                    case SUSPENDED -> "Your account is suspended. You cannot post or publish jobs.";
                    case VERIFIED -> "";
                });
            }
        }
    }

    /**
     * Updates the persistent header status pill based on verification status.
     */
    public void updateHeaderProfileStatusPill(RecruiterProfile profile) {
        if (headerProfileStatusPill == null) return;
        headerProfileStatusPill.getStyleClass().removeAll(
                "rec-profile-pill-ok", "rec-profile-pill-warn", "rec-profile-pill-neutral", "rec-profile-pill-danger"
        );
        RecruiterVerificationStatus status = (profile != null && profile.getVerificationStatus() != null)
                ? profile.getVerificationStatus()
                : RecruiterVerificationStatus.UNVERIFIED;

        switch (status) {
            case VERIFIED -> {
                headerProfileStatusPill.setText("Verified Recruiter");
                headerProfileStatusPill.getStyleClass().add("rec-profile-pill-ok");
            }
            case PENDING -> {
                headerProfileStatusPill.setText("Pending Verification");
                headerProfileStatusPill.getStyleClass().add("rec-profile-pill-warn");
            }
            case REJECTED -> {
                headerProfileStatusPill.setText("Verification Rejected");
                headerProfileStatusPill.getStyleClass().add("rec-profile-pill-danger");
            }
            case SUSPENDED -> {
                headerProfileStatusPill.setText("Verification Suspended");
                headerProfileStatusPill.getStyleClass().add("rec-profile-pill-danger");
            }
            case UNVERIFIED -> {
                headerProfileStatusPill.setText("Unverified");
                headerProfileStatusPill.getStyleClass().add("rec-profile-pill-neutral");
            }
        }
    }

    private void showPublishingRestrictedAlert(RecruiterVerificationStatus status) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Job Publishing Restricted");
        alert.setHeaderText("Account Verification Required (" + (status != null ? status.label() : "Unverified") + ")");

        String reason = (currentProfile != null) ? currentProfile.getVerificationReason() : null;
        String contentText = switch (status != null ? status : RecruiterVerificationStatus.UNVERIFIED) {
            case UNVERIFIED ->
                "Your company profile has not been submitted for verification.\n\n"
                + "You must submit your company details and be verified by an administrator before you can create or publish job postings.";
            case PENDING ->
                "Your recruiter profile is currently under review by an administrator.\n\n"
                + "Job creation and publishing will be unlocked once your verification is approved.";
            case REJECTED ->
                "Your verification request was rejected by an administrator"
                + (hasText(reason) ? ":\n\n\"" + reason.trim() + "\"\n\n" : ".\n\n")
                + "Please review the feedback, update your company profile, and resubmit for verification.";
            case SUSPENDED ->
                "Your recruiter account has been suspended by an administrator"
                + (hasText(reason) ? ":\n\n\"" + reason.trim() + "\"\n\n" : ".\n\n")
                + "Job posting and modifications are restricted. Please contact an administrator.";
            default ->
                "Your account is not currently verified. Job posting is restricted.";
        };

        alert.setContentText(contentText);

        if (status == RecruiterVerificationStatus.UNVERIFIED || status == RecruiterVerificationStatus.REJECTED) {
            ButtonType actionBtn = new ButtonType("Complete Profile", ButtonType.OK.getButtonData());
            alert.getButtonTypes().setAll(actionBtn, ButtonType.CANCEL);
            alert.showAndWait().ifPresent(btn -> {
                if (btn == actionBtn) {
                    showProfile();
                }
            });
        } else {
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
        }
    }

    // =========================================================================
    // Preserved Manage Jobs Table & CRUD Handlers
    // =========================================================================

    private void setupJobTable() {
        jobTable.setItems(jobsList);
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colType.setCellValueFactory(c -> new SimpleStringProperty(formatJobType(c.getValue().getJobType())));
        colLocation.setCellValueFactory(new PropertyValueFactory<>("location"));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        colDeadline.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDeadline() != null ? c.getValue().getDeadline().format(DATE_FORMAT) : "—"));
        colApplicants.setCellValueFactory(c -> {
            Job job = c.getValue();
            int count = jobAppCountMap.getOrDefault(job.getId(), 0);
            return new SimpleIntegerProperty(count).asObject();
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().addAll("badge", "badge-" + status.toLowerCase());
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Applicants");

            {
                btn.getStyleClass().add("table-action-btn");
                btn.setOnAction(e -> {
                    Job job = getTableView().getItems().get(getIndex());
                    openApplicantManagement(job);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        jobTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openApplicantManagement(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadJobsAsync() {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        if (recruiterId <= 0) return;

        Task<JobLoadBundle> task = new Task<>() {
            @Override
            protected JobLoadBundle call() {
                List<Job> jobs = jobService.getJobsByRecruiter(recruiterId);
                Map<Integer, Integer> counts = applicationService.countApplicationsByJobForRecruiter(recruiterId);
                return new JobLoadBundle(jobs, counts);
            }
        };
        task.setOnSucceeded(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            JobLoadBundle bundle = task.getValue();
            jobAppCountMap.clear();
            if (bundle.counts() != null) {
                jobAppCountMap.putAll(bundle.counts());
            }
            jobsList.setAll(bundle.jobs() != null ? bundle.jobs() : Collections.emptyList());
            jobTable.refresh();
        });
        task.setOnFailed(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            showStatus(jobTableStatus, "Failed to load job postings.", true);
        });

        Thread worker = new Thread(task, "recruiter-jobs-loader");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void handleEditJob() {
        if (!ensureRecruiterAuthorized()) return;
        Job selected = jobTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus(jobTableStatus, "Select a job posting to edit.", true);
            return;
        }
        openJobForm(selected);
    }

    @FXML
    public void handleCloseJob() {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        Job selected = jobTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus(jobTableStatus, "Select a job posting to close.", true);
            return;
        }
        if (selected.getStatus() == Job.Status.CLOSED) {
            showStatus(jobTableStatus, "This job is already closed.", true);
            return;
        }
        Task<JobService.ServiceResult> task = new Task<>() {
            @Override
            protected JobService.ServiceResult call() {
                return jobService.closeJob(recruiterId, selected.getId());
            }
        };
        task.setOnSucceeded(e -> {
            if (!isSessionMatchingRecruiter(recruiterId)) return;
            JobService.ServiceResult res = task.getValue();
            showStatus(jobTableStatus, res.getMessage(), !res.isSuccess());
            if (res.isSuccess()) {
                loadJobsAsync();
                loadAnalyticsAsync(true);
            }
        });
        Thread worker = new Thread(task, "recruiter-close-job");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void handleDeleteJob() {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        Job selected = jobTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus(jobTableStatus, "Select a job posting to delete.", true);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete job posting '" + selected.getTitle() + "'? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                Task<JobService.ServiceResult> task = new Task<>() {
                    @Override
                    protected JobService.ServiceResult call() {
                        return jobService.deleteJob(recruiterId, selected.getId());
                    }
                };
                task.setOnSucceeded(ev -> {
                    if (!isSessionMatchingRecruiter(recruiterId)) return;
                    JobService.ServiceResult res = task.getValue();
                    showStatus(jobTableStatus, res.getMessage(), !res.isSuccess());
                    if (res.isSuccess()) {
                        loadJobsAsync();
                        loadAnalyticsAsync(true);
                    }
                });
                Thread worker = new Thread(task, "recruiter-delete-job");
                worker.setDaemon(true);
                worker.start();
            }
        });
    }

    @FXML
    public void handleViewApplicants() {
        if (!ensureRecruiterAuthorized()) return;
        Job selected = jobTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus(jobTableStatus, "Select a job posting to view its applicants.", true);
            return;
        }
        openApplicantManagement(selected);
    }

    private void openJobForm(Job jobToEdit) {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        NavigationManager.navigate(rootPane, "/fxml/job_form.fxml",
                (JobFormController c) -> c.init(recruiterId, jobToEdit));
    }

    private void openApplicantManagement(Job job) {
        if (!ensureRecruiterAuthorized()) return;
        int recruiterId = authenticatedRecruiterId();
        if (job == null || job.getId() <= 0 || job.getRecruiterId() != recruiterId) {
            showStatus(jobTableStatus, "Access denied for the selected job posting.", true);
            return;
        }
        cancelAllBackgroundTasks();
        NavigationManager.navigate(rootPane, "/fxml/applicant_management.fxml",
                (ApplicantManagementController c) -> c.init(recruiterId, job));
    }

    // =========================================================================
    // Authorization & Fail-Closed Guards (Step 3A Preserved)
    // =========================================================================

    private boolean ensureRecruiterAuthorized() {
        if (SessionManager.getInstance().hasRole(User.Role.RECRUITER)) {
            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null && user.getId() > 0) {
                return true;
            }
        }
        cancelAllBackgroundTasks();
        hideRecruiterContent();
        failClosedToLogin();
        return false;
    }

    private int authenticatedRecruiterId() {
        User user = SessionManager.getInstance().getCurrentUser();
        return user != null ? user.getId() : -1;
    }

    private boolean isSessionMatchingRecruiter(int expectedRecruiterId) {
        if (!SessionManager.getInstance().hasRole(User.Role.RECRUITER)) {
            return false;
        }
        User user = SessionManager.getInstance().getCurrentUser();
        return user != null && user.getId() == expectedRecruiterId;
    }

    private void failClosedToLogin() {
        if (!redirectScheduled) {
            redirectScheduled = true;
            Platform.runLater(() -> NavigationManager.navigateTo("/fxml/login.fxml"));
        }
    }

    private void hideRecruiterContent() {
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
        if (jobsPane != null) {
            jobsPane.setVisible(false);
            jobsPane.setManaged(false);
        }
        for (Node anchor : new Node[]{navDashboardBtn, navLogoutBtn, welcomeLabel, dashboardPane}) {
            hideRootAncestor(anchor);
        }
    }

    private void hideRootAncestor(Node node) {
        if (node == null) return;
        Parent parent = node.getParent();
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();
        }
        if (parent != null) {
            parent.setVisible(false);
            parent.setManaged(false);
        }
    }

    private void cancelAllBackgroundTasks() {
        activeRequestSeq.incrementAndGet();
        Task<?> analyticsTask = activeAnalyticsTask;
        if (analyticsTask != null && !analyticsTask.isDone()) {
            analyticsTask.cancel(true);
        }
        Task<?> lookupTask = activeJobLookupTask;
        if (lookupTask != null && !lookupTask.isDone()) {
            lookupTask.cancel(true);
        }
        isRefreshing.set(false);
        setLoadingUi(false, false);
    }

    // =========================================================================
    // UI Utility Helpers
    // =========================================================================

    private void switchPane(VBox activePane, Button activeNavBtn) {
        VBox[] panes = {dashboardPane, profilePane, jobsPane};
        for (VBox pane : panes) {
            boolean show = pane == activePane;
            if (pane != null) {
                pane.setVisible(show);
                pane.setManaged(show);
            }
        }
        updateActiveNav(activeNavBtn);
        if (mainScrollPane != null) {
            mainScrollPane.setVvalue(0);
        }
    }

    private void updateActiveNav(Button activeBtn) {
        Button[] navBtns = {navDashboardBtn, navJobsBtn, navPostJobBtn, navProfileBtn};
        for (Button btn : navBtns) {
            if (btn != null) {
                btn.getStyleClass().removeAll("sidebar-nav-active", "sidebar-post-job-btn");
            }
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("sidebar-nav-active")) {
            activeBtn.getStyleClass().add("sidebar-nav-active");
        }
    }

    private void configureIntegerAxis(NumberAxis axis) {
        if (axis == null) return;
        axis.setForceZeroInRange(true);
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                if (object == null) return "";
                double v = object.doubleValue();
                if (Math.abs(v - Math.rint(v)) < 1e-6) {
                    return String.valueOf((long) Math.rint(v));
                }
                return "";
            }

            @Override
            public Number fromString(String string) {
                return Integer.parseInt(string);
            }
        });
    }

    private void showBanner(Node banner) {
        if (banner != null) {
            banner.setVisible(true);
            banner.setManaged(true);
        }
    }

    private void hideBanner(Node banner) {
        if (banner != null) {
            banner.setVisible(false);
            banner.setManaged(false);
        }
    }

    private void setProfileLoading(boolean loading) {
        if (profileLoading != null) {
            profileLoading.setVisible(loading);
            profileLoading.setManaged(loading);
        }
        if (saveProfileBtn != null) {
            saveProfileBtn.setDisable(loading);
        }
        if (submitVerificationBtn != null) {
            submitVerificationBtn.setDisable(loading);
        }
    }

    private void showStatus(Label label, String msg, boolean isError) {
        label.setText(msg);
        label.getStyleClass().removeAll("error-label", "success-label");
        label.getStyleClass().add(isError ? "error-label" : "success-label");
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideStatus(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    private static double safeRatio(int num, int den) {
        if (den <= 0 || num <= 0) return 0.0;
        return Math.min(1.0, (double) num / den);
    }

    private static double clampProgress(double ratePct) {
        if (Double.isNaN(ratePct) || ratePct <= 0.0) return 0.0;
        return Math.min(1.0, ratePct / 100.0);
    }

    private static String formatRateOrNA(double pct, int denominator) {
        if (denominator <= 0 || Double.isNaN(pct)) {
            return "N/A";
        }
        return String.format("%.1f%%", pct);
    }

    private static String formatGranularity(RecruiterTrendPoint.TrendGranularity granularity) {
        if (granularity == null) return "Monthly";
        return switch (granularity) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
        };
    }

    private static String formatJobType(Job.JobType type) {
        if (type == null) return "—";
        return formatJobType(type.name());
    }

    private static String formatJobType(String typeStr) {
        if (!hasText(typeStr)) return "—";
        return switch (typeStr.trim().toUpperCase(Locale.ROOT)) {
            case "INTERNSHIP" -> "Internship";
            case "FULL_TIME", "FULL-TIME" -> "Full-Time";
            case "PART_TIME", "PART-TIME" -> "Part-Time";
            default -> typeStr.trim();
        };
    }

    private static String formatStatusName(String status) {
        if (!hasText(status)) return "Unknown";
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Pending";
            case "SHORTLISTED" -> "Shortlisted";
            case "SELECTED" -> "Selected";
            case "REJECTED" -> "Rejected";
            default -> status.trim();
        };
    }

    private static String statusBadgeClass(String status) {
        if (!hasText(status)) return "rec-badge-pending";
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "rec-badge-pending";
            case "SHORTLISTED" -> "rec-badge-shortlisted";
            case "SELECTED" -> "rec-badge-selected";
            case "REJECTED" -> "rec-badge-rejected";
            default -> "rec-badge-pending";
        };
    }

    private static String statusHexColor(String status) {
        if (!hasText(status)) return "#64748B";
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "#D97706";
            case "SHORTLISTED" -> "#2563EB";
            case "SELECTED" -> "#16A34A";
            case "REJECTED" -> "#DC2626";
            default -> "#64748B";
        };
    }

    private static String formatActivityType(String eventType) {
        if (eventType == null) return "Activity";
        return switch (eventType) {
            case "JOB_POSTED" -> "Job Posted";
            case "APPLICATION_RECEIVED" -> "App Received";
            case "APPLICATION_REVIEWED" -> "App Reviewed";
            default -> eventType.replace('_', ' ');
        };
    }

    private static String activityBadgeClass(String eventType) {
        if (eventType == null) return "rec-badge-pending";
        return switch (eventType) {
            case "JOB_POSTED" -> "rec-badge-purple";
            case "APPLICATION_RECEIVED" -> "rec-badge-shortlisted";
            case "APPLICATION_REVIEWED" -> "rec-badge-selected";
            default -> "rec-badge-pending";
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String safeText(String s, String fallback) {
        return hasText(s) ? s.trim() : fallback;
    }

    private record JobLoadBundle(List<Job> jobs, Map<Integer, Integer> counts) {
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
