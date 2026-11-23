package com.hirehub.controller;

import com.hirehub.model.Job;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.JobService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JobBoardController — the shared job browsing screen for students.
 *
 * <p>Displays active jobs from MySQL with keyword, location, and job-type
 * filters. Job application is a Phase 3 feature — a clear entry point is
 * provided but no application is submitted.</p>
 */
public class JobBoardController {

    // ── Search / filter controls ──────────────────────────────────────────────
    @FXML private TextField       keywordField;
    @FXML private TextField       locationFilterField;
    @FXML private ComboBox<String> jobTypeFilter;
    @FXML private Button          searchBtn;
    @FXML private Button          clearBtn;

    // ── Results list ─────────────────────────────────────────────────────────
    @FXML private ListView<Job>   jobListView;
    @FXML private Label           resultCountLabel;
    @FXML private ProgressIndicator searchLoading;

    // ── Job detail panel ──────────────────────────────────────────────────────
    @FXML private VBox            detailPanel;
    @FXML private Label           detailTitle;
    @FXML private Label           detailCompany;
    @FXML private Label           detailType;
    @FXML private Label           detailLocation;
    @FXML private Label           detailSalary;
    @FXML private Label           detailDeadline;
    @FXML private TextArea        detailDescription;
    @FXML private Label           detailSkills;
    @FXML private Label           detailEligibility;
    @FXML private Button          applyBtn;
    @FXML private Label           applyNoteLabel;

    // ── Navigation ────────────────────────────────────────────────────────────
    @FXML private Button          backBtn;
    @FXML private StudentSidebarController studentSidebarController;

    // ── Services ──────────────────────────────────────────────────────────────
    private final JobService         jobService         = new JobService();
    private final ApplicationService applicationService = new ApplicationService();
    private User currentUser;
    private Job  currentlySelectedJob;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @FXML
    public void initialize() {
        if (studentSidebarController != null) {
            studentSidebarController.setActiveSection(com.hirehub.util.StudentNavSection.BROWSE_JOBS);
        }
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT)) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }

        jobTypeFilter.setItems(FXCollections.observableArrayList(
                "All Types", "Internship", "Full-Time", "Part-Time"));
        jobTypeFilter.getSelectionModel().selectFirst();

        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        searchLoading.setVisible(false);
        applyNoteLabel.setVisible(false);

        // Selection listener — show detail panel when a job is selected
        jobListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null) showDetail(selected);
                });

        // Custom cell factory — show job title + company + type as a card
        jobListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox card = new VBox(4);
                    card.getStyleClass().add("job-list-cell");

                    Label title = new Label(job.getTitle());
                    title.getStyleClass().add("job-cell-title");

                    Label sub = new Label(job.getCompanyName()
                            + "  ·  " + job.getJobType().label()
                            + (job.getLocation() != null && !job.getLocation().isBlank()
                               ? "  ·  " + job.getLocation() : ""));
                    sub.getStyleClass().add("job-cell-sub");

                    card.getChildren().addAll(title, sub);
                    setGraphic(card);
                    setText(null);
                }
            }
        });

        loadJobsAsync("", "", "");
    }

    // =========================================================================
    // Search / filter handlers
    // =========================================================================

    @FXML
    public void handleSearch(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        String keyword  = keywordField.getText().trim();
        String location = locationFilterField.getText().trim();
        String type     = jobTypeFilter.getValue();
        if ("All Types".equals(type)) type = "";
        loadJobsAsync(keyword, location, type);
    }

    @FXML
    public void handleClear(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        keywordField.clear();
        locationFilterField.clear();
        jobTypeFilter.getSelectionModel().selectFirst();
        loadJobsAsync("", "", "");
    }

    // =========================================================================
    // Navigation handlers
    // =========================================================================

    @FXML
    public void handleBack(ActionEvent e) {
        navigateTo("/fxml/student_dashboard.fxml");
    }

    @FXML
    public void handleApply(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        if (currentlySelectedJob == null) return;

        // Show dialog with optional cover letter
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Apply for Job");
        dialog.setHeaderText("Apply for: " + currentlySelectedJob.getTitle()
                + " at " + currentlySelectedJob.getCompanyName());

        ButtonType applyButtonType = new ButtonType("Submit Application", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

        TextArea coverLetterArea = new TextArea();
        coverLetterArea.setPromptText("Optional: Write a brief cover letter (max 1000 characters)…");
        coverLetterArea.setWrapText(true);
        coverLetterArea.setPrefRowCount(6);
        coverLetterArea.setPrefWidth(400);

        VBox content = new VBox(8);
        content.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));
        content.getChildren().addAll(new Label("Cover Letter (optional):"), coverLetterArea);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> btn == applyButtonType ? coverLetterArea.getText().trim() : null);

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return; // user cancelled

        final String coverLetter = result.get();
        applyBtn.setDisable(true);
        applyNoteLabel.setText("Submitting application…");
        applyNoteLabel.setStyle("-fx-text-fill: #6B7280;");
        applyNoteLabel.setVisible(true);

        final Job jobToApply = currentlySelectedJob;
        Task<ApplicationService.ServiceResult> task = new Task<>() {
            @Override protected ApplicationService.ServiceResult call() {
                return applicationService.apply(currentUser.getId(), jobToApply.getId(), coverLetter);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            ApplicationService.ServiceResult r = task.getValue();
            if (r.isSuccess()) {
                applyNoteLabel.setText("✅ " + r.getMessage());
                applyNoteLabel.setStyle("-fx-text-fill: #16A34A;");
                applyBtn.setText("Already Applied");
                applyBtn.setDisable(true);
            } else {
                applyNoteLabel.setText("⚠ " + r.getMessage());
                applyNoteLabel.setStyle("-fx-text-fill: #DC2626;");
                applyBtn.setDisable(false);
            }
        }));
        task.setOnFailed(ev -> Platform.runLater(() -> {
            applyNoteLabel.setText("⚠ Unexpected error. Please try again.");
            applyNoteLabel.setStyle("-fx-text-fill: #DC2626;");
            applyBtn.setDisable(false);
        }));
        new Thread(task, "apply-job").start();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void loadJobsAsync(String keyword, String location, String jobType) {
        searchLoading.setVisible(true);
        jobListView.setItems(FXCollections.emptyObservableList());
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);

        final String kw = keyword, loc = location, jt = jobType;
        Task<List<Job>> task = new Task<>() {
            @Override protected List<Job> call() {
                return jobService.searchJobs(kw, loc, jt);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            searchLoading.setVisible(false);
            List<Job> jobs = task.getValue();
            jobListView.setItems(FXCollections.observableArrayList(jobs));
            resultCountLabel.setText(jobs.size() + " job" + (jobs.size() == 1 ? "" : "s") + " found");
        }));
        task.setOnFailed(ev -> Platform.runLater(() -> {
            searchLoading.setVisible(false);
            resultCountLabel.setText("Error loading jobs. Please try again.");
        }));
        new Thread(task, "search-jobs").start();
    }

    private void showDetail(Job job) {
        if (currentUser == null || currentUser.getId() <= 0) return;
        currentlySelectedJob = job;
        detailTitle.setText(job.getTitle());
        detailCompany.setText(job.getCompanyName());
        detailType.setText(job.getJobType().label());
        detailLocation.setText(nvl(job.getLocation(), "—"));
        detailSalary.setText(nvl(job.getSalaryRange(), "Not disclosed"));
        detailDeadline.setText(job.getDeadline() != null ? job.getDeadline().toString() : "—");
        detailDescription.setText(nvl(job.getDescription(), ""));
        detailSkills.setText(nvl(job.getRequiredSkills(), "—"));
        detailEligibility.setText(nvl(job.getEligibility(), "—"));

        // Reset apply button state while we check
        applyBtn.setText("Apply Now");
        applyBtn.setDisable(true);
        applyNoteLabel.setText("Checking application status…");
        applyNoteLabel.setStyle("-fx-text-fill: #6B7280;");
        applyNoteLabel.setVisible(true);
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);

        // Check on background thread whether student already applied
        Task<Boolean> checkTask = new Task<>() {
            @Override protected Boolean call() {
                return applicationService.hasApplied(currentUser.getId(), job.getId());
            }
        };
        checkTask.setOnSucceeded(ev -> Platform.runLater(() -> {
            boolean alreadyApplied = checkTask.getValue();
            if (alreadyApplied) {
                applyBtn.setText("Already Applied");
                applyBtn.setDisable(true);
                applyNoteLabel.setText("You have already applied to this job.");
                applyNoteLabel.setStyle("-fx-text-fill: #6B7280;");
            } else {
                applyBtn.setText("Apply Now");
                applyBtn.setDisable(false);
                applyNoteLabel.setVisible(false);
            }
        }));
        checkTask.setOnFailed(ev -> Platform.runLater(() -> {
            applyBtn.setDisable(false);
            applyNoteLabel.setVisible(false);
        }));
        new Thread(checkTask, "check-applied").start();
    }

    private void navigateTo(String fxmlPath) {
        NavigationManager.navigateTo(backBtn, fxmlPath);
    }

    private String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}

