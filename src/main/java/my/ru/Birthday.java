package my.ru;

public class Birthday {
    private final String name;
    private final int day;
    private final int month;
    private final int year;

    public Birthday(String name, int day, int month, int year) {
        this.name = name;
        this.day = day;
        this.month = month;
        this.year = year;
    }

    // Геттеры
    public String getName() { return name; }
    public int getDay() { return day; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
}