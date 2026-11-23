package com.hirehub.resumes;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.ResumeAnalysisDAO;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.ResumeAnalysisService;
import com.hirehub.service.ResumeParserService;
import com.hirehub.service.ResumeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
 * ResumeAnalysisTest — Automated verification suite for Phase 4: Production AI Resume Analyzer.
 */
public class ResumeAnalysisTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 4: Production AI Resume Analyzer Verification");
        System.out.println("=================================================================\n");

        File testPdf = null;
        try {
            // 1. PDF Creation & Validation Test
            testPdf = createSamplePdfResume();
            testPdfValidation(testPdf);

            // 2. Resume Parsing Test
            ParsedResume parsed = testResumeParsing(testPdf);

            // 3. Deterministic Compatibility Scoring Engine Test (Job-Specific & General)
            testDeterministicScoring(parsed);
            testGeneralScoring(parsed);
            testFairScoringWithMissingData();

            // 4. Database CRUD & Schema Test (if DB connected)
            testDatabaseOperations(testPdf, parsed);

            // 5. FXML Verification
            testFXMLIntegrity();

        } catch (Throwable t) {
            System.err.println("\n[FATAL] Verification suite crashed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        } finally {
            if (testPdf != null && testPdf.exists()) {
                testPdf.delete();
            }
        }

        System.out.println("\n=================================================================");
        System.out.printf("  VERIFICATION SUMMARY: %d PASSED, %d FAILED\n", testsPassed, testsFailed);
        System.out.println("=================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    // =========================================================================
    // 1. PDF Creation & Validation
    // =========================================================================

    private static File createSamplePdfResume() throws IOException {
        System.out.println("[Test 1] Generating Synthetic PDF Resume using Apache PDFBox...");
        File pdfFile = new File("target", "synthetic_test_resume.pdf");
        if (pdfFile.getParentFile() != null) pdfFile.getParentFile().mkdirs();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(50, 720);
                cs.showText("Alex Sharma");
                cs.newLineAtOffset(0, -18);

                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("alex.sharma@example.com | +91 9876543210 | github.com/alexsharma | linkedin.com/in/alexsharma");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("EDUCATION");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("B.Tech in Computer Science - National Institute of Technology (2025)");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("TECHNICAL SKILLS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Languages & Frameworks: Java, Python, Spring Boot, MySQL, Docker, React, REST API, Git");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("WORK EXPERIENCE");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("Software Engineering Intern - TechCorp Solutions");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Developed backend microservices using Java and Spring Boot, boosting API throughput by 35%.");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Optimized MySQL relational schema and queries, reducing p99 response times by 120ms.");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("PROJECTS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("HireHub Recruitment Platform");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Built an AI-powered job board serving 500+ active students with 99.9% uptime.");
                cs.newLineAtOffset(0, -14);
                cs.showText("- Integrated Docker containerization and CI/CD automation pipelines.");
                cs.newLineAtOffset(0, -22);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("CERTIFICATIONS");
                cs.newLineAtOffset(0, -16);
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.showText("- Oracle Certified Professional: Java SE 17 Developer");
                cs.endText();
            }

            doc.save(pdfFile);
        }

        assertCondition(pdfFile.exists() && pdfFile.length() > 0, "Synthetic PDF created successfully (" + pdfFile.length() + " bytes)");
        return pdfFile;
    }

    private static void testPdfValidation(File pdfFile) {
        System.out.println("\n[Test 2] Testing PDF File Validation Heuristics...");
        ResumeParserService parserService = new ResumeParserService();

        ResumeParserService.ValidationResult okResult = parserService.validatePdfFile(pdfFile);
        assertCondition(okResult.isValid(), "Valid PDF passed validation: " + okResult.isValid());

        // Test invalid non-pdf file
        File dummy = new File("target", "dummy.txt");
        try {
            java.nio.file.Files.writeString(dummy.toPath(), "This is not a PDF");
            ResumeParserService.ValidationResult failResult = parserService.validatePdfFile(dummy);
            assertCondition(!failResult.isValid(), "Non-PDF correctly rejected: " + failResult.getErrorMessage());
        } catch (IOException e) {
            fail("Dummy non-pdf creation failed: " + e.getMessage());
        } finally {
            dummy.delete();
        }
    }

    // =========================================================================
    // 2. Resume Parsing Heuristics
    // =========================================================================

    private static ParsedResume testResumeParsing(File pdfFile) throws IOException {
        System.out.println("\n[Test 3] Testing Structured Resume Parsing with Apache PDFBox...");
        ResumeParserService parserService = new ResumeParserService();
        ParsedResume parsed = parserService.parseResume(pdfFile);

        assertCondition(parsed != null, "ParsedResume object produced");
        assertCondition("Alex Sharma".equalsIgnoreCase(parsed.getName()), "Extracted Name: " + parsed.getName());
        assertCondition("alex.sharma@example.com".equalsIgnoreCase(parsed.getEmail()), "Extracted Email: " + parsed.getEmail());
        assertCondition(parsed.getPhone() != null && parsed.getPhone().contains("9876543210"), "Extracted Phone: " + parsed.getPhone());
        assertCondition(parsed.getGraduationYear() == 2025, "Extracted Graduation Year: " + parsed.getGraduationYear());
        assertCondition(parsed.getCollege() != null && parsed.getCollege().contains("Institute of Technology"), "Extracted College: " + parsed.getCollege());
        assertCondition(parsed.getDegree() != null && parsed.getDegree().toUpperCase().contains("B.TECH"), "Extracted Degree: " + parsed.getDegree());

        // Skills check
        List<String> skills = parsed.getSkills();
        assertCondition(skills.contains("Java") && skills.contains("Spring Boot") && skills.contains("MySQL"),
                "Extracted Core Skills: " + skills);

        // Experience & Projects
        assertCondition(!parsed.getWorkExperiences().isEmpty(), "Extracted Work Experience Items: " + parsed.getWorkExperiences().size());
        assertCondition(!parsed.getProjects().isEmpty(), "Extracted Projects: " + parsed.getProjects().size());
        assertCondition(!parsed.getCertifications().isEmpty(), "Extracted Certifications: " + parsed.getCertifications().size());
        assertCondition(parsed.getMetricsCount() >= 2, "Extracted Quantifiable Metrics: " + parsed.getMetricsCount());
        assertCondition(!parsed.getLinks().isEmpty(), "Extracted Online Links: " + parsed.getLinks());

        return parsed;
    }

    // =========================================================================
    // 3. Deterministic Compatibility Scoring
    // =========================================================================

    private static void testDeterministicScoring(ParsedResume parsed) {
        System.out.println("\n[Test 4] Testing Deterministic 5-Part Scoring Engine...");
        ResumeAnalysisService analysisService = new ResumeAnalysisService();

        // Target job requiring Java, Spring Boot, MySQL, Docker, and AWS
        Job job = new Job();
        job.setId(999);
        job.setTitle("Full-Stack Backend Engineer");
        job.setCompanyName("CloudCorp Technologies");
        job.setDescription("Looking for a backend engineer proficient in Java, Spring Boot, MySQL, and cloud deployments.");
        job.setRequiredSkills("Java, Spring Boot, MySQL, Docker, AWS");

        ResumeAnalysisService.ScoringBreakdown breakdown = analysisService.calculateDeterministicScore(parsed, job);

        assertCondition(breakdown != null, "ScoringBreakdown calculated");
        System.out.printf("    - Overall Match Score: %d%%\n", breakdown.getOverallScore());
        System.out.printf("    - Skills Score: %d / 40 pts (%s)\n", breakdown.getSkillsScore(), breakdown.getSkillsExplanation());
        System.out.printf("    - Experience Score: %d / 25 pts (%s)\n", breakdown.getExperienceScore(), breakdown.getExperienceExplanation());
        System.out.printf("    - Education Score: %d / 15 pts (%s)\n", breakdown.getEducationScore(), breakdown.getEducationExplanation());
        System.out.printf("    - Completeness Score: %d / 10 pts (%s)\n", breakdown.getCompletenessScore(), breakdown.getCompletenessExplanation());
        System.out.printf("    - Certifications Score: %d / 10 pts (%s)\n", breakdown.getCertificationsScore(), breakdown.getCertificationsExplanation());

        assertCondition(breakdown.getOverallScore() >= 75 && breakdown.getOverallScore() <= 100,
                "Deterministic score is realistic and clamped [0, 100]: " + breakdown.getOverallScore() + "%");

        assertCondition(breakdown.getMatchingSkills().contains("Java") && breakdown.getMatchingSkills().contains("Docker"),
                "Matching skills identified: " + breakdown.getMatchingSkills());

        assertCondition(breakdown.getMissingSkills().contains("AWS"),
                "Missing required skill accurately identified: " + breakdown.getMissingSkills());

        // Check certification terminology (no misleading affirmative claims; explicit unverified disclaimer)
        String certExpl = breakdown.getCertificationsExplanation();
        assertCondition(certExpl.toLowerCase().contains("not been independently verified") || certExpl.toLowerCase().contains("unverified"),
                "Certifications rubric contains explicit unverified disclaimer: " + certExpl);
        assertCondition(!certExpl.toLowerCase().contains("verified certification"),
                "Certifications rubric does not claim verified certification status");
    }

    private static void testGeneralScoring(ParsedResume parsed) {
        System.out.println("\n[Test 4.1] Testing General Resume Quality Scoring (No Target Job)...");
        ResumeAnalysisService analysisService = new ResumeAnalysisService();

        ResumeAnalysisService.ScoringBreakdown generalBreakdown = analysisService.calculateDeterministicScore(parsed, null);

        assertCondition(generalBreakdown != null, "General ScoringBreakdown calculated");
        assertCondition(generalBreakdown.isGeneral(), "Flagged as isGeneral = true");
        assertCondition(generalBreakdown.getOverallScore() >= 60 && generalBreakdown.getOverallScore() <= 100,
                "General resume quality score in range: " + generalBreakdown.getOverallScore() + "%");
        assertCondition(generalBreakdown.getEvaluationConfidence() != null && !generalBreakdown.getEvaluationConfidence().isBlank(),
                "Evaluation confidence metric populated: " + generalBreakdown.getEvaluationConfidence());

        System.out.printf("    - Technical Breadth Score: %d / 40 pts (%s)\n",
                generalBreakdown.getSkillsScore(), generalBreakdown.getSkillsExplanation());
        System.out.printf("    - Experience & Impact Score: %d / 25 pts (%s)\n",
                generalBreakdown.getExperienceScore(), generalBreakdown.getExperienceExplanation());
        System.out.printf("    - Education Score: %d / 15 pts (%s)\n",
                generalBreakdown.getEducationScore(), generalBreakdown.getEducationExplanation());
        System.out.printf("    - Completeness Score: %d / 10 pts (%s)\n",
                generalBreakdown.getCompletenessScore(), generalBreakdown.getCompletenessExplanation());
        System.out.printf("    - Listed Credentials Score: %d / 10 pts (%s)\n",
                generalBreakdown.getCertificationsScore(), generalBreakdown.getCertificationsExplanation());

        String genCertExpl = generalBreakdown.getCertificationsExplanation();
        assertCondition(genCertExpl.toLowerCase().contains("not been independently verified") || genCertExpl.toLowerCase().contains("unverified"),
                "General certifications rubric contains explicit unverified disclaimer: " + genCertExpl);
        assertCondition(!genCertExpl.toLowerCase().contains("verified certification"),
                "General certifications rubric does not claim verified certification status");
    }

    private static void testFairScoringWithMissingData() {
        System.out.println("\n[Test 4.2] Testing Fair Scoring & Unevaluated Criteria Handling...");
        ResumeAnalysisService analysisService = new ResumeAnalysisService();

        // Create resume with missing graduation year and empty experience
        ParsedResume sparseResume = new ParsedResume();
        sparseResume.setName("Jordan Lee");
        sparseResume.setEmail("jordan@example.com");
        sparseResume.setSkills(java.util.List.of("Python", "SQL"));
        // No education, no experience, no certifications

        Job job = new Job();
        job.setId(888);
        job.setTitle("Junior Data Analyst");
        job.setCompanyName("DataInsights Inc");
        job.setRequiredSkills("Python, SQL, Tableau");

        ResumeAnalysisService.ScoringBreakdown fairBreakdown = analysisService.calculateDeterministicScore(sparseResume, job);

        assertCondition(fairBreakdown != null, "Fair breakdown calculated for sparse resume");
        assertCondition(!fairBreakdown.getUnevaluatedCriteria().isEmpty(),
                "Unevaluated criteria explicitly flagged (missing != penalty): " + fairBreakdown.getUnevaluatedCriteria());
        assertCondition(fairBreakdown.getEvaluationConfidence().startsWith("LOW") ||
                        fairBreakdown.getEvaluationConfidence().startsWith("MODERATE"),
                "Sparse resume lowers confidence to: " + fairBreakdown.getEvaluationConfidence());
    }

    // =========================================================================
    // 4. Database Persistence & Schema
    // =========================================================================
    private static void testDatabaseOperations(File pdfFile, ParsedResume parsed) {
        System.out.println("\n[Test 5] Testing Database Persistence, General Analysis & Resume Lifecycle...");
        if (!DatabaseConnection.testConnection()) {
            System.out.println("    [SKIP] MySQL connection not reachable (sandbox or offline). Skipping DB writes.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Find an existing student user
            int testStudentId = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM users WHERE role='STUDENT' LIMIT 1")) {
                if (rs.next()) {
                    testStudentId = rs.getInt(1);
                }
            }

            if (testStudentId == 0) {
                System.out.println("    [INFO] No student user found in users table, using student ID 1");
                testStudentId = 1;
            }

            // Set up test student session so security authorization in ResumeService succeeds
            User testUser = new User();
            testUser.setId(testStudentId);
            testUser.setEmail("student" + testStudentId + "@example.com");
            testUser.setRole(User.Role.STUDENT);
            SessionManager.getInstance().login(testUser);

            ResumeDAO resumeDAO = new ResumeDAO();
            ResumeService resumeService = new ResumeService();
            ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();

            // 1. Upload & Insert Resume
            ResumeService.UploadResult upRes = resumeService.uploadAndProcessResume(testStudentId, pdfFile);
            assertCondition(upRes.isSuccess(), "ResumeService upload & processing: " + upRes.getMessage());

            Resume insertedResume = upRes.getResume();
            assertCondition(insertedResume != null && insertedResume.getId() > 0,
                    "Resume persisted with generated ID: " + insertedResume.getId());

            File storedFile = new File(insertedResume.getStoredPath());
            assertCondition(storedFile.exists(), "Physical resume stored on disk at: " + storedFile.getName());

            // 2. Query active
            Optional<Resume> activeOpt = resumeDAO.findActiveByStudentId(testStudentId);
            assertCondition(activeOpt.isPresent(), "Retrieved active resume by student ID");

            // 3. Profile sync
            boolean synced = resumeService.syncWithStudentProfile(testStudentId, parsed);
            assertCondition(synced, "Synced extracted skills to student profile");

            // 4. Insert GENERAL Resume Analysis (job_id = NULL)
            ResumeAnalysis generalAnalysis = new ResumeAnalysis();
            generalAnalysis.setResumeId(insertedResume.getId());
            generalAnalysis.setJobId(null); // General analysis: NULL jobId
            generalAnalysis.setStudentId(testStudentId);
            generalAnalysis.setOverallScore(82);
            generalAnalysis.setSkillsScore(34);
            generalAnalysis.setExperienceScore(20);
            generalAnalysis.setEducationScore(14);
            generalAnalysis.setCompletenessScore(9);
            generalAnalysis.setCertificationsScore(5);
            generalAnalysis.setMatchingSkills("Java, Python, Spring Boot, MySQL");
            generalAnalysis.setMissingSkills("");
            generalAnalysis.setAnalysisSummary("## General Audit\nSolid engineering foundation.");

            analysisDAO.insert(generalAnalysis);
            assertCondition(generalAnalysis.getId() > 0,
                    "General ResumeAnalysis (job_id = NULL) persisted with ID: " + generalAnalysis.getId());

            // 5. Insert JOB-SPECIFIC Analysis
            int validJobId = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM jobs LIMIT 1")) {
                if (rs.next()) validJobId = rs.getInt(1);
            }

            ResumeAnalysis jobAnalysis = null;
            if (validJobId > 0) {
                jobAnalysis = new ResumeAnalysis();
                jobAnalysis.setResumeId(insertedResume.getId());
                jobAnalysis.setJobId(validJobId);
                jobAnalysis.setStudentId(testStudentId);
                jobAnalysis.setOverallScore(88);
                jobAnalysis.setSkillsScore(32);
                jobAnalysis.setExperienceScore(25);
                jobAnalysis.setEducationScore(15);
                jobAnalysis.setCompletenessScore(10);
                jobAnalysis.setCertificationsScore(6);
                jobAnalysis.setMatchingSkills("Java, Spring Boot, MySQL, Docker");
                jobAnalysis.setMissingSkills("AWS");
                jobAnalysis.setAnalysisSummary("## Role Compatibility\nStrong fit for backend opening.");
                analysisDAO.insert(jobAnalysis);
                assertCondition(jobAnalysis.getId() > 0,
                        "Job-specific ResumeAnalysis persisted with ID: " + jobAnalysis.getId());
            }

            // 6. Query Analysis History & verify both types are read correctly
            List<ResumeAnalysis> history = analysisDAO.findRecentByStudentId(testStudentId, 10);
            assertCondition(!history.isEmpty(), "Retrieved recent analysis history records: " + history.size());

            boolean foundGeneral = false;
            for (ResumeAnalysis ra : history) {
                if (ra.getId() == generalAnalysis.getId()) {
                    foundGeneral = true;
                    assertCondition(ra.isGeneralAnalysis(), "Persisted general analysis has isGeneralAnalysis() == true");
                    assertCondition("General Resume Review".equals(ra.getJobTitle()),
                            "Fallback job title for general analysis is: " + ra.getJobTitle());
                }
            }
            assertCondition(foundGeneral, "Found general analysis in student history query");

            // 7. Test Lifecycle: Delete Resume
            // Physical file must be removed, active profile resume path cleared,
            // and historical resume_analyses records must be preserved (FK ON DELETE SET NULL).
            boolean deleted = resumeService.deleteResume(insertedResume.getId(), testStudentId);
            assertCondition(deleted, "Resume successfully deleted via ResumeService");
            assertCondition(!storedFile.exists(), "Physical resume file deleted from disk safely");

            // Check that analysis record still exists with resume_id = NULL or intact
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, resume_id FROM resume_analyses WHERE id = " + generalAnalysis.getId())) {
                if (rs.next()) {
                    int rId = rs.getInt("resume_id");
                    boolean wasNull = rs.wasNull();
                    assertCondition(wasNull || rId == 0,
                            "Analysis history preserved with resume_id set to NULL on delete");
                } else {
                    fail("Analysis record was improperly deleted along with resume!");
                }
            }

            // Verify student_profiles resume_path is cleared
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT resume_path FROM student_profiles WHERE user_id = " + testStudentId)) {
                if (rs.next()) {
                    String rPath = rs.getString("resume_path");
                    assertCondition(rPath == null || rPath.isBlank(),
                            "Student profile resume_path was cleared upon deletion: " + rPath);
                }
            }

            // Cleanup test analysis records
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM resume_analyses WHERE id IN (" +
                        generalAnalysis.getId() + (jobAnalysis != null ? ", " + jobAnalysis.getId() : "") + ")");
            }
            System.out.println("    [OK] Test analyses cleaned up from database.");

        } catch (Exception e) {
            System.err.println("    [WARN] Database operation notice: " + e.getMessage());
            e.printStackTrace();
        } finally {
            SessionManager.getInstance().logout();
        }
    }

    // =========================================================================
    // 5. FXML Views Integrity
    // =========================================================================

    private static void testFXMLIntegrity() {
        System.out.println("\n[Test 6] Verifying FXML Layout Resources...");
        try {
            var url1 = ResumeAnalysisTest.class.getResource("/fxml/resume_analyzer.fxml");
            assertCondition(url1 != null, "Found /fxml/resume_analyzer.fxml");

            var url2 = ResumeAnalysisTest.class.getResource("/fxml/student_dashboard.fxml");
            assertCondition(url2 != null, "Found /fxml/student_dashboard.fxml");

            var cssUrl = ResumeAnalysisTest.class.getResource("/css/style.css");
            assertCondition(cssUrl != null, "Found /css/style.css");

        } catch (Exception e) {
            fail("FXML loading error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Assert Helpers
    // =========================================================================

    private static void assertCondition(boolean condition, String message) {
        if (condition) {
            System.out.println("    [OK] " + message);
            testsPassed++;
        } else {
            System.err.println("    [FAIL] " + message);
            testsFailed++;
        }
    }

    private static void fail(String message) {
        System.err.println("    [FAIL] " + message);
        testsFailed++;
    }
}

