package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.AdminAuditEntityType;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Admin User Management, Account Moderation, and Audit Logging.
 *
 * <p>All operations are strictly parameterized to prevent SQL injection.
 * Status modifications and audit log creations are executed within atomic transactions
 * using connection pooling via {@link DatabaseConnection}.</p>
 */
public class AdminUserDAO {

    /**
     * Searches and lists users matching optional search query, role filter, and active status filter.
     *
     * @param query        search string matching name, email, or exact ID (nullable)
     * @param roleFilter   filter by specific Role, or null for all roles
     * @param activeFilter filter by is_active, or null for both active and inactive
     * @param limit        maximum rows to return (>= 1)
     * @param offset       starting offset (>= 0)
     * @return list of matching users ordered by creation date descending
     * @throws SQLException on database error
     */
    public List<User> searchUsers(String query, User.Role roleFilter, Boolean activeFilter,
                                  int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, full_name, email, password_hash, role, is_active, created_at " +
                "FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildWhereClause(sql, params, query, roleFilter, activeFilter);

        sql.append("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUserRow(rs));
                }
            }
        }
        return users;
    }

    /**
     * Counts the total number of users matching the given search and filter criteria.
     */
    public int countUsers(String query, User.Role roleFilter, Boolean activeFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildWhereClause(sql, params, query, roleFilter, activeFilter);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    /**
     * Finds a user by ID.
     */
    public Optional<User> findById(int id) throws SQLException {
        String sql = "SELECT id, full_name, email, password_hash, role, is_active, created_at FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUserRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Counts how many active users have the ADMIN role.
     * Used to prevent deactivating the last remaining active administrator.
     */
    public int countActiveAdmins() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = TRUE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Atomically updates a user's account active status and records an audit log entry
     * in a single database transaction. If either operation fails, both are rolled back.
     *
     * @param adminId      the ID of the administrator performing the action
     * @param targetUserId the ID of the user whose account is being moderated
     * @param newStatus    true to activate, false to deactivate
     * @param action       action identifier (e.g. "DEACTIVATE_USER", "ACTIVATE_USER")
     * @param reason       optional explanation/reason for moderation
     * @return true if both the status update and audit log were committed
     * @throws SQLException on database error or transaction failure
     */
    public boolean updateAccountStatusWithAudit(int adminId, int targetUserId, boolean newStatus,
                                                String action, String reason) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Update user account status
                String updateSql = "UPDATE users SET is_active = ? WHERE id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setBoolean(1, newStatus);
                    updateStmt.setInt(2, targetUserId);
                    int affected = updateStmt.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("User account update failed: target user " + targetUserId + " not found.");
                    }
                }

                // 2. Insert audit log record
                String auditSql = "INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, action, reason) " +
                                  "VALUES ('USER', ?, ?, ?, ?)";
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                    auditStmt.setInt(1, adminId);
                    auditStmt.setInt(2, targetUserId);
                    auditStmt.setString(3, action != null ? action.trim() : "MODERATE_USER");
                    auditStmt.setString(4, reason != null && !reason.isBlank() ? reason.trim() : null);
                    auditStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Retrieves audit log history for a specific target user.
     */
    public List<AdminAuditLog> getAuditLogsForUser(int targetUserId) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "WHERE a.target_user_id = ? " +
                "ORDER BY a.created_at DESC, a.id DESC";

        List<AdminAuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, targetUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapAuditLogRow(rs));
                }
            }
        }
        return logs;
    }

    /**
     * Retrieves audit log history for a specific target job.
     */
    public List<AdminAuditLog> getAuditLogsForJob(int targetJobId) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "WHERE a.target_job_id = ? " +
                "ORDER BY a.created_at DESC, a.id DESC";

        List<AdminAuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, targetJobId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapAuditLogRow(rs));
                }
            }
        }
        return logs;
    }

    /**
     * Retrieves recent audit logs across all users and entities.
     */
    public List<AdminAuditLog> getRecentAuditLogs(int limit) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "ORDER BY a.created_at DESC, a.id DESC LIMIT ?";

        List<AdminAuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapAuditLogRow(rs));
                }
            }
        }
        return logs;
    }

    // -----------------------------------------------------------------------
    // Private Helpers
    // -----------------------------------------------------------------------

    private void buildWhereClause(StringBuilder sql, List<Object> params,
                                  String query, User.Role roleFilter, Boolean activeFilter) {
        if (query != null && !query.trim().isEmpty()) {
            String trimmed = query.trim().toLowerCase();
            String likeParam = "%" + trimmed + "%";
            Integer parsedId = null;
            try {
                parsedId = Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {}

            if (parsedId != null) {
                sql.append("AND (LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR id = ?) ");
                params.add(likeParam);
                params.add(likeParam);
                params.add(parsedId);
            } else {
                sql.append("AND (LOWER(full_name) LIKE ? OR LOWER(email) LIKE ?) ");
                params.add(likeParam);
                params.add(likeParam);
            }
        }

        if (roleFilter != null) {
            sql.append("AND role = ? ");
            params.add(roleFilter.name());
        }

        if (activeFilter != null) {
            sql.append("AND is_active = ? ");
            params.add(activeFilter);
        }
    }

    private void bindParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            int idx = i + 1;
            if (p instanceof String s) {
                stmt.setString(idx, s);
            } else if (p instanceof Integer n) {
                stmt.setInt(idx, n);
            } else if (p instanceof Boolean b) {
                stmt.setBoolean(idx, b);
            } else {
                stmt.setObject(idx, p);
            }
        }
    }

    private User mapUserRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(User.Role.valueOf(rs.getString("role")));
        user.setActive(rs.getBoolean("is_active"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toLocalDateTime());
        }
        return user;
    }

    private AdminAuditLog mapAuditLogRow(ResultSet rs) throws SQLException {
        AdminAuditLog log = new AdminAuditLog();
        log.setId(rs.getInt("id"));

        String entityTypeStr = rs.getString("entity_type");
        if (entityTypeStr != null) {
            log.setEntityType(AdminAuditEntityType.fromString(entityTypeStr));
        }

        log.setAdminId(rs.getInt("admin_id"));
        log.setAdminName(rs.getString("admin_name"));
        log.setAdminEmail(rs.getString("admin_email"));

        int targetUserId = rs.getInt("target_user_id");
        if (!rs.wasNull()) {
            log.setTargetUserId(targetUserId);
        }
        log.setTargetUserName(rs.getString("target_name"));
        log.setTargetUserEmail(rs.getString("target_email"));

        int targetJobId = rs.getInt("target_job_id");
        if (!rs.wasNull()) {
            log.setTargetJobId(targetJobId);
        }

        log.setAction(rs.getString("action"));
        log.setReason(rs.getString("reason"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            log.setCreatedAt(ts.toLocalDateTime());
        }
        return log;
    }
}
