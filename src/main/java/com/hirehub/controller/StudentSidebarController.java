package com.hirehub.controller;

import com.hirehub.util.StudentNavSection;
import com.hirehub.util.StudentNavigationHandler;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * StudentSidebarController — Manages the persistent student portal left navigation sidebar.
 */
public class StudentSidebarController {

    @FXML private Button navDashboardBtn;
    @FXML private Button navBrowseJobsBtn;
    @FXML private Button navApplicationsBtn;
    @FXML private Button navResumeAnalyzerBtn;
    @FXML private Button navAIBtn;
    @FXML private Button navProfileBtn;
    @FXML private Button navLogoutBtn;

    private StudentNavSection activeSection;

    public void setActiveSection(StudentNavSection section) {
        this.activeSection = section;
        Button[] all = {navDashboardBtn, navBrowseJobsBtn, navApplicationsBtn,
                        navResumeAnalyzerBtn, navAIBtn, navProfileBtn};
        for (Button b : all) {
            if (b != null) {
                b.getStyleClass().remove("sidebar-nav-active");
            }
        }
        if (section == null) return;
        Button target = switch (section) {
            case OVERVIEW -> navDashboardBtn;
            case BROWSE_JOBS -> navBrowseJobsBtn;
            case APPLICATIONS -> navApplicationsBtn;
            case RESUME_ANALYZER -> navResumeAnalyzerBtn;
            case AI_ASSISTANT -> navAIBtn;
            case PROFILE -> navProfileBtn;
        };
        if (target != null && !target.getStyleClass().contains("sidebar-nav-active")) {
            target.getStyleClass().add("sidebar-nav-active");
        }
    }

    @FXML
    public void handleNavDashboard(ActionEvent e) {
        if (activeSection == StudentNavSection.OVERVIEW) return;
        StudentNavigationHandler.navigateToDashboard(navDashboardBtn);
    }

    @FXML
    public void handleNavBrowseJobs(ActionEvent e) {
        if (activeSection == StudentNavSection.BROWSE_JOBS) return;
        StudentNavigationHandler.navigateToBrowseJobs(navBrowseJobsBtn);
    }

    @FXML
    public void handleNavApplications(ActionEvent e) {
        if (activeSection == StudentNavSection.APPLICATIONS) return;
        StudentNavigationHandler.navigateToApplications(navApplicationsBtn);
    }

    @FXML
    public void handleNavResumeAnalyzer(ActionEvent e) {
        if (activeSection == StudentNavSection.RESUME_ANALYZER) return;
        StudentNavigationHandler.navigateToResumeAnalyzer(navResumeAnalyzerBtn);
    }

    @FXML
    public void handleNavAI(ActionEvent e) {
        if (activeSection == StudentNavSection.AI_ASSISTANT) return;
        StudentNavigationHandler.navigateToAI(navAIBtn);
    }

    @FXML
    public void handleNavProfile(ActionEvent e) {
        if (activeSection == StudentNavSection.PROFILE) return;
        StudentNavigationHandler.navigateToProfile(navProfileBtn);
    }

    @FXML
    public void handleLogout(ActionEvent e) {
        StudentNavigationHandler.handleLogout(navLogoutBtn);
    }

    public Button getNavDashboardBtn() { return navDashboardBtn; }
    public Button getNavBrowseJobsBtn() { return navBrowseJobsBtn; }
    public Button getNavApplicationsBtn() { return navApplicationsBtn; }
    public Button getNavResumeAnalyzerBtn() { return navResumeAnalyzerBtn; }
    public Button getNavAIBtn() { return navAIBtn; }
    public Button getNavProfileBtn() { return navProfileBtn; }
    public Button getNavLogoutBtn() { return navLogoutBtn; }
}
