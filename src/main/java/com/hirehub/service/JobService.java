package com.hirehub.service;

import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JobService — business logic for job listing management.
 *
 * <p>Enforces ownership: recruiters may only modify their own jobs.
 * Enforces moderation: only VERIFIED recruiters may create or edit jobs;
 * newly created jobs receive PENDING moderation status; only APPROVED jobs
 * may be activated or published; rejected/suspended jobs cannot be reactivated.</p>
 */
public class JobService {

    private final JobDAO jobDAO;
    private final RecruiterDAO recruiterDAO;
    private final AdminUserDAO adminUserDAO;

    public JobService() {
        this(new JobDAO(), new RecruiterDAO(), new AdminUserDAO());
    }

    public JobService(JobDAO jobDAO, RecruiterDAO recruiterDAO, AdminUserDAO adminUserDAO) {
        this.jobDAO = Objects.requireNonNullElseGet(jobDAO, JobDAO::new);
        this.recruiterDAO = Objects.requireNonNullElseGet(recruiterDAO, RecruiterDAO::new);
        this.adminUserDAO = Objects.requireNonNullElseGet(adminUserDAO, AdminUserDAO::new);
    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    public static class ServiceResult {
        private final boolean success;
        private final String  message;
        private final Job     job;

        private ServiceResult(boolean success, String message, Job job) {
            this.success = success;
            this.message = message;
            this.job     = job;
        }

        public static ServiceResult ok(String msg)          { return new ServiceResult(true,  msg,  null); }
        public static ServiceResult ok(String msg, Job job) { return new ServiceResult(true,  msg,  job);  }
        public static ServiceResult fail(String msg)        { return new ServiceResult(false, msg,  null); }

        public boolean isSuccess() { return success; }
        public String  getMessage(){ return message; }
        public Job     getJob()    { return job; }
    }

    // -----------------------------------------------------------------------
    // Authorization helpers
    // -----------------------------------------------------------------------

    /**
     * Validates recruiter session authorization, role, positive ID match,
     * live database active account state, recruiter profile existence,
     * and optionally verification status (VERIFIED).
     */
    private ServiceResult authorizeRecruiter(int recruiterId, boolean requireVerified) {
        if (recruiterId <= 0) {
            return ServiceResult.fail("Invalid recruiter user ID.");
        }

        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) {
            return ServiceResult.fail("Unauthorized: Recruiter session required.");
        }
        if (current.getRole() != User.Role.RECRUITER) {
            return ServiceResult.fail("Unauthorized: Recruiter role required.");
        }
        if (current.getId() != recruiterId) {
            return ServiceResult.fail("Unauthorized: Session does not match recruiter ID " + recruiterId);
        }
        if (!current.isActive()) {
            return ServiceResult.fail("Unauthorized: Recruiter account is deactivated.");
        }

        // Live database verification to prevent stale-session authorization bypasses
        try {
            Optional<User> dbUserOpt = adminUserDAO.findById(recruiterId);
            if (dbUserOpt.isEmpty()) {
                return ServiceResult.fail("Unauthorized: Recruiter account not found in database.");
            }
            User dbUser = dbUserOpt.get();
            if (dbUser.getRole() != User.Role.RECRUITER) {
                return ServiceResult.fail("Unauthorized: User does not hold recruiter role.");
            }
            if (!dbUser.isActive()) {
                return ServiceResult.fail("Unauthorized: Recruiter account is deactivated.");
            }
        } catch (SQLException e) {
            System.err.println("[JobService] DB error verifying recruiter account: " + e.getMessage());
            return ServiceResult.fail("Database error verifying recruiter account: " + e.getMessage());
        }

        // Profile existence check
        Optional<RecruiterProfile> profileOpt;
        try {
            profileOpt = recruiterDAO.findByUserId(recruiterId);
        } catch (SQLException e) {
            System.err.println("[JobService] DB error finding recruiter profile: " + e.getMessage());
            return ServiceResult.fail("Database error verifying recruiter profile: " + e.getMessage());
        }
        if (profileOpt.isEmpty()) {
            return ServiceResult.fail("Recruiter profile not found. Please complete your company profile before managing jobs.");
        }

        // Verification status check
        if (requireVerified) {
            RecruiterProfile profile = profileOpt.get();
            RecruiterVerificationStatus status = profile.getVerificationStatus();
            if (status != RecruiterVerificationStatus.VERIFIED) {
                return ServiceResult.fail("Recruiter profile is not verified (status: "
                        + (status != null ? status.name() : "UNVERIFIED")
                        + "). Only verified recruiters may post or manage jobs.");
            }
        }

        return ServiceResult.ok("Authorized");
    }

    // -----------------------------------------------------------------------
    // Create job
    // -----------------------------------------------------------------------

    /**
     * Validates and creates a new job listing for the given recruiter.
     * Only VERIFIED recruiters may create job postings.
     * New job listings strictly receive PENDING moderation status.
     *
     * @param recruiterId  the authenticated recruiter's user id
     * @param title        job title (required)
     * @param companyName  company name (required)
     * @param description  full job description (required)
     * @param skills       comma-separated required skills
     * @param location     work location
     * @param jobTypeLabel "Internship", "Full-Time", or "Part-Time"
     * @param salaryRange  optional free-text salary info
     * @param eligibility  eligibility criteria
     * @param deadline     application deadline (must be today or later)
     * @param asDraft      if true, saves with DRAFT status; else ACTIVE
     */
    public ServiceResult createJob(int recruiterId, String title, String companyName,
                                   String description, String skills, String location,
                                   String jobTypeLabel, String salaryRange,
                                   String eligibility, LocalDate deadline, boolean asDraft) {
        ServiceResult auth = authorizeRecruiter(recruiterId, true);
        if (!auth.isSuccess()) {
            return auth;
        }

        ServiceResult validation = validateJobFields(title, companyName, description, deadline);
        if (!validation.isSuccess()) return validation;

        Job job = new Job(
            recruiterId, title.trim(), companyName.trim(), description.trim(),
            skills, location, Job.JobType.fromLabel(jobTypeLabel),
            salaryRange, eligibility, deadline,
            asDraft ? Job.Status.DRAFT : Job.Status.ACTIVE
        );

        // Security enforcement: new jobs must always start with PENDING moderation status
        job.setModerationStatus(JobModerationStatus.PENDING);
        job.setModerationReason(null);
        job.setModeratedAt(null);
        job.setModeratedBy(null);

        try {
            jobDAO.insert(job);
            return ServiceResult.ok("Job posted successfully.", job);
        } catch (SQLException e) {
            System.err.println("[JobService] createJob SQL error (code=" + e.getErrorCode()
                    + ", state=" + e.getSQLState() + "): " + e.getMessage());
            e.printStackTrace(System.err);
            String userMsg = buildUserFriendlyError(e, "post job");
            return ServiceResult.fail(userMsg);
        }
    }

    // -----------------------------------------------------------------------
    // Update job
    // -----------------------------------------------------------------------

    /**
     * Updates an existing job.
     * Ownership check: only the recruiter who created the job may update it.
     * Verification check: only VERIFIED recruiters may edit jobs.
     * Moderation check: activating or publishing a job requires APPROVED moderation status.
     * Moderation fields are strictly protected from modification.
     *
     * @param recruiterId the authenticated recruiter's id (for ownership check)
     * @param jobId       the id of the job to update
     */
    public ServiceResult updateJob(int recruiterId, int jobId, String title, String companyName,
                                   String description, String skills, String location,
                                   String jobTypeLabel, String salaryRange,
                                   String eligibility, LocalDate deadline, Job.Status status) {
        if (jobId <= 0) {
            return ServiceResult.fail("Job listing not found or you are not authorised to edit this job.");
        }
        if (status == null) {
            return ServiceResult.fail("Job status is required.");
        }

        ServiceResult auth = authorizeRecruiter(recruiterId, true);
        if (!auth.isSuccess()) {
            return auth;
        }

        ServiceResult validation = validateJobFields(title, companyName, description, deadline);
        if (!validation.isSuccess()) return validation;

        Job.JobType parsedJobType = Job.JobType.fromLabel(jobTypeLabel);
        if (parsedJobType == null) {
            return ServiceResult.fail("Invalid job type: " + jobTypeLabel);
        }

        Job job = new Job(
                recruiterId,
                title != null ? title.trim() : "",
                companyName != null ? companyName.trim() : "",
                description != null ? description.trim() : "",
                skills,
                location,
                parsedJobType,
                salaryRange,
                eligibility,
                deadline,
                status
        );
        job.setId(jobId);

        return updateJobForRecruiter(job, recruiterId);
    }

    /**
     * Recruiter-scoped job update accepting a {@link Job} entity.
     * Enforces ownership, race condition protection, and re-moderation atomically within a single database transaction.
     * Moderation fields are strictly protected from tampering.
     */
    public ServiceResult updateJobForRecruiter(Job job, int recruiterId) {
        if (job == null || job.getId() <= 0) {
            return ServiceResult.fail("Job listing not found or you are not authorised to edit this job.");
        }
        if (job.getJobType() == null || job.getStatus() == null) {
            return ServiceResult.fail("Job type and status are required.");
        }

        ServiceResult auth = authorizeRecruiter(recruiterId, true);
        if (!auth.isSuccess()) {
            return auth;
        }

        ServiceResult validation = validateJobFields(
                job.getTitle(), job.getCompanyName(), job.getDescription(), job.getDeadline());
        if (!validation.isSuccess()) return validation;

        try {
            JobDAO.JobUpdateResult res = jobDAO.updateJobForRecruiterAtomic(job, recruiterId);
            if (!res.isSuccess()) {
                return ServiceResult.fail(res.getMessage());
            }

            if (res.getJob() != null) {
                job.setModerationStatus(res.getJob().getModerationStatus());
                job.setModerationReason(res.getJob().getModerationReason());
                job.setModeratedAt(res.getJob().getModeratedAt());
                job.setModeratedBy(res.getJob().getModeratedBy());
            }

            return ServiceResult.ok(res.getMessage(), res.getJob());
        } catch (SQLException e) {
            System.err.println("[JobService] updateJobForRecruiter SQL error (code=" + e.getErrorCode()
                    + ", state=" + e.getSQLState() + "): " + e.getMessage());
            e.printStackTrace(System.err);
            return ServiceResult.fail(buildUserFriendlyError(e, "update job"));
        }
    }

    private ServiceResult persistRecruiterJobUpdate(Job job, int recruiterId) throws SQLException {
        int rows = jobDAO.updateForRecruiter(job, recruiterId);
        if (rows <= 0) {
            return ServiceResult.fail("Job listing not found or you are not authorised to edit this job.");
        }
        return ServiceResult.ok("Job updated successfully.", job);
    }

    // -----------------------------------------------------------------------
    // Status management
    // -----------------------------------------------------------------------

    /** Closes a job (ACTIVE → CLOSED). Verifies ownership atomically in SQL. */
    public ServiceResult closeJob(int recruiterId, int jobId) {
        return changeStatus(recruiterId, jobId, Job.Status.CLOSED, "Job closed successfully.");
    }

    /**
     * Re-opens a closed / draft job (→ ACTIVE).
     * Only permitted if recruiter is VERIFIED and job is APPROVED by an administrator.
     */
    public ServiceResult activateJob(int recruiterId, int jobId) {
        return changeStatus(recruiterId, jobId, Job.Status.ACTIVE, "Job activated successfully.");
    }

    /**
     * Recruiter-scoped status transition enforced atomically via {@code WHERE id = ? AND recruiter_id = ?}.
     */
    public ServiceResult updateJobStatusForRecruiter(int jobId, int recruiterId, Job.Status status) {
        if (status == null) {
            return ServiceResult.fail("Job status is required.");
        }
        String msg = switch (status) {
            case CLOSED -> "Job closed successfully.";
            case ACTIVE -> "Job activated successfully.";
            case DRAFT  -> "Job saved as draft successfully.";
        };
        return changeStatus(recruiterId, jobId, status, msg);
    }

    /** Deletes a job. Verifies ownership atomically in SQL. */
    public ServiceResult deleteJob(int recruiterId, int jobId) {
        return deleteJobForRecruiter(jobId, recruiterId);
    }

    /**
     * Recruiter-scoped delete enforced atomically via {@code WHERE id = ? AND recruiter_id = ?}.
     */
    public ServiceResult deleteJobForRecruiter(int jobId, int recruiterId) {
        if (jobId <= 0) {
            return ServiceResult.fail("Job listing not found or you are not authorised to delete this job.");
        }
        ServiceResult auth = authorizeRecruiter(recruiterId, false);
        if (!auth.isSuccess()) {
            return auth;
        }
        try {
            Optional<Job> existingOpt = jobDAO.findByIdAndRecruiter(jobId, recruiterId);
            if (existingOpt.isEmpty()) {
                return ServiceResult.fail("Job listing not found or you are not authorised to delete this job.");
            }
            int deleted = jobDAO.deleteForRecruiter(jobId, recruiterId);
            if (deleted <= 0) {
                return ServiceResult.fail("Job listing not found or you are not authorised to delete this job.");
            }
            return ServiceResult.ok("Job deleted successfully.");
        } catch (SQLException e) {
            System.err.println("[JobService] deleteJob error: " + e.getMessage());
            return ServiceResult.fail("Could not delete job. Please try again.");
        }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    /** Returns all jobs (any status) for a recruiter. */
    public List<Job> getJobsByRecruiter(int recruiterId) {
        if (recruiterId <= 0) {
            return Collections.emptyList();
        }
        try {
            return jobDAO.findByRecruiterId(recruiterId);
        } catch (SQLException e) {
            System.err.println("[JobService] getJobsByRecruiter error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Searches approved-and-verified active jobs visible to students.
     * Uses the fail-closed {@code searchApprovedActive()} DAO method which enforces:
     * job ACTIVE + moderation APPROVED + recruiter active + recruiter VERIFIED + deadline valid.
     *
     * <p>Legacy callers that relied on {@code searchActive()} must use this method for
     * student-facing job discovery. Recruiter/Admin internal lookups retain their own paths.</p>
     */
    public List<Job> searchJobs(String keyword, String location, String jobType) {
        try {
            return jobDAO.searchApprovedActive(keyword, location, jobType);
        } catch (SQLException e) {
            System.err.println("[JobService] searchJobs error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Searches approved-and-verified active jobs with pagination for student-facing discovery.
     */
    public List<Job> searchJobsForStudents(String keyword, String location, String jobType,
                                           int limit, int offset) {
        try {
            return jobDAO.searchApprovedActive(keyword, location, jobType, limit, offset);
        } catch (SQLException e) {
            System.err.println("[JobService] searchJobsForStudents error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Returns the active job count for a recruiter (dashboard stat). */
    public int getActiveJobCount(int recruiterId) {
        if (recruiterId <= 0) {
            return 0;
        }
        try {
            return jobDAO.countActiveByRecruiter(recruiterId);
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Retrieves a job by ID honoring role and visibility context:
     * <ul>
     *   <li>ADMIN or RECRUITER session: internal lookup via {@code findById}.</li>
     *   <li>STUDENT or unauthenticated session: strictly filtered to active, approved,
     *       verified-recruiter jobs with non-expired deadline; moderation metadata is stripped.</li>
     * </ul>
     */
    public Optional<Job> getJobById(int jobId) {
        if (jobId <= 0) {
            return Optional.empty();
        }
        User current = SessionManager.getInstance().getCurrentUser();
        if (current != null && (current.getRole() == User.Role.ADMIN || current.getRole() == User.Role.RECRUITER)) {
            try {
                return jobDAO.findById(jobId);
            } catch (SQLException e) {
                System.err.println("[JobService] getJobById error: " + e.getMessage());
                return Optional.empty();
            }
        }
        return getJobByIdForStudent(jobId);
    }

    /**
     * Student-facing direct-ID lookup: returns the job only if it satisfies every student
     * visibility condition (ACTIVE + APPROVED + active RECRUITER user + VERIFIED recruiter profile +
     * deadline NULL or >= CURDATE()).
     *
     * <p>Omits / sanitizes moderation status, reasons, and audit metadata to prevent data leakage.</p>
     */
    public Optional<Job> getJobByIdForStudent(int jobId) {
        if (jobId <= 0) {
            return Optional.empty();
        }
        try {
            return jobDAO.findApprovedActiveById(jobId).map(this::sanitizeJobForStudent);
        } catch (SQLException e) {
            System.err.println("[JobService] getJobByIdForStudent error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Strips moderation status, reasons, and admin audit fields from a job object
     * before returning it in a student-facing context.
     */
    private Job sanitizeJobForStudent(Job job) {
        if (job == null) return null;
        Job safe = new Job();
        safe.setId(job.getId());
        safe.setRecruiterId(job.getRecruiterId());
        safe.setTitle(job.getTitle());
        safe.setCompanyName(job.getCompanyName());
        safe.setDescription(job.getDescription());
        safe.setRequiredSkills(job.getRequiredSkills());
        safe.setLocation(job.getLocation());
        safe.setJobType(job.getJobType());
        safe.setSalaryRange(job.getSalaryRange());
        safe.setEligibility(job.getEligibility());
        safe.setDeadline(job.getDeadline());
        safe.setStatus(job.getStatus());
        safe.setCreatedAt(job.getCreatedAt());
        // Deliberately leave moderationStatus, moderationReason, moderatedBy, moderatedAt as null
        return safe;
    }

    /**
     * Admin-only: loads any job by id regardless of moderation or lifecycle status.
     * Never use this for student-facing operations.
     */
    public Optional<Job> getJobByIdForAdmin(int jobId) {
        if (jobId <= 0) {
            return Optional.empty();
        }
        try {
            return jobDAO.findById(jobId);
        } catch (SQLException e) {
            System.err.println("[JobService] getJobByIdForAdmin error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads a single job (including drafts/closed listings) only when owned by {@code recruiterId}.
     */
    public Optional<Job> getJobByIdForRecruiter(int jobId, int recruiterId) {
        if (jobId <= 0 || recruiterId <= 0) {
            return Optional.empty();
        }
        try {
            return jobDAO.findByIdAndRecruiter(jobId, recruiterId);
        } catch (SQLException e) {
            System.err.println("[JobService] getJobByIdForRecruiter error: " + e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ServiceResult changeStatus(int recruiterId, int jobId,
                                       Job.Status newStatus, String successMsg) {
        if (jobId <= 0 || newStatus == null) {
            return ServiceResult.fail("Job listing not found or you are not authorised to modify this job.");
        }

        // For ACTIVE status transition (publishing/activation), recruiter must be VERIFIED.
        // For CLOSED or DRAFT status, recruiter must be authenticated and active.
        boolean requireVerified = (newStatus == Job.Status.ACTIVE);
        ServiceResult auth = authorizeRecruiter(recruiterId, requireVerified);
        if (!auth.isSuccess()) {
            return auth;
        }

        try {
            Optional<Job> existingOpt = jobDAO.findByIdAndRecruiter(jobId, recruiterId);
            if (existingOpt.isEmpty()) {
                return ServiceResult.fail("Job listing not found or you are not authorised to modify this job.");
            }
            Job existing = existingOpt.get();

            // Moderation check on activation / publishing:
            if (newStatus == Job.Status.ACTIVE) {
                if (existing.getModerationStatus() != JobModerationStatus.APPROVED) {
                    return ServiceResult.fail("Cannot publish or activate job: Job must be approved by an administrator before activation (current moderation status: "
                            + (existing.getModerationStatus() != null ? existing.getModerationStatus().name() : "PENDING") + ").");
                }
                if (existing.getModerationStatus() == JobModerationStatus.REJECTED
                        || existing.getModerationStatus() == JobModerationStatus.SUSPENDED) {
                    return ServiceResult.fail("Cannot activate job: Rejected or suspended jobs cannot be published.");
                }
                if (existing.getDeadline() != null && existing.getDeadline().isBefore(LocalDate.now())) {
                    return ServiceResult.fail("Cannot activate job: Application deadline has expired.");
                }
            }

            int updated = jobDAO.updateStatusForRecruiter(jobId, recruiterId, newStatus);
            if (updated <= 0) {
                return ServiceResult.fail("Job listing not found or you are not authorised to modify this job.");
            }
            return ServiceResult.ok(successMsg);
        } catch (SQLException e) {
            System.err.println("[JobService] changeStatus error: " + e.getMessage());
            return ServiceResult.fail("Could not update job status. Please try again.");
        }
    }

    private ServiceResult validateJobFields(String title, String companyName,
                                            String description, LocalDate deadline) {
        if (title == null || title.isBlank())
            return ServiceResult.fail("Job title is required.");
        if (companyName == null || companyName.isBlank())
            return ServiceResult.fail("Company name is required.");
        if (description == null || description.isBlank())
            return ServiceResult.fail("Job description is required.");
        if (deadline != null && deadline.isBefore(LocalDate.now()))
            return ServiceResult.fail("Application deadline cannot be in the past. Please select today or a future date.");
        return ServiceResult.ok("valid");
    }

    /**
     * Determines whether any substantive content field of the job listing has changed.
     * Content fields subject to re-moderation when modified on an APPROVED job:
     * title, company name, description, required skills, location, job type,
     * salary range, eligibility criteria, application deadline.
     */
    private boolean isSubstantiveChange(Job existing,
                                        String title,
                                        String companyName,
                                        String description,
                                        String skills,
                                        String location,
                                        Job.JobType jobType,
                                        String salaryRange,
                                        String eligibility,
                                        LocalDate deadline) {
        if (isDifferentString(existing.getTitle(), title)) return true;
        if (isDifferentString(existing.getCompanyName(), companyName)) return true;
        if (isDifferentString(existing.getDescription(), description)) return true;
        if (isDifferentString(existing.getRequiredSkills(), skills)) return true;
        if (isDifferentString(existing.getLocation(), location)) return true;
        if (existing.getJobType() != jobType) return true;
        if (isDifferentString(existing.getSalaryRange(), salaryRange)) return true;
        if (isDifferentString(existing.getEligibility(), eligibility)) return true;
        if (!Objects.equals(existing.getDeadline(), deadline)) return true;
        return false;
    }

    private boolean isDifferentString(String original, String updated) {
        String o = original == null ? "" : original.trim();
        String u = updated == null ? "" : updated.trim();
        return !o.equals(u);
    }

    /**
     * Translates a raw SQLException into a user-readable message.
     */
    private String buildUserFriendlyError(SQLException e, String action) {
        int code = e.getErrorCode();
        if (code == 1146) {
            return "Database table missing — please run the migration SQL script and restart.";
        } else if (code == 1049) {
            return "Database not found — check DB_NAME configuration.";
        } else if (code == 1452 || code == 1215) {
            return "Foreign key error — ensure your recruiter account exists in the database.";
        } else if (code == 1062) {
            return "A duplicate record already exists.";
        } else if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
            return "Database connection failed — check that MySQL is running and credentials are correct.";
        }
        return "Could not " + action + ". Database error (" + code + "): " + e.getMessage();
    }
}
