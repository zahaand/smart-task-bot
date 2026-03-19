# Data Model: Button-Driven UX (003-button-driven-ux)

**Date**: 2026-03-19
**Branch**: `003-button-driven-ux`

---

## New Entity: UserState

### Purpose

Tracks the current step of a multi-step conversational dialog for each registered user. One row per user; the row is upserted on every state transition and reset to IDLE on completion or cancellation.

### Schema

```sql
CREATE TABLE user_states (
    telegram_user_id  BIGINT        NOT NULL PRIMARY KEY,
    state             VARCHAR(50)   NOT NULL DEFAULT 'IDLE',
    context           JSONB,
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_states_users
        FOREIGN KEY (telegram_user_id) REFERENCES users(telegram_user_id)
        ON DELETE CASCADE
);
```

**Rollback**: `DROP TABLE user_states;`

### Fields

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `telegram_user_id` | BIGINT | NOT NULL | PK + FK → `users.telegram_user_id`. One row per user. |
| `state` | VARCHAR(50) | NOT NULL | One of the `ConversationState` enum values (stored as string). Default `IDLE`. |
| `context` | JSONB | NULL | Structured payload for the current flow. Null when state is IDLE. |
| `updated_at` | TIMESTAMP | NOT NULL | UTC timestamp of last state transition. Used for stale-state detection (>24h → reset to IDLE). |

### State Machine

```
           ┌─────────────────────────────────────────┐
           │                                         │
           ▼         tap [📝 Новая задача]            │
         IDLE ──────────────────────────────► CREATING_TASK
           ▲                                         │
           │         user sends task text            │
           └─────────────────────────────────────────┘

           ┌──────────────────────────────────────────────────────────┐
           │                                                          │
           ▼     tap [⏰ Напомнить] on task                           │
         IDLE ──────────────────────────────► SELECTING_REMINDER_DATE │
                                                      │               │
                                    tap calendar date │               │
                                                      ▼               │
                                          ENTERING_REMINDER_TIME      │
                                                      │               │
                                    user sends time   │               │
                                                      └───────────────┘

           ┌──────────────────────────────────────────────┐
           │                                              │
           ▼     tap [🗑 Удалить] on task                 │
         IDLE ──────────────────────────────► CONFIRMING_DELETE
                                                      │
                          tap [✅ Да, удалить]         │
                          or [❌ Отмена]               │
                                                      └──► IDLE
```

**Interruption rule**: Tapping a persistent menu button while in any non-IDLE state cancels the flow with notification and transitions back to IDLE before starting the new flow.

**Stale-state rule**: Any state with `updated_at` older than 24 hours is lazily reset to IDLE on the next incoming event.

### Context Payloads by State

All contexts are serialized as JSON in the `context` column.

#### CREATING_TASK
```json
{}
```
No context needed — the next free-text message is unambiguous.

#### SELECTING_REMINDER_DATE
```json
{
  "taskId": 42,
  "viewingYear": 2026,
  "viewingMonth": 4
}
```
`viewingYear` / `viewingMonth` track which calendar page is displayed. Updated on each `CAL_NAV` callback.

#### ENTERING_REMINDER_TIME
```json
{
  "taskId": 42,
  "date": "2026-04-15"
}
```
`date` is ISO-8601 (YYYY-MM-DD). The `taskId` is needed to create the reminder.

#### CONFIRMING_DELETE
```json
{
  "taskId": 42
}
```

#### IDLE
`context` is NULL.

---

## New Java Types

### Enum: ConversationState

```
package ru.zahaand.smarttaskbot.model;

enum ConversationState {
    IDLE,
    CREATING_TASK,
    SELECTING_REMINDER_DATE,
    ENTERING_REMINDER_TIME,
    CONFIRMING_DELETE
}
```

### Entity: UserState

```
package ru.zahaand.smarttaskbot.model;

@Entity @Table(name = "user_states")
class UserState {
    @Id
    Long telegramUserId;

    @Enumerated(EnumType.STRING)
    ConversationState state;          // default IDLE

    String context;                   // JSON string; NULL when IDLE

    Instant updatedAt;                // UTC; updated on every state change
}
```

### DTO: ConversationContext

```
package ru.zahaand.smarttaskbot.dto;

class ConversationContext {
    Long taskId;         // present in: SELECTING_REMINDER_DATE, ENTERING_REMINDER_TIME, CONFIRMING_DELETE
    Integer viewingYear; // present in: SELECTING_REMINDER_DATE
    Integer viewingMonth;// present in: SELECTING_REMINDER_DATE (1-based)
    String date;         // ISO-8601; present in: ENTERING_REMINDER_TIME
}
```

---

## Modified Entities

### Task (no schema change)

No new columns required. The `deleteTask` operation is a physical `DELETE` on the existing table. The `getCompletedTasks` query uses the existing `status` column.

### User (no schema change)

No changes. The persistent keyboard is sent via Telegram API — no database field needed.

---

## Liquibase Migration

**File**: `src/main/resources/db/changelog/004-create-user-states-table.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                       http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.17.xsd">

    <changeSet id="004-create-user-states-table" author="zahaand">
        <createTable tableName="user_states">
            <column name="telegram_user_id" type="BIGINT">
                <constraints primaryKey="true" nullable="false"
                             foreignKeyName="fk_user_states_users"
                             references="users(telegram_user_id)"
                             deleteCascade="true"/>
            </column>
            <column name="state" type="VARCHAR(50)" defaultValue="IDLE">
                <constraints nullable="false"/>
            </column>
            <column name="context" type="JSONB"/>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="NOW()">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <rollback>
            <dropTable tableName="user_states"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**Master changelog** — append to `db.changelog-master.xml`:
```xml
<include file="db/changelog/004-create-user-states-table.xml"/>
```

---

## Repository Interface

### UserStateRepository

```
package ru.zahaand.smarttaskbot.repository;

interface UserStateRepository extends JpaRepository<UserState, Long> {
    // findById(telegramUserId) — inherited from JpaRepository
    // save(userState) — inherited; used for upsert
    // No custom queries needed — all access is by PK
}
```

---

## New TaskRepository Methods

```
// In TaskRepository (existing interface — add these):

List<Task> findByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status);

@Modifying
@Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.telegramUserId = :telegramUserId")
int deleteByIdAndUserTelegramUserId(@Param("taskId") Long taskId,
                                    @Param("telegramUserId") Long telegramUserId);
```

The `deleteByIdAndUserTelegramUserId` query enforces §II (User Data Isolation) at the repository layer. The existing `findByUserTelegramUserIdAndStatus` method name follows Spring Data naming conventions.
