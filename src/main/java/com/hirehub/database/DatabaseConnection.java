package com.hirehub.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConnection — Manages a centralized, thread-safe HikariCP connection pool to MySQL.
 *
 * <p>Phase 6 Step 3 (GAP-02): Replaces repeated DriverManager-based connection allocations
 * with high-performance connection pooling while preserving the standard
 * {@code DatabaseConnection.getConnection()} API for all DAOs.</p>
 *
 * <p>Database credentials and pool tuning settings are read from environment variables:</p>
 * <ul>
 *   <li>DB_HOST — MySQL host (default: localhost)</li>
 *   <li>DB_PORT — MySQL port (default: 3306)</li>
 *   <li>DB_NAME — Database name (default: hirehub_db)</li>
 *   <li>DB_USER — MySQL username (default: root)</li>
 *   <li>DB_PASS — MySQL password (read from env)</li>
 *   <li>DB_MAX_POOL_SIZE — Maximum connection pool size (default: 10)</li>
 *   <li>DB_MIN_IDLE — Minimum idle connections (default: 2)</li>
 * </ul>
 *
 * <p>Usage example (try-with-resources):</p>
 * <pre>{@code
 *   try (Connection conn = DatabaseConnection.getConnection()) {
 *       // perform JDBC operations; connection is returned to pool on close()
 *   }
 * }</pre>
 */
public class DatabaseConnection {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_NAME = "hirehub_db";
    private static final String DEFAULT_USER = "root";
    private static final int DEFAULT_MAX_POOL_SIZE = 10;
    private static final int DEFAULT_MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT_MS = 30000;
    private static final long IDLE_TIMEOUT_MS = 600000;
    private static final long MAX_LIFETIME_MS = 1800000;
    private static final long VALIDATION_TIMEOUT_MS = 5000;

    /** Centralized HikariCP data source (singleton). */
    private static volatile HikariDataSource dataSource = null;

    /** Lock for synchronized initialization and teardown. */
    private static final Object LOCK = new Object();

    // Register JVM shutdown hook for headless verifiers / command-line terminations
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isInitialized()) {
                shutdown();
            }
        }, "HireHub-HikariShutdownHook"));
    }

    // Prevent instantiation — utility class
    private DatabaseConnection() {}

    /**
     * Retrieves or lazily creates the initialized HikariDataSource.
     * Thread-safe and resilient to re-initialization if closed during testing.
     *
     * @return the active HikariDataSource
     */
    public static HikariDataSource getDataSource() {
        HikariDataSource localRef = dataSource;
        if (localRef == null || localRef.isClosed()) {
            synchronized (LOCK) {
                localRef = dataSource;
                if (localRef == null || localRef.isClosed()) {
                    dataSource = createDataSource();
                    localRef = dataSource;
                }
            }
        }
        return localRef;
    }

    /**
     * Constructs and initializes a new HikariDataSource using configured properties.
     */
    private static HikariDataSource createDataSource() {
        String dbHost = getEnv("DB_HOST", DEFAULT_HOST);
        String dbPort = getEnv("DB_PORT", DEFAULT_PORT);
        String dbName = getEnv("DB_NAME", DEFAULT_NAME);
        String dbUser = getEnv("DB_USER", DEFAULT_USER);
        String dbPass = getEnv("DB_PASS", "");

        if (dbPass.isBlank()) {
            System.err.println(
                    "[HireHub] WARNING: DB_PASS environment variable is not set. "
                    + "Ensure MySQL password is configured.");
        }

        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%s/%s"
                + "?useSSL=false"
                + "&serverTimezone=Asia/Kolkata"
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8",
                dbHost, dbPort, dbName
        );

        int maxPoolSize = getEnvInt("DB_MAX_POOL_SIZE", DEFAULT_MAX_POOL_SIZE);
        int minIdle = getEnvInt("DB_MIN_IDLE", DEFAULT_MIN_IDLE);

        HikariConfig config = new HikariConfig();
        config.setPoolName("HireHubPool");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPass);

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(Math.min(minIdle, maxPoolSize));
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setValidationTimeout(VALIDATION_TIMEOUT_MS);
        config.setAutoCommit(true);

        // MySQL Recommended Performance Properties
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        return new HikariDataSource(config);
    }

    /**
     * Borrows a connection from the HikariCP connection pool.
     *
     * <p>Callers must use try-with-resources to return the connection to the pool:</p>
     * <pre>{@code
     *   try (Connection conn = DatabaseConnection.getConnection()) { ... }
     * }</pre>
     *
     * @return a live {@link Connection} borrowed from the pool
     * @throws SQLException if a connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        try {
            return getDataSource().getConnection();
        } catch (SQLException e) {
            throw new SQLException(
                    "Failed to borrow connection from HikariCP pool. "
                    + "Check MySQL service status and credentials.\n"
                    + "Original error: " + e.getMessage(), e);
        }
    }

    /**
     * Tests connectivity to the database by borrowing and validating a connection.
     *
     * @return true if a test connection succeeded, false otherwise
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            System.err.println("[HireHub] Database connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the HikariCP connection pool is currently initialized and active.
     */
    public static boolean isInitialized() {
        HikariDataSource localRef = dataSource;
        return localRef != null && !localRef.isClosed();
    }

    /**
     * Shuts down the HikariCP connection pool idempotently.
     * Closes all active and idle pooled connections cleanly.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
                System.out.println("[HireHub] HikariCP connection pool shut down successfully.");
            }
        }
    }

    /**
     * Returns the count of connections currently borrowed / in active use.
     */
    public static int getActiveConnections() {
        if (isInitialized()) {
            HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
            if (bean != null) {
                return bean.getActiveConnections();
            }
        }
        return 0;
    }

    /**
     * Returns the count of idle connections currently resting in the pool.
     */
    public static int getIdleConnections() {
        if (isInitialized()) {
            HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
            if (bean != null) {
                return bean.getIdleConnections();
            }
        }
        return 0;
    }

    /**
     * Returns the total count of connections (active + idle) in the pool.
     */
    public static int getTotalConnections() {
        if (isInitialized()) {
            HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
            if (bean != null) {
                return bean.getTotalConnections();
            }
        }
        return 0;
    }

    /**
     * Returns the count of threads currently waiting to borrow a connection.
     */
    public static int getThreadsAwaitingConnection() {
        if (isInitialized()) {
            HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
            if (bean != null) {
                return bean.getThreadsAwaitingConnection();
            }
        }
        return 0;
    }

    /**
     * Returns a human-readable summary of the current connection pool status.
     */
    public static String getPoolStatusSummary() {
        if (!isInitialized()) {
            return "HikariCP Pool: [Not Initialized / Closed]";
        }
        return String.format(
                "HikariCP Pool [%s]: Active=%d, Idle=%d, Total=%d, WaitingThreads=%d",
                dataSource.getPoolName(),
                getActiveConnections(),
                getIdleConnections(),
                getTotalConnections(),
                getThreadsAwaitingConnection()
        );
    }

    // -----------------------------------------------------------------------
    // Environment property helpers
    // -----------------------------------------------------------------------

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
