package com.hirehub.service;

import com.hirehub.dao.AdminModerationDAO;
import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.AdminJobModerationKPI;
import com.hirehub.model.AdminRecruiterVerificationKPI;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AdminModerationService — service-layer authorization boundary for administrative
 * verification of recruiter profiles and moderation of job postings.
 *
 * <p>Enforces strict service-layer authorization:
 * <ul>
 *   <li>Only authenticated administrators ({@link User.Role#ADMIN}) may invoke moderation operations.</li>
 *   <li>The supplied {@code adminId} must be positive and match the authenticated session's user ID.</li>
 *   <li>The acting administrator's account must be active in session and verified active in the database
 *       to prevent stale-session authorization bypasses.</li>
 *   <li>Recruiter verification decisions delegate atomically to {@link AdminModerationDAO}.</li>
 *   <li>Job moderation decisions delegate atomically to {@link AdminModerationDAO}.</li>
 *   <li>Job moderation status is maintained strictly independent from job lifecycle status.</li>
 *   <li>Every successful status transition commits alongside an immutable audit entry in {@code admin_audit_logs}.</li>
 * </ul>
 * </p>
 */
public class AdminModerationService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final int MIN_REASON_LENGTH = 3;

    private final AdminModerationDAO moderationDAO;
    private final RecruiterDAO recruiterDAO;
    private final JobDAO jobDAO;
    private final AdminUserDAO adminUserDAO;

    /**
     * Default constructor creating production DAOs.
     */
    public AdminModerationService() {
        this(new AdminModerationDAO(), new RecruiterDAO(), new JobDAO(), new AdminUserDAO());
    }

    /**
     * Dependency injection constructor for testing and modular initialization.
     */
    public AdminModerationService(AdminModerationDAO moderationDAO,
                                  RecruiterDAO recruiterDAO,
                                  JobDAO jobDAO,
                                  AdminUserDAO adminUserDAO) {
        this.moderationDAO = Objects.requireNonNullElseGet(moderationDAO, AdminModerationDAO::new);
        this.recruiterDAO  = Objects.requireNonNullElseGet(recruiterDAO, RecruiterDAO::new);
        this.jobDAO        = Objects.requireNonNullElseGet(jobDAO, JobDAO::new);
        this.adminUserDAO  = Objects.requireNonNullElseGet(adminUserDAO, AdminUserDAO::new);
    }

    // =========================================================================
    // RECRUITER VERIFICATION OPERATIONS
    // =========================================================================

    /**
     * Approves and marks a recruiter profile as VERIFIED.
     */
    public ServiceResult<Void> verifyRecruiter(int adminId, int recruiterUserId, String reason) {
        return moderateRecruiter(adminId, recruiterUserId, RecruiterVerificationStatus.VERIFIED, reason);
    }

    /**
     * Rejects a recruiter profile. Requires a meaningful reason.
     */
    public ServiceResult<Void> rejectRecruiter(int adminId, int recruiterUserId, String reason) {
        return moderateRecruiter(adminId, recruiterUserId, RecruiterVerificationStatus.REJECTED, reason);
    }

    /**
     * Suspends an active/verified recruiter profile. Requires a meaningful reason.
     */
    public ServiceResult<Void> suspendRecruiter(int adminId, int recruiterUserId, String reason) {
        return moderateRecruiter(adminId, recruiterUserId, RecruiterVerificationStatus.SUSPENDED, reason);
    }

    /**
     * Returns a recruiter profile back to PENDING review status.
     */
    public ServiceResult<Void> returnRecruiterToPending(int adminId, int recruiterUserId, String reason) {
        return moderateRecruiter(adminId, recruiterUserId, RecruiterVerificationStatus.PENDING, reason);
    }

    /**
     * Alias for {@link #returnRecruiterToPending(int, int, String)}.
     */
    public ServiceResult<Void> resetRecruiterToPending(int adminId, int recruiterUserId, String reason) {
        return returnRecruiterToPending(adminId, recruiterUserId, reason);
    }

    /**
     * Dispatches a recruiter verification status mutation with strict validation, state verification,
     * and transactional audit logging.
     *
     * @param adminId         acting administrator ID
     * @param recruiterUserId target recruiter user ID
     * @param status          target verification status
     * @param reason          explanation (mandatory for REJECTED/SUSPENDED)
     * @return ServiceResult indicating outcome
     */
    public ServiceResult<Void> moderateRecruiter(int adminId, int recruiterUserId,
                                                 RecruiterVerificationStatus status, String reason) {
        // 1. Authorization checks
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return authCheck;
        }

        // 2. Status validation
        if (status == null) {
            return ServiceResult.failure("Moderation status cannot be null.");
        }
        if (status == RecruiterVerificationStatus.UNVERIFIED) {
            return ServiceResult.failure("Cannot transition recruiter back to UNVERIFIED; use PENDING for review.");
        }

        // 3. Target ID validation
        if (recruiterUserId <= 0) {
            return ServiceResult.failure("Invalid recruiter user ID: recruiterUserId must be positive.");
        }

        // 4. Reason validation
        boolean reasonMandatory = (status == RecruiterVerificationStatus.REJECTED
                || status == RecruiterVerificationStatus.SUSPENDED);
        ServiceResult<String> reasonValidation = validateReason(reason, reasonMandatory,
                status == RecruiterVerificationStatus.REJECTED ? "rejecting a recruiter" : "suspending a recruiter");
        if (!reasonValidation.isSuccess()) {
            return ServiceResult.failure(reasonValidation.getMessage());
        }
        String sanitizedReason = reasonValidation.getData();

        // 5. Target existence and state transition verification
        try {
            Optional<RecruiterProfile> profileOpt = recruiterDAO.findByUserId(recruiterUserId);
            if (profileOpt.isEmpty()) {
                return ServiceResult.failure("Recruiter profile not found for user ID: " + recruiterUserId);
            }

            RecruiterProfile currentProfile = profileOpt.get();
            if (currentProfile.getVerificationStatus() == status) {
                return ServiceResult.failure("Recruiter profile is already " + status.name().toLowerCase() + ".");
            }

            // 6. Atomic transactional update + audit log
            boolean updated = moderationDAO.updateRecruiterVerificationWithAudit(
                    adminId, recruiterUserId, status, sanitizedReason);

            if (updated) {
                return ServiceResult.ok("Recruiter profile " + status.name().toLowerCase() + " successfully.");
            } else {
                return ServiceResult.failure("Failed to update recruiter verification status.");
            }
        } catch (SQLException e) {
            return ServiceResult.failure("Database error moderating recruiter profile: " + e.getMessage());
        }
    }

    // =========================================================================
    // JOB MODERATION OPERATIONS
    // =========================================================================

    /**
     * Approves a job posting for student visibility (subject to active lifecycle and verified recruiter).
     * Does NOT alter the job's lifecycle status.
     */
    public ServiceResult<Void> approveJob(int adminId, int jobId, String reason) {
        return moderateJob(adminId, jobId, JobModerationStatus.APPROVED, reason);
    }

    /**
     * Rejects a job posting. Requires a meaningful reason.
     */
    public ServiceResult<Void> rejectJob(int adminId, int jobId, String reason) {
        return moderateJob(adminId, jobId, JobModerationStatus.REJECTED, reason);
    }

    /**
     * Suspends a previously approved job posting. Requires a meaningful reason.
     */
    public ServiceResult<Void> suspendJob(int adminId, int jobId, String reason) {
        return moderateJob(adminId, jobId, JobModerationStatus.SUSPENDED, reason);
    }

    /**
     * Returns a job posting back to PENDING review status.
     */
    public ServiceResult<Void> returnJobToPending(int adminId, int jobId, String reason) {
        return moderateJob(adminId, jobId, JobModerationStatus.PENDING, reason);
    }

    /**
     * Alias for {@link #returnJobToPending(int, int, String)}.
     */
    public ServiceResult<Void> resetJobToPending(int adminId, int jobId, String reason) {
        return returnJobToPending(adminId, jobId, reason);
    }

    /**
     * Dispatches a job moderation status mutation with strict validation, state verification,
     * and transactional audit logging.
     *
     * @param adminId acting administrator ID
     * @param jobId   target job posting ID
     * @param status  target moderation status
     * @param reason  explanation (mandatory for REJECTED/SUSPENDED)
     * @return ServiceResult indicating outcome
     */
    public ServiceResult<Void> moderateJob(int adminId, int jobId,
                                           JobModerationStatus status, String reason) {
        // 1. Authorization checks
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return authCheck;
        }

        // 2. Status validation
        if (status == null) {
            return ServiceResult.failure("Moderation status cannot be null.");
        }

        // 3. Target ID validation
        if (jobId <= 0) {
            return ServiceResult.failure("Invalid job ID: jobId must be positive.");
        }

        // 4. Reason validation
        boolean reasonMandatory = (status == JobModerationStatus.REJECTED
                || status == JobModerationStatus.SUSPENDED);
        ServiceResult<String> reasonValidation = validateReason(reason, reasonMandatory,
                status == JobModerationStatus.REJECTED ? "rejecting a job" : "suspending a job");
        if (!reasonValidation.isSuccess()) {
            return ServiceResult.failure(reasonValidation.getMessage());
        }
        String sanitizedReason = reasonValidation.getData();

        // 5. Target existence and state transition verification
        try {
            Optional<Job> jobOpt = jobDAO.findById(jobId);
            if (jobOpt.isEmpty()) {
                return ServiceResult.failure("Job not found with ID: " + jobId);
            }

            Job currentJob = jobOpt.get();
            if (currentJob.getModerationStatus() == status) {
                return ServiceResult.failure("Job is already " + status.name().toLowerCase() + ".");
            }

            // 6. Atomic transactional update + audit log
            boolean updated = moderationDAO.updateJobModerationWithAudit(
                    adminId, jobId, status, sanitizedReason);

            if (updated) {
                return ServiceResult.ok("Job posting " + status.name().toLowerCase() + " successfully.");
            } else {
                return ServiceResult.failure("Failed to update job moderation status.");
            }
        } catch (SQLException e) {
            return ServiceResult.failure("Database error moderating job posting: " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMIN MODERATION DATA: RECRUITERS
    // =========================================================================

    /**
     * Lists recruiter profiles for admin review matching filter criteria with pagination.
     */
    public ServiceResult<List<RecruiterProfile>> listRecruitersForModeration(
            String query, RecruiterVerificationStatus statusFilter, int limit, int offset) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return listRecruitersInternal(query, statusFilter, limit, offset);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<List<RecruiterProfile>> listRecruitersForModeration(
            int adminId, String query, RecruiterVerificationStatus statusFilter, int limit, int offset) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return listRecruitersInternal(query, statusFilter, limit, offset);
    }

    private ServiceResult<List<RecruiterProfile>> listRecruitersInternal(
            String query, RecruiterVerificationStatus statusFilter, int limit, int offset) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        int safeOffset = Math.max(0, offset);

        try {
            List<RecruiterProfile> list = recruiterDAO.searchRecruitersForAdmin(query, statusFilter, safeLimit, safeOffset);
            return ServiceResult.success(list);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving recruiters for moderation: " + e.getMessage());
        }
    }

    /**
     * Counts recruiter profiles matching review filter criteria.
     */
    public ServiceResult<Integer> countRecruitersForModeration(String query, RecruiterVerificationStatus statusFilter) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return countRecruitersInternal(query, statusFilter);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<Integer> countRecruitersForModeration(int adminId, String query,
                                                               RecruiterVerificationStatus statusFilter) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return countRecruitersInternal(query, statusFilter);
    }

    private ServiceResult<Integer> countRecruitersInternal(String query, RecruiterVerificationStatus statusFilter) {
        try {
            int count = recruiterDAO.countRecruitersForAdmin(query, statusFilter);
            return ServiceResult.success(count);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error counting recruiters for moderation: " + e.getMessage());
        }
    }

    /**
     * Retrieves a single recruiter profile by recruiter user ID for moderation review.
     */
    public ServiceResult<RecruiterProfile> getRecruiterProfileByUserId(int recruiterUserId) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecruiterProfileInternal(recruiterUserId);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<RecruiterProfile> getRecruiterProfileByUserId(int adminId, int recruiterUserId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecruiterProfileInternal(recruiterUserId);
    }

    private ServiceResult<RecruiterProfile> getRecruiterProfileInternal(int recruiterUserId) {
        if (recruiterUserId <= 0) {
            return ServiceResult.failure("Invalid recruiter user ID: " + recruiterUserId);
        }
        try {
            Optional<RecruiterProfile> opt = recruiterDAO.findByUserId(recruiterUserId);
            return opt.map(ServiceResult::success)
                    .orElseGet(() -> ServiceResult.failure("Recruiter profile not found for user ID: " + recruiterUserId));
        } catch (SQLException e) {
            return ServiceResult.failure("Database error finding recruiter profile: " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMIN MODERATION DATA: JOBS
    // =========================================================================

    /**
     * Lists jobs for admin review matching filter criteria with pagination.
     */
    public ServiceResult<List<Job>> listJobsForModeration(
            String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus,
            int limit, int offset) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return listJobsInternal(query, lifecycleStatus, moderationStatus, limit, offset);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<List<Job>> listJobsForModeration(
            int adminId, String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus,
            int limit, int offset) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return listJobsInternal(query, lifecycleStatus, moderationStatus, limit, offset);
    }

    private ServiceResult<List<Job>> listJobsInternal(
            String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus,
            int limit, int offset) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        int safeOffset = Math.max(0, offset);

        try {
            List<Job> list = jobDAO.searchJobsForAdmin(query, lifecycleStatus, moderationStatus, safeLimit, safeOffset);
            return ServiceResult.success(list);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving jobs for moderation: " + e.getMessage());
        }
    }

    /**
     * Counts jobs matching moderation filter criteria.
     */
    public ServiceResult<Integer> countJobsForModeration(
            String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return countJobsInternal(query, lifecycleStatus, moderationStatus);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<Integer> countJobsForModeration(
            int adminId, String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return countJobsInternal(query, lifecycleStatus, moderationStatus);
    }

    private ServiceResult<Integer> countJobsInternal(
            String query, Job.Status lifecycleStatus, JobModerationStatus moderationStatus) {
        try {
            int count = jobDAO.countJobsForAdmin(query, lifecycleStatus, moderationStatus);
            return ServiceResult.success(count);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error counting jobs for moderation: " + e.getMessage());
        }
    }

    /**
     * Retrieves a single job by its ID for moderation review.
     */
    public ServiceResult<Job> getJobById(int jobId) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getJobByIdInternal(jobId);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<Job> getJobById(int adminId, int jobId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getJobByIdInternal(jobId);
    }

    private ServiceResult<Job> getJobByIdInternal(int jobId) {
        if (jobId <= 0) {
            return ServiceResult.failure("Invalid job ID: " + jobId);
        }
        try {
            Optional<Job> opt = jobDAO.findById(jobId);
            return opt.map(ServiceResult::success)
                    .orElseGet(() -> ServiceResult.failure("Job not found with ID: " + jobId));
        } catch (SQLException e) {
            return ServiceResult.failure("Database error finding job: " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMIN MODERATION DATA: AUDIT LOGS & MODERATION HISTORY
    // =========================================================================

    /**
     * Retrieves moderation audit logs for a specific recruiter profile.
     */
    public ServiceResult<List<AdminAuditLog>> getModerationLogsForRecruiter(int recruiterUserId) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getModerationLogsForRecruiterInternal(recruiterUserId);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<List<AdminAuditLog>> getModerationLogsForRecruiter(int adminId, int recruiterUserId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getModerationLogsForRecruiterInternal(recruiterUserId);
    }

    private ServiceResult<List<AdminAuditLog>> getModerationLogsForRecruiterInternal(int recruiterUserId) {
        if (recruiterUserId <= 0) {
            return ServiceResult.failure("Invalid recruiter user ID: " + recruiterUserId);
        }
        try {
            List<AdminAuditLog> logs = moderationDAO.getAuditLogsForRecruiter(recruiterUserId);
            return ServiceResult.success(logs);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving recruiter audit logs: " + e.getMessage());
        }
    }

    /**
     * Retrieves moderation audit logs for a specific job posting.
     */
    public ServiceResult<List<AdminAuditLog>> getModerationLogsForJob(int jobId) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getModerationLogsForJobInternal(jobId);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<List<AdminAuditLog>> getModerationLogsForJob(int adminId, int jobId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getModerationLogsForJobInternal(jobId);
    }

    private ServiceResult<List<AdminAuditLog>> getModerationLogsForJobInternal(int jobId) {
        if (jobId <= 0) {
            return ServiceResult.failure("Invalid job ID: " + jobId);
        }
        try {
            List<AdminAuditLog> logs = moderationDAO.getAuditLogsForJob(jobId);
            return ServiceResult.success(logs);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving job audit logs: " + e.getMessage());
        }
    }

    /**
     * Retrieves recent moderation audit logs across recruiters and jobs.
     */
    public ServiceResult<List<AdminAuditLog>> getRecentModerationLogs(int limit) {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecentModerationLogsInternal(limit);
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<List<AdminAuditLog>> getRecentModerationLogs(int adminId, int limit) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecentModerationLogsInternal(limit);
    }

    private ServiceResult<List<AdminAuditLog>> getRecentModerationLogsInternal(int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        try {
            List<AdminAuditLog> logs = moderationDAO.getRecentModerationLogs(safeLimit);
            return ServiceResult.success(logs);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving recent moderation logs: " + e.getMessage());
        }
    }

    /**
     * Retrieves live aggregate verification counts for all registered recruiters.
     */
    public ServiceResult<AdminRecruiterVerificationKPI> getRecruiterVerificationKPI() {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecruiterVerificationKPIInternal();
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<AdminRecruiterVerificationKPI> getRecruiterVerificationKPI(int adminId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getRecruiterVerificationKPIInternal();
    }

    private ServiceResult<AdminRecruiterVerificationKPI> getRecruiterVerificationKPIInternal() {
        try {
            AdminRecruiterVerificationKPI kpi = moderationDAO.getRecruiterVerificationKPI();
            return ServiceResult.success(kpi);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving recruiter verification KPI: " + e.getMessage());
        }
    }

    /**
     * Retrieves live aggregate moderation and lifecycle counts for all job postings.
     */
    public ServiceResult<AdminJobModerationKPI> getJobModerationKPI() {
        ServiceResult<Void> authCheck = authorizeCurrentAdmin();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getJobModerationKPIInternal();
    }

    /**
     * Overloaded version accepting explicit admin ID.
     */
    public ServiceResult<AdminJobModerationKPI> getJobModerationKPI(int adminId) {
        ServiceResult<Void> authCheck = authorizeAdmin(adminId);
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }
        return getJobModerationKPIInternal();
    }

    private ServiceResult<AdminJobModerationKPI> getJobModerationKPIInternal() {
        try {
            AdminJobModerationKPI kpi = moderationDAO.getJobModerationKPI();
            return ServiceResult.success(kpi);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving job moderation KPI: " + e.getMessage());
        }
    }

    // =========================================================================
    // AUTHORIZATION & VALIDATION HELPERS
    // =========================================================================

    /**
     * Basic check on in-memory session: session must exist, role must be ADMIN,
     * and session user must be active.
     */
    private ServiceResult<Void> checkAdminSession() {
        SessionManager session = SessionManager.getInstance();
        if (!session.isLoggedIn()) {
            return ServiceResult.failure("Unauthorized: No active session found.");
        }
        if (!session.hasRole(User.Role.ADMIN)) {
            return ServiceResult.failure("Unauthorized: Administrator role is required.");
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0) {
            return ServiceResult.failure("Unauthorized: Invalid session user.");
        }
        if (!currentUser.isActive()) {
            return ServiceResult.failure("Unauthorized: Authenticated administrator account is deactivated.");
        }
        return ServiceResult.ok("Authorized");
    }

    /**
     * Authorizes the current admin session for operations that do not take an explicit admin ID argument.
     */
    private ServiceResult<Void> authorizeCurrentAdmin() {
        ServiceResult<Void> sessionCheck = checkAdminSession();
        if (!sessionCheck.isSuccess()) {
            return sessionCheck;
        }
        User currentUser = SessionManager.getInstance().getCurrentUser();
        return authorizeAdmin(currentUser.getId());
    }

    /**
     * Comprehensive admin authorization boundary:
     * 1. Session exists and role is ADMIN.
     * 2. Supplied adminId is positive.
     * 3. Supplied adminId matches the authenticated session user ID.
     * 4. Authenticated admin is active in memory.
     * 5. Authenticated admin is verified active in the database (guard against stale sessions).
     */
    private ServiceResult<Void> authorizeAdmin(int adminId) {
        ServiceResult<Void> sessionCheck = checkAdminSession();
        if (!sessionCheck.isSuccess()) {
            return sessionCheck;
        }

        if (adminId <= 0) {
            return ServiceResult.failure("Unauthorized: Admin ID must be a positive integer.");
        }

        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin.getId() != adminId) {
            return ServiceResult.failure("Admin identity mismatch: session user ID ("
                    + currentAdmin.getId() + ") does not match supplied admin ID (" + adminId + ").");
        }

        // Live database verification to prevent stale-session authorization bypasses
        try {
            Optional<User> dbUserOpt = adminUserDAO.findById(adminId);
            if (dbUserOpt.isEmpty()) {
                return ServiceResult.failure("Unauthorized: Administrator account not found in database.");
            }
            User dbUser = dbUserOpt.get();
            if (dbUser.getRole() != User.Role.ADMIN) {
                return ServiceResult.failure("Unauthorized: User does not hold administrator role.");
            }
            if (!dbUser.isActive()) {
                return ServiceResult.failure("Unauthorized: Authenticated administrator account is deactivated.");
            }
        } catch (SQLException e) {
            return ServiceResult.failure("Database error verifying administrator account: " + e.getMessage());
        }

        return ServiceResult.ok("Authorized");
    }

    /**
     * Sanitizes and validates reason text.
     */
    private ServiceResult<String> validateReason(String reason, boolean mandatory, String actionName) {
        String trimmed = (reason != null) ? reason.trim() : null;
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }

        if (mandatory) {
            if (trimmed == null || trimmed.length() < MIN_REASON_LENGTH) {
                return ServiceResult.failure("A meaningful reason (minimum " + MIN_REASON_LENGTH
                        + " characters) is required when " + actionName + ".");
            }
        }

        if (trimmed != null && trimmed.length() > MAX_REASON_LENGTH) {
            return ServiceResult.failure("Reason exceeds maximum permitted length of "
                    + MAX_REASON_LENGTH + " characters.");
        }

        return ServiceResult.success(trimmed);
    }

    // =========================================================================
    // SERVICE RESULT ENVELOPE
    // =========================================================================

    /**
     * Generic result envelope for service operations.
     */
    public static class ServiceResult<T> {
        private final boolean success;
        private final String message;
        private final T data;

        private ServiceResult(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static <T> ServiceResult<T> success(T data) {
            return new ServiceResult<>(true, null, data);
        }

        public static <T> ServiceResult<T> success(String message, T data) {
            return new ServiceResult<>(true, message, data);
        }

        public static <T> ServiceResult<T> ok(String message) {
            return new ServiceResult<>(true, message, null);
        }

        public static <T> ServiceResult<T> failure(String message) {
            return new ServiceResult<>(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public T getData() {
            return data;
        }
    }
}

