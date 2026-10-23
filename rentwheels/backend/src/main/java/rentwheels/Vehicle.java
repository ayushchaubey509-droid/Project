package rentwheels;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Base class. Field names match the JSON the frontend already uses. */
public abstract class Vehicle {
    public int id;
    public String type;
    public String name;
    public String regNo;
    public double pricePerDay;
    public boolean available = true;

    public abstract double calculateRent(int days);          // overridden by Car / Bike
    protected double baseRent(int days) { return pricePerDay * days; }

    /** Factory: builds the right subclass from a DB row. */
    static Vehicle fromRow(ResultSet rs) throws SQLException {
        Vehicle v;
        if ("Car".equals(rs.getString("type"))) {
            Car c = new Car(); c.seats = rs.getInt("seats"); c.fuelType = rs.getString("fuel_type"); v = c;
        } else {
            Bike b = new Bike(); b.engineCC = rs.getInt("engine_cc"); v = b;
        }
        v.id = rs.getInt("id"); v.name = rs.getString("name"); v.regNo = rs.getString("reg_no");
        v.pricePerDay = rs.getDouble("price_per_day"); v.available = rs.getBoolean("available");
        return v;
    }
}
