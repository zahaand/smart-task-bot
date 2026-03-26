# Research: Settings Menu and Release 1.0.0

**Branch**: `005-settings-release` | **Date**: 2026-03-26
**Status**: Complete — all unknowns resolved from codebase inspection

## Resolved Decisions

### RES-001: CASCADE deletion coverage

**Decision**: Existing migrations cover all cascades — no new migration needed.
**Rationale**: Migration 006 (`fk_tasks_users_cascade ON DELETE CASCADE`) covers `tasks`.
Migration 004 (`deleteCascade="true"` on `fk_user_states_users`) covers `user_states`.
Deleting the `users` row will automatically remove both child tables.
**Alternatives considered**: New migration for explicit CASCADE — rejected (already in place).

### RES-002: @UtilityClass eligibility

**Decision**: `CalendarKeyboardBuilder` and `TimeParserService` are both eligible.
**Rationale**: Code inspection confirms neither class has any injected Spring dependencies.
All fields are `static final`. All logic is stateless.

- `CalendarKeyboardBuilder`: annotated `@Component`, no injected fields → convert to `CalendarKeyboardBuilderUtils`
- `TimeParserService`: annotated `@Service`, no injected fields → convert to `TimeParserUtils`
  **Alternatives considered**: Keep as Spring beans — rejected, violates Dev Std #8.
  `TaskListKeyboardBuilder` has `MessageService` injected → NOT eligible, stays as `@Component`.

### RES-003: ConversationState storage type

**Decision**: `state VARCHAR(50)` — no migration needed for `CONFIRMING_DELETE_ACCOUNT`.
**Rationale**: Migration 004 defines `state VARCHAR(50) defaultValue="IDLE"`. PostgreSQL
stores the enum name as a plain string. Adding a new Java enum constant requires only a
code change.
**Alternatives considered**: N/A.

### RES-004: CreateTaskRequest status

**Decision**: DELETE — it is dead code.
**Rationale**: `grep -rn "CreateTaskRequest"` across all `.java` files returns only the
class declaration itself. `TaskService.createTask()` takes `(Long, String)` parameters
directly, never using `CreateTaskRequest`. Renaming and keeping unused code would violate
Dev Std #3.
**Alternatives considered**: Rename to `CreateTaskRequestDto` — rejected (never instantiated
anywhere; keeping it would be speculative/orphaned code).

### RES-005: Settings handler architecture

**Decision**: Single `SettingsCallbackHandler` class handles all `SETTINGS_*` callbacks.
**Rationale**: Matches the existing pattern (`DeleteAllCompletedCallbackHandler` handles
3 related callbacks in one class). Settings is a cohesive feature with a single
responsibility (user account preferences). A single handler is simpler than 7 small ones.
**Alternatives considered**: Reuse `LanguageCallbackHandler` / `TimezoneCallbackHandler` —
rejected because those handlers embed registration-flow state transitions (`AWAITING_TIMEZONE`,
persistent menu after timezone set) that are incompatible with the settings context.
Reuse at the service layer (`UserService.updateLanguage/updateTimezone`) is correct.

### RES-006: Post-creation action buttons

**Decision**: New `sendTaskCreatedWithActions(Long chatId, Long taskId, String taskText, Language)` in
`NotificationService`.
**Rationale**: Handler layer must remain thin (Constitution I). Keyboard construction and
message formatting belong in `NotificationService`. Reuses existing callback constants
`CB_TASK_REMIND`, `CB_TASK_DONE`, `CB_TASK_DELETE` — `TaskActionCallbackHandler` already
handles these correctly and already guards against missing tasks (`NoSuchElementException`).
**Alternatives considered**: Inline keyboard in handler — rejected (business logic in handler).

### RES-007: commons-lang3 version

**Decision**: Declare without explicit `<version>` — managed by Spring Boot BOM.
**Rationale**: Spring Boot 3.5.x BOM manages `commons-lang3` at version 3.17.0 (already
on transitive classpath). Declaring without version ensures BOM alignment and avoids
version drift.
**Alternatives considered**: Explicit version pin — rejected (BOM management is the
Spring Boot best practice).

### RES-008: Liquibase XML comments

**Decision**: No action required — already compliant.
**Rationale**: `grep "<!--" src/main/resources/db/changelog/*.xml` returns no matches.
All migration files are already free of XML comments.
**Alternatives considered**: N/A.

### RES-009: railway.toml startCommand

**Decision**: Change from wildcard `smart-task-bot-*.jar` to exact `smart-task-bot-1.0.0.jar`.
**Rationale**: FR-021 requires the `startCommand` to reference the correct 1.0.0 filename
for reproducible deploys. The wildcard works but is not deterministic.
**Alternatives considered**: Keep wildcard — rejected per spec FR-021.

### RES-010: StringUtils null/blank replacement scope

**Decision**: Replace manual `null || isBlank()` / `null || isEmpty()` string checks only.
Object-null checks (e.g. `ctx == null`, `user != null ? ...`) are NOT string checks and
are NOT subject to this rule.
**Affected locations identified**:

- `TimeParserUtils.java`: 2 occurrences of `input == null || input.isBlank()`
- `TaskService.java`: `text == null || text.isBlank()`
- `TaskCreationTextHandler.java`: `text.isBlank()` (no null risk post-trim, but standardise)
- `UpdateDispatcher.java`: `text != null && text.startsWith("/")` + null guard before split
- `UserStateService.java`: `json != null && !json.isBlank()`
