package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.BotException;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Handles {@code TASK_DETAIL:*} inline button callbacks.
 * Loads the task by ID and sends a detail message with action buttons.
 * <p>
 * Обрабатывает колбэки {@code TASK_DETAIL:*}.
 * Загружает задачу по ID и отправляет детальное сообщение с кнопками действий.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDetailCallbackHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    /**
     * Processes a {@code TASK_DETAIL:*} callback: loads the task and sends the detail view.
     * <p>
     * Обрабатывает колбэк {@code TASK_DETAIL:*}: загружает задачу и отправляет детальный просмотр.
     */
    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final String data = cq.getData();

        final Long taskId = Long.parseLong(data.substring(BotConstantsUtils.CB_TASK_DETAIL.length()));
        notificationService.answerCallbackQuery(cq.getId());

        final Language language = resolveLanguage(userId);

        try {
            final TaskDto task = taskService.getTask(userId, taskId);
            notificationService.sendTaskDetail(chatId, task, language);
        } catch (BotException e) {
            log.warn("TASK_DETAIL: task #{} not found for userId={}", taskId, userId);
            notificationService.sendMessage(chatId, messageService.get(MessageKey.SOMETHING_WENT_WRONG, language));
        }
    }

    private Language resolveLanguage(Long userId) {
        try {
            return userService.findById(userId).getLanguage();
        } catch (Exception e) {
            return null;
        }
    }
}
