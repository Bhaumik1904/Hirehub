-- =============================================================================
-- HireHub — Phase 3 Verification Script
-- Run in MySQL Workbench after executing phase3_migration.sql
-- =============================================================================

USE hirehub_db;

-- 1. Confirm tables exist
SHOW TABLES;

-- 2. Confirm applications table schema
DESCRIBE applications;

-- 3. See all applications
SELECT
    a.id,
    a.job_id,
    j.title        AS job_title,
    a.student_id,
    u.full_name    AS student_name,
    u.email        AS student_email,
    a.status,
    a.applied_at
FROM applications a
JOIN jobs    j ON a.job_id    = j.id
JOIN users   u ON a.student_id = u.id
ORDER BY a.applied_at DESC;

-- 4. Shortlist the first pending application (replace ID if needed)
-- UPDATE applications SET status = 'SHORTLISTED' WHERE id = 1;

-- 5. Verify the update
-- SELECT id, status, updated_at FROM applications WHERE id = 1;

