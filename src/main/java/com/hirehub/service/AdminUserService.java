package com.hirehub.service;

import com.hirehub.dao.AdminUserDAO;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Administrator User Management, Account Moderation, and Governance.
 *
 * <p>Enforces strict service-layer authorization:
 * <ul>
 *   <li>Only authenticated users with {@link User.Role#ADMIN} may invoke moderation APIs.</li>
 *   <li>Self-deactivation is strictly prohibited.</li>
 *   <li>Deactivating the last remaining active administrator is strictly prohibited.</li>
 *   <li>Admins cannot alter user roles via status operations.</li>
 *   <li>Every state transition generates an atomic audit log record.</li>
 * </ul>
 * </p>
 */
public class AdminUserService {

    private final AdminUserDAO dao;

    public AdminUserService() {
        this(new AdminUserDAO());
    }

    public AdminUserService(AdminUserDAO dao) {
        this.dao = dao;
    }

    // -----------------------------------------------------------------------
    // User Queries & Search
    // -----------------------------------------------------------------------

    /**
     * Lists users matching search and filter parameters with pagination.
     */
    public ServiceResult<List<User>> listUsers(String query, User.Role roleFilter, Boolean activeFilter,
                                               int limit, int offset) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }

        int safeLimit = Math.min(200, Math.max(1, limit));
        int safeOffset = Math.max(0, offset);

        try {
            List<User> users = dao.searchUsers(query, roleFilter, activeFilter, safeLimit, safeOffset);
            return ServiceResult.success(users);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving users: " + e.getMessage());
        }
    }

    /**
     * Counts users matching search and filter parameters.
     */
    public ServiceResult<Integer> countUsers(String query, User.Role roleFilter, Boolean activeFilter) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }

        try {
            int count = dao.countUsers(query, roleFilter, activeFilter);
            return ServiceResult.success(count);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error counting users: " + e.getMessage());
        }
    }

    /**
     * Retrieves a user by their unique primary key ID.
     */
    public ServiceResult<User> getUserById(int userId) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }

        if (userId <= 0) {
            return ServiceResult.failure("Invalid user ID: " + userId);
        }

        try {
            Optional<User> userOpt = dao.findById(userId);
            return userOpt.map(ServiceResult::success)
                    .orElseGet(() -> ServiceResult.failure("User not found with ID: " + userId));
        } catch (SQLException e) {
            return ServiceResult.failure("Database error finding user: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Account Moderation: Deactivate / Activate
    // -----------------------------------------------------------------------

    /**
     * Deactivates a user's account and records an audit log entry.
     *
     * <p>Enforces critical safeguards:
     * 1. Caller must be an authenticated administrator whose session ID matches {@code adminId}.
     * 2. Self-deactivation is strictly rejected.
     * 3. Target user must exist and currently be active.
     * 4. If target user is an ADMIN, system must not permit deactivating the last active administrator.
     * </p>
     *
     * @param adminId      the ID of the acting administrator
     * @param targetUserId the ID of the user to deactivate
     * @param reason       optional explanation/reason for moderation
     * @return ServiceResult indicating success or failure reason
     */
    public ServiceResult<Void> deactivateUser(int adminId, int targetUserId, String reason) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return authCheck;
        }

        if (adminId <= 0 || targetUserId <= 0) {
            return ServiceResult.failure("Invalid user IDs: adminId and targetUserId must be positive.");
        }

        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin.getId() != adminId) {
            return ServiceResult.failure("Admin identity mismatch: session user ID does not match supplied adminId.");
        }

        // Safeguard 1: Self-deactivation guard
        if (adminId == targetUserId) {
            return ServiceResult.failure("Administrators cannot deactivate their own account.");
        }

        try {
            // Safeguard 2: Target user exists
            Optional<User> targetOpt = dao.findById(targetUserId);
            if (targetOpt.isEmpty()) {
                return ServiceResult.failure("Target user not found: " + targetUserId);
            }
            User target = targetOpt.get();

            // Safeguard 3: Already inactive
            if (!target.isActive()) {
                return ServiceResult.failure("User account is already deactivated.");
            }

            // Safeguard 4: Last active admin protection
            if (target.getRole() == User.Role.ADMIN) {
                int activeAdmins = dao.countActiveAdmins();
                if (activeAdmins <= 1) {
                    return ServiceResult.failure("Cannot deactivate the last remaining active administrator.");
                }
            }

            // Atomic status update + audit log
            boolean ok = dao.updateAccountStatusWithAudit(adminId, targetUserId, false, "DEACTIVATE_USER", reason);
            if (ok) {
                return ServiceResult.ok("User account deactivated successfully.");
            } else {
                return ServiceResult.failure("Failed to deactivate user account.");
            }
        } catch (SQLException e) {
            return ServiceResult.failure("Database error deactivating user account: " + e.getMessage());
        }
    }

    /**
     * Activates a user's account and records an audit log entry.
     *
     * @param adminId      the ID of the acting administrator
     * @param targetUserId the ID of the user to activate
     * @param reason       optional explanation/reason for activation
     * @return ServiceResult indicating success or failure reason
     */
    public ServiceResult<Void> activateUser(int adminId, int targetUserId, String reason) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return authCheck;
        }

        if (adminId <= 0 || targetUserId <= 0) {
            return ServiceResult.failure("Invalid user IDs: adminId and targetUserId must be positive.");
        }

        User currentAdmin = SessionManager.getInstance().getCurrentUser();
        if (currentAdmin.getId() != adminId) {
            return ServiceResult.failure("Admin identity mismatch: session user ID does not match supplied adminId.");
        }

        try {
            Optional<User> targetOpt = dao.findById(targetUserId);
            if (targetOpt.isEmpty()) {
                return ServiceResult.failure("Target user not found: " + targetUserId);
            }
            User target = targetOpt.get();

            if (target.isActive()) {
                return ServiceResult.failure("User account is already active.");
            }

            boolean ok = dao.updateAccountStatusWithAudit(adminId, targetUserId, true, "ACTIVATE_USER", reason);
            if (ok) {
                return ServiceResult.ok("User account activated successfully.");
            } else {
                return ServiceResult.failure("Failed to activate user account.");
            }
        } catch (SQLException e) {
            return ServiceResult.failure("Database error activating user account: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Audit Logs
    // -----------------------------------------------------------------------

    /**
     * Retrieves audit log history for a specific user.
     */
    public ServiceResult<List<AdminAuditLog>> getAuditLogsForUser(int targetUserId) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }

        if (targetUserId <= 0) {
            return ServiceResult.failure("Invalid target user ID: " + targetUserId);
        }

        try {
            List<AdminAuditLog> logs = dao.getAuditLogsForUser(targetUserId);
            return ServiceResult.success(logs);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving audit logs: " + e.getMessage());
        }
    }

    /**
     * Retrieves recent audit logs across all users.
     */
    public ServiceResult<List<AdminAuditLog>> getRecentAuditLogs(int limit) {
        ServiceResult<Void> authCheck = checkAdminAuthorization();
        if (!authCheck.isSuccess()) {
            return ServiceResult.failure(authCheck.getMessage());
        }

        int safeLimit = Math.min(200, Math.max(1, limit));
        try {
            List<AdminAuditLog> logs = dao.getRecentAuditLogs(safeLimit);
            return ServiceResult.success(logs);
        } catch (SQLException e) {
            return ServiceResult.failure("Database error retrieving audit logs: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Authorization Helper
    // -----------------------------------------------------------------------

    private ServiceResult<Void> checkAdminAuthorization() {
        SessionManager session = SessionManager.getInstance();
        if (!session.hasRole(User.Role.ADMIN)) {
            return ServiceResult.failure("Unauthorized: Administrator role is required.");
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null || currentUser.getId() <= 0) {
            return ServiceResult.failure("Unauthorized: No authenticated session found.");
        }
        if (!currentUser.isActive()) {
            return ServiceResult.failure("Unauthorized: Authenticated administrator account is deactivated.");
        }
        return ServiceResult.ok(null);
    }

    // -----------------------------------------------------------------------
    // Service Result Wrapper
    // -----------------------------------------------------------------------

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

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public T getData() { return data; }
    }
}

