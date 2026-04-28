🇷🇺 Описание на русском ниже &nbsp;/&nbsp; 🇬🇧 Russian description below

---

## Smart Task Bot &nbsp; `v1.0.0`

A personal Telegram task manager bot built with Java 21 and Spring Boot.
Users register in two steps (language → timezone), then manage tasks entirely through
inline keyboards and reply buttons — no slash commands needed for day-to-day use.

**Demo:** [Smart Task Bot](https://t.me/smart_task_admin_bot)

<img width="832" height="923" alt="577095738-859c8941-10dd-473b-847b-01d9f8168928" src="https://github.com/user-attachments/assets/a3557a4a-4dea-41de-b2c5-1007194b1496" />

### Methodology

This project was built using the **Spec-Driven Development (SDD)** workflow powered by
[Spec Kit](https://github.com/speckit/speckit) and [Claude Code](https://claude.ai/code).
Each feature sprint starts with a machine-readable spec, passes through automated plan and
task generation, and ends with Claude Code executing every task against the generated checklist.
The result is a fully traceable audit trail from user story to merged commit.

### Features

- Two-step registration: language (EN / RU) → timezone
- Task creation via `/newtask` or the **New Task** persistent-menu button
- Task list with inline buttons — tap a task to view details
- Task detail view with **Remind**, **Complete**, and **Delete** action buttons
- Inline calendar for picking a reminder date, then free-text time input
- Reminder delivery within 60 seconds of the scheduled time (one automatic retry on failure)
- Settings menu: **Change Language**, **Change Timezone**, **Delete Account**
- Bilingual interface (English / Russian) powered by `MessageService`

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

<img width="832" height="923" alt="577095782-68409d3d-a9aa-416c-a024-301d1b917783" src="https://github.com/user-attachments/assets/a3bb5a09-5233-4740-ad03-1ba5bf7b48bc" />

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

## Smart Task Bot (на русском) &nbsp; `v1.0.0`

Персональный Telegram-бот для управления задачами на Java 21 и Spring Boot.
Пользователь регистрируется в два шага (язык → часовой пояс), после чего управляет
задачами через инлайн-клавиатуры и кнопки меню — команды для повседневного использования
не нужны.

**Демо:** [Smart Task Bot](https://t.me/smart_task_admin_bot)

### Методология разработки

Проект создан по методологии **Spec-Driven Development (SDD)** с использованием
[Spec Kit](https://github.com/speckit/speckit) и [Claude Code](https://claude.ai/code).
Каждый спринт начинается с машиночитаемой спецификации, проходит через автоматическую
генерацию плана и списка задач, а завершается выполнением всех задач Claude Code по
сгенерированному чеклисту. Результат — полная трассировка от пользовательской истории
до смёрженного коммита.

### Возможности

- Двухшаговая регистрация: язык (EN / RU) → часовой пояс
- Создание задач через `/newtask` или кнопку **Новая задача** в меню
- Список задач с инлайн-кнопками — нажмите на задачу для просмотра деталей
- Детальный просмотр задачи с кнопками **Напоминание**, **Выполнить**, **Удалить**
- Инлайн-календарь для выбора даты напоминания, затем ввод времени текстом
- Доставка напоминаний в течение 60 секунд от запланированного времени (одна автоматическая повторная попытка при сбое)
- Меню настроек: **Сменить язык**, **Сменить часовой пояс**, **Удалить аккаунт**
- Двуязычный интерфейс (English / Русский) через `MessageService`

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
