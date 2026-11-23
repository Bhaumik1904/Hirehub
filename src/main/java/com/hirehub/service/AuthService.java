package com.hirehub.service;

import com.hirehub.dao.UserDAO;
import com.hirehub.model.User;
import com.hirehub.util.PasswordUtil;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * AuthService — Business logic layer for user authentication.
 *
 * <p>Handles registration, login, validation, and session management.
 * Controllers call this service; this service calls {@link UserDAO}.</p>
 *
 * <p>This class never touches JDBC directly.</p>
 */
public class AuthService {

    /** Simple regex for email format validation */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserDAO userDAO;

    public AuthService() {
        this.userDAO = new UserDAO();
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Registers a new user (Student or Recruiter) in the system.
     *
     * <p>Admin registration is blocked — admins must be seeded via SQL.</p>
     *
     * @param fullName        the user's full name
     * @param email           the user's email address
     * @param plainPassword   the raw (unhashed) password
     * @param confirmPassword the password confirmation
     * @param role            the desired role (STUDENT or RECRUITER only)
     * @return an {@link AuthResult} indicating success or describing the failure
     */
    public AuthResult register(String fullName, String email,
                               String plainPassword, String confirmPassword,
                               User.Role role) {

        // --- Input validation ---
        if (fullName == null || fullName.trim().length() < 2) {
            return AuthResult.failure("Full name must be at least 2 characters.");
        }
        if (!isValidEmail(email)) {
            return AuthResult.failure("Please enter a valid email address.");
        }
        if (!plainPassword.equals(confirmPassword)) {
            return AuthResult.failure("Passwords do not match.");
        }
        String passwordError = PasswordUtil.validatePasswordStrength(plainPassword);
        if (passwordError != null) {
            return AuthResult.failure(passwordError);
        }
        if (role == User.Role.ADMIN) {
            return AuthResult.failure("Admin accounts cannot be created through public registration.");
        }

        // --- Database operations ---
        try {
            if (userDAO.emailExists(email.toLowerCase())) {
                return AuthResult.failure("An account with this email already exists.");
            }

            String hashedPassword = PasswordUtil.hashPassword(plainPassword);
            User newUser = new User(fullName.trim(), email.toLowerCase(), hashedPassword, role);
            int generatedId = userDAO.insertUser(newUser);
            newUser.setId(generatedId);

            return AuthResult.success(newUser);

        } catch (SQLException e) {
            System.err.println("[AuthService] Registration DB error: " + e.getMessage());
            return AuthResult.failure("Registration failed due to a server error. Please try again.");
        }
    }

    // -----------------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------------

    /**
     * Authenticates a user with their email and password.
     *
     * <p>On success, the user is stored in {@link SessionManager}.</p>
     *
     * @param email         the user's email
     * @param plainPassword the raw password entered by the user
     * @return an {@link AuthResult} with the authenticated user on success
     */
    public AuthResult login(String email, String plainPassword) {
        // --- Basic validation ---
        if (email == null || email.isBlank()) {
            return AuthResult.failure("Email address is required.");
        }
        if (plainPassword == null || plainPassword.isBlank()) {
            return AuthResult.failure("Password is required.");
        }

        // --- Database lookup + password verification ---
        try {
            Optional<User> userOpt = userDAO.findByEmail(email.toLowerCase());

            if (userOpt.isEmpty()) {
                // Use a generic message to avoid disclosing which emails are registered
                return AuthResult.failure("Invalid email or password.");
            }

            User user = userOpt.get();

            if (!PasswordUtil.verifyPassword(plainPassword, user.getPasswordHash())) {
                return AuthResult.failure("Invalid email or password.");
            }

            // Check if account has been deactivated by an administrator
            if (!user.isActive()) {
                return AuthResult.failure("Your account has been deactivated. Please contact an administrator.");
            }

            // Credentials verified — start session
            SessionManager.getInstance().login(user);
            return AuthResult.success(user);

        } catch (SQLException e) {
            System.err.println("[AuthService] Login DB error: " + e.getMessage());
            return AuthResult.failure("Login failed due to a server error. Please try again.");
        }
    }

    // -----------------------------------------------------------------------
    // Logout
    // -----------------------------------------------------------------------

    /**
     * Logs out the current user by clearing the session.
     */
    public void logout() {
        SessionManager.getInstance().logout();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    // -----------------------------------------------------------------------
    // AuthResult — inner result type for clean error propagation to controller
    // -----------------------------------------------------------------------

    /**
     * Encapsulates the result of an authentication operation.
     * Avoids throwing checked exceptions into JavaFX controllers.
     */
    public static class AuthResult {

        private final boolean success;
        private final String  errorMessage;
        private final User    user;

        private AuthResult(boolean success, String errorMessage, User user) {
            this.success      = success;
            this.errorMessage = errorMessage;
            this.user         = user;
        }

        public static AuthResult success(User user) {
            return new AuthResult(true, null, user);
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message, null);
        }

        public boolean isSuccess()      { return success; }
        public String  getErrorMessage(){ return errorMessage; }
        public User    getUser()        { return user; }
    }
}
