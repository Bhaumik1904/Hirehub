-- =============================================================================
-- HireHub — Phase 4 Database Migration: Production AI Resume Analyzer
-- Creates tables for storing student resumes and job compatibility analyses.
-- Idempotent: safe to run multiple times.
-- =============================================================================

USE hirehub_db;

-- -----------------------------------------------------------------------------
-- TABLE: resumes
-- Stores uploaded student resumes, file metadata, hash, and extracted content.
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- TABLE: resume_analyses
-- Stores deterministic compatibility scores and AI enhancement evaluations.
-- job_id is nullable to support general resume analysis without a target job.
-- resume_id is nullable so historical analyses are preserved upon resume deletion.
-- -----------------------------------------------------------------------------
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

