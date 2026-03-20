# Tasks: i18n, UX Improvements, and Professional Polish

**Feature**: `004-i18n-and-improvements`
**Input**: Design documents from `/specs/004-i18n-and-improvements/`
**Prerequisites**: plan.md ✓, spec.md ✓, data-model.md ✓, research.md ✓

**Organization**: Tasks are grouped by phase from plan.md, mapped to user stories from spec.md.
**Tests**: Included — plan.md explicitly defines Phase 6 tests.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: User story this task belongs to (US1–US7 per spec.md)
- Setup / Foundational phases carry no story label

---

## Phase 1: Setup — Schema Foundation

**Purpose**: Both Liquibase migrations that unblock all user stories. Covers **US2** (ON DELETE
CASCADE) entirely.

**US2 Independent Test**: Delete a user record directly in the database; verify all tasks and
user_states rows for that user are gone with no application involvement.

- [x] T001 Create Migration 005 in `src/main/resources/db/changelog/005-add-language-to-users.xml`: dropNotNullConstraint on `users.timezone`; addColumn `language VARCHAR(2)` NULL; UPDATE backfill to `'EN'`; addNotNullConstraint on `language`; rollback block per data-model.md
- [x] T002 [P] Create Migration 006 in `src/main/resources/db/changelog/006-add-cascade-to-tasks.xml`: drop existing FK `fk_tasks_users`; re-add FK with `ON DELETE CASCADE`; rollback restores non-cascade FK
- [x] T003 Update `src/main/resources/db/changelog/db.changelog-master.xml`: include 005 and 006 in order after 004

**Checkpoint**: Schema ready — US2 acceptance criteria satisfied. All other phases can begin.

---

## Phase 2: Foundational — i18n Infrastructure

**Purpose**: Core Java types and the MessageService layer. MUST be complete before any user
story handler work begins.

**⚠️ CRITICAL**: No user story implementation can start until T004–T010 are done.

- [x] T004 Create `src/main/java/ru/zahaand/smarttaskbot/model/Language.java`: enum with values `EN` and `RU`
- [x] T005 [P] Add `AWAITING_LANGUAGE` and `AWAITING_TIMEZONE` as first two values in `src/main/java/ru/zahaand/smarttaskbot/model/ConversationState.java`
- [x] T006 Update `src/main/java/ru/zahaand/smarttaskbot/model/User.java`: add `@Column(name = "language") @Enumerated(EnumType.STRING) private Language language;`; remove `nullable = false` from `timezone` column annotation
- [x] T007 Update `src/main/java/ru/zahaand/smarttaskbot/config/BotConstants.java`: add `CB_LANG = "lang:"`, `CB_LANG_EN = "lang:EN"`, `CB_LANG_RU = "lang:RU"`, `CB_DELETE_ALL_REQUEST = "DELETE_ALL:request"`, `CB_DELETE_ALL_CONFIRM = "DELETE_ALL:confirm"`, `CB_DELETE_ALL_CANCEL = "DELETE_ALL:cancel"`, and `TIMEZONE_CITY_CODES` Map per plan Step 3.8
- [x] T008 Create `src/main/java/ru/zahaand/smarttaskbot/model/MessageKey.java`: enum with `(String en, String ru)` constructor and `get(Language)` method; include all 25 constants from data-model.md (WELCOME_BILINGUAL, SELECT_TIMEZONE, ALREADY_REGISTERED, TIMEZONE_CONFIRMED, ENTER_TASK_DESCRIPTION, CHOOSE_REMINDER_DATE, ENTER_REMINDER_TIME, TASK_CREATED, TASK_REMINDER_SET, DELETE_CONFIRM_SINGLE, DELETE_CONFIRM_ALL, TASK_DELETED, ALL_COMPLETED_DELETED, NO_COMPLETED_TASKS, SOMETHING_WENT_WRONG, INVALID_TIME_FORMAT, OPERATION_CANCELLED, USE_BUTTONS, COMING_SOON, BTN_NEW_TASK, BTN_MY_TASKS, BTN_YES_DELETE_ALL, BTN_CANCEL, REMINDER_NOTIFICATION); ENTER_REMINDER_TIME EN string must include "HH MM, or HH-MM"; REMINDER_NOTIFICATION: `EN="⏰ Reminder: %s"` / `RU="⏰ Напоминание: %s"`; placed in **model** package (CHK013 — pure domain enum, no Spring deps)
- [x] T009 Create `src/main/java/ru/zahaand/smarttaskbot/service/MessageService.java`: `@Service` bean with `get(MessageKey, Language)` (null-language falls back to EN) and `get(MessageKey, User)` overload
- [x] T010 [P] Create `src/test/java/ru/zahaand/smarttaskbot/service/MessageServiceTest.java`: `@DisplayName` first on each method; `@Nested` per public method (`get(key, language)`, `get(key, user)`); verify EN/RU resolution and null-language EN fallback; cover WELCOME_BILINGUAL, SELECT_TIMEZONE, ALREADY_REGISTERED, and at least one format-string key

**Checkpoint**: i18n layer ready — all subsequent phases can inject MessageService.

---

## Phase 3: US1 — Bilingual Interface (Priority: P1) 🎯 MVP

**Goal**: New user flows AWAITING_LANGUAGE → AWAITING_TIMEZONE → IDLE; all user-facing text
served via MessageService; zero hardcoded strings in handlers or services.

**Independent Test**: A fresh Telegram account sends `/start`, sees bilingual welcome + two
language buttons; selects Russian; every subsequent bot message — timezone prompt, task
confirmation, error, reminder notification — arrives in Russian.

### Tests for US1 (write first)

- [ ] T011 [P] [US1] Write `src/test/java/ru/zahaand/smarttaskbot/handler/command/StartCommandHandlerTest.java`: `@Nested` per scenario — new-user creates partial user and sends bilingual welcome; returning-user sends ALREADY_REGISTERED in user's language + main menu; mid-registration redirect to RegistrationGuard
- [ ] T012 [P] [US1] Write `src/test/java/ru/zahaand/smarttaskbot/handler/callback/LanguageCallbackHandlerTest.java`: `@Nested` — valid `lang:EN`, valid `lang:RU`, invalid language code, missing user

### Registration Flow

- [ ] T013 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/service/UserService.java`: add `createPartialUser(telegramUserId, username)` (single `@Transactional` — persists User with language=null + timezone=null AND UserState AWAITING_LANGUAGE atomically); add `updateLanguage(telegramUserId, Language)`, `updateTimezone(telegramUserId, String)`; redefine `isRegistered()` as `language != null AND timezone != null`; add `isLanguagePending()` and `isTimezonePending()`; remove or repurpose old `register()` method
- [ ] T014 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/service/UserStateService.java`: remove `initRegistration()` — UserState creation is exclusively owned by `UserService.createPartialUser()`; ensure `transitionTo()` handles AWAITING_LANGUAGE and AWAITING_TIMEZONE
- [ ] T015 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java`: add `sendLanguageKeyboard(chatId)` — sends WELCOME_BILINGUAL text with `[🇬🇧 English]` (lang:EN) and `[🇷🇺 Русский]` (lang:RU) inline buttons; add `sendTimezoneKeyboard(chatId, Language)` overload — shows SELECT_TIMEZONE in user's language; add `sendPersistentMenu(chatId, String, Language)` overload — uses MessageService for BTN_NEW_TASK and BTN_MY_TASKS labels
- [ ] T016 [US1] Rewrite `src/main/java/ru/zahaand/smarttaskbot/handler/command/StartCommandHandler.java`: if `isRegistered(userId)` → send ALREADY_REGISTERED + persistent menu in user's language; else if user does not exist → `createPartialUser()` + `sendLanguageKeyboard()`; else → RegistrationGuard handles mid-registration; add `@Slf4j` logging per plan Step 3.3
- [ ] T017 [US1] Create `src/main/java/ru/zahaand/smarttaskbot/handler/callback/LanguageCallbackHandler.java`: parse lang:EN / lang:RU from callback data; `updateLanguage()`; `transitionTo(AWAITING_TIMEZONE)`; `sendTimezoneKeyboard(chatId, language)`; log INFO with userId and language
- [ ] T018 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/handler/callback/TimezoneCallbackHandler.java`: remove user-creation logic (User already exists from `/start`); only call `updateTimezone()` and `transitionTo(IDLE)`; replace hardcoded confirmation string with `messageService.get(TIMEZONE_CONFIRMED, user).formatted(timezone)`; send persistent menu
- [ ] T019 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/handler/RegistrationGuard.java`: implement three-state routing — no User row → `sendLanguageKeyboard()`; language IS NULL → `sendLanguageKeyboard()`; timezone IS NULL → `sendTimezoneKeyboard(chatId, user.getLanguage())`; fully registered → `commandAction.run()`
- [ ] T020 [US1] Update `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java`: add `lang:` prefix routing to `LanguageCallbackHandler` as the first branch in `routeCallback()`; replace Russian fallback strings (`"Пожалуйста, используй кнопки выше."`, `"Эта функция скоро появится!"`) with `messageService.get(USE_BUTTONS/COMING_SOON, user)`

### Handler Migration to MessageService (FR-005)

- [ ] T021 [P] [US1] Inject `MessageService` + `UserService` into `src/main/java/ru/zahaand/smarttaskbot/handler/command/UnknownInputHandler.java`; replace hardcoded fallback string with `messageService.get(USE_BUTTONS, user)`
- [ ] T022 [P] [US1] Inject `MessageService` + `UserService` into `HelpCommandHandler.java`, `DoneCommandHandler.java`, `RemindCommandHandler.java`, `NewTaskCommandHandler.java` under `src/main/java/ru/zahaand/smarttaskbot/handler/command/`; replace all hardcoded user-facing strings with MessageService calls
- [ ] T023 [P] [US1] Inject `MessageService` + `UserService` into `NewTaskButtonHandler.java`, `TaskCreationTextHandler.java`, `ReminderTimeTextHandler.java`, `TaskListButtonHandler.java` under `src/main/java/ru/zahaand/smarttaskbot/handler/text/`; replace all hardcoded prompt strings with MessageService calls
- [ ] T024 [P] [US1] Inject `MessageService` + `UserService` into `src/main/java/ru/zahaand/smarttaskbot/handler/command/TaskListCommandHandler.java` and `src/main/java/ru/zahaand/smarttaskbot/handler/callback/TaskListTabCallbackHandler.java`; replace hardcoded strings; pass user to keyboard builders for language-aware labels
- [ ] T025 [P] [US1] Inject `MessageService` + `UserService` into `CalendarCallbackHandler.java`, `TaskActionCallbackHandler.java`, `DeleteConfirmCallbackHandler.java` under `src/main/java/ru/zahaand/smarttaskbot/handler/callback/`; replace all hardcoded confirmation and error strings with MessageService calls
- [ ] T026 [US1] Inject `MessageService` into `src/main/java/ru/zahaand/smarttaskbot/service/UserStateService.java`; replace any hardcoded cancel or stale-state messages with `messageService.get(OPERATION_CANCELLED / SOMETHING_WENT_WRONG, user)` calls
- [ ] T027 [US1] Inject `UserService` + `MessageService` into `src/main/java/ru/zahaand/smarttaskbot/service/ReminderService.java`; resolve owner Language via `userService.findById(task.getTelegramUserId()).getLanguage()`; replace `"⏰ Reminder: " + task.getTitle()` with `messageService.get(REMINDER_NOTIFICATION, user).formatted(task.getTitle())`

**Checkpoint**: US1 complete — every bot message is bilingual; new-user registration two-step flow works; zero hardcoded strings in handlers and services.

---

## Phase 4: US3 — Extended Time Input Formats (Priority: P2)

**Goal**: Users can enter reminder times as `14 00` or `14-00` in addition to `14:00`.

**Independent Test**: Submit task creation with `14 00` and `14-00` as the reminder time; both
create tasks with correct reminder times identical to `14:00`.

### Tests for US3 (write first)

- [ ] T028 [P] [US3] Update `src/test/java/ru/zahaand/smarttaskbot/service/TimeParserServiceTest.java`: add `@ParameterizedTest` + `@MethodSource` for SPACE-separated valid cases (`14 00`, `9 05`), HYPHEN-separated valid cases (`14-00`, `9-05`), boundary invalids (`25 00`, `12 99`, `25-00`)

### Implementation

- [ ] T029 [US3] Update `src/main/java/ru/zahaand/smarttaskbot/service/TimeParserService.java`: add `private static final Pattern SPACE = Pattern.compile("(\\d{1,2}) (\\d{2})")` and `HYPHEN = Pattern.compile("(\\d{1,2})-(\\d{2})")`; extend `parse()` to try all three patterns in sequence; invalid input returns `Optional.empty()`

**Checkpoint**: US3 complete — all three time formats accepted; US1 ENTER_REMINDER_TIME message already includes all formats in both languages.

---

## Phase 5: US4 — Delete All Completed Tasks (Priority: P2)

**Goal**: `🗑 Delete All` button on Completed screen shows confirmation with count; confirm
deletes all completed tasks for the user; cancel returns unchanged screen.

**Independent Test**: Create several completed tasks; tap `🗑 Delete All`; confirm; Completed
list is empty. Repeat and cancel; tasks are preserved.

### Tests for US4 (write first)

- [ ] T030 [P] [US4] Create `src/test/java/ru/zahaand/smarttaskbot/handler/callback/DeleteAllCompletedCallbackHandlerTest.java`: `@Nested` — request with N > 0 tasks (shows confirmation with count), request with 0 tasks (NO_COMPLETED_TASKS), confirm deletes and refreshes, cancel shows OPERATION_CANCELLED
- [ ] T031 [P] [US4] Update `src/test/java/ru/zahaand/smarttaskbot/service/TaskServiceTest.java`: add `@Nested` for `deleteAllCompleted` — N tasks deleted returns N, 0 tasks (no-op) returns 0

### Implementation

- [ ] T032 [P] [US4] Update `src/main/java/ru/zahaand/smarttaskbot/repository/TaskRepository.java`: add Spring Data derived query `countByTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status)`; add `@Modifying @Query("DELETE FROM Task t WHERE t.telegramUserId = :telegramUserId AND t.status = :status") int deleteAllByTelegramUserIdAndStatus(...)` with `@Transactional`
- [ ] T033 [US4] Update `src/main/java/ru/zahaand/smarttaskbot/service/TaskService.java`: add `countCompleted(Long telegramUserId)` delegating to `taskRepository.countByTelegramUserIdAndStatus(..., COMPLETED)`; add `@Transactional deleteAllCompleted(Long telegramUserId)` delegating to repository bulk delete; returns deleted count
- [ ] T034 [US4] Update `src/main/java/ru/zahaand/smarttaskbot/service/TaskListKeyboardBuilder.java`: on Completed tab, if `taskService.countCompleted(telegramUserId) > 0` append `🗑 Delete All` button with callback data `BotConstants.CB_DELETE_ALL_REQUEST`; no button when count == 0
- [ ] T035 [US4] Create `src/main/java/ru/zahaand/smarttaskbot/handler/callback/DeleteAllCompletedCallbackHandler.java`: branch on callback data — `CB_DELETE_ALL_REQUEST`: count completed; if 0 → NO_COMPLETED_TASKS; else → send DELETE_CONFIRM_ALL.formatted(count) with `[✅ Yes, delete all]` (CB_DELETE_ALL_CONFIRM) and `[❌ Cancel]` (CB_DELETE_ALL_CANCEL) inline buttons; `CB_DELETE_ALL_CONFIRM`: `taskService.deleteAllCompleted(userId)` + log INFO + send ALL_COMPLETED_DELETED + re-render Completed tab; `CB_DELETE_ALL_CANCEL`: send OPERATION_CANCELLED + re-render Completed tab
- [ ] T036 [US4] Update `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java`: add routing for `CB_DELETE_ALL_REQUEST`, `CB_DELETE_ALL_CONFIRM`, `CB_DELETE_ALL_CANCEL` to `DeleteAllCompletedCallbackHandler` after the `lang:` branch per plan Step 3.7

**Checkpoint**: US4 complete — bulk delete with confirmation fully functional; idempotent on double-tap.

---

## Phase 6: US5 — Improved Timezone Button Labels (Priority: P2)

**Goal**: Each timezone button shows current local time and English city codes alongside the
IANA identifier.

**Independent Test**: Open timezone selection; each button shows correct current time and
expected city abbreviations (e.g., `10:00 MSK, SPB` for `Europe/Moscow`).

- [ ] T037 [US5] Update `sendTimezoneKeyboard` in `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java`: compute each button label as `ZonedDateTime.now(ZoneId.of(tz)).format(DateTimeFormatter.ofPattern("HH:mm")) + " " + BotConstants.TIMEZONE_CITY_CODES.get(tz)`; on `ZoneRulesException` fall back to existing `TIMEZONE_DISPLAY_NAMES.get(tz)` (no crash); city codes always English regardless of user language per FR-011

**Checkpoint**: US5 complete — timezone buttons show live local time and city codes.

---

## Phase 7: US6 + US7 — Documentation (Priority: P3)

**Goal**: Every public class/method has bilingual Javadoc; README is fully bilingual.

**Independent Test for US6**: Sample of public classes and methods each has Javadoc with
English paragraph, blank line, Russian paragraph. Private methods may be absent.

**Independent Test for US7**: README renders navigation hint, full English section, `---`
divider, full Russian section; each section covers all four topics.

- [ ] T038 [P] [US6] Add bilingual Javadoc (EN paragraph, blank line, RU paragraph) to all **new** public classes and their public methods: `Language`, `MessageKey`, `MessageService`, `LanguageCallbackHandler`, `DeleteAllCompletedCallbackHandler` — private methods exempt
- [ ] T039 [P] [US6] Add bilingual Javadoc to all **modified** public classes and their changed public methods: `User`, `ConversationState`, `UserService`, `UserStateService`, `TaskService`, `TaskRepository`, `NotificationService`, `TaskListKeyboardBuilder`, `TimeParserService`, `StartCommandHandler`, `TimezoneCallbackHandler`, `RegistrationGuard`, `UpdateDispatcher`, `ReminderService`
- [ ] T040 [US7] Rewrite `README.md` with structure per spec: first line `🇷🇺 Описание на русском ниже / 🇬🇧 Russian description below`; English section covering project description + SDD/Spec Kit/Claude Code methodology, full command list, supported time formats (HH:MM, HH MM, HH-MM), local setup; `---` divider; same four topics in Russian

**Checkpoint**: Documentation phase complete — project ready for portfolio demonstration.

---

## Final Phase: Polish & Cross-Cutting

- [ ] T041 [P] Remove all now-unused hardcoded user-facing string constants from `src/main/java/ru/zahaand/smarttaskbot/config/BotConstants.java` that were replaced by MessageService keys (BTN_NEW_TASK, BTN_MY_TASKS raw string constants, timezone label strings, and any registration prompt strings); verify no compile errors
- [ ] T042 Run `mvn test` from project root; fix any compilation or test failures across all phases; confirm all new test classes pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately; T001 and T002 can run in parallel
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user story phases; T004+T005 can run in parallel after T001; T008 depends on T004; T009 depends on T008
- **Phase 3 (US1)**: Depends on Phase 2 — tests (T011, T012) can run in parallel with T013–T014; handler migrations (T021–T025) can run in parallel with each other after T009+T020
- **Phase 4 (US3)**: Depends on Phase 2 only — independent of US1 registration flow
- **Phase 5 (US4)**: Depends on Phase 2 (MessageKey/MessageService); depends on T034 completing TaskListKeyboardBuilder before T035/T036
- **Phase 6 (US5)**: Depends on T015 (NotificationService method signature added in US1)
- **Phase 7 (US6+US7)**: Depends on all implementation phases being complete
- **Polish**: Depends on all user story phases

### User Story Dependencies

- **US2 (P1)**: Fully covered by Phase 1 — complete after T001–T003
- **US1 (P1)**: Depends on Phase 2 — blocking foundation for US4 (handler injection pattern)
- **US3 (P2)**: Depends on Phase 2 only — fully independent of US1 registration flow
- **US4 (P2)**: Depends on Phase 2 + T020 (UpdateDispatcher for DELETE_ALL: routing added in T036)
- **US5 (P2)**: Depends on T015 (method signature) from US1
- **US6 (P3)**: Depends on all implementation phases
- **US7 (P3)**: No implementation dependency — can be written any time after scope is finalized

### Parallel Opportunities Within Phases

**Phase 1**: T001 and T002 (two separate migration files)

**Phase 2**: T004 and T005 (different files); T007 independent of T004–T006; T009 after T008

**Phase 3 (US1)**:
- T011 and T012 (two separate test files) — write in parallel
- T013, T014, T015 — can proceed in parallel (different classes)
- T021, T022, T023, T024, T025 — all handler migrations in parallel (different files)

**Phase 5 (US4)**:
- T030, T031, T032 — tests and repository can run in parallel

**Phase 7**: T038 and T039 (different file sets)

---

## Parallel Example: US1 Registration Flow

```
# Write all US1 tests in parallel:
T011 — StartCommandHandlerTest
T012 — LanguageCallbackHandlerTest

# Then, after Phase 2 (foundational) is complete:
# Build service layer in parallel:
T013 — UserService
T014 — UserStateService
T015 — NotificationService additions

# Then build handlers (depend on service layer):
T016 — StartCommandHandler (depends on T013, T015)
T017 — LanguageCallbackHandler (depends on T013, T014, T015)
T018 — TimezoneCallbackHandler (depends on T013, T014)

# Handler migrations (all parallel — different files):
T021  T022  T023  T024  T025  — run all at once
```

---

## Implementation Strategy

### MVP First (US1 + US2 Only)

1. Complete Phase 1: Setup (schema — includes US2)
2. Complete Phase 2: Foundational (i18n layer — CRITICAL)
3. Complete Phase 3: US1 (bilingual registration + all handler migrations)
4. **STOP and VALIDATE**: Full bilingual bot works end-to-end; no hardcoded strings
5. Deploy/demo bilingual MVP

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready (US2 satisfied)
2. Phase 3 → US1 complete → Demo bilingual bot (P1 MVP)
3. Phase 4 → US3 complete → Extended time formats
4. Phase 5 → US4 complete → Bulk delete feature
5. Phase 6 → US5 complete → Live timezone labels
6. Phase 7 → US6+US7 complete → Portfolio-ready docs

### Notes

- `[P]` tasks touch different files — safe to run concurrently
- Each user story is independently testable after its checkpoint
- T036 (UpdateDispatcher DELETE_ALL: routing) must follow T035 (handler class creation)
- T026 (UserStateService migration) must follow T014 (UserStateService update) to avoid conflicts
- Verify `mvn test` passes after Phase 3 before proceeding to Phase 4+
