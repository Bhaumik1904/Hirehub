package com.hirehub.service;

import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.dao.AdminUserDAO;
import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.Resume;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ApplicationService — business logic for job applications.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Job must be ACTIVE and deadline not passed before applying.</li>
 *   <li>No duplicate applications (student + job unique).</li>
 *   <li>Recruiter can only update statuses for their own job's applications.</li>
 *   <li>Students cannot change their own application status.</li>
 *   <li>Recruiter resume access is restricted to applicants for their own jobs.</li>
 * </ul>
 */
public class ApplicationService {

    private final ApplicationDAO applicationDAO = new ApplicationDAO();
    private final JobDAO         jobDAO         = new JobDAO();
    private final ResumeDAO      resumeDAO      = new ResumeDAO();
    private final ResumeService  resumeService  = new ResumeService();
    private final AdminUserDAO   adminUserDAO   = new AdminUserDAO();

    // ── Result types ─────────────────────────────────────────────────────────

    public static class ServiceResult {
        private final boolean success;
        private final String  message;

        private ServiceResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ServiceResult ok(String msg)   { return new ServiceResult(true,  msg); }
        public static ServiceResult fail(String msg) { return new ServiceResult(false, msg); }

        public boolean isSuccess() { return success; }
        public String  getMessage(){ return message; }
    }

    public static class ResumeAccessResult {
        private final boolean success;
        private final String  message;
        private final Resume  resume;
        private final File    file;

        private ResumeAccessResult(boolean success, String message, Resume resume, File file) {
            this.success = success;
            this.message = message;
            this.resume  = resume;
            this.file    = file;
        }

        public static ResumeAccessResult ok(Resume resume, File file) {
            return new ResumeAccessResult(true, "Resume retrieved successfully.", resume, file);
        }

        public static ResumeAccessResult fail(String msg) {
            return new ResumeAccessResult(false, msg, null, null);
        }

        public boolean isSuccess() { return success; }
        public String  getMessage(){ return message; }
        public Resume  getResume() { return resume; }
        public File    getFile()   { return file; }
    }

    private boolean isAuthorizedStudent(int studentId) {
        if (studentId <= 0) return false;
        User current = SessionManager.getInstance().getCurrentUser();
        return current != null
                && current.getRole() == User.Role.STUDENT
                && current.getId() == studentId;
    }

    /**
     * Verifies the student account is still active in the live database.
     * Guards against deactivated accounts that still have a valid session object.
     */
    private boolean isStudentAccountActive(int studentId) {
        try {
            java.util.Optional<User> dbUser = adminUserDAO.findById(studentId);
            return dbUser.isPresent()
                    && dbUser.get().isActive()
                    && dbUser.get().getRole() == User.Role.STUDENT;
        } catch (java.sql.SQLException e) {
            System.err.println("[ApplicationService] DB error verifying student account: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // Student operations
    // =========================================================================

    /**
     * Submits a job application from the given student, automatically snapshotting the student's active resume.
     * Requires an authenticated STUDENT session matching {@code studentId}.
     *
     * @param studentId   the authenticated student's user id
     * @param jobId       the job to apply to
     * @param coverLetter optional cover letter text
     */
    public ServiceResult apply(int studentId, int jobId, String coverLetter) {
        return apply(studentId, jobId, coverLetter, null);
    }

    /**
     * Submits a job application from the given student, snapshotting a specified or active resume.
     * Requires an authenticated STUDENT session matching {@code studentId}.
     *
     * @param studentId         the authenticated student's user id
     * @param jobId             the job to apply to
     * @param coverLetter       optional cover letter text
     * @param requestedResumeId optional specific resume id to snapshot (must belong to student)
     */
    public ServiceResult apply(int studentId, int jobId, String coverLetter, Integer requestedResumeId) {
        if (studentId <= 0 || jobId <= 0) {
            return ServiceResult.fail("Invalid student or job ID.");
        }
        if (!isAuthorizedStudent(studentId)) {
            return ServiceResult.fail("Unauthorized: Session does not match student ID " + studentId);
        }
        // Live database account check — guard against deactivated students with stale sessions
        if (!isStudentAccountActive(studentId)) {
            return ServiceResult.fail("Unauthorized: Student account is inactive or no longer exists.");
        }
        try {
            // 1. Revalidate job against full approved-and-verified visibility rules.
            //    Uses findApprovedActiveById:
            //    ACTIVE + APPROVED + recruiter active + role RECRUITER + profile VERIFIED + deadline valid.
            Optional<Job> approvedOpt = jobDAO.findApprovedActiveById(jobId);
            if (approvedOpt.isEmpty()) {
                return ServiceResult.fail("This job listing is not available for applications.");
            }

            Job job = approvedOpt.get();

            // 2. Job must be ACTIVE (enforced by searchApprovedActive, but guard explicitly)
            if (job.getStatus() != Job.Status.ACTIVE)
                return ServiceResult.fail("This job listing is no longer accepting applications.");

            // 3. Deadline must not have passed (also enforced by searchApprovedActive)
            if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now()))
                return ServiceResult.fail("The application deadline for this job has passed.");

            // 4. No duplicate application
            if (applicationDAO.exists(jobId, studentId))
                return ServiceResult.fail("You have already applied to this job.");

            // 5. Determine resume snapshot
            Integer snapshotResumeId = null;
            if (requestedResumeId != null && requestedResumeId > 0) {
                // Verify requested resume belongs to this student
                Optional<Resume> resOpt = resumeDAO.findByIdForStudent(requestedResumeId, studentId);
                if (resOpt.isEmpty()) {
                    return ServiceResult.fail("Specified resume does not belong to your account.");
                }
                snapshotResumeId = requestedResumeId;
            } else {
                // Auto-snapshot currently active resume
                Optional<Resume> activeOpt = resumeDAO.findActiveByStudentId(studentId);
                if (activeOpt.isPresent()) {
                    snapshotResumeId = activeOpt.get().getId();
                }
            }

            // 6. Insert application atomically with snapshotted resume_id
            applicationDAO.insert(jobId, studentId, coverLetter, snapshotResumeId);
            return ServiceResult.ok("Application submitted successfully!");

        } catch (SQLException e) {
            System.err.println("[ApplicationService] apply error: " + e.getMessage());
            return ServiceResult.fail("Could not submit application. Please try again.");
        }
    }

    /** Returns true if the student has already applied to this job. Requires authenticated student session. */
    public boolean hasApplied(int studentId, int jobId) {
        if (studentId <= 0 || jobId <= 0 || !isAuthorizedStudent(studentId)) {
            return false;
        }
        try {
            return applicationDAO.exists(jobId, studentId);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Returns all applications for a student. Requires authenticated student session. */
    public List<Application> getStudentApplications(int studentId) {
        if (studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return Collections.emptyList();
        }
        try {
            return applicationDAO.findByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getStudentApplications error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Admin-only retrieval of all applications for any student. */
    public List<Application> getStudentApplicationsForAdmin(int studentId) {
        if (studentId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return Collections.emptyList();
        }
        try {
            return applicationDAO.findByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getStudentApplicationsForAdmin error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Recruiter operations
    // =========================================================================

    /**
     * Returns all applications for a job, verifying that the recruiter owns the job.
     *
     * @param recruiterId the authenticated recruiter's user id
     * @param jobId       the job to fetch applicants for
     */
    public ServiceResult getApplicantsForJob(int recruiterId, int jobId) {
        if (recruiterId <= 0 || jobId <= 0) {
            return ServiceResult.fail("Job not found or you are not authorised to view applicants for this job.");
        }
        try {
            Optional<Job> jobOpt = jobDAO.findByIdAndRecruiter(jobId, recruiterId);
            if (jobOpt.isEmpty())
                return ServiceResult.fail("Job not found or you are not authorised to view applicants for this job.");
            return ServiceResult.ok("authorised");
        } catch (SQLException e) {
            return ServiceResult.fail("Database error: " + e.getMessage());
        }
    }

    /** Returns all applications for a job owned by {@code recruiterId}. */
    public List<Application> getApplicationsByJob(int recruiterId, int jobId) {
        return getApplicationsForRecruiterJob(jobId, recruiterId);
    }

    /**
     * Recruiter-scoped applicant listing enforced atomically in SQL via
     * {@code INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ?}.
     */
    public List<Application> getApplicationsForRecruiterJob(int jobId, int recruiterId) {
        if (jobId <= 0 || recruiterId <= 0) {
            return Collections.emptyList();
        }
        try {
            return applicationDAO.findByJobIdAndRecruiter(jobId, recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getApplicationsForRecruiterJob error: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Fetches a single application detail only if its job belongs to {@code recruiterId}.
     */
    public Optional<Application> getApplicationByIdForRecruiter(int applicationId, int recruiterId) {
        if (applicationId <= 0 || recruiterId <= 0) {
            return Optional.empty();
        }
        try {
            return applicationDAO.findByIdAndRecruiter(applicationId, recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getApplicationByIdForRecruiter error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Legacy count wrapper restricted fail-closed to the authenticated recruiter session.
     * Prefer {@link #countApplicationsForJob(int, int)} or {@link #countApplicationsByJobForRecruiter(int)}.
     */
    @Deprecated
    public int countApplicationsForJob(int jobId) {
        if (jobId <= 0 || !SessionManager.getInstance().hasRole(User.Role.RECRUITER)) {
            return 0;
        }
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null || current.getId() <= 0) {
            return 0;
        }
        return countApplicationsForJob(jobId, current.getId());
    }

    /**
     * Returns the application count for {@code jobId} only when the job belongs to {@code recruiterId}.
     */
    public int countApplicationsForJob(int jobId, int recruiterId) {
        if (jobId <= 0 || recruiterId <= 0) {
            return 0;
        }
        try {
            return applicationDAO.countByJobAndRecruiter(jobId, recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] countApplicationsForJob error: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns bulk application counts keyed by job id across all jobs belonging to {@code recruiterId}.
     */
    public Map<Integer, Integer> countApplicationsByJobForRecruiter(int recruiterId) {
        if (recruiterId <= 0) {
            return Collections.emptyMap();
        }
        try {
            return applicationDAO.countByJobsForRecruiter(recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] countApplicationsByJobForRecruiter error: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** Total applications across all of a recruiter's jobs (dashboard stat). */
    public int countTotalApplicationsForRecruiter(int recruiterId) {
        if (recruiterId <= 0) {
            return 0;
        }
        try {
            return applicationDAO.countTotalByRecruiter(recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] countTotalApplicationsForRecruiter error: " + e.getMessage());
            return 0;
        }
    }

    /** Total SHORTLISTED applications across all of a recruiter's jobs (dashboard stat). */
    public int countShortlistedForRecruiter(int recruiterId) {
        if (recruiterId <= 0) {
            return 0;
        }
        try {
            return applicationDAO.countShortlistedByRecruiter(recruiterId);
        } catch (SQLException e) {
            System.err.println("[ApplicationService] countShortlistedForRecruiter error: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Updates an application's status. Enforces ownership atomically via recruiter-scoped SQL.
     *
     * @param recruiterId     the authenticated recruiter's user id
     * @param applicationId   the application to update
     * @param newStatus       the new status to set
     */
    public ServiceResult updateApplicationStatus(int recruiterId, int applicationId,
                                                  Application.Status newStatus) {
        return updateApplicationStatusForRecruiter(applicationId, recruiterId, newStatus);
    }

    /**
     * Atomically updates an application status only if its job belongs to {@code recruiterId}.
     */
    public ServiceResult updateApplicationStatusForRecruiter(int applicationId, int recruiterId,
                                                             Application.Status newStatus) {
        if (recruiterId <= 0 || applicationId <= 0) {
            return ServiceResult.fail("Application not found or you are not authorised to update it.");
        }
        if (newStatus == null) {
            return ServiceResult.fail("Invalid application status.");
        }
        try {
            int updated = applicationDAO.updateStatusForRecruiter(applicationId, recruiterId, newStatus);
            if (updated <= 0) {
                return ServiceResult.fail("Application not found or you are not authorised to update it.");
            }
            return ServiceResult.ok("Application status updated to " + newStatus.label() + ".");
        } catch (SQLException e) {
            System.err.println("[ApplicationService] updateApplicationStatusForRecruiter error: " + e.getMessage());
            return ServiceResult.fail("Could not update status. Please try again.");
        }
    }

    // =========================================================================
    // Resume Snapshot Access (Recruiter & Admin)
    // =========================================================================

    /**
     * Retrieves the resume snapshot associated with an application for the authenticated recruiter.
     * Enforces:
     * 1. Authenticated session with RECRUITER role.
     * 2. Application exists and belongs to a job owned by this recruiter.
     * 3. Resume was attached to the application (legacy applications without resume return clear message).
     * 4. Resume belongs to the applicant.
     * 5. Physical file passes strict path resolution within data/resumes (preventing path traversal).
     */
    public ResumeAccessResult getResumeForApplication(int applicationId) {
        if (applicationId <= 0) {
            return ResumeAccessResult.fail("Invalid application ID.");
        }
        if (!SessionManager.getInstance().hasRole(User.Role.RECRUITER)) {
            return ResumeAccessResult.fail("Unauthorized: Recruiter session required.");
        }
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null || current.getId() <= 0) {
            return ResumeAccessResult.fail("Unauthorized: Valid session required.");
        }
        int recruiterId = current.getId();

        try {
            // Check application ownership by recruiter
            Optional<Application> appOpt = applicationDAO.findByIdAndRecruiter(applicationId, recruiterId);
            if (appOpt.isEmpty()) {
                return ResumeAccessResult.fail("Application not found or you are not authorized to view its resume.");
            }

            Application app = appOpt.get();
            if (app.getResumeId() == null || app.getResumeId() <= 0) {
                return ResumeAccessResult.fail("Resume not available for this application.");
            }

            // Retrieve resume atomically joined with application and recruiter's job
            Optional<Resume> resumeOpt = resumeDAO.findResumeForApplicationAndRecruiter(applicationId, recruiterId);
            if (resumeOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume not found or not associated with this application.");
            }

            Resume resume = resumeOpt.get();
            Optional<File> fileOpt = resumeService.resolveAndValidateResumeFile(resume.getStoredPath());
            if (fileOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume file could not be found or has invalid storage path.");
            }

            return ResumeAccessResult.ok(resume, fileOpt.get());
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getResumeForApplication error: " + e.getMessage());
            return ResumeAccessResult.fail("Database error retrieving resume: " + e.getMessage());
        }
    }

    /**
     * Admin-only retrieval of the resume snapshot associated with an application.
     */
    public ResumeAccessResult getResumeForApplicationForAdmin(int applicationId) {
        if (applicationId <= 0) {
            return ResumeAccessResult.fail("Invalid application ID.");
        }
        if (!SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return ResumeAccessResult.fail("Unauthorized: Admin session required.");
        }

        try {
            Optional<Application> appOpt = applicationDAO.findById(applicationId);
            if (appOpt.isEmpty()) {
                return ResumeAccessResult.fail("Application not found.");
            }

            Application app = appOpt.get();
            if (app.getResumeId() == null || app.getResumeId() <= 0) {
                return ResumeAccessResult.fail("Resume not available for this application.");
            }

            Optional<Resume> resumeOpt = resumeDAO.findResumeForApplicationForAdmin(applicationId);
            if (resumeOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume not found or not associated with this application.");
            }

            Resume resume = resumeOpt.get();
            Optional<File> fileOpt = resumeService.resolveAndValidateResumeFile(resume.getStoredPath());
            if (fileOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume file could not be found or has invalid storage path.");
            }

            return ResumeAccessResult.ok(resume, fileOpt.get());
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getResumeForApplicationForAdmin error: " + e.getMessage());
            return ResumeAccessResult.fail("Database error retrieving resume: " + e.getMessage());
        }
    }

    /**
     * Student retrieval of the resume snapshot associated with their own application.
     */
    public ResumeAccessResult getResumeForApplicationForStudent(int applicationId, int studentId) {
        if (applicationId <= 0 || studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return ResumeAccessResult.fail("Unauthorized or invalid application ID.");
        }
        try {
            Optional<Application> appOpt = applicationDAO.findById(applicationId);
            if (appOpt.isEmpty() || appOpt.get().getStudentId() != studentId) {
                return ResumeAccessResult.fail("Application not found or does not belong to you.");
            }
            Application app = appOpt.get();
            if (app.getResumeId() == null || app.getResumeId() <= 0) {
                return ResumeAccessResult.fail("Resume not available for this application.");
            }
            Optional<Resume> resumeOpt = resumeDAO.findByIdForStudent(app.getResumeId(), studentId);
            if (resumeOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume not found.");
            }
            Resume resume = resumeOpt.get();
            Optional<File> fileOpt = resumeService.resolveAndValidateResumeFile(resume.getStoredPath());
            if (fileOpt.isEmpty()) {
                return ResumeAccessResult.fail("Resume file could not be found.");
            }
            return ResumeAccessResult.ok(resume, fileOpt.get());
        } catch (SQLException e) {
            System.err.println("[ApplicationService] getResumeForApplicationForStudent error: " + e.getMessage());
            return ResumeAccessResult.fail("Database error retrieving resume: " + e.getMessage());
        }
    }
}
