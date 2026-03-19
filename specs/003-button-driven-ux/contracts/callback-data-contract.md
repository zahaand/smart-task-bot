# Callback Data Contract

**Feature**: 003-button-driven-ux
**Date**: 2026-03-19

This document defines the complete set of inline callback data strings the bot sends and receives. All prefixes are defined as constants in `BotConstants` and referenced by both builders (keyboard construction) and handlers (routing + parsing).

Telegram enforces a **64-byte maximum** on callback data. All entries below are verified within limit.

---

## Prefix Registry

| Constant Name | Prefix String | Example Value | Max Length | Handler |
|---------------|---------------|---------------|-----------|---------|
| `CB_TZ` | `tz:` | `tz:Asia/Vladivostok` | 23 chars | `TimezoneCallbackHandler` |
| `CB_TASK_REMIND` | `TASK_REMIND:` | `TASK_REMIND:9999999999` | 22 chars | `TaskActionCallbackHandler` |
| `CB_TASK_DONE` | `TASK_DONE:` | `TASK_DONE:9999999999` | 20 chars | `TaskActionCallbackHandler` |
| `CB_TASK_DELETE` | `TASK_DELETE:` | `TASK_DELETE:9999999999` | 22 chars | `TaskActionCallbackHandler` |
| `CB_CAL_DATE` | `CAL_DATE:` | `CAL_DATE:2026-04-15` | 19 chars | `CalendarCallbackHandler` |
| `CB_CAL_NAV` | `CAL_NAV:` | `CAL_NAV:+1` or `CAL_NAV:-1` | 11 chars | `CalendarCallbackHandler` |
| `CB_CONFIRM_DELETE` | `CONFIRM_DELETE:` | `CONFIRM_DELETE:9999999999` | 26 chars | `DeleteConfirmCallbackHandler` |
| `CB_CONFIRM_CANCEL` | `CONFIRM_CANCEL` | `CONFIRM_CANCEL` | 15 chars | `DeleteConfirmCallbackHandler` |
| `CB_TASKS_TAB` | `TASKS_TAB:` | `TASKS_TAB:ACTIVE` or `TASKS_TAB:COMPLETED` | 20 chars | `TaskListTabCallbackHandler` |

---

## Routing Logic

`UpdateDispatcher.routeCallback()` applies `startsWith()` checks in this order:

```
1. startsWith("tz:")             → TimezoneCallbackHandler
2. startsWith("TASK_REMIND:")    → TaskActionCallbackHandler
3. startsWith("TASK_DONE:")      → TaskActionCallbackHandler
4. startsWith("TASK_DELETE:")    → TaskActionCallbackHandler
5. startsWith("CAL_DATE:")       → CalendarCallbackHandler
6. startsWith("CAL_NAV:")        → CalendarCallbackHandler
7. startsWith("CONFIRM_DELETE:") → DeleteConfirmCallbackHandler
8. equals("CONFIRM_CANCEL")      → DeleteConfirmCallbackHandler
9. startsWith("TASKS_TAB:")      → TaskListTabCallbackHandler
10. else                         → log WARN + answerCallbackQuery with error toast
```

---

## Payload Extraction Contracts

### TASK_REMIND / TASK_DONE / TASK_DELETE
```
data = "<PREFIX>:<taskId>"
taskId = Long.parseLong(data.substring(prefix.length()))
```

### CAL_DATE
```
data = "CAL_DATE:<YYYY-MM-DD>"
date = LocalDate.parse(data.substring("CAL_DATE:".length()))
```

### CAL_NAV
```
data = "CAL_NAV:<+1|-1>"
offset = Integer.parseInt(data.substring("CAL_NAV:".length()))
// +1 = next month, -1 = previous month
```

### CONFIRM_DELETE
```
data = "CONFIRM_DELETE:<taskId>"
taskId = Long.parseLong(data.substring("CONFIRM_DELETE:".length()))
```

### TASKS_TAB
```
data = "TASKS_TAB:<ACTIVE|COMPLETED>"
tab = TaskStatus.valueOf(data.substring("TASKS_TAB:".length()))
```

---

## Persistent Menu Button Labels

Defined in `BotConstants` as plain string constants (used for routing in `UpdateDispatcher`):

| Constant | Value |
|----------|-------|
| `BTN_NEW_TASK` | `"📝 Новая задача"` |
| `BTN_MY_TASKS` | `"📋 Мои задачи"` |
| `BTN_REMINDER` | `"⏰ Напоминание"` |

`UpdateDispatcher.isPersistentMenuButton(String text)` returns true if text equals any of these three constants.

---

## All-Handlers Answer Contract

Every handler that processes a `CallbackQuery` MUST call:
```java
notificationService.answerCallbackQuery(callbackQuery.getId());
```
This clears the Telegram loading spinner. Failure to do so leaves the spinner showing for 10 seconds, degrading UX.
