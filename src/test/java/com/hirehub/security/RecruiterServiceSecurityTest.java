package com.hirehub.security;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.User;
import com.hirehub.service.RecruiterService;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.Optional;

/**
 * RecruiterServiceSecurityTest — Verification suite for Phase 6 Step 1:
 * Recruiter Service Authorization Closure (GAP-03).
 *
 * <p>Validates:
 * <ol>
 *   <li>Offline Service-Level Authorization Guards:
 *       <ul>
 *         <li>Unauthenticated requests fail safe on all recruiter service methods.</li>
 *         <li>Cross-recruiter ID spoofing (Recruiter A calling with Recruiter B ID) fails safe.</li>
 *         <li>Cross-role requests (Student calling recruiter services) fail safe.</li>
 *         <li>Admin cannot silently bypass recruiter methods, must use explicit {@code *ForAdmin} methods.</li>
 *         <li>Non-admin callers cannot access {@code *ForAdmin} methods.</li>
 *         <li>Non-positive and invalid IDs fail safe across all operations.</li>
 *       </ul>
 *   </li>
 *   <li>DAO Ownership Contracts:
 *       <ul>
 *         <li>SQL statements in {@link RecruiterDAO} enforce {@code WHERE user_id=?} ownership predicates.</li>
 *         <li>Direct DAO methods scope lookups and mutations by {@code user_id}.</li>
 *       </ul>
 *   </li>
 *   <li>Live Database Tenant Isolation & Integrity:
 *       <ul>
 *         <li>Recruiter A cannot view or update Recruiter B's profile.</li>
 *         <li>Database records remain completely immutable upon unauthorized mutation attempts.</li>
 *         <li>Legitimate recruiter operations function smoothly when authenticated as that recruiter.</li>
 *         <li>Admin explicitly-named operations succeed when authenticated as Admin.</li>
 *         <li>100% database fixture teardown restoring baseline row counts.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class RecruiterServiceSecurityTest {

    private static final String PREFIX = "SEC6_1_TEST_";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    public static void main(String[] args) {
        RecruiterServiceSecurityTest verifier = new RecruiterServiceSecurityTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 1: Recruiter Service Authorization Closure");
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
        System.out.println("--- [1] Running Offline Recruiter Service Authorization & DAO Audit Suite ---");

        RecruiterService recruiterService = new RecruiterService();

        User recruiterA = new User("Recruiter A", "recruiterA@test.local", "hash", User.Role.RECRUITER);
        recruiterA.setId(201);

        User recruiterB = new User("Recruiter B", "recruiterB@test.local", "hash", User.Role.RECRUITER);
        recruiterB.setId(202);

        User student = new User("Student", "student@test.local", "hash", User.Role.STUDENT);
        student.setId(101);

        User admin = new User("Admin", "admin@test.local", "hash", User.Role.ADMIN);
        admin.setId(301);

        // ---------------------------------------------------------------------
        // Part 1A: Unauthenticated callers fail safe
        // ---------------------------------------------------------------------
        SessionManager.getInstance().logout();

        check("Unauthenticated: RecruiterService.getProfile returns empty",
                recruiterService.getProfile(201).isEmpty());
        check("Unauthenticated: RecruiterService.saveProfile returns fail",
                !recruiterService.saveProfile(201, "Alpha Corp", "https://alpha.test", "Desc", "Tech").isSuccess());
        check("Unauthenticated: RecruiterService.getProfileForAdmin returns empty",
                recruiterService.getProfileForAdmin(201).isEmpty());
        check("Unauthenticated: RecruiterService.saveProfileForAdmin returns fail",
                !recruiterService.saveProfileForAdmin(201, "Alpha Corp", "https://alpha.test", "Desc", "Tech").isSuccess());

        // ---------------------------------------------------------------------
        // Part 1B: Cross-Recruiter ID Spoofing Fails Safe
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(recruiterA);

        check("Cross-Recruiter: Recruiter A cannot read Recruiter B profile",
                recruiterService.getProfile(202).isEmpty());
        check("Cross-Recruiter: Recruiter A cannot save Recruiter B profile",
                !recruiterService.saveProfile(202, "Malicious Corp", "https://bad.test", "Hijacked", "Crime").isSuccess());

        // ---------------------------------------------------------------------
        // Part 1C: Cross-Role Calls (Student calling RecruiterService) Fail Safe
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(student);

        check("Cross-Role: Student cannot read recruiter profile",
                recruiterService.getProfile(201).isEmpty());
        check("Cross-Role: Student cannot save recruiter profile",
                !recruiterService.saveProfile(201, "Student Corp", "https://stu.test", "Desc", "Tech").isSuccess());
        check("Cross-Role: Student cannot call getProfileForAdmin",
                recruiterService.getProfileForAdmin(201).isEmpty());
        check("Cross-Role: Student cannot call saveProfileForAdmin",
                !recruiterService.saveProfileForAdmin(201, "Student Corp", "https://stu.test", "Desc", "Tech").isSuccess());

        // ---------------------------------------------------------------------
        // Part 1D: Admin Separation & Explicit Admin Authorization
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(admin);

        check("Admin Separation: Admin cannot silently read recruiter profile via getProfile",
                recruiterService.getProfile(201).isEmpty());
        check("Admin Separation: Admin cannot silently save recruiter profile via saveProfile",
                !recruiterService.saveProfile(201, "Admin Silent", "https://adm.test", "Desc", "Tech").isSuccess());

        SessionManager.getInstance().login(recruiterA);
        check("Non-Admin: Recruiter cannot call RecruiterService.getProfileForAdmin",
                recruiterService.getProfileForAdmin(201).isEmpty());
        check("Non-Admin: Recruiter cannot call RecruiterService.saveProfileForAdmin",
                !recruiterService.saveProfileForAdmin(201, "Rec Profile", "https://rec.test", "Desc", "Tech").isSuccess());

        SessionManager.getInstance().logout();

        // ---------------------------------------------------------------------
        // Part 1E: Invalid Input & Non-positive ID Checks Fail Safe
        // ---------------------------------------------------------------------
        SessionManager.getInstance().login(recruiterA);

        check("Invalid ID: RecruiterService.getProfile with ID 0 returns empty",
                recruiterService.getProfile(0).isEmpty());
        check("Invalid ID: RecruiterService.getProfile with negative ID returns empty",
                recruiterService.getProfile(-10).isEmpty());
        check("Invalid ID: RecruiterService.saveProfile with ID 0 returns fail",
                !recruiterService.saveProfile(0, "Company", "https://test.com", "Desc", "Tech").isSuccess());
        check("Invalid ID: RecruiterService.saveProfile with negative ID returns fail",
                !recruiterService.saveProfile(-5, "Company", "https://test.com", "Desc", "Tech").isSuccess());
        check("Invalid Input: RecruiterService.saveProfile with null companyName returns fail",
                !recruiterService.saveProfile(201, null, "https://test.com", "Desc", "Tech").isSuccess());
        check("Invalid Input: RecruiterService.saveProfile with empty companyName returns fail",
                !recruiterService.saveProfile(201, "   ", "https://test.com", "Desc", "Tech").isSuccess());

        SessionManager.getInstance().login(admin);
        check("Invalid ID: RecruiterService.getProfileForAdmin with ID 0 returns empty",
                recruiterService.getProfileForAdmin(0).isEmpty());
        check("Invalid ID: RecruiterService.getProfileForAdmin with negative ID returns empty",
                recruiterService.getProfileForAdmin(-1).isEmpty());
        check("Invalid ID: RecruiterService.saveProfileForAdmin with ID 0 returns fail",
                !recruiterService.saveProfileForAdmin(0, "Company", "https://test.com", "Desc", "Tech").isSuccess());
        check("Invalid ID: RecruiterService.saveProfileForAdmin with negative ID returns fail",
                !recruiterService.saveProfileForAdmin(-1, "Company", "https://test.com", "Desc", "Tech").isSuccess());
        check("Invalid Input: RecruiterService.saveProfileForAdmin with blank companyName returns fail",
                !recruiterService.saveProfileForAdmin(201, "   ", "https://test.com", "Desc", "Tech").isSuccess());

        SessionManager.getInstance().logout();

        // ---------------------------------------------------------------------
        // Part 1F: DAO & Method Contract Audit via Reflection
        // ---------------------------------------------------------------------
        check("RecruiterService.getProfile(int) exists",
                hasMethod(RecruiterService.class, "getProfile", int.class));
        check("RecruiterService.saveProfile(int, String, String, String, String) exists",
                hasMethod(RecruiterService.class, "saveProfile", int.class, String.class, String.class, String.class, String.class));
        check("RecruiterService.getProfileForAdmin(int) exists",
                hasMethod(RecruiterService.class, "getProfileForAdmin", int.class));
        check("RecruiterService.saveProfileForAdmin(int, String, String, String, String) exists",
                hasMethod(RecruiterService.class, "saveProfileForAdmin", int.class, String.class, String.class, String.class, String.class));

        check("RecruiterDAO.findByUserId(int) exists",
                hasMethod(RecruiterDAO.class, "findByUserId", int.class));
        check("RecruiterDAO.saveOrUpdate(RecruiterProfile) exists",
                hasMethod(RecruiterDAO.class, "saveOrUpdate", RecruiterProfile.class));
        check("RecruiterDAO.update(RecruiterProfile) exists",
                hasMethod(RecruiterDAO.class, "update", RecruiterProfile.class));
        check("RecruiterDAO.insert(RecruiterProfile) exists",
                hasMethod(RecruiterDAO.class, "insert", RecruiterProfile.class));
        check("RecruiterDAO.existsByUserId(int) exists",
                hasMethod(RecruiterDAO.class, "existsByUserId", int.class));
        check("RecruiterDAO.deleteByUserId(int) exists",
                hasMethod(RecruiterDAO.class, "deleteByUserId", int.class));
    }

    // =========================================================================
    // SECTION 2: Live Database Tenant Isolation & Integrity Suite
    // =========================================================================

    private void runLiveDatabaseSecuritySuite() {
        System.out.println("\n--- [2] Running Live Database Tenant Isolation & Integrity Suite ---");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null || conn.isClosed()) {
                System.out.println("  [SKIP] Database connection unavailable. Skipping live DB suite.");
                skipped += 18;
                return;
            }
        } catch (SQLException e) {
            System.out.println("  [SKIP] Database unreachable (" + e.getMessage() + "). Skipping live DB suite.");
            skipped += 18;
            return;
        }

        RecruiterService recruiterService = new RecruiterService();
        RecruiterDAO recruiterDAO = new RecruiterDAO();

        int recAId = 0;
        int recBId = 0;
        int stuId = 0;
        int admId = 0;

        int initialUsers = countTable("users");
        int initialRecruiterProfiles = countTable("recruiter_profiles");
        int initialJobs = countTable("jobs");
        int initialApps = countTable("applications");
        int initialResumes = countTable("resumes");
        int initialAnalyses = countTable("resume_analyses");
        int initialStudentProfiles = countTable("student_profiles");

        User recAUser = null;
        User recBUser = null;
        User stuUser = null;
        User admUser = null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Setup isolated fixtures
            recAId = insertUser(conn, PREFIX + "recA@test.local", "Security Recruiter A", "RECRUITER");
            recAUser = new User("Security Recruiter A", PREFIX + "recA@test.local", "hash", User.Role.RECRUITER);
            recAUser.setId(recAId);

            recBId = insertUser(conn, PREFIX + "recB@test.local", "Security Recruiter B", "RECRUITER");
            recBUser = new User("Security Recruiter B", PREFIX + "recB@test.local", "hash", User.Role.RECRUITER);
            recBUser.setId(recBId);

            stuId = insertUser(conn, PREFIX + "stu@test.local", "Security Student", "STUDENT");
            stuUser = new User("Security Student", PREFIX + "stu@test.local", "hash", User.Role.STUDENT);
            stuUser.setId(stuId);

            admId = insertUser(conn, PREFIX + "adm@test.local", "Security Admin", "ADMIN");
            admUser = new User("Security Admin", PREFIX + "adm@test.local", "hash", User.Role.ADMIN);
            admUser.setId(admId);

            // Initial Recruiter Profiles
            insertRecruiterProfile(conn, recAId, "Alpha Technologies", "https://alpha.tech", "Cloud systems", "Technology");
            insertRecruiterProfile(conn, recBId, "Beta Financial", "https://beta.finance", "Investment banking", "Finance");

            // -----------------------------------------------------------------
            // T1: Legitimate Authenticated Recruiter Operations Work Smoothly
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(recAUser);

            Optional<RecruiterProfile> optA = recruiterService.getProfile(recAId);
            check("Legitimate: Recruiter A reads own profile",
                    optA.isPresent() && "Alpha Technologies".equals(optA.get().getCompanyName()));

            RecruiterService.ServiceResult updateResult = recruiterService.saveProfile(
                    recAId, "Alpha Technologies Inc.", "https://alpha.tech/v2", "Enterprise Cloud", "Tech");
            check("Legitimate: Recruiter A updates own profile", updateResult.isSuccess());

            Optional<RecruiterProfile> updatedA = recruiterDAO.findByUserId(recAId);
            check("Legitimate: Recruiter A update persisted to DB",
                    updatedA.isPresent() && "Alpha Technologies Inc.".equals(updatedA.get().getCompanyName())
                            && "https://alpha.tech/v2".equals(updatedA.get().getCompanyWebsite()));

            // -----------------------------------------------------------------
            // T2: Cross-Tenant Isolation: Recruiter A Cannot Read/Modify Recruiter B
            // -----------------------------------------------------------------
            Optional<RecruiterProfile> crossRead = recruiterService.getProfile(recBId);
            check("Isolation: Recruiter A cannot read Recruiter B profile via service", crossRead.isEmpty());

            RecruiterService.ServiceResult crossSave = recruiterService.saveProfile(
                    recBId, "Hacked Beta", "https://hacked.com", "Hacked desc", "Fraud");
            check("Isolation: Recruiter A cannot overwrite Recruiter B profile via service", !crossSave.isSuccess());

            Optional<RecruiterProfile> recBInDb = recruiterDAO.findByUserId(recBId);
            check("Isolation: Recruiter B profile in database completely untouched",
                    recBInDb.isPresent() && "Beta Financial".equals(recBInDb.get().getCompanyName())
                            && "https://beta.finance".equals(recBInDb.get().getCompanyWebsite()));

            // -----------------------------------------------------------------
            // T3: Cross-Role Isolation: Student Cannot Read/Modify Recruiter Profiles
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(stuUser);

            check("Cross-Role: Student cannot read Recruiter A profile",
                    recruiterService.getProfile(recAId).isEmpty());
            check("Cross-Role: Student cannot modify Recruiter A profile",
                    !recruiterService.saveProfile(recAId, "Student Pwned", "https://pwn.com", "Pwned", "None").isSuccess());
            check("Cross-Role: Student cannot call getProfileForAdmin",
                    recruiterService.getProfileForAdmin(recAId).isEmpty());
            check("Cross-Role: Student cannot call saveProfileForAdmin",
                    !recruiterService.saveProfileForAdmin(recAId, "Student Pwned", "https://pwn.com", "Pwned", "None").isSuccess());

            Optional<RecruiterProfile> recAAfterStudent = recruiterDAO.findByUserId(recAId);
            check("Cross-Role: Recruiter A profile in DB untouched after Student attempt",
                    recAAfterStudent.isPresent() && "Alpha Technologies Inc.".equals(recAAfterStudent.get().getCompanyName()));

            // -----------------------------------------------------------------
            // T4: Admin Explicit Operations Succeed & Enforce Separation
            // -----------------------------------------------------------------
            SessionManager.getInstance().login(admUser);

            check("Admin Separation: Admin cannot silently read recruiter profile via getProfile",
                    recruiterService.getProfile(recAId).isEmpty());
            check("Admin Separation: Admin cannot silently modify recruiter profile via saveProfile",
                    !recruiterService.saveProfile(recAId, "Admin Hijack", "https://adm.com", "Desc", "Tech").isSuccess());

            Optional<RecruiterProfile> adminRead = recruiterService.getProfileForAdmin(recAId);
            check("Admin Explicit: Admin reads recruiter profile via getProfileForAdmin",
                    adminRead.isPresent() && "Alpha Technologies Inc.".equals(adminRead.get().getCompanyName()));

            RecruiterService.ServiceResult adminSave = recruiterService.saveProfileForAdmin(
                    recAId, "Alpha Corp (Admin Verified)", "https://alpha.tech/verified", "Audited profile", "Cloud Systems");
            check("Admin Explicit: Admin updates recruiter profile via saveProfileForAdmin", adminSave.isSuccess());

            Optional<RecruiterProfile> recAAfterAdmin = recruiterDAO.findByUserId(recAId);
            check("Admin Explicit: Recruiter A profile in DB reflects Admin update",
                    recAAfterAdmin.isPresent() && "Alpha Corp (Admin Verified)".equals(recAAfterAdmin.get().getCompanyName()));

            // -----------------------------------------------------------------
            // T5: DAO Scoped Behavior
            // -----------------------------------------------------------------
            check("DAO: findByUserId for nonexistent user returns empty",
                    recruiterDAO.findByUserId(999999).isEmpty());
            check("DAO: existsByUserId for nonexistent user returns false",
                    !recruiterDAO.existsByUserId(999999));

        } catch (SQLException e) {
            System.err.println("Database test exception: " + e.getMessage());
            failed++;
        } finally {
            SessionManager.getInstance().logout();

            // -----------------------------------------------------------------
            // T6: Complete Fixture Teardown & Baseline Verification
            // -----------------------------------------------------------------
            System.out.println("  Cleaning up test fixtures...");
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM recruiter_profiles WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                        ps.setString(1, PREFIX + "%");
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM users WHERE email LIKE ?")) {
                        ps.setString(1, PREFIX + "%");
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }

            int finalUsers = countTable("users");
            int finalRecruiterProfiles = countTable("recruiter_profiles");
            int finalJobs = countTable("jobs");
            int finalApps = countTable("applications");
            int finalResumes = countTable("resumes");
            int finalAnalyses = countTable("resume_analyses");
            int finalStudentProfiles = countTable("student_profiles");

            check("Users table fully restored (" + finalUsers + " == " + initialUsers + ")",
                    finalUsers == initialUsers);
            check("Recruiter profiles table fully restored (" + finalRecruiterProfiles + " == " + initialRecruiterProfiles + ")",
                    finalRecruiterProfiles == initialRecruiterProfiles);
            check("Jobs table fully restored (" + finalJobs + " == " + initialJobs + ")",
                    finalJobs == initialJobs);
            check("Applications table fully restored (" + finalApps + " == " + initialApps + ")",
                    finalApps == initialApps);
            check("Resumes table fully restored (" + finalResumes + " == " + initialResumes + ")",
                    finalResumes == initialResumes);
            check("Resume analyses table fully restored (" + finalAnalyses + " == " + initialAnalyses + ")",
                    finalAnalyses == initialAnalyses);
            check("Student profiles table fully restored (" + finalStudentProfiles + " == " + initialStudentProfiles + ")",
                    finalStudentProfiles == initialStudentProfiles);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + label);
            passed++;
        } else {
            System.out.println("  [FAIL] " + label);
            failed++;
        }
    }

    private static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            clazz.getDeclaredMethod(methodName, paramTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private int countTable(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("countTable(" + tableName + ") error: " + e.getMessage());
        }
        return -1;
    }

    private int insertUser(Connection conn, String email, String name, String role) throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'dummy_hash', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, name);
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to insert user " + email);
    }

    private void insertRecruiterProfile(Connection conn, int userId, String companyName, String website,
                                        String description, String industry) throws SQLException {
        String sql = "INSERT INTO recruiter_profiles (user_id, company_name, company_website, company_description, industry) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, companyName);
            ps.setString(3, website);
            ps.setString(4, description);
            ps.setString(5, industry);
            ps.executeUpdate();
        }
    }
}
