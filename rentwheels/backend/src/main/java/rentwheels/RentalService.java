package rentwheels;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** Business rules. Every multi-step change runs inside one DB transaction. */
public class RentalService {
    private final UserDao users = new UserDao();
    private final VehicleDao vehicles = new VehicleDao();
    private final BookingDao bookings = new BookingDao();
    private static final Pattern EMAIL = Pattern.compile("^\\S+@\\S+\\.\\S+$");

    // ---------- users ----------
    static String hash(String pw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pw.getBytes("UTF-8"))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public User login(String email, String password) throws SQLException {
        User u = Db.query(c -> users.findByEmail(c, email == null ? "" : email.trim().toLowerCase()));
        if (u == null || password == null || !u.passwordHash.equals(hash(password)))
            throw new AppException(401, "Invalid email or password.");
        return u;
    }

    public User register(String name, String email, String password) throws SQLException {
        name = name == null ? "" : name.trim(); email = email == null ? "" : email.trim().toLowerCase();
        if (name.isEmpty() || !EMAIL.matcher(email).matches() || password == null || password.length() < 4)
            throw AppException.bad("Enter a name, a valid email and a password of 4+ characters.");
        final String n = name, e = email, h = hash(password);
        return Db.tx(c -> {
            if (users.findByEmail(c, e) != null) throw AppException.conflict("That email is already registered.");
            return users.create(c, n, e, h, "USER");
        });
    }

    // ---------- vehicles ----------
    public List<Vehicle> listVehicles() throws SQLException { return Db.query(vehicles::readAll); }

    public Vehicle getVehicle(int id) throws SQLException {
        Vehicle v = Db.query(c -> vehicles.findById(c, id, false));
        if (v == null) throw AppException.notFound("Vehicle not found.");
        return v;
    }

    public Vehicle addVehicle(Vehicle v) throws SQLException { return Db.tx(c -> vehicles.create(c, v)); }

    public Vehicle updateVehicle(Vehicle v) throws SQLException {
        return Db.tx(c -> {
            Vehicle old = vehicles.findById(c, v.id, true);
            if (old == null) throw AppException.notFound("Vehicle not found.");
            v.available = old.available;                       // availability is only changed by bookings/returns
            if (!old.type.equals(v.type)) throw AppException.bad("Vehicle type cannot be changed.");
            vehicles.update(c, v);
            return vehicles.findById(c, v.id, false);
        });
    }

    public void deleteVehicle(int id) throws SQLException {
        Db.tx(c -> {
            if (bookings.hasActiveForVehicle(c, id)) throw AppException.conflict("Cannot delete: this vehicle has an active booking.");
            if (!vehicles.delete(c, id)) throw AppException.notFound("Vehicle not found.");
            return null;
        });
    }

    // ---------- bookings ----------
    public List<Booking> listBookings(Integer userId) throws SQLException {
        return Db.query(c -> userId == null ? bookings.readAll(c) : bookings.readByUser(c, userId));
    }

    public Booking book(int userId, int vehicleId, String startStr, String endStr) throws SQLException {
        LocalDate start, end;
        try { start = LocalDate.parse(startStr); end = LocalDate.parse(endStr); }
        catch (DateTimeParseException | NullPointerException e) { throw AppException.bad("Select both a start and a return date."); }
        if (start.isBefore(LocalDate.now())) throw AppException.bad("Start date cannot be in the past.");
        if (!end.isAfter(start)) throw AppException.bad("Return date must be after the start date.");
        int days = (int) ChronoUnit.DAYS.between(start, end);

        int id = Db.tx(c -> {
            if (users.findById(c, userId) == null) throw AppException.notFound("User not found.");
            Vehicle v = vehicles.findById(c, vehicleId, true);          // row lock: two people cannot grab the same vehicle
            if (v == null) throw AppException.notFound("Vehicle not found.");
            if (!v.available) throw AppException.conflict("Sorry, this vehicle is already booked.");
            int bid = bookings.create(c, userId, vehicleId, start, end, days, v.calculateRent(days));   // polymorphic price
            vehicles.setAvailable(c, vehicleId, false);
            return bid;
        });
        return Db.query(c -> bookings.findById(c, id));
    }

    public Booking cancel(int id) throws SQLException {
        Db.tx(c -> {
            Booking b = bookings.findByIdForUpdate(c, id);
            if (b == null) throw AppException.notFound("Booking not found.");
            if (!"ACTIVE".equals(b.status)) throw AppException.conflict("Only active bookings can be cancelled.");
            bookings.updateStatus(c, id, "CANCELLED");
            if (b.vehicleId != null) vehicles.setAvailable(c, b.vehicleId, true);
            return null;
        });
        return Db.query(c -> bookings.findById(c, id));
    }

    /** Late fee = 1.5 x daily price for every day after the booked return date. */
    public Booking processReturn(int id) throws SQLException {
        Db.tx(c -> {
            Booking b = bookings.findByIdForUpdate(c, id);
            if (b == null) throw AppException.notFound("Booking not found.");
            if (!"ACTIVE".equals(b.status)) throw AppException.conflict("Only active bookings can be returned.");
            int late = (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(b.end), LocalDate.now()));
            Vehicle v = b.vehicleId == null ? null : vehicles.findById(c, b.vehicleId, true);
            double fin = b.amount + (v == null ? 0 : late * v.pricePerDay * 1.5);
            bookings.markReturned(c, id, late, fin);
            if (v != null) vehicles.setAvailable(c, v.id, true);
            return null;
        });
        return Db.query(c -> bookings.findById(c, id));
    }

    public void removeBooking(int id) throws SQLException {
        Db.tx(c -> {
            Booking b = bookings.findByIdForUpdate(c, id);
            if (b == null) throw AppException.notFound("Booking not found.");
            if ("ACTIVE".equals(b.status)) throw AppException.conflict("Cancel or return an active booking before removing it.");
            bookings.delete(c, id);
            return null;
        });
    }
}
