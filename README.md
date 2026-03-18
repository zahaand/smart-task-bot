# Smart Task Bot

A personal Telegram task manager bot built with Java 21 and Spring Boot.
Users can create tasks, view their list, set reminders, and mark tasks as done — all via Telegram commands.

## Features

| Command | Description |
|---------|-------------|
| `/start` | Register and select your timezone |
| `/newtask <text>` | Create a new task |
| `/tasks` | List all active tasks |
| `/remind <id> DD.MM.YYYY HH:mm` | Set a reminder on a task |
| `/done <id>` | Mark a task as completed |
| `/help` | Show available commands |

Reminders are delivered within 60 seconds of the scheduled time. One retry is attempted on delivery failure.

## Tech Stack

- Java 21, Spring Boot 3.5
- Spring Data JPA + PostgreSQL 15+
- Liquibase (schema migrations)
- TelegramBots Spring Boot Starter 6.9.7.1
- Lombok, Maven

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+ running locally
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

## Setup

### 1. Database

```sql
CREATE DATABASE smart_task_bot;
CREATE USER smart_task_bot_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smart_task_bot TO smart_task_bot_user;
```

Liquibase applies all schema migrations automatically on startup — no manual DDL needed.

### 2. Environment Variables

Copy the example file:

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
BOT_TOKEN=your_telegram_bot_token_here
BOT_USERNAME=your_bot_username_here
DB_URL=jdbc:postgresql://localhost:5432/smart_task_bot
DB_USERNAME=smart_task_bot_user
DB_PASSWORD=your_password
```

> `.env` is listed in `.gitignore` and must never be committed.

Export variables before running:

```bash
export $(grep -v '^#' .env | xargs)
```

### 3. Run

```bash
mvn spring-boot:run
```

Expected startup output:

```
INFO  LiquibaseAutoConfiguration - Liquibase ran migrations successfully
INFO  TelegramBotsApi - Bot connected
```

## Lifecycle Verification

After startup, open a Telegram chat with your bot and run through this sequence:

```
/start                          → timezone keyboard appears
[tap a timezone button]         → "Timezone set: ... ✓"
/newtask Buy milk               → "Task created ✓ #1: Buy milk"
/tasks                          → "#1 Buy milk"
/remind 1 DD.MM.YYYY HH:mm     → "Reminder set ✓ ..."
[wait for reminder time]        → "⏰ Reminder: Buy milk"
/done 1                         → "Task completed ✓ #1 Buy milk"
/tasks                          → "You have no active tasks."
```

## Project Structure

```
src/main/java/ru/zahaand/smarttaskbot/
├── handler/        # Telegram update routing — no business logic
│   ├── command/    # One class per bot command
│   └── callback/   # Inline keyboard callback handlers
├── service/        # All business logic
├── repository/     # Spring Data JPA interfaces
├── model/          # JPA entities
├── dto/            # Data transfer objects between layers
└── config/         # Spring configuration and shared constants

src/main/resources/db/changelog/   # Liquibase migration files
```

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Bot does not respond | Wrong `BOT_TOKEN` or env vars not exported | Re-export `.env`, restart |
| Liquibase error on startup | DB not running or credentials wrong | Check PostgreSQL, verify `.env` |
| Reminders not firing | `@EnableScheduling` missing | Verify `SchedulingConfig` is on classpath |
| Unregistered user command ignored | Expected behavior | Send `/start` to register first |
