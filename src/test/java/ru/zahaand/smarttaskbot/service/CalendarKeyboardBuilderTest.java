package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarKeyboardBuilderTest {

    private final CalendarKeyboardBuilder builder = new CalendarKeyboardBuilder();

    // January 2024 is definitively in the past — all day cells must be "·" (NO_OP)
    private static final int PAST_YEAR = 2024;
    private static final int PAST_MONTH = 1;

    // Helper: collect all buttons across all rows (flattened)
    private List<InlineKeyboardButton> allButtons(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream().flatMap(List::stream).toList();
    }

    // Helper: get the nav row (always the last row)
    private List<InlineKeyboardButton> navRow(InlineKeyboardMarkup markup) {
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        return rows.get(rows.size() - 1);
    }

    // Helper: extract day-cell buttons (skip header row + day-of-week row)
    private List<InlineKeyboardButton> dayCells(InlineKeyboardMarkup markup) {
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        // row 0 = month header, row 1 = day-of-week labels, remaining (except last) = date grid
        return rows.subList(2, rows.size() - 1).stream().flatMap(List::stream).toList();
    }

    @Nested
    @DisplayName("Header row")
    class HeaderRow {

        @Test
        @DisplayName("first row is a single non-interactive button with NO_OP callback")
        void headerIsNoop() {
            InlineKeyboardMarkup markup = builder.buildCalendar(PAST_YEAR, PAST_MONTH);
            List<InlineKeyboardButton> header = markup.getKeyboard().get(0);

            assertThat(header).hasSize(1);
            assertThat(header.get(0).getCallbackData()).isEqualTo(BotConstants.CB_NO_OP);
            assertThat(header.get(0).getText()).contains("January").contains("2024");
        }
    }

    @Nested
    @DisplayName("Day-of-week row")
    class DayOfWeekRow {

        @Test
        @DisplayName("second row has 7 non-interactive day labels")
        void dayOfWeekRowHasSevenNoopButtons() {
            InlineKeyboardMarkup markup = builder.buildCalendar(PAST_YEAR, PAST_MONTH);
            List<InlineKeyboardButton> row = markup.getKeyboard().get(1);

            assertThat(row).hasSize(7);
            row.forEach(btn -> assertThat(btn.getCallbackData()).isEqualTo(BotConstants.CB_NO_OP));
        }
    }

    @Nested
    @DisplayName("Date cells — past month")
    class PastDateCells {

        @Test
        @DisplayName("all day cells in a past month use NO_OP callback (shown as ·)")
        void allPastDayCellsAreNoop() {
            InlineKeyboardMarkup markup = builder.buildCalendar(PAST_YEAR, PAST_MONTH);
            List<InlineKeyboardButton> cells = dayCells(markup);

            // Non-padding cells (not " ") should all be "·" with NO_OP
            cells.stream()
                    .filter(btn -> !"".equals(btn.getText().trim()) && !" ".equals(btn.getText()))
                    .forEach(btn -> {
                        if (btn.getText().equals("·")) {
                            assertThat(btn.getCallbackData()).isEqualTo(BotConstants.CB_NO_OP);
                        }
                    });

            long calDateCount = cells.stream()
                    .filter(btn -> btn.getCallbackData().startsWith(BotConstants.CB_CAL_DATE))
                    .count();
            assertThat(calDateCount).isZero();
        }
    }

    @Nested
    @DisplayName("Date cells — future month")
    class FutureDateCells {

        @Test
        @DisplayName("day cells in a future month use CAL_DATE:YYYY-MM-DD callbacks")
        void futureDayCellsHaveCalDateCallbacks() {
            LocalDate today = LocalDate.now();
            YearMonth nextYear = YearMonth.of(today.getYear() + 1, 1);
            InlineKeyboardMarkup markup = builder.buildCalendar(nextYear.getYear(), nextYear.getMonthValue());
            List<InlineKeyboardButton> cells = dayCells(markup);

            long calDateCount = cells.stream()
                    .filter(btn -> btn.getCallbackData().startsWith(BotConstants.CB_CAL_DATE))
                    .count();
            // January has 31 days, all in the future
            assertThat(calDateCount).isEqualTo(31);
        }

        @Test
        @DisplayName("CAL_DATE callback value is a valid ISO date in the correct month")
        void calDateCallbackIsValidIsoDate() {
            LocalDate today = LocalDate.now();
            YearMonth nextYear = YearMonth.of(today.getYear() + 1, 1);
            InlineKeyboardMarkup markup = builder.buildCalendar(nextYear.getYear(), nextYear.getMonthValue());
            List<InlineKeyboardButton> cells = dayCells(markup);

            cells.stream()
                    .filter(btn -> btn.getCallbackData().startsWith(BotConstants.CB_CAL_DATE))
                    .forEach(btn -> {
                        String datePart = btn.getCallbackData().substring(BotConstants.CB_CAL_DATE.length());
                        // Should parse without exception
                        LocalDate parsed = LocalDate.parse(datePart);
                        assertThat(parsed.getYear()).isEqualTo(nextYear.getYear());
                        assertThat(parsed.getMonthValue()).isEqualTo(nextYear.getMonthValue());
                    });
        }
    }

    @Nested
    @DisplayName("Navigation row")
    class NavRow {

        @Test
        @DisplayName("← is NO_OP when showing the current month (can't go further back)")
        void prevIsNoopForCurrentMonth() {
            LocalDate today = LocalDate.now();
            InlineKeyboardMarkup markup = builder.buildCalendar(today.getYear(), today.getMonthValue());
            InlineKeyboardButton prev = navRow(markup).get(0);

            assertThat(prev.getText()).isEqualTo("←");
            assertThat(prev.getCallbackData()).isEqualTo(BotConstants.CB_NO_OP);
        }

        @Test
        @DisplayName("← carries CAL_NAV:-1 for a future month")
        void prevHasNavCallbackForFutureMonth() {
            LocalDate today = LocalDate.now();
            YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
            InlineKeyboardMarkup markup = builder.buildCalendar(nextMonth.getYear(), nextMonth.getMonthValue());
            InlineKeyboardButton prev = navRow(markup).get(0);

            assertThat(prev.getText()).isEqualTo("←");
            assertThat(prev.getCallbackData()).isEqualTo(BotConstants.CB_CAL_NAV + "-1");
        }

        @Test
        @DisplayName("→ always carries CAL_NAV:+1")
        void nextAlwaysHasNavCallback() {
            InlineKeyboardMarkup pastMarkup = builder.buildCalendar(PAST_YEAR, PAST_MONTH);
            InlineKeyboardMarkup currentMarkup = builder.buildCalendar(
                    LocalDate.now().getYear(), LocalDate.now().getMonthValue());

            assertThat(navRow(pastMarkup).get(1).getCallbackData())
                    .isEqualTo(BotConstants.CB_CAL_NAV + "+1");
            assertThat(navRow(currentMarkup).get(1).getCallbackData())
                    .isEqualTo(BotConstants.CB_CAL_NAV + "+1");
        }

        @Test
        @DisplayName("nav row has exactly 2 buttons (← and →)")
        void navRowHasTwoButtons() {
            InlineKeyboardMarkup markup = builder.buildCalendar(PAST_YEAR, PAST_MONTH);
            assertThat(navRow(markup)).hasSize(2);
        }
    }
}
