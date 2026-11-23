package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Represents a registered user in the HireHub system.
 * This is the base entity stored in the users table.
 */
public class User {

    public enum Role {
        STUDENT, RECRUITER, ADMIN
    }

    private int           id;
    private String        fullName;
    private String        email;
    private String        passwordHash;
    private Role          role;
    private boolean       isActive = true;
    private LocalDateTime createdAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public User() {}

    /**
     * Constructor used when creating a new user (before DB insertion).
     */
    public User(String fullName, String email, String passwordHash, Role role) {
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.isActive     = true;
    }

    /**
     * Constructor used when creating a new user with explicit active status.
     */
    public User(String fullName, String email, String passwordHash, Role role, boolean isActive) {
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.isActive     = isActive;
    }

    /**
     * Backward-compatible constructor — used when loading a user from the database.
     */
    public User(int id, String fullName, String email,
                String passwordHash, Role role, LocalDateTime createdAt) {
        this.id           = id;
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.isActive     = true;
        this.createdAt    = createdAt;
    }

    /**
     * Full constructor including active status — used when loading from the database.
     */
    public User(int id, String fullName, String email,
                String passwordHash, Role role, boolean isActive, LocalDateTime createdAt) {
        this.id           = id;
        this.fullName     = fullName;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.isActive     = isActive;
        this.createdAt    = createdAt;
    }

    // -----------------------------------------------------------------------
    // Getters and Setters
    // -----------------------------------------------------------------------

    public int getId()                      { return id; }
    public void setId(int id)               { this.id = id; }

    public String getFullName()             { return fullName; }
    public void setFullName(String name)    { this.fullName = name; }

    public String getEmail()                { return email; }
    public void setEmail(String email)      { this.email = email; }

    public String getPasswordHash()         { return passwordHash; }
    public void setPasswordHash(String h)   { this.passwordHash = h; }

    public Role getRole()                   { return role; }
    public void setRole(Role role)          { this.role = role; }

    public boolean isActive()               { return isActive; }
    public void setActive(boolean active)   { this.isActive = active; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime created) { this.createdAt = created; }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', role=" + role + ", active=" + isActive + "}";
    }
}
