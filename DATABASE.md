# HireHub — Database Schema & Migration Specification

This document details the database architecture, schema definitions, entity relationships, and migration procedures for the **HireHub** relational database (`hirehub_db`) running on MySQL 8.0.

---

## 1. Overview & Storage Engine Standards

- **Database Engine**: MySQL 8.0+
- **Default Storage Engine**: `InnoDB` (enforces ACID compliance, foreign keys, and row-level locking)
- **Character Set**: `utf8mb4`
- **Collation**: `utf8mb4_unicode_ci`
- **Connection Management**: HikariCP connection pool (`com.zaxxer.hikari.HikariDataSource`)

---

## 2. Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    users ||--o| student_profiles : "has profile (1:1)"
    users ||--o| recruiter_profiles : "has profile (1:1)"
    users ||--o{ jobs : "posts (as recruiter)"
    users ||--o{ applications : "submits (as student)"
    users ||--o{ resumes : "owns (as student)"
    users ||--o{ admin_audit_logs : "performed by (admin)"
    users ||--o{ admin_audit_logs : "targets (user)"

    recruiter_profiles ||--o{ jobs : "owns"
    jobs ||--o{ applications : "receives"
    applications ||--o| application_resumes : "captures snapshot (1:1)"
    
    resumes ||--o{ application_resumes : "referenced in snapshot"
    resumes ||--o{ resume_analyses : "evaluates"
    jobs ||--o{ resume_analyses : "targeted by analysis"
    users ||--o{ resume_analyses : "evaluated for (student)"

    users {
        int id PK
        string full_name
        string email UK
        string password_hash
        enum role "STUDENT, RECRUITER, ADMIN"
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    student_profiles {
        int id PK
        int user_id FK,UK
        string phone
        string college
        string degree
        int graduation_year
        decimal cgpa
        text skills
        string resume_path
        timestamp created_at
        timestamp updated_at
    }

    recruiter_profiles {
        int id PK
        int user_id FK,UK
        string company_name
        string industry
        string company_size
        string website
        text company_description
        enum verification_status "UNVERIFIED, PENDING, VERIFIED, REJECTED"
        int verified_by FK
        timestamp verified_at
        string rejection_reason
        timestamp created_at
        timestamp updated_at
    }

    jobs {
        int id PK
        int recruiter_id FK
        string title
        text description
        string location
        enum job_type "FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT, REMOTE"
        string salary_range
        text requirements
        date deadline
        enum status "ACTIVE, CLOSED, DRAFT"
        enum moderation_status "PENDING, APPROVED, REJECTED, SUSPENDED"
        int moderated_by FK
        timestamp moderated_at
        string moderation_reason
        timestamp created_at
        timestamp updated_at
    }

    applications {
        int id PK
        int job_id FK
        int student_id FK
        enum status "APPLIED, REVIEWING, SHORTLISTED, ACCEPTED, REJECTED"
        timestamp applied_at
        timestamp updated_at
    }

    application_resumes {
        int id PK
        int application_id FK,UK
        int original_resume_id FK
        string stored_filename
        string original_filename
        string file_path
        int file_size_bytes
        string file_hash_sha256
        timestamp uploaded_at
        timestamp captured_at
    }

    resumes {
        int id PK
        int student_id FK
        string file_name
        string stored_path
        int file_size
        string file_hash
        boolean is_active
        timestamp uploaded_at
    }

    resume_analyses {
        int id PK
        int resume_id FK
        int job_id FK
        int student_id FK
        int overall_score
        int skills_score
        int experience_score
        int education_score
        int completeness_score
        int certifications_score
        text matching_skills
        text missing_skills
        text analysis_summary
        timestamp created_at
    }

    admin_audit_logs {
        int id PK
        int admin_id FK
        enum entity_type "USER, JOB, RECRUITER"
        int target_user_id FK
        int target_job_id FK
        string action
        string reason
        timestamp created_at
    }

    schema_migrations {
        string version PK
        string description
        string applied_by
        timestamp applied_at
    }
```

---

## 3. 10-Table Schema Specifications

### 3.1. `users`
Central identity store supporting multi-tenant role-based authentication.
- **Indexes**: `PRIMARY (id)`, `UNIQUE (email)`, `INDEX (role)`, `INDEX (is_active)`.
- **BCrypt Security**: `password_hash` stores standard 60-character BCrypt hashes with salt.

### 3.2. `student_profiles`
Academic background, contact details, and core technical skills.
- **Foreign Key**: `user_id -> users(id) ON DELETE CASCADE`.

### 3.3. `recruiter_profiles`
Corporate organization metadata and verification status.
- **Foreign Key**: `user_id -> users(id) ON DELETE CASCADE`.
- **Foreign Key**: `verified_by -> users(id) ON DELETE SET NULL`.
- **Verification Enum**: `'UNVERIFIED'`, `'PENDING'`, `'VERIFIED'`, `'REJECTED'`.

### 3.4. `jobs`
Campus job postings, requirements, deadlines, and moderation lifecycle.
- **Foreign Key**: `recruiter_id -> users(id) ON DELETE CASCADE`.
- **Foreign Key**: `moderated_by -> users(id) ON DELETE SET NULL`.
- **Moderation Enum**: `'PENDING'`, `'APPROVED'`, `'REJECTED'`, `'SUSPENDED'`.
- **Lifecycle Enum**: `'ACTIVE'`, `'CLOSED'`, `'DRAFT'`.

### 3.5. `resumes`
Physical resume files uploaded by students.
- **Foreign Key**: `student_id -> users(id) ON DELETE CASCADE`.
- **Tracking**: `is_active` marks the currently active resume. File hash prevents redundant parsing.

### 3.6. `applications`
Student job applications and hiring pipeline stage progression.
- **Foreign Key**: `job_id -> jobs(id) ON DELETE CASCADE`.
- **Foreign Key**: `student_id -> users(id) ON DELETE CASCADE`.
- **Unique Constraint**: `UNIQUE (job_id, student_id)` prevents duplicate submissions.

### 3.7. `application_resumes`
Immutable resume snapshot captured at the exact moment of application submission.
- **Foreign Key**: `application_id -> applications(id) ON DELETE CASCADE`.
- **Foreign Key**: `original_resume_id -> resumes(id) ON DELETE SET NULL`.
- **Design Guarantee**: If a student later replaces or deletes their active resume from `resumes`, the recruiter's historical application snapshot in `application_resumes` remains intact and downloadable.

### 3.8. `resume_analyses`
Structured results from deterministic 5-part scoring and Groq AI qualitative evaluation.
- **Foreign Key**: `resume_id -> resumes(id) ON DELETE SET NULL`.
- **Foreign Key**: `job_id -> jobs(id) ON DELETE SET NULL` *(NULL indicates general resume review without a specific target job)*.
- **Foreign Key**: `student_id -> users(id) ON DELETE CASCADE`.

### 3.9. `admin_audit_logs`
Disciplinary and moderation audit trail.
- **Foreign Key**: `admin_id -> users(id) ON DELETE RESTRICT`.
- **Foreign Key**: `target_user_id -> users(id) ON DELETE SET NULL`.
- **Foreign Key**: `target_job_id -> jobs(id) ON DELETE SET NULL`.
- **Immutability Guarantee**: Audit history is never deleted when a user or job is deleted.

### 3.10. `schema_migrations`
Tracks applied database version migrations.

---

## 4. Safe Topological Initialization Order

When creating the database on a fresh system, tables must be created in topological foreign-key order to resolve dependencies cleanly without `ERROR 1824 (HY000)`:

1. `users` (No dependencies)
2. `schema_migrations` (No dependencies)
3. `student_profiles` (Depends on `users`)
4. `recruiter_profiles` (Depends on `users`)
5. `jobs` (Depends on `users`)
6. `resumes` (Depends on `users`)
7. `applications` (Depends on `jobs`, `users`)
8. `application_resumes` (Depends on `applications`, `resumes`)
9. `resume_analyses` (Depends on `resumes`, `jobs`, `users`)
10. `admin_audit_logs` (Depends on `users`, `jobs`)

Both `database/schema/ensure_all_tables.sql` and the sequential migrations in `database/migrations/` strictly follow this topological ordering.

---

## 5. Step-by-Step Database Setup

### Fast Setup (Single Script)
To initialize all 10 tables on a fresh MySQL instance:

```powershell
# 1. Create database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hirehub_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Execute idempotent table creation
mysql -u root -p hirehub_db < database/schema/ensure_all_tables.sql

# 3. Seed initial administrator account
mysql -u root -p hirehub_db < database/seed/admin_seed.sql
```

### Versioned Migration Setup (Sequential)
To apply each versioned migration sequentially:

```powershell
$migrations = @(
    "V1__init_schema.sql",
    "V2__profiles_and_jobs.sql",
    "V3__applications.sql",
    "V4__resumes_and_analyses.sql",
    "V4_1__resume_analyses_retention.sql",
    "V6_2__application_resume_snapshot.sql",
    "V6_4_1__admin_user_management.sql",
    "V6_4_2__recruiter_verification_and_job_moderation.sql"
)

foreach ($m in $migrations) {
    Write-Host "Applying migration: $m" -ForegroundColor Cyan
    Get-Content "database/migrations/$m" | mysql -u root -p hirehub_db
}
```
