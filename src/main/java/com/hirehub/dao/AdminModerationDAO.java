package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.AdminAuditEntityType;
import com.hirehub.model.AdminAuditLog;
import com.hirehub.model.AdminJobModerationKPI;
import com.hirehub.model.AdminRecruiterVerificationKPI;
import com.hirehub.model.JobModerationStatus;
import com.hirehub.model.RecruiterVerificationStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AdminModerationDAO — atomic multi-table persistence for administrator moderation
 * of recruiter accounts and job postings with synchronized audit logging.
 *
 * <p>All operations are executed within atomic database transactions to guarantee
 * that status mutations and audit records are committed together or rolled back.</p>
 */
public class AdminModerationDAO {

    /**
     * Atomically updates a recruiter profile's verification status and writes an audit log entry.
     *
     * @param adminId         administrator user ID performing the review
     * @param recruiterUserId target recruiter user ID
     * @param status          target verification status
     * @param reason          optional or mandatory explanation for the decision
     * @return true if updated and committed; false if recruiter not found
     * @throws SQLException on database error
     */
    public boolean updateRecruiterVerificationWithAudit(int adminId, int recruiterUserId,
                                                        RecruiterVerificationStatus status,
                                                        String reason) throws SQLException {
        if (adminId <= 0 || recruiterUserId <= 0 || status == null) {
            return false;
        }

        String updateSql =
                "UPDATE recruiter_profiles " +
                "SET verification_status = ?, verification_reason = ?, " +
                "    verified_at = CASE WHEN ? IN ('VERIFIED', 'REJECTED', 'SUSPENDED') THEN NOW() ELSE NULL END, " +
                "    verified_by = CASE WHEN ? IN ('VERIFIED', 'REJECTED', 'SUSPENDED') THEN ? ELSE NULL END " +
                "WHERE user_id = ?";

        String auditSql =
                "INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, action, reason, created_at) " +
                "VALUES ('RECRUITER_PROFILE', ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Update recruiter profile
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, status.name());
                    setNullableString(updateStmt, 2, reason);
                    updateStmt.setString(3, status.name());
                    updateStmt.setString(4, status.name());
                    updateStmt.setInt(5, adminId);
                    updateStmt.setInt(6, recruiterUserId);

                    int rows = updateStmt.executeUpdate();
                    if (rows == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // 2. Insert audit log
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                    auditStmt.setInt(1, adminId);
                    auditStmt.setInt(2, recruiterUserId);
                    auditStmt.setString(3, "RECRUITER_" + status.name());
                    setNullableString(auditStmt, 4, reason);
                    auditStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Atomically updates a job posting's moderation status and writes an audit log entry.
     * Automatically retrieves and attaches the owning recruiter's user ID to the audit record.
     *
     * @param adminId administrator user ID performing the moderation
     * @param jobId   target job posting ID
     * @param status  target moderation status
     * @param reason  optional or mandatory moderation explanation
     * @return true if updated and committed; false if job not found
     * @throws SQLException on database error
     */
    public boolean updateJobModerationWithAudit(int adminId, int jobId,
                                                JobModerationStatus status,
                                                String reason) throws SQLException {
        if (adminId <= 0 || jobId <= 0 || status == null) {
            return false;
        }

        String selectRecruiterSql = "SELECT recruiter_id FROM jobs WHERE id = ?";

        String updateSql =
                "UPDATE jobs " +
                "SET moderation_status = ?, moderation_reason = ?, " +
                "    moderated_at = CASE WHEN ? IN ('APPROVED', 'REJECTED', 'SUSPENDED') THEN NOW() ELSE NULL END, " +
                "    moderated_by = CASE WHEN ? IN ('APPROVED', 'REJECTED', 'SUSPENDED') THEN ? ELSE NULL END " +
                "WHERE id = ?";

        String auditSql =
                "INSERT INTO admin_audit_logs (entity_type, admin_id, target_user_id, target_job_id, action, reason, created_at) " +
                "VALUES ('JOB', ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // 1. Retrieve recruiter ID
                Integer recruiterId = null;
                try (PreparedStatement selectStmt = conn.prepareStatement(selectRecruiterSql)) {
                    selectStmt.setInt(1, jobId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            recruiterId = rs.getInt("recruiter_id");
                        } else {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // 2. Update job moderation status
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, status.name());
                    setNullableString(updateStmt, 2, reason);
                    updateStmt.setString(3, status.name());
                    updateStmt.setString(4, status.name());
                    updateStmt.setInt(5, adminId);
                    updateStmt.setInt(6, jobId);

                    int rows = updateStmt.executeUpdate();
                    if (rows == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // 3. Insert audit log
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql)) {
                    auditStmt.setInt(1, adminId);
                    if (recruiterId != null && recruiterId > 0) {
                        auditStmt.setInt(2, recruiterId);
                    } else {
                        auditStmt.setNull(2, Types.INTEGER);
                    }
                    auditStmt.setInt(3, jobId);
                    auditStmt.setString(4, "JOB_" + status.name());
                    setNullableString(auditStmt, 5, reason);
                    auditStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignore) {}
            }
        }
    }

    /**
     * Retrieves audit logs for recruiter profile reviews.
     */
    public List<AdminAuditLog> getAuditLogsForRecruiter(int recruiterUserId) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "WHERE a.target_user_id = ? AND a.entity_type = 'RECRUITER_PROFILE' " +
                "ORDER BY a.created_at DESC, a.id DESC";

        List<AdminAuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, recruiterUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapAuditLogRow(rs));
                }
            }
        }
        return logs;
    }

    /**
     * Retrieves audit logs for a specific job posting.
     */
    public List<AdminAuditLog> getAuditLogsForJob(int jobId) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "WHERE a.target_job_id = ? AND a.entity_type = 'JOB' " +
                "ORDER BY a.created_at DESC, a.id DESC";

        List<AdminAuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapAuditLogRow(rs));
                }
            }
        }
        return logs;
    }

    /**
     * Retrieves recent moderation audit logs (recruiter verification & job moderation).
     */
    public List<AdminAuditLog> getRecentModerationLogs(int limit) throws SQLException {
        String sql =
                "SELECT a.id, a.entity_type, a.admin_id, u_adm.full_name AS admin_name, u_adm.email AS admin_email, " +
                "       a.target_user_id, u_tgt.full_name AS target_name, u_tgt.email AS target_email, " +
                "       a.target_job_id, a.action, a.reason, a.created_at " +
                "FROM admin_audit_logs a " +
                "LEFT JOIN users u_adm ON a.admin_id = u_adm.id " +
                "LEFT JOIN users u_tgt ON a.target_user_id = u_tgt.id " +
                "WHERE a.entity_type IN ('RECRUITER_PROFILE', 'JOB') " +
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

    /**
     * Retrieves live aggregate verification counts for all registered recruiters.
     */
    public AdminRecruiterVerificationKPI getRecruiterVerificationKPI() throws SQLException {
        String sql =
                "SELECT " +
                "    COUNT(u.id) AS total_recruiters, " +
                "    COALESCE(SUM(CASE WHEN rp.verification_status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_count, " +
                "    COALESCE(SUM(CASE WHEN rp.verification_status = 'VERIFIED' THEN 1 ELSE 0 END), 0) AS verified_count, " +
                "    COALESCE(SUM(CASE WHEN rp.verification_status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_count, " +
                "    COALESCE(SUM(CASE WHEN rp.verification_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_count, " +
                "    COALESCE(SUM(CASE WHEN rp.verification_status = 'UNVERIFIED' OR rp.verification_status IS NULL THEN 1 ELSE 0 END), 0) AS unverified_count " +
                "FROM users u " +
                "LEFT JOIN recruiter_profiles rp ON u.id = rp.user_id " +
                "WHERE u.role = 'RECRUITER'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new AdminRecruiterVerificationKPI(
                        rs.getInt("total_recruiters"),
                        rs.getInt("pending_count"),
                        rs.getInt("verified_count"),
                        rs.getInt("rejected_count"),
                        rs.getInt("suspended_count"),
                        rs.getInt("unverified_count")
                );
            }
            return new AdminRecruiterVerificationKPI(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Retrieves live aggregate moderation and lifecycle counts for all job postings.
     */
    public AdminJobModerationKPI getJobModerationKPI() throws SQLException {
        String sql =
                "SELECT " +
                "    COUNT(*) AS total_jobs, " +
                "    COALESCE(SUM(CASE WHEN moderation_status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_count, " +
                "    COALESCE(SUM(CASE WHEN moderation_status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved_count, " +
                "    COALESCE(SUM(CASE WHEN moderation_status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_count, " +
                "    COALESCE(SUM(CASE WHEN moderation_status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_count, " +
                "    COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_lifecycle_count " +
                "FROM jobs";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new AdminJobModerationKPI(
                        rs.getInt("total_jobs"),
                        rs.getInt("pending_count"),
                        rs.getInt("approved_count"),
                        rs.getInt("rejected_count"),
                        rs.getInt("suspended_count"),
                        rs.getInt("active_lifecycle_count")
                );
            }
            return new AdminJobModerationKPI(0, 0, 0, 0, 0, 0);
        }
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

    private void setNullableString(PreparedStatement s, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) s.setNull(idx, Types.VARCHAR);
        else                              s.setString(idx, val.trim());
    }
}

