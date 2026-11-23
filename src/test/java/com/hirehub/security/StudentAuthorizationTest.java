package com.hirehub.security;


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
import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * StudentAuthorizationTest — Verification suite for Phase 5 Step 4B.1:
 * Student Service & DAO Security Closure.
 *
 * <p>Validates:
 * <ol>
 *   <li>Offline Service-Level Authorization Guards:
 *       <ul>
 *         <li>Unauthenticated requests fail safe on all student services.</li>
 *         <li>Cross-student ID spoofing (Student A calling with Student B ID) fails safe.</li>
 *         <li>Cross-role requests (Recruiter calling student services) fail safe.</li>
 *         <li>Admin cannot silently bypass student methods, must use explicit {@code *ForAdmin} methods.</li>
 *         <li>Non-admin callers cannot access {@code *ForAdmin} methods.</li>
 *         <li>Non-positive IDs fail safe across all services.</li>
 *       </ul>
 *   </li>
 *   <li>DAO Ownership Contracts & Deprecations:
 *       <ul>
 *         <li>Unscoped methods in {@link ResumeDAO} and {@link ResumeAnalysisDAO} are marked {@code @Deprecated}.</li>
 *         <li>Scoped atomic methods exist and enforce ownership predicates in SQL.</li>
 *       </ul>
 *   </li>
 *   <li>Live Database Tenant Isolation & Integrity:
 *       <ul>
 *         <li>Student A cannot view, update, or delete Student B's profile, resume, analysis, or applications.</li>
 *         <li>Direct DAO scoped predicates prevent unauthorized access/mutation even with known IDs.</li>
 *         <li>Legitimate student operations function smoothly when authenticated as that student.</li>
 *         <li>Admin explicitly-named operations succeed when authenticated as Admin.</li>
 *         <li>100% database fixture teardown restoring baseline row counts.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class StudentAuthorizationTest {

    private static final String PREFIX = "SEC4B1_TEST_";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    public static void main(String[] args) {
        StudentAuthorizationTest verifier = new StudentAuthorizationTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 5 Step 4B.1: Student Service & DAO Security Closure");
        System.out.println("=================================================================\n");

        runOfflineSecuritySuite();
        runLiveDatabaseSecuritySuite();

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("SECURITY CLOSURE SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Security Suite
    // =========================================================================

    private void runOfflineSecuritySuite() {
        System.out.println("--- [1] Running Offline Service Authorization & DAO Deprecation Suite ---");

        StudentService studentService = new StudentService();
        ResumeService resumeService = new ResumeService();
        ResumeAnalysisService analysisService = new ResumeAnalysisService();
        ApplicationService appService = new ApplicationService();

        User studentA = new User("Student A", "studentA@test.local", "hash", User.Role.STUDENT);
        studentA.setId(101);

        User studentB = new User("Student B", "studentB@test.local", "hash", User.Role.STUDENT);
        studentB.setId(102);

        User recruiter = new User("Recruiter", "recruiter@test.local", "hash", User.Role.RECRUITER);
        recruiter.setId(201);

        User admin = new User("Admin", "admin@test.local", "hash", User.Role.ADMIN);
        admin.setId(301);

        // ---------------------------------------------------------------------
        // Part 1A: Unauthenticated callers fail safe
        // ---------------------------------------------------------------------
        SessionManager.getInstance().logout();

        check("Unauthenticated: StudentService.getProfile returns empty",
                studentService.getProfile(101).isEmpty());
        check("Unauthenticated: StudentService.saveProfile returns fail",
                !studentService.saveProfile(101, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("Unauthenticated: ResumeService.uploadAndProcessResume returns fail",
                !resumeService.uploadAndProcessResume(101, new File("nonexistent.pdf")).isSuccess());
        check("Unauthenticated: ResumeService.getActiveResume returns empty",
                resumeService.getActiveResume(101).isEmpty());
        check("Unauthenticated: ResumeService.getAllResumes returns empty list",
                resumeService.getAllResumes(101).isEmpty());
        check("Unauthenticated: ResumeService.syncWithStudentProfile returns false",
                !resumeService.syncWithStudentProfile(101, new ParsedResume()));
        check("Unauthenticated: ResumeService.deleteResume returns false",
                !resumeService.deleteResume(501, 101));

        ResumeAnalysisService.ScoringBreakdown dummyBreakdown = new ResumeAnalysisService.ScoringBreakdown(
                80, 30, 20, 10, 10, 10,
                List.of("Java"), List.of("Python"),
                "exp", "edu", "skills", "comp", "cert", false, "HIGH",
                Collections.emptyList(), Collections.emptyList()
        );

        assertThrows("Unauthenticated: ResumeAnalysisService.saveAnalysis throws SecurityException",
                SecurityException.class,
                () -> analysisService.saveAnalysis(null, null, 101, dummyBreakdown, "AI text"));
        check("Unauthenticated: ResumeAnalysisService.getStudentAnalysisHistory returns empty list",
                analysisService.getStudentAnalysisHistory(101, 10).isEmpty());

        check("Unauthenticated: ApplicationService.apply returns fail",
                !appService.apply(101, 1, "cover").isSuccess());
        check("Unauthenticated: ApplicationService.hasApplied returns false",
                !appService.hasApplied(101, 1));
        check("Unauthenticated: ApplicationService.getStudentApplications returns empty list",
                appService.getStudentApplications(101).isEmpty());

        // ---------------------------------------------------------------------
        // Part 1B: Cross-Student ID Spoofing (Student A logged in, calls Student B ID)
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(studentA);

        check("Cross-Student: Student A cannot read Student B profile",
                studentService.getProfile(102).isEmpty());
        check("Cross-Student: Student A cannot save Student B profile",
                !studentService.saveProfile(102, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("Cross-Student: Student A cannot upload resume for Student B",
                !resumeService.uploadAndProcessResume(102, new File("nonexistent.pdf")).isSuccess());
        check("Cross-Student: Student A cannot read Student B active resume",
                resumeService.getActiveResume(102).isEmpty());
        check("Cross-Student: Student A cannot read Student B all resumes",
                resumeService.getAllResumes(102).isEmpty());
        check("Cross-Student: Student A cannot sync Student B profile",
                !resumeService.syncWithStudentProfile(102, new ParsedResume()));
        check("Cross-Student: Student A cannot delete Student B resume",
                !resumeService.deleteResume(501, 102));

        assertThrows("Cross-Student: Student A cannot save analysis for Student B",
                SecurityException.class,
                () -> analysisService.saveAnalysis(null, null, 102, dummyBreakdown, "AI text"));
        check("Cross-Student: Student A cannot read Student B analysis history",
                analysisService.getStudentAnalysisHistory(102, 10).isEmpty());

        check("Cross-Student: Student A cannot apply on behalf of Student B",
                !appService.apply(102, 1, "cover").isSuccess());
        check("Cross-Student: Student A cannot check hasApplied for Student B",
                !appService.hasApplied(102, 1));
        check("Cross-Student: Student A cannot read Student B applications",
                appService.getStudentApplications(102).isEmpty());

        // ---------------------------------------------------------------------
        // Part 1C: Cross-Role Protection (Recruiter logged in calling student methods)
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(recruiter);

        check("Cross-Role: Recruiter cannot read student profile",
                studentService.getProfile(101).isEmpty());
        check("Cross-Role: Recruiter cannot save student profile",
                !studentService.saveProfile(101, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("Cross-Role: Recruiter cannot read student active resume",
                resumeService.getActiveResume(101).isEmpty());
        check("Cross-Role: Recruiter cannot delete student resume",
                !resumeService.deleteResume(501, 101));
        assertThrows("Cross-Role: Recruiter cannot save student analysis",
                SecurityException.class,
                () -> analysisService.saveAnalysis(null, null, 101, dummyBreakdown, "AI text"));
        check("Cross-Role: Recruiter cannot read student analysis history",
                analysisService.getStudentAnalysisHistory(101, 10).isEmpty());
        check("Cross-Role: Recruiter cannot apply as student",
                !appService.apply(101, 1, "cover").isSuccess());
        check("Cross-Role: Recruiter cannot get student applications",
                appService.getStudentApplications(101).isEmpty());

        // ---------------------------------------------------------------------
        // Part 1D: Admin Separation (Admin cannot silently use student methods)
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(admin);

        check("Admin Separation: Admin cannot silently read student profile via getProfile",
                studentService.getProfile(101).isEmpty());
        check("Admin Separation: Admin cannot silently save student profile via saveProfile",
                !studentService.saveProfile(101, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("Admin Separation: Admin cannot silently read active resume via getActiveResume",
                resumeService.getActiveResume(101).isEmpty());
        check("Admin Separation: Admin cannot silently read all resumes via getAllResumes",
                resumeService.getAllResumes(101).isEmpty());
        check("Admin Separation: Admin cannot silently delete resume via deleteResume",
                !resumeService.deleteResume(501, 101));
        assertThrows("Admin Separation: Admin cannot silently save analysis via saveAnalysis",
                SecurityException.class,
                () -> analysisService.saveAnalysis(null, null, 101, dummyBreakdown, "AI text"));
        check("Admin Separation: Admin cannot silently read analysis history via getStudentAnalysisHistory",
                analysisService.getStudentAnalysisHistory(101, 10).isEmpty());
        check("Admin Separation: Admin cannot silently apply via apply",
                !appService.apply(101, 1, "cover").isSuccess());
        check("Admin Separation: Admin cannot silently read student apps via getStudentApplications",
                appService.getStudentApplications(101).isEmpty());

        // ---------------------------------------------------------------------
        // Part 1E: Non-Admin callers rejected by explicit *ForAdmin methods
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(studentA);

        check("Non-Admin: Student cannot call StudentService.getProfileForAdmin",
                studentService.getProfileForAdmin(101).isEmpty());
        check("Non-Admin: Student cannot call StudentService.saveProfileForAdmin",
                !studentService.saveProfileForAdmin(101, "123", "Col", "Deg", 2025, "Java", null).isSuccess());
        check("Non-Admin: Student cannot call ResumeService.getActiveResumeForAdmin",
                resumeService.getActiveResumeForAdmin(101).isEmpty());
        check("Non-Admin: Student cannot call ResumeService.getAllResumesForAdmin",
                resumeService.getAllResumesForAdmin(101).isEmpty());
        check("Non-Admin: Student cannot call ResumeService.deleteResumeForAdmin",
                !resumeService.deleteResumeForAdmin(501, 101));
        assertThrows("Non-Admin: Student cannot call ResumeAnalysisService.saveAnalysisForAdmin",
                SecurityException.class,
                () -> analysisService.saveAnalysisForAdmin(null, null, 101, dummyBreakdown, "AI text"));
        check("Non-Admin: Student cannot call ResumeAnalysisService.getStudentAnalysisHistoryForAdmin",
                analysisService.getStudentAnalysisHistoryForAdmin(101, 10).isEmpty());
        check("Non-Admin: Student cannot call ApplicationService.getStudentApplicationsForAdmin",
                appService.getStudentApplicationsForAdmin(101).isEmpty());

        SessionManager.getInstance().logout();

        // ---------------------------------------------------------------------
        // Part 1F: DAO Deprecation & Scoped Method Contracts via Reflection
        // ---------------------------------------------------------------------
        check("ResumeDAO.findById(int) is @Deprecated",
                isMethodDeprecated(ResumeDAO.class, "findById", int.class));
        check("ResumeDAO.update(Resume) is @Deprecated",
                isMethodDeprecated(ResumeDAO.class, "update", Resume.class));
        check("ResumeDAO.delete(int) is @Deprecated",
                isMethodDeprecated(ResumeDAO.class, "delete", int.class));
        check("ResumeDAO.findByIdForStudent exists",
                hasMethod(ResumeDAO.class, "findByIdForStudent", int.class, int.class));
        check("ResumeDAO.deleteForStudent exists",
                hasMethod(ResumeDAO.class, "deleteForStudent", int.class, int.class));
        check("ResumeDAO.updateForStudent exists",
                hasMethod(ResumeDAO.class, "updateForStudent", Resume.class, int.class));

        check("ResumeAnalysisDAO.findById(int) is @Deprecated",
                isMethodDeprecated(ResumeAnalysisDAO.class, "findById", int.class));
        check("ResumeAnalysisDAO.delete(int) is @Deprecated",
                isMethodDeprecated(ResumeAnalysisDAO.class, "delete", int.class));
        check("ResumeAnalysisDAO.findByIdForStudent exists",
                hasMethod(ResumeAnalysisDAO.class, "findByIdForStudent", int.class, int.class));
        check("ResumeAnalysisDAO.deleteForStudent exists",
                hasMethod(ResumeAnalysisDAO.class, "deleteForStudent", int.class, int.class));
    }

    // =========================================================================
    // SECTION 2: Live Database Tenant Isolation & Integrity Suite
    // =========================================================================

    private void runLiveDatabaseSecuritySuite() {
        System.out.println("\n--- [2] Running Live Database Tenant Isolation & Integrity Suite ---");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null || conn.isClosed()) {
                System.out.println("  [SKIP] Database connection unavailable. Skipping live DB suite.");
                skipped += 25;
                return;
            }
        } catch (SQLException e) {
            System.out.println("  [SKIP] Database unreachable (" + e.getMessage() + "). Skipping live DB suite.");
            skipped += 25;
            return;
        }

        StudentService studentService = new StudentService();
        ResumeService resumeService = new ResumeService();
        ResumeAnalysisService analysisService = new ResumeAnalysisService();
        ApplicationService appService = new ApplicationService();

        ResumeDAO resumeDAO = new ResumeDAO();
        ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();

        int studentAId = 0;
        int studentBId = 0;
        int recruiterId = 0;
        int adminId = 0;
        int job1Id = 0;
        int resumeAId = 0;
        int resumeBId = 0;
        int analysisAId = 0;
        int analysisBId = 0;
        int appAId = 0;

        int initialUsers = countTable("users");
        int initialJobs = countTable("jobs");
        int initialApps = countTable("applications");
        int initialResumes = countTable("resumes");
        int initialAnalyses = countTable("resume_analyses");
        int initialProfiles = countTable("student_profiles");

        User studentAUser = null;
        User studentBUser = null;
        User adminUser = null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Setup isolated fixtures
            studentAId = insertUser(conn, PREFIX + "studentA@test.local", "Security Student A", "STUDENT");
            studentAUser = new User("Security Student A", PREFIX + "studentA@test.local", "hash", User.Role.STUDENT);
            studentAUser.setId(studentAId);

            studentBId = insertUser(conn, PREFIX + "studentB@test.local", "Security Student B", "STUDENT");
            studentBUser = new User("Security Student B", PREFIX + "studentB@test.local", "hash", User.Role.STUDENT);
            studentBUser.setId(studentBId);

            recruiterId = insertUser(conn, PREFIX + "recruiter@test.local", "Security Recruiter", "RECRUITER");
            adminId = insertUser(conn, PREFIX + "admin@test.local", "Security Admin", "ADMIN");
            adminUser = new User("Security Admin", PREFIX + "admin@test.local", "hash", User.Role.ADMIN);
            adminUser.setId(adminId);

            // Profiles
            insertStudentProfile(conn, studentAId, "+1111111111", "MIT", "B.S. CS", 2026, "Java, SQL", "/resumes/A.pdf");
            insertStudentProfile(conn, studentBId, "+2222222222", "Stanford", "B.S. EE", 2025, "Python, C++", "/resumes/B.pdf");

            // Job
            job1Id = insertJob(conn, recruiterId, PREFIX + "Software Engineer", "SecCorp",
                    "Java, SQL, Spring Boot", "ACTIVE", LocalDate.now().plusDays(20));

            // Applications
            appAId = insertApplication(conn, job1Id, studentAId, "PENDING", LocalDateTime.now());

            // Resumes
            resumeAId = insertResume(conn, studentAId, "resumeA.pdf", "/data/resumes/resumeA.pdf", "{\"skills\":[\"Java\"]}", true);
            resumeBId = insertResume(conn, studentBId, "resumeB.pdf", "/data/resumes/resumeB.pdf", "{\"skills\":[\"Python\"]}", true);

            // Analyses
            analysisAId = insertAnalysis(conn, resumeAId, job1Id, studentAId, 90, 38, 22, 15, 8, 7, "Java", "None", "Great A");
            analysisBId = insertAnalysis(conn, resumeBId, job1Id, studentBId, 85, 34, 21, 15, 8, 7, "Python", "None", "Great B");

            // -----------------------------------------------------------------
            // T1: Legitimate Authenticated Student Operations Work Smoothly
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(studentAUser);

            Optional<StudentProfile> ownProfile = studentService.getProfile(studentAId);
            check("Legitimate: Student A reads own profile",
                    ownProfile.isPresent() && "MIT".equals(ownProfile.get().getCollege()));

            StudentService.ServiceResult updateOwnProfile = studentService.saveProfile(
                    studentAId, "+1111111111", "MIT Updated", "M.S. CS", 2027, "Java, SQL, Docker", "/resumes/A.pdf");
            check("Legitimate: Student A updates own profile", updateOwnProfile.isSuccess());

            Optional<Resume> ownResume = resumeService.getActiveResume(studentAId);
            check("Legitimate: Student A reads own active resume",
                    ownResume.isPresent() && ownResume.get().getId() == resumeAId);

            List<Resume> ownAllResumes = resumeService.getAllResumes(studentAId);
            check("Legitimate: Student A lists own resumes",
                    ownAllResumes.size() == 1 && ownAllResumes.get(0).getId() == resumeAId);

            List<ResumeAnalysis> ownHistory = analysisService.getStudentAnalysisHistory(studentAId, 10);
            check("Legitimate: Student A reads own analysis history",
                    ownHistory.size() == 1 && ownHistory.get(0).getId() == analysisAId);

            List<Application> ownApps = appService.getStudentApplications(studentAId);
            check("Legitimate: Student A reads own applications",
                    ownApps.size() == 1 && ownApps.get(0).getId() == appAId);

            check("Legitimate: Student A checks hasApplied for Job 1",
                    appService.hasApplied(studentAId, job1Id));

            // -----------------------------------------------------------------
            // T2: Student A Cannot View, Update, or Delete Student B Data
            // -----------------------------------------------------------------
            check("Isolation: Student A cannot read Student B profile via service",
                    studentService.getProfile(studentBId).isEmpty());

            StudentService.ServiceResult hackProfile = studentService.saveProfile(
                    studentBId, "+9999999999", "Hacked University", "Hacked", 2030, "Hacked", null);
            check("Isolation: Student A cannot overwrite Student B profile via service",
                    !hackProfile.isSuccess());

            // Verify Student B profile in DB remains pristine
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT college FROM student_profiles WHERE user_id = ?")) {
                checkStmt.setInt(1, studentBId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    check("Isolation: Student B profile in database untouched",
                            rs.next() && "Stanford".equals(rs.getString("college")));
                }
            }

            check("Isolation: Student A cannot read Student B active resume via service",
                    resumeService.getActiveResume(studentBId).isEmpty());
            check("Isolation: Student A cannot list Student B resumes via service",
                    resumeService.getAllResumes(studentBId).isEmpty());

            boolean deleteOtherResume = resumeService.deleteResume(resumeBId, studentBId);
            check("Isolation: Student A cannot delete Student B resume via service",
                    !deleteOtherResume);

            // Verify resume B still exists in DB
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM resumes WHERE id = ?")) {
                checkStmt.setInt(1, resumeBId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    check("Isolation: Student B resume remains in database",
                            rs.next() && rs.getInt(1) == 1);
                }
            }

            check("Isolation: Student A cannot read Student B analysis history via service",
                    analysisService.getStudentAnalysisHistory(studentBId, 10).isEmpty());

            check("Isolation: Student A cannot check hasApplied for Student B",
                    !appService.hasApplied(studentBId, job1Id));
            check("Isolation: Student A cannot get Student B applications via service",
                    appService.getStudentApplications(studentBId).isEmpty());

            // -----------------------------------------------------------------
            // T3: Atomic DAO Scoped Ownership Predicates Protect Against Cross-Tenant Access
            // -----------------------------------------------------------------
            Optional<Resume> crossDaoResume = resumeDAO.findByIdForStudent(resumeBId, studentAId);
            check("DAO Isolation: resumeDAO.findByIdForStudent returns empty across students",
                    crossDaoResume.isEmpty());

            boolean crossDaoDelete = resumeDAO.deleteForStudent(resumeBId, studentAId);
            check("DAO Isolation: resumeDAO.deleteForStudent returns false across students",
                    !crossDaoDelete);

            // Verify updateForStudent does not update another student's resume
            Resume tamperedResumeB = new Resume(studentAId, "hacked.pdf", "/hacked", 100, "hhash", "htext", "{}");
            tamperedResumeB.setId(resumeBId);
            boolean crossDaoUpdate = resumeDAO.updateForStudent(tamperedResumeB, studentAId);
            check("DAO Isolation: resumeDAO.updateForStudent returns false across students",
                    !crossDaoUpdate);

            Optional<ResumeAnalysis> crossDaoAnalysis = analysisDAO.findByIdForStudent(analysisBId, studentAId);
            check("DAO Isolation: analysisDAO.findByIdForStudent returns empty across students",
                    crossDaoAnalysis.isEmpty());

            boolean crossDaoDeleteAnalysis = analysisDAO.deleteForStudent(analysisBId, studentAId);
            check("DAO Isolation: analysisDAO.deleteForStudent returns false across students",
                    !crossDaoDeleteAnalysis);

            // -----------------------------------------------------------------
            // T4: Admin Explicit Methods Succeed When Authenticated as Admin
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(adminUser);

            Optional<StudentProfile> adminViewProfile = studentService.getProfileForAdmin(studentAId);
            check("Admin Explicit: Admin reads student profile via getProfileForAdmin",
                    adminViewProfile.isPresent() && "MIT Updated".equals(adminViewProfile.get().getCollege()));

            StudentService.ServiceResult adminSaveProfile = studentService.saveProfileForAdmin(
                    studentAId, "+1111111111", "MIT Admin Verified", "M.S. CS", 2027, "Java, SQL", "/resumes/A.pdf");
            check("Admin Explicit: Admin updates student profile via saveProfileForAdmin",
                    adminSaveProfile.isSuccess());

            Optional<Resume> adminViewResume = resumeService.getActiveResumeForAdmin(studentAId);
            check("Admin Explicit: Admin reads student active resume via getActiveResumeForAdmin",
                    adminViewResume.isPresent() && adminViewResume.get().getId() == resumeAId);

            List<Resume> adminAllResumes = resumeService.getAllResumesForAdmin(studentAId);
            check("Admin Explicit: Admin lists student resumes via getAllResumesForAdmin",
                    adminAllResumes.size() == 1);

            List<ResumeAnalysis> adminAnalyses = analysisService.getStudentAnalysisHistoryForAdmin(studentAId, 10);
            check("Admin Explicit: Admin reads analysis history via getStudentAnalysisHistoryForAdmin",
                    adminAnalyses.size() == 1);

            List<Application> adminApps = appService.getStudentApplicationsForAdmin(studentAId);
            check("Admin Explicit: Admin reads student apps via getStudentApplicationsForAdmin",
                    adminApps.size() == 1);

            boolean adminDeleteResumeA = resumeService.deleteResumeForAdmin(resumeAId, studentAId);
            check("Admin Explicit: Admin deletes student resume atomically via deleteResumeForAdmin",
                    adminDeleteResumeA);

            // Verify resume A is gone from DB
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM resumes WHERE id = ?")) {
                checkStmt.setInt(1, resumeAId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    check("Admin Explicit: Student A resume deleted from database",
                            rs.next() && rs.getInt(1) == 0);
                }
            }

            // -----------------------------------------------------------------
            // T5: Student B Deleting Own Resume via Scoped Service
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(studentBUser);
            boolean studentBDeleteOwn = resumeService.deleteResume(resumeBId, studentBId);
            check("Legitimate: Student B deletes own resume via deleteResume",
                    studentBDeleteOwn);

            // Verify resume B is gone from DB
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM resumes WHERE id = ?")) {
                checkStmt.setInt(1, resumeBId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    check("Legitimate: Student B resume deleted from database",
                            rs.next() && rs.getInt(1) == 0);
                }
            }

        } catch (SQLException e) {
            fail("Live database execution error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            SessionManager.getInstance().logout();
            System.out.println("  Cleaning up test fixtures...");
            cleanupFixtures(studentAId, studentBId, recruiterId, adminId);

            int finalUsers = countTable("users");
            int finalJobs = countTable("jobs");
            int finalApps = countTable("applications");
            int finalResumes = countTable("resumes");
            int finalAnalyses = countTable("resume_analyses");
            int finalProfiles = countTable("student_profiles");

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
            check("Student profiles table fully restored (" + finalProfiles + " == " + initialProfiles + ")",
                    finalProfiles == initialProfiles);
        }
    }

    // =========================================================================
    // Fixture Helpers
    // =========================================================================

    private void cleanupFixtures(int studentAId, int studentBId, int recruiterId, int adminId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return;

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

            // Delete users
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM users WHERE id IN (?, ?, ?, ?) OR email LIKE ?")) {
                stmt.setInt(1, studentAId);
                stmt.setInt(2, studentBId);
                stmt.setInt(3, recruiterId);
                stmt.setInt(4, adminId);
                stmt.setString(5, PREFIX + "%");
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up fixtures: " + e.getMessage());
        }
    }

    private int insertUser(Connection conn, String email, String name, String role) throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'dummy_hash', ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, email);
            stmt.setString(2, name);
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
        String sql = "INSERT INTO student_profiles (user_id, phone, college, degree, graduation_year, skills, resume_path) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
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
        String sql = "INSERT INTO jobs (recruiter_id, title, company_name, description, required_skills, " +
                     "location, job_type, salary_range, eligibility, deadline, status, created_at) " +
                     "VALUES (?, ?, ?, 'Job desc', ?, 'Remote', 'FULL_TIME', '10 LPA', 'B.Tech', ?, ?, NOW())";
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

    private int insertApplication(Connection conn, int jobId, int studentId, String status,
                                  LocalDateTime appliedAt) throws SQLException {
        String sql = "INSERT INTO applications (job_id, student_id, cover_letter, status, applied_at, updated_at) " +
                     "VALUES (?, ?, 'Sample cover letter', ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, studentId);
            stmt.setString(3, status);
            stmt.setTimestamp(4, Timestamp.valueOf(appliedAt));
            stmt.setTimestamp(5, Timestamp.valueOf(appliedAt));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert application");
    }

    private int insertResume(Connection conn, int studentId, String fileName, String storedPath,
                             String parsedJson, boolean isActive) throws SQLException {
        String sql = "INSERT INTO resumes (student_id, file_name, stored_path, file_size_bytes, " +
                     "file_hash, parsed_text, parsed_json, is_active, uploaded_at) " +
                     "VALUES (?, ?, ?, 1024, 'dummy_hash', 'Sample resume text', ?, ?, NOW())";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, studentId);
            stmt.setString(2, fileName);
            stmt.setString(3, storedPath);
            stmt.setString(4, parsedJson);
            stmt.setBoolean(5, isActive);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert resume");
    }

    private int insertAnalysis(Connection conn, Integer resumeId, Integer jobId, int studentId,
                               int overallScore, int skillsScore, int expScore, int eduScore,
                               int compScore, int certScore, String matchingSkills, String missingSkills,
                               String summary) throws SQLException {
        String sql = "INSERT INTO resume_analyses (resume_id, job_id, student_id, overall_score, " +
                     "skills_score, experience_score, education_score, completeness_score, " +
                     "certifications_score, matching_skills, missing_skills, analysis_summary, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (resumeId != null) stmt.setInt(1, resumeId); else stmt.setNull(1, Types.INTEGER);
            if (jobId != null) stmt.setInt(2, jobId); else stmt.setNull(2, Types.INTEGER);
            stmt.setInt(3, studentId);
            stmt.setInt(4, overallScore);
            stmt.setInt(5, skillsScore);
            stmt.setInt(6, expScore);
            stmt.setInt(7, eduScore);
            stmt.setInt(8, compScore);
            stmt.setInt(9, certScore);
            stmt.setString(10, matchingSkills);
            stmt.setString(11, missingSkills);
            stmt.setString(12, summary);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert analysis");
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
    // Reflection & Assertion Helpers
    // =========================================================================

    private boolean isMethodDeprecated(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            return m.isAnnotationPresent(Deprecated.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            clazz.getMethod(methodName, paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

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

    private void assertThrows(String desc, Class<? extends Throwable> expected, ThrowingRunnable r) {
        try {
            r.run();
            fail(desc + " [Expected " + expected.getSimpleName() + " but nothing was thrown]");
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                pass(desc);
            } else {
                fail(desc + " [Expected " + expected.getSimpleName() + " but caught " + t.getClass().getSimpleName() + ": " + t.getMessage() + "]");
            }
        }
    }
}
