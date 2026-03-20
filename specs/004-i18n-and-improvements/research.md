# Research: 004-i18n-and-improvements

**Date**: 2026-03-20
**Branch**: `004-i18n-and-improvements`

---

## Decision 1 — MessageService Translation Storage

**Decision**: A `MessageKey` Java enum whose every constant carries both its `en` and `ru`
string. A thin `MessageService` Spring bean delegates to `key.get(language)`. No Spring
`MessageSource`, no `.properties` files.

**Rationale**: Translation set is closed and small (< 50 strings); all strings are known at
compile time; this approach is fully unit-testable without classpath setup; adding
`MessageSource` would introduce invisible magic that portfolio reviewers cannot easily trace.

**Alternatives considered**:
- Spring `MessageSource` + `.properties` files — rejected: adds classpath loading complexity,
  harder to test in isolation, unfamiliar to reviewers who haven't seen Spring i18n.
- `Map<String, Map<Language, String>>` in a singleton — rejected: `MessageKey` enum gives
  compile-time safety and eliminates stringly-typed key lookups.

---

## Decision 2 — Registration State Machine (chicken-and-egg resolution)

**Problem**: `UserState` has a FK to `users`. But to store `AWAITING_LANGUAGE` in
`UserState`, the `User` record must already exist. The old flow created `User` only after
timezone selection — so no intermediate state could be stored.

**Decision**: `/start` for a new user creates a minimal `User` row immediately
(`language = null`, `timezone = null`) and a `UserState` row (`AWAITING_LANGUAGE`).
Language callback updates `User.language` → `UserState` → `AWAITING_TIMEZONE`.
Timezone callback updates `User.timezone` → `UserState` → `IDLE`.

**Consequence**: Both `language` and `timezone` columns in `users` must be nullable for
the duration of registration. `isRegistered()` is redefined as:
`User exists AND timezone IS NOT NULL AND language IS NOT NULL`.

**RegistrationGuard routing**:
- No `User` row → send bilingual welcome + language buttons
- `User` exists, `timezone IS NULL` → send timezone keyboard in user's language
- Fully registered → allow command through

**Rationale**: Persisted state survives bot restarts. No in-memory session storage needed.
Consistent with existing `ConversationState` / `UserState` pattern.

**Alternatives considered**:
- In-memory language cache until timezone is chosen — rejected: state lost on restart;
  not production-appropriate.
- Create `User` on language callback (not on `/start`) — considered viable but `/start`
  is more natural; also means `AWAITING_LANGUAGE` in `UserState` can't exist until User
  is created at language step, leaving the brief window with no state.

---

## Decision 3 — ConversationState additions

**Decision**: Add two new values to the existing `ConversationState` enum:
`AWAITING_LANGUAGE` (first registration step) and `AWAITING_TIMEZONE` (second step).

**Rationale**: Keeps all routing through the existing `UpdateDispatcher` → callback handler
chain. No new routing mechanism needed.

---

## Decision 4 — ON DELETE CASCADE for tasks

**Finding**: Migration `004-create-user-states-table.xml` already adds `deleteCascade="true"`
on `user_states.telegram_user_id → users.telegram_user_id`. The `tasks` table (migration
`002`) does NOT have `deleteCascade` on its FK to `users`. This must be fixed.

**Decision**: Migration `006` drops and re-adds the FK on `tasks.telegram_user_id` with
`ON DELETE CASCADE`.

---

## Decision 5 — Timezone button label format

**Decision**: Labels rendered by `NotificationService` at display time (not static
constants) using `ZonedDateTime.now(ZoneId.of(tz))`. Format: `"HH:mm CODE"`.
City-code mappings live in `BotConstants.TIMEZONE_CITY_CODES` (English-only, static).
Clock fallback on error: show IANA identifier only (no crash).

**Alternatives considered**:
- Pre-compute times in `BotConstants` — rejected: times go stale immediately; must be
  computed fresh each time the keyboard is built.

---

## Decision 6 — Delete All Completed: callback routing

**Decision**: New callback prefix `CB_DELETE_ALL_COMPLETED = "DELETE_ALL:"`.
`UpdateDispatcher` routes it to a dedicated `DeleteAllCompletedCallbackHandler`. The
confirmation prompt reuses the existing two-button confirm/cancel inline keyboard pattern
(consistent with single-task delete confirmation already in the codebase).

**Rationale**: Consistent with existing `CB_CONFIRM_DELETE` / `CB_CONFIRM_CANCEL` pattern.
Separate handler preserves SRP.

---

## Decision 7 — Time parser extension

**Decision**: Extend `TimeParserService` with two additional regex patterns alongside the
existing `HH:MM` pattern:
- `"\\d{1,2} \\d{2}"` → space separator
- `"\\d{1,2}-\\d{2}"` → hyphen separator

All three patterns normalise to `HH:MM` before parsing.

---

## Decision 8 — Language immutability

**Decision**: No `/language` command or settings screen in this feature. Language set at
registration is permanent until a future feature adds language switching.

---

## Existing hardcoded strings requiring migration to MessageService

Identified from codebase exploration (Constitution v1.3.0 compliance):

| File | Hardcoded string |
|------|-----------------|
| `StartCommandHandler` | `"Welcome back! Use the buttons below."`, `"Welcome to Smart Task Bot!\nPlease select your timezone:"` |
| `TimezoneCallbackHandler` | `"Something went wrong. Please send /start to try again."` (×2), `"Timezone set: ... ✓\nYou're all set! ..."` |
| `RegistrationGuard` | `"Please select your timezone first:"` |
| `BotConstants` | `BTN_NEW_TASK`, `BTN_MY_TASKS` (button labels — must come from MessageService per language) |
| `UpdateDispatcher` | `"Пожалуйста, используй кнопки выше."`, `"Эта функция скоро появится!"` |
| `UnknownInputHandler` | Any hardcoded fallback message |
| `UserStateService` | Any cancel/stale-state messages |
| `NewTaskButtonHandler`, `TaskCreationTextHandler`, `ReminderTimeTextHandler` | All prompt strings |
| `NotificationService` | Any hardcoded button labels or prompts |

**All of the above MUST be replaced with `MessageService.get(MessageKey.X, user)` calls
before this branch is merged.**
