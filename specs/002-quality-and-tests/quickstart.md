# Quickstart: Verification Guide for 002-quality-and-tests

**Branch**: `002-quality-and-tests`

This guide covers how to verify each block of changes after implementation.

---

## Prerequisites

- Java 21, Maven, PostgreSQL running locally
- `.env` file populated per `README.md`

---

## Block 1 — Technical Debt: Verify

### D1 — Duplicate blank check removed

Open `NewTaskCommandHandler.java`. Confirm there is no `isBlank()` check in `handle()`.
The `extractTaskText()` helper may still exist; only the guard at the top of `handle()`
is removed.

### Q1 — `getReminderTime()` inlined

Open `TaskService.java`. Confirm:
- No method named `getReminderTime()` exists.
- `getTaskDto()` calls `formatReminder(...)` directly.

### Startup WARNs eliminated

```bash
mvn spring-boot:run
```

Check startup logs. Confirm **zero** lines matching:
- `open-in-view`
- `PostgreSQLDialect`

### Principle VIII member order

For each class listed below, confirm the member order:
**dependencies → helper objects → constants** (one blank line between groups).

| Class | Expected order |
|---|---|
| `TaskService` | `taskRepository`, `userRepository`, `userService` → `REMINDER_FORMATTER` |
| `HelpCommandHandler` | `notificationService` → `HELP_TEXT` |

---

## Block 2 — Logging: Verify

### `@Slf4j` on service classes

Grep for classes in `service/` missing `@Slf4j`:
```bash
grep -rL "@Slf4j" src/main/java/ru/zahaand/smarttaskbot/service/
```
Expected: empty output (all services annotated).

### INFO on success, ERROR/WARN on failure

Start the bot and exercise each command. Observe log output:

| Action | Expected log |
|---|---|
| `/newtask Buy milk` | `INFO ... Task created: id=X, userId=Y` |
| `/newtask` (blank) | `WARN ... Blank task text from userId=Y` |
| `/remind 1 25.03.2026 09:00` | `INFO ... Reminder set: taskId=1, userId=Y` |
| `/done 1` | `INFO ... Task completed: id=1, userId=Y` |
| `/done 999` (missing) | `ERROR ... Task #999 not found for user Y` |

### Centralized error handler

Temporarily make `UpdateDispatcher.dispatch()` throw `RuntimeException`.
Send any message to the bot. Confirm:
1. Log shows `ERROR Unhandled exception processing update: ...`
2. User receives `"Something went wrong. Please try again."`
3. Bot continues responding to subsequent messages.

---

## Block 3 — Deployment: Verify

### `railway.toml` present and correct

```bash
cat railway.toml
```

Expected: `builder`, `buildCommand`, `startCommand` all explicitly set.

### Railway deployment (optional, requires Railway account)

1. Push branch to remote.
2. Connect repository to Railway project.
3. Set env vars: `BOT_TOKEN`, `BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
4. Trigger deploy. Confirm deployment succeeds without manual dashboard changes.

---

## Block 4 — Unit Tests: Verify

### Run all unit tests (no database, no network required)

```bash
mvn test -Dspring.profiles.active=test
```

Or, to skip the `@SpringBootTest` context test:
```bash
mvn test -Dtest="TaskServiceTest,UserServiceTest,ReminderServiceTest,NewTaskCommandHandlerTest,RemindCommandHandlerTest,DoneCommandHandlerTest"
```

Expected: **all tests pass**, no database connection attempted.

### Coverage spot-check

Each test class must contain at least one `@Nested` class per public method, and each
test method must have a `@DisplayName`.

---

## Regression Check

After all blocks are complete, run the full test suite:
```bash
mvn test
```

Start the bot and manually verify the primary user flows:
1. `/start` → timezone keyboard appears
2. Select timezone → confirmation message
3. `/newtask Buy milk` → task created with ID
4. `/tasks` → task appears in list
5. `/remind <id> <future-date> <time>` → confirmation with formatted time
6. `/done <id>` → task marked complete
7. `/tasks` → list is empty
