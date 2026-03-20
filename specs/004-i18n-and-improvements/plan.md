# Implementation Plan: i18n, UX Improvements, and Professional Polish

**Branch**: `004-i18n-and-improvements` | **Date**: 2026-03-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-i18n-and-improvements/spec.md`

## Summary

Add bilingual support (EN/RU) to the Smart Task Bot via a `MessageKey` enum and a
`MessageService` bean that resolves all user-facing strings at runtime using the user's
stored language preference. Language is selected during a new first registration step
(`AWAITING_LANGUAGE` → `AWAITING_TIMEZONE` → `IDLE`). Two Liquibase migrations add the
`language` column to `users` and ON DELETE CASCADE to `tasks`. All existing hardcoded
strings in handlers and services are replaced with `MessageService` calls. Additional
UX improvements: extended time parsing (`HH MM`, `HH-MM`), bulk delete of completed
tasks, improved timezone button labels. P3 items: bilingual Javadoc and bilingual README.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5, TelegramBots Spring Boot Starter 6.9.7.1,
Spring Data JPA, Liquibase, Lombok
**Storage**: PostgreSQL 15+ — two new migrations (no new tables)
**Testing**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
**Target Platform**: Linux server (Railway)
**Performance Goals**: N/A — no throughput targets; all operations are user-initiated
**Constraints**: All user-facing strings through `MessageService`; no Spring MessageSource
**Scale/Scope**: Single-instance bot; small user base (portfolio project)

## Constitution Check

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Layered Architecture | ✅ | `MessageService` in service layer; all new handlers delegate only |
| II. User Data Isolation | ✅ | `deleteAllCompleted(telegramUserId)` scoped; `isRegistered` per userId |
| III. SRP | ✅ | `LanguageCallbackHandler`, `MessageService`, `DeleteAllCompletedCallbackHandler` each single-responsibility |
| IV. Liquibase Migrations | ✅ | Migrations 005 and 006; rollback blocks required on both |
| V. Secrets via Env Vars | ✅ | No new secrets introduced |
| VI. Simplicity | ✅ | No Spring MessageSource; no external config files; enum-based i18n |
| VII. Logging | ✅ | All new handlers use `@Slf4j`; DEBUG/INFO/WARN/ERROR per standard |
| VIII. Code Style | ✅ | Constructors after fields; ≤1 consecutive blank line; no `@Autowired` fields |
| IX. Testing | ✅ | `@ExtendWith(MockitoExtension.class)`; `@Nested` per method; `@DisplayName` first annotation |
| X. Application Language | ✅ | All strings via `MessageService`; no hardcoded user-facing text in handlers/services |
| Dev Standard #6 (railway.toml) | ✅ | No builder specified in `railway.toml` |

**No violations. Gate passed.**

## Project Structure

### Documentation (this feature)

```text
specs/004-i18n-and-improvements/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (affected paths)

```text
src/main/java/ru/zahaand/smarttaskbot/
├── model/
│   ├── Language.java                          [NEW — enum EN/RU]
│   ├── ConversationState.java                 [MOD — add AWAITING_LANGUAGE, AWAITING_TIMEZONE]
│   └── User.java                              [MOD — add language field; timezone nullable]
├── config/
│   └── BotConstants.java                      [MOD — add CB_LANG, CB_DELETE_ALL_*, TIMEZONE_CITY_CODES]
├── service/
│   ├── MessageKey.java                        [NEW — enum of all message keys with EN+RU strings]
│   ├── MessageService.java                    [NEW — get(MessageKey, Language/User) : String]
│   ├── UserService.java                       [MOD — register(userId, username); update language; update timezone; isRegistered()]
│   ├── UserStateService.java                  [MOD — transition() handles AWAITING_LANGUAGE, AWAITING_TIMEZONE]
│   ├── TaskService.java                       [MOD — add deleteAllCompleted(telegramUserId); add countCompleted(telegramUserId)]
│   ├── ReminderService.java                   [MOD — resolve reminder text via MessageService in user's language]
│   ├── TimeParserService.java                 [MOD — add HH MM and HH-MM patterns]
│   ├── NotificationService.java               [MOD — sendTimezoneKeyboard accepts language; sendLanguageKeyboard(); timezone labels with time+city codes]
│   ├── CalendarKeyboardBuilder.java           [MOD — accept user for language-aware labels if needed]
│   └── TaskListKeyboardBuilder.java           [MOD — add Delete All button on Completed tab]
├── handler/
│   ├── RegistrationGuard.java                 [MOD — route to language selection or timezone selection based on registration stage]
│   ├── UpdateDispatcher.java                  [MOD — add lang: routing to LanguageCallbackHandler; route DELETE_ALL: to new handler]
│   ├── callback/
│   │   ├── LanguageCallbackHandler.java       [NEW — handles lang:EN / lang:RU]
│   │   ├── TimezoneCallbackHandler.java       [MOD — update timezone only (user already exists); use MessageService]
│   │   ├── DeleteAllCompletedCallbackHandler.java [NEW — handles DELETE_ALL:confirm / DELETE_ALL:cancel]
│   │   ├── TaskListTabCallbackHandler.java    [MOD — inject MessageService; pass user for labels]
│   │   ├── DeleteConfirmCallbackHandler.java  [MOD — use MessageService for messages]
│   │   ├── CalendarCallbackHandler.java       [MOD — use MessageService]
│   │   └── TaskActionCallbackHandler.java     [MOD — use MessageService]
│   ├── command/
│   │   ├── StartCommandHandler.java           [MOD — create User+UserState on new user; bilingual welcome; use MessageService for returning users]
│   │   ├── HelpCommandHandler.java            [MOD — use MessageService]
│   │   ├── NewTaskCommandHandler.java         [MOD — use MessageService]
│   │   ├── TaskListCommandHandler.java        [MOD — use MessageService]
│   │   ├── DoneCommandHandler.java            [MOD — use MessageService]
│   │   ├── RemindCommandHandler.java          [MOD — use MessageService]
│   │   └── UnknownInputHandler.java           [MOD — use MessageService]
│   └── text/
│       ├── NewTaskButtonHandler.java          [MOD — use MessageService]
│       ├── TaskCreationTextHandler.java       [MOD — use MessageService]
│       ├── ReminderTimeTextHandler.java       [MOD — use MessageService]
│       └── TaskListButtonHandler.java         [MOD — use MessageService]

src/main/resources/db/changelog/
├── 005-add-language-to-users.xml              [NEW]
├── 006-add-cascade-to-tasks.xml               [NEW]
└── db.changelog-master.xml                    [MOD — include 005 and 006]

src/test/java/ru/zahaand/smarttaskbot/
├── service/
│   ├── MessageServiceTest.java                [NEW]
│   ├── TimeParserServiceTest.java             [MOD — add HH MM, HH-MM cases]
│   └── TaskServiceTest.java                   [MOD — add deleteAllCompleted test]
├── handler/
│   ├── command/
│   │   └── StartCommandHandlerTest.java       [MOD — add new-user bilingual flow; returning-user flow]
│   └── callback/
│       ├── LanguageCallbackHandlerTest.java   [NEW]
│       └── DeleteAllCompletedCallbackHandlerTest.java [NEW]
```

## Implementation Phases

### Phase 1 — Foundation: Schema and Model (no logic yet)

**Goal**: All new DB columns and Java types exist. No handler changes yet.

**Step 1.1 — Migration 005: Add `language` to users, make `timezone` nullable**

File: `src/main/resources/db/changelog/005-add-language-to-users.xml`

```xml
<changeSet id="005-add-language-to-users" author="a.zakharenkov">

  <!-- Make timezone nullable for two-step registration -->
  <modifyDataType tableName="users" columnName="timezone" newDataType="VARCHAR(50)"/>
  <addNotNullConstraint tableName="users" columnName="timezone"
      constraintName="..." enabled="false"/>
  <!-- More precisely: dropNotNullConstraint -->
  <dropNotNullConstraint tableName="users" columnName="timezone"/>

  <!-- Add language column, nullable initially -->
  <addColumn tableName="users">
    <column name="language" type="VARCHAR(2)"/>
  </addColumn>

  <!-- Backfill existing users -->
  <update tableName="users">
    <column name="language" value="EN"/>
    <where>language IS NULL</where>
  </update>

  <!-- Now enforce NOT NULL -->
  <addNotNullConstraint tableName="users" columnName="language"
      defaultNullValue="EN"/>

  <rollback>
    <dropColumn tableName="users" columnName="language"/>
    <addNotNullConstraint tableName="users" columnName="timezone"/>
  </rollback>
</changeSet>
```

**Step 1.2 — Migration 006: ON DELETE CASCADE on tasks**

File: `src/main/resources/db/changelog/006-add-cascade-to-tasks.xml`

Drop existing FK on `tasks.telegram_user_id` → `users`, re-add with `ON DELETE CASCADE`.
Include rollback that restores the non-cascade FK.

**Step 1.3 — Update `db.changelog-master.xml`**

Include both new changesets in order after 004.

**Step 1.4 — `Language` enum**

```java
package ru.zahaand.smarttaskbot.model;

public enum Language {
    EN, RU
}
```

**Step 1.5 — Update `ConversationState` enum**

Add `AWAITING_LANGUAGE` and `AWAITING_TIMEZONE` before `IDLE` (first two values; ordering
in the enum does not affect DB storage which is VARCHAR).

**Step 1.6 — Update `User` entity**

Add field:
```java
@Column(name = "language", nullable = true, length = 2)
@Enumerated(EnumType.STRING)
private Language language;
```
Keep `timezone` but remove `nullable = false` from `@Column`.

---

### Phase 2 — MessageService: i18n Layer

**Goal**: All EN/RU strings centralized. No handlers changed yet.

**Step 2.1 — `MessageKey` enum**

`src/main/java/ru/zahaand/smarttaskbot/service/MessageKey.java`

Enum constants (see data-model.md for full list). Each constant takes `(String en, String ru)`
constructor arguments and exposes:

```java
public String get(Language language) {
    return language == Language.RU ? ru : en;
}
```

Constants required (minimum set — expand as handlers are migrated):

| Constant | EN | RU |
|---|---|---|
| `WELCOME_BILINGUAL` | Full bilingual welcome block | (same — both languages in one string) |
| `SELECT_TIMEZONE` | "Please select your timezone:" | "Выберите часовой пояс:" |
| `ALREADY_REGISTERED` | "You are already registered." | "Вы уже зарегистрированы." |
| `TIMEZONE_CONFIRMED` | "Timezone set: %s ✓\nYou're all set! Use the buttons below." | "Часовой пояс: %s ✓\nВы готовы! Используйте кнопки ниже." |
| `ENTER_TASK_DESCRIPTION` | "Enter task description:" | "Введите описание задачи:" |
| `CHOOSE_REMINDER_DATE` | "Choose reminder date:" | "Выберите дату напоминания:" |
| `ENTER_REMINDER_TIME` | "Enter reminder time (HH:MM, HH MM, or HH-MM):" | "Введите время (ЧЧ:ММ, ЧЧ ММ или ЧЧ-ММ):" |
| `TASK_CREATED` | "Task created ✓" | "Задача создана ✓" |
| `TASK_REMINDER_SET` | "Reminder set for %s ✓" | "Напоминание на %s ✓" |
| `DELETE_CONFIRM_SINGLE` | "Delete this task? This cannot be undone." | "Удалить задачу? Это нельзя отменить." |
| `DELETE_CONFIRM_ALL` | "Delete all %d completed tasks? This cannot be undone." | "Удалить %d выполненных задач? Это нельзя отменить." |
| `TASK_DELETED` | "Task deleted." | "Задача удалена." |
| `ALL_COMPLETED_DELETED` | "All completed tasks deleted." | "Все выполненные задачи удалены." |
| `NO_COMPLETED_TASKS` | "No completed tasks." | "Нет выполненных задач." |
| `OPERATION_CANCELLED` | "Operation cancelled." | "Операция отменена." |
| `SOMETHING_WENT_WRONG` | "Something went wrong. Please try again." | "Что-то пошло не так. Попробуйте снова." |
| `INVALID_TIME_FORMAT` | "Invalid time format. Use HH:MM, HH MM, or HH-MM." | "Неверный формат. Используйте ЧЧ:ММ, ЧЧ ММ или ЧЧ-ММ." |
| `USE_BUTTONS` | "Please use the buttons above." | "Используйте кнопки выше." |
| `COMING_SOON` | "This feature is coming soon!" | "Функция скоро появится!" |
| `BTN_NEW_TASK` | "📝 New Task" | "📝 Новая задача" |
| `BTN_MY_TASKS` | "📋 My Tasks" | "📋 Мои задачи" |
| `BTN_YES_DELETE_ALL` | "✅ Yes, delete all" | "✅ Да, удалить всё" |
| `BTN_CANCEL` | "❌ Cancel" | "❌ Отмена" |

**Step 2.2 — `MessageService` bean**

```java
package ru.zahaand.smarttaskbot.service;

@Service
public class MessageService {

    public String get(MessageKey key, Language language) {
        return key.get(language != null ? language : Language.EN);
    }

    public String get(MessageKey key, User user) {
        return get(key, user.getLanguage());
    }
}
```

**Step 2.3 — Unit test: `MessageServiceTest`**

- `@Nested` class per public method
- Verify EN/RU resolution, null-language fallback to EN

---

### Phase 3 — Registration Flow

**Goal**: New user goes through AWAITING_LANGUAGE → AWAITING_TIMEZONE → IDLE.
Existing fully-registered users are unaffected.

**Step 3.1 — `UserService` updates**

- `createPartialUser(telegramUserId, username)` — **single `@Transactional` operation**
  that atomically: (1) persists a `User` row with `language=null` and `timezone=null`,
  and (2) persists a `UserState` row with `state=AWAITING_LANGUAGE`.
  `UserService` owns both writes in one transaction. `UserStateService` is NOT called
  separately during `/start`; there is no `initRegistration()` method. [CHK011 fix]
- `updateLanguage(telegramUserId, Language)` — sets `User.language`.
- `updateTimezone(telegramUserId, String timezone)` — sets `User.timezone`.
- `isRegistered(telegramUserId)` — returns `true` only when both `language != null`
  AND `timezone != null`.
- `isLanguagePending(telegramUserId)` — user exists but `language == null`.
- `isTimezonePending(telegramUserId)` — user exists, language set, `timezone == null`.
- Remove old `register(telegramUserId, username, timezone)` or repurpose as internal.

**Step 3.2 — `UserStateService` updates**

- `transitionTo(telegramUserId, ConversationState)` — existing method; ensure it handles
  `AWAITING_LANGUAGE` and `AWAITING_TIMEZONE` correctly.
- `initRegistration()` is **removed** — UserState creation during `/start` is the
  exclusive responsibility of `UserService.createPartialUser()`. [CHK011 fix]

**Step 3.3 — `StartCommandHandler` rewrite**

```
handle(update):
  userId, chatId ← extract from update
  if isRegistered(userId):
      log.info("Returning user: userId={}", userId)
      send messageService.get(ALREADY_REGISTERED, user) + persistent menu
  else if user does not exist at all:
      log.info("New user registration start: userId={}", userId)
      createPartialUser(userId, username)
      send WELCOME_BILINGUAL message + language inline keyboard
  else:
      // mid-registration — redirect to correct step
      RegistrationGuard handles this case
```

**Step 3.4 — `LanguageCallbackHandler` (new class)**

Handles callbacks matching `CB_LANG` prefix (`lang:EN`, `lang:RU`).

```
handle(update):
  userId, chatId ← extract
  languageCode ← parse from callback data ("EN" or "RU")
  language ← Language.valueOf(languageCode) // validate
  userService.updateLanguage(userId, language)
  userStateService.transitionTo(userId, AWAITING_TIMEZONE)
  log.info("Language selected: userId={}, language={}", userId, language)
  notificationService.sendTimezoneKeyboard(chatId, language)
```

**Step 3.5 — `TimezoneCallbackHandler` update**

Remove user creation logic. Now only updates timezone on an existing `User`.

```
handle(update):
  userId, chatId ← extract
  timezone ← parse and validate from callback data
  user ← userService.findById(userId)   // must exist (created at /start)
  userService.updateTimezone(userId, timezone)
  userStateService.transitionTo(userId, IDLE)
  log.info("Timezone set: userId={}, timezone={}", userId, timezone)
  send messageService.get(TIMEZONE_CONFIRMED, user).formatted(timezone)
  send persistent menu (buttons in user's language)
```

**Step 3.6 — `RegistrationGuard` update**

Three-state routing:
```
if not found in users table:
    send bilingual welcome + language buttons
else if language == null:
    send bilingual welcome + language buttons   // re-show language selection
else if timezone == null:
    send timezone keyboard in user's language
else:
    commandAction.run()   // fully registered
```

**Step 3.7 — `UpdateDispatcher` update**

Add routing for `lang:` and `DELETE_ALL:` prefixes before existing callback routing.
Full `routeCallback()` branching order (new branches shown first):

```java
// [CHK012 fix] CB_DELETE_ALL_REQUEST is the initial tap from TaskListKeyboardBuilder;
// CB_DELETE_ALL_CONFIRM and CB_DELETE_ALL_CANCEL are the confirmation buttons.
// All three must be routed to the same handler.
if (data.startsWith(BotConstants.CB_LANG)) {
    languageCallbackHandler.handle(update);
} else if (data.equals(BotConstants.CB_DELETE_ALL_REQUEST)
        || data.equals(BotConstants.CB_DELETE_ALL_CONFIRM)
        || data.equals(BotConstants.CB_DELETE_ALL_CANCEL)) {
    deleteAllCompletedCallbackHandler.handle(update);
} else if (...existing routing...)
```

All three `DELETE_ALL:*` values must be routed to `DeleteAllCompletedCallbackHandler`;
the handler itself branches internally on which specific value it received.

**Step 3.8 — `BotConstants` additions**

```java
public static final String CB_LANG         = "lang:";
public static final String CB_LANG_EN      = "lang:EN";
public static final String CB_LANG_RU      = "lang:RU";
public static final String CB_DELETE_ALL_CONFIRM = "DELETE_ALL:confirm";
public static final String CB_DELETE_ALL_CANCEL  = "DELETE_ALL:cancel";

public static final Map<String, String> TIMEZONE_CITY_CODES = Map.of(
    "Europe/Kaliningrad", "KGD",
    "Europe/Moscow",      "MSK, SPB",
    "Asia/Yekaterinburg", "YEK",
    "Asia/Novosibirsk",   "NOV, OMS",
    "Asia/Vladivostok",   "VLA, KHA"
);
```

**Step 3.9 — `NotificationService` additions**

- `sendLanguageKeyboard(chatId)` — sends bilingual welcome with `[🇬🇧 English]` `[🇷🇺 Русский]`
  inline buttons (`lang:EN`, `lang:RU`).
- `sendTimezoneKeyboard(chatId, Language)` — overload that passes language for button label
  prompt text. Button labels always use `TIMEZONE_CITY_CODES` + current time (English).
- `sendPersistentMenu(chatId, String messageText, Language)` — overload using MessageService
  for `BTN_NEW_TASK` / `BTN_MY_TASKS` labels.

Timezone button label computation (inside `sendTimezoneKeyboard`):
```java
String label = ZonedDateTime.now(ZoneId.of(tz))
    .format(DateTimeFormatter.ofPattern("HH:mm"))
    + " " + BotConstants.TIMEZONE_CITY_CODES.get(tz);
// On ZoneRulesException: fallback to TIMEZONE_DISPLAY_NAMES.get(tz)
```

---

### Phase 4 — Migrate All Existing Handlers to MessageService

**Goal**: Zero hardcoded user-facing strings anywhere in the codebase.

For every handler and service listed in `research.md` under "Existing hardcoded strings":

1. Inject `MessageService` and `UserService` (to resolve the user's language).
2. Replace each string literal with `messageService.get(MessageKey.X, user)`.
3. Remove any now-unused string constants from `BotConstants`.

**Order of migration** (safest first, least interdependency):
1. `UnknownInputHandler` — simplest, one string
2. `HelpCommandHandler`, `DoneCommandHandler`, `RemindCommandHandler`
3. `NewTaskButtonHandler`, `TaskCreationTextHandler`, `ReminderTimeTextHandler`
4. `TaskListCommandHandler`, `TaskListTabCallbackHandler`
5. `CalendarCallbackHandler`, `TaskActionCallbackHandler`, `DeleteConfirmCallbackHandler`
6. `UserStateService` (cancel/stale-state messages)
7. `UpdateDispatcher` (fallback messages)
8. `ReminderService` — push notifications (see Step 4.1 below) [CHK001 fix]

**Step 4.1 — `ReminderService` update** [CHK001 fix]

`ReminderService` sends scheduled push notifications. These must arrive in the user's
selected language (FR-004).

Changes required:
- Inject `UserService` and `MessageService` into `ReminderService`.
- For each due reminder, resolve the owner's `Language` via `userService.findById(task.getTelegramUserId()).getLanguage()`.
- Pass the resolved language to `NotificationService` when building the notification text.
- Add a `REMINDER_NOTIFICATION` key to `MessageKey`:
  `EN: "⏰ Reminder: %s"` / `RU: "⏰ Напоминание: %s"` (task title interpolated via `formatted()`).

Pattern:
```java
// Before
notificationService.sendMessage(chatId, "⏰ Reminder: " + task.getTitle());

// After
User user = userService.findById(task.getTelegramUserId());
String text = messageService.get(MessageKey.REMINDER_NOTIFICATION, user)
    .formatted(task.getTitle());
notificationService.sendMessage(chatId, text);
```

**Pattern for each migration**:
```java
// Before
notificationService.sendMessage(chatId, "Please use the buttons above.");

// After
User user = userService.findById(telegramUserId);
notificationService.sendMessage(chatId, messageService.get(USE_BUTTONS, user));
```

If a handler currently receives no `telegramUserId`, thread it through from the Update.

---

### Phase 5 — New UX Features

**Step 5.1 — Extended time parsing (`TimeParserService`)**

Add two new patterns to the existing parse method:

```java
// Existing
private static final Pattern COLON = Pattern.compile("(\\d{1,2}):(\\d{2})");

// New
private static final Pattern SPACE  = Pattern.compile("(\\d{1,2}) (\\d{2})");
private static final Pattern HYPHEN = Pattern.compile("(\\d{1,2})-(\\d{2})");
```

Try all three patterns in sequence. All produce the same `LocalTime` output.
Invalid input (e.g., `25 00`) returns `Optional.empty()`.

**Step 5.2 — Delete All Completed: `TaskService`**

```java
// Returns count of COMPLETED tasks for confirmation prompt. [CHK005 fix]
public int countCompleted(Long telegramUserId) {
    return taskRepository.countByTelegramUserIdAndStatus(telegramUserId, TaskStatus.COMPLETED);
}

// Deletes all COMPLETED tasks for the user. Returns count of deleted rows.
@Transactional
public int deleteAllCompleted(Long telegramUserId) {
    return taskRepository.deleteAllByTelegramUserIdAndStatus(telegramUserId, TaskStatus.COMPLETED);
}
```

Add to `TaskRepository`:
- `countByTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status)` — Spring Data
  derived query, no `@Query` annotation needed.
- `@Modifying @Query` for `deleteAllByTelegramUserIdAndStatus` (bulk delete requires
  explicit JPQL to avoid N+1 delete).

**Step 5.3 — `TaskListKeyboardBuilder` update**

On the Completed tab, add a `🗑 Delete All` button if completed task count > 0.
Button callback data: `BotConstants.CB_DELETE_ALL_CONFIRM`.

Actually the Delete All button triggers a confirmation first — so the button data should be
a new prefix that routes to `DeleteAllCompletedCallbackHandler`, which SHOWS the
confirmation prompt (not immediately deletes).

Revised callback chain:
```
[🗑 Delete All] button → callback "DELETE_ALL:confirm_request"
  → DeleteAllCompletedCallbackHandler shows confirmation with count N
  → [✅ Yes, delete all] → "DELETE_ALL:confirm" → deleteAllCompleted()
  → [❌ Cancel]          → "DELETE_ALL:cancel"  → show Completed screen
```

Add constants:
```java
public static final String CB_DELETE_ALL_REQUEST = "DELETE_ALL:request";
public static final String CB_DELETE_ALL_CONFIRM = "DELETE_ALL:confirm";
public static final String CB_DELETE_ALL_CANCEL  = "DELETE_ALL:cancel";
```

**Step 5.4 — `DeleteAllCompletedCallbackHandler` (new class)**

```
handle(update):
  userId, chatId ← extract
  data ← callback data

  if data == CB_DELETE_ALL_REQUEST:
      count ← taskService.countCompleted(userId)
      if count == 0:
          send messageService.get(NO_COMPLETED_TASKS, user)
          return
      user ← userService.findById(userId)
      msg ← messageService.get(DELETE_CONFIRM_ALL, user).formatted(count)
      send confirmation message with [✅ Yes, delete all] [❌ Cancel] buttons

  else if data == CB_DELETE_ALL_CONFIRM:
      taskService.deleteAllCompleted(userId)
      log.info("All completed tasks deleted: userId={}", userId)
      send messageService.get(ALL_COMPLETED_DELETED, user)
      // re-render Completed tab (empty)

  else if data == CB_DELETE_ALL_CANCEL:
      send messageService.get(OPERATION_CANCELLED, user)
      // re-render Completed tab unchanged
```

---

### Phase 6 — Tests

**New test classes**:

- `MessageServiceTest` — `@Nested` per method: `get(key, language)`, `get(key, user)`,
  null-language fallback. Covers all `MessageKey` values for both languages.
- `LanguageCallbackHandlerTest` — `@Nested` per scenario: valid EN, valid RU, invalid data,
  missing user.
- `DeleteAllCompletedCallbackHandlerTest` — request with N tasks, request with 0 tasks,
  confirm, cancel.

**Modified test classes**:

- `TimeParserServiceTest` — add `@ParameterizedTest` + `@MethodSource` for `HH MM` and
  `HH-MM` valid cases and boundary invalids (`25 00`, `12 99`).
- `StartCommandHandlerTest` — add new-user flow (creates partial user, shows bilingual
  welcome), returning-user flow (shows ALREADY_REGISTERED + menu).
- `TaskServiceTest` — add `deleteAllCompleted` test: N tasks deleted, 0 tasks (no-op).

**All tests must follow Constitution VIII / IX rules**:
- `@DisplayName` is the FIRST annotation on every test method
- `@Nested` classes have NO `@DisplayName`
- Mockito chains: `.thenReturn()` on a new indented line
- No `@SpringBootTest`

---

### Phase 7 — Documentation (P3)

**Step 7.1 — Bilingual Javadoc**

For every public class and public method created or modified in this feature, add:
```java
/**
 * English description of what this class/method does.
 *
 * Описание на русском языке.
 */
```

Private methods are exempt. Pre-existing unmodified code: best-effort.

**Step 7.2 — Bilingual README**

Rewrite `README.md` with structure:
```
🇷🇺 Описание на русском ниже / 🇬🇧 Russian description below

## Smart Task Bot  [English section]
[Project description + SDD + Spec Kit + Claude Code methodology]
[Full command list]
[Supported time formats: HH:MM, HH MM, HH-MM]
[Local setup instructions]

---

## Smart Task Bot  [Russian section]
[Same four topics in Russian]
```

---

## Implementation Order (dependency-safe)

```
1. Migrations 005, 006 + master changelog       [DB foundation]
2. Language enum, ConversationState update       [Java types]
3. User entity update                           [entity model]
4. MessageKey enum + MessageService + tests     [i18n layer — no other deps]
5. UserService updates + UserStateService       [domain logic]
6. BotConstants additions                       [constants]
7. NotificationService additions               [infrastructure]
8. StartCommandHandler rewrite + tests         [registration step 1]
9. LanguageCallbackHandler + tests             [registration step 2]
10. TimezoneCallbackHandler update             [registration step 3]
11. RegistrationGuard update                   [routing]
12. UpdateDispatcher update                    [routing]
13. Migrate all existing handlers              [Phase 4 — in listed order]
14. TimeParserService extension + tests        [US-3]
15. TaskService.deleteAllCompleted + tests     [US-4 logic]
16. TaskListKeyboardBuilder update             [US-4 UI]
17. DeleteAllCompletedCallbackHandler + tests  [US-4 handler]
18. NotificationService timezone labels        [US-5]
19. Bilingual Javadoc                          [P3]
20. Bilingual README                           [P3]
```

## Complexity Tracking

No constitution violations. No entries required.

## Open Questions (resolved — recorded for traceability)

| Question | Resolution |
|---|---|
| MessageService storage strategy | `MessageKey` enum with hardcoded EN/RU strings per constant |
| Registration state machine | `AWAITING_LANGUAGE` → `AWAITING_TIMEZONE` → `IDLE` via `ConversationState` |
| `/start` for registered user | Show `ALREADY_REGISTERED` message in user's language + main menu |
| Language mutability | Immutable after registration; no change mechanism in this feature |
| Double-tap Delete All | Naturally idempotent; no special guard |
