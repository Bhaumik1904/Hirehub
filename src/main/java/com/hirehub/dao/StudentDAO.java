package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.StudentProfile;

import java.sql.*;
import java.util.Optional;

/**
 * StudentDAO — CRUD for the student_profiles table.
 * All SQL uses PreparedStatement. Never called directly from controllers.
 */
public class StudentDAO {

    private static final String SQL_INSERT =
            "INSERT INTO student_profiles (user_id, phone, college, degree, graduation_year, skills, resume_path) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE student_profiles SET phone=?, college=?, degree=?, graduation_year=?, "
            + "skills=?, resume_path=? WHERE user_id=?";

    private static final String SQL_FIND_BY_USER =
            "SELECT id, user_id, phone, college, degree, graduation_year, skills, resume_path, updated_at "
            + "FROM student_profiles WHERE user_id=?";

    private static final String SQL_EXISTS_BY_USER =
            "SELECT 1 FROM student_profiles WHERE user_id=? LIMIT 1";

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------

    /**
     * Inserts a brand-new student profile.
     * Caller must ensure a profile does not already exist (use saveOrUpdate).
     */
    public void insert(StudentProfile p) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt   (1, p.getUserId());
            setNullableString(stmt, 2, p.getPhone());
            setNullableString(stmt, 3, p.getCollege());
            setNullableString(stmt, 4, p.getDegree());
            setNullableInt   (stmt, 5, p.getGraduationYear());
            setNullableString(stmt, 6, p.getSkills());
            setNullableString(stmt, 7, p.getResumePath());

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) p.setId(keys.getInt(1));
            }
        }
    }

    /** Updates all editable fields for the student with the given user_id. */
    public void update(StudentProfile p) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE)) {

            setNullableString(stmt, 1, p.getPhone());
            setNullableString(stmt, 2, p.getCollege());
            setNullableString(stmt, 3, p.getDegree());
            setNullableInt   (stmt, 4, p.getGraduationYear());
            setNullableString(stmt, 5, p.getSkills());
            setNullableString(stmt, 6, p.getResumePath());
            stmt.setInt      (7, p.getUserId());

            stmt.executeUpdate();
        }
    }

    /**
     * Insert-or-update: if a profile exists for this userId, update it;
     * otherwise insert a new row.
     */
    public void saveOrUpdate(StudentProfile p) throws SQLException {
        if (existsByUserId(p.getUserId())) {
            update(p);
        } else {
            insert(p);
        }
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    /** Loads the profile for the given user_id, returns empty if none exists. */
    public Optional<StudentProfile> findByUserId(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_USER)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        }
    }

    /** Returns true if a profile row already exists for this user_id. */
    public boolean existsByUserId(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_USER)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private StudentProfile mapRow(ResultSet rs) throws SQLException {
        StudentProfile p = new StudentProfile();
        p.setId            (rs.getInt   ("id"));
        p.setUserId        (rs.getInt   ("user_id"));
        p.setPhone         (rs.getString("phone"));
        p.setCollege       (rs.getString("college"));
        p.setDegree        (rs.getString("degree"));
        p.setGraduationYear(rs.getInt   ("graduation_year"));
        p.setSkills        (rs.getString("skills"));
        p.setResumePath    (rs.getString("resume_path"));

        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) p.setUpdatedAt(ts.toLocalDateTime());

        return p;
    }

    // -----------------------------------------------------------------------
    // Helpers for nullable SQL parameters
    // -----------------------------------------------------------------------

    private void setNullableString(PreparedStatement s, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) s.setNull(idx, Types.VARCHAR);
        else                              s.setString(idx, val.trim());
    }

    private void setNullableInt(PreparedStatement s, int idx, int val) throws SQLException {
        if (val <= 0) s.setNull(idx, Types.INTEGER);
        else          s.setInt(idx, val);
    }
}
