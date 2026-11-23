# HireHub Phase 5 – Step 3B: Recruiter Analytics Backend Report

**Scope:** Read-only, strictly recruiter-isolated analytics backend (`model`, `dao`, `service`, `util`) for the upcoming Recruiter Intelligence Dashboard (Step 3C).  
**Constraints respected:** Zero modifications to existing controllers, FXML, CSS, authentication/session classes, Step 3A security fixes, Admin analytics backend, database schema, or build dependencies. Zero database writes outside temporary fixture cleanup in the verifier.

---

## 1. Files Created

### Typed Analytics DTOs (`src/main/java/com/hirehub/model/`)
1. `RecruiterDashboardSnapshot.java` — Top-level container carrying `recruiterId`, range metadata, `TrendGranularity`, `refreshedAt`, `loadError`, and safe non-null sub-objects/lists.
2. `RecruiterOverviewKPI.java` — Recruiter identity & company profile completeness (`recruiterAccountPresent`, `profileCompleted`, `profileCompletenessRate`, fallback `companyName`), full lifetime job portfolio breakdown (`totalJobs`, `activeStatusJobs`, `openAcceptingJobs`, `expiredActiveJobs`, `closingWithin7DaysJobs`, `draftJobs`, `closedJobs`, `zeroApplicantOpenJobs`), lifetime application counters, and `[from, to)` period-scoped job/application metrics.
3. `RecruiterPipelineKPI.java` — Period application funnel counts (`totalApplications`, `pendingApplications`, `shortlistedApplications`, `selectedApplications`, `rejectedApplications`, `reviewedApplications`), distinct applicant counts per status, `jobsWithApplications`, and finite safe conversion rates (`reviewCompletionRate`, `shortlistRate`, `selectionRate`, `rejectionRate`, `advancementRate`, `uniqueHireRate`).
4. `RecruiterStatusDistribution.java` — Canonical status row (`status`, `applicationCount`, `uniqueApplicants`, `percentage`).
5. `RecruiterJobPerformance.java` — Per-job funnel performance (`jobId`, `title`, `companyName`, `jobType`, `location`, `deadline`, `status`, `expiredActive`, `createdAt`, `lifetimeApplications`, `lifetimePendingCount`, `lastAppliedAt`, `periodApplications`, `pendingCount`, `shortlistedCount`, `selectedCount`, `rejectedCount`, `shortlistRate`, `selectionRate`).
6. `RecruiterJobTypeStat.java` — Per-`Job.JobType` portfolio and period application counts (`totalJobs`, `activeStatusJobs`, `openAcceptingJobs`, `expiredActiveJobs`, `closedJobs`, `draftJobs`, `periodApplications`, `shortlistedApplications`, `selectedApplications`, `rejectedApplications`).
7. `RecruiterTrendPoint.java` — Time-bucketed trend point with `TrendGranularity` enum (`DAILY`, `WEEKLY`, `MONTHLY`), `periodStart`, `periodEnd`, formatted `periodLabel`, `applicationsCount`, `shortlistedCount`, `selectedCount`, and `rejectedCount`.
8. `RecruiterActivityEvent.java` — Real-timestamp hiring activity event (`JOB_POSTED`, `APPLICATION_RECEIVED`, `APPLICATION_REVIEWED`) scoped strictly to recruiter-owned jobs.
9. `RecruiterPendingApplicant.java` — Lightweight pending review item (`applicationId`, `jobId`, `jobTitle`, `jobType`, `applicantName`, optional academic `college`/`degree`, `appliedAt`, `daysPending`) without email, phone, cover letter, password hash, or file path exposure.
10. `RecruiterApplicantReadiness.java` — Scoped applicant profile/resume/job-AI readiness metrics (`readinessDataAvailable`, `aiAnalysisAvailable`, `unavailabilityReason`, `advisoryDisclaimer`, nullable `avgJobAiScore`, coverage counts & finite percentages).

### DAO, Service & Verification Harness
- `src/main/java/com/hirehub/dao/RecruiterAnalyticsDAO.java`
- `src/main/java/com/hirehub/service/RecruiterAnalyticsService.java`
- `src/main/java/com/hirehub/util/Phase5RecruiterAnalyticsVerifier.java`

---

## 2. Database Schema & Query Architecture

All SQL queries were verified against `database/schema.sql`, `phase2_migration.sql`, `phase3_migration.sql`, `phase4_migration.sql`, `phase4_1_migration.sql`, and live MySQL `8.0.44` (`ONLY_FULL_GROUP_BY` compatible).

| Section | Method in `RecruiterAnalyticsDAO` | Isolation Predicate | Date & Accuracy Strategy |
| :--- | :--- | :--- | :--- |
| Overview KPI (RQ1) | `fetchOverviewKPI` | `u.id = ? AND u.role = 'RECRUITER'` + `j.recruiter_id = ?` | Pre-aggregates `applications` per `job_id` before joining `jobs` to avoid fan-out. Keeps portfolio counts lifetime (`openAcceptingJobs + expiredActiveJobs == activeStatusJobs`) while scoping `jobs_created_in_period` and `period_*_applications` to `[from, to)`. Falls back `companyName` to `jobs.company_name` if `recruiter_profiles` is absent. |
| Pipeline KPI (RQ2) | `fetchPipelineKPI` | `j.recruiter_id = ?` | Single aggregate pass across `applications a JOIN jobs j` scoped by `[from, to)` on `a.applied_at`. |
| Status Distribution (RQ2) | `fetchStatusDistribution` | `j.recruiter_id = ?` | `GROUP BY a.status` ordered canonically; service zero-fills all 4 `Application.Status` values and computes finite percentages. |
| Job Performance (RQ3) | `fetchJobPerformance` | `j2.recruiter_id = ?` + `j.recruiter_id = ?` | Single query joining `jobs j` against a pre-scoped derived table `sa` (`applications` tagged with `in_period`), eliminating N+1 queries, retaining 0-application and older portfolio jobs, and reconciling `SUM(period_applications)` cleanly with pipeline totals. |
| Job Type Stats (RQ5) | `fetchJobTypeStats` | `j2.recruiter_id = ?` + `j.recruiter_id = ?` | Pre-aggregates period application counts per `job_id` before joining `jobs` (`GROUP BY j.job_type`), preventing job-count multiplication; service zero-fills all canonical `Job.JobType` values. |
| Application Trends (RQ4) | `fetchApplicationTrend` | `j.recruiter_id = ?` | Uses `TrendGranularity`: `DATE(a.applied_at)` (`DAILY`, `<= 31d`), `DATE_SUB(DATE(a.applied_at), INTERVAL WEEKDAY(a.applied_at) DAY)` (`WEEKLY`, `32..180d`), or `CAST(DATE_FORMAT(a.applied_at, '%Y-%m-01') AS DATE)` (`MONTHLY`, `> 180d` or unbounded). Service fills missing buckets in ascending chronological order. |
| Hiring Activity (RQ6) | `fetchRecentActivity` | `j.recruiter_id = ?` on all 3 branches | 3-branch `UNION ALL` over real timestamps (`jobs.created_at`, `applications.applied_at`, `applications.updated_at > applications.applied_at`) with per-branch predicate pushdown and `LIMIT`. |
| Pending Queue (RQ7) | `fetchPendingApplicants` | `j.recruiter_id = ?` | Filters `a.status = 'PENDING'`, joins `users` and optional `student_profiles` for minimal triage context only, ordered by `a.applied_at DESC, a.id DESC`. |
| Applicant Readiness (RQ8) | `fetchApplicantReadiness` | `j.recruiter_id = ?` | Joins pre-aggregated `resumes` (`GROUP BY student_id` with active vs any flags) and deduplicated latest job-specific `resume_analyses` (`MAX(id)` where `job_id IS NOT NULL` matched on `(a.student_id, a.job_id)`). General analyses (`job_id IS NULL`) are excluded and `avgJobAiScore` is `null` when unavailable. |

---

## 3. Service Safeguards & Contracts

`RecruiterAnalyticsService` exposes:
- `loadSnapshot(int recruiterId, LocalDate from, LocalDate to)`
- `loadAllTimeSnapshot(int recruiterId)`
- `loadLastNDaysSnapshot(int recruiterId, int days)`

Key guarantees verified by test cases:
- **Strict input validation:** Throws `IllegalArgumentException` on `recruiterId <= 0`, `days <= 0`, `from > to`, or non-positive limits (`limit <= 0`).
- **Nonexistent / wrong-role positive IDs:** A positive ID not belonging to a `RECRUITER` returns a non-error zero-filled snapshot (`recruiterAccountPresent == false`, zero counts, empty lists, `avgJobAiScore == null`) with zero leakage from other recruiters.
- **Numeric stability:** `safePct` and enrichment helpers guarantee every rate lies in `[0.0, 100.0]` and never emit `NaN` or `Infinity`.
- **Resilient error handling:** Database or unexpected runtime errors inside `loadSnapshot` populate `snapshot.getLoadError()` and apply safe zero-filled default collections/KPIs rather than propagating raw `SQLException`s to JavaFX controllers.

---

## 4. Build, Verification & Regression Results

All commands were executed after the final code edit. Database-dependent verifiers ran cleanly against live MySQL `8.0.44` (`hirehub_db`).

| Command / Suite | Environment | Result | Notes |
| :--- | :--- | :--- | :--- |
| `.\build.ps1` | Sandbox | **EXIT 0 (`89` Java sources compiled)** | Zero compilation errors. |
| `Phase5RecruiterAnalyticsVerifier` | Sandbox (offline only) | `31 PASSED, 0 FAILED, 1 SKIPPED` | Live DB suite skipped cleanly due to sandbox loopback block. |
| `Phase5RecruiterAnalyticsVerifier` | Live MySQL | **`50 PASSED, 0 FAILED, 0 SKIPPED`** | 31 offline + 19 live fixture assertions; exact DB row counts verified restored in `finally`. |
| `Phase5Step3ASecurityVerifier` | Live MySQL | **`36 PASSED, 0 FAILED, 0 SKIPPED`** | Verified Step 3A security hardening & baseline row counts (`users=3, jobs=1, applications=1`). |
| `Phase5AnalyticsVerifier` | Live MySQL | **`14 PASSED, 0 FAILED, 0 SKIPPED`** | Admin analytics backend regression intact. |
| `RecruiterApplicantVerifier` | Live MySQL | **`15 passed, 0 failed`** | Existing recruiter applicant management service & DAO intact. |
| `Phase3Verifier` | Sandbox | **`10 PASSED / 0 FAILED`** | Note `T11` skips live Gemini API network check when `GEMINI_API_KEY` is not set. |

---

## 5. Known Schema Constraints & Non-Blocking Notes for Step 3C UI

1. **No status-transition audit log table:** Because `applications` only retains the latest `status` and `updated_at`, intermediate status hops cannot be reconstructed historically; `APPLICATION_REVIEWED` reflects the latest state transition where `updated_at > applied_at`.
2. **Ad-hoc `applications.applied_at` / `updated_at` indexing:** No indexes exist on `applications.applied_at` or `applications.updated_at`; recruiter scoping via `idx_jobs_recruiter` and `idx_app_job` keeps datasets bounded to a single recruiter's jobs. Deferred schema indexing remains optional at larger scale.
3. **Advisory AI disclaimer separation:** `RecruiterApplicantReadiness` enforces a clear `DEFAULT_ADVISORY_DISCLAIMER` string and nullable `avgJobAiScore` so Step 3C UI should display AI compatibility indicators separately from hiring decisions and handle `null` gracefully when no job-specific analyses exist.

