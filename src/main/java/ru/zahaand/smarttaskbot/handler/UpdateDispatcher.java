package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.handler.callback.TimezoneCallbackHandler;
import ru.zahaand.smarttaskbot.handler.command.*;

/**
 * Central component for routing incoming updates.
 * Analyzes the type of update (message, callback query, etc.) and
 * forwards it to the appropriate handler or guard.
 */
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final RegistrationGuard registrationGuard;
    private final TimezoneCallbackHandler timezoneCallbackHandler;
    private final StartCommandHandler startCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final NewTaskCommandHandler newTaskCommandHandler;
    private final TaskListCommandHandler taskListCommandHandler;
    private final UnknownInputHandler unknownInputHandler;

    public void dispatch(Update update) {

        if (update.hasCallbackQuery()) {
            timezoneCallbackHandler.handle(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String command = extractCommand(update.getMessage().getText());
            switch (command) {
                case "/start" -> startCommandHandler.handle(update);
                case "/help" -> helpCommandHandler.handle(update);
                case "/newtask" -> registrationGuard.checkAndRoute(update,
                        () -> newTaskCommandHandler.handle(update));
                case "/tasks" -> registrationGuard.checkAndRoute(update,
                        () -> taskListCommandHandler.handle(update));
                default -> registrationGuard.checkAndRoute(update,
                        () -> unknownInputHandler.handle(update));
            }
        }
    }

    private String extractCommand(String text) {
        if (text == null) {
            return "";
        }

        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase();

        int atIndex = command.indexOf('@');
        if (atIndex != -1) {
            command = command.substring(0, atIndex);
        }

        return command;
    }
}
