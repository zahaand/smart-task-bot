# Data Model: 004-i18n-and-improvements

**Date**: 2026-03-20
**Branch**: `004-i18n-and-improvements`

---

## Schema Changes

### Migration 005 — Add `language` to `users`, make `timezone` nullable

```xml
<!-- changeSet id="005-add-language-to-users" -->

-- 1. Make timezone nullable (required during registration before timezone is chosen)
ALTER COLUMN timezone: nullable = true

-- 2. Add language column (nullable initially for migration safety)
ADD COLUMN language VARCHAR(2) NULL

-- 3. Backfill existing users to EN (backward compatibility)
UPDATE users SET language = 'EN' WHERE language IS NULL

-- 4. Add NOT NULL constraint after backfill
ALTER COLUMN language: nullable = false, defaultValue = 'EN'

<!-- Rollback [CHK007 fix]:
  Step 1: DELETE FROM users WHERE timezone IS NULL
          -- Removes partial-registration rows (timezone=null) that cannot
          -- survive a NOT NULL restore on timezone. Safe: these users have
          -- no timezone and will need to re-register after rollback.
  Step 2: ALTER COLUMN timezone SET NOT NULL
          -- Safe now: only fully-registered users (timezone IS NOT NULL) remain.
  Step 3: DROP COLUMN language
          -- Restores schema to pre-005 state.
-->
```

### Migration 006 — ON DELETE CASCADE on `tasks`

```xml
<!-- changeSet id="006-add-cascade-to-tasks" -->

-- Drop existing FK (no cascade)
DROP CONSTRAINT fk_tasks_users

-- Re-add FK with ON DELETE CASCADE
ADD FOREIGN KEY (telegram_user_id) REFERENCES users(telegram_user_id) ON DELETE CASCADE

<!-- Rollback: drop cascade FK, re-add without cascade -->
```

---

## Entity Changes

### `Language` (new enum)

```
Language
├── EN    — English
└── RU    — Russian
```

Stored as `VARCHAR(2)` in `users.language`. Used as a parameter throughout `MessageService`.

### `User` (updated)

| Field           | Type          | Nullable | Change        |
|-----------------|---------------|----------|---------------|
| telegramUserId  | Long (PK)     | no       | unchanged     |
| username        | String        | yes      | unchanged     |
| language        | Language enum | no*      | **NEW**       |
| timezone        | String (IANA) | yes*     | now nullable  |
| createdAt       | LocalDateTime | no       | unchanged     |

\* `language` and `timezone` are nullable only during the two-step registration flow.
`isRegistered()` = `language != null AND timezone != null`.

### `ConversationState` (updated enum)

```
ConversationState
├── AWAITING_LANGUAGE         ← NEW (step 1 of registration)
├── AWAITING_TIMEZONE         ← NEW (step 2 of registration)
├── IDLE
├── CREATING_TASK
├── SELECTING_REMINDER_DATE
├── ENTERING_REMINDER_TIME
└── CONFIRMING_DELETE
```

### `UserState` (entity — unchanged structure)

No structural changes. Accepts the two new `ConversationState` values transparently.

### `Task` (entity — unchanged structure)

No field changes. FK to `users` gains `ON DELETE CASCADE` via migration 006.

---

## New Application-Layer Types

### `MessageKey` (new enum, service layer)

Each constant holds both EN and RU string variants. Format strings use `%s` / `%d`
where interpolation is required.

```
MessageKey
│   ── Registration ──
├── WELCOME_BILINGUAL        "Welcome to Smart Task Bot!\nChoose your language:\n\nДобро пожаловать..."
├── SELECT_TIMEZONE          EN: "Please select your timezone:"  RU: "Выберите часовой пояс:"
├── ALREADY_REGISTERED       EN: "You are already registered."   RU: "Вы уже зарегистрированы."
├── TIMEZONE_CONFIRMED       EN: "Timezone set: %s ✓\n..."       RU: "Часовой пояс: %s ✓\n..."
│   ── Task creation ──
├── ENTER_TASK_DESCRIPTION   EN: "Enter task description:"       RU: "Введите описание задачи:"
├── CHOOSE_REMINDER_DATE     EN: "Choose reminder date:"         RU: "Выберите дату напоминания:"
├── ENTER_REMINDER_TIME      EN: "Enter reminder time (HH:MM, HH MM, or HH-MM):"  RU: "..."
├── TASK_CREATED             EN: "Task created ✓"                RU: "Задача создана ✓"
├── TASK_REMINDER_SET        EN: "Reminder set for %s ✓"         RU: "Напоминание на %s ✓"
│   ── Task operations ──
├── DELETE_CONFIRM_SINGLE    EN: "Delete this task? ..."         RU: "Удалить задачу? ..."
├── DELETE_CONFIRM_ALL       EN: "Delete all %d completed tasks? ..." RU: "Удалить %d задач? ..."
├── TASK_DELETED             EN: "Task deleted."                 RU: "Задача удалена."
├── ALL_COMPLETED_DELETED    EN: "All completed tasks deleted."  RU: "Все выполненные задачи удалены."
├── NO_COMPLETED_TASKS       EN: "No completed tasks."           RU: "Нет выполненных задач."
│   ── Errors ──
├── SOMETHING_WENT_WRONG     EN: "Something went wrong. ..."     RU: "Что-то пошло не так. ..."
├── INVALID_TIME_FORMAT      EN: "Invalid time. Use HH:MM ..."   RU: "Неверное время. ..."
├── OPERATION_CANCELLED      EN: "Operation cancelled."          RU: "Операция отменена."
├── USE_BUTTONS              EN: "Please use the buttons above." RU: "Используйте кнопки выше."
├── COMING_SOON              EN: "This feature is coming soon!"  RU: "Функция скоро появится!"
│   ── Button labels ──
├── BTN_NEW_TASK             EN: "📝 New Task"                   RU: "📝 Новая задача"
├── BTN_MY_TASKS             EN: "📋 My Tasks"                   RU: "📋 Мои задачи"
├── BTN_YES_DELETE_ALL       EN: "✅ Yes, delete all"            RU: "✅ Да, удалить всё"
└── BTN_CANCEL               EN: "❌ Cancel"                     RU: "❌ Отмена"
```

### `MessageService` (new Spring bean)

```
MessageService
├── get(MessageKey, Language) : String
└── get(MessageKey, User)     : String   // extracts language from User
```

Delegates to `MessageKey.get(Language)`. Both overloads fall back to `EN` when language
is null (safety guard for mid-registration state).

---

## Registration Flow (state transitions)

```
User sends /start (unknown user)
  → Create User(language=null, timezone=null)
  → Create UserState(AWAITING_LANGUAGE)
  → Send bilingual welcome + [🇬🇧 English] [🇷🇺 Русский]

User taps 🇬🇧 English  (lang:EN callback)
  → Update User.language = EN
  → Update UserState → AWAITING_TIMEZONE
  → Send timezone keyboard (labels in EN)

User taps timezone button  (tz:... callback)
  → Update User.timezone = chosen IANA string
  → Update UserState → IDLE
  → Send confirmation + persistent menu (in EN)

isRegistered(userId) = User.language != null AND User.timezone != null
```

---

## Callback Contracts

| Prefix                    | Handler                             | Data format         |
|---------------------------|-------------------------------------|---------------------|
| `lang:`                   | `LanguageCallbackHandler` (new)     | `lang:EN`, `lang:RU` |
| `tz:`                     | `TimezoneCallbackHandler` (existing) | `tz:Europe/Moscow`  |
| `DELETE_ALL:confirm`      | `DeleteAllCompletedCallbackHandler` (new) | literal string |
| `DELETE_ALL:cancel`       | `DeleteAllCompletedCallbackHandler` (new) | literal string |
| `TASK_DONE:`, `TASK_DELETE:`, `CB_CAL_DATE:`, etc. | unchanged | unchanged |

---

## BotConstants Additions

```java
// Language selection callbacks
public static final String CB_LANG = "lang:";
public static final String CB_LANG_EN = "lang:EN";
public static final String CB_LANG_RU = "lang:RU";

// Delete all completed
public static final String CB_DELETE_ALL_CONFIRM = "DELETE_ALL:confirm";
public static final String CB_DELETE_ALL_CANCEL  = "DELETE_ALL:cancel";

// Timezone city codes (always English)
public static final Map<String, String> TIMEZONE_CITY_CODES = Map.of(
    "Europe/Kaliningrad", "KGD",
    "Europe/Moscow",      "MSK, SPB",
    "Asia/Yekaterinburg", "YEK",
    "Asia/Novosibirsk",   "NOV, OMS",
    "Asia/Vladivostok",   "VLA, KHA"
);
```
