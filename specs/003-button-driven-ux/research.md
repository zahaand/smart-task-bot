# Research: Button-Driven UX (003-button-driven-ux)

**Date**: 2026-03-19
**Branch**: `003-button-driven-ux`

---

## Decision 1 — Persistent Reply Keyboard

**Decision**: `ReplyKeyboardMarkup` with `resizeKeyboard = true` and `isPersistent = true`

**Rationale**: Telegram Bot API 6.x adds the `is_persistent` flag to `ReplyKeyboardMarkup`. When set, the keyboard is shown even when the reply field is collapsed. `TelegramBots 6.9.7.1` exposes this as `ReplyKeyboardMarkup.builder().isPersistent(true)`. The keyboard must be re-sent once after registration — it persists in the Telegram client automatically without re-sending on every message.

**How to apply**: `NotificationService.sendPersistentMenu()` sends the keyboard attached to the post-registration welcome message. `StartCommandHandler` must also re-send it if the user is already registered (in case keyboard was dismissed).

**Alternatives considered**:
- `ReplyKeyboardMarkup` without `isPersistent` — keyboard hides after each reply, poor UX.
- `BotCommand` menu (the "/" commands menu in Telegram) — not visible during chat, insufficient.

---

## Decision 2 — Editing Messages In-Place

**Decision**: `EditMessageText` API call with inline keyboard markup replacement

**Rationale**: When the user taps a tab toggle (Активные ↔ Выполненные) or navigates the calendar, the bot must call `execute(EditMessageText.builder().chatId(...).messageId(...).text(...).replyMarkup(...).build())`. The `messageId` is always available from `callbackQuery.getMessage().getMessageId()` — no need to store it in `UserState` context for callback-triggered edits. After editing, the bot must answer the callback query with `AnswerCallbackQuery` (empty or with a short toast) to remove the Telegram loading spinner.

**Alternatives considered**:
- Sending new messages — clutters the chat, fails the "edit in-place" UX requirement.
- Storing `messageId` in `UserState` context — unnecessary for callback flows (messageId is in the callback payload itself). Needed only for the calendar, where the user might type text (ENTERING_REMINDER_TIME) and we want to optionally update the last calendar message.

---

## Decision 3 — JSONB Context Storage

**Decision**: Store `context` as a plain `VARCHAR`/`TEXT` column (JSON string) in the entity; use Jackson `ObjectMapper` in `UserStateService` to serialize/deserialize `ConversationContext`

**Rationale**: Hibernate's JSONB support requires either the Hypersistence Utils dependency or a custom `UserType`, both of which add complexity. Since `context` is only read/written by `UserStateService` (single consumer), a plain `String` column with Jackson ser/des in the service layer is simpler, fully testable, and avoids new dependencies. In PostgreSQL the column type is declared as `JSONB` in the Liquibase migration for indexing capability — Hibernate treats it as a String at the Java layer.

**Alternatives considered**:
- Hypersistence Utils `JsonType` — correct approach for large codebases, adds a dependency for one column.
- Custom `@TypeDef` Hibernate type — verbose, discouraged in Hibernate 6+.
- Separate context columns (taskId, date, messageId as individual columns) — simpler schema, but rigid; JSONB is the natural fit since context shape varies by state.

---

## Decision 4 — ConversationState Enum vs String

**Decision**: Java `enum ConversationState` (IDLE, CREATING_TASK, SELECTING_REMINDER_DATE, ENTERING_REMINDER_TIME, CONFIRMING_DELETE) stored as `VARCHAR` via `@Enumerated(EnumType.STRING)`

**Rationale**: Enum is type-safe, exhaustive in switch expressions (Java 21 pattern matching), and readable in the database. `EnumType.STRING` avoids ordinal brittleness on re-ordering.

---

## Decision 5 — Callback Data Prefix Scheme

**Decision**: Prefix-based scheme with `:` delimiter. All prefixes defined as constants in `BotConstants`.

| Prefix | Example | Handler |
|--------|---------|---------|
| `tz:` | `tz:Europe/Moscow` | `TimezoneCallbackHandler` (existing) |
| `TASK_REMIND:` | `TASK_REMIND:42` | `TaskActionCallbackHandler` |
| `TASK_DONE:` | `TASK_DONE:42` | `TaskActionCallbackHandler` |
| `TASK_DELETE:` | `TASK_DELETE:42` | `TaskActionCallbackHandler` |
| `CAL_DATE:` | `CAL_DATE:2026-04-15` | `CalendarCallbackHandler` |
| `CAL_NAV:` | `CAL_NAV:+1` or `CAL_NAV:-1` | `CalendarCallbackHandler` |
| `CONFIRM_DELETE:` | `CONFIRM_DELETE:42` | `DeleteConfirmCallbackHandler` |
| `CONFIRM_CANCEL` | `CONFIRM_CANCEL` | `DeleteConfirmCallbackHandler` |
| `TASKS_TAB:` | `TASKS_TAB:ACTIVE` or `TASKS_TAB:COMPLETED` | `TaskListTabCallbackHandler` |

**Rationale**: Prefix routing in `UpdateDispatcher.routeCallback()` is O(1) `startsWith` checks. All prefixes are constants — no magic strings outside `BotConstants`. 64-byte Telegram callback data limit: longest example is `CONFIRM_DELETE:9999999999` = 26 chars, well within limit.

**Alternatives considered**:
- State-driven routing (no prefix) — ambiguous; a bare `42` could mean "delete task 42" or "done task 42" depending on state, complicating tests.
- JSON payload — exceeds 64 bytes easily.

---

## Decision 6 — Calendar Navigation Month Tracking

**Decision**: Store `viewingYear` + `viewingMonth` in `ConversationContext` (in `UserState`). `CAL_NAV:±1` callbacks read the context to know the current displayed month.

**Rationale**: Calendar navigation is state-driven — the user is in `SELECTING_REMINDER_DATE` state, so context is already being used. Embedding absolute month in the callback data (`CAL_NAV:2026-05`) would work but tightly couples UI state to callback data. Storing in context is consistent with how `taskId` is tracked through the calendar flow.

---

## Decision 7 — Time Parsing Implementation

**Decision**: Custom `TimeParserService` with ordered regex matchers. No external NLP library.

**Supported formats and mapping**:

| Input pattern | Regex | Maps to |
|---------------|-------|---------|
| `HH:mm` | `^\d{1,2}:\d{2}$` | `LocalTime.of(H, m)` — validate 0–23 / 0–59 |
| `N утра` | `^(\d{1,2})\s+утра$` | N ∈ 1–11 → `LocalTime.of(N, 0)`; N=12 → reject |
| `N вечера` | `^(\d{1,2})\s+вечера$` | N ∈ 1–11 → `LocalTime.of(N+12, 0)`; N=12 → reject |
| `N:mm утра` | `^(\d{1,2}):(\d{2})\s+утра$` | N ∈ 1–11 |
| `N:mm вечера` | `^(\d{1,2}):(\d{2})\s+вечера$` | N ∈ 1–11 |

Returns `Optional<LocalTime>`. The caller (`ReminderTimeTextHandler`) checks empty → sends format hint; non-empty → proceeds.

**The 12 o'clock rule**: Inputs `12 утра` and `12 вечера` return `Optional.empty()`, triggering the special hint: `"Для 12 часов используй формат HH:mm (00:00 или 12:00)"`.

**Alternatives considered**:
- `DateTimeFormatter` with multiple format patterns — does not handle Russian "утра/вечера" keywords.
- External NLP/Duckling — massively over-engineered for 5 formats.

---

## Decision 8 — Stale State Cleanup

**Decision**: Lazy reset at the start of every incoming message/callback routing. `UserStateService.resetIfStale(telegramUserId)` checks `updatedAt`; if `> 24h` ago, resets state to IDLE and clears context.

**Rationale**: The spec requires this (FR-016). Lazy is simpler than a scheduled job — no new `@Scheduled` bean, no separate transaction, no polling overhead. Works correctly because a user who abandoned a flow 24h+ ago triggers cleanup on their very next message.

**Where to call**: At the top of `UpdateDispatcher.routeMessage()` and `UpdateDispatcher.routeCallback()` (both paths). In the callback path, relevant for `CONFIRMING_DELETE` and `SELECTING_REMINDER_DATE` which can go stale.

---

## Decision 9 — TaskService Additions

Three new methods required:

1. `getCompletedTasks(Long telegramUserId)` — mirrors `getActiveTasks` but filters by `COMPLETED` status.
2. `deleteTask(Long telegramUserId, Long taskId)` — hard DELETE, scoped to `telegramUserId` per §II (User Data Isolation).
3. `setReminderFromCalendar(Long telegramUserId, Long taskId, LocalDate date, LocalTime time)` — overload used by button flow; internally converts to UTC and delegates to existing persistence logic.

---

## Decision 10 — UpdateDispatcher Refactoring Strategy

The current dispatcher is a single `dispatch()` method. Refactoring approach:

- Extract `routeCallback(Update)` and `routeMessage(Update)` as private methods.
- `routeCallback()` — O(1) prefix dispatch to the appropriate callback handler.
- `routeMessage()` — in order: (1) stale state reset, (2) get current state, (3) if non-IDLE + persistent menu button → cancel flow, (4) if non-IDLE text → route to state handler, (5) if IDLE + menu button → route to button handler, (6) command routing unchanged.
- `isPersistentMenuButton(String text)` — helper that checks text against `BotConstants.BTN_NEW_TASK`, `BTN_MY_TASKS`, `BTN_REMINDER`.

This keeps `UpdateDispatcher` as a pure router (no business logic, §I), delegates cancellation notification to `UserStateService.cancelWithNotification()`.
