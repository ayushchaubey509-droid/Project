package rentwheels;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class BookingDao {
    private static final String SELECT =
        "SELECT b.id, b.user_id, b.vehicle_id, b.start_date, b.end_date, b.days, b.amount, b.final_amount, b.late_days, b.status, " +
        "u.name AS user_name, v.name AS vehicle_name FROM bookings b " +
        "JOIN users u ON u.id = b.user_id LEFT JOIN vehicles v ON v.id = b.vehicle_id ";

    private static Booking map(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.id = rs.getInt("id"); b.userId = rs.getInt("user_id");
        int vid = rs.getInt("vehicle_id"); b.vehicleId = rs.wasNull() ? null : vid;
        b.start = rs.getDate("start_date").toLocalDate().toString(); b.end = rs.getDate("end_date").toLocalDate().toString();
        b.days = rs.getInt("days"); b.amount = rs.getDouble("amount");
        double fa = rs.getDouble("final_amount"); b.finalAmount = rs.wasNull() ? null : fa;
        int ld = rs.getInt("late_days"); b.lateDays = rs.wasNull() ? null : ld;
        b.status = rs.getString("status"); b.userName = rs.getString("user_name"); b.vehicleName = rs.getString("vehicle_name");
        return b;
    }

    public int create(Connection c, int userId, int vehicleId, LocalDate start, LocalDate end, int days, double amount) throws SQLException {   // C
        String sql = "INSERT INTO bookings (user_id, vehicle_id, start_date, end_date, days, amount, status) VALUES (?,?,?,?,?,?,'ACTIVE')";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId); ps.setInt(2, vehicleId); ps.setDate(3, Date.valueOf(start)); ps.setDate(4, Date.valueOf(end));
            ps.setInt(5, days); ps.setBigDecimal(6, java.math.BigDecimal.valueOf(amount));
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    public List<Booking> readAll(Connection c) throws SQLException {                                                      // R
        List<Booking> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(SELECT + "ORDER BY b.id")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Booking> readByUser(Connection c, int userId) throws SQLException {                                       // R
        List<Booking> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE b.user_id = ? ORDER BY b.id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public Booking findById(Connection c, int id) throws SQLException {                                                   // R
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE b.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    /** Locks just the booking row (no joins) and returns it; names are left null. */
    public Booking findByIdForUpdate(Connection c, int id) throws SQLException {
        String sql = "SELECT id, user_id, vehicle_id, start_date, end_date, days, amount, final_amount, late_days, status, " +
                     "NULL AS user_name, NULL AS vehicle_name FROM bookings WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }

    public void updateStatus(Connection c, int id, String status) throws SQLException {                                    // U
        try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, id); ps.executeUpdate();
        }
    }

    public void markReturned(Connection c, int id, int lateDays, double finalAmount) throws SQLException {                 // U
        String sql = "UPDATE bookings SET status='RETURNED', late_days=?, final_amount=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lateDays); ps.setBigDecimal(2, java.math.BigDecimal.valueOf(finalAmount)); ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public boolean hasActiveForVehicle(Connection c, int vehicleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM bookings WHERE vehicle_id=? AND status='ACTIVE'")) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        }
    }

    public void delete(Connection c, int id) throws SQLException {                                                        // D
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM bookings WHERE id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
    }
}
