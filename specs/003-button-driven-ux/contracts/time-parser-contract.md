# Time Parser Contract

**Feature**: 003-button-driven-ux
**Date**: 2026-03-19
**Class**: `TimeParserService` in `ru.zahaand.smarttaskbot.service`

---

## Signature

```java
public Optional<LocalTime> parse(String input)
```

Returns `Optional.empty()` if the input matches no supported format or is explicitly rejected (12 o'clock rule). Never throws an exception — all parsing failures become empty optionals.

---

## Supported Formats

Input is trimmed before matching. Matching is case-insensitive for Russian keywords.

| Pattern | Example inputs | Output |
|---------|---------------|--------|
| `H:mm` or `HH:mm` | `9:05`, `14:30`, `09:00`, `0:00` | `LocalTime.of(H, mm)` — validated 0–23 / 0–59 |
| `N утра` | `9 утра`, `11 утра` | N ∈ 1–11 → `LocalTime.of(N, 0)` |
| `N вечера` | `3 вечера`, `9 вечера` | N ∈ 1–11 → `LocalTime.of(N + 12, 0)` |
| `N:mm утра` | `9:30 утра`, `11:00 утра` | N ∈ 1–11, mm 0–59 → `LocalTime.of(N, mm)` |
| `N:mm вечера` | `3:30 вечера`, `11:45 вечера` | N ∈ 1–11, mm 0–59 → `LocalTime.of(N + 12, mm)` |

---

## Rejection Rules

| Input | Reason | Returns |
|-------|--------|---------|
| `12 утра` | Ambiguous (midnight vs noon) | `Optional.empty()` with `REJECT_12` marker |
| `12 вечера` | Ambiguous (noon vs midnight) | `Optional.empty()` with `REJECT_12` marker |
| `0 утра` | 0 is not a valid "утра" hour | `Optional.empty()` |
| `25:00` | Hour out of range | `Optional.empty()` |
| `14:60` | Minute out of range | `Optional.empty()` |
| `abc` | No pattern match | `Optional.empty()` |

---

## Caller Behaviour Contract

`ReminderTimeTextHandler` calls `timeParserService.parse(text)` and:

```
if result is present:
    → create reminder, confirm, transition to IDLE

if result is empty AND input matches "12 утра" or "12 вечера":
    → reply: "Для 12 часов используй формат HH:mm (00:00 или 12:00)"
    → stay in ENTERING_REMINDER_TIME

if result is empty otherwise:
    → reply: "Не понял формат времени. Попробуй: 14:30, 9 утра, 21:00"
    → stay in ENTERING_REMINDER_TIME
```

To distinguish the 12-o'clock case from a generic parse failure, `TimeParserService` exposes a helper:
```java
public boolean isTwelveOClockAmbiguous(String input)
```
Returns true if the trimmed, lowercased input matches `12 утра` or `12 вечера`.

---

## Test Cases (Parameterized)

| Input | Expected Output |
|-------|----------------|
| `14:30` | `Optional.of(LocalTime.of(14, 30))` |
| `9:05` | `Optional.of(LocalTime.of(9, 5))` |
| `0:00` | `Optional.of(LocalTime.of(0, 0))` |
| `23:59` | `Optional.of(LocalTime.of(23, 59))` |
| `9 утра` | `Optional.of(LocalTime.of(9, 0))` |
| `11 утра` | `Optional.of(LocalTime.of(11, 0))` |
| `3 вечера` | `Optional.of(LocalTime.of(15, 0))` |
| `11 вечера` | `Optional.of(LocalTime.of(23, 0))` |
| `9:30 утра` | `Optional.of(LocalTime.of(9, 30))` |
| `3:45 вечера` | `Optional.of(LocalTime.of(15, 45))` |
| `12 утра` | `Optional.empty()` (12-o'clock ambiguous) |
| `12 вечера` | `Optional.empty()` (12-o'clock ambiguous) |
| `0 утра` | `Optional.empty()` |
| `25:00` | `Optional.empty()` |
| `14:60` | `Optional.empty()` |
| `abc` | `Optional.empty()` |
| `21:00` | `Optional.of(LocalTime.of(21, 0))` |
