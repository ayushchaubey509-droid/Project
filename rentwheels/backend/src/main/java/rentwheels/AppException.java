package rentwheels;
public class AppException extends RuntimeException {
    public final int status;
    public AppException(int status, String message) { super(message); this.status = status; }
    public static AppException bad(String m)      { return new AppException(400, m); }
    public static AppException notFound(String m) { return new AppException(404, m); }
    public static AppException conflict(String m) { return new AppException(409, m); }
}
