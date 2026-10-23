package rentwheels;

public class User {
    public int id;
    public String name;
    public String email;
    public String role;
    public transient String passwordHash;   // transient: Gson never sends it to the browser
}
