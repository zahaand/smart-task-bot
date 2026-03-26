package ru.zahaand.smarttaskbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} annotation processing.
 * Required for {@link ru.zahaand.smarttaskbot.service.ReminderService} polling.
 * <p>
 * Включает обработку аннотации {@code @Scheduled} в Spring.
 * Необходим для работы поллинга в {@link ru.zahaand.smarttaskbot.service.ReminderService}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
