# Data Model: Smart Task Bot MVP

**Branch**: `001-task-bot-mvp` | **Date**: 2026-03-18

## Entities

### User

Represents a registered Telegram user. Created automatically on first `/start`
interaction. Registration is not complete until a timezone is selected.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `telegram_user_id` | `BIGINT` | PRIMARY KEY | Telegram's own user ID — not generated |
| `username` | `VARCHAR(255)` | NULLABLE | Telegram @username; may be null |
| `timezone` | `VARCHAR(50)` | NOT NULL | IANA tz identifier, e.g. `"Europe/Moscow"` |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | UTC |

**Uniqueness**: `telegram_user_id` is the natural primary key — no surrogate key needed.

**Registration state**: A user record with a populated `timezone` field is considered
fully registered. Before timezone selection, the record does NOT exist yet (created
atomically when timezone is chosen via callback).

### Task

A single to-do item owned by one user. Globally unique ID auto-incremented across
all users.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `BIGINT` | PRIMARY KEY, GENERATED ALWAYS AS IDENTITY | Global auto-increment |
| `telegram_user_id` | `BIGINT` | NOT NULL, FK → users | Ownership |
| `text` | `VARCHAR(500)` | NOT NULL | Max 500 characters (FR-004) |
| `status` | `VARCHAR(20)` | NOT NULL, DEFAULT `'ACTIVE'` | `'ACTIVE'` or `'COMPLETED'` |
| `reminder_time` | `TIMESTAMP` | NULLABLE | UTC; set when user issues `/remind` |
| `reminder_processed` | `BOOLEAN` | NOT NULL, DEFAULT `false` | True when processing is finished — either delivered successfully OR discarded after failed retry. Does NOT guarantee delivery. |
| `reminder_retry_at` | `TIMESTAMP` | NULLABLE | UTC; set after first delivery failure |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | UTC |

**Status transitions**:

```
ACTIVE ──(/done)──→ COMPLETED
```

No reverse transition. COMPLETED is terminal.

**Reminder lifecycle**:

```
reminder_time = NULL            → no reminder set
reminder_time IS SET,
  reminder_processed = false,
  reminder_retry_at = NULL      → pending delivery

[scheduler fires, Telegram call fails]
  reminder_retry_at = now+60s  → retry pending

[retry fires, Telegram call succeeds]
  reminder_processed = true     → delivered

[retry fires, Telegram call fails]
  reminder_processed = true     → logged, discarded (FR-009)
```

`reminder_processed = true` means "scheduler will not attempt this reminder again".
It does NOT mean the notification was successfully delivered — use logs to distinguish
delivery success from discard after failure.

---

## Relationships

```
users (1) ──────── (N) tasks
  telegram_user_id PK    telegram_user_id FK
```

One user owns zero-or-many tasks. A task belongs to exactly one user.

---

## Indexes

| Table | Columns | Purpose |
|-------|---------|---------|
| `tasks` | `(telegram_user_id, status)` | Fast `/tasks` query (filter ACTIVE by user) |
| `tasks` | `(reminder_processed, reminder_time)` | Scheduler poll: due unsent reminders |
| `tasks` | `(reminder_processed, reminder_retry_at)` | Scheduler poll: due retry reminders |

---

## Liquibase Migration Plan

All schema changes via Liquibase changesets in `db.changelog-master.xml`.

| Changeset ID | Description |
|--------------|-------------|
| `001-create-users-table` | Create `users` table with all columns |
| `002-create-tasks-table` | Create `tasks` table with FK, status check constraint |
| `003-create-tasks-indexes` | Add performance indexes on `tasks` |

---

## Java Entity Mapping

### User.java

```
@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor

Long telegramUserId          → @Id, column = "telegram_user_id"
String username              → @Column(nullable = true)
String timezone              → @Column(nullable = false, length = 50)
LocalDateTime createdAt      → @Column(nullable = false)
```

### Task.java

```
@Entity @Table(name = "tasks")
@Getter @Setter @NoArgsConstructor

Long id                      → @Id @GeneratedValue(strategy = IDENTITY)
User user                    → @ManyToOne(fetch = LAZY), @JoinColumn(name = "telegram_user_id")
String text                  → @Column(nullable = false, length = 500)
TaskStatus status            → @Enumerated(STRING), @Column(nullable = false)
Instant reminderTime         → @Column(nullable = true)
boolean reminderProcessed    → @Column(nullable = false)  // processing done, not necessarily delivered
Instant reminderRetryAt      → @Column(nullable = true)
LocalDateTime createdAt      → @Column(nullable = false)
```

### TaskStatus.java (Enum)

```
ACTIVE, COMPLETED
```

---

## Validation Rules (enforced at service layer)

| Rule | FR | Detail |
|------|----|--------|
| Task text not blank | FR-003 | Reject empty or whitespace-only text |
| Task text ≤ 500 chars | FR-004 | Reject at service before persisting |
| Reminder only on ACTIVE tasks | FR-008 | Service checks status before setting reminderTime |
| `telegramUserId` predicate on all queries | Constitution II | No query without ownership filter |
