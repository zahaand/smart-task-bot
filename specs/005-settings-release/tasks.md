# Tasks: Settings Menu and Release 1.0.0

**Input**: `specs/005-settings-release/plan.md`, `specs/005-settings-release/spec.md`
**Branch**: `005-settings-release`
**Constitution**: v1.4.0

## Format: `[ID] [P?] [Story?] Description — file path`

- **[P]**: Parallelizable (different files, no incomplete dependencies)
- **[US1–US4]**: Maps to User Stories in spec.md
- Phase A tasks have no story label — they are foundational blockers

---

## Phase A — Foundation

**Purpose**: No user-visible change. Must be 100% complete before any Phase B–E work begins.

**⚠️ CRITICAL**: All user story phases block on Phase A completion.

- [x] T001 [P] Add `CONFIRMING_DELETE_ACCOUNT` to `ConversationState` enum —
  `src/main/java/ru/zahaand/smarttaskbot/model/ConversationState.java`
- [x] T002 [P] Add 13 new `MessageKey` constants with EN + RU strings: `BTN_SETTINGS`, `SETTINGS_TITLE`,
  `BTN_CHANGE_LANGUAGE`, `BTN_CHANGE_TIMEZONE`, `BTN_RESET_STATE`, `BTN_DELETE_ACCOUNT`, `SETTINGS_LANGUAGE_CHANGED`,
  `SETTINGS_TIMEZONE_CHANGED` (`%s`), `SETTINGS_STATE_RESET`, `SETTINGS_DELETE_CONFIRM_PROMPT`,
  `SETTINGS_ACCOUNT_DELETED`, `SETTINGS_DELETE_CANCELLED`, `TASK_CREATED_WITH_ACTIONS` (`%d`, `%s`) —
  `src/main/java/ru/zahaand/smarttaskbot/model/MessageKey.java`
- [x] T003 [P] Add 7 `CB_SETTINGS_*` constants to `BotConstants` (`CB_SETTINGS_MENU`, `CB_SETTINGS_LANG`,
  `CB_SETTINGS_TZ_REQUEST`, `CB_SETTINGS_RESET`, `CB_SETTINGS_DEL_REQ`, `CB_SETTINGS_DEL_CFM`, `CB_SETTINGS_DEL_CNC`);
  add `@UtilityClass`; remove explicit `private BotConstants()` constructor; delete `@Deprecated TZ_CALLBACK_PREFIX`
  field — `src/main/java/ru/zahaand/smarttaskbot/config/BotConstants.java`
- [x] T004 Update `TimezoneCallbackHandler`: (1) replace `BotConstants.TZ_CALLBACK_PREFIX` → `BotConstants.CB_TZ` in
  `startsWith` check; (2) add context-aware branch after `UserService.updateTimezone()` — if user was already
  registered (timezone non-null before update) send `SETTINGS_TIMEZONE_CHANGED.formatted(tz)` + persistent menu + set
  IDLE, otherwise continue existing registration flow —
  `src/main/java/ru/zahaand/smarttaskbot/handler/callback/TimezoneCallbackHandler.java`
- [x] T005 [P] Add `deleteUser(Long telegramUserId)` to `UserService`: `@Transactional`, call
  `userRepository.deleteById(telegramUserId)`, log INFO on success —
  `src/main/java/ru/zahaand/smarttaskbot/service/UserService.java`
- [x] T006 [P] Write `UserServiceTest` `@Nested` class for `deleteUser()`: test success path (verify `deleteById` called
  with correct id) and missing-user path (verify no exception propagates to handler layer) —
  `src/test/java/ru/zahaand/smarttaskbot/service/UserServiceTest.java`

**Checkpoint**: All enum values, message keys, constants, and service methods are in place. Phase B–E can begin.

---

## Phase B — User Story 1: Settings Menu (Priority: P1) 🎯 MVP

**Goal**: Registered user taps ⚙️ Settings from the persistent menu and can change language, change timezone, reset
state, or delete their account — all actions work end-to-end.

**Independent Test** (from spec.md): Open Settings, tap each of the four inline buttons, verify the appropriate response
is received in the user's language — independently of US2–US4.

- [x] T007 [US1] Create `SettingsCallbackHandler` with all 7 handler methods: `handleMenu()` (send 4-button inline
  keyboard), `handleLanguageChange()` (set IDLE, call `UserService.updateLanguage()`, send `SETTINGS_LANGUAGE_CHANGED` +
  persistent menu), `handleTimezoneRequest()` (set `AWAITING_TIMEZONE`, call
  `notificationService.sendTimezoneKeyboard(chatId, language)`), `handleReset()` (set IDLE, send
  `SETTINGS_STATE_RESET` + persistent menu), `handleDeleteRequest()` (set `CONFIRMING_DELETE_ACCOUNT`, send
  `SETTINGS_DELETE_CONFIRM_PROMPT` with Confirm/Cancel buttons), `handleDeleteConfirm()` (call
  `userService.deleteUser()`, send `SETTINGS_ACCOUNT_DELETED` — do NOT set state after deletion),
  `handleDeleteCancel()` (set IDLE, send `SETTINGS_DELETE_CANCELLED` + persistent menu); use `@RequiredArgsConstructor`;
  declare fields in Constitution VIII order —
  `src/main/java/ru/zahaand/smarttaskbot/handler/callback/SettingsCallbackHandler.java`
- [x] T008 [P] [US1] Update `NotificationService`: (1) add `sendSettingsMenu(Long chatId, Language language)` — sends
  settings message with 4 inline option buttons (`CB_SETTINGS_LANG:EN`, `CB_SETTINGS_LANG:RU`, `CB_SETTINGS_TZ_REQUEST`,
  `CB_SETTINGS_RESET`, `CB_SETTINGS_DEL_REQ`); (2) update `sendPersistentMenu()` to add 3rd keyboard row with
  `BTN_SETTINGS` button — `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java`
- [x] T009 [US1] Update `UpdateDispatcher`: (1) wire `SettingsCallbackHandler` via constructor injection; (2) add
  `CB_SETTINGS_*` routing block in `routeCallback()` before "Unrecognised callback" fallback (use `equals` for all
  except `CB_SETTINGS_LANG` which uses `startsWith`); (3) update `isPersistentMenuButton()` to recognise `BTN_SETTINGS`
  EN + RU; (4) update `routeMenuButton()` — reset state to IDLE before calling
  `notificationService.sendSettingsMenu()`; (5) add `CONFIRMING_DELETE_ACCOUNT` to the free-text rejection guard in
  `routeMessage()` alongside existing blocked states —
  `src/main/java/ru/zahaand/smarttaskbot/handler/UpdateDispatcher.java`
- [x] T010 [P] [US1] Write `SettingsCallbackHandlerTest` with `@ExtendWith(MockitoExtension.class)`; one `@Nested` class
  per method under test (7 total); cover: happy path for each method, stale-state guard (callback arrives while user is
  `CREATING_TASK`), `handleTimezoneRequest()` sets `AWAITING_TIMEZONE` + calls sendTimezoneKeyboard,
  `handleDeleteConfirm()` does NOT call `userStateService.setState()` after deletion; `@DisplayName` on every test
  method as first annotation — `src/test/java/ru/zahaand/smarttaskbot/handler/callback/SettingsCallbackHandlerTest.java`
- [x] T011 [P] [US1] Update `NotificationServiceTest`: add `@Nested` for `sendSettingsMenu()` (verify message sent with
  correct inline keyboard); update any existing persistent-menu test that asserts 2-row keyboard shape → assert 3 rows —
  `src/test/java/ru/zahaand/smarttaskbot/service/NotificationServiceTest.java`
- [x] T012 [P] [US1] Update `TimezoneCallbackHandlerTest`: add `@Nested` for settings-context scenario — user already
  registered (timezone non-null before update), verify `SETTINGS_TIMEZONE_CHANGED` sent and state set to IDLE; verify
  registration flow (timezone was null) still sends standard persistent menu —
  `src/test/java/ru/zahaand/smarttaskbot/handler/callback/TimezoneCallbackHandlerTest.java`

**Checkpoint**: Settings menu is fully functional. A registered user can use all four settings options. Test with:
register → open Settings → exercise each button independently.

---

## Phase C — User Story 2: Task Created with Action Buttons (Priority: P1)

**Goal**: After successful task creation, the confirmation message includes Remind/Complete/Delete inline buttons
allowing immediate action on the newly created task.

**Independent Test** (from spec.md): Create a task via `/newtask` or the New Task button. Confirm the creation message
includes three inline buttons and each performs the correct action on the new task.

- [ ] T013 [P] [US2] Add `sendTaskCreatedWithActions(Long chatId, Long taskId, String taskText, Language language)` to
  `NotificationService`: send confirmation message with `TASK_CREATED_WITH_ACTIONS.formatted(taskId, taskText)` and
  inline keyboard containing `CB_TASK_REMIND`, `CB_TASK_DONE`, `CB_TASK_DELETE` buttons in user's language —
  `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java`
- [ ] T014 [US2] Update `TaskCreationTextHandler`: remove `notificationService.sendMessage(...)` call for task creation
  success; replace with
  `notificationService.sendTaskCreatedWithActions(chatId, created.getId(), created.getText(), user.getLanguage())` —
  `src/main/java/ru/zahaand/smarttaskbot/handler/text/TaskCreationTextHandler.java`
- [ ] T015 [P] [US2] Update `NotificationServiceTest`: add `@Nested` for `sendTaskCreatedWithActions()` — verify correct
  message text with formatted task ID + text, verify inline keyboard contains all three action buttons —
  `src/test/java/ru/zahaand/smarttaskbot/service/NotificationServiceTest.java`
- [ ] T016 [US2] Write or update `TaskCreationTextHandlerTest`: verify `sendTaskCreatedWithActions()` is called with
  correct arguments on success; verify plain `sendMessage()` is no longer called for task creation —
  `src/test/java/ru/zahaand/smarttaskbot/handler/text/TaskCreationTextHandlerTest.java`

**Checkpoint**: Newly created tasks show action buttons. Create a task and immediately tap Remind, Complete, or Delete
to confirm each works identically to the task-list equivalents.

---

## Phase D — User Story 3: Code Quality Cleanup (Priority: P2)

**Goal**: Full Constitution v1.4.0 compliance — deprecated code removed, DTO/utility renames applied, XML comments
cleaned up, commons-lang3 explicit, StringUtils/CollectionUtils used throughout.

**Independent Test** (from spec.md): `mvn test` passes with zero failures; grep confirms no `@Deprecated`, no DTO
without `Dto` suffix, no static-only class without `@UtilityClass`, no `<!-- -->` in Liquibase XMLs, `commons-lang3` in
`pom.xml`.

- [ ] T017 [P] [US3] Confirm `CreateTaskRequest` has zero references outside its own file (run
  `grep -r "CreateTaskRequest" src/`); then delete the file —
  `src/main/java/ru/zahaand/smarttaskbot/dto/CreateTaskRequest.java`
- [ ] T018 [P] [US3] Create `CalendarKeyboardBuilderUtils`: copy `CalendarKeyboardBuilder` content, add `@UtilityClass`,
  make all existing methods `static`, remove `@Component` annotation, remove explicit constructor —
  `src/main/java/ru/zahaand/smarttaskbot/service/CalendarKeyboardBuilderUtils.java`
- [ ] T019 [US3] Update `NotificationService`: remove `CalendarKeyboardBuilder calendarKeyboardBuilder` field and
  constructor parameter; replace all `calendarKeyboardBuilder.buildCalendar()` and
  `calendarKeyboardBuilder.buildHeaderRow()` etc. with static `CalendarKeyboardBuilderUtils.*` calls; delete original
  `CalendarKeyboardBuilder.java` — `src/main/java/ru/zahaand/smarttaskbot/service/NotificationService.java` and
  `src/main/java/ru/zahaand/smarttaskbot/service/CalendarKeyboardBuilder.java`
- [ ] T020 [P] [US3] Update `NotificationServiceTest`: remove `@Mock CalendarKeyboardBuilder calendarKeyboardBuilder`;
  remove it from the `NotificationService` constructor call in `@BeforeEach`; fix any compilation errors from the
  removed parameter — `src/test/java/ru/zahaand/smarttaskbot/service/NotificationServiceTest.java`
- [ ] T021 [P] [US3] Create `TimeParserUtils`: copy `TimeParserService` content, add `@UtilityClass`, make all methods
  `static` (`parse()`, `isTwelveOClockAmbiguous()`, `tryParse()`, `validTime()`), remove `@Service`, remove `@Slf4j` (
  add explicit `private static final Logger log = LoggerFactory.getLogger(TimeParserUtils.class)` only if `log` is used
  in the catch block — Constitution VII) — `src/main/java/ru/zahaand/smarttaskbot/service/TimeParserUtils.java`
- [ ] T022 [US3] Update `ReminderTimeTextHandler`: remove `TimeParserService timeParserService` field and constructor
  injection; replace with static calls `TimeParserUtils.parse(input)` and
  `TimeParserUtils.isTwelveOClockAmbiguous(input)`; delete original `TimeParserService.java` —
  `src/main/java/ru/zahaand/smarttaskbot/handler/text/ReminderTimeTextHandler.java` and
  `src/main/java/ru/zahaand/smarttaskbot/service/TimeParserService.java`
- [ ] T023 [P] [US3] Rename `TimeParserServiceTest` → `TimeParserUtilsTest`: update class name, update all
  `TimeParserService` references to `TimeParserUtils`, remove Spring bean wiring (call static methods directly with
  `@ExtendWith(MockitoExtension.class)`) — `src/test/java/ru/zahaand/smarttaskbot/service/TimeParserUtilsTest.java`
- [ ] T024 [P] [US3] Add `commons-lang3` as an explicit top-level dependency in `pom.xml` with no `<version>` tag (
  version managed by Spring Boot 3.5.11 BOM — 3.17.0) — `pom.xml`
- [ ] T025 [US3] Replace manual string null/blank checks with `StringUtils` equivalents across: `TimeParserUtils.java` (
  `StringUtils.isBlank(input)`, 2×), `TaskService.java` (`StringUtils.isBlank(text)` +
  `StringUtils.length(text) > 500`), `TaskCreationTextHandler.java` (`StringUtils.isBlank(text)`),
  `UpdateDispatcher.java` (`StringUtils.startsWith(text, "/")` + `StringUtils.defaultString(text)`),
  `UserStateService.java` (`StringUtils.isNotBlank(json)`) — multiple files
- [ ] T026 [P] [US3] Audit collection null/empty checks in all service and handler classes: grep for `.isEmpty()` and
  `== null` on List/Collection/Map types; replace with `CollectionUtils.isEmpty()` from `commons-lang3` where
  applicable — multiple files
- [ ] T027 [US3] Run `mvn test`; confirm all tests pass with zero failures and zero skips; fix any regressions before
  proceeding

**Checkpoint**: Zero Constitution v1.4.0 violations. Run `mvn test` green, grep confirms no deprecated code, no manual
null-checks on strings or collections, DTO/utility naming compliant.

---

## Phase E — User Story 4: Release 1.0.0 (Priority: P1)

**Goal**: Project versioned as 1.0.0, deployment descriptor updated, README reflects current feature set, all tests
pass, JAR deploys cleanly on Railway.

**Independent Test** (from spec.md): `mvn clean package` produces `smart-task-bot-1.0.0.jar`; bot starts on Railway;
`/start` command returns correct response.

- [ ] T028 [P] [US4] Bump version in `pom.xml`: `0.0.1-SNAPSHOT` → `1.0.0` — `pom.xml`
- [ ] T029 [P] [US4] Update `railway.toml` `startCommand`: replace `java -jar target/smart-task-bot-*.jar` →
  `java -jar target/smart-task-bot-1.0.0.jar`; verify `[build]` section contains only `buildCommand` and no explicit
  `builder` key (Constitution Dev Std #6) — `railway.toml`
- [ ] T030 [P] [US4] Update `README.md`: add v1.0.0 badge, add Settings menu section describing all four options, update
  feature examples to reflect current bot behaviour — `README.md`
- [ ] T031 [US4] Final Constitution v1.4.0 compliance review across all files modified in this branch: confirm layered
  architecture (no business logic in handlers), no `@Deprecated` annotations, all DTO classes carry `Dto` suffix, all
  static-only helpers use `@UtilityClass` + `Utils` suffix, no `<!-- -->` in Liquibase XMLs, no hardcoded strings
  outside MessageService, constructor injection everywhere, all fields `final`
- [ ] T032 [US4] Run `mvn test`; all tests must pass on the 1.0.0 codebase with zero skipped or failing tests (satisfies
  SC-006)
- [ ] T033 [US4] Run `mvn clean package`; verify the output JAR is named `smart-task-bot-1.0.0.jar` in `target/`
- [ ] T034 [US4] Deploy to Railway; send `/start` to the bot; confirm it responds correctly; verify Railway startup logs
  show no errors (satisfies SC-007)

**Checkpoint**: Release 1.0.0 is live, all acceptance criteria from SC-001 through SC-007 are satisfied.

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase A (Foundation)
    ├── Phase B (US1 — Settings Menu)        depends on A
    ├── Phase C (US2 — Post-Creation Buttons) depends on A; run after B due to shared NotificationService
    ├── Phase D (US3 — Code Quality)          depends on A; run after B + C (NotificationService edits must not conflict)
    └── Phase E (US4 — Release 1.0.0)        depends on B + C + D complete
```

### Within Phase A

- T001, T002, T003, T005, T006 — fully parallel (different files)
- T004 — depends on T003 (needs `CB_TZ` constant from BotConstants)

### Within Phase B

- T007, T008 — parallel (different files; both depend on Phase A)
- T009 — depends on T007 + T008 (wires both into UpdateDispatcher)
- T010, T011, T012 — parallel test tasks (different files; depend on T007/T008 respectively)

### Within Phase C

- T013 — parallel with Phase B tasks (different method in NotificationService, but safest after T008 merges)
- T014 — depends on T013
- T015 — parallel with T014 (different file, depends on T013)
- T016 — depends on T014

### Within Phase D

- T017, T018, T021, T024 — fully parallel (different files)
- T019 — depends on T018 (CalendarKeyboardBuilderUtils must exist)
- T020 — depends on T019 (needs updated NotificationService constructor)
- T022 — depends on T021 (TimeParserUtils must exist)
- T023 — depends on T021
- T025 — depends on T024 (commons-lang3 must be on classpath)
- T026 — parallel with T025 (depends on T024)
- T027 — depends on T017–T026 complete

### Within Phase E

- T028, T029, T030 — fully parallel
- T031 — parallel with T028–T030 (manual review)
- T032 — depends on T028–T031
- T033 — depends on T032
- T034 — depends on T033

---

## Parallel Execution Examples

### Phase A (6 tasks → 2 waves)

```
Wave 1 (parallel): T001, T002, T003, T005, T006
Wave 2 (sequential): T004  ← after T003
```

### Phase B (6 tasks → 3 waves)

```
Wave 1 (parallel): T007, T008
Wave 2 (sequential): T009  ← after T007 + T008
Wave 3 (parallel): T010, T011, T012  ← after T007/T008
```

### Phase D (11 tasks → 4 waves)

```
Wave 1 (parallel): T017, T018, T021, T024
Wave 2 (parallel): T019, T022  ← after T018/T021 respectively
Wave 3 (parallel): T020, T023, T025, T026  ← after T019/T021/T024 respectively
Wave 4 (sequential): T027  ← after all above
```

---

## Implementation Strategy

### MVP (Phase A + B only)

1. Complete Phase A — Foundation
2. Complete Phase B — Settings Menu (US1, P1)
3. **Stop and validate**: all 6 settings scenarios work end-to-end; `mvn test` passes
4. Demo/deploy if desired

### Full Sequential Delivery

1. Phase A → Phase B → Phase C → Phase D → Phase E
2. Run `mvn test` after each phase checkpoint
3. Each phase adds user-visible value or release-readiness without breaking the previous

---

## Notes

- `[P]` tasks operate on different files with no incomplete task dependencies
- `[US1–US4]` labels map to User Stories in `specs/005-settings-release/spec.md`
- Phase A tasks have no story label — they are blocking foundational work
- `handleDeleteConfirm()` (T007): **do NOT call `userStateService.setState(IDLE)`** after `userService.deleteUser()` —
  CASCADE deletes the `user_states` row; a subsequent setState would create a ghost record (CHK006)
- `TimezoneCallbackHandler` context detection (T004): check `user.getTimezone()` non-null before update to distinguish
  settings context from registration context
- `NotificationService` is modified across phases B, C, and D — complete in that order to avoid merge conflicts on the
  same file
- `ReminderTimeTextHandlerTest` may need updating after T022 removes `TimeParserService` injection — check for
  `@Mock TimeParserService` in the test constructor/setup
