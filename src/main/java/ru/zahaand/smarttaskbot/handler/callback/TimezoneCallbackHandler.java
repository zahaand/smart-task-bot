package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class TimezoneCallbackHandler {

    private static final String TZ_PREFIX = "tz:";

    private static final Set<String> VALID_TIMEZONES = Set.of(
            "Europe/Kaliningrad",
            "Europe/Moscow",
            "Asia/Yekaterinburg",
            "Asia/Novosibirsk",
            "Asia/Vladivostok"
    );

    private final UserService userService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();

        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramUserId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String data = callbackQuery.getData();

        if (data == null || !data.startsWith(TZ_PREFIX)) {
            notificationService.sendMessage(chatId, "Something went wrong. Please send /start to try again.");
            return;
        }

        String timezone = data.substring(TZ_PREFIX.length());

        if (!VALID_TIMEZONES.contains(timezone)) {
            notificationService.sendMessage(chatId, "Something went wrong. Please send /start to try again.");
            return;
        }

        userService.register(telegramUserId, username, timezone);
        notificationService.sendMessage(chatId,
                "Timezone set: " + timezone + " ✓\nYou're all set! Use /help to see available commands.");
    }
}
