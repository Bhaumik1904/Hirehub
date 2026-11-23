package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.controller.*;
import com.hirehub.model.User;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UIAccuracyQualityAuditTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("  HireHub UI Accuracy, Quality & Final Redesign Audit Suite");
        System.out.println("=================================================================\n");

        initJavaFx();

        try {
            auditAuthenticationStatistics();
            auditPlatformStatusNeutrality();
            auditDynamicUserInformation();
            auditScreenshotMappingCatalog();
            auditScreenLoadingAndLayoutIntegrity();
        } finally {
            SessionManager.getInstance().logout();
            Platform.exit();
        }

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("AUDIT VERIFICATION SUMMARY: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + description);
            passed++;
        } else {
            System.err.println("  [FAIL] " + description);
            failed++;
        }
    }

    // =========================================================================
    // 1. Authentication Statistics Audit
    // =========================================================================
    private static void auditAuthenticationStatistics() throws Exception {
        System.out.println("--- [1] Authentication Statistics & Product Messaging ---");

        Parent loginRoot = loadFxml("/fxml/login.fxml");
        String loginText = collectAllText(loginRoot);

        check("1a: login.fxml does not contain hardcoded '2,400+' metric", !loginText.contains("2,400+"));
        check("1b: login.fxml does not contain hardcoded '180+' metric", !loginText.contains("180+"));
        check("1c: login.fxml does not contain hardcoded '92%' metric", !loginText.contains("92%"));
        check("1d: login.fxml presents non-numeric value prop 'Campus First'", loginText.contains("Campus First"));
        check("1e: login.fxml presents non-numeric value prop 'Verified'", loginText.contains("Verified"));
        check("1f: login.fxml presents non-numeric value prop 'AI Powered'", loginText.contains("AI Powered"));
        check("1g: login.fxml does not expose hardcoded email 'ananya.mehta@nexoratech.com'", !loginText.contains("ananya.mehta"));

        Parent regRoot = loadFxml("/fxml/register.fxml");
        String regText = collectAllText(regRoot);

        check("1h: register.fxml does not contain hardcoded '2,400+' metric", !regText.contains("2,400+"));
        check("1i: register.fxml does not contain hardcoded '180+' metric", !regText.contains("180+"));
        check("1j: register.fxml does not contain hardcoded '92%' metric", !regText.contains("92%"));
        check("1k: register.fxml presents non-numeric value prop 'Campus First'", regText.contains("Campus First"));
        check("1l: register.fxml presents non-numeric value prop 'Verified'", regText.contains("Verified"));
        check("1m: register.fxml presents non-numeric value prop 'AI Powered'", regText.contains("AI Powered"));
    }

    // =========================================================================
    // 2. Platform Status Neutrality Audit
    // =========================================================================
    private static void auditPlatformStatusNeutrality() throws Exception {
        System.out.println("\n--- [2] Platform Status Neutrality & Health Claims ---");

        Parent adminRoot = loadFxml("/fxml/admin_dashboard.fxml");
        String adminText = collectAllText(adminRoot);

        check("2a: admin_dashboard.fxml does not claim unverified 'All services operational'",
                !adminText.contains("All services operational"));
        check("2b: admin_dashboard.fxml displays neutral 'Placement Operations'",
                adminText.contains("Placement Operations"));
        check("2c: admin_dashboard.fxml displays neutral 'System Administration'",
                adminText.contains("System Administration"));

        Parent recRoot = loadFxml("/fxml/recruiter_dashboard.fxml");
        String recText = collectAllText(recRoot);

        check("2d: recruiter_dashboard.fxml sidebar does not claim hardcoded 'Enterprise verified' status",
                !recText.contains("Enterprise verified"));
        check("2e: recruiter_dashboard.fxml displays neutral 'Recruiter Workspace'",
                recText.contains("Recruiter Workspace"));

        Parent stuRoot = loadFxml("/fxml/student_dashboard.fxml");
        String stuText = collectAllText(stuRoot);

        check("2f: student_dashboard.fxml sidebar does not claim hardcoded mock 'Strong profile 84%'",
                !stuText.contains("Strong profile"));
        check("2g: student_dashboard.fxml displays neutral 'Student Workspace'",
                stuText.contains("Student Workspace"));
    }

    // =========================================================================
    // 3. Dynamic User Information Audit
    // =========================================================================
    private static void auditDynamicUserInformation() throws Exception {
        System.out.println("\n--- [3] Dynamic User Information & Sample Identity Removal ---");

        Parent stuRoot = loadFxml("/fxml/student_dashboard.fxml");
        String stuText = collectAllText(stuRoot);

        check("3a: student_dashboard.fxml has no hardcoded 'Bhaumik Hinunia' in default template",
                !stuText.contains("Bhaumik Hinunia"));
        check("3b: student_dashboard.fxml has no hardcoded 'BH' in avatar badges",
                !stuText.contains("BH"));
        check("3c: student_dashboard.fxml profile card has no mock '28 +12%' recruiter views",
                !stuText.contains("28 +12%"));
        check("3d: student_dashboard.fxml profile card has no mock '86/100' score",
                !stuText.contains("86/100"));
        check("3e: student_dashboard.fxml profile card has no mock '8 submitted' apps",
                !stuText.contains("8 submitted"));

        Parent recRoot = loadFxml("/fxml/recruiter_dashboard.fxml");
        String recText = collectAllText(recRoot);

        check("3f: recruiter_dashboard.fxml has no hardcoded 'Ananya Mehta' in default template",
                !recText.contains("Ananya Mehta"));
        check("3g: recruiter_dashboard.fxml has no hardcoded 'Nexora Technologies' in default template",
                !recText.contains("Nexora Technologies"));
        check("3h: recruiter_dashboard.fxml has no hardcoded 'AM' in avatar badge",
                !recText.contains("AM"));

        // Dynamic session test: Rahul Sharma (Student)
        User testStudent = new User("Rahul Sharma", "rahul.sharma@campus.edu", "hash", User.Role.STUDENT);
        testStudent.setId(77);
        SessionManager.getInstance().login(testStudent);

        FXMLLoader stuLoader = new FXMLLoader(UIAccuracyQualityAuditTest.class.getResource("/fxml/student_dashboard.fxml"));
        Parent loadedStu = runOnFx(() -> {
            try {
                return stuLoader.load();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        StudentDashboardController stuCtrl = stuLoader.getController();

        Label headerName = getField(stuCtrl, "headerUserNameLabel", Label.class);
        Label headerInitials = getField(stuCtrl, "headerUserInitialsLabel", Label.class);
        Label welcomeGreeting = getField(stuCtrl, "welcomeGreetingLabel", Label.class);

        check("3i: StudentDashboardController dynamically displays 'Rahul Sharma'",
                headerName != null && "Rahul Sharma".equals(headerName.getText()));
        check("3j: StudentDashboardController dynamically generates initials 'RS'",
                headerInitials != null && "RS".equals(headerInitials.getText()));
        check("3k: Student greeting dynamically includes 'Rahul Sharma'",
                welcomeGreeting != null && welcomeGreeting.getText().contains("Rahul Sharma"));

        // Dynamic session test: Vikram Malhotra (Recruiter)
        User testRecruiter = new User("Vikram Malhotra", "v.malhotra@zenith.com", "hash", User.Role.RECRUITER);
        testRecruiter.setId(88);
        SessionManager.getInstance().login(testRecruiter);

        FXMLLoader recLoader = new FXMLLoader(UIAccuracyQualityAuditTest.class.getResource("/fxml/recruiter_dashboard.fxml"));
        Parent loadedRec = runOnFx(() -> {
            try {
                return recLoader.load();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        RecruiterDashboardController recCtrl = recLoader.getController();

        Label recInitials = getField(recCtrl, "recruiterInitialsLabel", Label.class);
        Label recWelcome = getField(recCtrl, "welcomeLabel", Label.class);

        check("3l: RecruiterDashboardController dynamically displays 'Vikram Malhotra'",
                recWelcome != null && recWelcome.getText().contains("Vikram Malhotra"));
        check("3m: RecruiterDashboardController dynamically generates initials 'VM'",
                recInitials != null && "VM".equals(recInitials.getText()));

        // Dynamic session test: Sarah Connor (Admin)
        User testAdmin = new User("Sarah Connor", "sarah.admin@hirehub.edu", "hash", User.Role.ADMIN);
        testAdmin.setId(99);
        SessionManager.getInstance().login(testAdmin);

        FXMLLoader admLoader = new FXMLLoader(UIAccuracyQualityAuditTest.class.getResource("/fxml/admin_dashboard.fxml"));
        Parent loadedAdm = runOnFx(() -> {
            try {
                return admLoader.load();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        AdminDashboardController admCtrl = admLoader.getController();

        Label admName = getField(admCtrl, "adminNameHeaderLabel", Label.class);
        Label admInitials = getField(admCtrl, "adminInitialsHeaderLabel", Label.class);

        check("3n: AdminDashboardController dynamically displays 'Sarah Connor'",
                admName != null && "Sarah Connor".equals(admName.getText()));
        check("3o: AdminDashboardController dynamically generates initials 'SC'",
                admInitials != null && "SC".equals(admInitials.getText()));
    }

    // =========================================================================
    // 4. Screenshot Mapping & Catalog Audit
    // =========================================================================
    private static void auditScreenshotMappingCatalog() {
        System.out.println("\n--- [4] Screenshot Folder & Filename Reconciled Audit ---");

        File imagesDir = new File("D:/HireHub/images");
        check("4a: D:/HireHub/images directory exists", imagesDir.exists() && imagesDir.isDirectory());

        String[] expectedFiles = {
                "admin-audit-log.png",
                "admin-command-center.png",
                "admin-jobs-recruitment.png",
                "admin-moderation.png",
                "admin-recruiter-engagement.png",
                "admin-student-readiness.png",
                "admin-user-management.png",
                "admin-verification.png",
                "recruiter-applicants.png",
                "recruiter-company-profile.png",
                "recruiter-interviews.png",
                "recruiter-job-management.png",
                "recruiter-overview.png",
                "recruiter-post-job.png",
                "recruiter-settings.png",
                "recruiter-talent-pool.png",
                "student-applications.png",
                "student-browse-jobs.png",
                "student-career-assistant.png",
                "student-overview.png",
                "student-profile.png",
                "student-resume-analyzer.png",
                "student-settings.png"
        };

        check("4b: Catalog specifies exactly 23 screenshots", expectedFiles.length == 23);

        int presentCount = 0;
        for (String filename : expectedFiles) {
            File f = new File(imagesDir, filename);
            if (f.exists() && f.isFile() && f.length() > 0) {
                presentCount++;
            } else {
                System.err.println("  [MISSING] Screenshot not found: " + filename);
            }
        }
        check("4c: All 23 screenshot image files present and non-empty on disk", presentCount == 23);
    }

    // =========================================================================
    // 5. Screen Loading, Scrolling & Overlap Integrity Audit
    // =========================================================================
    private static void auditScreenLoadingAndLayoutIntegrity() throws Exception {
        System.out.println("\n--- [5] Screen Loading & Layout Quality Verification ---");

        String[] fxmlList = {
                "/fxml/login.fxml",
                "/fxml/register.fxml",
                "/fxml/student_dashboard.fxml",
                "/fxml/job_board.fxml",
                "/fxml/my_applications.fxml",
                "/fxml/resume_analyzer.fxml",
                "/fxml/ai_assistant.fxml",
                "/fxml/recruiter_dashboard.fxml",
                "/fxml/job_form.fxml",
                "/fxml/applicant_management.fxml",
                "/fxml/admin_dashboard.fxml"
        };

        for (String fxml : fxmlList) {
            Parent root = loadFxml(fxml);
            check("5: Successfully loaded FXML on FX toolkit: " + fxml, root != null);
            check("5: Root node has valid CSS class or layout: " + fxml, root.getStyleClass().size() > 0 || root instanceof Pane);
        }

        // Verify Admin Dashboard 8 Panes
        User testAdmin = new User("Admin", "admin@hirehub.edu", "hash", User.Role.ADMIN);
        SessionManager.getInstance().login(testAdmin);
        FXMLLoader admLoader = new FXMLLoader(UIAccuracyQualityAuditTest.class.getResource("/fxml/admin_dashboard.fxml"));
        Parent admRoot = runOnFx(() -> {
            try { return admLoader.load(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        AdminDashboardController admCtrl = admLoader.getController();

        String[] adminPanes = {
                "overviewPane", "jobsPane", "studentsPane", "recruitersPane",
                "recruiterVerificationPane", "jobModerationPane", "usersPane", "activityPane"
        };
        for (String paneName : adminPanes) {
            VBox pane = getField(admCtrl, paneName, VBox.class);
            check("5: Admin pane exists and injected: " + paneName, pane != null);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private static Parent loadFxml(String path) throws Exception {
        return runOnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(UIAccuracyQualityAuditTest.class.getResource(path));
                return loader.load();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load " + path, e);
            }
        });
    }

    private static String collectAllText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectTextRecursive(node, sb);
        return sb.toString();
    }

    private static void collectTextRecursive(Node node, StringBuilder sb) {
        if (node instanceof Labeled labeled) {
            sb.append(" ").append(labeled.getText());
        } else if (node instanceof TextInputControl textInput) {
            sb.append(" ").append(textInput.getText());
            sb.append(" ").append(textInput.getPromptText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTextRecursive(child, sb);
            }
        }
    }

    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Class<?> curr = target.getClass();
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField(name);
                f.setAccessible(true);
                return type.cast(f.get(target));
            } catch (NoSuchFieldException ignored) {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    private static <T> T runOnFx(FxCallable<T> callable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return callable.call();
        }
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(callable.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("FX thread execution timed out");
        }
        if (err.get() != null) {
            throw new RuntimeException(err.get());
        }
        return ref.get();
    }

    @FunctionalInterface
    interface FxCallable<T> {
        T call() throws Exception;
    }
}
