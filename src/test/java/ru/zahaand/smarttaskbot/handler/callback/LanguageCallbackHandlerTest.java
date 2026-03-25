package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LanguageCallbackHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserStateService userStateService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private LanguageCallbackHandler handler;

    private Update update;
    private CallbackQuery callbackQuery;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long CHAT_ID = 100L;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        callbackQuery = mock(CallbackQuery.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getFrom()).thenReturn(from);
        when(callbackQuery.getId()).thenReturn("cq-id");
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(from.getId()).thenReturn(USER_ID);

        lenient().when(messageService.get(any(ru.zahaand.smarttaskbot.model.MessageKey.class), any(Language.class)))
                .thenReturn("Something went wrong. Please try again.");
    }

    @Nested
    class Handle {

        @DisplayName("updates language to EN, transitions to AWAITING_TIMEZONE, sends timezone keyboard")
        @Test
        void updatesLanguageToEN_andSendsTimezoneKeyboard() {
            when(callbackQuery.getData()).thenReturn("lang:EN");

            handler.handle(update);

            verify(userService).updateLanguage(USER_ID, Language.EN);
            verify(userStateService).setState(USER_ID, ConversationState.AWAITING_TIMEZONE);
            verify(notificationService).sendTimezoneKeyboard(CHAT_ID, Language.EN);
            verify(notificationService).answerCallbackQuery("cq-id");
        }

        @DisplayName("updates language to RU, transitions to AWAITING_TIMEZONE, sends timezone keyboard")
        @Test
        void updatesLanguageToRU_andSendsTimezoneKeyboard() {
            when(callbackQuery.getData()).thenReturn("lang:RU");

            handler.handle(update);

            verify(userService).updateLanguage(USER_ID, Language.RU);
            verify(userStateService).setState(USER_ID, ConversationState.AWAITING_TIMEZONE);
            verify(notificationService).sendTimezoneKeyboard(CHAT_ID, Language.RU);
            verify(notificationService).answerCallbackQuery("cq-id");
        }

        @DisplayName("sends error message for invalid language code, does not update language")
        @Test
        void sendsError_forInvalidLanguageCode() {
            when(callbackQuery.getData()).thenReturn("lang:DE");

            handler.handle(update);

            verify(userService, never()).updateLanguage(any(), any());
            verify(notificationService).sendMessage(eq(CHAT_ID), anyString());
            verify(notificationService).answerCallbackQuery("cq-id");
        }

        @DisplayName("sends error message when user not found (IllegalStateException from updateLanguage)")
        @Test
        void sendsError_whenUserNotFound() {
            when(callbackQuery.getData()).thenReturn("lang:EN");
            doThrow(new IllegalStateException("User not found: 42"))
                    .when(userService).updateLanguage(USER_ID, Language.EN);

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), anyString());
            verify(notificationService).answerCallbackQuery("cq-id");
        }
    }
}
