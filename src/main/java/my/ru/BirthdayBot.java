package my.ru;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class BirthdayBot extends TelegramLongPollingBot {
    private final BirthdayDatabase database;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private ScheduledExecutorService scheduler;

    public BirthdayBot() {
        this.database = new BirthdayDatabase();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            try {
                if (messageText.startsWith("/addbirthday")) {
                    handleAddBirthday(chatId, messageText);
                } else if (messageText.startsWith("/deletebirthday")) {
                    handleDeleteBirthday(chatId, messageText);
                } else if (messageText.equals("/checkbirthdays")) {
                    handleCheckBirthdays(chatId);
                } else if (messageText.equals("/listbirthdays")) {
                    handleListBirthdays(chatId);
                } else if (messageText.equals("/help")) {
                    sendHelp(chatId);
                } else if (messageText.equals("/start")) {
                    sendWelcome(chatId);
                }
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ Ошибка: " + e.getMessage());
            }
        }
    }

    private void handleAddBirthday(long chatId, String messageText) throws Exception {
        String[] parts = messageText.split(" ", 5);

        if (parts.length < 4) {
            sendMessage(chatId, "Неверный формат. Используйте: /addbirthday Фамилия Имя Отчество(опц.) dd.MM.yyyy");
            return;
        }

        try {
            String lastName = parts[1];
            String firstName = parts[2];
            String middleName = parts.length > 4 ? parts[3] : null;
            String dateStr = parts.length > 4 ? parts[4] : parts[3];

            LocalDate birthDate = LocalDate.parse(dateStr, dateFormatter);
            database.addBirthday(lastName, firstName, middleName, birthDate, chatId);

            String fullName = buildFullName(lastName, firstName, middleName);
            sendMessage(chatId, "✅ День рождения для " + fullName + " добавлен!");
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "❌ Ошибка формата даты. Используйте dd.MM.yyyy");
        }
    }

    private void handleDeleteBirthday(long chatId, String messageText) throws Exception {
        String[] parts = messageText.split(" ", 4);

        if (parts.length < 3) {
            sendMessage(chatId, "Неверный формат. Используйте: /deletebirthday Фамилия Имя Отчество(опц.)");
            return;
        }

        String lastName = parts[1];
        String firstName = parts[2];
        String middleName = parts.length > 3 ? parts[3] : null;

        database.deleteBirthday(lastName, firstName, middleName, chatId);

        String fullName = buildFullName(lastName, firstName, middleName);
        sendMessage(chatId, "✅ День рождения для " + fullName + " удалён!");
    }

    void handleCheckBirthdays(long chatId) throws Exception {
        LocalDate today = LocalDate.now();
        List<String> birthdays = database.getBirthdaysByDate(today);

        if (birthdays.isEmpty()) {
            sendMessage(chatId, "Сегодня никто не празднует день рождения 😊");
        } else {
            StringBuilder message = new StringBuilder("🎉 Сегодня день рождения у:\n");
            for (String name : birthdays) {
                message.append("- ").append(name).append("\n");
            }
            sendMessage(chatId, message.toString());
        }
    }

    private void handleListBirthdays(long chatId) throws Exception {
        List<BirthdayDatabase.BirthdayRecord> birthdays = database.getAllBirthdays(chatId);

        if (birthdays.isEmpty()) {
            sendMessage(chatId, "В базе нет записей о днях рождения.");
            return;
        }

        Map<Month, List<BirthdayDatabase.BirthdayRecord>> byMonth = birthdays.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getBirthDate().getMonth(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        StringBuilder message = new StringBuilder("📅 Все дни рождения:\n\n");

        for (Map.Entry<Month, List<BirthdayDatabase.BirthdayRecord>> entry : byMonth.entrySet()) {
            message.append("🗓 ").append(entry.getKey().getDisplayName(
                    TextStyle.FULL_STANDALONE,
                    new Locale("ru"))
            ).append(":\n");

            for (BirthdayDatabase.BirthdayRecord record : entry.getValue()) {
                message.append("• ").append(record.getFormattedDate())
                        .append(" - ").append(record.getFullName())
                        .append("\n");
            }
            message.append("\n");
        }

        splitAndSendLongMessage(chatId, message.toString());
    }

    public void startBirthdayNotifier() {
        scheduler = Executors.newScheduledThreadPool(1);
        long initialDelay = getInitialDelay();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndNotifyUpcomingBirthdays();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    private void checkAndNotifyUpcomingBirthdays() throws SQLException {
        checkBirthdaysForDay(0);  // Сегодня
        checkBirthdaysForDay(3);  // Через 3 дня
        checkBirthdaysForDay(7);  // Через неделю
    }

    private void checkBirthdaysForDay(int daysBefore) throws SQLException {
        List<BirthdayDatabase.BirthdayRecord> upcoming = database.getUpcomingBirthdays(daysBefore);

        for (BirthdayDatabase.BirthdayRecord record : upcoming) {
            String message = createNotificationMessage(record, daysBefore);
            sendMessage(record.getChatId(), message);
        }
    }

    private String createNotificationMessage(BirthdayDatabase.BirthdayRecord record, int daysBefore) {
        String fullName = record.getFullName();
        String formattedDate = record.getFormattedDate();
        int years = Year.now().getValue() - record.getBirthDate().getYear();

        if (daysBefore == 0) {
            return String.format(
                    "🎉 Сегодня день рождения у %s! (%s, %d %s)\n" +
                            "Не забудьте поздравить! 🎂🎁",
                    fullName, formattedDate, years, getYearWord(years));
        } else {
            return String.format(
                    "🔔 Напоминание: через %d %s день рождения у %s (%s, будет %d %s)\n" +
                            "Подготовьте поздравление!",
                    daysBefore, getDayWord(daysBefore), fullName,
                    formattedDate, years + 1, getYearWord(years + 1));
        }
    }

    private String getDayWord(int days) {
        if (days % 10 == 1 && days % 100 != 11) return "день";
        if (days % 10 >= 2 && days % 10 <= 4 &&
                (days % 100 < 10 || days % 100 >= 20)) return "дня";
        return "дней";
    }

    private String getYearWord(int years) {
        if (years % 10 == 1 && years % 100 != 11) return "год";
        if (years % 10 >= 2 && years % 10 <= 4 &&
                (years % 100 < 10 || years % 100 >= 20)) return "года";
        return "лет";
    }

    private void sendWelcome(long chatId) {
        String text = "👋 Привет! Я бот для напоминания о днях рождения.\n\n" +
                "Используйте /help для списка команд";
        sendMessage(chatId, text);
    }

    private void sendHelp(long chatId) {
        String helpText = """
                🎂 Бот для уведомлений о днях рождения 🎂
                
                Команды:
                /addbirthday Фамилия Имя Отчество(опц.) dd.MM.yyyy - добавить
                /deletebirthday Фамилия Имя Отчество(опц.) - удалить
                /checkbirthdays - проверить сегодняшние дни рождения
                /listbirthdays - показать все дни рождения (сгруппированы по месяцам)
                /help - показать это сообщение
                
                Примеры:
                /addbirthday Иванов Иван Иванович 15.08.1990
                /addbirthday Петров Петр 20.05.1985
                /deletebirthday Иванов Иван Иванович""";
        sendMessage(chatId, helpText);
    }

    private String buildFullName(String lastName, String firstName, String middleName) {
        return lastName + " " + firstName + (middleName != null ? " " + middleName : "");
    }

    private void splitAndSendLongMessage(long chatId, String longMessage) {
        int length = longMessage.length();
        for (int i = 0; i < length; i += 4000) {
            String part = longMessage.substring(i, Math.min(length, i + 4000));
            sendMessage(chatId, part);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    private static long getInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(9).withMinute(0).withSecond(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }

    @Override
    public String getBotUsername() {
        return "https://t.me/birthday444_bot";
    }

    @Override
    public String getBotToken() {
        return "8121916279:AAFkmyUek7WsV6ib1dQ6ZHWP1sGc-4nOiXo";
    }

    @Override
    public void onClosing() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        super.onClosing();
    }
}

/*@Override
    public String getBotUsername() {
        return "https://t.me/birthday444_bot";
    }

    @Override
    public String getBotToken() {
        return "8121916279:AAFkmyUek7WsV6ib1dQ6ZHWP1sGc-4nOiXo";
    }*/




