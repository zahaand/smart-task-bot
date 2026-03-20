# Plan Review Checklist: i18n, UX Improvements, and Professional Polish

**Purpose**: Validate implementation plan completeness, constitution compliance, and
spec-to-plan traceability before task generation. Flags both missing coverage and
quality/clarity gaps in the plan's written requirements.
**Created**: 2026-03-20
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)
**Depth**: Thorough | **Audience**: Author (pre-`/speckit.tasks` gate)

---

## Spec-to-Plan FR Coverage

- [x] CHK001 — Is `ReminderService` (which sends push notifications) included in the Phase 4
  handler migration list? FR-004 requires "reminder notifications" to be in the user's
  language, but the plan's Project Structure section does not list `ReminderService` as
  `[MOD]`. [Gap, Spec §FR-004]

- [ ] CHK002 — Is a `HELP_TEXT` (or equivalent) `MessageKey` constant defined for
  `HelpCommandHandler`? The plan lists `HelpCommandHandler` as `[MOD — use MessageService]`
  but no help-text key appears in the `MessageKey` table. Same question applies to
  `DoneCommandHandler` and `RemindCommandHandler`. [Gap, Plan §Phase 4]

- [ ] CHK003 — Is the user isolation predicate for `deleteAllCompleted()` explicitly
  documented in the plan? FR-009 requires that deletion MUST NOT affect other users'
  tasks; the plan specifies `deleteAllCompleted(telegramUserId)` but does not state that
  the repository query is scoped by `telegramUserId`. [Clarity, Spec §FR-009, Plan §Step 5.2]

- [ ] CHK004 — Is the unresolved spec edge case "What happens if `language` is null for
  migrated users?" formally resolved in the plan? The spec lists it as an open edge case;
  migration 005 backfills EN, but the plan does not explicitly close this edge case or
  cross-reference the migration as the resolution. [Gap, Spec §Edge Cases, Plan §Step 1.1]

- [x] CHK005 — Is there a plan step covering `TaskService.countCompleted(telegramUserId)`?
  Step 5.4 references this method call, but Step 5.2 only defines `deleteAllCompleted()`.
  The method must exist before the handler can be implemented. [Gap, Plan §Step 5.2, §Step 5.4]

- [ ] CHK006 — Does the plan cover the `SC-008` verification strategy ("zero hardcoded
  user-visible strings")? No static-analysis or review step is defined for confirming
  that Phase 4 migration is complete across all handlers. [Gap, Spec §SC-008]

---

## Migration Requirements Quality

- [x] CHK007 — Is the rollback for migration 005 safe when mid-registration users exist
  at rollback time? The plan's rollback re-adds `NOT NULL` on `timezone`, but users
  created during `/start` (before language/timezone selection) will have `timezone = null`,
  causing the constraint addition to fail or corrupt data. Is a data-cleanup step or
  precondition specified? [Ambiguity, Plan §Step 1.1]

- [ ] CHK008 — Is the original FK constraint name on `tasks.telegram_user_id` captured
  in migration 006 so the rollback can precisely restore it? The plan says "drop existing
  FK… re-add without cascade" but does not record the original constraint name required
  for an exact rollback. [Clarity, Plan §Step 1.2]

- [ ] CHK009 — Does migration 005 specify the order of operations atomically? The plan
  adds `language` as nullable, backfills, then adds `NOT NULL`. If the backfill step
  fails mid-execution, is the changeSet transactional? Are Liquibase `runInTransaction`
  semantics addressed? [Completeness, Plan §Step 1.1]

- [ ] CHK010 — Is the migration 006 rollback defined with a concrete FK constraint name
  and `references` clause, or is it left as prose ("restore non-cascade FK")? Rollback
  blocks that are only described in natural language are not executable by Liquibase.
  [Clarity, Spec §FR-013, Plan §Step 1.2]

---

## Architecture Decision Clarity

- [x] CHK011 — Is the responsibility boundary between `UserService.createPartialUser()`
  and `UserStateService.initRegistration()` unambiguous? The plan defines both:
  `createPartialUser()` "creates User AND UserState(AWAITING_LANGUAGE)", and
  `initRegistration()` "creates UserState with AWAITING_LANGUAGE". Which one is
  authoritative for UserState creation? Overlapping responsibility risks double-creation
  or divergence. [Conflict, Plan §Step 3.1, §Step 3.2]

- [x] CHK012 — Is the routing path for `CB_DELETE_ALL_REQUEST` defined in the
  `UpdateDispatcher` update? Step 3.7 routes `CB_DELETE_ALL_CONFIRM` and
  `CB_DELETE_ALL_CANCEL`, but Step 5.3 introduces `CB_DELETE_ALL_REQUEST` as the
  actual button callback that triggers the confirmation prompt. `CB_DELETE_ALL_REQUEST`
  is absent from the Step 3.7 routing table. [Conflict, Plan §Step 3.7, §Step 5.3]

- [ ] CHK013 — Is the package placement of `MessageKey.java` in the `service` package
  justified? `MessageKey` is a pure enum with no Spring dependencies — placing it in
  `service` rather than `model` may conflict with Constitution Principle III (SRP) if
  the service package is expected to contain only Spring beans. [Ambiguity, Plan §Step 2.1,
  Constitution §III]

- [ ] CHK014 — Is `userService.findById()` defined or confirmed to exist in the plan?
  Step 3.5 (`TimezoneCallbackHandler` update) and Step 5.4 both call `userService.findById(userId)`,
  but this method is not listed in Step 3.1's `UserService` updates. If it must be added,
  is it included in the plan? [Gap, Plan §Step 3.1, §Step 3.5]

---

## Registration Flow Scenario Coverage

- [ ] CHK015 — Is the behaviour for a mid-registration user (User exists, language set,
  timezone null) who sends a command OTHER than a callback (e.g., `/newtask`) fully
  specified in the plan? The `RegistrationGuard` update (Step 3.6) covers this case, but
  it is not explicitly tested — no test class for `RegistrationGuard` appears in Phase 6.
  [Gap, Plan §Step 3.6, §Phase 6]

- [ ] CHK016 — Is the `LanguageCallbackHandler` error path specified when the callback
  arrives for a user who does not exist in the database (e.g., `lang:EN` sent without
  a prior `/start`)? The plan's pseudocode does not handle the missing-user case.
  [Gap, Plan §Step 3.4]

- [ ] CHK017 — Is the behaviour specified when `StartCommandHandler` encounters a user
  who is mid-registration (User exists but not fully registered)? Step 3.3 says
  "RegistrationGuard handles this case" without defining how `StartCommandHandler` detects
  or delegates this path. The handler's branching logic has three cases but only two are
  spelled out. [Clarity, Plan §Step 3.3]

---

## Constitution Compliance

- [ ] CHK018 — Does the plan's Constitution Check table include evidence that Principle VII
  (Logging) is enforced for ALL new handlers and services, not just "new handlers use
  `@Slf4j`"? Specifically: are `log.error()` before throw, and `log.warn()` for invalid
  language codes, specified in the pseudocode? [Completeness, Plan §Constitution Check,
  Constitution §VII]

- [ ] CHK019 — Does the plan document that `NotificationService` changes (Step 3.9) do
  not introduce business logic into the service, consistent with Constitution Principle I?
  The timezone button label computation (`ZonedDateTime.now(ZoneId.of(tz))`) inside
  `NotificationService` is presentation logic — is this placement explicitly justified,
  or should it live in a dedicated `TimezoneKeyboardBuilder`? [Ambiguity, Plan §Step 3.9,
  Constitution §I]

- [ ] CHK020 — Are Constitution Principle VIII style rules (constructor-after-fields,
  ≤1 blank line, `@DisplayName` first on test methods, `.thenReturn()` on new line,
  `@Nested` without `@DisplayName`) specified as acceptance criteria for ALL new and
  modified classes in this plan, not only listed once in the Constitution Check table?
  [Completeness, Plan §Phase 6, Constitution §VIII]

---

## Testing Requirements Quality

- [ ] CHK021 — Is a test for `UserService` new methods (`createPartialUser`,
  `isRegistered`, `isLanguagePending`, `isTimezonePending`, `updateLanguage`,
  `updateTimezone`) included in Phase 6? These are the core domain logic of the
  registration flow but do not appear in the test class list. [Gap, Plan §Phase 6]

- [ ] CHK022 — Is `TimezoneCallbackHandlerTest` listed as `[MOD]` in Phase 6? The handler
  is substantially rewritten (removes user-creation logic, now requires a pre-existing
  User) but no test update is mentioned. [Gap, Plan §Phase 6]

- [ ] CHK023 — Does the `MessageServiceTest` plan specify that ALL `MessageKey` enum
  constants are tested for both EN and RU variants? Step 2.3 says "Covers all MessageKey
  values" but this is not quantified — with ~22 constants × 2 languages, a missing key
  is easy to overlook. Is a `@ParameterizedTest` over all enum values required?
  [Clarity, Plan §Step 2.3]

---

## Notes

- Items marked `[Conflict]` indicate internal contradictions within the plan that must be
  resolved before task generation.
- Items marked `[Gap]` indicate requirements present in the spec not addressed in the plan.
- Items marked `[Ambiguity]` indicate plan language that is unclear enough to produce
  divergent implementations.
- Priority order for resolution: CHK012 (routing conflict) → CHK011 (service overlap) →
  CHK001 (ReminderService gap) → CHK007 (migration rollback safety) → CHK005 (missing method).
