package ru.zahaand.smarttaskbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateTaskRequest {

    private final Long telegramUserId;
    private final String text;
}
