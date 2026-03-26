package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.*;

/**
 * Handles CONFIRM_DELETE and CONFIRM_CANCEL callbacks produced by the delete-confirmation prompt.
 * <p>
 * CONFIRM_DELETE:<taskId> — deletes the task and notifies the user.
 * CONFIRM_CANCEL          — cancels the deletion and resets state to IDLE.
 * <p>
 * Both actions verify that the user is in CONFIRMING_DELETE state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteConfirmCallbackHandler {

    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final String data = cq.getData();

        if (userStateService.getState(userId) != ConversationState.CONFIRMING_DELETE) {
            notificationService.answerCallbackQuery(cq.getId());
            userStateService.setState(userId, ConversationState.IDLE);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.SESSION_EXPIRED, resolveLanguage(userId)));
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_CONFIRM_DELETE)) {
            handleConfirmDelete(cq.getId(), userId, chatId, data);
        } else if (data.equals(BotConstantsUtils.CB_CONFIRM_CANCEL)) {
            handleConfirmCancel(cq.getId(), userId, chatId);
        }
    }

    private void handleConfirmDelete(String callbackQueryId, Long userId, Long chatId, String data) {
        final ConversationContext ctx = userStateService.getContext(userId).orElse(null);

        if (ctx == null || ctx.getTaskId() == null) {
            log.warn("DeleteConfirmCallbackHandler: missing taskId in context for userId={}", userId);
            notificationService.answerCallbackQuery(callbackQueryId);
            userStateService.setState(userId, ConversationState.IDLE);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.SESSION_EXPIRED, resolveLanguage(userId)));
            return;
        }

        notificationService.answerCallbackQuery(callbackQueryId);
        userStateService.setState(userId, ConversationState.IDLE);

        final int deleted = taskService.deleteTask(userId, ctx.getTaskId());
        final Language language = resolveLanguage(userId);
        if (deleted == 0) {
            log.warn("DeleteConfirmCallbackHandler: task #{} already gone for userId={}", ctx.getTaskId(), userId);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.TASK_ALREADY_DELETED, language));
        } else {
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.TASK_DELETED, language));
        }
    }

    private void handleConfirmCancel(String callbackQueryId, Long userId, Long chatId) {
        notificationService.answerCallbackQuery(callbackQueryId);
        userStateService.setState(userId, ConversationState.IDLE);
        notificationService.sendMessage(chatId,
                messageService.get(MessageKey.OPERATION_CANCELLED, resolveLanguage(userId)));
    }

    private Language resolveLanguage(Long userId) {
        try {
            final User user = userService.findById(userId);
            return user.getLanguage();
        } catch (Exception e) {
            return null;
        }
    }
}
