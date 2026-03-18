# Implementation Plan: Smart Task Bot MVP

**Branch**: `001-task-bot-mvp` | **Date**: 2026-03-18 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-task-bot-mvp/spec.md`

## Summary

Build a Telegram task-management bot (4 user stories: create task, view list, set reminder,
complete task) on top of the existing Spring Boot 3.5 / PostgreSQL / Liquibase skeleton.
The bot uses long polling via `TelegramLongPollingBot`, routes updates through a dispatcher,
enforces registration (timezone selection via inline keyboard) as a guard before any command,
stores all datetimes in UTC, and polls for due reminders every 60 seconds via
`@Scheduled`.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.11, Spring Data JPA, Spring Validation,
TelegramBots Spring Boot Starter 6.9.7.1, Liquibase, Lombok
**Storage**: PostgreSQL 15+ (tables: `users`, `tasks`)
**Testing**: JUnit 5 + Mockito (spring-boot-starter-test, already in pom.xml)
**Target Platform**: Linux server, long-running JVM process
**Project Type**: Telegram bot (long-polling, single instance)
**Performance Goals**: Command response < 3 s (SC-004); reminder delivery within 60 s (SC-003)
**Constraints**: Data isolation by `telegram_user_id` on every query; UTC storage;
user timezone applied at service layer only; max task text 500 chars
**Scale/Scope**: Portfolio MVP — small user base, single bot instance

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Layered Architecture | ✅ PASS | `handler → service → repository → model`; `SmartTaskBot` delegates immediately to `UpdateDispatcher`; zero business logic in handler layer |
| II. User Data Isolation | ✅ PASS | All repository queries include `telegramUserId` predicate; service layer validates ownership before any mutation |
| III. Single Responsibility | ✅ PASS | One handler class per command; `NotificationService` only sends; `ReminderService` only schedules |
| IV. Liquibase Migrations | ✅ PASS | Three changesets planned; no direct DDL; Liquibase already in pom.xml |
| V. Secrets via Env Vars | ✅ PASS | `BOT_TOKEN`, `BOT_USERNAME`, `DB_*` read from environment; no defaults for secrets in config |
| VI. Simplicity | ✅ PASS | `@Scheduled` instead of Quartz; no pagination; no extra abstractions |

**All gates pass. No violations to justify.**

## Project Structure

### Documentation (this feature)

```text
specs/001-task-bot-mvp/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── bot-commands.md  # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/ru/zahaand/smarttaskbot/
├── SmartTaskBotApplication.java
│
├── handler/                          # Telegram update entry-point and routing
│   ├── SmartTaskBot.java             # extends TelegramLongPollingBot; delegates to UpdateDispatcher
│   ├── UpdateDispatcher.java         # routes Update → command or callback handler
│   ├── RegistrationGuard.java        # intercepts unregistered users; re-displays timezone keyboard
│   ├── command/
│   │   ├── StartCommandHandler.java
│   │   ├── NewTaskCommandHandler.java
│   │   ├── TaskListCommandHandler.java
│   │   ├── RemindCommandHandler.java
│   │   ├── DoneCommandHandler.java
│   │   ├── HelpCommandHandler.java
│   │   └── UnknownInputHandler.java
│   └── callback/
│       └── TimezoneCallbackHandler.java
│
├── service/                          # All business logic
│   ├── UserService.java              # registration, timezone lookup
│   ├── TaskService.java             # create, list, setReminder, complete
│   ├── ReminderService.java         # @Scheduled polling for due reminders
│   └── NotificationService.java     # sends Telegram messages (wraps bot API calls)
│
├── repository/                       # Spring Data JPA interfaces
│   ├── UserRepository.java
│   └── TaskRepository.java
│
├── model/                            # JPA entities and enums
│   ├── User.java
│   ├── Task.java
│   └── TaskStatus.java              # ACTIVE, COMPLETED
│
├── dto/                              # Data transfer between layers
│   ├── TaskDto.java                 # used by TaskService → handler
│   └── CreateTaskRequest.java       # handler → TaskService input
│
└── config/
    ├── BotConfig.java               # @Value("${telegram.bot.token}") etc.
    └── SchedulingConfig.java        # @EnableScheduling

src/main/resources/
├── application.yaml
└── db/changelog/
    ├── db.changelog-master.xml
    ├── 001-create-users-table.xml
    ├── 002-create-tasks-table.xml
    └── 003-create-tasks-indexes.xml

src/test/java/ru/zahaand/smarttaskbot/
└── (unit tests per service and handler)
```

**Structure Decision**: Single project, Maven standard layout. Package structure mirrors
the four architectural layers mandated by Constitution Principle I, with `command/` and
`callback/` as organisational sub-packages within `handler/` for readability.

## Key Design Decisions

### Update Dispatcher Pattern

`SmartTaskBot.onUpdateReceived(Update)` contains a single line:
`dispatcher.dispatch(update)`. `UpdateDispatcher` branches:

```
if hasCallbackQuery()
  → TimezoneCallbackHandler
else if hasMessage()
  → RegistrationGuard.check(update)
    → if not registered: show timezone keyboard, return
    → if registered: route by command text to appropriate CommandHandler
```

This ensures `SmartTaskBot` has zero business logic (Principle I) and the guard
applies uniformly to every command (FR-016) without duplicating checks in each handler.

### Timezone Handling

Input path (user sets reminder):
```
"/remind 42 25.03.2026 09:00"
→ parse LocalDateTime with DateTimeFormatter("dd.MM.yyyy HH:mm")
→ atZone(ZoneId.of(user.getTimezone()))
→ toInstant()  →  stored as TIMESTAMP (UTC) in tasks.reminder_time
```

Output path (display in /tasks):
```
task.getReminderTime()  (Instant, UTC)
→ atZone(ZoneId.of(user.getTimezone()))
→ format("dd.MM.yyyy HH:mm")
```

### Reminder Scheduler

`ReminderService.processDueReminders()` annotated `@Scheduled(fixedDelay = 60_000)`:

1. Query: `reminderTime ≤ now AND reminderProcessed = false AND status = ACTIVE AND retryAt IS NULL`
2. For each: call `NotificationService.sendReminder(task)`
   - Success → `reminderProcessed = true`
   - Failure → `reminderRetryAt = now + 60s`
3. Query: `reminderRetryAt ≤ now AND reminderProcessed = false AND status = ACTIVE`
4. For each: call `NotificationService.sendReminder(task)` (retry)
   - Success → `reminderProcessed = true`
   - Failure → log WARN, `reminderProcessed = true` (discard — FR-009)

### Registration Guard

`RegistrationGuard` calls `userService.isRegistered(telegramUserId)` (service layer,
not repository directly — Constitution Principle I):
- Not registered → call `NotificationService.sendTimezoneKeyboard(chatId)`, return
- Registered → pass update to command router

**Conscious deviation from FR-001**: FR-001 states "register automatically on first
interaction". In this design, the `User` record is created atomically in
`TimezoneCallbackHandler` when the user taps a timezone button — not on the first
message or `/start` command. This is intentional: "registered" unconditionally implies
"timezone set". There is no intermediate state where a user record exists without a
timezone. This eliminates a class of null-check bugs and simplifies all downstream code.
The `/start` command delivers the registration prompt; the timezone tap completes it.

### Circular Dependency Resolution — NotificationService → Telegram API

`NotificationService` must call `AbsSender.execute()` to dispatch Telegram messages.
`TelegramLongPollingBot` (i.e., `SmartTaskBot`) extends `AbsSender`, so injecting
`SmartTaskBot` directly into `NotificationService` creates a circular dependency:

```
SmartTaskBot → UpdateDispatcher → handlers → NotificationService → SmartTaskBot
```

**Resolution**: `NotificationService` injects `AbsSender` (the abstract interface),
not `SmartTaskBot`, and annotates the injection with `@Lazy`:

```
@Lazy AbsSender sender  (resolved by Spring to SmartTaskBot at first use)
```

`@Lazy` breaks the cycle: Spring creates `SmartTaskBot` first, then injects the proxy
into `NotificationService` on first method call. This is the canonical solution for
this pattern in TelegramBots + Spring Boot. `SmartTaskBot` itself has no dependency
on `NotificationService` — it only depends on `UpdateDispatcher`.

## Complexity Tracking

> No Constitution violations detected. Section intentionally empty.
