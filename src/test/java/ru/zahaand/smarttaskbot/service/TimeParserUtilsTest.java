package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TimeParserUtilsTest {

    record Case(String input, Optional<LocalTime> expected) {
        @Override
        public String toString() {
            return "\"" + input + "\"";
        }
    }

    static Stream<Case> validCases() {
        return Stream.of(
                new Case("14:30", Optional.of(LocalTime.of(14, 30))),
                new Case("9:05", Optional.of(LocalTime.of(9, 5))),
                new Case("0:00", Optional.of(LocalTime.of(0, 0))),
                new Case("23:59", Optional.of(LocalTime.of(23, 59))),
                new Case("21:00", Optional.of(LocalTime.of(21, 0))),
                new Case("9 утра", Optional.of(LocalTime.of(9, 0))),
                new Case("11 утра", Optional.of(LocalTime.of(11, 0))),
                new Case("3 вечера", Optional.of(LocalTime.of(15, 0))),
                new Case("11 вечера", Optional.of(LocalTime.of(23, 0))),
                new Case("9:30 утра", Optional.of(LocalTime.of(9, 30))),
                new Case("3:45 вечера", Optional.of(LocalTime.of(15, 45)))
        );
    }

    static Stream<Case> spaceSeparatedCases() {
        return Stream.of(
                new Case("14 00", Optional.of(LocalTime.of(14, 0))),
                new Case("9 05", Optional.of(LocalTime.of(9, 5)))
        );
    }

    static Stream<Case> hyphenSeparatedCases() {
        return Stream.of(
                new Case("14-00", Optional.of(LocalTime.of(14, 0))),
                new Case("9-05", Optional.of(LocalTime.of(9, 5)))
        );
    }

    static Stream<String> rejectedInputs() {
        return Stream.of("12 утра", "12 вечера", "0 утра", "25:00", "14:60", "abc",
                "25 00", "12 99", "25-00");
    }

    @Nested
    @DisplayName("parse() — valid inputs")
    class ValidInputs {

        @ParameterizedTest(name = "{0}")
        @MethodSource("ru.zahaand.smarttaskbot.service.TimeParserUtilsTest#validCases")
        void parsesValidInput(Case c) {
            assertThat(TimeParserUtils.parse(c.input())).isEqualTo(c.expected());
        }

        @DisplayName("trims leading/trailing whitespace before matching")
        @Test
        void trimsWhitespace() {
            assertThat(TimeParserUtils.parse("  14:30  ")).isEqualTo(Optional.of(LocalTime.of(14, 30)));
        }
    }

    @Nested
    @DisplayName("parse() — space-separated HH mm")
    class SpaceSeparated {

        @ParameterizedTest(name = "{0}")
        @MethodSource("ru.zahaand.smarttaskbot.service.TimeParserUtilsTest#spaceSeparatedCases")
        void parsesSpaceSeparatedInput(Case c) {
            assertThat(TimeParserUtils.parse(c.input())).isEqualTo(c.expected());
        }
    }

    @Nested
    @DisplayName("parse() — hyphen-separated HH-mm")
    class HyphenSeparated {

        @ParameterizedTest(name = "{0}")
        @MethodSource("ru.zahaand.smarttaskbot.service.TimeParserUtilsTest#hyphenSeparatedCases")
        void parsesHyphenSeparatedInput(Case c) {
            assertThat(TimeParserUtils.parse(c.input())).isEqualTo(c.expected());
        }
    }

    @Nested
    @DisplayName("parse() — rejected / empty inputs")
    class RejectedInputs {

        @ParameterizedTest(name = "\"{0}\" → empty")
        @MethodSource("ru.zahaand.smarttaskbot.service.TimeParserUtilsTest#rejectedInputs")
        void rejectsInput(String input) {
            assertThat(TimeParserUtils.parse(input)).isEmpty();
        }

        @DisplayName("null → empty, no exception")
        @Test
        void returnsEmptyForNull() {
            assertThat(TimeParserUtils.parse(null)).isEmpty();
        }

        @DisplayName("blank string → empty, no exception")
        @Test
        void returnsEmptyForBlank() {
            assertThat(TimeParserUtils.parse("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("isTwelveOClockAmbiguous()")
    class TwelveOClock {

        @DisplayName("true for \"12 утра\"")
        @Test
        void trueFor12Utra() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous("12 утра")).isTrue();
        }

        @DisplayName("true for \"12 вечера\"")
        @Test
        void trueFor12Vechera() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous("12 вечера")).isTrue();
        }

        @DisplayName("case-insensitive: \"12 УТРА\" → true")
        @Test
        void caseInsensitive() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous("12 УТРА")).isTrue();
        }

        @DisplayName("false for non-ambiguous input \"9 утра\"")
        @Test
        void falseForNonAmbiguous() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous("9 утра")).isFalse();
        }

        @DisplayName("false for null")
        @Test
        void falseForNull() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous(null)).isFalse();
        }

        @DisplayName("false for blank")
        @Test
        void falseForBlank() {
            assertThat(TimeParserUtils.isTwelveOClockAmbiguous("  ")).isFalse();
        }
    }
}
