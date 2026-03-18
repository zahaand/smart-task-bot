# Bot Command Contracts: Smart Task Bot MVP

**Branch**: `001-task-bot-mvp` | **Date**: 2026-03-18

This document defines the full interaction contract between the Telegram user and the bot:
command syntax, expected responses, and error messages.

---

## Update Types

The bot processes two types of Telegram updates:

| Update Type | Detection | Handler |
|-------------|-----------|---------|
| Text message / command | `update.hasMessage()` | `UpdateDispatcher` → command handlers |
| Inline keyboard tap | `update.hasCallbackQuery()` | `UpdateDispatcher` → `TimezoneCallbackHandler` |

---

## Registration Guard

**Applies to**: all commands except `/start`

**Trigger**: User has no record in the `users` table (timezone not yet set)

**Bot response**:
```
Please select your timezone first:
[inline keyboard with timezone buttons]
```

The original command is NOT executed.

---

## Commands

### `/start`

**Access**: always (no registration guard)

**Trigger**: new user OR returning user re-sends `/start`

**Bot response (new user)**:
```
Welcome to Smart Task Bot!
Please select your timezone:

[Europe/Kaliningrad] [Europe/Moscow]
[Asia/Yekaterinburg] [Asia/Novosibirsk]
[Asia/Vladivostok]
```

**Bot response (already registered user)**:
```
Welcome back! You are already registered.
Use /help to see available commands.
```

**No duplicate user records created on repeat `/start`.**

---

### Timezone Callback

**Trigger**: user taps an inline keyboard button during timezone selection

**Callback data format**: `tz:<IANA_timezone_id>`

Examples: `tz:Europe/Moscow`, `tz:Asia/Novosibirsk`

**Bot response**:
```
Timezone set: Europe/Moscow ✓
You're all set! Use /help to see available commands.
```

**Error (unexpected / invalid callback data)**:
```
Something went wrong. Please send /start to try again.
```

---

### `/newtask <text>`

**Access**: registered users only

**Syntax**: `/newtask <task text>`

**Constraints**: text must be 1–500 characters (non-whitespace)

**Success response**:
```
Task created ✓
#42: Buy groceries
```

**Error — no text provided**:
```
Please provide task text.
Usage: /newtask <your task>
```

**Error — text too long (>500 characters)**:
```
Task text is too long (max 500 characters).
```

---

### `/tasks`

**Access**: registered users only

**Syntax**: `/tasks`

**Success response (with tasks)**:
```
Your active tasks:

#42 Buy groceries
#45 Call dentist  ⏰ 25.03.2026 09:00
#51 Submit report
```

Reminder time displayed in user's local timezone (DD.MM.YYYY HH:mm format).
`⏰` icon shown only when reminder is set.

**Success response (no active tasks)**:
```
You have no active tasks.
Use /newtask to create one.
```

---

### `/remind <id> <DD.MM.YYYY HH:mm>`

**Access**: registered users only

**Syntax**: `/remind <task_id> <date> <time>`

**Example**: `/remind 42 25.03.2026 09:00`

Date/time interpreted in the user's timezone. Stored as UTC internally.

**Success response**:
```
Reminder set ✓
#42 Buy groceries — 25.03.2026 09:00 (Europe/Moscow)
```

**Error — task not found / belongs to another user**:
```
Task #42 not found.
```

**Error — task is COMPLETED**:
```
Cannot set a reminder on a completed task.
```

**Error — invalid date format**:
```
Invalid date format.
Usage: /remind <id> DD.MM.YYYY HH:mm
Example: /remind 42 25.03.2026 09:00
```

**Error — missing arguments**:
```
Usage: /remind <id> DD.MM.YYYY HH:mm
Example: /remind 42 25.03.2026 09:00
```

---

### `/done <id>`

**Access**: registered users only

**Syntax**: `/done <task_id>`

**Example**: `/done 42`

**Success response**:
```
Task completed ✓
#42 Buy groceries
```

**Error — task not found / belongs to another user**:
```
Task #42 not found.
```

**Error — missing or non-numeric ID**:
```
Please provide a task ID.
Usage: /done <task_id>
```

---

### `/help`

**Access**: always (no registration guard)

**Syntax**: `/help`

**Response**:
```
Smart Task Bot — available commands:

/newtask <text>              — create a new task
/tasks                       — list your active tasks
/remind <id> DD.MM.YYYY HH:mm  — set a reminder
/done <id>                   — mark a task as completed
/help                        — show this message
```

---

### Unrecognised Input

**Trigger**: any text or command not matching the above

**Response**:
```
I don't understand that command.
Use /help to see available commands.
```

---

## Reminder Notification (outbound)

**Trigger**: scheduler fires when `reminder_time <= now UTC` and `reminder_processed = false`
and task `status = ACTIVE`

**Message sent to user's chat**:
```
⏰ Reminder: Buy groceries
```

**Retry**: if Telegram API call fails, `reminder_retry_at = now + 60s` is set.
One retry attempt is made. On second failure: logged, `reminder_processed = true` (discarded).

---

## Callback Data Namespace

| Prefix | Meaning | Example |
|--------|---------|---------|
| `tz:` | Timezone selection | `tz:Europe/Moscow` |
