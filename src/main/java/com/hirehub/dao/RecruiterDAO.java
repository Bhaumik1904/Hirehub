package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.RecruiterProfile;
import com.hirehub.model.RecruiterVerificationStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RecruiterDAO — CRUD and Admin Governance for the recruiter_profiles table.
 *
 * <p>Supports recruiter profile management and Phase 6 Step 4.2 account verification governance.</p>
 */
public class RecruiterDAO {

    private static final String SQL_INSERT =
            "INSERT INTO recruiter_profiles (user_id, company_name, company_website, company_description, industry, "
            + "verification_status, verification_reason, verified_at, verified_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE recruiter_profiles SET company_name=?, company_website=?, company_description=?, industry=? "
            + "WHERE user_id=?";

    private static final String SQL_FIND_BY_USER =
            "SELECT rp.id, rp.user_id, rp.company_name, rp.company_website, rp.company_description, rp.industry, "
            + "rp.verification_status, rp.verification_reason, rp.verified_at, rp.verified_by, rp.updated_at, "
            + "u.full_name AS recruiter_name, u.email AS recruiter_email, u.is_active AS user_active, u.created_at AS user_created_at "
            + "FROM recruiter_profiles rp "
            + "LEFT JOIN users u ON rp.user_id = u.id "
            + "WHERE rp.user_id=?";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, user_id, company_name, company_website, company_description, industry, "
            + "verification_status, verification_reason, verified_at, verified_by, updated_at "
            + "FROM recruiter_profiles WHERE id=?";

    private static final String SQL_EXISTS_BY_USER =
            "SELECT 1 FROM recruiter_profiles WHERE user_id=? LIMIT 1";

    private static final String SQL_UPDATE_VERIFICATION =
            "UPDATE recruiter_profiles SET verification_status=?, verification_reason=?, "
            + "verified_at=?, verified_by=? WHERE user_id=?";

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------

    public void insert(RecruiterProfile p) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, p.getUserId());
            setNullableString(stmt, 2, p.getCompanyName());
            setNullableString(stmt, 3, p.getCompanyWebsite());
            setNullableString(stmt, 4, p.getCompanyDescription());
            setNullableString(stmt, 5, p.getIndustry());

            RecruiterVerificationStatus status = p.getVerificationStatus() != null
                    ? p.getVerificationStatus()
                    : RecruiterVerificationStatus.UNVERIFIED;
            stmt.setString(6, status.name());
            setNullableString(stmt, 7, p.getVerificationReason());

            if (p.getVerifiedAt() != null) {
                stmt.setTimestamp(8, Timestamp.valueOf(p.getVerifiedAt()));
            } else {
                stmt.setNull(8, Types.TIMESTAMP);
            }

            if (p.getVerifiedBy() != null && p.getVerifiedBy() > 0) {
                stmt.setInt(9, p.getVerifiedBy());
            } else {
                stmt.setNull(9, Types.INTEGER);
            }

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) p.setId(keys.getInt(1));
            }
        }
    }

    /**
     * Recruiter-facing profile update. Mutates only company details;
     * verification status is protected and unmodified.
     */
    public void update(RecruiterProfile p) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            setNullableString(stmt, 1, p.getCompanyName());
            setNullableString(stmt, 2, p.getCompanyWebsite());
            setNullableString(stmt, 3, p.getCompanyDescription());
            setNullableString(stmt, 4, p.getIndustry());
            stmt.setInt      (5, p.getUserId());

            stmt.executeUpdate();
        }
    }

    public void saveOrUpdate(RecruiterProfile p) throws SQLException {
        if (existsByUserId(p.getUserId())) {
            update(p);
        } else {
            insert(p);
        }
    }

    /**
     * Inserts a new recruiter profile strictly initialized with PENDING verification status.
     * Clears any moderation reason or administrator attribution.
     */
    public void insertWithPendingStatus(RecruiterProfile p) throws SQLException {
        p.setVerificationStatus(RecruiterVerificationStatus.PENDING);
        p.setVerificationReason(null);
        p.setVerifiedAt(null);
        p.setVerifiedBy(null);
        insert(p);
    }

    /**
     * Atomically transitions an UNVERIFIED or REJECTED recruiter profile to PENDING
     * with updated company information.
     *
     * <p>Enforces state transition rules at the database boundary:
     * only UNVERIFIED or REJECTED profiles may transition to PENDING.
     * Existing VERIFIED, SUSPENDED, or already PENDING profiles are rejected by the SQL predicate.
     * Clears previous rejection reason and admin attribution upon valid submission.</p>
     *
     * @return true if an existing UNVERIFIED or REJECTED profile was updated to PENDING;
     *         false if no matching row satisfied the predicate.
     */
    public boolean submitForVerification(int userId, String companyName, String companyWebsite,
                                         String companyDescription, String industry) throws SQLException {
        if (userId <= 0) {
            return false;
        }

        String sql =
                "UPDATE recruiter_profiles " +
                "SET company_name = ?, company_website = ?, company_description = ?, industry = ?, " +
                "    verification_status = 'PENDING', verification_reason = NULL, " +
                "    verified_at = NULL, verified_by = NULL " +
                "WHERE user_id = ? AND verification_status IN ('UNVERIFIED', 'REJECTED')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setNullableString(stmt, 1, companyName);
            setNullableString(stmt, 2, companyWebsite);
            setNullableString(stmt, 3, companyDescription);
            setNullableString(stmt, 4, industry);
            stmt.setInt(5, userId);

            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM recruiter_profiles WHERE user_id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    // -----------------------------------------------------------------------
    // Admin Verification Operations
    // -----------------------------------------------------------------------

    /**
     * Updates verification status for a recruiter profile.
     * Automatically sets or clears {@code verified_at} and {@code verified_by}
     * based on whether the target status is an audited decision (VERIFIED, REJECTED, SUSPENDED).
     */
    public boolean updateVerificationStatus(int userId, RecruiterVerificationStatus status,
                                            String reason, Integer adminId) throws SQLException {
        boolean isAudited = (status == RecruiterVerificationStatus.VERIFIED
                          || status == RecruiterVerificationStatus.REJECTED
                          || status == RecruiterVerificationStatus.SUSPENDED);
        LocalDateTime verifiedAt = isAudited ? LocalDateTime.now() : null;
        Integer admin = isAudited ? adminId : null;
        return updateVerificationStatus(userId, status, reason, admin, verifiedAt);
    }

    /**
     * Updates verification status with explicit timestamp and administrator attribution.
     */
    public boolean updateVerificationStatus(int userId, RecruiterVerificationStatus status,
                                            String reason, Integer adminId, LocalDateTime verifiedAt) throws SQLException {
        if (userId <= 0 || status == null) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VERIFICATION)) {

            stmt.setString(1, status.name());
            setNullableString(stmt, 2, reason);

            if (verifiedAt != null) {
                stmt.setTimestamp(3, Timestamp.valueOf(verifiedAt));
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }

            if (adminId != null && adminId > 0) {
                stmt.setInt(4, adminId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }

            stmt.setInt(5, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    public Optional<RecruiterProfile> findByUserId(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USER)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public Optional<RecruiterProfile> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    public boolean existsByUserId(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_USER)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Searches recruiter profiles for administrative review and queue management.
     * Supports filtering by query (company name, recruiter full name, recruiter email, user ID)
     * and verification status.
     */
    public List<RecruiterProfile> searchRecruitersForAdmin(String query, RecruiterVerificationStatus statusFilter,
                                                          int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT rp.id, rp.user_id, rp.company_name, rp.company_website, rp.company_description, rp.industry, "
                + "rp.verification_status, rp.verification_reason, rp.verified_at, rp.verified_by, rp.updated_at, "
                + "u.full_name AS recruiter_name, u.email AS recruiter_email, u.is_active AS user_active, u.created_at AS user_created_at "
                + "FROM recruiter_profiles rp "
                + "LEFT JOIN users u ON rp.user_id = u.id "
                + "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        buildAdminRecruiterWhereClause(sql, params, query, statusFilter);

        sql.append("ORDER BY rp.updated_at DESC, rp.id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        List<RecruiterProfile> list = new ArrayList<>();
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
     * Counts recruiter profiles matching the given administrative search and filter criteria.
     */
    public int countRecruitersForAdmin(String query, RecruiterVerificationStatus statusFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM recruiter_profiles rp "
                + "LEFT JOIN users u ON rp.user_id = u.id "
                + "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        buildAdminRecruiterWhereClause(sql, params, query, statusFilter);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mapping & Helpers
    // -----------------------------------------------------------------------

    private RecruiterProfile mapRow(ResultSet rs) throws SQLException {
        RecruiterProfile p = new RecruiterProfile();
        p.setId                (rs.getInt   ("id"));
        p.setUserId            (rs.getInt   ("user_id"));
        p.setCompanyName       (rs.getString("company_name"));
        p.setCompanyWebsite    (rs.getString("company_website"));
        p.setCompanyDescription(rs.getString("company_description"));
        p.setIndustry          (rs.getString("industry"));

        String statusStr = rs.getString("verification_status");
        if (statusStr != null) {
            p.setVerificationStatus(RecruiterVerificationStatus.fromString(statusStr));
        } else {
            p.setVerificationStatus(RecruiterVerificationStatus.UNVERIFIED);
        }

        p.setVerificationReason(rs.getString("verification_reason"));

        Timestamp verifiedAt = rs.getTimestamp("verified_at");
        if (verifiedAt != null) {
            p.setVerifiedAt(verifiedAt.toLocalDateTime());
        }

        int verifiedBy = rs.getInt("verified_by");
        p.setVerifiedBy(rs.wasNull() ? null : verifiedBy);

        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) p.setUpdatedAt(ts.toLocalDateTime());

        try {
            p.setRecruiterName(rs.getString("recruiter_name"));
            p.setRecruiterEmail(rs.getString("recruiter_email"));
            boolean userActive = rs.getBoolean("user_active");
            if (!rs.wasNull()) {
                p.setUserActive(userActive);
            }
            Timestamp uca = rs.getTimestamp("user_created_at");
            if (uca != null) {
                p.setUserCreatedAt(uca.toLocalDateTime());
            }
        } catch (SQLException ignored) {}

        return p;
    }

    private void buildAdminRecruiterWhereClause(StringBuilder sql, List<Object> params,
                                                String query, RecruiterVerificationStatus statusFilter) {
        if (query != null && !query.trim().isEmpty()) {
            String trimmed = query.trim().toLowerCase();
            String likeParam = "%" + trimmed + "%";
            Integer parsedId = null;
            try {
                parsedId = Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {}

            if (parsedId != null) {
                sql.append("AND (LOWER(rp.company_name) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ? OR rp.user_id = ?) ");
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
                params.add(parsedId);
            } else {
                sql.append("AND (LOWER(rp.company_name) LIKE ? OR LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ?) ");
                params.add(likeParam);
                params.add(likeParam);
                params.add(likeParam);
            }
        }

        if (statusFilter != null) {
            sql.append("AND rp.verification_status = ? ");
            params.add(statusFilter.name());
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
}
