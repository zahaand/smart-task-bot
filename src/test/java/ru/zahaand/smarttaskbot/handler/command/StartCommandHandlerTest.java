package ru.zahaand.smarttaskbot.handler.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.handler.RegistrationGuard;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RegistrationGuard registrationGuard;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private StartCommandHandler handler;

    private Update update;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long CHAT_ID = 100L;
    private static final Long USER_ID = 42L;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        when(from.getUserName()).thenReturn(USERNAME);
    }

    @Nested
    class Handle {

        @DisplayName("creates partial user and sends language keyboard for new user")
        @Test
        void createsPartialUserAndSendsLanguageKeyboard_forNewUser() {
            when(userService.isRegistered(USER_ID)).thenReturn(false);
            when(userService.userExists(USER_ID)).thenReturn(false);

            handler.handle(update);

            verify(userService).createPartialUser(USER_ID, USERNAME);
            verify(notificationService).sendLanguageKeyboard(CHAT_ID);
            verify(registrationGuard, never()).checkAndRoute(any(), any());
        }

        @DisplayName("sends ALREADY_REGISTERED in user language and persistent menu for returning user")
        @Test
        void sendsAlreadyRegisteredAndPersistentMenu_forReturningUser() {
            final User user = new User();
            user.setLanguage(Language.RU);
            when(userService.isRegistered(USER_ID)).thenReturn(true);
            when(userService.findById(USER_ID)).thenReturn(user);
            when(messageService.get(MessageKey.ALREADY_REGISTERED, user))
                    .thenReturn("Вы уже зарегистрированы. Используйте кнопки ниже.");

            handler.handle(update);

            verify(notificationService).sendPersistentMenu(eq(CHAT_ID), anyString(), eq(Language.RU));
            verify(userService, never()).createPartialUser(any(), any());
        }

        @DisplayName("delegates to RegistrationGuard for mid-registration user")
        @Test
        void delegatesToRegistrationGuard_forMidRegistrationUser() {
            when(userService.isRegistered(USER_ID)).thenReturn(false);
            when(userService.userExists(USER_ID)).thenReturn(true);

            handler.handle(update);

            verify(registrationGuard).checkAndRoute(eq(update), any());
            verify(userService, never()).createPartialUser(any(), any());
            verify(notificationService, never()).sendLanguageKeyboard(any());
        }
    }
}
