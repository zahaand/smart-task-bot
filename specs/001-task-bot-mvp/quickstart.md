# Quickstart: Smart Task Bot MVP

**Branch**: `001-task-bot-mvp` | **Date**: 2026-03-18

## Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15+ running locally
- A Telegram Bot token (obtain via [@BotFather](https://t.me/BotFather))

---

## 1. Database Setup

```sql
CREATE DATABASE smart_task_bot;
CREATE USER smart_task_bot_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smart_task_bot TO smart_task_bot_user;
```

Liquibase applies the schema automatically on startup — no manual DDL needed.

---

## 2. Environment Configuration

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

Edit `.env`:

```
BOT_TOKEN=your_telegram_bot_token_here
BOT_USERNAME=your_bot_username_here

DB_URL=jdbc:postgresql://localhost:5432/smart_task_bot
DB_USERNAME=smart_task_bot_user
DB_PASSWORD=your_password
```

`.env` is listed in `.gitignore` and MUST NOT be committed.

---

## 3. Run the Application

```bash
mvn spring-boot:run
```

Spring Boot reads environment variables from `.env` via `spring-dotenv` or
system environment. Liquibase runs migrations on startup. The bot connects to
Telegram via long polling.

Expected startup output:
```
INFO  LiquibaseAutoConfiguration - Liquibase ran migrations successfully
INFO  SmartTaskBot - Bot registered: @your_bot_username
INFO  TelegramBotsApi - Bot connected
```

---

## 4. Verify It Works

1. Open Telegram and search for your bot by `@username`
2. Send `/start` → bot should reply with a timezone selection keyboard
3. Tap a timezone button → bot confirms registration
4. Send `/newtask Buy milk` → bot confirms with task ID
5. Send `/tasks` → task appears in the list
6. Send `/remind <id> <tomorrow's date> 09:00` → bot confirms reminder
7. Send `/done <id>` → bot confirms completion, task disappears from `/tasks`

---

## 5. application.yaml Reference

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        timezone:
          default_storage: NORMALIZE_UTC

telegram:
  bot:
    token: ${BOT_TOKEN}
    username: ${BOT_USERNAME}
```

---

## 6. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Bot does not respond | Wrong `BOT_TOKEN` or bot not started | Check `.env`, restart |
| Liquibase error on startup | DB not running or credentials wrong | Check PostgreSQL, `.env` |
| `NullPointerException` on update | Handler accessing wrong update field | Check `hasCallbackQuery()` branch |
| Reminders not firing | `@EnableScheduling` missing | Add to `@SpringBootApplication` or `SchedulingConfig` |
