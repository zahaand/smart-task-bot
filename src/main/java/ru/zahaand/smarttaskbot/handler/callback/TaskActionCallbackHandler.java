package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.time.LocalDate;
import java.util.List;

/**
 * Handles TASK_DONE, TASK_REMIND, and TASK_DELETE inline button callbacks.
 * Built incrementally across phases: TASK_DONE here (Phase 4),
 * TASK_REMIND in Phase 5 (T026), TASK_DELETE in Phase 6 (T032).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskActionCallbackHandler {

    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final Integer messageId = cq.getMessage().getMessageId();
        final String data = cq.getData();

        if (data.startsWith(BotConstants.CB_TASK_REMIND)) {
            handleTaskRemind(cq.getId(), userId, chatId, data);
            return;
        }

        if (data.startsWith(BotConstants.CB_TASK_DONE)) {
            handleTaskDone(cq.getId(), userId, chatId, messageId, data);
            return;
        }

        // TASK_DELETE wired in Phase 6 (T032)
        log.warn("TaskActionCallbackHandler: unhandled callback prefix — {}", data);
        notificationService.answerCallbackQuery(cq.getId());
    }

    private void handleTaskRemind(String callbackQueryId, Long userId, Long chatId, String data) {
        final Long taskId = Long.parseLong(data.substring(BotConstants.CB_TASK_REMIND.length()));
        notificationService.answerCallbackQuery(callbackQueryId);

        final LocalDate today = LocalDate.now();
        final ConversationContext ctx = ConversationContext.builder()
                .taskId(taskId)
                .viewingYear(today.getYear())
                .viewingMonth(today.getMonthValue())
                .build();
        userStateService.setStateWithContext(userId, ConversationState.SELECTING_REMINDER_DATE, ctx);
        notificationService.sendCalendar(chatId, today.getYear(), today.getMonthValue());
    }

    private void handleTaskDone(String callbackQueryId, Long userId, Long chatId,
                                Integer messageId, String data) {
        final Long taskId = Long.parseLong(data.substring(BotConstants.CB_TASK_DONE.length()));
        taskService.completeTask(userId, taskId);
        notificationService.answerCallbackQuery(callbackQueryId);

        final List<TaskDto> activeTasks = taskService.getActiveTasks(userId);
        notificationService.editTaskList(chatId, messageId, activeTasks, TaskStatus.ACTIVE);
    }
}
