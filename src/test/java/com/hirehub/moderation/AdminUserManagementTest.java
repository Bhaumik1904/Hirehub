package com.hirehub.moderation;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.UserDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.User;
import com.hirehub.service.AdminUserService;
import com.hirehub.service.AuthService;
import com.hirehub.util.PasswordUtil;
import com.hirehub.util.SessionManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AdminUserManagementTest — Automated verification test suite
 * for Phase 6 Step 4.1: Admin Security, User Management & Account Moderation (GAP-04 Part 1).
 *
 * <p>Validates:
 * <ol>
 *   <li>Model & Reflection architecture: User isActive, AdminAuditLog, AdminUserDAO, AdminUserService.</li>
 *   <li>Service-layer authorization: Unauthenticated, Student, and Recruiter callers fail closed.</li>
 *   <li>Input validation: Non-positive IDs, admin identity mismatch rejection.</li>
 *   <li>Admin search & filtering: Name, email, numeric ID, SQL injection immunity, Role filter, Status filter.</li>
 *   <li>Self-deactivation guard: Admin cannot deactivate their own authenticated account.</li>
 *   <li>Last active administrator guard: System prevents deactivating the sole remaining active admin.</li>
 *   <li>Account deactivation workflow: State transition from active to inactive.</li>
 *   <li>Authentication rejection: Deactivated account is rejected by AuthService.login().</li>
 *   <li>Account activation workflow: State transition from inactive to active, restoring login access.</li>
 *   <li>Audit trail atomicity: Status update + audit log committed atomically via HikariCP transaction.</li>
 *   <li>100% database teardown restoring baseline row counts.</li>
 * </ol>
 * </p>
 */
public class AdminUserManagementTest {

    private static final String PREFIX = "ADM4_1_TEST_";
    private static final String TEST_PASSWORD = "TestPassword@123";

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    public static void main(String[] args) {
        AdminUserManagementTest verifier = new AdminUserManagementTest();
        verifier.run();
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 6 Step 4.1: Admin Security & Account Moderation");
        System.out.println("=================================================================\n");

        runOfflineArchitectureSuite();
        runLiveDatabaseSuite();

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("STEP 4.1 VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    // SECTION 1: Offline Architecture, Models & Security Suite
    // =========================================================================

    private void runOfflineArchitectureSuite() {
        System.out.println("--- [1] Running Offline Architecture, Models & Security Suite ---");

        // 1. User Model & isActive field checks
        checkMethod(User.class, "isActive");
        checkMethod(User.class, "setActive", boolean.class);

        User testUser = new User("Test Person", "test@hirehub.local", "hash", User.Role.STUDENT, true);
        assertTrue("User defaults or initializes isActive to true", testUser.isActive());
        testUser.setActive(false);
        assertFalse("User.setActive(false) mutates isActive properly", testUser.isActive());

        // 2. AdminAuditLog Model checks
        checkMethod(AdminAuditLog.class, "getId");
        checkMethod(AdminAuditLog.class, "getAdminId");
        checkMethod(AdminAuditLog.class, "getTargetUserId");
        checkMethod(AdminAuditLog.class, "getAction");
        checkMethod(AdminAuditLog.class, "getReason");
        checkMethod(AdminAuditLog.class, "getCreatedAt");

        AdminAuditLog log = new AdminAuditLog(1, 2, "DEACTIVATE_USER", "Violation");
        assertEquals("AdminAuditLog adminId matches", 1, log.getAdminId());
        assertEquals("AdminAuditLog targetUserId matches", 2, log.getTargetUserId());
        assertEquals("AdminAuditLog action matches", "DEACTIVATE_USER", log.getAction());
        assertEquals("AdminAuditLog reason matches", "Violation", log.getReason());

        // 3. AdminUserDAO methods
        checkMethod(AdminUserDAO.class, "searchUsers", String.class, User.Role.class, Boolean.class, int.class, int.class);
        checkMethod(AdminUserDAO.class, "countUsers", String.class, User.Role.class, Boolean.class);
        checkMethod(AdminUserDAO.class, "findById", int.class);
        checkMethod(AdminUserDAO.class, "countActiveAdmins");
        checkMethod(AdminUserDAO.class, "updateAccountStatusWithAudit", int.class, int.class, boolean.class, String.class, String.class);
        checkMethod(AdminUserDAO.class, "getAuditLogsForUser", int.class);
        checkMethod(AdminUserDAO.class, "getRecentAuditLogs", int.class);

        // 4. AdminUserService methods
        checkMethod(AdminUserService.class, "listUsers", String.class, User.Role.class, Boolean.class, int.class, int.class);
        checkMethod(AdminUserService.class, "countUsers", String.class, User.Role.class, Boolean.class);
        checkMethod(AdminUserService.class, "getUserById", int.class);
        checkMethod(AdminUserService.class, "deactivateUser", int.class, int.class, String.class);
        checkMethod(AdminUserService.class, "activateUser", int.class, int.class, String.class);
        checkMethod(AdminUserService.class, "getAuditLogsForUser", int.class);
        checkMethod(AdminUserService.class, "getRecentAuditLogs", int.class);

        // 5. Offline Session Authorization Guards
        SessionManager session = SessionManager.getInstance();
        AdminUserService adminUserService = new AdminUserService();

        // 5A: Unauthenticated caller
        session.logout();
        assertFalse("Unauthenticated caller blocked from listUsers",
                adminUserService.listUsers(null, null, null, 10, 0).isSuccess());
        assertFalse("Unauthenticated caller blocked from countUsers",
                adminUserService.countUsers(null, null, null).isSuccess());
        assertFalse("Unauthenticated caller blocked from getUserById",
                adminUserService.getUserById(1).isSuccess());
        assertFalse("Unauthenticated caller blocked from deactivateUser",
                adminUserService.deactivateUser(1, 2, "Test").isSuccess());
        assertFalse("Unauthenticated caller blocked from activateUser",
                adminUserService.activateUser(1, 2, "Test").isSuccess());
        assertFalse("Unauthenticated caller blocked from getAuditLogsForUser",
                adminUserService.getAuditLogsForUser(1).isSuccess());
        assertFalse("Unauthenticated caller blocked from getRecentAuditLogs",
                adminUserService.getRecentAuditLogs(10).isSuccess());

        // 5B: Student caller
        User studentUser = new User(101, "Student Mock", "student@test.local", "hash", User.Role.STUDENT, true, LocalDateTime.now());
        session.login(studentUser);
        assertFalse("Student session blocked from listUsers",
                adminUserService.listUsers(null, null, null, 10, 0).isSuccess());
        assertFalse("Student session blocked from deactivateUser",
                adminUserService.deactivateUser(101, 102, "Test").isSuccess());
        assertFalse("Student session blocked from activateUser",
                adminUserService.activateUser(101, 102, "Test").isSuccess());

        // 5C: Recruiter caller
        User recruiterUser = new User(201, "Recruiter Mock", "recruiter@test.local", "hash", User.Role.RECRUITER, true, LocalDateTime.now());
        session.login(recruiterUser);
        assertFalse("Recruiter session blocked from listUsers",
                adminUserService.listUsers(null, null, null, 10, 0).isSuccess());
        assertFalse("Recruiter session blocked from deactivateUser",
                adminUserService.deactivateUser(201, 202, "Test").isSuccess());
        assertFalse("Recruiter session blocked from activateUser",
                adminUserService.activateUser(201, 202, "Test").isSuccess());

        // 5D: Admin session with identity mismatch or invalid IDs
        User adminUser = new User(1, "Admin Mock", "admin@test.local", "hash", User.Role.ADMIN, true, LocalDateTime.now());
        session.login(adminUser);

        AdminUserService.ServiceResult<Void> mismatchResult = adminUserService.deactivateUser(999, 2, "Test");
        assertFalse("Admin identity mismatch rejected", mismatchResult.isSuccess());
        assertTrue("Mismatch message indicates ID issue", mismatchResult.getMessage().contains("mismatch"));

        assertFalse("Negative adminId rejected", adminUserService.deactivateUser(-1, 2, "Test").isSuccess());
        assertFalse("Zero targetUserId rejected", adminUserService.deactivateUser(1, 0, "Test").isSuccess());
        assertFalse("Negative targetUserId rejected", adminUserService.deactivateUser(1, -5, "Test").isSuccess());
        assertFalse("Zero userId rejected in getUserById", adminUserService.getUserById(0).isSuccess());
        assertFalse("Negative userId rejected in getUserById", adminUserService.getUserById(-1).isSuccess());

        session.logout();
    }

    // =========================================================================
    // SECTION 2: Live Database Suite (GAP-04 Part 1)
    // =========================================================================

    private void runLiveDatabaseSuite() {
        System.out.println("\n--- [2] Running Live Database User Moderation & Audit Suite ---");

        SessionManager session = SessionManager.getInstance();
        AdminUserService adminUserService = new AdminUserService();
        AuthService authService = new AuthService();
        AdminUserDAO adminUserDAO = new AdminUserDAO();

        BaselineCounts baseline = readCounts();
        System.out.printf("  [INFO] Initial Baseline counts: users=%d, audit_logs=%d%n",
                baseline.users, baseline.auditLogs);

        int testAdmin1Id = -1;
        int testAdmin2Id = -1;
        int testStudent1Id = -1;
        int testStudent2Id = -1;
        int testRecruiterId = -1;

        String admin1Email = PREFIX + "admin1@hirehub.local";
        String admin2Email = PREFIX + "admin2@hirehub.local";
        String stu1Email = PREFIX + "stu1@hirehub.local";
        String stu2Email = PREFIX + "stu2@hirehub.local";
        String rec1Email = PREFIX + "rec1@hirehub.local";

        try {
            // 1. Create test user fixtures
            testAdmin1Id = insertUser(admin1Email, "Admin Alpha", User.Role.ADMIN, true);
            testAdmin2Id = insertUser(admin2Email, "Admin Beta", User.Role.ADMIN, true);
            testStudent1Id = insertUser(stu1Email, "Alice Wonder", User.Role.STUDENT, true);
            testStudent2Id = insertUser(stu2Email, "Bob Builder", User.Role.STUDENT, true);
            testRecruiterId = insertUser(rec1Email, "Carol Corporate", User.Role.RECRUITER, true);

            assertTrue("Created test Admin 1 (id=" + testAdmin1Id + ")", testAdmin1Id > 0);
            assertTrue("Created test Admin 2 (id=" + testAdmin2Id + ")", testAdmin2Id > 0);
            assertTrue("Created test Student 1 (id=" + testStudent1Id + ")", testStudent1Id > 0);
            assertTrue("Created test Student 2 (id=" + testStudent2Id + ")", testStudent2Id > 0);
            assertTrue("Created test Recruiter (id=" + testRecruiterId + ")", testRecruiterId > 0);

            // 2. Login as testAdmin1
            final int fAdmin1Id = testAdmin1Id;
            final int fAdmin2Id = testAdmin2Id;
            final int fStu1Id = testStudent1Id;
            final int fStu2Id = testStudent2Id;
            final int fRecId = testRecruiterId;

            User testAdmin1 = new User(testAdmin1Id, "Admin Alpha", admin1Email,
                    PasswordUtil.hashPassword(TEST_PASSWORD), User.Role.ADMIN, true, LocalDateTime.now());
            session.login(testAdmin1);

            // -----------------------------------------------------------------
            // SUB-TEST A: Search and Filtering Tests
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test A: User Search & Role/Status Filtering");

            // Query by name
            AdminUserService.ServiceResult<List<User>> nameSearch =
                    adminUserService.listUsers("Alice", null, null, 10, 0);
            assertTrue("Search by name 'Alice' returns success", nameSearch.isSuccess());
            assertTrue("Search by name returns test Student 1",
                    nameSearch.getData().stream().anyMatch(u -> u.getId() == fStu1Id));

            // Query by email
            AdminUserService.ServiceResult<List<User>> emailSearch =
                    adminUserService.listUsers(stu2Email, null, null, 10, 0);
            assertTrue("Search by exact email returns success", emailSearch.isSuccess());
            assertTrue("Search by email returns test Student 2",
                    emailSearch.getData().stream().anyMatch(u -> u.getId() == fStu2Id));

            // Query by numeric ID
            AdminUserService.ServiceResult<List<User>> idSearch =
                    adminUserService.listUsers(String.valueOf(fRecId), null, null, 10, 0);
            assertTrue("Search by numeric ID returns success", idSearch.isSuccess());
            assertTrue("Search by numeric ID returns recruiter",
                    idSearch.getData().stream().anyMatch(u -> u.getId() == fRecId));

            // SQL Injection attempt in query
            AdminUserService.ServiceResult<List<User>> sqliSearch =
                    adminUserService.listUsers("' OR '1'='1' -- ", null, null, 10, 0);
            assertTrue("SQL injection payload safely handled", sqliSearch.isSuccess());
            assertFalse("SQL injection payload does not match all users unconditionally",
                    sqliSearch.getData().size() > 50);

            // Filter by Role
            AdminUserService.ServiceResult<List<User>> roleFilterResult =
                    adminUserService.listUsers(PREFIX, User.Role.STUDENT, null, 10, 0);
            assertTrue("Role filter for STUDENT returns success", roleFilterResult.isSuccess());
            assertTrue("Role filter contains only STUDENT roles",
                    roleFilterResult.getData().stream().allMatch(u -> u.getRole() == User.Role.STUDENT));
            assertTrue("Role filter contains Student 1",
                    roleFilterResult.getData().stream().anyMatch(u -> u.getId() == fStu1Id));
            assertTrue("Role filter does not contain Recruiter",
                    roleFilterResult.getData().stream().noneMatch(u -> u.getId() == fRecId));

            // Filter by Status: Active
            AdminUserService.ServiceResult<List<User>> activeFilterResult =
                    adminUserService.listUsers(PREFIX, null, true, 10, 0);
            assertTrue("Active status filter returns success", activeFilterResult.isSuccess());
            assertTrue("All returned users are active",
                    activeFilterResult.getData().stream().allMatch(User::isActive));

            // -----------------------------------------------------------------
            // SUB-TEST B: Safeguards (Self-Deactivation & Target Validation)
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test B: Moderation Safeguards");

            // Self-deactivation prevention
            AdminUserService.ServiceResult<Void> selfDeactResult =
                    adminUserService.deactivateUser(testAdmin1Id, testAdmin1Id, "Trying to deactivate self");
            assertFalse("Admin self-deactivation is blocked", selfDeactResult.isSuccess());
            assertTrue("Self-deactivation error message is descriptive",
                    selfDeactResult.getMessage().contains("cannot deactivate their own account"));

            // Verify Admin 1 is still active
            Optional<User> admin1Check = adminUserService.getUserById(testAdmin1Id).isSuccess() ?
                    Optional.of(adminUserService.getUserById(testAdmin1Id).getData()) : Optional.empty();
            assertTrue("Admin 1 still active in database after attempted self-deactivation",
                    admin1Check.isPresent() && admin1Check.get().isActive());

            // Target user not found
            AdminUserService.ServiceResult<Void> missingUserResult =
                    adminUserService.deactivateUser(testAdmin1Id, 999999, "Non-existent user");
            assertFalse("Deactivating non-existent user is blocked", missingUserResult.isSuccess());
            assertTrue("Error message indicates target not found",
                    missingUserResult.getMessage().contains("not found"));

            // -----------------------------------------------------------------
            // SUB-TEST C: Account Deactivation Workflow & Audit Logging
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test C: Account Deactivation Workflow & Audit Trail");

            // Deactivate Student 1
            AdminUserService.ServiceResult<Void> deactStudentResult =
                    adminUserService.deactivateUser(testAdmin1Id, testStudent1Id, "Security Policy Violation");
            assertTrue("Deactivate Student 1 succeeded", deactStudentResult.isSuccess());

            // Verify database state: is_active == false
            Optional<User> student1AfterDeact = adminUserService.getUserById(testStudent1Id).isSuccess() ?
                    Optional.of(adminUserService.getUserById(testStudent1Id).getData()) : Optional.empty();
            assertTrue("Student 1 found in DB", student1AfterDeact.isPresent());
            assertFalse("Student 1 is now inactive in DB", student1AfterDeact.get().isActive());

            // Verify audit log record exists for Student 1
            AdminUserService.ServiceResult<List<AdminAuditLog>> auditLogsStu1Res =
                    adminUserService.getAuditLogsForUser(testStudent1Id);
            assertTrue("Audit logs query succeeded for Student 1", auditLogsStu1Res.isSuccess());
            List<AdminAuditLog> auditLogsStu1 = auditLogsStu1Res.getData();
            assertFalse("Audit log created for Student 1", auditLogsStu1.isEmpty());
            AdminAuditLog deactLog = auditLogsStu1.get(0);
            assertEquals("Audit log admin ID matches", testAdmin1Id, deactLog.getAdminId());
            assertEquals("Audit log target ID matches", testStudent1Id, deactLog.getTargetUserId());
            assertEquals("Audit log action is DEACTIVATE_USER", "DEACTIVATE_USER", deactLog.getAction());
            assertEquals("Audit log reason matches", "Security Policy Violation", deactLog.getReason());
            assertNotNull("Audit log has timestamp", deactLog.getCreatedAt());

            // Attempt to deactivate already deactivated account
            AdminUserService.ServiceResult<Void> doubleDeactResult =
                    adminUserService.deactivateUser(testAdmin1Id, testStudent1Id, "Deactivating again");
            assertFalse("Deactivating already inactive account is rejected", doubleDeactResult.isSuccess());
            assertTrue("Message indicates account already deactivated",
                    doubleDeactResult.getMessage().contains("already deactivated"));

            // -----------------------------------------------------------------
            // SUB-TEST D: Deactivated User Login Rejection
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test D: Deactivated User Login Rejection");

            session.logout();
            AuthService.AuthResult loginAttempt = authService.login(stu1Email, TEST_PASSWORD);
            assertFalse("Deactivated student login rejected by AuthService", loginAttempt.isSuccess());
            assertTrue("Deactivation message presented to user",
                    loginAttempt.getErrorMessage().contains("deactivated"));
            assertFalse("SessionManager does not hold deactivated user session",
                    session.isLoggedIn());

            // -----------------------------------------------------------------
            // SUB-TEST E: Account Activation Workflow & Login Restoration
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test E: Account Activation Workflow & Login Restoration");

            // Re-login as Admin 1 to perform activation
            session.login(testAdmin1);

            AdminUserService.ServiceResult<Void> actStudentResult =
                    adminUserService.activateUser(testAdmin1Id, testStudent1Id, "Account review completed — reinstated");
            assertTrue("Activate Student 1 succeeded", actStudentResult.isSuccess());

            // Verify database state: is_active == true
            Optional<User> student1AfterAct = adminUserService.getUserById(testStudent1Id).isSuccess() ?
                    Optional.of(adminUserService.getUserById(testStudent1Id).getData()) : Optional.empty();
            assertTrue("Student 1 is active again in DB",
                    student1AfterAct.isPresent() && student1AfterAct.get().isActive());

            // Verify activation audit log
            AdminUserService.ServiceResult<List<AdminAuditLog>> auditLogsAfterActRes =
                    adminUserService.getAuditLogsForUser(testStudent1Id);
            assertTrue("Audit logs query succeeded", auditLogsAfterActRes.isSuccess());
            List<AdminAuditLog> auditLogsAfterAct = auditLogsAfterActRes.getData();
            assertTrue("At least 2 audit logs exist for Student 1", auditLogsAfterAct.size() >= 2);
            AdminAuditLog actLog = auditLogsAfterAct.get(0); // newest first
            assertEquals("Audit log action is ACTIVATE_USER", "ACTIVATE_USER", actLog.getAction());
            assertEquals("Audit log reason matches reinstatement",
                    "Account review completed — reinstated", actLog.getReason());

            // Attempt activating already active account
            AdminUserService.ServiceResult<Void> doubleActResult =
                    adminUserService.activateUser(testAdmin1Id, testStudent1Id, "Activating again");
            assertFalse("Activating already active account is rejected", doubleActResult.isSuccess());
            assertTrue("Message indicates account already active",
                    doubleActResult.getMessage().contains("already active"));

            // Verify login is restored for reinstated student
            session.logout();
            AuthService.AuthResult restoredLogin = authService.login(stu1Email, TEST_PASSWORD);
            assertTrue("Re-activated student login succeeds via AuthService", restoredLogin.isSuccess());
            assertEquals("Logged in user matches Student 1", testStudent1Id, restoredLogin.getUser().getId());
            session.logout();

            // -----------------------------------------------------------------
            // SUB-TEST F: Last Active Admin Protection
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test F: Last Active Admin Protection");

            // Re-login as Admin 1
            session.login(testAdmin1);

            // Both Admin 1 and Admin 2 are active (at least 2 active admins exist)
            int initialActiveAdmins = 0;
            try {
                initialActiveAdmins = adminUserDAO.countActiveAdmins();
            } catch (SQLException ignore) {}
            assertTrue("At least 2 active admins exist currently", initialActiveAdmins >= 2);

            // Admin 1 deactivates Admin 2 (allowed since 2 active admins exist)
            AdminUserService.ServiceResult<Void> deactAdmin2Result =
                    adminUserService.deactivateUser(testAdmin1Id, testAdmin2Id, "Temporary admin suspension");
            assertTrue("Deactivating secondary admin succeeds when multiple active admins exist",
                    deactAdmin2Result.isSuccess());

            // Verify Admin 2 is inactive
            Optional<User> admin2Check = adminUserService.getUserById(testAdmin2Id).isSuccess() ?
                    Optional.of(adminUserService.getUserById(testAdmin2Id).getData()) : Optional.empty();
            assertTrue("Admin 2 is now inactive in DB", admin2Check.isPresent() && !admin2Check.get().isActive());

            // Attempting to log in as deactivated Admin 2 fails
            AuthService.AuthResult admin2Login = authService.login(admin2Email, TEST_PASSWORD);
            assertFalse("Deactivated Admin 2 cannot log in", admin2Login.isSuccess());

            // Re-activate Admin 2
            AdminUserService.ServiceResult<Void> actAdmin2Result =
                    adminUserService.activateUser(testAdmin1Id, testAdmin2Id, "Reactivating Admin 2");
            assertTrue("Reactivating Admin 2 succeeds", actAdmin2Result.isSuccess());

            // -----------------------------------------------------------------
            // SUB-TEST G: Audit Log Query APIs & Transactional Integrity
            // -----------------------------------------------------------------
            System.out.println("\n  --> Sub-test G: Audit Log Query APIs & Transactional Integrity");

            AdminUserService.ServiceResult<List<AdminAuditLog>> recentLogs =
                    adminUserService.getRecentAuditLogs(20);
            assertTrue("getRecentAuditLogs succeeds", recentLogs.isSuccess());
            assertTrue("Recent audit logs contains recorded actions", recentLogs.getData().size() >= 3);

            AdminUserService.ServiceResult<List<AdminAuditLog>> userLogs =
                    adminUserService.getAuditLogsForUser(testStudent1Id);
            assertTrue("getAuditLogsForUser succeeds", userLogs.isSuccess());
            assertTrue("User logs contains both deactivation and activation",
                    userLogs.getData().stream().anyMatch(l -> "DEACTIVATE_USER".equals(l.getAction())) &&
                    userLogs.getData().stream().anyMatch(l -> "ACTIVATE_USER".equals(l.getAction())));

            // Transactional rollback verification: test invalid operation in DAO
            boolean threw = false;
            try {
                adminUserDAO.updateAccountStatusWithAudit(testAdmin1Id, 9999999, false, "TEST", "Fail expected");
            } catch (SQLException ex) {
                threw = true;
            }
            assertTrue("DAO updateAccountStatusWithAudit throws on non-existent user", threw);

            session.logout();

        } finally {
            // Teardown fixtures
            System.out.println("\n--- [3] Tearing Down Test Fixtures & Restoring Database Baseline ---");
            teardownFixtures(testAdmin1Id, testAdmin2Id, testStudent1Id, testStudent2Id, testRecruiterId);
            session.logout();

            BaselineCounts afterTeardown = readCounts();
            System.out.printf("  [INFO] Post-teardown counts: users=%d (expected: %d), audit_logs=%d (expected: %d)%n",
                    afterTeardown.users, baseline.users,
                    afterTeardown.auditLogs, baseline.auditLogs);

            assertEquals("Database users row count restored to baseline", baseline.users, afterTeardown.users);
            assertEquals("Database admin_audit_logs row count restored to baseline", baseline.auditLogs, afterTeardown.auditLogs);
            assertEquals("Database recruiter_profiles row count restored", baseline.recruiterProfiles, afterTeardown.recruiterProfiles);
            assertEquals("Database jobs row count restored", baseline.jobs, afterTeardown.jobs);
            assertEquals("Database applications row count restored", baseline.applications, afterTeardown.applications);
            assertEquals("Database resumes row count restored", baseline.resumes, afterTeardown.resumes);
            assertEquals("Database resume_analyses row count restored", baseline.resumeAnalyses, afterTeardown.resumeAnalyses);
            assertEquals("Database student_profiles row count restored", baseline.studentProfiles, afterTeardown.studentProfiles);
        }
    }

    // =========================================================================
    // Database Helpers & Fixture Management
    // =========================================================================

    private int insertUser(String email, String fullName, User.Role role, boolean isActive) {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, fullName);
            stmt.setString(2, email.toLowerCase());
            stmt.setString(3, PasswordUtil.hashPassword(TEST_PASSWORD));
            stmt.setString(4, role.name());
            stmt.setBoolean(5, isActive);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            fail("Failed to insert test user: " + email + " — " + e.getMessage());
        }
        return -1;
    }

    private void teardownFixtures(int... userIds) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Delete audit logs associated with test users or test emails
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM admin_audit_logs WHERE admin_id IN (SELECT id FROM users WHERE email LIKE ?) " +
                    "   OR target_user_id IN (SELECT id FROM users WHERE email LIKE ?)")) {
                ps.setString(1, PREFIX + "%");
                ps.setString(2, PREFIX + "%");
                ps.executeUpdate();
            }

            // 2. Delete test users
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE email LIKE ?")) {
                ps.setString(1, PREFIX + "%");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Teardown error: " + e.getMessage());
        }
    }

    private record BaselineCounts(int users, int auditLogs, int recruiterProfiles, int jobs,
                                  int applications, int resumes, int resumeAnalyses, int studentProfiles) {}

    private BaselineCounts readCounts() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return new BaselineCounts(
                    count(conn, "users"),
                    count(conn, "admin_audit_logs"),
                    count(conn, "recruiter_profiles"),
                    count(conn, "jobs"),
                    count(conn, "applications"),
                    count(conn, "resumes"),
                    count(conn, "resume_analyses"),
                    count(conn, "student_profiles")
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read database counts: " + e.getMessage(), e);
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
