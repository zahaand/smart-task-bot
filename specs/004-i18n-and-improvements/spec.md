# Feature Specification: i18n, UX Improvements, and Professional Polish

**Feature Branch**: `004-i18n-and-improvements`
**Created**: 2026-03-20
**Status**: Draft
**Input**: User description: "Add bilingual support (EN/RU), improve UX, and bring the project to a professional standard for client demonstrations."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Bilingual Interface (Priority: P1)

A new user writes `/start`. The bot replies with a bilingual welcome message and presents
two language-selection buttons. After the user picks a language, the bot continues the
registration flow (timezone selection) and all subsequent responses appear in the chosen
language. Existing users registered before this feature receive a default language of EN.

**Why this priority**: The entire UX depends on knowing the user's language. No other
story can be validated without this foundation. It directly fulfils Constitution v1.3.0
Principle X.

**Independent Test**: A fresh Telegram account sends `/start`, selects a language, and
every subsequent bot message — including registration prompts, task confirmations, and
error messages — arrives in that language.

**Acceptance Scenarios**:

1. **Given** a new user with no registration, **When** they send `/start`, **Then** the
   bot creates a `UserState` row in `AWAITING_LANGUAGE` and replies with a message
   containing both English and Russian text, and two inline buttons: `🇬🇧 English`
   and `🇷🇺 Русский`.
2. **Given** the user is in `AWAITING_LANGUAGE` state, **When** they tap `🇬🇧 English`,
   **Then** the bot stores `EN`, transitions `UserState` to `AWAITING_TIMEZONE`, and
   all subsequent messages are in English.
3. **Given** the user is in `AWAITING_LANGUAGE` state, **When** they tap `🇷🇺 Русский`,
   **Then** the bot stores `RU`, transitions `UserState` to `AWAITING_TIMEZONE`, and
   all subsequent messages are in Russian.
4. **Given** `UserState` transitions to `AWAITING_TIMEZONE`, **When** the bot shows the
   timezone prompt, **Then** it is displayed in the user's selected language.
5. **Given** a registered user with `EN`, **When** they interact with any feature, **Then**
   every message, button label, error, and notification is in English.
6. **Given** a registered user with `RU`, **When** they interact with any feature, **Then**
   every message, button label, error, and notification is in Russian.
7. **Given** handler and service classes, **When** reviewed, **Then** no user-facing string
   is hardcoded; all text is retrieved from a centralised message-provider by key.
8. **Given** a fully-registered user, **When** they send `/start`, **Then** the bot replies
   with "You are already registered" in their stored language and shows the main menu.
   The registration flow is NOT re-triggered.

---

### User Story 2 — ON DELETE CASCADE for User Data (Priority: P1)

When a user record is deleted, all tasks and user-state records belonging to that user
are automatically removed at the database level, without requiring explicit cleanup logic
in application code.

**Why this priority**: Missing cascades cause orphaned rows, referential integrity
violations, and data leaks. This is a correctness prerequisite rather than an enhancement.

**Independent Test**: Delete a user record directly in the database. Verify that all
corresponding tasks and user_states rows are gone without any application involvement.

**Acceptance Scenarios**:

1. **Given** a user with tasks and user_states, **When** the user record is deleted,
   **Then** all their tasks are deleted automatically.
2. **Given** a user with tasks and user_states, **When** the user record is deleted,
   **Then** all their user_states records are deleted automatically.
3. **Given** the cascade migration, **When** it is rolled back, **Then** the schema is
   restored to its previous state without data loss.

---

### User Story 3 — Extended Time Input Formats (Priority: P2)

Users entering task reminders can use a space (`14 00`) or a hyphen (`14-00`) as a
time separator in addition to the colon format (`14:00`). All three formats are treated
identically.

**Why this priority**: Reduces friction on mobile keyboards where `:` requires an extra
tap. Non-blocking for core functionality.

**Independent Test**: Submit task creation with `14 00` and `14-00` as the reminder time.
Both create tasks with the correct reminder time.

**Acceptance Scenarios**:

1. **Given** a task creation flow, **When** the user enters `14 00`, **Then** the bot
   parses it as 14:00 and schedules the reminder accordingly.
2. **Given** a task creation flow, **When** the user enters `14-00`, **Then** the bot
   parses it as 14:00 and schedules the reminder accordingly.
3. **Given** a task creation flow, **When** the user enters `14:00`, **Then** existing
   behaviour is preserved unchanged.
4. **Given** an invalid time like `25 00`, **When** submitted, **Then** the bot shows a
   validation error in the user's selected language.

---

### User Story 4 — Delete All Completed Tasks (Priority: P2)

On the Completed tasks screen, a `🗑 Delete All` button appears. Tapping it shows a
confirmation prompt with the count of tasks to be deleted. Confirming removes all
completed tasks permanently; cancelling returns to the Completed screen unchanged.

**Why this priority**: Power users accumulate completed tasks over time. Bulk deletion
reduces clutter and demonstrates thoughtful UX design to reviewers.

**Independent Test**: Create several completed tasks, tap `🗑 Delete All`, confirm, and
verify the Completed list is empty. Repeat and cancel to verify tasks are preserved.

**Acceptance Scenarios**:

1. **Given** a user has N > 0 completed tasks, **When** they open the Completed screen,
   **Then** a `🗑 Delete All` button is visible.
2. **Given** the user taps `🗑 Delete All`, **Then** a confirmation message is shown
   (in the user's language) with the count N and two buttons: `✅ Yes, delete all` and
   `❌ Cancel`.
3. **Given** the confirmation is shown, **When** the user taps `✅ Yes, delete all`,
   **Then** all completed tasks for that user are deleted and the Completed screen is
   refreshed showing zero tasks.
4. **Given** the confirmation is shown, **When** the user taps `❌ Cancel`, **Then** no
   tasks are deleted and the Completed screen is shown unchanged.
5. **Given** a user has zero completed tasks, **When** they open the Completed screen,
   **Then** the `🗑 Delete All` button is absent.

---

### User Story 5 — Improved Timezone Button Labels (Priority: P2)

During timezone selection, each button displays the current local time and short city
codes alongside the timezone identifier, making it easier for users to recognise their
zone at a glance. City codes are always in English regardless of the user's language.

**Why this priority**: Users often don't know IANA timezone identifiers. Showing live
local time with familiar city abbreviations dramatically reduces selection errors.

**Independent Test**: Open timezone selection. Verify each button shows the correct
current time and expected city abbreviations (e.g., `10:00 MSK, SPB` for `Europe/Moscow`).

**Acceptance Scenarios**:

1. **Given** a user reaches the timezone selection step, **When** the buttons are shown,
   **Then** each button displays the current local time and short city codes for that zone.
2. **Given** the timezone `Europe/Moscow`, **When** rendered, **Then** the button label
   contains the current local time and the codes `MSK, SPB`.
3. **Given** the timezone `Europe/Kaliningrad`, **When** rendered, **Then** the button
   label contains the current local time and the code `KGD`.
4. **Given** the timezone `Asia/Yekaterinburg`, **When** rendered, **Then** the button
   label contains the current local time and the code `YEK`.
5. **Given** the timezone `Asia/Novosibirsk`, **When** rendered, **Then** the button
   label contains the current local time and the codes `NOV, OMS`.
6. **Given** the timezone `Asia/Vladivostok`, **When** rendered, **Then** the button
   label contains the current local time and the codes `VLA, KHA`.
7. **Given** a Russian-language user, **When** timezone buttons are shown, **Then** city
   codes remain in English (not translated).

---

### User Story 6 — Bilingual Javadoc (Priority: P3)

All public classes and public methods carry Javadoc comments written first in English,
then in Russian, separated by a blank line. Private members are exempt.

**Why this priority**: Improves the portfolio impression for Russian-speaking clients
reviewing source code. Zero functional risk.

**Independent Test**: Review a sample of public classes and methods. Each has a Javadoc
block with an English paragraph followed by a blank line and a Russian paragraph.

**Acceptance Scenarios**:

1. **Given** any public class or method, **When** its Javadoc is read, **Then** it
   contains an English description first, a blank line, then a Russian description.
2. **Given** a private method, **When** reviewed, **Then** bilingual Javadoc is not
   required (may be absent or English-only).

---

### User Story 7 — Bilingual README (Priority: P3)

The project README contains full content in both English and Russian: a navigation hint
at the top, a complete English section, a `---` divider, and a complete Russian section
covering the same topics.

**Why this priority**: The README is the first thing a prospective client or reviewer
sees. A bilingual README signals professionalism and accessibility.

**Independent Test**: Open the README. Verify the navigation hint, complete English
section, `---` divider, and complete Russian section each covering: project description,
SDD + Spec Kit + Claude Code methodology, command list, supported time formats, and
local setup instructions.

**Acceptance Scenarios**:

1. **Given** the README, **When** opened, **Then** the first line is:
   `🇷🇺 Описание на русском ниже / 🇬🇧 Russian description below`.
2. **Given** the English section, **When** read, **Then** it covers: project description
   and the SDD + Spec Kit + Claude Code methodology, full command list, all supported
   time-input formats, and local setup instructions.
3. **Given** the Russian section (after `---`), **When** read, **Then** it covers the
   same four topics as the English section.

---

### Edge Cases

- **Resolved**: A registered user who sends `/start` receives a "You are already registered"
  message in their stored language, followed by the main menu. Registration is NOT re-triggered.
- What happens if the `language` field is `null` for users migrated from before this
  feature (data migration gap)?
- What if the system clock is unavailable when building timezone button labels?
- **Resolved**: Rapid double-taps of `✅ Yes, delete all` are safe — the operation is
  naturally idempotent; the second call deletes 0 rows and the screen refreshes cleanly.
- What if `14-00` or `14 00` is entered while a previous failed format attempt is still
  in the conversation state?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST present a bilingual welcome message (English first, Russian
  second) to any user who has not yet selected a language.
- **FR-002**: The system MUST offer exactly two language-selection buttons: `🇬🇧 English`
  and `🇷🇺 Русский`.
- **FR-003**: The system MUST persist the selected language (`EN` or `RU`) as part of the
  user record immediately upon selection.
- **FR-004**: The system MUST serve all user-facing text — messages, button labels, error
  messages, confirmation prompts, and reminder notifications — in the user's stored
  language for every interaction after language selection.
- **FR-005**: The system MUST NOT contain hardcoded user-facing strings in handler or
  service classes; all such strings MUST be retrieved by enum key from `MessageService`,
  a Spring bean that holds all EN and RU translations as hardcoded constants. Spring
  MessageSource and external `.properties` files MUST NOT be used.
- **FR-006**: The system MUST accept time input in three equivalent formats: `HH:MM`,
  `HH MM`, and `HH-MM`, treating all three identically.
- **FR-007**: The system MUST display a `🗑 Delete All` button on the Completed tasks
  screen when the user has at least one completed task.
- **FR-008**: The system MUST show a confirmation prompt before bulk-deleting completed
  tasks, stating the exact count of tasks to be removed.
- **FR-009**: The system MUST delete all completed tasks for the requesting user upon
  confirmation, and MUST NOT affect tasks belonging to any other user.
- **FR-010**: Timezone selection buttons MUST display the current local time for each
  timezone alongside a fixed set of short city codes.
- **FR-011**: City codes on timezone buttons MUST always be displayed in English,
  regardless of the user's chosen language.
- **FR-012**: Deleting a user record MUST automatically delete all associated tasks and
  user-state records via database-level cascade rules.
- **FR-013**: All schema changes (language column, cascade constraints) MUST be delivered
  via versioned migration files that include rollback blocks.
- **FR-014**: All public classes and public methods MUST carry bilingual Javadoc (English
  paragraph, blank line, Russian paragraph).
- **FR-015**: The README MUST contain complete project documentation in both English and
  Russian, structured as described in User Story 7.
- **FR-016**: Users migrated from before this feature MUST receive a default language of
  `EN` to prevent null-language scenarios. Language is immutable after registration;
  no change mechanism exists in this feature.

### Key Entities

- **User**: Registered Telegram user. Gains a `language` attribute (`EN` or `RU`) set
  once during registration, governing all subsequent text output.
- **Task**: Existing entity. Gains implicit cascade dependency — deleted automatically
  when the owning User is deleted.
- **UserState**: Existing entity. Gains a new leading registration state `AWAITING_LANGUAGE`.
  Full registration sequence: `AWAITING_LANGUAGE` → `AWAITING_TIMEZONE` → registered.
  Same cascade dependency as Task.
- **Message Key**: A typed constant (Java enum value) that carries both its EN and RU
  string variants. Not persisted — resolved at runtime by `MessageService` using the
  user's stored language. Spring MessageSource and external properties files are
  explicitly NOT used.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who selects Russian receives every subsequent bot message, button
  label, and notification entirely in Russian — zero English strings visible after the
  welcome screen.
- **SC-002**: A user who selects English receives every subsequent bot message, button
  label, and notification entirely in English — zero Russian strings visible after the
  welcome screen.
- **SC-003**: Time inputs using space or hyphen separators (`14 00`, `14-00`) are accepted
  and produce identical reminder times to equivalent colon-separated input in 100% of
  valid cases.
- **SC-004**: Confirming `🗑 Delete All` removes all completed tasks for that user; the
  Completed screen shows zero completed tasks immediately after.
- **SC-005**: Cancelling the `Delete All` confirmation leaves all tasks intact and returns
  the user to the unchanged Completed screen.
- **SC-006**: Timezone buttons show the correct current local time for each zone at the
  moment the selection screen is rendered.
- **SC-007**: Deleting a user in the database removes all their tasks and user_states with
  no orphaned rows remaining.
- **SC-008**: A codebase audit finds zero hardcoded user-visible strings in handler or
  service classes.
- **SC-009**: Every public class and public method has a Javadoc block containing both
  an English and a Russian description.
- **SC-010**: The README renders correctly in both languages and covers all four required
  topics: project description + methodology, commands, time formats, and setup.

## Clarifications

### Session 2026-03-20

- Q: Where and how are EN/RU string pairs physically stored and resolved at runtime? → A: A `MessageService` Spring bean with translations hardcoded as constants — either a `Map<String, Map<Language, String>>` or a Java enum where each key carries its EN and RU string. No Spring MessageSource, no external properties files.
- Q: How does language selection integrate with the existing `UserState` registration flow? → A: A new `AWAITING_LANGUAGE` state is added as the first step in the `UserState` registration sequence. `/start` creates a `UserState` row in `AWAITING_LANGUAGE`; the language callback sets the language and transitions to `AWAITING_TIMEZONE`.
- Q: What should the bot do when a fully-registered user sends `/start`? → A: Show a "You are already registered" message in the user's stored language, then immediately show the main menu. Registration flow is NOT re-triggered.
- Q: Can a user change their language after initial registration? → A: No. Language is set once during registration and is immutable in this feature. Migrated users receive EN permanently until a future feature adds language switching.
- Q: Is any special protection needed against rapid double-taps of `✅ Yes, delete all`? → A: No. The bulk delete is naturally idempotent — a second call deletes 0 rows and the screen refreshes cleanly. No UI-level debounce or lock is required.

## Assumptions

- Users registered before this feature (no `language` column) are assigned `EN` as the
  default language via the database migration to avoid null-language scenarios.
- The set of supported timezones does not change; only the button label format changes.
- City-code mappings for timezone buttons are fixed constants (not user-configurable).
- "Delete All" applies exclusively to tasks in `COMPLETED` status; active or pending
  tasks are unaffected.
- Bilingual Javadoc is required for all new and modified public classes and methods;
  coverage of pre-existing, unmodified code is best-effort within this feature's scope.
- The README documents the project state as it exists after this feature ships.
