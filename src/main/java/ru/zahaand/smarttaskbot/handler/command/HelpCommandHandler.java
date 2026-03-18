package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;

@Component
@RequiredArgsConstructor
public class HelpCommandHandler {

    private static final String HELP_TEXT = """
            Smart Task Bot \u2014 available commands:

            /newtask <text>              \u2014 create a new task
            /tasks                       \u2014 list your active tasks
            /remind <id> DD.MM.YYYY HH:mm  \u2014 set a reminder
            /done <id>                   \u2014 mark a task as completed
            /help                        \u2014 show this message""";

    private final NotificationService notificationService;

    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        notificationService.sendMessage(chatId, HELP_TEXT);
    }
}
