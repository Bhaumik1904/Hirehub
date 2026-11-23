package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JobDAO — CRUD and Admin Moderation for the jobs table.
 *
 * <p>All SQL uses PreparedStatement. Ownership checks are enforced in JobService.
 * Administrative moderation attributes are managed via dedicated moderation operations.</p>
 */
public class JobDAO {

    private static final String COLS =
            "id, recruiter_id, title, company_name, description, required_skills, "
            + "location, job_type, salary_range, eligibility, deadline, status, "
            + "moderation_status, moderation_reason, moderated_at, moderated_by, created_at";

    private static final String JOB_COLS_PREFIXED =
            "j.id, j.recruiter_id, j.title, j.company_name, j.description, j.required_skills, "
            + "j.location, j.job_type, j.salary_range, j.eligibility, j.deadline, j.status, "
            + "j.moderation_status, j.moderation_reason, j.moderated_at, j.moderated_by, j.created_at";

    private static final String SQL_INSERT =
            "INSERT INTO jobs (recruiter_id, title, company_name, description, required_skills, "
            + "location, job_type, salary_range, eligibility, deadline, status, "
            + "moderation_status, moderation_reason, moderated_at, moderated_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE jobs SET title=?, company_name=?, description=?, required_skills=?, "
            + "location=?, job_type=?, salary_range=?, eligibility=?, deadline=?, status=? "
            + "WHERE id=?";

    private static final String SQL_UPDATE_FOR_RECRUITER =
            "UPDATE jobs SET title=?, company_name=?, description=?, required_skills=?, "
            + "location=?, job_type=?, salary_range=?, eligibility=?, deadline=?, status=? "
            + "WHERE id=? AND recruiter_id=?";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + JOB_COLS_PREFIXED + ", u.full_name AS recruiter_name, u.email AS recruiter_email "
            + "FROM jobs j LEFT JOIN users u ON j.recruiter_id = u.id WHERE j.id=?";

    private static final String SQL_FIND_BY_ID_AND_RECRUITER =
            "SELECT " + COLS + " FROM jobs WHERE id=? AND recruiter_id=?";

    private static final String SQL_FIND_BY_RECRUITER =
            "SELECT " + COLS + " FROM jobs WHERE recruiter_id=? ORDER BY created_at DESC";

    /** Active jobs for job board — supports optional keyword + location + type filters. */
    private static final String SQL_SEARCH_ACTIVE =
            "SELECT " + COLS + " FROM jobs "
            + "WHERE status='ACTIVE' "
            + "AND (title LIKE ? OR company_name LIKE ?) "
            + "AND (? = '' OR location LIKE ?) "
            + "AND (? = '' OR job_type = ?) "
            + "ORDER BY created_at DESC";

    /**
     * Approved and active jobs for student-facing job discovery.
     * Enforces fail-closed security:
     * - Recruiter user account must be active (u.is_active = TRUE)
     * - User role must be RECRUITER (u.role = 'RECRUITER')
     * - Recruiter profile must be verified (rp.verification_status = 'VERIFIED')
     * - Job status must be ACTIVE (j.status = 'ACTIVE')
     * - Job moderation must be APPROVED (j.moderation_status = 'APPROVED')
     * - Deadline must be valid/non-expired (j.deadline IS NULL OR j.deadline >= CURDATE())
     */
    private static final String SQL_SEARCH_APPROVED_ACTIVE =
            "SELECT " + JOB_COLS_PREFIXED + " FROM jobs j "
            + "JOIN users u ON u.id = j.recruiter_id "
            + "JOIN recruiter_profiles rp ON rp.user_id = u.id "
            + "WHERE j.status = 'ACTIVE' "
            + "AND j.moderation_status = 'APPROVED' "
            + "AND u.is_active = TRUE "
            + "AND u.role = 'RECRUITER' "
            + "AND rp.verification_status = 'VERIFIED' "
            + "AND (j.deadline IS NULL OR j.deadline >= CURDATE()) "
            + "AND (j.title LIKE ? OR j.company_name LIKE ?) "
            + "AND (? = '' OR j.location LIKE ?) "
            + "AND (? = '' OR j.job_type = ?) "
            + "ORDER BY j.created_at DESC, j.id DESC";

    /** Non-expired active jobs for recommendations and active counts. */
    private static final String SQL_FIND_OPEN_ACTIVE =
            "SELECT " + COLS + " FROM jobs "
            + "WHERE status='ACTIVE' "
            + "AND (deadline IS NULL OR deadline >= CURDATE()) "
            + "ORDER BY created_at DESC";

    /**
     * Non-expired approved active jobs for student intelligence recommendations.
     * Enforces identical fail-closed verification of recruiter account and moderation status.
     */
    private static final String SQL_FIND_OPEN_APPROVED_ACTIVE =
            "SELECT " + JOB_COLS_PREFIXED + " FROM jobs j "
            + "JOIN users u ON u.id = j.recruiter_id "
            + "JOIN recruiter_profiles rp ON rp.user_id = u.id "
            + "WHERE j.status = 'ACTIVE' "
            + "AND j.moderation_status = 'APPROVED' "
            + "AND u.is_active = TRUE "
            + "AND u.role = 'RECRUITER' "
            + "AND rp.verification_status = 'VERIFIED' "
            + "AND (j.deadline IS NULL OR j.deadline >= CURDATE()) "
            + "ORDER BY j.created_at DESC, j.id DESC";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE jobs SET status=? WHERE id=?";

    private static final String SQL_UPDATE_STATUS_FOR_RECRUITER =
            "UPDATE jobs SET status=? WHERE id=? AND recruiter_id=?";

    private static final String SQL_UPDATE_MODERATION =
            "UPDATE jobs SET moderation_status=?, moderation_reason=?, "
            + "moderated_at=?, moderated_by=? WHERE id=?";

    private static final String SQL_DELETE =
            "DELETE FROM jobs WHERE id=?";

    private static final String SQL_DELETE_FOR_RECRUITER =
            "DELETE FROM jobs WHERE id=? AND recruiter_id=?";

    private static final String SQL_COUNT_ACTIVE_BY_RECRUITER =
            "SELECT COUNT(*) FROM jobs WHERE recruiter_id=? AND status='ACTIVE'";

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------

    /** Inserts a new job. Returns the generated id. */
    public int insert(Job job) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            bindJobParams(stmt, job);   // params 1-15 (no id)
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    job.setId(keys.getInt(1));
                    return job.getId();
                }
                throw new SQLException("Job insert returned no generated key.");
            }
        }
    }

    /** Updates all editable fields. Prefer {@link #updateForRecruiter(Job, int)} for recruiter flows. */
    public void update(Job job) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            bindEditableFields(stmt, job);
            stmt.setInt(11, job.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Atomically updates all editable fields only when {@code id} belongs to {@code recruiterId}.
     *
     * @return number of rows modified (1 when updated, 0 when missing or owned by another recruiter)
     */
    public int updateForRecruiter(Job job, int recruiterId) throws SQLException {
        if (job == null || job.getId() <= 0 || recruiterId <= 0) {
            return 0;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_FOR_RECRUITER)) {

            bindEditableFields(stmt, job);
            stmt.setInt(11, job.getId());
            stmt.setInt(12, recruiterId);

            return stmt.executeUpdate();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 6 Step 4.2C.2.2: Atomic Re-moderation & Failure Safety
    // -----------------------------------------------------------------------

    /**
     * Encapsulates the atomic result of a recruiter-scoped job update.
     */
    public static class JobUpdateResult {
        public enum Status {
            SUCCESS,
            NOT_FOUND_OR_FORBIDDEN,
            VALIDATION_FAILED,
            FAILED
        }

        private final Status status;
        private final String message;
        private final boolean reModerated;
        private final Job job;

        public JobUpdateResult(Status status, String message, boolean reModerated, Job job) {
            this.status = status;
            this.message = message;
            this.reModerated = reModerated;
            this.job = job;
        }

        public static JobUpdateResult success(boolean reModerated, Job job) {
            return new JobUpdateResult(Status.SUCCESS,
                    reModerated ? "Job updated successfully and submitted for administrator re-approval."
                                : "Job updated successfully.",
                    reModerated, job);
        }

        public static JobUpdateResult notFound() {
            return new JobUpdateResult(Status.NOT_FOUND_OR_FORBIDDEN,
                    "Job listing not found or you are not authorised to edit this job.", false, null);
        }

        public static JobUpdateResult validationFailed(String message) {
            return new JobUpdateResult(Status.VALIDATION_FAILED, message, false, null);
        }

        public static JobUpdateResult failed(String message) {
            return new JobUpdateResult(Status.FAILED, message, false, null);
        }

        public static JobUpdateResult activationForbidden(String message) {
            return new JobUpdateResult(Status.VALIDATION_FAILED, message, false, null);
        }

        public boolean isSuccess() { return status == Status.SUCCESS; }
        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public boolean isReModerated() { return reModerated; }
        public Job getJob() { return job; }
    }

    // -----------------------------------------------------------------------
    // Test Failure-Injection Seam (isolated, volatile, thread-safe)
    // -----------------------------------------------------------------------
    private static volatile String failureInjectionPoint = null;

    public static void setFailureInjectionPoint(String point) {
        failureInjectionPoint = point;
    }

    public static String getFailureInjectionPoint() {
        return failureInjectionPoint;
    }

    private static void checkFailureInjection(String currentPoint) throws SQLException {
        String target = failureInjectionPoint;
        if (target != null && target.equalsIgnoreCase(currentPoint)) {
            throw new SQLException("Simulated database failure at injection point: " + currentPoint);
        }
    }

    public static boolean isSubstantiveChange(Job existing, Job updated) {
        if (existing == null || updated == null) return false;
        if (isDifferentString(existing.getTitle(), updated.getTitle())) return true;
        if (isDifferentString(existing.getCompanyName(), updated.getCompanyName())) return true;
        if (isDifferentString(existing.getDescription(), updated.getDescription())) return true;
        if (isDifferentString(existing.getRequiredSkills(), updated.getRequiredSkills())) return true;
        if (isDifferentString(existing.getLocation(), updated.getLocation())) return true;
        if (existing.getJobType() != updated.getJobType()) return true;
        if (isDifferentString(existing.getSalaryRange(), updated.getSalaryRange())) return true;
        if (isDifferentString(existing.getEligibility(), updated.getEligibility())) return true;
        if (!Objects.equals(existing.getDeadline(), updated.getDeadline())) return true;
        return false;
    }

    public static boolean isDifferentString(String original, String updated) {
        String o = original == null ? "" : original.trim();
        String u = updated == null ? "" : updated.trim();
        return !o.equals(u);
    }

    /**
     * Phase 6 Step 4.2C.2.2: Atomically updates substantive job content and enforces re-moderation
     * within a single database transaction.
     *
     * <p>Guarantees:
     * <ul>
     *   <li>Validates ownership and acquires exclusive row lock via {@code SELECT ... FOR UPDATE}.</li>
     *   <li>Validates lifecycle activation status under row lock against database state.</li>
     *   <li>Evaluates substantive changes against the locked record.</li>
     *   <li>If substantive change on APPROVED job: updates content and resets moderation to PENDING with reason
     *       "Content modified by recruiter — pending re-approval" and clears timestamp and attribution.</li>
     *   <li>If no-op / non-substantive update: preserves APPROVED moderation status and metadata.</li>
     *   <li>Atomic commit / rollback: either all changes commit together or all changes roll back.</li>
     * </ul>
     * </p>
     */
    public JobUpdateResult updateJobForRecruiterAtomic(Job job, int recruiterId) throws SQLException {
        if (job == null || job.getId() <= 0 || recruiterId <= 0) {
            return JobUpdateResult.notFound();
        }
        if (job.getJobType() == null || job.getStatus() == null) {
            return JobUpdateResult.validationFailed("Job type and status are required.");
        }

        String selectForUpdateSql =
                "SELECT " + COLS + " FROM jobs WHERE id = ? AND recruiter_id = ? FOR UPDATE";

        String updateContentSql =
                "UPDATE jobs SET title = ?, company_name = ?, description = ?, required_skills = ?, "
                + "location = ?, job_type = ?, salary_range = ?, eligibility = ?, deadline = ?, status = ? "
                + "WHERE id = ? AND recruiter_id = ?";

        String updateModerationSql =
                "UPDATE jobs SET moderation_status = 'PENDING', "
                + "moderation_reason = 'Content modified by recruiter — pending re-approval', "
                + "moderated_at = NULL, moderated_by = NULL "
                + "WHERE id = ? AND recruiter_id = ? AND moderation_status = 'APPROVED'";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Lock row and verify ownership atomically
                Job existing = null;
                try (PreparedStatement selectStmt = conn.prepareStatement(selectForUpdateSql)) {
                    selectStmt.setInt(1, job.getId());
                    selectStmt.setInt(2, recruiterId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            existing = mapRow(rs);
                        } else {
                            conn.rollback();
                            return JobUpdateResult.notFound();
                        }
                    }
                }

                // 2. Substantive change check under lock
                boolean substantiveChange = isSubstantiveChange(existing, job);
                boolean reModerationNeeded = (existing.getModerationStatus() == JobModerationStatus.APPROVED) && substantiveChange;

                // 3. Lifecycle activation & moderation guard under lock
                if (job.getStatus() == Job.Status.ACTIVE && existing.getStatus() != Job.Status.ACTIVE) {
                    if (existing.getModerationStatus() != JobModerationStatus.APPROVED || reModerationNeeded) {
                        conn.rollback();
                        return JobUpdateResult.activationForbidden(
                                "Cannot publish or activate job: Job must be approved by an administrator before activation (current moderation status: "
                                + (reModerationNeeded ? "PENDING" : (existing.getModerationStatus() != null ? existing.getModerationStatus().name() : "PENDING")) + ").");
                    }
                }

                if (job.getStatus() == Job.Status.ACTIVE && (existing.getModerationStatus() == JobModerationStatus.REJECTED
                        || existing.getModerationStatus() == JobModerationStatus.SUSPENDED)) {
                    conn.rollback();
                    return JobUpdateResult.activationForbidden("Cannot activate job: Rejected or suspended jobs cannot be published.");
                }

                if (job.getStatus() == Job.Status.ACTIVE && job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
                    conn.rollback();
                    return JobUpdateResult.activationForbidden("Cannot activate job: Application deadline has expired.");
                }

                // 4. Test failure injection point: CONTENT_UPDATE
                checkFailureInjection("CONTENT_UPDATE");

                // Trim text fields
                if (job.getTitle() != null) job.setTitle(job.getTitle().trim());
                if (job.getCompanyName() != null) job.setCompanyName(job.getCompanyName().trim());
                if (job.getDescription() != null) job.setDescription(job.getDescription().trim());

                // 5. Update content columns
                try (PreparedStatement updateStmt = conn.prepareStatement(updateContentSql)) {
                    bindEditableFields(updateStmt, job);
                    updateStmt.setInt(11, job.getId());
                    updateStmt.setInt(12, recruiterId);
                    int rows = updateStmt.executeUpdate();
                    if (rows == 0) {
                        conn.rollback();
                        return JobUpdateResult.notFound();
                    }
                }

                // 6. Test failure injection point: MODERATION_UPDATE
                checkFailureInjection("MODERATION_UPDATE");

                // 7. If re-moderation needed, atomically reset moderation status to PENDING
                if (reModerationNeeded) {
                    try (PreparedStatement modStmt = conn.prepareStatement(updateModerationSql)) {
                        modStmt.setInt(1, job.getId());
                        modStmt.setInt(2, recruiterId);
                        int modRows = modStmt.executeUpdate();
                        if (modRows == 0) {
                            conn.rollback();
                            return JobUpdateResult.failed(
                                    "Concurrent state conflict: Job moderation status changed during update.");
                        }
                    }
                }

                // 8. Commit entire transaction
                conn.commit();

                // Build result entity reflecting committed state
                Job resultJob = new Job();
                copyJobState(existing, job, reModerationNeeded, resultJob);

                return JobUpdateResult.success(reModerationNeeded, resultJob);

            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    System.err.println("[JobDAO] rollback error: " + rbEx.getMessage());
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignore) {}
            }
        }
    }

    private void copyJobState(Job existing, Job updated, boolean reModerated, Job dest) {
        dest.setId(existing.getId());
        dest.setRecruiterId(existing.getRecruiterId());
        dest.setTitle(updated.getTitle());
        dest.setCompanyName(updated.getCompanyName());
        dest.setDescription(updated.getDescription());
        dest.setRequiredSkills(updated.getRequiredSkills());
        dest.setLocation(updated.getLocation());
        dest.setJobType(updated.getJobType());
        dest.setSalaryRange(updated.getSalaryRange());
        dest.setEligibility(updated.getEligibility());
        dest.setDeadline(updated.getDeadline());
        dest.setStatus(updated.getStatus());
        dest.setCreatedAt(existing.getCreatedAt());

        if (reModerated) {
            dest.setModerationStatus(JobModerationStatus.PENDING);
            dest.setModerationReason("Content modified by recruiter — pending re-approval");
            dest.setModeratedAt(null);
            dest.setModeratedBy(null);
        } else {
            dest.setModerationStatus(existing.getModerationStatus());
            dest.setModerationReason(existing.getModerationReason());
            dest.setModeratedAt(existing.getModeratedAt());
            dest.setModeratedBy(existing.getModeratedBy());
        }
    }

    /** Changes the status of a job (e.g. ACTIVE → CLOSED). Prefer {@link #updateStatusForRecruiter(int, int, Job.Status)}. */
    public void updateStatus(int jobId, Job.Status status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            stmt.setString(1, status.name());
            stmt.setInt   (2, jobId);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically updates job status only when {@code jobId} belongs to {@code recruiterId}.
     *
     * @return number of rows modified (1 when updated, 0 when missing or owned by another recruiter)
     */
    public int updateStatusForRecruiter(int jobId, int recruiterId, Job.Status status) throws SQLException {
        if (jobId <= 0 || recruiterId <= 0 || status == null) {
            return 0;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS_FOR_RECRUITER)) {

            stmt.setString(1, status.name());
            stmt.setInt   (2, jobId);
            stmt.setInt   (3, recruiterId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Updates administrative moderation status of a job posting.
     */
    public boolean updateModerationStatus(int jobId, JobModerationStatus status,
                                          String reason, Integer adminId) throws SQLException {
        boolean isAudited = (status == JobModerationStatus.APPROVED
                          || status == JobModerationStatus.REJECTED
                          || status == JobModerationStatus.SUSPENDED);
        LocalDateTime moderatedAt = isAudited ? LocalDateTime.now() : null;
        Integer admin = isAudited ? adminId : null;
        return updateModerationStatus(jobId, status, reason, admin, moderatedAt);
    }

    /**
     * Updates administrative moderation status with explicit timestamp and administrator attribution.
     */
    public boolean updateModerationStatus(int jobId, JobModerationStatus status,
                                          String reason, Integer adminId, LocalDateTime moderatedAt) throws SQLException {
        if (jobId <= 0 || status == null) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MODERATION)) {

            stmt.setString(1, status.name());
            setNullableString(stmt, 2, reason);

            if (moderatedAt != null) {
                stmt.setTimestamp(3, Timestamp.valueOf(moderatedAt));
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }

            if (adminId != null && adminId > 0) {
                stmt.setInt(4, adminId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }

            stmt.setInt(5, jobId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Deletes a job permanently. Prefer {@link #deleteForRecruiter(int, int)} for recruiter flows. */
    public void delete(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {

            stmt.setInt(1, jobId);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically deletes a job only when {@code jobId} belongs to {@code recruiterId}.
     *
     * @return number of rows deleted (1 when deleted, 0 when missing or owned by another recruiter)
     */
    public int deleteForRecruiter(int jobId, int recruiterId) throws SQLException {
        if (jobId <= 0 || recruiterId <= 0) {
            return 0;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FOR_RECRUITER)) {

            stmt.setInt(1, jobId);
            stmt.setInt(2, recruiterId);
            return stmt.executeUpdate();
        }
    }

    private void bindEditableFields(PreparedStatement stmt, Job job) throws SQLException {
        stmt.setString(1,  job.getTitle());
        stmt.setString(2,  job.getCompanyName());
        stmt.setString(3,  job.getDescription());
        setNullableString(stmt, 4, job.getRequiredSkills());
        setNullableString(stmt, 5, job.getLocation());
        stmt.setString(6,  job.getJobType().name());
        setNullableString(stmt, 7, job.getSalaryRange());
        setNullableString(stmt, 8, job.getEligibility());
        setNullableDate  (stmt, 9, job.getDeadline());
        stmt.setString(10, job.getStatus().name());
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    public Optional<Job> findById(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a job by id only if it is owned by {@code recruiterId}.
     */
    public Optional<Job> findByIdAndRecruiter(int jobId, int recruiterId) throws SQLException {
        if (jobId <= 0 || recruiterId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID_AND_RECRUITER)) {

            stmt.setInt(1, jobId);
            stmt.setInt(2, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    /** Returns all jobs (any status) created by a specific recruiter. */
    public List<Job> findByRecruiterId(int recruiterId) throws SQLException {
        List<Job> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_RECRUITER)) {

            stmt.setInt(1, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Searches ACTIVE jobs with optional filters.
     * Preserved for backward compatibility.
     *
     * @param keyword  title/company search term (may be blank → matches all)
     * @param location filter string (may be blank → matches all)
     * @param jobType  e.g. "INTERNSHIP" (may be blank → matches all types)
     */
    public List<Job> searchActive(String keyword, String location, String jobType) throws SQLException {
        List<Job> result = new ArrayList<>();
        String kw  = "%" + (keyword  == null ? "" : keyword.trim())  + "%";
        String loc =        (location == null ? "" : location.trim());
        String typ =        (jobType  == null ? "" : jobType.trim());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_SEARCH_ACTIVE)) {

            stmt.setString(1, kw);
            stmt.setString(2, kw);
            stmt.setString(3, loc);
            stmt.setString(4, "%" + loc + "%");
            stmt.setString(5, typ);
            stmt.setString(6, typ);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Searches ACTIVE and APPROVED jobs with optional filters for student-facing discovery.
     * Enforces fail-closed security: recruiter is active and verified, job is active, approved, and non-expired.
     */
    public List<Job> searchApprovedActive(String keyword, String location, String jobType) throws SQLException {
        return searchApprovedActive(keyword, location, jobType, -1, -1);
    }

    /**
     * Searches ACTIVE and APPROVED jobs with optional filters and pagination for student-facing discovery.
     * When limit <= 0, returns unpaginated results.
     */
    public List<Job> searchApprovedActive(String keyword, String location, String jobType, int limit, int offset) throws SQLException {
        List<Job> result = new ArrayList<>();
        String kw  = "%" + (keyword  == null ? "" : keyword.trim())  + "%";
        String loc =        (location == null ? "" : location.trim());
        String typ =        (jobType  == null ? "" : jobType.trim());

        String sql = SQL_SEARCH_APPROVED_ACTIVE;
        boolean paginated = limit > 0;
        if (paginated) {
            sql += " LIMIT ? OFFSET ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, kw);
            stmt.setString(2, kw);
            stmt.setString(3, loc);
            stmt.setString(4, "%" + loc + "%");
            stmt.setString(5, typ);
            stmt.setString(6, typ);

            if (paginated) {
                stmt.setInt(7, limit);
                stmt.setInt(8, Math.max(0, offset));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Finds active jobs that have not passed their application deadline.
     * Preserved for backward compatibility.
     */
    public List<Job> findOpenActiveJobs() throws SQLException {
        List<Job> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_OPEN_ACTIVE);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Finds active, approved jobs that have not passed their application deadline.
     * Only returns jobs with active recruiter accounts and verified recruiter profiles.
     */
    public List<Job> findOpenApprovedActiveJobs() throws SQLException {
        List<Job> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_OPEN_APPROVED_ACTIVE);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Finds a single active, approved job by ID that has not passed its application deadline,
     * posted by an active user with a verified recruiter profile.
     * Returns empty if the job does not satisfy all student visibility criteria.
     */
    public Optional<Job> findApprovedActiveById(int jobId) throws SQLException {
        if (jobId <= 0) {
            return Optional.empty();
        }
        String sql = "SELECT " + JOB_COLS_PREFIXED + " FROM jobs j "
                + "JOIN users u ON u.id = j.recruiter_id "
                + "JOIN recruiter_profiles rp ON rp.user_id = u.id "
                + "WHERE j.id = ? "
                + "AND j.status = 'ACTIVE' "
                + "AND j.moderation_status = 'APPROVED' "
                + "AND u.is_active = TRUE "
                + "AND u.role = 'RECRUITER' "
                + "AND rp.verification_status = 'VERIFIED' "
                + "AND (j.deadline IS NULL OR j.deadline >= CURDATE())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Counts active jobs for a recruiter (used in dashboard stats). */
    public int countActiveByRecruiter(int recruiterId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_ACTIVE_BY_RECRUITER)) {

            stmt.setInt(1, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return 0;
            }
        }
    }

    /**
     * Searches jobs for admin review and moderation.
     * Supports search query (title, company name, location, ID), recruiter lifecycle status filter,
     * and moderation status filter.
     */
    public List<Job> searchJobsForAdmin(String query, Job.Status lifecycleStatus,
                                        JobModerationStatus moderationStatus,
                                        int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + JOB_COLS_PREFIXED + ", u.full_name AS recruiter_name, u.email AS recruiter_email FROM jobs j LEFT JOIN users u ON j.recruiter_id = u.id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildAdminJobsWhereClause(sql, params, query, lifecycleStatus, moderationStatus);

        sql.append("ORDER BY j.created_at DESC, j.id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        List<Job> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Counts jobs matching administrative search and filter criteria.
     */
    public int countJobsForAdmin(String query, Job.Status lifecycleStatus,
                                 JobModerationStatus moderationStatus) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM jobs j LEFT JOIN users u ON j.recruiter_id = u.id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildAdminJobsWhereClause(sql, params, query, lifecycleStatus, moderationStatus);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private Job mapRow(ResultSet rs) throws SQLException {
        Job j = new Job();
        j.setId            (rs.getInt   ("id"));
        j.setRecruiterId   (rs.getInt   ("recruiter_id"));
        j.setTitle         (rs.getString("title"));
        j.setCompanyName   (rs.getString("company_name"));
        j.setDescription   (rs.getString("description"));
        j.setRequiredSkills(rs.getString("required_skills"));
        j.setLocation      (rs.getString("location"));
        j.setJobType       (Job.JobType.valueOf(rs.getString("job_type")));
        j.setSalaryRange   (rs.getString("salary_range"));
        j.setEligibility   (rs.getString("eligibility"));
        j.setStatus        (Job.Status.valueOf(rs.getString("status")));

        String modStatus = rs.getString("moderation_status");
        if (modStatus != null) {
            j.setModerationStatus(JobModerationStatus.fromString(modStatus));
        } else {
            j.setModerationStatus(JobModerationStatus.PENDING);
        }

        j.setModerationReason(rs.getString("moderation_reason"));

        Timestamp modAt = rs.getTimestamp("moderated_at");
        if (modAt != null) {
            j.setModeratedAt(modAt.toLocalDateTime());
        }

        int modBy = rs.getInt("moderated_by");
        j.setModeratedBy(rs.wasNull() ? null : modBy);

        Date deadline = rs.getDate("deadline");
        if (deadline != null) j.setDeadline(deadline.toLocalDate());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) j.setCreatedAt(createdAt.toLocalDateTime());

        try {
            j.setRecruiterName(rs.getString("recruiter_name"));
            j.setRecruiterEmail(rs.getString("recruiter_email"));
        } catch (SQLException ignored) {}

        return j;
    }

    /** Bind the 15 job params for INSERT (excludes id; param indices 1-15). */
    private void bindJobParams(PreparedStatement stmt, Job job) throws SQLException {
        stmt.setInt   (1, job.getRecruiterId());
        stmt.setString(2, job.getTitle());
        stmt.setString(3, job.getCompanyName());
        stmt.setString(4, job.getDescription());
        setNullableString(stmt, 5, job.getRequiredSkills());
        setNullableString(stmt, 6, job.getLocation());
        stmt.setString(7, job.getJobType().name());
        setNullableString(stmt, 8, job.getSalaryRange());
        setNullableString(stmt, 9, job.getEligibility());
        setNullableDate  (stmt, 10, job.getDeadline());
        stmt.setString(11, job.getStatus().name());

        JobModerationStatus modStatus = job.getModerationStatus() != null
                ? job.getModerationStatus()
                : JobModerationStatus.PENDING;
        stmt.setString(12, modStatus.name());

        setNullableString(stmt, 13, job.getModerationReason());

        if (job.getModeratedAt() != null) {
            stmt.setTimestamp(14, Timestamp.valueOf(job.getModeratedAt()));
        } else {
            stmt.setNull(14, Types.TIMESTAMP);
        }

        if (job.getModeratedBy() != null && job.getModeratedBy() > 0) {
            stmt.setInt(15, job.getModeratedBy());
        } else {
            stmt.setNull(15, Types.INTEGER);
        }
    }

    private void buildAdminJobsWhereClause(StringBuilder sql, List<Object> params,
                                           String query, Job.Status lifecycleStatus,
                                           JobModerationStatus moderationStatus) {
        if (query != null && !query.trim().isEmpty()) {
            String trimmed = query.trim().toLowerCase();
            String likeParam = "%" + trimmed + "%";
            Integer parsedId = null;
            try {
                parsedId = Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {}

            if (parsedId != null) {
                sql.append("AND (LOWER(j.title) LIKE ? OR LOWER(j.company_name) LIKE ? OR LOWER(j.location) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ? OR j.id = ?) ");
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(parsedId);
            } else {
                sql.append("AND (LOWER(j.title) LIKE ? OR LOWER(j.company_name) LIKE ? OR LOWER(j.location) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ?) ");
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
            }
        }

        if (lifecycleStatus != null) {
            sql.append("AND j.status = ? ");
            params.add(lifecycleStatus.name());
        }

        if (moderationStatus != null) {
            sql.append("AND j.moderation_status = ? ");
            params.add(moderationStatus.name());
        }
    }

    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            int idx = i + 1;
            if (p instanceof String s) {
                stmt.setString(idx, s);
            } else if (p instanceof Integer n) {
                stmt.setInt(idx, n);
            } else {
                stmt.setObject(idx, p);
            }
        }
    }

    private void setNullableString(PreparedStatement s, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) s.setNull(idx, Types.VARCHAR);
        else                              s.setString(idx, val.trim());
    }

    private void setNullableDate(PreparedStatement s, int idx, LocalDate date) throws SQLException {
        if (date == null) s.setNull(idx, Types.DATE);
        else              s.setDate(idx, Date.valueOf(date));
    }
}
