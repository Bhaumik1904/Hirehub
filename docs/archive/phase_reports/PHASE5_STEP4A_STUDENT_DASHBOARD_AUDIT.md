# HireHub Phase 5 — Step 4A: Student Intelligence Dashboard Pre-Implementation Audit & Plan

**Project:** `D:\HireHub`  
**Phase:** Phase 5, Step 4A (Read-Only Audit & Implementation Plan)  
**Date:** 2026-11-12  
**Audit Scope:** Strictly read-only inspection of existing Student UI, authentication/navigation, student/job/application/resume/AI domain layers, Phase 5 Admin & Recruiter analytics patterns, CSS/chart conventions, and live MySQL 8 schema. Zero existing source, FXML, CSS, SQL, or dependency files were modified.

---

## Executive Summary

The existing HireHub student experience consists of five separate JavaFX views centered around a placeholder card menu in `student_dashboard.fxml`:
1. **Student Dashboard & Profile Editor** (`student_dashboard.fxml` / `StudentDashboardController`)
2. **Browse Jobs** (`job_board.fxml` / `JobBoardController`)
3. **My Applications** (`my_applications.fxml` / `MyApplicationsController`)
4. **AI Resume Analyzer** (`resume_analyzer.fxml` / `ResumeAnalyzerController`)
5. **AI Career Assistant** (`ai_assistant.fxml` / `AIAssistantController`)

The application already possesses strong deterministic building blocks—most notably `StudentService`, `ApplicationService`, `JobService`, `ResumeService`, `ResumeParserService`, and `ResumeAnalysisService` (deterministic 100-point rubric, 90+ skill vocabulary with alias normalization, confidence reporting, and persisted `resume_analyses`). However, it lacks a dedicated student-scoped analytics backend, lacks persisted tracking for AI Career Assistant outputs and historical application status transitions, and exhibits **serious student role/session guard and DAO ownership gaps** relative to the security baseline established for Recruiters in Step 3A.

Following the proven Phase 5 pattern (Admin Steps 1–2 and Recruiter Steps 3A–3C), upgrading the Student Portal should proceed in two cleanly separated implementation phases:
- **Step 4B:** Student Security Hardening & Read-Only Analytics Backend (`StudentAnalyticsDAO`, `StudentAnalyticsService`, typed `Student*` DTOs, fail-closed `Role.STUDENT` controller/service guards, atomic `student_id` DAO predicates, and CLI verifier)
- **Step 4C:** Student Intelligence Dashboard UI (light-themed white/navy/light-grey `.stu-*` analytics dashboard replacing the Phase 1 placeholder while preserving all 5 existing student workflows and sidebar navigation)

---

## 1. Existing Student Dashboard Architecture

### 1.1 Student UI Views & Navigation Map

| Screen | FXML File | Controller Class | Navigation Entry Points | Primary Purpose |
|---|---|---|---|---|
| **Student Home & Edit Profile** | `src/main/resources/fxml/student_dashboard.fxml` | `com.hirehub.controller.StudentDashboardController` | Post-login target for `User.Role.STUDENT` via `LoginController` & `RegisterController`; "Dashboard" / "My Profile" sidebar buttons | Welcome header, single Profile Summary card, 5 quick-action navigation cards, and toggled `profilePane` form |
| **Browse Jobs** | `src/main/resources/fxml/job_board.fxml` | `com.hirehub.controller.JobBoardController` | Sidebar `navBrowseJobsBtn` / Quick-action card in `student_dashboard.fxml` | Keyword/location/type search over `ACTIVE` jobs, job details pane, Apply cover-letter flow; Back to Dashboard |
| **My Applications** | `src/main/resources/fxml/my_applications.fxml` | `com.hirehub.controller.MyApplicationsController` | Sidebar `navApplicationsBtn` / Quick-action card in `student_dashboard.fxml` | Table of student's submitted applications, keyword/status client filter, cover letter inspector; Back to Dashboard |
| **AI Resume Analyzer** | `src/main/resources/fxml/resume_analyzer.fxml` | `com.hirehub.controller.ResumeAnalyzerController` | Sidebar `navResumeAnalyzerBtn` / Quick-action card in `student_dashboard.fxml` | PDF upload/history (`resumes`), PDFBox parsing, profile skill sync, deterministic job compatibility scoring, optional Gemini deep analysis + history table |
| **AI Career Assistant** | `src/main/resources/fxml/ai_assistant.fxml` | `com.hirehub.controller.AIAssistantController` | Sidebar `navAIBtn` / Quick-action card in `student_dashboard.fxml` | Target job + profile skills selector, Gemini markdown output for Skill Gap Analysis, Interview Questions, and 4-Week Prep Plan |

### 1.2 Current `student_dashboard.fxml` & `StudentDashboardController` Structure

- **Root & Sidebar:** `BorderPane` (`styleClass="app-root"`) with `#1A2B5E` left sidebar (`minWidth="220"`, `prefWidth="220"`):
  - Brand block: `"HireHub"` / `"Student Portal"`
  - Nav buttons: `navDashboardBtn`, `navProfileBtn`, `navBrowseJobsBtn`, `navApplicationsBtn`, `navResumeAnalyzerBtn`, `navAIBtn`, `navLogoutBtn`
- **Content Area:** Single outer `<ScrollPane fitToWidth="true" styleClass="content-scroll">` switching between:
  - `dashboardPane`: account header (`welcomeLabel`, `memberSinceLabel`), 1 Profile Summary card (`profileCollegeLabel`, `profileDegreeLabel`, `profileGradYearLabel`, `profilePhoneLabel`, `profileSkillsLabel`, `profileCompletionBadge`, `editProfileBtn`), and 5 static quick-action cards (`Complete Profile`, `Browse Jobs`, `My Applications`, `AI Resume Analyzer`, `AI Career Assistant`).
  - `profilePane`: hidden by default (`visible="false" managed="false"`), inline Edit Profile form (`phoneField`, `collegeField`, `degreeField`, `gradYearField`, `skillsArea`, `saveProfileBtn`, `profileLoading`, `profileStatusLabel`).
- **Observed UI / Structural Bugs in Existing Student Screens:**
  1. **Invalid JavaFX Space-Separated `styleClass` Attributes:** `student_dashboard.fxml` declares `styleClass="sidebar-nav-btn sidebar-nav-active"`, `"sidebar-nav-btn sidebar-logout-btn"`, and `"text-input text-area-input"` using spaces rather than comma-delimited CSS class lists (the exact bug previously fixed in Admin and Recruiter FXMLs). Consequently, active-nav highlight and logout styling are ignored unless fixed.
  2. **Missing Sidebar Active-State Updates:** `StudentDashboardController.handleNavDashboard()` and `handleNavProfile()` toggle pane visibility without updating `.sidebar-nav-active` across `navDashboardBtn` and `navProfileBtn`.
  3. **Untracked Ad-Hoc Threads:** `StudentDashboardController` spawns unmanaged `new Thread(task)` instances (`loadProfileAsync()`, `handleSaveProfile()`) without daemon naming, cancellation/generation tokens (`AtomicLong`), or in-flight guards (`AtomicBoolean`).
  4. **Mixed Layout Density:** Compared to `admin_dashboard.fxml` (single `ScrollPane`, top refresh toolbar, date filters, KPI row, charts, tables) and `recruiter_dashboard.fxml` (`225px` sidebar, header status pills, `.rec-*` responsive cards/charts/tables), `student_dashboard.fxml` has zero telemetry, zero charts, zero readiness breakdown, zero job matching, and zero activity timeline.
  5. **`NavigationManager` Preservation:** All student sub-screens currently navigate cleanly via `NavigationManager.navigateTo(node, fxmlPath)` (`Scene.setRoot()`, `MIN_WIDTH = 1200.0`, `MIN_HEIGHT = 750.0`, `/css/style.css`). Sub-screen navigation back buttons return directly to `/fxml/student_dashboard.fxml`.

---

## 2. Existing Reusable Services & DTOs

### 2.1 Reusable Production Classes & Methods

| Component | Key Methods / Fields Available for Reuse | Capabilities & Constraints |
|---|---|---|
| `SessionManager` (`com.hirehub.util`) | `getCurrentUser()`, `isLoggedIn()`, `hasRole(User.Role)`, `logout()` | Thread-safe singleton (`volatile User`). Authoritative session identity source (`user.getId()`, `user.getFullName()`, `user.getCreatedAt()`). |
| `NavigationManager` (`com.hirehub.util`) | `navigateTo(Node, String)`, `navigate(Node, String, Consumer<T>)` | Preserves stage geometry and applies `/css/style.css`. Must remain untouched. |
| `StudentService` / `StudentDAO` | `getProfile(int userId)` -> `Optional<StudentProfile>`; `saveProfile(...)` | Fetches `student_profiles` by `user_id`. Note: `StudentProfile.isComplete()` checks only `college`, `degree`, and `graduationYear > 2000` (does **not** include `skills`, `phone`, or resume). `Student.java` is an unused legacy subclass of `User` containing an unmapped `branch` field—do not use it. |
| `ApplicationService` / `ApplicationDAO` | `getStudentApplications(int studentId)` -> `List<Application>`; `hasApplied(int, int)`; `apply(...)`; `Application.Status` (`PENDING`, `SHORTLISTED`, `REJECTED`, `SELECTED`) | `findByStudentId(int)` joins `jobs` for `jobTitle`, `companyName`, `jobType`, `location`. Does **not** join `jobs.deadline` or `jobs.status`. |
| `JobService` / `JobDAO` | `searchJobs(String keyword, String location, String jobType)`; `getJobById(int id)` | `searchActive` queries `WHERE status = 'ACTIVE'` sorted by `created_at DESC`. **Gap:** does not exclude expired deadlines (`deadline < CURDATE()`), whereas `ApplicationService.apply()` rejects expired jobs. |
| `ResumeService` / `ResumeDAO` | `getActiveResume(int studentId)` -> `Optional<Resume>`; `getAllResumes(int studentId)`; `parseJsonMetadata(Resume resume)` | Stores uploaded PDF metadata, `fileSizeBytes`, `fileHash`, `parsedText`, `parsedJson`, `isActive`, `uploadedAt`, and deserializes `resumes.parsed_json` cleanly into `ParsedResume` without re-reading disk PDFs. Files stored under `data/resumes/`. |
| `ResumeParserService` & `ParsedResume` | `parseResume(File pdfFile)`, `parseExtractedText(String text)`, `validatePdfFile(File)` | Extracts structured `ParsedResume` fields (`skills`, `workExperiences`, `projects`, `certifications`, `links`, `metricsCount`, `college`, `degree`, `graduationYear`) via Apache PDFBox and its internal `KNOWN_SKILLS` vocabulary (~90 skills, currently `private static final`). |
| `ResumeAnalysisService` / `ResumeAnalysisDAO` | `calculateDeterministicScore(ParsedResume, Job)`; `getStudentAnalysisHistory(int studentId, int limit)`; `saveAnalysis(...)`; `ScoringBreakdown`; `ResumeAnalysis` | Deterministic 100-pt rubric: `skillsScore` (40), `experienceScore` (25), `educationScore` (15), `completenessScore` (10), `certificationsScore` (10). Internal alias normalization (`SKILL_ALIASES` / `canonicalSkill(...)`, currently `private`) handles aliases like `js`->`JavaScript`, `reactjs`->`React`, `k8s`->`Kubernetes`. Computes `evaluationConfidence` and `unevaluatedCriteria` in memory. Note: `saveAnalysis()` writes `fullAiText` to `analysis_summary`, skills/experience explanations to `quality_feedback`, and `completenessExplanation` to `improvement_plan`. |
| `AIService` | `isConfigured()`, `isConnectionVerified()`, `analyzeSkillGap(...)`, `generateInterviewQuestions(...)`, `generatePreparationPlan(...)` | Uses Gemini API (`gemini-3.8-flash` + `gemini-3.5-flash` fallback + `CircuitBreaker`). Strictly transient UI calls; **no DB persistence**. |

### 2.2 Comparison Against Phase 5 Analytics Conventions (Admin & Recruiter)

Inspection of `AdminAnalyticsDAO` / `AdminAnalyticsService` (`AdminDashboardSnapshot`, 10 DTOs) and `RecruiterAnalyticsDAO` / `RecruiterAnalyticsService` (`RecruiterDashboardSnapshot`, 10 DTOs) establishes strict architectural conventions for Step 4B:
- Dedicated `StudentAnalyticsDAO` (read-only JDBC `PreparedStatement`s, MySQL 8 `ONLY_FULL_GROUP_BY` compatible, half-open `[from, to)` `LocalDateTime` predicates, zero-filled `Application.Status` maps).
- Dedicated `StudentAnalyticsService` enforcing `SessionManager` `Role.STUDENT` authorization & positive `studentId` validation, returning a unified immutable `StudentDashboardSnapshot`.
- Strongly typed, immutable/read-only DTOs under `com.hirehub.model`:
  - `StudentDashboardSnapshot`
  - `StudentOverviewKPI` (profile field completeness, resume presence, application counts by status, open eligible job counts)
  - `StudentStatusDistribution` (`Application.Status` zero-filled counts + percentages)
  - `StudentApplicationRow` (application metadata + joined `jobs.status`, `jobs.deadline`, `appliedAt`, `updatedAt`, latest status change timestamp)
  - `StudentResumeReadiness` (active vs historical resume metadata, parsed resume signal counts, deterministic rubric breakdown, live `evaluationConfidence` & `unevaluatedCriteria`, latest persisted `ResumeAnalysis` metrics)
  - `StudentSkillInsight` (normalized profile skills vs resume skills vs missing skills from analyses/open jobs)
  - `StudentJobRecommendation` (open `ACTIVE` & non-expired job, `SkillMatchScore` / overlap counts, `matchingSkills`, `missingRequiredSkills`, `alreadyApplied`, `evaluationMethodLabel`)
  - `StudentTrendPoint` (applications over time by period bucket, zero-filled cleanly)
  - `StudentActivityEvent` (real timestamped union of application submissions, latest status transitions, resume uploads, and saved resume analyses)

---

## 3. Actual Database Tables, Columns, Indexes, and Live State

Verified live against MySQL `8.0.44` (`hirehub_db`) with active `sql_mode = ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`:

### 3.1 Verified Tables, Columns, Constraints, and Indexes

1. **`users`** (Live rows: **3** — `1 ADMIN`, `1 STUDENT`, `1 RECRUITER`)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `full_name` (`VARCHAR(100) NOT NULL`), `email` (`VARCHAR(150) NOT NULL`), `password_hash` (`VARCHAR(255) NOT NULL`), `role` (`ENUM('STUDENT','RECRUITER','ADMIN') NOT NULL DEFAULT 'STUDENT'`), `created_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `UNIQUE uq_email(email)`

2. **`student_profiles`** (Live rows: **1**)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `user_id` (`INT NOT NULL`), `phone` (`VARCHAR(20)`), `college` (`VARCHAR(150)`), `degree` (`VARCHAR(100)`), `graduation_year` (`INT`), `skills` (`TEXT`), `resume_path` (`VARCHAR(500)`), `updated_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `UNIQUE uq_student_user(user_id)`, `FK fk_student_user(user_id) -> users(id) ON DELETE CASCADE`

3. **`recruiter_profiles`** (Live rows: **1**)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `user_id` (`INT NOT NULL`), `company_name` (`VARCHAR(150)`), `company_website` (`VARCHAR(300)`), `company_description` (`TEXT`), `industry` (`VARCHAR(100)`), `updated_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `UNIQUE uq_recruiter_user(user_id)`, `FK fk_recruiter_user(user_id) -> users(id) ON DELETE CASCADE`

4. **`jobs`** (Live rows: **1** — status `CLOSED`, `deadline < CURDATE()`; currently **0 `ACTIVE` jobs**)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `recruiter_id` (`INT NOT NULL`), `title` (`VARCHAR(200) NOT NULL`), `company_name` (`VARCHAR(150) NOT NULL`), `description` (`TEXT NOT NULL`), `required_skills` (`TEXT`), `location` (`VARCHAR(150)`), `job_type` (`ENUM('INTERNSHIP','FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME'`), `salary_range` (`VARCHAR(100)`), `eligibility` (`TEXT`), `deadline` (`DATE`), `status` (`ENUM('ACTIVE','DRAFT','CLOSED') NOT NULL DEFAULT 'ACTIVE'`), `created_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `FK fk_jobs_recruiter(recruiter_id) -> users(id) ON DELETE CASCADE`, `idx_jobs_recruiter(recruiter_id)`, `idx_jobs_status(status)`, `idx_jobs_title(title(50))`, `idx_jobs_deadline(deadline)`

5. **`applications`** (Live rows: **1** — `SELECTED`, `updated_at > applied_at`)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `job_id` (`INT NOT NULL`), `student_id` (`INT NOT NULL`), `cover_letter` (`TEXT`), `status` (`VARCHAR(30) NOT NULL DEFAULT 'PENDING'`), `applied_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`), `updated_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `UNIQUE uq_application(job_id, student_id)`, `FK fk_app_job(job_id) -> jobs(id) ON DELETE CASCADE`, `FK fk_app_student(student_id) -> users(id) ON DELETE CASCADE`, `idx_app_student(student_id)`, `idx_app_job(job_id)`, `idx_app_status(status)`

6. **`resumes`** (Live rows: **3** — all 3 currently `is_active = 0`, all 3 have non-empty `parsed_json`)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `student_id` (`INT NOT NULL`), `file_name` (`VARCHAR(255) NOT NULL`), `stored_path` (`VARCHAR(500) NOT NULL`), `file_size_bytes` (`BIGINT NOT NULL`), `file_hash` (`VARCHAR(64) NOT NULL`), `parsed_text` (`MEDIUMTEXT`), `parsed_json` (`TEXT`), `is_active` (`TINYINT(1) NOT NULL DEFAULT 1`), `uploaded_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`), `updated_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `FK fk_resumes_student(student_id) -> users(id) ON DELETE CASCADE`, `idx_resumes_student(student_id)`, `idx_resumes_active(is_active)`, `idx_resumes_file_hash(file_hash)`

7. **`resume_analyses`** (Live rows: **1** — `job_id IS NULL`, i.e., `GENERAL`)
   - Columns: `id` (`INT PK AUTO_INCREMENT`), `resume_id` (`INT NULL`), `job_id` (`INT NULL`), `student_id` (`INT NOT NULL`), `overall_score` (`INT NOT NULL DEFAULT 0`), `skills_score` (`INT NOT NULL DEFAULT 0`), `experience_score` (`INT NOT NULL DEFAULT 0`), `education_score` (`INT NOT NULL DEFAULT 0`), `completeness_score` (`INT NOT NULL DEFAULT 0`), `certifications_score` (`INT NOT NULL DEFAULT 0`), `matching_skills` (`TEXT`), `missing_skills` (`TEXT`), `analysis_summary` (`MEDIUMTEXT`), `quality_feedback` (`MEDIUMTEXT`), `improvement_plan` (`MEDIUMTEXT`), `created_at` (`TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`)
   - Constraints & Indexes: `PRIMARY(id)`, `FK fk_analysis_student(student_id) -> users(id) ON DELETE CASCADE`, `FK fk_analysis_resume(resume_id) -> resumes(id) ON DELETE SET NULL`, `FK fk_analysis_job(job_id) -> jobs(id) ON DELETE SET NULL`, `idx_analysis_student_job(student_id, job_id)`, `idx_analysis_resume(resume_id)`, `idx_analysis_created(created_at)`

### 3.2 Key Schema Observations from Live Data
- **Sparse & Empty-State Realities Observed Live:**
  - `resumes` can have **0 active rows** (`is_active = 1`) while historical rows (`is_active = 0`) and `resume_analyses` still exist (e.g., if the student deactivated/deleted their latest resume or hasn't uploaded a replacement yet).
  - `jobs` can have **0 open active postings** (`0 ACTIVE`, `1 CLOSED`).
  - Newly registered students (`RegisterController`) have **no `student_profiles` row** until they first save their profile or sync from Resume Analyzer.
- **Missing Composite Indexes for Analytics Scaling:**
  - `applications` has single-column `idx_app_student(student_id)` and `idx_app_status(status)`, not composite `(student_id, applied_at)` or `(student_id, status)`.
  - `resumes` has single-column `idx_resumes_student(student_id)` and `idx_resumes_active(is_active)`, not composite `(student_id, is_active)`.
  - `jobs` has separate `idx_jobs_status(status)` and `idx_jobs_deadline(deadline)`, not composite `(status, deadline)`.
  - `resume_analyses` has `idx_analysis_student_job(student_id, job_id)` and global `idx_analysis_created(created_at)`, not `(student_id, created_at)`.
- **Schema Absences:** No `application_status_history`, no `ai_screening_logs`, no `interviews`, and no `career_prep_plans` tables exist in MySQL (only unimplemented comments existed in `schema.sql`).

---

## 4. Existing Student Security Boundaries & Vulnerabilities Found

> **Audit Scope Note:** Step 3A hardened Recruiter ownership (`*ForRecruiter` methods in `JobDAO`/`ApplicationDAO` and guards in `JobService`/`ApplicationService`/Recruiter controllers), leaving Student paths mostly untouched. Below are the exact security findings identified in the student layer—**documented here only; no Step 3A or completed files were modified.**

### 4.1 Controller Session & Role-Guard Gaps (High Priority)
All five student controllers fail to enforce `SessionManager.getInstance().hasRole(User.Role.STUDENT)`:
1. **`StudentDashboardController`:** Checks `currentUser == null -> return;` silently in `initialize()` (leaves dashboard visible rather than failing closed / redirecting to login) and never checks `hasRole(User.Role.STUDENT)`. Event handlers (`handleNavDashboard`, `handleNavProfile`, `handleSaveProfile`, `loadProfileAsync`) dereference `currentUser.getId()` without verifying an active `STUDENT` session.
2. **`JobBoardController`:** **Zero** null check on `currentUser` and **zero** `Role.STUDENT` check in `initialize()`, `loadJobsAsync()`, `showJobDetail()`, or `handleApply()`. An unauthenticated load throws `NullPointerException` on `currentUser.getId()`; an `ADMIN` or `RECRUITER` navigating to `job_board.fxml` could submit or test applications.
3. **`MyApplicationsController`:** **Zero** null check on `currentUser` and **zero** `Role.STUDENT` check in `initialize()` or `loadApplicationsAsync()`. Calling `currentUser.getId()` without a session causes an unhandled `NullPointerException`.
4. **`ResumeAnalyzerController`:** Checks `if (currentUser == null) return;` in `initialize()`, but neither hides/redirects fail-closed nor enforces `Role.STUDENT`. Action handlers assume `currentUser != null && STUDENT`.
5. **`AIAssistantController`:** **Zero** null check on `currentUser` and **zero** `Role.STUDENT` check in `initialize()` or action handlers (`handleAnalyzeSkillGap`, `handleGenerateQuestions`, `handleGeneratePlan`).

### 4.2 Service & DAO Scoping Vulnerabilities
1. **Unscoped `ResumeDAO` & `ResumeAnalysisDAO` Mutations/Lookups:**
   - `ResumeDAO.findById(int id)` (`WHERE id = ?`), `ResumeDAO.update(Resume r)` (`WHERE id = ?`), and `ResumeDAO.delete(int id)` (`WHERE id = ?`) contain **no `AND student_id = ?` predicate**. (`ResumeService.deleteResume(resumeId, studentId)` verifies ownership in Java before calling `delete(resumeId)`, rather than atomic SQL ownership enforcement.)
   - `ResumeAnalysisDAO.findById(int id)` (`WHERE ra.id = ?`) and `ResumeAnalysisDAO.delete(int id)` (`DELETE FROM resume_analyses WHERE id = ?`) similarly lack `AND student_id = ?` predicates.
   - *(Note: `ResumeDAO.findActiveByStudentId`, `findAllByStudentId`, and `ResumeAnalysisDAO.findByStudentAndJob`, `findRecentByStudentId` ARE parameterized by `student_id`.)*
2. **Missing Positive-ID (`<= 0`) & Role Validation in Student Services:**
   - `StudentService.getProfile(int userId)` / `saveProfile(int userId, ...)` and `StudentDAO` accept any `int` including `<= 0`.
   - `ApplicationService.apply(int studentId, int jobId, ...)`, `hasApplied(int studentId, int jobId)`, and `getStudentApplications(int studentId)` lack `studentId <= 0` / `jobId <= 0` validation.
   - `ResumeService` (`getActiveResume`, `getAllResumes`, `deleteResume`, `syncWithStudentProfile`) and `ResumeAnalysisService` (`saveAnalysis`, `getStudentAnalysisHistory`) lack `studentId <= 0` guards (`ResumeAnalysisService.calculateDeterministicScore` does not touch DB).
3. **Positive Baseline Already Present:**
   - All student-scoped reads (`ApplicationDAO.findByStudentId`, `exists`, `countByStudent`, `StudentDAO.findByUserId`, `ResumeDAO.findActiveByStudentId`, `findAllByStudentId`, `ResumeAnalysisDAO.findRecentByStudentId`) use parameterized `PreparedStatement`s bound strictly to `student_id`.
   - New `StudentAnalyticsDAO` + `StudentAnalyticsService` in Step 4B can enforce `SessionManager` `Role.STUDENT` checks, positive `studentId`, and `WHERE student_id = ?` SQL predicates consistently across every query.

---

## 5. Features That Can Be Implemented Cleanly Right Now (100% Real Data)

All of the following metrics, charts, and lists can be computed strictly from existing MySQL tables and deterministic domain services with **zero schema changes and zero dummy data**:

### A. Student Overview & Readiness Summary
- **Profile Completion Meter & Checklist:** Built from `student_profiles` + active `resumes` (`is_active = TRUE`):
  - Core academic completion (`StudentProfile.isComplete()`: college, degree, `graduationYear > 2000`)
  - Extended profile readiness indicators: phone populated, `skills` non-empty, and active resume uploaded (`resumes.is_active = 1`), yielding e.g. `"Complete (6/6)"` or `"Partial (4/6) — Add skills, upload active resume"`.
- **KPI Summary Cards:**
  - Total Applications (`COUNT(*) FROM applications WHERE student_id = ?`)
  - Pending Applications (`status = 'PENDING'`)
  - Shortlisted (`status = 'SHORTLISTED'`) & Selected Offers (`status = 'SELECTED'`)
  - Open Eligible Jobs (`jobs WHERE status = 'ACTIVE' AND (deadline IS NULL OR deadline >= CURDATE())`)
  - Active Resume Status (`Present` / `Inactive History Only` / `Not Uploaded`) & Latest Resume Score (or `"Not analyzed"`)

### B. Application Pipeline & Trend
- **Pipeline Status Distribution Chart (`PieChart` / Horizontal `BarChart`):** Zero-filled counts across all 4 `Application.Status` states (`PENDING`, `SHORTLISTED`, `REJECTED`, `SELECTED`) with exact counts and percentages.
- **Application Submission Trend (`LineChart` / `BarChart`):** Student's application volume grouped by time bucket (`applied_at`) across selectable date presets (`All Time`, `Last 30 Days`, `Last 90 Days`, `This Year`, `Custom Range`).
- **Pipeline Detail Table (`TableView`):** Joined `applications` + `jobs` columns: Job Title, Company, Job Type (`INTERNSHIP` / `FULL_TIME` / `PART_TIME`), Location, Applied On (`applied_at`), Status (`PENDING` / `SHORTLISTED` / `REJECTED` / `SELECTED`), Status Updated (`updated_at` if `> applied_at`, else `"—"`), Job Status (`ACTIVE` / `CLOSED`), and Application Deadline (`deadline` with expired warning badge if applicable). Clicking a row or action navigates to `MyApplicationsController` or opens job details.

### C. Placement Readiness & Deterministic Resume Diagnostics
- **Active Resume Inspection:** Directly inspects active `Resume` (`is_active = 1`) and deserializes `parsed_json` via `ResumeService.parseJsonMetadata(resume)` without touching disk PDFs unnecessarily:
  - Displays uploaded file name, upload timestamp, parsed counts (`skills`, `workExperiences`, `projects`, `certifications`, `links`, quantified `metricsCount`), plus fallback note if only historical inactive resumes exist (`is_active = 0`).
- **Live Deterministic Resume Rubric Breakdown (`ScoringBreakdown`):**
  - When an active `ParsedResume` exists, `ResumeAnalysisService.calculateDeterministicScore(parsedResume, null)` computes the live 100-point General Readiness breakdown:
    - Overall Score (`/100`), Skills (`/40`), Experience (`/25`), Education (`/15`), Completeness (`/10`), Certifications (`/10`)
    - Live **Evaluation Confidence** (`HIGH` / `MODERATE (...)` / `LOW (...)`) and explicit **Unevaluated Criteria** badges
    - **Honest UI Labeling:** Must be clearly labeled **"Deterministic Resume Quality / Job Compatibility Score"**—never as a predictive placement/hiring probability.
- **Persisted Resume Analysis History (`resume_analyses`):**
  - Displays latest and historical `resume_analyses` rows (`overall_score`, category scores, target `job_title`/`company_name` or `"General Analysis"`, `matching_skills`, `missing_skills`, `analysis_summary`, `quality_feedback`, `improvement_plan` / completeness note, `created_at`) scoped strictly to `WHERE ra.student_id = ?`.
- **Skill Inventory & Gap Signals:**
  - Combines unique canonicalized skills from `student_profiles.skills` + active `ParsedResume.getSkills()` (Step 4B should promote `ResumeAnalysisService.canonicalSkill(...)` to a `public` helper for reuse).
  - Aggregates real missing skills from persisted `resume_analyses.missing_skills` and from open active job requirements (`jobs.required_skills`).

### D. Deterministic Skill-Matched Job Recommendations
- **Feasibility:** Completely feasible using **existing `jobs` + `applications` + `student_profiles` + active `ParsedResume`**, with zero schema changes and zero AI API dependency:
  1. Query open jobs: `WHERE j.status = 'ACTIVE' AND (j.deadline IS NULL OR j.deadline >= CURDATE())`.
  2. Annotate whether the student has already applied (`EXISTS / LEFT JOIN applications WHERE student_id = ?`).
  3. Build the student's normalized skill set from `student_profiles.skills` + active `ParsedResume.getSkills()` using `ResumeAnalysisService`'s canonical alias rules.
  4. Rank open unapplied (and optionally applied) jobs deterministically:
     - If active `ParsedResume` exists and `job.getRequiredSkills()` is populated: compute `ResumeAnalysisService.calculateDeterministicScore(parsed, job)` -> exact compatibility score (`0–100`), `matchingSkills`, `missingSkills`, and `evaluationConfidence`.
     - If only `student_profiles.skills` exists (no active resume): compute deterministic required-skill overlap ratio (`matchedCount / requiredCount`) against parsed comma-separated `jobs.required_skills`.
     - If `jobs.required_skills` is empty or the student has zero declared/parsed skills: classify transparently as `"Unranked — Required skills or student skills unavailable"` rather than inventing scores!
- **Naming & Transparency Rule:** Display as **"Skill-Matched Open Jobs (Deterministic)"** with a tooltip/subtitle explaining the rule (`Matched against Profile + Active Resume skills; not an AI prediction`). Provide a quick `"Open in Job Board"` / `"View & Apply"` button navigating via `NavigationManager`.

### F. Real Timestamped Recent Activity Timeline
- **Unified Chronological Union (`WHERE student_id = ?`):**
  1. **Application Submitted:** `applications.applied_at` -> `"Applied to {job.title} at {job.company_name}"`
  2. **Application Status Updated:** `applications.updated_at` (strictly when `updated_at > applied_at`) -> `"Application for {job.title} updated to {status}"`
  3. **Resume Uploaded:** `resumes.uploaded_at` -> `"Uploaded resume: {file_name}"` (+ `(Active)` / `(Archived)`)
  4. **Resume Analysis Saved:** `resume_analyses.created_at` -> `"Completed AI/Rubric Resume Analysis for {job_title or 'General Readiness'} ({overall_score}/100)"`

---

## 6. Features Requiring Additional Persistence or Backend Work

To avoid fabricated telemetry or silent data loss, the following capabilities **cannot** be honestly displayed as persisted analytics until additional persistence/backend work is performed:

| Feature / Metric | Current Gap in Schema & Code | What Is Needed if Desired Later |
|---|---|---|
| **Full Application Stage History Timeline** (e.g., time spent in `PENDING` vs `SHORTLISTED`, multi-step transition log) | `applications` stores only single mutable `status`, `applied_at`, and auto-updating `updated_at` (`ON UPDATE CURRENT_TIMESTAMP`). Only the *most recent* transition is inferable when `updated_at > applied_at`. | New `application_status_history(id, application_id, old_status, new_status, changed_by, changed_at)` table populated in `ApplicationDAO.apply` & status update methods. |
| **Persisted Confidence & Unevaluated Criteria on Saved Analyses** | `ResumeAnalysisService.calculateDeterministicScore()` computes `evaluationConfidence` and `unevaluatedCriteria`, but `resume_analyses` has no columns for them; `ResumeAnalysisDAO.insert()` discards them. | `ALTER TABLE resume_analyses ADD COLUMN evaluation_confidence VARCHAR(120), ADD COLUMN unevaluated_criteria TEXT` (or display note on historical rows). |
| **Offline / Deterministic Analysis History Persistence** | `ResumeAnalyzerController` only calls `resumeAnalysisService.saveAnalysis(...)` after a **successful Gemini AI call** (offline rubric runs are never saved to `resume_analyses`). | Optional toggle/auto-save in `ResumeAnalyzerController` / `ResumeAnalysisService` for deterministic runs (`analysis_summary = NULL` or deterministic tag). |
| **Section E — Persisted AI Career Assistant Plans & Prep Progress** (`% completed`, saved interview questions, saved 4-week roadmaps, saved skill-gap reports) | `AIAssistantController` renders markdown from `AIService` ephemerally via `MarkdownRenderer`; `ai_screening_logs` and `interviews` mentioned in `schema.sql` comments **do not exist**; zero tables exist for career/interview prep plans or task checklists. | **New optional Phase/Step (e.g., Step 4D):** tables `career_prep_artifacts` (saved markdown reports per student & optional `job_id`, `artifact_type`) and `career_prep_tasks` (trackable checklist items) + DAO/Service. **Interim Step 4B/4C behavior:** Surface Gemini API readiness (`AIService.isConfigured()` / `isConnectionVerified()`), quick-launch shortcuts to `AIAssistantController` / `ResumeAnalyzerController`, and display **real persisted** `missing_skills` & `analysis_summary` / `quality_feedback` from existing `resume_analyses` rows (with explicit `"No persisted AI Career Assistant session tracking"` empty states). |

---

## 7. Proposed Student Intelligence Dashboard UI Layout

Design goal: Align visually with Phase 5 `AdminDashboardController` (`.admin-*`) and `RecruiterDashboardController` (`.rec-*`), while preserving every existing student navigation route and workflow.

### 7.1 Visual Theme & Scoped CSS Rules
- **Palette:** `#FFFFFF` card surfaces, `#F1F3F7` / `#F8F9FB` light-grey canvas, `#1A2B5E` deep navy sidebar & primary titles, `#2563EB` primary interactive blue, `#E2E6EF` borders, semantic badges (`#ECFDF5`/`#047857` green, `#FFFBEB`/`#B45309` amber, `#FEF2F2`/`#DC2626` red).
- **Scoped Styles:** Add dedicated `.stu-*` classes in `style.css` (mirroring `.admin-*` and `.rec-*`) rather than altering shared Admin/Recruiter/Login styles. Fix space-separated `styleClass` declarations in `student_dashboard.fxml` to comma-separated format.
- **Scroll Discipline:** Single outer `<ScrollPane fitToWidth="true" hbarPolicy="NEVER" vbarPolicy="AS_NEEDED">` wrapping the main content `VBox`; zero nested scrollable `ListView`/`ScrollPane` containers inside dashboard cards; dynamic height `TableView` sizing helper matching `AdminDashboardController`/`RecruiterDashboardController`.

### 7.2 Navigation & Structural Layout (`student_dashboard.fxml`)
- **Persistent Left Sidebar (`225px` `#1A2B5E`):**
  - Brand: `"HireHub"` / `"Student Portal"`
  - In-Dashboard Section Switchers (scroll/switch smoothly, keeping `.sidebar-nav-active` synchronized):
    - `Overview` (`navDashboardBtn` — default view)
    - `Career Readiness & Skills` (`navReadinessBtn` — quick scroll/focus to Readiness, Job Matches & Prep)
    - `My Profile` (`navProfileBtn` — opens existing `profilePane` editor cleanly)
  - Existing Dedicated Student Workspace Nav Buttons (preserved verbatim via `NavigationManager`):
    - `Browse Jobs` (`navBrowseJobsBtn` -> `/fxml/job_board.fxml`)
    - `My Applications` (`navApplicationsBtn` -> `/fxml/my_applications.fxml`)
    - `AI Resume Analyzer` (`navResumeAnalyzerBtn` -> `/fxml/resume_analyzer.fxml`)
    - `AI Career Assistant` (`navAIBtn` -> `/fxml/ai_assistant.fxml`)
  - Footer: `Refresh` (`navRefreshBtn`) + `Logout` (`navLogoutBtn`)

- **Top Account Header & Filter Toolbar (in `dashboardPane`):**
  - Left: `"Welcome back, {Full Name}"`, Profile Readiness pill (`Complete` / `Incomplete — Finish Profile`), Member Since subtitle, College/Degree badge (or setup prompt).
  - Right: `ProgressIndicator` (fixed CSS `prefWidth`/`prefHeight` per Step 2 loop guard), `"Last updated: HH:mm:ss"`, Student role pill, Period Filter (`All Time`, `Last 30 Days`, `Last 90 Days`, `This Year`, `Custom` + `From`/`To` `DatePicker`s + `Apply`/`Reset`), and non-blocking inline error/warning banner.

- **Dashboard Sections Inside `dashboardPane` (All Real Data):**
  1. **Row 1 — Overview KPI Grid (5 Cards):**
     - **Profile Readiness** (e.g., `4 / 6 checks`, College/Degree/Skills/Active Resume status + `"Edit Profile"` link)
     - **Applications Sent** (Total in selected period vs All-Time, Active/Pending count)
     - **Shortlisted & Selected** (Combined advancement count + selection highlight)
     - **Latest Resume Score** (`XX / 100` deterministic/persisted score + confidence badge or `"Upload Resume"` prompt)
     - **Open Matching Jobs** (`N` open non-expired active jobs + top deterministic skill overlap summary)
  2. **Row 2 — Application Pipeline Charts (`HBox` 2-Column Split):**
     - Left Card: **Application Status Breakdown** (Non-animated `PieChart` with zero-filled legend showing `PENDING`, `SHORTLISTED`, `REJECTED`, `SELECTED` counts & `%`)
     - Right Card: **Application Activity Over Time** (Non-animated `LineChart` of applications submitted over time, clearly handling `0` applications with clean empty-state overlay)
  3. **Row 3 — Placement Readiness & Skills Intelligence (`HBox` 2-Column Split):**
     - Left Card: **Resume & Placement Readiness Breakdown** — Active resume status (`Active` / `Historical Only` / `None`), `ProgressBar` rows for deterministic rubric (`Skills /40`, `Experience /25`, `Education /15`, `Completeness /10`, `Certifications /10`), Evaluation Confidence pill (`HIGH`/`MODERATE`/`LOW`), Unevaluated Criteria list, and disclaimer: *"Deterministic quality indicator — not a hiring probability"*. Quick CTA -> `Resume Analyzer`.
     - Right Card: **Skills Profile & Identified Gaps** — Unified Profile + Parsed Resume skill chips (`student_profiles.skills ∪ active ParsedResume.skills`), **Identified Missing Skills** from latest persisted `resume_analyses` + demand across open `ACTIVE` jobs, plus quick links to `Edit Profile` or `AI Career Assistant`.
  4. **Row 4 — Skill-Matched Open Jobs (Deterministic):**
     - Compact dynamic-height `TableView` (top 8–10 open, non-expired `ACTIVE` jobs ranked by deterministic skill overlap / rubric score):
       - Columns: `Job Title`, `Company`, `Type`, `Deadline`, `Skill Match`, `Matched / Required Skills`, `Status` (`Applied` badge vs `Not Applied`), `Action` (`Open in Job Board`).
     - Explicit header subtitle noting `"Deterministic keyword & alias overlap — apply in Browse Jobs"`. Graceful empty state when `0 ACTIVE` open jobs exist (such as right now in `hirehub_db`).
  5. **Row 5 — Recent Applications & Career Preparation (`HBox` 2-Column Split):**
     - Left Card: **Application Pipeline Table** (Latest 8–10 student applications: Job, Company, Applied Date, Latest Update, Status badge, Job State) + `"View All in My Applications"` link.
     - Right Card: **Career Preparation & AI Workspace** — Live Gemini status pill (`Configured` / `API Key Missing`), latest persisted `resume_analyses.analysis_summary` / `quality_feedback` preview (or clean fallback prompt), notice that AI Career Assistant sessions are interactive/ephemeral, and launch buttons for **Skill Gap Analysis**, **Interview Prep**, and **4-Week Roadmap** in `ai_assistant.fxml` + **Deep Resume Review** in `resume_analyzer.fxml`.
  6. **Row 6 — Recent Student Activity Timeline:**
     - Clean chronological table/feed of real student events (`Application Submitted`, `Status Updated`, `Resume Uploaded`, `Resume Analysis Saved`) ordered newest-first.

- **Preserved `profilePane`:** Keeps all existing Edit Profile fields (`phoneField`, `collegeField`, `degreeField`, `gradYearField`, `skillsArea`), validation, and save workflow intact, refreshed cleanly back into `StudentDashboardSnapshot` upon save.

---

## 8. Files Requiring Modification or Creation (Future Steps 4B & 4C)

### Step 4B — Security Hardening & Read-Only Student Analytics Backend
**Create:**
- `src/main/java/com/hirehub/model/StudentDashboardSnapshot.java`
- `src/main/java/com/hirehub/model/StudentOverviewKPI.java`
- `src/main/java/com/hirehub/model/StudentStatusDistribution.java`
- `src/main/java/com/hirehub/model/StudentApplicationRow.java`
- `src/main/java/com/hirehub/model/StudentResumeReadiness.java`
- `src/main/java/com/hirehub/model/StudentSkillInsight.java`
- `src/main/java/com/hirehub/model/StudentJobRecommendation.java`
- `src/main/java/com/hirehub/model/StudentTrendPoint.java`
- `src/main/java/com/hirehub/model/StudentActivityEvent.java`
- `src/main/java/com/hirehub/dao/StudentAnalyticsDAO.java`
- `src/main/java/com/hirehub/service/StudentAnalyticsService.java`
- `src/main/java/com/hirehub/util/Phase5Step4BStudentAnalyticsVerifier.java` (automated CLI verification for security isolation, SQL accuracy, empty/sparse states, and determinism)

**Modify (Security & Scoping Hardening Only — preserving existing Admin/Recruiter behavior):**
- `src/main/java/com/hirehub/dao/ResumeDAO.java` (add `findByIdForStudent(int id, int studentId)`, `deleteForStudent(int id, int studentId)` with atomic `WHERE id = ? AND student_id = ?`)
- `src/main/java/com/hirehub/dao/ResumeAnalysisDAO.java` (add `findByIdForStudent`, `deleteForStudent` with atomic `AND student_id = ?`)
- `src/main/java/com/hirehub/service/StudentService.java` (enforce `SessionManager` `Role.STUDENT` + `userId > 0` ownership guards)
- `src/main/java/com/hirehub/service/ApplicationService.java` (add `studentId > 0`, `jobId > 0`, `SessionManager` `Role.STUDENT` verification on student-facing `apply`, `hasApplied`, `getStudentApplications` without disturbing Step 3A recruiter methods)
- `src/main/java/com/hirehub/service/ResumeService.java` & `ResumeAnalysisService.java` (enforce `studentId > 0` and `Role.STUDENT` session checks, route `deleteResume` through `deleteForStudent`)
- `src/main/java/com/hirehub/dao/JobDAO.java` (optional helper `searchOpenActive(...)` or deadline-aware filtering cleanly without breaking existing recruiter behavior; evaluate carefully during Step 4B)
- Student Controllers (`JobBoardController.java`, `MyApplicationsController.java`, `ResumeAnalyzerController.java`, `AIAssistantController.java`, `StudentDashboardController.java`) — add fail-closed `SessionManager.getInstance().hasRole(User.Role.STUDENT)` + session-id checks in `initialize()` and handlers (controller UI overhaul waits for Step 4C).

### Step 4C — Student Intelligence Dashboard UI
**Modify:**
- `src/main/resources/fxml/student_dashboard.fxml` (replace Phase 1 quick-action placeholder with Student Intelligence Dashboard layout, preserving sidebar IDs & `profilePane`)
- `src/main/java/com/hirehub/controller/StudentDashboardController.java` (consume `StudentAnalyticsService` asynchronously with `AtomicBoolean` + `AtomicLong` generation guards, bind charts/tables/KPIs/job-match/activity/profile form, manage `.sidebar-nav-active` cleanly)
- `src/main/resources/css/style.css` (append strictly scoped `.stu-*` styles matching `#1A2B5E` / `#2563EB` / light-grey/white aesthetic; fix `.text-area-input` selector cleanly if needed)
- *(Optional tiny navigation tweak only if adding direct "View in Job Board" pre-selection via `JobBoardController.selectJob(int)`—otherwise zero sub-controller UI changes needed.)*

**Create:**
- `src/main/java/com/hirehub/util/Phase5Step4CStudentDashboardVerifier.java` (headless FXML load, chart/table/empty-state controller binding, and layout checks)

**Untouched / Preserved Throughout Steps 4B & 4C:**
- `AdminDashboardController`, `admin_dashboard.fxml`, `AdminAnalyticsDAO`, `AdminAnalyticsService`, all `Admin*` DTOs
- `RecruiterDashboardController`, `recruiter_dashboard.fxml`, `RecruiterAnalyticsDAO`, `RecruiterAnalyticsService`, all `Recruiter*` DTOs, `ApplicantManagementController`, `JobFormController`, `*ForRecruiter` security methods
- `SessionManager`, `NavigationManager`, `AuthService`, `LoginController`, `RegisterController`, `DatabaseConnection`, `pom.xml`, `module-info.java`, `AIService`

---

## 9. Proposed Phased Implementation Sequence

1. **Step 4A (Current — Complete):** Pre-implementation audit, live schema verification, security gap discovery, and implementation plan approval.
2. **Step 4B — Student Security Hardening & Analytics Backend:**
   - Fix student session/role guards across all 5 student controllers and add atomic `student_id`-scoped methods in `ResumeDAO` / `ResumeAnalysisDAO` + student service checks.
   - Implement `StudentAnalyticsDAO`, `StudentAnalyticsService`, and all 9 typed `Student*` DTOs with real deterministic skill-overlap matching (`JobRecommendation`) and strict empty/sparse handling.
   - Verify via `mvn compile` and `Phase5Step4BStudentAnalyticsVerifier` against live MySQL (`ONLY_FULL_GROUP_BY`), confirming zero leakage across students, accurate counts for 0 active resumes / 0 open jobs / 1 selected app, and zero regressions on Admin (`Phase5AnalyticsVerifier`) & Recruiter (`Phase5Step3ASecurityVerifier`, `Phase5RecruiterAnalyticsVerifier`) tests.
3. **Step 4C — Student Intelligence Dashboard UI:**
   - Add scoped `.stu-*` CSS rules, update `student_dashboard.fxml` (single `ScrollPane`, fixed comma `styleClass` lists, fixed `ProgressIndicator` size), and wire `StudentDashboardController` to `StudentAnalyticsService` with async cancellation & generation tokens.
   - Verify via `mvn compile`, headless JavaFX verification (`Phase5Step4CStudentDashboardVerifier` testing sparse data, populated multi-application data, date filters, and navigation to all 4 child student workspaces + Profile edit/save + Logout), and live UI launch check.
4. **Optional Future Step 4D (Separate Scope — Only If Requested):**
   - Persist `application_status_history`, `evaluation_confidence` / `unevaluated_criteria` in `resume_analyses`, and saved AI Career Assistant preparation plans/checklists (`career_prep_artifacts`, `career_prep_tasks`).

---

## 10. Risks, Missing Dependencies, & Recommendations

### 10.1 Technical Risks & Edge Cases
1. **Unauthenticated / Cross-Role Controller Exposure (High Risk):** Currently `JobBoardController`, `MyApplicationsController`, and `AIAssistantController` crash with `NullPointerException` if entered without a logged-in user, and all 5 student controllers accept `ADMIN` or `RECRUITER` sessions. Step 4B **must** close this before UI work.
2. **Empty & Sparse Live State Handling:** Live MySQL currently contains `0` `ACTIVE` jobs, `0` active (`is_active=1`) resumes (despite 3 inactive rows), `1` historical general `resume_analyses` row, and `1` `SELECTED` application. JavaFX charts (`PieChart`, `LineChart`), score cards, and job match tables must render informative empty states rather than blank voids or `NaN`/`0/0` errors.
3. **JSON Deserialization Performance (`resumes.parsed_json`):** Deserializing `ParsedResume` JSON once per snapshot via `ResumeService.parseJsonMetadata(resume)` avoids disk I/O (`data/resumes/`) inside analytics queries, gracefully handling null/empty `parsed_json`.
4. **`JobBoardController` Expired-Active Deadline Discrepancy:** `JobDAO.searchActive` shows jobs with `status = 'ACTIVE'` even if `deadline < CURDATE()`, which then fail on Apply. Student job recommendations **must** filter `(j.deadline IS NULL OR j.deadline >= CURDATE())` so students are never recommended unapplicable expired postings, and `JobDAO.searchActive` should be reviewed/aligned carefully during Step 4B.
5. **JavaFX Modena CSS Loop / Nested Scroll Traps:** Keep a single outer `ScrollPane`, bind `TableView` pref/min/max heights to row counts (`CONSTRAINED_RESIZE_POLICY`), avoid overriding Modena `.text-input`/`.error-label` globally, and set explicit `prefWidth`/`prefHeight` on `ProgressIndicator` (lesson learned in Step 2).

### 10.2 Missing Dependencies & Database Recommendations
- **Maven / Dependencies:** Zero new Maven dependencies are needed (`Java 21`, `JavaFX 21`, `mysql-connector-j`, `PDFBox`, `jBCrypt`, `Gson` are already present and modularized in `module-info.java`).
- **Non-Breaking Optional Index Additions (for scaling beyond MVP):** When ready for production volume, composite indexes (`applications(student_id, applied_at)`, `resumes(student_id, is_active)`, `jobs(status, deadline)`, `resume_analyses(student_id, created_at)`) would further accelerate dashboard queries; they are not required to implement Step 4B cleanly right now.
- **Explicit Recommendation Before Coding:** Proceed to **Step 4B** first (Student Security Hardening + Read-Only `StudentAnalyticsDAO`/`Service`/DTOs) before touching `student_dashboard.fxml` or `StudentDashboardController.java` (Step 4C), keeping scope tight and verifiable at every boundary.
