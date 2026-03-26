package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.TaskRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private ReminderService reminderService;

    private static final Long USER_TELEGRAM_ID = 42L;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setTelegramUserId(USER_TELEGRAM_ID);

        lenient().when(userService.findById(anyLong())).thenReturn(testUser);
        lenient().when(messageService.get(any(MessageKey.class), any(User.class))).thenReturn("⏰ Reminder: %s");
    }

    private Task buildTask(long id) {
        Task task = Task.builder()
                .id(id)
                .text("Task " + id)
                .status(TaskStatus.ACTIVE)
                .reminderProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();
        task.setUser(testUser);
        return task;
    }

    @Nested
    class ProcessDueReminders {

        @DisplayName("only passes reminders with reminderTime not after now to initial processing")
        @Test
        void callsFindDueRemindersWithNow() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(taskRepository).findDueReminders(any(Instant.class));
        }

        @DisplayName("only passes retries with reminderRetryAt not after now to retry processing")
        @Test
        void callsFindDueRetriesWithNow() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(taskRepository).findDueRetries(any(Instant.class));
        }

        @DisplayName("processes no tasks when both findDueReminders and findDueRetries return empty")
        @Test
        void doesNothingWhenNoTasksDue() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(notificationService, never()).sendMessage(anyLong(), anyString());
        }

        @DisplayName("sets reminderProcessed true when initial send succeeds")
        @Test
        void setsReminderProcessedOnInitialSuccess() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @DisplayName("sets reminderRetryAt to now+60s when initial send fails")
        @Test
        void setsRetryAtWhenInitialSendFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("network error")).when(notificationService).sendMessage(anyLong(), anyString());

            Instant before = Instant.now();
            reminderService.processDueReminders();
            Instant after = Instant.now();

            assertThat(task.getReminderRetryAt())
                    .isAfterOrEqualTo(before.plusSeconds(60))
                    .isBeforeOrEqualTo(after.plusSeconds(60));
        }

        @DisplayName("does not set reminderProcessed true when initial send fails")
        @Test
        void doesNotSetReminderProcessedWhenInitialSendFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("network error")).when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isFalse();
        }

        @DisplayName("sets reminderProcessed true when retry send succeeds")
        @Test
        void setsReminderProcessedOnRetrySuccess() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task));

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @DisplayName("sets reminderProcessed true and discards reminder when retry send fails")
        @Test
        void discardsReminderWhenRetryFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task));
            doThrow(new RuntimeException("network error")).when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @DisplayName("continues processing remaining initial reminders after one send failure")
        @Test
        void continuesAfterInitialSendFailure() {
            Task failingTask = buildTask(1L);
            Task succeedingTask = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(failingTask, succeedingTask));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("fail")).doNothing()
                    .when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            assertThat(succeedingTask.isReminderProcessed()).isTrue();
        }

        @DisplayName("continues processing remaining retries after one retry send failure")
        @Test
        void continuesAfterRetrySendFailure() {
            Task failingTask = buildTask(1L);
            Task succeedingTask = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(failingTask, succeedingTask));
            doThrow(new RuntimeException("fail")).doNothing()
                    .when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            assertThat(succeedingTask.isReminderProcessed()).isTrue();
        }

        @DisplayName("calls saveAll after initial pass with all tasks regardless of send outcome")
        @Test
        void callsSaveAllAfterInitialPass() {
            Task task1 = buildTask(1L);
            Task task2 = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task1, task2));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("fail")).doNothing()
                    .when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
            verify(taskRepository, atLeastOnce()).saveAll(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(task1, task2));
        }

        @DisplayName("calls saveAll after retry pass with all tasks regardless of send outcome")
        @Test
        void callsSaveAllAfterRetryPass() {
            Task task1 = buildTask(1L);
            Task task2 = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task1, task2));
            doThrow(new RuntimeException("fail")).doNothing()
                    .when(notificationService).sendMessage(anyLong(), anyString());

            reminderService.processDueReminders();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
            verify(taskRepository, atLeastOnce()).saveAll(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(task1, task2));
        }
    }
}
