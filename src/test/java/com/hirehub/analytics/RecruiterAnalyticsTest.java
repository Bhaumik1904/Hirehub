package com.hirehub.analytics;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.RecruiterAnalyticsDAO;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;
import com.hirehub.model.RecruiterTrendPoint.TrendGranularity;
import com.hirehub.service.RecruiterAnalyticsService;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Self-contained verification harness for Phase 5 Step 3B (Recruiter Analytics Backend).
 *
 * <p>Runs two verification suites:
 * <ol>
 *   <li><strong>Offline suite:</strong> argument validation, divide-by-zero / NaN protection,
 *       status & job-type zero-filling, trend granularity & chronological bucket zero-filling,
 *       SQL error isolation, and privacy boundary checks.</li>
 *   <li><strong>Live MySQL suite:</strong> skipped gracefully if the database is unreachable;
 *       otherwise creates an isolated multi-recruiter fixture, verifies strict recruiter data
 *       isolation, portfolio vs date-filter semantics, metric reconciliations, trend bucket
 *       summation, AI readiness deduplication/isolation, and restores exact DB state in
 *       {@code finally}.</li>
 * </ol>
 */
public class RecruiterAnalyticsTest {

    private int passed = 0;
    private int failed = 0;
    private int skipped = 0;

    private static final String PREFIX = "p5_step3b_verify_" + System.currentTimeMillis() + "_";

    public static void main(String[] args) {
        RecruiterAnalyticsTest verifier = new RecruiterAnalyticsTest();
        verifier.run();
        if (verifier.failed > 0) {
            System.exit(1);
        }
    }

    public void run() {
        System.out.println("=================================================================");
        System.out.println("HireHub Phase 5 Step 3B - Recruiter Analytics Backend Verifier");
        System.out.println("=================================================================");

        runOfflineSuite();
        runLiveDatabaseSuite();

        System.out.println("-----------------------------------------------------------------");
        System.out.printf("VERIFICATION SUMMARY: %d PASSED, %d FAILED, %d SKIPPED%n",
                passed, failed, skipped);
        System.out.println("=================================================================");
    }

    // =========================================================================
    // Offline Verification Suite
    // =========================================================================

    private void runOfflineSuite() {
        RecruiterAnalyticsService service = new RecruiterAnalyticsService();
        RecruiterAnalyticsDAO dao = new RecruiterAnalyticsDAO();

        // T1: Recruiter ID & limit validation
        assertThrows("Service rejects recruiterId=0", IllegalArgumentException.class,
                () -> service.loadAllTimeSnapshot(0));
        assertThrows("Service rejects negative recruiterId", IllegalArgumentException.class,
                () -> service.loadAllTimeSnapshot(-999));
        assertThrows("DAO rejects non-positive recruiterId", IllegalArgumentException.class,
                () -> dao.fetchOverviewKPI(0, null, null));
        assertThrows("DAO rejects non-positive limit on activity", IllegalArgumentException.class,
                () -> dao.fetchRecentActivity(1, null, null, 0));
        assertThrows("DAO rejects non-positive limit on pending queue", IllegalArgumentException.class,
                () -> dao.fetchPendingApplicants(1, null, null, -5));

        // T2: Date range & days validation
        LocalDate d2 = LocalDate.of(2026, 11, 10);
        LocalDate d1 = LocalDate.of(2026, 11, 1);
        assertThrows("Service rejects from > to", IllegalArgumentException.class,
                () -> service.loadSnapshot(1, d2, d1));
        assertThrows("Service rejects days <= 0", IllegalArgumentException.class,
                () -> service.loadLastNDaysSnapshot(1, 0));

        // Same-day range allowed cleanly
        service.validateRange(d1, d1);
        pass("Same-day date range validation succeeds");

        // T3: Divide-by-zero & NaN/Infinity safety
        RecruiterPipelineKPI zeroPipeline = new RecruiterPipelineKPI();
        service.enrichPipelineKPI(zeroPipeline);
        check("Zero-app pipeline reviewCompletionRate == 0.0 and finite",
                zeroPipeline.getReviewCompletionRate() == 0.0 && Double.isFinite(zeroPipeline.getReviewCompletionRate()));
        check("Zero-app pipeline selectionRate == 0.0 and finite",
                zeroPipeline.getSelectionRate() == 0.0 && Double.isFinite(zeroPipeline.getSelectionRate()));
        check("Zero-app pipeline uniqueHireRate == 0.0 and finite",
                zeroPipeline.getUniqueHireRate() == 0.0 && Double.isFinite(zeroPipeline.getUniqueHireRate()));

        RecruiterApplicantReadiness badReadiness = new RecruiterApplicantReadiness();
        badReadiness.setTotalApplicationsInPeriod(0);
        badReadiness.setUniqueApplicants(0);
        badReadiness.setApplicationsWithJobAiAnalysis(0);
        badReadiness.setAvgJobAiScore(Double.NaN);
        service.enrichApplicantReadiness(badReadiness);
        check("NaN avgJobAiScore sanitized to null when unavailable",
                badReadiness.getAvgJobAiScore() == null && !badReadiness.isAiAnalysisAvailable());
        check("Empty applicant readiness rates finite zero",
                badReadiness.getProfileCompletionRate() == 0.0 && badReadiness.getJobAiCoverageRate() == 0.0);

        check("safePct(0, 0) == 0.0", RecruiterAnalyticsService.safePct(0, 0) == 0.0);
        check("safePct(1, 3) == 33.3", RecruiterAnalyticsService.safePct(1, 3) == 33.3);

        // T4: Zero-fill canonical statuses & job types
        List<RecruiterStatusDistribution> partialStatus = List.of(
                new RecruiterStatusDistribution("SHORTLISTED", 3, 2)
        );
        List<RecruiterStatusDistribution> fullStatus = service.zeroFillStatusDistribution(partialStatus, 3);
        check("Status distribution contains all 4 canonical statuses", fullStatus.size() == 4);
        check("Missing PENDING zero-filled",
                fullStatus.stream().anyMatch(s -> "PENDING".equals(s.getStatus()) && s.getApplicationCount() == 0));
        check("SHORTLISTED percentage == 100.0",
                fullStatus.stream().anyMatch(s -> "SHORTLISTED".equals(s.getStatus()) && s.getPercentage() == 100.0));

        List<RecruiterJobTypeStat> partialTypes = List.of(
                new RecruiterJobTypeStat("INTERNSHIP", 2, 1, 1, 0, 1, 0, 5, 2, 1, 1)
        );
        List<RecruiterJobTypeStat> fullTypes = service.zeroFillJobTypeStats(partialTypes);
        check("Job type distribution zero-fills all Job.JobType values",
                fullTypes.size() == Job.JobType.values().length);
        check("Missing FULL_TIME zero-filled",
                fullTypes.stream().anyMatch(t -> "FULL_TIME".equals(t.getJobType()) && t.getTotalJobs() == 0));

        // T5: Trend granularity & chronological zero-filling
        check("1-day span selects DAILY",
                service.determineTrendGranularity(d1, d1) == TrendGranularity.DAILY);
        check("31-day span selects DAILY",
                service.determineTrendGranularity(d1, d1.plusDays(30)) == TrendGranularity.DAILY);
        check("32-day span selects WEEKLY",
                service.determineTrendGranularity(d1, d1.plusDays(31)) == TrendGranularity.WEEKLY);
        check("180-day span selects WEEKLY",
                service.determineTrendGranularity(d1, d1.plusDays(179)) == TrendGranularity.WEEKLY);
        check("181-day span selects MONTHLY",
                service.determineTrendGranularity(d1, d1.plusDays(180)) == TrendGranularity.MONTHLY);
        check("Null bounds select MONTHLY",
                service.determineTrendGranularity(null, null) == TrendGranularity.MONTHLY);

        List<RecruiterTrendPoint> sparseDaily = List.of(
                new RecruiterTrendPoint(TrendGranularity.DAILY, LocalDate.of(2026, 11, 3), null, null, 4, 1, 0, 1)
        );
        List<RecruiterTrendPoint> filledDaily = service.fillMissingTrendBuckets(
                sparseDaily, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), TrendGranularity.DAILY);
        check("DAILY zero-fill produces 5 chronological points",
                filledDaily.size() == 5
                        && filledDaily.get(0).getPeriodStart().equals(LocalDate.of(2026, 11, 1))
                        && filledDaily.get(2).getApplicationsCount() == 4
                        && filledDaily.get(4).getPeriodStart().equals(LocalDate.of(2026, 11, 5)));

        List<RecruiterTrendPoint> sparseMonthly = List.of(
                new RecruiterTrendPoint(TrendGranularity.MONTHLY, LocalDate.of(2025, 12, 1), null, null, 2, 1, 1, 0),
                new RecruiterTrendPoint(TrendGranularity.MONTHLY, LocalDate.of(2026, 2, 1), null, null, 3, 0, 1, 1)
        );
        List<RecruiterTrendPoint> filledMonthly = service.fillMissingTrendBuckets(
                sparseMonthly, null, null, TrendGranularity.MONTHLY);
        check("MONTHLY zero-fill bridges year boundary Dec->Jan->Feb",
                filledMonthly.size() == 3
                        && filledMonthly.get(1).getPeriodStart().equals(LocalDate.of(2026, 1, 1))
                        && filledMonthly.get(1).getApplicationsCount() == 0);

        List<RecruiterTrendPoint> filledWeekly = service.fillMissingTrendBuckets(
                List.of(new RecruiterTrendPoint(TrendGranularity.WEEKLY, LocalDate.of(2026, 11, 9), null, null, 5, 2, 1, 0)),
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 22), TrendGranularity.WEEKLY);
        check("WEEKLY zero-fill covers 3 Monday weeks cleanly",
                filledWeekly.size() == 3
                        && filledWeekly.get(0).getPeriodStart().equals(LocalDate.of(2026, 11, 2))
                        && filledWeekly.get(1).getApplicationsCount() == 5
                        && filledWeekly.get(2).getPeriodStart().equals(LocalDate.of(2026, 11, 16)));

        // T6: SQL error isolation via failing stub DAO
        RecruiterAnalyticsService failingService = new RecruiterAnalyticsService(new RecruiterAnalyticsDAO() {
            @Override
            public RecruiterOverviewKPI fetchOverviewKPI(int recruiterId, LocalDateTime from, LocalDateTime to)
                    throws SQLException {
                throw new SQLException("Simulated offline DB outage");
            }
        });
        RecruiterDashboardSnapshot errSnap = failingService.loadAllTimeSnapshot(42);
        check("SQL failure sets loadError and provides safe defaults",
                errSnap != null
                        && errSnap.hasError()
                        && errSnap.getOverviewKPI() != null
                        && errSnap.getPipelineKPI() != null
                        && errSnap.getStatusDistribution().size() == 4
                        && errSnap.getJobTypeStats().size() == Job.JobType.values().length
                        && errSnap.getApplicantReadiness() != null
                        && errSnap.getApplicantReadiness().getAvgJobAiScore() == null);

        // T7: Privacy boundary — analytics queue/activity DTOs omit private contact/document fields
        List<String> forbiddenFieldFragments = List.of("email", "phone", "password", "path", "coverletter", "hash");
        boolean pendingHasForbidden = Arrays.stream(RecruiterPendingApplicant.class.getDeclaredFields())
                .map(f -> f.getName().toLowerCase(Locale.ROOT))
                .anyMatch(name -> forbiddenFieldFragments.stream().anyMatch(name::contains));
        boolean activityHasForbidden = Arrays.stream(RecruiterActivityEvent.class.getDeclaredFields())
                .map(f -> f.getName().toLowerCase(Locale.ROOT))
                .anyMatch(name -> forbiddenFieldFragments.stream().anyMatch(name::contains));
        check("RecruiterPendingApplicant & RecruiterActivityEvent omit email/phone/password/path/coverLetter",
                !pendingHasForbidden && !activityHasForbidden);
    }

    // =========================================================================
    // Live Database Verification Suite
    // =========================================================================

    private void runLiveDatabaseSuite() {
        if (!DatabaseConnection.testConnection()) {
            skip("Live DB suite skipped cleanly (MySQL connection unavailable in current environment)");
            return;
        }

        RecruiterAnalyticsService service = new RecruiterAnalyticsService();
        BaselineCounts beforeCounts = captureBaselineCounts();

        List<Integer> tempAnalysisIds = new ArrayList<>();
        List<Integer> tempResumeIds = new ArrayList<>();
        List<Integer> tempAppIds = new ArrayList<>();
        List<Integer> tempJobIds = new ArrayList<>();
        List<Integer> tempUserIds = new ArrayList<>();

        try {
            // L1: Positive nonexistent recruiter ID returns clean empty snapshot (no error, zero counts)
            int nonExistentId = findMaxUserId() + 50000;
            RecruiterDashboardSnapshot missingSnap = service.loadAllTimeSnapshot(nonExistentId);
            check("Positive nonexistent recruiter returns non-error empty snapshot",
                    !missingSnap.hasError()
                            && !missingSnap.getOverviewKPI().isRecruiterAccountPresent()
                            && missingSnap.getOverviewKPI().getTotalJobs() == 0
                            && missingSnap.getPipelineKPI().getTotalApplications() == 0
                            && missingSnap.getJobPerformance().isEmpty()
                            && missingSnap.getApplicantReadiness().getAvgJobAiScore() == null);

            // L2: Build isolated multi-recruiter fixture
            int recA = insertUser("Recruiter Alpha", PREFIX + "recA@test.local", "RECRUITER");
            int recB = insertUser("Recruiter Beta", PREFIX + "recB@test.local", "RECRUITER");
            int stu1 = insertUser("Student One", PREFIX + "stu1@test.local", "STUDENT");
            int stu2 = insertUser("Student Two", PREFIX + "stu2@test.local", "STUDENT");
            tempUserIds.addAll(List.of(recA, recB, stu1, stu2));

            // Calling service with STUDENT ID must NOT recognize as recruiter
            RecruiterDashboardSnapshot studentIdSnap = service.loadAllTimeSnapshot(stu1);
            check("Non-recruiter user ID yields recruiterAccountPresent == false and zero jobs",
                    !studentIdSnap.hasError()
                            && !studentIdSnap.getOverviewKPI().isRecruiterAccountPresent()
                            && studentIdSnap.getOverviewKPI().getTotalJobs() == 0);

            // Complete company profile for recA; leave recB without recruiter_profiles row
            insertRecruiterProfile(recA, "Alpha Analytics Corp", "https://alpha.example", "Cloud software", "SaaS");
            insertStudentProfile(stu1, "IIT Test", "B.Tech CS", 2027, null);
            insertStudentProfile(stu2, null, null, null, "/tmp/fallback_resume.pdf");

            tempResumeIds.add(insertResume(stu1, "s1_old.pdf", false));
            int res1Active = insertResume(stu1, "s1_active.pdf", true);
            tempResumeIds.add(res1Active);
            tempResumeIds.add(insertResume(stu2, "s2_inactive.pdf", false));

            LocalDate today = LocalDate.now();
            LocalDateTime ts45dAgo = today.minusDays(45).atTime(10, 0);
            LocalDateTime ts40dAgo = today.minusDays(40).atTime(12, 0);
            LocalDateTime ts38dAgo = today.minusDays(38).atTime(15, 0);
            LocalDateTime ts60dAgo = today.minusDays(60).atTime(9, 0);
            LocalDateTime ts10dAgo = today.minusDays(10).atTime(11, 0);
            LocalDateTime ts5dAgo  = today.minusDays(5).atTime(14, 0);
            LocalDateTime ts4dAgo  = today.minusDays(4).atTime(16, 0);
            LocalDateTime ts2dAgo  = today.minusDays(2).atTime(9, 30);
            LocalDateTime ts1dAgo  = today.minusDays(1).atTime(13, 0);

            // Jobs for recA
            int jobA1 = insertJob(recA, PREFIX + "Backend Engineer", "Alpha Analytics Corp",
                    "FULL_TIME", "ACTIVE", today.plusDays(10), ts45dAgo);   // open accepting
            int jobA2 = insertJob(recA, PREFIX + "Data Intern", "Alpha Analytics Corp",
                    "INTERNSHIP", "ACTIVE", today.minusDays(3), ts10dAgo);  // expired active
            int jobA3 = insertJob(recA, PREFIX + "QA Part-Time", "Alpha Analytics Corp",
                    "PART_TIME", "DRAFT", null, ts2dAgo);                  // draft
            int jobA4 = insertJob(recA, PREFIX + "Legacy Role", "Alpha Analytics Corp",
                    "FULL_TIME", "CLOSED", null, ts60dAgo);                // closed
            // Job for recB (no recruiter_profiles row, fallback companyName on job)
            int jobB1 = insertJob(recB, PREFIX + "Beta ML Engineer", "Beta Labs LLC",
                    "FULL_TIME", "ACTIVE", today.plusDays(15), ts5dAgo);
            tempJobIds.addAll(List.of(jobA1, jobA2, jobA3, jobA4, jobB1));

            // Applications for recA (4 total: 2 on jobA1, 2 on jobA2)
            tempAppIds.add(insertApplication(jobA1, stu1, "SELECTED", ts40dAgo, ts38dAgo));     // outside 30d, reviewed
            tempAppIds.add(insertApplication(jobA1, stu2, "SHORTLISTED", ts10dAgo, ts10dAgo));  // inside 30d, updated==applied
            tempAppIds.add(insertApplication(jobA2, stu1, "PENDING", ts2dAgo, ts2dAgo));        // pending
            tempAppIds.add(insertApplication(jobA2, stu2, "REJECTED", ts5dAgo, ts4dAgo));       // inside 30d, reviewed
            // Applications for recB (2 total on jobB1)
            tempAppIds.add(insertApplication(jobB1, stu1, "PENDING", ts1dAgo, ts1dAgo));
            tempAppIds.add(insertApplication(jobB1, stu2, "SELECTED", ts1dAgo, ts1dAgo));

            // Resume analyses
            tempAnalysisIds.add(insertResumeAnalysis(null, null, stu2, 99));        // general (must NOT pollute A or B)
            tempAnalysisIds.add(insertResumeAnalysis(res1Active, jobA1, stu1, 45)); // stale analysis for (jobA1, stu1)
            tempAnalysisIds.add(insertResumeAnalysis(res1Active, jobA1, stu1, 85)); // latest for (jobA1, stu1)
            tempAnalysisIds.add(insertResumeAnalysis(res1Active, jobA2, stu1, 75)); // latest for (jobA2, stu1)

            // --- Verify Recruiter A All-Time Snapshot ---
            RecruiterDashboardSnapshot snapAAll = service.loadAllTimeSnapshot(recA);
            check("Recruiter A All-Time loads without error", !snapAAll.hasError());

            RecruiterOverviewKPI ovA = snapAAll.getOverviewKPI();
            check("Recruiter A profile metadata accurate",
                    ovA.isRecruiterAccountPresent()
                            && ovA.isProfileCompleted()
                            && ovA.getProfileCompletenessRate() == 100.0
                            && "Alpha Analytics Corp".equals(ovA.getCompanyName()));
            check("Recruiter A job portfolio counts isolated (total=4, open=1, expiredActive=1, draft=1, closed=1)",
                    ovA.getTotalJobs() == 4
                            && ovA.getActiveStatusJobs() == 2
                            && ovA.getOpenAcceptingJobs() == 1
                            && ovA.getExpiredActiveJobs() == 1
                            && ovA.getDraftJobs() == 1
                            && ovA.getClosedJobs() == 1
                            && ovA.getLifetimeTotalApplications() == 4
                            && ovA.getLifetimePendingApplications() == 1);

            RecruiterPipelineKPI pipeA = snapAAll.getPipelineKPI();
            check("Recruiter A pipeline totals and rates reconcile",
                    pipeA.getTotalApplications() == 4
                            && pipeA.getPendingApplications() == 1
                            && pipeA.getShortlistedApplications() == 1
                            && pipeA.getSelectedApplications() == 1
                            && pipeA.getRejectedApplications() == 1
                            && pipeA.getReviewedApplications() == 3
                            && pipeA.getUniqueApplicants() == 2
                            && pipeA.getUniqueSelectedApplicants() == 1
                            && pipeA.getJobsWithApplications() == 2
                            && pipeA.getUniqueHireRate() == 50.0);

            int statusSumA = snapAAll.getStatusDistribution().stream()
                    .mapToInt(RecruiterStatusDistribution::getApplicationCount).sum();
            int perfSumA = snapAAll.getJobPerformance().stream()
                    .mapToInt(RecruiterJobPerformance::getPeriodApplications).sum();
            int typeJobSumA = snapAAll.getJobTypeStats().stream()
                    .mapToInt(RecruiterJobTypeStat::getTotalJobs).sum();
            int typeAppSumA = snapAAll.getJobTypeStats().stream()
                    .mapToInt(RecruiterJobTypeStat::getPeriodApplications).sum();
            int trendSumA = snapAAll.getApplicationTrend().stream()
                    .mapToInt(RecruiterTrendPoint::getApplicationsCount).sum();

            check("Recruiter A reconciles: statusSum == perfSum == typeAppSum == trendSum == 4 and typeJobSum == 4",
                    statusSumA == 4 && perfSumA == 4 && typeAppSumA == 4 && trendSumA == 4 && typeJobSumA == 4
                            && snapAAll.getJobPerformance().size() == 4);

            // Check no Beta leak anywhere in jobPerformance, pendingApplicants, or activity
            check("Recruiter A does not leak Recruiter B jobs or applications",
                    snapAAll.getJobPerformance().stream().noneMatch(j -> j.getJobId() == jobB1)
                            && snapAAll.getRecentActivity().stream().noneMatch(e -> e.getJobId() == jobB1)
                            && snapAAll.getPendingApplicants().stream().noneMatch(p -> p.getJobId() == jobB1));

            // Check activity strictly derived from DB timestamps (no review event for stu2+jobA1 where updated==applied)
            boolean hasStu2JobA1Review = snapAAll.getRecentActivity().stream()
                    .anyMatch(e -> "APPLICATION_REVIEWED".equals(e.getEventType())
                            && e.getJobId() == jobA1
                            && "Student Two".equals(e.getSubjectName()));
            long reviewEventCountA = snapAAll.getRecentActivity().stream()
                    .filter(e -> "APPLICATION_REVIEWED".equals(e.getEventType()))
                    .count();
            long jobPostEventCountA = snapAAll.getRecentActivity().stream()
                    .filter(e -> "JOB_POSTED".equals(e.getEventType()))
                    .count();
            long appReceivedEventCountA = snapAAll.getRecentActivity().stream()
                    .filter(e -> "APPLICATION_RECEIVED".equals(e.getEventType()))
                    .count();
            check("Recruiter A emits only real DB activity events (4 posted, 4 applied, 2 reviewed; no unreviewed stu2+jobA1)",
                    !hasStu2JobA1Review
                            && reviewEventCountA == 2
                            && jobPostEventCountA == 4
                            && appReceivedEventCountA == 4);

            // Pending queue contains only (jobA2, stu1)
            check("Recruiter A pending queue contains exactly 1 pending applicant (Student One on Data Intern)",
                    snapAAll.getPendingApplicants().size() == 1
                            && snapAAll.getPendingApplicants().get(0).getJobId() == jobA2
                            && "Student One".equals(snapAAll.getPendingApplicants().get(0).getApplicantName()));

            // Readiness deduplication & general analysis exclusion
            RecruiterApplicantReadiness readyA = snapAAll.getApplicantReadiness();
            check("Recruiter A readiness deduplicates resumes and latest job AI score ((85+75)/2 = 80.0)",
                    readyA.isReadinessDataAvailable()
                            && readyA.isAiAnalysisAvailable()
                            && readyA.getUniqueApplicants() == 2
                            && readyA.getApplicantsWithCompleteProfile() == 1
                            && readyA.getApplicantsWithActiveResume() == 1
                            && readyA.getApplicantsWithAnyResume() == 2
                            && readyA.getApplicationsWithJobAiAnalysis() == 2
                            && Double.valueOf(80.0).equals(readyA.getAvgJobAiScore()));

            // --- Verify Recruiter A Last 30 Days (Portfolio vs Period filter) ---
            RecruiterDashboardSnapshot snapA30 = service.loadLastNDaysSnapshot(recA, 30);
            check("Recruiter A Last 30 Days uses DAILY granularity with 30 points",
                    snapA30.getTrendGranularity() == TrendGranularity.DAILY
                            && snapA30.getApplicationTrend().size() == 30);
            check("Recruiter A Last 30 Days preserves full job portfolio (total=4) while scoping period apps=3 & createdInPeriod=2",
                    snapA30.getOverviewKPI().getTotalJobs() == 4
                            && snapA30.getOverviewKPI().getJobsCreatedInPeriod() == 2
                            && snapA30.getPipelineKPI().getTotalApplications() == 3
                            && snapA30.getJobPerformance().size() == 4);
            RecruiterJobPerformance ja1Perf30 = snapA30.getJobPerformance().stream()
                    .filter(j -> j.getJobId() == jobA1).findFirst().orElse(null);
            check("Recruiter A older jobA1 retains lifetimeApplications=2 while showing periodApplications=1",
                    ja1Perf30 != null
                            && ja1Perf30.getLifetimeApplications() == 2
                            && ja1Perf30.getPeriodApplications() == 1);

            // --- Verify Weekly (60d) & Monthly (200d) Live SQL Buckets ---
            RecruiterDashboardSnapshot snapA60 = service.loadLastNDaysSnapshot(recA, 60);
            int sumA60 = snapA60.getApplicationTrend().stream()
                    .mapToInt(RecruiterTrendPoint::getApplicationsCount).sum();
            check("Recruiter A Last 60 Days uses WEEKLY buckets and trend sums to pipeline total (4)",
                    snapA60.getTrendGranularity() == TrendGranularity.WEEKLY
                            && sumA60 == snapA60.getPipelineKPI().getTotalApplications()
                            && sumA60 == 4);

            RecruiterDashboardSnapshot snapA200 = service.loadLastNDaysSnapshot(recA, 200);
            int sumA200 = snapA200.getApplicationTrend().stream()
                    .mapToInt(RecruiterTrendPoint::getApplicationsCount).sum();
            check("Recruiter A Last 200 Days uses MONTHLY buckets and trend sums to pipeline total (4)",
                    snapA200.getTrendGranularity() == TrendGranularity.MONTHLY
                            && sumA200 == snapA200.getPipelineKPI().getTotalApplications()
                            && sumA200 == 4);

            // --- Verify Future Date Range (0 apps, portfolio preserved) ---
            RecruiterDashboardSnapshot snapAFuture = service.loadSnapshot(
                    recA, today.plusDays(30), today.plusDays(35));
            check("Recruiter A future range yields 0 period apps, 6 zero-filled days, null avgAiScore, portfolio preserved",
                    !snapAFuture.hasError()
                            && snapAFuture.getOverviewKPI().getTotalJobs() == 4
                            && snapAFuture.getPipelineKPI().getTotalApplications() == 0
                            && snapAFuture.getApplicationTrend().size() == 6
                            && snapAFuture.getApplicantReadiness().getAvgJobAiScore() == null
                            && !snapAFuture.getApplicantReadiness().isReadinessDataAvailable()
                            && snapAFuture.getPendingApplicants().isEmpty());

            // --- Verify Recruiter B Isolation + Missing Recruiter Profile + Unavailable AI ---
            RecruiterDashboardSnapshot snapB = service.loadAllTimeSnapshot(recB);
            check("Recruiter B isolated cleanly with fallback companyName, 1 job, 2 apps, and null AI score",
                    !snapB.hasError()
                            && snapB.getOverviewKPI().isRecruiterAccountPresent()
                            && !snapB.getOverviewKPI().isProfileCompleted()
                            && "Beta Labs LLC".equals(snapB.getOverviewKPI().getCompanyName())
                            && snapB.getOverviewKPI().getTotalJobs() == 1
                            && snapB.getPipelineKPI().getTotalApplications() == 2
                            && snapB.getPendingApplicants().size() == 1
                            && !snapB.getApplicantReadiness().isAiAnalysisAvailable()
                            && snapB.getApplicantReadiness().getAvgJobAiScore() == null);

        } catch (Exception e) {
            fail("Unexpected exception in Live DB Suite: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanupFixture(tempAnalysisIds, tempResumeIds, tempAppIds, tempJobIds, tempUserIds);
            BaselineCounts afterCounts = captureBaselineCounts();
            check("Database row counts restored identically after cleanup",
                    beforeCounts.equals(afterCounts));
        }
    }

    // =========================================================================
    // Test Assertions
    // =========================================================================

    private void check(String name, boolean condition) {
        if (condition) {
            pass(name);
        } else {
            fail(name);
        }
    }

    private void pass(String name) {
        passed++;
        System.out.println("  [PASS] " + name);
    }

    private void fail(String name) {
        failed++;
        System.err.println("  [FAIL] " + name);
    }

    private void skip(String reason) {
        skipped++;
        System.out.println("  [SKIP] " + reason);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private void assertThrows(String name, Class<? extends Throwable> expectedType, ThrowingAction action) {
        try {
            action.run();
            fail(name + " (expected " + expectedType.getSimpleName() + ", got nothing)");
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                pass(name);
            } else {
                fail(name + " (expected " + expectedType.getSimpleName() + ", got " + t.getClass().getSimpleName() + ")");
            }
        }
    }

    // =========================================================================
    // Fixture Setup & Cleanup Helpers
    // =========================================================================

    private record BaselineCounts(int users, int recruiterProfiles, int jobs, int applications,
                                  int resumes, int analyses) {}

    private BaselineCounts captureBaselineCounts() {
        return new BaselineCounts(
                countTable("users"),
                countTable("recruiter_profiles"),
                countTable("jobs"),
                countTable("applications"),
                countTable("resumes"),
                countTable("resume_analyses")
        );
    }

    private int countTable(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    private int findMaxUserId() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int insertUser(String fullName, String email, String role) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, "$2a$12$dummyhashforverifier000000000000000000000000000000");
            ps.setString(4, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void insertRecruiterProfile(int userId, String company, String website,
                                        String desc, String industry) throws SQLException {
        String sql = "INSERT INTO recruiter_profiles (user_id, company_name, company_website, company_description, industry) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, company);
            ps.setString(3, website);
            ps.setString(4, desc);
            ps.setString(5, industry);
            ps.executeUpdate();
        }
    }

    private void insertStudentProfile(int userId, String college, String degree,
                                      Integer gradYear, String resumePath) throws SQLException {
        String sql = "INSERT INTO student_profiles (user_id, college, degree, graduation_year, resume_path) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (college == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, college);
            if (degree == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, degree);
            if (gradYear == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, gradYear);
            if (resumePath == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, resumePath);
            ps.executeUpdate();
        }
    }

    private int insertJob(int recruiterId, String title, String company, String jobType,
                          String status, LocalDate deadline, LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO jobs (recruiter_id, title, company_name, description, location, job_type, deadline, status, created_at) "
                + "VALUES (?, ?, ?, 'Fixture job', 'Remote', ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, recruiterId);
            ps.setString(2, title);
            ps.setString(3, company);
            ps.setString(4, jobType);
            if (deadline == null) ps.setNull(5, Types.DATE); else ps.setDate(5, java.sql.Date.valueOf(deadline));
            ps.setString(6, status);
            ps.setTimestamp(7, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private int insertApplication(int jobId, int studentId, String status,
                                  LocalDateTime appliedAt, LocalDateTime updatedAt) throws SQLException {
        String sql = "INSERT INTO applications (job_id, student_id, cover_letter, status, applied_at, updated_at) "
                + "VALUES (?, ?, 'Fixture application', ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, jobId);
            ps.setInt(2, studentId);
            ps.setString(3, status);
            ps.setTimestamp(4, Timestamp.valueOf(appliedAt));
            ps.setTimestamp(5, Timestamp.valueOf(updatedAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private int insertResume(int studentId, String fileName, boolean isActive) throws SQLException {
        String sql = "INSERT INTO resumes (student_id, file_name, stored_path, file_size_bytes, file_hash, is_active) "
                + "VALUES (?, ?, ?, 1024, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, studentId);
            ps.setString(2, fileName);
            ps.setString(3, "/tmp/" + fileName);
            ps.setString(4, PREFIX + fileName);
            ps.setBoolean(5, isActive);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private int insertResumeAnalysis(Integer resumeId, Integer jobId, int studentId, int score)
            throws SQLException {
        String sql = "INSERT INTO resume_analyses (resume_id, job_id, student_id, overall_score) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (resumeId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, resumeId);
            if (jobId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, jobId);
            ps.setInt(3, studentId);
            ps.setInt(4, score);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private void cleanupFixture(List<Integer> analysisIds, List<Integer> resumeIds,
                                List<Integer> appIds, List<Integer> jobIds, List<Integer> userIds) {
        deleteByIds("resume_analyses", analysisIds);
        deleteByIds("resumes", resumeIds);
        deleteByIds("applications", appIds);
        deleteByIds("jobs", jobIds);
        deleteByIds("users", userIds); // cascades student_profiles & recruiter_profiles
    }

    private void deleteByIds(String tableName, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer id : ids) {
                if (id != null && id > 0) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("[Cleanup] Warning deleting from " + tableName + ": " + e.getMessage());
        }
    }
}
