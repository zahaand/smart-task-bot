# Feature Specification: Smart Task Bot MVP

**Feature Branch**: `001-task-bot-mvp`
**Created**: 2026-03-18
**Status**: Draft

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Create a Task (Priority: P1)

A user sends a command to the bot with the text of a new task.
The bot saves it and confirms the creation with a unique identifier, so the user
has a record of the task and can reference it later.

**Why this priority**: Creating a task is the foundational action — without it
no other feature has any value. This story alone constitutes a usable MVP.

**Independent Test**: Can be fully tested by sending the create-task command and
verifying a confirmation message with an assigned task ID is returned, and that
the task appears in a subsequent task-list query.

**Acceptance Scenarios**:

1. **Given** a new user, **When** they send `/start`,
   **Then** the bot greets them and presents a predefined list of timezones as inline keyboard buttons.
2. **Given** a new user presented with the timezone inline keyboard, **When** they tap a timezone button,
   **Then** the bot saves that timezone to their profile and confirms registration is complete.
3. **Given** a registered user, **When** they send `/newtask Buy groceries`,
   **Then** the bot replies with a confirmation containing the task text and a
   unique numeric ID, and the task is saved with status ACTIVE.
4. **Given** a registered user, **When** they send `/newtask` with no text,
   **Then** the bot replies with a clear usage hint and does NOT create a task.
5. **Given** two different users each creating a task, **When** either user views
   their list, **Then** they see only their own tasks.

---

### User Story 2 — View Task List (Priority: P2)

A user sends a command to retrieve all their currently active tasks.
The bot returns a numbered list so the user can see what remains to be done
and identify tasks by their IDs for follow-up actions.

**Why this priority**: A task list is the core consumption feature — it turns
isolated task creation into a usable personal workflow.

**Independent Test**: Can be fully tested by creating several tasks and sending
the list command, verifying only ACTIVE tasks for that user are returned.

**Acceptance Scenarios**:

1. **Given** a user with active tasks, **When** they send `/tasks`,
   **Then** the bot returns a numbered list showing each task's ID, text,
   and reminder time (if set).
2. **Given** a user with no tasks, **When** they send `/tasks`,
   **Then** the bot responds with a friendly "no active tasks" message.
3. **Given** a user who has completed some tasks, **When** they send `/tasks`,
   **Then** only ACTIVE tasks appear — completed tasks are excluded.

---

### User Story 3 — Set a Reminder (Priority: P3)

A user assigns a date and time to an existing task.
At that moment the bot proactively sends a notification message, so the user
is reminded to act without having to check the bot manually.

**Why this priority**: Reminders add proactive value on top of the passive list;
they are the differentiating feature of a task *bot* vs. a simple to-do list.

**Independent Test**: Can be fully tested by setting a near-future reminder,
waiting for the scheduled time, and confirming the bot sends a notification
message to the user's chat.

**Acceptance Scenarios**:

1. **Given** an ACTIVE task with ID 7, **When** the user sends `/remind 7 25.03.2026 09:00`,
   **Then** the bot confirms the reminder is set and the scheduled time is stored on the task.
2. **Given** a reminder set for a specific time, **When** that time arrives,
   **Then** the bot sends a notification to the user's chat mentioning the task text.
3. **Given** a user sends `/remind` with a non-existent task ID,
   **Then** the bot responds with an error message and makes no changes.
4a. **Given** a user sends `/remind` for a task with status COMPLETED,
   **Then** the bot responds with an error "Cannot set a reminder on a completed task"
   and makes no changes.
4. **Given** a user sends `/remind` with an invalid date format,
   **Then** the bot explains the expected format (DD.MM.YYYY HH:mm) and makes no changes.

---

### User Story 4 — Complete a Task (Priority: P4)

A user marks one of their tasks as done.
The task is removed from the active list, keeping the list clean and relevant.

**Why this priority**: Without completion, the task list grows indefinitely.
This story closes the full task lifecycle started in User Story 1.

**Independent Test**: Can be fully tested by creating a task, marking it done,
and verifying it no longer appears in the `/tasks` response.

**Acceptance Scenarios**:

1. **Given** an ACTIVE task with ID 7, **When** the user sends `/done 7`,
   **Then** the bot confirms completion and the task status changes to COMPLETED.
2. **Given** a task that belongs to another user, **When** the user sends `/done` with
   that task's ID, **Then** the bot responds as if the task does not exist and
   makes no changes.
3. **Given** a user sends `/done` with no ID or a non-numeric ID,
   **Then** the bot replies with a usage hint and makes no changes.

---

### Edge Cases

- What happens when a user sends an unrecognised command? The bot responds with a
  hint to use `/help`.
- What happens when a user sends `/start` more than once? The bot greets them again
  but does NOT create a duplicate user record.
- What happens when an unregistered user (timezone not yet selected) sends any command
  other than `/start`? The bot ignores the command, responds with a prompt to select
  a timezone, and re-displays the inline keyboard. The command is NOT executed.
- What happens if a reminder fires for a task that has since been marked COMPLETED?
  The notification is skipped silently.
- What happens when task text exceeds 500 characters? The bot rejects the input with
  an explanatory message and does NOT create a task.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST register a user when they complete the `/start`
  registration flow by selecting a timezone. The user record is created at that
  moment, storing their Telegram identity and selected timezone. The system MUST NOT
  create duplicate records if the user sends `/start` or taps the timezone button
  again after already being registered.
- **FR-002**: The system MUST create a task linked exclusively to the requesting user,
  assign it a unique numeric ID, and set its initial status to ACTIVE.
- **FR-003**: The system MUST reject task creation when no task text is provided,
  returning a usage hint.
- **FR-004**: The system MUST reject task text exceeding 500 characters, returning
  an explanatory message.
- **FR-005**: The system MUST return only the requesting user's ACTIVE tasks when the
  list command is issued.
- **FR-006**: The system MUST display each task's ID, text, and reminder time (if set)
  in the task list.
- **FR-007**: The system MUST return a clear "no active tasks" message when the user
  has no active tasks.
- **FR-008**: The system MUST store a reminder date-time on a task when the user
  provides a valid owned task ID, the task status is ACTIVE, and a datetime in
  DD.MM.YYYY HH:mm format is supplied. The system MUST reject the command if the
  referenced task has status COMPLETED.
- **FR-009**: The system MUST send a reminder notification to the user's chat at the
  scheduled time (within 60 seconds), unless the task has already been completed.
  If delivery fails, the system MUST retry once after 60 seconds. If the retry also
  fails, the system MUST log the failure and discard the notification.
- **FR-010**: The system MUST reject reminder commands referencing task IDs that do
  not belong to the requesting user.
- **FR-011**: The system MUST reject reminder commands with an invalid or unparseable
  datetime and explain the required format to the user.
- **FR-012**: The system MUST change a task's status to COMPLETED when the user issues
  the complete command with a valid owned task ID.
- **FR-013**: The system MUST reject complete commands referencing task IDs that do
  not belong to the requesting user.
- **FR-014**: The system MUST respond to `/help` with a list of all available commands
  and their usage syntax.
- **FR-015**: The system MUST respond to unrecognised input with a hint to use `/help`.
- **FR-016**: The system MUST ask the user to select a timezone during `/start` registration
  before any other interaction is possible.
- **FR-017**: The system MUST store the user's selected timezone and apply it to all
  reminder time input parsing and notification delivery.
- **FR-018**: The system MUST provide a predefined list of timezone options as Telegram
  inline keyboard buttons, including at minimum: Europe/Moscow, Europe/Kaliningrad,
  Asia/Yekaterinburg, Asia/Novosibirsk, Asia/Vladivostok. The user selects by tapping
  a button; no free-text timezone input is accepted.

### Key Entities

- **User**: Represents a registered Telegram user. Identified by their Telegram
  identity. A user record is created upon completion of the `/start` registration
  flow — specifically when the user selects their timezone via the inline keyboard.
  Before timezone selection is complete, no user record exists. "Registered" always
  means "timezone is set"; there is no partial registration state. Each user's data
  is fully isolated from all other users.
- **Task**: A single to-do item owned by exactly one user. Has a text description,
  a lifecycle status (ACTIVE or COMPLETED), and an optional scheduled reminder time.
  Identified by a globally unique numeric ID (auto-incremented across all users).

## Clarifications

### Session 2026-03-18

- Q: How should timezone selection be presented to the user during `/start`? → A: Telegram inline keyboard buttons (Option A)
- Q: What is the scope of Task ID uniqueness? → A: Global auto-increment across all users (Option A)
- Q: What should the bot do when an unregistered user sends a command before completing timezone selection? → A: Block the command, respond with timezone prompt, re-display inline keyboard (Option A)
- Q: Should `/remind` be allowed on a COMPLETED task? → A: No — reject with explicit error message (Option A)
- Q: How should the system handle Telegram API failure during reminder delivery? → A: One retry after 60 seconds, then log and discard (Option A)

## Assumptions

- The datetime input format is fixed at `DD.MM.YYYY HH:mm`. Each user has an individual
  timezone set during `/start` registration. All reminder times are entered and displayed
  in the user's local timezone. The server stores all times in UTC internally.
- Reminder delivery is best-effort. The system checks for due reminders at least
  once per minute; sub-minute precision is not guaranteed.
- There is no pagination for the task list in MVP. All ACTIVE tasks are returned in
  a single message.
- A user cannot edit or delete tasks in MVP — only create and complete.

## Out of Scope (MVP)

- Task editing or deletion
- Recurring reminders
- Task categories, tags, or priority levels
- Pagination of task lists

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can complete the full task lifecycle (create → list → remind → complete)
  within 2 minutes of first use, with no guidance beyond `/help`.
- **SC-002**: A user never sees tasks belonging to another user under any interaction
  pattern — data isolation is absolute and verifiable.
- **SC-003**: Reminder notifications are delivered within 60 seconds of the scheduled
  time in at least 95% of cases under normal operating conditions.
- **SC-004**: The bot responds to every valid command within 3 seconds under normal load.
- **SC-005**: All four core commands (`/newtask`, `/tasks`, `/remind`, `/done`) are
  independently verifiable and functional without requiring the others to be present.
