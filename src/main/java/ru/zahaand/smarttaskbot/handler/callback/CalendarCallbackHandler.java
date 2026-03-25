package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.time.YearMonth;

/**
 * Handles CAL_NAV and CAL_DATE callbacks produced by the inline calendar.
 * <p>
 * CAL_NAV:±1 — navigate one month forward/backward; guards against going
 *              before the current month on the server side as a second defence.
 * CAL_DATE:YYYY-MM-DD — user selected a future date; transition to ENTERING_REMINDER_TIME.
 * <p>
 * Both actions verify that the user is in SELECTING_REMINDER_DATE state.
 * A stale/missing state causes a "session expired" message and a reset to IDLE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarCallbackHandler {

    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final Integer messageId = cq.getMessage().getMessageId();
        final String data = cq.getData();

        if (userStateService.getState(userId) != ConversationState.SELECTING_REMINDER_DATE) {
            notificationService.answerCallbackQuery(cq.getId());
            userStateService.setState(userId, ConversationState.IDLE);
            final User user = findUserSafely(userId);
            notificationService.sendMessage(chatId, messageService.get(MessageKey.SESSION_EXPIRED,
                    user != null ? user.getLanguage() : (Language) null));
            return;
        }

        if (data.startsWith(BotConstants.CB_CAL_NAV)) {
            handleNav(cq.getId(), userId, chatId, messageId, data);
        } else if (data.startsWith(BotConstants.CB_CAL_DATE)) {
            handleDate(cq.getId(), userId, chatId, data);
        }
    }

    private void handleNav(String callbackQueryId, Long userId, Long chatId,
                           Integer messageId, String data) {
        final int delta = Integer.parseInt(data.substring(BotConstants.CB_CAL_NAV.length()));
        final ConversationContext ctx = userStateService.getContext(userId).orElse(null);

        if (ctx == null || ctx.getViewingYear() == null || ctx.getViewingMonth() == null) {
            log.warn("CalendarCallbackHandler: missing context for userId={}", userId);
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        YearMonth target = YearMonth.of(ctx.getViewingYear(), ctx.getViewingMonth()).plusMonths(delta);
        final YearMonth current = YearMonth.now();
        if (target.isBefore(current)) {
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        final ConversationContext updated = ConversationContext.builder()
                .taskId(ctx.getTaskId())
                .viewingYear(target.getYear())
                .viewingMonth(target.getMonthValue())
                .build();
        userStateService.updateContext(userId, updated);
        notificationService.editCalendar(chatId, messageId, target.getYear(), target.getMonthValue());
        notificationService.answerCallbackQuery(callbackQueryId);
    }

    private void handleDate(String callbackQueryId, Long userId, Long chatId, String data) {
        final String dateStr = data.substring(BotConstants.CB_CAL_DATE.length());
        final ConversationContext ctx = userStateService.getContext(userId).orElse(null);

        if (ctx == null || ctx.getTaskId() == null) {
            log.warn("CalendarCallbackHandler: missing taskId in context for userId={}", userId);
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        final ConversationContext next = ConversationContext.builder()
                .taskId(ctx.getTaskId())
                .date(dateStr)
                .build();
        userStateService.setStateWithContext(userId, ConversationState.ENTERING_REMINDER_TIME, next);
        notificationService.answerCallbackQuery(callbackQueryId);

        final User user = findUserSafely(userId);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.ENTER_REMINDER_TIME,
                user != null ? user.getLanguage() : (Language) null));
    }

    private User findUserSafely(Long userId) {
        try {
            return userService.findById(userId);
        } catch (Exception e) {
            return null;
        }
    }
}
