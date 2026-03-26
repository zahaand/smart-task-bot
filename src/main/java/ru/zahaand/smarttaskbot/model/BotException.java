package ru.zahaand.smarttaskbot.model;

import lombok.Getter;

/**
 * Domain exception carrying a {@link MessageKey} for localized user-facing error messages.
 * Handlers catch this and resolve the message via {@link ru.zahaand.smarttaskbot.service.MessageService}.
 * <p>
 * Доменное исключение с {@link MessageKey} для локализованных сообщений об ошибках.
 * Обработчики перехватывают его и разрешают сообщение через {@link ru.zahaand.smarttaskbot.service.MessageService}.
 */
@Getter
public class BotException extends RuntimeException {

    private final MessageKey messageKey;
    private final Object[] args;

    /**
     * Creates a new {@code BotException} with the given message key and optional format arguments.
     * <p>
     * Создаёт новый {@code BotException} с указанным ключом сообщения и необязательными аргументами форматирования.
     *
     * @param messageKey the localization key to resolve at the handler level
     * @param args       optional arguments for {@link String#formatted}
     */
    public BotException(MessageKey messageKey, Object... args) {
        super(messageKey.name());
        this.messageKey = messageKey;
        this.args = args;
    }
}
