# smart-task-bot Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-26

## Active Technologies
- Java 21 + Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1, (002-quality-and-tests)
- PostgreSQL (no schema changes in this feature) (002-quality-and-tests)
- Java 21 + Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1, Spring Data JPA, Liquibase, Lombok, Jackson (for JSONB context serialization — already on classpath via Spring Boot) (003-button-driven-ux)
- PostgreSQL 15+ — one new table (`user_states`) (003-button-driven-ux)
- Java 21 + Spring Boot 3.5, TelegramBots Spring Boot Starter 6.9.7.1, (004-i18n-and-improvements)
- PostgreSQL 15+ — two new migrations (no new tables) (004-i18n-and-improvements)
- PostgreSQL 15+ — no new tables or columns in this branch (005-settings-release)

- Java 21, Spring Boot 3.5.11
- TelegramBots Spring Boot Starter 6.9.7.1 (long-polling, `TelegramLongPollingBot`)
- Spring Data JPA + PostgreSQL 15+
- Liquibase (all schema changes via migration files only)
- Lombok (boilerplate elimination)
- Maven

## Project Structure

```text
src/main/java/ru/zahaand/smarttaskbot/
├── handler/        # Telegram update entry-point; NO business logic here
│   ├── command/    # One class per bot command
│   └── callback/   # Inline keyboard callback handlers
├── service/        # ALL business logic lives here
├── repository/     # Spring Data JPA interfaces only; NO business logic
├── model/          # JPA entities and enums
├── dto/            # Data transfer objects between layers
└── config/         # Spring configuration classes

src/main/resources/db/changelog/   # Liquibase migration files
```

## Architecture Rules (from Constitution v1.2.0)

- **Handler layer**: receives Telegram updates, delegates immediately — ZERO business logic
- **Service layer**: all validation, domain logic, timezone conversion — the ONLY place for logic
- **Repository layer**: data access only; EVERY query MUST include `telegramUserId` predicate
- **Entities MUST NOT cross layer boundaries** — use DTOs between layers
- **All schema changes via Liquibase** — no direct DDL ever

## Commands

```bash
# Run the application
mvn spring-boot:run

# Build
mvn clean package

# Run tests
mvn test
```

## Environment Variables (required in .env)

```
BOT_TOKEN=          # Telegram bot token from @BotFather
BOT_USERNAME=       # Bot username without @
DB_URL=             # jdbc:postgresql://localhost:5432/smart_task_bot
DB_USERNAME=        # PostgreSQL user
DB_PASSWORD=        # PostgreSQL password
```

## Key Patterns

- `SmartTaskBot.onUpdateReceived()` → single line: `dispatcher.dispatch(update)`
- `UpdateDispatcher` branches on `hasCallbackQuery()` first, then message handlers
- `RegistrationGuard` intercepts every command for unregistered users (no timezone set)
- Timezone stored as IANA string (e.g., `"Europe/Moscow"`) in `users.timezone`
- All datetimes stored as UTC `TIMESTAMP`; converted to/from user timezone in service layer
- Reminders polled via `@Scheduled(fixedDelay = 60_000)` in `ReminderService`

## Code Style

- Java conventions: PascalCase classes, camelCase members
- English identifiers and comments throughout
- Comments explain *why*, not *what*
- Lombok used for `@Getter`, `@Setter`, `@NoArgsConstructor`, `@Builder` — not for hiding logic

## Recent Changes

- 005-settings-release: Added Java 21 + Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1,
- 004-i18n-and-improvements: Added Java 21 + Spring Boot 3.5, TelegramBots Spring Boot Starter 6.9.7.1,
- 003-button-driven-ux: Added Java 21 + Spring Boot 3.5.11, TelegramBots Spring Boot Starter 6.9.7.1, Spring Data JPA, Liquibase, Lombok, Jackson (for JSONB context serialization — already on classpath via Spring Boot)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
