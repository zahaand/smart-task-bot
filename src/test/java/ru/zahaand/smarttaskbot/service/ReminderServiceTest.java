package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.repository.TaskRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReminderService reminderService;

    private Task buildTask(long id) {
        return Task.builder()
                .id(id)
                .text("Task " + id)
                .status(TaskStatus.ACTIVE)
                .reminderProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    class ProcessDueReminders {

        @Test
        @DisplayName("only passes reminders with reminderTime not after now to initial processing")
        void callsFindDueRemindersWithNow() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(taskRepository).findDueReminders(any(Instant.class));
        }

        @Test
        @DisplayName("only passes retries with reminderRetryAt not after now to retry processing")
        void callsFindDueRetriesWithNow() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(taskRepository).findDueRetries(any(Instant.class));
        }

        @Test
        @DisplayName("processes no tasks when both findDueReminders and findDueRetries return empty")
        void doesNothingWhenNoTasksDue() {
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            verify(notificationService, never()).sendReminder(any());
        }

        @Test
        @DisplayName("sets reminderProcessed true when initial send succeeds")
        void setsReminderProcessedOnInitialSuccess() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @Test
        @DisplayName("sets reminderRetryAt to now+60s when initial send fails")
        void setsRetryAtWhenInitialSendFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("network error")).when(notificationService).sendReminder(task);

            Instant before = Instant.now();
            reminderService.processDueReminders();
            Instant after = Instant.now();

            assertThat(task.getReminderRetryAt())
                    .isAfterOrEqualTo(before.plusSeconds(60))
                    .isBeforeOrEqualTo(after.plusSeconds(60));
        }

        @Test
        @DisplayName("does not set reminderProcessed true when initial send fails")
        void doesNotSetReminderProcessedWhenInitialSendFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("network error")).when(notificationService).sendReminder(task);

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isFalse();
        }

        @Test
        @DisplayName("sets reminderProcessed true when retry send succeeds")
        void setsReminderProcessedOnRetrySuccess() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task));

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @Test
        @DisplayName("sets reminderProcessed true and discards reminder when retry send fails")
        void discardsReminderWhenRetryFails() {
            Task task = buildTask(1L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task));
            doThrow(new RuntimeException("network error")).when(notificationService).sendReminder(task);

            reminderService.processDueReminders();

            assertThat(task.isReminderProcessed()).isTrue();
        }

        @Test
        @DisplayName("continues processing remaining initial reminders after one send failure")
        void continuesAfterInitialSendFailure() {
            Task failingTask = buildTask(1L);
            Task succeedingTask = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(failingTask, succeedingTask));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("fail")).when(notificationService).sendReminder(failingTask);

            reminderService.processDueReminders();

            assertThat(succeedingTask.isReminderProcessed()).isTrue();
        }

        @Test
        @DisplayName("continues processing remaining retries after one retry send failure")
        void continuesAfterRetrySendFailure() {
            Task failingTask = buildTask(1L);
            Task succeedingTask = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(failingTask, succeedingTask));
            doThrow(new RuntimeException("fail")).when(notificationService).sendReminder(failingTask);

            reminderService.processDueReminders();

            assertThat(succeedingTask.isReminderProcessed()).isTrue();
        }

        @Test
        @DisplayName("calls saveAll after initial pass with all tasks regardless of send outcome")
        void callsSaveAllAfterInitialPass() {
            Task task1 = buildTask(1L);
            Task task2 = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of(task1, task2));
            when(taskRepository.findDueRetries(any())).thenReturn(List.of());
            doThrow(new RuntimeException("fail")).when(notificationService).sendReminder(task1);

            reminderService.processDueReminders();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
            verify(taskRepository, atLeastOnce()).saveAll(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(task1, task2));
        }

        @Test
        @DisplayName("calls saveAll after retry pass with all tasks regardless of send outcome")
        void callsSaveAllAfterRetryPass() {
            Task task1 = buildTask(1L);
            Task task2 = buildTask(2L);
            when(taskRepository.findDueReminders(any())).thenReturn(List.of());
            when(taskRepository.findDueRetries(any())).thenReturn(List.of(task1, task2));
            doThrow(new RuntimeException("fail")).when(notificationService).sendReminder(task1);

            reminderService.processDueReminders();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Task>> captor = ArgumentCaptor.forClass(List.class);
            verify(taskRepository, atLeastOnce()).saveAll(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(list -> assertThat(list).containsExactlyInAnyOrder(task1, task2));
        }
    }
}
