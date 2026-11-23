package com.hirehub.util;

import com.hirehub.model.User;

/**
 * SessionManager — In-memory session store for the currently logged-in user.
 *
 * <p>Implemented as a thread-safe singleton. Stores the authenticated
 * {@link User} object for the duration of the application session.</p>
 *
 * <p>On logout or session invalidation, the stored user is cleared.</p>
 */
public class SessionManager {

    // -----------------------------------------------------------------------
    // Singleton setup
    // -----------------------------------------------------------------------

    private static volatile SessionManager instance;

    /**
     * Returns the singleton SessionManager instance.
     * Uses double-checked locking for thread safety.
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // Session state
    // -----------------------------------------------------------------------

    /** The currently authenticated user, or null if no session is active. */
    private volatile User currentUser;

    // Private constructor — use getInstance()
    private SessionManager() {}

    // -----------------------------------------------------------------------
    // Session operations
    // -----------------------------------------------------------------------

    /**
     * Stores the authenticated user and starts a new session.
     *
     * @param user the user who successfully logged in
     */
    public void login(User user) {
        this.currentUser = user;
        System.out.println("[SessionManager] Session started for: " + user.getEmail()
                + " [" + user.getRole() + "]");
    }

    /**
     * Clears the current session (logout).
     */
    public void logout() {
        if (currentUser != null) {
            System.out.println("[SessionManager] Session ended for: " + currentUser.getEmail());
        }
        this.currentUser = null;
    }

    /**
     * Returns the currently logged-in user.
     *
     * @return the authenticated {@link User}, or null if not logged in
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Checks whether there is an active user session.
     *
     * @return true if a user is logged in, false otherwise
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Convenience method to check if the current user has a specific role.
     *
     * @param role the role to check
     * @return true if the current user has that role
     */
    public boolean hasRole(User.Role role) {
        return isLoggedIn() && currentUser.getRole() == role;
    }
}
