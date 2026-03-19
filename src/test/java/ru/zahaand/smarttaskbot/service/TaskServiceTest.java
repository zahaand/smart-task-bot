package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.TaskRepository;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TaskService taskService;

    private static final Long USER_ID = 42L;
    private static final Long TASK_ID = 7L;

    private User buildUser() {
        return User.builder()
                .telegramUserId(USER_ID)
                .username("alice")
                .timezone("Europe/Moscow")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Task buildTask(TaskStatus status) {
        return Task.builder()
                .id(TASK_ID)
                .user(buildUser())
                .text("Buy milk")
                .status(status)
                .reminderProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    class CreateTask {

        @Test
        @DisplayName("creates task and returns DTO for valid input")
        void createsTaskAndReturnsDto() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));
            Task saved = buildTask(TaskStatus.ACTIVE);
            when(taskRepository.save(any())).thenReturn(saved);

            TaskDto result = taskService.createTask(USER_ID, "Buy milk");

            assertThat(result.getId()).isEqualTo(TASK_ID);
            assertThat(result.getText()).isEqualTo("Buy milk");
            assertThat(result.getReminderTime()).isNull();
        }

        @ParameterizedTest
        @MethodSource("ru.zahaand.smarttaskbot.service.TaskServiceTest#blankTexts")
        @DisplayName("throws IllegalArgumentException for blank or null text")
        void throwsForBlankOrNullText(String text) {
            assertThatThrownBy(() -> taskService.createTask(USER_ID, text))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for text exceeding 500 characters")
        void throwsForTextOver500Chars() {
            String longText = "x".repeat(501);
            assertThatThrownBy(() -> taskService.createTask(USER_ID, longText))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when user is not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.createTask(USER_ID, "Buy milk"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class GetActiveTasks {

        @Test
        @DisplayName("returns empty list when no active tasks exist for user")
        void returnsEmptyListWhenNoActiveTasks() {
            when(userService.getTimezone(USER_ID)).thenReturn("Europe/Moscow");
            when(taskRepository.findByUserTelegramUserIdAndStatus(USER_ID, TaskStatus.ACTIVE))
                    .thenReturn(List.of());

            List<TaskDto> result = taskService.getActiveTasks(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns DTOs with formatted reminder times for active tasks")
        void returnsDtosWithFormattedReminderTimes() {
            when(userService.getTimezone(USER_ID)).thenReturn("Europe/Moscow");
            Task task = buildTask(TaskStatus.ACTIVE);
            task.setReminderTime(Instant.parse("2026-03-25T06:00:00Z")); // 09:00 Moscow (UTC+3)
            when(taskRepository.findByUserTelegramUserIdAndStatus(USER_ID, TaskStatus.ACTIVE))
                    .thenReturn(List.of(task));

            List<TaskDto> result = taskService.getActiveTasks(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReminderTime()).isEqualTo("25.03.2026 09:00");
        }
    }

    @Nested
    class SetReminder {

        @Test
        @DisplayName("sets reminder and returns DTO with formatted time for valid input")
        void setsReminderAndReturnsDto() {
            Task task = buildTask(TaskStatus.ACTIVE);
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.of(task));
            when(userService.getTimezone(USER_ID)).thenReturn("Europe/Moscow");
            when(taskRepository.save(any())).thenReturn(task);

            TaskDto result = taskService.setReminder(USER_ID, TASK_ID, "25.03.2026 09:00");

            assertThat(result.getReminderTime()).isEqualTo("25.03.2026 09:00");
            verify(taskRepository).save(task);
        }

        @Test
        @DisplayName("throws NoSuchElementException when task not found for user")
        void throwsWhenTaskNotFound() {
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.setReminder(USER_ID, TASK_ID, "25.03.2026 09:00"))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when task status is COMPLETED")
        void throwsWhenTaskIsCompleted() {
            Task task = buildTask(TaskStatus.COMPLETED);
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.of(task));

            assertThatThrownBy(() -> taskService.setReminder(USER_ID, TASK_ID, "25.03.2026 09:00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws DateTimeParseException for invalid datetime string")
        void throwsForInvalidDatetimeString() {
            Task task = buildTask(TaskStatus.ACTIVE);
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.of(task));
            when(userService.getTimezone(USER_ID)).thenReturn("Europe/Moscow");

            assertThatThrownBy(() -> taskService.setReminder(USER_ID, TASK_ID, "not-a-date"))
                    .isInstanceOf(DateTimeParseException.class);
        }
    }

    @Nested
    class CompleteTask {

        @Test
        @DisplayName("marks task COMPLETED and returns DTO for valid task")
        void marksTaskCompletedAndReturnsDto() {
            Task task = buildTask(TaskStatus.ACTIVE);
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.of(task));
            when(taskRepository.save(any())).thenReturn(task);

            TaskDto result = taskService.completeTask(USER_ID, TASK_ID);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.getId()).isEqualTo(TASK_ID);
        }

        @Test
        @DisplayName("throws NoSuchElementException when task not found for user")
        void throwsWhenTaskNotFound() {
            when(taskRepository.findByIdAndUserTelegramUserId(TASK_ID, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.completeTask(USER_ID, TASK_ID))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    static Stream<String> blankTexts() {
        return Stream.of("", "   ", null);
    }
}
