package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimezoneCallbackHandler {

    private final UserService userService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();

        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramUserId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String data = callbackQuery.getData();

        if (data == null || !data.startsWith(BotConstants.TZ_CALLBACK_PREFIX)) {
            notificationService.sendMessage(chatId, "Something went wrong. Please send /start to try again.");
            return;
        }

        String timezone = data.substring(BotConstants.TZ_CALLBACK_PREFIX.length());

        if (!BotConstants.VALID_TIMEZONES.contains(timezone)) {
            notificationService.sendMessage(chatId, "Something went wrong. Please send /start to try again.");
            return;
        }

        userService.register(telegramUserId, username, timezone);
        log.info("User registered: userId={}, timezone={}", telegramUserId, timezone);
        notificationService.sendPersistentMenu(chatId,
                "Timezone set: " + timezone + " ✓\nYou're all set! Use the buttons below to get started.");
    }
}
