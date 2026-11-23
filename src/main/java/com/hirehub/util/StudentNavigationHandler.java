package com.hirehub.util;

import com.hirehub.controller.StudentDashboardController;
import com.hirehub.service.AuthService;
import javafx.scene.Node;

/**
 * StudentNavigationHandler — Centralized routing handler for student portal navigation.
 */
public final class StudentNavigationHandler {

    private static final AuthService authService = new AuthService();

    private StudentNavigationHandler() {}

    public static void navigateToDashboard(Node sourceNode) {
        NavigationManager.navigateTo(sourceNode, "/fxml/student_dashboard.fxml");
    }

    public static void navigateToProfile(Node sourceNode) {
        NavigationManager.navigate(sourceNode, "/fxml/student_dashboard.fxml",
                (StudentDashboardController c) -> c.showProfileDirectly());
    }

    public static void navigateToBrowseJobs(Node sourceNode) {
        NavigationManager.navigateTo(sourceNode, "/fxml/job_board.fxml");
    }

    public static void navigateToApplications(Node sourceNode) {
        NavigationManager.navigateTo(sourceNode, "/fxml/my_applications.fxml");
    }

    public static void navigateToResumeAnalyzer(Node sourceNode) {
        NavigationManager.navigateTo(sourceNode, "/fxml/resume_analyzer.fxml");
    }

    public static void navigateToAI(Node sourceNode) {
        NavigationManager.navigateTo(sourceNode, "/fxml/ai_assistant.fxml");
    }

    public static void handleLogout(Node sourceNode) {
        authService.logout();
        NavigationManager.navigateTo(sourceNode, "/fxml/login.fxml");
    }
}
