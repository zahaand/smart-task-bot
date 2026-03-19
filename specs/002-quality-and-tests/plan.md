# Implementation Plan: Quality Improvements and Test Coverage

**Branch**: `002-quality-and-tests` | **Date**: 2026-03-19 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-quality-and-tests/spec.md`

## Summary

Close all MVP technical debt identified against Constitution v1.1.0 (Principles VII–IX),
eliminate two startup WARNs, add a Railway deployment config, and write unit tests for
all core service and handler classes. No schema changes. No new dependencies. After this
phase the codebase is fully constitution-compliant and test-covered, ready for new features.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1,
Lombok, JUnit 5 + Mockito 5 (via `spring-boot-starter-test` — already present)
**Storage**: PostgreSQL (no schema changes in this feature)
**Testing**: JUnit 5, Mockito 5, AssertJ — all available via existing `spring-boot-starter-test`
**Target Platform**: Linux server (Railway)
**Project Type**: Telegram long-polling bot (Spring Boot service)
**Performance Goals**: Test suite must complete without database or network access
**Constraints**: No new Maven dependencies; no Liquibase migrations; retry logic in
`ReminderService` MUST NOT be changed — only tested
**Scale/Scope**: 25 source files affected (6 modified, 1 new); 6 new test classes

## Constitution Check

*GATE: Must pass before implementation. Re-checked post-design below.*

### Pre-implementation gate (current violations — all resolved by this plan)

| Principle | Violation in current codebase | Resolved by |
|---|---|---|
| VII — Logging | `TaskService`: no `@Slf4j`, no log calls | Block 2, task T009–T011 |
| VII — Logging | `UserService`: no `@Slf4j`, no log calls | Block 2, task T012–T013 |
| VII — Logging | `SmartTaskBot.onUpdateReceived()`: no try-catch | Block 2, task T008 |
| VIII — Code Style | `TaskService`: `REMINDER_FORMATTER` before deps | Block 1, task T005 |
| VIII — Code Style | `HelpCommandHandler`: `HELP_TEXT` before dep | Block 1, task T006 |
| VIII — Code Style | `RemindCommandHandler.getTaskId()`: returns `null` | Block 1, task T003 |
| VIII — Code Style | `DoneCommandHandler.getTaskId()`: returns `null` | Block 1, task T004 |
| IX — Testing | No unit tests exist for any service or handler | Block 4, tasks T015–T020 |

### Gates that are already satisfied (no action required)

| Principle | Status |
|---|---|
| I — Layered Architecture | ✅ No business logic in handlers; services own all logic |
| II — User Data Isolation | ✅ All repository queries include `telegramUserId` predicate |
| III — SRP | ✅ Each class has one responsibility |
| IV — DB Migrations via Liquibase | ✅ No schema changes in this feature |
| V — Secrets via Env Vars | ✅ No new secrets introduced |
| VI — Simplicity | ✅ All changes are minimum necessary |
| VII — `ReminderService` / `NotificationService` | ✅ Both already have `@Slf4j` and correct log levels |
| VIII — Constructor injection + `final` fields | ✅ All classes already use `@RequiredArgsConstructor` with `final` fields |
| VIII — Curly braces, verb-prefixed methods | ✅ No violations found |

### Post-design re-check

No new classes or layers introduced. Constitution Check passes for the entire plan.

## Project Structure

### Documentation (this feature)

```text
specs/002-quality-and-tests/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks — NOT created by /speckit.plan)
```

No `data-model.md` (no new entities) and no `contracts/` (no new public interfaces).

### Source Code Changes (repository root)

```text
src/main/
├── java/ru/zahaand/smarttaskbot/
│   ├── handler/
│   │   ├── SmartTaskBot.java                    ← MODIFY: add @Slf4j, try-catch
│   │   └── command/
│   │       ├── NewTaskCommandHandler.java        ← MODIFY: remove duplicate blank check
│   │       ├── RemindCommandHandler.java         ← MODIFY: null→Optional, member reorder N/A
│   │       ├── DoneCommandHandler.java           ← MODIFY: null→Optional
│   │       └── HelpCommandHandler.java           ← MODIFY: fix member order (const→dep)
│   └── service/
│       ├── TaskService.java                     ← MODIFY: fix member order, @Slf4j,
│       │                                           logging, inline getReminderTime()
│       └── UserService.java                     ← MODIFY: @Slf4j, logging
└── resources/
    └── application.yaml                         ← MODIFY: open-in-view, remove dialect

railway.toml                                     ← CREATE

src/test/java/ru/zahaand/smarttaskbot/
├── service/
│   ├── TaskServiceTest.java                     ← CREATE
│   ├── UserServiceTest.java                     ← CREATE
│   └── ReminderServiceTest.java                 ← CREATE
└── handler/command/
    ├── NewTaskCommandHandlerTest.java            ← CREATE
    ├── RemindCommandHandlerTest.java             ← CREATE
    └── DoneCommandHandlerTest.java              ← CREATE
```

**Structure Decision**: Single-project Maven layout. Test classes mirror the
`src/main` package structure under `src/test`.

---

## Phase 0 — Research (Complete)

See [research.md](research.md) for full findings. Key decisions:

- No new dependencies needed; `spring-boot-starter-test` already provides JUnit 5 + Mockito 5
- Telegram `Update` chain mocked via Mockito (no real API objects)
- `SmartTaskBot` error reply uses `execute()` directly (avoids circular dep with `NotificationService`)
- Log levels follow Constitution Principle VII: WARN for user input errors, ERROR for system failures
- `getTaskId()` null returns fixed via `Optional<Long>`

---

## Phase 1 — Detailed Design

### Block 1: Technical Debt Elimination

#### T001 — `application.yaml`: fix startup WARNs

**File**: `src/main/resources/application.yaml`

Two changes under `spring.jpa`:

1. Add `open-in-view: false` directly under `spring.jpa:`
2. Remove the line `dialect: org.hibernate.dialect.PostgreSQLDialect`
   (the entire `hibernate.dialect` key)

Result after change:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        timezone:
          default_storage: NORMALIZE_UTC
```

#### T002 — `NewTaskCommandHandler`: remove duplicate blank check

**File**: `src/main/java/.../handler/command/NewTaskCommandHandler.java`

Remove lines 30–34 (the `if (taskText.isBlank())` guard and its body).
`TaskService.createTask()` already throws `IllegalArgumentException` for blank input,
which is caught by the existing `catch (IllegalArgumentException e)` block.

After removal, `handle()` becomes:
```java
public void handle(Update update) {
    Long chatId = update.getMessage().getChatId();
    Long telegramUserId = update.getMessage().getFrom().getId();
    String messageText = update.getMessage().getText();
    String taskText = extractTaskText(messageText);

    try {
        TaskDto task = taskService.createTask(telegramUserId, taskText);
        notificationService.sendMessage(chatId,
                "Task created ✓\n#" + task.getId() + ": " + task.getText());
    } catch (IllegalArgumentException e) {
        notificationService.sendMessage(chatId, e.getMessage());
    }
}
```

#### T003 — `RemindCommandHandler`: fix null return in `getTaskId()`

**File**: `src/main/java/.../handler/command/RemindCommandHandler.java`

Change `getTaskId()` signature from `private Long getTaskId(...)` to
`private Optional<Long> getTaskId(...)`.

- On successful parse: `return Optional.of(taskId);`
- On `NumberFormatException`: send usage hint, `return Optional.empty();`

Update call site in `handle()`:
```java
Optional<Long> taskId = getTaskId(parts[0], chatId);
if (taskId.isEmpty()) {
    return;
}
// use taskId.get() below
TaskDto dto = taskService.setReminder(telegramUserId, taskId.get(), dateTimeInput);
```

Add import: `java.util.Optional`

#### T004 — `DoneCommandHandler`: fix null return in `getTaskId()`

**File**: `src/main/java/.../handler/command/DoneCommandHandler.java`

Same pattern as T003:

Change `getTaskId()` to return `Optional<Long>`.

Update call site in `handle()`:
```java
Optional<Long> taskId = getTaskId(argsText, chatId);
if (taskId.isEmpty()) {
    return;
}
TaskDto task = taskService.completeTask(telegramUserId, taskId.get());
```

Add import: `java.util.Optional`

#### T005 — `TaskService`: fix member declaration order

**File**: `src/main/java/.../service/TaskService.java`

Move `REMINDER_FORMATTER` from position 1 (before deps) to position 2 (after deps).

Correct order:
```java
// 1. Injected dependencies
private final TaskRepository taskRepository;
private final UserRepository userRepository;
private final UserService userService;

// 2. Helper objects
private static final DateTimeFormatter REMINDER_FORMATTER =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
```

Note: `@RequiredArgsConstructor` generates the constructor based on field declaration
order. Moving `REMINDER_FORMATTER` is safe because it is `static final` and is NOT
injected via constructor (Lombok ignores `static` fields).

#### T006 — `HelpCommandHandler`: fix member declaration order

**File**: `src/main/java/.../handler/command/HelpCommandHandler.java`

Move `HELP_TEXT` constant to after `notificationService` dependency.

Correct order:
```java
// 1. Injected dependencies
private final NotificationService notificationService;

// 2. Constants
private static final String HELP_TEXT = """
        Smart Task Bot — available commands:
        ...
        """;
```

#### T007 — `TaskService`: inline `getReminderTime()` and delete it

**File**: `src/main/java/.../service/TaskService.java`

`getReminderTime()` is a single-line delegate:
```java
private String getReminderTime(Task task, ZoneId userZone) {
    return formatReminder(task.getReminderTime(), userZone);
}
```

Replace the `getTaskDto()` call site to call `formatReminder()` directly:
```java
private TaskDto getTaskDto(Task task, ZoneId userZone) {
    return new TaskDto(task.getId(), task.getText(),
            formatReminder(task.getReminderTime(), userZone));
}
```

Delete `getReminderTime()` entirely.

---

### Block 2: Logging and Error Handling

#### T008 — `SmartTaskBot`: add `@Slf4j` and centralized try-catch

**File**: `src/main/java/.../handler/SmartTaskBot.java`

Add `@Slf4j` annotation to the class.

Wrap `dispatcher.dispatch(update)` in try-catch. Send error reply directly via
`execute()` (avoids circular dependency with `NotificationService`).

Add private helper `extractChatId()` returning `Optional<Long>`:

```java
@Slf4j
@Component
public class SmartTaskBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final UpdateDispatcher dispatcher;

    public SmartTaskBot(BotConfig botConfig, UpdateDispatcher dispatcher) {
        super(botConfig.getBotToken());
        this.botConfig = botConfig;
        this.dispatcher = dispatcher;
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            dispatcher.dispatch(update);
        } catch (Exception e) {
            log.error("Unhandled exception processing update: {}", e.getMessage(), e);
            extractChatId(update).ifPresent(chatId -> {
                try {
                    execute(new SendMessage(chatId.toString(),
                            "Something went wrong. Please try again."));
                } catch (TelegramApiException ex) {
                    log.error("Failed to send error reply to chatId={}: {}",
                            chatId, ex.getMessage());
                }
            });
        }
    }

    private Optional<Long> extractChatId(Update update) {
        if (update.hasMessage()) {
            return Optional.of(update.getMessage().getChatId());
        }
        if (update.hasCallbackQuery()
                && update.getCallbackQuery().getMessage() != null) {
            return Optional.of(update.getCallbackQuery().getMessage().getChatId());
        }
        return Optional.empty();
    }
}
```

New imports: `lombok.extern.slf4j.Slf4j`, `java.util.Optional`,
`org.telegram.telegrambots.meta.api.methods.send.SendMessage`,
`org.telegram.telegrambots.meta.exceptions.TelegramApiException`

#### T009 — `TaskService`: add `@Slf4j`

**File**: `src/main/java/.../service/TaskService.java`

Add `@Slf4j` to the class. Add import `lombok.extern.slf4j.Slf4j`.

#### T010 — `TaskService`: add logging to `createTask()`

Add log calls per Constitution Principle VII level rules:

```java
public TaskDto createTask(Long telegramUserId, String text) {
    if (text == null || text.isBlank()) {
        log.warn("Blank task text from userId={}", telegramUserId);
        throw new IllegalArgumentException("Please provide task text.\nUsage: /newtask <your task>");
    }
    if (text.length() > 500) {
        log.warn("Task text too long ({} chars) from userId={}", text.length(), telegramUserId);
        throw new IllegalArgumentException("Task text is too long (max 500 characters).");
    }
    User user = userRepository.findById(telegramUserId)
            .orElseThrow(() -> {
                log.error("User not found during task creation: userId={}", telegramUserId);
                return new IllegalStateException("User not found: " + telegramUserId);
            });
    // ... existing task creation ...
    log.info("Task created: id={}, userId={}", saved.getId(), telegramUserId);
    return new TaskDto(saved.getId(), saved.getText(), null);
}
```

#### T011 — `TaskService`: add logging to `setReminder()` and `completeTask()`

`setReminder()`:
```java
// Before task not found throw:
log.error("Task #{} not found for userId={}", taskId, telegramUserId);
// Before completed task throw:
log.warn("Attempt to set reminder on completed task #{} by userId={}", taskId, telegramUserId);
// After successful save:
log.info("Reminder set: taskId={}, userId={}", taskId, telegramUserId);
```

`completeTask()`:
```java
// Before task not found throw:
log.error("Task #{} not found for userId={}", taskId, telegramUserId);
// After successful save:
log.info("Task completed: id={}, userId={}", saved.getId(), telegramUserId);
```

#### T012 — `UserService`: add `@Slf4j`

Add `@Slf4j` to the class.

#### T013 — `UserService`: add logging

`register()`: add INFO when a new user is saved (not when skipped as existing):
```java
// After userRepository.save(user):
log.info("User registered: userId={}", telegramUserId);
```

`getTimezone()`: add ERROR before throw:
```java
.orElseThrow(() -> {
    log.error("Timezone lookup failed — user not found: userId={}", telegramUserId);
    return new IllegalStateException("User not found: " + telegramUserId);
});
```

---

### Block 3: Deployment Configuration

#### T014 — Create `railway.toml`

**File**: `railway.toml` (repository root)

```toml
[build]
builder = "NIXPACKS"
buildCommand = "mvn clean package -DskipTests"

[deploy]
startCommand = "java -jar target/smart-task-bot-*.jar"
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

---

### Block 4: Unit Tests

All test classes follow Constitution Principle IX:
- `@ExtendWith(MockitoExtension.class)` — no Spring context
- `@Nested` per method under test
- `@DisplayName` in English on every test method
- `@ParameterizedTest` + `@MethodSource` for boundary/equivalence cases

Update mocks with `when(from.getId()).thenReturn(...)` using
`org.telegram.telegrambots.meta.api.objects.User` (aliased if needed to avoid clash
with `java.util.function` etc.).

#### T015 — `TaskServiceTest`

**File**: `src/test/java/.../service/TaskServiceTest.java`

Mocks: `TaskRepository`, `UserRepository`, `UserService`

```
@Nested class CreateTask {
  "creates task and returns DTO for valid input"
  "throws IllegalArgumentException for blank text"          ← @ParameterizedTest: "", "  ", null
  "throws IllegalArgumentException for text over 500 chars"
  "throws IllegalStateException when user not found"
}
@Nested class GetActiveTasks {
  "returns empty list when user has no active tasks"
  "returns DTOs with formatted reminder times for active tasks"
}
@Nested class SetReminder {
  "sets reminder and returns DTO with formatted time for valid input"
  "throws NoSuchElementException when task not found"
  "throws IllegalArgumentException when task is COMPLETED"
  "throws DateTimeParseException for invalid datetime string"
}
@Nested class CompleteTask {
  "marks task COMPLETED and returns DTO for valid task"
  "throws NoSuchElementException when task not found"
}
```

#### T016 — `UserServiceTest`

**File**: `src/test/java/.../service/UserServiceTest.java`

Mocks: `UserRepository`

```
@Nested class Register {
  "saves new user when not yet registered"
  "does nothing when user already registered"
}
@Nested class IsRegistered {
  "returns true for registered user"
  "returns false for unknown user"
}
@Nested class GetTimezone {
  "returns timezone string for registered user"
  "throws IllegalStateException for unknown user"
}
```

#### T017 — `ReminderServiceTest`

**File**: `src/test/java/.../service/ReminderServiceTest.java`

Mocks: `TaskRepository`, `NotificationService`

```
@Nested class ProcessDueReminders {
  // Initial reminder paths
  "marks reminder processed when initial send succeeds"
  "sets reminderRetryAt when initial send fails"
  "does not mark processed when initial send fails"
  // Retry paths
  "marks reminder processed when retry send succeeds"
  "marks reminder processed and discards when retry send fails"
  // Isolation
  "continues processing remaining reminders after one initial failure"
  "continues processing remaining reminders after one retry failure"
  // Selection
  "only processes reminders whose reminderTime is not after now"
  "only processes retries whose reminderRetryAt is not after now"
  // Saves
  "calls saveAll with all processed tasks after initial pass"
  "calls saveAll with all processed tasks after retry pass"
}
```

Key test setup note: `processDueReminders()` calls private `processInitialReminders(now)`
and `processRetries(now)`. Both are tested through the public method by controlling what
`taskRepository.findDueReminders(now)` and `taskRepository.findDueRetries(now)` return.
Use `ArgumentCaptor<Instant>` to verify the Instant passed to the repository queries.

#### T018 — `NewTaskCommandHandlerTest`

**File**: `src/test/java/.../handler/command/NewTaskCommandHandlerTest.java`

Mocks: `TaskService`, `NotificationService`

```
@Nested class Handle {
  "calls taskService and sends success message for valid input"
  "forwards error message to user when taskService throws for blank input"  ← @ParameterizedTest: "", "  ", null
  "forwards error message to user when taskService throws IllegalArgumentException"
}
```

Note: after T002, the handler has no blank check of its own. For blank/null input,
`taskService.createTask()` is called, throws `IllegalArgumentException`, and the handler
forwards the exception message to the user. The parameterized test stubs
`taskService.createTask()` to throw and verifies the error message is sent.

#### T019 — `RemindCommandHandlerTest`

**File**: `src/test/java/.../handler/command/RemindCommandHandlerTest.java`

Mocks: `TaskService`, `NotificationService`, `UserService`

```
@Nested class Handle {
  "sends usage hint when no arguments provided"
  "sends usage hint when fewer than 3 argument parts"
  "sends usage hint when task ID is non-numeric"
  "sets reminder and sends success message for valid input"
  "sends format error when datetime cannot be parsed"
  "sends error message when task not found (NoSuchElementException)"
  "sends error message when task is completed (IllegalArgumentException)"
}
```

#### T020 — `DoneCommandHandlerTest`

**File**: `src/test/java/.../handler/command/DoneCommandHandlerTest.java`

Mocks: `TaskService`, `NotificationService`

```
@Nested class Handle {
  "sends usage hint when no task ID provided"
  "sends usage hint when task ID is non-numeric"
  "marks task complete and sends success message for valid ID"
  "sends error message when task not found (NoSuchElementException)"
}
```

---

## Complexity Tracking

> No Constitution Check violations in this plan.

---

## Implementation Order and Dependencies

```
T001  application.yaml          independent
T002  NewTaskCommandHandler     independent
T003  RemindCommandHandler      independent
T004  DoneCommandHandler        independent
T005  TaskService member order  independent (do before T009–T011)
T006  HelpCommandHandler        independent
T007  TaskService inline        independent (do after T005)
──────────────────────────────────────────────────────────────
T008  SmartTaskBot try-catch    independent
T009  TaskService @Slf4j        depends on T005, T007
T010  TaskService logging       depends on T009
T011  TaskService logging       depends on T009
T012  UserService @Slf4j        independent
T013  UserService logging       depends on T012
──────────────────────────────────────────────────────────────
T014  railway.toml              independent
──────────────────────────────────────────────────────────────
T015  TaskServiceTest           depends on T005, T007, T009–T011
T016  UserServiceTest           depends on T012, T013
T017  ReminderServiceTest       no changes to ReminderService; can start anytime
T018  NewTaskCommandHandlerTest depends on T002
T019  RemindCommandHandlerTest  depends on T003
T020  DoneCommandHandlerTest    depends on T004
```

Blocks 1–3 can be executed in parallel across tasks. Block 4 tests should be written
after the corresponding source changes are complete so they test the final state.
