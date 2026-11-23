package com.hirehub.controller;

import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.JobService;
import com.hirehub.service.StudentService;
import com.hirehub.util.NavigationManager;
import com.hirehub.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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

/**
 * MyApplicationsController — Phase 3 student application history screen.
 *
 * <p>Displays all applications for the logged-in student with status badges,
 * search/filter support, and a detail view panel.</p>
 */
public class MyApplicationsController {

    // ── Filter controls ───────────────────────────────────────────────────────
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button           refreshBtn;
    @FXML private Button           backBtn;
    @FXML private StudentSidebarController studentSidebarController;

    // ── Results table ─────────────────────────────────────────────────────────
    @FXML private TableView<Application>            applicationsTable;
    @FXML private TableColumn<Application, String>  colJobTitle;
    @FXML private TableColumn<Application, String>  colCompany;
    @FXML private TableColumn<Application, String>  colType;
    @FXML private TableColumn<Application, String>  colLocation;
    @FXML private TableColumn<Application, String>  colAppliedDate;
    @FXML private TableColumn<Application, String>  colStatus;
    @FXML private Label                             resultLabel;
    @FXML private ProgressIndicator                 loadingIndicator;

    // ── Detail panel ──────────────────────────────────────────────────────────
    @FXML private VBox    detailPanel;
    @FXML private Label   detailJobTitle;
    @FXML private Label   detailCompany;
    @FXML private Label   detailType;
    @FXML private Label   detailLocation;
    @FXML private Label   detailAppliedDate;
    @FXML private Label   detailStatus;
    @FXML private TextArea detailCoverLetter;

    // ── Services ──────────────────────────────────────────────────────────────
    private final ApplicationService applicationService = new ApplicationService();
    private User currentUser;
    private List<Application> allApplications;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @FXML
    public void initialize() {
        if (studentSidebarController != null) {
            studentSidebarController.setActiveSection(com.hirehub.util.StudentNavSection.APPLICATIONS);
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

        statusFilter.setItems(FXCollections.observableArrayList(
                "All Statuses", "Pending", "Shortlisted", "Rejected", "Selected"));
        statusFilter.getSelectionModel().selectFirst();

        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        loadingIndicator.setVisible(false);

        setupTable();

        // Selection listener
        applicationsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> { if (selected != null) showDetail(selected); });

        // Search text change listener — live filtering
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());

        loadApplicationsAsync();
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    @FXML public void handleSearch(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        applyFilters();
    }

    @FXML public void handleRefresh(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        loadApplicationsAsync();
    }

    @FXML public void handleBack(ActionEvent e) { navigateTo("/fxml/student_dashboard.fxml"); }

    @FXML public void handleStatusFilter(ActionEvent e) {
        if (!SessionManager.getInstance().hasRole(User.Role.STUDENT) || currentUser == null || currentUser.getId() <= 0) {
            NavigationManager.navigateTo(backBtn, "/fxml/login.fxml");
            return;
        }
        applyFilters();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void setupTable() {
        colJobTitle.setCellValueFactory(data ->
                new SimpleStringProperty(nvl(data.getValue().getJobTitle())));
        colCompany.setCellValueFactory(data ->
                new SimpleStringProperty(nvl(data.getValue().getCompanyName())));
        colType.setCellValueFactory(data ->
                new SimpleStringProperty(formatJobType(data.getValue().getJobType())));
        colLocation.setCellValueFactory(data ->
                new SimpleStringProperty(nvl(data.getValue().getLocation(), "—")));
        colAppliedDate.setCellValueFactory(data -> {
            var dt = data.getValue().getAppliedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "—");
        });
        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus().label()));

        // Status badge cell coloring
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle(switch (status) {
                    case "Shortlisted" -> "-fx-text-fill: #2563EB; -fx-font-weight: bold;";
                    case "Selected"    -> "-fx-text-fill: #16A34A; -fx-font-weight: bold;";
                    case "Rejected"    -> "-fx-text-fill: #DC2626; -fx-font-weight: bold;";
                    default            -> "-fx-text-fill: #6B7280;";
                });
            }
        });

        applicationsTable.setPlaceholder(new Label("No applications yet. Browse jobs to get started!"));
    }

    private void loadApplicationsAsync() {
        if (currentUser == null || currentUser.getId() <= 0) return;
        loadingIndicator.setVisible(true);
        applicationsTable.setItems(FXCollections.emptyObservableList());

        Task<List<Application>> task = new Task<>() {
            @Override protected List<Application> call() {
                return applicationService.getStudentApplications(currentUser.getId());
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            allApplications = task.getValue();
            applyFilters();
        }));
        task.setOnFailed(ev -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            resultLabel.setText("Error loading applications.");
        }));
        new Thread(task, "load-my-apps").start();
    }

    private void applyFilters() {
        if (allApplications == null) return;
        String keyword = searchField.getText().trim().toLowerCase();
        String status  = statusFilter.getValue();

        List<Application> filtered = allApplications.stream()
                .filter(a -> {
                    boolean matchKeyword = keyword.isBlank()
                            || (a.getJobTitle() != null && a.getJobTitle().toLowerCase().contains(keyword))
                            || (a.getCompanyName() != null && a.getCompanyName().toLowerCase().contains(keyword));
                    boolean matchStatus = "All Statuses".equals(status)
                            || a.getStatus().label().equals(status);
                    return matchKeyword && matchStatus;
                })
                .toList();

        applicationsTable.setItems(FXCollections.observableArrayList(filtered));
        resultLabel.setText(filtered.size() + " application" + (filtered.size() == 1 ? "" : "s"));
    }

    private void showDetail(Application app) {
        detailJobTitle.setText(nvl(app.getJobTitle()));
        detailCompany.setText(nvl(app.getCompanyName()));
        detailType.setText(formatJobType(app.getJobType()));
        detailLocation.setText(nvl(app.getLocation(), "—"));
        detailAppliedDate.setText(app.getAppliedAt() != null ? app.getAppliedAt().toLocalDate().toString() : "—");
        detailStatus.setText(app.getStatus().label());
        detailStatus.setStyle(switch (app.getStatus()) {
            case SHORTLISTED -> "-fx-text-fill: #2563EB; -fx-font-weight: bold;";
            case SELECTED    -> "-fx-text-fill: #16A34A; -fx-font-weight: bold;";
            case REJECTED    -> "-fx-text-fill: #DC2626; -fx-font-weight: bold;";
            default          -> "-fx-text-fill: #6B7280;";
        });
        detailCoverLetter.setText(app.getCoverLetter() != null ? app.getCoverLetter() : "(No cover letter provided)");
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void navigateTo(String fxmlPath) {
        NavigationManager.navigateTo(backBtn, fxmlPath);
    }

    private String nvl(String s)                    { return (s == null || s.isBlank()) ? "—" : s; }
    private String nvl(String s, String fallback)   { return (s == null || s.isBlank()) ? fallback : s; }
    private String formatJobType(String raw) {
        if (raw == null) return "—";
        return switch (raw.toUpperCase()) {
            case "INTERNSHIP" -> "Internship";
            case "FULL_TIME"  -> "Full-Time";
            case "PART_TIME"  -> "Part-Time";
            default           -> raw;
        };
    }
}

