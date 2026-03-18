<!--
SYNC IMPACT REPORT
==================
Version change: [TEMPLATE] → 1.0.0
Modified principles: N/A (initial population from template)
Added sections:
  - Core Principles (I–VI)
  - Technology Stack
  - Development Standards
  - Governance
Removed sections: N/A
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ (Constitution Check section already present; no structural changes needed)
  - .specify/templates/spec-template.md ✅ (no constitution-driven mandatory section changes)
  - .specify/templates/tasks-template.md ✅ (task categories align with principles)
  - .claude/commands/speckit.plan.md ✅ (no outdated agent-only references)
  - .claude/commands/speckit.constitution.md ✅ (generic guidance, no updates needed)
Follow-up TODOs: None — all placeholders resolved.
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

**Version**: 1.0.0 | **Ratified**: 2026-03-18 | **Last Amended**: 2026-03-18
