package com.hirehub.resumes;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.controller.ResumeAnalyzerController;
import com.hirehub.controller.StudentDashboardController;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.User;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;

import java.lang.reflect.Field;
import java.net.URL;

/**
 * ResumeAnalyzerNavigationTest — Verifies that the AI Resume Analyzer FXML,
 * its controller, and both Student Dashboard entry points navigate cleanly.
 */
public class ResumeAnalyzerNavigationTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub: AI Resume Analyzer Navigation & FXML Integrity Test  ");
        System.out.println("=================================================================\n");

        try {
            // Start JavaFX runtime if not running
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already started
        }

        try {
            // Setup a mock student session so initialize() runs completely
            User mockStudent = new User();
            mockStudent.setId(5);
            mockStudent.setFullName("Bhaumik Hinunia");
            mockStudent.setEmail("bhaumikhinunia019@gmail.com");
            mockStudent.setRole(User.Role.STUDENT);
            SessionManager.getInstance().login(mockStudent);

            // Test 1: Load student_dashboard.fxml and inspect navigation controls
            testStudentDashboardFxml();

            // Test 2: Load resume_analyzer.fxml and inspect injected controller controls
            testResumeAnalyzerFxml();

            // Test 3: Verify back button and round-trip navigation configuration
            testRoundTripNavigation();

            // Test 4: Verify all student views load cleanly
            testAllStudentScreens();

        } catch (Throwable t) {
            System.err.println("[FATAL] Test failed with exception: " + t);
            t.printStackTrace();
            failed++;
        }

        System.out.println("\n=================================================================");
        System.out.printf("  NAVIGATION TEST RESULTS: %d PASSED, %d FAILED\n", passed, failed);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void testStudentDashboardFxml() throws Exception {
        System.out.println("[Test 1] Testing /fxml/student_dashboard.fxml loading & entry points...");
        URL url = ResumeAnalyzerNavigationTest.class.getResource("/fxml/student_dashboard.fxml");
        assertCondition(url != null, "Found /fxml/student_dashboard.fxml on classpath");

        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        assertCondition(root != null, "Loaded student_dashboard.fxml root node");

        StudentDashboardController controller = loader.getController();
        assertCondition(controller != null, "StudentDashboardController instantiated");

        // Verify sidebar navResumeAnalyzerBtn
        Field navBtnField = StudentDashboardController.class.getDeclaredField("navResumeAnalyzerBtn");
        navBtnField.setAccessible(true);
        Button navBtn = (Button) navBtnField.get(controller);
        assertCondition(navBtn != null, "Sidebar navResumeAnalyzerBtn is injected via @FXML");
        assertCondition(navBtn.getOnAction() != null, "Sidebar navResumeAnalyzerBtn has onAction handler attached");
        assertCondition(navBtn.getText().contains("Resume Analyzer"),
                "Sidebar button label is correct: '" + navBtn.getText() + "'");

        // Look for the dashboard action card button with text "Analyze Resume"
        Button analyzeResumeCardBtn = findButtonByText(root, "Analyze Resume");
        assertCondition(analyzeResumeCardBtn != null, "Found dashboard quick-action card button: 'Analyze Resume'");
        assertCondition(analyzeResumeCardBtn != null && analyzeResumeCardBtn.getOnAction() != null,
                "Dashboard quick-action card button has onAction handler attached");
    }

    private static void testResumeAnalyzerFxml() throws Exception {
        System.out.println("\n[Test 2] Testing /fxml/resume_analyzer.fxml loading & component hierarchy...");
        URL url = ResumeAnalyzerNavigationTest.class.getResource("/fxml/resume_analyzer.fxml");
        assertCondition(url != null, "Found /fxml/resume_analyzer.fxml on classpath");

        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        assertCondition(root != null, "Loaded resume_analyzer.fxml root node successfully");

        ResumeAnalyzerController controller = loader.getController();
        assertCondition(controller != null, "ResumeAnalyzerController instantiated");

        // Verify key injected components
        assertFieldNonNull(controller, "backBtn", Button.class);
        assertFieldNonNull(controller, "uploadResumeBtn", Button.class);
        assertFieldNonNull(controller, "replaceResumeBtn", Button.class);
        assertFieldNonNull(controller, "deleteResumeBtn", Button.class);
        assertFieldNonNull(controller, "syncProfileBtn", Button.class);
        assertFieldNonNull(controller, "jobComboBox", ComboBox.class);
        assertFieldNonNull(controller, "clearJobSelectionBtn", Button.class);
        assertFieldNonNull(controller, "generateAiReviewBtn", Button.class);
        assertFieldNonNull(controller, "historyTableView", TableView.class);
        assertFieldNonNull(controller, "matchingSkillsFlow", javafx.scene.layout.FlowPane.class);
        assertFieldNonNull(controller, "missingSkillsFlow", javafx.scene.layout.FlowPane.class);
        assertFieldNonNull(controller, "unevaluatedCriteriaBox", javafx.scene.layout.VBox.class);
    }

    private static void testRoundTripNavigation() throws Exception {
        System.out.println("\n[Test 3] Verifying Back Navigation & Controller Event Bindings...");
        URL url = ResumeAnalyzerNavigationTest.class.getResource("/fxml/resume_analyzer.fxml");
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        ResumeAnalyzerController controller = loader.getController();

        Field backBtnField = ResumeAnalyzerController.class.getDeclaredField("backBtn");
        backBtnField.setAccessible(true);
        Button backBtn = (Button) backBtnField.get(controller);
        assertCondition(backBtn != null, "Back button present");
        assertCondition(backBtn.getOnAction() != null, "Back button has onAction handler attached (#handleBack)");
        assertCondition(backBtn.getText().contains("Back to Dashboard"),
                "Back button label is correct: '" + backBtn.getText() + "'");
    }

    private static void testAllStudentScreens() {
        System.out.println("\n[Test 4] Verifying all student screens load without XML/controller errors...");
        String[] screens = {
                "/fxml/student_dashboard.fxml",
                "/fxml/resume_analyzer.fxml",
                "/fxml/job_board.fxml",
                "/fxml/my_applications.fxml",
                "/fxml/ai_assistant.fxml"
        };

        for (String screen : screens) {
            try {
                URL url = ResumeAnalyzerNavigationTest.class.getResource(screen);
                assertCondition(url != null, "Resource exists: " + screen);
                FXMLLoader loader = new FXMLLoader(url);
                Parent root = loader.load();
                assertCondition(root != null, "Loaded root node for " + screen);
            } catch (Exception e) {
                System.err.println("    [FAIL] Could not load screen " + screen + ": " + e.getMessage());
                e.printStackTrace();
                failed++;
            }
        }
    }

    private static Button findButtonByText(javafx.scene.Node root, String targetText) {
        if (root instanceof Button btn) {
            if (btn.getText() != null && btn.getText().trim().equalsIgnoreCase(targetText.trim())) {
                return btn;
            }
        }
        if (root instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            Button b = findButtonByText(sp.getContent(), targetText);
            if (b != null) return b;
        }
        if (root instanceof Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                Button b = findButtonByText(child, targetText);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static void assertFieldNonNull(Object target, String fieldName, Class<?> expectedType) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(target);
            assertCondition(val != null && expectedType.isInstance(val),
                    "Injected field '" + fieldName + "' of type " + expectedType.getSimpleName() + " is present");
        } catch (Exception e) {
            System.err.println("    [FAIL] Field '" + fieldName + "' missing or null: " + e.getMessage());
            failed++;
        }
    }

    private static void assertCondition(boolean cond, String message) {
        if (cond) {
            System.out.println("    [OK] " + message);
            passed++;
        } else {
            System.err.println("    [FAIL] " + message);
            failed++;
        }
    }
}
