package com.hirehub.applications;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.Resume;
import com.hirehub.model.User;
import com.hirehub.service.ApplicationService;
import com.hirehub.service.ResumeService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;

/**
 * ApplicationResumeSnapshotTest — Automated test suite for Phase 6 Step 2:
 * Application Resume Snapshot + Secure Recruiter Resume Access (GAP-05 + GAP-01).
 *
 * <p>Validates:
 * <ol>
 *   <li>GAP-05: Application Resume Snapshotting:
 *       <ul>
 *         <li>When student applies, active resume ID is snapshotted into {@code applications.resume_id}.</li>
 *         <li>Replacing/updating active resume later never mutates historical applications.</li>
 *         <li>Resumes referenced by any application are protected against deletion (both Java & DB foreign key).</li>
 *         <li>Unreferenced resumes can still be deleted safely.</li>
 *       </ul>
 *   </li>
 *   <li>GAP-01: Secure Recruiter Resume Access:
 *       <ul>
 *         <li>Recruiter can view/download resumes only for applications submitted to their own jobs.</li>
 *         <li>Cross-recruiter access attempts fail closed.</li>
 *         <li>Path traversal attempts (e.g. {@code ..}) and files outside {@code data/resumes} are rejected.</li>
 *         <li>Legacy applications without resumes return clear "Resume not available" status without errors.</li>
 *         <li>Admin explicit {@code *ForAdmin} method works properly; silent direct bypass is blocked.</li>
 *       </ul>
 *   </li>
 *   <li>100% database fixture teardown restoring baseline row counts.</li>
 * </ol>
 */
public class ApplicationResumeSnapshotTest {

    private static final String PREFIX = "SEC6_2_TEST_";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    public static void main(String[] args) {
        ApplicationResumeSnapshotTest verifier = new ApplicationResumeSnapshotTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 2: Resume Snapshot & Secure Recruiter Access");
        System.out.println("=================================================================\n");

        runOfflineArchitectureSuite();
        runLiveDatabaseSuite();

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("STEP 2 VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Architecture & Security Suite
    // =========================================================================

    private void runOfflineArchitectureSuite() {
        System.out.println("--- [1] Running Offline Architecture, Path Traversal & Security Suite ---");

        // 1. Model & Reflection checks
        checkMethod(Application.class, "getResumeId");
        checkMethod(Application.class, "setResumeId", Integer.class);
        checkMethod(ApplicationDAO.class, "insert", int.class, int.class, String.class, Integer.class);
        checkMethod(ApplicationDAO.class, "isResumeReferenced", int.class);
        checkMethod(ResumeDAO.class, "findResumeForApplicationAndRecruiter", int.class, int.class);
        checkMethod(ResumeDAO.class, "findResumeForApplicationForAdmin", int.class);
        checkMethod(ResumeDAO.class, "isReferencedByAnyApplication", int.class);
        checkMethod(ResumeService.class, "resolveAndValidateResumeFile", String.class);
        checkMethod(ResumeService.class, "isResumeReferencedByApplication", int.class);
        checkMethod(ApplicationService.class, "getResumeForApplication", int.class);
        checkMethod(ApplicationService.class, "getResumeForApplicationForAdmin", int.class);
        checkMethod(ApplicationService.class, "getResumeForApplicationForStudent", int.class, int.class);

        // 2. Path Traversal & Storage Isolation Checks
        ResumeService resumeService = new ResumeService();
        Path storageDir = resumeService.getStorageDirectory();

        // 2A: Null or blank
        assertFalse("Path validation: null path returns empty",
                resumeService.resolveAndValidateResumeFile(null).isPresent());
        assertFalse("Path validation: empty path returns empty",
                resumeService.resolveAndValidateResumeFile("").isPresent());
        assertFalse("Path validation: blank path returns empty",
                resumeService.resolveAndValidateResumeFile("   ").isPresent());

        // 2B: Directory traversal fast reject
        assertFalse("Path validation: '..' traversal rejected",
                resumeService.resolveAndValidateResumeFile("../data/resumes/test.pdf").isPresent());
        assertFalse("Path validation: Windows root traversal rejected",
                resumeService.resolveAndValidateResumeFile("..\\..\\..\\windows\\system32\\cmd.exe").isPresent());

        // 2C: Non-existent file
        assertFalse("Path validation: Non-existent file in storage dir returns empty",
                resumeService.resolveAndValidateResumeFile(storageDir.resolve("non_existent_12345.pdf").toString()).isPresent());

        // 2D: Existing file outside storage dir (e.g., pom.xml)
        File outsideFile = new File("pom.xml");
        if (outsideFile.exists()) {
            assertFalse("Path validation: Existing file outside storage dir (pom.xml) rejected",
                    resumeService.resolveAndValidateResumeFile(outsideFile.getAbsolutePath()).isPresent());
        }

        // 2E: Legitimate file inside storage dir
        try {
            Path testPdf = storageDir.resolve("offline_test_verify.pdf");
            Files.writeString(testPdf, "%PDF-1.4 test dummy content for path verification");
            Optional<File> validOpt = resumeService.resolveAndValidateResumeFile(testPdf.toString());
            assertTrue("Path validation: Valid file inside data/resumes accepted", validOpt.isPresent());
            if (validOpt.isPresent()) {
                assertTrue("Path validation: File exists on disk", validOpt.get().exists());
            }
            Files.deleteIfExists(testPdf);
        } catch (IOException e) {
            fail("Path validation file creation failed: " + e.getMessage());
        }

        // 3. Offline Service-Level Authorization Guards
        ApplicationService appService = new ApplicationService();
        SessionManager session = SessionManager.getInstance();
        session.logout();

        // 3A: Unauthenticated callers
        ApplicationService.ResumeAccessResult unauthRes = appService.getResumeForApplication(1);
        assertFalse("Unauth: getResumeForApplication fails safe", unauthRes.isSuccess());

        ApplicationService.ResumeAccessResult unauthAdminRes = appService.getResumeForApplicationForAdmin(1);
        assertFalse("Unauth: getResumeForApplicationForAdmin fails safe", unauthAdminRes.isSuccess());

        // 3B: Student session cannot access recruiter endpoint
        User studentUser = new User("Test Student", "student@test.local", "hash", User.Role.STUDENT);
        studentUser.setId(101);
        session.login(studentUser);

        ApplicationService.ResumeAccessResult studentAttempt = appService.getResumeForApplication(1);
        assertFalse("Student cannot call getResumeForApplication", studentAttempt.isSuccess());
        assertTrue("Student blocked with unauthorized message",
                studentAttempt.getMessage().contains("Recruiter session required"));

        // 3C: Recruiter session cannot call admin endpoint
        User recruiterUser = new User("Test Recruiter", "recruiter@test.local", "hash", User.Role.RECRUITER);
        recruiterUser.setId(201);
        session.login(recruiterUser);

        ApplicationService.ResumeAccessResult recruiterAdminAttempt = appService.getResumeForApplicationForAdmin(1);
        assertFalse("Recruiter cannot call getResumeForApplicationForAdmin", recruiterAdminAttempt.isSuccess());
        assertTrue("Recruiter blocked from admin endpoint",
                recruiterAdminAttempt.getMessage().contains("Admin session required"));

        // 3D: Admin session cannot call recruiter endpoint directly
        User adminUser = new User("Test Admin", "admin@test.local", "hash", User.Role.ADMIN);
        adminUser.setId(301);
        session.login(adminUser);

        ApplicationService.ResumeAccessResult adminDirectAttempt = appService.getResumeForApplication(1);
        assertFalse("Admin cannot call standard recruiter getResumeForApplication", adminDirectAttempt.isSuccess());
        assertTrue("Admin blocked from recruiter endpoint without recruiter role",
                adminDirectAttempt.getMessage().contains("Recruiter session required"));

        // 3E: Non-positive IDs
        session.login(recruiterUser);
        assertFalse("Invalid ID: 0 rejected", appService.getResumeForApplication(0).isSuccess());
        assertFalse("Invalid ID: negative rejected", appService.getResumeForApplication(-5).isSuccess());

        session.logout();
    }

    // =========================================================================
    // SECTION 2: Live Database Suite (GAP-05 & GAP-01)
    // =========================================================================

    private void runLiveDatabaseSuite() {
        System.out.println("\n--- [2] Running Live Database Tenant Isolation & Snapshot Suite ---");

        SessionManager session = SessionManager.getInstance();
        ApplicationService applicationService = new ApplicationService();
        ResumeService resumeService = new ResumeService();
        ApplicationDAO applicationDAO = new ApplicationDAO();
        ResumeDAO resumeDAO = new ResumeDAO();
        JobDAO jobDAO = new JobDAO();

        // Baseline verification
        BaselineCounts baseline = readCounts();

        int rec1Id = -1, rec2Id = -1, stu1Id = -1, stu2Id = -1;
        int job1Id = -1, job2Id = -1;
        int resAId = -1, resBId = -1, resCId = -1, resDId = -1;
        int app1Id = -1, app2Id = -1, legacyAppId = -1;

        Path fileA = null, fileB = null, fileC = null, fileD = null;

        try {
            // 1. Create test users
            rec1Id = insertUser(PREFIX + "rec1@test.local", "Recruiter One", User.Role.RECRUITER);
            rec2Id = insertUser(PREFIX + "rec2@test.local", "Recruiter Two", User.Role.RECRUITER);
            stu1Id = insertUser(PREFIX + "stu1@test.local", "Student One", User.Role.STUDENT);
            stu2Id = insertUser(PREFIX + "stu2@test.local", "Student Two", User.Role.STUDENT);

            // 2. Create jobs
            job1Id = insertJob(rec1Id, PREFIX + "Backend Engineer", "TechCorp");
            job2Id = insertJob(rec2Id, PREFIX + "Frontend Engineer", "DevStudio");

            // 3. Create physical files in data/resumes
            Path storageDir = resumeService.getStorageDirectory();
            fileA = storageDir.resolve("student_" + stu1Id + "_resumeA.pdf");
            fileB = storageDir.resolve("student_" + stu1Id + "_resumeB.pdf");
            fileC = storageDir.resolve("student_" + stu2Id + "_resumeC.pdf");
            fileD = storageDir.resolve("student_" + stu1Id + "_resumeD_unref.pdf");

            Files.writeString(fileA, "%PDF-1.4 Resume A: Student 1 Java & SQL");
            Files.writeString(fileB, "%PDF-1.4 Resume B: Student 1 FullStack React & Spring");
            Files.writeString(fileC, "%PDF-1.4 Resume C: Student 2 Python Data Science");
            Files.writeString(fileD, "%PDF-1.4 Resume D: Unreferenced temp resume");

            // 4. Insert resumes in DB
            // Resume A (Student 1, active)
            Resume resA = new Resume(stu1Id, "resume_A.pdf", fileA.toString(), Files.size(fileA), "hashA", "Resume A text", null);
            resA.setActive(true);
            resumeDAO.insert(resA);
            resAId = resA.getId();

            // Resume B (Student 1, inactive initially)
            Resume resB = new Resume(stu1Id, "resume_B.pdf", fileB.toString(), Files.size(fileB), "hashB", "Resume B text", null);
            resB.setActive(false);
            resumeDAO.insert(resB);
            resBId = resB.getId();

            // Resume C (Student 2, active)
            Resume resC = new Resume(stu2Id, "resume_C.pdf", fileC.toString(), Files.size(fileC), "hashC", "Resume C text", null);
            resC.setActive(true);
            resumeDAO.insert(resC);
            resCId = resC.getId();

            // 5. TEST GAP-05: Snapshot on Application Submission
            User stu1 = new User("Student One", PREFIX + "stu1@test.local", "hash", User.Role.STUDENT);
            stu1.setId(stu1Id);
            session.login(stu1);

            // Student 1 applies to Job 1 (Recruiter 1) while Resume A is active
            final int targetJob1Id = job1Id;
            ApplicationService.ServiceResult applyRes1 = applicationService.apply(stu1Id, job1Id, "Applying with Resume A");
            assertTrue("Student 1 application to Job 1 succeeds", applyRes1.isSuccess());

            Optional<Application> app1Opt = applicationDAO.findByStudentId(stu1Id).stream()
                    .filter(a -> a.getJobId() == targetJob1Id)
                    .findFirst();
            assertTrue("Application 1 exists in DB", app1Opt.isPresent());
            app1Id = app1Opt.get().getId();

            assertEquals("GAP-05: Application 1 snapshotted Resume A id",
                    Integer.valueOf(resAId), app1Opt.get().getResumeId());

            // Student 1 now activates Resume B (simulating uploading a new resume or switching active resume)
            resumeDAO.activateResume(resBId, stu1Id);

            Optional<Resume> activeAfterSwitch = resumeDAO.findActiveByStudentId(stu1Id);
            assertTrue("Active resume is now Resume B", activeAfterSwitch.isPresent() && activeAfterSwitch.get().getId() == resBId);

            // VERIFY HISTORICAL APPLICATION PRESERVATION:
            Optional<Application> app1AfterSwitch = applicationDAO.findById(app1Id);
            assertTrue("Application 1 still present", app1AfterSwitch.isPresent());
            assertEquals("GAP-05: Historical Application 1 resume_id remained Resume A after active resume switched",
                    Integer.valueOf(resAId), app1AfterSwitch.get().getResumeId());

            // Student 1 applies to Job 2 (Recruiter 2) while Resume B is active
            final int targetJob2Id = job2Id;
            ApplicationService.ServiceResult applyRes2 = applicationService.apply(stu1Id, job2Id, "Applying with Resume B");
            assertTrue("Student 1 application to Job 2 succeeds", applyRes2.isSuccess());

            Optional<Application> app2Opt = applicationDAO.findByStudentId(stu1Id).stream()
                    .filter(a -> a.getJobId() == targetJob2Id)
                    .findFirst();
            assertTrue("Application 2 exists in DB", app2Opt.isPresent());
            app2Id = app2Opt.get().getId();

            assertEquals("GAP-05: Application 2 snapshotted Resume B id",
                    Integer.valueOf(resBId), app2Opt.get().getResumeId());

            // 6. TEST GAP-01: Recruiter Resume Access & Multi-tenant Authorization
            // Recruiter 1 login
            User rec1 = new User("Recruiter One", PREFIX + "rec1@test.local", "hash", User.Role.RECRUITER);
            rec1.setId(rec1Id);
            session.login(rec1);

            // Recruiter 1 accesses Application 1 resume (own job) -> SUCCESS
            ApplicationService.ResumeAccessResult rec1App1Res = applicationService.getResumeForApplication(app1Id);
            assertTrue("GAP-01: Recruiter 1 successfully accesses resume for own job application", rec1App1Res.isSuccess());
            assertNotNull("Recruiter 1 retrieved resume entity", rec1App1Res.getResume());
            assertEquals("Recruiter 1 received exact snapshot Resume A", resAId, rec1App1Res.getResume().getId());
            assertNotNull("Recruiter 1 retrieved file handle", rec1App1Res.getFile());
            assertTrue("Recruiter 1 file exists on disk", rec1App1Res.getFile().exists());

            // Recruiter 1 attempts to access Application 2 resume (Job 2 owned by Recruiter 2) -> BLOCKED
            ApplicationService.ResumeAccessResult rec1App2Res = applicationService.getResumeForApplication(app2Id);
            assertFalse("GAP-01: Cross-recruiter access blocked (Recruiter 1 cannot access Application 2 resume)",
                    rec1App2Res.isSuccess());
            assertTrue("Blocked message indicates unauthorized or not found",
                    rec1App2Res.getMessage().contains("not authorized") || rec1App2Res.getMessage().contains("not found"));

            // Recruiter 2 login
            User rec2 = new User("Recruiter Two", PREFIX + "rec2@test.local", "hash", User.Role.RECRUITER);
            rec2.setId(rec2Id);
            session.login(rec2);

            // Recruiter 2 accesses Application 2 resume (own job) -> SUCCESS
            ApplicationService.ResumeAccessResult rec2App2Res = applicationService.getResumeForApplication(app2Id);
            assertTrue("GAP-01: Recruiter 2 successfully accesses resume for own job application", rec2App2Res.isSuccess());
            assertEquals("Recruiter 2 received exact snapshot Resume B", resBId, rec2App2Res.getResume().getId());

            // Recruiter 2 attempts to access Application 1 resume (Job 1 owned by Recruiter 1) -> BLOCKED
            ApplicationService.ResumeAccessResult rec2App1Res = applicationService.getResumeForApplication(app1Id);
            assertFalse("GAP-01: Cross-recruiter access blocked (Recruiter 2 cannot access Application 1 resume)",
                    rec2App1Res.isSuccess());

            // 7. TEST Legacy Application (resume_id = NULL)
            // Insert legacy application directly
            legacyAppId = applicationDAO.insert(job1Id, stu2Id, "Legacy cover letter without resume", null);
            session.login(rec1);
            ApplicationService.ResumeAccessResult legacyRes = applicationService.getResumeForApplication(legacyAppId);
            assertFalse("Legacy application access returns failure", legacyRes.isSuccess());
            assertEquals("Legacy application returns clear message",
                    "Resume not available for this application.", legacyRes.getMessage());

            // 8. TEST GAP-05 Deletion Protection
            // Student 1 attempts to delete Resume A (referenced by Application 1)
            session.login(stu1);
            boolean deleteAttemptA = resumeService.deleteResume(resAId, stu1Id);
            assertFalse("GAP-05: Referenced Resume A deletion blocked in Service", deleteAttemptA);

            // Confirm Resume A is still present in database and file still on disk
            Optional<Resume> checkA = resumeDAO.findById(resAId);
            assertTrue("Resume A still exists in DB after blocked deletion", checkA.isPresent());
            assertTrue("Resume A file still exists on disk after blocked deletion", Files.exists(fileA));

            // Admin attempts to delete Resume A via deleteResumeForAdmin -> also blocked
            User adminUser = new User("Admin", PREFIX + "admin@test.local", "hash", User.Role.ADMIN);
            adminUser.setId(999);
            session.login(adminUser);
            boolean adminDeleteAttempt = resumeService.deleteResumeForAdmin(resAId, stu1Id);
            assertFalse("GAP-05: Admin cannot delete referenced resume", adminDeleteAttempt);

            // Verify database foreign key constraint ON DELETE RESTRICT
            boolean fkBlocked = false;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM resumes WHERE id = ?")) {
                stmt.setInt(1, resAId);
                stmt.executeUpdate();
            } catch (SQLException sqle) {
                // Expected MySQL error 1451: Cannot delete or update a parent row: a foreign key constraint fails
                fkBlocked = (sqle.getErrorCode() == 1451 || sqle.getMessage().toLowerCase().contains("foreign key"));
            }
            assertTrue("GAP-05: Direct SQL DELETE on referenced resume blocked by MySQL foreign key constraint (1451)", fkBlocked);

            // 9. TEST Unreferenced Resume Deletion:
            // Insert Resume D for Student 1 (not referenced by any application)
            Resume resD = new Resume(stu1Id, "resume_D.pdf", fileD.toString(), Files.size(fileD), "hashD", "Resume D text", null);
            resD.setActive(false);
            resumeDAO.insert(resD);
            resDId = resD.getId();

            session.login(stu1);
            boolean deleteDRes = resumeService.deleteResume(resDId, stu1Id);
            assertTrue("GAP-05: Unreferenced Resume D can be deleted successfully", deleteDRes);
            assertFalse("Resume D row is gone from DB", resumeDAO.findById(resDId).isPresent());
            assertFalse("Resume D file is removed from disk", Files.exists(fileD));

        } catch (Exception e) {
            fail("Live database suite encountered unexpected exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Teardown test fixtures
            session.logout();
            System.out.println("  Cleaning up test fixtures and files...");
            cleanupFixtures(rec1Id, rec2Id, stu1Id, stu2Id, job1Id, job2Id,
                    app1Id, app2Id, legacyAppId, resAId, resBId, resCId, resDId,
                    fileA, fileB, fileC, fileD);

            // Validate full restoration
            BaselineCounts current = readCounts();
            assertEquals("Users table fully restored", baseline.users, current.users);
            assertEquals("Recruiter profiles table fully restored", baseline.recruiterProfiles, current.recruiterProfiles);
            assertEquals("Jobs table fully restored", baseline.jobs, current.jobs);
            assertEquals("Applications table fully restored", baseline.applications, current.applications);
            assertEquals("Resumes table fully restored", baseline.resumes, current.resumes);
            assertEquals("Resume analyses table fully restored", baseline.resumeAnalyses, current.resumeAnalyses);
            assertEquals("Student profiles table fully restored", baseline.studentProfiles, current.studentProfiles);
        }
    }

    // =========================================================================
    // Fixture Helpers & Teardown
    // =========================================================================

    private int insertUser(String email, String fullName, User.Role role) throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'dummy_hash', ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, email);
            stmt.setString(2, fullName);
            stmt.setString(3, role.name());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Could not insert user " + email);
    }

    private int insertJob(int recruiterId, String title, String company) throws SQLException {
        String sql = "INSERT INTO jobs (recruiter_id, title, company_name, description, job_type, location, status, deadline) " +
                "VALUES (?, ?, ?, 'Test job description', 'FULL_TIME', 'Remote', 'ACTIVE', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, recruiterId);
            stmt.setString(2, title);
            stmt.setString(3, company);
            stmt.setDate(4, Date.valueOf(LocalDate.now().plusDays(30)));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Could not insert job " + title);
    }

    private void cleanupFixtures(int rec1, int rec2, int stu1, int stu2,
                                 int job1, int job2,
                                 int app1, int app2, int legApp,
                                 int resA, int resB, int resC, int resD,
                                 Path fileA, Path fileB, Path fileC, Path fileD) {
        // Delete disk files
        try {
            if (fileA != null) Files.deleteIfExists(fileA);
            if (fileB != null) Files.deleteIfExists(fileB);
            if (fileC != null) Files.deleteIfExists(fileC);
            if (fileD != null) Files.deleteIfExists(fileD);
        } catch (IOException ignored) {}

        // Delete DB rows in reverse dependency order
        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Delete applications
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM applications WHERE id IN (?, ?, ?) OR student_id IN (?, ?) OR job_id IN (?, ?)")) {
                stmt.setInt(1, app1);
                stmt.setInt(2, app2);
                stmt.setInt(3, legApp);
                stmt.setInt(4, stu1);
                stmt.setInt(5, stu2);
                stmt.setInt(6, job1);
                stmt.setInt(7, job2);
                stmt.executeUpdate();
            }

            // 2. Delete resumes
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM resumes WHERE id IN (?, ?, ?, ?) OR student_id IN (?, ?)")) {
                stmt.setInt(1, resA);
                stmt.setInt(2, resB);
                stmt.setInt(3, resC);
                stmt.setInt(4, resD);
                stmt.setInt(5, stu1);
                stmt.setInt(6, stu2);
                stmt.executeUpdate();
            }

            // 3. Delete jobs
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM jobs WHERE id IN (?, ?) OR recruiter_id IN (?, ?)")) {
                stmt.setInt(1, job1);
                stmt.setInt(2, job2);
                stmt.setInt(3, rec1);
                stmt.setInt(4, rec2);
                stmt.executeUpdate();
            }

            // 4. Delete recruiter profiles if any created
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM recruiter_profiles WHERE user_id IN (?, ?)")) {
                stmt.setInt(1, rec1);
                stmt.setInt(2, rec2);
                stmt.executeUpdate();
            }

            // 5. Delete student profiles if any created
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM student_profiles WHERE user_id IN (?, ?)")) {
                stmt.setInt(1, stu1);
                stmt.setInt(2, stu2);
                stmt.executeUpdate();
            }

            // 6. Delete users
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM users WHERE id IN (?, ?, ?, ?) OR email LIKE ?")) {
                stmt.setInt(1, rec1);
                stmt.setInt(2, rec2);
                stmt.setInt(3, stu1);
                stmt.setInt(4, stu2);
                stmt.setString(5, PREFIX + "%");
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Teardown] Error during fixture cleanup: " + e.getMessage());
        }
    }

    private record BaselineCounts(int users, int recruiterProfiles, int jobs, int applications,
                                  int resumes, int resumeAnalyses, int studentProfiles) {}

    private BaselineCounts readCounts() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            int users = count(conn, "users");
            int recProfiles = count(conn, "recruiter_profiles");
            int jobs = count(conn, "jobs");
            int apps = count(conn, "applications");
            int resumes = count(conn, "resumes");
            int analyses = count(conn, "resume_analyses");
            int stuProfiles = count(conn, "student_profiles");
            return new BaselineCounts(users, recProfiles, jobs, apps, resumes, analyses, stuProfiles);
        } catch (SQLException e) {
            throw new RuntimeException("Could not read baseline table counts: " + e.getMessage(), e);
        }
    }

    private int count(Connection conn, String table) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // =========================================================================
    // Test Assertion Utilities
    // =========================================================================

    private void checkMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            assertTrue("Method exists: " + clazz.getSimpleName() + "." + methodName, m != null);
        } catch (NoSuchMethodException e) {
            fail("Missing expected method: " + clazz.getSimpleName() + "." + methodName);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + message);
        } else {
            failed++;
            System.err.println("  [FAIL] " + message);
        }
    }

    private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        boolean match = (expected == null && actual == null) || (expected != null && expected.equals(actual));
        if (match) {
            passed++;
            System.out.println("  [PASS] " + message + " (" + actual + ")");
        } else {
            failed++;
            System.err.println("  [FAIL] " + message + " — Expected: " + expected + ", Actual: " + actual);
        }
    }

    private void assertNotNull(String message, Object obj) {
        assertTrue(message, obj != null);
    }

    private void fail(String message) {
        failed++;
        System.err.println("  [FAIL] " + message);
    }
}
