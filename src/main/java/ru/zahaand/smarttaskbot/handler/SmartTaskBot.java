package ru.zahaand.smarttaskbot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.zahaand.smarttaskbot.config.BotConfig;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;

import java.util.Optional;

/**
 * Main bot class that handles communication with Telegram via Long Polling.
 * Inherits from {@link TelegramLongPollingBot} to receive updates and
 * delegates them to the {@link UpdateDispatcher}.
 */
@Slf4j
@Component
public class SmartTaskBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final UpdateDispatcher dispatcher;

    public SmartTaskBot(BotConfig botConfig, UpdateDispatcher dispatcher) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.dispatcher = dispatcher;
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            dispatcher.dispatch(update);
        } catch (Exception e) {
            log.error("Unhandled exception processing update: {}", e.getMessage(), e);
            extractChatId(update).ifPresent(chatId -> {
                try {
                    execute(new SendMessage(chatId.toString(),
                            MessageKey.SOMETHING_WENT_WRONG.get(Language.EN)));
                } catch (TelegramApiException ex) {
                    log.error("Failed to send error reply to chatId={}: {}", chatId, ex.getMessage(), ex);
                }
            });
        }
    }

    private Optional<Long> extractChatId(Update update) {
        if (update.hasMessage() && update.getMessage() != null) {
            return Optional.of(update.getMessage().getChatId());
        }

        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return Optional.of(update.getCallbackQuery().getMessage().getChatId());
        }

        return Optional.empty();
    }
}
