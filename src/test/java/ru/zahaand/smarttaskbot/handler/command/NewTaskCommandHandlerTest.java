package ru.zahaand.smarttaskbot.handler.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.BotException;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewTaskCommandHandlerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private NewTaskCommandHandler handler;

    private Update update;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long CHAT_ID = 100L;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);

        User user = new User();
        user.setLanguage(Language.EN);
        lenient().when(userService.findById(USER_ID)).thenReturn(user);
        lenient().when(messageService.get(any(MessageKey.class), any(User.class))).thenAnswer(inv -> {
            MessageKey key = inv.getArgument(0);
            return switch (key) {
                case TASK_TEXT_EMPTY -> "Task text cannot be empty.";
                case TASK_TEXT_TOO_LONG -> "Task text is too long (max 500 characters).";
                default -> key.name();
            };
        });
    }

    @Nested
    class Handle {

        @DisplayName("calls taskService and sends success message for valid input")
        @Test
        void sendsSuccessMessageForValidInput() {
            when(message.getText()).thenReturn("/newtask Buy milk");
            when(taskService.createTask(USER_ID, "Buy milk"))
                    .thenReturn(new TaskDto(7L, "Buy milk", null));

            handler.handle(update);

            verify(notificationService).sendTaskCreatedWithActions(CHAT_ID, 7L, "Buy milk", Language.EN);
        }

        @ParameterizedTest
        @MethodSource("ru.zahaand.smarttaskbot.handler.command.NewTaskCommandHandlerTest#blankInputMessages")
        @DisplayName("forwards error message to user when taskService throws BotException for blank input")
        void forwardsErrorForBlankInput(String messageText) {
            when(message.getText()).thenReturn(messageText);
            when(taskService.createTask(eq(USER_ID), any()))
                    .thenThrow(new BotException(MessageKey.TASK_TEXT_EMPTY));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("cannot be empty"));
        }

        @DisplayName("forwards error message to user when taskService throws BotException for text over 500 chars")
        @Test
        void forwardsErrorForTooLongText() {
            String longText = "x".repeat(501);
            when(message.getText()).thenReturn("/newtask " + longText);
            when(taskService.createTask(eq(USER_ID), eq(longText)))
                    .thenThrow(new BotException(MessageKey.TASK_TEXT_TOO_LONG));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("too long"));
        }
    }

    static Stream<String> blankInputMessages() {
        return Stream.of("/newtask", "/newtask ", "/newtask   ");
    }
}
