package rentwheels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
public class Main {
    static final Gson GSON = new Gson();
    static final RentalService svc = new RentalService();
    static Path staticRoot;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(Db.env("PORT", "8080"));
        staticRoot = Paths.get(Db.env("STATIC_DIR", "../frontend")).toAbsolutePath().normalize();
        try (var c = Db.get()) { System.out.println("Connected to database: " + c.getMetaData().getURL()); }   // fail fast

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", Main::api);
        server.createContext("/", Main::staticFile);
        server.start();
        System.out.println("RentWheels running at http://localhost:" + port + "  (frontend from " + staticRoot + ")");
    }
    static void api(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String[] p = ex.getRequestURI().getPath().substring("/api/".length()).split("/");
            Object result = route(method, p, ex);
            send(ex, 200, GSON.toJson(result));
        } catch (AppException e) {
            send(ex, e.status, GSON.toJson(Map.of("error", e.getMessage())));
        } catch (SQLIntegrityConstraintViolationException e) {
            send(ex, 409, GSON.toJson(Map.of("error", "That value already exists (for example the registration number).")));
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, GSON.toJson(Map.of("error", "Database error. Please try again.")));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, GSON.toJson(Map.of("error", "Something went wrong. Please try again.")));
        }
    }

    static Object route(String m, String[] p, HttpExchange ex) throws Exception {
        switch (p[0]) {
            case "login": {
                requireMethod(m, "POST"); JsonObject b = body(ex);
                return svc.login(str(b, "email"), str(b, "password"));
            }
            case "register": {
                requireMethod(m, "POST"); JsonObject b = body(ex);
                svc.register(str(b, "name"), str(b, "email"), str(b, "password"));
                return Map.of("ok", true);
            }
            case "vehicles": {
                if (p.length == 1) {
                    if (m.equals("GET"))  return svc.listVehicles();
                    if (m.equals("POST")) return svc.addVehicle(vehicleFrom(body(ex), 0));
                } else {
                    int id = id(p[1]);
                    if (m.equals("GET"))    return svc.getVehicle(id);
                    if (m.equals("PUT"))    return svc.updateVehicle(vehicleFrom(body(ex), id));
                    if (m.equals("DELETE")) { svc.deleteVehicle(id); return Map.of("ok", true); }
                }
                break;
            }
            case "bookings": {
                if (p.length == 1) {
                    if (m.equals("GET")) {
                        String q = ex.getRequestURI().getQuery();            // e.g. userId=2
                        Integer uid = (q != null && q.startsWith("userId=")) ? id(q.substring(7)) : null;
                        return svc.listBookings(uid);
                    }
                    if (m.equals("POST")) {
                        JsonObject b = body(ex);
                        return svc.book(num(b, "userId"), num(b, "vehicleId"), str(b, "start"), str(b, "end"));
                    }
                } else if (p.length == 2 && m.equals("DELETE")) {
                    svc.removeBooking(id(p[1])); return Map.of("ok", true);
                } else if (p.length == 3 && m.equals("POST")) {
                    if (p[2].equals("cancel")) return svc.cancel(id(p[1]));
                    if (p[2].equals("return")) return svc.processReturn(id(p[1]));
                }
                break;
            }
        }
        throw AppException.notFound("Unknown endpoint.");
    }

    // ---------- helpers ----------
    static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) throw new AppException(405, "Method not allowed.");
    }
    static int id(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { throw AppException.bad("Invalid id."); }
    }
    static JsonObject body(HttpExchange ex) throws IOException {
        String s = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        try { return s.isEmpty() ? new JsonObject() : JsonParser.parseString(s).getAsJsonObject(); }
        catch (RuntimeException e) { throw AppException.bad("Invalid request body."); }
    }
    static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    static int num(JsonObject o, String k) {
        try { return o.get(k).getAsInt(); } catch (RuntimeException e) { throw AppException.bad("Missing or invalid field: " + k); }
    }

    static Vehicle vehicleFrom(JsonObject b, int id) {
        String type = str(b, "type"), name = str(b, "name"), reg = str(b, "regNo");
        double price;
        try { price = b.get("pricePerDay").getAsDouble(); } catch (RuntimeException e) { price = 0; }
        if (name == null || name.trim().isEmpty() || reg == null || reg.trim().isEmpty() || !(price > 0))
            throw AppException.bad("Name, registration no. and a positive price are required.");
        Vehicle v;
        if ("Car".equals(type)) {
            Car c = new Car(); c.seats = b.has("seats") ? num(b, "seats") : 4;
            String f = str(b, "fuelType"); c.fuelType = f == null || f.trim().isEmpty() ? "Petrol" : f.trim();
            if (c.seats < 1) throw AppException.bad("Seats must be at least 1.");
            v = c;
        } else if ("Bike".equals(type)) {
            Bike k = new Bike(); k.engineCC = b.has("engineCC") ? num(b, "engineCC") : 100;
            if (k.engineCC < 1) throw AppException.bad("Engine CC must be at least 1.");
            v = k;
        } else throw AppException.bad("Type must be Car or Bike.");
        v.id = id; v.name = name.trim(); v.regNo = reg.trim(); v.pricePerDay = price;
        return v;
    }

    static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, out.length);
        try (var os = ex.getResponseBody()) { os.write(out); }
    }

    // ---------- static files (the frontend) ----------
    static void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Path f = staticRoot.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!f.startsWith(staticRoot) || !Files.isRegularFile(f)) { send(ex, 404, "{\"error\":\"Not found\"}"); return; }
        String n = f.getFileName().toString();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".css") ? "text/css; charset=utf-8"
                    : n.endsWith(".js") ? "text/javascript; charset=utf-8" : "application/octet-stream";
        byte[] data = Files.readAllBytes(f);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, data.length);
        try (var os = ex.getResponseBody()) { os.write(data); }
    }
}
