package my.ru;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class BirthdayBot extends TelegramLongPollingBot {
    private final BirthdayDatabase database;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Кнопки интерфейса
    private static final String ADD_BIRTHDAY = "➕ Добавить день рождения";
    private static final String DELETE_BIRTHDAY = "➖ Удалить день рождения";
    private static final String CHECK_TODAY = "🎂 Сегодняшние дни рождения";
    private static final String LIST_ALL = "📅 Все дни рождения";
    private static final String HELP = "❓ Помощь";
    private static final String CANCEL = "❌ Отмена";

    // Состояния
    private static final String STATE_ADD = "ADD";
    private static final String STATE_DELETE = "DELETE";
    private final Map<Long, String> userStates = new HashMap<>();

    public BirthdayBot() {
        this.database = new BirthdayDatabase();
    }
    public BirthdayDatabase getDatabase() {
        return this.database;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            try {
                // Обработка отмены
                if (messageText.equals(CANCEL)) {
                    userStates.remove(chatId);
                    showMainMenu(chatId);
                    return;
                }

                String userState = userStates.get(chatId);

                if (userState != null) {
                    handleUserState(chatId, userState, messageText);
                    return;
                }

                handleMainMenu(chatId, messageText);
            } catch (Exception e) {
                sendError(chatId, e);
            }
        }
    }

    private void handleUserState(long chatId, String state, String input) throws SQLException {
        switch (state) {
            case STATE_ADD:
                processAddBirthday(chatId, input);
                break;
            case STATE_DELETE:
                processDeleteBirthday(chatId, input);
                break;
        }
        userStates.remove(chatId);
        showMainMenu(chatId);
    }

    private void handleMainMenu(long chatId, String command) throws SQLException {
        switch (command) {
            case ADD_BIRTHDAY:
                prepareAddBirthday(chatId);
                break;
            case DELETE_BIRTHDAY:
                prepareDeleteBirthday(chatId);
                break;
            case CHECK_TODAY:
                showTodayBirthdays(chatId);
                break;
            case LIST_ALL:
                showAllBirthdays(chatId);
                break;
            case HELP:
                showHelp(chatId);
                break;
            default:
                showMainMenu(chatId);
        }
    }

    private void prepareAddBirthday(long chatId) {
        userStates.put(chatId, STATE_ADD);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Введите данные в формате:\nФамилия Имя Отчество(опционально) дд.мм.гггг\n\nПример:\nИванов Иван 15.08.1990\n\nИли нажмите ❌ Отмена");
        showCancelKeyboard(message);
        executeMessage(message);
    }

    private void processAddBirthday(long chatId, String input) throws SQLException {
        String[] parts = input.split("\\s+", 4);

        if (parts.length < 3) {
            sendMessage(chatId, "❌ Неверный формат. Нужно: Фамилия Имя [Отчество] дд.мм.гггг");
            return;
        }

        try {
            String lastName = parts[0];
            String firstName = parts[1];
            String middleName = parts.length > 3 ? parts[2] : null;
            String dateStr = parts.length > 3 ? parts[3] : parts[2];

            LocalDate birthDate = LocalDate.parse(dateStr, dateFormatter);
            database.addBirthday(lastName, firstName, middleName, birthDate, chatId);

            sendMessage(chatId, "✅ Добавлен: " + formatName(lastName, firstName, middleName) +
                    " - " + birthDate.format(dateFormatter));
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "❌ Ошибка формата даты. Используйте дд.мм.гггг");
        }
    }

    private void prepareDeleteBirthday(long chatId) {
        userStates.put(chatId, STATE_DELETE);

        try {
            List<BirthdayDatabase.BirthdayRecord> records = database.getAllBirthdays(chatId);
            if (records.isEmpty()) {
                sendMessage(chatId, "Нет записей для удаления");
                userStates.remove(chatId);
                return;
            }

            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Выберите запись для удаления:\n(Фамилия Имя Отчество)\n\nИли нажмите ❌ Отмена");

            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            List<KeyboardRow> rows = new ArrayList<>();

            // Добавляем кнопки для каждой записи
            for (BirthdayDatabase.BirthdayRecord record : records) {
                KeyboardRow row = new KeyboardRow();
                row.add(new KeyboardButton(record.getFullName()));
                rows.add(row);
            }

            // Добавляем кнопку отмены
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(new KeyboardButton(CANCEL));
            rows.add(cancelRow);

            keyboard.setKeyboard(rows);
            keyboard.setResizeKeyboard(true);
            message.setReplyMarkup(keyboard);

            executeMessage(message);
        } catch (SQLException e) {
            sendError(chatId, e);
        }
    }

    private void processDeleteBirthday(long chatId, String input) throws SQLException {
        String[] parts = input.split("\\s+", 3);

        if (parts.length < 2) {
            sendMessage(chatId, "❌ Неверный формат. Нужно: Фамилия Имя [Отчество]");
            return;
        }

        String lastName = parts[0];
        String firstName = parts[1];
        String middleName = parts.length > 2 ? parts[2] : null;

        if (database.deleteBirthday(lastName, firstName, middleName, chatId)) {
            sendMessage(chatId, "✅ Удален: " + formatName(lastName, firstName, middleName));
        } else {
            sendMessage(chatId, "❌ Запись не найдена");
        }
    }

    private void showTodayBirthdays(long chatId) throws SQLException {
        List<String> birthdays = database.getBirthdaysByDate(LocalDate.now());

        if (birthdays.isEmpty()) {
            sendMessage(chatId, "Сегодня никто не празднует день рождения 🎈");
        } else {
            StringBuilder sb = new StringBuilder("🎉 Сегодня день рождения у:\n\n");
            birthdays.forEach(name -> sb.append("• ").append(name).append("\n"));
            sendMessage(chatId, sb.toString());
        }
    }

    private void showAllBirthdays(long chatId) throws SQLException {
        List<BirthdayDatabase.BirthdayRecord> records = database.getAllBirthdays(chatId);

        if (records.isEmpty()) {
            sendMessage(chatId, "В базе нет записей о днях рождения");
            return;
        }

        Map<Month, List<BirthdayDatabase.BirthdayRecord>> byMonth = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getBirthDate().getMonth(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        StringBuilder sb = new StringBuilder("📅 Все дни рождения:\n\n");

        byMonth.forEach((month, monthRecords) -> {
            sb.append("🗓 ").append(month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")))
                    .append(":\n");

            monthRecords.forEach(record ->
                    sb.append("• ").append(record.getFormattedDate())
                            .append(" - ").append(record.getFullName())
                            .append("\n")
            );

            sb.append("\n");
        });

        sendMessage(chatId, sb.toString());
    }

    private void showHelp(long chatId) {
        String helpText = """
                🎂 <b>Бот-напоминатель о днях рождения</b> 🎂
                
                <b>Как использовать:</b>
                1. Добавить день рождения - вводите ФИО и дату
                2. Удалить - выбираете из списка
                3. Просматривайте дни рождения
                
                <b>Формат даты:</b> дд.мм.гггг (например 15.08.1990)
                
                Данные хранятся в вашей личной базе""";

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(helpText);
        message.setParseMode("HTML");
        executeMessage(message);
    }

    private void showMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите действие:");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(ADD_BIRTHDAY));
        row1.add(new KeyboardButton(DELETE_BIRTHDAY));

        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(CHECK_TODAY));
        row2.add(new KeyboardButton(LIST_ALL));

        // Третья строка
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(HELP));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        message.setReplyMarkup(keyboard);

        executeMessage(message);
    }

    private void showCancelKeyboard(SendMessage message) {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(CANCEL));
        rows.add(row);
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        message.setReplyMarkup(keyboard);
    }

    private String formatName(String lastName, String firstName, String middleName) {
        return lastName + " " + firstName + (middleName != null ? " " + middleName : "");
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private void sendError(long chatId, Exception e) {
        sendMessage(chatId, "⚠️ Ошибка: " + e.getMessage());
        showMainMenu(chatId);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return "https://t.me/birthday444_bot";
    }

    @Override
    public String getBotToken() {
        return "8121916279:AAFkmyUek7WsV6ib1dQ6ZHWP1sGc-4nOiXo";
    }

    public void handleCheckBirthdays(int i) {
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




