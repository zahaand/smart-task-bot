package ru.zahaand.smarttaskbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationContext {

    /**
     * Present in: SELECTING_REMINDER_DATE, ENTERING_REMINDER_TIME, CONFIRMING_DELETE
     */
    private Long taskId;

    /**
     * Present in: SELECTING_REMINDER_DATE — calendar page currently displayed
     */
    private Integer viewingYear;

    /**
     * Present in: SELECTING_REMINDER_DATE — 1-based month (1=January)
     */
    private Integer viewingMonth;

    /**
     * Present in: ENTERING_REMINDER_TIME — ISO-8601 date chosen from calendar
     */
    private String date;
}
