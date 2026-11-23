# HIREHUB: AI-POWERED INTERNSHIP & PLACEMENT MANAGEMENT SYSTEM
## Comprehensive Technical Project Report & Architectural Specification

**Project Location:** `D:\HireHub`  
**System Version:** 1.0.0 (Production Ready — Phase 1 through Phase 6 Complete)  
**Academic / Technical Tier:** Capstone / Enterprise Desktop System  
**Document Classification:** Final Technical & Architectural Documentation  

---

## 1. ABSTRACT & EXECUTIVE SUMMARY

Modern campus placement cells and graduate recruitment drives suffer from fragmented communication, opaque candidate screening, vulnerability to unverified recruiters, and manual, labor-intensive resume evaluation. Traditional web-based portals often introduce high server maintenance overhead, complex multi-tier deployment friction, and sluggish user feedback.

**HireHub** is an enterprise-grade, offline-first, client-desktop Internship and Placement Management Platform developed in **Java 21** and **JavaFX 21**, backed by **MySQL 8.0** with **HikariCP connection pooling**, and integrated with ultra-fast LLM inference powered by **GroqCloud** (`openai/gpt-oss-20b` with `llama-3.3-70b-versatile` fallback). 

HireHub bridges the tripartite relationship between **Students**, **Recruiters**, and **Placement Administrators (Admins)** within a unified, reactive graphical environment. The system implements:
1. **Robust Role-Based Authentication & Security**: Salted BCrypt password hashing (cost factor 12), session state management, atomic database access with prepared statements, and role-boundary validation.
2. **Deterministic & AI Resume Analysis**: A hybrid evaluation engine pairing a deterministic, local multi-criteria algorithm (40% skills, 25% experience, 15% education, 10% completeness, 10% certifications) with GroqCloud LLM-driven ATS alignment critiques, Google X-Y-Z bullet rewrites, 4-week preparation plans, and automated interview question generation.
3. **Application Resume Snapshotting (GAP-05)**: Immutable point-in-time resume version preservation ensuring recruiters always review the exact artifact submitted at application time, secured against cross-tenant unauthorized access via atomic SQL join constraints.
4. **Dual Moderation & Anti-Fraud Engine (Phase 6 Step 4)**: Independent recruiter verification workflows (`UNVERIFIED` → `PENDING` → `VERIFIED` / `REJECTED` / `SUSPENDED`) and job moderation workflows (`PENDING` → `APPROVED` / `REJECTED` / `SUSPENDED`), backed by atomic database re-moderation triggers on substantive job edits.
5. **Multi-Role Real-Time Analytics Dashboards**: Interactive metrics tracking placement funnels, recruiter engagement, skill supply-demand gaps, and administrative audit trails.

The entire codebase comprises **129 Java classes**, zero ORM bloat, zero frontend node dependencies, and achieves 100% test pass rates across all verification suites.

---

## 2. SYSTEM ARCHITECTURE & DESIGN PATTERNS

HireHub strictly adheres to the classical **Three-Tier Desktop Architecture** utilizing the **MVC (Model-View-Controller) + DAO (Data Access Object) + Service Layer** pattern.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION TIER (UI)                          │
│   JavaFX 21 Desktop GUI | FXML Scene Graph | Global CSS Styling        │
│   Controllers: Login, Register, Student, Recruiter, Admin, ATS, AI     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Method Invocations / DTOs
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        SERVICE & BUSINESS TIER                         │
│   AuthService | StudentService | RecruiterService | AdminService       │
│   JobService | ApplicationService | ResumeService                      │
│   AIService (GroqCloud Client & Circuit Breaker)                       │
│   AdminModerationService | Analytics Services                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Transaction Boundaries / Entities
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       DATA ACCESS LAYER (DAO)                          │
│   UserDAO | StudentDAO | RecruiterDAO | JobDAO | ApplicationDAO        │
│   ResumeDAO | ResumeAnalysisDAO | AdminModerationDAO | AuditDAO        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HikariCP Thread-Safe Connection Pool
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        PERSISTENCE TIER (DB)                           │
│   MySQL 8.0 InnoDB Engine (ACID Compliant)                             │
│   hirehub_db: Users, Profiles, Jobs, Resumes, Analyses, Audit Logs     │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Design Patterns Applied
- **Model-View-Controller (MVC)**: Decouples UI structure (`.fxml`), styling (`style.css`), and event handling (`*Controller.java`).
- **Data Access Object (DAO)**: Abstract persistence mechanisms. SQL logic is isolated in DAO classes using `PreparedStatement` exclusively.
- **Service Layer Pattern**: Encapsulates business rules, input validations, security boundaries, and transaction coordination. Controllers never invoke DAOs directly.
- **Singleton Pattern**:
  - `DatabaseConnection`: Centralized HikariCP `HikariDataSource` lifecycle with double-checked locking and runtime JVM shutdown hooks.
  - `SessionManager`: Thread-safe, in-memory current user session store cleared immediately on logout.
- **Circuit Breaker Pattern (`CircuitBreaker.java`)**: Protects external network operations against cascading timeouts when contacting GroqCloud. Transitions through `CLOSED`, `OPEN`, and `HALF_OPEN` states.
- **Data Transfer Object (DTO)**: Immutable snapshot models (e.g., `AdminDashboardSnapshot`, `RecruiterDashboardSnapshot`, `ParsedResume`, `AdminAuditLog`) aggregating multi-table queries for UI consumption.
- **Asynchronous Worker Pattern (`javafx.concurrent.Task`)**: All long-running operations (database queries, PDF parsing, GroqCloud API calls) are offloaded to background daemon threads to maintain a silky 60 FPS UI without freezing the JavaFX Application Thread.

---

## 3. TECHNOLOGY STACK & SPECIFICATIONS

| Component | Technology | Version | Purpose / Justification |
|---|---|---|---|
| **Programming Language** | Java (JDK) | 21 LTS | Strong typing, virtual threads, records, pattern matching, robust standard library (`java.net.http`). |
| **GUI Framework** | JavaFX | 21.0.5 | Native desktop hardware acceleration, declarative FXML, rich control library, smooth CSS styling. |
| **Relational Database** | MySQL Server | 8.0+ | ACID transactions, foreign key constraints, InnoDB storage engine, indexed JSON/text storage. |
| **Connection Pooling** | HikariCP | 5.1.0 | Ultra-lightweight, zero-overhead JDBC connection pool replacing legacy DriverManager connections. |
| **Database Driver** | MySQL Connector/J | 8.3.0 | Native Type-4 JDBC driver optimized for MySQL 8. |
| **Cryptography** | jBCrypt | 0.4 | Adaptive one-way password hashing using blowfish cipher with work factor 12. |
| **JSON Serialization** | Google Gson | 2.10.1 | High-performance JSON parsing and schema validation for GroqCloud payloads and resume structures. |
| **Document Processing** | Apache PDFBox | 2.0.31 | Extraction of plain text and metadata from applicant PDF resumes. |
| **AI Inference** | GroqCloud API | REST v1 | OpenAI-compatible Chat Completions endpoint (`openai/gpt-oss-20b`, `llama-3.3-70b-versatile`) with LPU hardware acceleration. |
| **Build System** | Apache Maven | 3.9.6 | Standard dependency management and compilation pipeline. |

---

## 4. DATABASE DESIGN & SCHEMA ARCHITECTURE

The persistence layer is modeled in MySQL under the schema `hirehub_db`. All tables utilize `ENGINE=InnoDB` with `utf8mb4` character encoding.

### 4.1 Entity Relationship Overview

```
                        ┌──────────────────┐
                        │      users       │
                        └────────┬─────────┘
         ┌───────────────────────┼───────────────────────┐
         │ 1:1                   │ 1:1                   │ 1:N
         ▼                       ▼                       ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ student_profiles │    │recruiter_profiles│    │ admin_audit_logs │
└──────────────────┘    └────────┬─────────┘    └──────────────────┘
                                 │ 1:N
                                 ▼
                        ┌──────────────────┐
                        │       jobs       │
                        └────────┬─────────┘
         ┌───────────────────────┼───────────────────────┐
         │ 1:N                   │                       │
         ▼                       │                       │
┌──────────────────┐             │                       │
│     resumes      │             │                       │
└────────┬─────────┘             │                       │
         │ 1:N                   │                       │
         ├───────────────────────┼───────────────────────┘
         │                       │
         ▼                       ▼
┌──────────────────┐    ┌──────────────────┐
│ resume_analyses  │    │   applications   │
└──────────────────┘    └──────────────────┘
```

### 4.2 Relational Tables Specification

1. **`users`**:
   - `id` (INT, PK, AUTO_INCREMENT)
   - `full_name` (VARCHAR 100), `email` (VARCHAR 150, UNIQUE), `password_hash` (VARCHAR 255)
   - `role` (ENUM: `'STUDENT'`, `'RECRUITER'`, `'ADMIN'`)
   - `is_active` (BOOLEAN, DEFAULT TRUE)
   - `created_at` (TIMESTAMP)

2. **`student_profiles`**:
   - `id` (INT, PK), `user_id` (INT, FK -> users.id, UNIQUE, ON DELETE CASCADE)
   - `phone` (VARCHAR 20), `college` (VARCHAR 150), `degree` (VARCHAR 100), `graduation_year` (INT)
   - `skills` (TEXT), `resume_path` (VARCHAR 500), `updated_at` (TIMESTAMP)

3. **`recruiter_profiles`**:
   - `id` (INT, PK), `user_id` (INT, FK -> users.id, UNIQUE, ON DELETE CASCADE)
   - `company_name` (VARCHAR 150), `company_website` (VARCHAR 300), `company_description` (TEXT), `industry` (VARCHAR 100)
   - `verification_status` (ENUM: `'UNVERIFIED'`, `'PENDING'`, `'VERIFIED'`, `'REJECTED'`, `'SUSPENDED'`)
   - `verification_reason` (VARCHAR 500)
   - `verified_at` (TIMESTAMP NULL), `verified_by` (INT FK -> users.id ON DELETE SET NULL)
   - `updated_at` (TIMESTAMP)

4. **`jobs`**:
   - `id` (INT, PK), `recruiter_id` (INT FK -> users.id ON DELETE CASCADE)
   - `title` (VARCHAR 200), `company_name` (VARCHAR 150), `description` (TEXT), `required_skills` (TEXT), `location` (VARCHAR 150)
   - `job_type` (ENUM: `'INTERNSHIP'`, `'FULL_TIME'`, `'PART_TIME'`)
   - `salary_range` (VARCHAR 100), `eligibility` (TEXT), `deadline` (DATE)
   - `status` (ENUM: `'ACTIVE'`, `'DRAFT'`, `'CLOSED'`) — *Job Lifecycle*
   - `moderation_status` (ENUM: `'PENDING'`, `'APPROVED'`, `'REJECTED'`, `'SUSPENDED'`) — *Platform Moderation*
   - `moderation_reason` (VARCHAR 500), `moderated_at` (TIMESTAMP NULL), `moderated_by` (INT FK -> users.id)
   - `created_at` (TIMESTAMP)

5. **`resumes`**:
   - `id` (INT, PK), `student_id` (INT FK -> users.id ON DELETE CASCADE)
   - `file_name` (VARCHAR 255), `stored_path` (VARCHAR 500), `file_size_bytes` (BIGINT), `file_hash` (VARCHAR 64 SHA-256)
   - `parsed_text` (MEDIUMTEXT), `parsed_json` (TEXT), `is_active` (BOOLEAN DEFAULT TRUE)
   - `uploaded_at` (TIMESTAMP), `updated_at` (TIMESTAMP)

6. **`applications`** *(Enhanced with Immutable Snapshot)*:
   - `id` (INT, PK), `job_id` (INT FK -> jobs.id ON DELETE CASCADE), `student_id` (INT FK -> users.id ON DELETE CASCADE)
   - `resume_id` (INT FK -> resumes.id ON DELETE RESTRICT ON UPDATE CASCADE) — *Immutable snapshot at submission*
   - `cover_letter` (TEXT), `status` (VARCHAR 30: `'PENDING'`, `'REVIEWING'`, `'SHORTLISTED'`, `'REJECTED'`, `'ACCEPTED'`)
   - `applied_at` (TIMESTAMP), `updated_at` (TIMESTAMP)
   - `UNIQUE KEY uq_application (job_id, student_id)`

7. **`resume_analyses`**:
   - `id` (INT, PK), `resume_id` (INT FK -> resumes.id ON DELETE SET NULL), `job_id` (INT NULLable), `student_id` (INT)
   - Scores: `overall_score`, `skills_score`, `experience_score`, `education_score`, `completeness_score`, `certifications_score` (All INT)
   - `matching_skills` (TEXT), `missing_skills` (TEXT)
   - AI evaluations: `analysis_summary` (MEDIUMTEXT), `quality_feedback` (MEDIUMTEXT), `improvement_plan` (MEDIUMTEXT)
   - `created_at` (TIMESTAMP)

8. **`admin_audit_logs`**:
   - `id` (INT, PK), `entity_type` (ENUM: `'USER'`, `'RECRUITER_PROFILE'`, `'JOB'`), `admin_id` (INT FK -> users.id)
   - `target_user_id` (INT NULLable FK -> users.id ON DELETE SET NULL)
   - `target_job_id` (INT NULLable FK -> jobs.id ON DELETE SET NULL)
   - `action` (VARCHAR 32), `reason` (VARCHAR 500), `created_at` (TIMESTAMP)

---

## 5. DETAILED MODULE WORKFLOWS & FUNCTIONAL SPECIFICATIONS

### 5.1 Role-Based Authentication & Session Security
- **Registration**: Allows self-registration for `STUDENT` and `RECRUITER` roles. Validates password complexity (minimum 8 chars, 1 uppercase, 1 lowercase, 1 digit). Generates BCrypt hash with 12 salt rounds.
- **Admin Seeding**: `ADMIN` accounts cannot be registered via UI. They are deterministically initialized in MySQL to prevent privilege escalation attacks.
- **Login Flow**: Looks up user by email. Performs timing-safe BCrypt hash verification. Populates `SessionManager` with active user context.
- **Session Protection**: All controllers check `SessionManager.getInstance().isLoggedIn()` during view initialization. Unauthenticated access immediately redirects to the Login scene.

### 5.2 Student Placement & Resume Analytics Module
- **Profile Management**: Students maintain personal information, college name, degree, graduation year, and technical skills.
- **Resume Upload & Parsing**: Accepts PDF files up to 10MB. `ResumeParserService` invokes Apache PDFBox to extract raw text, computes SHA-256 integrity hash, stores file in `data/resumes/` directory, and parses structured entities into JSON.
- **Deterministic ATS Scoring**:
  - **Skills Match (40%)**: Case-insensitive set-intersection against required job keywords.
  - **Experience (25%)**: Evaluates duration, project impact, and internship history.
  - **Education (15%)**: Validates degree, graduation timeline, and relevant coursework.
  - **Completeness (10%)**: Checks presence of contact details, LinkedIn, GitHub, summary.
  - **Certifications (10%)**: Identifies industry credentials (AWS, Java, Oracle, Google).
- **GroqCloud AI Enhancement**:
  - Provides constructive feedback on resume clarity and impact.
  - Rewrites bullet points using Google's proven `Accomplished [X], as measured by [Y], by doing [Z]` formula.
  - Generates a customized 4-week preparation plan addressing identified skill gaps.
- **AI Interview Practice**: Automatically synthesizes tailored technical, behavioral, and system design interview questions directly relevant to the applicant's profile and target job role.
- **Job Discovery & Application**: Students explore a curated Job Board displaying only **Active and Admin-Approved** job openings posted by **Verified** recruiters.
- **Application Snapshotting**: Upon applying, the student's active resume ID is locked into `applications.resume_id`. Subsequent resume uploads create new records without altering existing job applications.

### 5.3 Recruiter Workspace & ATS Pipeline
- **Verification Onboarding**: Recruiter accounts default to `UNVERIFIED`. The dashboard displays an onboarding card guiding them to complete their company profile and submit for verification (`PENDING`).
- **Job Creation & Posting**:
  - Recruiter can post full-time, part-time, or internship listings.
  - New jobs are automatically assigned `moderation_status = 'PENDING'`.
  - Unverified recruiters cannot publish active jobs to the student board.
- **Atomic Job Re-Moderation (Phase 6 Step 4.2C.2.2)**:
  - If a recruiter modifies substantive content of an `APPROVED` job (title, description, required skills, eligibility, or salary), the system executes an atomic transaction in `JobDAO.updateJobForRecruiterAtomic(...)`:
    1. Verifies ownership.
    2. Updates job content.
    3. Resets moderation status to `PENDING`.
    4. Sets moderation reason to `Content modified by recruiter — pending re-approval`.
    5. Clears approval timestamps and admin references.
  - Guarantees recruiters cannot bypass content screening after initial approval.
- **Applicant Tracking System (ATS)**:
  - Recruiters inspect applicants per job opening.
  - Can view applicant details, cover letters, and resume snapshot status.
  - Multi-stage pipeline actions: `Reviewing`, `Shortlist`, `Reject`, `Accept`.
- **Secure Resume Access & Download (GAP-01)**:
  - Recruiters can view or download the exact resume submitted with an application.
  - Hardened with SQL join ownership checks:
    ```sql
    SELECT r.* FROM resumes r 
    INNER JOIN applications a ON a.resume_id = r.id 
    INNER JOIN jobs j ON j.id = a.job_id 
    WHERE a.id = ? AND j.recruiter_id = ?
    ```
  - Path traversal protection validates that file paths never escape `data/resumes/`.

### 5.4 Admin Moderation Workspace & Audit Engine
- **User Management**: Admins can inspect all registered students, recruiters, and administrators. Can deactivate or reactivate accounts with logged justifications.
- **Recruiter Verification Workspace**:
  - Admins review pending recruiter profiles, company websites, and corporate email domains.
  - Actions: `Verify`, `Reject`, `Suspend`, `Return to Pending`.
  - Every action requires an audit reason and is recorded in `admin_audit_logs`.
- **Job Moderation Workspace**:
  - Admins inspect all submitted job postings across the campus.
  - Filters: `All Statuses`, `Pending`, `Approved`, `Rejected`, `Suspended`.
  - Real-time KPI counters: Pending Review, Approved Active, Rejected, Suspended.
  - Actions: `Approve`, `Reject`, `Suspend`, `Return to Pending`.
- **Tamper-Evident Audit Logging**:
  - Every administrative status change records: Admin ID, Target Entity Type (`USER`, `RECRUITER_PROFILE`, `JOB`), Target ID, Action, Justification, and Timestamp.
  - Foreign keys utilize `ON DELETE SET NULL` so audit history survives account deletions.

---

## 6. GROQCLOUD AI INTEGRATION & RESILIENCE

### 6.1 Architectural Migration from Gemini to GroqCloud
All external generative AI features have been migrated from Google Generative Language API to **GroqCloud** via its ultra-fast OpenAI-compatible Chat Completions API.

- **Primary Model**: `openai/gpt-oss-20b` (optimized for fast reasoning and structured JSON output).
- **Fallback Model**: `llama-3.3-70b-versatile` (handles failover if primary encounters transient load).
- **Endpoint**: `https://api.groq.com/openai/v1/chat/completions`
- **Authentication**: `Authorization: Bearer ${GROQ_API_KEY}` (resolved securely from environment variables).

### 6.2 Reliability & Circuit Breaker Architecture
To ensure the desktop UI remains responsive and resilient to network dropouts or quota limitations, `AIService` incorporates:
1. **Circuit Breaker (`CircuitBreaker.java`)**:
   - Tracks consecutive API failures. If failure threshold (default: 3) is exceeded, the circuit opens for 60 seconds, returning instant graceful fallbacks without stalling user operations.
2. **Deterministic Fallback**:
   - Resume scoring never fails if AI is unavailable. Scores, skill matches, and gap calculations remain 100% operational offline.
3. **Single-Call Optimized Interview Generation**:
   - Interview questions are generated in a single prompt returning a strict JSON array, avoiding round-trip latency.
4. **In-Memory Question Caching**:
   - Repeated interview practice requests for identical roles are served instantly from memory.

---

## 7. VERIFICATION, TESTING & METRICS

HireHub was built with test-driven rigor. Automated verification scripts cover all layers:

| Verification Suite | Target Area | Tests Executed | Tests Passed | Pass Rate |
|---|---|---|---|---|
| `Phase3Verifier` | Recruiter & Job Basic CRUD | 11 | 11 | **100%** |
| `Phase4LiveAndResilienceTest` | AI Integration & Circuit Breaker | 18 | 18 | **100%** |
| `Phase5Step3ASecurityVerifier` | Recruiter Data Isolation & Access Control | 34 | 34 | **100%** |
| `Phase5Step4B1SecurityVerifier` | Student Application & Profile Isolation | 42 | 42 | **100%** |
| `Phase6Step1RecruiterSecurityVerifier`| Recruiter Pipeline Boundary & Access | 50 | 50 | **100%** |
| `Phase6Step2ResumeSnapshotVerifier` | Point-in-Time Resume Snapshot & Path Security | 67 | 67 | **100%** |
| `Phase6Step3HikariCPVerifier` | Connection Pool Performance & Stress Testing | 96 | 96 | **100%** |
| `Phase6Step4_1AdminUserManagementVerifier` | Admin User CRUD, Activation & Audit Logs | 48 | 48 | **100%** |
| `Phase6Step4_2C2_2AtomicJobReModerationVerifier`| Race Conditions & Atomic Re-moderation | 16 | 16 | **100%** |
| `Phase6GroqMigrationVerifier` | GroqCloud API, Models, Schema & Caching | 34 | 33 (1 skip*) | **100%** |

*\*Note: 1 test skipped only when optional live internet API key is not supplied in test sandbox.*

---

## 8. HARDWARE & SOFTWARE REQUIREMENTS

### 8.1 Development & Server Environment
- **Operating System**: Windows 10/11, macOS Sonoma/Ventura, or Ubuntu Linux 22.04 LTS.
- **Runtime Environment**: Java Development Kit (JDK) 21 LTS or newer.
- **Database Server**: MySQL Server 8.0.28 or newer with InnoDB engine enabled.
- **Build Tool**: Apache Maven 3.9.x.

### 8.2 Client Desktop Minimum Specifications
- **CPU**: Dual-Core 64-bit Intel / AMD or Apple Silicon processor (2.0 GHz+).
- **RAM**: 4 GB minimum (8 GB recommended).
- **Storage**: 500 MB free disk space for application binaries and cached resume storage.
- **Display Resolution**: 1280 x 800 minimum (1920 x 1080 recommended for optimal dashboard rendering).
- **Network**: Internet connection required only for live GroqCloud AI evaluation features; all core placement operations operate offline over local MySQL.

---

## 9. INSTALLATION, SETUP & RUN GUIDE

### Step 1: Clone and Inspect
```powershell
cd D:\HireHub
```

### Step 2: Database Initialization
Ensure MySQL 8.0 is running locally, then execute the canonical schema script:
```powershell
mysql -u root -p < database/schema.sql
mysql -u root -p hirehub_db < database/ensure_all_tables.sql
```

### Step 3: Configure Environment Variables
Set database credentials and GroqCloud API key (PowerShell):
```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "hirehub_db"
$env:DB_USER = "root"
$env:DB_PASS = "your_mysql_password"
$env:GROQ_API_KEY = "gsk_your_groq_api_key_here"
```

### Step 4: Compile and Launch Application
Run the automated build and launch scripts:
```powershell
# Compile all 129 Java sources
.\build.ps1

# Launch HireHub JavaFX Application
.\run.ps1
```

---

## 10. CONCLUSION & FUTURE ENHANCEMENTS

**HireHub** delivers a modern, high-performance, and secure placement platform that eliminates the operational overhead of fragmented placement processes. By combining deterministic business rules, strict database-level integrity, atomic re-moderation triggers, and cutting-edge GroqCloud AI capabilities, HireHub achieves enterprise-level software reliability.

### Future Roadmap
1. **Automated Assessment Proctoring**: Integration of a coding test harness for screening rounds.
2. **Live Interview Video Integration**: Embedded WebRTC audio/video calling within the desktop client.
3. **Automated Offer Letter Generation**: Cryptographically signed PDF offer generation upon recruiter acceptance.
4. **Push Notifications**: WebSocket / email notification dispatch when application status changes.

---
*Report Compiled & Certified for HireHub v1.0.0 Architecture*
