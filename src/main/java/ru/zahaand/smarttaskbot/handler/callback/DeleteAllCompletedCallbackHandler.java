package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

/**
 * Handles DELETE_ALL_REQUEST, DELETE_ALL_CONFIRM, and DELETE_ALL_CANCEL callbacks.
 * <p>
 * DELETE_ALL_REQUEST — counts completed tasks; if 0 sends NO_COMPLETED_TASKS;
 * otherwise sends a confirmation prompt with Yes/Cancel buttons.
 * DELETE_ALL_CONFIRM — bulk-deletes all COMPLETED tasks for the user and re-renders the Completed tab.
 * DELETE_ALL_CANCEL  — sends OPERATION_CANCELLED and re-renders the Completed tab unchanged.
 * <p>
 * Обрабатывает колбэки DELETE_ALL_REQUEST, DELETE_ALL_CONFIRM и DELETE_ALL_CANCEL.
 * <p>
 * DELETE_ALL_REQUEST — подсчитывает выполненные задачи; при 0 отправляет NO_COMPLETED_TASKS;
 * иначе — отправляет запрос подтверждения с кнопками «Да» и «Отмена».
 * DELETE_ALL_CONFIRM — массово удаляет все задачи со статусом COMPLETED и перерисовывает вкладку «Выполненные».
 * DELETE_ALL_CANCEL  — отправляет OPERATION_CANCELLED и перерисовывает вкладку «Выполненные» без изменений.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteAllCompletedCallbackHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    /**
     * Dispatches the incoming callback to the appropriate sub-handler based on callback data.
     * <p>
     * Направляет входящий колбэк в нужный обработчик по данным колбэка.
     */
    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final Integer msgId = cq.getMessage().getMessageId();
        final String data = cq.getData();
        final String cbId = cq.getId();

        if (BotConstantsUtils.CB_DELETE_ALL_REQUEST.equals(data)) {
            handleRequest(cbId, userId, chatId);
        } else if (BotConstantsUtils.CB_DELETE_ALL_CONFIRM.equals(data)) {
            handleConfirm(cbId, userId, chatId, msgId);
        } else if (BotConstantsUtils.CB_DELETE_ALL_CANCEL.equals(data)) {
            handleCancel(cbId, userId, chatId, msgId);
        }
    }

    private void handleRequest(String cbId, Long userId, Long chatId) {
        final long count = taskService.countCompleted(userId);
        notificationService.answerCallbackQuery(cbId);
        if (count == 0) {
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.NO_COMPLETED_TASKS, resolveLanguage(userId)));
        } else {
            final String text = messageService.get(MessageKey.DELETE_CONFIRM_ALL, resolveLanguage(userId))
                    .formatted(count);
            notificationService.sendDeleteAllConfirmation(chatId, text, resolveLanguage(userId));
        }
    }

    private void handleConfirm(String cbId, Long userId, Long chatId, Integer msgId) {
        notificationService.answerCallbackQuery(cbId);
        final Language language = resolveLanguage(userId);
        final int deleted = taskService.deleteAllCompleted(userId);
        log.info("DeleteAllCompleted: {} tasks deleted for userId={}", deleted, userId);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.ALL_COMPLETED_DELETED, language));
        final List<TaskDto> remaining = taskService.getCompletedTasks(userId);
        notificationService.editTaskList(chatId, msgId, remaining, TaskStatus.COMPLETED, language);
    }

    private void handleCancel(String cbId, Long userId, Long chatId, Integer msgId) {
        notificationService.answerCallbackQuery(cbId);
        final Language language = resolveLanguage(userId);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.OPERATION_CANCELLED, language));
        final List<TaskDto> tasks = taskService.getCompletedTasks(userId);
        notificationService.editTaskList(chatId, msgId, tasks, TaskStatus.COMPLETED, language);
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
