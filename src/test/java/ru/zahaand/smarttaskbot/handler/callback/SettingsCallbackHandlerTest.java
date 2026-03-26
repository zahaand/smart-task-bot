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
import org.telegram.telegrambots.meta.api.objects.User;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsCallbackHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private UserStateService userStateService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MessageService messageService;

    @InjectMocks
    private SettingsCallbackHandler handler;

    private static final Long USER_ID = 100L;
    private static final Long CHAT_ID = 200L;
    private static final String CQ_ID = "cq-001";

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
        when(cq.getId()).thenReturn(CQ_ID);
        when(cq.getFrom()).thenReturn(tgUser);
        when(cq.getData()).thenReturn(callbackData);
        when(cq.getMessage()).thenReturn(message);

        final Update update = mock(Update.class);
        when(update.getCallbackQuery()).thenReturn(cq);

        return update;
    }

    @Nested
    class HandleMenu {

        @DisplayName("resets state to IDLE and sends settings menu")
        @Test
        void resetsStateAndSendsMenu() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_MENU);

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendSettingsMenu(CHAT_ID, Language.EN);
        }
    }

    @Nested
    class HandleLanguageRequest {

        @DisplayName("resets state to IDLE and sends language selection keyboard")
        @Test
        void resetsStateAndSendsLanguageKeyboard() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_LANG_REQUEST);

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendSettingsLanguageKeyboard(CHAT_ID, Language.EN);
        }
    }

    @Nested
    class HandleLanguageChange {

        @DisplayName("updates language and sends persistent menu")
        @Test
        void updatesLanguageAndSendsMenu() {
            when(messageService.get(MessageKey.SETTINGS_LANGUAGE_CHANGED, Language.RU))
                    .thenReturn("Язык изменён ✓");
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_LANG + "RU");

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(userService).updateLanguage(USER_ID, Language.RU);
            verify(notificationService).sendPersistentMenu(eq(CHAT_ID), eq("Язык изменён ✓"), eq(Language.RU));
        }
    }

    @Nested
    class HandleTimezoneRequest {

        @DisplayName("sets AWAITING_TIMEZONE state and sends timezone keyboard")
        @Test
        void setsStateAndSendsKeyboard() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_TZ_REQUEST);

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.AWAITING_TIMEZONE);
            verify(notificationService).sendTimezoneKeyboard(CHAT_ID, Language.EN);
        }
    }

    @Nested
    class HandleDeleteRequest {

        @DisplayName("sets CONFIRMING_DELETE_ACCOUNT state and sends confirmation prompt")
        @Test
        void setsStateAndSendsPrompt() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_DEL_REQ);

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.CONFIRMING_DELETE_ACCOUNT);
            verify(notificationService).sendAccountDeleteConfirmation(CHAT_ID, Language.EN);
        }
    }

    @Nested
    class HandleDeleteConfirm {

        @DisplayName("deletes user and sends farewell with start button without setting state")
        @Test
        void deletesUserWithoutSettingState() {
            when(userService.userExists(USER_ID)).thenReturn(true);
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_DEL_CFM);

            handler.handle(update);

            verify(userService).deleteUser(USER_ID);
            verify(notificationService).sendAccountDeleted(CHAT_ID, Language.EN);
            // setState must NOT be called after deleteUser (CASCADE removes user_states row)
            verify(userStateService, never()).setState(eq(USER_ID), any());
        }

        @DisplayName("double-tap: does nothing when user is already deleted")
        @Test
        void doubleTapDoesNothingWhenUserAlreadyDeleted() {
            when(userService.userExists(USER_ID)).thenReturn(false);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_DEL_CFM);

            handler.handle(update);

            verify(userService, never()).deleteUser(any());
            verify(notificationService, never()).sendAccountDeleted(any(), any());
        }
    }

    @Nested
    class HandleDeleteCancel {

        @DisplayName("sets IDLE state and sends persistent menu with cancellation confirmation")
        @Test
        void setsIdleAndSendsMenu() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            when(messageService.get(MessageKey.SETTINGS_DELETE_CANCELLED, Language.EN))
                    .thenReturn("Deletion cancelled.");
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_DEL_CNC);

            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendPersistentMenu(CHAT_ID, "Deletion cancelled.", Language.EN);
        }
    }

    @Nested
    class StaleStateGuard {

        @DisplayName("SETTINGS_MENU still works when user is in CREATING_TASK state")
        @Test
        void menuWorksEvenWhenCreatingTask() {
            when(userService.findById(USER_ID))
                    .thenReturn(registeredUser);
            final Update update = buildUpdate(BotConstantsUtils.CB_SETTINGS_MENU);

            // State is CREATING_TASK from the outside world's perspective;
            // handler resets it to IDLE regardless.
            handler.handle(update);

            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
        }
    }
}
