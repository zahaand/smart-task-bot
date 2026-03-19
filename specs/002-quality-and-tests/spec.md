# Feature Specification: Quality Improvements and Test Coverage

**Feature Branch**: `002-quality-and-tests`
**Created**: 2026-03-19
**Status**: Draft
**Input**: User description: "Close MVP technical debt and cover code with tests. After this phase the project must be fully ready for new functionality."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Code Conformance to Constitution (Priority: P1)

A developer working on the codebase finds that all existing classes follow the structural
and style rules defined in Constitution v1.1.0 (Principles VII and VIII): correct class
member declaration order, constructor injection, `final` fields, verb-prefixed private
methods, and no duplicate validation logic.

**Why this priority**: Structural violations accumulate and become harder to fix the longer
they persist. Clean, uniform code is a prerequisite for confident refactoring and new
feature work. This is the lowest-risk, highest-readability win.

**Independent Test**: Verify by code review that `NewTaskCommandHandler` contains no blank
check duplicated from `TaskService`, that `TaskService` has no `getReminderTime()` method,
and that all classes in `handler/` and `service/` follow the member ordering rule from
Principle VIII.

**Acceptance Scenarios**:

1. **Given** `NewTaskCommandHandler.handle()` receives a blank task name,
   **When** the blank check runs,
   **Then** the check exists only in `TaskService`, not in the handler.
2. **Given** a reminder needs formatting,
   **When** `TaskService` formats it,
   **Then** `formatReminder()` is called directly — `getReminderTime()` does not exist.
3. **Given** any service or handler class,
   **When** its members are listed in declaration order,
   **Then** the order is: injected dependencies → helper objects → constants.

---

### User Story 2 — Operational Reliability: Logging and Error Handling (Priority: P2)

A developer can observe every significant business operation outcome in the application
logs, and no unhandled exception can crash the Telegram update processing loop or surface
an uninformative error to the user.

**Why this priority**: Without structured logging and a safety net at the entry point,
failures in production are invisible and any unhandled exception stops the bot from
responding. This must be in place before the project is extended.

**Independent Test**: Start the application, send a command that triggers an error path,
and confirm: (a) the log contains an ERROR entry with context identifiers, and (b) the
user receives "Something went wrong. Please try again." instead of silence. Confirm INFO
entries appear for successful operations. Confirm startup produces zero WARN entries.

**Acceptance Scenarios**:

1. **Given** a service class processes a business operation successfully,
   **When** the operation completes,
   **Then** an INFO entry is written to the log identifying the operation.
2. **Given** a service class encounters an exception,
   **When** the exception is raised,
   **Then** an ERROR entry is written with `telegramUserId` and relevant entity ID before
   the exception propagates.
3. **Given** any unhandled exception reaches the Telegram update entry point,
   **When** the exception is caught,
   **Then** an ERROR entry is logged and the user receives
   "Something went wrong. Please try again."
4. **Given** the application is started cleanly,
   **When** the startup log is reviewed,
   **Then** no WARN entries appear for open-in-view or dialect auto-detection.

---

### User Story 3 — Unit Test Coverage for Core Logic (Priority: P3)

A developer can run the full test suite and receive fast, deterministic feedback on the
correctness of all core service and handler methods without needing a running database
or Telegram connection.

**Why this priority**: Tests are the safety net that makes future feature additions safe.
They must exist before new functionality is layered on top of the existing code.

**Independent Test**: Run the test suite in an environment without a database or network
access. All tests pass. Coverage includes every method listed in the requirements.

**Acceptance Scenarios**:

1. **Given** `TaskService.createTask()` is called with valid input,
   **When** the test runs,
   **Then** the task is persisted and returned; no exception is thrown.
2. **Given** `TaskService.createTask()` is called with a blank name,
   **When** the test runs,
   **Then** an appropriate exception is thrown and no persistence occurs.
3. **Given** `UserService.register()` is called for an already-registered user,
   **When** the test runs,
   **Then** no save occurs and no duplicate is created.
4. **Given** `ReminderService.processDueReminders()` runs when due reminders exist,
   **When** the test runs,
   **Then** only reminders whose scheduled time has passed are selected and processed.
5. **Given** `processDueReminders()` encounters a send failure for a reminder that has
   not yet been retried,
   **When** the test runs,
   **Then** `reminder_retry_at` is set to schedule a retry and the reminder is not
   marked processed; processing continues for remaining reminders.
6. **Given** `processDueReminders()` encounters a send failure for a reminder that is
   already on its retry attempt,
   **When** the test runs,
   **Then** `reminder_processed` is set to `true`, a WARN is logged, and processing
   continues for remaining reminders.
7. **Given** `NewTaskCommandHandler.handle()` receives a blank task name,
   **When** the test runs,
   **Then** no task is created and the user receives a validation error message.
8. **Given** `NewTaskCommandHandler.handle()` receives valid input,
   **When** the test runs,
   **Then** the task is created and a success message is sent to the user.
9. **Given** `RemindCommandHandler.handle()` receives valid input with a correct task ID,
   **When** the test runs,
   **Then** the reminder is set and a success message is sent to the user.
10. **Given** `RemindCommandHandler.handle()` receives input with an invalid date/time format,
    **When** the test runs,
    **Then** no reminder is set and the user receives a format error message.
11. **Given** `RemindCommandHandler.handle()` receives a task ID that does not belong to
    the user,
    **When** the test runs,
    **Then** no reminder is set and the user receives a not-found error message.
12. **Given** `DoneCommandHandler.handle()` receives a valid task ID that belongs to the user,
    **When** the test runs,
    **Then** the task is marked complete and a success message is sent to the user.
13. **Given** `DoneCommandHandler.handle()` receives a task ID that does not belong to
    the user,
    **When** the test runs,
    **Then** the task is not modified and the user receives a not-found error message.
14. **Given** `DoneCommandHandler.handle()` is called without a task ID,
    **When** the test runs,
    **Then** no task is modified and the user receives a usage/format error message.

---

### User Story 4 — Reproducible Deployment Configuration (Priority: P4)

A developer can deploy the application to Railway by pushing the repository, with all
deployment settings captured in source-controlled configuration — no manual dashboard
configuration required beyond supplying environment variables.

**Why this priority**: Deployment settings that live only in a cloud dashboard are
invisible and non-reproducible. Capturing them in code is a one-time, low-effort step
that makes the project portable and professionally complete.

**Independent Test**: Connect the repository to a fresh Railway project, set the required
environment variables, and trigger a deploy. The application starts without any manual
Railway dashboard changes beyond env vars.

**Acceptance Scenarios**:

1. **Given** the repository is connected to Railway,
   **When** a deploy is triggered,
   **Then** Railway reads build and start settings from a configuration file at the
   repository root.
2. **Given** the deployment configuration file is present,
   **When** its contents are reviewed,
   **Then** the builder, build command, and start command are explicitly specified.

---

### Edge Cases

- What happens when a service method receives a `null` argument instead of a blank string?
  Validation MUST reject `null` with the same outcome as blank input.
- What happens when `processDueReminders()` runs and the send call fails for one reminder?
  The failure MUST be logged at ERROR level and processing MUST continue for the remaining
  reminders (no full-batch abort).
- What happens when an exception occurs before the user's chat ID is known?
  The ERROR MUST still be logged even if no reply can be sent to the user.

## Clarifications

### Session 2026-03-19

- Q: Does `ReminderService` need new retry logic, or should tests cover existing retry behavior? → A: Retry logic is already implemented in MVP (001-task-bot-mvp) using `reminder_retry_at` field for one retry attempt, then `reminder_processed = true` + ERROR log. Tests MUST cover this existing behavior as-is — no changes to retry logic in this phase.
- Q: What was the intended test class behind the cut-off "R" in the feature description? → A: Two handler classes — `RemindCommandHandler.handle()` (success, invalid format, wrong task ID) and `DoneCommandHandler.handle()` (success, task not found, no ID provided).
- Q: Which layers does Principle VIII refactoring (member ordering, final fields, constructor injection) apply to? → A: `handler/` and `service/` only. `repository/` interfaces, `model/`, `dto/`, and `config/` are out of scope for this phase.
- Q: Should FR-010 distinguish log levels for user-input vs. system exceptions? → A: Yes. Constitution Principle VII takes precedence over the original literal wording of FR-010. User-input validation exceptions MUST be logged at WARN; system failures and unexpected exceptions MUST be logged at ERROR. FR-010 updated accordingly.

## Requirements *(mandatory)*

### Functional Requirements

**Block 1 — Technical Debt Elimination**

- **FR-001**: The blank-input check for task names MUST exist only in `TaskService`;
  the duplicate check in `NewTaskCommandHandler` MUST be removed.
- **FR-002**: `TaskService` MUST NOT contain a `getReminderTime()` method; `formatReminder()`
  MUST be called directly wherever reminder text is needed.
- **FR-003**: The application configuration MUST explicitly disable the open-in-view
  persistence session pattern.
- **FR-004**: The application configuration MUST NOT contain an explicit database dialect
  setting; auto-detection MUST be relied upon.
- **FR-005**: All classes in `handler/` and `service/` MUST declare members in the order:
  injected dependencies → helper objects → constants, with one blank line separating each group.
- **FR-006**: All fields in `handler/` and `service/` classes MUST be immutable after
  construction.
- **FR-007**: Constructor injection MUST be used in all classes; field-level injection
  annotations are PROHIBITED.

**Block 2 — Logging and Error Handling**

- **FR-008**: Every class in `service/` MUST be annotated for structured logging via Lombok.
- **FR-009**: Every successful business operation completion MUST produce an INFO log entry.
- **FR-010**: Exceptions MUST be logged with `telegramUserId` and any relevant entity
  identifier before being thrown or re-thrown. Log level rules:
  user-input validation exceptions (blank text, invalid format, completed-task guard)
  MUST be logged at WARN level; system failures and unexpected exceptions (user not found,
  task not found, unhandled errors) MUST be logged at ERROR level.
- **FR-011**: The Telegram update entry point MUST wrap all processing in a single top-level
  error handler, log any unhandled exception at ERROR level, and send the user:
  "Something went wrong. Please try again."
- **FR-012**: No exception MAY propagate above the handler layer.

**Block 3 — Deployment Configuration**

- **FR-013**: A deployment configuration file (`railway.toml`) MUST exist at the repository
  root, specifying the builder, build command, and start command.

**Block 4 — Unit Tests**

- **FR-014**: Unit tests MUST cover `TaskService` methods: `createTask()`, `getActiveTasks()`,
  `setReminder()`, `completeTask()`.
- **FR-015**: Unit tests MUST cover `UserService` methods: `register()`, `isRegistered()`,
  `getTimezone()`.
- **FR-016**: Unit tests MUST cover `ReminderService.processDueReminders()`, including:
  the due-reminder selection logic; the single-retry attempt via `reminder_retry_at`
  (first failure schedules a retry, not immediate discard); and the final-failure path
  where the retry attempt also fails — `reminder_processed` is set to `true` and a
  WARN is logged. Tests MUST cover existing behavior only — retry logic MUST NOT be
  changed.
- **FR-017**: Unit tests MUST cover `NewTaskCommandHandler.handle()` for at minimum:
  success path, blank-input path, and validation-error path.
- **FR-021**: Unit tests MUST cover `RemindCommandHandler.handle()` for at minimum:
  success path, invalid date/time format path, and wrong task ID path.
- **FR-022**: Unit tests MUST cover `DoneCommandHandler.handle()` for at minimum:
  success path, task not found path, and no ID provided path.
- **FR-018**: All unit tests MUST use mocked dependencies; no database or network connection
  is required to run the suite.
- **FR-019**: Tests MUST be structured with nested classes per method under test and carry
  display names in English describing the scenario.
- **FR-020**: Boundary and equivalence-class cases MUST use parameterized tests.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Application startup produces zero WARN or ERROR log entries under normal
  configuration.
- **SC-002**: Any unhandled exception during update processing results in a user-visible
  error message within the same interaction — no silent failure.
- **SC-003**: Every ERROR log entry for a business-layer exception includes at least one
  user or entity identifier (`telegramUserId`, `taskId`, etc.).
- **SC-004**: The full unit test suite completes successfully in an environment with no
  database or network access.
- **SC-005**: Each of the six service/handler classes listed in Block 4 (FR-014–FR-017,
  FR-021–FR-022) has at least one test per public method, covering the happy path and
  at least one failure path.
- **SC-006**: A fresh deployment to Railway using only the repository and required
  environment variables succeeds without manual dashboard configuration.
- **SC-007**: No class in `handler/` or `service/` contains duplicate validation logic
  already enforced by a lower layer.

## Assumptions

- The "R" at the end of the feature description was the start of `RemindCommandHandler`;
  `DoneCommandHandler` was also intended. Both are captured in FR-021 and FR-022.
- `railway.toml` will target a Maven build (`mvn clean package -DskipTests`) and use a
  wildcard start command (`java -jar target/smart-task-bot-*.jar`) to avoid hardcoding
  the artifact version.
- "All service classes" refers to classes in the `service/` package of the existing
  MVP codebase.
- Per-reminder failure isolation means a failed send for reminder N does not prevent
  processing of reminder N+1; this behavior MUST have a dedicated test.
- `ReminderService` retry logic (one retry via `reminder_retry_at`, then
  `reminder_processed = true` + WARN log) was implemented in MVP (001-task-bot-mvp)
  and MUST NOT be changed in this phase — only tested.
- Read-only query methods (e.g. `getActiveTasks()`) are exempt from the FR-009 INFO
  logging requirement. FR-009 applies to write operations only (create, update, complete).