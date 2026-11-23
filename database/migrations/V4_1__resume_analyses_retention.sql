-- =============================================================================
-- HireHub — Phase 4.1 Database Migration: General Resume Analysis & Retention
-- Makes job_id and resume_id nullable in resume_analyses with ON DELETE SET NULL.
-- Preserves existing analysis history when resumes or jobs are updated/deleted.
-- =============================================================================

USE hirehub_db;

-- 1. Drop existing foreign keys
ALTER TABLE resume_analyses DROP FOREIGN KEY fk_analysis_job;
ALTER TABLE resume_analyses DROP FOREIGN KEY fk_analysis_resume;

-- 2. Modify columns to allow NULL for general resume reviews
ALTER TABLE resume_analyses MODIFY COLUMN job_id INT NULL DEFAULT NULL;
ALTER TABLE resume_analyses MODIFY COLUMN resume_id INT NULL DEFAULT NULL;

-- 3. Re-add foreign key constraints with ON DELETE SET NULL
ALTER TABLE resume_analyses
    ADD CONSTRAINT fk_analysis_job FOREIGN KEY (job_id) REFERENCES jobs (id)
    ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE resume_analyses
    ADD CONSTRAINT fk_analysis_resume FOREIGN KEY (resume_id) REFERENCES resumes (id)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- 4. Verify table schema
DESCRIBE resume_analyses;

