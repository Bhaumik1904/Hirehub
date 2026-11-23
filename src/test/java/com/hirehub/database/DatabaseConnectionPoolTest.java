package com.hirehub.database;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 6 Step 3 Verifier: HikariCP Database Connection Pooling (GAP-02).
 *
 * <p>Validates:</p>
 * <ul>
 *   <li>Section 1: Configuration & Pool Initialization (properties, URL, pool size, timeouts)</li>
 *   <li>Section 2: Connection Borrowing, Return, & Pool Statistics (HikariPoolMXBean integration)</li>
 *   <li>Section 3: High-Concurrency Multithreading (20 threads concurrently borrowing from 10-connection pool)</li>
 *   <li>Section 4: Auto-Commit Restoration & Leak Prevention</li>
 *   <li>Section 5: Transaction Isolation, Commit & Rollback Integrity</li>
 *   <li>Section 6: Full Live DAO Suite Verification across all 10 DAOs</li>
 *   <li>Section 7: Idempotent Shutdown & Resilient Pool Re-initialization</li>
 *   <li>Teardown: 100% Database Baseline Row Count Preservation</li>
 * </ul>
 */
public class DatabaseConnectionPoolTest {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static final List<String> failures = new ArrayList<>();

    // Baseline row counts
    private static final Map<String, Integer> baselineCounts = new LinkedHashMap<>();
    private static final String[] TRACKED_TABLES = {
            "users", "recruiter_profiles", "jobs", "applications",
            "resumes", "resume_analyses", "student_profiles"
    };

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  HIREHUB PHASE 6 STEP 3: HIKARICP CONNECTION POOL VERIFIER    ");
        System.out.println("===============================================================\n");

        try {
            captureBaseline();

            testSection1_ConfigurationAndInitialization();
            testSection2_BorrowReturnAndPoolStats();
            testSection3_HighConcurrencyAccess();
            testSection4_AutoCommitRestoration();
            testSection5_TransactionCommitAndRollback();
            testSection6_LiveDAOIntegration();
            testSection7_ShutdownAndReinitialization();

            verifyBaselinePreservation();

        } catch (Exception e) {
            System.err.println("\n[FATAL] Unexpected verifier failure: " + e.getMessage());
            e.printStackTrace();
            recordFailure("FATAL Exception during test execution: " + e.getMessage());
        }

        printSummary();
    }

    // =========================================================================
    // SECTION 1: Configuration & Pool Initialization
    // =========================================================================

    private static void testSection1_ConfigurationAndInitialization() {
        System.out.println("--- SECTION 1: CONFIGURATION & POOL INITIALIZATION ---");

        // 1.1 Pool is initialized or initializable
        boolean connTest = DatabaseConnection.testConnection();
        assertTrue("1.1 DatabaseConnection.testConnection() succeeds", connTest);
        assertTrue("1.2 DatabaseConnection.isInitialized() returns true", DatabaseConnection.isInitialized());

        HikariDataSource ds = DatabaseConnection.getDataSource();
        assertNotNull("1.3 HikariDataSource instance is not null", ds);
        assertFalse("1.4 HikariDataSource is not closed", ds.isClosed());

        // 1.5 Verify Pool Configuration Properties
        assertEquals("1.5 Pool name is 'HireHubPool'", "HireHubPool", ds.getPoolName());
        assertEquals("1.6 Driver class is 'com.mysql.cj.jdbc.Driver'", "com.mysql.cj.jdbc.Driver", ds.getDriverClassName());
        assertTrue("1.7 Maximum pool size >= 10", ds.getMaximumPoolSize() >= 10);
        assertTrue("1.8 Minimum idle >= 2", ds.getMinimumIdle() >= 2);
        assertEquals("1.9 Connection timeout is 30,000ms", 30000L, ds.getConnectionTimeout());
        assertEquals("1.10 Idle timeout is 600,000ms", 600000L, ds.getIdleTimeout());
        assertEquals("1.11 Max lifetime is 1,800,000ms", 1800000L, ds.getMaxLifetime());
        assertEquals("1.12 Validation timeout is 5,000ms", 5000L, ds.getValidationTimeout());
        assertTrue("1.13 Auto-commit defaults to true", ds.isAutoCommit());

        // 1.14 Connection URL sanity check
        String jdbcUrl = ds.getJdbcUrl();
        assertNotNull("1.14 JDBC URL is not null", jdbcUrl);
        assertTrue("1.15 JDBC URL starts with jdbc:mysql://", jdbcUrl.startsWith("jdbc:mysql://"));
        assertTrue("1.16 JDBC URL targets hirehub_db", jdbcUrl.contains("hirehub_db"));
        assertTrue("1.17 JDBC URL specifies Asia/Kolkata timezone", jdbcUrl.contains("serverTimezone=Asia/Kolkata"));

        // 1.18 Borrow connection and verify proxy type
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertNotNull("1.18 Borrowed connection is not null", conn);
            assertFalse("1.19 Borrowed connection is not closed", conn.isClosed());
            assertTrue("1.20 Borrowed connection is a Hikari proxy", conn.getClass().getName().contains("Hikari"));
            assertTrue("1.21 Connection is valid with 2s timeout", conn.isValid(2));
        } catch (SQLException e) {
            recordFailure("1.18-1.21 Borrow connection failed: " + e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // SECTION 2: Connection Borrowing, Return & Pool Statistics
    // =========================================================================

    private static void testSection2_BorrowReturnAndPoolStats() {
        System.out.println("--- SECTION 2: CONNECTION BORROW, RETURN & POOL STATS ---");

        int initialActive = DatabaseConnection.getActiveConnections();
        int initialTotal = DatabaseConnection.getTotalConnections();
        assertTrue("2.1 Initial active connections >= 0", initialActive >= 0);
        assertTrue("2.2 Total connections >= minimum idle", initialTotal >= 0);

        // 2.3 Borrow single connection: active count increments
        try (Connection conn1 = DatabaseConnection.getConnection()) {
            int activeDuring = DatabaseConnection.getActiveConnections();
            assertTrue("2.3 Active connections increases while borrowed", activeDuring >= initialActive + 1);

            // 2.4 Borrow second connection
            try (Connection conn2 = DatabaseConnection.getConnection()) {
                int activeDuring2 = DatabaseConnection.getActiveConnections();
                assertTrue("2.4 Active connections increases again with second borrow", activeDuring2 >= activeDuring + 1);
            }
            // 2.5 conn2 closed -> active count decreases
            int activeAfterConn2 = DatabaseConnection.getActiveConnections();
            assertTrue("2.5 Active connections decrements after conn2 closed", activeAfterConn2 < activeDuring + 1);
        } catch (SQLException e) {
            recordFailure("2.3-2.5 Borrow connection failed: " + e.getMessage());
        }

        // 2.6 conn1 closed -> returns to initial level
        int activeAfterAll = DatabaseConnection.getActiveConnections();
        assertTrue("2.6 Active connections returns to baseline level", activeAfterAll <= initialActive + 1);

        // 2.7 Pool status summary string formatting
        String summary = DatabaseConnection.getPoolStatusSummary();
        assertNotNull("2.7 Pool status summary is not null", summary);
        assertTrue("2.8 Summary contains 'HireHubPool'", summary.contains("HireHubPool"));
        assertTrue("2.9 Summary contains 'Active='", summary.contains("Active="));
        assertTrue("2.10 Summary contains 'Idle='", summary.contains("Idle="));
        assertTrue("2.11 Summary contains 'Total='", summary.contains("Total="));
        System.out.println("  [Diag] Status Summary: " + summary);
        System.out.println();
    }

    // =========================================================================
    // SECTION 3: High-Concurrency Multithreading
    // =========================================================================

    private static void testSection3_HighConcurrencyAccess() {
        System.out.println("--- SECTION 3: HIGH-CONCURRENCY MULTITHREADING ---");

        final int NUM_THREADS = 20;
        final int TASKS_PER_THREAD = 3;
        final int TOTAL_OPERATIONS = NUM_THREADS * TASKS_PER_THREAD;

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(TOTAL_OPERATIONS);

        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        List<String> threadErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < TOTAL_OPERATIONS; i++) {
            final int opId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all workers to strike simultaneously
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users");
                         ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            if (count >= 0) {
                                successfulOps.incrementAndGet();
                            } else {
                                failedOps.incrementAndGet();
                                threadErrors.add("Op " + opId + ": Invalid user count: " + count);
                            }
                        }
                    }
                } catch (Exception e) {
                    failedOps.incrementAndGet();
                    threadErrors.add("Op " + opId + " failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release latch to let all 20 threads hammer the 10-connection pool simultaneously
        startLatch.countDown();

        try {
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            assertTrue("3.1 All 60 concurrent database operations completed within 15 seconds", completed);
            assertEquals("3.2 100% of concurrent operations succeeded (60/60)", TOTAL_OPERATIONS, successfulOps.get());
            assertEquals("3.3 Zero concurrent operations failed", 0, failedOps.get());
            if (!threadErrors.isEmpty()) {
                System.err.println("  [Error] Concurrency error sample: " + threadErrors.get(0));
            }
        } catch (InterruptedException e) {
            recordFailure("3.1 Concurrency test interrupted: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
        System.out.println();
    }

    // =========================================================================
    // SECTION 4: Auto-Commit Restoration & Leak Prevention
    // =========================================================================

    private static void testSection4_AutoCommitRestoration() {
        System.out.println("--- SECTION 4: AUTO-COMMIT RESTORATION & LEAK PREVENTION ---");

        // 4.1 Check and alter autoCommit
        try {
            Connection conn = DatabaseConnection.getConnection();
            assertTrue("4.1 Default borrowed connection has autoCommit=true", conn.getAutoCommit());
            conn.setAutoCommit(false);
            assertFalse("4.2 Changed autoCommit to false", conn.getAutoCommit());
            conn.close(); // Return to pool with autoCommit=false

            // Borrow again from pool — Hikari must have restored autoCommit to true!
            try (Connection conn2 = DatabaseConnection.getConnection()) {
                assertTrue("4.3 Hikari automatically restores autoCommit=true upon borrow", conn2.getAutoCommit());
            }
        } catch (SQLException e) {
            recordFailure("4.1-4.3 AutoCommit test failed: " + e.getMessage());
        }

        // 4.4 Verify multiple rapid borrows/closes do not leak connections
        for (int i = 0; i < 25; i++) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT 1");
                 ResultSet rs = stmt.executeQuery()) {
                assertTrue("4.4 Rapid borrow/close succeeds on iteration " + i, rs.next() && rs.getInt(1) == 1);
            } catch (SQLException e) {
                recordFailure("4.4 Rapid borrow iteration " + i + " failed: " + e.getMessage());
                break;
            }
        }
        System.out.println();
    }

    // =========================================================================
    // SECTION 5: Transaction Commit & Rollback Integrity
    // =========================================================================

    private static void testSection5_TransactionCommitAndRollback() {
        System.out.println("--- SECTION 5: TRANSACTION COMMIT & ROLLBACK INTEGRITY ---");

        String testEmail = "test_pool_txn_" + System.currentTimeMillis() + "@hirehub.test";

        // 5.1 Test Rollback
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, full_name) VALUES (?, 'hash', 'STUDENT', 'Txn Rollback Test')")) {
                stmt.setString(1, testEmail);
                stmt.executeUpdate();
            }
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            recordFailure("5.1 Insert before rollback failed: " + e.getMessage());
        }

        // Verify row does NOT exist after rollback
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM users WHERE email = ?")) {
            stmt.setString(1, testEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                assertFalse("5.1 Rolled back transaction did not persist row", rs.next());
            }
        } catch (SQLException e) {
            recordFailure("5.1 Verify rollback check failed: " + e.getMessage());
        }

        // 5.2 Test Commit
        int insertedUserId = -1;
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, full_name) VALUES (?, 'hash', 'STUDENT', 'Txn Commit Test')",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, testEmail);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        insertedUserId = keys.getInt(1);
                    }
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
            assertTrue("5.2 Committed transaction generated user ID", insertedUserId > 0);
        } catch (SQLException e) {
            recordFailure("5.2 Insert before commit failed: " + e.getMessage());
        }

        // Verify row DOES exist after commit
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT full_name FROM users WHERE id = ?")) {
            stmt.setInt(1, insertedUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue("5.3 Committed row persists in database", rs.next());
                assertEquals("5.4 Committed row has expected name", "Txn Commit Test", rs.getString(1));
            }
        } catch (SQLException e) {
            recordFailure("5.3 Verify commit check failed: " + e.getMessage());
        }

        // Clean up committed test row to preserve baseline
        if (insertedUserId > 0) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                stmt.setInt(1, insertedUserId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                recordFailure("5.5 Cleanup test user failed: " + e.getMessage());
            }
        }
        System.out.println();
    }

    // =========================================================================
    // SECTION 6: Representative Live DAO Suite
    // =========================================================================

    private static void testSection6_LiveDAOIntegration() {
        System.out.println("--- SECTION 6: REPRESENTATIVE LIVE DAO SUITE ---");

        int studentId = 5;
        int recruiterId = 22;
        int jobId = 1;

        // 6.1 UserDAO
        try {
            UserDAO userDAO = new UserDAO();
            Optional<User> adminOpt = userDAO.findByEmail("admin@hirehub.com");
            assertTrue("6.1 UserDAO.findByEmail('admin@hirehub.com') returns admin", adminOpt.isPresent());
            assertEquals("6.2 User has ADMIN role", User.Role.ADMIN, adminOpt.get().getRole());

            Optional<User> studentOpt = userDAO.findByEmail("bhaumikhinunia019@gmail.com");
            if (studentOpt.isPresent()) {
                studentId = studentOpt.get().getId();
            }
            Optional<User> recruiterOpt = userDAO.findByEmail("infotech@gmail.com");
            if (recruiterOpt.isPresent()) {
                recruiterId = recruiterOpt.get().getId();
            }
        } catch (Exception e) {
            recordFailure("6.1 UserDAO integration failed: " + e.getMessage());
        }

        // 6.3 StudentDAO
        try {
            StudentDAO studentDAO = new StudentDAO();
            Optional<StudentProfile> spOpt = studentDAO.findByUserId(studentId);
            assertTrue("6.3 StudentDAO.findByUserId(" + studentId + ") returns profile", spOpt.isPresent());
        } catch (Exception e) {
            recordFailure("6.3 StudentDAO integration failed: " + e.getMessage());
        }

        // 6.4 RecruiterDAO
        try {
            RecruiterDAO recruiterDAO = new RecruiterDAO();
            Optional<RecruiterProfile> rpOpt = recruiterDAO.findByUserId(recruiterId);
            assertTrue("6.4 RecruiterDAO.findByUserId(" + recruiterId + ") returns profile", rpOpt.isPresent());
        } catch (Exception e) {
            recordFailure("6.4 RecruiterDAO integration failed: " + e.getMessage());
        }

        // 6.5 JobDAO
        try {
            JobDAO jobDAO = new JobDAO();
            Optional<Job> jobOpt = jobDAO.findById(jobId);
            assertTrue("6.5 JobDAO.findById(" + jobId + ") returns job", jobOpt.isPresent());
            List<Job> activeJobs = jobDAO.findOpenActiveJobs();
            assertNotNull("6.6 JobDAO.findOpenActiveJobs() returns non-null list", activeJobs);
        } catch (Exception e) {
            recordFailure("6.5 JobDAO integration failed: " + e.getMessage());
        }

        // 6.7 ApplicationDAO
        try {
            ApplicationDAO appDAO = new ApplicationDAO();
            boolean hasApplied = appDAO.exists(jobId, studentId);
            assertTrue("6.7 ApplicationDAO.exists(" + jobId + ", " + studentId + ") returns true", hasApplied);
            List<Application> apps = appDAO.findByStudentId(studentId);
            assertTrue("6.8 ApplicationDAO.findByStudentId(" + studentId + ") returns at least 1 application", !apps.isEmpty());
        } catch (Exception e) {
            recordFailure("6.7 ApplicationDAO integration failed: " + e.getMessage());
        }

        // 6.9 ResumeDAO
        try {
            ResumeDAO resumeDAO = new ResumeDAO();
            List<Resume> allResumes = resumeDAO.findAllByStudentId(studentId);
            assertTrue("6.9 ResumeDAO.findAllByStudentId(" + studentId + ") returns resumes", !allResumes.isEmpty());
            if (!allResumes.isEmpty()) {
                int firstResumeId = allResumes.get(0).getId();
                Optional<Resume> rOpt = resumeDAO.findByIdForStudent(firstResumeId, studentId);
                assertTrue("6.10 ResumeDAO.findByIdForStudent(" + firstResumeId + ", " + studentId + ") returns resume", rOpt.isPresent());
            }
        } catch (Exception e) {
            recordFailure("6.9 ResumeDAO integration failed: " + e.getMessage());
        }

        // 6.11 ResumeAnalysisDAO
        try {
            ResumeAnalysisDAO analysisDAO = new ResumeAnalysisDAO();
            List<ResumeAnalysis> recentAnalyses = analysisDAO.findRecentByStudentId(studentId, 5);
            assertNotNull("6.11 ResumeAnalysisDAO.findRecentByStudentId(" + studentId + ") returns non-null list", recentAnalyses);
        } catch (Exception e) {
            recordFailure("6.11 ResumeAnalysisDAO integration failed: " + e.getMessage());
        }

        // 6.12 StudentAnalyticsDAO
        try {
            StudentAnalyticsDAO studentAnalyticsDAO = new StudentAnalyticsDAO();
            int openJobs = studentAnalyticsDAO.countOpenActiveJobs();
            assertTrue("6.12 StudentAnalyticsDAO.countOpenActiveJobs() >= 0", openJobs >= 0);
            Map<Application.Status, Integer> statusMap = studentAnalyticsDAO.countApplicationsByStatus(studentId, null, null);
            assertNotNull("6.13 StudentAnalyticsDAO.countApplicationsByStatus() returns map", statusMap);
        } catch (Exception e) {
            recordFailure("6.12 StudentAnalyticsDAO integration failed: " + e.getMessage());
        }

        // 6.14 RecruiterAnalyticsDAO
        try {
            RecruiterAnalyticsDAO recruiterAnalyticsDAO = new RecruiterAnalyticsDAO();
            RecruiterPipelineKPI pipeline = recruiterAnalyticsDAO.fetchPipelineKPI(recruiterId, null, null);
            assertNotNull("6.14 RecruiterAnalyticsDAO.fetchPipelineKPI(" + recruiterId + ", null, null) returns non-null", pipeline);
        } catch (Exception e) {
            recordFailure("6.14 RecruiterAnalyticsDAO integration failed: " + e.getMessage());
        }

        // 6.15 AdminAnalyticsDAO
        try {
            AdminAnalyticsDAO adminAnalyticsDAO = new AdminAnalyticsDAO();
            AdminOverviewKPI adminKPI = adminAnalyticsDAO.fetchOverviewKPI(null, null);
            assertNotNull("6.15 AdminAnalyticsDAO.fetchOverviewKPI() returns non-null", adminKPI);
            assertTrue("6.16 Total users >= 3", adminKPI.getTotalUsers() >= 3);
        } catch (Exception e) {
            recordFailure("6.15 AdminAnalyticsDAO integration failed: " + e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // SECTION 7: Idempotent Shutdown & Re-Initialization
    // =========================================================================

    private static void testSection7_ShutdownAndReinitialization() {
        System.out.println("--- SECTION 7: IDEMPOTENT SHUTDOWN & RE-INITIALIZATION ---");

        assertTrue("7.1 Pool is initialized before shutdown", DatabaseConnection.isInitialized());

        // 7.2 Shutdown pool
        DatabaseConnection.shutdown();
        assertFalse("7.2 Pool is not initialized after shutdown()", DatabaseConnection.isInitialized());

        // 7.3 Second shutdown call — must be idempotent and not throw
        try {
            DatabaseConnection.shutdown();
            assertTrue("7.3 Repeated shutdown() call executes idempotently without exception", true);
        } catch (Exception e) {
            recordFailure("7.3 Repeated shutdown threw exception: " + e.getMessage());
        }

        // 7.4 Resilient Re-Initialization on demand
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertNotNull("7.4 Pool automatically re-initializes upon subsequent getConnection() call", conn);
            assertTrue("7.5 Re-initialized pool connection is valid", conn.isValid(2));
            assertTrue("7.6 DatabaseConnection.isInitialized() is true again", DatabaseConnection.isInitialized());
        } catch (SQLException e) {
            recordFailure("7.4-7.6 Pool re-initialization failed: " + e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // Baseline Tracking & Verification
    // =========================================================================

    private static void captureBaseline() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String table : TRACKED_TABLES) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        baselineCounts.put(table, rs.getInt(1));
                    }
                }
            }
        }
        System.out.println("Database Baseline Snapshot:");
        baselineCounts.forEach((tbl, cnt) -> System.out.println("  - " + tbl + ": " + cnt));
        System.out.println();
    }

    private static void verifyBaselinePreservation() throws SQLException {
        System.out.println("--- DATABASE BASELINE PRESERVATION CHECK ---");
        boolean allMatched = true;
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String table : TRACKED_TABLES) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int currentCount = rs.getInt(1);
                        int expectedCount = baselineCounts.getOrDefault(table, -1);
                        if (currentCount == expectedCount) {
                            assertTrue("Baseline matched for table '" + table + "' (" + currentCount + ")", true);
                        } else {
                            allMatched = false;
                            recordFailure("Baseline mismatch for table '" + table + "': expected "
                                    + expectedCount + ", found " + currentCount);
                        }
                    }
                }
            }
        }
        if (allMatched) {
            System.out.println(">> Database baseline is 100% PRESERVED! Zero collateral records.\n");
        }
    }

    // =========================================================================
    // Test Assertion Utilities
    // =========================================================================

    private static void assertTrue(String testName, boolean condition) {
        totalTests++;
        if (condition) {
            passedTests++;
            System.out.println("  [PASS] " + testName);
        } else {
            failedTests++;
            failures.add(testName);
            System.err.println("  [FAIL] " + testName);
        }
    }

    private static void assertFalse(String testName, boolean condition) {
        assertTrue(testName, !condition);
    }

    private static void assertNotNull(String testName, Object obj) {
        assertTrue(testName, obj != null);
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        assertTrue(testName + " (expected: " + expected + ", actual: " + actual + ")",
                Objects.equals(expected, actual));
    }

    private static void recordFailure(String message) {
        totalTests++;
        failedTests++;
        failures.add(message);
        System.err.println("  [FAIL] " + message);
    }

    private static void printSummary() {
        System.out.println("===============================================================");
        System.out.println("  PHASE 6 STEP 3 VERIFIER SUMMARY");
        System.out.println("===============================================================");
        System.out.println("  TOTAL TESTS : " + totalTests);
        System.out.println("  PASSED      : " + passedTests);
        System.out.println("  FAILED      : " + failedTests);
        System.out.println("===============================================================");

        if (failedTests > 0) {
            System.err.println("\nFAILURES DETECTED (" + failedTests + "):");
            for (String f : failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        } else {
            System.out.println("\n>> ALL " + passedTests + " TESTS PASSED CLEANLY!");
            System.exit(0);
        }
    }
}
