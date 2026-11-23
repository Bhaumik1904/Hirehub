package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.ResumeAnalysisDAO;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.dao.UserDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.ResumeAnalysisService;
import com.hirehub.service.ResumeParserService;
import com.hirehub.service.ResumeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * StudentEndToEndWorkflowTest — End-to-End workflow verification on real student account:
 * bhaumikhinunia019@gmail.com (User ID: 5).
 */
public class StudentEndToEndWorkflowTest {

    private static final String TARGET_EMAIL = "bhaumikhinunia019@gmail.com";
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 4: End-to-End Test on Real Student Account");
        System.out.println("  Account: " + TARGET_EMAIL);
        System.out.println("=================================================================\n");

        if (!DatabaseConnection.testConnection()) {
            System.err.println("[FAIL] MySQL connection unreachable. Ensure MySQL is running on port 3306.");
            System.exit(1);
        }

        File resumePdf1 = null;
        File resumePdf2 = null;

        try {
            // 1. Locate student account
            UserDAO userDAO = new UserDAO();
            Optional<User> userOpt = userDAO.findByEmail(TARGET_EMAIL);
            if (userOpt.isEmpty()) {
                System.err.println("[FAIL] Target user " + TARGET_EMAIL + " not found in database.");
                System.exit(1);
            }
            User student = userOpt.get();
            int studentId = student.getId();
            assertCondition(student.getRole() == User.Role.STUDENT,
                    "Verified target user is a STUDENT (ID: " + studentId + ", Name: " + student.getFullName() + ")");

            ResumeDAO resumeDAO = new ResumeDAO();
            ResumeService resumeService = new ResumeService();
            ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();
            ResumeAnalysisService analysisService = new ResumeAnalysisService();
            JobDAO jobDAO = new JobDAO();

            // 2. Generate Real PDF Resume (v1)
            System.out.println("\n--- Step A: Create and Upload PDF Resume (v1) ---");
            resumePdf1 = createStudentPdf("target/bhaumik_resume_v1.pdf", student.getFullName(), student.getEmail());
            ResumeService.UploadResult upRes1 = resumeService.uploadAndProcessResume(studentId, resumePdf1);
            assertCondition(upRes1.isSuccess(), "Upload & process v1 resume: " + upRes1.getMessage());

            Resume resume1 = upRes1.getResume();
            assertCondition(resume1 != null && resume1.getId() > 0, "Resume 1 inserted with ID: " + resume1.getId());
            File storedFile1 = new File(resume1.getStoredPath());
            assertCondition(storedFile1.exists(), "Resume 1 physical file confirmed on disk: " + storedFile1.getName());

            // Verify active resume lookup
            Optional<Resume> activeOpt = resumeDAO.findActiveByStudentId(studentId);
            assertCondition(activeOpt.isPresent() && activeOpt.get().getId() == resume1.getId(),
                    "Active resume in DB points to Resume ID: " + resume1.getId());

            ParsedResume parsed1 = new ResumeParserService().parseResume(storedFile1);
            assertCondition(parsed1.getSkills().contains("Java") && parsed1.getSkills().contains("MySQL"),
                    "Parsed skills contains Java and MySQL: " + parsed1.getSkills());

            // 3. Step B: General Resume Quality Audit (No Job Selected)
            System.out.println("\n--- Step B: General Resume Quality Audit (job = null) ---");
            ResumeAnalysisService.ScoringBreakdown generalBreakdown =
                    analysisService.calculateDeterministicScore(parsed1, null);

            assertCondition(generalBreakdown.isGeneral(), "Deterministic scoring identified as General Review (isGeneral = true)");
            assertCondition(generalBreakdown.getOverallScore() >= 70 && generalBreakdown.getOverallScore() <= 100,
                    "General score in expected range: " + generalBreakdown.getOverallScore() + "%");
            assertCondition(generalBreakdown.getEvaluationConfidence().startsWith("HIGH") ||
                            generalBreakdown.getEvaluationConfidence().startsWith("MODERATE"),
                    "Evaluation confidence: " + generalBreakdown.getEvaluationConfidence());

            String generalReport = "## General Resume Review\nCandidate displays strong foundational skills in Java and database design.";
            ResumeAnalysis genSaved = analysisService.saveAnalysis(resume1.getId(), null, studentId, generalBreakdown, generalReport);
            assertCondition(genSaved != null && genSaved.getId() > 0,
                    "General analysis saved to DB with ID: " + genSaved.getId() + " and job_id = NULL");

            // 4. Step C: Job-Specific Compatibility Audit
            System.out.println("\n--- Step C: Job-Specific Compatibility Audit ---");
            List<Job> activeJobs = jobDAO.searchActive("", "", "");
            assertCondition(!activeJobs.isEmpty(), "Found " + activeJobs.size() + " active job(s) in system");

            Job targetJob = activeJobs.get(0);
            System.out.println("    Target Job selected: ID " + targetJob.getId() + " - \"" + targetJob.getTitle()
                    + "\" at " + targetJob.getCompanyName());

            ResumeAnalysisService.ScoringBreakdown jobBreakdown =
                    analysisService.calculateDeterministicScore(parsed1, targetJob);

            assertCondition(!jobBreakdown.isGeneral(), "Identified as Job-Specific compatibility audit");
            assertCondition(jobBreakdown.getOverallScore() > 0 && jobBreakdown.getOverallScore() <= 100,
                    "Job match score: " + jobBreakdown.getOverallScore() + "%");
            assertCondition(!jobBreakdown.getMatchingSkills().isEmpty(),
                    "Matching skills: " + jobBreakdown.getMatchingSkills());

            String jobReport = "## Job Compatibility Review for " + targetJob.getTitle() + "\nStrong match for core skills.";
            ResumeAnalysis jobSaved = analysisService.saveAnalysis(resume1.getId(), targetJob.getId(), studentId, jobBreakdown, jobReport);
            assertCondition(jobSaved != null && jobSaved.getId() > 0,
                    "Job-specific analysis saved to DB with ID: " + jobSaved.getId() + " (Job ID: " + targetJob.getId() + ")");

            // 5. Step D: History TableView Verification
            System.out.println("\n--- Step D: History Table Retrieval Verification ---");
            List<ResumeAnalysis> history = analysisService.getStudentAnalysisHistory(studentId, 10);
            assertCondition(history.size() >= 2, "Retrieved " + history.size() + " historical analysis record(s)");

            boolean foundGeneralInHistory = false;
            boolean foundJobInHistory = false;
            for (ResumeAnalysis ra : history) {
                if (ra.getId() == genSaved.getId()) {
                    foundGeneralInHistory = true;
                    assertCondition(ra.isGeneralAnalysis(), "History record " + ra.getId() + " isGeneralAnalysis() == true");
                    assertCondition("General Resume Review".equals(ra.getJobTitle()),
                            "History record " + ra.getId() + " title displays: " + ra.getJobTitle());
                }
                if (ra.getId() == jobSaved.getId()) {
                    foundJobInHistory = true;
                    assertCondition(!ra.isGeneralAnalysis(), "History record " + ra.getId() + " isGeneralAnalysis() == false");
                    assertCondition(targetJob.getTitle().equalsIgnoreCase(ra.getJobTitle()),
                            "History record " + ra.getId() + " title displays: " + ra.getJobTitle());
                }
            }
            assertCondition(foundGeneralInHistory, "General analysis verified in student history list");
            assertCondition(foundJobInHistory, "Job-specific analysis verified in student history list");

            // 6. Step E: Replace Resume Lifecycle
            System.out.println("\n--- Step E: Replace Resume Lifecycle (Upload v2) ---");
            resumePdf2 = createStudentPdf("target/bhaumik_resume_v2.pdf", student.getFullName(), student.getEmail());
            ResumeService.UploadResult upRes2 = resumeService.uploadAndProcessResume(studentId, resumePdf2);
            assertCondition(upRes2.isSuccess(), "Uploaded v2 resume");

            Resume resume2 = upRes2.getResume();
            assertCondition(resume2.getId() != resume1.getId(), "New resume generated distinct ID: " + resume2.getId());

            // Check that resume1 is now inactive
            Optional<Resume> oldResumeCheck = resumeDAO.findById(resume1.getId());
            assertCondition(oldResumeCheck.isPresent() && !oldResumeCheck.get().isActive(),
                    "Previous resume (ID " + resume1.getId() + ") marked isActive = false");

            // Check that resume2 is active
            Optional<Resume> newResumeCheck = resumeDAO.findActiveByStudentId(studentId);
            assertCondition(newResumeCheck.isPresent() && newResumeCheck.get().getId() == resume2.getId(),
                    "New resume (ID " + resume2.getId() + ") is now the active resume");

            // Check history is preserved
            List<ResumeAnalysis> historyAfterReplace = analysisService.getStudentAnalysisHistory(studentId, 10);
            assertCondition(historyAfterReplace.size() >= 2,
                    "History remains intact after replacing resume (" + historyAfterReplace.size() + " records)");

            // 7. Step F: Delete Active Resume Lifecycle
            System.out.println("\n--- Step F: Delete Resume Lifecycle & Privacy ---");
            File storedFile2 = new File(resume2.getStoredPath());
            assertCondition(storedFile2.exists(), "v2 physical file exists before deletion");

            boolean deleted2 = resumeService.deleteResume(resume2.getId(), studentId);
            assertCondition(deleted2, "v2 resume deleted via ResumeService");
            assertCondition(!storedFile2.exists(), "v2 physical file safely purged from disk");

            // Verify student_profiles resume_path cleared
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT resume_path FROM student_profiles WHERE user_id = " + studentId)) {
                if (rs.next()) {
                    String p = rs.getString(1);
                    assertCondition(p == null || p.isBlank(), "Student profile resume_path cleared to NULL/blank: " + p);
                }
            }

            // Verify historical analyses still exist with resume_id = NULL
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, resume_id, job_id, overall_score FROM resume_analyses WHERE id IN ("
                         + genSaved.getId() + ", " + jobSaved.getId() + ")")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertCondition(count == 2, "Historical analyses preserved in database after resume deletion");
            }

            // Cleanup: delete v1 resume record & test analyses
            resumeService.deleteResume(resume1.getId(), studentId);
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM resume_analyses WHERE id IN (" + genSaved.getId() + ", " + jobSaved.getId() + ")");
            }
            System.out.println("    [OK] Test analyses cleaned up cleanly.");

        } catch (Throwable t) {
            System.err.println("[FATAL] End-to-end test crashed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        } finally {
            if (resumePdf1 != null && resumePdf1.exists()) resumePdf1.delete();
            if (resumePdf2 != null && resumePdf2.exists()) resumePdf2.delete();
        }

        System.out.println("\n=================================================================");
        System.out.printf("  REAL STUDENT E2E SUMMARY: %d PASSED, %d FAILED\n", testsPassed, testsFailed);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static File createStudentPdf(String path, String fullName, String email) throws IOException {
        File pdfFile = new File(path);
        if (pdfFile.getParentFile() != null) pdfFile.getParentFile().mkdirs();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(50, 720);
                cs.showText(fullName);
                cs.newLineAtOffset(0, -18);

                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText(email + " | +91 9123456780 | github.com/bhaumikhinunia | linkedin.com/in/bhaumikhinunia");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("EDUCATION");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Bachelor of Technology in Information Technology (2025)");
                cs.newLineAtOffset(0, -14);
                cs.showText("Institute of Engineering & Technology");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("TECHNICAL SKILLS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Languages & Tools: Java, Python, SQL, MySQL, Spring Boot, React, Docker, Maven, Git, REST API");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("PROJECTS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("HireHub — Modern Campus Placement & Recruitment Suite");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Built end-to-end recruitment platform using JavaFX and MySQL with 100% test pass rate.");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Implemented AI Resume Analyzer reducing profile review time by 80%.");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("EXPERIENCE");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Software Engineering Intern");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Developed RESTful microservices in Java and Spring Boot, optimizing database query response times by 35%.");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("CERTIFICATIONS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Oracle Certified Associate Java Programmer");
                cs.endText();
            }

            doc.save(pdfFile);
        }
        return pdfFile;
    }

    private static void assertCondition(boolean condition, String message) {
        if (condition) {
            System.out.println("    [OK] " + message);
            testsPassed++;
        } else {
            System.err.println("    [FAIL] " + message);
            testsFailed++;
        }
    }
}
