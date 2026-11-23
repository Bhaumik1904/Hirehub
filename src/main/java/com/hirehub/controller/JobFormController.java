package com.hirehub.controller;

import com.hirehub.model.Job;
import com.hirehub.model.User;
import com.hirehub.service.JobService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.Optional;

/**
 * JobFormController — create or edit a job listing.
 *
 * <p>Initialised by {@link RecruiterDashboardController#openJobForm(Job)}.
 * When {@code jobToEdit} is null the form creates a new job; otherwise it
 * pre-populates for editing.</p>
 */
public class JobFormController {

    @FXML private Label          formTitleLabel;

    // ── Scroll pane (for scrolling to top on error) ───────────────────────────
    @FXML private ScrollPane     formScrollPane;

    // ── Fields ──────────────────────────────────────────────────────────────
    @FXML private TextField      titleField;
    @FXML private TextField      companyNameField;
    @FXML private TextArea       descriptionArea;
    @FXML private TextField      skillsField;
    @FXML private TextField      locationField;
    @FXML private ComboBox<String> jobTypeCombo;
    @FXML private TextField      salaryRangeField;
    @FXML private TextArea       eligibilityArea;
    @FXML private DatePicker     deadlinePicker;

    // ── Action buttons ───────────────────────────────────────────────────────
    @FXML private Button         publishBtn;
    @FXML private Button         saveDraftBtn;
    @FXML private Button         cancelBtn;

    // ── Sidebar Navigation Buttons ───────────────────────────────────────────
    @FXML private Button         navDashboardBtn;
    @FXML private Button         navJobsBtn;
    @FXML private Button         navPostJobBtn;
    @FXML private Button         navProfileBtn;
    @FXML private Button         navLogoutBtn;

    // ── Status label ─────────────────────────────────────────────────────────
    @FXML private Label          statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    // ── State ─────────────────────────────────────────────────────────────────
    private int          recruiterId;
    private Job          jobToEdit;      // null → create mode
    private final JobService jobService = new JobService();
    private PauseTransition redirectTransition;
    private boolean navigationCancelled = false;
    private boolean lifecycleListenersAttached = false;

    // =========================================================================
    // Initialisation (called by parent controller)
    // =========================================================================

    @FXML
    public void initialize() {
        attachLifecycleCleanupListeners();
        ensureRecruiterAuthorized();
    }

    /**
     * Must be called immediately after the FXML is loaded.
     *
     * @param recruiterId the authenticated recruiter's user id
     * @param jobToEdit   the existing job to edit, or {@code null} to create a new one
     */
    public void init(int recruiterId, Job jobToEdit) {
        cancelPendingNavigation();
        navigationCancelled = false;
        attachLifecycleCleanupListeners();

        if (!ensureRecruiterAuthorized()) {
            return;
        }
        int sessionRecruiterId = getAuthenticatedRecruiterId();
        if (recruiterId <= 0 || recruiterId != sessionRecruiterId) {
            failClosedToLogin();
            return;
        }

        if (jobToEdit != null && (jobToEdit.getId() <= 0 || jobToEdit.getRecruiterId() != sessionRecruiterId)) {
            failClosedToLogin();
            return;
        }

        this.recruiterId = sessionRecruiterId;
        this.jobToEdit   = jobToEdit;

        jobTypeCombo.setItems(FXCollections.observableArrayList("Internship", "Full-Time", "Part-Time"));
        jobTypeCombo.getSelectionModel().selectFirst();

        statusLabel.setVisible(false);
        loadingIndicator.setVisible(false);

        if (navPostJobBtn != null && navJobsBtn != null) {
            navPostJobBtn.getStyleClass().remove("sidebar-nav-active");
            navJobsBtn.getStyleClass().remove("sidebar-nav-active");
            if (jobToEdit != null) {
                if (!navJobsBtn.getStyleClass().contains("sidebar-nav-active")) {
                    navJobsBtn.getStyleClass().add("sidebar-nav-active");
                }
            } else {
                if (!navPostJobBtn.getStyleClass().contains("sidebar-nav-active")) {
                    navPostJobBtn.getStyleClass().add("sidebar-nav-active");
                }
            }
        }

        if (jobToEdit != null) {
            formTitleLabel.setText("Edit Job Listing");
            setLoading(true);
            verifyEditableJobOwnershipAsync(jobToEdit.getId(), sessionRecruiterId);
        } else {
            formTitleLabel.setText("Post New Job");
            setLoading(false);
        }
    }

    // =========================================================================
    // Button handlers
    // =========================================================================

    /** Publishes the job as ACTIVE. */
    @FXML
    public void handlePublish(ActionEvent e) {
        if (!ensureRecruiterAuthorized()) return;
        submitJob(false);
    }

    /** Saves the job as DRAFT (visible only to the recruiter). */
    @FXML
    public void handleSaveDraft(ActionEvent e) {
        if (!ensureRecruiterAuthorized()) return;
        submitJob(true);
    }

    /** Navigates back to the recruiter dashboard without saving. */
    @FXML
    public void handleCancel(ActionEvent e) {
        navigationCancelled = true;
        cancelPendingNavigation();
        if (!ensureRecruiterAuthorized()) return;
        navigateToRecruiterDashboard();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void verifyEditableJobOwnershipAsync(int jobId, int sessionRecruiterId) {
        javafx.concurrent.Task<Optional<Job>> verifyTask = new javafx.concurrent.Task<>() {
            @Override
            protected Optional<Job> call() {
                return jobService.getJobByIdForRecruiter(jobId, sessionRecruiterId);
            }
        };
        verifyTask.setOnSucceeded(ev -> Platform.runLater(() -> {
            setLoading(false);
            if (!isSessionMatchingRecruiter(sessionRecruiterId)) {
                failClosedToLogin();
                return;
            }
            Optional<Job> ownedOpt = verifyTask.getValue();
            if (ownedOpt.isEmpty()) {
                this.jobToEdit = null;
                failClosedToLogin();
                return;
            }
            Job authoritativeJob = ownedOpt.get();
            this.jobToEdit = authoritativeJob;
            populateForm(authoritativeJob);
        }));
        verifyTask.setOnFailed(ev -> Platform.runLater(() -> {
            setLoading(false);
            this.jobToEdit = null;
            failClosedToLogin();
        }));
        new Thread(verifyTask, "verify-job-ownership").start();
    }

    private void submitJob(boolean asDraft) {
        if (!ensureRecruiterAuthorized()) return;
        int sessionRecruiterId = getAuthenticatedRecruiterId();
        if (this.recruiterId <= 0 || this.recruiterId != sessionRecruiterId) {
            failClosedToLogin();
            return;
        }
        if (jobToEdit != null && (jobToEdit.getId() <= 0 || jobToEdit.getRecruiterId() != sessionRecruiterId)) {
            failClosedToLogin();
            return;
        }

        navigationCancelled = false;
        cancelPendingNavigation();

        String title       = titleField.getText();
        String company     = companyNameField.getText();
        String description = descriptionArea.getText();
        String skills      = skillsField.getText();
        String location    = locationField.getText();
        String jobType     = jobTypeCombo.getValue();
        String salary      = salaryRangeField.getText();
        String eligibility = eligibilityArea.getText();
        LocalDate deadline = deadlinePicker.getValue();

        // Client-side pre-validation — gives instant feedback without DB round-trip
        if (title == null || title.isBlank()) {
            showError("Job title is required. (Scroll up to fill in the Job Title field.)");
            scrollToTop();
            titleField.requestFocus();
            return;
        }
        if (company == null || company.isBlank()) {
            showError("Company name is required.");
            return;
        }
        if (description == null || description.isBlank()) {
            showError("Job description is required.");
            return;
        }
        if (deadline != null && deadline.isBefore(LocalDate.now())) {
            showError("Application deadline cannot be in the past. Please select today or a future date.");
            return;
        }

        setLoading(true);
        final int submitRecruiterId = sessionRecruiterId;
        final Job targetJob = jobToEdit;

        // Run on background thread — DB IO
        new Thread(() -> {
            JobService.ServiceResult result;
            if (!isSessionMatchingRecruiter(submitRecruiterId)) {
                result = JobService.ServiceResult.fail("Recruiter session is no longer valid.");
            } else if (targetJob == null) {
                result = jobService.createJob(submitRecruiterId, title, company, description,
                        skills, location, jobType, salary, eligibility, deadline, asDraft);
            } else {
                Optional<Job> ownedOpt = jobService.getJobByIdForRecruiter(targetJob.getId(), submitRecruiterId);
                if (ownedOpt.isEmpty()) {
                    result = JobService.ServiceResult.fail("Job listing not found or you are not authorised to edit this job.");
                } else {
                    Job.Status status = asDraft ? Job.Status.DRAFT : Job.Status.ACTIVE;
                    result = jobService.updateJob(submitRecruiterId, ownedOpt.get().getId(), title, company,
                            description, skills, location, jobType, salary, eligibility, deadline, status);
                }
            }
            final JobService.ServiceResult r = result;
            Platform.runLater(() -> {
                setLoading(false);
                if (navigationCancelled) {
                    cancelPendingNavigation();
                    return;
                }
                if (!isSessionMatchingRecruiter(submitRecruiterId)) {
                    failClosedToLogin();
                    return;
                }
                if (r.isSuccess()) {
                    showSuccess(r.getMessage() + " Returning to dashboard…");
                    scheduleDashboardRedirect(submitRecruiterId);
                } else {
                    showError(r.getMessage());
                }
            });
        }, "submit-job").start();
    }

    private void scheduleDashboardRedirect(int expectedRecruiterId) {
        cancelPendingNavigation();
        if (navigationCancelled) {
            return;
        }
        redirectTransition = new PauseTransition(Duration.millis(1200));
        redirectTransition.setOnFinished(event -> {
            redirectTransition = null;
            if (navigationCancelled || !isScreenAttachedAndActive()) {
                return;
            }
            if (!isSessionMatchingRecruiter(expectedRecruiterId)) {
                failClosedToLogin();
                return;
            }
            navigateToRecruiterDashboard();
        });
        redirectTransition.playFromStart();
    }

    /** Stops any scheduled post-save navigation (called on Back or screen teardown). */
    public void cancelPendingNavigation() {
        if (redirectTransition != null) {
            redirectTransition.stop();
            redirectTransition = null;
        }
    }

    private void attachLifecycleCleanupListeners() {
        if (lifecycleListenersAttached || titleField == null) {
            return;
        }
        lifecycleListenersAttached = true;
        titleField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                navigationCancelled = true;
                cancelPendingNavigation();
            } else if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                    if (oldWin != null && newWin == null) {
                        navigationCancelled = true;
                        cancelPendingNavigation();
                    } else if (newWin != null) {
                        newWin.showingProperty().addListener((sObs, wasShowing, isShowing) -> {
                            if (wasShowing && !isShowing) {
                                navigationCancelled = true;
                                cancelPendingNavigation();
                            }
                        });
                    }
                });
            }
        });
    }

    private boolean isScreenAttachedAndActive() {
        return titleField != null
                && titleField.getScene() != null
                && (titleField.getScene().getWindow() == null || titleField.getScene().getWindow().isShowing());
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
        return true;
    }

    private int getAuthenticatedRecruiterId() {
        User user = SessionManager.getInstance().getCurrentUser();
        return (SessionManager.getInstance().hasRole(User.Role.RECRUITER) && user != null) ? user.getId() : -1;
    }

    private boolean isSessionMatchingRecruiter(int expectedRecruiterId) {
        int activeId = getAuthenticatedRecruiterId();
        return activeId > 0 && activeId == expectedRecruiterId;
    }

    private void failClosedToLogin() {
        navigationCancelled = true;
        cancelPendingNavigation();
        this.recruiterId = -1;
        this.jobToEdit = null;
        hideFormRoot();
        Platform.runLater(() -> NavigationManager.navigateTo(titleField, "/fxml/login.fxml"));
    }

    private void hideFormRoot() {
        Node root = findRootNode(formScrollPane, titleField, formTitleLabel);
        if (root != null) {
            root.setVisible(false);
            root.setManaged(false);
        }
        if (formScrollPane != null) {
            formScrollPane.setVisible(false);
            formScrollPane.setManaged(false);
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

    private void populateForm(Job job) {
        titleField.setText(nvl(job.getTitle()));
        companyNameField.setText(nvl(job.getCompanyName()));
        descriptionArea.setText(nvl(job.getDescription()));
        skillsField.setText(nvl(job.getRequiredSkills()));
        locationField.setText(nvl(job.getLocation()));
        jobTypeCombo.setValue(job.getJobType() != null ? job.getJobType().label() : "Full-Time");
        salaryRangeField.setText(nvl(job.getSalaryRange()));
        eligibilityArea.setText(nvl(job.getEligibility()));
        deadlinePicker.setValue(job.getDeadline());
    }

    private void navigateToRecruiterDashboard() {
        NavigationManager.navigateTo(titleField, "/fxml/recruiter_dashboard.fxml");
    }

    private void setLoading(boolean loading) {
        publishBtn.setDisable(loading);
        saveDraftBtn.setDisable(loading);
        loadingIndicator.setVisible(loading);
    }

    private void showError(String msg) {
        statusLabel.getStyleClass().removeAll("success-label");
        if (!statusLabel.getStyleClass().contains("error-label"))
            statusLabel.getStyleClass().add("error-label");
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
    }

    private void showSuccess(String msg) {
        statusLabel.getStyleClass().removeAll("error-label");
        if (!statusLabel.getStyleClass().contains("success-label"))
            statusLabel.getStyleClass().add("success-label");
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
    }

    /** Scrolls the form pane to the top so the Job Title field is visible. */
    private void scrollToTop() {
        if (formScrollPane != null) {
            formScrollPane.setVvalue(0.0);
        }
    }

    private String nvl(String s) { return s == null ? "" : s; }
}

