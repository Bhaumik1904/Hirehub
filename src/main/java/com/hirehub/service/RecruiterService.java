package com.hirehub.service;

import com.hirehub.dao.AdminUserDAO;
import com.hirehub.dao.RecruiterDAO;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * RecruiterService — business logic for recruiter company profile management.
 * Controllers call this; this calls RecruiterDAO. No direct JDBC in controllers.
 * Enforces session-level authorization and role checks for recruiter operations.
 *
 * <p>Phase 6 Step 4.2C.2 Hardening:
 * - Live database active status checks (users.is_active = TRUE) for stale-session prevention.
 * - Profile updates strictly preserve moderation-controlled fields (verification_status,
 *   verification_reason, verified_at, verified_by).</p>
 */
public class RecruiterService {

    private final RecruiterDAO recruiterDAO;
    private final AdminUserDAO adminUserDAO;

    public RecruiterService() {
        this(new RecruiterDAO(), new AdminUserDAO());
    }

    public RecruiterService(RecruiterDAO recruiterDAO) {
        this(recruiterDAO, new AdminUserDAO());
    }

    public RecruiterService(RecruiterDAO recruiterDAO, AdminUserDAO adminUserDAO) {
        this.recruiterDAO = Objects.requireNonNullElseGet(recruiterDAO, RecruiterDAO::new);
        this.adminUserDAO = Objects.requireNonNullElseGet(adminUserDAO, AdminUserDAO::new);
    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    public static class ServiceResult {
        private final boolean success;
        private final String  message;

        private ServiceResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ServiceResult ok(String message)   { return new ServiceResult(true,  message); }
        public static ServiceResult fail(String message) { return new ServiceResult(false, message); }

        public boolean isSuccess() { return success; }
        public String  getMessage(){ return message; }
    }

    // -----------------------------------------------------------------------
    // Authorization helpers
    // -----------------------------------------------------------------------

    private boolean isAuthorizedRecruiter(int userId) {
        if (userId <= 0) return false;
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null
                || current.getRole() != User.Role.RECRUITER
                || current.getId() != userId
                || !current.isActive()) {
            return false;
        }

        // Live database verification to prevent stale-session authorization bypasses
        try {
            Optional<User> dbUserOpt = adminUserDAO.findById(userId);
            if (dbUserOpt.isEmpty()) {
                return false;
            }
            User dbUser = dbUserOpt.get();
            if (dbUser.getRole() != User.Role.RECRUITER || !dbUser.isActive()) {
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[RecruiterService] DB error in isAuthorizedRecruiter: " + e.getMessage());
            return false;
        }

        return true;
    }

    private boolean isAuthorizedAdmin() {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null
                || current.getRole() != User.Role.ADMIN
                || !current.isActive()
                || current.getId() <= 0) {
            return false;
        }

        // Live database verification for admin session
        try {
            Optional<User> dbUserOpt = adminUserDAO.findById(current.getId());
            if (dbUserOpt.isEmpty()) {
                return false;
            }
            User dbUser = dbUserOpt.get();
            return dbUser.getRole() == User.Role.ADMIN && dbUser.isActive();
        } catch (SQLException e) {
            System.err.println("[RecruiterService] DB error in isAuthorizedAdmin: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Profile retrieval
    // -----------------------------------------------------------------------

    /**
     * Loads the profile for the given recruiter userId.
     * Requires an authenticated RECRUITER session matching {@code userId}.
     * Returns an empty Optional if unauthorized, if userId is invalid, or if the recruiter hasn't created a profile yet.
     */
    public Optional<RecruiterProfile> getProfile(int userId) {
        if (userId <= 0 || !isAuthorizedRecruiter(userId)) {
            return Optional.empty();
        }
        try {
            return recruiterDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println("[RecruiterService] getProfile error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Explicit Admin-only profile lookup.
     * Requires an authenticated ADMIN session.
     */
    public Optional<RecruiterProfile> getProfileForAdmin(int userId) {
        if (userId <= 0 || !isAuthorizedAdmin()) {
            return Optional.empty();
        }
        try {
            return recruiterDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println("[RecruiterService] getProfileForAdmin error: " + e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // Save / update
    // -----------------------------------------------------------------------

    /**
     * Validates and persists a recruiter's company profile.
     * Requires an authenticated RECRUITER session matching {@code userId}.
     * Performs insert-or-update (upsert) based on whether a profile already exists.
     * Moderation-controlled fields (verification_status, etc.) cannot be overwritten.
     *
     * @param userId             the authenticated recruiter's user id (from SessionManager)
     * @param companyName        company name (required)
     * @param companyWebsite     company website (optional)
     * @param companyDescription company overview / description (optional)
     * @param industry           industry classification (optional)
     */
    public ServiceResult saveProfile(int userId, String companyName, String companyWebsite,
                                     String companyDescription, String industry) {
        if (userId <= 0) {
            return ServiceResult.fail("Invalid recruiter user ID.");
        }
        if (!isAuthorizedRecruiter(userId)) {
            return ServiceResult.fail("Unauthorized: Session does not match recruiter ID " + userId);
        }
        if (companyName == null || companyName.isBlank())
            return ServiceResult.fail("Company name is required.");

        RecruiterProfile p = new RecruiterProfile(userId, companyName.trim(), companyWebsite,
                                                  companyDescription, industry);
        try {
            recruiterDAO.saveOrUpdate(p);
            return ServiceResult.ok("Company profile saved successfully.");
        } catch (SQLException e) {
            System.err.println("[RecruiterService] saveProfile error: " + e.getMessage());
            return ServiceResult.fail("Could not save profile. Please try again.");
        }
    }

    /**
     * Explicit Admin-only profile modification.
     * Requires an authenticated ADMIN session.
     */
    public ServiceResult saveProfileForAdmin(int userId, String companyName, String companyWebsite,
                                            String companyDescription, String industry) {
        if (userId <= 0) {
            return ServiceResult.fail("Invalid recruiter user ID.");
        }
        if (!isAuthorizedAdmin()) {
            return ServiceResult.fail("Unauthorized: Admin role required.");
        }
        if (companyName == null || companyName.isBlank())
            return ServiceResult.fail("Company name is required.");

        RecruiterProfile p = new RecruiterProfile(userId, companyName.trim(), companyWebsite,
                                                  companyDescription, industry);
        try {
            recruiterDAO.saveOrUpdate(p);
            return ServiceResult.ok("Company profile saved successfully by Admin.");
        } catch (SQLException e) {
            System.err.println("[RecruiterService] saveProfileForAdmin error: " + e.getMessage());
            return ServiceResult.fail("Could not save profile. Please try again.");
        }
    }

    // -----------------------------------------------------------------------
    // Verification Submission Flow (Step 4.2D)
    // -----------------------------------------------------------------------

    /**
     * Submits or resubmits a recruiter's company profile for verification.
     * Transitions status from UNVERIFIED or REJECTED to PENDING.
     * Rejects submission if already PENDING, already VERIFIED, or SUSPENDED.
     * Clears previous verification_reason, verified_at, and verified_by on successful submission.
     *
     * @param userId             the authenticated recruiter's user id (from SessionManager)
     * @param companyName        company name (required)
     * @param companyWebsite     company website (optional)
     * @param companyDescription company description (optional)
     * @param industry           industry classification (optional)
     */
    public ServiceResult submitForVerification(int userId, String companyName, String companyWebsite,
                                               String companyDescription, String industry) {
        if (userId <= 0) {
            return ServiceResult.fail("Invalid recruiter user ID.");
        }
        if (!isAuthorizedRecruiter(userId)) {
            return ServiceResult.fail("Unauthorized: Session does not match recruiter ID " + userId);
        }
        if (companyName == null || companyName.isBlank()) {
            return ServiceResult.fail("Company name is required.");
        }

        try {
            Optional<RecruiterProfile> existingOpt = recruiterDAO.findByUserId(userId);
            if (existingOpt.isEmpty()) {
                // New profile onboarding: insert directly with status PENDING
                RecruiterProfile p = new RecruiterProfile(userId, companyName.trim(), companyWebsite,
                        companyDescription, industry);
                p.setVerificationStatus(RecruiterVerificationStatus.PENDING);
                recruiterDAO.insertWithPendingStatus(p);
                return ServiceResult.ok("Company profile submitted for verification successfully. Awaiting Admin review.");
            }

            RecruiterProfile existing = existingOpt.get();
            RecruiterVerificationStatus currentStatus = existing.getVerificationStatus();
            if (currentStatus == RecruiterVerificationStatus.VERIFIED) {
                return ServiceResult.fail("Your recruiter profile is already verified.");
            }
            if (currentStatus == RecruiterVerificationStatus.PENDING) {
                return ServiceResult.fail("Your recruiter profile is already pending verification.");
            }
            if (currentStatus == RecruiterVerificationStatus.SUSPENDED) {
                return ServiceResult.fail("Your recruiter account is suspended. Profile cannot be resubmitted for verification. Please contact an administrator.");
            }

            // UNVERIFIED or REJECTED:
            boolean updated = recruiterDAO.submitForVerification(
                    userId, companyName.trim(), companyWebsite, companyDescription, industry);
            if (!updated) {
                return ServiceResult.fail("Unable to submit for verification. Current status does not permit submission.");
            }
            return ServiceResult.ok("Company profile submitted for verification successfully. Awaiting Admin review.");
        } catch (SQLException e) {
            System.err.println("[RecruiterService] submitForVerification error: " + e.getMessage());
            return ServiceResult.fail("Could not submit profile for verification. Please try again.");
        }
    }
}
