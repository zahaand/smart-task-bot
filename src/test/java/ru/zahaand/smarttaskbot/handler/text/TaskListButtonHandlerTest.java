package ru.zahaand.smarttaskbot.handler.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskListButtonHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @InjectMocks
    TaskListButtonHandler handler;

    private Update update;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User from =
                mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("fetches active tasks and sends task list")
    void fetchesActiveTasksAndSendsList() {
        List<TaskDto> tasks = List.of(new TaskDto(1L, "Buy milk", null));
        User user = mock(User.class);
        when(user.getLanguage()).thenReturn(Language.EN);
        when(userService.findById(USER_ID)).thenReturn(user);
        when(taskService.getActiveTasks(USER_ID)).thenReturn(tasks);

        handler.handle(update);

        verify(taskService).getActiveTasks(USER_ID);
        verify(notificationService).sendTaskList(CHAT_ID, tasks, TaskStatus.ACTIVE, Language.EN);
    }
}
