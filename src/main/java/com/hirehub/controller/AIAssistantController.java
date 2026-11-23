package com.hirehub.controller;

import com.hirehub.model.Job;
import com.hirehub.model.StudentProfile;
import com.hirehub.model.User;
import com.hirehub.service.AIService;
import com.hirehub.service.JobService;
import com.hirehub.service.StudentService;
import com.hirehub.util.MarkdownRenderer;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * AIAssistantController — AI Career Assistant for Students.
 *
 * <p>Provides three core AI-driven career acceleration capabilities:
 * <ol>
 *   <li><b>Skill Gap Analysis:</b> Matches student profile skills against job requirements.</li>
 *   <li><b>Interview Questions:</b> Generates 3 technical + 2 behavioural questions with answer points.</li>
 *   <li><b>Preparation Plan:</b> Generates a 4-week structured milestone study schedule.</li>
 * </ol>
 *
 * <p>All network and database requests run strictly on background threads to ensure
 * the JavaFX UI remains 100% fluid and responsive.</p>
 */
public class AIAssistantController {

    // ── Navigation & Header ───────────────────────────────────────────────────
    @FXML private Button backBtn;
    @FXML private StudentSidebarController studentSidebarController;

    // ── Scroll Pane ───────────────────────────────────────────────────────────
    @FXML private ScrollPane contentScrollPane;

    // ── Status & Profile Card ─────────────────────────────────────────────────
    @FXML private Label apiKeyStatusLabel;
    @FXML private Label apiKeyHelpLabel;
    @FXML private Label studentSkillsLabel;
    @FXML private Label studentSkillsWarningLabel;

    // ── Job Selection Card ───────────────────────────────────────────────────
    @FXML private ComboBox<Job> jobCombo;
    @FXML private Label         jobSelectionHintLabel;

    // ── Action Buttons & Validation ───────────────────────────────────────────
    @FXML private Label             validationErrorLabel;
    @FXML private Button            analyzeSkillsBtn;
    @FXML private Button            generateQuestionsBtn;
    @FXML private Button            prepPlanBtn;
    @FXML private ProgressIndicator aiLoading;
    @FXML private Label             loadingLabel;

    // ── Results Area ──────────────────────────────────────────────────────────
    @FXML private VBox       resultsCard;
    @FXML private Label      resultsTitleLabel;
    @FXML private VBox       resultsContentBox;
    @FXML private Button     retryBtn;
    @FXML private Button     copyBtn;

    private String currentResponseText;

    // ── Services & State ──────────────────────────────────────────────────────
    private final AIService      aiService      = new AIService();
    private final JobService     jobService     = new JobService();
    private final StudentService studentService = new StudentService();

    private User           currentUser;
    private StudentProfile currentProfile;
    private Callable<AIService.AIResult> lastAction; // For retry button
    private String         lastActionTitle = "AI Request";

    private static record InitialData(List<Job> jobs, StudentProfile profile) {}

    // =========================================================================
    // Lifecycle & Initialization
    // =========================================================================

    @FXML
    public void initialize() {
        if (studentSidebarController != null) {
            studentSidebarController.setActiveSection(com.hirehub.util.StudentNavSection.AI_ASSISTANT);
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

        // 1. Update GroqCloud API Key Status Badge
        updateApiKeyStatusUI();

        // 2. Hide transient UI elements
        if (validationErrorLabel != null) {
            validationErrorLabel.setVisible(false);
            validationErrorLabel.setManaged(false);
        }
        if (studentSkillsWarningLabel != null) {
            studentSkillsWarningLabel.setVisible(false);
            studentSkillsWarningLabel.setManaged(false);
        }
        if (aiLoading != null) {
            aiLoading.setVisible(false);
            aiLoading.setManaged(false);
        }
        if (loadingLabel != null) {
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
        }
        if (resultsCard != null) {
            resultsCard.setVisible(false);
            resultsCard.setManaged(false);
        }
        if (retryBtn != null) {
            retryBtn.setVisible(false);
            retryBtn.setManaged(false);
        }
        if (copyBtn != null) {
            copyBtn.setVisible(false);
            copyBtn.setManaged(false);
        }
        if (resultsContentBox != null) {
            resultsContentBox.getChildren().clear();
        }

        // 3. Configure Job ComboBox Cell Rendering
        setupJobComboBox();

        // 4. Asynchronously load active jobs and profile data
        loadInitialDataAsync();
    }

    private void updateApiKeyStatusUI() {
        if (aiService.isConfigured()) {
            if (aiService.isConnectionVerified()) {
                // A real HTTP 200 has been received — connection confirmed
                apiKeyStatusLabel.setText("✅ GroqCloud API verified (Model: " + aiService.getModelName() + ")");
                apiKeyHelpLabel.setText("Connected to GroqCloud. All three AI capabilities are active.");
                apiKeyHelpLabel.setStyle("-fx-text-fill: #16A34A;");
            } else {
                // Key is set but no successful request yet
                apiKeyStatusLabel.setText("🔑 API key configured (Model: " + aiService.getModelName()
                        + ") — click any feature to test");
                apiKeyHelpLabel.setText("API key detected. Click Skill Gap Analysis, Interview Questions, or Preparation Plan to verify connectivity.");
                apiKeyHelpLabel.setStyle("-fx-text-fill: #1D4ED8;");
            }
            apiKeyStatusLabel.getStyleClass().removeAll("ai-api-key-badge-inactive");
            if (!apiKeyStatusLabel.getStyleClass().contains("ai-api-key-badge-active")) {
                apiKeyStatusLabel.getStyleClass().add("ai-api-key-badge-active");
            }
        } else {
            apiKeyStatusLabel.setText("⚠ GROQ_API_KEY not set — AI features unavailable");
            apiKeyStatusLabel.getStyleClass().removeAll("ai-api-key-badge-active");
            if (!apiKeyStatusLabel.getStyleClass().contains("ai-api-key-badge-inactive")) {
                apiKeyStatusLabel.getStyleClass().add("ai-api-key-badge-inactive");
            }
            apiKeyHelpLabel.setText("Set the environment variable in PowerShell: $env:GROQ_API_KEY=\"your_key\" before launching HireHub.");
            apiKeyHelpLabel.setStyle("-fx-text-fill: #92400E;");
        }
    }

    private void setupJobComboBox() {
        jobCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Job j, boolean empty) {
                super.updateItem(j, empty);
                if (empty || j == null) {
                    setText(null);
                } else {
                    setText(j.getTitle() + " — " + j.getCompanyName()
                            + " (" + (j.getJobType() != null ? j.getJobType().label() : "Job") + ")");
                }
            }
        });

        jobCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Job j, boolean empty) {
                super.updateItem(j, empty);
                if (empty || j == null) {
                    setText("Select a job listing…");
                } else {
                    setText(j.getTitle() + " — " + j.getCompanyName()
                            + " (" + (j.getJobType() != null ? j.getJobType().label() : "Job") + ")");
                }
            }
        });

        jobCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (validationErrorLabel != null) {
                validationErrorLabel.setVisible(false);
                validationErrorLabel.setManaged(false);
            }
            if (selected != null) {
                jobSelectionHintLabel.setText("Target role: " + selected.getTitle()
                        + " | Required skills: " + (selected.getRequiredSkills() != null ? selected.getRequiredSkills() : "None specified"));
                jobSelectionHintLabel.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: bold;");
            }
        });
    }

    // =========================================================================
    // Background Initial Data Loader
    // =========================================================================

    private void loadInitialDataAsync() {
        Task<InitialData> task = new Task<>() {
            @Override
            protected InitialData call() {
                List<Job> jobs = jobService.searchJobs("", "", "");
                StudentProfile profile = null;
                if (currentUser != null && currentUser.getId() > 0) {
                    profile = studentService.getProfile(currentUser.getId()).orElse(null);
                }
                return new InitialData(jobs, profile);
            }
        };

        task.setOnSucceeded(ev -> {
            InitialData data = task.getValue();
            currentProfile = data.profile();

            // Populate jobs
            if (data.jobs() != null && !data.jobs().isEmpty()) {
                jobCombo.setItems(FXCollections.observableArrayList(data.jobs()));
                jobCombo.getSelectionModel().selectFirst();
                Job first = jobCombo.getValue();
                jobSelectionHintLabel.setText("Target role: " + first.getTitle()
                        + " | Required skills: " + (first.getRequiredSkills() != null ? first.getRequiredSkills() : "None specified"));
                jobSelectionHintLabel.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: bold;");
            } else {
                jobCombo.setItems(FXCollections.emptyObservableList());
                jobSelectionHintLabel.setText("No active job postings found. Post jobs from the Recruiter Portal or check back later.");
                jobSelectionHintLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
            }

            // Populate student profile skills
            if (currentProfile != null && currentProfile.getSkills() != null && !currentProfile.getSkills().isBlank()) {
                studentSkillsLabel.setText(currentProfile.getSkills());
                studentSkillsLabel.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold;");
                studentSkillsWarningLabel.setVisible(false);
                studentSkillsWarningLabel.setManaged(false);
            } else {
                studentSkillsLabel.setText("No skills recorded in your profile yet.");
                studentSkillsLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-style: italic;");
                studentSkillsWarningLabel.setText("💡 Tip: Add your skills in 'My Profile' so the AI can provide tailored recommendations.");
                studentSkillsWarningLabel.setVisible(true);
                studentSkillsWarningLabel.setManaged(true);
            }
        });

        task.setOnFailed(ev -> {
            jobSelectionHintLabel.setText("Could not load jobs from database.");
            jobSelectionHintLabel.setStyle("-fx-text-fill: #DC2626;");
        });

        Thread t = new Thread(task, "ai-init-data");
        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    // AI Feature Button Handlers
    // =========================================================================

    /**
     * Feature 1: Skill Gap Analysis
     */
    @FXML
    public void handleAnalyzeSkills(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Job selected = jobCombo.getValue();
        if (!validateSelection(selected, "Skill Gap Analysis")) return;

        String studentSkills = (currentProfile != null && currentProfile.getSkills() != null && !currentProfile.getSkills().isBlank())
                ? currentProfile.getSkills().trim()
                : "None listed (Entry-level beginner)";

        String jobTitle = selected.getTitle();
        String jobDesc = (selected.getDescription() != null && !selected.getDescription().isBlank())
                ? selected.getDescription() : "Standard industry responsibilities";
        String reqSkills = (selected.getRequiredSkills() != null && !selected.getRequiredSkills().isBlank())
                ? selected.getRequiredSkills() : selected.getTitle();

        lastAction = () -> aiService.analyzeSkillGap(studentSkills, jobTitle, jobDesc, reqSkills);
        executeAITask("🎯 Skill Gap Analysis", lastAction);
    }

    /**
     * Feature 2: Interview Question Generator
     */
    @FXML
    public void handleGenerateQuestions(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Job selected = jobCombo.getValue();
        if (!validateSelection(selected, "Interview Questions")) return;

        String jobTitle = selected.getTitle();
        String reqSkills = (selected.getRequiredSkills() != null && !selected.getRequiredSkills().isBlank())
                ? selected.getRequiredSkills() : selected.getTitle();
        String jobDesc = (selected.getDescription() != null && !selected.getDescription().isBlank())
                ? selected.getDescription() : "Standard industry responsibilities";

        lastAction = () -> aiService.generateInterviewQuestions(jobTitle, reqSkills, jobDesc);
        executeAITask("💬 Interview Questions", lastAction);
    }

    /**
     * Feature 3: Personalized 4-Week Preparation Plan
     */
    @FXML
    public void handlePrepPlan(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        Job selected = jobCombo.getValue();
        if (!validateSelection(selected, "Preparation Plan")) return;

        String studentSkills = (currentProfile != null && currentProfile.getSkills() != null && !currentProfile.getSkills().isBlank())
                ? currentProfile.getSkills().trim()
                : "None listed (Entry-level beginner)";

        String jobTitle = selected.getTitle();
        String reqSkills = (selected.getRequiredSkills() != null && !selected.getRequiredSkills().isBlank())
                ? selected.getRequiredSkills() : selected.getTitle();

        lastAction = () -> aiService.generatePreparationPlan(studentSkills, jobTitle, reqSkills);
        executeAITask("📅 4-Week Preparation Plan", lastAction);
    }

    /**
     * Retries the previous AI action
     */
    @FXML
    public void handleRetry(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        if (lastAction != null) {
            executeAITask(lastActionTitle, lastAction);
        }
    }

    /**
     * Copies the current AI response text to the system clipboard.
     */
    @FXML
    public void handleCopy(ActionEvent e) {
        String text = currentResponseText;
        if (text != null && !text.isBlank()) {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            if (copyBtn != null) {
                copyBtn.setText("✔ Copied!");
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        if (copyBtn != null) copyBtn.setText("📋 Copy Response");
                    });
                }).start();
            }
        }
    }

    /**
     * Returns back to the Student Dashboard using the existing navigation mechanism.
     */
    @FXML
    public void handleBack(ActionEvent e) {
        navigateTo("/fxml/student_dashboard.fxml");
    }

    // =========================================================================
    // Private Helpers & Background Execution
    // =========================================================================

    /**
     * Validates prerequisites before invoking GroqCloud API.
     */
    private boolean validateSelection(Job selected, String featureName) {
        if (validationErrorLabel != null) {
            validationErrorLabel.setVisible(false);
            validationErrorLabel.setManaged(false);
        }

        // 1. Check API Key
        if (!aiService.isConfigured()) {
            showValidationError("⚠ GROQ_API_KEY is not configured. Please set the environment variable to enable AI features.");
            showErrorResult("GROQ_API_KEY is not configured.\n\n"
                    + "To enable AI Career Assistant features:\n"
                    + "1. Get a key from GroqCloud Console: https://console.groq.com/keys\n"
                    + "2. Set the environment variable in PowerShell:\n"
                    + "   $env:GROQ_API_KEY = \"your_api_key_here\"\n"
                    + "3. Re-launch the HireHub application.");
            return false;
        }

        // 2. Check Job Selection
        if (selected == null) {
            showValidationError("⚠ Please select a target job from the dropdown above before clicking " + featureName + ".");
            showErrorResult("No job selected.\n\nPlease choose an active job listing from the 'Select Target Job Posting' dropdown above.");
            return false;
        }

        // 3. Check Job Validity
        if (selected.getTitle() == null || selected.getTitle().isBlank()) {
            showValidationError("⚠ The selected job has no valid title. Please select a different job.");
            return false;
        }

        return true;
    }

    /**
     * Executes an AI API call on a background worker thread.
     */
    private void executeAITask(String title, Callable<AIService.AIResult> callable) {
        this.lastActionTitle = title;
        setLoading(true, "Consulting " + aiService.getModelName() + " for " + title + "…");
        if (copyBtn != null) {
            copyBtn.setVisible(false);
            copyBtn.setManaged(false);
        }
        if (resultsContentBox != null) {
            resultsContentBox.getChildren().clear();
        }
        resultsCard.setVisible(false);
        resultsCard.setManaged(false);

        Task<AIService.AIResult> task = new Task<>() {
            @Override
            protected AIService.AIResult call() throws Exception {
                return callable.call();
            }
        };

        task.setOnSucceeded(ev -> {
            setLoading(false, "");
            AIService.AIResult result = task.getValue();
            showResult(title, result);
        });

        task.setOnFailed(ev -> {
            setLoading(false, "");
            Throwable ex = task.getException();
            showErrorResult("Error executing " + title + ": " + (ex != null ? ex.getMessage() : "Unexpected failure"));
        });

        Thread thread = new Thread(task, "ai-task-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void showResult(String title, AIService.AIResult result) {
        if (result != null && result.isSuccess()) {
            String metaInfo = (result.getModelUsed() != null)
                    ? " (" + result.getModelUsed() + (result.isFallbackUsed() ? " [Fallback]" : "") + " • " + result.getFormattedTimestamp() + ")"
                    : "";
            resultsTitleLabel.setText(title + metaInfo);

            currentResponseText = result.getText();
            if (resultsContentBox != null) MarkdownRenderer.renderInto(resultsContentBox, currentResponseText);

            if (retryBtn != null) {
                retryBtn.setVisible(false);
                retryBtn.setManaged(false);
            }
            if (copyBtn != null) {
                copyBtn.setVisible(true);
                copyBtn.setManaged(true);
                copyBtn.setText("📋 Copy Response");
            }
            // Update status badge to "verified" now that we have a real 200 response
            updateApiKeyStatusUI();
        } else {
            resultsTitleLabel.setText(title + " — Service Notice");
            String errorMsg = (result != null && result.getError() != null)
                    ? result.getError()
                    : "Unknown error occurred while contacting AI service.";
            currentResponseText = "⚠ " + errorMsg;
            if (resultsContentBox != null) MarkdownRenderer.renderErrorInto(resultsContentBox, errorMsg);

            if (retryBtn != null) {
                retryBtn.setVisible(true);
                retryBtn.setManaged(true);
            }
            if (copyBtn != null) {
                copyBtn.setVisible(false);
                copyBtn.setManaged(false);
            }
        }

        resultsCard.setVisible(true);
        resultsCard.setManaged(true);

        // Smoothly scroll down the main page so the beginning of the result is immediately visible
        Platform.runLater(() -> {
            if (contentScrollPane != null) {
                contentScrollPane.setVvalue(1.0);
            }
        });
    }

    private void showErrorResult(String message) {
        resultsTitleLabel.setText("Notice / Error");
        currentResponseText = "⚠ " + message;
        if (resultsContentBox != null) MarkdownRenderer.renderErrorInto(resultsContentBox, message);

        if (retryBtn != null) {
            retryBtn.setVisible(false);
            retryBtn.setManaged(false);
        }
        if (copyBtn != null) {
            copyBtn.setVisible(false);
            copyBtn.setManaged(false);
        }
        resultsCard.setVisible(true);
        resultsCard.setManaged(true);

        Platform.runLater(() -> {
            if (contentScrollPane != null) {
                contentScrollPane.setVvalue(1.0);
            }
        });
    }

    /** Returns the raw text of the current AI response. */
    public String getCurrentResponseText() {
        return currentResponseText;
    }

    private void showValidationError(String message) {
        if (validationErrorLabel != null) {
            validationErrorLabel.setText(message);
            validationErrorLabel.setVisible(true);
            validationErrorLabel.setManaged(true);
        }
    }

    private void setLoading(boolean loading, String message) {
        analyzeSkillsBtn.setDisable(loading);
        generateQuestionsBtn.setDisable(loading);
        prepPlanBtn.setDisable(loading);
        jobCombo.setDisable(loading);

        aiLoading.setVisible(loading);
        aiLoading.setManaged(loading);
        loadingLabel.setVisible(loading);
        loadingLabel.setManaged(loading);
        loadingLabel.setText(message);
    }

    /**
     * Standard Scene navigation helper.
     */
    private void navigateTo(String fxmlPath) {
        NavigationManager.navigateTo(backBtn, fxmlPath);
    }
}
