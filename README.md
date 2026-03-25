🇷🇺 Описание на русском ниже &nbsp;/&nbsp; 🇬🇧 Russian description below

---

## Smart Task Bot

A personal Telegram task manager bot built with Java 21 and Spring Boot.
Users register in two steps (language → timezone), then manage tasks entirely through
inline keyboards and reply buttons — no slash commands needed for day-to-day use.

### Methodology

This project was built using the **Spec-Driven Development (SDD)** workflow powered by
[Spec Kit](https://github.com/speckit/speckit) and [Claude Code](https://claude.ai/code).
Each feature sprint starts with a machine-readable spec, passes through automated plan and
task generation, and ends with Claude Code executing every task against the generated checklist.
The result is a fully traceable audit trail from user story to merged commit.

### Commands

| Command | Description |
|---------|-------------|
| `/start` | Begin registration (language → timezone) |
| `/newtask <text>` | Create a new task |
| `/tasks` | Open the task list with inline action buttons |
| `/remind <id> DD.MM.YYYY HH:mm` | Set a reminder on a task (legacy CLI) |
| `/done <id>` | Mark a task as completed (legacy CLI) |
| `/help` | Show available commands |
| `/cancel` | Cancel the current multi-step flow |

### Reminder Time Formats

The bot accepts reminder times in three equivalent formats:

| Format | Example |
|--------|---------|
| `HH:MM` | `14:30` |
| `HH MM` | `14 30` |
| `HH-MM` | `14-30` |

Russian AM/PM suffixes are also supported: `9 утра`, `3 вечера`, `9:30 утра`.

Reminders are delivered within 60 seconds of the scheduled time. One retry is attempted on delivery failure.

### Local Setup

**Prerequisites**

- Java 21+
- Maven 3.9+
- PostgreSQL 15+ running locally
- A Telegram bot token from [@BotFather](https://t.me/BotFather)

**1. Database**

```sql
CREATE DATABASE smart_task_bot;
CREATE USER smart_task_bot_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smart_task_bot TO smart_task_bot_user;
```

Liquibase applies all schema migrations automatically on startup — no manual DDL needed.

**2. Environment Variables**

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

**3. Run**

```bash
mvn spring-boot:run
```

Expected startup output:

```
INFO  LiquibaseAutoConfiguration - Liquibase ran migrations successfully
INFO  TelegramBotsApi - Bot connected
```

---

## Smart Task Bot (на русском)

Персональный Telegram-бот для управления задачами на Java 21 и Spring Boot.
Пользователь регистрируется в два шага (язык → часовой пояс), после чего управляет
задачами через инлайн-клавиатуры и кнопки меню — команды для повседневного использования
не нужны.

### Методология разработки

Проект создан по методологии **Spec-Driven Development (SDD)** с использованием
[Spec Kit](https://github.com/speckit/speckit) и [Claude Code](https://claude.ai/code).
Каждый спринт начинается с машиночитаемой спецификации, проходит через автоматическую
генерацию плана и списка задач, а завершается выполнением всех задач Claude Code по
сгенерированному чеклисту. Результат — полная трассировка от пользовательской истории
до смёрженного коммита.

### Команды

| Команда | Описание |
|---------|----------|
| `/start` | Начать регистрацию (язык → часовой пояс) |
| `/newtask <текст>` | Создать новую задачу |
| `/tasks` | Открыть список задач с инлайн-кнопками |
| `/remind <id> ДД.ММ.ГГГГ ЧЧ:мм` | Установить напоминание (legacy CLI) |
| `/done <id>` | Отметить задачу выполненной (legacy CLI) |
| `/help` | Показать доступные команды |
| `/cancel` | Отменить текущий многошаговый сценарий |

### Форматы времени для напоминаний

Бот принимает время напоминания в трёх эквивалентных форматах:

| Формат | Пример |
|--------|--------|
| `ЧЧ:ММ` | `14:30` |
| `ЧЧ ММ` | `14 30` |
| `ЧЧ-ММ` | `14-30` |

Поддерживаются русские суффиксы AM/PM: `9 утра`, `3 вечера`, `9:30 утра`.

Напоминания доставляются в течение 60 секунд от запланированного времени. При сбое выполняется одна повторная попытка.

### Локальный запуск

**Требования**

- Java 21+
- Maven 3.9+
- PostgreSQL 15+, запущенный локально
- Токен бота от [@BotFather](https://t.me/BotFather)

**1. База данных**

```sql
CREATE DATABASE smart_task_bot;
CREATE USER smart_task_bot_user WITH PASSWORD 'ваш_пароль';
GRANT ALL PRIVILEGES ON DATABASE smart_task_bot TO smart_task_bot_user;
```

Liquibase применяет все миграции схемы автоматически при запуске — ручной DDL не нужен.

**2. Переменные окружения**

Скопируйте пример файла:

```bash
cp .env.example .env
```

Отредактируйте `.env`:

```env
BOT_TOKEN=токен_вашего_бота
BOT_USERNAME=имя_пользователя_бота
DB_URL=jdbc:postgresql://localhost:5432/smart_task_bot
DB_USERNAME=smart_task_bot_user
DB_PASSWORD=ваш_пароль
```

> `.env` добавлен в `.gitignore` — никогда не коммитьте его.

Экспортируйте переменные перед запуском:

```bash
export $(grep -v '^#' .env | xargs)
```

**3. Запуск**

```bash
mvn spring-boot:run
```

Ожидаемый вывод при старте:

```
INFO  LiquibaseAutoConfiguration - Liquibase ran migrations successfully
INFO  TelegramBotsApi - Bot connected
```
