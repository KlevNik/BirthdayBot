package my.ru;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class BirthdayBot extends TelegramLongPollingBot {
    private final BirthdayDatabase database;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
                }
            } catch (Exception e) {
                sendMessage(chatId, "⚠️ Ошибка: " + e.getMessage());
            }
        }
    }

    private void handleAddBirthday(long chatId, String messageText) throws Exception {
        // Формат: /addbirthday Иванов Иван Иванович 15.08.1990
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
        // Формат: /deletebirthday Иванов Иван Иванович
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

    private String buildFullName(String lastName, String firstName, String middleName) {
        return lastName + " " + firstName + (middleName != null ? " " + middleName : "");
    }

    private void handleCheckBirthdays(long chatId) throws Exception {
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
        // Реализация просмотра всех дней рождения
        sendMessage(chatId, "Функция в разработке...");
    }

    private void sendHelp(long chatId) {
        String helpText = """
                🎂 Бот для уведомлений о днях рождения 🎂
                
                Команды:
                /addbirthday Фамилия Имя Отчество(опц.) dd.MM.yyyy - добавить
                /deletebirthday Фамилия Имя Отчество(опц.) - удалить
                /checkbirthdays - проверить сегодняшние дни рождения
                /listbirthdays - показать все дни рождения
                /help - показать это сообщение
                
                Примеры:
                /addbirthday Иванов Иван Иванович 15.08.1990
                /addbirthday Петров Петр 20.05.1985
                /deletebirthday Иванов Иван Иванович""";
        sendMessage(chatId, helpText);
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для автоматической проверки дней рождения
    public void checkBirthdaysScheduled() throws Exception {
        LocalDate today = LocalDate.now();
        List<String> birthdays = database.getBirthdaysByDate(today);

        if (!birthdays.isEmpty()) {
            StringBuilder message = new StringBuilder("🎉 Сегодня день рождения у:\n");
            for (String name : birthdays) {
                message.append("- ").append(name).append("\n");
            }
            // ID группы для уведомлений
            String groupChatId = "ВАШ_GROUP_CHAT_ID";
            sendMessage(Long.parseLong(groupChatId), message.toString());
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
}

/*@Override
    public String getBotUsername() {
        return "https://t.me/birthday444_bot";
    }

    @Override
    public String getBotToken() {
        return "8121916279:AAFkmyUek7WsV6ib1dQ6ZHWP1sGc-4nOiXo";
    }*/




