<!--
SYNC IMPACT REPORT
==================
Version change: 1.1.1 → 1.2.0
Modified principles: None
Added sections:
  - X. Application Language (NON-NEGOTIABLE) — all user-facing bot text MUST be in English;
    Russian or any other language in Telegram API output is PROHIBITED.
Removed sections: None
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (Constitution Check section is dynamically derived; no edit needed)
  - .specify/templates/spec-template.md ✅ (no structural changes needed)
  - .specify/templates/tasks-template.md ✅ (no structural changes needed)
  - .specify/templates/constitution-template.md ✅ (generic slot-based template; Principle X is
    project-specific and does not warrant a new generic slot in the template)
Follow-up TODOs:
  - Code written before 2026-03-20 (Phase 2/3 of 003-button-driven-ux) contains Russian
    user-facing strings. These MUST be translated to English before the branch is merged.
    Affected files: TimezoneCallbackHandler, StartCommandHandler, NewTaskButtonHandler,
    TaskCreationTextHandler, UnknownInputHandler, UserStateService (cancelMessage strings),
    UpdateDispatcher (sendMessage "Пожалуйста, используй кнопки выше." and
    "Эта функция скоро появится!"), BotConstants (BTN_* label values).
-->

# Smart Task Bot Constitution

## Core Principles

### I. Layered Architecture (NON-NEGOTIABLE)

The application MUST follow a strict four-layer architecture: `handler → service → repository → model`.

- **Handler layer**: Responsible solely for receiving and routing Telegram messages/commands.
  It MUST NOT contain any business logic.
- **Service layer**: All business logic MUST reside here. No exceptions.
- **Repository layer**: Responsible solely for data access via Spring Data JPA.
  It MUST NOT contain any business logic.
- **Model layer**: JPA entities only — no behavior beyond simple accessors.
- **DTO layer**: Data transfer objects used between layers.
  Entities MUST NOT leak across layer boundaries.
- **Config layer**: Spring configuration classes only.

Rationale: Separation of concerns is the foundation of maintainable, testable, and readable
portfolio code. Violations make the codebase unpresentable to reviewers.

### II. User Data Isolation

Every data-access operation MUST be scoped to the authenticated Telegram User ID.

- Repository queries MUST include a `telegramUserId` predicate so a user can never read
  or mutate another user's tasks or reminders.
- The service layer MUST reject any request where the caller's identity cannot be established.
- No cross-user data access is permitted under any circumstances.

Rationale: Multi-user bots without strict data isolation are a security vulnerability
and a demonstration of poor design — both disqualifying in a portfolio context.

### III. Single Responsibility (SRP)

Every class MUST have exactly one clearly defined responsibility.

- A class that creates tasks MUST NOT also send Telegram messages.
- A class that parses user input MUST NOT also persist data.
- If a class's responsibility cannot be described in one sentence, it MUST be split.

Rationale: SRP is the single most visible quality signal during portfolio code review.

### IV. Database Migrations via Liquibase

All database schema changes MUST be applied exclusively through Liquibase migration files.

- Direct DDL execution (e.g., `CREATE TABLE`, `ALTER TABLE`) outside of Liquibase is PROHIBITED.
- Every migration file MUST include a rollback block, or provide a documented justification
  for why rollback is not feasible.
- Migration files are part of committed source code and MUST be reviewed before merge.

Rationale: Schema-as-code is a professional standard. Ad-hoc schema changes break
reproducibility and make the project unpresentable.

### V. Secrets via Environment Variables

All sensitive configuration values (bot tokens, database passwords, API keys) MUST be
supplied through environment variables or a dedicated secrets-management mechanism.

- Hardcoded credentials in source code are PROHIBITED.
- Default values for secrets in configuration files are PROHIBITED.
- The `.env` file MUST be listed in `.gitignore` and MUST NOT be committed.

Rationale: Leaked credentials in a portfolio repository are a disqualifying red flag.

### VI. Simplicity and Portfolio Readability

Code MUST be the minimum complexity required to satisfy the stated MVP requirements.

- YAGNI: Features not listed in the MVP scope MUST NOT be implemented speculatively.
- Code MUST be written to be understood by a reviewer unfamiliar with the project.
- Identifier names (classes, methods, variables) MUST be in English and be self-descriptive.
- Comments, where present, MUST be in English and explain *why*, not *what*.
- Lombok annotations MUST be used to eliminate boilerplate, not to obscure logic.

Rationale: This is a portfolio project. Clarity and professionalism of code are the
primary success criteria alongside functional correctness.

### VII. Logging Standards (NON-NEGOTIABLE)

All logging MUST use the `@Slf4j` Lombok annotation. Direct logger instantiation is PROHIBITED.

Log levels MUST be applied as follows:

- **DEBUG**: input parameters, intermediate processing steps.
- **INFO**: successful completion of a business operation.
- **WARN**: abnormal but handled situation (e.g., retry, invalid user input).
- **ERROR**: exception or operation failure.

Every `Exception` MUST be logged at ERROR level with identifying context before being thrown:

```java
log.error("Task {} not found for user {}", taskId, userId);
throw new NoSuchElementException(...);
```

- Log messages MUST include relevant identifiers (`telegramUserId`, `taskId`, etc.).
- Logging sensitive data (e.g., `BOT_TOKEN`, passwords) is PROHIBITED.
- `SmartTaskBot.onUpdateReceived()` MUST wrap all processing in a centralized `try-catch`,
  log any unhandled exception at ERROR level, and reply to the user:
  `"Something went wrong. Please try again."`
- Propagating exceptions above the handler layer is PROHIBITED.

Rationale: Structured, level-appropriate logging is a professional standard that makes
failures diagnosable in production and demonstrates operational maturity to reviewers.

### VIII. Code Style (NON-NEGOTIABLE)

Class members MUST be declared in the following order:

1. Injected dependencies (via constructor) — one blank line below the class declaration.
2. Helper objects (`DateTimeFormatter`, etc.) — one blank line below dependencies.
3. Constants (`static final`) — one blank line below helper objects.

Additional rules:

- All fields MUST be declared `final`.
- Constructor injection MUST be used; `@Autowired` on fields is PROHIBITED.
- PROHIBITED: returning `null` from public methods — use `Optional` or throw an exception.
  Private methods MAY return `null` when used as internal control flow signals within
  a single class, provided the usage is limited to a single call site and the intent
  is obvious from context.
- Curly braces MUST always be used, including single-line `if` and `for` bodies.
- `var` is PROHIBITED except for complex generic types where the explicit type
  is excessively verbose.
- Private method names MUST start with a verb: `extractTaskId()`, `formatReminder()`.

Rationale: Consistent, explicit code style reduces cognitive load during review and
signals discipline — a critical quality marker in portfolio code.

### IX. Testing Standards (NON-NEGOTIABLE)

- Unit tests MUST use `@ExtendWith(MockitoExtension.class)`.
- `@SpringBootTest` in unit tests is PROHIBITED.
- Tests MUST be structured with `@Nested` classes, one per method under test.
- Every test method MUST carry `@DisplayName` in English describing the scenario.
- Boundary and equivalence-class cases MUST use `@ParameterizedTest` + `@MethodSource`.

Rationale: A test suite that follows these conventions is self-documenting, fast, and
demonstrates mastery of unit-testing discipline — essential for a portfolio project.

### X. Application Language (NON-NEGOTIABLE)

All user-facing text sent via the Telegram API MUST be in English:

- Bot messages and replies
- Button labels and persistent menu items
- Error messages and validation hints
- Reminder notifications
- Confirmation prompts

PROHIBITED: Russian or any other language in bot responses, button labels,
or any text delivered to the user through the Telegram API.

This constraint applies to all new code. Existing Russian strings introduced before
this amendment MUST be translated before the containing branch is merged to `main`.

Rationale: The bot is a portfolio project demonstrated to international clients.
An English-only interface signals professionalism and international readiness.

## Technology Stack

The following stack is fixed for the lifetime of this project.
Deviations require a constitution amendment.

| Component    | Choice                                   |
|--------------|------------------------------------------|
| Language     | Java 21                                  |
| Framework    | Spring Boot 3.5                          |
| Database     | PostgreSQL                               |
| Migrations   | Liquibase                                |
| Telegram SDK | TelegramBots Spring Boot Starter 6.9.7.1 |
| Boilerplate  | Lombok                                   |
| Build tool   | Maven                                    |

All dependency version changes MUST be tracked in version control with an explanatory
commit message.

## Development Standards

1. **Package structure** MUST mirror the architectural layers:
   `handler`, `service`, `repository`, `model`, `dto`, `config`.
2. **Naming** MUST follow standard Java conventions (PascalCase for classes,
   camelCase for members and variables).
3. **No orphaned code**: Unused classes, methods, or imports MUST be removed before commit.
4. **Environment parity**: The application MUST be runnable locally using only the
   instructions in the README and a valid `.env` file.
5. **Commit hygiene**: Each commit MUST represent a single logical change with a
   clear, descriptive message.

## Governance

This Constitution supersedes all other development practices for the Smart Task Bot project.

**Amendment procedure**:

1. Identify the principle or section to change and state the motivation.
2. Increment the version according to semantic versioning:
    - **MAJOR**: Removal or incompatible redefinition of an existing principle.
    - **MINOR**: Addition of a new principle or materially expanded guidance.
    - **PATCH**: Clarification, wording fix, or non-semantic refinement.
3. Update `LAST_AMENDED_DATE` to the amendment date (ISO format: YYYY-MM-DD).
4. Record the change in the Sync Impact Report comment block at the top of this file.

**Compliance**: All code reviews MUST verify compliance with the Core Principles above.
Any violation that cannot be immediately corrected MUST be logged in the Complexity
Tracking section of the relevant `plan.md` with explicit justification.

**Runtime guidance**: See `.specify/memory/` for feature-specific specs, plans, and tasks.

**Version**: 1.2.0 | **Ratified**: 2026-03-18 | **Last Amended**: 2026-03-20
