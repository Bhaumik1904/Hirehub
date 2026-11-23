package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * UserDAO — Data Access Object for the {@code users} table.
 *
 * <p>All SQL is isolated here — controllers and services never touch JDBC
 * directly. Every query uses {@link PreparedStatement} to prevent SQL injection.</p>
 */
public class UserDAO {

    // -----------------------------------------------------------------------
    // SQL constants
    // -----------------------------------------------------------------------

    private static final String SQL_INSERT_USER =
            "INSERT INTO users (full_name, email, password_hash, role, is_active) VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_EMAIL =
            "SELECT id, full_name, email, password_hash, role, is_active, created_at "
            + "FROM users WHERE email = ?";

    private static final String SQL_EXISTS_BY_EMAIL =
            "SELECT 1 FROM users WHERE email = ? LIMIT 1";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, full_name, email, password_hash, role, is_active, created_at "
            + "FROM users WHERE id = ?";

    // -----------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------

    /**
     * Inserts a new user into the database.
     *
     * @param user the user to persist (password_hash must already be hashed)
     * @return the auto-generated user ID
     * @throws SQLException if insertion fails (e.g. duplicate email)
     */
    public int insertUser(User user) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getRole().name());
            stmt.setBoolean(5, user.isActive());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert failed — no rows affected.");
            }

            // Retrieve the auto-generated primary key
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                } else {
                    throw new SQLException("Insert succeeded but no ID was returned.");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Read operations
    // -----------------------------------------------------------------------

    /**
     * Finds a user by their email address.
     *
     * @param email the email to search for
     * @return an Optional containing the user if found, or empty
     * @throws SQLException on database error
     */
    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_EMAIL)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a user by their numeric ID.
     *
     * @param id the user's primary key
     * @return an Optional containing the user if found, or empty
     * @throws SQLException on database error
     */
    public Optional<User> findById(int id) throws SQLException {
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
     * Checks whether a given email address is already registered.
     *
     * @param email the email to check
     * @return true if the email already exists in the database
     * @throws SQLException on database error
     */
    public boolean emailExists(String email) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_EMAIL)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mapping helper
    // -----------------------------------------------------------------------

    /**
     * Maps a {@link ResultSet} row to a {@link User} instance.
     * Assumes the cursor is already positioned on the desired row.
     */
    private User mapRow(ResultSet rs) throws SQLException {
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
}
