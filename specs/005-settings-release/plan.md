# Implementation Plan: Settings Menu and Release 1.0.0

**Branch**: `005-settings-release` | **Date**: 2026-03-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-settings-release/spec.md`

## Summary

Adds a ⚙️ Settings persistent-menu button with four options (Change Language, Change
Timezone, Reset State, Delete Account); adds inline action buttons to the task-creation
confirmation; performs a full Constitution v1.4.0 compliance cleanup (DTO/utility rename,
commons-lang3, StringUtils, dead-code removal); and bumps the project to release 1.0.0.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1,
Spring Data JPA, Liquibase, Lombok, commons-lang3 (to be added explicitly)
**Storage**: PostgreSQL 15+ — no new tables or columns in this branch
**Testing**: JUnit 5 + Mockito via spring-boot-starter-test
**Target Platform**: Linux server (Railway)
**Project Type**: Long-polling Telegram bot (Spring Boot application)
**Performance Goals**: None new — existing ~60s reminder polling unchanged
**Constraints**: Zero tolerance for hardcoded user-facing strings (Constitution X);
all class renames require same-commit update of all callers; project MUST compile and
all tests MUST pass after every rename
**Scale/Scope**: Single-user Telegram bot; no concurrency concerns

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle               | Status      | Notes                                                                                       |
|-------------------------|-------------|---------------------------------------------------------------------------------------------|
| I. Layered Architecture | ✅ Pass      | All new logic in handler/service; no repository access from handlers                        |
| II. User Data Isolation | ✅ Pass      | `deleteUser()` scoped to caller's userId; all queries include telegramUserId predicate      |
| III. SRP                | ✅ Pass      | New `SettingsCallbackHandler` owns all SETTINGS_* callbacks; existing handlers unchanged    |
| IV. Liquibase           | ✅ Pass      | No schema changes needed (VARCHAR storage, CASCADE already in place)                        |
| V. Secrets              | ✅ Pass      | No secrets involved                                                                         |
| VI. Simplicity          | ✅ Pass      | Settings handler reuses `UserService.updateLanguage/updateTimezone`; no duplication         |
| VII. Logging            | ✅ Pass      | All new methods must log at INFO on success, WARN on bad input, ERROR on exception          |
| VIII. Code Style        | ✅ Pass      | @UtilityClass conversions, constructor injection preserved, @DisplayName ordering           |
| IX. Testing             | ✅ Pass      | Every new handler and method covered by @Nested unit tests                                  |
| X. Application Language | ✅ Pass      | All new strings via MessageService; SETTINGS_* MessageKey constants required                |
| Dev Std #3              | ✅ Pass      | CreateTaskRequest (dead code) deleted; TZ_CALLBACK_PREFIX (@Deprecated) deleted             |
| Dev Std #7              | ✅ Pass      | CreateTaskRequest is dead and deleted; no rename needed                                     |
| Dev Std #8              | ✅ Pass      | CalendarKeyboardBuilder → CalendarKeyboardBuilderUtils; TimeParserService → TimeParserUtils |
| Dev Std #9              | ✅ Pass      | No XML comments found in any Liquibase migration (already compliant)                        |
| Dev Std #10             | ⚠ Violation | commons-lang3 not explicit in pom.xml; manual null/blank checks exist — must be fixed       |

**GATE: One open violation (Dev Std #10). Resolved in Phase 4 before release.**

## Project Structure

### Documentation (this feature)

```text
specs/005-settings-release/
├── plan.md              ← this file
├── research.md          ← Phase 0 output (below)
├── data-model.md        ← Phase 1 output (below)
├── checklists/
│   └── requirements.md  ← already written
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code — affected files

```text
src/main/java/ru/zahaand/smarttaskbot/
├── config/
│   └── BotConstants.java                          MODIFY — add SETTINGS_* constants, add @UtilityClass, remove @Deprecated TZ_CALLBACK_PREFIX
├── model/
│   ├── ConversationState.java                     MODIFY — add CONFIRMING_DELETE_ACCOUNT
│   └── MessageKey.java                            MODIFY — add 10+ new constants for Settings
├── dto/
│   ├── CreateTaskRequest.java                     DELETE  — dead code (never referenced outside its own file)
│   └── ConversationContext.java                   no change
├── handler/
│   ├── UpdateDispatcher.java                      MODIFY — add SettingsCallbackHandler wiring + routeCallback + routeMenuButton + isPersistentMenuButton
│   ├── callback/
│   │   ├── SettingsCallbackHandler.java           CREATE  — handles SETTINGS_* callbacks; handleTimezoneRequest() sets AWAITING_TIMEZONE
│   │   ├── TimezoneCallbackHandler.java           MODIFY  — replace TZ_CALLBACK_PREFIX with CB_TZ; add settings-context detection
│   │   └── [all others]                           no change
│   └── text/
│       └── TaskCreationTextHandler.java           MODIFY  — add inline action buttons to creation confirmation
├── service/
│   ├── CalendarKeyboardBuilderUtils.java          RENAME+REFACTOR from CalendarKeyboardBuilder (@UtilityClass, static methods)
│   ├── TimeParserUtils.java                       RENAME+REFACTOR from TimeParserService (@UtilityClass, static methods)
│   ├── NotificationService.java                   MODIFY  — remove CalendarKeyboardBuilder injection; add sendSettingsMenu(), sendTaskCreatedWithActions(); update sendPersistentMenu() for 3rd button; update editCalendar/sendCalendar to use static CalendarKeyboardBuilderUtils
│   ├── TaskListKeyboardBuilder.java               no change (has MessageService injection — NOT eligible for @UtilityClass)
│   ├── UserService.java                           MODIFY  — add deleteUser(Long telegramUserId)
│   └── [others]                                   StringUtils replacements only
├── handler/text/
│   └── ReminderTimeTextHandler.java               MODIFY  — replace TimeParserService injection with static TimeParserUtils call

src/test/java/ru/zahaand/smarttaskbot/
├── handler/callback/
│   └── SettingsCallbackHandlerTest.java           CREATE
├── service/
│   ├── NotificationServiceTest.java               MODIFY  — remove CalendarKeyboardBuilder mock; add tests for sendSettingsMenu, sendTaskCreatedWithActions
│   └── TimeParserUtilsTest.java                   RENAME  from TimeParserServiceTest (class under test renamed)

pom.xml                                            MODIFY  — add commons-lang3, bump version 0.0.1-SNAPSHOT → 1.0.0
railway.toml                                       MODIFY  — update startCommand to exact 1.0.0 JAR name
README.md                                          MODIFY  — add v1.0.0 badge, Settings section, updated examples
```

## Complexity Tracking

| Violation                                                                                                  | Why Needed                                                                                       | Simpler Alternative Rejected Because                                                                                |
|------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `SettingsCallbackHandler` replicates some logic from `LanguageCallbackHandler` / `TimezoneCallbackHandler` | Settings context requires different post-action flow (no state advance, no registration handoff) | Sharing via a flag parameter would pollute the registration handlers with settings-context branching, violating SRP |

---

## Phase 0: Research

All technical decisions are resolved from codebase inspection. No external research required.

### Decision Log

| Decision                                    | Choice                                                                                                          | Rationale                                                                                                                                                                                       |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Settings handler architecture               | Single `SettingsCallbackHandler` handles all `SETTINGS_*` callbacks                                             | Follows the existing pattern (one handler per callback group). Clear SRP. No state-machine complexity.                                                                                          |
| Language/Timezone reuse via Settings        | New dedicated code in `SettingsCallbackHandler` that calls the same `UserService.updateLanguage/updateTimezone` | Existing handlers embed registration state transitions (`AWAITING_TIMEZONE`, persistent menu send) incompatible with settings context. Reusing at service layer (not handler layer) is cleaner. |
| `CalendarKeyboardBuilder` → `@UtilityClass` | Convert + rename to `CalendarKeyboardBuilderUtils`; remove from `NotificationService` constructor               | No instance state, no injected deps — pure builder. Static call removes circular-injection risk. Test updated to remove the mock parameter.                                                     |
| `TimeParserService` → `@UtilityClass`       | Convert + rename to `TimeParserUtils`; update `ReminderTimeTextHandler` to static call                          | No instance state, no injected deps. `@Slf4j` not used inside util methods — remove it. Existing `TimeParserServiceTest` → `TimeParserUtilsTest`.                                               |
| `CreateTaskRequest`                         | DELETE (dead code, FR-014)                                                                                      | Never referenced outside its own class definition. Renaming to `CreateTaskRequestDto` and then keeping unused code would violate Dev Std #3.                                                    |
| Post-creation keyboard                      | New `sendTaskCreatedWithActions(Long chatId, Long taskId, String taskText, Language)` in `NotificationService`  | Handler layer must stay thin; keyboard construction belongs in the service layer. Reuses existing `CB_TASK_REMIND`, `CB_TASK_DONE`, `CB_TASK_DELETE` constants.                                 |
| Settings persistent-menu button             | `ReplyKeyboardMarkup` text button ("⚙️ Settings" / "⚙️ Настройки") — matches existing pattern                   | Persistent menu is already `ReplyKeyboardMarkup`; Settings should be consistent. `UpdateDispatcher.isPersistentMenuButton()` and `routeMenuButton()` updated to recognize it.                   |
| Delete Account in Settings                  | New `ConversationState.CONFIRMING_DELETE_ACCOUNT` + `userService.deleteUser()`                                  | Mirrors existing `CONFIRMING_DELETE` pattern. No Liquibase migration needed (VARCHAR storage).                                                                                                  |
| `BotConstants` `@UtilityClass`              | Add `@UtilityClass`, remove explicit `private BotConstants()` constructor                                       | Already utility-class shaped. `@UtilityClass` makes it explicit and removes the manual private constructor.                                                                                     |
| Liquibase XML comments                      | No action — already compliant                                                                                   | `grep "<!--"` on all migration XMLs returned no matches.                                                                                                                                        |
| `railway.toml` startCommand                 | Update from `java -jar target/smart-task-bot-*.jar` to exact `java -jar target/smart-task-bot-1.0.0.jar`        | Wildcard currently works, but exact name is required by FR-021 for reproducible deploys.                                                                                                        |
| commons-lang3 version                       | Use the version managed by Spring Boot 3.5.x BOM (3.17.0)                                                       | Already on transitive classpath via Spring Boot; declaring explicitly without `<version>` defers to BOM.                                                                                        |

---

## Phase 1: Data Model

### New `ConversationState` values

```java
// model/ConversationState.java — append:
CONFIRMING_DELETE_ACCOUNT
```

No database migration required (`state VARCHAR(50)`).

### New `MessageKey` constants (all require EN + RU strings)

| Constant                         | EN string                                                          | RU string                                                           |
|----------------------------------|--------------------------------------------------------------------|---------------------------------------------------------------------|
| `BTN_SETTINGS`                   | `⚙️ Settings`                                                      | `⚙️ Настройки`                                                      |
| `SETTINGS_TITLE`                 | `⚙️ Settings`                                                      | `⚙️ Настройки`                                                      |
| `BTN_CHANGE_LANGUAGE`            | `🌐 Change Language`                                               | `🌐 Сменить язык`                                                   |
| `BTN_CHANGE_TIMEZONE`            | `🕐 Change Timezone`                                               | `🕐 Сменить часовой пояс`                                           |
| `BTN_RESET_STATE`                | `🔄 Reset State`                                                   | `🔄 Сбросить состояние`                                             |
| `BTN_DELETE_ACCOUNT`             | `🗑 Delete Account`                                                | `🗑 Удалить аккаунт`                                                |
| `SETTINGS_LANGUAGE_CHANGED`      | `✅ Language updated.`                                              | `✅ Язык обновлён.`                                                  |
| `SETTINGS_TIMEZONE_CHANGED`      | `✅ Timezone updated to %s.`                                        | `✅ Часовой пояс обновлён: %s.`                                      |
| `SETTINGS_STATE_RESET`           | `🔄 State reset. You're back to the main menu.`                    | `🔄 Состояние сброшено. Вы вернулись в главное меню.`               |
| `SETTINGS_DELETE_CONFIRM_PROMPT` | `⚠️ Delete your account and all data? This cannot be undone.`      | `⚠️ Удалить аккаунт и все данные? Это действие нельзя отменить.`    |
| `SETTINGS_ACCOUNT_DELETED`       | `👋 Your account has been deleted. Send /start to register again.` | `👋 Аккаунт удалён. Отправь /start чтобы зарегистрироваться снова.` |
| `SETTINGS_DELETE_CANCELLED`      | `✅ Deletion cancelled.`                                            | `✅ Удаление отменено.`                                              |
| `TASK_CREATED_WITH_ACTIONS`      | `✅ Task #%d created: %s`                                           | `✅ Задача #%d создана: %s`                                          |

### New `BotConstants` values

```java
// SETTINGS_* callback prefixes
public static final String CB_SETTINGS_MENU       = "SETTINGS_MENU";
public static final String CB_SETTINGS_LANG       = "SETTINGS_LANG:";        // + EN or RU
public static final String CB_SETTINGS_TZ_REQUEST = "SETTINGS_TZ_REQUEST";   // show timezone keyboard
public static final String CB_SETTINGS_RESET      = "SETTINGS_RESET";
public static final String CB_SETTINGS_DEL_REQ    = "SETTINGS_DELETE_REQUEST";
public static final String CB_SETTINGS_DEL_CFM    = "SETTINGS_DELETE_CONFIRM";
public static final String CB_SETTINGS_DEL_CNC    = "SETTINGS_DELETE_CANCEL";
// Timezone selection reuses existing CB_TZ prefix handled by TimezoneCallbackHandler
```

Also: remove `@Deprecated TZ_CALLBACK_PREFIX` field; add `@UtilityClass`.

### New `UserService.deleteUser()`

```java
@Transactional
public void deleteUser(Long telegramUserId) {
    userRepository.deleteById(telegramUserId);
    log.info("Account deleted: userId={}", telegramUserId);
}
```

CASCADE configured in migrations 004 + 006 removes `user_states` and `tasks` automatically.

### `SettingsCallbackHandler` — method map

| Callback data                           | Method                    | Action                                                                                                                                                              |
|-----------------------------------------|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SETTINGS_MENU`                         | `handleMenu()`            | Answer query + send Settings message with 4 inline buttons                                                                                                          |
| `SETTINGS_LANG:EN` / `SETTINGS_LANG:RU` | `handleLanguageChange()`  | Set state IDLE, update language via `UserService.updateLanguage()`, send `SETTINGS_LANGUAGE_CHANGED` + persistent menu                                              |
| `SETTINGS_TZ_REQUEST`                   | `handleTimezoneRequest()` | Set state `AWAITING_TIMEZONE`, send timezone-selection keyboard (reuses existing keyboard builder via `notificationService.sendTimezoneKeyboard(chatId, language)`) |
| `SETTINGS_RESET`                        | `handleReset()`           | Set state IDLE, send `SETTINGS_STATE_RESET` + persistent menu                                                                                                       |
| `SETTINGS_DELETE_REQUEST`               | `handleDeleteRequest()`   | Set state `CONFIRMING_DELETE_ACCOUNT`, send `SETTINGS_DELETE_CONFIRM_PROMPT` with Confirm/Cancel buttons                                                            |
| `SETTINGS_DELETE_CONFIRM`               | `handleDeleteConfirm()`   | Call `userService.deleteUser()`, send `SETTINGS_ACCOUNT_DELETED` (plain message, no menu; do NOT set state after deletion — CASCADE removes user_states row)        |
| `SETTINGS_DELETE_CANCEL`                | `handleDeleteCancel()`    | Set state IDLE, send `SETTINGS_DELETE_CANCELLED` + persistent menu                                                                                                  |

**Timezone selection continuation**: after `handleTimezoneRequest()` sets state `AWAITING_TIMEZONE`,
the user selects a timezone from the keyboard — generating a `CB_TZ:IANA` callback routed to
`TimezoneCallbackHandler`. That handler updates the timezone of the already-registered user
(existing `UserService.updateTimezone()`) and sends `SETTINGS_TIMEZONE_CHANGED.formatted(tz)` +
persistent menu + sets state IDLE. No duplicate-user risk: `updateTimezone()` modifies the
existing record; it does not create a new one.

**State reset before settings flow**: `handleLanguageChange()`, `handleTimezoneRequest()`,
`handleReset()`, and `handleDeleteRequest()` each set/reset conversation state explicitly as the
first step, ensuring any in-progress flow is cancelled before the settings action begins.

### `NotificationService` — new and modified methods

| Method                                                                            | Change                                                                                                               |
|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `sendPersistentMenu(Long, String, Language)`                                      | Add 3rd row button using `BTN_SETTINGS` MessageKey                                                                   |
| `sendSettingsMenu(Long, Language)`                                                | NEW — sends Settings message with 4 inline option buttons                                                            |
| `sendTaskCreatedWithActions(Long chatId, Long taskId, String taskText, Language)` | NEW — sends creation confirmation with Remind/Complete/Delete inline keyboard                                        |
| `sendCalendar(Long, int, int, Language)`                                          | Remove `calendarKeyboardBuilder.buildCalendar()` call; use `CalendarKeyboardBuilderUtils.buildCalendar()` statically |
| `editCalendar(Long, Integer, int, int, Language)`                                 | Same static call update                                                                                              |
| Constructor                                                                       | Remove `CalendarKeyboardBuilder` parameter (field + wiring removed)                                                  |

### `UpdateDispatcher` — routing changes

```java
// routeCallback(): add before the "Unrecognised callback" fallback:
if (data.startsWith(BotConstants.CB_SETTINGS_LANG)
        || data.equals(BotConstants.CB_SETTINGS_TZ_REQUEST)
        || data.equals(BotConstants.CB_SETTINGS_MENU)
        || data.equals(BotConstants.CB_SETTINGS_RESET)
        || data.equals(BotConstants.CB_SETTINGS_DEL_REQ)
        || data.equals(BotConstants.CB_SETTINGS_DEL_CFM)
        || data.equals(BotConstants.CB_SETTINGS_DEL_CNC)) {
    settingsCallbackHandler.handle(update);
    return;
}
// Note: CB_TZ:IANA callbacks from the timezone keyboard (shown after SETTINGS_TZ_REQUEST)
// continue to be routed to TimezoneCallbackHandler — no routing change needed there.

// isPersistentMenuButton(): add BTN_SETTINGS EN + RU checks
// routeMenuButton(): Settings text button handling:
//   1. Reset conversation state to IDLE if non-IDLE (cancel any in-progress flow)
//   2. Call notificationService.sendSettingsMenu(chatId, language)
```

**Note**: The Settings persistent-menu button sends text "⚙️ Settings" or "⚙️ Настройки".
`routeMenuButton()` MUST reset state to IDLE before calling `sendSettingsMenu()` — this
cancels any in-progress flow (task creation, reminder entry, etc.) consistent with the spec
edge case: "Settings actions reset the conversation state to IDLE before initiating the
settings flow." No separate handler class is needed — the inline button options are
handled by `SettingsCallbackHandler` and (for timezone selection) `TimezoneCallbackHandler`.

### `TaskCreationTextHandler` changes

Remove the plain `sendMessage()` call for task creation success. Replace with:

```java
notificationService.sendTaskCreatedWithActions(chatId, created.getId(), created.getText(), user.getLanguage());
```

### `TimezoneCallbackHandler` changes

1. Replace `BotConstants.TZ_CALLBACK_PREFIX` → `BotConstants.CB_TZ` in the `startsWith` check.
2. Add context-aware post-update logic: after calling `UserService.updateTimezone()`, detect
   whether the user is already registered (has an existing timezone value, i.e., was redirected
   from Settings):
    - **Settings context** (user already registered): send `SETTINGS_TIMEZONE_CHANGED.formatted(tz)`
        + persistent menu + set state IDLE.
    - **Registration context** (user has no timezone yet): continue existing registration flow
      (send persistent menu for the first time, set state IDLE).
      Detection: check whether `user.getTimezone()` was non-null before the update, or equivalently
      check `ConversationState` — if state was `AWAITING_TIMEZONE` and user is already fully
      registered (has language set), treat as settings context.

---

## Phase 2: Contracts

This feature has no external API or interface contracts beyond the Telegram Bot API,
which is already defined by the SDK. No contracts/ artifacts required.

---

## Implementation Phases (execution order)

### Phase A — Foundation (no user-visible change; must be first)

1. Add `CONFIRMING_DELETE_ACCOUNT` to `ConversationState`.
2. Add all new `MessageKey` constants (EN + RU strings).
3. Add all `CB_SETTINGS_*` constants to `BotConstants` (including `CB_SETTINGS_TZ_REQUEST`);
   add `@UtilityClass`; remove `TZ_CALLBACK_PREFIX`.
4. Fix `TimezoneCallbackHandler`: replace `TZ_CALLBACK_PREFIX` → `CB_TZ`; add context-aware
   post-update logic (settings context vs. registration context, see Phase 1 §TimezoneCallbackHandler).
5. Add `userService.deleteUser(Long)`.

### Phase B — Settings Menu (User Story 1)

6. Create `SettingsCallbackHandler` with all 7 `handle*()` methods (including `handleTimezoneRequest()`
   replacing the former `handleTimezoneChange()`).
7. Update `NotificationService`: `sendPersistentMenu()` 3rd button; new `sendSettingsMenu()`.
8. Update `UpdateDispatcher`:
    - Wire `SettingsCallbackHandler`; update `routeCallback()` (use `CB_SETTINGS_TZ_REQUEST`).
    - Update `isPersistentMenuButton()` (add BTN_SETTINGS EN + RU).
    - Update `routeMenuButton()`: reset state to IDLE before calling `sendSettingsMenu()`.
9. Write `SettingsCallbackHandlerTest` with `@Nested` per method; include stale-state and
   `handleTimezoneRequest()` scenarios.

### Phase C — Post-creation Action Buttons (User Story 2)

10. Add `sendTaskCreatedWithActions()` to `NotificationService`.
11. Update `TaskCreationTextHandler` to call the new method.
12. Update `NotificationServiceTest` with new test for `sendTaskCreatedWithActions`.
13. Update `TaskCreationTextHandlerTest` (if it exists) or create it.

### Phase D — Code Quality Cleanup (User Story 3)

14. Delete `CreateTaskRequest.java` (dead code — never referenced).
15. Convert `CalendarKeyboardBuilder` → `CalendarKeyboardBuilderUtils`:
    - Rename file, add `@UtilityClass`, make all methods `static`, remove `@Component`.
    - Update `NotificationService`: remove field + constructor param; use static calls.
    - Update `NotificationServiceTest`: remove `@Mock CalendarKeyboardBuilder`; update constructor call.
16. Convert `TimeParserService` → `TimeParserUtils`:
    - Rename file, add `@UtilityClass`, make all methods `static`, remove `@Service` + `@Slf4j`.
    - Update `ReminderTimeTextHandler`: remove injection; call `TimeParserUtils.parse()` +
      `TimeParserUtils.isTwelveOClockAmbiguous()` statically.
    - Rename `TimeParserServiceTest` → `TimeParserUtilsTest`.
17. Add `commons-lang3` to `pom.xml` (no `<version>` tag — managed by Spring Boot BOM).
18. Replace all manual string null/blank checks with `StringUtils` equivalents:
    - `TimeParserUtils.java` (2 occurrences): `StringUtils.isBlank(input)`
    - `TaskService.java`: `StringUtils.isBlank(text)` + `StringUtils.length(text) > 500`
    - `TaskCreationTextHandler.java`: `StringUtils.isBlank(text)`
    - `UpdateDispatcher.java`: `StringUtils.startsWith(text, "/")` and `StringUtils.defaultString(text)`
    - `UserStateService.java`: `StringUtils.isNotBlank(json)`
      Audit for `CollectionUtils` (Constitution Dev Std #10): grep for manual `.isEmpty()` / `== null`
      checks on collections/lists across service and handler classes; replace with
      `CollectionUtils.isEmpty()` where applicable.
19. Run `mvn test` — all tests must pass.

### Phase E — Release 1.0.0 (User Story 4)

20. Bump `pom.xml` version: `0.0.1-SNAPSHOT` → `1.0.0`.
21. Update `railway.toml` `startCommand`: `java -jar target/smart-task-bot-*.jar` →
    `java -jar target/smart-task-bot-1.0.0.jar`.
    Verify `[build]` section contains only `buildCommand` — no explicit `builder` key
    (Constitution Dev Std #6).
22. Update README: add v1.0.0 badge, Settings section, update feature examples.
23. Final constitution compliance review.
24. Run `mvn test` — all tests must pass on the 1.0.0 codebase (satisfies SC-006).
25. Run `mvn clean package` — verify JAR name is `smart-task-bot-1.0.0.jar`.
26. Deploy to Railway; smoke-test: send `/start` to the bot and confirm it responds correctly
    with no startup errors in Railway logs (satisfies SC-007).

---

## Risk & Dependency Notes

| Risk                                                                                                                     | Mitigation                                                                                                                                                                                             |
|--------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CalendarKeyboardBuilder` → static breaks `NotificationService` constructor injection used in its test                   | `NotificationServiceTest` manually constructs `NotificationService`; remove the `CalendarKeyboardBuilder` mock parameter when removing it from the constructor. Done in Phase D step 15.               |
| `TZ_CALLBACK_PREFIX` deletion breaks `TimezoneCallbackHandler`                                                           | Fixed in Phase A step 4 before the deprecated field is removed.                                                                                                                                        |
| `TimezoneCallbackHandler` context detection — settings vs. registration                                                  | Detect by checking whether `user.getTimezone()` was non-null (already registered). `updateTimezone()` modifies the existing record; no duplicate-user risk.                                            |
| `SETTINGS_TZ_REQUEST` routing not in `CB_TZ` path                                                                        | `routeCallback()` only routes `CB_SETTINGS_TZ_REQUEST` to `SettingsCallbackHandler`; subsequent `CB_TZ:IANA` callbacks continue to route to `TimezoneCallbackHandler` as before — no routing conflict. |
| `sendPersistentMenu()` 3rd button changes layout for all existing tests                                                  | Update any test that asserts the persistent menu keyboard shape (row/button count).                                                                                                                    |
| Post-creation buttons reuse `CB_TASK_REMIND/DONE/DELETE` — `TaskActionCallbackHandler` must handle stale task gracefully | Already handles `NoSuchElementException` per existing code — no change needed.                                                                                                                         |
| `StringUtils.startsWith(text, "/")` semantics differ from `text.startsWith("/")` when text is null                       | `StringUtils.startsWith(null, "/")` returns false — same behaviour as `text != null && text.startsWith("/")`. Safe.                                                                                    |
