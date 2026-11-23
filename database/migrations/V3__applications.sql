-- =============================================================================
-- HireHub Phase 3 Migration
-- Creates: applications table
-- Safe to run on existing Phase 1+2 database.
-- =============================================================================

USE hirehub_db;

CREATE TABLE IF NOT EXISTS applications (
    id           INT          NOT NULL AUTO_INCREMENT,
    job_id       INT          NOT NULL,
    student_id   INT          NOT NULL,
    cover_letter TEXT         DEFAULT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    applied_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_application (job_id, student_id),
    KEY idx_app_student  (student_id),
    KEY idx_app_job      (job_id),
    KEY idx_app_status   (status),

    CONSTRAINT fk_app_job
        FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_app_student
        FOREIGN KEY (student_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Student job applications';
