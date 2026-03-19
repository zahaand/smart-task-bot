# Quickstart: Button-Driven UX (003-button-driven-ux)

**Date**: 2026-03-19
**Branch**: `003-button-driven-ux`

---

## Prerequisites

Same as the base project (from README):
- Java 21
- Maven 3.9+
- PostgreSQL 15+ running locally
- A `.env` file with `BOT_TOKEN`, `BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

No new environment variables are introduced by this feature.

---

## Migration

This feature adds one new Liquibase migration. It runs automatically on application startup:

```
004-create-user-states-table.xml  →  creates user_states table
```

If running against an existing database, Liquibase will apply this migration on first boot. No manual SQL required.

---

## Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

---

## Verifying the Feature

### 1. Persistent Menu
1. Start a **new** Telegram conversation with the bot or send `/start` as a registered user.
2. After registration (or immediately for already-registered users), the reply keyboard with three buttons should appear.

### 2. Create Task via Button
1. Tap **📝 Новая задача**.
2. Bot replies: `"Введи текст задачи:"`.
3. Type any non-empty text.
4. Bot confirms: `"✅ Задача #N создана: [text]"`.

### 3. Task List with Inline Buttons
1. Tap **📋 Мои задачи**.
2. Active tasks appear with `[⏰ Напомнить] [✅ Выполнить] [🗑 Удалить]` buttons.
3. Tap the **Выполненные** tab — list updates in-place.

### 4. Set Reminder via Calendar
1. Tap **⏰ Напомнить** on an active task.
2. Inline calendar appears. Past dates are greyed out.
3. Navigate with `←`/`→`. Select a future date.
4. Bot asks: `"Введи время (например: 14:30, 9 утра, 21:00)"`.
5. Type `9 утра` → bot confirms the reminder.
6. Type `12 вечера` → bot asks to use `HH:mm` format.

### 5. Delete Task
1. Tap **🗑 Удалить** on any task.
2. Confirmation message appears with `[✅ Да, удалить] [❌ Отмена]`.
3. Tap **❌ Отмена** — task unchanged.
4. Tap **✅ Да, удалить** — task deleted permanently.

### 6. Timezone with Live Time
1. Send `/start` from a **new account** (or clear user from DB).
2. Timezone keyboard appears; each button shows current local time, e.g., `"🕐 Москва, СПб (сейчас 18:45)"`.

### 7. Backward Compatibility
```
/newtask test task   → creates task (same as before)
/tasks               → text list of active tasks (same as before)
/remind 1 25.12.2026 09:00 → sets reminder (same as before)
/done 1              → marks done (same as before)
/help                → shows command list (same as before)
```

---

## Running Tests

```bash
mvn test
```

New test classes in this feature:
- `TimeParserServiceTest` — 16+ parameterized cases
- `UserStateServiceTest` — state transition and stale-reset logic
- `CalendarCallbackHandlerTest` — navigation and date selection
- `TaskActionCallbackHandlerTest` — remind, done, delete routing
- `DeleteConfirmCallbackHandlerTest` — confirm/cancel flows
- `UpdateDispatcherTest` — routing for new callback prefixes and menu buttons
