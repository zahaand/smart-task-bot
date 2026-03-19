# Plan Compliance Checklist: Quality Improvements and Test Coverage

**Purpose**: Validate implementation plan quality — completeness, consistency, and
traceability against spec.md and Constitution v1.1.0 before implementation begins.
**Created**: 2026-03-19
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)
**Depth**: Gate-level (formal pre-implementation review)
**Audience**: Implementer + reviewer

---

## FR-to-Task Traceability

- [ ] CHK001 — Is FR-009 ("every successful business operation MUST produce an INFO log
  entry") addressed for `getActiveTasks()`? The plan's T010/T011 cover `createTask`,
  `setReminder`, and `completeTask`, but `getActiveTasks()` has no INFO log call
  specified. Is it intentionally excluded, or is this a gap? [Gap, FR-009]

- [ ] CHK002 — Is FR-006 (all handler/service fields MUST be immutable after construction)
  verified by an explicit audit step in the plan, or merely asserted as "already satisfied"
  in the Constitution Check without a corresponding task? [Completeness, FR-006]

- [ ] CHK003 — Is FR-007 (constructor injection only; `@Autowired` on fields PROHIBITED)
  verified by an explicit audit step, or asserted without a verification task?
  The Constitution Check claims compliance but cites no evidence. [Completeness, FR-007]

- [ ] CHK004 — Is the centralized error-handler behavior introduced by T008 (FR-011,
  SC-002) covered by any test plan task? No test class for `SmartTaskBot` appears in
  T015–T020. Is the absence of a `SmartTaskBotTest` intentional? [Gap, FR-011, SC-002]

- [ ] CHK005 — Does the plan provide a task to audit ALL private methods in `handler/`
  and `service/` for Principle VIII's null-return prohibition, beyond the two identified
  (`RemindCommandHandler.getTaskId()`, `DoneCommandHandler.getTaskId()`)? [Completeness,
  Principle VIII]

---

## Spec-Plan Consistency

- [x] CHK006 — Does spec US3 acceptance scenario 6 ("an ERROR is logged" when the retry
  attempt fails) conflict with the existing `ReminderService` code that uses `log.warn()`
  for retry failures? The plan says retry logic MUST NOT be changed, yet the spec scenario
  says ERROR. Is this contradiction resolved? [Conflict, Spec §US3 scenario 6, FR-016]

- [x] CHK007 — Does spec §Edge Cases ("the failure MUST be logged at ERROR level" for
  `processDueReminders()` send failures) conflict with the clarification that the existing
  WARN-level logging in `ReminderService` must not be changed? Both are in the same spec
  document. Which takes precedence? [Conflict, Spec §Edge Cases]

- [ ] CHK008 — Does spec US3 acceptance scenario 3 ("no duplicate is created and **the
  existing record is returned**") contradict `UserService.register()` which is `void` and
  returns nothing? The plan's T016 correctly says "does nothing" but the spec scenario
  implies a return value. [Conflict, Spec §US3 scenario 3]

- [x] CHK009 — Does the plan's T018 test description ("sends error message and **skips
  taskService** for blank task text") contradict the T002 change? After T002, blank input
  flows into `taskService.createTask()` which throws — taskService IS called, not skipped.
  [Conflict, Plan §T018, Plan §T002]

- [x] CHK010 — Does the plan's `railway.toml` `startCommand` hardcode the artifact version
  (`smart-task-bot-0.0.1-SNAPSHOT.jar`) while the spec assumption uses a wildcard
  (`java -jar target/*.jar`)? Is version pinning intentional? [Conflict, Spec §Assumptions,
  Plan §T014]

- [x] CHK011 — Is the plan's log-level decision (research.md Finding #4: WARN for user-input
  errors, ERROR for system failures) reconciled with spec FR-010's literal language ("every
  exception MUST be logged at ERROR level")? The research.md addresses this but the spec
  FR-010 is not updated to reflect the decision. [Conflict, FR-010, Principle VII]

---

## Test Coverage Completeness

- [ ] CHK012 — Is the spec edge case "null argument MUST be rejected with the same outcome
  as blank input" (Spec §Edge Cases) explicitly represented in the test specifications for
  T018 (`NewTaskCommandHandler`), T019 (`RemindCommandHandler`), and T020
  (`DoneCommandHandler`)? These handlers receive input from the Telegram `Update` object
  where `getText()` could return null. [Coverage, Spec §Edge Cases]

- [ ] CHK013 — Is the spec edge case "ERROR MUST still be logged even if no reply can be
  sent" (exception before chatId is known) covered by any test in T015–T020? T008 adds
  the `extractChatId()` fallback, but no test verifies the "chatId unknown → log only,
  no reply" path. [Coverage, Spec §Edge Cases]

- [ ] CHK014 — Does T017 (`ReminderServiceTest`) explicitly cover the scenario where
  `processDueReminders()` is called when no due reminders exist (empty repository result)?
  The current test outline only covers non-empty cases. [Coverage, Edge Case]

- [ ] CHK015 — Is FR-020 (`@ParameterizedTest` + `@MethodSource` for boundary cases)
  applied to specific test methods beyond T015's blank-text test and T018's blank-input
  test? Which methods in T016, T017, T019, T020 are designated as parameterized tests?
  [Clarity, FR-020]

- [ ] CHK016 — Does T019 (`RemindCommandHandlerTest`) explicitly cover the scenario
  where `handle()` receives a task ID that belongs to a different user (NoSuchElementException
  path)? This is spec acceptance scenario 11. The plan lists this as "sends error message
  when task not found" — is the distinction between "not found" and "wrong user" clear?
  [Clarity, Spec §US3 scenario 11]

---

## Constitution Principle Compliance

- [ ] CHK017 — Does the plan's Constitution Check verify that NO other null-returning
  methods exist in handler/ or service/ beyond the two identified? The check claims
  "Curly braces, verb-prefixed methods: ✅ No violations found" but does not explicitly
  state that a full null-return audit was performed on all private methods. [Completeness,
  Principle VIII]

- [ ] CHK018 — Is Principle IX's `@ExtendWith(MockitoExtension.class)` requirement
  explicitly stated as a per-class requirement for each of the 6 test classes (T015–T020),
  or only in the Block 4 intro? If only in the intro, is it clear enough to prevent
  `@SpringBootTest` from being used? [Clarity, Principle IX]

- [ ] CHK019 — Are the `@Slf4j` annotations specified for `SmartTaskBot` (handler layer,
  T008) justified against FR-008 which scopes the annotation requirement to `service/` only?
  Is the plan's decision to add `@Slf4j` to a handler class consistent with the spec?
  [Consistency, FR-008, Principle VII]

---

## Requirement Clarity

- [ ] CHK020 — Is "successful business operation" defined clearly enough in FR-009 to
  determine whether read-only operations like `getActiveTasks()` require an INFO log entry?
  The ambiguity affects whether T010 has a gap. [Ambiguity, FR-009]

- [ ] CHK021 — Is the scope of SC-001 ("zero WARN or ERROR log entries under normal
  configuration") defined beyond the two known WARNs addressed by T001? Does the plan
  define a method to confirm that no other configuration-driven WARNs exist after the
  two changes? [Clarity, SC-001]

- [ ] CHK022 — Is SC-005 ("at least one failure path per public method") measurable for
  `UserService.register()`, where the only alternative to saving a new user is a no-op
  (idempotent return)? Is a no-op considered a "failure path" for the purpose of this
  criterion? [Measurability, SC-005]

- [ ] CHK023 — Is the plan's claimed scope of "25 source files affected (6 modified, 1 new)"
  consistent with the project structure diagram, which lists 5 modified source files
  (`SmartTaskBot`, `NewTaskCommandHandler`, `RemindCommandHandler`, `DoneCommandHandler`,
  `HelpCommandHandler`, `TaskService`, `UserService`, `application.yaml`) — which is
  actually 8? [Clarity, Plan §Technical Context]

---

## Edge Case Coverage in Requirements

- [ ] CHK024 — Does the spec define what happens if `ReminderService.processDueReminders()`
  itself throws an unexpected exception during scheduled execution (e.g., a database
  connection failure)? The scheduler is not wrapped in any error handler. Is this scenario
  in or out of scope? [Gap, Spec §Edge Cases]

- [ ] CHK025 — Are requirements defined for how `SmartTaskBot`'s `extractChatId()` should
  behave when `update.getCallbackQuery().getMessage()` is null (non-message callback)?
  The plan handles `callbackQuery.getMessage() != null` but does not define what happens
  for callback updates without a message. [Coverage, Plan §T008]

---

## Dependencies and Assumptions

- [ ] CHK026 — Is the assumption that Lombok's `@RequiredArgsConstructor` ignores `static`
  fields (making T005's member reorder safe) explicitly documented as a rationale, not just
  stated as a note? Implementers unfamiliar with Lombok internals may not know this.
  [Assumption, Plan §T005]

- [ ] CHK027 — Is the plan's assumption that `spring-boot-starter-test` in Spring Boot
  3.5.11 bundles Mockito 5 and that it supports mocking the full Telegram API
  `Update`/`Message`/`User` chain (all setter-based objects) validated in research.md?
  The research identifies the strategy but does not confirm Mockito version compatibility.
  [Assumption, Plan §Phase 0]

---

## Notes

- Mark items complete with `[x]` as each is reviewed.
- Items marked **[Conflict]** require explicit resolution before implementation begins —
  either update the spec or document the justified deviation.
- Items marked **[Gap]** require a decision: add a task, or explicitly declare out of scope.
- Items marked **[Ambiguity]** can be resolved with a one-line clarification in the spec
  or plan.
- CHK006, CHK007, CHK009, CHK011 are the highest-priority items — they represent
  contradictions within or between spec and plan that could cause incorrect implementation.
