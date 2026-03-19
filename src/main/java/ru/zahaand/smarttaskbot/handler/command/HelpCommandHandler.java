package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;

@Component
@RequiredArgsConstructor
public class HelpCommandHandler {

    private final NotificationService notificationService;

    private static final String HELP_TEXT = """
            Smart Task Bot — available commands:

            /newtask <text>              — create a new task
            /tasks                       — list your active tasks
            /remind <id> DD.MM.YYYY HH:mm  — set a reminder
            /done <id>                   — mark a task as completed
            /help                        — show this message""";

    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        notificationService.sendMessage(chatId, HELP_TEXT);
    }
}
