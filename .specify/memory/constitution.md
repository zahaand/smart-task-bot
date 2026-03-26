<!--
SYNC IMPACT REPORT
==================
Version change: 1.3.1 → 1.4.0
Added sections:
  - Development Standard #7: DTO naming — all DTO classes MUST carry the Dto suffix.
  - Development Standard #8: Utility classes — static-only helpers MUST use @UtilityClass
    and the Utils suffix.
  - Development Standard #9: No XML comments in Liquibase migrations — documentation
    belongs in changeset id and remarks attributes only.
  - Development Standard #10: Apache Commons usage — StringUtils / CollectionUtils MUST
    replace manual null/blank/empty checks; commons-lang3 MUST be an explicit pom.xml dep.
Modified principles: None
Removed sections: None
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (no changes required)
  - .specify/templates/spec-template.md ✅ (no changes required)
  - .specify/templates/tasks-template.md ✅ (no changes required)
  - .specify/templates/constitution-template.md ✅ (generic; project-specific standards
    do not require new generic slots)
Follow-up TODOs:
  - Verify pom.xml declares commons-lang3 as an explicit dependency.
  - Audit existing utility-like classes for @UtilityClass and Utils suffix compliance.
  - Audit existing DTO classes for Dto suffix compliance (e.g. any CreateTaskRequest).
  - Audit Liquibase migration XML files for existing <!-- --> comments and remove them.

==================
Version change: 1.3.0 → 1.3.1
Modified principles:
  - VIII. Code Style — removed Mockito when().thenReturn() formatting rule.
    The rule was overly prescriptive and conflicted with idiomatic single-line
    Mockito stubs that are more readable for simple cases. Removed from
    NON-NEGOTIABLE rules; formatting deferred to developer judgement.
Removed sections: None
Templates requiring updates:
  - .specify/templates/ ✅ (no changes required)
Follow-up TODOs: None

==================
Version change: 1.2.0 → 1.3.0
Modified principles:
  - X. Application Language — replaced "English-only" with multilingual support
    (English or Russian based on user selection during registration).
    Previous follow-up TODO about translating Russian strings is now VOID:
    Russian is a supported language; existing Russian strings are valid once
    they are served through the i18n layer, not hardcoded.
Added sections:
  - Development Standard #6: railway.toml builder rule.
  - VIII. Code Style: constructor placement, blank-line limit, @DisplayName
    ordering, Mockito when().thenReturn() formatting, @Nested no @DisplayName.
Removed sections: None
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (no changes required)
  - .specify/templates/spec-template.md ✅ (no changes required)
  - .specify/templates/tasks-template.md ✅ (no changes required)
  - .specify/templates/constitution-template.md ✅ (generic; project-specific
    Principle X does not require a new generic slot)
Follow-up TODOs:
  - All hardcoded Russian/English strings MUST be migrated to i18n message files
    before the feature branch that implements language selection is merged.
  - Affected files (pre-existing): TimezoneCallbackHandler, StartCommandHandler,
    NewTaskButtonHandler, TaskCreationTextHandler, UnknownInputHandler,
    UserStateService, UpdateDispatcher, BotConstants.
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
- Class constructors MUST appear after field declarations (dependencies, helpers, constants)
  and before other methods.
- No more than one consecutive blank line is permitted anywhere in a class.
- PROHIBITED: returning `null` from public methods — use `Optional` or throw an exception.
  Private methods MAY return `null` when used as internal control flow signals within
  a single class, provided the usage is limited to a single call site and the intent
  is obvious from context.
- Curly braces MUST always be used, including single-line `if` and `for` bodies.
- `var` is PROHIBITED except for complex generic types where the explicit type
  is excessively verbose.
- Private method names MUST start with a verb: `extractTaskId()`, `formatReminder()`.
- `@DisplayName` annotation on test methods MUST be the first annotation, placed directly
  above the method signature.
- `@Nested` test classes MUST NOT have a `@DisplayName` annotation.

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

Bot messages and UI text MUST be displayed in the language selected by the user during
registration (English or Russian).

- Before language selection, the welcome message MUST be shown in both languages
  (English first, then Russian).
- After language selection, ALL bot responses, button labels, error messages, and
  notifications MUST be in the selected language.
- Source code, identifiers, comments, and documentation MUST remain in English only.
  This rule applies to all layers.
- PROHIBITED: hardcoded language strings outside of i18n message files.

Rationale: Supporting both English and Russian makes the bot accessible to the target
audience while keeping the codebase internationally readable for portfolio reviewers.

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
6. **Railway deployment**: `railway.toml` MUST NOT specify a builder explicitly.
   Railway auto-detects the builder from the project structure.
   The `[build]` section MUST contain only `buildCommand`.
7. **DTO naming**: All Data Transfer Object classes MUST carry the `Dto` suffix
   (e.g. `TaskDto`, `CreateTaskRequestDto`). Names without the suffix are PROHIBITED
   for objects whose sole purpose is transferring data between layers.
8. **Utility classes**: Classes with no injected Spring beans and only static methods
   MUST be annotated with Lombok `@UtilityClass` and MUST use the `Utils` suffix
   (e.g. `TimeParserUtils`, `CalendarUtils`). `@UtilityClass` makes the constructor
   private and all methods implicitly static; no additional boilerplate is needed.
9. **No comments in Liquibase migrations**: Migration XML files MUST NOT contain
   XML comments (`<!-- -->`). All documentation MUST go in the `id` and `remarks`
   attributes of the changeset element only.
10. **Apache Commons usage**: `StringUtils` MUST be used for all string null/blank
    checks instead of manual `null` checks or `.isEmpty()`. `CollectionUtils` MUST
    be used for collection null/empty checks. `commons-lang3` MUST be declared as an
    explicit dependency in `pom.xml` (not relied on as a transitive dependency).

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

**Version**: 1.4.0 | **Ratified**: 2026-03-18 | **Last Amended**: 2026-03-26
