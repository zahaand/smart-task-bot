# Plan Review Checklist: Smart Task Bot MVP

**Purpose**: Validate plan.md quality — completeness, clarity, and consistency
against spec.md (FR-001–FR-018) and Constitution v1.0.0 (Principles I–VI)
**Created**: 2026-03-18
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md)

---

## Constitution Compliance

- [x] CHK001 — Is the boundary between `RegistrationGuard` (handler layer) and the
  repository clearly specified? The plan shows `RegistrationGuard` calling
  `userRepository.existsById()` directly from the handler layer, which violates
  Constitution Principle I (repository access MUST go through the service layer).
  Is `UserService.isRegistered()` the intended call, or is the direct repository
  access explicitly justified? [Conflict, Constitution I, Plan §Registration Guard]

- [ ] CHK002 — Is the responsibility boundary of `NotificationService` unambiguous?
  The plan assigns it both "sending Telegram messages" and "building the timezone
  inline keyboard" (`sendTimezoneKeyboard()`). Are keyboard construction (UI concern)
  and message dispatch (transport concern) one responsibility or two, and is this
  decision documented? [Clarity, Constitution III, Plan §Key Design Decisions]

- [ ] CHK003 — Is the `UpdateDispatcher` documented with a single, clearly stated
  responsibility? The plan describes it routing updates, branching on update type,
  and invoking `RegistrationGuard`. Are these three actions considered one
  responsibility, or does the plan justify their co-location? [Clarity, Constitution III,
  Plan §Project Structure]

- [ ] CHK004 — Is `SmartTaskBot.java` placement in `handler/` explicitly justified?
  It is both a framework subclass (`TelegramLongPollingBot`) and a Spring-configured
  bean (reads token via `@Value`). Does the plan address why it does not belong in
  `config/` or a dedicated `bot/` package? [Clarity, Constitution I, Plan §Project Structure]

- [x] CHK005 — Is the Spring circular dependency risk for `NotificationService`
  addressed in the plan? `SmartTaskBot` → `UpdateDispatcher` → handlers →
  `NotificationService` → needs `SmartTaskBot` API executor. Is the resolution
  strategy (e.g., `@Lazy` injection, `ApplicationContext.getBean()`, or event-based
  decoupling) specified? [Gap, Constitution I/III, Plan §Key Design Decisions]

---

## Functional Requirement Coverage

- [x] CHK006 — Is the deviation from FR-001 explicitly documented? FR-001 states the
  user MUST be registered "on first interaction". The plan creates the user record only
  in `TimezoneCallbackHandler` (when a button is tapped), meaning a user who sends
  `/start` but never selects a timezone has no record. Is this deferred-registration
  approach stated as a conscious, justified deviation? [Conflict, FR-001, Plan §Registration Guard]

- [ ] CHK007 — Is duplicate prevention for timezone callback specified? FR-001 requires
  MUST NOT create duplicate records. If a user taps a timezone button twice (e.g., after
  re-sending `/start`), is the idempotency mechanism (e.g., upsert / `ON CONFLICT DO
  NOTHING` in the migration, or service-layer guard) defined in the plan? [Gap, FR-001,
  Plan §Registration Guard]

- [ ] CHK008 — Is the behavior for unexpected callback queries (non-timezone) defined?
  The dispatch flow gates message commands behind `RegistrationGuard` but routes ALL
  callback queries directly to `TimezoneCallbackHandler`. Is there a defined fallback
  for callbacks with unrecognised data (e.g., stale keyboard from a previous session)?
  [Gap, FR-016, Plan §Update Dispatcher Pattern]

- [ ] CHK009 — Is the ownership validation pattern specified for BOTH `/remind` and
  `/done`? FR-010 and FR-013 each require rejecting operations on tasks belonging to
  other users. The plan mentions "service layer validates ownership" generically —
  is the specific query pattern (e.g., `findByIdAndTelegramUserId()`) documented as
  the canonical approach for both commands? [Clarity, FR-010 + FR-013, Plan §Project Structure]

- [ ] CHK010 — Is the location of the predefined timezone list (FR-018) specified?
  FR-018 mandates a minimum set of 5 IANA timezone identifiers. The plan does not state
  whether this list lives in a configuration file, a constants class, or application.yaml.
  Is this decision documented? [Gap, FR-018, Plan §Project Structure]

- [ ] CHK011 — Is the output path of timezone conversion for `/tasks` explicitly covered?
  FR-017 requires timezone applied to both "input parsing AND notification delivery".
  The plan details the input path (`/remind` parsing) but does not explicitly state that
  `TaskDto` contains the reminder time already converted to the user's local timezone
  for the `/tasks` display. Is this covered? [Clarity, FR-017, Plan §Timezone Handling]

- [ ] CHK012 — Is `StartCommandHandler` behaviour for already-registered users defined
  in the plan? The spec edge case ("user sends `/start` more than once") says the bot
  greets again but does NOT create a duplicate record. The plan only mentions record
  creation in `TimezoneCallbackHandler`. Is the `StartCommandHandler`'s handling of
  existing users (no-op on record, display greeting only) explicitly documented?
  [Gap, FR-001, Plan §Project Structure]

---

## Data Model Integrity

- [x] CHK013 — Is the semantic meaning of `reminder_sent = true` consistently defined?
  `data-model.md` column notes say "True after successful delivery", but the reminder
  lifecycle diagram shows `reminder_sent = true (failed)` after a discarded retry.
  Is the field accurately described as "processed (regardless of outcome)" rather than
  "successfully delivered"? [Conflict, data-model.md §Task, FR-009]

- [ ] CHK014 — Is a `CHECK` constraint on `tasks.status` specified in the migration
  plan? `data-model.md` references "status check constraint" in the changeset description
  but does not define the constraint value set explicitly (`CHECK (status IN ('ACTIVE',
  'COMPLETED'))`). Is this constraint's exact definition documented? [Clarity,
  data-model.md §Liquibase Migration Plan]

- [ ] CHK015 — Is the `User.username` update policy documented? The field is NULLABLE,
  but the plan does not specify when it is populated or whether it is refreshed on
  subsequent interactions (Telegram usernames can change). Is this behaviour intentionally
  undefined, or is it a gap? [Gap, data-model.md §User]

- [ ] CHK016 — Is the DTO surface complete? The plan lists `TaskDto` and
  `CreateTaskRequest`. Is a structured input type for the `/remind` command (task ID +
  raw datetime string) also needed, or is it intentionally absent (with parsing done
  inline in the handler or service)? [Completeness, Plan §Project Structure]

---

## Design Decision Completeness

- [ ] CHK017 — Is the `ReminderService` → Telegram API dependency chain specified?
  `ReminderService` calls `NotificationService`, which must call the Telegram bot
  executor. The plan does not state how `NotificationService` holds a reference to
  the sending capability without creating a circular Spring bean dependency.
  Is the wiring strategy documented? [Gap, Plan §Reminder Scheduler]

- [ ] CHK018 — Is the retry state transition during task completion addressed? If a task
  has `reminderRetryAt` set (retry pending) and the user marks it COMPLETED before
  the retry fires, the scheduler query filters by `status = ACTIVE` — so the retry
  would be skipped. Is this interaction explicitly documented as the intended
  behaviour, consistent with FR-009 ("unless the task has already been completed")?
  [Clarity, Plan §Reminder Scheduler, FR-009]

- [ ] CHK019 — Is the `fixedDelay` vs `fixedRate` choice for `@Scheduled` documented?
  The plan uses `fixedDelay = 60_000` (next poll starts 60 s after previous finishes).
  Under slow DB conditions this extends the delivery window beyond 60 s, potentially
  violating SC-003 (95% within 60 s). Is this trade-off acknowledged? [Gap,
  Plan §Reminder Scheduler, SC-003]

---

## Non-Functional Requirement Traceability

- [ ] CHK020 — Is the response time requirement (SC-004, < 3 s) traceable to any
  design constraint in the plan? The plan states the goal but does not specify
  synchronous vs async handling, Telegram API call timeouts, or DB query expectations.
  Is SC-004 addressed beyond being stated in Technical Context? [Gap, SC-004,
  Plan §Technical Context]

- [ ] CHK021 — Is JVM timezone configuration addressed? The plan stores all datetimes
  in UTC. If the production JVM's default timezone is not UTC, `LocalDateTime.now()`
  calls inside the application could silently shift timestamps. Is a JVM startup flag
  (`-Duser.timezone=UTC`) or programmatic override specified or acknowledged?
  [Gap, Plan §Timezone Handling, Spec §Assumptions]

- [ ] CHK022 — Is the "task not found vs access denied" response strategy specified for
  SC-002? Returning "Task #N not found" (rather than "Access denied") when a user
  references another user's task prevents ID enumeration. Is this security-adjacent
  behaviour documented as intentional? [Clarity, SC-002, Plan §Key Design Decisions]

---

## Consistency & Conflicts

- [x] CHK023 — Is the "created on first interaction" vs "created on timezone tap"
  conflict between spec and plan resolved? The spec Key Entities section says User is
  "Created automatically on first interaction." The plan says it is created in
  `TimezoneCallbackHandler`. Are these two statements explicitly reconciled —
  either by updating the spec or by the plan documenting the deviation with rationale?
  [Conflict, Spec §Key Entities, Plan §Registration Guard]

- [ ] CHK024 — Is the `reminder_retry_at` column justified against Constitution VI
  (Simplicity / YAGNI)? The two-query scheduler approach adds a new DB column and
  a second query per poll cycle. Is it explicitly stated that this complexity is
  required by FR-009 (one retry requirement) and not speculative?
  [Constitution VI, data-model.md §Task, FR-009]

---

## Notes

- Mark items `[x]` when the plan is updated to address the issue.
- CHK001, CHK005, CHK006, CHK013 are the highest-priority items — they represent
  either Constitution violations or spec/plan contradictions that could cause rework.
- CHK019 and CHK021 are infrastructure concerns best resolved before implementation begins.
