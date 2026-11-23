package com.hirehub.service;

import com.hirehub.dao.StudentDAO;
import com.hirehub.model.StudentProfile;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.Optional;

/**
 * StudentService — business logic for student profile management.
 * Controllers call this; this calls StudentDAO. No JDBC in controllers.
 */
public class StudentService {

    private final StudentDAO studentDAO = new StudentDAO();

    // -----------------------------------------------------------------------
    // Result type (mirrors AuthService pattern)
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

    private boolean isAuthorizedStudent(int userId) {
        if (userId <= 0) return false;
        User current = SessionManager.getInstance().getCurrentUser();
        return current != null
                && current.getRole() == User.Role.STUDENT
                && current.getId() == userId;
    }

    // -----------------------------------------------------------------------
    // Profile retrieval
    // -----------------------------------------------------------------------

    /**
     * Loads the profile for the given userId.
     * Requires an authenticated STUDENT session matching {@code userId}.
     * Returns an empty Optional if unauthorized or if the student hasn't created a profile yet.
     */
    public Optional<StudentProfile> getProfile(int userId) {
        if (userId <= 0 || !isAuthorizedStudent(userId)) {
            return Optional.empty();
        }
        try {
            return studentDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println("[StudentService] getProfile error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Explicit Admin-only profile lookup.
     */
    public Optional<StudentProfile> getProfileForAdmin(int userId) {
        if (userId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return Optional.empty();
        }
        try {
            return studentDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println("[StudentService] getProfileForAdmin error: " + e.getMessage());
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // Save / update profile
    // -----------------------------------------------------------------------

    /**
     * Validates and persists a student profile.
     * Requires an authenticated STUDENT session matching {@code userId}.
     * Performs insert-or-update (upsert) based on whether a profile already exists.
     *
     * @param userId         the authenticated student's user id (from SessionManager)
     * @param phone          phone number (optional)
     * @param college        college / university name
     * @param degree         degree name (B.Tech, BCA, etc.)
     * @param graduationYear expected graduation year
     * @param skills         comma-separated skill list
     * @param resumePath     file path or URL to resume (optional)
     */
    public ServiceResult saveProfile(int userId, String phone, String college,
                                     String degree, int graduationYear,
                                     String skills, String resumePath) {
        if (userId <= 0) {
            return ServiceResult.fail("Invalid student user ID.");
        }
        if (!isAuthorizedStudent(userId)) {
            return ServiceResult.fail("Unauthorized: Session does not match student ID " + userId);
        }
        // Validation
        if (college == null || college.isBlank())
            return ServiceResult.fail("College / university name is required.");
        if (degree == null || degree.isBlank())
            return ServiceResult.fail("Degree is required.");
        if (graduationYear < 2000 || graduationYear > 2060)
            return ServiceResult.fail("Please enter a valid graduation year (2000–2060).");

        StudentProfile p = new StudentProfile(userId, phone, college, degree,
                                              graduationYear, skills, resumePath);
        try {
            studentDAO.saveOrUpdate(p);
            return ServiceResult.ok("Profile saved successfully.");
        } catch (SQLException e) {
            System.err.println("[StudentService] saveProfile error: " + e.getMessage());
            return ServiceResult.fail("Could not save profile. Please try again.");
        }
    }

    /**
     * Explicit Admin-only profile modification.
     */
    public ServiceResult saveProfileForAdmin(int userId, String phone, String college,
                                            String degree, int graduationYear,
                                            String skills, String resumePath) {
        if (userId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return ServiceResult.fail("Unauthorized: Admin role required.");
        }
        if (college == null || college.isBlank())
            return ServiceResult.fail("College / university name is required.");
        if (degree == null || degree.isBlank())
            return ServiceResult.fail("Degree is required.");
        if (graduationYear < 2000 || graduationYear > 2060)
            return ServiceResult.fail("Please enter a valid graduation year (2000–2060).");

        StudentProfile p = new StudentProfile(userId, phone, college, degree,
                                              graduationYear, skills, resumePath);
        try {
            studentDAO.saveOrUpdate(p);
            return ServiceResult.ok("Profile saved successfully by Admin.");
        } catch (SQLException e) {
            System.err.println("[StudentService] saveProfileForAdmin error: " + e.getMessage());
            return ServiceResult.fail("Could not save profile. Please try again.");
        }
    }
}
