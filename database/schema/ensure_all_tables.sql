-- =============================================================================
-- HireHub — Canonical Schema: Ensure All Tables Exist (Safe to Re-Run)
-- Database: hirehub_db
-- MySQL 8.0+
--
-- Represents the canonical intended schema including:
--   - users (is_active)
--   - student_profiles
--   - recruiter_profiles (verification_status, reason, verified_at, verified_by)
--   - jobs (moderation_status, reason, moderated_at, moderated_by)
--   - applications (resume_id)
--   - resumes & resume_analyses
--   - admin_audit_logs (entity_type, target_job_id, safe SET NULL deletion)
--   - schema_migrations & migration_legacy_records
--
-- Safe defaults:
--   - recruiter_profiles.verification_status defaults to 'UNVERIFIED'
--   - jobs.moderation_status defaults to 'PENDING'
--   - admin_audit_logs.entity_type defaults to 'USER'
--
-- mysql -u root -p hirehub_db < database/ensure_all_tables.sql
-- =============================================================================

USE hirehub_db;

-- users ────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            INT           NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role          ENUM('STUDENT', 'RECRUITER', 'ADMIN') NOT NULL DEFAULT 'STUDENT',
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_email (email),
    INDEX idx_users_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- student_profiles ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS student_profiles (
    id              INT           NOT NULL AUTO_INCREMENT,
    user_id         INT           NOT NULL,
    phone           VARCHAR(20)   DEFAULT NULL,
    college         VARCHAR(150)  DEFAULT NULL,
    degree          VARCHAR(100)  DEFAULT NULL,
    graduation_year INT           DEFAULT NULL,
    skills          TEXT          DEFAULT NULL,
    resume_path     VARCHAR(500)  DEFAULT NULL,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_student_user (user_id),
    CONSTRAINT fk_sp_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- recruiter_profiles ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recruiter_profiles (
    id                  INT           NOT NULL AUTO_INCREMENT,
    user_id             INT           NOT NULL,
    company_name        VARCHAR(150)  DEFAULT NULL,
    company_website     VARCHAR(300)  DEFAULT NULL,
    company_description TEXT          DEFAULT NULL,
    industry            VARCHAR(100)  DEFAULT NULL,
    verification_status ENUM('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')
                                      NOT NULL DEFAULT 'UNVERIFIED',
    verification_reason VARCHAR(500)  DEFAULT NULL,
    verified_at         TIMESTAMP     NULL DEFAULT NULL,
    verified_by         INT           DEFAULT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recruiter_user (user_id),
    INDEX idx_rp_verification_status (verification_status),
    INDEX idx_rp_verified_by (verified_by),
    CONSTRAINT fk_rp_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_rp_verified_by FOREIGN KEY (verified_by) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- jobs ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS jobs (
    id                INT           NOT NULL AUTO_INCREMENT,
    recruiter_id      INT           NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    company_name      VARCHAR(150)  NOT NULL,
    description       TEXT          NOT NULL,
    required_skills   TEXT          DEFAULT NULL,
    location          VARCHAR(150)  DEFAULT NULL,
    job_type          ENUM('INTERNSHIP','FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME',
    salary_range      VARCHAR(100)  DEFAULT NULL,
    eligibility       TEXT          DEFAULT NULL,
    deadline          DATE          DEFAULT NULL,
    status            ENUM('ACTIVE','DRAFT','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    moderation_status ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
    moderation_reason VARCHAR(500)  DEFAULT NULL,
    moderated_at      TIMESTAMP     NULL DEFAULT NULL,
    moderated_by      INT           DEFAULT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_jobs_recruiter         (recruiter_id),
    INDEX idx_jobs_status            (status),
    INDEX idx_jobs_moderation_status (moderation_status),
    INDEX idx_jobs_status_moderation (status, moderation_status),
    INDEX idx_jobs_moderated_by      (moderated_by),
    CONSTRAINT fk_jobs_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_jobs_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- resumes ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS resumes (
    id                INT           NOT NULL AUTO_INCREMENT,
    student_id        INT           NOT NULL,
    file_name         VARCHAR(255)  NOT NULL,
    stored_path       VARCHAR(500)  NOT NULL,
    file_size_bytes   BIGINT        NOT NULL,
    file_hash         VARCHAR(64)   NOT NULL,
    parsed_text       MEDIUMTEXT    DEFAULT NULL,
    parsed_json       TEXT          DEFAULT NULL,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    uploaded_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_resumes_student   (student_id),
    INDEX idx_resumes_active    (is_active),
    INDEX idx_resumes_file_hash (file_hash),
    CONSTRAINT fk_resumes_student FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Uploaded student resumes and parsed text/JSON metadata';

-- resume_analyses ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS resume_analyses (
    id                    INT           NOT NULL AUTO_INCREMENT,
    resume_id             INT           DEFAULT NULL,
    job_id                INT           DEFAULT NULL,
    student_id            INT           NOT NULL,
    overall_score         INT           NOT NULL DEFAULT 0,
    skills_score          INT           NOT NULL DEFAULT 0,
    experience_score      INT           NOT NULL DEFAULT 0,
    education_score       INT           NOT NULL DEFAULT 0,
    completeness_score    INT           NOT NULL DEFAULT 0,
    certifications_score  INT           NOT NULL DEFAULT 0,
    matching_skills       TEXT          DEFAULT NULL,
    missing_skills        TEXT          DEFAULT NULL,
    analysis_summary      MEDIUMTEXT    DEFAULT NULL,
    quality_feedback      MEDIUMTEXT    DEFAULT NULL,
    improvement_plan      MEDIUMTEXT    DEFAULT NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_analysis_student_job (student_id, job_id),
    INDEX idx_analysis_resume      (resume_id),
    INDEX idx_analysis_created     (created_at),
    CONSTRAINT fk_analysis_resume  FOREIGN KEY (resume_id)  REFERENCES resumes (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_analysis_job     FOREIGN KEY (job_id)     REFERENCES jobs (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_analysis_student FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Resume compatibility scores and AI evaluation records';

-- applications ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS applications (
    id           INT          NOT NULL AUTO_INCREMENT,
    job_id       INT          NOT NULL,
    student_id   INT          NOT NULL,
    resume_id    INT          DEFAULT NULL,
    cover_letter TEXT         DEFAULT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    applied_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_application (job_id, student_id),
    INDEX idx_app_resume      (resume_id),
    CONSTRAINT fk_app_job     FOREIGN KEY (job_id)     REFERENCES jobs    (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_app_student FOREIGN KEY (student_id) REFERENCES users   (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_app_resume  FOREIGN KEY (resume_id)  REFERENCES resumes (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- admin_audit_logs ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id             INT          NOT NULL AUTO_INCREMENT,
    entity_type    ENUM('USER', 'RECRUITER_PROFILE', 'JOB') NOT NULL DEFAULT 'USER',
    admin_id       INT          NOT NULL,
    target_user_id INT          DEFAULT NULL,
    target_job_id  INT          DEFAULT NULL,
    action         VARCHAR(32)  NOT NULL,
    reason         VARCHAR(500) DEFAULT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_admin   (admin_id),
    INDEX idx_audit_target  (target_user_id),
    INDEX idx_audit_job     (target_job_id),
    INDEX idx_audit_entity  (entity_type),
    INDEX idx_audit_created (created_at),
    CONSTRAINT fk_audit_admin  FOREIGN KEY (admin_id)       REFERENCES users (id) ON DELETE CASCADE  ON UPDATE CASCADE,
    CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_audit_job    FOREIGN KEY (target_job_id)  REFERENCES jobs  (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- migration tracking tables ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS schema_migrations (
    migration_id VARCHAR(64) PRIMARY KEY,
    applied_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details      TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS migration_legacy_records (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    migration_id VARCHAR(64) NOT NULL,
    entity_type  VARCHAR(32) NOT NULL,
    entity_id    INT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_migration_entity (migration_id, entity_type, entity_id),
    INDEX idx_mlr_lookup (migration_id, entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Verify ───────────────────────────────────────────────────────────────────────
SHOW TABLES;
