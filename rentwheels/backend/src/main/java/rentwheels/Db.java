package rentwheels;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC connection helper. Settings come from environment variables, with local-MySQL defaults. */
public final class Db {
    private Db() {}

    static final String URL  = env("DB_URL",
            "jdbc:mysql://localhost:3306/rentwheels?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    static final String USER = env("DB_USER", "root");
    static final String PASS = env("DB_PASSWORD", "");

    static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isEmpty() ? d : v; }

    public static Connection get() throws SQLException { return DriverManager.getConnection(URL, USER, PASS); }

    @FunctionalInterface public interface Work<T> { T run(Connection c) throws SQLException; }

    /** Runs the work on a fresh connection with auto-commit off: commit on success, rollback on any failure. */
    public static <T> T tx(Work<T> work) throws SQLException {
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try { T r = work.run(c); c.commit(); return r; }
            catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    public static <T> T query(Work<T> work) throws SQLException {
        try (Connection c = get()) { return work.run(c); }
    }
}
