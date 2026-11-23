package com.hirehub.controller;

import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.JobService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * ApplicantManagementController — Phase 3 recruiter screen for reviewing applicants.
 *
 * <p>Displays all applicants for a selected job. Recruiter can shortlist,
 * reject, or mark candidates as selected. Ownership is enforced in
 * ApplicationService — a recruiter can only manage their own job's applicants.</p>
 */
public class ApplicantManagementController {

    // ── Header ────────────────────────────────────────────────────────────────
    @FXML private Label             jobTitleLabel;
    @FXML private Label             companyLabel;
    @FXML private Label             applicantCountLabel;
    @FXML private Button            backBtn;

    // ── Filter ────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String>  statusFilterCombo;
    @FXML private Button            refreshBtn;

    // ── Applicants table ──────────────────────────────────────────────────────
    @FXML private TableView<Application>            applicantsTable;
    @FXML private TableColumn<Application, String>  colName;
    @FXML private TableColumn<Application, String>  colEmail;
    @FXML private TableColumn<Application, String>  colCollege;
    @FXML private TableColumn<Application, String>  colDegree;
    @FXML private TableColumn<Application, String>  colSkills;
    @FXML private TableColumn<Application, String>  colAppliedDate;
    @FXML private TableColumn<Application, String>  colStatus;
    @FXML private Label                             tableStatusLabel;
    @FXML private ProgressIndicator                 loadingIndicator;

    // ── Applicant detail panel ────────────────────────────────────────────────
    @FXML private VBox    detailPanel;
    @FXML private Label   detailName;
    @FXML private Label   detailEmail;
    @FXML private Label   detailCollege;
    @FXML private Label   detailDegree;
    @FXML private Label   detailGradYear;
    @FXML private Label   detailSkills;
    @FXML private Label   detailStatus;
    @FXML private Label   detailResumeStatus;
    @FXML private Button  detailDownloadResumeBtn;
    @FXML private TextArea detailCoverLetter;

    // ── Action buttons ────────────────────────────────────────────────────────
    @FXML private Button viewProfileBtn;
    @FXML private Button viewCoverLetterBtn;
    @FXML private Button viewResumeBtn;
    @FXML private Button shortlistBtn;
    @FXML private Button selectBtn;
    @FXML private Button rejectBtn;
    @FXML private Label  actionStatusLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private final ApplicationService applicationService = new ApplicationService();
    private final JobService jobService = new JobService();
    private User currentUser;
    private Job  currentJob;
    private List<Application> allApplicants;

    private record LoadApplicantsOutcome(boolean authorized, Job verifiedJob, List<Application> applications) {}

    // =========================================================================
    // Initialisation (called by RecruiterDashboardController after FXML load)
    // =========================================================================

    /**
     * Must be called immediately after FXML load.
     *
     * @param recruiterId the expected recruiter user id (verified against active session)
     * @param job         the job whose applicants should be displayed
     */
    public void init(int recruiterId, Job job) {
        if (!ensureRecruiterAuthorized()) {
            return;
        }
        int sessionRecruiterId = getAuthenticatedRecruiterId();
        if (recruiterId <= 0 || recruiterId != sessionRecruiterId
                || job == null || job.getId() <= 0 || job.getRecruiterId() != sessionRecruiterId) {
            failClosedToLogin();
            return;
        }

        this.currentJob = job;

        jobTitleLabel.setText(nvl(job.getTitle(), ""));
        companyLabel.setText(nvl(job.getCompanyName(), ""));
        actionStatusLabel.setVisible(false);

        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        loadingIndicator.setVisible(false);

        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All Statuses", "Pending", "Shortlisted", "Rejected", "Selected"));
        statusFilterCombo.getSelectionModel().selectFirst();
        statusFilterCombo.setOnAction(e -> applyFilter());

        setupTable();
        loadApplicantsAsync();
    }

    @FXML
    public void initialize() {
        ensureRecruiterAuthorized();
    }

    // =========================================================================
    // Navigation / action handlers
    // =========================================================================

    @FXML public void handleBack(ActionEvent e) {
        if (!ensureRecruiterAuthorized()) return;
        navigateTo("/fxml/recruiter_dashboard.fxml");
    }

    @FXML public void handleRefresh(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        loadApplicantsAsync();
    }

    @FXML public void handleViewProfile(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        Application selected = applicantsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showActionError("Please select an applicant from the table first.");
            return;
        }
        showDetail(selected);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Candidate Profile");
        alert.setHeaderText(selected.getStudentName());
        String info = String.format(
                "Email: %s\nCollege: %s\nDegree: %s\nGraduation Year: %s\nSkills: %s\nStatus: %s\nApplied Date: %s",
                nvl(selected.getStudentEmail()),
                nvl(selected.getStudentCollege(), "—"),
                nvl(selected.getStudentDegree(), "—"),
                selected.getStudentGradYear() > 0 ? String.valueOf(selected.getStudentGradYear()) : "—",
                nvl(selected.getStudentSkills(), "—"),
                selected.getStatus().label(),
                selected.getAppliedAt() != null ? selected.getAppliedAt().toLocalDate().toString() : "—"
        );
        alert.setContentText(info);
        alert.showAndWait();
    }

    @FXML public void handleViewCoverLetter(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        Application selected = applicantsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showActionError("Please select an applicant from the table first.");
            return;
        }
        showDetail(selected);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Candidate Cover Letter");
        alert.setHeaderText("Cover Letter — " + selected.getStudentName());
        String letter = selected.getCoverLetter();
        alert.setContentText(letter != null && !letter.isBlank() ? letter : "(No cover letter provided by candidate)");
        alert.showAndWait();
    }

    @FXML public void handleViewResume(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        Application selected = applicantsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showActionError("Please select an applicant from the table first.");
            return;
        }
        if (selected.getResumeId() == null || selected.getResumeId() <= 0) {
            showActionError("Resume not available for this application.");
            return;
        }

        final int applicationId = selected.getId();
        final String studentName = selected.getStudentName();
        showActionSuccess("Fetching candidate resume…");

        Task<ApplicationService.ResumeAccessResult> task = new Task<>() {
            @Override protected ApplicationService.ResumeAccessResult call() {
                return applicationService.getResumeForApplication(applicationId);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            ApplicationService.ResumeAccessResult result = task.getValue();
            if (result != null && result.isSuccess()) {
                File file = result.getFile();
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(file);
                        showActionSuccess("Opening resume: " + file.getName());
                    } else {
                        promptDownloadFallback(file, result.getResume().getFileName(), studentName);
                    }
                } catch (Exception ex) {
                    System.err.println("[ApplicantManagementController] Desktop open failed: " + ex.getMessage());
                    promptDownloadFallback(file, result.getResume().getFileName(), studentName);
                }
            } else {
                showActionError(result != null ? result.getMessage() : "Could not retrieve resume.");
            }
        }));
        task.setOnFailed(ev -> Platform.runLater(() ->
                showActionError("Unexpected error retrieving resume.")));
        new Thread(task, "view-resume").start();
    }

    @FXML public void handleDownloadResume(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        Application selected = applicantsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showActionError("Please select an applicant from the table first.");
            return;
        }
        if (selected.getResumeId() == null || selected.getResumeId() <= 0) {
            showActionError("Resume not available for this application.");
            return;
        }

        final int applicationId = selected.getId();
        final String studentName = selected.getStudentName();
        showActionSuccess("Preparing resume for download…");

        Task<ApplicationService.ResumeAccessResult> task = new Task<>() {
            @Override protected ApplicationService.ResumeAccessResult call() {
                return applicationService.getResumeForApplication(applicationId);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            ApplicationService.ResumeAccessResult result = task.getValue();
            if (result != null && result.isSuccess()) {
                promptDownloadFallback(result.getFile(), result.getResume().getFileName(), studentName);
            } else {
                showActionError(result != null ? result.getMessage() : "Could not retrieve resume.");
            }
        }));
        task.setOnFailed(ev -> Platform.runLater(() ->
                showActionError("Unexpected error retrieving resume.")));
        new Thread(task, "download-resume").start();
    }

    private void promptDownloadFallback(File sourceFile, String originalFileName, String candidateName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Candidate Resume — " + candidateName);
        String defaultName = (originalFileName != null && !originalFileName.isBlank()) ? originalFileName : "resume.pdf";
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        File targetFile = fileChooser.showSaveDialog(backBtn.getScene().getWindow());
        if (targetFile != null) {
            try {
                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showActionSuccess("Resume saved successfully: " + targetFile.getName());
            } catch (Exception ex) {
                System.err.println("[ApplicantManagementController] Save failed: " + ex.getMessage());
                showActionError("Could not save file: " + ex.getMessage());
            }
        }
    }

    @FXML public void handleShortlist(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        updateSelectedStatus(Application.Status.SHORTLISTED);
    }

    @FXML public void handleSelect(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        updateSelectedStatus(Application.Status.SELECTED);
    }

    @FXML public void handleReject(ActionEvent e) {
        if (!ensureAuthorizedForCurrentJob()) return;
        updateSelectedStatus(Application.Status.REJECTED);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void setupTable() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStudentName())));
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStudentEmail())));
        colCollege.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStudentCollege(), "—")));
        colDegree.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStudentDegree(), "—")));
        colSkills.setCellValueFactory(d -> new SimpleStringProperty(nvl(d.getValue().getStudentSkills(), "—")));
        colAppliedDate.setCellValueFactory(d -> {
            var dt = d.getValue().getAppliedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "—");
        });
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().label()));

        // Color-coded status cell
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "Shortlisted" -> "-fx-text-fill: #2563EB; -fx-font-weight: bold;";
                    case "Selected"    -> "-fx-text-fill: #16A34A; -fx-font-weight: bold;";
                    case "Rejected"    -> "-fx-text-fill: #DC2626; -fx-font-weight: bold;";
                    default            -> "-fx-text-fill: #6B7280;";
                });
            }
        });

        applicantsTable.setPlaceholder(new Label("No applicants yet for this job."));

        applicantsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> { if (selected != null) showDetail(selected); });
    }

    private void loadApplicantsAsync() {
        if (!ensureAuthorizedForCurrentJob()) return;
        final int recruiterId = getAuthenticatedRecruiterId();
        final int jobId = currentJob.getId();

        loadingIndicator.setVisible(true);
        applicantsTable.setItems(FXCollections.emptyObservableList());
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);

        Task<LoadApplicantsOutcome> task = new Task<>() {
            @Override protected LoadApplicantsOutcome call() {
                Optional<Job> verifiedJob = jobService.getJobByIdForRecruiter(jobId, recruiterId);
                if (verifiedJob.isEmpty()) {
                    return new LoadApplicantsOutcome(false, null, Collections.emptyList());
                }
                List<Application> apps = applicationService.getApplicationsForRecruiterJob(jobId, recruiterId);
                return new LoadApplicantsOutcome(true, verifiedJob.get(), apps);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            if (!isSessionMatchingRecruiter(recruiterId)) {
                failClosedToLogin();
                return;
            }
            LoadApplicantsOutcome outcome = task.getValue();
            if (outcome == null || !outcome.authorized()) {
                allApplicants = Collections.emptyList();
                applicantsTable.setItems(FXCollections.emptyObservableList());
                failClosedToLogin();
                return;
            }
            currentJob = outcome.verifiedJob();
            jobTitleLabel.setText(nvl(currentJob.getTitle(), ""));
            companyLabel.setText(nvl(currentJob.getCompanyName(), ""));
            allApplicants = outcome.applications();
            applyFilter();
            applicantCountLabel.setText(allApplicants.size() + " applicant" +
                    (allApplicants.size() == 1 ? "" : "s"));
        }));
        task.setOnFailed(ev -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            tableStatusLabel.setText("Error loading applicants.");
            tableStatusLabel.setVisible(true);
        }));
        new Thread(task, "load-applicants").start();
    }

    private void applyFilter() {
        if (allApplicants == null) return;
        String status = statusFilterCombo.getValue();
        List<Application> filtered = allApplicants.stream()
                .filter(a -> "All Statuses".equals(status) || a.getStatus().label().equals(status))
                .toList();
        applicantsTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void showDetail(Application app) {
        detailName.setText(nvl(app.getStudentName()));
        detailEmail.setText(nvl(app.getStudentEmail()));
        detailCollege.setText(nvl(app.getStudentCollege(), "—"));
        detailDegree.setText(nvl(app.getStudentDegree(), "—"));
        detailGradYear.setText(app.getStudentGradYear() > 0 ? String.valueOf(app.getStudentGradYear()) : "—");
        detailSkills.setText(nvl(app.getStudentSkills(), "—"));
        detailStatus.setText(app.getStatus().label());

        if (app.getResumeId() == null || app.getResumeId() <= 0) {
            if (viewResumeBtn != null) viewResumeBtn.setDisable(true);
            if (detailResumeStatus != null) {
                detailResumeStatus.setText("Resume not available (legacy application)");
                detailResumeStatus.setStyle("-fx-text-fill: #9CA3AF; -fx-font-style: italic;");
            }
            if (detailDownloadResumeBtn != null) detailDownloadResumeBtn.setDisable(true);
        } else {
            if (viewResumeBtn != null) viewResumeBtn.setDisable(false);
            if (detailResumeStatus != null) {
                detailResumeStatus.setText("Attached (Snapshot #" + app.getResumeId() + ")");
                detailResumeStatus.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold;");
            }
            if (detailDownloadResumeBtn != null) detailDownloadResumeBtn.setDisable(false);
        }

        detailCoverLetter.setText(app.getCoverLetter() != null ? app.getCoverLetter() : "(No cover letter)");
        actionStatusLabel.setVisible(false);
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void updateSelectedStatus(Application.Status newStatus) {
        if (!ensureAuthorizedForCurrentJob()) return;
        final int recruiterId = getAuthenticatedRecruiterId();

        Application selected = applicantsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showActionError("Please select an applicant first.");
            return;
        }
        final int applicationId = selected.getId();

        Task<ApplicationService.ServiceResult> task = new Task<>() {
            @Override protected ApplicationService.ServiceResult call() {
                return applicationService.updateApplicationStatusForRecruiter(
                        applicationId, recruiterId, newStatus);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            if (!isSessionMatchingRecruiter(recruiterId)) {
                failClosedToLogin();
                return;
            }
            ApplicationService.ServiceResult r = task.getValue();
            if (r.isSuccess()) {
                showActionSuccess(r.getMessage());
                loadApplicantsAsync();
            } else {
                showActionError(r.getMessage());
            }
        }));
        new Thread(task, "update-app-status").start();
    }

    private void navigateTo(String fxmlPath) {
        NavigationManager.navigateTo(backBtn, fxmlPath);
    }

    private void showActionSuccess(String msg) {
        actionStatusLabel.getStyleClass().removeAll("error-label");
        if (!actionStatusLabel.getStyleClass().contains("success-label"))
            actionStatusLabel.getStyleClass().add("success-label");
        actionStatusLabel.setText(msg);
        actionStatusLabel.setVisible(true);
    }

    private void showActionError(String msg) {
        actionStatusLabel.getStyleClass().removeAll("success-label");
        if (!actionStatusLabel.getStyleClass().contains("error-label"))
            actionStatusLabel.getStyleClass().add("error-label");
        actionStatusLabel.setText(msg);
        actionStatusLabel.setVisible(true);
    }

    private boolean ensureRecruiterAuthorized() {
        SessionManager session = SessionManager.getInstance();
        if (!session.hasRole(User.Role.RECRUITER)) {
            failClosedToLogin();
            return false;
        }
        User user = session.getCurrentUser();
        if (user == null || user.getId() <= 0) {
            failClosedToLogin();
            return false;
        }
        this.currentUser = user;
        return true;
    }

    private boolean ensureAuthorizedForCurrentJob() {
        if (!ensureRecruiterAuthorized()) {
            return false;
        }
        int recruiterId = getAuthenticatedRecruiterId();
        if (currentJob == null || currentJob.getId() <= 0 || currentJob.getRecruiterId() != recruiterId) {
            failClosedToLogin();
            return false;
        }
        return true;
    }

    private int getAuthenticatedRecruiterId() {
        SessionManager session = SessionManager.getInstance();
        User user = session.getCurrentUser();
        if (!session.hasRole(User.Role.RECRUITER) || user == null || user.getId() <= 0) {
            return -1;
        }
        this.currentUser = user;
        return user.getId();
    }

    private boolean isSessionMatchingRecruiter(int expectedRecruiterId) {
        int activeId = getAuthenticatedRecruiterId();
        return activeId > 0 && activeId == expectedRecruiterId;
    }

    private void failClosedToLogin() {
        this.currentUser = null;
        this.currentJob = null;
        this.allApplicants = Collections.emptyList();
        hideApplicantRoot();
        Platform.runLater(() -> NavigationManager.navigateTo(backBtn, "/fxml/login.fxml"));
    }

    private void hideApplicantRoot() {
        Node root = findRootNode(applicantsTable, detailPanel, backBtn, jobTitleLabel);
        if (root != null) {
            root.setVisible(false);
            root.setManaged(false);
        }
        if (applicantsTable != null) {
            applicantsTable.setVisible(false);
            applicantsTable.setManaged(false);
        }
        if (detailPanel != null) {
            detailPanel.setVisible(false);
            detailPanel.setManaged(false);
        }
    }

    private Node findRootNode(Node... candidates) {
        for (Node candidate : candidates) {
            if (candidate == null) continue;
            Node current = candidate;
            while (current.getParent() != null) {
                current = current.getParent();
            }
            return current;
        }
        return null;
    }

    private String nvl(String s)               { return (s == null || s.isBlank()) ? "—" : s; }
    private String nvl(String s, String fb)    { return (s == null || s.isBlank()) ? fb : s; }
}

