---

description: "Task list for Smart Task Bot MVP implementation"
---

# Tasks: Smart Task Bot MVP

**Input**: Design documents from `specs/001-task-bot-mvp/`
**Prerequisites**: plan.md ✅ | spec.md ✅ | data-model.md ✅ | contracts/bot-commands.md ✅

**Tests**: Not requested — no test tasks generated.

**Organization**: Tasks grouped by user story. Each story is independently implementable
and testable after Foundational phase completes.

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no intra-phase dependency)
- **[USx]**: Which user story this task belongs to
- File paths are absolute from project root

---

## Phase 1: Setup

**Purpose**: Establish project skeleton and shared configuration files.

- [x] T001 Create all package directories: `handler/command/`, `handler/callback/`, `service/`, `repository/`, `model/`, `dto/`, `config/` under `src/main/java/ru/zahaand/smarttaskbot/`
- [x] T002 Configure `src/main/resources/application.yaml` with datasource env vars (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`), `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.properties.hibernate.timezone.default_storage: NORMALIZE_UTC`, Liquibase enabled, and bot token/username placeholders
- [x] T003 [P] Create `.env.example` in project root with all required variable names: `BOT_TOKEN`, `BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, entities, repositories, shared services, and bot entry-point
infrastructure. No user story can be implemented or manually tested until this phase is complete.

**⚠️ CRITICAL**: All subsequent phases depend on this phase being complete.

### Database Migrations

- [x] T004 Create Liquibase changeset `001-create-users-table.xml` in `src/main/resources/db/changelog/` — define `users` table: `telegram_user_id BIGINT PK`, `username VARCHAR(255) NULL`, `timezone VARCHAR(50) NOT NULL`, `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
- [x] T005 Create Liquibase changeset `002-create-tasks-table.xml` in `src/main/resources/db/changelog/` — define `tasks` table: `id BIGINT GENERATED ALWAYS AS IDENTITY PK`, `telegram_user_id BIGINT NOT NULL FK→users`, `text VARCHAR(500) NOT NULL`, `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED'))`, `reminder_time TIMESTAMP NULL`, `reminder_processed BOOLEAN NOT NULL DEFAULT false`, `reminder_retry_at TIMESTAMP NULL`, `created_at TIMESTAMP NOT NULL DEFAULT NOW()`
- [x] T006 [P] Create Liquibase changeset `003-create-tasks-indexes.xml` in `src/main/resources/db/changelog/` — add three indexes: `(telegram_user_id, status)`, `(reminder_processed, reminder_time)`, `(reminder_processed, reminder_retry_at)` on `tasks`
- [x] T007 Register changesets `001`, `002`, `003` in `src/main/resources/db/changelog/db.changelog-master.xml` in execution order

### Model Layer

- [x] T008 [P] Create `TaskStatus` enum (`ACTIVE`, `COMPLETED`) in `src/main/java/ru/zahaand/smarttaskbot/model/TaskStatus.java`
- [x] T009 [P] Create `User` JPA entity in `src/main/java/ru/zahaand/smarttaskbot/model/User.java` — `@Entity @Table(name="users")`, `@Id Long telegramUserId`, `String username`, `String timezone`, `LocalDateTime createdAt`, Lombok `@Getter @Setter @NoArgsConstructor`, `@PrePersist` sets `createdAt = LocalDateTime.now()`
- [x] T010 Create `Task` JPA entity in `src/main/java/ru/zahaand/smarttaskbot/model/Task.java` — `@Entity @Table(name="tasks")`, `@Id @GeneratedValue(IDENTITY) Long id`, `@ManyToOne(fetch=LAZY) User user`, `String text`, `@Enumerated(STRING) TaskStatus status`, `Instant reminderTime`, `boolean reminderProcessed`, `Instant reminderRetryAt`, `LocalDateTime createdAt`, Lombok annotations, `@PrePersist` sets `status=ACTIVE`, `reminderProcessed=false`, `createdAt=now`

### Repository Layer

- [x] T011 [P] Create `UserRepository` extending `JpaRepository<User, Long>` in `src/main/java/ru/zahaand/smarttaskbot/repository/UserRepository.java` — no custom queries needed beyond `existsById` and `findById` (inherited)
- [x] T012 Create `TaskRepository` extending `JpaRepository<Task, Long>` in `src/main/java/ru/zahaand/smarttaskbot/repository/TaskRepository.java` — add custom queries: `findAllByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status)`, `findByIdAndUserTelegramUserId(Long id, Long telegramUserId)`, `findAllByReminderProcessedFalseAndReminderTimeIsNotNullAndReminderTimeBeforeAndStatusAndReminderRetryAtIsNull(Instant now, TaskStatus status)`, `findAllByReminderProcessedFalseAndReminderRetryAtBeforeAndStatus(Instant now, TaskStatus status)`

### Config Layer

- [x] T013 [P] Create `BotConfig` in `src/main/java/ru/zahaand/smarttaskbot/config/BotConfig.java` — `@Configuration` class holding `@Value("${telegram.bot.token}") String botToken` and `@Value("${telegram.bot.username}") String botUsername`
- [x] T014 [P] Create `SchedulingConfig` in `src/main/java/ru/zahaand/smarttaskbot/config/SchedulingConfig.java` — `@Configuration @EnableScheduling` class (empty body; activates Spring scheduler)

### Service Layer — Core

- [x] T015 Create `NotificationService` in `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java` — inject `@Lazy AbsSender sender` (breaks circular dependency with `SmartTaskBot`); implement `sendMessage(Long chatId, String text)` wrapping `sender.execute(new SendMessage(...))` and `sendTimezoneKeyboard(Long chatId)` building `InlineKeyboardMarkup` with buttons for the 5 required timezones using callback data prefix `tz:` (e.g. `tz:Europe/Moscow`)
- [x] T016 Create `UserService` in `src/main/java/ru/zahaand/smarttaskbot/service/UserService.java` — inject `UserRepository`; implement `register(Long telegramUserId, String username, String timezone)` (creates and saves `User`, no-op if already exists), `isRegistered(Long telegramUserId)` returning `boolean`, `getTimezone(Long telegramUserId)` returning `String`

### Handler Layer — Bot Infrastructure

- [x] T017 Create `UpdateDispatcher` in `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java` — inject `RegistrationGuard`, `TimezoneCallbackHandler`, `StartCommandHandler`, `HelpCommandHandler`, `UnknownInputHandler`; implement `dispatch(Update update)`: if `update.hasCallbackQuery()` → route to `TimezoneCallbackHandler`; else if `update.hasMessage()` → extract command text, route `/start` and `/help` directly (no registration guard), all other commands through `RegistrationGuard` first. Initially routes only `/start`, `/help`, unknown — new command handlers added in US phases
- [x] T018 [P] Create `RegistrationGuard` in `src/main/java/ru/zahaand/smarttaskbot/handler/RegistrationGuard.java` — inject `UserService`, `NotificationService`; implement `checkAndRoute(Update update, Runnable commandAction)`: calls `userService.isRegistered(telegramUserId)`, if false sends timezone keyboard via `notificationService.sendTimezoneKeyboard()` and returns, if true executes `commandAction`
- [x] T019 Create `SmartTaskBot` in `src/main/java/ru/zahaand/smarttaskbot/handler/SmartTaskBot.java` — `@Component` extending `TelegramLongPollingBot`; constructor receives `BotConfig` and `UpdateDispatcher`; override `getBotToken()` and `getBotUsername()` from config; `onUpdateReceived(Update update)` contains single line: `dispatcher.dispatch(update)`
- [x] T020 [P] Create `HelpCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/HelpCommandHandler.java` — inject `NotificationService`; `handle(Update update)` sends the help text listing all 5 commands with syntax (see contracts/bot-commands.md `/help` response)
- [x] T021 [P] Create `UnknownInputHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/UnknownInputHandler.java` — inject `NotificationService`; `handle(Update update)` sends "I don't understand that command. Use /help to see available commands."
- [x] T022 Create `StartCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/StartCommandHandler.java` — inject `UserService`, `NotificationService`; `handle(Update update)`: if `userService.isRegistered(telegramUserId)` → send "Welcome back! You are already registered. Use /help to see available commands."; else → send greeting + call `notificationService.sendTimezoneKeyboard(chatId)`
- [x] T023 Create `TimezoneCallbackHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/callback/TimezoneCallbackHandler.java` — inject `UserService`, `NotificationService`; `handle(Update update)`: parse callback data, validate `tz:` prefix and known timezone value; if valid → call `userService.register(telegramUserId, username, timezone)`, send "Timezone set: {tz} ✓ You're all set! Use /help to see available commands."; if invalid → send error message

**Checkpoint**: Bot starts, connects to Telegram, `/start` shows timezone keyboard, tapping a button registers the user, `/help` lists commands. Foundation is complete — user story implementation begins.

---

## Phase 3: User Story 1 — Create a Task (Priority: P1) 🎯 MVP

**Goal**: User can register and create a task that is saved with a unique ID.

**Independent Test**: Send `/start`, select timezone, send `/newtask Buy groceries`,
verify bot replies with `Task created ✓ #<ID>: Buy groceries`. Send `/newtask` with no
text, verify usage hint. Two different users each create a task and verify isolation.

- [x] T024 [P] [US1] Create `CreateTaskRequest` DTO in `src/main/java/ru/zahaand/smarttaskbot/dto/CreateTaskRequest.java` — fields: `Long telegramUserId`, `String text`; Lombok `@Getter @AllArgsConstructor`
- [x] T025 [US1] Create `TaskService` in `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java` — inject `TaskRepository`, `UserRepository`; implement `createTask(Long telegramUserId, String text)`: validate text not blank and ≤500 chars (throw `IllegalArgumentException` with user-facing message on violation), look up `User` by `telegramUserId`, create and save `Task` with `status=ACTIVE`, return saved task's `id` and `text`
- [x] T026 [US1] Create `NewTaskCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/NewTaskCommandHandler.java` — inject `TaskService`, `NotificationService`; `handle(Update update)`: extract text after `/newtask`, if blank → send usage hint; else call `taskService.createTask()`, on success send "Task created ✓\n#{id}: {text}", on validation error send the exception message
- [x] T027 [US1] Register `NewTaskCommandHandler` in `UpdateDispatcher` routing in `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java` — inject `NewTaskCommandHandler`; add `/newtask` case to the command routing through `RegistrationGuard`

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 — View Task List (Priority: P2)

**Goal**: User can retrieve a formatted list of all their ACTIVE tasks.

**Independent Test**: Create 3 tasks, send `/tasks`, verify all 3 appear with correct IDs
and text. Complete one task (manually or after US4), verify it disappears from the list.
Send `/tasks` with no tasks, verify empty-state message.

- [x] T028 [P] [US2] Create `TaskDto` in `src/main/java/ru/zahaand/smarttaskbot/dto/TaskDto.java` — fields: `Long id`, `String text`, `String reminderTime` (pre-formatted as `DD.MM.YYYY HH:mm` in user's timezone, `null` if no reminder); Lombok `@Getter @AllArgsConstructor`
- [x] T029 [US2] Add `getActiveTasks(Long telegramUserId)` method to `TaskService` in `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java` — call `taskRepository.findAllByUserTelegramUserIdAndStatus(telegramUserId, ACTIVE)`, convert each to `TaskDto` by mapping fields and converting `reminderTime` (`Instant` UTC → `ZonedDateTime` in user's timezone via `userService.getTimezone()` → format `dd.MM.yyyy HH:mm`), return `List<TaskDto>`
- [x] T030 [US2] Create `TaskListCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/TaskListCommandHandler.java` — inject `TaskService`, `NotificationService`; `handle(Update update)`: call `taskService.getActiveTasks(telegramUserId)`; if empty → send "You have no active tasks.\nUse /newtask to create one."; else build numbered list "Your active tasks:\n\n#{id} {text}" with `⏰ {reminderTime}` appended when non-null, send via `notificationService.sendMessage()`
- [x] T031 [US2] Register `TaskListCommandHandler` in `UpdateDispatcher` routing in `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java` — inject `TaskListCommandHandler`; add `/tasks` case through `RegistrationGuard`

**Checkpoint**: User Stories 1 and 2 are both independently functional.

---

## Phase 5: User Story 3 — Set a Reminder (Priority: P3)

**Goal**: User can attach a datetime to a task; bot sends a notification at that time.

**Independent Test**: Set a reminder 2 minutes in the future on an existing task, wait for
delivery, verify notification arrives in chat. Set reminder on a COMPLETED task, verify
error. Use invalid date format, verify format hint.

- [ ] T032 [US3] Add `setReminder(Long telegramUserId, Long taskId, String dateTimeInput)` to `TaskService` in `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java` — look up task via `taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)`, throw if absent; reject if `status=COMPLETED`; parse `dateTimeInput` with `DateTimeFormatter("dd.MM.yyyy HH:mm")`, attach user's timezone via `ZoneId.of(userService.getTimezone(telegramUserId))`, convert to `Instant` (UTC); save `reminderTime`, reset `reminderProcessed=false`, `reminderRetryAt=null`; return `TaskDto` for confirmation message
- [ ] T033 [P] [US3] Add `sendReminder(Task task)` method to `NotificationService` in `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java` — send message `"⏰ Reminder: {task.getText()}"` to `task.getUser().getTelegramUserId()`
- [ ] T034 [US3] Create `RemindCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/RemindCommandHandler.java` — inject `TaskService`, `NotificationService`; `handle(Update update)`: split args into `[taskId, date, time]`; on missing/invalid args send usage hint; call `taskService.setReminder()`; on `DateTimeParseException` send format error; on task-not-found or COMPLETED error send respective message; on success send "Reminder set ✓\n#{id} {text} — {datetime} ({timezone})"
- [ ] T035 [US3] Create `ReminderService` in `src/main/java/ru/zahaand/smarttaskbot/service/ReminderService.java` — inject `TaskRepository`, `NotificationService`; annotate `processDueReminders()` with `@Scheduled(fixedDelay = 60_000)` and `@Transactional`; Query 1: find tasks where `reminderTime ≤ Instant.now()`, `reminderProcessed=false`, `status=ACTIVE`, `reminderRetryAt IS NULL` → for each call `notificationService.sendReminder()`, on success set `reminderProcessed=true`, on failure set `reminderRetryAt = Instant.now().plus(60, SECONDS)`; Query 2: find tasks where `reminderRetryAt ≤ Instant.now()`, `reminderProcessed=false`, `status=ACTIVE` → for each retry `notificationService.sendReminder()`, on success set `reminderProcessed=true`, on failure log WARN and set `reminderProcessed=true` (discard per FR-009); save all modified tasks
- [ ] T036 [US3] Register `RemindCommandHandler` in `UpdateDispatcher` routing in `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java` — inject `RemindCommandHandler`; add `/remind` case through `RegistrationGuard`

**Checkpoint**: User Stories 1, 2, and 3 are all independently functional.

---

## Phase 6: User Story 4 — Complete a Task (Priority: P4)

**Goal**: User can mark a task as COMPLETED; it disappears from the active list.

**Independent Test**: Create a task, send `/done {id}`, verify "Task completed ✓" response.
Send `/tasks`, verify task is absent. Send `/done` with another user's task ID, verify
"Task not found" (no data leak).

- [ ] T037 [US4] Add `completeTask(Long telegramUserId, Long taskId)` to `TaskService` in `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java` — look up task via `taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)`; if absent throw with "Task #{id} not found." message; set `status=COMPLETED`, save, return task text for confirmation
- [ ] T038 [US4] Create `DoneCommandHandler` in `src/main/java/ru/zahaand/smarttaskbot/handler/command/DoneCommandHandler.java` — inject `TaskService`, `NotificationService`; `handle(Update update)`: parse task ID from args, if missing or non-numeric → send usage hint; call `taskService.completeTask()`; on success send "Task completed ✓\n#{id} {text}"; on not-found send "Task #{id} not found."
- [ ] T039 [US4] Register `DoneCommandHandler` in `UpdateDispatcher` routing in `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java` — inject `DoneCommandHandler`; add `/done` case through `RegistrationGuard`

**Checkpoint**: All four user stories are independently functional. Full task lifecycle works.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final hardening, validation, and documentation completeness.

- [ ] T040 [P] Review `application.yaml` — confirm `ddl-auto: validate`, `timezone.default_storage: NORMALIZE_UTC` are present; add `show-sql: false` for production readiness in `src/main/resources/application.yaml`
- [ ] T041 [P] Verify Lombok `@PrePersist` sets `createdAt` on both `User` and `Task` in `src/main/java/ru/zahaand/smarttaskbot/model/` — add if missing from T009/T010
- [ ] T042 [P] Verify no unused imports, no orphaned classes across all packages — run `mvn compile` and confirm zero warnings
- [ ] T043 Run full lifecycle validation per `specs/001-task-bot-mvp/quickstart.md` — send `/start`, select timezone, `/newtask`, `/tasks`, `/remind`, wait for notification, `/done`, confirm task list is empty

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Foundational
- **US2 (Phase 4)**: Depends on US1 (reuses `TaskService`, `UserService`)
- **US3 (Phase 5)**: Depends on Foundational; can run parallel to US2
- **US4 (Phase 6)**: Depends on Foundational; can run parallel to US2/US3
- **Polish (Phase 7)**: Depends on all user story phases complete

### Within Foundational Phase — Parallel Opportunities

```
[Immediate start — no dependencies]
  T008 TaskStatus enum
  T009 User entity
  T013 BotConfig
  T014 SchedulingConfig

[After T009]
  T011 UserRepository

[After T008 + T009]
  T010 Task entity

[After T010]
  T012 TaskRepository

[After T013]
  T015 NotificationService

[After T011]
  T016 UserService

[After T015 + T016]
  T018 RegistrationGuard
  T020 HelpCommandHandler   [P with T018]
  T021 UnknownInputHandler  [P with T018]
  T022 StartCommandHandler
  T023 TimezoneCallbackHandler

[After T015 + T016 + T018 + T020 + T021 + T022 + T023]
  T017 UpdateDispatcher

[After T017 + T013]
  T019 SmartTaskBot

Migrations (sequential due to FK):
  T004 → T005 → T006 [P with T005] → T007
```

### Within User Story Phases — Parallel Opportunities

```bash
# US1
Task: T024 CreateTaskRequest DTO        # [P] — independent file
Task: T025 TaskService.createTask()     # after T024 logically
Task: T026 NewTaskCommandHandler        # after T025
Task: T027 Wire to UpdateDispatcher     # after T026

# US2
Task: T028 TaskDto                      # [P] — independent file
Task: T029 TaskService.getActiveTasks() # after T028
Task: T030 TaskListCommandHandler       # after T029
Task: T031 Wire to UpdateDispatcher     # after T030

# US3 — T033 [P] with T032
Task: T033 NotificationService.sendReminder()   # [P] — independent method
Task: T032 TaskService.setReminder()            # independent
Task: T034 RemindCommandHandler                 # after T032 + T033
Task: T035 ReminderService                      # after T033
Task: T036 Wire to UpdateDispatcher             # after T034

# US4
Task: T037 TaskService.completeTask()
Task: T038 DoneCommandHandler           # after T037
Task: T039 Wire to UpdateDispatcher     # after T038
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (**CRITICAL** — blocks everything)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: `/start` → timezone → `/newtask` → confirm task ID returned
5. Deploy or demo if ready

### Incremental Delivery

1. Setup + Foundational → bot starts, registration works
2. US1 → task creation works → first deliverable demo
3. US2 → task listing works → users can manage their list
4. US3 → reminders work → bot's differentiating feature is live
5. US4 → task completion works → full lifecycle closes

---

## Notes

- `[P]` tasks operate on different files with no intra-phase dependency — safe to parallelize
- `[USx]` label maps each task to its user story for traceability
- `TaskService` is built incrementally: one method per user story (T025 → T029 → T032 → T037)
- `UpdateDispatcher` is wired incrementally: one handler per user story (T027 → T031 → T036 → T039)
- `NotificationService` gains `sendReminder()` in US3 (T033) — all other send methods built in Foundational (T015)
- Reminder delivery deliberately uses `reminder_processed` (not `reminder_sent`) — field means "scheduler done with this reminder", not "delivered successfully"
- All `Instant` UTC values must be converted to user's `ZoneId` before display — conversion only in service layer
