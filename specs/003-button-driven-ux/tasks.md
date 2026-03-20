# Tasks: Button-Driven UX Improvements

**Branch**: `003-button-driven-ux`
**Input**: `specs/003-button-driven-ux/plan.md`, `specs/003-button-driven-ux/spec.md`
**Spec**: US1–US6 (P1×3, P2×2, P3×1) | **Total tasks**: 46

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[USN]**: User story label for traceability
- Paths relative to `src/main/java/ru/zahaand/smarttaskbot/` unless noted

---

## Phase 1: Foundation — State Machine Infrastructure

**Purpose**: Core infrastructure that MUST be complete before any user story flow can work.
All state-driven routing, the `UserState` persistence layer, and `UpdateDispatcher` refactoring live here.

**⚠️ CRITICAL**: No user story implementation can begin until this phase is complete.

- [x] T001 Create `ConversationState` enum with values IDLE, CREATING_TASK, SELECTING_REMINDER_DATE, ENTERING_REMINDER_TIME, CONFIRMING_DELETE in `model/ConversationState.java`

- [x] T002 Create `UserState` JPA entity (PK = `telegramUserId`, fields: `state` VARCHAR(50), `context` TEXT/JSONB, `updatedAt` Instant) in `model/UserState.java` — note: JPA entities are exempt from `final` fields per plan

- [x] T003 Create `UserStateRepository` extending `JpaRepository<UserState, Long>` in `repository/UserStateRepository.java`

- [x] T004 Create `ConversationContext` DTO with fields `taskId`, `viewingYear`, `viewingMonth`, `date` (Jackson `@JsonInclude(NON_NULL)`) in `dto/ConversationContext.java`

- [x] T005 Create Liquibase migration `src/main/resources/db/changelog/004-create-user-states-table.xml` — table `user_states` with columns `telegram_user_id` (BIGINT PK FK→users), `state` (VARCHAR(50) NOT NULL DEFAULT 'IDLE'), `context` (JSONB nullable), `updated_at` (TIMESTAMP NOT NULL DEFAULT NOW()); include rollback block `DROP TABLE user_states`; register in `src/main/resources/db/changelog/db.changelog-master.xml`

- [x] T006 [P] Add all callback-prefix constants (`CB_TZ`, `CB_TASK_REMIND`, `CB_TASK_DONE`, `CB_TASK_DELETE`, `CB_CAL_DATE`, `CB_CAL_NAV`, `CB_CONFIRM_DELETE`, `CB_CONFIRM_CANCEL`, `CB_TASKS_TAB`, `CB_NO_OP`) and persistent-menu button label constants (`BTN_NEW_TASK = "📝 Новая задача"`, `BTN_MY_TASKS = "📋 Мои задачи"`, `BTN_REMINDER = "⏰ Напоминание"`) to `config/BotConstants.java`

- [x] T007 Implement `UserStateService` in `service/UserStateService.java` with methods: `getState(Long)`, `setState(Long, ConversationState)`, `setStateWithContext(Long, ConversationState, ConversationContext)`, `getContext(Long)`, `updateContext(Long, ConversationContext)`, `resetIfStale(Long)` (resets to IDLE if `updatedAt` > 24h ago), `cancelWithNotification(Long userId, Long chatId, ConversationState)` (resets state + sends cancel message); inject `ObjectMapper` for JSON ser/des; catch `JsonProcessingException` in `getContext()` → reset to IDLE + log ERROR

- [x] T008 Add to `service/NotificationService.java`: `answerCallbackQuery(String callbackQueryId)`, `sendPersistentMenu(Long chatId, String text)` (builds `ReplyKeyboardMarkup` with `isPersistent=true`, `resizeKeyboard=true`, 3 buttons from `BotConstants`), and private `safeEdit(EditMessageText editRequest, SendMessage fallback)` helper (wraps `execute(editRequest)` in try-catch `TelegramApiException`; on failure executes `sendMessage` fallback and logs WARN)

- [x] T009 Refactor `handler/UpdateDispatcher.java`: extract `routeCallback(Update)` and `routeMessage(Update)` private methods; implement full routing logic per plan (10-step `routeMessage` including `resetIfStale` call, `/cancel` detection at step 3, `isPersistentMenuButton` check at step 4, state-driven routing at steps 5–7, button routing at step 8, command switch at step 9); implement `routeCallback` with `startsWith`-based dispatch for all 9 prefixes + `NO_OP` silent answer; add `isPersistentMenuButton(String)` helper

**Checkpoint**: State machine, DB schema, and dispatcher routing are in place. User story phases can now begin.

---

## Phase 2: US1 — Persistent Menu After Registration (P1)

**Goal**: Every registered user sees the persistent keyboard after registration and on `/start`.
**Independent Test**: New user completes timezone selection → persistent keyboard appears. Existing user sends `/start` → keyboard re-appears. All 3 buttons respond meaningfully.

- [x] T010 [US1] Modify `handler/callback/TimezoneCallbackHandler.java`: after calling `userService.register(...)`, call `notificationService.sendPersistentMenu(chatId, "Timezone set: " + timezone + " ✓\nГотово! Нажми кнопку ниже, чтобы начать.")` instead of plain `sendMessage`

- [x] T011 [US1] Modify `handler/command/StartCommandHandler.java`: for already-registered users, call `notificationService.sendPersistentMenu(chatId, "С возвращением! Нажми кнопку ниже.")` instead of plain `sendMessage`

**Checkpoint**: US1 complete — persistent keyboard visible after registration and on `/start`.

---

## Phase 3: US2 — Create Task via Button (P1)

**Goal**: User taps "📝 Новая задача" → bot prompts for text → user types text → task created and confirmed.
**Independent Test**: Tap button, type task text, confirm "✅ Задача #N создана:" appears. Try empty text — bot stays in CREATING_TASK. Try `/cancel` mid-flow — bot cancels with notification.

- [x] T012 [US2] Create `handler/text/NewTaskButtonHandler.java`: calls `userStateService.setState(userId, CREATING_TASK)`, then `notificationService.sendMessage(chatId, "Введи текст задачи:")`

- [x] T013 [US2] Create `handler/text/TaskCreationTextHandler.java`: called when state is CREATING_TASK and input is non-command, non-menu text; if blank → `sendMessage("Текст задачи не может быть пустым.")` + stay in state; else → `taskService.createTask(userId, text)` → `notificationService.sendMessage(chatId, "✅ Задача #" + id + " создана: " + text)` → `userStateService.setState(userId, IDLE)`

- [x] T014 [US2] Modify `handler/UnknownInputHandler.java`: update response text to hint at persistent menu buttons, e.g. `"Используй кнопки меню ниже или /help для списка команд."` (FR-019)

**Checkpoint**: US2 complete — full create-task-via-button flow works independently.

---

## Phase 4: US3 — Task List with Inline Action Buttons (P1)

**Goal**: "📋 Мои задачи" shows active tasks with 3 inline buttons each; tab toggle switches between active/completed in-place.
**Independent Test**: Tap "📋 Мои задачи" → see active tasks with `[⏰ Напомнить][✅ Выполнить][🗑 Удалить]`; tap "Выполненные" → list edits in-place to show completed tasks with `[🗑 Удалить]`; tap "✅ Выполнить" on a task → task marked done, list refreshes.

- [x] T015 [US3] Add `getCompletedTasks(Long telegramUserId)` method to `service/TaskService.java` using existing `findByUserTelegramUserIdAndStatus` query with `TaskStatus.COMPLETED`

- [x] T016 [P] [US3] Add `findByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status)` query method to `repository/TaskRepository.java` (Spring Data naming — may already exist; confirm and add if missing)

- [x] T017 [US3] Create `service/TaskListKeyboardBuilder.java`: method `buildKeyboard(List<TaskDto> tasks, TaskStatus activeTab)` → `InlineKeyboardMarkup`; active tasks get 3-button row `[TASK_REMIND:<id>][TASK_DONE:<id>][TASK_DELETE:<id>]`; completed tasks get 1-button row `[TASK_DELETE:<id>]`; append tab row `[Активные ✓][Выполненные]` or `[Активные][Выполненные ✓]` using `BotConstants.CB_TASKS_TAB` prefixes; truncate at 20 tasks with note "Показаны первые 20 задач…"

- [x] T018 [US3] Add to `service/NotificationService.java`: `sendTaskList(Long chatId, List<TaskDto> tasks, TaskStatus tab)` → sends text + keyboard built by `TaskListKeyboardBuilder`; `editTaskList(Long chatId, Integer messageId, List<TaskDto> tasks, TaskStatus tab)` → builds `EditMessageText` + calls `safeEdit(...)`

- [x] T019 [US3] Create `handler/text/TaskListButtonHandler.java`: called for `BTN_MY_TASKS` and `BTN_REMINDER` taps; loads active tasks via `taskService.getActiveTasks(userId)` → calls `notificationService.sendTaskList(chatId, tasks, ACTIVE)`

- [x] T020 [US3] Create `handler/callback/TaskListTabCallbackHandler.java`: handles `TASKS_TAB:ACTIVE` and `TASKS_TAB:COMPLETED`; calls `answerCallbackQuery`; loads appropriate task list; calls `notificationService.editTaskList(chatId, messageId, tasks, tab)`

- [x] T021 [US3] Create `handler/callback/TaskActionCallbackHandler.java` (partial — TASK_DONE only): handles `TASK_DONE:<taskId>`; calls `taskService.completeTask(userId, taskId)`; calls `answerCallbackQuery`; refreshes task list in-place via `notificationService.editTaskList(..., ACTIVE)` — TASK_DELETE and TASK_REMIND will be added in later phases

**Checkpoint**: US3 complete — task list with tab toggle and "Done" action work independently. "Delete" and "Remind" buttons are visible but not yet wired (handled in Phase 5–6).

---

## Phase 5: US4 — Set Reminder via Inline Calendar (P2)

**Goal**: "⏰ Напомнить" on a task opens an inline calendar; user picks a future date; bot prompts for time as free text; reminder is created.
**Independent Test**: Tap "⏰ Напомнить" on any active task → calendar appears with past dates disabled → navigate months → select future date → type "14:30" → reminder confirmed. Try "12 вечера" → specific 12-hour hint appears. Try gibberish → format hint appears.

- [x] T022 [US4] Create `service/TimeParserService.java`: `parse(String input)` → `Optional<LocalTime>`; `isTwelveOClockAmbiguous(String input)` → boolean; support formats: `HH:mm`, `H:mm`, `N утра` (N 1–11), `N вечера` (N 1–11), `N:mm утра`, `N:mm вечера`; reject `12 утра`/`12 вечера` with empty Optional; use ordered regex matching; no exceptions escape the method

- [x] T023 [US4] Create `service/CalendarKeyboardBuilder.java`: `buildCalendar(int year, int month)` → `InlineKeyboardMarkup`; row 0: header "« Месяц YYYY »" with `CB_NO_OP`; row 1: day-of-week labels with `CB_NO_OP`; rows 2–N: date cells — past dates get text "·" + `CB_NO_OP`, future/today dates get text "D" + `CAL_DATE:YYYY-MM-DD`, out-of-month cells get " " + `CB_NO_OP`; nav row: "←" + `CAL_NAV:-1` (or `CB_NO_OP` if current month) + "→" + `CAL_NAV:+1`

- [x] T024 [US4] Add `setReminderFromCalendar(Long telegramUserId, Long taskId, LocalDate date, LocalTime time)` to `service/TaskService.java`: combines date+time, converts from user's timezone to UTC, delegates to existing reminder persistence logic

- [x] T025 [US4] Add to `service/NotificationService.java`: `sendCalendar(Long chatId, int year, int month)` and `editCalendar(Long chatId, Integer messageId, int year, int month)` — the latter calls `safeEdit(...)` with the updated calendar keyboard

- [x] T026 [US4] Expand `handler/callback/TaskActionCallbackHandler.java` with TASK_REMIND handling: on `TASK_REMIND:<taskId>` → call `answerCallbackQuery`; load current month; call `userStateService.setStateWithContext(userId, SELECTING_REMINDER_DATE, ctx{taskId, year, month})`; call `notificationService.sendCalendar(chatId, year, month)`

- [x] T027 [US4] Create `handler/callback/CalendarCallbackHandler.java`: handles `CAL_NAV:<±1>` — verify state is SELECTING_REMINDER_DATE (else send "Сессия истекла, начни заново" + IDLE), load context, compute new year/month, call `userStateService.updateContext(...)`, call `notificationService.editCalendar(chatId, messageId, year, month)`, call `answerCallbackQuery`; handles `CAL_DATE:<YYYY-MM-DD>` — verify state, load taskId from context, call `userStateService.setStateWithContext(userId, ENTERING_REMINDER_TIME, ctx{taskId, date})`, send "Введи время (например: 14:30, 9 утра, 21:00)"

- [x] T028 [US4] Create `handler/text/ReminderTimeTextHandler.java`: called when state is ENTERING_REMINDER_TIME; call `timeParserService.parse(text)`; if empty + `isTwelveOClockAmbiguous` → send "Для 12 часов используй формат HH:mm (00:00 или 12:00)"; if empty otherwise → send "Не понял формат. Попробуй: 14:30, 9 утра, 21:00"; if present → load context → call `taskService.setReminderFromCalendar(userId, taskId, date, time)` → confirm "⏰ Напоминание установлено на [date] [time]" → `userStateService.setState(userId, IDLE)`

**Checkpoint**: US4 complete — full calendar reminder flow works independently.

---

## Phase 6: US5 — Delete Task with Confirmation (P2)

**Goal**: "🗑 Удалить" shows confirmation dialog; user confirms → task permanently deleted; or cancels → no change.
**Independent Test**: Tap "🗑 Удалить" → confirmation message with task name appears; tap "❌ Отмена" → task unchanged, state IDLE; repeat → tap "✅ Да, удалить" → task gone, confirmed.

- [x] T029 [US5] Add `deleteByIdAndUserTelegramUserId(Long taskId, Long telegramUserId)` to `repository/TaskRepository.java` using `@Modifying @Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.telegramUserId = :telegramUserId")`

- [x] T030 [US5] Add `deleteTask(Long telegramUserId, Long taskId)` to `service/TaskService.java`: calls `taskRepository.deleteByIdAndUserTelegramUserId`; returns deleted row count; service layer treats 0 rows as "already gone" (no exception — handled gracefully by caller)

- [x] T031 [US5] Add `sendDeleteConfirmation(Long chatId, Long taskId, String taskText)` to `service/NotificationService.java`: sends "Удалить задачу '#N: [text]'? Это действие нельзя отменить." with inline buttons `[✅ Да, удалить → CONFIRM_DELETE:<taskId>]` `[❌ Отмена → CONFIRM_CANCEL]`

- [x] T032 [US5] Expand `handler/callback/TaskActionCallbackHandler.java` with TASK_DELETE handling: on `TASK_DELETE:<taskId>` → call `answerCallbackQuery`; load task text via `taskService.getActiveTasks` (or dedicated lookup); call `userStateService.setStateWithContext(userId, CONFIRMING_DELETE, ctx{taskId})`; call `notificationService.sendDeleteConfirmation(chatId, taskId, taskText)`

- [x] T033 [US5] Create `handler/callback/DeleteConfirmCallbackHandler.java`: handles `CONFIRM_DELETE:<taskId>` → call `answerCallbackQuery`; load taskId from callback data; call `taskService.deleteTask(userId, taskId)`; if 0 rows deleted → send "Задача уже была удалена."; else → send "✅ Задача удалена."; call `userStateService.setState(userId, IDLE)`. Handles `CONFIRM_CANCEL` → call `answerCallbackQuery`; call `userStateService.setState(userId, IDLE)`; send "Удаление отменено."

**Checkpoint**: US5 complete — delete with confirmation works; all P2 flows (US4 + US5) are functional.

---

## Phase 7: Cross-Cutting — editMessageText Fallback (Phase 3a from plan)

**Purpose**: Ensure all in-place message edits degrade gracefully when the Telegram edit API fails.
This step validates that `safeEdit` (added in T008) is correctly wired in all callers.

- [ ] T034 Verify `editTaskList` and `editCalendar` in `service/NotificationService.java` both route through `safeEdit(EditMessageText, SendMessage)` — the fallback `SendMessage` must carry the same text and keyboard; confirm WARN log is issued on fallback path; this is a code-review / hardening task, not new functionality

---

## Phase 8: US6 — Timezone Selection with Live Time (P3)

**Goal**: During registration each timezone button shows current local time: "🕐 Москва, СПб (сейчас 18:45)".
**Independent Test**: Start fresh registration flow → each timezone button shows correct current HH:mm for that zone.

- [ ] T035 [US6] Modify `service/NotificationService.java` method `sendTimezoneKeyboard(Long chatId, String text)` (or its keyboard builder): for each timezone in `BotConstants.TIMEZONE_ROWS`, compute `ZonedDateTime.now(ZoneId.of(tz)).format(DateTimeFormatter.ofPattern("HH:mm"))` and append to button label, e.g. `"Москва, СПб (сейчас " + time + ")"`

**Checkpoint**: US6 complete — timezone registration keyboard shows live local time.

---

## Phase 9: Tests

**Purpose**: Unit tests for all new and modified classes, per Constitution §IX.
All test tasks are independent and can run in parallel.

- [ ] T036 [P] Create `src/test/java/ru/zahaand/smarttaskbot/service/TimeParserServiceTest.java`: `@ExtendWith(MockitoExtension.class)`, `@Nested` per method, `@ParameterizedTest @MethodSource` for all 16 input cases from `contracts/time-parser-contract.md`; cover: valid HH:mm, valid N утра (1–11), valid N вечера (1–11), valid N:mm утра/вечера, rejection of 12 утра / 12 вечера, out-of-range hours/minutes, non-matching strings; verify `isTwelveOClockAmbiguous` returns true only for "12 утра" / "12 вечера"

- [ ] T037 [P] Create `src/test/java/ru/zahaand/smarttaskbot/service/UserStateServiceTest.java`: test `getState` returns IDLE when no row exists; test `setState` upserts correctly; test `setStateWithContext` serializes `ConversationContext` to JSON; test `resetIfStale` resets to IDLE when `updatedAt` > 24h, does nothing when < 24h; test `cancelWithNotification` for all 4 states produces correct cancel message string and calls `notificationService.sendMessage`; test `getContext` returns empty on malformed JSON and resets state to IDLE

- [ ] T038 [P] Create `src/test/java/ru/zahaand/smarttaskbot/service/CalendarKeyboardBuilderTest.java`: test past dates (before today) produce buttons with text "·" and callback `NO_OP`; test today and future dates produce `CAL_DATE:YYYY-MM-DD` callbacks; test out-of-month cells produce `NO_OP`; test nav row: "←" gets `CAL_NAV:-1` for future months, `NO_OP` for current month; test "→" always gets `CAL_NAV:+1`; test header row is non-interactive (`NO_OP`)

- [ ] T039 [P] Create `src/test/java/ru/zahaand/smarttaskbot/service/TaskListKeyboardBuilderTest.java`: test active task row has exactly 3 buttons with correct `TASK_REMIND:`, `TASK_DONE:`, `TASK_DELETE:` callbacks; test completed task row has exactly 1 button with `TASK_DELETE:` callback; test tab row is always present; test ACTIVE tab shows "Активные ✓" + "Выполненные"; test COMPLETED tab shows "Активные" + "Выполненные ✓"; test truncation: 21 tasks → 20 buttons + note appended to message text

- [ ] T040 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/callback/CalendarCallbackHandlerTest.java`: test `CAL_NAV:+1` from any month updates context and calls `editCalendar`; test `CAL_NAV:-1` from a future month navigates back; test `CAL_NAV:-1` from current month is answered silently (no context change, no edit, just `answerCallbackQuery`); test `CAL_DATE:<date>` transitions state to ENTERING_REMINDER_TIME with date in context; test stale state (not SELECTING_REMINDER_DATE) → sends "Сессия истекла" + resets to IDLE

- [ ] T041 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/callback/TaskActionCallbackHandlerTest.java`: test `TASK_DONE:<id>` marks task complete and refreshes list; test `TASK_REMIND:<id>` sets state to SELECTING_REMINDER_DATE with taskId in context and sends calendar; test `TASK_DELETE:<id>` sets state to CONFIRMING_DELETE with taskId in context and sends confirmation message

- [ ] T042 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/callback/DeleteConfirmCallbackHandlerTest.java`: test `CONFIRM_DELETE:<id>` for existing task → deletes + sends confirmation + state IDLE; test `CONFIRM_DELETE:<id>` for already-deleted task (0 rows) → sends "Задача уже была удалена." + state IDLE; test `CONFIRM_CANCEL` → sends "Удаление отменено." + state IDLE + no deletion

- [ ] T043 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/text/NewTaskButtonHandlerTest.java`: test sets state to CREATING_TASK and sends "Введи текст задачи:"

- [ ] T044 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/text/TaskCreationTextHandlerTest.java`: test non-empty text → creates task + confirms + state IDLE; test blank/whitespace → error prompt + state stays CREATING_TASK

- [ ] T045 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/text/ReminderTimeTextHandlerTest.java`: test valid time "14:30" → creates reminder + confirms + state IDLE; test "12 вечера" → specific 12-hour hint + state stays ENTERING_REMINDER_TIME; test "abc" → generic format hint + state stays ENTERING_REMINDER_TIME

- [ ] T046 [P] Add to existing (or create new) `src/test/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcherTest.java`: test `CAL_DATE:` callback routes to `CalendarCallbackHandler`; test `TASKS_TAB:` callback routes to `TaskListTabCallbackHandler`; test `NO_OP` callback is answered silently without routing to any handler; test `BTN_NEW_TASK` text in IDLE state routes to `NewTaskButtonHandler`; test `BTN_MY_TASKS` in CREATING_TASK state → triggers `cancelWithNotification` then routes to `TaskListButtonHandler`; test `/cancel` in CREATING_TASK state → calls `cancelWithNotification` + returns without routing further; test `/newtask` in CREATING_TASK state → bypasses state handler + routes to command switch; test free text in CONFIRMING_DELETE state → sends "Пожалуйста, используй кнопки выше." + state unchanged

- [ ] T047 [P] Create `src/test/java/ru/zahaand/smarttaskbot/handler/LegacyCommandRegressionTest.java`: for each of `/newtask`, `/tasks`, `/remind`, `/done`, `/help` — test in IDLE state that the command produces the same response as before the refactoring (mock handlers, verify delegation); test `/newtask` and `/tasks` in CREATING_TASK state → verify they still reach their handlers (bypass state routing via command check at step 3/9 in `routeMessage`)

**Checkpoint**: All new and modified classes have unit tests. Feature is ready for manual QA via `quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (Foundation): No dependencies — start immediately
- **Phase 2** (US1): Depends on Phase 1 (`UserStateService`, `NotificationService.sendPersistentMenu`, `UpdateDispatcher` refactor)
- **Phase 3** (US2): Depends on Phase 1 (`UserStateService`, `UpdateDispatcher` routing)
- **Phase 4** (US3): Depends on Phase 1 (routing); `TaskService.getCompletedTasks` and `TaskListKeyboardBuilder` can start as soon as Phase 1 is done
- **Phase 5** (US4): Depends on Phase 4 (task list + `TaskActionCallbackHandler` stub for TASK_REMIND); `TimeParserService` and `CalendarKeyboardBuilder` are independent
- **Phase 6** (US5): Depends on Phase 4 (task list + `TaskActionCallbackHandler` stub for TASK_DELETE); `TaskRepository.deleteByIdAndUserTelegramUserId` is independent
- **Phase 7** (Fallback): Validates work done in Phases 4–5 (`safeEdit` wiring); no new code
- **Phase 8** (US6): Depends on Phase 1 only (existing `sendTimezoneKeyboard`); independent of US2–US5
- **Phase 9** (Tests): Each test class is independent; can be written in parallel with or after the corresponding implementation phase

### Within Phases

- T001–T005 (model/repo/migration): T002 before T003; T001 before T002; T005 immediately after T002 (schema matches entity)
- T006 (constants): independent, can start anytime in Phase 1
- T007 (UserStateService): depends on T001–T004
- T008 (NotificationService additions): independent of T007; T006 needed for constants
- T009 (UpdateDispatcher refactor): depends on T007 and T008

### Parallel Opportunities in Phase 1

```
T001 → T002 → T003   (sequential: enum → entity → repo)
T001 → T002 → T005   (sequential: entity must exist before migration)
T004                  (parallel: DTO has no dependencies)
T006                  (parallel: constants have no dependencies)
T008                  (parallel once T006 done: NotificationService additions)
T007 (after T001–T004 complete)
T009 (after T007 + T008 complete)
```

### Parallel Opportunities in Phase 9

All test tasks T036–T047 are fully independent and can be written concurrently.

---

## Implementation Strategy

### MVP First (US1 + US2 — Demo-ready in 2 phases)

1. Complete **Phase 1** (Foundation)
2. Complete **Phase 2** (US1 — Persistent Menu)
3. Complete **Phase 3** (US2 — Create Task via Button)
4. **STOP and VALIDATE**: User can register, see keyboard, tap button, create task — without typing a command
5. Demo-ready for the simplest customer scenario

### Incremental Delivery (Full Feature)

1. Phase 1 → Foundation ready
2. Phase 2 → US1: Persistent Menu ✓
3. Phase 3 → US2: Task Creation via Button ✓
4. Phase 4 → US3: Task List + Tab Toggle + Mark Done ✓
5. Phase 5 → US4: Calendar Reminder ✓
6. Phase 6 → US5: Delete with Confirmation ✓
7. Phase 7 → Hardening: edit fallback verified ✓
8. Phase 8 → US6: Timezone Live Time ✓
9. Phase 9 → Tests ✓

Each phase adds independently demonstrable value without breaking previous phases.

---

## Notes

- `[P]` tasks operate on different files with no shared mutable state — safe to parallelize
- `[USN]` labels map tasks to spec user stories for traceability (US1–US6)
- Constitution §VIII JPA exception: `UserState` entity fields need not be `final` — all other new classes must use `final` fields
- Constitution §IX: use `@ExtendWith(MockitoExtension.class)`, `@Nested` per method under test, `@ParameterizedTest @MethodSource` for boundary cases
- `TaskActionCallbackHandler` is built incrementally: TASK_DONE in T021, TASK_REMIND in T026, TASK_DELETE in T032 — each phase adds to the same class
- `safeEdit` helper (T008) must be in place before any `editTaskList` or `editCalendar` calls are made (Phases 4–5)
