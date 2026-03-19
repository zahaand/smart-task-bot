# Plan Quality Checklist: Button-Driven UX Improvements

**Purpose**: Validate implementation plan completeness, spec traceability, and constitution compliance before task generation
**Created**: 2026-03-19
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)
**Scope**: plan.md vs spec.md (FR-001–FR-019, US-1–US-6) and Constitution v1.1.1

---

## Plan-to-Spec Traceability

- [ ] CHK001 — Is every functional requirement (FR-001 through FR-019) explicitly mapped to at least one named deliverable in the Implementation Phases? Currently FR-018 (timezone live time) maps to Phase 4 step 30 and FR-019 (IDLE unknown input hint) maps to Phase 2 step 18, but the mapping is implicit — are all 19 FRs traceable by requirement ID? [Traceability, Plan §Implementation Phases]

- [ ] CHK002 — Is the behavior of the `⏰ Напоминание` persistent menu button (routes to active task list, not a standalone reminder flow) explicitly documented in the plan, not only in the spec Assumptions? [Completeness, Spec §Assumptions, Plan §Architecture Deep-Dive]

- [ ] CHK003 — Is there a plan deliverable that covers the "IDLE state unknown text" response defined in FR-019 and the Edge Cases section? The plan mentions `UnknownInputHandler` modification in Phase 2 step 18, but the step description ("update message to hint at menu buttons") does not reference FR-019 explicitly. [Traceability, Spec §FR-019]

- [x] CHK004 — Does the plan address the acceptance scenario from US-2 AC-4: "Given a user in CREATING_TASK state, When they send a command (e.g., /cancel), Then the bot cancels task creation"? The plan's `routeMessage` routes to `TaskCreationTextHandler` at step 4 *before* reaching the command switch at step 7 — are commands during non-IDLE states explicitly defined as interrupting the active flow? [Gap, Spec §US-2 AC-4, Plan §Architecture Deep-Dive] ✅ Fixed: routing diagram reordered — `/cancel` checked at step 3 before any state handler; all commands bypass state handlers via new step 3 isCommand() check.

- [x] CHK005 — Is the `TASKS_TAB:` callback prefix and the `NO_OP` callback included in FR-010a? Currently FR-010a lists `CAL_DATE`, `CAL_NAV`, `TASK_REMIND`, `TASK_DONE`, `TASK_DELETE`, `CONFIRM_DELETE`, `CONFIRM_CANCEL` but omits `TASKS_TAB:` and `NO_OP`. The plan and contract add both — is this an unresolved inconsistency between spec and plan? [Conflict, Spec §FR-010a, Plan contracts/callback-data-contract.md] ✅ Fixed: FR-010a in spec.md updated — `TASKS_TAB:<ACTIVE|COMPLETED>` and `NO_OP` added to the complete callback prefix list.

- [ ] CHK006 — Does the plan cover the spec's success criterion SC-002 (zero regression on all 5 legacy commands)? The plan includes backward compat in the architecture diagram, but is there a dedicated regression test step in Phase 5? [Traceability, Spec §SC-002]

---

## State Machine Completeness

- [ ] CHK007 — Are all five `ConversationState` values fully specified for *both* the happy-path exit *and* the interruption exit? The data-model.md diagram shows happy-path transitions; the interruption rule is stated as a note. Is the state diagram complete for all cancellation paths (e.g., CONFIRMING_DELETE → IDLE via "Отмена", SELECTING_REMINDER_DATE → IDLE via stale timeout)? [Completeness, Plan §data-model.md]

- [x] CHK008 — Is the behavior defined when a user in `CONFIRMING_DELETE` or `SELECTING_REMINDER_DATE` state sends free text (not a menu button, not a command)? The `routeMessage` routing diagram only explicitly handles CREATING_TASK (step 4) and ENTERING_REMINDER_TIME (step 5). Free text during `CONFIRMING_DELETE` or `SELECTING_REMINDER_DATE` would fall through to `UnknownInputHandler` — is this the intended, documented behavior? [Gap, Plan §Architecture Deep-Dive] ✅ Fixed: step 7 added to routing diagram — free text in CONFIRMING_DELETE/SELECTING_REMINDER_DATE → "Пожалуйста, используй кнопки выше.", stay in current state.

- [ ] CHK009 — Is the `NO_OP` callback handling included in the Phase 1 implementation step for `UpdateDispatcher`? The architecture deep-dive defines `CB_NO_OP` and its silent answer behavior, but Phase 1 step 6 only lists "routeCallback + routeMessage extraction; stale reset call; persistent menu button detection" — `NO_OP` is absent. [Completeness, Plan §Phase 1 step 6]

- [ ] CHK010 — Is the stale-state lazy reset documented as applying to **both** `routeMessage` and `routeCallback` paths? The plan's routing diagram calls `userStateService.resetIfStale(userId)` only at the top of `routeMessage`. The research.md says "call in both routeMessage and routeCallback" — is this requirement present in the plan's implementation phases? [Consistency, Plan §research.md Decision 8 vs Architecture Deep-Dive]

- [ ] CHK011 — Does the plan specify what happens when `CalendarCallbackHandler` receives a `CAL_NAV` or `CAL_DATE` callback but the user's current state is *not* `SELECTING_REMINDER_DATE` (e.g., state was reset to IDLE after 24h stale)? The Key Risks section mentions this scenario for calendar — is the handler's defensive check (verify state before processing) specified as a requirement in the plan? [Coverage, Plan §Key Risks]

---

## Architecture & SRP Requirements

- [x] CHK012 — Is the `NotificationService` SRP boundary clearly defined given 8+ new methods are added (`sendPersistentMenu`, `answerCallbackQuery`, `sendTaskList`, `editTaskList`, `sendCalendar`, `editCalendar`, `sendDeleteConfirmation`, `editMessage`)? Can its responsibility still be described in one sentence per Constitution §III? Is this explicitly addressed in the plan? [Clarity, Constitution §III, Plan §Constitution Check] ✅ Fixed: Constitution Check §III updated — all methods share one cohesive responsibility ("send or edit Telegram messages on behalf of the bot"), no data persistence or domain logic inside.

- [ ] CHK013 — Does the plan specify which layer is responsible for `answerCallbackQuery` calls? The callback-data-contract.md says every handler MUST call it, which means handlers would call both `notificationService.answerCallbackQuery()` AND a business service. Is calling two services from one handler consistent with Constitution §I (handler = pure router) and the established pattern in `TimezoneCallbackHandler`? [Consistency, Constitution §I, contracts/callback-data-contract.md]

- [ ] CHK014 — Is the package placement of `CalendarKeyboardBuilder` and `TaskListKeyboardBuilder` under `service/` clearly justified? These classes build Telegram `InlineKeyboardMarkup` objects — is this "business logic" (correct for `service/`) or "presentation/notification logic" (should be co-located with `NotificationService`)? The plan places them in `service/` without explanation. [Clarity, Plan §Source Code Changes, Constitution §I]

- [ ] CHK015 — Are the responsibilities of `TaskActionCallbackHandler` (handles TASK_REMIND, TASK_DONE, AND TASK_DELETE) described in one sentence per Constitution §III? Three distinct action types in one handler class may conflict with SRP if each requires different service calls and state transitions. [Clarity, Constitution §III, Plan §Source Code Changes]

- [x] CHK016 — Is it specified that `UserState` JPA entity fields cannot all be declared `final` due to JPA lifecycle requirements (no-arg constructor, managed state mutation)? The Constitution §VIII states "All fields MUST be declared final" and the plan's constitution check marks this PASS — but JPA entities are a known exception. Is this exception documented? [Conflict, Constitution §VIII, Plan §Constitution Check] ✅ Fixed: Constitution Check §VIII updated — explicit JPA entity exception documented; `final` rule applies to service/handler/repository/DTO classes only.

---

## Callback Routing Requirements

- [ ] CHK017 — Is the routing priority between `TASK_DELETE:` and other `TASK_` prefixes explicitly defined? All three `TASK_*` prefixes share a common root — is there a risk that `startsWith("TASK_REMIND:")` could match a malformed callback beginning with `TASK_REMIND_` (hypothetical future prefix)? The plan does not define a requirement for exact prefix validation beyond `startsWith`. [Clarity, Plan §Architecture Deep-Dive]

- [ ] CHK018 — Is it specified how the `CalendarCallbackHandler` obtains `taskId` when processing a `CAL_NAV` callback? The callback data carries only `CAL_NAV:+1` — no `taskId`. The handler must read it from `UserState` context. Is this explicit in the plan, or is the dependency on UserState context implied only from the data-model.md? [Completeness, Plan §Architecture Deep-Dive, data-model.md]

- [ ] CHK019 — Is `CalendarKeyboardBuilder.buildCalendar(int year, int month, Long taskId)` signature consistent with its usage? The `CAL_DATE:` callback output contains only the date, not the taskId — if `taskId` is already tracked in `UserState` context and never embedded in callback data, why does the builder require `taskId` as a parameter? Is this parameter used, and if so, for what? [Ambiguity, Plan §CalendarKeyboardBuilder, contracts/callback-data-contract.md]

- [ ] CHK020 — Does the spec's Key Entities section (updated during clarification) mention `messageId for in-place edits` in the `UserState` context, but the plan's `ConversationContext` DTO excludes `messageId` (using the callback's own messageId instead)? Is this intentional discrepancy between spec and plan documented? [Conflict, Spec §Key Entities, Plan §data-model.md]

---

## Test Coverage Requirements

- [x] CHK021 — Are unit tests specified for `CalendarKeyboardBuilder` and `TaskListKeyboardBuilder`? These are pure, deterministic builders with clear inputs and outputs — ideal for unit testing per Constitution §IX — yet neither appears in the Phase 5 test plan. [Gap, Plan §Phase 5, Constitution §IX] ✅ Fixed: Phase 5 steps 34–35 added for both builders with specific test cases (past date blocking, nav boundary, button counts, tab callbacks).

- [x] CHK022 — Is the `UpdateDispatcherTest` requirement specified to cover command inputs while in non-IDLE states (e.g., `/cancel` typed during CREATING_TASK)? Phase 5 step 38 mentions "routing for all new prefixes, menu button routing, interruption policy" but command-during-state routing is not called out. [Completeness, Plan §Phase 5 step 38, Spec §US-2 AC-4] ✅ Fixed: Phase 5 step 41 (`UpdateDispatcherTest`) explicitly includes `/cancel` during non-IDLE and free-text during CONFIRMING_DELETE/SELECTING_REMINDER_DATE.

- [x] CHK023 — Are regression tests for all 5 legacy commands (`/newtask`, `/tasks`, `/remind`, `/done`, `/help`) specified in the test plan? SC-002 requires 100% backward compatibility, but Phase 5 only lists new handler tests — no backward-compat regression test class is named. [Gap, Spec §SC-002, Plan §Phase 5] ✅ Fixed: Phase 5 step 42 `LegacyCommandRegressionTest` added — all 5 commands tested in IDLE and non-IDLE states.

- [ ] CHK024 — Is boundary testing for the calendar navigation specified — specifically, verifying that `CAL_NAV:-1` from the current month is blocked (returns `NO_OP` or ignores), and `CAL_NAV:+1` from any month succeeds? Phase 5 step 33 says "nav boundaries" but does not enumerate the specific boundary test cases. [Completeness, Plan §Phase 5 step 33, Spec §FR-011]

- [ ] CHK025 — Is `UserStateServiceTest` specified to cover the `cancelWithNotification` cancellation message strings for all four non-IDLE states (CREATING_TASK, ENTERING_REMINDER_TIME, CONFIRMING_DELETE, SELECTING_REMINDER_DATE)? The plan defines four `cancelMessage()` strings but the test step description only says "state transitions + stale-reset". [Completeness, Plan §Phase 5 step 32, Plan §UserStateService]

---

## Edge Case & Failure Scenario Requirements

- [x] CHK026 — Is the plan's handling of the `editMessageText` failure (>48h Telegram limit) specified as a concrete implementation requirement, not only as a risk note? The Key Risks section says "on failure, send new message instead; log WARN" — but no phase or step implements this fallback. [Gap, Plan §Key Risks] ✅ Fixed: Phase 3a step 30 added — `NotificationService.safeEdit()` private helper wraps all edit calls with try-catch, falls back to sendMessage on TelegramApiException, logs WARN.

- [ ] CHK027 — Is the partial-delivery scenario defined: Phase 2 deploys the task list with `[🗑 Удалить]` buttons, but the delete flow (TASK_DELETE callback → CONFIRMING_DELETE) is only implemented in Phase 3. Is the intermediate broken state (button visible but callback unrouted) addressed — e.g., with a "coming soon" toast, a disabled button, or acceptance that phases are never deployed partially? [Completeness, Plan §Phase 2 vs Phase 3]

- [ ] CHK028 — Is the behavior specified when `Long.parseLong()` fails parsing a taskId from a callback payload (e.g., malformed callback data)? The callback contract documents extraction logic but does not define the error handling path — plan handlers are not specified to validate payload format before parsing. [Gap, contracts/callback-data-contract.md, Constitution §VII]

- [ ] CHK029 — Is the task-list truncation behavior (≥20 tasks → show first 20 + note) specified as a requirement in the plan with a note on which tab(s) it applies to? The spec Assumptions state the rule, but the plan's `TaskListKeyboardBuilder` description does not mention truncation handling. [Completeness, Spec §Assumptions, Plan §TaskListKeyboardBuilder]

- [ ] CHK030 — Is the behavior defined when a user in `CONFIRMING_DELETE` state taps a persistent menu button (valid interruption scenario per FR-005a)? The routing diagram shows step 3 handles this via `cancelWithNotification`, but the `cancelMessage(CONFIRMING_DELETE)` string is `"Удаление отменено."` — is notifying the user that a deletion was cancelled (when they never confirmed it) the correct UX? [Clarity, Spec §FR-005a, Plan §UserStateService]

---

## Dependency & Assumption Requirements

- [ ] CHK031 — Is the Jackson `ObjectMapper` bean dependency for `UserStateService` (JSONB context serialization) specified in the plan? Spring Boot auto-configures a `Jackson2ObjectMapperBuilder` bean, but `UserStateService` needing to inject `ObjectMapper` is a non-obvious dependency not called out in the plan's Technical Context. [Completeness, Plan §Technical Context, Plan §research.md Decision 3]

- [ ] CHK032 — Is the `db.changelog-master.xml` modification (adding the include for `004-create-user-states-table.xml`) explicitly listed as an implementation step? The data-model.md specifies this change, but no Phase step names it — it could be missed as a side-effect omission. [Completeness, Plan §data-model.md, Plan §Implementation Phases]

- [ ] CHK033 — Does the plan specify that `UserStateRepository.save()` is used as an upsert (insert-or-update by PK), and that Hibernate's `saveOrUpdate` / `merge` semantics are relied upon? JPA's `save()` on a new entity does INSERT but on a detached entity does a SELECT+UPDATE — is this behavior clarified for `UserStateService` implementors? [Clarity, Plan §data-model.md Repository Interface]

---

## Notes

- **CHK004, CHK008, CHK012, CHK016, CHK021, CHK022, CHK023, CHK026** — resolved 2026-03-19, see inline ✅ markers.
- **CHK005** remains open — FR-010a in spec.md should be updated to include `TASKS_TAB:` and `NO_OP` before task generation.
- **CHK009, CHK010** remain open — minor plan wording gaps worth fixing but non-blocking.
- **CHK006** is resolved by CHK023 (`LegacyCommandRegressionTest`).
- Items marked `[Gap]` represent missing requirements; items marked `[Conflict]` represent direct contradictions between documents.
- Plan is ready for `/speckit.tasks` after addressing CHK005.
