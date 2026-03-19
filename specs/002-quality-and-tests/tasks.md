---

description: "Task list for 002-quality-and-tests"
---

# Tasks: Quality Improvements and Test Coverage

**Input**: Design documents from `specs/002-quality-and-tests/`
**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | quickstart.md ✅

**Tests**: Block 4 (unit tests) is mandatory per specification (FR-014–FR-022).

**Organization**: Tasks are grouped by spec block, which maps to user story.
Blocks 1–3 are independent of each other and MUST be complete before Block 4.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: `[US1]`–`[US4]` maps to spec user stories / FR blocks
- Exact file paths are included in every task description

## Path Conventions

All paths relative to repository root:

- Source: `src/main/java/ru/zahaand/smarttaskbot/`
- Resources: `src/main/resources/`
- Tests: `src/test/java/ru/zahaand/smarttaskbot/`

---

## Phase 1: Setup

**Purpose**: Confirm the feature branch builds cleanly before any changes.

- [X] T000 Verify the project builds on branch `002-quality-and-tests` by running
  `mvn clean package -DskipTests` and confirming zero compilation errors

**Checkpoint**: Clean build confirmed — implementation can begin.

---

## Phase 2: Foundational

**Purpose**: No dedicated foundational tasks — the project structure and all dependencies
already exist. Constraint: complete Phases 3, 4, and 5 (Blocks 1–3) before starting
Phase 6 (Block 4), since tests must target the refactored source code.

---

## Phase 3: Block 1 — Technical Debt Elimination (US1, Priority: P1) 🎯

**Goal**: Remove all Constitution v1.1.0 Principle VIII violations and eliminate startup
WARNs. All existing classes in `handler/` and `service/` conform to member ordering,
null-return prohibition, and no duplicate validation logic.

**Independent Test**: Code review confirms no `getReminderTime()` method in `TaskService`,
no blank check in `NewTaskCommandHandler`, correct member ordering in `TaskService` and
`HelpCommandHandler`, `Optional<Long>` return from `getTaskId()` in both handlers, and
zero startup WARNs when running `mvn spring-boot:run`.

### Implementation for Block 1

- [X] T001 [P] [US1] Fix startup WARNs in `src/main/resources/application.yaml`:
  add `open-in-view: false` under `spring.jpa`; remove the
  `hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect` line entirely
  (Hibernate auto-detects PostgreSQL dialect — explicit setting triggers deprecation WARN)

- [X] T002 [P] [US1] Remove duplicate blank-input guard from
  `src/main/java/ru/zahaand/smarttaskbot/handler/command/NewTaskCommandHandler.java`:
  delete the `if (taskText.isBlank())` block and its early-return body (lines 30–34);
  blank input is already validated in `TaskService.createTask()` which throws
  `IllegalArgumentException`, caught by the existing `catch` block in the handler

- [X] T003 [P] [US1] No change needed in
  `src/main/java/ru/zahaand/smarttaskbot/handler/command/RemindCommandHandler.java`:
  `getTaskId()` returning `null` on parse failure is permitted under Constitution v1.1.1
  (Principle VIII) — private method, single call site, intent obvious from `if (taskId == null)`
  guard immediately below. No code change required; task closed as N/A.

- [X] T004 [P] [US1] No change needed in
  `src/main/java/ru/zahaand/smarttaskbot/handler/command/DoneCommandHandler.java`:
  same rationale as T003 — `getTaskId()` null return is permitted under Constitution v1.1.1
  (Principle VIII). Task closed as N/A.

- [X] T005 [P] [US1] Fix member declaration order in
  `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`:
  move `REMINDER_FORMATTER` (currently declared first) to AFTER the three injected
  dependencies (`taskRepository`, `userRepository`, `userService`); correct order is:
  injected deps → blank line → `REMINDER_FORMATTER` as helper object → blank line →
  no constants; note: `@RequiredArgsConstructor` ignores `static` fields so the
  generated constructor is unaffected by this reorder

- [X] T006 [P] [US1] Fix member declaration order in
  `src/main/java/ru/zahaand/smarttaskbot/handler/command/HelpCommandHandler.java`:
  move `notificationService` (currently declared second) to BEFORE `HELP_TEXT`;
  correct order: `private final NotificationService notificationService;` → blank line →
  `private static final String HELP_TEXT = ...;`

- [X] T007 [US1] Inline `getReminderTime()` in
  `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`
  (run after T005 — same file):
  replace the `getTaskDto()` body with a direct call to `formatReminder()`:
  `return new TaskDto(task.getId(), task.getText(), formatReminder(task.getReminderTime(), userZone));`
  then delete the `getReminderTime()` method entirely

**Checkpoint**: Zero startup WARNs on `mvn spring-boot:run`; no null returns in
`handler/`; no duplicate blank check; correct member ordering in all modified classes.
US1 is independently complete and verifiable by code review + startup log.

---

## Phase 4: Block 2 — Logging and Error Handling (US2, Priority: P2)

**Goal**: Every service class has `@Slf4j` and structured log calls; `SmartTaskBot`
has a centralized try-catch; no exception propagates above the handler layer; log levels
follow Constitution Principle VII (WARN for user-input errors, ERROR for system failures).

**Independent Test**: Start the application, trigger each error path (blank task, task
not found, simulated unhandled exception). Confirm ERROR entries include `userId`/`taskId`
identifiers. Confirm "Something went wrong. Please try again." is delivered to the user
on unhandled exceptions. Confirm startup log has zero WARN/ERROR entries.

### Implementation for Block 2

- [X] T008 [P] [US2] Add `@Slf4j` and centralized try-catch to
  `src/main/java/ru/zahaand/smarttaskbot/handler/SmartTaskBot.java`:
  add `@Slf4j` class annotation; wrap `dispatcher.dispatch(update)` in try-catch;
  in the catch block: `log.error("Unhandled exception processing update: {}", e.getMessage(), e)`;
  add private `extractChatId(Update update)` returning `Optional<Long>` (check
  `update.hasMessage()` then `update.hasCallbackQuery()` with non-null message, else
  return `Optional.empty()`); call `extractChatId(update).ifPresent(chatId -> execute(
  new SendMessage(chatId.toString(), "Something went wrong. Please try again.")))` inside
  a nested try-catch for `TelegramApiException`;
  add imports: `lombok.extern.slf4j.Slf4j`, `java.util.Optional`,
  `org.telegram.telegrambots.meta.api.methods.send.SendMessage`,
  `org.telegram.telegrambots.meta.exceptions.TelegramApiException`

- [X] T009 [US2] Add `@Slf4j` to
  `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`
  (run after T005 and T007 — same file):
  add `@Slf4j` class annotation; add `import lombok.extern.slf4j.Slf4j`

- [X] T010 [P] [US2] Add logging to `createTask()` in
  `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`
  (run after T009; parallel with T011):
  before blank-text throw: `log.warn("Blank task text from userId={}", telegramUserId)`;
  before too-long throw: `log.warn("Task text too long ({} chars) from userId={}",
  text.length(), telegramUserId)`;
  before user-not-found throw: `log.error("User not found during task creation: userId={}",
  telegramUserId)`;
  after `taskRepository.save()`: `log.info("Task created: id={}, userId={}",
  saved.getId(), telegramUserId)`

- [X] T011 [P] [US2] Add logging to `setReminder()` and `completeTask()` in
  `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`
  (run after T009; parallel with T010):
  in `setReminder()` — before task-not-found throw:
  `log.error("Task #{} not found for userId={}", taskId, telegramUserId)`;
  before completed-task throw:
  `log.warn("Reminder on completed task #{} by userId={}", taskId, telegramUserId)`;
  after save: `log.info("Reminder set: taskId={}, userId={}", taskId, telegramUserId)`;
  in `completeTask()` — before task-not-found throw:
  `log.error("Task #{} not found for userId={}", taskId, telegramUserId)`;
  after save: `log.info("Task completed: id={}, userId={}", saved.getId(), telegramUserId)`

- [X] T012 [P] [US2] Add `@Slf4j` to
  `src/main/java/ru/zahaand/smarttaskbot/service/UserService.java`:
  add `@Slf4j` class annotation; add `import lombok.extern.slf4j.Slf4j`

- [X] T013 [US2] Add logging to `register()` and `getTimezone()` in
  `src/main/java/ru/zahaand/smarttaskbot/service/UserService.java`
  (run after T012):
  in `register()` — after `userRepository.save(user)`:
  `log.info("User registered: userId={}", telegramUserId)`;
  in `getTimezone()` — inside `orElseThrow` lambda, before the exception:
  `log.error("Timezone lookup failed — user not found: userId={}", telegramUserId)`

**Checkpoint**: All service classes have `@Slf4j`; every exception site has a log call
at the correct level; any uncaught exception in `onUpdateReceived()` replies to the user
and logs ERROR. US2 is independently verifiable by log inspection.

---

## Phase 5: Block 3 — Deployment Configuration (US4, Priority: P4)

**Goal**: Railway deployment settings are captured in source control; no manual dashboard
configuration is needed beyond supplying environment variables.

**Independent Test**: `railway.toml` exists at the repository root with `builder`,
`buildCommand`, and `startCommand` explicitly set.

### Implementation for Block 3

- [X] T014 [US4] Create `railway.toml` at the repository root with the following content:

  ```toml
  [build]
  builder = "NIXPACKS"
  buildCommand = "mvn clean package -DskipTests"

  [deploy]
  startCommand = "java -jar target/smart-task-bot-*.jar"
  restartPolicyType = "ON_FAILURE"
  restartPolicyMaxRetries = 3
  ```

  Wildcard `*.jar` avoids hardcoding the artifact version and remains valid across
  version bumps.

**Checkpoint**: `railway.toml` exists at repo root with all three required fields.
US4 is independently verifiable by file inspection.

---

## Phase 6: Block 4 — Unit Tests (US3, Priority: P3)

**Goal**: All six service and handler classes have unit tests covering every public
method's happy path and at least one failure path. Tests run without a database or
network connection.

**Independent Test**: `mvn test -Dtest="TaskServiceTest,UserServiceTest,
ReminderServiceTest,NewTaskCommandHandlerTest,RemindCommandHandlerTest,
DoneCommandHandlerTest"` passes in an environment with no database or network access.

**Prerequisites**: Phases 3 and 4 must be complete — tests target the refactored code.

**Test conventions** (apply to every test class):
- `@ExtendWith(MockitoExtension.class)` — no Spring context
- `@Nested` class per method under test
- `@DisplayName` in English on every test method
- `@ParameterizedTest` + `@MethodSource` for boundary / equivalence-class cases
- Mock `Update`/`Message`/`User` chain via Mockito (no real Telegram API objects)

### Tests for Block 4

- [X] T015 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/service/TaskServiceTest.java`
  (run after T005, T007, T009–T011):
  Mocks: `TaskRepository`, `UserRepository`, `UserService`.
  ```
  @Nested class CreateTask {
    "creates task and returns DTO for valid input"
    "throws IllegalArgumentException for blank or null text"  ← @ParameterizedTest: "", "  ", null
    "throws IllegalArgumentException for text exceeding 500 characters"
    "throws IllegalStateException when user is not found"
  }
  @Nested class GetActiveTasks {
    "returns empty list when no active tasks exist for user"
    "returns DTOs with formatted reminder times for active tasks"
  }
  @Nested class SetReminder {
    "sets reminder and returns DTO with formatted time for valid input"
    "throws NoSuchElementException when task not found for user"
    "throws IllegalArgumentException when task status is COMPLETED"
    "throws DateTimeParseException for invalid datetime string"
  }
  @Nested class CompleteTask {
    "marks task COMPLETED and returns DTO for valid task"
    "throws NoSuchElementException when task not found for user"
  }
  ```

- [X] T016 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/service/UserServiceTest.java`
  (run after T012–T013):
  Mocks: `UserRepository`.
  ```
  @Nested class Register {
    "saves new user when not yet registered"
    "does not save when user already registered (idempotent)"
  }
  @Nested class IsRegistered {
    "returns true for a registered user"
    "returns false for an unknown user"
  }
  @Nested class GetTimezone {
    "returns timezone string for a registered user"
    "throws IllegalStateException when user not found"
  }
  ```

- [X] T017 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/service/ReminderServiceTest.java`
  (no source dep — `ReminderService` is unchanged in this branch):
  Mocks: `TaskRepository`, `NotificationService`.
  Call `processDueReminders()` directly; control behaviour via `taskRepository
  .findDueReminders(any())` and `taskRepository.findDueRetries(any())` stubs.
  Use `ArgumentCaptor<List<Task>>` to verify `saveAll()` is called with correct state.
  ```
  @Nested class ProcessDueReminders {
    // Selection
    "only passes reminders with reminderTime not after now to initial processing"
    "only passes retries with reminderRetryAt not after now to retry processing"
    "processes no tasks when both findDueReminders and findDueRetries return empty"
    // Initial reminder — success path
    "sets reminderProcessed true when initial send succeeds"
    // Initial reminder — failure path
    "sets reminderRetryAt to now+60s when initial send fails"
    "does not set reminderProcessed true when initial send fails"
    // Retry — success path
    "sets reminderProcessed true when retry send succeeds"
    // Retry — failure path (discard)
    "sets reminderProcessed true and discards reminder when retry send fails"
    // Isolation
    "continues processing remaining initial reminders after one send failure"
    "continues processing remaining retries after one retry send failure"
    // Persistence
    "calls saveAll after initial pass with all tasks regardless of send outcome"
    "calls saveAll after retry pass with all tasks regardless of send outcome"
  }
  ```

- [X] T018 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/handler/command/NewTaskCommandHandlerTest.java`
  (run after T002):
  Mocks: `TaskService`, `NotificationService`.
  Mock `Update`/`Message`/`User` chain via Mockito.
  After T002, blank input flows into `taskService.createTask()` which throws;
  parameterized test stubs the throw.
  ```
  @Nested class Handle {
    "calls taskService and sends success message for valid input"
    "forwards error message to user when taskService throws for blank input"
      ← @ParameterizedTest: stub createTask to throw for "", "  ", null input
    "forwards error message to user when taskService throws IllegalArgumentException
     (e.g. text over 500 chars)"
  }
  ```

- [X] T019 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/handler/command/RemindCommandHandlerTest.java`
  (run after T003):
  Mocks: `TaskService`, `NotificationService`, `UserService`.
  Mock `Update`/`Message`/`User` chain via Mockito.
  ```
  @Nested class Handle {
    "sends usage hint when no arguments are provided"
    "sends usage hint when fewer than 3 argument parts are given"
    "sends usage hint when task ID part is non-numeric"
    "sets reminder and sends success message for valid input"
    "sends format error message when datetime cannot be parsed (DateTimeParseException)"
    "sends error message when task not found (NoSuchElementException from taskService)"
    "sends error message when task is already completed (IllegalArgumentException)"
  }
  ```

- [X] T020 [P] [US3] Create
  `src/test/java/ru/zahaand/smarttaskbot/handler/command/DoneCommandHandlerTest.java`
  (run after T004):
  Mocks: `TaskService`, `NotificationService`.
  Mock `Update`/`Message`/`User` chain via Mockito.
  ```
  @Nested class Handle {
    "sends usage hint when no task ID is provided (blank args)"
    "sends usage hint when task ID is non-numeric"
    "marks task complete and sends success message for valid task ID"
    "sends error message when task not found (NoSuchElementException from taskService)"
  }
  ```

**Checkpoint**: All 6 test classes pass with zero failures; zero Spring context loaded;
`mvn test` (targeted) completes without database or network. US3 independently verifiable.

---

## Phase 7: Polish and Verification

**Purpose**: Confirm all blocks work together; validate quickstart.md against the
actual implementation.

- [ ] T021 Run `mvn test` (full suite) and confirm zero failures across all test classes
  including the existing `SmartTaskBotApplicationTests`

- [ ] T022 Run `mvn spring-boot:run` and confirm application startup log contains
  zero WARN and zero ERROR entries (validates SC-001 and T001)

- [ ] T023 Walk through `specs/002-quality-and-tests/quickstart.md` verification steps
  end-to-end: trigger each command manually via Telegram, confirm log output matches
  expected INFO/WARN/ERROR patterns, confirm user-facing messages are correct

---

## Dependencies and Execution Order

### Block Dependencies

```
Block 1 (T001–T007): No dependencies — start immediately
Block 2 (T008–T013): T008, T012 independent; T009 after T005+T007; T013 after T012
Block 3 (T014):      No dependencies — start immediately
─────────────────────────────────────────────────────────────
Block 4 (T015–T020): Each depends on the corresponding Block 1/2 source changes:
  T015 → after T005, T007, T009, T010, T011
  T016 → after T012, T013
  T017 → no source dep (ReminderService unchanged)
  T018 → after T002
  T019 → after T003
  T020 → after T004
─────────────────────────────────────────────────────────────
Polish (T021–T023): After all blocks complete
```

### Within-Block Dependencies

```
Block 1:
  T001, T002, T003, T004, T005, T006 — all parallel (different files)
  T007 — after T005 (same file: TaskService.java)

Block 2:
  T008, T012 — parallel (different files)
  T009 — after T005 + T007 (same file: TaskService.java)
  T010, T011 — parallel, after T009 (same file, different methods)
  T013 — after T012 (same file: UserService.java)

Block 4:
  T015, T016, T017, T018, T019, T020 — all parallel (different files)
```

### Parallel Opportunities

**Block 1** (6 parallel tasks):
```
T001  src/main/resources/application.yaml
T002  handler/command/NewTaskCommandHandler.java
T003  handler/command/RemindCommandHandler.java
T004  handler/command/DoneCommandHandler.java
T005  service/TaskService.java  ← T007 follows
T006  handler/command/HelpCommandHandler.java
```

**Block 2** (partial parallel):
```
T008  handler/SmartTaskBot.java
T012  service/UserService.java  ← T013 follows
# After T009:
T010  service/TaskService.java (createTask logging)
T011  service/TaskService.java (setReminder/completeTask logging)
```

**Block 4** (all 6 parallel):
```
T015  service/TaskServiceTest.java
T016  service/UserServiceTest.java
T017  service/ReminderServiceTest.java
T018  handler/command/NewTaskCommandHandlerTest.java
T019  handler/command/RemindCommandHandlerTest.java
T020  handler/command/DoneCommandHandlerTest.java
```

---

## Implementation Strategy

### MVP First (Block 1 only)

1. Complete T001–T007 in parallel (except T007 after T005)
2. **Stop and validate**: zero startup WARNs; code review passes for member ordering,
   null returns, and duplicate check removal
3. US1 complete — proceed to Block 2

### Incremental Delivery

1. Block 1 (T001–T007) → US1 complete: constitution-compliant code structure
2. Block 2 (T008–T013) → US2 complete: structured logging + error safety net
3. Block 3 (T014) → US4 complete: reproducible deployment config
4. Block 4 (T015–T020) → US3 complete: full unit test coverage
5. Polish (T021–T023) → all SCs verified

### Parallel Execution (if multiple developers)

```
Developer A: T001, T002, T005, T007, T009, T010, T011, T015
Developer B: T003, T004, T006, T008, T012, T013, T019, T020
Developer C: T014, T017, T018, T016
```

---

## Notes

- `[P]` tasks touch different files — safe to run concurrently
- Block 1 and Block 3 are fully independent and can be done in any order
- Block 4 tests mirror the source structure; place each `*Test.java` in the same
  package as the class under test
- `ReminderService` source is NOT modified in this branch — T017 tests existing behaviour
- Do not use `@SpringBootTest` in any test class (Constitution Principle IX)
- Do not return `null` from any new private method (Constitution Principle VIII)
- Log levels: WARN for user-input validation, ERROR for system failures (FR-010, research.md Finding 4)
