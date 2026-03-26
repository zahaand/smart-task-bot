package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTest {

    private final MessageService service = new MessageService();

    private User userWith(Language language) {
        User user = new User();
        user.setLanguage(language);
        return user;
    }

    @Nested
    class GetByKeyAndLanguage {

        @DisplayName("returns English string when language is EN")
        @Test
        void returnsEnglish_whenLanguageIsEN() {
            String result = service.get(MessageKey.SELECT_TIMEZONE, Language.EN);
            assertThat(result).isEqualTo("Please select your timezone:");
        }

        @DisplayName("returns Russian string when language is RU")
        @Test
        void returnsRussian_whenLanguageIsRU() {
            String result = service.get(MessageKey.SELECT_TIMEZONE, Language.RU);
            assertThat(result).isEqualTo("Выберите часовой пояс:");
        }

        @DisplayName("falls back to English when language is null")
        @Test
        void fallsBackToEnglish_whenLanguageIsNull() {
            String result = service.get(MessageKey.SELECT_TIMEZONE, (Language) null);
            assertThat(result).isEqualTo("Please select your timezone:");
        }

        @DisplayName("returns non-null non-blank English for every MessageKey constant")
        @ParameterizedTest
        @EnumSource(MessageKey.class)
        void allKeysHaveNonBlankEnglishString(MessageKey key) {
            String result = service.get(key, Language.EN);
            assertThat(result)
                    .as("EN string for %s must not be blank", key)
                    .isNotBlank();
        }

        @DisplayName("returns non-null non-blank Russian for every MessageKey constant")
        @ParameterizedTest
        @EnumSource(MessageKey.class)
        void allKeysHaveNonBlankRussianString(MessageKey key) {
            String result = service.get(key, Language.RU);
            assertThat(result)
                    .as("RU string for %s must not be blank", key)
                    .isNotBlank();
        }

        @DisplayName("ALREADY_REGISTERED differs between EN and RU")
        @Test
        void alreadyRegistered_differsBetweenLanguages() {
            String en = service.get(MessageKey.ALREADY_REGISTERED, Language.EN);
            String ru = service.get(MessageKey.ALREADY_REGISTERED, Language.RU);
            assertThat(en).isNotEqualTo(ru);
        }

        @DisplayName("TIMEZONE_CONFIRMED contains %s placeholder in both languages")
        @Test
        void timezoneConfirmed_containsPlaceholder() {
            assertThat(service.get(MessageKey.TIMEZONE_CONFIRMED, Language.EN)).contains("%s");
            assertThat(service.get(MessageKey.TIMEZONE_CONFIRMED, Language.RU)).contains("%s");
        }

        @DisplayName("REMINDER_NOTIFICATION contains %s placeholder in both languages")
        @Test
        void reminderNotification_containsPlaceholder() {
            assertThat(service.get(MessageKey.REMINDER_NOTIFICATION, Language.EN)).contains("%s");
            assertThat(service.get(MessageKey.REMINDER_NOTIFICATION, Language.RU)).contains("%s");
        }

        @DisplayName("DELETE_CONFIRM_ALL contains %d placeholder in both languages")
        @Test
        void deleteConfirmAll_containsCountPlaceholder() {
            assertThat(service.get(MessageKey.DELETE_CONFIRM_ALL, Language.EN)).contains("%d");
            assertThat(service.get(MessageKey.DELETE_CONFIRM_ALL, Language.RU)).contains("%d");
        }

        @DisplayName("TASK_DELETE_CONFIRM contains %d and %s placeholders in both languages")
        @Test
        void taskDeleteConfirm_containsIdAndTextPlaceholders() {
            assertThat(service.get(MessageKey.TASK_DELETE_CONFIRM, Language.EN)).contains("%d").contains("%s");
            assertThat(service.get(MessageKey.TASK_DELETE_CONFIRM, Language.RU)).contains("%d").contains("%s");
        }

        @DisplayName("TASKS_TRUNCATED contains %d placeholder in both languages")
        @Test
        void tasksTruncated_containsCountPlaceholder() {
            assertThat(service.get(MessageKey.TASKS_TRUNCATED, Language.EN)).contains("%d");
            assertThat(service.get(MessageKey.TASKS_TRUNCATED, Language.RU)).contains("%d");
        }

        @DisplayName("BTN_LANG_EN and BTN_LANG_RU are identical in both languages (bilingual by design)")
        @Test
        void langButtons_areSameInBothLanguages() {
            assertThat(service.get(MessageKey.BTN_LANG_EN, Language.EN))
                    .isEqualTo(service.get(MessageKey.BTN_LANG_EN, Language.RU));
            assertThat(service.get(MessageKey.BTN_LANG_RU, Language.EN))
                    .isEqualTo(service.get(MessageKey.BTN_LANG_RU, Language.RU));
        }

        @DisplayName("TASKS_EMPTY_ACTIVE and TASKS_EMPTY_COMPLETED differ between EN and RU")
        @Test
        void emptyStateMessages_differBetweenLanguages() {
            assertThat(service.get(MessageKey.TASKS_EMPTY_ACTIVE, Language.EN))
                    .isNotEqualTo(service.get(MessageKey.TASKS_EMPTY_ACTIVE, Language.RU));
            assertThat(service.get(MessageKey.TASKS_EMPTY_COMPLETED, Language.EN))
                    .isNotEqualTo(service.get(MessageKey.TASKS_EMPTY_COMPLETED, Language.RU));
        }
    }

    @Nested
    class GetByKeyAndUser {

        @DisplayName("returns English string for user with language EN")
        @Test
        void returnsEnglish_forEnUser() {
            User user = userWith(Language.EN);
            String result = service.get(MessageKey.ALREADY_REGISTERED, user);
            assertThat(result).isEqualTo("You are already registered. Use the buttons below.");
        }

        @DisplayName("returns Russian string for user with language RU")
        @Test
        void returnsRussian_forRuUser() {
            User user = userWith(Language.RU);
            String result = service.get(MessageKey.ALREADY_REGISTERED, user);
            assertThat(result).isEqualTo("Вы уже зарегистрированы. Используйте кнопки ниже.");
        }

        @DisplayName("falls back to English for user with null language (mid-registration)")
        @Test
        void fallsBackToEnglish_forUserWithNullLanguage() {
            User user = userWith(null);
            String result = service.get(MessageKey.ALREADY_REGISTERED, user);
            assertThat(result).isEqualTo("You are already registered. Use the buttons below.");
        }

        @DisplayName("WELCOME_BILINGUAL is identical for EN and RU users (single bilingual string)")
        @Test
        void welcomeBilingual_isSameForBothLanguages() {
            String en = service.get(MessageKey.WELCOME_BILINGUAL, userWith(Language.EN));
            String ru = service.get(MessageKey.WELCOME_BILINGUAL, userWith(Language.RU));
            assertThat(en).isEqualTo(ru);
        }
    }
}
