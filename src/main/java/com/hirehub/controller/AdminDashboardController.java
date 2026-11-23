package com.hirehub.controller;

import com.hirehub.dao.AdminAnalyticsDAO;
import com.hirehub.model.AdminActivityEvent;
import com.hirehub.model.AdminApplicationKPI;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.AdminDashboardSnapshot;
import com.hirehub.model.AdminJobKPI;
import com.hirehub.model.AdminJobModerationKPI;
import com.hirehub.model.AdminJobTypeStat;
import com.hirehub.model.AdminOverviewKPI;
import com.hirehub.model.AdminRecruiterEngagement;
import com.hirehub.model.AdminRecruiterVerificationKPI;
import com.hirehub.model.AdminStatusDistribution;
import com.hirehub.model.AdminStudentParticipation;
import com.hirehub.model.AdminTopJob;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.service.AdminAnalyticsService;
import com.hirehub.service.AdminModerationService;
import com.hirehub.service.AdminUserService;
import com.hirehub.service.AuthService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for the Phase 5 Admin Placement Intelligence Dashboard.
 * Provides role-guarded access to overview KPIs, job recruitment metrics,
 * student readiness, recruiter engagement, and recent platform activity.
 */
public class AdminDashboardController {

    private static final String PRESET_ALL_TIME = "All Time";
    private static final String PRESET_LAST_7 = "Last 7 Days";
    private static final String PRESET_LAST_30 = "Last 30 Days";
    private static final String PRESET_LAST_90 = "Last 90 Days";
    private static final String PRESET_CUSTOM = "Custom Range";
    private static final String ALL_EVENT_TYPES = "All Event Types";
    private static final int ACTIVITY_LOG_LIMIT = 50;
    private static final int OVERVIEW_ACTIVITY_PREVIEW_LIMIT = 8;
    private static final double TABLE_HEADER_HEIGHT = 38.0;
    private static final double TABLE_ROW_HEIGHT = 42.0;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    // ---- Layout & Navigation ----
    @FXML private BorderPane rootPane;
    @FXML private ScrollPane mainScrollPane;
    @FXML private Button navOverviewBtn;
    @FXML private Button navJobsBtn;
    @FXML private Button navStudentsBtn;
    @FXML private Button navRecruitersBtn;
    @FXML private Button navRecruiterVerificationBtn;
    @FXML private Button navJobModerationBtn;
    @FXML private Button navUsersBtn;
    @FXML private Button navActivityBtn;
    @FXML private Button navRefreshBtn;
    @FXML private Button navLogoutBtn;

    // ---- Header, Date Filter & Load State ----
    @FXML private Label welcomeSubtitleLabel;
    @FXML private Label adminInitialsHeaderLabel;
    @FXML private Label adminNameHeaderLabel;
    @FXML private Label lastRefreshedLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private ComboBox<String> datePresetCombo;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button applyFilterBtn;
    @FXML private Button resetFilterBtn;
    @FXML private Label activeFilterBadge;
    @FXML private Label filterScopeNoteLabel;
    @FXML private Label filterErrorLabel;
    @FXML private HBox loadErrorBox;
    @FXML private Label loadErrorLabel;
    @FXML private Button retryLoadBtn;

    // ---- Section Panes ----
    @FXML private VBox overviewPane;
    @FXML private VBox jobsPane;
    @FXML private VBox studentsPane;
    @FXML private VBox recruitersPane;
    @FXML private VBox usersPane;
    @FXML private VBox activityPane;
    @FXML private VBox recruiterVerificationPane;
    @FXML private VBox jobModerationPane;

    // ---- Overview KPIs & Visuals ----
    @FXML private Label ovTotalStudentsLabel;
    @FXML private Label ovStudentsSubLabel;
    @FXML private Label ovTotalRecruitersLabel;
    @FXML private Label ovRecruitersSubLabel;
    @FXML private Label ovTotalJobsLabel;
    @FXML private Label ovJobsBreakdownLabel;
    @FXML private Label ovOpenJobsLabel;
    @FXML private Label ovExpiredJobsBadge;
    @FXML private Label ovOpenJobsSubLabel;
    @FXML private Label ovTotalAppsLabel;
    @FXML private Label ovUniqueApplicantsLabel;
    @FXML private Label ovPendingAppsLabel;
    @FXML private Label ovPendingSubLabel;
    @FXML private Label ovShortlistedAppsLabel;
    @FXML private Label ovShortlistedSubLabel;
    @FXML private Label ovUniqueSelectedLabel;
    @FXML private Label ovSelectedAppsSubLabel;
    @FXML private PieChart statusPieChart;
    @FXML private VBox statusPieEmptyBox;
    @FXML private VBox statusBreakdownList;
    @FXML private VBox funnelRowsContainer;
    @FXML private Label ovShortlistRateLabel;
    @FXML private Label ovSelectionRateLabel;
    @FXML private Label ovUniqueHireRateLabel;
    @FXML private VBox overviewActivityContainer;

    // ---- Jobs & Recruitment Section ----
    @FXML private Label jbTotalLabel;
    @FXML private Label jbActiveStatusLabel;
    @FXML private Label jbOpenLabel;
    @FXML private Label jbExpiredActiveLabel;
    @FXML private Label jbDraftLabel;
    @FXML private Label jbClosedLabel;
    @FXML private Label jbPostingRecruitersLabel;
    @FXML private BarChart<String, Number> jobTypeBarChart;
    @FXML private CategoryAxis jobTypeXAxis;
    @FXML private NumberAxis jobTypeYAxis;
    @FXML private VBox jobTypeChartEmptyBox;
    @FXML private HBox jobTypeSummaryRow;
    @FXML private TableView<AdminTopJob> topJobsTable;
    @FXML private TableColumn<AdminTopJob, String> colTopJobTitle;
    @FXML private TableColumn<AdminTopJob, String> colTopJobCompany;
    @FXML private TableColumn<AdminTopJob, String> colTopJobType;
    @FXML private TableColumn<AdminTopJob, String> colTopJobStatus;
    @FXML private TableColumn<AdminTopJob, String> colTopJobDeadline;
    @FXML private TableColumn<AdminTopJob, Integer> colTopJobApps;
    @FXML private TableColumn<AdminTopJob, Integer> colTopJobShortlisted;
    @FXML private TableColumn<AdminTopJob, Integer> colTopJobSelected;

    // ---- Student Readiness Section ----
    @FXML private Label stTotalStudentsLabel;
    @FXML private Label stCompletedProfilesLabel;
    @FXML private Label stProfileCompletionRateLabel;
    @FXML private Label stAppliedStudentsLabel;
    @FXML private Label stAppliedRateLabel;
    @FXML private Label stAiUsersLabel;
    @FXML private Label stAiTotalAnalysesLabel;
    @FXML private Label stAvgScoreLabel;
    @FXML private VBox studentProgressContainer;
    @FXML private VBox stAiEmptyBox;
    @FXML private Label stDetailTotalAnalysesLabel;
    @FXML private Label stDetailAiUsersLabel;
    @FXML private Label stDetailJobSpecificLabel;
    @FXML private Label stDetailGeneralLabel;

    // ---- Recruiter Engagement Section ----
    @FXML private Label recruiterCountBadge;
    @FXML private TableView<AdminRecruiterEngagement> recruiterTable;
    @FXML private TableColumn<AdminRecruiterEngagement, String> colRecName;
    @FXML private TableColumn<AdminRecruiterEngagement, String> colRecCompany;
    @FXML private TableColumn<AdminRecruiterEngagement, Integer> colRecJobsPosted;
    @FXML private TableColumn<AdminRecruiterEngagement, Integer> colRecOpenJobs;
    @FXML private TableColumn<AdminRecruiterEngagement, Integer> colRecAppsReceived;
    @FXML private TableColumn<AdminRecruiterEngagement, Integer> colRecShortlisted;
    @FXML private TableColumn<AdminRecruiterEngagement, Integer> colRecSelected;
    @FXML private TableColumn<AdminRecruiterEngagement, String> colRecLastJob;
    @FXML private TableColumn<AdminRecruiterEngagement, String> colRecLastReview;

    // ---- Activity Log Section ----
    @FXML private Label activityCountLabel;
    @FXML private ComboBox<String> activityFilterCombo;
    @FXML private Button clearActivityFilterBtn;
    @FXML private VBox activityLogContainer;
    @FXML private VBox activityEmptyBox;
    @FXML private Label activityEmptyDescLabel;

    // ---- User Management Section ----
    @FXML private Label userCountSubtitleLabel;
    @FXML private ProgressIndicator userLoadingIndicator;
    @FXML private TextField txtUserSearch;
    @FXML private ComboBox<String> comboRoleFilter;
    @FXML private ComboBox<String> comboStatusFilter;
    @FXML private Button btnApplyUserFilters;
    @FXML private Button btnClearUserFilters;
    @FXML private Label userStatusMessageLabel;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String> colUserFullName;
    @FXML private TableColumn<User, String> colUserEmail;
    @FXML private TableColumn<User, String> colUserRole;
    @FXML private TableColumn<User, Boolean> colUserStatus;
    @FXML private TableColumn<User, String> colUserCreatedAt;
    @FXML private TableColumn<User, Void> colUserAction;
    @FXML private VBox userEmptyBox;

    // ---- Recruiter Verification Section ----
    @FXML private Label recTotalLabel;
    @FXML private Label recPendingLabel;
    @FXML private Label recVerifiedLabel;
    @FXML private Label recRejectedLabel;
    @FXML private Label recSuspendedLabel;
    @FXML private Label recUnverifiedLabel;
    @FXML private Label recSubtitleLabel;
    @FXML private ProgressIndicator recLoadingIndicator;
    @FXML private TextField txtRecruiterSearch;
    @FXML private ComboBox<String> comboRecruiterStatusFilter;
    @FXML private Button btnApplyRecruiterFilters;
    @FXML private Button btnClearRecruiterFilters;
    @FXML private Button btnRefreshRecruiters;
    @FXML private Label recruiterStatusMsgLabel;
    @FXML private TableView<RecruiterProfile> recruiterVerificationTable;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterCompany;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterEmail;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterIndustry;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterWebsite;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterStatus;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterSubmittedAt;
    @FXML private TableColumn<RecruiterProfile, String> colRecruiterReviewedAt;
    @FXML private VBox recruiterEmptyBox;
    @FXML private VBox recruiterPlaceholderBox;
    @FXML private VBox recruiterDetailContainer;
    @FXML private Label recDetailCompanyName;
    @FXML private Label recDetailRecruiterName;
    @FXML private Label recDetailStatusBadge;
    @FXML private Label recDetailEmail;
    @FXML private Label recDetailUserIdAndActive;
    @FXML private Label recDetailRegisteredDate;
    @FXML private Label recDetailWebsite;
    @FXML private Label recDetailIndustry;
    @FXML private Label recDetailDescription;
    @FXML private Label recDetailReason;
    @FXML private Label recDetailReviewMetadata;
    @FXML private Button btnVerifyRecruiter;
    @FXML private Button btnRejectRecruiter;
    @FXML private Button btnSuspendRecruiter;
    @FXML private Button btnReturnRecruiterPending;
    @FXML private VBox recruiterAuditListContainer;
    @FXML private Label recruiterAuditEmptyLabel;

    // ---- Job Moderation Section ----
    @FXML private Label jobModTotalLabel;
    @FXML private Label jobModPendingLabel;
    @FXML private Label jobModApprovedLabel;
    @FXML private Label jobModRejectedLabel;
    @FXML private Label jobModSuspendedLabel;
    @FXML private Label jobModActiveLifecycleLabel;
    @FXML private Label jobModSubtitleLabel;
    @FXML private ProgressIndicator jobModLoadingIndicator;
    @FXML private TextField txtJobModSearch;
    @FXML private ComboBox<String> comboJobModerationStatusFilter;
    @FXML private ComboBox<String> comboJobLifecycleFilter;
    @FXML private Button btnApplyJobModFilters;
    @FXML private Button btnClearJobModFilters;
    @FXML private Button btnRefreshJobMod;
    @FXML private Label jobModStatusMsgLabel;
    @FXML private TableView<Job> jobModerationTable;
    @FXML private TableColumn<Job, String> colJobModTitle;
    @FXML private TableColumn<Job, String> colJobModCompany;
    @FXML private TableColumn<Job, String> colJobModRecruiter;
    @FXML private TableColumn<Job, String> colJobModLocation;
    @FXML private TableColumn<Job, String> colJobModType;
    @FXML private TableColumn<Job, String> colJobModLifecycleStatus;
    @FXML private TableColumn<Job, String> colJobModModerationStatus;
    @FXML private TableColumn<Job, String> colJobModCreatedAt;
    @FXML private TableColumn<Job, String> colJobModDeadline;
    @FXML private VBox jobModEmptyBox;
    @FXML private VBox jobModPlaceholderBox;
    @FXML private VBox jobModDetailContainer;
    @FXML private Label jobModDetailTitle;
    @FXML private Label jobModDetailCompany;
    @FXML private Label jobModDetailLifecycleBadge;
    @FXML private Label jobModDetailModerationBadge;
    @FXML private Label jobModDetailRecruiter;
    @FXML private Label jobModDetailTypeAndLocation;
    @FXML private Label jobModDetailSalary;
    @FXML private Label jobModDetailDates;
    @FXML private Label jobModDetailSkills;
    @FXML private Label jobModDetailEligibility;
    @FXML private Label jobModDetailDesc;
    @FXML private Label jobModDetailReason;
    @FXML private Label jobModDetailReviewMetadata;
    @FXML private Button btnApproveJob;
    @FXML private Button btnRejectJob;
    @FXML private Button btnSuspendJob;
    @FXML private Button btnReturnJobPending;
    @FXML private VBox jobAuditListContainer;
    @FXML private Label jobAuditEmptyLabel;

    // ---- Services & State ----
    private final AuthService authService = new AuthService();
    private final AdminAnalyticsService analyticsService = new AdminAnalyticsService();
    private final AdminAnalyticsDAO analyticsDAO = new AdminAnalyticsDAO();
    private final AdminUserService adminUserService = new AdminUserService();
    private final AdminModerationService moderationService = new AdminModerationService();
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final ObservableList<RecruiterProfile> recruiterList = FXCollections.observableArrayList();
    private final ObservableList<Job> jobModList = FXCollections.observableArrayList();
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicBoolean isUserLoading = new AtomicBoolean(false);
    private final AtomicBoolean isRecruiterLoading = new AtomicBoolean(false);
    private final AtomicBoolean isJobModLoading = new AtomicBoolean(false);
    private long recruiterQuerySeq = 0;
    private long jobQuerySeq = 0;
    private RecruiterProfile selectedRecruiterProfile = null;
    private Job selectedJobMod = null;

    private String activeNavStyleClass = "sidebar-btn-active";
    private boolean updatingControls = false;
    private String activePreset = PRESET_ALL_TIME;
    private LocalDate activeCustomFrom = null;
    private LocalDate activeCustomTo = null;

    private AdminDashboardSnapshot lastValidSnapshot = null;
    private List<AdminActivityEvent> cachedActivityEvents = Collections.emptyList();

    private record LoadResult(AdminDashboardSnapshot snapshot, List<AdminActivityEvent> activityEvents) {}

    private enum FilterMode { ALL_TIME, LAST_7, LAST_30, LAST_90, CUSTOM }

    @FXML
    public void initialize() {
        if (!ensureAdminAuthorized()) {
            return;
        }

        detectActiveNavStyleClass();
        populateHeaderIdentity();
        setupNavigationState();
        setupDateFilterControls();
        setupCharts();
        setupTopJobsTable();
        setupRecruiterTable();
        setupUserControls();
        setupUserTable();
        setupRecruiterVerificationControls();
        setupRecruiterVerificationTable();
        setupJobModerationControls();
        setupJobModerationTable();
        setupActivityFilterControls();
        setupInitialLoadState();

        loadAnalyticsAsync(FilterMode.ALL_TIME, null, null);
    }

    private boolean ensureAdminAuthorized() {
        if (SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return true;
        }
        if (rootPane != null) {
            rootPane.setVisible(false);
            rootPane.setManaged(false);
        }
        Platform.runLater(() -> NavigationManager.navigateTo("/fxml/login.fxml"));
        return false;
    }

    private void detectActiveNavStyleClass() {
        if (navOverviewBtn == null || navJobsBtn == null) {
            return;
        }
        for (String styleClass : navOverviewBtn.getStyleClass()) {
            if (!navJobsBtn.getStyleClass().contains(styleClass)) {
                activeNavStyleClass = styleClass;
                return;
            }
        }
    }

    private void populateHeaderIdentity() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String fullName = currentUser.getFullName();
            String email = currentUser.getEmail();
            if (welcomeSubtitleLabel != null) {
                if (fullName != null && !fullName.isBlank() && email != null && !email.isBlank()) {
                    welcomeSubtitleLabel.setText("Signed in as " + fullName + " (" + email + ")");
                } else if (fullName != null && !fullName.isBlank()) {
                    welcomeSubtitleLabel.setText("Signed in as " + fullName);
                }
            }
            if (adminNameHeaderLabel != null) {
                adminNameHeaderLabel.setText(fullName != null && !fullName.isBlank() ? fullName : "System Administrator");
            }
            if (adminInitialsHeaderLabel != null) {
                adminInitialsHeaderLabel.setText(extractInitials(fullName, "AD"));
            }
        }
    }

    private void setupNavigationState() {
        setVisibleManaged(loadErrorBox, false);
        showPane(overviewPane, navOverviewBtn);
    }

    private void setupDateFilterControls() {
        if (datePresetCombo != null) {
            datePresetCombo.setItems(FXCollections.observableArrayList(
                    PRESET_ALL_TIME, PRESET_LAST_7, PRESET_LAST_30, PRESET_LAST_90, PRESET_CUSTOM));
            updatingControls = true;
            datePresetCombo.getSelectionModel().select(PRESET_ALL_TIME);
            updatingControls = false;

            datePresetCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (updatingControls || newVal == null) {
                    return;
                }
                clearFilterError();
                boolean isCustom = PRESET_CUSTOM.equals(newVal);
                setCustomPickersEnabled(isCustom);
                if (!isCustom) {
                    activePreset = newVal;
                    activeCustomFrom = null;
                    activeCustomTo = null;
                    applyPresetSelection(newVal);
                }
            });
        }

        setCustomPickersEnabled(false);
        if (retryLoadBtn != null) {
            retryLoadBtn.setOnAction(this::handleRefresh);
        }
    }

    private void setCustomPickersEnabled(boolean enabled) {
        if (fromDatePicker != null) {
            fromDatePicker.setDisable(!enabled);
            if (!enabled) {
                fromDatePicker.setValue(null);
            }
        }
        if (toDatePicker != null) {
            toDatePicker.setDisable(!enabled);
            if (!enabled) {
                toDatePicker.setValue(null);
            }
        }
    }

    private void setupCharts() {
        if (statusPieChart != null) {
            statusPieChart.setAnimated(false);
            statusPieChart.setLabelsVisible(true);
        }
        if (jobTypeBarChart != null) {
            jobTypeBarChart.setAnimated(false);
        }
        if (jobTypeXAxis != null) {
            jobTypeXAxis.setAnimated(false);
        }
        if (jobTypeYAxis != null) {
            jobTypeYAxis.setAnimated(false);
            jobTypeYAxis.setAutoRanging(false);
            jobTypeYAxis.setLowerBound(0);
            jobTypeYAxis.setUpperBound(4);
            jobTypeYAxis.setTickUnit(1);
            jobTypeYAxis.setMinorTickVisible(false);
            jobTypeYAxis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number value) {
                    if (value == null) {
                        return "";
                    }
                    double d = value.doubleValue();
                    if (Math.abs(d - Math.rint(d)) < 1e-6) {
                        return String.valueOf(Math.round(d));
                    }
                    return "";
                }

                @Override
                public Number fromString(String string) {
                    return string == null || string.isBlank() ? 0 : Double.parseDouble(string);
                }
            });
        }
    }

    private void setupTopJobsTable() {
        if (topJobsTable == null) {
            return;
        }
        topJobsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        topJobsTable.setFixedCellSize(TABLE_ROW_HEIGHT);
        topJobsTable.setPlaceholder(createTablePlaceholder("No jobs found for the selected filter period."));

        colTopJobTitle.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(nonEmptyOrDash(cell.getValue() == null ? null : cell.getValue().getTitle())));
        colTopJobCompany.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(nonEmptyOrDash(cell.getValue() == null ? null : cell.getValue().getCompanyName())));
        colTopJobType.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatJobType(cell.getValue() == null ? null : cell.getValue().getJobType())));
        colTopJobStatus.setCellValueFactory(cell -> {
            AdminTopJob job = cell.getValue();
            if (job == null) {
                return new ReadOnlyStringWrapper("—");
            }
            return new ReadOnlyStringWrapper(isExpiredActive(job) ? "ACTIVE (Expired)" : nonEmptyOrDash(job.getStatus()));
        });
        colTopJobDeadline.setCellValueFactory(cell -> {
            AdminTopJob job = cell.getValue();
            if (job == null || job.getDeadline() == null) {
                return new ReadOnlyStringWrapper("—");
            }
            return new ReadOnlyStringWrapper(job.getDeadline().format(DATE_FMT));
        });
        colTopJobApps.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getTotalApplications()));
        colTopJobShortlisted.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getShortlistedCount()));
        colTopJobSelected.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getSelectedCount()));

        colTopJobStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                AdminTopJob rowItem = getTableRow() == null ? null : getTableRow().getItem();
                Label badge = createJobStatusBadge(rowItem, item);
                setGraphic(badge);
                setText(null);
            }
        });

        topJobsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AdminTopJob item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("admin-row-expired");
                if (!empty && isExpiredActive(item)) {
                    getStyleClass().add("admin-row-expired");
                }
            }
        });

        updateTableHeight(topJobsTable, 0);
    }

    private void setupRecruiterTable() {
        if (recruiterTable == null) {
            return;
        }
        recruiterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recruiterTable.setFixedCellSize(TABLE_ROW_HEIGHT);
        recruiterTable.setPlaceholder(createTablePlaceholder("No recruiters registered yet."));

        colRecName.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(nonEmptyOrDash(cell.getValue() == null ? null : cell.getValue().getRecruiterName())));
        colRecCompany.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatRecruiterCompany(cell.getValue())));
        colRecJobsPosted.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getTotalJobsPosted()));
        colRecOpenJobs.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getOpenJobs()));
        colRecAppsReceived.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getApplicationsReceived()));
        colRecShortlisted.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getShortlistedCount()));
        colRecSelected.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue() == null ? 0 : cell.getValue().getSelectedCount()));
        colRecLastJob.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDateTimeOrDash(
                        cell.getValue() == null ? null : cell.getValue().getLastJobPostedAt(), "Never")));
        colRecLastReview.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(formatDateTimeOrDash(
                        cell.getValue() == null ? null : cell.getValue().getLastAppReviewAt(), "Never")));

        updateTableHeight(recruiterTable, 0);
    }

    private void setupActivityFilterControls() {
        if (activityFilterCombo != null) {
            activityFilterCombo.setItems(FXCollections.observableArrayList(ALL_EVENT_TYPES));
            updatingControls = true;
            activityFilterCombo.getSelectionModel().select(ALL_EVENT_TYPES);
            updatingControls = false;
            activityFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!updatingControls) {
                    applyActivityFilter();
                }
            });
        }
        if (clearActivityFilterBtn != null) {
            clearActivityFilterBtn.setDisable(true);
        }
    }

    private void setupInitialLoadState() {
        if (lastRefreshedLabel != null) {
            lastRefreshedLabel.setText("Loading live metrics...");
        }
        if (activeFilterBadge != null) {
            activeFilterBadge.setText(PRESET_ALL_TIME);
        }
        setVisibleManaged(ovExpiredJobsBadge, false);
        setVisibleManaged(filterErrorLabel, false);
    }

    // ---- Navigation Handlers ----

    @FXML
    private void handleNavOverview(ActionEvent event) {
        showPane(overviewPane, navOverviewBtn);
    }

    @FXML
    private void handleNavJobs(ActionEvent event) {
        showPane(jobsPane, navJobsBtn);
    }

    @FXML
    private void handleNavStudents(ActionEvent event) {
        showPane(studentsPane, navStudentsBtn);
    }

    @FXML
    private void handleNavRecruiters(ActionEvent event) {
        showPane(recruitersPane, navRecruitersBtn);
    }

    @FXML
    private void handleNavRecruiterVerification(ActionEvent event) {
        showPane(recruiterVerificationPane, navRecruiterVerificationBtn);
        loadRecruiterKPIAsync();
        loadRecruitersAsync();
    }

    @FXML
    private void handleNavJobModeration(ActionEvent event) {
        showPane(jobModerationPane, navJobModerationBtn);
        loadJobKPIAsync();
        loadJobModerationAsync();
    }

    @FXML
    private void handleNavUsers(ActionEvent event) {
        showPane(usersPane, navUsersBtn);
        loadUsersAsync();
    }

    @FXML
    private void handleApplyUserFilters(ActionEvent event) {
        loadUsersAsync();
    }

    @FXML
    private void handleClearUserFilters(ActionEvent event) {
        if (txtUserSearch != null) {
            txtUserSearch.clear();
        }
        if (comboRoleFilter != null) {
            comboRoleFilter.getSelectionModel().selectFirst();
        }
        if (comboStatusFilter != null) {
            comboStatusFilter.getSelectionModel().selectFirst();
        }
        clearUserStatusMessage();
        loadUsersAsync();
    }

    @FXML
    private void handleNavActivity(ActionEvent event) {
        showPane(activityPane, navActivityBtn);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        if (!ensureAdminAuthorized()) {
            return;
        }
        clearFilterError();
        reloadCurrentSelection();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        authService.logout();
        NavigationManager.navigateTo(navLogoutBtn, "/fxml/login.fxml");
    }

    // ---- Filter Handlers ----

    @FXML
    private void handleApplyFilter(ActionEvent event) {
        if (!ensureAdminAuthorized()) {
            return;
        }
        clearFilterError();
        String selectedPreset = datePresetCombo == null ? PRESET_ALL_TIME : datePresetCombo.getValue();
        if (PRESET_CUSTOM.equals(selectedPreset)) {
            LocalDate from = fromDatePicker == null ? null : fromDatePicker.getValue();
            LocalDate to = toDatePicker == null ? null : toDatePicker.getValue();
            if (from == null && to == null) {
                showFilterError("Select a From date, a To date, or both for a custom range.");
                return;
            }
            if (from != null && to != null && from.isAfter(to)) {
                showFilterError("From date cannot be after To date.");
                return;
            }
            activePreset = PRESET_CUSTOM;
            activeCustomFrom = from;
            activeCustomTo = to;
            loadAnalyticsAsync(FilterMode.CUSTOM, from, to);
        } else {
            activePreset = selectedPreset == null ? PRESET_ALL_TIME : selectedPreset;
            activeCustomFrom = null;
            activeCustomTo = null;
            applyPresetSelection(activePreset);
        }
    }

    @FXML
    private void handleResetFilter(ActionEvent event) {
        if (!ensureAdminAuthorized()) {
            return;
        }
        clearFilterError();
        activePreset = PRESET_ALL_TIME;
        activeCustomFrom = null;
        activeCustomTo = null;

        updatingControls = true;
        if (datePresetCombo != null) {
            datePresetCombo.getSelectionModel().select(PRESET_ALL_TIME);
        }
        setCustomPickersEnabled(false);
        updatingControls = false;

        loadAnalyticsAsync(FilterMode.ALL_TIME, null, null);
    }

    @FXML
    private void handleClearActivityFilter(ActionEvent event) {
        if (activityFilterCombo != null) {
            activityFilterCombo.getSelectionModel().select(ALL_EVENT_TYPES);
        }
    }

    private void applyPresetSelection(String preset) {
        switch (preset) {
            case PRESET_LAST_7 -> loadAnalyticsAsync(FilterMode.LAST_7, null, null);
            case PRESET_LAST_30 -> loadAnalyticsAsync(FilterMode.LAST_30, null, null);
            case PRESET_LAST_90 -> loadAnalyticsAsync(FilterMode.LAST_90, null, null);
            default -> loadAnalyticsAsync(FilterMode.ALL_TIME, null, null);
        }
    }

    private void reloadCurrentSelection() {
        switch (activePreset) {
            case PRESET_LAST_7 -> loadAnalyticsAsync(FilterMode.LAST_7, null, null);
            case PRESET_LAST_30 -> loadAnalyticsAsync(FilterMode.LAST_30, null, null);
            case PRESET_LAST_90 -> loadAnalyticsAsync(FilterMode.LAST_90, null, null);
            case PRESET_CUSTOM -> loadAnalyticsAsync(FilterMode.CUSTOM, activeCustomFrom, activeCustomTo);
            default -> loadAnalyticsAsync(FilterMode.ALL_TIME, null, null);
        }
    }

    private void loadAnalyticsAsync(FilterMode mode, LocalDate customFrom, LocalDate customTo) {
        if (!ensureAdminAuthorized()) {
            return;
        }
        if (!isLoading.compareAndSet(false, true)) {
            return;
        }

        setLoadingUi(true);
        setVisibleManaged(loadErrorBox, false);

        Task<LoadResult> task = new Task<>() {
            @Override
            protected LoadResult call() throws Exception {
                AdminDashboardSnapshot snapshot = switch (mode) {
                    case LAST_7 -> analyticsService.loadLastNDaysSnapshot(7);
                    case LAST_30 -> analyticsService.loadLastNDaysSnapshot(30);
                    case LAST_90 -> analyticsService.loadLastNDaysSnapshot(90);
                    case CUSTOM -> analyticsService.loadSnapshot(customFrom, customTo);
                    case ALL_TIME -> analyticsService.loadAllTimeSnapshot();
                };

                List<AdminActivityEvent> events = Collections.emptyList();
                if (snapshot != null && !snapshot.hasError()) {
                    LocalDateTime dtFrom = snapshot.getFilterFromDate() == null
                            ? null : snapshot.getFilterFromDate().atStartOfDay();
                    LocalDateTime dtTo = snapshot.getFilterToDate() == null
                            ? null : snapshot.getFilterToDate().plusDays(1).atStartOfDay();
                    try {
                        events = analyticsDAO.fetchRecentActivity(dtFrom, dtTo, ACTIVITY_LOG_LIMIT);
                    } catch (Exception ex) {
                        events = snapshot.getRecentActivity();
                    }
                }
                return new LoadResult(snapshot, events == null ? Collections.emptyList() : events);
            }
        };

        task.setOnSucceeded(e -> {
            isLoading.set(false);
            setLoadingUi(false);
            LoadResult result = task.getValue();
            if (result == null || result.snapshot() == null) {
                handleLoadFailure("Received an empty analytics response from the backend.");
                return;
            }
            if (result.snapshot().hasError()) {
                handleLoadFailure(nonEmptyOrFallback(
                        result.snapshot().getLoadError(),
                        "Failed to load analytics snapshot from database."));
                return;
            }

            lastValidSnapshot = result.snapshot();
            cachedActivityEvents = result.activityEvents();
            onSnapshotLoaded(lastValidSnapshot, cachedActivityEvents);
        });

        task.setOnFailed(e -> {
            isLoading.set(false);
            setLoadingUi(false);
            Throwable ex = task.getException();
            String message = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Unable to connect to the database or compute placement analytics."
                    : ex.getMessage();
            handleLoadFailure(message);
        });

        Thread worker = new Thread(task, "admin-analytics-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLoadingUi(boolean loading) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(loading);
            loadingIndicator.setManaged(loading);
        }
        if (navRefreshBtn != null) {
            navRefreshBtn.setDisable(loading);
        }
        if (applyFilterBtn != null) {
            applyFilterBtn.setDisable(loading);
        }
        if (resetFilterBtn != null) {
            resetFilterBtn.setDisable(loading);
        }
        if (datePresetCombo != null) {
            datePresetCombo.setDisable(loading);
        }
    }

    private void handleLoadFailure(String errorMessage) {
        if (loadErrorLabel != null) {
            String suffix = lastValidSnapshot == null
                    ? ""
                    : " Showing previously loaded data.";
            loadErrorLabel.setText("Analytics load failed: " + errorMessage + suffix);
        }
        setVisibleManaged(loadErrorBox, true);
        if (lastValidSnapshot == null && lastRefreshedLabel != null) {
            lastRefreshedLabel.setText("Load failed — click Retry");
        }
    }

    private void onSnapshotLoaded(AdminDashboardSnapshot snapshot, List<AdminActivityEvent> activityEvents) {
        setVisibleManaged(loadErrorBox, false);

        if (lastRefreshedLabel != null) {
            LocalDateTime refreshed = snapshot.getRefreshedAt();
            lastRefreshedLabel.setText(refreshed == null
                    ? "Refreshed just now"
                    : "Last refreshed: " + refreshed.format(TIME_FMT));
        }

        String rangeLabel = nonEmptyOrFallback(snapshot.getDateRangeLabel(), PRESET_ALL_TIME);
        if (activeFilterBadge != null) {
            activeFilterBadge.setText(rangeLabel);
        }
        if (filterScopeNoteLabel != null) {
            if (snapshot.getFilterFromDate() == null && snapshot.getFilterToDate() == null) {
                filterScopeNoteLabel.setText(
                        "Showing all-time metrics across user registrations, jobs, applications, resume analyses, and platform activity.");
            } else {
                filterScopeNoteLabel.setText(
                        "Date filter (" + rangeLabel + ") scopes user registrations (created_at), jobs created, applications submitted, "
                                + "resume analyses, and activity log events. Active job deadlines, profile completeness, and active resumes reflect current live state.");
            }
        }

        renderOverview(snapshot, activityEvents);
        renderJobs(snapshot);
        renderStudents(snapshot);
        renderRecruiters(snapshot);
        renderActivityLog(activityEvents);
    }

    // ---- Rendering: Overview ----

    private void renderOverview(AdminDashboardSnapshot snapshot, List<AdminActivityEvent> activityEvents) {
        AdminOverviewKPI ov = snapshot.getOverviewKPI();
        AdminJobKPI jb = snapshot.getJobKPI();
        AdminApplicationKPI ap = snapshot.getApplicationKPI();

        if (ov != null) {
            ovTotalStudentsLabel.setText(String.valueOf(ov.getTotalStudents()));
            ovStudentsSubLabel.setText(String.format("%d profiles complete (%.1f%%) · %d active PDFs",
                    ov.getStudentsCompletedProfile(),
                    ov.getStudentProfileCompletionRate(),
                    ov.getStudentsWithActivePdfResume()));

            ovTotalRecruitersLabel.setText(String.valueOf(ov.getTotalRecruiters()));
            ovRecruitersSubLabel.setText(String.format("%d company profiles complete (%.1f%%)",
                    ov.getRecruitersCompletedProfile(),
                    ov.getRecruiterProfileCompletionRate()));
        }

        if (jb != null) {
            ovTotalJobsLabel.setText(String.valueOf(jb.getTotalJobs()));
            ovJobsBreakdownLabel.setText(String.format("%d Active · %d Draft · %d Closed",
                    jb.getActiveStatusJobs(), jb.getDraftJobs(), jb.getClosedJobs()));

            ovOpenJobsLabel.setText(String.valueOf(jb.getOpenAcceptingJobs()));
            int expired = jb.getExpiredActiveJobs();
            if (expired > 0) {
                ovExpiredJobsBadge.setText(expired + " expired ACTIVE");
                setVisibleManaged(ovExpiredJobsBadge, true);
            } else {
                setVisibleManaged(ovExpiredJobsBadge, false);
            }
            ovOpenJobsSubLabel.setText(jb.getActivePostingRecruiters()
                    + (jb.getActivePostingRecruiters() == 1 ? " recruiter posting" : " recruiters posting"));
        }

        if (ap != null) {
            ovTotalAppsLabel.setText(String.valueOf(ap.getTotalApplications()));
            ovUniqueApplicantsLabel.setText(ap.getUniqueApplyingStudents() + " unique student(s) applied");

            ovPendingAppsLabel.setText(String.valueOf(ap.getPendingApplications()));
            ovPendingSubLabel.setText(ap.getTotalApplications() == 0
                    ? "No pending backlog"
                    : String.format("%.1f%% of total applications", pct(ap.getPendingApplications(), ap.getTotalApplications())));

            ovShortlistedAppsLabel.setText(String.valueOf(ap.getShortlistedApplications()));
            ovShortlistedSubLabel.setText(String.format("%d unique student(s) · %.1f%% rate",
                    ap.getUniqueShortlistedStudents(), ap.getShortlistRate()));

            ovUniqueSelectedLabel.setText(String.valueOf(ap.getUniqueSelectedStudents()));
            ovSelectedAppsSubLabel.setText(String.format("%d offer(s) across %d application(s) · %.1f%% unique hire rate",
                    ap.getSelectedApplications(), ap.getTotalApplications(), ap.getUniqueHireRate()));

            ovShortlistRateLabel.setText(String.format("%.1f%%", ap.getShortlistRate()));
            ovSelectionRateLabel.setText(String.format("%.1f%%", ap.getSelectionRate()));
            ovUniqueHireRateLabel.setText(String.format("%.1f%%", ap.getUniqueHireRate()));
        }

        renderStatusDistribution(snapshot.getStatusDistribution());
        renderFunnel(ov, ap);
        renderOverviewActivityPreview(activityEvents);
    }

    private void renderStatusDistribution(List<AdminStatusDistribution> distribution) {
        List<AdminStatusDistribution> rows = distribution == null ? Collections.emptyList() : distribution;
        int totalCount = 0;
        for (AdminStatusDistribution item : rows) {
            totalCount += item.getApplicationCount();
        }

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (AdminStatusDistribution item : rows) {
            if (item.getApplicationCount() > 0) {
                pieData.add(new PieChart.Data(
                        formatStatusDisplay(item.getStatus()) + " (" + item.getApplicationCount() + ")",
                        item.getApplicationCount()));
            }
        }

        if (totalCount == 0 || pieData.isEmpty()) {
            statusPieChart.setData(FXCollections.observableArrayList());
            setVisibleManaged(statusPieChart, false);
            setVisibleManaged(statusPieEmptyBox, true);
        } else {
            statusPieChart.setAnimated(false);
            statusPieChart.setData(pieData);
            setVisibleManaged(statusPieEmptyBox, false);
            setVisibleManaged(statusPieChart, true);
            applyPieSliceColors(rows);
        }

        statusBreakdownList.getChildren().clear();
        if (rows.isEmpty() || totalCount == 0) {
            Label empty = new Label("No application statuses to break down yet.");
            empty.getStyleClass().add("admin-empty-desc");
            statusBreakdownList.getChildren().add(empty);
            return;
        }

        for (AdminStatusDistribution item : rows) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("admin-breakdown-row");

            Label statusPill = createStatusPill(item.getStatus());
            Label countLbl = new Label(String.format("%d apps (%d unique)",
                    item.getApplicationCount(), item.getUniqueStudents()));
            countLbl.getStyleClass().add("admin-breakdown-detail");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label pctLbl = new Label(String.format("%.1f%%", item.getPercentage()));
            pctLbl.getStyleClass().add("admin-breakdown-pct");

            row.getChildren().addAll(statusPill, countLbl, spacer, pctLbl);
            statusBreakdownList.getChildren().add(row);
        }
    }

    private void applyPieSliceColors(List<AdminStatusDistribution> rows) {
        Map<String, String> colorByStatus = new LinkedHashMap<>();
        colorByStatus.put("PENDING", "#D97706");
        colorByStatus.put("SHORTLISTED", "#2563EB");
        colorByStatus.put("SELECTED", "#059669");
        colorByStatus.put("REJECTED", "#DC2626");

        List<AdminStatusDistribution> nonZero = new ArrayList<>();
        for (AdminStatusDistribution item : rows) {
            if (item.getApplicationCount() > 0) {
                nonZero.add(item);
            }
        }

        ObservableList<PieChart.Data> data = statusPieChart.getData();
        for (int i = 0; i < data.size() && i < nonZero.size(); i++) {
            PieChart.Data slice = data.get(i);
            String key = nonZero.get(i).getStatus() == null
                    ? "" : nonZero.get(i).getStatus().toUpperCase(Locale.ENGLISH);
            String color = colorByStatus.getOrDefault(key, "#64748B");
            if (slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
        }
    }

    private void renderFunnel(AdminOverviewKPI ov, AdminApplicationKPI ap) {
        funnelRowsContainer.getChildren().clear();
        int totalStudents = ov == null ? 0 : ov.getTotalStudents();
        int completedProfiles = ov == null ? 0 : ov.getStudentsCompletedProfile();
        int uniqueApplicants = ap == null ? 0 : ap.getUniqueApplyingStudents();
        int uniqueShortlisted = ap == null ? 0 : ap.getUniqueShortlistedStudents();
        int uniqueSelected = ap == null ? 0 : ap.getUniqueSelectedStudents();

        int denominator = Math.max(totalStudents, Math.max(uniqueApplicants, 1));

        funnelRowsContainer.getChildren().addAll(
                createProgressRow("Total Registered Students", totalStudents + " student(s)",
                        totalStudents == 0 ? 0.0 : 1.0, "admin-funnel-bar-navy"),
                createProgressRow("Profile Completed",
                        String.format("%d / %d (%.1f%%)", completedProfiles, totalStudents, pct(completedProfiles, totalStudents)),
                        pct(completedProfiles, denominator) / 100.0, "admin-funnel-bar-indigo"),
                createProgressRow("Applied to >= 1 Job",
                        String.format("%d / %d (%.1f%%)", uniqueApplicants, totalStudents, pct(uniqueApplicants, totalStudents)),
                        pct(uniqueApplicants, denominator) / 100.0, "admin-funnel-bar-blue"),
                createProgressRow("Shortlisted (Unique Students)",
                        String.format("%d / %d applicants (%.1f%%)",
                                uniqueShortlisted, uniqueApplicants, pct(uniqueShortlisted, uniqueApplicants)),
                        pct(uniqueShortlisted, denominator) / 100.0, "admin-funnel-bar-blue"),
                createProgressRow("Selected / Placed (Unique Students)",
                        String.format("%d / %d applicants (%.1f%%)",
                                uniqueSelected, uniqueApplicants, pct(uniqueSelected, uniqueApplicants)),
                        pct(uniqueSelected, denominator) / 100.0, "admin-funnel-bar-emerald")
        );
    }

    private void renderOverviewActivityPreview(List<AdminActivityEvent> activityEvents) {
        overviewActivityContainer.getChildren().clear();
        if (activityEvents == null || activityEvents.isEmpty()) {
            Label empty = new Label("No platform events recorded in this period.");
            empty.getStyleClass().add("admin-empty-desc");
            overviewActivityContainer.getChildren().add(empty);
            return;
        }
        int limit = Math.min(OVERVIEW_ACTIVITY_PREVIEW_LIMIT, activityEvents.size());
        for (int i = 0; i < limit; i++) {
            overviewActivityContainer.getChildren().add(createActivityRowNode(activityEvents.get(i), false));
        }
    }

    // ---- Rendering: Jobs & Recruitment ----

    private void renderJobs(AdminDashboardSnapshot snapshot) {
        AdminJobKPI jb = snapshot.getJobKPI();
        if (jb != null) {
            jbTotalLabel.setText(String.valueOf(jb.getTotalJobs()));
            jbActiveStatusLabel.setText(String.valueOf(jb.getActiveStatusJobs()));
            jbOpenLabel.setText(String.valueOf(jb.getOpenAcceptingJobs()));
            jbExpiredActiveLabel.setText(String.valueOf(jb.getExpiredActiveJobs()));
            jbDraftLabel.setText(String.valueOf(jb.getDraftJobs()));
            jbClosedLabel.setText(String.valueOf(jb.getClosedJobs()));
            int postingRecruiters = jb.getActivePostingRecruiters();
            jbPostingRecruitersLabel.setText(postingRecruiters
                    + (postingRecruiters == 1 ? " recruiter posting" : " recruiters posting")
                    + " · posted vs currently open jobs by job type");
        }

        renderJobTypeChart(snapshot.getJobTypeStats());

        List<AdminTopJob> topJobs = snapshot.getTopJobs() == null
                ? Collections.emptyList() : snapshot.getTopJobs();
        topJobsTable.setItems(FXCollections.observableArrayList(topJobs));
        updateTableHeight(topJobsTable, topJobs.size());
    }

    private void renderJobTypeChart(List<AdminJobTypeStat> stats) {
        List<AdminJobTypeStat> rows = stats == null ? Collections.emptyList() : stats;
        int totalAcrossTypes = 0;
        int maxBarCount = 0;
        for (AdminJobTypeStat stat : rows) {
            totalAcrossTypes += stat.getTotalJobs();
            maxBarCount = Math.max(maxBarCount, Math.max(stat.getTotalJobs(), stat.getOpenJobs()));
        }

        jobTypeBarChart.getData().clear();
        jobTypeSummaryRow.getChildren().clear();

        if (rows.isEmpty() || totalAcrossTypes == 0) {
            setVisibleManaged(jobTypeBarChart, false);
            setVisibleManaged(jobTypeChartEmptyBox, true);
            Label empty = new Label("No jobs available to summarize by job type.");
            empty.getStyleClass().add("admin-empty-desc");
            jobTypeSummaryRow.getChildren().add(empty);
            return;
        }

        setVisibleManaged(jobTypeChartEmptyBox, false);
        setVisibleManaged(jobTypeBarChart, true);

        int upperBound = Math.max(4, maxBarCount + 1);
        jobTypeYAxis.setLowerBound(0);
        jobTypeYAxis.setUpperBound(upperBound);
        jobTypeYAxis.setTickUnit(Math.max(1, (int) Math.ceil(upperBound / 5.0)));

        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total Jobs");
        XYChart.Series<String, Number> openSeries = new XYChart.Series<>();
        openSeries.setName("Open (Accepting)");

        for (AdminJobTypeStat stat : rows) {
            String label = formatJobType(stat.getJobType());
            totalSeries.getData().add(new XYChart.Data<>(label, stat.getTotalJobs()));
            openSeries.getData().add(new XYChart.Data<>(label, stat.getOpenJobs()));

            VBox pill = new VBox(2);
            pill.getStyleClass().add("admin-type-summary-pill");
            Label titleLbl = new Label(label);
            titleLbl.getStyleClass().add("admin-type-summary-title");
            Label detailLbl = new Label(String.format("%d total · %d open · %d closed · %d draft",
                    stat.getTotalJobs(), stat.getOpenJobs(), stat.getClosedJobs(), stat.getDraftJobs()));
            detailLbl.getStyleClass().add("admin-type-summary-sub");
            pill.getChildren().addAll(titleLbl, detailLbl);
            HBox.setHgrow(pill, Priority.ALWAYS);
            jobTypeSummaryRow.getChildren().add(pill);
        }

        jobTypeBarChart.setAnimated(false);
        jobTypeBarChart.getData().add(totalSeries);
        jobTypeBarChart.getData().add(openSeries);
    }

    // ---- Rendering: Student Readiness ----

    private void renderStudents(AdminDashboardSnapshot snapshot) {
        AdminOverviewKPI ov = snapshot.getOverviewKPI();
        AdminStudentParticipation sp = snapshot.getStudentParticipation();

        int totalStudents = sp != null ? sp.getTotalStudents() : (ov != null ? ov.getTotalStudents() : 0);
        int completedProfiles = ov == null ? 0 : ov.getStudentsCompletedProfile();
        double profileRate = ov == null ? 0.0 : ov.getStudentProfileCompletionRate();
        int activePdfResumes = ov == null ? 0 : ov.getStudentsWithActivePdfResume();
        int everResumes = ov == null ? 0 : ov.getStudentsEverUploadedResume();
        int anyResumes = ov == null ? 0 : ov.getStudentsWithAnyResume();
        double resumeRate = ov == null ? 0.0 : ov.getStudentResumeUploadRate();

        int appliedStudents = sp == null ? 0 : sp.getStudentsWhoApplied();
        int aiUsers = sp == null ? 0 : sp.getStudentsWithAiAnalysis();
        int totalAnalyses = sp == null ? 0 : sp.getTotalResumeAnalyses();
        int jobSpecificAnalyses = sp == null ? 0 : sp.getJobSpecificAnalyses();
        int generalAnalyses = sp == null ? 0 : sp.getGeneralAnalyses();
        Double avgScore = sp == null ? null : sp.getAvgResumeScore();

        stTotalStudentsLabel.setText(String.valueOf(totalStudents));
        stCompletedProfilesLabel.setText(String.valueOf(completedProfiles));
        stProfileCompletionRateLabel.setText(String.format("%.1f%% of registered students", profileRate));

        stAppliedStudentsLabel.setText(String.valueOf(appliedStudents));
        stAppliedRateLabel.setText(String.format("%.1f%% of students applied", pct(appliedStudents, totalStudents)));

        stAiUsersLabel.setText(String.valueOf(aiUsers));
        stAiTotalAnalysesLabel.setText(totalAnalyses + " total AI/ATS run(s)");
        stAvgScoreLabel.setText(avgScore == null ? "Not available" : String.format("%.1f / 100", avgScore));

        stDetailTotalAnalysesLabel.setText(String.valueOf(totalAnalyses));
        stDetailAiUsersLabel.setText(String.valueOf(aiUsers));
        stDetailJobSpecificLabel.setText(String.valueOf(jobSpecificAnalyses));
        stDetailGeneralLabel.setText(String.valueOf(generalAnalyses));
        setVisibleManaged(stAiEmptyBox, totalAnalyses == 0);

        studentProgressContainer.getChildren().clear();
        studentProgressContainer.getChildren().addAll(
                createProgressRow("Profile Completed",
                        String.format("%d / %d (%.1f%%)", completedProfiles, totalStudents, profileRate),
                        profileRate / 100.0, "admin-funnel-bar-navy"),
                createProgressRow("Active PDF Resume Uploaded",
                        String.format("%d / %d (%.1f%%) · %d with any resume, %d ever uploaded",
                                activePdfResumes, totalStudents, resumeRate, anyResumes, everResumes),
                        resumeRate / 100.0, "admin-funnel-bar-indigo"),
                createProgressRow("Job Application Participation",
                        String.format("%d / %d (%.1f%%)", appliedStudents, totalStudents, pct(appliedStudents, totalStudents)),
                        pct(appliedStudents, totalStudents) / 100.0, "admin-funnel-bar-blue"),
                createProgressRow("AI Resume Analyzer Adoption",
                        String.format("%d / %d (%.1f%%)", aiUsers, totalStudents, pct(aiUsers, totalStudents)),
                        pct(aiUsers, totalStudents) / 100.0, "admin-funnel-bar-emerald")
        );
    }

    // ---- Rendering: Recruiter Engagement ----

    private void renderRecruiters(AdminDashboardSnapshot snapshot) {
        List<AdminRecruiterEngagement> recruiters = snapshot.getRecruiterEngagement() == null
                ? Collections.emptyList() : snapshot.getRecruiterEngagement();
        if (recruiterCountBadge != null) {
            recruiterCountBadge.setText(recruiters.size() + (recruiters.size() == 1 ? " Recruiter" : " Recruiters"));
        }
        recruiterTable.setItems(FXCollections.observableArrayList(recruiters));
        updateTableHeight(recruiterTable, recruiters.size());
    }

    // ---- Rendering: Activity Log ----

    private void renderActivityLog(List<AdminActivityEvent> activityEvents) {
        cachedActivityEvents = activityEvents == null ? Collections.emptyList() : activityEvents;

        if (activityFilterCombo != null) {
            String previousSelection = activityFilterCombo.getValue();
            Set<String> eventTypes = new LinkedHashSet<>();
            eventTypes.add(ALL_EVENT_TYPES);
            for (AdminActivityEvent ev : cachedActivityEvents) {
                if (ev.getEventType() != null && !ev.getEventType().isBlank()) {
                    eventTypes.add(ev.getEventType());
                }
            }
            updatingControls = true;
            activityFilterCombo.setItems(FXCollections.observableArrayList(eventTypes));
            if (previousSelection != null && eventTypes.contains(previousSelection)) {
                activityFilterCombo.getSelectionModel().select(previousSelection);
            } else {
                activityFilterCombo.getSelectionModel().select(ALL_EVENT_TYPES);
            }
            updatingControls = false;
        }

        applyActivityFilter();
    }

    private void applyActivityFilter() {
        String selectedType = activityFilterCombo == null ? ALL_EVENT_TYPES : activityFilterCombo.getValue();
        boolean isFiltered = selectedType != null && !selectedType.isBlank() && !ALL_EVENT_TYPES.equals(selectedType);
        if (clearActivityFilterBtn != null) {
            clearActivityFilterBtn.setDisable(!isFiltered);
        }

        List<AdminActivityEvent> matching = new ArrayList<>();
        for (AdminActivityEvent ev : cachedActivityEvents) {
            if (!isFiltered || Objects.equals(selectedType, ev.getEventType())) {
                matching.add(ev);
            }
        }

        activityLogContainer.getChildren().clear();
        if (matching.isEmpty()) {
            activityCountLabel.setText("0 events");
            if (activityEmptyDescLabel != null) {
                activityEmptyDescLabel.setText(isFiltered
                        ? "No events of type \"" + selectedType + "\" matched the selected date range."
                        : "Try widening or resetting the global date filter.");
            }
            setVisibleManaged(activityEmptyBox, true);
            setVisibleManaged(activityLogContainer, false);
            return;
        }

        setVisibleManaged(activityEmptyBox, false);
        setVisibleManaged(activityLogContainer, true);
        activityCountLabel.setText(matching.size() + (matching.size() == 1 ? " event" : " events")
                + (cachedActivityEvents.size() != matching.size()
                ? " (of " + cachedActivityEvents.size() + " loaded)" : ""));

        for (AdminActivityEvent ev : matching) {
            activityLogContainer.getChildren().add(createActivityRowNode(ev, true));
        }
    }

    // ---- UI Component Helpers ----

    private VBox createProgressRow(String title, String valueText, double progress, String barStyleClass) {
        VBox wrapper = new VBox(5);
        wrapper.getStyleClass().add("admin-funnel-row");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("admin-funnel-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label valLbl = new Label(valueText);
        valLbl.getStyleClass().add("admin-funnel-value");
        header.getChildren().addAll(titleLbl, spacer, valLbl);

        ProgressBar bar = new ProgressBar(Math.max(0.0, Math.min(1.0, progress)));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().addAll("rubric-bar", "admin-funnel-bar", barStyleClass);

        wrapper.getChildren().addAll(header, bar);
        return wrapper;
    }

    private HBox createActivityRowNode(AdminActivityEvent ev, boolean tabular) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("admin-activity-row");

        Label badge = createEventTypeBadge(ev == null ? null : ev.getEventType());
        String actor = nonEmptyOrFallback(ev == null ? null : ev.getActorName(), "System");
        String summary = nonEmptyOrFallback(ev == null ? null : ev.getSummary(), "Activity recorded");
        Label timeLbl = new Label(formatDateTimeOrDash(ev == null ? null : ev.getOccurredAt(), "—"));
        timeLbl.getStyleClass().add("admin-activity-time");

        if (tabular) {
            HBox badgeCell = new HBox(badge);
            badgeCell.setAlignment(Pos.CENTER_LEFT);
            badgeCell.setMinWidth(175);
            badgeCell.setPrefWidth(175);
            badgeCell.setMaxWidth(175);

            Label actorLbl = new Label(actor);
            actorLbl.setWrapText(true);
            actorLbl.getStyleClass().add("detail-value-bold");
            actorLbl.setMinWidth(160);
            actorLbl.setPrefWidth(160);
            actorLbl.setMaxWidth(160);

            Label summaryLbl = new Label(summary);
            summaryLbl.setWrapText(true);
            summaryLbl.setMaxWidth(Double.MAX_VALUE);
            summaryLbl.getStyleClass().add("admin-activity-summary");
            HBox.setHgrow(summaryLbl, Priority.ALWAYS);

            timeLbl.setMinWidth(145);
            timeLbl.setPrefWidth(145);
            timeLbl.setMaxWidth(145);
            timeLbl.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(badgeCell, actorLbl, summaryLbl, timeLbl);
        } else {
            Label summaryLbl = new Label(actor + " — " + summary);
            summaryLbl.setWrapText(true);
            summaryLbl.setMaxWidth(Double.MAX_VALUE);
            summaryLbl.getStyleClass().add("admin-activity-summary");
            HBox.setHgrow(summaryLbl, Priority.ALWAYS);
            timeLbl.setMinWidth(Control.USE_PREF_SIZE);

            row.getChildren().addAll(badge, summaryLbl, timeLbl);
        }
        return row;
    }

    private Label createEventTypeBadge(String eventType) {
        String text = formatEventTypeLabel(eventType);
        Label badge = new Label(text);
        badge.setMinWidth(Control.USE_PREF_SIZE);

        String upper = eventType == null ? "" : eventType.toUpperCase(Locale.ENGLISH);
        if (upper.contains("RESUME") || upper.contains("ANALY")) {
            badge.getStyleClass().add("admin-badge-purple");
        } else if (upper.contains("JOB")) {
            badge.getStyleClass().add("admin-badge-info");
        } else if (upper.contains("APP")) {
            badge.getStyleClass().add("admin-badge-success");
        } else if (upper.contains("USER") || upper.contains("REGISTER") || upper.contains("STUDENT") || upper.contains("RECRUITER")) {
            badge.getStyleClass().add("admin-badge-neutral");
        } else {
            badge.getStyleClass().add("admin-badge-warn");
        }
        return badge;
    }

    private Label createStatusPill(String status) {
        Label badge = new Label(formatStatusDisplay(status));
        String upper = status == null ? "" : status.toUpperCase(Locale.ENGLISH);
        switch (upper) {
            case "PENDING" -> badge.getStyleClass().add("admin-badge-warn");
            case "SHORTLISTED" -> badge.getStyleClass().add("admin-badge-info");
            case "SELECTED" -> badge.getStyleClass().add("admin-badge-success");
            case "REJECTED" -> badge.getStyleClass().add("admin-badge-danger");
            default -> badge.getStyleClass().add("admin-badge-neutral");
        }
        return badge;
    }

    private Label createJobStatusBadge(AdminTopJob job, String text) {
        Label badge = new Label(text);
        badge.setMinWidth(Control.USE_PREF_SIZE);
        if (isExpiredActive(job)) {
            badge.getStyleClass().add("admin-badge-warn");
            return badge;
        }
        String status = job == null || job.getStatus() == null ? "" : job.getStatus().toUpperCase(Locale.ENGLISH);
        switch (status) {
            case "ACTIVE" -> badge.getStyleClass().add("admin-badge-success");
            case "CLOSED" -> badge.getStyleClass().add("admin-badge-danger");
            default -> badge.getStyleClass().add("admin-badge-neutral");
        }
        return badge;
    }

    private Label createTablePlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("admin-empty-desc");
        return label;
    }

    private void updateTableHeight(TableView<?> table, int rowCount) {
        double height = TABLE_HEADER_HEIGHT + Math.max(rowCount, 2) * TABLE_ROW_HEIGHT;
        table.setMinHeight(Control.USE_PREF_SIZE);
        table.setPrefHeight(height);
        table.setMaxHeight(Control.USE_PREF_SIZE);
    }

    // ---- View State Helpers ----

    private void showPane(VBox targetPane, Button activeNavButton) {
        VBox[] panes = {overviewPane, jobsPane, studentsPane, recruitersPane, usersPane, activityPane,
                        recruiterVerificationPane, jobModerationPane};
        for (VBox pane : panes) {
            if (pane != null) {
                setVisibleManaged(pane, pane == targetPane);
            }
        }

        Button[] navButtons = {navOverviewBtn, navJobsBtn, navStudentsBtn, navRecruitersBtn, navUsersBtn, navActivityBtn,
                               navRecruiterVerificationBtn, navJobModerationBtn};
        for (Button btn : navButtons) {
            if (btn == null) {
                continue;
            }
            btn.getStyleClass().remove(activeNavStyleClass);
            if (btn == activeNavButton && !btn.getStyleClass().contains(activeNavStyleClass)) {
                btn.getStyleClass().add(activeNavStyleClass);
            }
        }

        if (mainScrollPane != null) {
            mainScrollPane.setVvalue(0.0);
        }
    }

    private void showFilterError(String message) {
        if (filterErrorLabel != null) {
            filterErrorLabel.setText(message);
            setVisibleManaged(filterErrorLabel, true);
        }
    }

    private void clearFilterError() {
        if (filterErrorLabel != null) {
            filterErrorLabel.setText("");
            setVisibleManaged(filterErrorLabel, false);
        }
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    // ---- Formatting Helpers ----

    private static boolean isExpiredActive(AdminTopJob job) {
        return job != null
                && job.getStatus() != null
                && "ACTIVE".equalsIgnoreCase(job.getStatus())
                && job.getDeadline() != null
                && job.getDeadline().isBefore(LocalDate.now());
    }

    private static double pct(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (numerator * 100.0) / denominator;
    }

    private static String nonEmptyOrDash(String value) {
        return nonEmptyOrFallback(value, "—");
    }

    private static String nonEmptyOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String formatDateTimeOrDash(LocalDateTime dateTime, String fallback) {
        return dateTime == null ? fallback : dateTime.format(DATETIME_FMT);
    }

    private static String formatJobType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "—";
        }
        try {
            return Job.JobType.valueOf(rawType.trim().toUpperCase(Locale.ENGLISH)).label();
        } catch (IllegalArgumentException ignored) {
            return formatEventTypeLabel(rawType);
        }
    }

    private static String formatStatusDisplay(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        String lower = status.trim().toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatRecruiterCompany(AdminRecruiterEngagement rec) {
        if (rec == null) {
            return "—";
        }
        String company = nonEmptyOrDash(rec.getCompanyName());
        String industry = rec.getIndustry();
        if (!"—".equals(company) && industry != null && !industry.isBlank()) {
            return company + " (" + industry.trim() + ")";
        }
        return company;
    }

    private static String formatEventTypeLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Event";
        }
        String[] parts = raw.trim().replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ENGLISH));
            }
        }
        return sb.length() == 0 ? raw : sb.toString();
    }

    // ---- User Management Helpers ----

    private void setupUserControls() {
        if (comboRoleFilter != null) {
            comboRoleFilter.setItems(FXCollections.observableArrayList(
                    "All Roles", "STUDENT", "RECRUITER", "ADMIN"));
            comboRoleFilter.getSelectionModel().selectFirst();
        }
        if (comboStatusFilter != null) {
            comboStatusFilter.setItems(FXCollections.observableArrayList(
                    "All Statuses", "Active", "Inactive"));
            comboStatusFilter.getSelectionModel().selectFirst();
        }
        if (txtUserSearch != null) {
            txtUserSearch.setOnAction(e -> loadUsersAsync());
        }
    }

    private void setupUserTable() {
        if (userTable == null) {
            return;
        }

        userTable.setItems(userList);

        if (colUserId != null) {
            colUserId.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getId()));
        }
        if (colUserFullName != null) {
            colUserFullName.setCellValueFactory(d -> new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue().getFullName())));
        }
        if (colUserEmail != null) {
            colUserEmail.setCellValueFactory(d -> new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue().getEmail())));
        }
        if (colUserRole != null) {
            colUserRole.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                    d.getValue().getRole() != null ? d.getValue().getRole().name() : "—"));
            colUserRole.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String roleName, boolean empty) {
                    super.updateItem(roleName, empty);
                    if (empty || roleName == null || "—".equals(roleName)) {
                        setGraphic(null);
                        setText(empty ? null : "—");
                    } else {
                        Label badge = new Label(roleName);
                        badge.getStyleClass().add("admin-badge");
                        switch (roleName) {
                            case "ADMIN" -> badge.getStyleClass().add("admin-badge-info");
                            case "RECRUITER" -> badge.getStyleClass().add("admin-badge-warn");
                            default -> badge.getStyleClass().add("admin-badge-neutral");
                        }
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
        if (colUserStatus != null) {
            colUserStatus.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().isActive()));
            colUserStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Boolean active, boolean empty) {
                    super.updateItem(active, empty);
                    if (empty || active == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = new Label(active ? "Active" : "Inactive");
                        badge.getStyleClass().add("admin-badge");
                        badge.getStyleClass().add(active ? "admin-badge-success" : "admin-badge-danger");
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
        if (colUserCreatedAt != null) {
            colUserCreatedAt.setCellValueFactory(d -> {
                LocalDateTime dt = d.getValue().getCreatedAt();
                return new ReadOnlyStringWrapper(dt != null ? dt.format(DATE_FMT) : "—");
            });
        }
        if (colUserAction != null) {
            colUserAction.setCellFactory(col -> new TableCell<>() {
                private final Button actionBtn = new Button();

                {
                    actionBtn.getStyleClass().add("admin-compact-btn");
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getIndex() >= userList.size()) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        User user = userList.get(getIndex());
                        User currentAdmin = SessionManager.getInstance().getCurrentUser();
                        boolean isSelf = currentAdmin != null && currentAdmin.getId() == user.getId();

                        if (isSelf) {
                            actionBtn.setText("Current Admin");
                            actionBtn.setDisable(true);
                            actionBtn.setTooltip(new Tooltip("Cannot deactivate your own administrator account."));
                            actionBtn.getStyleClass().removeAll("primary-button", "secondary-button");
                            actionBtn.getStyleClass().add("secondary-button");
                        } else if (user.isActive()) {
                            actionBtn.setText("Deactivate");
                            actionBtn.setDisable(false);
                            actionBtn.setTooltip(new Tooltip("Deactivate this user account."));
                            actionBtn.getStyleClass().removeAll("primary-button", "secondary-button");
                            actionBtn.getStyleClass().add("secondary-button");
                            actionBtn.setOnAction(e -> confirmAndToggleUserStatus(user));
                        } else {
                            actionBtn.setText("Activate");
                            actionBtn.setDisable(false);
                            actionBtn.setTooltip(new Tooltip("Activate this user account."));
                            actionBtn.getStyleClass().removeAll("primary-button", "secondary-button");
                            actionBtn.getStyleClass().add("primary-button");
                            actionBtn.setOnAction(e -> confirmAndToggleUserStatus(user));
                        }

                        setGraphic(actionBtn);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
    }

    private void confirmAndToggleUserStatus(User user) {
        if (user == null) return;
        boolean willDeactivate = user.isActive();
        String actionName = willDeactivate ? "Deactivate" : "Activate";

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Account Moderation");
        confirmAlert.setHeaderText(actionName + " User: " + user.getFullName() + " (" + user.getRole() + ")");
        confirmAlert.setContentText("Are you sure you want to " + actionName.toLowerCase() + " account " +
                user.getEmail() + "?\n" +
                (willDeactivate ? "The user will be immediately blocked from signing in to HireHub." :
                                  "The user will be restored and allowed to sign in."));

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            executeUserStatusUpdate(user, willDeactivate);
        }
    }

    private void executeUserStatusUpdate(User user, boolean deactivating) {
        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin == null) {
            showUserStatusMessage("Action aborted: No active admin session.", true);
            return;
        }

        setVisibleManaged(userLoadingIndicator, true);
        Task<AdminUserService.ServiceResult<Void>> task = new Task<>() {
            @Override
            protected AdminUserService.ServiceResult<Void> call() {
                if (deactivating) {
                    return adminUserService.deactivateUser(
                            currentAdmin.getId(), user.getId(), "Admin dashboard deactivation");
                } else {
                    return adminUserService.activateUser(
                            currentAdmin.getId(), user.getId(), "Admin dashboard activation");
                }
            }
        };

        task.setOnSucceeded(e -> {
            setVisibleManaged(userLoadingIndicator, false);
            AdminUserService.ServiceResult<Void> res = task.getValue();
            if (res.isSuccess()) {
                showUserStatusMessage(
                        (deactivating ? "Deactivated" : "Activated") + " account for " + user.getEmail(), false);
                loadUsersAsync();
            } else {
                showUserStatusMessage("Moderation failed: " + res.getMessage(), true);
            }
        });

        task.setOnFailed(e -> {
            setVisibleManaged(userLoadingIndicator, false);
            showUserStatusMessage("Error updating account: " + task.getException().getMessage(), true);
        });

        Thread th = new Thread(task, "HireHub-UserStatusToggle");
        th.setDaemon(true);
        th.start();
    }

    private void loadUsersAsync() {
        if (!isUserLoading.compareAndSet(false, true)) {
            return;
        }

        setVisibleManaged(userLoadingIndicator, true);

        String query = txtUserSearch != null ? txtUserSearch.getText() : null;
        String roleStr = comboRoleFilter != null ? comboRoleFilter.getValue() : null;
        User.Role roleFilter = null;
        if (roleStr != null && !roleStr.isBlank() && !"All Roles".equalsIgnoreCase(roleStr)) {
            try {
                roleFilter = User.Role.valueOf(roleStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        String statusStr = comboStatusFilter != null ? comboStatusFilter.getValue() : null;
        Boolean activeFilter = null;
        if ("Active".equalsIgnoreCase(statusStr)) {
            activeFilter = Boolean.TRUE;
        } else if ("Inactive".equalsIgnoreCase(statusStr)) {
            activeFilter = Boolean.FALSE;
        }

        final User.Role finalRole = roleFilter;
        final Boolean finalActive = activeFilter;
        final String finalQuery = query;

        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() {
                AdminUserService.ServiceResult<List<User>> result =
                        adminUserService.listUsers(finalQuery, finalRole, finalActive, 200, 0);
                if (result.isSuccess()) {
                    return result.getData();
                } else {
                    throw new RuntimeException(result.getMessage());
                }
            }
        };

        task.setOnSucceeded(e -> {
            isUserLoading.set(false);
            setVisibleManaged(userLoadingIndicator, false);
            List<User> users = task.getValue();
            userList.setAll(users != null ? users : Collections.emptyList());

            if (userCountSubtitleLabel != null) {
                int total = userList.size();
                userCountSubtitleLabel.setText("Displaying " + total + " user account" + (total == 1 ? "" : "s") +
                        (finalRole != null ? " (" + finalRole + ")" : "") +
                        (finalActive != null ? (finalActive ? " [Active]" : " [Inactive]") : ""));
            }

            if (userEmptyBox != null) {
                setVisibleManaged(userEmptyBox, userList.isEmpty());
            }
        });

        task.setOnFailed(e -> {
            isUserLoading.set(false);
            setVisibleManaged(userLoadingIndicator, false);
            showUserStatusMessage("Failed to load users: " + task.getException().getMessage(), true);
        });

        Thread th = new Thread(task, "HireHub-LoadUsers");
        th.setDaemon(true);
        th.start();
    }

    private void showUserStatusMessage(String message, boolean isError) {
        if (userStatusMessageLabel != null) {
            userStatusMessageLabel.setText(message);
            userStatusMessageLabel.setStyle(isError ? "-fx-text-fill: #E53935; -fx-font-weight: bold;" :
                                                    "-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
            setVisibleManaged(userStatusMessageLabel, true);
        }
    }

    private void clearUserStatusMessage() {
        if (userStatusMessageLabel != null) {
            userStatusMessageLabel.setText("");
            setVisibleManaged(userStatusMessageLabel, false);
        }
    }

    // =========================================================================
    // RECRUITER VERIFICATION MANAGEMENT
    // =========================================================================

    private void setupRecruiterVerificationControls() {
        if (comboRecruiterStatusFilter != null) {
            comboRecruiterStatusFilter.setItems(FXCollections.observableArrayList(
                    "PENDING (Default)", "All Statuses", "VERIFIED", "REJECTED", "SUSPENDED", "UNVERIFIED"));
            comboRecruiterStatusFilter.getSelectionModel().selectFirst();
        }
        if (txtRecruiterSearch != null) {
            txtRecruiterSearch.setOnAction(e -> handleApplyRecruiterFilters(null));
        }
    }

    private void setupRecruiterVerificationTable() {
        if (recruiterVerificationTable == null) return;

        recruiterVerificationTable.setItems(recruiterList);

        if (colRecruiterCompany != null) {
            colRecruiterCompany.setCellValueFactory(d -> {
                RecruiterProfile p = d.getValue();
                if (p == null) return new ReadOnlyStringWrapper("—");
                return new ReadOnlyStringWrapper(formatRecruiterCompanyDisplay(p));
            });
        }
        if (colRecruiterEmail != null) {
            colRecruiterEmail.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getRecruiterEmail())));
        }
        if (colRecruiterIndustry != null) {
            colRecruiterIndustry.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getIndustry())));
        }
        if (colRecruiterWebsite != null) {
            colRecruiterWebsite.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getCompanyWebsite())));
        }
        if (colRecruiterStatus != null) {
            colRecruiterStatus.setCellValueFactory(d -> {
                RecruiterProfile p = d.getValue();
                return new ReadOnlyStringWrapper(p == null || p.getVerificationStatus() == null
                        ? "UNVERIFIED" : p.getVerificationStatus().name());
            });
            colRecruiterStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = createRecruiterStatusBadge(status);
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
        if (colRecruiterSubmittedAt != null) {
            colRecruiterSubmittedAt.setCellValueFactory(d -> {
                RecruiterProfile p = d.getValue();
                LocalDateTime dt = p == null ? null : p.getUpdatedAt();
                return new ReadOnlyStringWrapper(dt != null ? dt.format(DATETIME_FMT) : "—");
            });
        }
        if (colRecruiterReviewedAt != null) {
            colRecruiterReviewedAt.setCellValueFactory(d -> {
                RecruiterProfile p = d.getValue();
                LocalDateTime dt = p == null ? null : p.getVerifiedAt();
                return new ReadOnlyStringWrapper(dt != null ? dt.format(DATETIME_FMT) : "Never");
            });
        }

        recruiterVerificationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            renderRecruiterDetail(newV);
        });
    }

    private void renderRecruiterDetail(RecruiterProfile profile) {
        selectedRecruiterProfile = profile;
        if (profile == null) {
            setVisibleManaged(recruiterPlaceholderBox, true);
            setVisibleManaged(recruiterDetailContainer, false);
            return;
        }

        setVisibleManaged(recruiterPlaceholderBox, false);
        setVisibleManaged(recruiterDetailContainer, true);

        if (recDetailCompanyName != null) {
            recDetailCompanyName.setText(nonEmptyOrDash(profile.getCompanyName()));
        }
        if (recDetailRecruiterName != null) {
            String name = profile.getRecruiterName();
            recDetailRecruiterName.setText(name != null && !name.isBlank() ? "Recruiter: " + name.trim() : "Recruiter: (Not set)");
        }
        if (recDetailStatusBadge != null) {
            String st = profile.getVerificationStatus() != null ? profile.getVerificationStatus().name() : "UNVERIFIED";
            recDetailStatusBadge.setText(st);
            recDetailStatusBadge.getStyleClass().removeAll(
                    "admin-badge-neutral", "admin-badge-warn", "admin-badge-success", "admin-badge-danger", "admin-badge-suspended");
            switch (st) {
                case "VERIFIED" -> recDetailStatusBadge.getStyleClass().add("admin-badge-success");
                case "PENDING" -> recDetailStatusBadge.getStyleClass().add("admin-badge-warn");
                case "REJECTED" -> recDetailStatusBadge.getStyleClass().add("admin-badge-danger");
                case "SUSPENDED" -> recDetailStatusBadge.getStyleClass().add("admin-badge-suspended");
                default -> recDetailStatusBadge.getStyleClass().add("admin-badge-neutral");
            }
        }
        if (recDetailEmail != null) {
            recDetailEmail.setText(nonEmptyOrDash(profile.getRecruiterEmail()));
        }
        if (recDetailUserIdAndActive != null) {
            boolean active = profile.isUserActive() == null || profile.isUserActive();
            recDetailUserIdAndActive.setText("User #" + profile.getUserId() + " (" + (active ? "Active" : "Inactive") + ")");
        }
        if (recDetailRegisteredDate != null) {
            LocalDateTime reg = profile.getUserCreatedAt();
            recDetailRegisteredDate.setText(reg != null ? reg.format(DATE_FMT) : "—");
        }
        if (recDetailWebsite != null) {
            recDetailWebsite.setText(nonEmptyOrDash(profile.getCompanyWebsite()));
        }
        if (recDetailIndustry != null) {
            recDetailIndustry.setText(nonEmptyOrDash(profile.getIndustry()));
        }
        if (recDetailDescription != null) {
            recDetailDescription.setText(nonEmptyOrDash(profile.getCompanyDescription()));
        }
        if (recDetailReason != null) {
            recDetailReason.setText(nonEmptyOrFallback(profile.getVerificationReason(), "None"));
        }
        if (recDetailReviewMetadata != null) {
            LocalDateTime dt = profile.getVerifiedAt();
            Integer by = profile.getVerifiedBy();
            if (dt != null) {
                recDetailReviewMetadata.setText(dt.format(DATETIME_FMT) + (by != null && by > 0 ? " by Admin #" + by : ""));
            } else {
                recDetailReviewMetadata.setText("Not reviewed yet");
            }
        }

        // Action Buttons State Configuration
        RecruiterVerificationStatus status = profile.getVerificationStatus();
        if (btnVerifyRecruiter != null) {
            btnVerifyRecruiter.setDisable(status == RecruiterVerificationStatus.VERIFIED);
        }
        if (btnRejectRecruiter != null) {
            btnRejectRecruiter.setDisable(status == RecruiterVerificationStatus.REJECTED);
        }
        if (btnSuspendRecruiter != null) {
            btnSuspendRecruiter.setDisable(status == RecruiterVerificationStatus.SUSPENDED);
        }
        if (btnReturnRecruiterPending != null) {
            btnReturnRecruiterPending.setDisable(status == RecruiterVerificationStatus.PENDING);
        }

        loadRecruiterAuditHistoryAsync(profile.getUserId());
    }

    private void loadRecruiterKPIAsync() {
        Task<AdminModerationService.ServiceResult<AdminRecruiterVerificationKPI>> task = new Task<>() {
            @Override
            protected AdminModerationService.ServiceResult<AdminRecruiterVerificationKPI> call() {
                return moderationService.getRecruiterVerificationKPI();
            }
        };
        task.setOnSucceeded(e -> {
            var res = task.getValue();
            if (res != null && res.isSuccess() && res.getData() != null) {
                AdminRecruiterVerificationKPI kpi = res.getData();
                if (recTotalLabel != null) recTotalLabel.setText(String.valueOf(kpi.getTotalRecruiters()));
                if (recPendingLabel != null) recPendingLabel.setText(String.valueOf(kpi.getPendingCount()));
                if (recVerifiedLabel != null) recVerifiedLabel.setText(String.valueOf(kpi.getVerifiedCount()));
                if (recRejectedLabel != null) recRejectedLabel.setText(String.valueOf(kpi.getRejectedCount()));
                if (recSuspendedLabel != null) recSuspendedLabel.setText(String.valueOf(kpi.getSuspendedCount()));
                if (recUnverifiedLabel != null) recUnverifiedLabel.setText(String.valueOf(kpi.getUnverifiedCount()));
            }
        });
        Thread th = new Thread(task, "HireHub-RecruiterKPI");
        th.setDaemon(true);
        th.start();
    }

    private void loadRecruitersAsync() {
        if (!isRecruiterLoading.compareAndSet(false, true)) {
            return;
        }

        setVisibleManaged(recLoadingIndicator, true);
        final long thisSeq = ++recruiterQuerySeq;

        String query = txtRecruiterSearch != null ? txtRecruiterSearch.getText() : null;
        String statusStr = comboRecruiterStatusFilter != null ? comboRecruiterStatusFilter.getValue() : null;

        RecruiterVerificationStatus statusFilter = null;
        if (statusStr != null && !statusStr.isBlank() && !"All Statuses".equalsIgnoreCase(statusStr)) {
            if ("PENDING (Default)".equalsIgnoreCase(statusStr)) {
                statusFilter = RecruiterVerificationStatus.PENDING;
            } else {
                try {
                    statusFilter = RecruiterVerificationStatus.valueOf(statusStr.trim());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        final RecruiterVerificationStatus finalStatus = statusFilter;
        final String finalQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        Task<List<RecruiterProfile>> task = new Task<>() {
            @Override
            protected List<RecruiterProfile> call() {
                var res = moderationService.listRecruitersForModeration(finalQuery, finalStatus, 200, 0);
                if (res.isSuccess()) {
                    return res.getData();
                } else {
                    throw new RuntimeException(res.getMessage());
                }
            }
        };

        task.setOnSucceeded(e -> {
            isRecruiterLoading.set(false);
            setVisibleManaged(recLoadingIndicator, false);
            if (thisSeq != recruiterQuerySeq) {
                return;
            }

            List<RecruiterProfile> list = task.getValue();
            recruiterList.setAll(list != null ? list : Collections.emptyList());

            if (recSubtitleLabel != null) {
                int total = recruiterList.size();
                recSubtitleLabel.setText("Displaying " + total + " recruiter profile" + (total == 1 ? "" : "s") +
                        (finalStatus != null ? " [" + finalStatus + "]" : " [All Statuses]") +
                        (finalQuery != null ? " matching '" + finalQuery + "'" : ""));
            }

            if (recruiterEmptyBox != null) {
                setVisibleManaged(recruiterEmptyBox, recruiterList.isEmpty());
            }

            if (selectedRecruiterProfile != null) {
                int selectedId = selectedRecruiterProfile.getUserId();
                RecruiterProfile found = null;
                for (RecruiterProfile p : recruiterList) {
                    if (p.getUserId() == selectedId) {
                        found = p;
                        break;
                    }
                }
                if (found != null) {
                    recruiterVerificationTable.getSelectionModel().select(found);
                    renderRecruiterDetail(found);
                } else {
                    renderRecruiterDetail(null);
                }
            } else if (!recruiterList.isEmpty()) {
                recruiterVerificationTable.getSelectionModel().selectFirst();
            } else {
                renderRecruiterDetail(null);
            }
        });

        task.setOnFailed(e -> {
            isRecruiterLoading.set(false);
            setVisibleManaged(recLoadingIndicator, false);
            if (thisSeq == recruiterQuerySeq) {
                showRecruiterStatusMessage("Failed to load recruiters: " + task.getException().getMessage(), true);
            }
        });

        Thread th = new Thread(task, "HireHub-LoadRecruiters");
        th.setDaemon(true);
        th.start();
    }

    private void loadRecruiterAuditHistoryAsync(int recruiterUserId) {
        if (recruiterAuditListContainer == null) return;
        recruiterAuditListContainer.getChildren().clear();
        setVisibleManaged(recruiterAuditEmptyLabel, false);

        Task<List<AdminAuditLog>> task = new Task<>() {
            @Override
            protected List<AdminAuditLog> call() {
                var res = moderationService.getModerationLogsForRecruiter(recruiterUserId);
                return res.isSuccess() ? res.getData() : Collections.emptyList();
            }
        };

        task.setOnSucceeded(e -> {
            List<AdminAuditLog> logs = task.getValue();
            recruiterAuditListContainer.getChildren().clear();
            if (logs == null || logs.isEmpty()) {
                setVisibleManaged(recruiterAuditEmptyLabel, true);
            } else {
                setVisibleManaged(recruiterAuditEmptyLabel, false);
                for (AdminAuditLog log : logs) {
                    recruiterAuditListContainer.getChildren().add(createAuditRowNode(log));
                }
            }
        });

        Thread th = new Thread(task, "HireHub-RecruiterAudit");
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleApplyRecruiterFilters(ActionEvent event) {
        clearRecruiterStatusMessage();
        loadRecruitersAsync();
    }

    @FXML
    private void handleClearRecruiterFilters(ActionEvent event) {
        if (txtRecruiterSearch != null) {
            txtRecruiterSearch.clear();
        }
        if (comboRecruiterStatusFilter != null) {
            comboRecruiterStatusFilter.getSelectionModel().selectFirst();
        }
        clearRecruiterStatusMessage();
        loadRecruitersAsync();
    }

    @FXML
    private void handleRefreshRecruiters(ActionEvent event) {
        clearRecruiterStatusMessage();
        loadRecruiterKPIAsync();
        loadRecruitersAsync();
    }

    @FXML
    private void handleVerifyRecruiter(ActionEvent event) {
        if (selectedRecruiterProfile == null) return;
        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin == null) {
            showRecruiterStatusMessage("Action aborted: No active admin session.", true);
            return;
        }

        String companyName = nonEmptyOrFallback(selectedRecruiterProfile.getCompanyName(), "Recruiter #" + selectedRecruiterProfile.getUserId());

        if (Boolean.FALSE.equals(selectedRecruiterProfile.isUserActive())) {
            boolean confirmInactive = showConfirmDialog(
                    "Warning: Inactive Recruiter Account",
                    "Recruiter Account Is Inactive",
                    "This recruiter account is currently deactivated in User Management.\n" +
                    "Verifying will approve their company profile credentials, but the recruiter cannot log in until their account is activated.\n\n" +
                    "Do you wish to proceed with verification?");
            if (!confirmInactive) return;
        }

        boolean confirm = showConfirmDialog(
                "Confirm Recruiter Verification",
                "Verify Company Profile: " + companyName,
                "Are you sure you want to approve and verify this recruiter profile?\n\n" +
                "The recruiter will receive VERIFIED status and will be permitted to post and publish job listings to students.");
        if (!confirm) return;

        Optional<String> reasonOpt = showReasonInputDialog(
                "Verification Note",
                "Optional Verification Note for " + companyName,
                "Enter an optional verification remark or audit reason (press Confirm to accept default):",
                false);
        if (reasonOpt.isEmpty()) return;

        String reason = reasonOpt.get().isBlank() ? "Approved by Admin" : reasonOpt.get().trim();
        executeRecruiterModeration(selectedRecruiterProfile.getUserId(), RecruiterVerificationStatus.VERIFIED, reason);
    }

    @FXML
    private void handleRejectRecruiter(ActionEvent event) {
        if (selectedRecruiterProfile == null) return;
        String companyName = nonEmptyOrFallback(selectedRecruiterProfile.getCompanyName(), "Recruiter #" + selectedRecruiterProfile.getUserId());

        Optional<String> reasonOpt = showReasonInputDialog(
                "Reject Recruiter Verification",
                "Reject Verification for: " + companyName,
                "Please state why this recruiter profile is being rejected (minimum 3 characters required).\n" +
                "This explanation will be shared with the recruiter to help them rectify and resubmit their profile:",
                true);
        if (reasonOpt.isEmpty() || reasonOpt.get().isBlank()) return;

        executeRecruiterModeration(selectedRecruiterProfile.getUserId(), RecruiterVerificationStatus.REJECTED, reasonOpt.get().trim());
    }

    @FXML
    private void handleSuspendRecruiter(ActionEvent event) {
        if (selectedRecruiterProfile == null) return;
        String companyName = nonEmptyOrFallback(selectedRecruiterProfile.getCompanyName(), "Recruiter #" + selectedRecruiterProfile.getUserId());

        Optional<String> reasonOpt = showReasonInputDialog(
                "Suspend Recruiter Account",
                "Suspend Recruiter Privileges: " + companyName,
                "Warning: Suspending this recruiter immediately revokes their job publishing privileges.\n" +
                "Please enter a mandatory explanation for this suspension (minimum 3 characters required):",
                true);
        if (reasonOpt.isEmpty() || reasonOpt.get().isBlank()) return;

        executeRecruiterModeration(selectedRecruiterProfile.getUserId(), RecruiterVerificationStatus.SUSPENDED, reasonOpt.get().trim());
    }

    @FXML
    private void handleReturnRecruiterPending(ActionEvent event) {
        if (selectedRecruiterProfile == null) return;
        String companyName = nonEmptyOrFallback(selectedRecruiterProfile.getCompanyName(), "Recruiter #" + selectedRecruiterProfile.getUserId());

        boolean confirm = showConfirmDialog(
                "Return to Pending Review",
                "Return Recruiter to Pending: " + companyName,
                "Are you sure you want to return this recruiter profile to the PENDING moderation queue?\n\n" +
                "Historical audit records will be fully preserved.");
        if (!confirm) return;

        Optional<String> reasonOpt = showReasonInputDialog(
                "Review Queue Note",
                "Optional Return to Pending Note",
                "Enter an optional note explaining why this profile is returned to review queue:",
                false);
        if (reasonOpt.isEmpty()) return;

        String reason = reasonOpt.get().isBlank() ? "Returned to pending review by Admin" : reasonOpt.get().trim();
        executeRecruiterModeration(selectedRecruiterProfile.getUserId(), RecruiterVerificationStatus.PENDING, reason);
    }

    private void executeRecruiterModeration(int recruiterUserId, RecruiterVerificationStatus targetStatus, String reason) {
        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin == null) {
            showRecruiterStatusMessage("No active admin session.", true);
            return;
        }

        setVisibleManaged(recLoadingIndicator, true);
        Task<AdminModerationService.ServiceResult<Void>> task = new Task<>() {
            @Override
            protected AdminModerationService.ServiceResult<Void> call() {
                return moderationService.moderateRecruiter(currentAdmin.getId(), recruiterUserId, targetStatus, reason);
            }
        };

        task.setOnSucceeded(e -> {
            setVisibleManaged(recLoadingIndicator, false);
            var res = task.getValue();
            if (res != null && res.isSuccess()) {
                showRecruiterStatusMessage("Recruiter profile updated to " + targetStatus + " successfully.", false);
                loadRecruiterKPIAsync();
                loadRecruitersAsync();
            } else {
                showRecruiterStatusMessage("Moderation failed: " + (res != null ? res.getMessage() : "Unknown error"), true);
            }
        });

        task.setOnFailed(e -> {
            setVisibleManaged(recLoadingIndicator, false);
            showRecruiterStatusMessage("Moderation error: " + task.getException().getMessage(), true);
        });

        Thread th = new Thread(task, "HireHub-ModerateRecruiter");
        th.setDaemon(true);
        th.start();
    }

    private void showRecruiterStatusMessage(String message, boolean isError) {
        if (recruiterStatusMsgLabel != null) {
            recruiterStatusMsgLabel.setText(message);
            recruiterStatusMsgLabel.setStyle(isError ? "-fx-text-fill: #E53935; -fx-font-weight: bold;" :
                                                       "-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
            setVisibleManaged(recruiterStatusMsgLabel, true);
        }
    }

    private void clearRecruiterStatusMessage() {
        if (recruiterStatusMsgLabel != null) {
            recruiterStatusMsgLabel.setText("");
            setVisibleManaged(recruiterStatusMsgLabel, false);
        }
    }

    // =========================================================================
    // JOB MODERATION MANAGEMENT
    // =========================================================================

    private void setupJobModerationControls() {
        if (comboJobModerationStatusFilter != null) {
            comboJobModerationStatusFilter.setItems(FXCollections.observableArrayList(
                    "PENDING (Default)", "All Moderation", "APPROVED", "REJECTED", "SUSPENDED"));
            comboJobModerationStatusFilter.getSelectionModel().selectFirst();
        }
        if (comboJobLifecycleFilter != null) {
            comboJobLifecycleFilter.setItems(FXCollections.observableArrayList(
                    "All Lifecycles", "ACTIVE", "DRAFT", "CLOSED"));
            comboJobLifecycleFilter.getSelectionModel().selectFirst();
        }
        if (txtJobModSearch != null) {
            txtJobModSearch.setOnAction(e -> handleApplyJobModFilters(null));
        }
    }

    private void setupJobModerationTable() {
        if (jobModerationTable == null) return;

        jobModerationTable.setItems(jobModList);

        if (colJobModTitle != null) {
            colJobModTitle.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getTitle())));
        }
        if (colJobModCompany != null) {
            colJobModCompany.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getCompanyName())));
        }
        if (colJobModRecruiter != null) {
            colJobModRecruiter.setCellValueFactory(d -> {
                Job j = d.getValue();
                if (j == null) return new ReadOnlyStringWrapper("—");
                return new ReadOnlyStringWrapper(formatJobRecruiter(j));
            });
        }
        if (colJobModLocation != null) {
            colJobModLocation.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(nonEmptyOrDash(d.getValue() == null ? null : d.getValue().getLocation())));
        }
        if (colJobModType != null) {
            colJobModType.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(d.getValue() == null || d.getValue().getJobType() == null
                            ? "—" : d.getValue().getJobType().label()));
        }
        if (colJobModLifecycleStatus != null) {
            colJobModLifecycleStatus.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(d.getValue() == null || d.getValue().getStatus() == null
                            ? "—" : d.getValue().getStatus().name()));
            colJobModLifecycleStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = new Label(status);
                        badge.getStyleClass().add("admin-badge");
                        switch (status.toUpperCase(Locale.ENGLISH)) {
                            case "ACTIVE" -> badge.getStyleClass().add("admin-badge-info");
                            case "CLOSED" -> badge.getStyleClass().add("admin-badge-neutral");
                            case "DRAFT"  -> badge.getStyleClass().add("admin-badge-neutral");
                            default       -> badge.getStyleClass().add("admin-badge-neutral");
                        }
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
        if (colJobModModerationStatus != null) {
            colJobModModerationStatus.setCellValueFactory(d ->
                    new ReadOnlyStringWrapper(d.getValue() == null || d.getValue().getModerationStatus() == null
                            ? "PENDING" : d.getValue().getModerationStatus().name()));
            colJobModModerationStatus.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        Label badge = createJobModerationBadge(status);
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }
        if (colJobModCreatedAt != null) {
            colJobModCreatedAt.setCellValueFactory(d -> {
                Job j = d.getValue();
                LocalDateTime dt = j == null ? null : j.getCreatedAt();
                return new ReadOnlyStringWrapper(dt != null ? dt.format(DATETIME_FMT) : "—");
            });
        }
        if (colJobModDeadline != null) {
            colJobModDeadline.setCellValueFactory(d -> {
                Job j = d.getValue();
                LocalDate dl = j == null ? null : j.getDeadline();
                return new ReadOnlyStringWrapper(dl != null ? dl.format(DATE_FMT) : "No deadline");
            });
        }

        jobModerationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            renderJobDetail(newV);
        });
    }

    private void renderJobDetail(Job job) {
        selectedJobMod = job;
        if (job == null) {
            setVisibleManaged(jobModPlaceholderBox, true);
            setVisibleManaged(jobModDetailContainer, false);
            return;
        }

        setVisibleManaged(jobModPlaceholderBox, false);
        setVisibleManaged(jobModDetailContainer, true);

        if (jobModDetailTitle != null) {
            jobModDetailTitle.setText(nonEmptyOrDash(job.getTitle()));
        }
        if (jobModDetailCompany != null) {
            jobModDetailCompany.setText(nonEmptyOrDash(job.getCompanyName()));
        }
        if (jobModDetailLifecycleBadge != null) {
            String life = job.getStatus() != null ? job.getStatus().name() : "DRAFT";
            jobModDetailLifecycleBadge.setText(life);
            jobModDetailLifecycleBadge.getStyleClass().removeAll("admin-badge-info", "admin-badge-neutral", "admin-badge-danger");
            switch (life) {
                case "ACTIVE" -> jobModDetailLifecycleBadge.getStyleClass().add("admin-badge-info");
                case "CLOSED" -> jobModDetailLifecycleBadge.getStyleClass().add("admin-badge-danger");
                default -> jobModDetailLifecycleBadge.getStyleClass().add("admin-badge-neutral");
            }
        }
        if (jobModDetailModerationBadge != null) {
            String mod = job.getModerationStatus() != null ? job.getModerationStatus().name() : "PENDING";
            jobModDetailModerationBadge.setText(mod);
            jobModDetailModerationBadge.getStyleClass().removeAll(
                    "admin-badge-neutral", "admin-badge-warn", "admin-badge-success", "admin-badge-danger", "admin-badge-suspended");
            switch (mod) {
                case "APPROVED" -> jobModDetailModerationBadge.getStyleClass().add("admin-badge-success");
                case "PENDING" -> jobModDetailModerationBadge.getStyleClass().add("admin-badge-warn");
                case "REJECTED" -> jobModDetailModerationBadge.getStyleClass().add("admin-badge-danger");
                case "SUSPENDED" -> jobModDetailModerationBadge.getStyleClass().add("admin-badge-suspended");
                default -> jobModDetailModerationBadge.getStyleClass().add("admin-badge-neutral");
            }
        }
        if (jobModDetailRecruiter != null) {
            jobModDetailRecruiter.setText(formatJobRecruiter(job));
        }
        if (jobModDetailTypeAndLocation != null) {
            String typeStr = job.getJobType() != null ? job.getJobType().label() : "—";
            jobModDetailTypeAndLocation.setText(typeStr + " · " + nonEmptyOrDash(job.getLocation()));
        }
        if (jobModDetailSalary != null) {
            jobModDetailSalary.setText(nonEmptyOrFallback(job.getSalaryRange(), "Not specified"));
        }
        if (jobModDetailDates != null) {
            String dl = job.getDeadline() != null ? job.getDeadline().format(DATE_FMT) : "No deadline";
            String cr = job.getCreatedAt() != null ? job.getCreatedAt().format(DATE_FMT) : "—";
            jobModDetailDates.setText("Deadline: " + dl + " | Posted: " + cr);
        }
        if (jobModDetailSkills != null) {
            jobModDetailSkills.setText(nonEmptyOrFallback(job.getRequiredSkills(), "None specified"));
        }
        if (jobModDetailEligibility != null) {
            jobModDetailEligibility.setText(nonEmptyOrFallback(job.getEligibility(), "Open to all students"));
        }
        if (jobModDetailDesc != null) {
            jobModDetailDesc.setText(nonEmptyOrDash(job.getDescription()));
        }
        if (jobModDetailReason != null) {
            jobModDetailReason.setText(nonEmptyOrFallback(job.getModerationReason(), "None"));
        }
        if (jobModDetailReviewMetadata != null) {
            LocalDateTime dt = job.getModeratedAt();
            Integer by = job.getModeratedBy();
            if (dt != null) {
                jobModDetailReviewMetadata.setText(dt.format(DATETIME_FMT) + (by != null && by > 0 ? " by Admin #" + by : ""));
            } else {
                jobModDetailReviewMetadata.setText("Pending initial review");
            }
        }

        // Action Buttons State Configuration
        JobModerationStatus modStatus = job.getModerationStatus();
        if (btnApproveJob != null) {
            btnApproveJob.setDisable(modStatus == JobModerationStatus.APPROVED);
        }
        if (btnRejectJob != null) {
            btnRejectJob.setDisable(modStatus == JobModerationStatus.REJECTED);
        }
        if (btnSuspendJob != null) {
            btnSuspendJob.setDisable(modStatus == JobModerationStatus.SUSPENDED);
        }
        if (btnReturnJobPending != null) {
            btnReturnJobPending.setDisable(modStatus == JobModerationStatus.PENDING);
        }

        loadJobAuditHistoryAsync(job.getId());
    }

    private void loadJobKPIAsync() {
        Task<AdminModerationService.ServiceResult<AdminJobModerationKPI>> task = new Task<>() {
            @Override
            protected AdminModerationService.ServiceResult<AdminJobModerationKPI> call() {
                return moderationService.getJobModerationKPI();
            }
        };
        task.setOnSucceeded(e -> {
            var res = task.getValue();
            if (res != null && res.isSuccess() && res.getData() != null) {
                AdminJobModerationKPI kpi = res.getData();
                if (jobModTotalLabel != null) jobModTotalLabel.setText(String.valueOf(kpi.getTotalJobs()));
                if (jobModPendingLabel != null) jobModPendingLabel.setText(String.valueOf(kpi.getPendingCount()));
                if (jobModApprovedLabel != null) jobModApprovedLabel.setText(String.valueOf(kpi.getApprovedCount()));
                if (jobModRejectedLabel != null) jobModRejectedLabel.setText(String.valueOf(kpi.getRejectedCount()));
                if (jobModSuspendedLabel != null) jobModSuspendedLabel.setText(String.valueOf(kpi.getSuspendedCount()));
                if (jobModActiveLifecycleLabel != null) jobModActiveLifecycleLabel.setText(String.valueOf(kpi.getActiveLifecycleCount()));
            }
        });
        Thread th = new Thread(task, "HireHub-JobModKPI");
        th.setDaemon(true);
        th.start();
    }

    private void loadJobModerationAsync() {
        if (!isJobModLoading.compareAndSet(false, true)) {
            return;
        }

        setVisibleManaged(jobModLoadingIndicator, true);
        final long thisSeq = ++jobQuerySeq;

        String query = txtJobModSearch != null ? txtJobModSearch.getText() : null;
        String modStatusStr = comboJobModerationStatusFilter != null ? comboJobModerationStatusFilter.getValue() : null;
        String lifecycleStr = comboJobLifecycleFilter != null ? comboJobLifecycleFilter.getValue() : null;

        JobModerationStatus modStatus = null;
        if (modStatusStr != null && !modStatusStr.isBlank() && !"All Moderation".equalsIgnoreCase(modStatusStr)) {
            if ("PENDING (Default)".equalsIgnoreCase(modStatusStr)) {
                modStatus = JobModerationStatus.PENDING;
            } else {
                try {
                    modStatus = JobModerationStatus.valueOf(modStatusStr.trim());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        Job.Status lifecycle = null;
        if (lifecycleStr != null && !lifecycleStr.isBlank() && !"All Lifecycles".equalsIgnoreCase(lifecycleStr)) {
            try {
                lifecycle = Job.Status.valueOf(lifecycleStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        final JobModerationStatus finalModStatus = modStatus;
        final Job.Status finalLifecycle = lifecycle;
        final String finalQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        Task<List<Job>> task = new Task<>() {
            @Override
            protected List<Job> call() {
                var res = moderationService.listJobsForModeration(finalQuery, finalLifecycle, finalModStatus, 200, 0);
                if (res.isSuccess()) {
                    return res.getData();
                } else {
                    throw new RuntimeException(res.getMessage());
                }
            }
        };

        task.setOnSucceeded(e -> {
            isJobModLoading.set(false);
            setVisibleManaged(jobModLoadingIndicator, false);
            if (thisSeq != jobQuerySeq) {
                return;
            }

            List<Job> list = task.getValue();
            jobModList.setAll(list != null ? list : Collections.emptyList());

            if (jobModSubtitleLabel != null) {
                int total = jobModList.size();
                jobModSubtitleLabel.setText("Displaying " + total + " job listing" + (total == 1 ? "" : "s") +
                        (finalModStatus != null ? " [Mod: " + finalModStatus + "]" : " [All Moderation]") +
                        (finalLifecycle != null ? " [Life: " + finalLifecycle + "]" : "") +
                        (finalQuery != null ? " matching '" + finalQuery + "'" : ""));
            }

            if (jobModEmptyBox != null) {
                setVisibleManaged(jobModEmptyBox, jobModList.isEmpty());
            }

            if (selectedJobMod != null) {
                int selectedId = selectedJobMod.getId();
                Job found = null;
                for (Job j : jobModList) {
                    if (j.getId() == selectedId) {
                        found = j;
                        break;
                    }
                }
                if (found != null) {
                    jobModerationTable.getSelectionModel().select(found);
                    renderJobDetail(found);
                } else {
                    renderJobDetail(null);
                }
            } else if (!jobModList.isEmpty()) {
                jobModerationTable.getSelectionModel().selectFirst();
            } else {
                renderJobDetail(null);
            }
        });

        task.setOnFailed(e -> {
            isJobModLoading.set(false);
            setVisibleManaged(jobModLoadingIndicator, false);
            if (thisSeq == jobQuerySeq) {
                showJobModStatusMessage("Failed to load jobs for moderation: " + task.getException().getMessage(), true);
            }
        });

        Thread th = new Thread(task, "HireHub-LoadJobMod");
        th.setDaemon(true);
        th.start();
    }

    private void loadJobAuditHistoryAsync(int jobId) {
        if (jobAuditListContainer == null) return;
        jobAuditListContainer.getChildren().clear();
        setVisibleManaged(jobAuditEmptyLabel, false);

        Task<List<AdminAuditLog>> task = new Task<>() {
            @Override
            protected List<AdminAuditLog> call() {
                var res = moderationService.getModerationLogsForJob(jobId);
                return res.isSuccess() ? res.getData() : Collections.emptyList();
            }
        };

        task.setOnSucceeded(e -> {
            List<AdminAuditLog> logs = task.getValue();
            jobAuditListContainer.getChildren().clear();
            if (logs == null || logs.isEmpty()) {
                setVisibleManaged(jobAuditEmptyLabel, true);
            } else {
                setVisibleManaged(jobAuditEmptyLabel, false);
                for (AdminAuditLog log : logs) {
                    jobAuditListContainer.getChildren().add(createAuditRowNode(log));
                }
            }
        });

        Thread th = new Thread(task, "HireHub-JobAudit");
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleApplyJobModFilters(ActionEvent event) {
        clearJobModStatusMessage();
        loadJobModerationAsync();
    }

    @FXML
    private void handleClearJobModFilters(ActionEvent event) {
        if (txtJobModSearch != null) {
            txtJobModSearch.clear();
        }
        if (comboJobModerationStatusFilter != null) {
            comboJobModerationStatusFilter.getSelectionModel().selectFirst();
        }
        if (comboJobLifecycleFilter != null) {
            comboJobLifecycleFilter.getSelectionModel().selectFirst();
        }
        clearJobModStatusMessage();
        loadJobModerationAsync();
    }

    @FXML
    private void handleRefreshJobMod(ActionEvent event) {
        clearJobModStatusMessage();
        loadJobKPIAsync();
        loadJobModerationAsync();
    }

    @FXML
    private void handleApproveJob(ActionEvent event) {
        if (selectedJobMod == null) return;
        String jobTitle = nonEmptyOrFallback(selectedJobMod.getTitle(), "Job #" + selectedJobMod.getId());

        boolean confirm = showConfirmDialog(
                "Confirm Job Approval",
                "Approve Job Listing: " + jobTitle,
                "Are you sure you want to approve this job posting?\n\n" +
                "Placement Notice:\n" +
                "The job posting will become visible to students in the job board only if:\n" +
                "  1. The recruiter profile is VERIFIED,\n" +
                "  2. The recruiter account is ACTIVE,\n" +
                "  3. The job lifecycle status is ACTIVE, and\n" +
                "  4. The deadline has not expired.\n\n" +
                "Job lifecycle status (" + selectedJobMod.getStatus() + ") will remain unchanged.");
        if (!confirm) return;

        Optional<String> reasonOpt = showReasonInputDialog(
                "Approval Note",
                "Optional Approval Note for " + jobTitle,
                "Enter an optional approval remark or audit reason (press Confirm to accept default):",
                false);
        if (reasonOpt.isEmpty()) return;

        String reason = reasonOpt.get().isBlank() ? "Approved by Admin" : reasonOpt.get().trim();
        executeJobModeration(selectedJobMod.getId(), JobModerationStatus.APPROVED, reason);
    }

    @FXML
    private void handleRejectJob(ActionEvent event) {
        if (selectedJobMod == null) return;
        String jobTitle = nonEmptyOrFallback(selectedJobMod.getTitle(), "Job #" + selectedJobMod.getId());

        Optional<String> reasonOpt = showReasonInputDialog(
                "Reject Job Posting",
                "Reject Job: " + jobTitle,
                "Please state why this job posting is being rejected (minimum 3 characters required).\n" +
                "The reason will be recorded in the moderation history:",
                true);
        if (reasonOpt.isEmpty() || reasonOpt.get().isBlank()) return;

        executeJobModeration(selectedJobMod.getId(), JobModerationStatus.REJECTED, reasonOpt.get().trim());
    }

    @FXML
    private void handleSuspendJob(ActionEvent event) {
        if (selectedJobMod == null) return;
        String jobTitle = nonEmptyOrFallback(selectedJobMod.getTitle(), "Job #" + selectedJobMod.getId());

        Optional<String> reasonOpt = showReasonInputDialog(
                "Suspend Job Posting",
                "Suspend Job: " + jobTitle,
                "Warning: Suspending this job immediately revokes student visibility across search, recommendations, and detail views.\n" +
                "Please enter a mandatory explanation for this suspension (minimum 3 characters required):",
                true);
        if (reasonOpt.isEmpty() || reasonOpt.get().isBlank()) return;

        executeJobModeration(selectedJobMod.getId(), JobModerationStatus.SUSPENDED, reasonOpt.get().trim());
    }

    @FXML
    private void handleReturnJobPending(ActionEvent event) {
        if (selectedJobMod == null) return;
        String jobTitle = nonEmptyOrFallback(selectedJobMod.getTitle(), "Job #" + selectedJobMod.getId());

        boolean confirm = showConfirmDialog(
                "Return to Pending Moderation",
                "Return Job to Pending: " + jobTitle,
                "Are you sure you want to return this job posting to the PENDING moderation queue?\n\n" +
                "The job will be hidden from students until re-approved. Historical audit records will be fully preserved.");
        if (!confirm) return;

        Optional<String> reasonOpt = showReasonInputDialog(
                "Moderation Queue Note",
                "Optional Return to Pending Note",
                "Enter an optional note explaining why this job is returned to pending review:",
                false);
        if (reasonOpt.isEmpty()) return;

        String reason = reasonOpt.get().isBlank() ? "Returned to pending review by Admin" : reasonOpt.get().trim();
        executeJobModeration(selectedJobMod.getId(), JobModerationStatus.PENDING, reason);
    }

    private void executeJobModeration(int jobId, JobModerationStatus targetStatus, String reason) {
        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin == null) {
            showJobModStatusMessage("No active admin session.", true);
            return;
        }

        setVisibleManaged(jobModLoadingIndicator, true);
        Task<AdminModerationService.ServiceResult<Void>> task = new Task<>() {
            @Override
            protected AdminModerationService.ServiceResult<Void> call() {
                return moderationService.moderateJob(currentAdmin.getId(), jobId, targetStatus, reason);
            }
        };

        task.setOnSucceeded(e -> {
            setVisibleManaged(jobModLoadingIndicator, false);
            var res = task.getValue();
            if (res != null && res.isSuccess()) {
                showJobModStatusMessage("Job posting updated to " + targetStatus + " successfully.", false);
                loadJobKPIAsync();
                loadJobModerationAsync();
            } else {
                showJobModStatusMessage("Moderation failed: " + (res != null ? res.getMessage() : "Unknown error"), true);
            }
        });

        task.setOnFailed(e -> {
            setVisibleManaged(jobModLoadingIndicator, false);
            showJobModStatusMessage("Moderation error: " + task.getException().getMessage(), true);
        });

        Thread th = new Thread(task, "HireHub-ModerateJob");
        th.setDaemon(true);
        th.start();
    }

    private void showJobModStatusMessage(String message, boolean isError) {
        if (jobModStatusMsgLabel != null) {
            jobModStatusMsgLabel.setText(message);
            jobModStatusMsgLabel.setStyle(isError ? "-fx-text-fill: #E53935; -fx-font-weight: bold;" :
                                                    "-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
            setVisibleManaged(jobModStatusMsgLabel, true);
        }
    }

    private void clearJobModStatusMessage() {
        if (jobModStatusMsgLabel != null) {
            jobModStatusMsgLabel.setText("");
            setVisibleManaged(jobModStatusMsgLabel, false);
        }
    }

    // =========================================================================
    // DIALOG & FORMATTING HELPERS
    // =========================================================================

    private Optional<String> showReasonInputDialog(String title, String header, String prompt, boolean mandatory) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType okButtonType = new ButtonType(mandatory ? "Submit Decision" : "Confirm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        VBox content = new VBox(8);
        content.setPrefWidth(420);
        Label promptLabel = new Label(prompt);
        promptLabel.setWrapText(true);
        promptLabel.getStyleClass().add("detail-key");

        TextArea textArea = new TextArea();
        textArea.setPromptText(mandatory ? "Enter reason (minimum 3 characters required)..." : "Optional reason or review comments...");
        textArea.setPrefRowCount(4);
        textArea.setWrapText(true);

        Label charCountLabel = new Label(mandatory ? "0 / 500 (minimum 3 required)" : "0 / 500");
        charCountLabel.getStyleClass().add("admin-kpi-sub");

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okButtonType);
        if (mandatory && okBtn != null) {
            okBtn.setDisable(true);
        }

        textArea.textProperty().addListener((obs, oldV, newV) -> {
            int len = (newV == null) ? 0 : newV.trim().length();
            charCountLabel.setText(len + " / 500" + (mandatory && len < 3 ? " (minimum 3 required)" : ""));
            Button b = (Button) dialog.getDialogPane().lookupButton(okButtonType);
            if (b != null && mandatory) {
                b.setDisable(len < 3 || len > 500);
            }
        });

        content.getChildren().addAll(promptLabel, textArea, charCountLabel);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(textArea::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return textArea.getText() == null ? "" : textArea.getText().trim();
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private boolean showConfirmDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static Label createRecruiterStatusBadge(String status) {
        Label badge = new Label(status);
        badge.getStyleClass().add("admin-badge");
        if (status == null) {
            badge.getStyleClass().add("admin-badge-neutral");
            return badge;
        }
        switch (status.toUpperCase(Locale.ENGLISH)) {
            case "VERIFIED"  -> badge.getStyleClass().add("admin-badge-success");
            case "PENDING"   -> badge.getStyleClass().add("admin-badge-warn");
            case "REJECTED"  -> badge.getStyleClass().add("admin-badge-danger");
            case "SUSPENDED" -> badge.getStyleClass().add("admin-badge-suspended");
            default          -> badge.getStyleClass().add("admin-badge-neutral");
        }
        return badge;
    }

    private static Label createJobModerationBadge(String status) {
        Label badge = new Label(status);
        badge.getStyleClass().add("admin-badge");
        if (status == null) {
            badge.getStyleClass().add("admin-badge-warn");
            return badge;
        }
        switch (status.toUpperCase(Locale.ENGLISH)) {
            case "APPROVED"  -> badge.getStyleClass().add("admin-badge-success");
            case "PENDING"   -> badge.getStyleClass().add("admin-badge-warn");
            case "REJECTED"  -> badge.getStyleClass().add("admin-badge-danger");
            case "SUSPENDED" -> badge.getStyleClass().add("admin-badge-suspended");
            default          -> badge.getStyleClass().add("admin-badge-neutral");
        }
        return badge;
    }

    private static String formatRecruiterCompanyDisplay(RecruiterProfile p) {
        if (p == null) return "—";
        String comp = nonEmptyOrDash(p.getCompanyName());
        String name = p.getRecruiterName();
        if (name != null && !name.isBlank() && !name.equals(comp) && !"—".equals(comp)) {
            return comp + " (" + name.trim() + ")";
        }
        return comp;
    }

    private static String formatJobRecruiter(Job j) {
        if (j == null) return "—";
        String name = j.getRecruiterName();
        String email = j.getRecruiterEmail();
        if (name != null && !name.isBlank()) {
            return name + (email != null && !email.isBlank() ? " (" + email + ")" : "");
        }
        return email != null && !email.isBlank() ? email : "Recruiter #" + j.getRecruiterId();
    }

    private static javafx.scene.Node createAuditRowNode(AdminAuditLog log) {
        VBox item = new VBox(3);
        item.getStyleClass().add("admin-audit-item");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label actionBadge = new Label(log.getAction() != null ? log.getAction() : "ACTION");
        actionBadge.getStyleClass().add("admin-badge");
        if (log.getAction() != null) {
            String act = log.getAction().toUpperCase(Locale.ENGLISH);
            if (act.contains("VERIF") || act.contains("APPROV")) {
                actionBadge.getStyleClass().add("admin-badge-success");
            } else if (act.contains("REJECT")) {
                actionBadge.getStyleClass().add("admin-badge-danger");
            } else if (act.contains("SUSPEND")) {
                actionBadge.getStyleClass().add("admin-badge-suspended");
            } else {
                actionBadge.getStyleClass().add("admin-badge-warn");
            }
        }

        String adminInfo = log.getAdminName() != null && !log.getAdminName().isBlank()
                ? log.getAdminName()
                : "Admin #" + log.getAdminId();
        Label adminLabel = new Label("by " + adminInfo);
        adminLabel.getStyleClass().add("admin-kpi-sub");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(log.getCreatedAt() != null ? log.getCreatedAt().format(DATETIME_FMT) : "—");
        timeLabel.getStyleClass().add("admin-kpi-sub");

        top.getChildren().addAll(actionBadge, adminLabel, spacer, timeLabel);
        item.getChildren().add(top);

        if (log.getReason() != null && !log.getReason().isBlank()) {
            Label reasonLabel = new Label("Reason: " + log.getReason().trim());
            reasonLabel.setWrapText(true);
            reasonLabel.getStyleClass().add("admin-detail-sub");
            item.getChildren().add(reasonLabel);
        }

        return item;
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
