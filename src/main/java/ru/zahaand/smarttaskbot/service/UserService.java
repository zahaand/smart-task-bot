package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserRepository;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

/**
 * Service for managing user data and registration.
 * All domain logic for user lifecycle lives here; repositories are not accessed directly
 * from handlers.
 *
 * Сервис управления данными пользователей и регистрацией.
 * Вся доменная логика жизненного цикла пользователей находится здесь.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserStateRepository userStateRepository;

    // ── New two-step registration ─────────────────────────────────────────────

    /**
     * Atomically creates a minimal User row (language=null, timezone=null) and a
     * UserState row (AWAITING_LANGUAGE) in a single transaction.
     * Called exclusively from StartCommandHandler when a brand-new user sends /start.
     * UserStateService.initRegistration() MUST NOT be called separately — this method
     * is the sole owner of UserState creation during /start (CHK011 resolution).
     *
     * Атомарно создаёт строку User (language=null, timezone=null) и UserState
     * (AWAITING_LANGUAGE) в одной транзакции. Вызывается только из StartCommandHandler.
     */
    @Transactional
    public void createPartialUser(Long telegramUserId, String username) {
        if (userRepository.existsById(telegramUserId)) {
            log.warn("createPartialUser called for existing user, skipping: userId={}", telegramUserId);
            return;
        }

        User user = new User();
        user.setTelegramUserId(telegramUserId);
        user.setUsername(username);
        // language and timezone intentionally null — set during registration steps
        userRepository.save(user);

        UserState userState = new UserState(telegramUserId);
        userState.setState(ConversationState.AWAITING_LANGUAGE);
        userStateRepository.save(userState);

        log.info("Partial user created: userId={}", telegramUserId);
    }

    /**
     * Sets the user's language. Called from LanguageCallbackHandler after language selection.
     *
     * Устанавливает язык пользователя. Вызывается из LanguageCallbackHandler.
     */
    @Transactional
    public void updateLanguage(Long telegramUserId, Language language) {
        userRepository.findById(telegramUserId).ifPresent(user -> {
            user.setLanguage(language);
            userRepository.save(user);
            log.info("Language updated: userId={}, language={}", telegramUserId, language);
        });
    }

    /**
     * Sets the user's timezone. Called from TimezoneCallbackHandler after timezone selection.
     *
     * Устанавливает часовой пояс пользователя. Вызывается из TimezoneCallbackHandler.
     */
    @Transactional
    public void updateTimezone(Long telegramUserId, String timezone) {
        userRepository.findById(telegramUserId).ifPresent(user -> {
            user.setTimezone(timezone);
            userRepository.save(user);
            log.info("Timezone updated: userId={}, timezone={}", telegramUserId, timezone);
        });
    }

    // ── Registration status ───────────────────────────────────────────────────

    /**
     * Returns true only when both language and timezone are set.
     * A user row existing without these fields is considered mid-registration.
     *
     * Возвращает true только когда установлены и язык, и часовой пояс.
     */
    public boolean isRegistered(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .map(u -> u.getLanguage() != null && u.getTimezone() != null)
                .orElse(false);
    }

    /**
     * Returns true when a User row exists but language is still null (step 1 of registration).
     *
     * Возвращает true, если строка User существует, но язык ещё не выбран.
     */
    public boolean isLanguagePending(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .map(u -> u.getLanguage() == null)
                .orElse(false);
    }

    /**
     * Returns true when language is set but timezone is still null (step 2 of registration).
     *
     * Возвращает true, если язык выбран, но часовой пояс ещё не задан.
     */
    public boolean isTimezonePending(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .map(u -> u.getLanguage() != null && u.getTimezone() == null)
                .orElse(false);
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    /**
     * Returns the User for the given Telegram user ID, or throws if not found.
     * Used by handlers that need the full User object (e.g. for language resolution).
     *
     * Возвращает User по telegramUserId или бросает исключение если не найден.
     */
    public User findById(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .orElseThrow(() -> {
                    log.error("User not found: userId={}", telegramUserId);
                    return new IllegalStateException("User not found: " + telegramUserId);
                });
    }

    /**
     * Returns the IANA timezone string for the given user, or throws if the user is not found.
     * <p>
     * Возвращает строку часового пояса IANA или бросает исключение, если пользователь не найден.
     */
    public String getTimezone(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .map(User::getTimezone)
                .orElseThrow(() -> {
                    log.error("Timezone lookup failed — user not found: userId={}", telegramUserId);
                    return new IllegalStateException("User not found: " + telegramUserId);
                });
    }

    /**
     * Returns true when a User row exists for the given Telegram user ID.
     *
     * Возвращает true, если строка User существует для указанного пользователя.
     */
    public boolean userExists(Long telegramUserId) {
        return userRepository.existsById(telegramUserId);
    }
}
