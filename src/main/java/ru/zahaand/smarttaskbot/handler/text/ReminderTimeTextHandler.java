package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Handles free-text time input while the user is in ENTERING_REMINDER_TIME state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderTimeTextHandler {

    private final TimeParserService timeParserService;
    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final String text = update.getMessage().getText().trim();
        final User user = userService.findById(userId);

        final Optional<LocalTime> parsed = timeParserService.parse(text);

        if (parsed.isEmpty()) {
            sendHint(chatId, text, user);
            return;
        }

        final ConversationContext ctx = userStateService.getContext(userId).orElse(null);
        if (ctx == null || ctx.getTaskId() == null || ctx.getDate() == null) {
            log.warn("ReminderTimeTextHandler: missing context for userId={}", userId);
            notificationService.sendMessage(chatId, messageService.get(MessageKey.SESSION_EXPIRED, user));
            userStateService.setState(userId, ConversationState.IDLE);
            return;
        }

        final LocalDate date = LocalDate.parse(ctx.getDate());
        final LocalTime time = parsed.get();

        final TaskDto result = taskService.setReminderFromCalendar(userId, ctx.getTaskId(), date, time);
        notificationService.sendMessage(chatId,
                messageService.get(MessageKey.TASK_REMINDER_SET, user).formatted(
                        ctx.getDate() + " " + time + " (" + result.getReminderTime() + ")"));
        userStateService.setState(userId, ConversationState.IDLE);
    }

    private void sendHint(Long chatId, String input, User user) {
        if (timeParserService.isTwelveOClockAmbiguous(input)) {
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.TWELVE_OCLOCK_HINT, user));
        } else {
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.INVALID_TIME_FORMAT, user));
        }
    }
}
