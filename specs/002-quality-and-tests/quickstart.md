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
is removed. Blank input now flows to `TaskService.createTask()` which throws
`IllegalArgumentException`, caught by the existing `catch` block.

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

Verifiable statically:
```bash
grep "open-in-view\|dialect" src/main/resources/application.yaml
# Expected: only "open-in-view: false" — no dialect line
```

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

### INFO on success, WARN/ERROR on failure

Start the bot and exercise each command. Observe log output:

| Action | Expected log |
|---|---|
| `/newtask Buy milk` | `INFO ... Task created: id=X, userId=Y` |
| `/newtask` (blank) | `WARN ... Blank task text from userId=Y` |
| `/remind 1 25.03.2026 09:00` | `INFO ... Reminder set: taskId=1, userId=Y` |
| `/done 1` | `INFO ... Task completed: id=1, userId=Y` |
| `/done 999` (missing) | `ERROR ... Task #999 not found for userId=Y` |

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

Expected content:
```toml
[build]
builder = "NIXPACKS"
buildCommand = "mvn clean package -DskipTests"

[deploy]
startCommand = "java -jar target/smart-task-bot-*.jar"
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

Note: `-DskipTests` is required — without it Railway would attempt to run tests
during build, which fails because the CI environment has no database.

### Railway deployment (optional, requires Railway account)

1. Push branch to remote.
2. Connect repository to Railway project.
3. Set env vars: `BOT_TOKEN`, `BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
4. Trigger deploy. Confirm deployment succeeds without manual dashboard changes.

---

## Block 4 — Unit Tests: Verify

### Run all unit tests (no database, no network required)

```bash
mvn test
```

Expected: **48 tests pass, 1 skipped** (integration smoke test `SmartTaskBotApplicationTests`
is `@Disabled` — it requires a live database and Telegram credentials).
Zero failures. No database connection attempted by unit tests.

To run only the unit test classes explicitly:
```bash
mvn test -Dtest="TaskServiceTest,UserServiceTest,ReminderServiceTest,NewTaskCommandHandlerTest,RemindCommandHandlerTest,DoneCommandHandlerTest"
```

### Integration smoke test (requires live environment)

`SmartTaskBotApplicationTests.contextLoads()` is annotated `@Disabled` and will be
skipped by default. To run it manually with a populated `.env`:

```bash
mvn test -Dtest="SmartTaskBotApplicationTests" -Dspring.config.location=.env
```

Or simply start the application normally — a successful startup is equivalent proof
that the Spring context loads correctly.

### Coverage spot-check

Each test class must contain at least one `@Nested` class per public method, and each
test method must have a `@DisplayName`.

---

## Regression Check

After all blocks are complete, run the full test suite:
```bash
mvn test
# Expected: Tests run: 49, Failures: 0, Errors: 0, Skipped: 1
```

Start the bot and manually verify the primary user flows:
1. `/start` → timezone keyboard appears
2. Select timezone → confirmation message
3. `/newtask Buy milk` → task created with ID
4. `/tasks` → task appears in list
5. `/remind <id> <future-date> <time>` → confirmation with formatted time and timezone
6. `/done <id>` → task marked complete
7. `/tasks` → list is empty
