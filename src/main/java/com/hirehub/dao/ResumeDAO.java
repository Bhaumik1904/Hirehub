package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.Resume;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ResumeDAO — JDBC CRUD operations for the `resumes` table.
 */
public class ResumeDAO {

    private static final String SQL_INSERT =
            "INSERT INTO resumes (student_id, file_name, stored_path, file_size_bytes, "
            + "file_hash, parsed_text, parsed_json, is_active) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE resumes SET file_name=?, stored_path=?, file_size_bytes=?, file_hash=?, "
            + "parsed_text=?, parsed_json=?, is_active=? WHERE id=?";

    private static final String SQL_FIND_ACTIVE_BY_STUDENT =
            "SELECT id, student_id, file_name, stored_path, file_size_bytes, file_hash, "
            + "parsed_text, parsed_json, is_active, uploaded_at, updated_at "
            + "FROM resumes WHERE student_id=? AND is_active=TRUE ORDER BY id DESC LIMIT 1";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, student_id, file_name, stored_path, file_size_bytes, file_hash, "
            + "parsed_text, parsed_json, is_active, uploaded_at, updated_at "
            + "FROM resumes WHERE id=?";

    private static final String SQL_FIND_ALL_BY_STUDENT =
            "SELECT id, student_id, file_name, stored_path, file_size_bytes, file_hash, "
            + "parsed_text, parsed_json, is_active, uploaded_at, updated_at "
            + "FROM resumes WHERE student_id=? ORDER BY uploaded_at DESC";

    private static final String SQL_DEACTIVATE_ALL =
            "UPDATE resumes SET is_active=FALSE WHERE student_id=?";

    private static final String SQL_SET_ACTIVE =
            "UPDATE resumes SET is_active=TRUE WHERE id=? AND student_id=?";

    private static final String SQL_DELETE =
            "DELETE FROM resumes WHERE id=?";

    private static final String SQL_UPDATE_FOR_STUDENT =
            "UPDATE resumes SET file_name=?, stored_path=?, file_size_bytes=?, file_hash=?, "
            + "parsed_text=?, parsed_json=?, is_active=? WHERE id=? AND student_id=?";

    private static final String SQL_FIND_BY_ID_FOR_STUDENT =
            "SELECT id, student_id, file_name, stored_path, file_size_bytes, file_hash, "
            + "parsed_text, parsed_json, is_active, uploaded_at, updated_at "
            + "FROM resumes WHERE id=? AND student_id=?";

    private static final String SQL_DELETE_FOR_STUDENT =
            "DELETE FROM resumes WHERE id=? AND student_id=?";

    private static final String SQL_FIND_RESUME_FOR_APP_AND_RECRUITER =
            "SELECT r.id, r.student_id, r.file_name, r.stored_path, r.file_size_bytes, r.file_hash, "
            + "r.parsed_text, r.parsed_json, r.is_active, r.uploaded_at, r.updated_at "
            + "FROM applications a "
            + "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? "
            + "INNER JOIN resumes r ON r.id = a.resume_id AND r.student_id = a.student_id "
            + "WHERE a.id = ?";

    private static final String SQL_FIND_RESUME_FOR_APP_ADMIN =
            "SELECT r.id, r.student_id, r.file_name, r.stored_path, r.file_size_bytes, r.file_hash, "
            + "r.parsed_text, r.parsed_json, r.is_active, r.uploaded_at, r.updated_at "
            + "FROM applications a "
            + "INNER JOIN resumes r ON r.id = a.resume_id AND r.student_id = a.student_id "
            + "WHERE a.id = ?";

    private static final String SQL_IS_RESUME_REFERENCED =
            "SELECT 1 FROM applications WHERE resume_id = ? LIMIT 1";

    /**
     * Inserts a new resume row. Sets generated id on the model.
     */
    public void insert(Resume r) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, r.getStudentId());
            stmt.setString(2, r.getFileName());
            stmt.setString(3, r.getStoredPath());
            stmt.setLong(4, r.getFileSizeBytes());
            stmt.setString(5, r.getFileHash());
            setNullableString(stmt, 6, r.getParsedText());
            setNullableString(stmt, 7, r.getParsedJson());
            stmt.setBoolean(8, r.isActive());

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    r.setId(keys.getInt(1));
                }
            }
        }
    }

    /**
     * Updates an existing resume row.
     *
     * @deprecated Unscoped update. Use {@link #updateForStudent(Resume, int)} for student operations.
     */
    @Deprecated
    public void update(Resume r) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            stmt.setString(1, r.getFileName());
            stmt.setString(2, r.getStoredPath());
            stmt.setLong(3, r.getFileSizeBytes());
            stmt.setString(4, r.getFileHash());
            setNullableString(stmt, 5, r.getParsedText());
            setNullableString(stmt, 6, r.getParsedJson());
            stmt.setBoolean(7, r.isActive());
            stmt.setInt(8, r.getId());

            stmt.executeUpdate();
        }
    }

    /**
     * Atomically updates a resume row only if owned by the given student.
     *
     * @param r         the resume entity with updated fields
     * @param studentId the student user id owning the resume
     * @return true if a row was updated, false otherwise
     */
    public boolean updateForStudent(Resume r, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_FOR_STUDENT)) {

            stmt.setString(1, r.getFileName());
            stmt.setString(2, r.getStoredPath());
            stmt.setLong(3, r.getFileSizeBytes());
            stmt.setString(4, r.getFileHash());
            setNullableString(stmt, 5, r.getParsedText());
            setNullableString(stmt, 6, r.getParsedJson());
            stmt.setBoolean(7, r.isActive());
            stmt.setInt(8, r.getId());
            stmt.setInt(9, studentId);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Finds the currently active resume for the given student.
     */
    public Optional<Resume> findActiveByStudentId(int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_ACTIVE_BY_STUDENT)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a resume by primary key id.
     *
     * @deprecated Unscoped lookup. Use {@link #findByIdForStudent(int, int)} for student operations.
     */
    @Deprecated
    public Optional<Resume> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a resume by ID scoped to a student (atomic ownership check).
     */
    public Optional<Resume> findByIdForStudent(int id, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID_FOR_STUDENT)) {

            stmt.setInt(1, id);
            stmt.setInt(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Lists all resumes uploaded by a student (active and inactive).
     */
    public List<Resume> findAllByStudentId(int studentId) throws SQLException {
        List<Resume> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_ALL_BY_STUDENT)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Deactivates all existing resumes for a student prior to setting a new active one.
     */
    public void deactivateAllForStudent(int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DEACTIVATE_ALL)) {
            stmt.setInt(1, studentId);
            stmt.executeUpdate();
        }
    }

    /**
     * Deactivates all other resumes and sets the designated resume as active.
     */
    public void activateResume(int resumeId, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement deact = conn.prepareStatement(SQL_DEACTIVATE_ALL)) {
                    deact.setInt(1, studentId);
                    deact.executeUpdate();
                }
                try (PreparedStatement act = conn.prepareStatement(SQL_SET_ACTIVE)) {
                    act.setInt(1, resumeId);
                    act.setInt(2, studentId);
                    act.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Deletes a resume row.
     *
     * @deprecated Unscoped delete. Use {@link #deleteForStudent(int, int)} for student operations.
     */
    @Deprecated
    public void delete(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically deletes a resume only if owned by the given student.
     *
     * @param id        the resume primary key
     * @param studentId the student user id
     * @return true if a row was deleted, false otherwise
     */
    public boolean deleteForStudent(int id, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FOR_STUDENT)) {
            stmt.setInt(1, id);
            stmt.setInt(2, studentId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Finds the resume attached to an application only if the application's job is owned by recruiterId.
     * Enforces job ownership, applicant identity, and application reference in a single atomic query.
     */
    public Optional<Resume> findResumeForApplicationAndRecruiter(int applicationId, int recruiterId) throws SQLException {
        if (applicationId <= 0 || recruiterId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_RESUME_FOR_APP_AND_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            stmt.setInt(2, applicationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Admin-only lookup of the resume attached to an application.
     */
    public Optional<Resume> findResumeForApplicationForAdmin(int applicationId) throws SQLException {
        if (applicationId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_RESUME_FOR_APP_ADMIN)) {
            stmt.setInt(1, applicationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Checks if a resume is referenced by any application in the applications table.
     */
    public boolean isReferencedByAnyApplication(int resumeId) throws SQLException {
        if (resumeId <= 0) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_IS_RESUME_REFERENCED)) {
            stmt.setInt(1, resumeId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ── Row Mapping ─────────────────────────────────────────────────────────

    private Resume mapRow(ResultSet rs) throws SQLException {
        Resume r = new Resume();
        r.setId           (rs.getInt("id"));
        r.setStudentId    (rs.getInt("student_id"));
        r.setFileName     (rs.getString("file_name"));
        r.setStoredPath   (rs.getString("stored_path"));
        r.setFileSizeBytes(rs.getLong("file_size_bytes"));
        r.setFileHash     (rs.getString("file_hash"));
        r.setParsedText   (rs.getString("parsed_text"));
        r.setParsedJson   (rs.getString("parsed_json"));
        r.setActive       (rs.getBoolean("is_active"));

        Timestamp up = rs.getTimestamp("uploaded_at");
        if (up != null) r.setUploadedAt(up.toLocalDateTime());

        Timestamp upd = rs.getTimestamp("updated_at");
        if (upd != null) r.setUpdatedAt(upd.toLocalDateTime());

        return r;
    }

    private void setNullableString(PreparedStatement stmt, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) stmt.setNull(idx, Types.VARCHAR);
        else                              stmt.setString(idx, val);
    }
}

