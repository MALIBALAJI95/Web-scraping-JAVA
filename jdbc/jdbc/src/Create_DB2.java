import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Create_DB2 {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "New4jdbc44";
    private static final String DB_NAME = "WEBSCARPING";

    public static void main(String[] args) {

        System.out.println("[DB] Starting database initialization...");

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            String createDbQuery =
                    "CREATE DATABASE IF NOT EXISTS " + DB_NAME +
                    " DEFAULT CHARACTER SET utf8mb4 " +
                    " COLLATE utf8mb4_unicode_ci";

            stmt.executeUpdate(createDbQuery);

            System.out.println("[DB] Database ready: " + DB_NAME);
            System.out.println("[DB] Connection closed cleanly.");

        } catch (SQLException e) {
            System.err.println("[DB] Database creation failed.");
            System.err.println("[DB] Reason: " + e.getMessage());
        }
    }
}
