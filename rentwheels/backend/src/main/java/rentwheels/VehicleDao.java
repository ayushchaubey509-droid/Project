package rentwheels;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** JDBC CRUD for the vehicles table. */
public class VehicleDao {
    private static final String COLS = "id, type, name, reg_no, price_per_day, available, seats, fuel_type, engine_cc";

    public Vehicle create(Connection c, Vehicle v) throws SQLException {                                                  // C
        String sql = "INSERT INTO vehicles (type, name, reg_no, price_per_day, available, seats, fuel_type, engine_cc) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, v);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return findById(c, k.getInt(1), false); }
        }
    }

    public List<Vehicle> readAll(Connection c) throws SQLException {                                                      // R
        List<Vehicle> list = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT " + COLS + " FROM vehicles ORDER BY id")) {
            while (rs.next()) list.add(Vehicle.fromRow(rs));
        }
        return list;
    }

    /** forUpdate = true locks the row until the surrounding transaction ends (prevents double booking). */
    public Vehicle findById(Connection c, int id, boolean forUpdate) throws SQLException {                                // R
        String sql = "SELECT " + COLS + " FROM vehicles WHERE id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Vehicle.fromRow(rs) : null; }
        }
    }

    public void update(Connection c, Vehicle v) throws SQLException {                                                     // U
        String sql = "UPDATE vehicles SET type=?, name=?, reg_no=?, price_per_day=?, available=?, seats=?, fuel_type=?, engine_cc=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, v); ps.setInt(9, v.id);
            ps.executeUpdate();
        }
    }

    public void setAvailable(Connection c, int id, boolean available) throws SQLException {                               // U
        try (PreparedStatement ps = c.prepareStatement("UPDATE vehicles SET available=? WHERE id=?")) {
            ps.setBoolean(1, available); ps.setInt(2, id); ps.executeUpdate();
        }
    }

    public boolean delete(Connection c, int id) throws SQLException {                                                     // D
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM vehicles WHERE id=?")) {
            ps.setInt(1, id); return ps.executeUpdate() > 0;
        }
    }

    private static void bind(PreparedStatement ps, Vehicle v) throws SQLException {
        ps.setString(1, v.type); ps.setString(2, v.name); ps.setString(3, v.regNo);
        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(v.pricePerDay)); ps.setBoolean(5, v.available);
        if (v instanceof Car c) { ps.setInt(6, c.seats); ps.setString(7, c.fuelType); ps.setNull(8, Types.INTEGER); }
        else { ps.setNull(6, Types.INTEGER); ps.setNull(7, Types.VARCHAR); ps.setInt(8, ((Bike) v).engineCC); }
    }
}
