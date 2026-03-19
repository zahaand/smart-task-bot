# Research: Quality Improvements and Test Coverage

**Branch**: `002-quality-and-tests` | **Date**: 2026-03-19

No external research was required for this feature. All findings are derived from
direct codebase inspection of the MVP (001-task-bot-mvp) and Constitution v1.1.0.

---

## Finding 1 — Test Infrastructure

**Decision**: No new Maven dependencies required.

**Rationale**: `spring-boot-starter-test` (already present in `pom.xml`) bundles
JUnit 5, Mockito 5, and AssertJ — sufficient for all tests mandated in the spec
(FR-014 through FR-022). The `@SpringBootTest` context test
(`SmartTaskBotApplicationTests`) is out of scope and is left unchanged.

**Alternatives considered**: Separate `mockito-core` dependency — rejected; already
transitively included.

---

## Finding 2 — Telegram Update Mocking Strategy

**Decision**: Mock the full `Update`/`Message`/`User` chain using Mockito in handler
tests.

**Rationale**: `telegrambots` 6.9.7.1 uses the legacy setter-based model API. The
objects lack builder patterns. Mockito `mock()` on the chain
(`Update` → `getMessage()` → `getChatId()` / `getFrom().getId()`) is the standard
approach for this library version; it avoids instantiating Spring context and keeps
tests fast.

**Alternatives considered**: Constructing real Telegram API objects via reflection —
rejected; fragile and harder to read than mocks.

---

## Finding 3 — SmartTaskBot Error Reply Strategy

**Decision**: `SmartTaskBot.onUpdateReceived()` sends the error reply directly via
`execute(new SendMessage(...))` — NOT via `NotificationService`.

**Rationale**: `NotificationService` takes `@Lazy AbsSender` in its constructor.
`SmartTaskBot` IS the `AbsSender` implementation. Injecting `NotificationService`
into `SmartTaskBot` would create a circular dependency (SmartTaskBot → NotificationService
→ SmartTaskBot). Since `SmartTaskBot` extends `TelegramLongPollingBot`, it already has
access to `execute()` directly — use it.

**Pattern**:
```java
extractChatId(update).ifPresent(chatId -> {
    try {
        execute(new SendMessage(chatId.toString(),
                "Something went wrong. Please try again."));
    } catch (TelegramApiException ex) {
        log.error("Failed to send error reply to chatId={}: {}", chatId, ex.getMessage());
    }
});
```

**Alternatives considered**: Inject `NotificationService` with `@Lazy` — rejected;
adds circular dependency risk and is architecturally messier.

---

## Finding 4 — Log Level for Validation vs. System Exceptions

**Decision**: Logging levels follow **Constitution Principle VII** (not the literal
text of FR-010 which says "ERROR"). Constitution takes precedence over spec language.

| Situation | Level | Rationale |
|---|---|---|
| User input validation failure (blank text, wrong format, completed task) | WARN | "Abnormal but handled" — Principle VII |
| System state failure (user not found, task not found for valid user) | ERROR | "Exception, operation failure" — Principle VII |
| Unhandled exception in `onUpdateReceived()` | ERROR | Principle VII §centralized catch |

**Implication**: `ReminderService` existing log levels (WARN for handled retry failure)
are already constitutional — **do not change them**.

**Alternatives considered**: Log everything at ERROR per FR-010 literally — rejected;
would drown ERROR channel with normal user input events, obscuring real failures.

---

## Finding 5 — Null Return Elimination in Handler Private Methods

**Decision**: Change `getTaskId()` return type from `Long` (nullable) to
`Optional<Long>` in both `RemindCommandHandler` and `DoneCommandHandler`.

**Rationale**: Constitution Principle VIII prohibits returning `null`. The callers
already check the return value before proceeding; switching to `Optional` requires a
minimal change to the call site (`if (taskId == null)` → `if (taskId.isEmpty())`).

**Alternatives considered**: Throw exception on invalid input — rejected; the handler
already sends a user-facing message and returns early. Optional models the
"might not parse" case more clearly than an exception.

---

## Finding 6 — Member Declaration Order Violations

**Inventory of violations** (handler/ and service/ scope only):

| Class | Violation | Fix |
|---|---|---|
| `TaskService` | `REMINDER_FORMATTER` (helper) declared BEFORE `taskRepository`, `userRepository`, `userService` (deps) | Move deps above formatter |
| `HelpCommandHandler` | `HELP_TEXT` (constant) declared BEFORE `notificationService` (dep) | Move constant below dep |

All other handler and service classes already comply with Principle VIII ordering.

---

## Finding 7 — railway.toml Configuration

**Decision**: Use Railway's NIXPACKS builder with explicit Maven build and JVM start commands.

**Rationale**: NIXPACKS is Railway's standard auto-detection builder for JVM projects.
Specifying it explicitly in `railway.toml` guarantees reproducibility even if Railway
changes its default detection heuristics. The start command targets the fat JAR produced
by `spring-boot-maven-plugin`.

```toml
[build]
builder = "NIXPACKS"
buildCommand = "mvn clean package -DskipTests"

[deploy]
startCommand = "java -jar target/smart-task-bot-0.0.1-SNAPSHOT.jar"
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

**Alternatives considered**: Dockerfile-based build — rejected; NIXPACKS is sufficient
and simpler to maintain for a single-JAR Maven project.

---

## Finding 8 — `application.yaml` Startup WARNs Root Cause

| WARN | Cause | Fix |
|---|---|---|
| `spring.jpa.open-in-view` | Not explicitly set; Spring Boot defaults to `true` and warns about it | Add `open-in-view: false` under `spring.jpa` |
| `PostgreSQLDialect` | Explicit `dialect: org.hibernate.dialect.PostgreSQLDialect` set; Hibernate 6 warns that explicit dialects are deprecated in favor of auto-detection | Remove the `hibernate.dialect` property entirely |
