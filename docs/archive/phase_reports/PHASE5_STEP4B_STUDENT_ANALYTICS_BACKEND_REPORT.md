# HireHub Phase 5 – Step 4B: Student Security Hardening & Analytics Backend Report

**Scope:** Student security hardening across 5 student controllers, atomic ownership methods on DAOs, positive-ID service guards, canonical skill normalization promotion, non-expired active job lookup, and a read-only, strictly student-isolated analytics backend (`model`, `dao`, `service`, `util`) for the upcoming Student Intelligence Dashboard (Step 4C).  
**Constraints Respected:**
- Zero modifications to Admin or Recruiter controllers, FXML, or CSS files.
- Zero modifications to authentication/session infrastructure (`SessionManager`, `NavigationManager`, `AuthService`, `LoginController`, `RegisterController`, `DatabaseConnection`).
- Zero modifications to `pom.xml`, `module-info.java`, or `AIService`.
- Zero modifications to Phase 5 Step 3A Recruiter security hardening (`*ForRecruiter` methods remain completely untouched).
- Zero Step 4C UI modifications initiated early (`student_dashboard.fxml` and other FXML files remain intact).
- Zero database writes outside temporary fixture setup with 100% guaranteed teardown in the verifier's `finally` block.

---

## 1. Files Modified & Created

### Part 1: Security Hardening (Modified Existing Files)
1. `src/main/java/com/hirehub/controller/StudentDashboardController.java`
   - Added fail-closed role guard (`User.Role.STUDENT`) and positive ID check in `initialize()`.
   - Added auth and ID guard in `handleSaveProfile()` with early return and redirect on unauthenticated access.
2. `src/main/java/com/hirehub/controller/JobBoardController.java`
   - Added fail-closed role guard and positive ID check in `initialize()`.
   - Added guards in `handleSearch()`, `handleClear()`, `handleApply()`, and `showDetail()`.
3. `src/main/java/com/hirehub/controller/MyApplicationsController.java`
   - Added fail-closed role guard and positive ID check in `initialize()`.
   - Added guards in `handleSearch()`, `handleRefresh()`, `handleStatusFilter()`, and `loadApplicationsAsync()`.
4. `src/main/java/com/hirehub/controller/ResumeAnalyzerController.java`
   - Added fail-closed role guard and positive ID check in `initialize()`.
   - Added guards across `chooseAndUploadResume()`, `handleDeleteResume()`, `handleSyncProfile()`, `handleGenerateAiReview()`, `handleRetryAiReview()`, `loadActiveResume()`, and `loadHistory()`.
5. `src/main/java/com/hirehub/controller/AIAssistantController.java`
   - Added fail-closed role guard and positive ID check in `initialize()`.
   - Added guards across `handleAnalyzeSkills()`, `handleGenerateQuestions()`, `handlePrepPlan()`, `handleRetry()`, and `loadInitialDataAsync()`.
6. `src/main/java/com/hirehub/dao/ResumeDAO.java`
   - Added atomic ownership query: `findByIdForStudent(int id, int studentId)` (`WHERE id = ? AND student_id = ?`).
   - Added atomic ownership mutation: `deleteForStudent(int id, int studentId)` (`DELETE FROM resumes WHERE id = ? AND student_id = ?`).
   - Preserved all existing unscoped methods (`findById`, `delete`) for Recruiter/Admin interoperability.
7. `src/main/java/com/hirehub/dao/ResumeAnalysisDAO.java`
   - Added atomic ownership query: `findByIdForStudent(int id, int studentId)` (`WHERE ra.id = ? AND ra.student_id = ?`).
   - Added atomic ownership mutation: `deleteForStudent(int id, int studentId)` (`DELETE FROM resume_analyses WHERE id = ? AND student_id = ?`).
   - Preserved all existing unscoped methods.
8. `src/main/java/com/hirehub/dao/JobDAO.java`
   - Added `findOpenActiveJobs()`: `WHERE status = 'ACTIVE' AND (deadline IS NULL OR deadline >= CURDATE()) ORDER BY created_at DESC`.
   - Preserved existing `searchActive(...)` and all recruiter-scoped queries.
9. `src/main/java/com/hirehub/service/StudentService.java`
   - Added input guards: `getProfile(int userId)` and `saveProfile(int userId, ...)` reject non-positive user IDs (`userId <= 0`) safely with empty `Optional` / failed `ServiceResult`.
10. `src/main/java/com/hirehub/service/ApplicationService.java`
    - Hardened student-facing methods only: `apply(studentId, jobId, coverLetter)`, `hasApplied(studentId, jobId)`, and `getStudentApplications(studentId)` validate `studentId <= 0 || jobId <= 0`.
    - Zero modifications to recruiter-facing methods (`*ForRecruiter`).
11. `src/main/java/com/hirehub/service/ResumeService.java`
    - Hardened `uploadAndProcessResume`, `getActiveResume`, `getAllResumes`, and `syncWithStudentProfile` against non-positive student IDs.
    - Routed `deleteResume(int resumeId, int studentId)` through atomic `resumeDAO.deleteForStudent(resumeId, studentId)`.
12. `src/main/java/com/hirehub/service/ResumeAnalysisService.java`
    - Promoted `canonicalSkill(String skill)` from `private` to `public static` for reuse across analytics and recommendation engines.
    - Added non-positive student ID guards to `getStudentAnalysisHistory` and `saveAnalysis`.

---

### Part 2: Student Analytics Backend (New Files Created)

#### 9 Typed Analytics DTOs (`src/main/java/com/hirehub/model/`)
1. `StudentOverviewKPI.java` — Top-level metrics: application status counters (`totalApplications`, `pendingCount`, `shortlistedCount`, `rejectedCount`, `selectedCount`), `openActiveJobs` count, profile completion signals (`hasProfile`, `profileComplete`, `profileCompletionScore` [0–6], `missingProfileFields`), resume presence (`hasActiveResume`, `hasHistoricalResumes`), and nullable latest resume signals (`latestResumeScore` [null when not analyzed, NOT zero], `latestConfidence`).
2. `StudentStatusDistribution.java` — Zero-filled status distribution with safe percentage computation (`safePct`) for all 4 canonical `Application.Status` states (`PENDING`, `SHORTLISTED`, `REJECTED`, `SELECTED`), strictly guarding against division-by-zero or `NaN`/`Infinity`.
3. `StudentApplicationRow.java` — Application view model (`applicationId`, `jobId`, `jobTitle`, `companyName`, `jobType`, `location`, `status`, `appliedAt`, `latestUpdateAt`, `jobStatus`, `jobDeadline`, `deadlineExpired`), excluding sensitive internal columns (no cover letters, no email/password hashes, no physical filesystem paths).
4. `StudentResumeReadiness.java` — Resume signals and quality indicators (`hasActiveResume`, `hasHistoricalResumes`, `historicalResumeCount`, active resume metadata, parsed signal counts for skills/experiences/projects/certifications/metrics, live deterministic rubric breakdown [0-100 overall, skills, experience, education, completeness, certifications, evaluation confidence, unevaluated criteria], persisted analysis snapshot, and mandatory non-prediction disclaimer: `"Deterministic resume quality indicator — not a hiring probability"`).
5. `StudentSkillInsight.java` — Normalized skill inventory: `profileSkills`, `resumeSkills`, deduplicated union `combinedSkills`, `persistedMissingSkills` from latest analysis, and `inDemandMissingSkills` across all open active jobs.
6. `StudentJobRecommendation.java` — Skill-matched job recommendation model (`jobId`, `title`, `companyName`, `jobType`, `location`, `deadline`, `requiredSkills`, `matchScore` [0-100 or -1 for unranked], `matchingSkills`, `missingSkills`, `matchConfidence`, `matchExplanation`, `alreadyApplied`).
7. `StudentTrendPoint.java` — Chronological application timeline data point (`date`, `periodLabel`, `applicationsCount`).
8. `StudentActivityEvent.java` — Real-timestamp student activity event (`APPLICATION_SUBMITTED`, `APPLICATION_STATUS_UPDATED`, `RESUME_UPLOADED`, `RESUME_ANALYSIS_SAVED`) carrying title, detail, timestamp, and entity references.
9. `StudentDashboardSnapshot.java` — Master container holding all student analytics slices, date bounds, timestamp, and safe error capture.

#### DAO & Service (`src/main/java/com/hirehub/dao/` & `service/`)
1. `StudentAnalyticsDAO.java`
   - Read-only queries strictly scoped with `WHERE student_id = ?` or `WHERE a.student_id = ?`.
   - Full MySQL 8.0 `ONLY_FULL_GROUP_BY` compliance.
   - Methods: `countApplicationsByStatus`, `countOpenActiveJobs`, `getStudentProfile`, `getActiveResume`, `countHistoricalResumes`, `getLatestResumeAnalysis`, `getApplicationRows`, `getApplicationTrend`, `getAppliedJobIds`, `getRecentActivity`, `getRecentAnalyses`.
2. `StudentAnalyticsService.java`
   - Input validation: `validateStudentId(int)`, `validateDateRange(LocalDate, LocalDate)`.
   - Session authorization: `validateSessionAccess(int studentId)` permits student only to access their own ID, while permitting Admin to inspect any student.
   - Orchestrates profile completeness scoring (0 to 6 rubric), resume readiness calculation using `ResumeAnalysisService.calculateDeterministicScore`, skill aggregation with canonical alias mapping, and skill-matched job recommendation ranking with `alreadyApplied` detection.
   - Comprehensive exception handling: captures SQLExceptions gracefully into `snapshot.setErrorMessage(...)` without crashing JavaFX threads.

#### Verification Harness (`src/main/java/com/hirehub/util/`)
1. `Phase5Step4BStudentAnalyticsVerifier.java`
   - Runs offline tests (input validation, service security guards, canonical skill normalization, DTO zero-division safety, session access control).
   - Runs live database tests (atomic ownership DAO methods, non-expired job queries, data isolation between students, profile scoring, resume readiness, job recommendations, and 100% database cleanup verification).

---

## 2. Database Schema & Query Architecture

All queries conform to MySQL 8 `ONLY_FULL_GROUP_BY` and operate without requiring any database schema alterations:

| Query Focus | Method in `StudentAnalyticsDAO` | SQL Filter / Strategy | Data Integrity & Aggregation Strategy |
| :--- | :--- | :--- | :--- |
| **Status Distribution** | `countApplicationsByStatus` | `WHERE student_id = ?` (+ optional `applied_at` bounds) | `SELECT status, COUNT(*) ... GROUP BY status`. Service zero-fills missing statuses and computes safe percentages. |
| **Active Open Jobs** | `countOpenActiveJobs` | `WHERE status = 'ACTIVE' AND (deadline IS NULL OR deadline >= CURDATE())` | Single scalar count of open opportunities currently accepting applications. |
| **Student Profile** | `getStudentProfile` | `WHERE user_id = ?` | Reads profile attributes for completeness evaluation (phone, college, degree, graduation_year, skills, resume_path). |
| **Active Resume** | `getActiveResume` | `WHERE student_id = ? AND is_active = 1` | Fetches active resume row (metadata, `parsed_json`, file size) without exposing filesystem paths to UI models. |
| **Historical Resumes** | `countHistoricalResumes` | `WHERE student_id = ?` | Returns total lifetime count of resumes uploaded by student. |
| **Latest Analysis** | `getLatestResumeAnalysis` | `WHERE student_id = ? ORDER BY created_at DESC, id DESC LIMIT 1` | Scoped to student ID, returns latest persisted scores and skill gaps. |
| **Application Rows** | `getApplicationRows` | `WHERE a.student_id = ?` | `JOIN jobs j ON a.job_id = j.id`. Derives `deadlineExpired` (`j.deadline < CURDATE()`). Excludes sensitive fields. |
| **Trend Timeline** | `getApplicationTrend` | `WHERE a.student_id = ?` | `GROUP BY DATE(applied_at) ORDER BY date ASC`. Zero-fills chronological points across requested range. |
| **Applied Job IDs** | `getAppliedJobIds` | `WHERE student_id = ?` | Returns `Set<Integer>` of all job IDs the student has ever applied to. |
| **Activity Timeline** | `getRecentActivity` | 4-branch `UNION ALL` with `WHERE student_id = ?` | Unifies application submission, status change (`updated_at > applied_at`), resume upload, and resume analysis with real DB timestamps. |

---

## 3. Verification & Regression Results

The entire project was built and verified against the live MySQL database (`hirehub_db` at `127.0.0.1:3306`) with zero regressions.

### Summary Table

| Verifier / Command | Mode | Result | Notes |
| :--- | :--- | :--- | :--- |
| `powershell build.ps1` | Sandbox | **BUILD SUCCESS** | 102 Java sources compiled cleanly (JDK 26 `javac`). |
| `Phase5Step4BStudentAnalyticsVerifier` | Live MySQL | **97 PASSED, 0 FAILED, 0 SKIPPED** | Comprehensive offline + live suite; verified 100% database restoration. |
| `AdminLoginVerifier` | Live MySQL | **6 PASSED, 0 FAILED** | Admin authentication and role routing regression verified. |
| `Phase5AnalyticsVerifier` | Live MySQL | **14 PASSED, 0 FAILED, 0 SKIPPED** | Step 1 Admin analytics backend regression verified. |
| `Phase5Step3ASecurityVerifier` | Live MySQL | **36 PASSED, 0 FAILED, 0 SKIPPED** | Step 3A Recruiter security hardening regression verified. |
| `Phase5RecruiterAnalyticsVerifier` | Live MySQL | **50 PASSED, 0 FAILED, 0 SKIPPED** | Step 3B Recruiter analytics backend regression verified. |
| `RecruiterApplicantVerifier` | Live MySQL | **15 PASSED, 0 FAILED** | Recruiter applicant management regression verified. |

### Database Restoration Proof
Before and after test execution, database row counts were strictly verified:
- `users`: 3 baseline == 3 final
- `jobs`: 1 baseline == 1 final
- `applications`: 1 baseline == 1 final
- `resumes`: 3 baseline == 3 final
- `resume_analyses`: 1 baseline == 1 final

Zero database contamination or dangling fixture rows occurred.

---

## 4. Key Implementation Nuances & Alignment with Step 4C

1. **Nullable Metrics Discipline:**
   - `latestResumeScore` in `StudentOverviewKPI` is typed as `Integer` and remains `null` when the student has never run an analysis. It is never coerced to `0`, preventing false representation of student performance.
2. **Honest Labeling & Disclaimers:**
   - `StudentResumeReadiness.getDisclaimerText()` enforces `"Deterministic resume quality indicator — not a hiring probability"`.
   - Live rubric scores indicate syntactic completeness and resume keyword presence, not predictive placement guarantees.
3. **Skill Alias Unification:**
   - `ResumeAnalysisService.canonicalSkill(...)` is now `public static`, ensuring both `StudentAnalyticsService` and `ResumeAnalyzerController` normalize aliases identically (`js` -> `JavaScript`, `py` -> `Python`, `k8s` -> `Kubernetes`, `aws` -> `AWS`, `gcp` -> `GCP`, etc.).
4. **Deterministic Recommendations:**
   - Job recommendations check student's profile + active resume skills against `Job.getRequiredSkills()`. If active resume parsed data is available, exact rubric compatibility score is generated; if only profile skills exist, required-skill overlap ratio is computed; if required skills are blank, the job is cleanly marked unranked. Applied jobs are flagged via `isAlreadyApplied()`.
5. **Ready for Step 4C UI Integration:**
   - All models, services, and DAOs are in place for the Student Intelligence Dashboard UI implementation in Step 4C without needing further backend changes.

