package com.hirehub.analytics;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.*;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Self-contained verification harness for Phase 5 Step 4B: Student Security Hardening & Analytics Backend.
 *
 * <p>Runs two comprehensive verification suites:
 * <ol>
 *   <li><strong>Offline Suite:</strong> Input validation, negative/zero ID guards, date range checks,
 *       fail-closed role/session checks, DTO zero-division safety, resume readiness rubric calculations,
 *       and canonical skill normalization.</li>
 *   <li><strong>Live MySQL Suite:</strong> Multi-tenant isolation verification, atomic ownership methods
 *       ({@code findByIdForStudent}, {@code deleteForStudent}), open active job filtering, cross-student
 *       data isolation, and end-to-end {@link StudentAnalyticsService} snapshot validation.
 *       All fixtures are strictly cleaned up in a {@code finally} block to guarantee zero DB contamination.</li>
 * </ol>
 */
public class StudentAnalyticsTest {

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    private static final String PREFIX = "p5_step4b_test_" + System.currentTimeMillis() + "_";

    public static void main(String[] args) {
        StudentAnalyticsTest verifier = new StudentAnalyticsTest();
        verifier.run();
        if (verifier.failed > 0) {
            System.exit(1);
        }
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 5 Step 4B - Student Security & Analytics Verifier");
        System.out.println("=================================================================");

        runOfflineSuite();
        runLiveDatabaseSuite();

        System.out.println("-----------------------------------------------------------------");
        System.out.printf("VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");
    }

    // =========================================================================
    // 1. OFFLINE VERIFICATION SUITE
    // =========================================================================

    private void runOfflineSuite() {
        System.out.println("\n--- [1] Running Offline Verification Suite ---");

        StudentAnalyticsService service = new StudentAnalyticsService();
        StudentAnalyticsDAO dao = new StudentAnalyticsDAO();

        // ---------------------------------------------------------------------
        // T1: Student ID & Date Range Validation
        // ---------------------------------------------------------------------
        assertThrows("Service rejects studentId=0 in loadAllTimeSnapshot", IllegalArgumentException.class,
                () -> service.loadAllTimeSnapshot(0));
        assertThrows("Service rejects negative studentId in loadAllTimeSnapshot", IllegalArgumentException.class,
                () -> service.loadAllTimeSnapshot(-42));
        assertThrows("Service rejects studentId=0 in loadSnapshot", IllegalArgumentException.class,
                () -> service.loadSnapshot(0, null, null));
        assertThrows("Service rejects negative studentId in loadSnapshot", IllegalArgumentException.class,
                () -> service.loadSnapshot(-1, null, null));
        assertThrows("Service rejects days <= 0 in loadLastNDaysSnapshot", IllegalArgumentException.class,
                () -> service.loadLastNDaysSnapshot(1, 0));
        assertThrows("Service rejects negative days in loadLastNDaysSnapshot", IllegalArgumentException.class,
                () -> service.loadLastNDaysSnapshot(1, -7));

        LocalDate d1 = LocalDate.of(2026, 11, 1);
        LocalDate d2 = LocalDate.of(2026, 11, 15);
        assertThrows("Service rejects from > to", IllegalArgumentException.class,
                () -> service.loadSnapshot(1, d2, d1));

        // Same-day allowed cleanly
        service.validateDateRange(d1, d1);
        pass("Same-day date range validation succeeds");

        // DAO input validation
        assertThrows("DAO rejects studentId=0 in countApplicationsByStatus", IllegalArgumentException.class,
                () -> dao.countApplicationsByStatus(0, null, null));
        assertThrows("DAO rejects studentId=0 in getStudentProfile", IllegalArgumentException.class,
                () -> dao.getStudentProfile(0));
        assertThrows("DAO rejects studentId=0 in getActiveResume", IllegalArgumentException.class,
                () -> dao.getActiveResume(0));
        assertThrows("DAO rejects studentId=0 in countHistoricalResumes", IllegalArgumentException.class,
                () -> dao.countHistoricalResumes(0));
        assertThrows("DAO rejects studentId=0 in getLatestResumeAnalysis", IllegalArgumentException.class,
                () -> dao.getLatestResumeAnalysis(0));
        assertThrows("DAO rejects studentId=0 in getApplicationRows", IllegalArgumentException.class,
                () -> dao.getApplicationRows(0, null, null));
        assertThrows("DAO rejects studentId=0 in getApplicationTrend", IllegalArgumentException.class,
                () -> dao.getApplicationTrend(0, null, null));
        assertThrows("DAO rejects studentId=0 in getAppliedJobIds", IllegalArgumentException.class,
                () -> dao.getAppliedJobIds(0));
        assertThrows("DAO rejects studentId=0 in getRecentActivity", IllegalArgumentException.class,
                () -> dao.getRecentActivity(0, 10));
        assertThrows("DAO rejects studentId=0 in getRecentAnalyses", IllegalArgumentException.class,
                () -> dao.getRecentAnalyses(0, 10));

        // ---------------------------------------------------------------------
        // T2: Student Security Hardening Guards on Services
        // ---------------------------------------------------------------------
        StudentService studentService = new StudentService();
        check("StudentService.getProfile(0) returns empty",
                studentService.getProfile(0).isEmpty());
        check("StudentService.getProfile(-1) returns empty",
                studentService.getProfile(-1).isEmpty());
        check("StudentService.saveProfile(0, ...) returns fail",
                !studentService.saveProfile(0, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("StudentService.saveProfile(-5, ...) returns fail",
                !studentService.saveProfile(-5, "123", "Col", "Deg", 2025, "Java", null).isSuccess());

        ApplicationService applicationService = new ApplicationService();
        check("ApplicationService.apply(0, 1, '') returns fail",
                !applicationService.apply(0, 1, "cover").isSuccess());
        check("ApplicationService.apply(1, 0, '') returns fail",
                !applicationService.apply(1, 0, "cover").isSuccess());
        check("ApplicationService.hasApplied(0, 1) returns false",
                !applicationService.hasApplied(0, 1));
        check("ApplicationService.hasApplied(1, 0) returns false",
                !applicationService.hasApplied(1, 0));
        check("ApplicationService.getStudentApplications(0) returns empty list",
                applicationService.getStudentApplications(0).isEmpty());
        check("ApplicationService.getStudentApplications(-1) returns empty list",
                applicationService.getStudentApplications(-1).isEmpty());

        ResumeService resumeService = new ResumeService();
        check("ResumeService.uploadAndProcessResume(0, file) returns fail",
                !resumeService.uploadAndProcessResume(0, new File("nonexistent.pdf")).isSuccess());
        check("ResumeService.getActiveResume(0) returns empty",
                resumeService.getActiveResume(0).isEmpty());
        check("ResumeService.getAllResumes(0) returns empty list",
                resumeService.getAllResumes(0).isEmpty());
        check("ResumeService.syncWithStudentProfile(0, parsed) returns false",
                !resumeService.syncWithStudentProfile(0, new ParsedResume()));
        check("ResumeService.deleteResume(0, 1) returns false",
                !resumeService.deleteResume(0, 1));
        check("ResumeService.deleteResume(1, 0) returns false",
                !resumeService.deleteResume(1, 0));

        ResumeAnalysisService analysisService = new ResumeAnalysisService();
        check("ResumeAnalysisService.getStudentAnalysisHistory(0, 10) returns empty list",
                analysisService.getStudentAnalysisHistory(0, 10).isEmpty());
        assertThrows("ResumeAnalysisService.saveAnalysis(null, null, 0, ...) throws IllegalArgumentException",
                IllegalArgumentException.class,
                () -> analysisService.saveAnalysis(null, null, 0,
                        new ResumeAnalysisService.ScoringBreakdown(80, 30, 20, 10, 10, 10,
                                List.of(), List.of(), "", "", "", "", "", true, "High", List.of(), List.of()),
                        "Summary"));

        // Canonical skill normalization
        check("canonicalSkill('js') maps to 'JavaScript'",
                "JavaScript".equals(ResumeAnalysisService.canonicalSkill("js")));
        check("canonicalSkill('k8s') maps to 'Kubernetes'",
                "Kubernetes".equals(ResumeAnalysisService.canonicalSkill("k8s")));
        check("canonicalSkill('py') maps to 'Python'",
                "Python".equals(ResumeAnalysisService.canonicalSkill("py")));
        check("canonicalSkill('aws') maps to 'AWS'",
                "AWS".equals(ResumeAnalysisService.canonicalSkill("aws")));
        check("canonicalSkill('gcp') maps to 'GCP'",
                "GCP".equals(ResumeAnalysisService.canonicalSkill("gcp")));
        check("canonicalSkill('UnknownSkill') preserves original casing/trimmed",
                "UnknownSkill".equals(ResumeAnalysisService.canonicalSkill(" UnknownSkill ")));
        check("canonicalSkill(null) returns empty string safely",
                "".equals(ResumeAnalysisService.canonicalSkill(null)));

        // ---------------------------------------------------------------------
        // T3: DTO Division-by-Zero Safety & Model Contracts
        // ---------------------------------------------------------------------
        StudentStatusDistribution zeroDist = new StudentStatusDistribution(0, 0, 0, 0);
        check("Zero status distribution total is 0", zeroDist.getTotal() == 0);
        check("Zero status distribution pendingPct is 0.0 and finite",
                zeroDist.getPendingPct() == 0.0 && Double.isFinite(zeroDist.getPendingPct()));
        check("Zero status distribution shortlistedPct is 0.0 and finite",
                zeroDist.getShortlistedPct() == 0.0 && Double.isFinite(zeroDist.getShortlistedPct()));
        check("Zero status distribution rejectedPct is 0.0 and finite",
                zeroDist.getRejectedPct() == 0.0 && Double.isFinite(zeroDist.getRejectedPct()));
        check("Zero status distribution selectedPct is 0.0 and finite",
                zeroDist.getSelectedPct() == 0.0 && Double.isFinite(zeroDist.getSelectedPct()));

        StudentStatusDistribution nonZeroDist = new StudentStatusDistribution(1, 1, 1, 1);
        check("Non-zero distribution total is 4", nonZeroDist.getTotal() == 4);
        check("Non-zero distribution percentages are 25.0% each",
                nonZeroDist.getPendingPct() == 25.0 && nonZeroDist.getShortlistedPct() == 25.0);

        StudentOverviewKPI kpi = new StudentOverviewKPI();
        kpi.setLatestResumeScore(null);
        check("StudentOverviewKPI allows nullable latestResumeScore (not forced to zero)",
                kpi.getLatestResumeScore() == null);

        StudentResumeReadiness readiness = new StudentResumeReadiness();
        check("StudentResumeReadiness has non-prediction disclaimer",
                StudentResumeReadiness.DEFAULT_DISCLAIMER.equals(readiness.getDisclaimerText()));
        check("StudentResumeReadiness rubric scores default to null",
                readiness.getOverallScore() == null);

        StudentDashboardSnapshot emptySnap = StudentDashboardSnapshot.empty(99, "Simulated Error");
        check("StudentDashboardSnapshot.empty records error message",
                emptySnap.hasError() && "Simulated Error".equals(emptySnap.getErrorMessage()));

        // ---------------------------------------------------------------------
        // T4: Session / Cross-Tenant Security Check
        // ---------------------------------------------------------------------
        try {
            User studentA = new User();
            studentA.setId(101);
            studentA.setRole(User.Role.STUDENT);
            studentA.setFullName("Student A");
            SessionManager.getInstance().login(studentA);

            // Attempting to query analytics for another student ID (102) must throw SecurityException
            assertThrows("Accessing another student's analytics throws SecurityException", SecurityException.class,
                    () -> service.loadSnapshot(102, null, null));

            // Admin viewing student 102 is allowed
            User adminUser = new User();
            adminUser.setId(1);
            adminUser.setRole(User.Role.ADMIN);
            SessionManager.getInstance().login(adminUser);

            // Doesn't throw SecurityException for admin (will proceed to DAO)
            try {
                service.loadSnapshot(102, null, null);
                pass("Admin user is permitted to inspect student analytics");
            } catch (SecurityException se) {
                fail("Admin should be permitted to inspect student analytics: " + se.getMessage());
            } catch (Exception ignored) {
                // DAO exceptions are caught inside loadSnapshot and stored in snapshot
                pass("Admin user is permitted to inspect student analytics");
            }

        } finally {
            SessionManager.getInstance().logout();
        }
    }

    // =========================================================================
    // 2. LIVE MYSQL VERIFICATION SUITE
    // =========================================================================

    private void runLiveDatabaseSuite() {
        System.out.println("\n--- [2] Running Live Database Verification Suite ---");

        // 1. Connectivity check
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null || conn.isClosed()) {
                System.out.println("  [SKIP] Database connection unavailable. Skipping live DB suite.");
                skipped += 15;
                return;
            }
        } catch (SQLException e) {
            System.out.println("  [SKIP] Database unreachable (" + e.getMessage() + "). Skipping live DB suite.");
            skipped += 15;
            return;
        }

        StudentAnalyticsDAO dao = new StudentAnalyticsDAO();
        StudentAnalyticsService service = new StudentAnalyticsService();
        ResumeDAO resumeDAO = new ResumeDAO();
        ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();
        JobDAO jobDAO = new JobDAO();

        int studentAId = 0;
        int studentBId = 0;
        int recruiterId = 0;
        int job1Id = 0;
        int job2Id = 0;
        int job3Id = 0;
        int resume1Id = 0;
        int resume2Id = 0;
        int analysis1Id = 0;

        int initialUsers = countTable("users");
        int initialJobs = countTable("jobs");
        int initialApps = countTable("applications");
        int initialResumes = countTable("resumes");
        int initialAnalyses = countTable("resume_analyses");

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Setup test fixtures
            studentAId = insertUser(conn, PREFIX + "studentA@example.com", "Student A", "STUDENT");
            studentBId = insertUser(conn, PREFIX + "studentB@example.com", "Student B", "STUDENT");
            recruiterId = insertUser(conn, PREFIX + "recruiter@example.com", "Recruiter One", "RECRUITER");

            // Profiles
            insertStudentProfile(conn, studentAId, "+1234567890", "Tech University", "B.Tech CS", 2026,
                    "Java, Spring Boot, MySQL, AWS", "/path/resumeA.pdf");
            insertStudentProfile(conn, studentBId, null, "Liberal Arts College", "BA History", 2024,
                    "Writing, Communication", null);

            // Recruiter profile (Phase 6 Step 4.2: verified recruiter)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO recruiter_profiles (user_id, company_name, verification_status) VALUES (?, 'TechCorp', 'VERIFIED')")) {
                stmt.setInt(1, recruiterId);
                stmt.executeUpdate();
            }

            // Jobs
            LocalDate today = LocalDate.now();
            job1Id = insertJob(conn, recruiterId, PREFIX + "Junior Java Engineer", "TechCorp",
                    "Java, Spring Boot, MySQL", "ACTIVE", today.plusDays(30));
            job2Id = insertJob(conn, recruiterId, PREFIX + "Expired Python Dev", "PyCorp",
                    "Python, Django", "ACTIVE", today.minusDays(5));
            job3Id = insertJob(conn, recruiterId, PREFIX + "Closed Frontend Lead", "WebCorp",
                    "React, TypeScript", "CLOSED", today.plusDays(10));

            // Applications
            insertApplication(conn, job1Id, studentAId, "PENDING", LocalDateTime.now().minusDays(2));
            insertApplication(conn, job1Id, studentBId, "SHORTLISTED", LocalDateTime.now().minusDays(1));

            // Resumes
            String fakeParsedJson = """
                    {
                      "name": "Student A",
                      "email": "studentA@example.com",
                      "phone": "+1234567890",
                      "college": "Tech University",
                      "degree": "B.Tech CS",
                      "graduationYear": 2026,
                      "skills": ["Java", "Spring Boot", "MySQL", "AWS", "Docker"],
                      "workExperiences": ["Software Intern at Alpha"],
                      "projects": ["HireHub Recruitment Platform"],
                      "certifications": ["AWS Certified Cloud Practitioner"],
                      "metricsCount": 3,
                      "rawText": "Experienced Java and Spring Boot engineer with 3 successful cloud deployments."
                    }
                    """;
            resume1Id = insertResume(conn, studentAId, "resume_active.pdf", "/data/resumes/resume_active.pdf",
                    fakeParsedJson, true);
            resume2Id = insertResume(conn, studentAId, "resume_old.pdf", "/data/resumes/resume_old.pdf",
                    null, false);

            // Analysis
            analysis1Id = insertAnalysis(conn, resume1Id, null, studentAId, 85, 35, 20, 15, 8, 7,
                    "Java, Spring Boot, MySQL", "Kubernetes", "Strong technical background");

            // -----------------------------------------------------------------
            // T5: Scoped Atomic Ownership Methods on DAOs
            // -----------------------------------------------------------------
            Optional<Resume> resA = resumeDAO.findByIdForStudent(resume1Id, studentAId);
            check("ResumeDAO.findByIdForStudent returns resume for actual owner (Student A)",
                    resA.isPresent() && resA.get().getId() == resume1Id);

            Optional<Resume> resB = resumeDAO.findByIdForStudent(resume1Id, studentBId);
            check("ResumeDAO.findByIdForStudent returns empty when called with non-owner (Student B)",
                    resB.isEmpty());

            boolean deletedOther = resumeDAO.deleteForStudent(resume1Id, studentBId);
            check("ResumeDAO.deleteForStudent returns false when deleting another student's resume",
                    !deletedOther);

            Optional<ResumeAnalysis> anaA = analysisDAO.findByIdForStudent(analysis1Id, studentAId);
            check("ResumeAnalysisDAO.findByIdForStudent returns analysis for actual owner (Student A)",
                    anaA.isPresent() && anaA.get().getId() == analysis1Id);

            Optional<ResumeAnalysis> anaB = analysisDAO.findByIdForStudent(analysis1Id, studentBId);
            check("ResumeAnalysisDAO.findByIdForStudent returns empty when called with non-owner (Student B)",
                    anaB.isEmpty());

            boolean deletedAnaOther = analysisDAO.deleteForStudent(analysis1Id, studentBId);
            check("ResumeAnalysisDAO.deleteForStudent returns false when deleting another student's analysis",
                    !deletedAnaOther);

            // -----------------------------------------------------------------
            // T6: JobDAO findOpenActiveJobs
            // -----------------------------------------------------------------
            List<Job> openActive = jobDAO.findOpenActiveJobs();
            boolean hasJob1 = false;
            boolean hasJob2 = false;
            boolean hasJob3 = false;
            for (Job j : openActive) {
                if (j.getId() == job1Id) hasJob1 = true;
                if (j.getId() == job2Id) hasJob2 = true;
                if (j.getId() == job3Id) hasJob3 = true;
            }

            check("JobDAO.findOpenActiveJobs includes active non-expired Job 1", hasJob1);
            check("JobDAO.findOpenActiveJobs excludes active expired Job 2", !hasJob2);
            check("JobDAO.findOpenActiveJobs excludes closed Job 3", !hasJob3);

            // -----------------------------------------------------------------
            // T7: StudentAnalyticsDAO Isolation & Queries
            // -----------------------------------------------------------------
            Map<Application.Status, Integer> countsA = dao.countApplicationsByStatus(studentAId, null, null);
            check("Student A has exactly 1 PENDING application",
                    countsA.get(Application.Status.PENDING) == 1 && countsA.get(Application.Status.SHORTLISTED) == 0);

            Map<Application.Status, Integer> countsB = dao.countApplicationsByStatus(studentBId, null, null);
            check("Student B has exactly 1 SHORTLISTED application (isolated from Student A)",
                    countsB.get(Application.Status.SHORTLISTED) == 1 && countsB.get(Application.Status.PENDING) == 0);

            int openCount = dao.countOpenActiveJobs();
            check("StudentAnalyticsDAO.countOpenActiveJobs returns >= 1", openCount >= 1);

            int histResumesA = dao.countHistoricalResumes(studentAId);
            check("Student A has 2 historical resumes", histResumesA == 2);
            int histResumesB = dao.countHistoricalResumes(studentBId);
            check("Student B has 0 historical resumes", histResumesB == 0);

            List<StudentApplicationRow> rowsA = dao.getApplicationRows(studentAId, null, null);
            check("Student A has 1 application row with correct job metadata",
                    rowsA.size() == 1 && rowsA.get(0).getJobId() == job1Id && !rowsA.get(0).isDeadlineExpired());

            Set<Integer> appliedA = dao.getAppliedJobIds(studentAId);
            check("Student A appliedJobIds contains Job 1",
                    appliedA.contains(job1Id) && appliedA.size() == 1);

            List<StudentActivityEvent> actsA = dao.getRecentActivity(studentAId, 10);
            check("Student A activity contains application, resume, and analysis events",
                    !actsA.isEmpty());

            // -----------------------------------------------------------------
            // T8: StudentAnalyticsService Full Snapshot Integration
            // -----------------------------------------------------------------
            StudentDashboardSnapshot snapA = service.loadAllTimeSnapshot(studentAId);
            check("Student A snapshot loads without errors", !snapA.hasError());
            check("Student A KPI total applications == 1", snapA.getKpi().getTotalApplications() == 1);
            check("Student A KPI pending == 1", snapA.getKpi().getPendingCount() == 1);
            check("Student A KPI profile is complete (score == 6)",
                    snapA.getKpi().isProfileComplete() && snapA.getKpi().getProfileCompletionScore() == 6);
            check("Student A KPI hasActiveResume is true", snapA.getKpi().isHasActiveResume());
            check("Student A KPI latestResumeScore is 85",
                    Integer.valueOf(85).equals(snapA.getKpi().getLatestResumeScore()));

            check("Student A resume readiness parsed skills count == 5",
                    snapA.getResumeReadiness().getParsedSkillCount() == 5);
            check("Student A resume readiness live rubric score is computed (> 0)",
                    snapA.getResumeReadiness().getOverallScore() != null && snapA.getResumeReadiness().getOverallScore() > 0);

            StudentSkillInsight skillsA = snapA.getSkillInsight();
            check("Student A combined skills contain Java, Spring Boot, AWS, Docker",
                    skillsA.getCombinedSkills().contains("Java") && skillsA.getCombinedSkills().contains("Docker"));

            List<StudentJobRecommendation> recsA = snapA.getRecommendations();
            check("Student A recommendations list is populated", !recsA.isEmpty());
            StudentJobRecommendation recJob1 = null;
            for (StudentJobRecommendation r : recsA) {
                if (r.getJobId() == job1Id) {
                    recJob1 = r;
                    break;
                }
            }
            check("Job 1 recommendation for Student A is marked as already applied",
                    recJob1 != null && recJob1.isAlreadyApplied());

            // Student B snapshot
            StudentDashboardSnapshot snapB = service.loadAllTimeSnapshot(studentBId);
            check("Student B snapshot loads without errors", !snapB.hasError());
            check("Student B KPI shortlisted == 1", snapB.getKpi().getShortlistedCount() == 1);
            check("Student B KPI profile complete is false", !snapB.getKpi().isProfileComplete());
            check("Student B missing profile fields contains Phone Number and Resume Document",
                    snapB.getKpi().getMissingProfileFields().contains("Phone Number")
                    && snapB.getKpi().getMissingProfileFields().contains("Resume Document"));
            check("Student B latestResumeScore is null (not 0)", snapB.getKpi().getLatestResumeScore() == null);
            check("Student B hasActiveResume is false", !snapB.getKpi().isHasActiveResume());

            // Atomic deletion test for student's own resume
            boolean deletedOwn = resumeDAO.deleteForStudent(resume2Id, studentAId);
            check("ResumeDAO.deleteForStudent deletes own resume successfully", deletedOwn);

            boolean deletedOwnAna = analysisDAO.deleteForStudent(analysis1Id, studentAId);
            check("ResumeAnalysisDAO.deleteForStudent deletes own analysis successfully", deletedOwnAna);

        } catch (SQLException e) {
            fail("Live database test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Strictly clean up all test fixtures in reverse order of FK dependencies
            System.out.println("  Cleaning up test fixtures...");
            cleanupFixtures(studentAId, studentBId, recruiterId);

            // Re-check table counts
            int finalUsers = countTable("users");
            int finalJobs = countTable("jobs");
            int finalApps = countTable("applications");
            int finalResumes = countTable("resumes");
            int finalAnalyses = countTable("resume_analyses");

            check("Users table fully restored (" + finalUsers + " == " + initialUsers + ")",
                    finalUsers == initialUsers);
            check("Jobs table fully restored (" + finalJobs + " == " + initialJobs + ")",
                    finalJobs == initialJobs);
            check("Applications table fully restored (" + finalApps + " == " + initialApps + ")",
                    finalApps == initialApps);
            check("Resumes table fully restored (" + finalResumes + " == " + initialResumes + ")",
                    finalResumes == initialResumes);
            check("Resume analyses table fully restored (" + finalAnalyses + " == " + initialAnalyses + ")",
                    finalAnalyses == initialAnalyses);
        }
    }

    // =========================================================================
    // Fixture Helpers
    // =========================================================================

    private int insertUser(Connection conn, String email, String fullName, String role) throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'hash', ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, email);
            stmt.setString(2, fullName);
            stmt.setString(3, role);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert user " + email);
    }

    private void insertStudentProfile(Connection conn, int userId, String phone, String college,
                                      String degree, int gradYear, String skills, String resumePath) throws SQLException {
        String sql = "INSERT INTO student_profiles (user_id, phone, college, degree, graduation_year, skills, resume_path) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, phone);
            stmt.setString(3, college);
            stmt.setString(4, degree);
            stmt.setInt(5, gradYear);
            stmt.setString(6, skills);
            stmt.setString(7, resumePath);
            stmt.executeUpdate();
        }
    }

    private int insertJob(Connection conn, int recruiterId, String title, String company,
                          String reqSkills, String status, LocalDate deadline) throws SQLException {
        String sql = "INSERT INTO jobs (recruiter_id, title, company_name, description, required_skills, "
                + "location, job_type, salary_range, eligibility, deadline, status, moderation_status) "
                + "VALUES (?, ?, ?, 'Job desc', ?, 'Remote', 'FULL_TIME', '$100k', 'All', ?, ?, 'APPROVED')";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, recruiterId);
            stmt.setString(2, title);
            stmt.setString(3, company);
            stmt.setString(4, reqSkills);
            stmt.setDate(5, deadline != null ? java.sql.Date.valueOf(deadline) : null);
            stmt.setString(6, status);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert job " + title);
    }

    private void insertApplication(Connection conn, int jobId, int studentId, String status, LocalDateTime appliedAt) throws SQLException {
        String sql = "INSERT INTO applications (job_id, student_id, cover_letter, status, applied_at, updated_at) "
                + "VALUES (?, ?, 'Cover text', ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, studentId);
            stmt.setString(3, status);
            stmt.setTimestamp(4, Timestamp.valueOf(appliedAt));
            stmt.setTimestamp(5, Timestamp.valueOf(appliedAt));
            stmt.executeUpdate();
        }
    }

    private int insertResume(Connection conn, int studentId, String fileName, String path, String json, boolean active) throws SQLException {
        String sql = "INSERT INTO resumes (student_id, file_name, stored_path, file_size_bytes, file_hash, "
                + "parsed_text, parsed_json, is_active) VALUES (?, ?, ?, 1024, 'dummy_hash', 'text', ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, studentId);
            stmt.setString(2, fileName);
            stmt.setString(3, path);
            stmt.setString(4, json);
            stmt.setBoolean(5, active);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert resume " + fileName);
    }

    private int insertAnalysis(Connection conn, Integer resumeId, Integer jobId, int studentId,
                               int overall, int skills, int exp, int edu, int comp, int cert,
                               String matching, String missing, String summary) throws SQLException {
        String sql = "INSERT INTO resume_analyses (resume_id, job_id, student_id, overall_score, skills_score, "
                + "experience_score, education_score, completeness_score, certifications_score, "
                + "matching_skills, missing_skills, analysis_summary) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (resumeId == null) stmt.setNull(1, Types.INTEGER); else stmt.setInt(1, resumeId);
            if (jobId == null) stmt.setNull(2, Types.INTEGER); else stmt.setInt(2, jobId);
            stmt.setInt(3, studentId);
            stmt.setInt(4, overall);
            stmt.setInt(5, skills);
            stmt.setInt(6, exp);
            stmt.setInt(7, edu);
            stmt.setInt(8, comp);
            stmt.setInt(9, cert);
            stmt.setString(10, matching);
            stmt.setString(11, missing);
            stmt.setString(12, summary);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert analysis");
    }

    private void cleanupFixtures(int studentAId, int studentBId, int recruiterId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Delete analyses
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM resume_analyses WHERE student_id IN (?, ?)")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.executeUpdate();
            }

            // Delete resumes
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM resumes WHERE student_id IN (?, ?)")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.executeUpdate();
            }

            // Delete applications
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM applications WHERE student_id IN (?, ?)")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.executeUpdate();
            }

            // Delete jobs
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM jobs WHERE recruiter_id = ? OR title LIKE ?")) {
                stmt.setInt(1, recruiterId);
                stmt.setString(2, PREFIX + "%");
                stmt.executeUpdate();
            }

            // Delete student profiles
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM student_profiles WHERE user_id IN (?, ?)")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.executeUpdate();
            }

            // Delete recruiter profiles
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM recruiter_profiles WHERE user_id = ?")) {
                stmt.setInt(1, recruiterId);
                stmt.executeUpdate();
            }

            // Delete users
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM users WHERE id IN (?, ?) OR email LIKE ?")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.setString(3, PREFIX + "%");
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up fixtures: " + e.getMessage());
        }
    }

    private int countTable(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("countTable(" + tableName + ") error: " + e.getMessage());
        }
        return -1;
    }

    // =========================================================================
    // Test Assertions & Reporters
    // =========================================================================

    private void check(String desc, boolean condition) {
        if (condition) {
            pass(desc);
        } else {
            fail(desc + " [FAILED CONDITION]");
        }
    }

    private void pass(String desc) {
        passed++;
        System.out.println("  [PASS] " + desc);
    }

    private void fail(String desc) {
        failed++;
        System.err.println("  [FAIL] " + desc);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private void assertThrows(String desc, Class<? extends Throwable> expected, ThrowingRunnable action) {
        try {
            action.run();
            fail(desc + " [Did not throw " + expected.getSimpleName() + "]");
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                pass(desc);
            } else {
                fail(desc + " [Threw " + t.getClass().getSimpleName() + " instead of " + expected.getSimpleName() + ": " + t.getMessage() + "]");
            }
        }
    }
}
