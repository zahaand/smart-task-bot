package ru.zahaand.smarttaskbot.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConfig;

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
        dispatcher.dispatch(update);
    }
}
