package my.ru;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BirthdayDatabase {
    private static final String DB_URL = "jdbc:sqlite:birthdays.db";
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS birthdays (" +
            "id INTEGER PRIMARY KEY," +
            "last_name TEXT NOT NULL," +      // Фамилия
            "first_name TEXT NOT NULL," +    // Имя
            "middle_name TEXT," +            // Отчество (может быть null)
            "birth_date TEXT NOT NULL," +    // Дата рождения в формате yyyy-MM-dd
            "chat_id INTEGER NOT NULL)";     // ID чата

    public BirthdayDatabase() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    public void addBirthday(String lastName, String firstName, String middleName,
                            LocalDate birthDate, long chatId) throws SQLException {
        String sql = "INSERT INTO birthdays (last_name, first_name, middle_name, birth_date, chat_id) " +
                "VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, lastName);
            pstmt.setString(2, firstName);
            pstmt.setString(3, middleName);
            pstmt.setString(4, birthDate.toString());
            pstmt.setLong(5, chatId);
            pstmt.executeUpdate();
        }
    }

    public List<String> getBirthdaysByDate(LocalDate date) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT last_name, first_name, middle_name FROM birthdays " +
                "WHERE strftime('%m-%d', birth_date) = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String fullName = rs.getString("last_name") + " " +
                        rs.getString("first_name") +
                        (rs.getString("middle_name") != null ?
                                " " + rs.getString("middle_name") : "");
                names.add(fullName);
            }
        }
        return names;
    }

    public void deleteBirthday(String lastName, String firstName, String middleName,
                               long chatId) throws SQLException {
        String sql = "DELETE FROM birthdays WHERE last_name = ? AND first_name = ? " +
                "AND (middle_name = ? OR (middle_name IS NULL AND ? IS NULL)) " +
                "AND chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, lastName);
            pstmt.setString(2, firstName);
            pstmt.setString(3, middleName);
            pstmt.setString(4, middleName);
            pstmt.setLong(5, chatId);
            pstmt.executeUpdate();
        }
    }

    public List<BirthdayRecord> getAllBirthdays(long chatId) throws SQLException {
        List<BirthdayRecord> birthdays = new ArrayList<>();
        String sql = "SELECT last_name, first_name, middle_name, birth_date FROM birthdays " +
                "WHERE chat_id = ? ORDER BY strftime('%m-%d', birth_date)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String lastName = rs.getString("last_name");
                String firstName = rs.getString("first_name");
                String middleName = rs.getString("middle_name");
                LocalDate birthDate = LocalDate.parse(rs.getString("birth_date"));

                birthdays.add(new BirthdayRecord(lastName, firstName, middleName, birthDate));
            }
        }
        return birthdays;
    }

    public static class BirthdayRecord {
        private final String lastName;
        private final String firstName;
        private final String middleName;
        private final LocalDate birthDate;

        public BirthdayRecord(String lastName, String firstName, String middleName, LocalDate birthDate) {
            this.lastName = lastName;
            this.firstName = firstName;
            this.middleName = middleName;
            this.birthDate = birthDate;
        }

        public String getFullName() {
            return lastName + " " + firstName + (middleName != null ? " " + middleName : "");
        }

        public String getFormattedDate() {
            return birthDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }

        public LocalDate getBirthDate() {
            return birthDate;
        }
    }
}