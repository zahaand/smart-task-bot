# Feature Specification: Settings Menu and Release 1.0.0

**Feature Branch**: `005-settings-release`
**Created**: 2026-03-26
**Status**: Draft
**Input**: User description: "Добавить Settings меню, провести финальный code review и выпустить релизную версию 1.0.0"

## Clarifications

### Session 2026-03-26

- Q: Is CASCADE deletion of tasks and user_states already covered by existing migrations? → A: Yes. Migration 006 adds
  ON DELETE CASCADE to `tasks`; migration 004 already declares `deleteCascade="true"` on `user_states`. No new migration
  is required for account deletion.
- Q: Are CalendarKeyboardBuilder and TimeParserService eligible for @UtilityClass? → A: Both are eligible. Neither has
  injected Spring dependencies. Both must be converted: `CalendarKeyboardBuilder` → `CalendarKeyboardBuilderUtils`;
  `TimeParserService` → `TimeParserUtils`. All callers must be updated in the same commit.
- Q: Is ConversationState stored as a PostgreSQL ENUM or VARCHAR? → A: VARCHAR(50), confirmed in migration 004. Adding
  `CONFIRMING_DELETE_ACCOUNT` to the Java enum requires no database migration.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Settings Menu (Priority: P1)

A registered user opens the persistent menu and taps ⚙️ Settings. The bot displays four
options as inline buttons. The user can change their language, change their timezone,
reset their conversation state, or delete their account. All actions work without losing
task data unless the user explicitly chooses to delete their account.

**Why this priority**: Settings are a foundational UX feature that enables users to
self-serve corrections to registration choices (wrong language, wrong timezone). Without
this, users have no recovery path. It must ship before release 1.0.0.

**Independent Test**: A registered user can open Settings, tap each of the four option
buttons, and see the appropriate response — independently of any other new feature in
this branch.

**Acceptance Scenarios**:

1. **Given** a registered user, **When** they tap ⚙️ Settings in the persistent menu,
   **Then** the bot sends a message with four inline buttons: Change Language, Change
   Timezone, Reset State, Delete Account — all in the user's current language.

2. **Given** a user taps Change Language, **When** they select a new language,
   **Then** the bot confirms the change and all subsequent messages use the new language.
   Tasks and reminders are unaffected.

3. **Given** a user taps Change Timezone, **When** they select a new timezone,
   **Then** the bot confirms the change and all subsequent reminder times use the new
   timezone. Tasks and reminders are unaffected.

4. **Given** a user taps Reset State, **When** the bot receives the action,
   **Then** the user's conversation state is set to IDLE and the bot sends a
   localised confirmation. No tasks or user data are deleted.

5. **Given** a user taps Delete Account, **When** the bot sends a confirmation prompt
   and the user confirms, **Then** the bot deletes the user and all their data (tasks,
   reminders, conversation state) and sends a farewell message. The user returns to
   the unregistered state.

6. **Given** a user taps Delete Account and receives the confirmation prompt,
   **When** the user taps Cancel, **Then** no data is deleted and the bot acknowledges
   the cancellation.

---

### User Story 2 — Task Created with Action Buttons (Priority: P1)

After a task is successfully created, the bot immediately shows a confirmation message
with three inline action buttons — Remind, Complete, Delete — so the user can act on
the new task without opening the full task list.

**Why this priority**: This closes a UX gap where newly created tasks are invisible
until the user navigates to the task list. Immediate post-creation actions are expected
in modern task-management UIs and increase engagement.

**Independent Test**: Create a task via /newtask or the New Task button. Verify the
confirmation message contains three buttons and that each button performs its correct
action on the newly created task.

**Acceptance Scenarios**:

1. **Given** a user creates a task, **When** the bot confirms creation, **Then** the
   confirmation message includes three inline buttons: Remind, Complete, Delete — in
   the user's language.

2. **Given** a user taps Remind on the task-creation confirmation, **When** they select
   a date and time, **Then** a reminder is scheduled exactly as if the action was
   triggered from the task list.

3. **Given** a user taps Complete on the task-creation confirmation, **When** confirmed,
   **Then** the task is marked complete and the confirmation message is acknowledged.

4. **Given** a user taps Delete on the task-creation confirmation, **When** the user
   confirms the deletion prompt, **Then** the task is deleted.

5. **Given** a user taps any action button on a task that was already deleted or
   completed, **When** the handler processes the callback, **Then** the bot responds
   with a user-friendly error message and no crash or data corruption occurs.

---

### User Story 3 — Code Quality Cleanup (Priority: P2)

The codebase is audited and updated to fully comply with Constitution v1.4.0: all
deprecated and orphaned code is removed, DTO and utility class names follow the mandated
suffixes, Liquibase migrations are stripped of XML comments, commons-lang3 is added as
an explicit dependency, and all manual null/blank string checks are replaced with
StringUtils.

**Why this priority**: Prerequisite for release 1.0.0. A constitution-compliant codebase
is required before tagging a public release. P2 because it does not change user-visible
behaviour and can be completed after P1 stories.

**Independent Test**: All automated tests pass, no `@Deprecated` annotations remain,
all DTO classes carry the `Dto` suffix, all static-only helper classes use `@UtilityClass`
and the `Utils` suffix, no XML comments exist in migration files, and
`StringUtils.isBlank()` is used consistently.

**Acceptance Scenarios**:

1. **Given** the codebase, **When** reviewed, **Then** no `@Deprecated` classes,
   methods, or fields remain.

2. **Given** all DTO classes, **When** reviewed, **Then** every DTO class name ends
   with `Dto` (e.g. `TaskDto`, `CreateTaskRequestDto`).

3. **Given** all static utility classes with no Spring injection, **When** reviewed,
   **Then** each carries `@UtilityClass` and its name ends with `Utils`.

4. **Given** all Liquibase migration XML files, **When** reviewed, **Then** no
   `<!-- ... -->` XML comments are present.

5. **Given** `pom.xml`, **When** reviewed, **Then** `commons-lang3` appears as an
   explicit top-level dependency.

6. **Given** all service and handler classes, **When** reviewed, **Then** no manual
   `null` or `.isEmpty()` string checks remain; `StringUtils` equivalents are used.

---

### User Story 4 — Release 1.0.0 (Priority: P1)

The project is versioned as 1.0.0, the deployment descriptor is updated for the new JAR
name, and the README reflects the full feature set including the Settings menu.

**Why this priority**: Release 1.0.0 is the branch's primary deliverable. All P1 user
stories and the code quality cleanup must be complete before this story is executed.

**Independent Test**: The JAR builds successfully as 1.0.0, the bot starts on Railway
with the new version, and the README accurately describes all current features.

**Acceptance Scenarios**:

1. **Given** `pom.xml`, **When** built, **Then** the produced JAR is named
   `smart-task-bot-1.0.0.jar`.

2. **Given** `railway.toml`, **When** reviewed, **Then** the `startCommand` references
   the 1.0.0 JAR filename.

3. **Given** the README, **When** reviewed, **Then** it includes a version badge showing
   1.0.0, a description of the Settings menu, and all feature examples reflect the
   current bot behaviour.

4. **Given** the full codebase, **When** reviewed against Constitution v1.4.0,
   **Then** no violations are found.

---

### Edge Cases

- What happens when a user taps Change Language or Change Timezone during an in-progress
  flow (e.g. mid task creation)? → Settings actions reset the conversation state to IDLE
  before initiating the settings flow.
- What happens when the Delete Account confirmation is left unanswered and the user sends
  another message? → The existing CONFIRMING_DELETE_ACCOUNT state remains active and the
  bot re-prompts or handles per the stale-state guard pattern used elsewhere.
- What happens when a user taps Reset State while already in IDLE? → The bot still
  responds with the confirmation message; the operation is idempotent.
- What happens when post-creation action buttons are tapped on a task deleted between
  creation and the button tap? → The handler detects the missing task and responds
  with an error message; no unhandled exception occurs.
- What happens if a DTO or utility class rename breaks a callers import? → All callers
  must be updated in the same commit; the project MUST compile and all tests MUST pass.

## Requirements *(mandatory)*

### Functional Requirements

**Settings Menu**

- **FR-001**: The persistent menu MUST include a third button ⚙️ Settings / ⚙️ Настройки
  visible to all registered users.
- **FR-002**: Tapping Settings MUST display four inline option buttons in the user's
  current language: Change Language, Change Timezone, Reset State, Delete Account.
- **FR-003**: Change Language MUST reuse the existing language-selection flow and update
  the `language` field on the User entity without affecting tasks or reminders.
- **FR-004**: Change Timezone MUST reuse the existing timezone-selection flow and update
  the `timezone` field on the User entity without affecting tasks or reminders.
- **FR-005**: Reset State MUST set the user's conversation state to IDLE and send a
  localised confirmation message. No user data MUST be deleted.
- **FR-006**: Delete Account MUST send a localised confirmation prompt with Confirm and
  Cancel inline buttons before performing any deletion.
- **FR-007**: On Delete Account confirmation, the system MUST delete the user record and
  all associated data (tasks, reminders, conversation state) and return the user to the
  unregistered state. CASCADE deletion is guaranteed by existing migrations (006 for tasks,
  004 for user_states) — no new migration is required.
- **FR-008**: All Settings callbacks MUST use dedicated prefixes: `SETTINGS_MENU`,
  `SETTINGS_LANG`, `SETTINGS_TZ`, `SETTINGS_RESET`, `SETTINGS_DELETE_REQUEST`,
  `SETTINGS_DELETE_CONFIRM`, `SETTINGS_DELETE_CANCEL`.
- **FR-009**: All Settings-related user-facing strings MUST be served through
  MessageService; no hardcoded strings are permitted (Constitution Principle X).

**Task Created with Action Buttons**

- **FR-010**: After successful task creation, the bot MUST send a confirmation message
  that includes three inline buttons: Remind, Complete, Delete — in the user's language.
- **FR-011**: Each post-creation button MUST behave identically to its counterpart
  in the task list (same handler logic, same state transitions).
- **FR-012**: If a post-creation action targets a task that no longer exists, the bot
  MUST respond with a localised error message and MUST NOT throw an unhandled exception.

**Code Quality Cleanup**

- **FR-013**: All `@Deprecated` classes, methods, and fields MUST be removed.
- **FR-014**: All unused classes, methods, and fields MUST be removed
  (Constitution Development Standard #3).
- **FR-015**: All DTO classes MUST carry the `Dto` suffix; `CreateTaskRequest` MUST
  be renamed to `CreateTaskRequestDto` and all callers updated
  (Constitution Development Standard #7).
- **FR-016**: All static-only utility classes without Spring injection MUST be annotated
  with `@UtilityClass` and renamed to carry the `Utils` suffix
  (Constitution Development Standard #8). Confirmed eligible: `CalendarKeyboardBuilder` →
  `CalendarKeyboardBuilderUtils`; `TimeParserService` → `TimeParserUtils`. Both have no
  injected dependencies. All callers must be updated in the same commit.
- **FR-017**: All XML comments (`<!-- -->`) MUST be removed from Liquibase migration
  files (Constitution Development Standard #9).
- **FR-018**: `commons-lang3` MUST be declared as an explicit dependency in `pom.xml`
  (Constitution Development Standard #10).
- **FR-019**: All manual `null`/`.isEmpty()` string checks MUST be replaced with
  `StringUtils` equivalents (Constitution Development Standard #10).

**Release 1.0.0**

- **FR-020**: The version in `pom.xml` MUST be changed from `0.0.1-SNAPSHOT` to `1.0.0`.
- **FR-021**: `railway.toml` `startCommand` MUST reference the 1.0.0 JAR filename.
- **FR-022**: The README MUST include a version 1.0.0 badge, a Settings menu description,
  and updated feature examples that reflect the current bot behaviour.

### Key Entities

- **User**: Existing entity. `language` and `timezone` fields (added in feature 004)
  are updated via Settings flows. No new fields required.
- **UserState**: Existing entity. Extended with one new state value:
  `CONFIRMING_DELETE_ACCOUNT`. Reset to `IDLE` via the Reset State action.
  The `state` column is `VARCHAR(50)` (not a PostgreSQL ENUM), so adding this value
  requires no database migration — only a Java enum change.
- **Task**: Existing entity. No schema changes. Newly created tasks expose action
  buttons in the creation confirmation message.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A registered user can access all four Settings options within two taps
  from any bot state, with no navigation dead-ends.
- **SC-002**: Language and timezone changes take effect on the very next bot message,
  with zero tasks or reminders lost.
- **SC-003**: Account deletion removes all user data within one confirmation tap, with
  zero orphaned records remaining in the database.
- **SC-004**: A newly created task shows its three action buttons in the creation
  confirmation message 100% of the time, in the user's selected language.
- **SC-005**: After the code quality cleanup, the codebase contains zero violations
  of Constitution v1.4.0 (verified by automated tests and manual review).
- **SC-006**: All automated tests pass on the 1.0.0 build with zero skipped or failing
  tests.
- **SC-007**: The bot deploys and starts successfully on Railway after the version bump
  with no manual intervention beyond a standard deploy.

## Assumptions

- The existing `LanguageCallbackHandler` and `TimezoneCallbackHandler` can be reused
  for the Settings flows with a minimal distinguishing flag or entry-point callback prefix
  to differentiate registration-context from settings-context.
- `CalendarKeyboardBuilder` has no injected Spring dependencies and is eligible for
  `@UtilityClass`; it will be renamed `CalendarKeyboardBuilderUtils` (confirmed by
  code inspection in the 004 branch).
- `TimeParserService` has no injected Spring dependencies and is eligible for
  `@UtilityClass`; it will be renamed `TimeParserUtils` (confirmed by code inspection).
- CASCADE deletion of tasks and user_states is already fully configured: migration 006
  covers `tasks`; migration 004 covers `user_states`. No new migration is needed for
  account deletion.
- Post-creation action buttons reuse the existing `TaskActionCallbackHandler`; no new
  handler class is required.
- The Settings menu button is added to the existing persistent-menu keyboard builder;
  the builder is updated in-place rather than replaced.
- `ConversationState` is stored as `VARCHAR(50)` — confirmed in migration 004.
  Adding `CONFIRMING_DELETE_ACCOUNT` requires only a Java enum change; no Liquibase
  migration is needed.
