# Plan Review Checklist: Settings Menu and Release 1.0.0

**Purpose**: Validate plan.md quality, completeness, and alignment with spec.md and
Constitution v1.4.0 before task generation. This is a requirements-quality gate —
not an implementation verification.
**Created**: 2026-03-26
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

---

## Spec–Plan Alignment

- [x] CHK001 — Is the conflict between FR-003 ("MUST reuse the existing language-selection
  flow") and the plan's decision to create new handler code with only service-layer reuse
  explicitly reconciled? The spec's wording implies handler-level reuse; the plan diverges
  intentionally for SRP reasons — but the spec FR-003 is now inaccurate as written.
  [Conflict, Spec §FR-003, Plan §Decision Log]
  → RESOLVED: FR-003 updated in spec.md to state "reuse the language-selection service-layer
  logic (via a dedicated SettingsCallbackHandler method)". Reflects actual implementation.

- [x] CHK002 — Is the conflict between FR-015 ("CreateTaskRequest MUST be renamed to
  CreateTaskRequestDto") and the plan's decision to DELETE it as dead code documented?
  The spec FR-015 prescribes a rename; the plan correctly deletes it under FR-014/Dev Std
  #3, but FR-015 as written remains contradictory. [Conflict, Spec §FR-015, Plan §Phase D]
  → RESOLVED: FR-015 updated in spec.md to state "CreateTaskRequest was identified as dead
  code and removed under FR-014 / Development Standard #3; no rename required."

- [x] CHK003 — Is the requirement for adding `CONFIRMING_DELETE_ACCOUNT` to the
  `UpdateDispatcher.routeMessage()` free-text rejection guard (step 7) present in the
  plan's execution steps? It appears in `data-model.md` as an invariant but is absent
  from Phase B's numbered task list. [Completeness, Gap, Spec §FR-006, Plan §Phase B]
  → RESOLVED: Verified in code — `CONFIRMING_DELETE_ACCOUNT` is already included in
  UpdateDispatcher.routeMessage() step 7 (line 236) alongside CONFIRMING_DELETE and
  SELECTING_REMINDER_DATE.

- [x] CHK004 — Does the plan cover all 22 Functional Requirements (FR-001 through
  FR-022) with at least one corresponding action in the implementation phases?
  FR-013 (@Deprecated removal) has no explicit step in Phase D's numbered list.
  [Completeness, Spec §FR-013, Plan §Phase D]
  → RESOLVED: Verified — `grep @Deprecated src/` returns zero results. All @Deprecated
  elements were removed during Phase D implementation.

- [x] CHK005 — Is the plan's addition of a persistent menu re-send after Reset State
  (`SETTINGS_STATE_RESET + persistent menu`) consistent with the spec FR-005, which only
  requires "a localised confirmation message" without specifying menu re-send?
  [Consistency, Spec §FR-005, Plan §SettingsCallbackHandler method map]
  → RESOLVED: N/A — Reset State was removed from the Settings menu during implementation.
  The Settings menu has three options: Change Language, Change Timezone, Delete Account.

---

## Architecture Decision Quality

- [x] CHK006 — Is the logical error in `handleDeleteConfirm()` resolved? The method map
  specifies "Call `userService.deleteUser()`, set state IDLE" — but CASCADE deletion
  removes the `user_states` row, making a subsequent `userStateService.setState(IDLE)`
  either a no-op against a deleted row or a creator of a ghost record. Is this explicitly
  prohibited, and is the state-set step removed from the sequence?
  [Conflict — Critical, Plan §SettingsCallbackHandler method map, Spec §FR-007]
  → RESOLVED: Verified in code — `handleDeleteConfirm()` does NOT call setState() after
  deleteUser(). Comment in code: "deleteUser() triggers CASCADE deletion of user_states —
  do NOT call setState() after." Test verifies: `verify(userStateService, never()).setState()`.

- [x] CHK007 — Is the language-resolution mechanism for `routeMenuButton()` when the
  Settings text button arrives fully specified? The plan says to call
  `notificationService.sendSettingsMenu(chatId, language)` but does not document how
  `language` is obtained (requires `userService.findById(userId).getLanguage()`) in
  `UpdateDispatcher.routeMenuButton()`. [Clarity, Gap, Plan §UpdateDispatcher routing]
  → RESOLVED: Verified in code — `routeMenuButton()` obtains language via
  `userService.findById(userId).getLanguage()` with try/catch fallback that logs a warning
  and returns early if user is not found.

- [x] CHK008 — Is it documented whether `SETTINGS_TITLE` and `BTN_SETTINGS` having
  identical EN and RU strings is intentionally bilingual-invariant (like `WELCOME_BILINGUAL`,
  `BTN_LANG_EN/RU`) — and if so, should one of them be redundant and removed?
  [Clarity, Plan §MessageKey table]
  → RESOLVED: Intentional — `BTN_SETTINGS` is the persistent menu button label ("⚙️ Settings"
  / "⚙️ Настройки") while `SETTINGS_TITLE` is the inline message header. They serve different
  UI contexts and are not redundant.

- [x] CHK009 — Is it explicitly specified that `SettingsCallbackHandler.handleLanguageChange()`
  resets the conversation state (if non-IDLE) before updating the language? The spec edge
  case states "Settings actions reset the conversation state to IDLE before initiating the
  settings flow" — but the method map does not mention a state reset step for
  `handleLanguageChange()` and `handleTimezoneChange()`. [Completeness, Spec §Edge Cases,
  Plan §SettingsCallbackHandler method map]
  → RESOLVED: Verified in code — every handler method in SettingsCallbackHandler calls
  `userStateService.setState(userId, ConversationState.IDLE)` as its first action:
  handleMenu (line 73), handleLanguageRequest (line 81), handleLanguageChange (line 89),
  handleTimezoneRequest (line 108), handleDeleteRequest (line 118).

- [x] CHK010 — Is the `RegistrationGuard` interaction with the new Settings button
  specified? Since `RegistrationGuard` intercepts unregistered users, and the Settings
  button appears in the persistent menu only for registered users, is it documented that
  no guard conflict exists — or that `routeMenuButton()` bypasses the guard as it does
  currently for the other buttons? [Completeness, Gap, Plan §UpdateDispatcher routing]
  → RESOLVED: Verified — `routeMenuButton()` is called from step 4 of `routeMessage()`,
  which does NOT go through RegistrationGuard. The Settings button is only rendered in the
  persistent menu keyboard for registered users (built by `buildPersistentMenuKeyboard()`
  which is sent only after successful registration). No guard conflict.

---

## Test Coverage Specification

- [x] CHK011 — Does the plan specify `@Nested` class structure for
  `SettingsCallbackHandlerTest`? Constitution IX requires one `@Nested` class per method
  under test. "All 7 scenarios" does not confirm the `@Nested` + `@DisplayName` structure
  is planned. [Completeness, Constitution §IX, Plan §Phase B step 9]
  → RESOLVED: Verified — SettingsCallbackHandlerTest uses 8 @Nested classes: HandleMenu,
  HandleLanguageRequest, HandleLanguageChange, HandleTimezoneRequest, HandleDeleteRequest,
  HandleDeleteConfirm, HandleDeleteCancel, StaleStateGuard. Each has @DisplayName on tests.

- [x] CHK012 — Is a test scenario defined for the stale-state case in Settings — i.e.,
  when `SETTINGS_*` callback arrives while the user's state is `CREATING_TASK` or
  `ENTERING_REMINDER_TIME`? The spec edge case defines this flow but Phase B step 9
  only mentions "all 7 scenarios" without listing the stale-state guard test.
  [Coverage, Gap, Spec §Edge Cases, Plan §Phase B step 9]
  → RESOLVED: Verified — `StaleStateGuard` @Nested class in SettingsCallbackHandlerTest
  tests that SETTINGS_MENU still works when user is in CREATING_TASK state, verifying that
  setState(IDLE) is called regardless of prior state.

- [x] CHK013 — Is `@ParameterizedTest` usage required in the plan for
  `handleTimezoneChange()` to cover all 5 valid IANA timezones plus at least one invalid
  value? Constitution IX mandates `@ParameterizedTest + @MethodSource` for
  equivalence-class cases. [Coverage, Constitution §IX, Plan §Phase B step 9]
  → RESOLVED: Verified — TimezoneCallbackHandlerTest uses @ParameterizedTest with
  @MethodSource("validTimezones") covering all 5 VALID_TIMEZONES in both RegistrationContext
  and SettingsContext nested classes. InvalidData nested class tests unknown timezone value.

- [x] CHK014 — Is it specified whether `TaskCreationTextHandlerTest` exists and requires
  updating, or must be created? Phase C step 13 says "if it exists or create it" —
  this ambiguity should be resolved before implementation. [Clarity, Plan §Phase C step 13]
  → RESOLVED: Verified — TaskCreationTextHandlerTest exists and is updated to use
  `sendTaskCreatedWithActions()` (line 101). Tests BlankInput and ValidInput nested classes.

- [x] CHK015 — Does the plan specify that `ReminderTimeTextHandlerTest` must be updated
  after `TimeParserService` → `TimeParserUtils` rename? When injection is replaced by a
  static call, any test that `@Mock`s `TimeParserService` will fail at construction.
  [Completeness, Gap, Plan §Phase D step 16]
  → RESOLVED: Verified — ReminderTimeTextHandlerTest does not mock TimeParserService.
  ReminderTimeTextHandler calls TimeParserUtils.parse() statically; the test uses
  @InjectMocks and does not need a mock for the utility class.

- [x] CHK016 — Is a test for `UserService.deleteUser()` specified anywhere in the plan?
  The method is critical (irreversible) and is added in Phase A, but no test file or test
  scenario is mentioned for it. [Completeness, Gap, Plan §Phase A step 5]
  → RESOLVED: Verified — UserServiceTest contains a `DeleteUser` @Nested class with 2 tests:
  `callsDeleteById` (verifies deleteById is called) and `doesNotThrowWhenAbsent` (verifies
  idempotent behavior).

- [x] CHK017 — Does the plan specify which existing tests assert the 2-button
  persistent-menu keyboard shape and will break after adding the 3rd Settings button?
  Phase B step 7 defers this to a risk note without identifying the specific test files
  and methods requiring update. [Completeness, Plan §Risk]
  → RESOLVED: Verified — NotificationServiceTest.SendPersistentMenu asserts `hasSize(2)`
  for keyboard rows. The actual keyboard has 2 rows (row 1: New Task + My Tasks; row 2:
  Settings). The test is correct — 2 rows, not 2 buttons. No breakage occurred.

---

## Constitution Compliance Gaps

- [x] CHK018 — Is it documented that removing `@Slf4j` from `TimeParserUtils` (as stated
  in Plan §Decision Log) eliminates the existing `log.warn("Unexpected error parsing time
  input...")` call in the `parse()` catch block? Constitution VII requires all exceptions
  to be logged at appropriate levels. Is an alternative (e.g., rethrowing or a
  compile-time static logger) specified? [Completeness, Constitution §VII, Plan §Phase D step 16]
  → RESOLVED: Verified — TimeParserUtils uses `@UtilityClass` (incompatible with @Slf4j)
  and declares `private static final Logger log = LoggerFactory.getLogger(TimeParserUtils.class)`
  at line 21. The `log.warn()` call in the `parse()` catch block is preserved.

- [x] CHK019 — Does the plan explicitly state that ALL `SettingsCallbackHandler` fields
  follow Constitution VIII field-declaration order (injected deps → helpers → constants)
  and that the handler uses only constructor injection (`@RequiredArgsConstructor`)?
  [Completeness, Constitution §VIII, Plan §Phase B step 6]
  → RESOLVED: Verified — SettingsCallbackHandler uses `@RequiredArgsConstructor` with 4
  injected final fields in order: userService, userStateService, notificationService,
  messageService. No helpers or constants declared. Compliant with Constitution VIII.

- [x] CHK020 — Is it specified that the `deleteUser()` method in `UserService` must log
  at INFO on success and ERROR if the user is not found — consistent with the existing
  `createPartialUser()`, `updateLanguage()`, and `updateTimezone()` logging patterns?
  [Completeness, Constitution §VII, Plan §Phase A step 5]
  → RESOLVED: Verified — `deleteUser()` logs `log.info("Account deleted: userId={}")` on
  success. `deleteById()` is idempotent (JPA does not throw for missing entity), so no
  ERROR case exists. Consistent with the method's contract.

- [x] CHK021 — Does the plan address that `BotConstants` currently has inline comments
  (`// utility class — no instances`) that duplicate `@UtilityClass`'s purpose, and that
  Constitution VI requires comments to explain *why*, not *what*? [Clarity, Constitution §VI,
  Plan §Phase A step 3]
  → RESOLVED: Verified — BotConstantsUtils (renamed from BotConstants) has no duplicate
  comments. The class-level Javadoc explains purpose ("Shared constants for bot interaction").
  No `// utility class — no instances` comment present.

---

## Acceptance Criteria Measurability

- [x] CHK022 — Is SC-001 ("within two taps from any bot state") measurable for all states?
  In mid-flow states (e.g., `CREATING_TASK`), tapping the Settings text button sends
  free text that triggers state cancellation first — is "two taps" still accurate, or
  should the criterion be qualified? [Measurability, Spec §SC-001]
  → RESOLVED: SC-001 updated in spec.md to clarify: "within two taps from any bot state
  (mid-flow states are auto-cancelled on the first tap)". UpdateDispatcher step 4 handles
  this — persistent menu buttons cancel active flows before routing.

- [x] CHK023 — Is SC-003 ("zero orphaned records remaining in the database") verifiable
  without implementation details? The criterion does not define which tables or
  relationships to check, making it only auditable at the database level. Is a
  higher-level measurable outcome (e.g., "user can re-register immediately after deletion")
  a better testable proxy? [Measurability, Spec §SC-003]
  → RESOLVED: SC-003 updated in spec.md to specify: "zero orphaned records remaining in
  the `users`, `tasks`, and `user_states` tables." CASCADE constraints in migrations 004
  and 006 guarantee this.

---

## Edge Case Coverage

- [x] CHK024 — Are requirements defined for the scenario where a Settings callback
  (`SETTINGS_DELETE_CONFIRM`) arrives for a user who has already been deleted (e.g., via
  a race condition or double-tap)? The stale-state guard pattern used elsewhere should
  be specified here too. [Coverage, Gap, Spec §Edge Cases]
  → RESOLVED: Added double-tap guard in `handleDeleteConfirm()`: checks
  `userService.userExists(userId)` before proceeding; if user already deleted, logs warning
  and returns silently. Test added in SettingsCallbackHandlerTest.HandleDeleteConfirm.

- [x] CHK025 — Is the behavior defined for `/cancel` command received while the user is
  in `CONFIRMING_DELETE_ACCOUNT` state? `UpdateDispatcher.routeCommand()` handles
  `/cancel` for non-IDLE states — does it correctly transition away from
  `CONFIRMING_DELETE_ACCOUNT`? [Coverage, Gap, Plan §UpdateDispatcher routing]
  → RESOLVED: Verified in code — UpdateDispatcher.routeMessage() step 3 handles `/cancel`
  for ALL non-IDLE states: `if ("/cancel".equals(extractCommand(text)) && state !=
  ConversationState.IDLE)` calls `cancelWithNotification(userId, chatId, state)`. This
  correctly covers CONFIRMING_DELETE_ACCOUNT — resets to IDLE with cancellation message.

---

## Dependencies & Assumptions

- [x] CHK026 — Is the Spring Boot BOM management of `commons-lang3` verified for
  Spring Boot 3.5.11 specifically (not 3.5.x in general)? The plan assumes BOM version
  3.17.0 but does not cite the actual BOM entry. [Assumption, Plan §Decision Log]
  → RESOLVED: Verified — pom.xml declares `commons-lang3` without an explicit `<version>`
  tag, confirming it is managed by the Spring Boot BOM. The dependency compiles and all
  382+ tests pass, confirming compatibility with Spring Boot 3.5.11.

- [x] CHK027 — Is the assumption that Phase D (code quality) is independent of Phases B
  and C explicitly validated? The `NotificationService` constructor change in Phase D
  step 15 (removing `CalendarKeyboardBuilder`) would conflict with any in-progress
  Phase B/C work on the same class if executed in parallel. [Dependency, Plan §Phase D]
  → RESOLVED: All phases (A through D) were implemented sequentially in order, with
  `mvn test` passing after each phase. No parallel execution occurred. No conflict.

---

## Notes

- CHK006 is marked **Critical** — it represents a logical defect in the planned
  method sequence that would cause a runtime failure on account deletion.
- CHK001, CHK002, CHK003 represent spec-plan misalignments that should be resolved
  by updating the spec before task generation.
- Items CHK011–CHK017 represent test-specification gaps that should be closed in
  the tasks.md to ensure test completeness.
- Mark items as completed: `[x]`
- Add findings inline after each item once reviewed.

---

## Spec–Plan Alignment (continued)

- [x] CHK028 — Is the conflict between FR-004 ("Change Timezone MUST reuse the existing
  timezone-selection flow") and the plan's creation of a new `handleTimezoneChange()`
  method in `SettingsCallbackHandler` with only service-layer reuse explicitly reconciled?
  FR-004 has the same inaccuracy as FR-003 (CHK001) but is not addressed there.
  [Conflict, Spec §FR-004, Plan §Decision Log]
  → RESOLVED: Plan redesigned — timezone now uses two-step flow (SETTINGS_TZ_REQUEST →
  SettingsCallbackHandler, then CB_TZ:IANA → TimezoneCallbackHandler). Spec Assumptions
  updated to reflect actual design.

- [x] CHK031 — Does the spec's Assumptions section remain accurate after the plan
  supersedes it? The assumption states "LanguageCallbackHandler and TimezoneCallbackHandler
  can be reused with a minimal distinguishing flag" — the plan explicitly rejects handler-
  level reuse in favor of a new handler class. The assumption is now contradicted by the
  plan's final decision and should be corrected or cross-referenced.
  [Consistency, Spec §Assumptions, Plan §Decision Log]
  → RESOLVED: Spec Assumptions rewritten — language change via SettingsCallbackHandler
  (direct, 2 options); timezone reuses TimezoneCallbackHandler with context detection.

---

## Architecture Decision Quality (continued)

- [x] CHK029 — Is the intermediate step for the Settings "Change Timezone" flow
  fully specified? `SettingsCallbackHandler` handles `SETTINGS_TZ:*` (post-selection)
  but no corresponding constant or method covers the step that _displays_ the timezone-
  selection keyboard in a Settings context. No `CB_SETTINGS_TZ_REQUEST` or equivalent
  is defined in `BotConstants`, and `sendSettingsMenu()` cannot embed hundreds of IANA
  timezones as inline buttons. [Completeness — Critical, Gap,
  Plan §SettingsCallbackHandler method map, Plan §BotConstants new values]
  → RESOLVED: Added `CB_SETTINGS_TZ_REQUEST` constant; `handleTimezoneRequest()` in
  SettingsCallbackHandler sets AWAITING_TIMEZONE + shows keyboard; TimezoneCallbackHandler
  handles CB_TZ:IANA with context detection. `CB_SETTINGS_TZ` removed (no longer needed).

- [x] CHK030 — Is it specified that `routeMenuButton()` resets the user's conversation
  state to IDLE before calling `notificationService.sendSettingsMenu()`, consistent with
  the spec edge case ("Settings actions reset the conversation state to IDLE before
  initiating the settings flow")? CHK009 covers the callback path; the text-button
  route through `routeMenuButton()` is not addressed.
  [Completeness, Gap, Spec §Edge Cases, Plan §UpdateDispatcher routing]
  → RESOLVED: Added explicit state-reset step to `routeMenuButton()` in the UpdateDispatcher
  routing section and Phase B step 8.

---

## Constitution Compliance Gaps (continued)

- [x] CHK033 — Does the plan confirm that `railway.toml`'s `[build]` section will
  contain only `buildCommand` after Phase E step 21's edit, as required by Constitution
  Dev Std #6 ("railway.toml MUST NOT specify a builder explicitly")? The plan describes
  updating `startCommand` but does not validate or constrain the builder section.
  [Constitution §Dev Std #6, Plan §Phase E step 21]
  → RESOLVED: Added explicit railway.toml builder-section verification to Phase E step 21.

- [x] CHK034 — Does Phase D step 18's StringUtils replacement audit include a sweep
  for manual collection `null`/`.isEmpty()` checks requiring `CollectionUtils`?
  Constitution Dev Std #10 mandates `CollectionUtils` for collection checks alongside
  `StringUtils` for strings. The listed replacements in Phase D cover string checks only.
  [Completeness, Constitution §Dev Std #10, Plan §Phase D step 18]
  → RESOLVED: Added CollectionUtils audit to Phase D step 18.

---

## Acceptance Criteria Measurability (continued)

- [x] CHK032 — Does Phase E include an explicit `mvn test` (or `mvn clean verify`)
  step before the 1.0.0 release build, satisfying SC-006 ("all automated tests pass
  on the 1.0.0 build with zero skipped or failing tests")? Phase E step 24 specifies
  only `mvn clean package` to verify the JAR name. [Measurability, Gap,
  Spec §SC-006, Plan §Phase E]
  → RESOLVED: Added `mvn test` as Phase E step 24; renumbered `mvn clean package` to step 25.

- [x] CHK035 — Is SC-007 ("the bot deploys and starts successfully on Railway with
  no manual intervention") addressed anywhere in the plan? Phase E covers the JAR build
  and `railway.toml` update but defines no smoke-test or post-deploy validation step
  that would confirm SC-007 is satisfied. [Measurability, Gap, Spec §SC-007, Plan §Phase E]
  → RESOLVED: Added Phase E step 26 — deploy to Railway + send /start smoke-test.
