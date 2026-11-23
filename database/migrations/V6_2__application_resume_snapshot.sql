-- =============================================================================
-- HireHub — Phase 6 Step 2 Database Migration:
-- Application Resume Snapshot & Recruiter Resume Access (GAP-05 + GAP-01)
--
-- Adds resume_id foreign key to applications table with ON DELETE RESTRICT.
-- Ensures application-linked resumes cannot be deleted while preserving legacy apps.
-- Idempotent: safe to run multiple times.
-- =============================================================================

USE hirehub_db;

-- 1. Add resume_id column to applications if it doesn't already exist
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'hirehub_db'
      AND TABLE_NAME = 'applications'
      AND COLUMN_NAME = 'resume_id'
);

SET @sql_col = IF(@col_exists = 0,
    'ALTER TABLE applications ADD COLUMN resume_id INT NULL DEFAULT NULL AFTER student_id, ADD INDEX idx_app_resume (resume_id)',
    'SELECT "Column resume_id already exists in applications"'
);
PREPARE stmt_col FROM @sql_col;
EXECUTE stmt_col;
DEALLOCATE PREPARE stmt_col;

-- 2. Add foreign key fk_app_resume with ON DELETE RESTRICT if it doesn't already exist
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = 'hirehub_db'
      AND TABLE_NAME = 'applications'
      AND CONSTRAINT_NAME = 'fk_app_resume'
);

SET @sql_fk = IF(@fk_exists = 0,
    'ALTER TABLE applications ADD CONSTRAINT fk_app_resume FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE RESTRICT ON UPDATE CASCADE',
    'SELECT "Foreign key fk_app_resume already exists"'
);
PREPARE stmt_fk FROM @sql_fk;
EXECUTE stmt_fk;
DEALLOCATE PREPARE stmt_fk;

-- 3. Verify schema
DESCRIBE applications;

