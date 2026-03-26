package ru.zahaand.smarttaskbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;

/**
 * Resolves user-facing strings by key and language at runtime.
 * All text in handlers and services must be retrieved via this bean — no hardcoded strings.
 * Null language falls back to EN (safety guard for mid-registration state).
 * <p>
 * Разрешает пользовательские строки по ключу и языку во время выполнения.
 * Весь текст в обработчиках и сервисах должен получаться через этот бин.
 * Null-язык автоматически заменяется на EN (защита для состояния mid-registration).
 */
@Slf4j
@Service
public class MessageService {

    /**
     * Returns the string for the given key in the given language.
     * Falls back to EN when language is null.
     * <p>
     * Возвращает строку для ключа на указанном языке, при null — на английском.
     */
    public String get(MessageKey key, Language language) {
        return key.get(language != null ? language : Language.EN);
    }

    /**
     * Returns the string for the given key in the user's stored language.
     * Falls back to EN when the user's language field is null (mid-registration).
     * <p>
     * Возвращает строку для ключа на языке пользователя.
     * При null-языке (mid-registration) — на английском.
     */
    public String get(MessageKey key, User user) {
        return get(key, user.getLanguage());
    }
}
