package rentwheels;

import java.sql.*;

/** JDBC CRUD for the users table (only the operations the app needs). */
public class UserDao {
    private static final String COLS = "id, name, email, password_hash, role";

    private static User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.id = rs.getInt("id"); u.name = rs.getString("name"); u.email = rs.getString("email");
        u.passwordHash = rs.getString("password_hash"); u.role = rs.getString("role");
        return u;
    }

    public User create(Connection c, String name, String email, String passwordHash, String role) throws SQLException {   // C
        String sql = "INSERT INTO users (name, email, password_hash, role) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, email); ps.setString(3, passwordHash); ps.setString(4, role);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return findById(c, k.getInt(1)); }
        }
    }

    public User findById(Connection c, int id) throws SQLException {                                                      // R
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public User findByEmail(Connection c, String email) throws SQLException {                                             // R
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }
}
