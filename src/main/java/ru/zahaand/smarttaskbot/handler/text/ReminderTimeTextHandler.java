package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.TimeParserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Handles free-text time input while the user is in ENTERING_REMINDER_TIME state.
 * Parses the input with {@link TimeParserService}, creates the reminder on success,
 * and sends a format hint on failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderTimeTextHandler {

    private final TimeParserService timeParserService;
    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final String text = update.getMessage().getText().trim();

        final Optional<LocalTime> parsed = timeParserService.parse(text);

        if (parsed.isEmpty()) {
            sendHint(chatId, text);
            return;
        }

        final ConversationContext ctx = userStateService.getContext(userId).orElse(null);
        if (ctx == null || ctx.getTaskId() == null || ctx.getDate() == null) {
            log.warn("ReminderTimeTextHandler: missing context for userId={}", userId);
            notificationService.sendMessage(chatId,
                    "Your session has expired. Please start over.");
            userStateService.setState(userId, ConversationState.IDLE);
            return;
        }

        final LocalDate date = LocalDate.parse(ctx.getDate());
        final LocalTime time = parsed.get();

        final TaskDto result = taskService.setReminderFromCalendar(userId, ctx.getTaskId(), date, time);
        notificationService.sendMessage(chatId,
                "⏰ Reminder set for " + ctx.getDate() + " at " + time
                        + " (" + result.getReminderTime() + " local time).");
        userStateService.setState(userId, ConversationState.IDLE);
    }

    private void sendHint(Long chatId, String input) {
        if (timeParserService.isTwelveOClockAmbiguous(input)) {
            notificationService.sendMessage(chatId,
                    "For 12 o'clock use the 24-hour format: 00:00 (midnight) or 12:00 (noon).");
        } else {
            notificationService.sendMessage(chatId,
                    "Could not parse the time. Try: 14:30, 9:00, 21:00");
        }
    }
}
