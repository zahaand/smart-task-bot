package ru.zahaand.smarttaskbot.service;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds an inline keyboard that displays a monthly calendar.
 * Past dates are rendered as non-interactive "·" cells; today and future dates
 * carry a {@code CAL_DATE:YYYY-MM-DD} callback. Navigation arrows step one month
 * at a time; "←" is disabled (NO_OP) when already showing the current month.
 */
@Component
public class CalendarKeyboardBuilder {

    private static final DateTimeFormatter MONTH_HEADER_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ISO_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public InlineKeyboardMarkup buildCalendar(int year, int month) {
        final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        final YearMonth ym = YearMonth.of(year, month);
        final LocalDate today = LocalDate.now();

        rows.add(buildHeaderRow(ym));
        rows.add(buildDayOfWeekRow());
        rows.addAll(buildDateRows(ym, today));
        rows.add(buildNavRow(ym, today));

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    // ── rows ─────────────────────────────────────────────────────────────────

    private List<InlineKeyboardButton> buildHeaderRow(YearMonth ym) {
        final String label = "« " + ym.atDay(1).format(MONTH_HEADER_FMT) + " »";
        return List.of(noop(label));
    }

    private List<InlineKeyboardButton> buildDayOfWeekRow() {
        // Mon–Sun, abbreviated
        final List<InlineKeyboardButton> row = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            row.add(noop(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
        }
        return row;
    }

    private List<List<InlineKeyboardButton>> buildDateRows(YearMonth ym, LocalDate today) {
        final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> week = new ArrayList<>();

        // Pad Monday-aligned grid: find where the 1st falls in the week
        final LocalDate firstOfMonth = ym.atDay(1);
        final int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1; // Mon=1
        for (int i = 0; i < leadingBlanks; i++) {
            week.add(noop(" "));
        }

        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            final LocalDate date = ym.atDay(day);

            if (date.isBefore(today)) {
                week.add(noop("·"));
            } else {
                final String callback = BotConstants.CB_CAL_DATE + date.format(ISO_DATE_FMT);
                week.add(button(String.valueOf(day), callback));
            }

            if (week.size() == 7) {
                rows.add(week);
                week = new ArrayList<>();
            }
        }

        // Trailing blanks to fill final week
        if (!week.isEmpty()) {
            while (week.size() < 7) {
                week.add(noop(" "));
            }
            rows.add(week);
        }

        return rows;
    }

    private List<InlineKeyboardButton> buildNavRow(YearMonth ym, LocalDate today) {
        final YearMonth current = YearMonth.from(today);
        // "←" is a no-op when already on the current month — can't go further back
        final InlineKeyboardButton prev = ym.isAfter(current)
                ? button("←", BotConstants.CB_CAL_NAV + "-1")
                : noop("←");
        final InlineKeyboardButton next = button("→", BotConstants.CB_CAL_NAV + "+1");
        return List.of(prev, next);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InlineKeyboardButton button(String text, String callbackData) {
        final InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private InlineKeyboardButton noop(String text) {
        return button(text, BotConstants.CB_NO_OP);
    }
}
