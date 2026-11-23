-- =============================================================================
-- HireHub Phase 2 Migration
-- Adds: student_profiles, recruiter_profiles, jobs
-- Safe to run against an existing Phase 1 database.
-- Does NOT drop or alter Phase 1 tables.
-- =============================================================================

USE hirehub_db;

-- =============================================================================
-- TABLE: student_profiles
-- One-to-one with users (role=STUDENT). One profile per student.
-- =============================================================================

CREATE TABLE IF NOT EXISTS student_profiles (
    id              INT           NOT NULL AUTO_INCREMENT,
    user_id         INT           NOT NULL,
    phone           VARCHAR(20)   DEFAULT NULL,
    college         VARCHAR(150)  DEFAULT NULL,
    degree          VARCHAR(100)  DEFAULT NULL,
    graduation_year INT           DEFAULT NULL,
    skills          TEXT          DEFAULT NULL,   -- comma-separated skill tags
    resume_path     VARCHAR(500)  DEFAULT NULL,   -- local file path or URL
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_student_user (user_id),          -- one profile per student
    CONSTRAINT fk_student_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Extended profile for STUDENT role users';

-- =============================================================================
-- TABLE: recruiter_profiles
-- One-to-one with users (role=RECRUITER). One profile per recruiter.
-- =============================================================================

CREATE TABLE IF NOT EXISTS recruiter_profiles (
    id                  INT           NOT NULL AUTO_INCREMENT,
    user_id             INT           NOT NULL,
    company_name        VARCHAR(150)  DEFAULT NULL,
    company_website     VARCHAR(300)  DEFAULT NULL,
    company_description TEXT          DEFAULT NULL,
    industry            VARCHAR(100)  DEFAULT NULL,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_recruiter_user (user_id),        -- one profile per recruiter
    CONSTRAINT fk_recruiter_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Extended profile for RECRUITER role users';

-- =============================================================================
-- TABLE: jobs
-- Job listings created by recruiters.
-- =============================================================================

CREATE TABLE IF NOT EXISTS jobs (
    id              INT           NOT NULL AUTO_INCREMENT,
    recruiter_id    INT           NOT NULL,        -- FK to users.id (RECRUITER role)
    title           VARCHAR(200)  NOT NULL,
    company_name    VARCHAR(150)  NOT NULL,
    description     TEXT          NOT NULL,
    required_skills TEXT          DEFAULT NULL,    -- comma-separated
    location        VARCHAR(150)  DEFAULT NULL,
    job_type        ENUM('INTERNSHIP','FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME',
    salary_range    VARCHAR(100)  DEFAULT NULL,    -- optional free-text e.g. "5-8 LPA"
    eligibility     TEXT          DEFAULT NULL,    -- min CGPA, branch, etc.
    deadline        DATE          DEFAULT NULL,    -- application deadline
    status          ENUM('ACTIVE','DRAFT','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_jobs_recruiter  (recruiter_id),
    INDEX idx_jobs_status     (status),
    INDEX idx_jobs_title      (title(50)),
    INDEX idx_jobs_deadline   (deadline),
    CONSTRAINT fk_jobs_recruiter
        FOREIGN KEY (recruiter_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Job listings posted by recruiters';

-- =============================================================================
-- Verify Phase 2 tables
-- =============================================================================
-- Run after migration:
-- SHOW TABLES;
-- DESCRIBE student_profiles;
-- DESCRIBE recruiter_profiles;
-- DESCRIBE jobs;
