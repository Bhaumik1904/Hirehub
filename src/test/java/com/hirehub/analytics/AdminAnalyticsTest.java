package com.hirehub.analytics;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.service.AdminAnalyticsService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

/**
 * AdminAnalyticsTest — Automated verification suite for Phase 5 Step 1:
 * Admin Analytics Backend (Read-Only).
 *
 * <p>Follows the same pattern as Phase4VerificationTest:
 * <ul>
 *   <li>Structural / logic tests run regardless of DB availability.</li>
 *   <li>Attempts DB connection; DB-dependent tests are marked SKIPPED (not FAILED) if unavailable.</li>
 *   <li>Performs ONLY read-only checks (never inserts/updates/deletes rows).</li>
 *   <li>Exits non-zero if any test FAILS.</li>
 * </ul>
 *
 * Run via: verify_phase4.ps1 -MainClass com.hirehub.util.AdminAnalyticsTest
 */
public class AdminAnalyticsTest {

    private static int testsPassed  = 0;
    private static int testsFailed  = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  HireHub Phase 5 Step 1: Admin Analytics Backend Verification");
        System.out.println("=================================================================\n");

        boolean dbAvailable = checkDbConnection();

        if (!dbAvailable) {
            System.out.println("  [WARN] MySQL unavailable - DB-dependent tests will be SKIPPED.\n");
        }

        // --- Structural / offline tests (no DB needed) ---
        testModelInstantiation();
        testServiceValidatesDateRange();
        testAvgScoreSanitizedWhenZeroAnalyses();

        // --- DB-dependent tests (read-only) ---
        if (dbAvailable) {
            testAllTimeSnapshotLoads();
            test30DayBoundedRange();
            testFutureDateRangeReturnsZeros();
            testAllStatusesAndJobTypesPresent();
            testUniqueSelectedConstraint();
            testAvgScoreSafeWhenNoAnalysesDb();
            testRecruiterCountMatchesDb();
            testJobKPIConstraints();
            testStudentParticipationConsistency();
            testNoNaNInSnapshot();
            testCollectionsAndSortOrder();
        } else {
            skipDbTests();
        }

        printSummary();
    }

    // =========================================================================
    // DB connection check
    // =========================================================================

    private static boolean checkDbConnection() {
        try {
            return DatabaseConnection.testConnection();
        } catch (Exception e) {
            System.err.println("  [WARN] DB connection check threw: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // Structural / logic tests (no DB)
    // =========================================================================

    /** T1: All model DTOs can be instantiated and setters/getters work. */
    private static void testModelInstantiation() {
        try {
            AdminOverviewKPI o = new AdminOverviewKPI();
            o.setTotalStudents(5);
            o.setStudentsWithAnyResume(3);
            o.setStudentsEverUploadedResume(4);
            assertTrue(o.getTotalStudents() == 5, "T1a AdminOverviewKPI totalStudents");
            assertTrue(o.getStudentsWithAnyResume() == 3 && o.getStudentsEverUploadedResume() == 4,
                    "T1a AdminOverviewKPI distinct resume metrics");

            AdminJobKPI j = new AdminJobKPI();
            j.setExpiredActiveJobs(2);
            j.setOpenAcceptingJobs(3);
            assertTrue(j.getExpiredActiveJobs() == 2, "T1b AdminJobKPI getter/setter");

            AdminApplicationKPI a = new AdminApplicationKPI();
            a.setUniqueSelectedStudents(1);
            assertTrue(a.getUniqueSelectedStudents() == 1, "T1c AdminApplicationKPI getter/setter");

            AdminStatusDistribution sd = new AdminStatusDistribution("PENDING", 10, 8);
            assertTrue("PENDING".equals(sd.getStatus()), "T1d AdminStatusDistribution status");

            AdminJobTypeStat jt = new AdminJobTypeStat("FULL_TIME", 5, 3, 2, 1, 1);
            assertTrue(jt.getTotalJobs() == 5, "T1e AdminJobTypeStat total");

            AdminTopJob tj = new AdminTopJob();
            tj.setTitle("Test Job");
            assertTrue("Test Job".equals(tj.getTitle()), "T1f AdminTopJob title");

            AdminStudentParticipation sp = new AdminStudentParticipation();
            sp.setAvgResumeScore(null);
            assertTrue(sp.getAvgResumeScore() == null, "T1g AdminStudentParticipation null avg");

            AdminRecruiterEngagement re = new AdminRecruiterEngagement();
            re.setRecruiterId(42);
            re.setRecruiterEmail("hr@acme.com");
            assertTrue(re.getRecruiterId() == 42 && "hr@acme.com".equals(re.getRecruiterEmail()),
                    "T1h AdminRecruiterEngagement fields");

            AdminActivityEvent ae = new AdminActivityEvent("JOB_POSTED", "Alice", "Job posted", null);
            assertTrue("JOB_POSTED".equals(ae.getEventType()), "T1i AdminActivityEvent type");

            AdminTrendPoint tp = new AdminTrendPoint(LocalDate.now(), 5, 2, 1);
            assertTrue(tp.getApplicationsCount() == 5, "T1j AdminTrendPoint count");

            AdminDashboardSnapshot snap = new AdminDashboardSnapshot();
            snap.setLoadError(null);
            assertTrue(!snap.hasError(), "T1k AdminDashboardSnapshot no error");

            pass("T1: All 11 model DTOs instantiate and setter/getter round-trip correctly");
        } catch (AssertionError e) {
            fail("T1: Model instantiation: " + e.getMessage());
        } catch (Exception e) {
            fail("T1: Unexpected exception: " + e.getMessage());
        }
    }

    /** T2: Service rejects from > to with IllegalArgumentException. */
    private static void testServiceValidatesDateRange() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            LocalDate future = LocalDate.now().plusDays(10);
            LocalDate past   = LocalDate.now().minusDays(10);
            boolean threw = false;
            try {
                svc.loadSnapshot(future, past);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            assertTrue(threw, "T2: loadSnapshot(from > to) must throw IllegalArgumentException");
            pass("T2: Service rejects invalid date range (from > to) with IllegalArgumentException");
        } catch (AssertionError e) {
            fail("T2: Date range validation: " + e.getMessage());
        }
    }

    /** T3: Service sanitization forces avgResumeScore to null when totalAnalyses == 0 or non-finite. */
    private static void testAvgScoreSanitizedWhenZeroAnalyses() {
        try {
            AdminStudentParticipation pZero = new AdminStudentParticipation();
            pZero.setTotalResumeAnalyses(0);
            pZero.setAvgResumeScore(0.0);
            AdminAnalyticsService.sanitizeStudentParticipation(pZero);
            assertTrue(pZero.getAvgResumeScore() == null,
                    "T3a: avgResumeScore must be null when totalResumeAnalyses == 0");

            AdminStudentParticipation pNaN = new AdminStudentParticipation();
            pNaN.setTotalResumeAnalyses(2);
            pNaN.setAvgResumeScore(Double.NaN);
            AdminAnalyticsService.sanitizeStudentParticipation(pNaN);
            assertTrue(pNaN.getAvgResumeScore() == null,
                    "T3b: avgResumeScore must be null when NaN");

            pass("T3: avgResumeScore sanitization guards zero analyses and NaN/Infinity");
        } catch (AssertionError e) {
            fail("T3: " + e.getMessage());
        }
    }

    // =========================================================================
    // DB-dependent tests (read-only)
    // =========================================================================

    /** T4: All-time snapshot loads without exception. */
    private static void testAllTimeSnapshotLoads() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(snap != null, "T4: snapshot not null");
            assertTrue(!snap.hasError(), "T4: snapshot has no load error - " + snap.getLoadError());
            assertTrue(snap.getOverviewKPI() != null, "T4: overviewKPI not null");
            assertTrue(snap.getJobKPI() != null, "T4: jobKPI not null");
            assertTrue(snap.getApplicationKPI() != null, "T4: applicationKPI not null");
            assertTrue(snap.getStudentParticipation() != null, "T4: studentParticipation not null");
            assertTrue(snap.getRefreshedAt() != null, "T4: refreshedAt not null");

            System.out.println("    [INFO] Live All-Time Snapshot: " + snap.getOverviewKPI()
                    + " | " + snap.getJobKPI()
                    + " | " + snap.getApplicationKPI()
                    + " | " + snap.getStudentParticipation());
            pass("T4: All-time snapshot loads cleanly with no errors");
        } catch (AssertionError e) {
            fail("T4: All-time snapshot: " + e.getMessage());
        } catch (Exception e) {
            fail("T4: Unexpected exception: " + e.getMessage());
        }
    }

    /** T5: 30-day bounded snapshot loads without exception. */
    private static void test30DayBoundedRange() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadLastNDaysSnapshot(30);
            assertTrue(snap != null, "T5: snapshot not null");
            assertTrue(!snap.hasError(), "T5: no error - " + snap.getLoadError());
            pass("T5: 30-day bounded snapshot loads without errors");
        } catch (AssertionError e) {
            fail("T5: 30-day range: " + e.getMessage());
        } catch (Exception e) {
            fail("T5: Unexpected exception: " + e.getMessage());
        }
    }

    /** T6: Future date range (year 2099) returns zero counts, not an exception. */
    private static void testFutureDateRangeReturnsZeros() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            LocalDate from = LocalDate.of(2099, 1, 1);
            LocalDate to   = LocalDate.of(2099, 12, 31);
            AdminDashboardSnapshot snap = svc.loadSnapshot(from, to);
            assertTrue(snap != null, "T6: snapshot not null");
            assertTrue(!snap.hasError(), "T6: no error - " + snap.getLoadError());
            assertTrue(snap.getOverviewKPI().getTotalStudents() == 0,
                    "T6: totalStudents must be 0 for future range");
            assertTrue(snap.getJobKPI().getTotalJobs() == 0,
                    "T6: totalJobs must be 0 for future range");
            assertTrue(snap.getApplicationKPI().getTotalApplications() == 0,
                    "T6: totalApplications must be 0 for future range");
            assertTrue(snap.getStudentParticipation().getTotalResumeAnalyses() == 0,
                    "T6: totalResumeAnalyses must be 0 for future range");
            assertTrue(snap.getStudentParticipation().getAvgResumeScore() == null,
                    "T6: avgResumeScore must be null for future range");
            pass("T6: Future date range (2099) returns clean zeros without errors");
        } catch (AssertionError e) {
            fail("T6: Future range: " + e.getMessage());
        } catch (Exception e) {
            fail("T6: Unexpected exception: " + e.getMessage());
        }
    }

    /** T7: Status distribution contains all 4 statuses AND job type stats contains all 3 types. */
    private static void testAllStatusesAndJobTypesPresent() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T7: no load error");

            List<AdminStatusDistribution> dist = snap.getStatusDistribution();
            assertTrue(dist != null && dist.size() == Application.Status.values().length,
                    "T7: status distribution must have 4 rows, got " + (dist == null ? -1 : dist.size()));
            for (Application.Status s : Application.Status.values()) {
                boolean found = dist.stream().anyMatch(d -> s.name().equals(d.getStatus()));
                assertTrue(found, "T7: status '" + s.name() + "' missing from distribution");
            }

            List<AdminJobTypeStat> jt = snap.getJobTypeStats();
            assertTrue(jt != null && jt.size() == Job.JobType.values().length,
                    "T7: jobTypeStats must have 3 rows, got " + (jt == null ? -1 : jt.size()));
            for (Job.JobType t : Job.JobType.values()) {
                boolean found = jt.stream().anyMatch(r -> t.name().equals(r.getJobType()));
                assertTrue(found, "T7: jobType '" + t.name() + "' missing from jobTypeStats");
            }

            pass("T7: All 4 Application.Status & 3 Job.JobType buckets present (zero-fill confirmed)");
        } catch (AssertionError e) {
            fail("T7: Distribution / JobType check: " + e.getMessage());
        } catch (Exception e) {
            fail("T7: Unexpected exception: " + e.getMessage());
        }
    }

    /** T8: uniqueSelectedStudents <= selectedApplications and uniqueShortlistedStudents <= shortlistedApplications. */
    private static void testUniqueSelectedConstraint() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T8: no load error");
            AdminApplicationKPI kpi = snap.getApplicationKPI();
            assertTrue(kpi.getUniqueSelectedStudents() <= kpi.getSelectedApplications(),
                    "T8: uniqueSelectedStudents must be <= selectedApplications");
            assertTrue(kpi.getUniqueShortlistedStudents() <= kpi.getShortlistedApplications(),
                    "T8: uniqueShortlistedStudents must be <= shortlistedApplications");
            assertTrue(kpi.getUniqueApplyingStudents() <= kpi.getTotalApplications(),
                    "T8: uniqueApplyingStudents must be <= totalApplications");
            pass("T8: Distinct student counts <= total application counts");
        } catch (AssertionError e) {
            fail("T8: Unique student constraint: " + e.getMessage());
        } catch (Exception e) {
            fail("T8: Unexpected exception: " + e.getMessage());
        }
    }

    /** T9: avgResumeScore is null when totalResumeAnalyses == 0, or finite when > 0. */
    private static void testAvgScoreSafeWhenNoAnalysesDb() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T9: no load error");
            AdminStudentParticipation sp = snap.getStudentParticipation();
            if (sp.getTotalResumeAnalyses() == 0) {
                assertTrue(sp.getAvgResumeScore() == null,
                        "T9: avgResumeScore must be null when totalResumeAnalyses == 0");
            } else {
                Double avg = sp.getAvgResumeScore();
                assertTrue(avg == null || Double.isFinite(avg),
                        "T9: avgResumeScore must be finite, got " + avg);
            }
            pass("T9: Live DB avgResumeScore is null/finite safely (" + sp.getAvgResumeScore() + ")");
        } catch (AssertionError e) {
            fail("T9: avgResumeScore safety (DB): " + e.getMessage());
        } catch (Exception e) {
            fail("T9: Unexpected exception: " + e.getMessage());
        }
    }

    /** T10: Recruiter count in snapshot and engagement rows match direct DB count. */
    private static void testRecruiterCountMatchesDb() {
        try {
            int dbCount = queryCount("SELECT COUNT(*) FROM users WHERE role = 'RECRUITER'");

            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T10: no load error");

            int snapshotCount = snap.getOverviewKPI().getTotalRecruiters();
            int engagementRows = snap.getRecruiterEngagement().size();
            assertTrue(snapshotCount == dbCount,
                    "T10: overviewKPI.totalRecruiters (" + snapshotCount + ") != DB (" + dbCount + ")");
            assertTrue(engagementRows == dbCount,
                    "T10: recruiterEngagement.size() (" + engagementRows + ") != DB (" + dbCount + ")");

            pass("T10: Recruiter count matches DB users table (" + dbCount + ") even without recruiter_profiles");
        } catch (AssertionError e) {
            fail("T10: Recruiter count: " + e.getMessage());
        } catch (Exception e) {
            fail("T10: Unexpected exception: " + e.getMessage());
        }
    }

    /** T11: Job KPI invariants hold across active, open, expired, closed, draft. */
    private static void testJobKPIConstraints() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T11: no load error");
            AdminJobKPI kpi = snap.getJobKPI();
            assertTrue(kpi.getExpiredActiveJobs() >= 0,
                    "T11: expiredActiveJobs must be >= 0, got " + kpi.getExpiredActiveJobs());
            assertTrue(kpi.getOpenAcceptingJobs() <= kpi.getActiveStatusJobs(),
                    "T11: openAcceptingJobs must be <= activeStatusJobs");
            assertTrue(kpi.getExpiredActiveJobs() + kpi.getOpenAcceptingJobs() == kpi.getActiveStatusJobs(),
                    "T11: expiredActiveJobs + openAcceptingJobs must equal activeStatusJobs");
            assertTrue(kpi.getActiveStatusJobs() + kpi.getClosedJobs() + kpi.getDraftJobs() == kpi.getTotalJobs(),
                    "T11: active + closed + draft must equal totalJobs");
            pass("T11: Job KPI invariants hold (open=" + kpi.getOpenAcceptingJobs()
                    + ", expiredActive=" + kpi.getExpiredActiveJobs()
                    + ", active=" + kpi.getActiveStatusJobs()
                    + ", total=" + kpi.getTotalJobs() + ")");
        } catch (AssertionError e) {
            fail("T11: Job KPI constraints: " + e.getMessage());
        } catch (Exception e) {
            fail("T11: Unexpected exception: " + e.getMessage());
        }
    }

    /** T12: Cross-check Q7 Student Participation against raw SQL counts (verifies zero row multiplication). */
    private static void testStudentParticipationConsistency() {
        try {
            int dbStudents    = queryCount("SELECT COUNT(*) FROM users WHERE role = 'STUDENT'");
            int dbAnalyses    = queryCount("SELECT COUNT(*) FROM resume_analyses");
            int dbJobSpecific = queryCount("SELECT COUNT(*) FROM resume_analyses WHERE job_id IS NOT NULL");
            int dbGeneral     = queryCount("SELECT COUNT(*) FROM resume_analyses WHERE job_id IS NULL");

            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T12: no load error");

            AdminStudentParticipation sp = snap.getStudentParticipation();
            assertTrue(sp.getTotalStudents() == dbStudents,
                    "T12: studentParticipation.totalStudents (" + sp.getTotalStudents()
                    + ") != DB (" + dbStudents + ")");
            assertTrue(snap.getOverviewKPI().getTotalStudents() == dbStudents,
                    "T12: overviewKPI.totalStudents != DB (" + dbStudents + ")");
            assertTrue(sp.getTotalResumeAnalyses() == dbAnalyses,
                    "T12: totalResumeAnalyses (" + sp.getTotalResumeAnalyses()
                    + ") != DB (" + dbAnalyses + ") - row multiplication check");
            assertTrue(sp.getJobSpecificAnalyses() == dbJobSpecific && sp.getGeneralAnalyses() == dbGeneral,
                    "T12: jobSpecific/general split mismatch");
            assertTrue(sp.getStudentsWhoApplied() <= sp.getTotalStudents(),
                    "T12: studentsWhoApplied must be <= totalStudents");
            assertTrue(sp.getStudentsWithAiAnalysis() <= sp.getTotalStudents(),
                    "T12: studentsWithAiAnalysis must be <= totalStudents");

            pass("T12: StudentParticipation matches raw SQL counts (no row multiplication)");
        } catch (AssertionError e) {
            fail("T12: StudentParticipation consistency: " + e.getMessage());
        } catch (Exception e) {
            fail("T12: Unexpected exception: " + e.getMessage());
        }
    }

    /** T13: No NaN or Infinity in numeric fields of the loaded snapshot. */
    private static void testNoNaNInSnapshot() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T13: no load error");

            AdminOverviewKPI o = snap.getOverviewKPI();
            assertFinite(o.getStudentProfileCompletionRate(), "T13: studentProfileCompletionRate");
            assertFinite(o.getStudentResumeUploadRate(), "T13: studentResumeUploadRate");
            assertFinite(o.getRecruiterProfileCompletionRate(), "T13: recruiterProfileCompletionRate");

            AdminApplicationKPI a = snap.getApplicationKPI();
            assertFinite(a.getSelectionRate(), "T13: selectionRate");
            assertFinite(a.getShortlistRate(), "T13: shortlistRate");
            assertFinite(a.getUniqueHireRate(), "T13: uniqueHireRate");

            AdminStudentParticipation sp = snap.getStudentParticipation();
            if (sp.getAvgResumeScore() != null) {
                assertFinite(sp.getAvgResumeScore(), "T13: avgResumeScore");
            }

            for (AdminStatusDistribution sd : snap.getStatusDistribution()) {
                assertFinite(sd.getPercentage(), "T13: status pct for " + sd.getStatus());
            }

            pass("T13: No NaN or Infinity found in any numeric snapshot field");
        } catch (AssertionError e) {
            fail("T13: NaN check: " + e.getMessage());
        } catch (Exception e) {
            fail("T13: Unexpected exception: " + e.getMessage());
        }
    }

    /** T14: Verifies Q4 percentages, Q6 topJobs, Q8 recruiter fallback, Q9 activity & Q10 trend ordering. */
    private static void testCollectionsAndSortOrder() {
        try {
            AdminAnalyticsService svc = new AdminAnalyticsService();
            AdminDashboardSnapshot snap = svc.loadAllTimeSnapshot();
            assertTrue(!snap.hasError(), "T14: no load error");

            // Q4 percentages sum check
            int totalApps = snap.getApplicationKPI().getTotalApplications();
            double pctSum = snap.getStatusDistribution().stream()
                    .mapToDouble(AdminStatusDistribution::getPercentage)
                    .sum();
            if (totalApps > 0) {
                assertTrue(Math.abs(pctSum - 100.0) < 0.01,
                        "T14: status percentages must sum to 100.0, got " + pctSum);
            } else {
                assertTrue(Math.abs(pctSum) < 0.01,
                        "T14: status percentages must sum to 0.0 when no apps");
            }

            // Q6 topJobs match jobs when totalJobs <= 10
            if (snap.getJobKPI().getTotalJobs() <= 10) {
                assertTrue(snap.getTopJobs().size() == snap.getJobKPI().getTotalJobs(),
                        "T14: topJobs.size() mismatch with totalJobs");
            }

            // Q8 recruiterEngagement company fallback is never blank/null
            for (AdminRecruiterEngagement re : snap.getRecruiterEngagement()) {
                assertTrue(re.getRecruiterName() != null && !re.getRecruiterName().isBlank(),
                        "T14: recruiterName must not be blank");
                assertTrue(re.getCompanyName() != null && !re.getCompanyName().isBlank(),
                        "T14: recruiter companyName fallback must not be blank");
            }

            // Q9 recentActivity descending timestamp check
            List<AdminActivityEvent> events = snap.getRecentActivity();
            assertTrue(events.size() >= 1 || snap.getOverviewKPI().getTotalUsers() == 0,
                    "T14: expected activity events when users exist");
            for (int i = 1; i < events.size(); i++) {
                if (events.get(i - 1).getOccurredAt() != null && events.get(i).getOccurredAt() != null) {
                    assertTrue(!events.get(i - 1).getOccurredAt().isBefore(events.get(i).getOccurredAt()),
                            "T14: recentActivity must be sorted DESC by occurredAt");
                }
            }

            // Q10 applicationTrend ascending date check
            List<AdminTrendPoint> trend = snap.getApplicationTrend();
            for (int i = 1; i < trend.size(); i++) {
                if (trend.get(i - 1).getActivityDate() != null && trend.get(i).getActivityDate() != null) {
                    assertTrue(!trend.get(i - 1).getActivityDate().isAfter(trend.get(i).getActivityDate()),
                            "T14: applicationTrend must be sorted ASC by activityDate");
                }
            }

            System.out.println("    [INFO] Live TopJobs=" + snap.getTopJobs().size()
                    + ", Recruiters=" + snap.getRecruiterEngagement()
                    + ", ActivityEvents=" + events.size()
                    + ", TrendPoints=" + trend.size());
            pass("T14: Collections, Q8 company fallback, Q9 DESC & Q10 ASC ordering verified");
        } catch (AssertionError e) {
            fail("T14: Collections & ordering: " + e.getMessage());
        } catch (Exception e) {
            fail("T14: Unexpected exception: " + e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static int queryCount(String sql) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void skipDbTests() {
        String[] dbTests = {
            "T4: All-time snapshot loads",
            "T5: 30-day bounded range",
            "T6: Future date range returns zeros",
            "T7: All 4 statuses & 3 job types present",
            "T8: Unique selected constraint",
            "T9: avgResumeScore safe (DB)",
            "T10: Recruiter count matches DB",
            "T11: Job KPI constraints",
            "T12: StudentParticipation matches DB",
            "T13: No NaN in snapshot",
            "T14: Collections & sort order"
        };
        for (String t : dbTests) {
            skip(t);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new AssertionError(label + " is not finite: " + value);
        }
    }

    private static void pass(String message) {
        testsPassed++;
        System.out.println("  [PASS] " + message);
    }

    private static void fail(String message) {
        testsFailed++;
        System.err.println("  [FAIL] " + message);
    }

    private static void skip(String message) {
        testsSkipped++;
        System.out.println("  [SKIP] " + message + " (DB unavailable)");
    }

    private static void printSummary() {
        System.out.println();
        System.out.println("=================================================================");
        System.out.printf("  VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                testsPassed, testsFailed, testsSkipped);
        System.out.println("=================================================================");
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
}
