# Research: Smart Task Bot MVP

**Branch**: `001-task-bot-mvp` | **Date**: 2026-03-18

## 1. TelegramBots 6.9.7.1 — Update Routing & Inline Keyboards

### Decision
Route all incoming updates via `Update.hasCallbackQuery()` check first, then fall through
to message handling. Build timezone keyboard with `InlineKeyboardMarkup` +
`InlineKeyboardButton`. Read callback payload via `update.getCallbackQuery().getData()`.

### Rationale
`Update` fields are mutually exclusive — a callback query update never carries
`getMessage()`, so calling `getMessage().getText()` on it throws `NullPointerException`.
The dispatcher MUST branch on update type before any field access.

Callback data is a plain string (max 64 bytes). Using a namespaced prefix
(e.g., `"tz:Europe/Moscow"`) allows the dispatcher to identify the callback type
without ambiguity.

### Alternatives Considered
- **Reply keyboards** (ReplyKeyboardMarkup): visible as a persistent keyboard
  replacement — worse UX for a one-time selection, cannot be dismissed by the bot.
  Rejected.
- **Text-based number selection**: requires stateful conversation tracking
  (remembering "user X is in timezone selection mode"). Inline keyboard makes
  the selection stateless — the callback payload carries all needed information.
  Rejected.

---

## 2. Reminder Scheduling — Spring `@Scheduled` vs Quartz

### Decision
Use Spring `@Scheduled(fixedDelay = 60_000)` with `@EnableScheduling`.
Poll the database every 60 seconds for due reminders.

### Rationale
- Single-instance MVP: no distributed locking needed.
- Zero additional configuration beyond the annotation.
- Default single-thread executor prevents overlapping executions.
- Satisfies SC-003 (delivery within 60 seconds) by design.

For the one-retry requirement (FR-009): after a failed Telegram API call, set
`reminder_retry_at = now + 60s` on the task. The next poll cycle picks it up
via a second query predicate. This keeps retry logic entirely within the
`ReminderService` — no external scheduler framework required.

### Alternatives Considered
- **Quartz Scheduler**: persistent job store, misfire policies, cluster-aware.
  Adds JDBC schema, configuration complexity, and extra dependency.
  Violates Constitution Principle VI (Simplicity / YAGNI) for MVP scope.
  Rejected.
- **`@Scheduled(fixedRate = 60_000)`**: fires every 60 seconds regardless of
  execution duration — risks overlap if DB is slow. `fixedDelay` is safer.
  Rejected.

---

## 3. Timezone-Aware DateTime Handling

### Decision
- **Parse user input**: `LocalDateTime.parse(input, formatter)` (naive, no zone yet)
- **Attach user zone**: `ZonedDateTime zoned = localDt.atZone(ZoneId.of(user.getTimezone()))`
- **Store as UTC Instant**: `Instant utc = zoned.toInstant()` → persisted as
  `TIMESTAMP WITHOUT TIME ZONE` in PostgreSQL (UTC convention)
- **Display to user**: `Instant.atZone(ZoneId.of(user.getTimezone()))` → format
  with `DateTimeFormatter` pattern `"dd.MM.yyyy HH:mm"`

### Java Types
| Field | Java Type | DB Column Type |
|-------|-----------|----------------|
| reminder_time (stored) | `Instant` | `TIMESTAMP` (UTC) |
| reminder_time (display) | `ZonedDateTime` | — |
| User.timezone | `String` (ZoneId name) | `VARCHAR(50)` |

### Rationale
- `Instant` is unambiguous UTC — no DST confusion in storage.
- `ZonedDateTime` correctly applies DST rules during conversion.
- Conversion happens only at the service layer boundary (input parsing,
  output formatting) — not in repository or model.
- `LocalDateTime` in the JPA entity for `reminderTime` is acceptable since
  the column is UTC by convention; Hibernate maps it without offset shift
  when the JVM is set to UTC.

### Alternatives Considered
- **`OffsetDateTime`**: more explicit than `LocalDateTime`, but requires
  confirming Hibernate/PostgreSQL driver behavior. `Instant` + UTC convention
  is simpler and equally correct for MVP.
- **Store timezone-aware string**: e.g., `"2026-03-25T09:00:00+03:00"`.
  Harder to query with standard SQL comparisons. Rejected.

---

## Summary

| Question | Decision | Confidence |
|----------|----------|------------|
| Callback vs message routing | `hasCallbackQuery()` branch first | High |
| Inline keyboard builder | `InlineKeyboardMarkup` + `InlineKeyboardButton` | High |
| Callback payload read | `getCallbackQuery().getData()` | High |
| Reminder polling | `@Scheduled(fixedDelay = 60_000)` | High |
| Retry mechanism | `reminder_retry_at` DB column + second poll query | High |
| UTC storage type | `Instant` / `TIMESTAMP` (UTC convention) | High |
| Local display conversion | `Instant.atZone(userZoneId)` → format | High |
