package my.ru;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static BirthdayBot bot;

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            bot = new BirthdayBot();
            botsApi.registerBot(bot);

            // Настройка ежедневной проверки в 9:00 утра
            long initialDelay = getInitialDelay();
            scheduler.scheduleAtFixedRate(Main::checkUpcomingBirthdays,
                    initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);

            // Обработчик завершения работы
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

        } catch (Exception e) {
            System.err.println("Ошибка при запуске бота: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void checkUpcomingBirthdays() {
        try {
            LocalDate today = LocalDate.now();
            BirthdayDatabase database = bot.getDatabase();
            List<Long> chatIds = database.getAllChatIds();

            if (chatIds.isEmpty()) return;

            checkBirthdaysForDate(today, "🎉 Сегодня день рождения у:");
            checkBirthdaysForDate(today.plusDays(3), "⏳ Через 3 дня день рождения у:");
            checkBirthdaysForDate(today.plusDays(7), "🗓 Через неделю день рождения у:");

        } catch (Exception e) {
            System.err.println("Ошибка при проверке дней рождения: " + e.getMessage());
        }
    }

    private static void checkBirthdaysForDate(LocalDate date, String messagePrefix) throws SQLException {
        List<Long> chatIds = bot.getDatabase().getAllChatIds();
        String dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM"));

        for (Long chatId : chatIds) {
            List<String> birthdays = bot.getDatabase().getBirthdaysByDateForChat(date, chatId);
            if (birthdays.isEmpty()) continue;

            StringBuilder message = new StringBuilder(messagePrefix);
            if (!date.isEqual(LocalDate.now())) {
                message.append(" (").append(dateStr).append(")");
            }
            message.append(":\n\n");

            birthdays.forEach(name -> message.append("• ").append(name).append("\n"));

            if (date.isEqual(LocalDate.now())) {
                message.append("\n").append(getRandomCongratulation());
            }

            bot.sendMessage(chatId, message.toString());
        }
    }

    private static String getRandomCongratulation() {
        String[] congrats = {
                "Пусть этот день будет наполнен радостью и смехом!",
                "Желаем счастья, здоровья и успехов во всех начинаниях!",
                " Желаю исполнения всех желаний! ",
                " Пусть жизнь будет сладкой, как торт! ",
                " Солнечного настроения круглый год! ",
                " Пусть удача всегда будет рядом! ",
                " Крепкого здоровья и бодрости духа! ",
                " Пусть сбудутся даже самые смелые мечты! ",
                " Тепла в доме и гармонии в семье! ",
                " Ярких впечатлений и незабываемых моментов! ",
                " Легкости на жизненном пути! ",
                " Пусть ангел-хранитель всегда оберегает! ",
                " Улыбок, смеха и детского восторга! ",
                " Пусть сердце будет открыто для чудес! ",
                " Желаю стабильности и уверенности! ",
                " Пусть счастье не знает границ! ",
                " Желаю жить легко и радостно! ",
                " Пусть сбываются даже несбыточные мечты! ",
                " Пусть каждый день будет праздником! ",
                " Успехов во всех начинаниях! ",
                " Пусть жизнь будет вкусной, как торт! ",
                " Желаю душевного тепла и уюта! ",
                " Крепких нервов и стального здоровья! ",
                " Легкого пути к своей мечте! ",
                " Желаю ценить каждый момент! ",
                " Крепкого чая и мягкого пледа! ",
                " Желаю всегда верить в лучшее! ",
                " Пусть фортуна будет на твоей стороне! ",
                " Желаю простого человеческого счастья! ",
                " Теплых встреч и добрых людей вокруг! ",
                " Пусть сердце поет от радости! ",
                " Легкого парения по жизни! ",
                " Желаю жить в гармонии с собой! ",
                " Пусть каждый день приносит подарки! ",
                " Желаю оставаться молодым душой! ",
                " Пусть мечты становятся реальностью! ",
                "Пусть сбудутся все мечты и желания в этот особенный день!"
        };
        return congrats[new Random().nextInt(congrats.length)];
    }

    private static long getInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(21).withMinute(25).withSecond(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).getSeconds();
    }
}