package my.ru;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            BirthdayBot bot = new BirthdayBot();
            botsApi.registerBot(bot);

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            long initialDelay = getInitialDelay();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    bot.handleCheckBirthdays(1770716295);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
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
}