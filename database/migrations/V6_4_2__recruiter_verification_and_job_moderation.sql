-- =============================================================================
-- HireHub — Phase 6 Step 4.2A Database Migration:
-- Recruiter Verification, Job Moderation & Audit Log Extension
--
-- 1. Persistent migration tracking & legacy entity snapshots.
-- 2. Recruiter verification columns on recruiter_profiles (default UNVERIFIED).
-- 3. Job moderation columns on jobs (default PENDING).
-- 4. Audit log extension: entity_type, target_job_id, safe ON DELETE SET NULL.
-- 5. Grandfathers ONLY captured legacy records (VERIFIED for recruiters, APPROVED for jobs).
--
-- Idempotent & restartable: safe to execute multiple times.
-- =============================================================================

USE hirehub_db;

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_apply_phase6_step4_2a$$

CREATE PROCEDURE sp_apply_phase6_step4_2a()
BEGIN
    DECLARE v_legacy_count INT DEFAULT 0;
    DECLARE v_col_count INT DEFAULT 0;
    DECLARE v_idx_count INT DEFAULT 0;
    DECLARE v_fk_count INT DEFAULT 0;
    DECLARE v_fk_name VARCHAR(128);
    DECLARE v_delete_rule VARCHAR(64);

    -- -------------------------------------------------------------------------
    -- 1. Ensure Migration Tracking Tables Exist
    -- -------------------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS schema_migrations (
        migration_id VARCHAR(64) PRIMARY KEY,
        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        details TEXT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS migration_legacy_records (
        id INT AUTO_INCREMENT PRIMARY KEY,
        migration_id VARCHAR(64) NOT NULL,
        entity_type VARCHAR(32) NOT NULL,
        entity_id INT NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_migration_entity (migration_id, entity_type, entity_id),
        INDEX idx_mlr_lookup (migration_id, entity_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- -------------------------------------------------------------------------
    -- 2. Capture Legacy Snapshots Before Alteration (If not already taken)
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_legacy_count 
    FROM migration_legacy_records 
    WHERE migration_id = 'phase6_step4_2a';

    IF v_legacy_count = 0 THEN
        -- Create placeholder profile for any legacy recruiter without one
        INSERT INTO recruiter_profiles (user_id, company_name, company_description, updated_at)
        SELECT u.id, 'Legacy Recruiter Profile', 'Auto-created during Phase 6 Step 4.2A migration', NOW()
        FROM users u
        LEFT JOIN recruiter_profiles rp ON u.id = rp.user_id
        WHERE u.role = 'RECRUITER' AND rp.id IS NULL;

        -- Record legacy recruiters
        INSERT IGNORE INTO migration_legacy_records (migration_id, entity_type, entity_id)
        SELECT 'phase6_step4_2a', 'RECRUITER', u.id
        FROM users u
        WHERE u.role = 'RECRUITER';

        -- Record legacy jobs
        INSERT IGNORE INTO migration_legacy_records (migration_id, entity_type, entity_id)
        SELECT 'phase6_step4_2a', 'JOB', j.id
        FROM jobs j;
    END IF;

    -- -------------------------------------------------------------------------
    -- 3. Recruiter Verification Schema (recruiter_profiles)
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND COLUMN_NAME = 'verification_status';
    IF v_col_count = 0 THEN
        ALTER TABLE recruiter_profiles
        ADD COLUMN verification_status ENUM('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED') 
            NOT NULL DEFAULT 'UNVERIFIED' AFTER industry;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND COLUMN_NAME = 'verification_reason';
    IF v_col_count = 0 THEN
        ALTER TABLE recruiter_profiles
        ADD COLUMN verification_reason VARCHAR(500) NULL AFTER verification_status;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND COLUMN_NAME = 'verified_at';
    IF v_col_count = 0 THEN
        ALTER TABLE recruiter_profiles
        ADD COLUMN verified_at TIMESTAMP NULL AFTER verification_reason;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND COLUMN_NAME = 'verified_by';
    IF v_col_count = 0 THEN
        ALTER TABLE recruiter_profiles
        ADD COLUMN verified_by INT NULL AFTER verified_at;
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND INDEX_NAME = 'idx_rp_verification_status';
    IF v_idx_count = 0 THEN
        ALTER TABLE recruiter_profiles ADD INDEX idx_rp_verification_status (verification_status);
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' AND INDEX_NAME = 'idx_rp_verified_by';
    IF v_idx_count = 0 THEN
        ALTER TABLE recruiter_profiles ADD INDEX idx_rp_verified_by (verified_by);
    END IF;

    SELECT COUNT(*) INTO v_fk_count
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'recruiter_profiles' 
      AND CONSTRAINT_NAME = 'fk_rp_verified_by' AND CONSTRAINT_TYPE = 'FOREIGN KEY';
    IF v_fk_count = 0 THEN
        ALTER TABLE recruiter_profiles 
        ADD CONSTRAINT fk_rp_verified_by FOREIGN KEY (verified_by) REFERENCES users (id) 
        ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;

    -- -------------------------------------------------------------------------
    -- 4. Job Moderation Schema (jobs)
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'moderation_status';
    IF v_col_count = 0 THEN
        ALTER TABLE jobs
        ADD COLUMN moderation_status ENUM('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED') 
            NOT NULL DEFAULT 'PENDING' AFTER status;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'moderation_reason';
    IF v_col_count = 0 THEN
        ALTER TABLE jobs
        ADD COLUMN moderation_reason VARCHAR(500) NULL AFTER moderation_status;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'moderated_at';
    IF v_col_count = 0 THEN
        ALTER TABLE jobs
        ADD COLUMN moderated_at TIMESTAMP NULL AFTER moderation_reason;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND COLUMN_NAME = 'moderated_by';
    IF v_col_count = 0 THEN
        ALTER TABLE jobs
        ADD COLUMN moderated_by INT NULL AFTER moderated_at;
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND INDEX_NAME = 'idx_jobs_moderation_status';
    IF v_idx_count = 0 THEN
        ALTER TABLE jobs ADD INDEX idx_jobs_moderation_status (moderation_status);
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND INDEX_NAME = 'idx_jobs_status_moderation';
    IF v_idx_count = 0 THEN
        ALTER TABLE jobs ADD INDEX idx_jobs_status_moderation (status, moderation_status);
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' AND INDEX_NAME = 'idx_jobs_moderated_by';
    IF v_idx_count = 0 THEN
        ALTER TABLE jobs ADD INDEX idx_jobs_moderated_by (moderated_by);
    END IF;

    SELECT COUNT(*) INTO v_fk_count
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'jobs' 
      AND CONSTRAINT_NAME = 'fk_jobs_moderated_by' AND CONSTRAINT_TYPE = 'FOREIGN KEY';
    IF v_fk_count = 0 THEN
        ALTER TABLE jobs 
        ADD CONSTRAINT fk_jobs_moderated_by FOREIGN KEY (moderated_by) REFERENCES users (id) 
        ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;

    -- -------------------------------------------------------------------------
    -- 5. Audit Log Extension (admin_audit_logs)
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' AND COLUMN_NAME = 'entity_type';
    IF v_col_count = 0 THEN
        ALTER TABLE admin_audit_logs
        ADD COLUMN entity_type ENUM('USER', 'RECRUITER_PROFILE', 'JOB') 
            NOT NULL DEFAULT 'USER' AFTER id;
    END IF;

    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' AND COLUMN_NAME = 'target_job_id';
    IF v_col_count = 0 THEN
        ALTER TABLE admin_audit_logs
        ADD COLUMN target_job_id INT NULL AFTER target_user_id;
    END IF;

    -- Make target_user_id nullable if not already
    SELECT COUNT(*) INTO v_col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' 
      AND COLUMN_NAME = 'target_user_id' AND IS_NULLABLE = 'NO';
    IF v_col_count > 0 THEN
        ALTER TABLE admin_audit_logs MODIFY COLUMN target_user_id INT NULL;
    END IF;

    -- Expand reason column length if needed
    ALTER TABLE admin_audit_logs MODIFY COLUMN reason VARCHAR(500) NULL;

    -- Safe Foreign Key on target_user_id (ON DELETE SET NULL)
    SET v_fk_name = NULL;
    SELECT rc.CONSTRAINT_NAME, rc.DELETE_RULE INTO v_fk_name, v_delete_rule
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    JOIN information_schema.KEY_COLUMN_USAGE kcu 
      ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
    WHERE rc.CONSTRAINT_SCHEMA = DATABASE() AND rc.TABLE_NAME = 'admin_audit_logs'
      AND kcu.COLUMN_NAME = 'target_user_id' AND rc.REFERENCED_TABLE_NAME = 'users'
    LIMIT 1;

    IF v_fk_name IS NOT NULL AND v_delete_rule = 'CASCADE' THEN
        SET @drop_fk_sql = CONCAT('ALTER TABLE admin_audit_logs DROP FOREIGN KEY ', v_fk_name);
        PREPARE stmt_drop_fk FROM @drop_fk_sql;
        EXECUTE stmt_drop_fk;
        DEALLOCATE PREPARE stmt_drop_fk;
        SET v_fk_name = NULL;
    END IF;

    IF v_fk_name IS NULL THEN
        ALTER TABLE admin_audit_logs 
        ADD CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) 
        ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;

    -- Foreign Key on target_job_id
    SELECT COUNT(*) INTO v_fk_count
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' 
      AND CONSTRAINT_NAME = 'fk_audit_job' AND CONSTRAINT_TYPE = 'FOREIGN KEY';
    IF v_fk_count = 0 THEN
        ALTER TABLE admin_audit_logs 
        ADD CONSTRAINT fk_audit_job FOREIGN KEY (target_job_id) REFERENCES jobs (id) 
        ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' AND INDEX_NAME = 'idx_audit_job';
    IF v_idx_count = 0 THEN
        ALTER TABLE admin_audit_logs ADD INDEX idx_audit_job (target_job_id);
    END IF;

    SELECT COUNT(*) INTO v_idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_logs' AND INDEX_NAME = 'idx_audit_entity';
    IF v_idx_count = 0 THEN
        ALTER TABLE admin_audit_logs ADD INDEX idx_audit_entity (entity_type);
    END IF;

    -- -------------------------------------------------------------------------
    -- 6. Grandfather Captured Legacy Records ONLY
    -- -------------------------------------------------------------------------
    UPDATE recruiter_profiles rp
    JOIN migration_legacy_records mlr
      ON rp.user_id = mlr.entity_id
     AND mlr.migration_id = 'phase6_step4_2a'
     AND mlr.entity_type = 'RECRUITER'
    SET rp.verification_status = 'VERIFIED',
        rp.verification_reason = 'Grandfathered legacy recruiter verified during Phase 6 Step 4.2A migration',
        rp.verified_at = COALESCE(rp.verified_at, NOW())
    WHERE rp.verification_status != 'VERIFIED';

    UPDATE jobs j
    JOIN migration_legacy_records mlr
      ON j.id = mlr.entity_id
     AND mlr.migration_id = 'phase6_step4_2a'
     AND mlr.entity_type = 'JOB'
    SET j.moderation_status = 'APPROVED',
        j.moderation_reason = 'Grandfathered legacy job approved during Phase 6 Step 4.2A migration',
        j.moderated_at = COALESCE(j.moderated_at, NOW())
    WHERE j.moderation_status != 'APPROVED';

    -- -------------------------------------------------------------------------
    -- 7. Record Migration Progress
    -- -------------------------------------------------------------------------
    INSERT INTO schema_migrations (migration_id, details)
    VALUES ('phase6_step4_2a', 'Phase 6 Step 4.2A: Recruiter Verification, Job Moderation & Audit Log Extension')
    ON DUPLICATE KEY UPDATE applied_at = NOW(), details = VALUES(details);

END$$

DELIMITER ;

-- Execute the idempotent migration procedure
CALL sp_apply_phase6_step4_2a();

-- Clean up the temporary procedure
DROP PROCEDURE IF EXISTS sp_apply_phase6_step4_2a;

