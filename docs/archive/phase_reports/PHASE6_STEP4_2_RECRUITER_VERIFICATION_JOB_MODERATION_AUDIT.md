# HireHub — Phase 6 Step 4.2: Recruiter Verification & Job Moderation
## Comprehensive Product, Architecture, Security, and Database Audit Report

**Document Version:** 1.0  
**Audit Type:** Read-Only Product, Architecture, Security, and Database Audit  
**Target Environment:** `D:\HireHub`  
**Date:** November 13, 2026  
**Auditor:** Antigravity AI Senior Systems & Security Architecture Specialist  
**Execution Mode:** Strictly Read-Only (Zero Code/Database Modifications Made)  

---

## 1. Executive Summary

HireHub is a JavaFX 21 desktop recruitment and placement management application built with Java 21, MySQL 8, HikariCP connection pooling, BCrypt password hashing, and MVC + DAO + Service layers. Following the successful completion of **Phase 6 Step 4.1** (which introduced account activation/deactivation via `users.is_active` and atomic administrative audit logging via `admin_audit_logs`), this audit evaluates the application's readiness for **Phase 6 Step 4.2: Recruiter Verification & Job Moderation**.

### The Core Problem
In its present architecture, HireHub exhibits a critical governance deficiency that prevents it from safely operating in a university placement environment:
1. **Instant, Unverified Recruiter Onboarding:** Any user can register an account selecting the `RECRUITER` role. Upon registration, the user is immediately granted an active account without administrative review, domain verification, company authorization, or identity vetting.
2. **Unrestricted, Immediate Job Broadcasting:** Any recruiter (including freshly registered, unverified accounts with empty company profiles) can create and publish jobs with status `ACTIVE`.
3. **Instant Campus-Wide Student Exposure:** Jobs with status `ACTIVE` are immediately visible to all students on the job board, appear in student recommendation algorithms, and can immediately receive applications containing sensitive student PII and resume files.
4. **Complete Absence of Job Moderation:** There is no administrative review queue for job listings, no capability for administrators to approve, reject, flag, or suspend active job postings, and no distinction between a recruiter's publishing intent and an institution's moderation status.

### Impact on an Institutional Placement Platform
In a real university placement ecosystem, this gap presents catastrophic compliance, legal, and security risks:
- **Phishing & Fraudulent Job Postings:** Rogue actors can create accounts pretending to represent prominent companies (e.g., "Google", "Microsoft") and post fraudulent openings.
- **Harvester Attacks on Student PII:** Malicious recruiters can collect private student resumes, contact details, home addresses, GPA records, and project IP without university authorization.
- **Brand & Accreditation Liability:** Unvetted job descriptions containing discriminatory eligibility criteria, substandard compensation, or illegal conditions directly expose the institution to reputational and statutory liability.

### Proposed Architectural Solution: Two-Axis Governance
To solve these challenges without disrupting legitimate existing workflows, this audit establishes a **Two-Axis Governance Architecture**:
1. **Axis 1: Recruiter Verification Lifecycle:** Tracks the trustworthiness of the corporate entity (`UNVERIFIED` $\rightarrow$ `PENDING` $\rightarrow$ `VERIFIED` / `REJECTED` / `SUSPENDED`).
2. **Axis 2: Job Moderation Lifecycle:** Decouples recruiter workflow status (`DRAFT`, `ACTIVE`, `CLOSED`) from institutional moderation status (`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`).

Under this architecture, a job is visible to students on the campus job board and in analytics recommendations **if and only if**:
$$\text{Student Visibility} = (\text{Job.status} = \text{'ACTIVE'}) \land (\text{Job.moderation\_status} = \text{'APPROVED'}) \land (\text{User.is\_active} = \text{TRUE}) \land (\text{Recruiter.verification\_status} = \text{'VERIFIED'}) \land (\text{Job.deadline} \ge \text{CURDATE}())$$

---

## 2. Existing Architecture and Workflow

HireHub follows a strict Model-View-Controller (MVC) combined with Data Access Object (DAO) and Service layer separation, powered by a modular Java 21 architecture.

```
+---------------------------------------------------------------------------------------+
|                                    PRESENTATION LAYER                                 |
|  LoginController       RegisterController       JobFormController                     |
|  JobBoardController    MyApplicationsController AdminDashboardController              |
|  RecruiterDashboardController                   ApplicantManagementController         |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                      SERVICE LAYER                                    |
|  AuthService           RecruiterService         JobService                            |
|  ApplicationService    StudentService           AdminUserService                      |
|  AdminAnalyticsService RecruiterAnalyticsService ResumeService                        |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                   DATA ACCESS LAYER                                   |
|  UserDAO               RecruiterDAO             JobDAO                                |
|  ApplicationDAO        StudentDAO               AdminUserDAO                          |
|  ResumeDAO             ResumeAnalysisDAO        AdminAnalyticsDAO                     |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                 INFRASTRUCTURE LAYER                                  |
|   DatabaseConnection (HikariDataSource 5.1.0 Pool, 10 conn, 30s timeout)              |
|   MySQL 8.0 InnoDB Database (hirehub_db)                                              |
|   SessionManager (Singleton in-memory authenticated session state)                   |
|   NavigationManager (Primary stage view switcher)                                     |
+---------------------------------------------------------------------------------------+
```

### End-to-End Workflow Analysis
1. **User Registration:**
   - User inputs `Full Name`, `Email`, `Password`, and chooses role (`STUDENT` or `RECRUITER`) in `register.fxml`.
   - `RegisterController` invokes `AuthService.register(fullName, email, password, role)`.
   - `UserDAO.insertUser()` hashes password via BCrypt (cost factor 12) and inserts row into `users` table with `is_active = TRUE`.
   - **Critical Observation:** No row is created in `recruiter_profiles` during registration. The user is immediately eligible to log in.
2. **Recruiter Login & Session Creation:**
   - Recruiter submits credentials in `login.fxml`.
   - `AuthService.login()` validates password, checks `user.isActive()`, and initialises `SessionManager.getInstance().setCurrentUser(user)`.
   - `NavigationManager` navigates to `/fxml/recruiter_dashboard.fxml`.
3. **Job Creation & Publishing:**
   - Recruiter clicks "Post Job", opening `/fxml/job_form.fxml` managed by `JobFormController`.
   - The recruiter enters title, company, description, skills, location, type, salary, eligibility, deadline.
   - Recruiter clicks "Publish Job" (`handlePublish`), which calls `jobService.createJob(..., asDraft = false)`.
   - `JobDAO.insert()` inserts record into `jobs` table with `status = 'ACTIVE'`.
   - **Critical Observation:** Neither `JobFormController`, `JobService`, nor `JobDAO` validates whether the recruiter has completed their profile or whether their company has been vetted by an administrator.
4. **Student Job Discovery:**
   - Student navigates to `/fxml/job_board.fxml`.
   - `JobBoardController` calls `jobService.searchJobs(keyword, location, jobType)`.
   - `JobDAO.searchActive()` executes:
     ```sql
     SELECT id, recruiter_id, title, company_name, description, required_skills, location,
            job_type, salary_range, eligibility, deadline, status, created_at
     FROM jobs
     WHERE status='ACTIVE'
       AND (title LIKE ? OR company_name LIKE ?)
       AND (? = '' OR location LIKE ?)
       AND (? = '' OR job_type = ?)
     ORDER BY created_at DESC;
     ```
   - The newly published job is returned instantly and displayed in the student UI.
5. **Application Submission:**
   - Student clicks "Apply" in `JobBoardController`.
   - `ApplicationService.apply(studentId, jobId, coverLetter)` verifies that `job.getStatus() == Job.Status.ACTIVE` and `job.getDeadline() >= CURDATE()`.
   - `ApplicationDAO.insert()` inserts application and snapshots active resume ID.
   - The unverified recruiter can now immediately view the student's full name, email, CGPA, profile, and download their resume snapshot.

---

## 3. Exact Files and Database Tables Inspected

The following source code components, resource bundles, SQL scripts, and database structures were inspected in detail during this audit:

### Java Source Code Inspected
| Class Path | Layer | Responsibility Inspected |
| :--- | :--- | :--- |
| `src/main/java/com/hirehub/controller/RegisterController.java` | Controller | Registration input fields, validation, and role assignment. |
| `src/main/java/com/hirehub/controller/LoginController.java` | Controller | Authentication flow, session creation, post-login routing. |
| `src/main/java/com/hirehub/controller/RecruiterDashboardController.java` | Controller | Recruiter navigation, profile tab, job management tab. |
| `src/main/java/com/hirehub/controller/JobFormController.java` | Controller | Job posting/editing logic, draft vs publish button handlers. |
| `src/main/java/com/hirehub/controller/JobBoardController.java` | Controller | Student job search, filtering, card rendering, and application launch. |
| `src/main/java/com/hirehub/controller/AdminDashboardController.java` | Controller | Navigation switching, pane management, user management toolbar, and refresh logic. |
| `src/main/java/com/hirehub/service/AuthService.java` | Service | User registration, password hashing, and active status check. |
| `src/main/java/com/hirehub/service/RecruiterService.java` | Service | Recruiter profile CRUD, session authorization, and Admin override methods. |
| `src/main/java/com/hirehub/service/JobService.java` | Service | Job creation, updating, activation, closing, deletion, and recruiter ownership enforcement. |
| `src/main/java/com/hirehub/service/ApplicationService.java` | Service | Application submission, status management, job active status checks. |
| `src/main/java/com/hirehub/service/AdminUserService.java` | Service | Admin authorization guard, user search, account deactivation logic. |
| `src/main/java/com/hirehub/dao/UserDAO.java` | DAO | User table CRUD, password checks, `is_active` persistence. |
| `src/main/java/com/hirehub/dao/RecruiterDAO.java` | DAO | Recruiter profile SQL upsert and retrieval by user ID. |
| `src/main/java/com/hirehub/dao/JobDAO.java` | DAO | Job SQL queries (`searchActive`, `findOpenActiveJobs`, recruiter CRUD). |
| `src/main/java/com/hirehub/dao/AdminUserDAO.java` | DAO | Parameterized user queries, atomic transactions via HikariCP, audit log creation. |
| `src/main/java/com/hirehub/dao/StudentAnalyticsDAO.java` | DAO | Job count queries and student job recommendation SQL queries. |
| `src/main/java/com/hirehub/dao/AdminAnalyticsDAO.java` | DAO | Admin job metrics, fleet inventory, and top jobs table. |
| `src/main/java/com/hirehub/dao/RecruiterAnalyticsDAO.java` | DAO | Recruiter dashboard KPI calculations and pipeline metrics. |
| `src/main/java/com/hirehub/model/User.java` | Model | User domain entity with `isActive` field. |
| `src/main/java/com/hirehub/model/RecruiterProfile.java` | Model | Recruiter profile fields and `isComplete()` method. |
| `src/main/java/com/hirehub/model/Job.java` | Model | Job domain entity, `JobType` enum, and `Job.Status` enum. |
| `src/main/java/com/hirehub/model/AdminAuditLog.java` | Model | Audit trail domain entity. |
| `src/main/java/com/hirehub/database/DatabaseConnection.java` | Infrastructure | HikariCP pool configuration, connection provider, and graceful shutdown. |
| `src/main/java/com/hirehub/util/SessionManager.java` | Utility | In-memory session holder and role authorization helper. |

### FXML & Resource Files Inspected
| Resource File | Purpose & UI Structure Inspected |
| :--- | :--- |
| `src/main/resources/fxml/register.fxml` | Registration form layout and controls. |
| `src/main/resources/fxml/recruiter_dashboard.fxml` | Recruiter tab bar (`navDashboard`, `navJobs`, `navProfile`), job cards. |
| `src/main/resources/fxml/job_form.fxml` | Job form fields, "Save as Draft", "Publish Job", "Cancel" buttons. |
| `src/main/resources/fxml/job_board.fxml` | Student job browsing layout, search inputs, apply buttons. |
| `src/main/resources/fxml/admin_dashboard.fxml` | Sidebar navigation, `overviewPane`, `jobsPane`, `recruitersPane`, `usersPane`. |

### Database Schemas & Migrations Inspected
| Script / Table Name | Structure & Metadata Verified |
| :--- | :--- |
| `database/schema.sql` | Baseline table definitions for `users`, `jobs`, `applications`, `student_profiles`, `recruiter_profiles`. |
| `database/ensure_all_tables.sql` | Comprehensive idempotent DDL script. |
| `database/phase6_step4_1_migration.sql` | Migration adding `users.is_active` and `admin_audit_logs`. |
| `users` table | `id`, `full_name`, `email`, `password_hash`, `role`, `is_active`, `created_at`. |
| `recruiter_profiles` table | `id`, `user_id`, `company_name`, `company_website`, `company_description`, `industry`, `updated_at`. |
| `jobs` table | `id`, `recruiter_id`, `title`, `company_name`, `description`, `required_skills`, `location`, `job_type`, `salary_range`, `eligibility`, `deadline`, `status`, `created_at`. |
| `applications` table | `id`, `job_id`, `student_id`, `resume_id`, `status`, `applied_at`, `updated_at`. |
| `admin_audit_logs` table | `id`, `admin_id`, `target_user_id`, `action`, `reason`, `created_at`. |

---

## 4. Current Recruiter Registration and Publishing Behavior

A line-by-line inspection of the registration and job publishing path reveals how an unvetted recruiter currently operates:

### 4.1 Registration Path
In `RegisterController.java` (`handleRegister`):
```java
String fullName = fullNameField.getText();
String email    = emailField.getText();
String password = passwordField.getText();
User.Role role  = recruiterRadio.isSelected() ? User.Role.RECRUITER : User.Role.STUDENT;

AuthService.AuthResult result = authService.register(fullName, email, password, role);
```
In `AuthService.java` (`register`):
```java
String hash = PasswordUtil.hashPassword(password);
User user = new User(fullName, email, hash, role); // user.isActive defaults to true
int userId = userDAO.insertUser(user);
```
- **Finding 1:** Registration requests only personal name, email, and password. No company name, verification document, business email, or tax/incorporation ID is collected.
- **Finding 2:** `userDAO.insertUser()` writes `is_active = TRUE` immediately for recruiters.
- **Finding 3:** No entry is made in `recruiter_profiles`. The database contains an active `users` row with no corresponding corporate record.

### 4.2 Recruiter Profile State
In `RecruiterProfile.java`:
```java
public boolean isComplete() {
    return companyName != null && !companyName.isBlank();
}
```
- **Finding 4:** The concept of "completeness" in `RecruiterProfile` only requires a non-blank `company_name`. Website, description, and industry are completely optional.
- **Finding 5:** There is no field in `recruiter_profiles` representing verification status, submission timestamp, reviewer ID, or approval state.

### 4.3 Job Form Submission
In `JobFormController.java` (`submitJob`):
```java
if (targetJob == null) {
    result = jobService.createJob(submitRecruiterId, title, company, description,
            skills, location, jobType, salary, eligibility, deadline, asDraft);
}
```
In `JobService.java` (`createJob`):
```java
Job.Status status = asDraft ? Job.Status.DRAFT : Job.Status.ACTIVE;
Job job = new Job(recruiterId, title.trim(), companyName.trim(), description.trim(),
                  skills, location, type, salary, eligibility, deadline, status);
int jobId = jobDAO.insert(job);
```
- **Finding 6:** `JobFormController` contains both "Save as Draft" and "Publish Job" buttons. Both are enabled by default for all recruiters at all times.
- **Finding 7:** When the recruiter clicks "Publish Job", `asDraft` is `false`, resulting in `status = Job.Status.ACTIVE`.
- **Finding 8:** `JobService.createJob()` performs syntactic validations (non-null title, valid deadline date) but **never inspects whether the recruiter's account is verified**.

---

## 5. Current Job Lifecycle and Status Transitions

The application currently manages jobs using a single-axis enumeration in `com.hirehub.model.Job.Status`:
```java
public enum Status {
    ACTIVE, DRAFT, CLOSED;
}
```

```
                   +-------------------+
                   |   (Recruiter)     |
                   |      DRAFT        |
                   +---------+---------+
                             |
                   publish   |   re-draft
                   +---------+---------+
                   |                   |
                   v                   |
         +-------------------+         |
         |   (Recruiter)     |         |
         |      ACTIVE       +<--------+
         +---------+---------+
                   |
                   | close
                   v
         +-------------------+
         |   (Recruiter)     |
         |      CLOSED       |
         +-------------------+
```

### Current Operational Rules
1. **DRAFT:** Visible only to the creating recruiter via `jobDAO.findByRecruiterId()`.
2. **ACTIVE:** Visible to the recruiter, all students (`jobDAO.searchActive()`), and all administrators (`AdminAnalyticsDAO.java`).
3. **CLOSED:** No longer accepting applications (`ApplicationService.apply()` rejects submissions). Existing applications remain readable by the recruiter.

### The Fundamental Flaw
The single `Status` enum conflates two independent concerns:
- **Recruiter Workflow Intent:** Does the recruiter want this job visible or archived? (`DRAFT` vs `ACTIVE` vs `CLOSED`).
- **Institutional Administrative Governance:** Has the placement department vetted and authorized this job listing? (`PENDING` vs `APPROVED` vs `REJECTED` vs `SUSPENDED`).

Because there is no second axis, a recruiter who sets a job to `ACTIVE` automatically grants themselves institutional approval without any intermediary moderation step.

---

## 6. Existing Security Protections and Gaps

### 6.1 Existing Security Protections (Already in Place)
1. **Fail-Closed Controller Session Authentication:** All controllers verify that `SessionManager.getInstance().getCurrentUser()` is non-null and possesses the appropriate role (`STUDENT`, `RECRUITER`, `ADMIN`).
2. **Service-Layer Recruiter Ownership Enforcement (GAP-03 Closure):** Every mutating method in `JobService` (`updateJob`, `closeJob`, `deleteJob`, `getJobByIdForRecruiter`) verifies that the job's `recruiter_id` strictly matches the authenticated session's user ID.
3. **Database-Level Ownership in SQL:** `JobDAO` provides `updateForRecruiter()`, `deleteForRecruiter()`, and `updateStatusForRecruiter()` with `WHERE id = ? AND recruiter_id = ?`.
4. **Account Deactivation Protection (GAP-04 Part 1):** `AuthService.login()` rejects authentication for any account with `is_active = FALSE`. `AdminUserService` prevents non-admins from altering account statuses and prevents self-deactivation.
5. **Connection Pool Hardening:** All database access routes through HikariCP with connection validation and auto-commit cleanup.

### 6.2 Identified Security Gaps (Phase 6 Step 4.2 Scope)

| Gap ID | Severity | Component | Description of Vulnerability |
| :--- | :--- | :--- | :--- |
| **GAP-SEC-01** | **CRITICAL** | `RegisterController` / `JobService` | **Unvetted Recruiter Impersonation & Immediate Publishing:** An attacker can register an account claiming to be "Google HR" and immediately broadcast live jobs to students without email verification or admin approval. |
| **GAP-SEC-02** | **CRITICAL** | `JobBoardController` / `JobDAO` | **Instant Exposure of Campus Jobs Without Moderation:** `JobDAO.searchActive()` and `JobDAO.findOpenActiveJobs()` have no check for recruiter verification or admin moderation, exposing unvetted listings directly to students. |
| **GAP-SEC-03** | **HIGH** | `ApplicationService` / `ResumeDAO` | **PII & Resume Harvesting by Unverified Entities:** Students can submit applications to unverified jobs, granting unvetted third parties immediate access to student contact details and resume PDFs. |
| **GAP-SEC-04** | **HIGH** | `AdminDashboardController` | **Lack of Administrative Takedown Controls:** Administrators have no UI button or service method to suspend, unpublish, or reject fraudulent active jobs from the dashboard. |
| **GAP-SEC-05** | **MEDIUM** | `SessionManager` | **In-Memory Session Staleness on Status Change:** When an admin deactivates an account or suspends a recruiter, an existing logged-in recruiter session remains active in memory until the application is restarted. |
| **GAP-SEC-06** | **MEDIUM** | `admin_audit_logs` Schema | **Audit Log Foreign Key Deletion Risk:** `CONSTRAINT fk_admin_audit_target FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE` means deleting a user wipes out their disciplinary audit trail. |

---

## 7. Proposed Recruiter Verification State Model

To eliminate GAP-SEC-01 and GAP-SEC-03, recruiter profiles must transition through a formalized Verification Finite State Machine.

### 7.1 State Definitions
```
                +-------------------+
                |    UNVERIFIED     |  (Initial state after registration)
                +---------+---------+
                          |
             Profile Saved|
              & Submitted |
                          v
                +-------------------+
        +------>|      PENDING      |  (Awaiting placement admin review)
        |       +----+---------+----+
        |            |         |
        |    Approve |         | Reject (with reason)
        |            v         v
        |       +---------+  +----------+
        |       | VERIFIED|  | REJECTED |
        |       +----+----+  +----+-----+
        |            |            |
        |     Suspend|            | Re-edit profile & re-submit
        |            v            |
        |       +---------+       |
        +-------+SUSPENDED|<------+
                +---------+
```

1. **`UNVERIFIED`:**
   - Assigned automatically when a recruiter account is created.
   - Profile incomplete or not yet submitted for institutional review.
   - **Capabilities:** Can log in, edit company profile, browse existing candidate search tools (if enabled), create and edit **DRAFT** jobs.
   - **Restrictions:** **Cannot publish `ACTIVE` jobs.**
2. **`PENDING`:**
   - Triggered when the recruiter completes their corporate profile (`company_name`, `company_website`, `company_description`, `industry`) and requests verification.
   - Displayed in the Admin Dashboard "Recruiter Verification Queue".
   - **Capabilities:** Same as `UNVERIFIED`.
   - **Restrictions:** **Cannot publish `ACTIVE` jobs.** UI displays an informational alert: *"Your company profile is under administrative review. You may prepare draft job listings in the interim."*
3. **`VERIFIED`:**
   - Assigned by an Administrator after inspecting corporate credentials.
   - Records `verified_at` timestamp and `verified_by` admin ID.
   - **Capabilities:** Full privileges. Can publish `ACTIVE` jobs, close jobs, receive student applications, and download candidate resumes.
4. **`REJECTED`:**
   - Assigned by an Administrator when credentials cannot be confirmed, or corporate identity is fraudulent.
   - Requires a mandatory rejection reason (stored in `verification_reason` and logged to `admin_audit_logs`).
   - **Capabilities:** Can log in to view the rejection explanation and update their company profile to re-submit for review. Cannot publish jobs.
5. **`SUSPENDED`:**
   - Assigned by an Administrator if a previously verified recruiter violates placement policy.
   - All active jobs belonging to this recruiter are automatically de-listed or set to `SUSPENDED`.

### 7.2 Permitted Actor Actions Matrix
| Action | `UNVERIFIED` | `PENDING` | `VERIFIED` | `REJECTED` | `SUSPENDED` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Log in to Recruiter Portal | Yes | Yes | Yes | Yes | Yes (Restricted) |
| Edit Company Profile | Yes | Yes | Yes | Yes | No |
| Create / Save DRAFT Job | Yes | Yes | Yes | Yes | No |
| Publish Job as ACTIVE | **NO** | **NO** | **YES** | **NO** | **NO** |
| Receive Applications | No | No | Yes | No | No |
| View Candidate Resumes | No | No | Yes | No | No |
| Admin Approve Recruiter | Yes | Yes | N/A | Yes | Yes |
| Admin Reject Recruiter | Yes | Yes | Yes | N/A | Yes |
| Admin Suspend Recruiter | Yes | Yes | Yes | Yes | N/A |

---

## 8. Proposed Job Moderation State Model

To eliminate GAP-SEC-02 and GAP-SEC-04, the application must decouple the recruiter's publishing lifecycle from the university's moderation lifecycle.

### 8.1 The Two Orthogonal Dimensions
A job posting possesses two distinct status indicators:
1. `jobs.status` (Recruiter Lifecycle): `DRAFT`, `ACTIVE`, `CLOSED`.
2. `jobs.moderation_status` (Administrative Moderation): `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.

```
                    Recruiter Lifecycle (jobs.status)
                   +----------------------------------+
                   |   DRAFT  -->  ACTIVE  --> CLOSED |
                   +-----------------+----------------+
                                     |
                                     | requires approval
                                     v
                 Administrative Moderation (jobs.moderation_status)
                   +----------------------------------+
                   |  PENDING --> APPROVED / REJECTED |
                   |     |                            |
                   |     +------> SUSPENDED           |
                   +----------------------------------+
```

### 8.2 Institutional Moderation Modes
In enterprise and campus placement platforms, there are two common moderation policies:
1. **Pre-Moderation (Strict Campus Policy):** Every job published by any recruiter enters `moderation_status = 'PENDING'` and remains hidden from students until an administrator manually clicks "Approve".
2. **Tiered / Trust-Based Auto-Moderation (Recommended):**
   - When a **`VERIFIED`** recruiter publishes a job, `moderation_status` is automatically set to **`APPROVED`**, allowing immediate publication without bureaucratic delay.
   - Placement administrators retain full post-moderation oversight: they can view all live jobs, search by keyword, and immediately click **"Suspend Job"** or **"Reject Job"** (with an audit-logged reason) if a posting violates policy.
   - If a recruiter is **`UNVERIFIED`** or **`PENDING`**, they are prohibited from publishing `ACTIVE` jobs entirely.

> **Recommendation for HireHub:** Adopt the **Tiered Auto-Moderation** model for `VERIFIED` recruiters, combined with an administrative **Job Moderation & Takedown Queue**. This guarantees instantaneous operational speed for verified institutional partners while providing placement officers with instant takedown authority.

### 8.3 Student Visibility Rule
To ensure no unmoderated or unverified content leaks to students, all student-facing queries (`JobDAO.searchActive()`, `JobDAO.findOpenActiveJobs()`, and `StudentAnalyticsDAO`) must enforce:
```sql
WHERE j.status = 'ACTIVE'
  AND j.moderation_status = 'APPROVED'
  AND (j.deadline IS NULL OR j.deadline >= CURDATE())
  AND u.is_active = TRUE
  AND rp.verification_status = 'VERIFIED'
```

---

## 9. Recommended Database Schema Changes

All schema changes must be 100% backward-compatible, safe for concurrent execution, and idempotent.

### 9.1 Schema Design Decision: Profile vs. User Table
We evaluated whether `verification_status` belongs in `users` or `recruiter_profiles`:
- **Option A (`users` table):** Puts verification alongside `role` and `is_active`. However, students do not have corporate verification, making the column null/irrelevant for student rows.
- **Option B (`recruiter_profiles` table - RECOMMENDED):** `recruiter_profiles` already models recruiter-specific attributes (company name, website, industry). Placing verification fields here keeps `users` clean, maintains normalized role data, and co-locates corporate credentials with company profile details.

### 9.2 Exact DDL Migration Script (`database/phase6_step4_2_migration.sql`)

```sql
-- =============================================================================
-- HireHub Phase 6 Step 4.2 Migration: Recruiter Verification & Job Moderation
-- Safe, Idempotent, Backward-Compatible DDL
-- =============================================================================

-- 1. Ensure all existing RECRUITER users have a row in recruiter_profiles
INSERT INTO recruiter_profiles (user_id, company_name, created_at, updated_at)
SELECT u.id, 'Unspecified Company', NOW(), NOW()
FROM users u
LEFT JOIN recruiter_profiles rp ON u.id = rp.user_id
WHERE u.role = 'RECRUITER' AND rp.id IS NULL;

-- 2. Enhance recruiter_profiles with Verification Columns
ALTER TABLE recruiter_profiles
    ADD COLUMN verification_status ENUM('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED') 
        NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN verification_reason VARCHAR(500) NULL,
    ADD COLUMN verified_at TIMESTAMP NULL,
    ADD COLUMN verified_by INT NULL,
    ADD CONSTRAINT fk_rp_verified_by FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD INDEX idx_rp_verification_status (verification_status),
    ADD INDEX idx_rp_verified_by (verified_by);

-- 3. Grandfather Existing Recruiters as VERIFIED (Prevents Breaking Existing Tests)
UPDATE recruiter_profiles
SET verification_status = 'VERIFIED',
    verified_at = NOW()
WHERE verification_status = 'UNVERIFIED';

-- 4. Enhance jobs with Moderation Columns
ALTER TABLE jobs
    ADD COLUMN moderation_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED') 
        NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN moderation_reason VARCHAR(500) NULL,
    ADD COLUMN moderated_at TIMESTAMP NULL,
    ADD COLUMN moderated_by INT NULL,
    ADD CONSTRAINT fk_jobs_moderated_by FOREIGN KEY (moderated_by) REFERENCES users(id) ON DELETE SET NULL,
    ADD INDEX idx_jobs_moderation_status (moderation_status),
    ADD INDEX idx_jobs_status_moderation (status, moderation_status),
    ADD INDEX idx_jobs_moderated_by (moderated_by);

-- 5. Grandfather Existing Jobs as APPROVED
UPDATE jobs
SET moderation_status = 'APPROVED',
    moderated_at = NOW()
WHERE moderation_status IS NULL OR moderation_status = 'PENDING';

-- 6. Enhance admin_audit_logs to Support Job & Recruiter Moderation Entities
ALTER TABLE admin_audit_logs
    MODIFY COLUMN target_user_id INT NULL,
    ADD COLUMN target_job_id INT NULL AFTER target_user_id,
    ADD COLUMN entity_type ENUM('USER', 'RECRUITER_PROFILE', 'JOB') NOT NULL DEFAULT 'USER' AFTER id,
    ADD CONSTRAINT fk_admin_audit_job FOREIGN KEY (target_job_id) REFERENCES jobs(id) ON DELETE SET NULL,
    ADD INDEX idx_audit_job (target_job_id),
    ADD INDEX idx_audit_entity (entity_type);
```

---

## 10. Backward Compatibility and Legacy Data Migration Strategy

A primary requirement of Phase 6 Step 4.2 is that **existing test suites, automated verifiers, and test fixtures must continue passing without breakage**.

### 10.1 Preserving Existing Baseline Data
HireHub currently has 435 passing tests across 9 regression verifiers:
1. In existing test suites (e.g. `Phase5Step3ASecurityVerifier`, `Phase5RecruiterAnalyticsVerifier`, `Phase6Step2ResumeSnapshotVerifier`), existing recruiters post jobs and evaluate applications.
2. If newly added columns defaulted to `UNVERIFIED` and `PENDING`, existing test runners would fail because their legacy recruiter accounts would suddenly be blocked from publishing jobs.
3. **Migration Solution:**
   - New column `recruiter_profiles.verification_status` defaults to `'UNVERIFIED'` for future registrations.
   - However, during the initial execution of `phase6_step4_2_migration.sql`, an explicit `UPDATE recruiter_profiles SET verification_status = 'VERIFIED'` grandfathers all existing database records.
   - Similarly, `jobs.moderation_status` defaults to `'APPROVED'`, ensuring that all previously created active jobs remain active and discoverable.

### 10.2 Idempotent Migration Runner
Following the pattern established by `ApplyMigration4_1.java`, Step 4.2 must provide an automated Java runner: `com.hirehub.util.ApplyMigration4_2.java`.
- Inspects `information_schema.columns` to check if `verification_status` and `moderation_status` exist before attempting `ALTER TABLE`.
- Safely handles re-runs without SQL syntax errors or duplicate foreign key exceptions.

---

## 11. Proposed Admin Dashboard UI and Navigation

The Admin Dashboard (`admin_dashboard.fxml` and `AdminDashboardController.java`) currently includes 6 navigation buttons:
- `navOverviewBtn` (Overview KPI metrics)
- `navJobsBtn` (Job Analytics metrics and top jobs table)
- `navStudentsBtn` (Student Analytics)
- `navRecruitersBtn` (Recruiter Analytics)
- `navUsersBtn` (User Accounts & Moderation - Step 4.1)
- `navActivityBtn` (Audit Activity Log)

### 11.1 Architectural UI Integration Options
We evaluated two integration approaches:
- **Approach 1 (Separate Navigation Buttons):** Add two new sidebar items: "Recruiter Approvals" and "Job Moderation".
  - *Drawback:* Adds clutter to the left navigation bar; sidebar width is fixed (220px).
- **Approach 2 (Tabbed Sub-Views in Existing Panes - RECOMMENDED):**
  - In `usersPane` (User Management), add a sub-tab or filter segment: **"All Users"** vs **"Recruiter Verifications"**.
  - In `jobsPane`, complement the existing analytical summary cards with a moderation management table: **"Job Moderation Queue"** (with status badges: `APPROVED`, `PENDING`, `REJECTED`, `SUSPENDED`).
  - Alternatively, create a unified **"Governance & Moderation"** workspace in `usersPane` or add dedicated **`navVerificationsBtn`** ("Recruiter Approvals") and **`navJobModBtn`** ("Job Moderation") directly into the navigation list.

### 11.2 Proposed Layout: Recruiter Verification Queue
Located within the Admin Dashboard workspace:
```
+-------------------------------------------------------------------------------------------------------------+
| RECRUITER VERIFICATION QUEUE                                                   [Refresh]  [Filter: PENDING v] |
+-------------------------------------------------------------------------------------------------------------+
| ID | Recruiter Name  | Company Name      | Website            | Industry    | Submitted  | Status   | Actions   |
+----+-----------------+-------------------+--------------------+-------------+------------+----------+-----------+
| 12 | Sarah Jenkins   | Apex FinTech Ltd  | apexfintech.com    | Finance     | 2026-11-13 | PENDING  | [Approve] |
|    |                 |                   |                    |             |            |          | [Reject]  |
+----+-----------------+-------------------+--------------------+-------------+------------+----------+-----------+
| 18 | Marcus Vance    | CloudScale Inc    | cloudscale.io      | Software    | 2026-11-12 | VERIFIED | [Suspend] |
+----+-----------------+-------------------+--------------------+-------------+------------+----------+-----------+
```
- **Approve Action:** Displays confirmation dialog. On confirm, sets `verification_status = 'VERIFIED'`, logs audit entry, updates table row badge to green.
- **Reject Action:** Opens a modal text prompt: *"Please enter the reason for rejection (this will be visible to the recruiter):"*. On submit, sets `verification_status = 'REJECTED'`, persists reason, logs audit entry.

### 11.3 Proposed Layout: Job Moderation Queue
```
+-------------------------------------------------------------------------------------------------------------+
| JOB POSTING MODERATION & TAKEDOWN QUEUE                                        [Search...] [Status: ALL v]  |
+-------------------------------------------------------------------------------------------------------------+
| ID | Title                 | Company       | Recruiter Status | Lifecycle | Moderation | Actions            |
+----+-----------------------+---------------+------------------+-----------+------------+--------------------+
| 42 | Senior Java Engineer  | Apex FinTech  | VERIFIED         | ACTIVE    | APPROVED   | [Suspend] [Reject] |
| 45 | Crypto Trader Intern  | Unknown LLC   | UNVERIFIED       | ACTIVE    | PENDING    | [Approve] [Reject] |
+-------------------------------------------------------------------------------------------------------------+
```
- **Suspend Action:** Takes down a live job immediately without deleting historical applications. Updates `moderation_status = 'SUSPENDED'`. The job vanishes from the student job board instantly.

---

## 12. Proposed Recruiter UI Changes

Recruiters must receive transparent visual feedback regarding their verification state and any restrictions placed on their posting privileges.

### 12.1 Recruiter Dashboard Banner / Badge (`recruiter_dashboard.fxml`)
In the recruiter dashboard header:
- **If `UNVERIFIED`:** Yellow pill badge: `⚠️ Verification Required — Complete profile to request posting privileges`.
- **If `PENDING`:** Blue pill badge: `⏳ Verification Pending — Under administrative review`.
- **If `VERIFIED`:** Green badge: `✓ Verified Partner`.
- **If `REJECTED`:** Red pill badge: `⛔ Verification Rejected — Reason: [Reason Text] — Click to update profile`.

### 12.2 Job Form Publish Button Hardening (`job_form.fxml` & `JobFormController.java`)
In `JobFormController.java`:
```java
private void updatePublishPermissions() {
    RecruiterProfile profile = recruiterService.getProfile(sessionRecruiterId);
    boolean isVerified = profile != null && profile.getVerificationStatus() == VerificationStatus.VERIFIED;

    if (!isVerified) {
        publishButton.setDisable(true);
        publishButton.setTooltip(new Tooltip(
            "Account verification is required to publish live job postings. You may save this job as a Draft."
        ));
        verificationWarningBox.setVisible(true);
        verificationWarningBox.setManaged(true);
    } else {
        publishButton.setDisable(false);
        publishButton.setTooltip(null);
        verificationWarningBox.setVisible(false);
        verificationWarningBox.setManaged(false);
    }
}
```
- **Fail-Closed Client Guard:** If `publishButton` is clicked (e.g. via keyboard shortcut), `submitJob(asDraft = false)` immediately aborts if `!isVerified`.

---

## 13. Service-Layer and DAO Authorization Design

Relying on UI disabling is insufficient. The security boundary must be strictly enforced at the Service and DAO layers.

### 13.1 Service-Layer Verification Contracts
In `JobService.java`:
```java
public ServiceResult createJob(int recruiterId, String title, String companyName,
                              String description, String skills, String location,
                              Job.JobType type, String salary, String eligibility,
                              LocalDate deadline, boolean asDraft) {
    // 1. Session Authentication & Recruiter Role Guard
    if (!SessionManager.getInstance().hasRole(User.Role.RECRUITER)) {
        return ServiceResult.fail("Unauthorized: Recruiter session required.");
    }
    int sessionUserId = SessionManager.getInstance().getCurrentUser().getId();
    if (recruiterId <= 0 || recruiterId != sessionUserId) {
        return ServiceResult.fail("Security violation: Recruiter ID mismatch.");
    }

    // 2. Publish Authorization Guard (GAP-SEC-01 Closure)
    if (!asDraft) {
        RecruiterProfile profile = recruiterDAO.findByUserId(recruiterId);
        if (profile == null || profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return ServiceResult.fail("Account unverified: Only verified recruiters can publish live jobs.");
        }
    }

    // 3. Status assignment
    Job.Status status = asDraft ? Job.Status.DRAFT : Job.Status.ACTIVE;
    Job.ModerationStatus modStatus = asDraft ? Job.ModerationStatus.PENDING : Job.ModerationStatus.APPROVED;

    // 4. Persistence
    Job job = new Job(...);
    job.setStatus(status);
    job.setModerationStatus(modStatus);
    int jobId = jobDAO.insert(job);
    return ServiceResult.ok("Job created successfully.", jobId);
}
```

In `JobService.java` (`updateJob`):
```java
if (newStatus == Job.Status.ACTIVE) {
    RecruiterProfile profile = recruiterDAO.findByUserId(recruiterId);
    if (profile == null || profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
        return ServiceResult.fail("Account unverified: Cannot transition job to ACTIVE status.");
    }
}
```

### 13.2 Dedicated Admin Moderation Service (`AdminModerationService.java`)
A new dedicated service to handle administrative approvals:
```java
public class AdminModerationService {

    public ServiceResult verifyRecruiter(int adminId, int recruiterId, VerificationStatus newStatus, String reason) {
        requireAdminRole(adminId);
        if (recruiterId <= 0 || newStatus == null) {
            throw new IllegalArgumentException("Invalid recruiter ID or target status.");
        }
        if (newStatus == VerificationStatus.REJECTED && (reason == null || reason.isBlank())) {
            return ServiceResult.fail("A rejection reason is required.");
        }

        boolean success = adminModerationDAO.updateRecruiterVerificationWithAudit(
            adminId, recruiterId, newStatus, reason
        );
        return success ? ServiceResult.ok("Recruiter status updated.") : ServiceResult.fail("Failed to update status.");
    }

    public ServiceResult moderateJob(int adminId, int jobId, Job.ModerationStatus newStatus, String reason) {
        requireAdminRole(adminId);
        if (jobId <= 0 || newStatus == null) {
            throw new IllegalArgumentException("Invalid job ID or target status.");
        }

        boolean success = adminModerationDAO.updateJobModerationWithAudit(
            adminId, jobId, newStatus, reason
        );
        return success ? ServiceResult.ok("Job moderation status updated.") : ServiceResult.fail("Failed to update job.");
    }

    private void requireAdminRole(int adminId) {
        if (!SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            throw new SecurityException("Caller is not authenticated as ADMIN.");
        }
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null || current.getId() != adminId) {
            throw new SecurityException("Authenticated admin ID mismatch.");
        }
    }
}
```

---

## 14. Transaction and Concurrency Requirements

When an administrator moderates a recruiter or job, multiple tables must be updated atomically to prevent race conditions or orphaned audit logs.

### 14.1 Atomic Status Update & Audit Logging
In `AdminModerationDAO.java`:
```java
public boolean updateRecruiterVerificationWithAudit(int adminId, int recruiterId, 
                                                   VerificationStatus status, String reason) throws SQLException {
    String updateSql = """
        UPDATE recruiter_profiles 
        SET verification_status = ?, verification_reason = ?, verified_at = NOW(), verified_by = ? 
        WHERE user_id = ?
    """;
    String auditSql = """
        INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, action, reason, created_at)
        VALUES ('RECRUITER_PROFILE', ?, ?, ?, ?, NOW())
    """;

    try (Connection conn = DatabaseConnection.getConnection()) {
        conn.setAutoCommit(false);
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
             PreparedStatement auditStmt  = conn.prepareStatement(auditSql)) {

            updateStmt.setString(1, status.name());
            updateStmt.setString(2, reason);
            updateStmt.setInt(3, adminId);
            updateStmt.setInt(4, recruiterId);
            int rowsUpdated = updateStmt.executeUpdate();

            if (rowsUpdated == 0) {
                conn.rollback();
                return false;
            }

            auditStmt.setInt(1, adminId);
            auditStmt.setInt(2, recruiterId);
            auditStmt.setString(3, "RECRUITER_" + status.name());
            auditStmt.setString(4, reason);
            auditStmt.executeUpdate();

            conn.commit();
            return true;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
```

### 14.2 Concurrency Race Condition Mitigation
**The Scenario:** A recruiter clicks "Publish Job" at the exact millisecond an administrator clicks "Reject Recruiter".
- **Mitigation:** In `JobDAO.insert()` and `JobDAO.updateForRecruiter()`, verify recruiter verification status either inside an active transaction or with a conditional subquery:
  ```sql
  INSERT INTO jobs (recruiter_id, title, ..., status, moderation_status)
  SELECT ?, ?, ..., 'ACTIVE', 'APPROVED'
  FROM recruiter_profiles rp
  JOIN users u ON u.id = rp.user_id
  WHERE rp.user_id = ? 
    AND rp.verification_status = 'VERIFIED' 
    AND u.is_active = TRUE;
  ```
  If the recruiter is rejected concurrently, the `INSERT` selects zero rows and inserts nothing, failing safe.

---

## 15. Audit Logging Requirements

Administrative accountability requires full traceability of all verification and moderation actions.

### 15.1 Audit Actions Catalog
The `action` column in `admin_audit_logs` must record standard actions:
- `RECRUITER_VERIFIED`: Recruiter profile approved by admin.
- `RECRUITER_REJECTED`: Recruiter profile rejected by admin (mandatory reason).
- `RECRUITER_SUSPENDED`: Recruiter profile suspended by admin.
- `JOB_APPROVED`: Job listing approved for student discovery.
- `JOB_REJECTED`: Job listing rejected (mandatory reason).
- `JOB_SUSPENDED`: Live job taken down by admin.

### 15.2 Schema Evolution for Multi-Entity Audit Logs
In Step 4.1, `admin_audit_logs` was designed with:
```sql
target_user_id INT NOT NULL,
CONSTRAINT fk_admin_audit_target FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE
```
**Two Architectural Deficiencies:**
1. **Cascade Deletion:** If a user is deleted, their disciplinary audit trail is erased. The FK constraint should be modified to `ON DELETE SET NULL`.
2. **Job Entities:** Moderating a job involves a `job_id`, not just a `user_id`. Adding `target_job_id INT NULL` and `entity_type ENUM('USER', 'RECRUITER_PROFILE', 'JOB')` allows auditing both users and job postings cleanly in a single table.

---

## 16. Application and Historical Data Preservation Strategy

When a job or recruiter status changes, the integrity of existing applications and student records must be preserved.

### 16.1 Cascading Effects of Recruiter Suspension
If a verified recruiter is subsequently suspended or deactivated:
1. **Existing Applications Preserved:** All historical applications submitted by students remain intact in `applications`.
2. **Resumes Preserved:** All resume snapshots associated with prior applications remain accessible for university records and accreditation audits.
3. **Active Jobs Auto-Hidden:** Any active jobs belonging to that recruiter must be immediately hidden from student job search (`rp.verification_status = 'VERIFIED'` check in SQL automatically handles this without mutating job rows).

### 16.2 Behavior on Job Rejection / Suspension
- If an admin rejects or suspends a job that has already received applications, the applications are **not** deleted.
- In `MyApplicationsController.java`, the student sees their application status alongside a badge: `Listing Suspended by Admin`.
- The recruiter can no longer hire or update status for a suspended job until an appeal is resolved.

---

## 17. Test Plan

To maintain HireHub's high quality standards, Step 4.2 must be backed by a comprehensive automated test suite and zero regression in existing verifiers.

### 17.1 New Verifier: `Phase6Step4_2RecruiterVerificationJobModerationVerifier.java`
The new verifier must validate the following 8 test suites:
1. **Schema Integrity:** Verify existence of `verification_status`, `moderation_status`, indexes, foreign keys, and audit log columns.
2. **Recruiter Registration Baseline:** Verify new recruiter registers as `UNVERIFIED`.
3. **Recruiter Publish Restrictions:** Verify `UNVERIFIED` and `PENDING` recruiters cannot set `status = ACTIVE` (must fail with `ServiceResult.fail`). Verify they CAN save `DRAFT`.
4. **Admin Recruiter Verification:** Verify admin can transition recruiter to `VERIFIED` and `REJECTED`, requiring reason on rejection. Verify atomic audit log generation.
5. **Verified Recruiter Publishing:** Verify `VERIFIED` recruiter can successfully publish `ACTIVE` jobs.
6. **Student Job Board Filtering:** Verify student queries return only `ACTIVE` + `APPROVED` jobs from `VERIFIED` recruiters. Verify unverified or suspended jobs are omitted.
7. **Admin Job Moderation & Takedown:** Verify admin can suspend an active job, verifying it disappears from student search immediately.
8. **Concurrency & Rollback:** Verify atomic rollback if audit log insertion fails during status transition.

### 17.2 Full Regression Verifier Checklist
Before Step 4.2 is marked complete, all preceding verification suites must execute cleanly:
- [x] `Phase5Step3ASecurityVerifier` (Recruiter Ownership & Security)
- [x] `Phase5RecruiterAnalyticsVerifier` (Recruiter Analytics KPI Engine)
- [x] `Phase5AnalyticsVerifier` (Admin Analytics Engine)
- [x] `RecruiterApplicantVerifier` (Applicant Review Pipeline)
- [x] `Phase5Step4BSecurityVerifier` (Student Security & Analytics Backend)
- [x] `Phase6Step1RecruiterSecurityVerifier` (Recruiter Service Authorization)
- [x] `Phase6Step2ResumeSnapshotVerifier` (Resume Snapshots & Secure Recruiter Access)
- [x] `Phase6Step3ConnectionPoolVerifier` (HikariCP Connection Pool Integrity)
- [x] `Phase6Step4_1AdminUserManagementVerifier` (Admin User Management & Audit Logging)

---

## 18. Risk Assessment

| Risk Description | Likelihood | Impact | Proposed Mitigation |
| :--- | :---: | :---: | :--- |
| **Recruiter Friction Due to Approval Delay** | High | Medium | Implement clear UI messaging informing recruiters that drafts can be drafted immediately while verification is pending. Provide rapid admin approval workflows. |
| **Breaking Existing Test Fixtures** | High | Critical | Execute migration with backward-compatible defaults and grandfather existing recruiters as `VERIFIED` and existing jobs as `APPROVED`. |
| **Stale In-Memory Sessions** | Medium | High | Re-validate `verification_status` and `is_active` directly from the database at the start of mutating operations (`createJob`, `updateJob`, `publishJob`). |
| **Performance Overhead on Student Search** | Low | Low | Add composite indexes on `jobs(status, moderation_status)` and `recruiter_profiles(verification_status)`. |
| **Accidental Data Loss on User Deletion** | Medium | High | Change audit log foreign keys from `ON DELETE CASCADE` to `ON DELETE SET NULL`. |

---

## 19. Implementation Phases and Dependencies

The implementation of Phase 6 Step 4.2 should proceed in six sequential, decoupled phases:

```
+---------------------------------------------------------------------------------------+
| PHASE 4.2A: Database Migration & Schema Setup                                        |
| - Execute database/phase6_step4_2_migration.sql                                      |
| - Grandfather legacy records                                                          |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
| PHASE 4.2B: Model & DAO Modernization                                                 |
| - Update RecruiterProfile & Job models with verification/moderation enums             |
| - Update RecruiterDAO & JobDAO query methods                                         |
| - Create AdminModerationDAO for atomic updates                                        |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
| PHASE 4.2C: Service-Layer Security & Authorization                                    |
| - Harden JobService.createJob() and updateJob() with verification checks             |
| - Implement AdminModerationService with fail-closed admin checks                     |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
| PHASE 4.2D: Recruiter UI Hardening                                                    |
| - Add verification status banner to recruiter dashboard                              |
| - Conditionally disable "Publish Job" button in JobFormController                    |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
| PHASE 4.2E: Admin Moderation UI                                                       |
| - Add Recruiter Verification Queue to Admin Dashboard                                |
| - Add Job Moderation & Takedown Queue to Admin Dashboard                              |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
| PHASE 4.2F: Automated Verification & Regression Testing                               |
| - Build Phase6Step4_2RecruiterVerificationJobModerationVerifier                       |
| - Execute all 9 preceding regression suites to verify 100% pass rate                  |
+---------------------------------------------------------------------------------------+
```

---

## 20. Final Recommendation & Gaps Ranked by Severity

### Prioritized Gap Hierarchy
1. **P0 (Critical Security & Governance):**
   - Recruiter accounts register as active without verification.
   - Unverified recruiters can publish active jobs.
   - Student job queries expose unvetted jobs and allow PII/resume submission.
2. **P1 (Administrative Governance & Operations):**
   - No administrative job takedown or moderation controls in Admin Dashboard.
   - No structured verification queue for placement administrators.
3. **P2 (UX & Audit Resilience):**
   - Lack of visual status pill/banner in Recruiter Dashboard.
   - Potential loss of audit trails due to `ON DELETE CASCADE` on `admin_audit_logs`.

### Final Architectural Sign-Off
Implementing Phase 6 Step 4.2 according to the Two-Axis Governance Model will close the single largest remaining operational risk in HireHub, transforming it from a prototype job portal into a secure, accredited-ready institutional placement platform.

---

## Verification & Read-Only Audit Confirmations

1. **Zero Source Code Files Modified:**  
   `Get-ChildItem -Path D:\HireHub\src -Recurse | Where-Object { $_.LastWriteTime -gt (Get-Date).AddHours(-1) }` confirmed that **zero** `.java`, `.fxml`, or `.css` files were modified or created during this audit.
2. **Zero Database Records or Schemas Modified:**  
   No `ALTER`, `UPDATE`, `INSERT`, `DELETE`, or `DROP` statements were executed on `hirehub_db`. All table schemas and record counts remain identical to the post-Step 4.1 baseline.
3. **Exact Files Inspected:**  
   Every file listed in Section 3 was read directly and analyzed in its live workspace context.
4. **Regression Suites to Rerun Post-Implementation:**  
   All 9 existing test verifiers (totaling 435 tests) must be re-executed following the implementation of Step 4.2 to guarantee complete backward compatibility.

