package ru.zahaand.smarttaskbot.handler.callback;

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
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimezoneCallbackHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private UserStateService userStateService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MessageService messageService;

    @InjectMocks
    private TimezoneCallbackHandler handler;

    private static final Long USER_ID = 55L;
    private static final Long CHAT_ID = 66L;

    private ru.zahaand.smarttaskbot.model.User registeredUser;

    @BeforeEach
    void setUp() {
        registeredUser = new ru.zahaand.smarttaskbot.model.User();
        registeredUser.setTelegramUserId(USER_ID);
        registeredUser.setLanguage(Language.EN);
        registeredUser.setTimezone("Europe/Moscow");
    }

    private Update buildUpdate(String callbackData) {
        final User tgUser = new User();
        tgUser.setId(USER_ID);

        final Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(CHAT_ID);

        final CallbackQuery cq = mock(CallbackQuery.class);
        when(cq.getFrom()).thenReturn(tgUser);
        when(cq.getData()).thenReturn(callbackData);
        when(cq.getMessage()).thenReturn(message);

        final Update update = mock(Update.class);
        when(update.getCallbackQuery()).thenReturn(cq);

        return update;
    }

    static Stream<String> validTimezones() {
        return BotConstantsUtils.VALID_TIMEZONES.stream();
    }

    @Nested
    class RegistrationContext {

        @DisplayName("sets timezone + IDLE state + sends persistent menu with TIMEZONE_CONFIRMED text")
        @ParameterizedTest(name = "timezone={0}")
        @MethodSource("ru.zahaand.smarttaskbot.handler.callback.TimezoneCallbackHandlerTest#validTimezones")
        void setsTimezoneAndSendsRegistrationConfirm(String timezone) {
            // User is NOT registered yet (isRegistered returns false)
            when(userService.isRegistered(USER_ID)).thenReturn(false);
            when(userService.findById(USER_ID)).thenReturn(registeredUser);
            when(messageService.get(eq(MessageKey.TIMEZONE_CONFIRMED), any(ru.zahaand.smarttaskbot.model.User.class)))
                    .thenReturn("Timezone set: %s ✓\nYou're all set!");
            final Update update = buildUpdate(BotConstantsUtils.CB_TZ + timezone);

            handler.handle(update);

            verify(userService).updateTimezone(USER_ID, timezone);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendPersistentMenu(eq(CHAT_ID), any(), eq(Language.EN));
        }
    }

    @Nested
    class SettingsContext {

        @DisplayName("sets timezone + IDLE state + sends persistent menu with SETTINGS_TIMEZONE_CHANGED text")
        @ParameterizedTest(name = "timezone={0}")
        @MethodSource("ru.zahaand.smarttaskbot.handler.callback.TimezoneCallbackHandlerTest#validTimezones")
        void setsTimezoneAndSendsSettingsConfirm(String timezone) {
            // User IS registered (isRegistered returns true → settings context)
            when(userService.isRegistered(USER_ID)).thenReturn(true);
            when(userService.findById(USER_ID)).thenReturn(registeredUser);
            when(messageService.get(eq(MessageKey.SETTINGS_TIMEZONE_CHANGED), any(ru.zahaand.smarttaskbot.model.User.class)))
                    .thenReturn("Timezone changed to %s ✓");
            final Update update = buildUpdate(BotConstantsUtils.CB_TZ + timezone);

            handler.handle(update);

            verify(userService).updateTimezone(USER_ID, timezone);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendPersistentMenu(eq(CHAT_ID), any(), eq(Language.EN));
        }
    }

    @Nested
    class InvalidData {

        @DisplayName("sends SOMETHING_WENT_WRONG for an unknown timezone value")
        @Test
        void unknownTimezoneValueSendsError() {
            final Update update = buildUpdate(BotConstantsUtils.CB_TZ + "Invalid/Zone");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), any());
            verify(userService, never()).updateTimezone(any(), any());
        }
    }
}
