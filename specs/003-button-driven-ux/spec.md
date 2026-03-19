# Feature Specification: Button-Driven UX Improvements

**Feature Branch**: `003-button-driven-ux`
**Created**: 2026-03-19
**Status**: Draft

## Overview

Improve the Telegram bot's user experience so that registered users can complete all core interactions (create tasks, view tasks, set reminders, mark done, delete) entirely through buttons — without typing any commands. A persistent keyboard and inline action buttons replace the command-line interface as the primary interaction model. All existing commands remain functional for backwards compatibility.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Persistent Menu After Registration (Priority: P1)

After a user completes timezone registration, a persistent reply keyboard appears at the bottom of the chat and remains visible in all subsequent sessions. The keyboard contains three buttons: "New Task", "My Tasks", and "Reminder". The user never has to remember or type a command to access core bot features.

**Why this priority**: This is the foundation of the button-driven UX. Without the persistent menu, all other button interactions require knowing commands. It must land first as it is the entry point for every other story.

**Independent Test**: A newly registered user can start a fresh chat, see the keyboard, and tap each of the three buttons — confirming the bot responds meaningfully to each — without typing a single command.

**Acceptance Scenarios**:

1. **Given** a user has just selected their timezone, **When** registration completes, **Then** the bot sends a welcome message and a persistent reply keyboard with buttons "📝 Новая задача", "📋 Мои задачи", "⏰ Напоминание" appears.
2. **Given** a registered user reopens the bot in a new session, **When** they view the chat, **Then** the persistent keyboard is still visible at the bottom of the screen.
3. **Given** a registered user types `/newtask`, `/tasks`, `/remind`, `/done`, or `/help`, **When** the bot receives the command, **Then** it responds as it did before this feature (full backward compatibility).

---

### User Story 2 — Create Task via Button (Priority: P1)

A registered user taps "New Task" on the persistent keyboard. The bot replies asking for the task text. The user types the task text as a free-text message. The bot creates the task and confirms creation with the task's number and text.

**Why this priority**: Task creation is the most-used action. Removing the need to type `/newtask` is the highest-impact UX change.

**Independent Test**: Open the bot, tap "📝 Новая задача", type any text, and verify a task is created and confirmed — all without typing a command.

**Acceptance Scenarios**:

1. **Given** a registered user in IDLE state, **When** they tap "📝 Новая задача", **Then** the bot replies "Введи текст задачи:" and the user's conversation state changes to CREATING_TASK.
2. **Given** a user in CREATING_TASK state, **When** they send any non-empty text message, **Then** the bot creates the task, replies with confirmation (e.g., "✅ Задача #N создана: [text]"), and returns to IDLE state.
3. **Given** a user in CREATING_TASK state, **When** they send an empty message or only whitespace, **Then** the bot replies with an error prompt and remains in CREATING_TASK state.
4. **Given** a user in CREATING_TASK state, **When** they send a command (e.g., `/cancel`), **Then** the bot cancels task creation, returns to IDLE, and informs the user.

---

### User Story 3 — Task List with Inline Action Buttons (Priority: P1)

A registered user taps "My Tasks" on the persistent keyboard. The bot shows a list of tasks with an active/completed toggle and inline action buttons on each task. Active tasks have "Remind", "Done", and "Delete" buttons. Completed tasks have only a "Delete" button. The user can switch between views by tapping the toggle.

**Why this priority**: The task list is the central dashboard. Inline buttons on tasks replace all task-manipulation commands and make the bot fully operable without text input.

**Independent Test**: Tap "📋 Мои задачи", verify active tasks appear with three action buttons each, tap "Выполненные" to switch tabs and verify completed tasks appear with one button each.

**Acceptance Scenarios**:

1. **Given** a registered user with at least one active task, **When** they tap "📋 Мои задачи", **Then** the bot shows active tasks by default, each with buttons "⏰ Напомнить", "✅ Выполнить", "🗑 Удалить".
2. **Given** the task list is showing active tasks, **When** the user taps "Выполненные", **Then** the list updates to show completed tasks, each with only "🗑 Удалить".
3. **Given** the task list is showing completed tasks, **When** the user taps "Активные ✓", **Then** the list switches back to active tasks.
4. **Given** a registered user with no tasks, **When** they tap "📋 Мои задачи", **Then** the bot shows a friendly empty-state message (e.g., "У тебя пока нет задач. Создай первую!").
5. **Given** a task list is displayed, **When** the user taps "✅ Выполнить" on a task, **Then** the task is marked as done and the list refreshes.

---

### User Story 4 — Set Reminder via Inline Calendar (Priority: P2)

A registered user taps "⏰ Напомнить" on an active task. An inline calendar appears showing the current month. Past dates are disabled. The user navigates months using forward/back arrows and selects a future date. The bot then asks the user to type the reminder time as free text. The bot parses the time, creates the reminder, and confirms.

**Why this priority**: Reminder setting is a key bot function. Replacing the text-based `/remind` flow with a calendar picker prevents date-format errors and improves the experience significantly. It builds on P1 (task list) so it lands second.

**Independent Test**: Tap "⏰ Напомнить" on any active task, navigate the calendar to a future date, tap it, type "14:30", and confirm that a reminder is created for that date and time.

**Acceptance Scenarios**:

1. **Given** a user taps "⏰ Напомнить" on an active task, **When** the callback is processed, **Then** an inline calendar for the current month is displayed; all dates before today are visually disabled and non-tappable.
2. **Given** the calendar is showing the current month, **When** the user taps "→" (next month), **Then** the calendar updates to show the next month.
3. **Given** the calendar is showing a month after the current, **When** the user taps "←" (previous month), **Then** the calendar moves back, but never before the current month.
4. **Given** the calendar is displayed, **When** the user taps a valid future date, **Then** the bot stores the date in the conversation context, updates user state to ENTERING_REMINDER_TIME, and asks "Введи время (например: 14:30, 9 утра, 21:00)".
5. **Given** a user in ENTERING_REMINDER_TIME state, **When** they send a valid time string (HH:mm, H:mm, "N утра", "N вечера"), **Then** the reminder is created, the bot confirms "⏰ Напоминание установлено на [date] [time]", and state returns to IDLE.
6. **Given** a user in ENTERING_REMINDER_TIME state, **When** they send an unrecognised time format, **Then** the bot replies with a format hint and remains in ENTERING_REMINDER_TIME.

---

### User Story 5 — Delete Task with Confirmation (Priority: P2)

A user taps "🗑 Удалить" on a task. The bot shows a confirmation message with the task's number and text, and two inline buttons: "Yes, delete" and "Cancel". Tapping "Yes, delete" permanently removes the task. Tapping "Cancel" dismisses the confirmation and returns to IDLE.

**Why this priority**: Deletion is irreversible, so a confirmation step is essential for safety. It builds on the task list (P1) and is grouped with P2 features.

**Independent Test**: Tap "🗑 Удалить" on any task, verify the confirmation message appears with both buttons, tap "❌ Отмена" and confirm the task is unchanged; repeat and tap "✅ Да, удалить" and confirm the task is removed.

**Acceptance Scenarios**:

1. **Given** a user taps "🗑 Удалить" on a task, **When** the callback is processed, **Then** the bot sends: "Удалить задачу '#N: [text]'? Это действие нельзя отменить." with inline buttons "✅ Да, удалить" and "❌ Отмена", and state changes to CONFIRMING_DELETE with taskId in context.
2. **Given** a user in CONFIRMING_DELETE state, **When** they tap "✅ Да, удалить", **Then** the task is permanently deleted from the system, the bot confirms deletion, and state returns to IDLE.
3. **Given** a user in CONFIRMING_DELETE state, **When** they tap "❌ Отмена", **Then** no data is changed, the bot sends a cancellation message, and state returns to IDLE.
4. **Given** a user in CONFIRMING_DELETE state, **When** the task no longer exists (e.g., deleted concurrently), **Then** the bot handles gracefully, informs the user, and returns to IDLE.

---

### User Story 6 — Timezone Selection Shows Current Local Time (Priority: P3)

During the registration flow, each timezone option button shows the current time in that timezone alongside the city name (e.g., "🕐 Москва, СПб (сейчас 18:45)"). This helps users quickly verify which timezone is correct for them without external lookup.

**Why this priority**: A usability enhancement to an existing flow. Lower risk, lower frequency (registration happens once), but improves first-impression quality for demos.

**Independent Test**: Start a new registration flow, observe the timezone selection buttons, and verify that each button displays the correct current local time for its timezone.

**Acceptance Scenarios**:

1. **Given** a new user starts the bot and the registration flow begins, **When** the timezone keyboard is presented, **Then** each button shows the timezone label and the current time in that zone, formatted as "HH:mm".
2. **Given** the timezone keyboard is displayed, **When** the user selects a timezone, **Then** registration continues as before — timezone is saved, persistent menu appears, welcome message is sent.
3. **Given** 30+ minutes pass during registration without selection, **When** the user taps a timezone button, **Then** the displayed time on the button may be stale, but the timezone is still saved correctly (time on buttons is display-only at render time).

---

### Edge Cases

- What happens when a user sends a free-text message while in IDLE state (no active dialog)? The bot should reply with a hint to use the menu buttons.
- What happens when a user taps an inline button on an old task-list message after the list has been updated? The bot should handle stale callback gracefully (re-fetch current state, not crash).
- What happens when a user's task is deleted by a concurrent action while the delete confirmation is pending? The bot must not throw an error — it should confirm the task is gone and return to IDLE.
- What happens when the calendar navigation reaches the current month and the user tries to go back further? The "←" button must be disabled or produce no action.
- What happens when a user in CREATING_TASK or ENTERING_REMINDER_TIME state taps a button from the persistent keyboard? The bot MUST cancel the current flow, notify the user (e.g., "Создание задачи отменено"), reset state to IDLE, and immediately start the newly requested flow.
- What if a user has more than ~20 tasks? The task list response must not exceed Telegram message size limits — pagination or truncation with a note should be applied.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST display a persistent reply keyboard with three buttons ("📝 Новая задача", "📋 Мои задачи", "⏰ Напоминание") to every registered user after successful registration.
- **FR-002**: The persistent keyboard MUST persist across sessions — it must be visible whenever the user opens the bot chat.
- **FR-003**: The system MUST maintain a per-user conversation state (IDLE, CREATING_TASK, SELECTING_REMINDER_DATE, ENTERING_REMINDER_TIME, CONFIRMING_DELETE) and route incoming messages accordingly.
- **FR-004**: The system MUST store conversation context (e.g., taskId, selected date) associated with the current user state so that multi-step flows can be completed across messages.
- **FR-005**: The system MUST transition user state back to IDLE after every completed or cancelled multi-step flow.
- **FR-005a**: When a persistent keyboard button is tapped while the user is in any non-IDLE state, the system MUST cancel the active flow, send a cancellation notification (e.g., "Создание задачи отменено"), reset state to IDLE, and then immediately begin the newly requested flow.
- **FR-006**: Tapping "📝 Новая задача" MUST prompt the user for task text and create the task upon receiving it.
- **FR-007**: Tapping "📋 Мои задачи" MUST display the user's task list with a tab toggle between active and completed tasks. Switching tabs MUST edit the existing message in-place (update text and inline buttons) — no new message is sent.
- **FR-008**: Each active task in the list MUST show inline action buttons: "⏰ Напомнить", "✅ Выполнить", "🗑 Удалить".
- **FR-009**: Each completed task in the list MUST show an inline action button: "🗑 Удалить".
- **FR-010**: Tapping "⏰ Напомнить" on an active task MUST open an inline calendar showing only the current and future months; past dates MUST be visually disabled and non-interactive.
- **FR-010a**: All inline callback data MUST use a prefix-based namespacing scheme to allow unambiguous routing: `CAL_DATE:<YYYY-MM-DD>` for date selection, `CAL_NAV:<±N>` for month navigation, `TASK_REMIND:<taskId>`, `TASK_DONE:<taskId>`, `TASK_DELETE:<taskId>` for task actions, `CONFIRM_DELETE:<taskId>` and `CONFIRM_CANCEL` for the deletion confirmation dialog, `TASKS_TAB:<ACTIVE|COMPLETED>` for switching between the active and completed task list tabs, and `NO_OP` as a no-action placeholder for non-interactive calendar cells (past dates, weekday headers, out-of-month padding).
- **FR-011**: The inline calendar MUST support month-by-month navigation (forward and backward), with backward navigation blocked at the current month.
- **FR-012**: After the user selects a date on the calendar, the system MUST prompt for a time as free text and support the following input formats: HH:mm, H:mm, "N утра" (1–11, maps to 01:00–11:00), "N вечера" (1–11, maps to 13:00–23:00). Input of "12 утра" or "12 вечера" MUST be rejected with the prompt: "Для 12 часов используй формат HH:mm (00:00 или 12:00)" — the state remains ENTERING_REMINDER_TIME.
- **FR-013**: Upon receiving a valid time, the system MUST create the reminder for the selected date and time in the user's stored timezone and confirm to the user.
- **FR-014**: Tapping "🗑 Удалить" on any task MUST display a confirmation message naming the task and offer "✅ Да, удалить" and "❌ Отмена" inline buttons before any deletion occurs.
- **FR-015**: Confirming deletion MUST permanently remove the task from the system (physical delete, no soft-delete).
- **FR-016**: The system MUST store user state changes with a timestamp. On every incoming message or callback, the system MUST check whether the current state is older than 24 hours; if so, it MUST reset state to IDLE before routing the update (lazy cleanup — no dedicated scheduled job required).
- **FR-017**: All existing text commands (/newtask, /tasks, /remind, /done, /help) MUST continue to function exactly as before.
- **FR-018**: During the timezone registration flow, each timezone option MUST display the current local time in that timezone at the moment the keyboard is rendered.
- **FR-019**: Unrecognised free-text messages received while the user is in IDLE state MUST trigger a helpful response directing the user to use the menu buttons.

### Key Entities

- **UserState**: Tracks the current step of a multi-step dialog for each user. Belongs to one user. Has a state identifier, optional structured context payload (e.g., taskId, date, messageId for in-place edits), and a last-updated timestamp.
- **Task**: An existing entity. Updated by this feature to support inline "Done" and "Delete" actions triggered by button taps rather than commands only.
- **Reminder**: An existing entity. Updated by this feature to be creatable via the calendar+time flow in addition to the existing command flow.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user who has never used the bot can create a task, view the task list, and set a reminder without typing a single command — completing the full journey in under 2 minutes.
- **SC-002**: 100% of existing text commands (/newtask, /tasks, /remind, /done, /help) produce the same responses as before this feature is shipped (zero regression).
- **SC-003**: All multi-step flows (task creation, reminder setting, deletion confirmation) complete successfully when the user follows the happy path, with no intermediate errors or orphaned states.
- **SC-004**: Invalid time inputs during the reminder flow are rejected with a clear format hint, and the user can retry without restarting the flow.
- **SC-005**: The bot responds to every button tap or state-driven message within the same response latency as before this feature (no perceptible slowdown).
- **SC-006**: After a completed or cancelled flow, the user's state is always returned to IDLE — no user is permanently stuck in a non-IDLE state.
- **SC-007**: The timezone selection screen correctly shows the current local time for each timezone option at the moment the screen is rendered, with no more than 1-minute drift.

---

## Clarifications

### Session 2026-03-19

- Q: When a user in CREATING_TASK or ENTERING_REMINDER_TIME taps a persistent keyboard button, what should happen? → A: Cancel current flow with notification ("Создание задачи отменено"), reset to IDLE, immediately start the new flow.
- Q: When the task list tab is toggled (Активные ↔ Выполненные), should the bot edit the existing message or send a new one? → A: Edit the existing message in-place — update text and inline buttons without sending a new message.
- Q: How should inline callback data be structured to avoid routing collisions between calendar, task actions, and confirmation dialogs? → A: Prefix-based namespacing — e.g., `CAL_DATE:<date>`, `CAL_NAV:<±N>`, `TASK_REMIND:<id>`, `TASK_DONE:<id>`, `TASK_DELETE:<id>`, `CONFIRM_DELETE:<id>`, `CONFIRM_CANCEL`.
- Q: How should "12 утра" / "12 вечера" be handled in free-text time parsing? → A: Reject with a clarification prompt — "Для 12 часов используй формат HH:mm (00:00 или 12:00)"; state stays ENTERING_REMINDER_TIME.
- Q: How should stale conversation states (>24h) be cleaned up? → A: Lazy reset — on every incoming message/callback, check `updated_at`; if >24h old, reset to IDLE before routing. No dedicated scheduled job needed.

---

## Assumptions

- The number of supported timezone options in the registration keyboard remains the same as in the current implementation; no new timezones are added in this feature.
- Task lists with more than ~20 items will be handled by truncation with a note ("Показаны первые 20 задач…") as a reasonable default; full pagination is out of scope for this feature.
- "Физическое удаление" means a hard DELETE in the database — there is no trash/restore capability in this feature.
- The `/remind` legacy command continues to use the existing text-based date/time input, not the new calendar UI.
- The "⏰ Напоминание" button on the persistent keyboard opens the task list filtered to active tasks, allowing the user to then tap "⏰ Напомнить" on a specific task — it does not start a reminder flow without a task selected.
- Conversation state entries older than 24 hours are considered stale and reset to IDLE lazily — on the next incoming message from that user, before routing. No background cleanup job is required.