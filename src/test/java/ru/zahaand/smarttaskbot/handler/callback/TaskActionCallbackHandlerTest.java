package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskActionCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;
    @InjectMocks
    TaskActionCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Long TASK_ID = 7L;
    private static final Integer MSG_ID = 55;
    private static final String CB_ID = "cbId";

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        cq = mock(CallbackQuery.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(cq);
        when(cq.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        when(cq.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getMessageId()).thenReturn(MSG_ID);
        when(cq.getId()).thenReturn(CB_ID);
    }

    @Nested
    @DisplayName("TASK_DONE")
    class TaskDone {

        @Test
        @DisplayName("marks task complete and refreshes active task list in place")
        void completesTaskAndRefreshesList() {
            when(cq.getData()).thenReturn(BotConstants.CB_TASK_DONE + TASK_ID);
            when(taskService.completeTask(USER_ID, TASK_ID))
                    .thenReturn(new TaskDto(TASK_ID, "Buy milk", null));
            when(taskService.getActiveTasks(USER_ID)).thenReturn(List.of());

            handler.handle(update);

            verify(taskService).completeTask(USER_ID, TASK_ID);
            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).editTaskList(CHAT_ID, MSG_ID, List.of(), TaskStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("TASK_REMIND")
    class TaskRemind {

        @Test
        @DisplayName("sets state to SELECTING_REMINDER_DATE with taskId in context and sends calendar")
        void setsStateAndSendsCalendar() {
            when(cq.getData()).thenReturn(BotConstants.CB_TASK_REMIND + TASK_ID);

            handler.handle(update);

            ArgumentCaptor<ConversationContext> ctxCaptor = ArgumentCaptor.forClass(ConversationContext.class);
            verify(userStateService).setStateWithContext(
                    eq(USER_ID), eq(ConversationState.SELECTING_REMINDER_DATE), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getTaskId()).isEqualTo(TASK_ID);
            assertThat(ctxCaptor.getValue().getViewingYear()).isNotNull();
            assertThat(ctxCaptor.getValue().getViewingMonth()).isNotNull();

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendCalendar(eq(CHAT_ID), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("TASK_DELETE")
    class TaskDelete {

        @Test
        @DisplayName("sets state to CONFIRMING_DELETE with taskId in context and sends confirmation")
        void setsStateAndSendsConfirmation() {
            when(cq.getData()).thenReturn(BotConstants.CB_TASK_DELETE + TASK_ID);
            when(taskService.getTaskText(USER_ID, TASK_ID)).thenReturn("Buy milk");

            handler.handle(update);

            ArgumentCaptor<ConversationContext> ctxCaptor = ArgumentCaptor.forClass(ConversationContext.class);
            verify(userStateService).setStateWithContext(
                    eq(USER_ID), eq(ConversationState.CONFIRMING_DELETE), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getTaskId()).isEqualTo(TASK_ID);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendDeleteConfirmation(CHAT_ID, TASK_ID, "Buy milk");
        }

        @Test
        @DisplayName("sends \"Task not found.\" when task no longer exists")
        void sendsNotFoundWhenTaskGone() {
            when(cq.getData()).thenReturn(BotConstants.CB_TASK_DELETE + TASK_ID);
            when(taskService.getTaskText(USER_ID, TASK_ID))
                    .thenThrow(new NoSuchElementException("Task #7 not found."));

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("not found"));
            verify(notificationService, never()).sendDeleteConfirmation(any(), any(), any());
        }
    }
}
