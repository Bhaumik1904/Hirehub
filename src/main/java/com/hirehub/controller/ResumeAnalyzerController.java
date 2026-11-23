package com.hirehub.controller;

import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.util.MarkdownRenderer;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ResumeAnalyzerController — Phase 4 Controller for the AI Resume Analyzer screen.
 */
public class ResumeAnalyzerController {

    // ── Header & Navigation ──────────────────────────────────────────────────
    @FXML private Button backBtn;
    @FXML private ScrollPane contentScrollPane;
    @FXML private StudentSidebarController studentSidebarController;

    // ── 1. My Resume Card ────────────────────────────────────────────────────
    @FXML private Label resumeStatusBadge;
    @FXML private VBox noResumeBox;
    @FXML private VBox activeResumeBox;
    @FXML private Button uploadResumeBtn;
    @FXML private Button replaceResumeBtn;
    @FXML private Button deleteResumeBtn;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Label uploadDateLabel;
    @FXML private Label fileHashLabel;
    @FXML private Label uploadFeedbackLabel;

    // ── 2. Extracted Profile Card ────────────────────────────────────────────
    @FXML private VBox extractedInfoCard;
    @FXML private Label extractedNameLabel;
    @FXML private Label extractedEmailLabel;
    @FXML private Label extractedPhoneLabel;
    @FXML private Label extractedEduLabel;
    @FXML private Label extractedLinksLabel;
    @FXML private FlowPane skillsFlowPane;
    @FXML private Button syncProfileBtn;
    @FXML private Label syncStatusLabel;

    // ── 3. Target Job Selection Card ─────────────────────────────────────────
    @FXML private ComboBox<Job> jobComboBox;
    @FXML private Button clearJobSelectionBtn;
    @FXML private VBox jobDetailsBox;
    @FXML private Label jobTitleHeaderLabel;
    @FXML private Label jobCompanyLabel;
    @FXML private Label jobLocationTypeLabel;
    @FXML private Label jobSkillsLabel;

    // ── 4. Deterministic Compatibility Score Card ────────────────────────────
    @FXML private VBox scoreCard;
    @FXML private Label scoreSectionTitleLabel;
    @FXML private Label confidenceBadgeLabel;
    @FXML private Label overallScoreNumberLabel;
    @FXML private Label overallScoreTypeLabel;
    @FXML private Label rubric1TitleLabel;
    @FXML private Label skillsScoreLabel;
    @FXML private ProgressBar skillsProgressBar;
    @FXML private Label skillsBreakdownDetailLabel;

    @FXML private Label expScoreLabel;
    @FXML private ProgressBar expProgressBar;
    @FXML private Label expBreakdownDetailLabel;

    @FXML private Label eduScoreLabel;
    @FXML private ProgressBar eduProgressBar;
    @FXML private Label eduBreakdownDetailLabel;

    @FXML private Label compScoreLabel;
    @FXML private ProgressBar compProgressBar;
    @FXML private Label compBreakdownDetailLabel;

    @FXML private Label certScoreLabel;
    @FXML private ProgressBar certProgressBar;
    @FXML private Label certBreakdownDetailLabel;

    @FXML private VBox unevaluatedCriteriaBox;
    @FXML private Label unevaluatedCriteriaLabel;

    @FXML private Label matchingSkillsTitleLabel;
    @FXML private FlowPane matchingSkillsFlow;
    @FXML private VBox missingSkillsBox;
    @FXML private FlowPane missingSkillsFlow;

    // ── 5. AI Deep Analysis Card ─────────────────────────────────────────────
    @FXML private VBox aiActionCard;
    @FXML private Button generateAiReviewBtn;
    @FXML private HBox aiLoadingBox;
    @FXML private ProgressIndicator aiProgressIndicator;
    @FXML private Label aiLoadingStatusLabel;
    @FXML private VBox aiReportContainer;
    @FXML private Label aiReportMetaLabel;
    @FXML private Button copyReportBtn;
    @FXML private VBox aiMarkdownContentBox;
    @FXML private VBox aiRetryCard;
    @FXML private Label aiServiceNoticeLabel;
    @FXML private Button retryAiAnalysisBtn;

    // ── 6. Analysis History TableView ────────────────────────────────────────
    @FXML private TableView<ResumeAnalysis> historyTableView;
    @FXML private TableColumn<ResumeAnalysis, String> colDate;
    @FXML private TableColumn<ResumeAnalysis, String> colJobTitle;
    @FXML private TableColumn<ResumeAnalysis, String> colCompany;
    @FXML private TableColumn<ResumeAnalysis, String> colScore;
    @FXML private TableColumn<ResumeAnalysis, String> colSkillsScore;
    @FXML private TableColumn<ResumeAnalysis, Void>   colAction;

    // ── Services & State ─────────────────────────────────────────────────────
    private final ResumeService         resumeService   = new ResumeService();
    private final ResumeAnalysisService analysisService = new ResumeAnalysisService();
    private final JobService            jobService      = new JobService();

    private User                            currentUser;
    private Resume                          currentResume;
    private ParsedResume                    currentParsed;
    private ResumeAnalysisService.ScoringBreakdown currentBreakdown;
    private String                          lastGeneratedReport;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @FXML
    public void initialize() {
        if (studentSidebarController != null) {
            studentSidebarController.setActiveSection(com.hirehub.util.StudentNavSection.RESUME_ANALYZER);
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

        if (aiRetryCard != null) {
            aiRetryCard.setVisible(false);
            aiRetryCard.setManaged(false);
        }
        if (aiReportMetaLabel != null) {
            aiReportMetaLabel.setVisible(false);
            aiReportMetaLabel.setManaged(false);
        }

        setupJobComboBox();
        setupHistoryTable();
        loadActiveResume();
        loadHistory();
    }

    // =========================================================================
    // Initialization & Setup
    // =========================================================================

    private void setupJobComboBox() {
        List<Job> activeJobs = jobService.searchJobs("", "", "");
        ObservableList<Job> jobList = FXCollections.observableArrayList(activeJobs);
        jobComboBox.setItems(jobList);

        jobComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Job j, boolean empty) {
                super.updateItem(j, empty);
                if (empty || j == null) {
                    setText(null);
                } else {
                    setText(j.getTitle() + " — " + j.getCompanyName() + " (" + j.getLocation() + ")");
                }
            }
        });

        jobComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Job j, boolean empty) {
                super.updateItem(j, empty);
                if (empty || j == null) {
                    setText("Select a target job listing…");
                } else {
                    setText(j.getTitle() + " — " + j.getCompanyName());
                }
            }
        });

        jobComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newJob) -> {
            if (newJob != null) {
                showJobDetails(newJob);
                if (currentParsed != null) {
                    runDeterministicScoring(currentParsed, newJob);
                }
            } else {
                jobDetailsBox.setVisible(false);
                jobDetailsBox.setManaged(false);
                if (currentParsed != null) {
                    runDeterministicScoring(currentParsed, null);
                }
            }
        });
    }

    @FXML
    public void handleClearJobSelection(ActionEvent event) {
        jobComboBox.getSelectionModel().clearSelection();
        jobDetailsBox.setVisible(false);
        jobDetailsBox.setManaged(false);
        if (currentParsed != null) {
            runDeterministicScoring(currentParsed, null);
        }
    }

    private void setupHistoryTable() {
        colDate.setCellValueFactory(cellData -> {
            var ts = cellData.getValue().getCreatedAt();
            return new SimpleStringProperty(ts != null ? ts.format(DATE_FORMATTER) : "—");
        });

        colJobTitle.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getJobTitle() != null ? cellData.getValue().getJobTitle() : "Target Job"));

        colCompany.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCompanyName() != null ? cellData.getValue().getCompanyName() : "—"));

        colScore.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getOverallScore() + "%"));

        colSkillsScore.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSkillsScore() + " / 40 pts"));

        // Add action button to view saved report
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View Report");
            {
                viewBtn.getStyleClass().add("table-action-button");
                viewBtn.setOnAction(e -> {
                    ResumeAnalysis a = getTableView().getItems().get(getIndex());
                    displayHistoricalAnalysis(a);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(viewBtn);
                }
            }
        });
    }

    // =========================================================================
    // Active Resume & Extraction Logic
    // =========================================================================

    private void loadActiveResume() {
        if (currentUser == null || currentUser.getId() <= 0) return;
        Task<Optional<Resume>> task = new Task<>() {
            @Override
            protected Optional<Resume> call() {
                return resumeService.getActiveResume(currentUser.getId());
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Optional<Resume> opt = task.getValue();
            if (opt.isPresent()) {
                currentResume = opt.get();
                currentParsed = resumeService.parseJsonMetadata(currentResume);
                showActiveResumeUI(currentResume, currentParsed);
            } else {
                showNoResumeUI();
            }
        }));

        new Thread(task, "load-resume-thread").start();
    }

    private void showActiveResumeUI(Resume r, ParsedResume parsed) {
        noResumeBox.setVisible(false);
        noResumeBox.setManaged(false);

        activeResumeBox.setVisible(true);
        activeResumeBox.setManaged(true);

        resumeStatusBadge.setText("Active Resume");
        resumeStatusBadge.getStyleClass().removeAll("badge-inactive");
        if (!resumeStatusBadge.getStyleClass().contains("badge-active")) {
            resumeStatusBadge.getStyleClass().add("badge-active");
        }

        fileNameLabel.setText(r.getFileName());
        fileSizeLabel.setText(r.getFormattedSize());
        uploadDateLabel.setText(r.getUploadedAt() != null ? r.getUploadedAt().format(DATE_FORMATTER) : "Recently");
        fileHashLabel.setText("SHA-256: " + r.getShortHash() + "…");

        // Display extracted information
        extractedInfoCard.setVisible(true);
        extractedInfoCard.setManaged(true);

        extractedNameLabel.setText(nvl(parsed.getName()));
        extractedEmailLabel.setText(nvl(parsed.getEmail()));
        extractedPhoneLabel.setText(nvl(parsed.getPhone()));

        String edu = (parsed.getDegree() != null ? parsed.getDegree() : "")
                + (parsed.getCollege() != null ? " at " + parsed.getCollege() : "")
                + (parsed.getGraduationYear() > 0 ? " (Class of " + parsed.getGraduationYear() + ")" : "");
        extractedEduLabel.setText(edu.isBlank() ? "Not detected" : edu);

        if (parsed.getLinks() != null && !parsed.getLinks().isEmpty()) {
            extractedLinksLabel.setText(String.join("  |  ", parsed.getLinks()));
        } else {
            extractedLinksLabel.setText("None detected");
        }

        // Skills chips
        skillsFlowPane.getChildren().clear();
        if (parsed.getSkills() != null && !parsed.getSkills().isEmpty()) {
            for (String skill : parsed.getSkills()) {
                Label chip = new Label(skill);
                chip.getStyleClass().add("skill-chip");
                skillsFlowPane.getChildren().add(chip);
            }
        } else {
            Label noSkills = new Label("No specific technical skills parsed. Update your resume text.");
            noSkills.getStyleClass().add("detail-key");
            skillsFlowPane.getChildren().add(noSkills);
        }

        // Trigger scoring (job-specific if job selected, otherwise general resume quality)
        Job selectedJob = jobComboBox.getSelectionModel().getSelectedItem();
        runDeterministicScoring(parsed, selectedJob);
    }

    private void showNoResumeUI() {
        currentResume = null;
        currentParsed = null;

        activeResumeBox.setVisible(false);
        activeResumeBox.setManaged(false);
        extractedInfoCard.setVisible(false);
        extractedInfoCard.setManaged(false);
        scoreCard.setVisible(false);
        scoreCard.setManaged(false);

        noResumeBox.setVisible(true);
        noResumeBox.setManaged(true);

        resumeStatusBadge.setText("No Resume Uploaded");
        resumeStatusBadge.getStyleClass().removeAll("badge-active");
        if (!resumeStatusBadge.getStyleClass().contains("badge-inactive")) {
            resumeStatusBadge.getStyleClass().add("badge-inactive");
        }
    }

    // =========================================================================
    // User Actions: Upload, Replace, Delete, Sync
    // =========================================================================

    @FXML
    public void handleUploadResume(ActionEvent event) {
        chooseAndUploadResume();
    }

    @FXML
    public void handleReplaceResume(ActionEvent event) {
        chooseAndUploadResume();
    }

    private void chooseAndUploadResume() {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Stage stage = NavigationManager.resolveStage(backBtn);
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF Resume — HireHub");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile == null) return;

        uploadFeedbackLabel.setText("Uploading & analyzing PDF…");
        uploadFeedbackLabel.setStyle("-fx-text-fill: #2563EB;");

        Task<ResumeService.UploadResult> task = new Task<>() {
            @Override
            protected ResumeService.UploadResult call() {
                return resumeService.uploadAndProcessResume(currentUser.getId(), selectedFile);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            ResumeService.UploadResult res = task.getValue();
            if (res.isSuccess()) {
                currentResume = res.getResume();
                currentParsed = res.getParsedResume();
                showActiveResumeUI(currentResume, currentParsed);
                uploadFeedbackLabel.setText("✓ " + res.getMessage());
                uploadFeedbackLabel.setStyle("-fx-text-fill: #16A34A;");
            } else {
                uploadFeedbackLabel.setText("⚠ " + res.getMessage());
                uploadFeedbackLabel.setStyle("-fx-text-fill: #DC2626;");
                showAlert(Alert.AlertType.ERROR, "Upload Error", res.getMessage());
            }
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            uploadFeedbackLabel.setText("⚠ An unexpected error occurred during upload.");
            uploadFeedbackLabel.setStyle("-fx-text-fill: #DC2626;");
        }));

        new Thread(task, "upload-resume-thread").start();
    }

    @FXML
    public void handleDeleteResume(ActionEvent event) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        if (currentResume == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Resume Confirmation");
        confirm.setHeaderText("Are you sure you want to delete your active resume?");
        confirm.setContentText("This will remove your parsed resume from HireHub. Previous analysis history will remain saved.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean deleted = resumeService.deleteResume(currentResume.getId(), currentUser.getId());
            if (deleted) {
                showNoResumeUI();
                uploadFeedbackLabel.setText("Resume deleted.");
                uploadFeedbackLabel.setStyle("-fx-text-fill: #6B7280;");
            } else {
                showAlert(Alert.AlertType.ERROR, "Delete Failed", "Could not delete resume. Please try again.");
            }
        }
    }

    @FXML
    public void handleSyncProfile(ActionEvent event) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        if (currentParsed == null) return;

        boolean ok = resumeService.syncWithStudentProfile(currentUser.getId(), currentParsed);
        if (ok) {
            syncStatusLabel.setText("✓ Student profile successfully updated with extracted education and skills!");
            syncStatusLabel.setVisible(true);
            syncStatusLabel.setManaged(true);
        } else {
            showAlert(Alert.AlertType.ERROR, "Sync Failed", "Could not sync extracted skills to student profile.");
        }
    }

    // =========================================================================
    // Deterministic Compatibility Scoring
    // =========================================================================

    private void showJobDetails(Job j) {
        jobDetailsBox.setVisible(true);
        jobDetailsBox.setManaged(true);

        jobTitleHeaderLabel.setText(j.getTitle());
        jobCompanyLabel.setText("at " + j.getCompanyName());
        jobLocationTypeLabel.setText(j.getJobType().label() + " • " + j.getLocation());
        jobSkillsLabel.setText(j.getRequiredSkills() != null ? j.getRequiredSkills() : "None explicitly listed");
    }

    private void runDeterministicScoring(ParsedResume parsed, Job job) {
        currentBreakdown = analysisService.calculateDeterministicScore(parsed, job);

        scoreCard.setVisible(true);
        scoreCard.setManaged(true);

        int overall = currentBreakdown.getOverallScore();
        overallScoreNumberLabel.setText(overall + "%");

        // Dynamic title & badge based on mode
        if (job == null) {
            scoreSectionTitleLabel.setText("4. Deterministic Resume Quality Audit");
            overallScoreTypeLabel.setText("Quality Score");
            rubric1TitleLabel.setText("🎯 Technical Breadth & Stack Coverage (40%)");
            matchingSkillsTitleLabel.setText("Detected Technical Skills (" + currentBreakdown.getMatchingSkills().size() + ")");
            missingSkillsBox.setVisible(false);
            missingSkillsBox.setManaged(false);
        } else {
            scoreSectionTitleLabel.setText("4. Deterministic Compatibility Audit (" + job.getTitle() + ")");
            overallScoreTypeLabel.setText("Overall Match");
            rubric1TitleLabel.setText("🎯 Required Skills Match (40%)");
            matchingSkillsTitleLabel.setText("Matching Required Skills (" + currentBreakdown.getMatchingSkills().size() + ")");
            missingSkillsBox.setVisible(true);
            missingSkillsBox.setManaged(true);
        }

        // Confidence badge
        String conf = currentBreakdown.getEvaluationConfidence();
        confidenceBadgeLabel.setText("Confidence: " + conf);
        confidenceBadgeLabel.getStyleClass().removeAll("badge-active", "badge-neutral", "badge-inactive");
        if (conf.startsWith("HIGH")) {
            confidenceBadgeLabel.getStyleClass().add("badge-active");
        } else if (conf.startsWith("MODERATE")) {
            confidenceBadgeLabel.getStyleClass().add("badge-neutral");
        } else {
            confidenceBadgeLabel.getStyleClass().add("badge-inactive");
        }

        // Color overall badge based on score
        if (overall >= 80) {
            overallScoreNumberLabel.setStyle("-fx-text-fill: #16A34A;");
        } else if (overall >= 60) {
            overallScoreNumberLabel.setStyle("-fx-text-fill: #2563EB;");
        } else {
            overallScoreNumberLabel.setStyle("-fx-text-fill: #DC2626;");
        }

        // Rubric 1: Skills (40)
        skillsScoreLabel.setText(currentBreakdown.getSkillsScore() + " / 40 pts");
        skillsProgressBar.setProgress(currentBreakdown.getSkillsScore() / 40.0);
        skillsBreakdownDetailLabel.setText(currentBreakdown.getSkillsExplanation());

        // Rubric 2: Experience (25)
        expScoreLabel.setText(currentBreakdown.getExperienceScore() + " / 25 pts");
        expProgressBar.setProgress(currentBreakdown.getExperienceScore() / 25.0);
        expBreakdownDetailLabel.setText(currentBreakdown.getExperienceExplanation());

        // Rubric 3: Education (15)
        eduScoreLabel.setText(currentBreakdown.getEducationScore() + " / 15 pts");
        eduProgressBar.setProgress(currentBreakdown.getEducationScore() / 15.0);
        eduBreakdownDetailLabel.setText(currentBreakdown.getEducationExplanation());

        // Rubric 4: Completeness (10)
        compScoreLabel.setText(currentBreakdown.getCompletenessScore() + " / 10 pts");
        compProgressBar.setProgress(currentBreakdown.getCompletenessScore() / 10.0);
        compBreakdownDetailLabel.setText(currentBreakdown.getCompletenessExplanation());

        // Rubric 5: Certifications (10)
        certScoreLabel.setText(currentBreakdown.getCertificationsScore() + " / 10 pts");
        certProgressBar.setProgress(currentBreakdown.getCertificationsScore() / 10.0);
        certBreakdownDetailLabel.setText(currentBreakdown.getCertificationsExplanation());

        // Unevaluated criteria alert
        if (!currentBreakdown.getUnevaluatedCriteria().isEmpty()) {
            unevaluatedCriteriaBox.setVisible(true);
            unevaluatedCriteriaBox.setManaged(true);
            unevaluatedCriteriaLabel.setText("• " + String.join("\n• ", currentBreakdown.getUnevaluatedCriteria()));
        } else {
            unevaluatedCriteriaBox.setVisible(false);
            unevaluatedCriteriaBox.setManaged(false);
        }

        // Matching skills chips
        matchingSkillsFlow.getChildren().clear();
        if (currentBreakdown.getMatchingSkills().isEmpty()) {
            Label lbl = new Label("None detected");
            lbl.getStyleClass().add("detail-key");
            matchingSkillsFlow.getChildren().add(lbl);
        } else {
            for (String s : currentBreakdown.getMatchingSkills()) {
                Label chip = new Label("✓ " + s);
                chip.getStyleClass().add("matching-skill-chip");
                matchingSkillsFlow.getChildren().add(chip);
            }
        }

        // Missing skills chips (if job mode)
        missingSkillsFlow.getChildren().clear();
        if (job != null) {
            if (currentBreakdown.getMissingSkills().isEmpty()) {
                Label lbl = new Label("All required skills found!");
                lbl.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold;");
                missingSkillsFlow.getChildren().add(lbl);
            } else {
                for (String s : currentBreakdown.getMissingSkills()) {
                    Label chip = new Label("✕ " + s);
                    chip.getStyleClass().add("missing-skill-chip");
                    missingSkillsFlow.getChildren().add(chip);
                }
            }
        }
    }

    // =========================================================================
    // GroqCloud AI Deep Review with Explicit Consent & Graceful Degradation
    // =========================================================================

    @FXML
    public void handleGenerateAiReview(ActionEvent event) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Job selectedJob = jobComboBox.getSelectionModel().getSelectedItem();
        startAiReviewWithConsent(selectedJob);
    }

    @FXML
    public void handleRetryAiReview(ActionEvent event) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Job selectedJob = jobComboBox.getSelectionModel().getSelectedItem();
        startAiReviewWithConsent(selectedJob);
    }

    private void startAiReviewWithConsent(Job selectedJob) {
        if (currentResume == null || currentParsed == null) {
            showAlert(Alert.AlertType.WARNING, "Resume Required", "Please upload a resume first before running AI analysis.");
            return;
        }

        // Ensure deterministic scoring has run and is preserved
        if (currentBreakdown == null || currentBreakdown.isGeneral() != (selectedJob == null)) {
            currentBreakdown = analysisService.calculateDeterministicScore(currentParsed, selectedJob);
        }

        // ── Explicit Student Consent Prompt ──────────────────────────────────
        Alert consentDialog = new Alert(Alert.AlertType.CONFIRMATION);
        consentDialog.setTitle("Student Privacy & AI Analysis Consent");

        if (selectedJob != null) {
            consentDialog.setHeaderText("Authorize GroqCloud Role-Specific Review?");
            consentDialog.setContentText(
                    "HireHub will securely transmit your extracted resume summary and requirements for \""
                    + selectedJob.getTitle() + " at " + selectedJob.getCompanyName() + "\" to GroqCloud AI.\n\n"
                    + "GroqCloud will provide:\n"
                    + " • Job alignment critique & ATS readability tips\n"
                    + " • Bullet point rewrites using Google's X-Y-Z formula\n"
                    + " • Prioritized 30-day preparation roadmap\n\n"
                    + "Do you authorize transmission of your resume data to GroqCloud?"
            );
        } else {
            consentDialog.setHeaderText("Authorize GroqCloud General Resume Quality Review?");
            consentDialog.setContentText(
                    "HireHub will securely transmit your extracted resume summary to GroqCloud AI for a comprehensive general audit.\n\n"
                    + "GroqCloud will provide:\n"
                    + " • ATS structural critique & formatting assessment\n"
                    + " • Technical stack profile recommendations\n"
                    + " • Bullet point optimizations using Google's X-Y-Z formula\n"
                    + " • 30-day engineering career roadmap\n\n"
                    + "Do you authorize transmission of your resume data to GroqCloud?"
            );
        }

        ButtonType authorizeBtn = new ButtonType("Authorize & Proceed", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn    = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        consentDialog.getButtonTypes().setAll(authorizeBtn, cancelBtn);

        Optional<ButtonType> consentChoice = consentDialog.showAndWait();
        if (consentChoice.isEmpty() || consentChoice.get() != authorizeBtn) {
            return; // user declined or cancelled
        }

        // Set UI to loading state
        setAiLoading(true);
        if (aiRetryCard != null) {
            aiRetryCard.setVisible(false);
            aiRetryCard.setManaged(false);
        }
        if (aiReportMetaLabel != null) {
            aiReportMetaLabel.setVisible(false);
            aiReportMetaLabel.setManaged(false);
        }
        aiReportContainer.setVisible(true);
        aiReportContainer.setManaged(true);
        aiMarkdownContentBox.getChildren().clear();

        final ParsedResume parsedSnapshot   = currentParsed;
        final Job          jobSnapshot      = selectedJob;
        final ResumeAnalysisService.ScoringBreakdown breakdownSnapshot = currentBreakdown;
        final int          resumeIdSnapshot = currentResume.getId();

        Task<AIService.AIResult> aiTask = new Task<>() {
            @Override
            protected AIService.AIResult call() {
                return analysisService.generateAIReview(parsedSnapshot, jobSnapshot, breakdownSnapshot);
            }
        };

        aiTask.setOnSucceeded(e -> Platform.runLater(() -> {
            setAiLoading(false);
            AIService.AIResult result = aiTask.getValue();

            if (result.isSuccess()) {
                lastGeneratedReport = result.getText();
                MarkdownRenderer.renderInto(aiMarkdownContentBox, lastGeneratedReport);
                copyReportBtn.setVisible(true);
                copyReportBtn.setManaged(true);

                if (aiReportMetaLabel != null) {
                    String meta = "Report generated by " + result.getModelUsed()
                            + (result.isFallbackUsed() ? " [Fallback Model]" : "")
                            + " • " + result.getFormattedTimestamp()
                            + " (" + result.getAttempts() + " attempt" + (result.getAttempts() > 1 ? "s" : "") + ")";
                    aiReportMetaLabel.setText(meta);
                    aiReportMetaLabel.setVisible(true);
                    aiReportMetaLabel.setManaged(true);
                }

                if (aiRetryCard != null) {
                    aiRetryCard.setVisible(false);
                    aiRetryCard.setManaged(false);
                }

                // Save analysis to database in background
                new Thread(() -> {
                    try {
                        Integer jId = (jobSnapshot != null && jobSnapshot.getId() > 0) ? jobSnapshot.getId() : null;
                        analysisService.saveAnalysis(
                                resumeIdSnapshot, jId, currentUser.getId(),
                                breakdownSnapshot, lastGeneratedReport
                        );
                        loadHistory();
                    } catch (SQLException ex) {
                        System.err.println("[ResumeAnalyzer] Could not save analysis to DB: " + ex.getMessage());
                    }
                }, "save-analysis-thread").start();

            } else {
                // Graceful degradation: preserve all Section 1-4 deterministic scores and render friendly notice
                MarkdownRenderer.renderErrorInto(aiMarkdownContentBox, result.getError());
                copyReportBtn.setVisible(false);
                copyReportBtn.setManaged(false);

                if (aiReportMetaLabel != null) {
                    aiReportMetaLabel.setVisible(false);
                    aiReportMetaLabel.setManaged(false);
                }

                if (aiRetryCard != null) {
                    aiRetryCard.setVisible(true);
                    aiRetryCard.setManaged(true);
                    if (aiServiceNoticeLabel != null) {
                        aiServiceNoticeLabel.setText(
                                "GroqCloud AI is currently experiencing high demand or upstream capacity limits (HTTP 503).\n"
                                + "All deterministic scores, parsed skills, and evaluation breakdowns above remain fully intact.\n"
                                + "Click 'Retry AI Analysis' to try again without re-uploading."
                        );
                    }
                }
            }
        }));

        aiTask.setOnFailed(e -> Platform.runLater(() -> {
            setAiLoading(false);
            if (aiReportMetaLabel != null) {
                aiReportMetaLabel.setVisible(false);
                aiReportMetaLabel.setManaged(false);
            }
            if (aiRetryCard != null) {
                aiRetryCard.setVisible(true);
                aiRetryCard.setManaged(true);
                if (aiServiceNoticeLabel != null) {
                    aiServiceNoticeLabel.setText(
                            "Could not reach GroqCloud AI service. All deterministic scores and profile data above remain intact.\n"
                            + "Click 'Retry AI Analysis' to try again."
                    );
                }
            }
            MarkdownRenderer.renderErrorInto(aiMarkdownContentBox,
                    "An unexpected network or service error occurred while communicating with GroqCloud. Please try again.");
        }));

        new Thread(aiTask, "groq-analysis-thread").start();
    }

    private void setAiLoading(boolean loading) {
        generateAiReviewBtn.setDisable(loading);
        aiLoadingBox.setVisible(loading);
        aiLoadingBox.setManaged(loading);
    }

    @FXML
    public void handleCopyReport(ActionEvent event) {
        if (lastGeneratedReport == null || lastGeneratedReport.isBlank()) return;

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(lastGeneratedReport);
        clipboard.setContent(content);

        copyReportBtn.setText("✓ Copied!");
        Platform.runLater(() -> {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> copyReportBtn.setText("📋 Copy Report"));
                }
            }, 2000);
        });
    }

    // =========================================================================
    // Analysis History Display
    // =========================================================================

    private void loadHistory() {
        if (currentUser == null || currentUser.getId() <= 0) return;
        Task<List<ResumeAnalysis>> task = new Task<>() {
            @Override
            protected List<ResumeAnalysis> call() {
                return analysisService.getStudentAnalysisHistory(currentUser.getId(), 15);
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<ResumeAnalysis> list = task.getValue();
            historyTableView.setItems(FXCollections.observableArrayList(list));
        }));

        new Thread(task, "load-history-thread").start();
    }

    private void displayHistoricalAnalysis(ResumeAnalysis a) {
        if (a == null) return;

        aiReportContainer.setVisible(true);
        aiReportContainer.setManaged(true);
        lastGeneratedReport = a.getAnalysisSummary();

        if (lastGeneratedReport != null && !lastGeneratedReport.isBlank()) {
            MarkdownRenderer.renderInto(aiMarkdownContentBox, lastGeneratedReport);
            copyReportBtn.setVisible(true);
            copyReportBtn.setManaged(true);
        } else {
            MarkdownRenderer.renderInto(aiMarkdownContentBox,
                    "### Saved Historical Analysis\n\n"
                    + "- **Target Job:** " + a.getJobTitle() + " (" + a.getCompanyName() + ")\n"
                    + "- **Compatibility Score:** " + a.getOverallScore() + "%\n"
                    + "- **Skills Score:** " + a.getSkillsScore() + " / 40 pts\n"
                    + "- **Matching Skills:** " + a.getMatchingSkills() + "\n"
                    + "- **Missing Skills:** " + a.getMissingSkills() + "\n"
            );
        }

        // Smoothly scroll down to AI section
        contentScrollPane.setVvalue(0.75);
    }

    // =========================================================================
    // Navigation & Helpers
    // =========================================================================

    @FXML
    public void handleBack(ActionEvent event) {
        NavigationManager.navigateTo(backBtn, "/fxml/student_dashboard.fxml");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}

