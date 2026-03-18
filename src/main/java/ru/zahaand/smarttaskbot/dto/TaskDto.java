package ru.zahaand.smarttaskbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskDto {

    private final Long id;
    private final String text;

    /**
     * Pre-formatted as {@code dd.MM.yyyy HH:mm} in the user's timezone,
     * or {@code null} if no reminder is set.
     */
    private final String reminderTime;
}
