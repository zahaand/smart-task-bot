package ru.zahaand.smarttaskbot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.zahaand.smarttaskbot.handler.SmartTaskBot;

/**
 * Configuration class for initializing the Telegram Bot API.
 * Loads bot credentials from properties and registers the {@link SmartTaskBot} bean.
 */
@Configuration
@Getter
public class BotConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Bean
    public TelegramBotsApi telegramBotsApi(SmartTaskBot smartTaskBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(smartTaskBot);
        return api;
    }
}
