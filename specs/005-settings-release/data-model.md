# Data Model: Settings Menu and Release 1.0.0

**Branch**: `005-settings-release` | **Date**: 2026-03-26

## Schema Changes

**None.** No new tables, columns, or indexes are introduced in this branch.
All existing CASCADE constraints (migrations 004 + 006) already satisfy the
Delete Account requirement.

## Model Changes (Java only)

### `ConversationState` enum — 1 new value

| Value                       | Purpose                                                                                                        |
|-----------------------------|----------------------------------------------------------------------------------------------------------------|
| `CONFIRMING_DELETE_ACCOUNT` | User has tapped Delete Account; awaiting Confirm or Cancel inline button. Bot rejects free text in this state. |

Storage: `VARCHAR(50)` column in `user_states.state` — no migration required.

### `MessageKey` enum — 13 new constants

| Constant                         | Placeholder?           | Purpose                                |
|----------------------------------|------------------------|----------------------------------------|
| `BTN_SETTINGS`                   | —                      | Persistent-menu button label           |
| `SETTINGS_TITLE`                 | —                      | Header of the settings options message |
| `BTN_CHANGE_LANGUAGE`            | —                      | Inline option button                   |
| `BTN_CHANGE_TIMEZONE`            | —                      | Inline option button                   |
| `BTN_RESET_STATE`                | —                      | Inline option button                   |
| `BTN_DELETE_ACCOUNT`             | —                      | Inline option button                   |
| `SETTINGS_LANGUAGE_CHANGED`      | —                      | Confirmation after language update     |
| `SETTINGS_TIMEZONE_CHANGED`      | `%s` (new timezone)    | Confirmation after timezone update     |
| `SETTINGS_STATE_RESET`           | —                      | Confirmation after state reset         |
| `SETTINGS_DELETE_CONFIRM_PROMPT` | —                      | Prompt before account deletion         |
| `SETTINGS_ACCOUNT_DELETED`       | —                      | Farewell after successful deletion     |
| `SETTINGS_DELETE_CANCELLED`      | —                      | Confirmation of cancellation           |
| `TASK_CREATED_WITH_ACTIONS`      | `%d` (id), `%s` (text) | Task creation confirmation line        |

All constants follow the existing `(String en, String ru)` constructor pattern.
`BTN_SETTINGS` must be identical in EN and RU if the emoji is used as the primary
identifier, OR use distinct translations — recommended: distinct translations for full
bilingual compliance.

### `BotConstants` — 7 new constants, 1 deletion

**Added**:

```java
CB_SETTINGS_MENU    = "SETTINGS_MENU"
CB_SETTINGS_LANG    = "SETTINGS_LANG:"
CB_SETTINGS_TZ      = "SETTINGS_TZ:"
CB_SETTINGS_RESET   = "SETTINGS_RESET"
CB_SETTINGS_DEL_REQ = "SETTINGS_DELETE_REQUEST"
CB_SETTINGS_DEL_CFM = "SETTINGS_DELETE_CONFIRM"
CB_SETTINGS_DEL_CNC = "SETTINGS_DELETE_CANCEL"
```

**Deleted**: `TZ_CALLBACK_PREFIX` (deprecated alias for `CB_TZ`)
**Modified**: Add `@UtilityClass`; remove explicit `private BotConstants()` constructor
(Lombok `@UtilityClass` handles it).

## New Classes

### `SettingsCallbackHandler`

```
package: ru.zahaand.smarttaskbot.handler.callback
stereotype: @Component
dependencies: UserService, UserStateService, NotificationService, MessageService
```

**Responsibilities**: Handle all `SETTINGS_*` callback data; route to private methods
by data value; call service layer for persistence; send appropriate notifications.

**Does NOT**: contain business logic beyond routing and delegation.

### `CalendarKeyboardBuilderUtils` (renamed from `CalendarKeyboardBuilder`)

```
package: ru.zahaand.smarttaskbot.service
stereotype: @UtilityClass (Lombok)
dependencies: none
```

All existing `buildCalendar()`, `buildHeaderRow()`, etc. methods become `static`.
`@Component` removed.

### `TimeParserUtils` (renamed from `TimeParserService`)

```
package: ru.zahaand.smarttaskbot.service
stereotype: @UtilityClass (Lombok)
dependencies: none
```

All existing `parse()`, `isTwelveOClockAmbiguous()`, `tryParse()`, `validTime()` methods
become `static`. `@Service` and `@Slf4j` removed (no injected logging needed in a
stateless utility — use standard `log` if needed but currently not used in static context).

## Modified Classes

### `NotificationService`

- **Removed**: `CalendarKeyboardBuilder calendarKeyboardBuilder` field + constructor param
- **Added**: `sendSettingsMenu(Long chatId, Language language)`
- **Added**: `sendTaskCreatedWithActions(Long chatId, Long taskId, String taskText, Language language)`
- **Modified**: `sendPersistentMenu()` — adds 3rd keyboard row with `BTN_SETTINGS` button
- **Modified**: `sendCalendar()`, `editCalendar()` — static call to `CalendarKeyboardBuilderUtils`
- Constructor changes: one fewer parameter

### `UserService`

- **Added**: `deleteUser(Long telegramUserId)` — `@Transactional`, calls `userRepository.deleteById()`

### `TaskCreationTextHandler`

- **Modified**: replaces `notificationService.sendMessage(...)` with
  `notificationService.sendTaskCreatedWithActions(chatId, created.getId(), created.getText(), user.getLanguage())`

### `ReminderTimeTextHandler`

- **Modified**: removes `TimeParserService timeParserService` field; calls
  `TimeParserUtils.parse(input)` and `TimeParserUtils.isTwelveOClockAmbiguous(input)` statically

### `UpdateDispatcher`

- **Added field**: `SettingsCallbackHandler settingsCallbackHandler`
- **Modified**: `routeCallback()` — add SETTINGS_* routing block
- **Modified**: `routeMenuButton()` — add Settings button routing
- **Modified**: `isPersistentMenuButton()` — add `BTN_SETTINGS` EN + RU check

### `TimezoneCallbackHandler`

- **Modified**: `data.startsWith(BotConstants.TZ_CALLBACK_PREFIX)` → `data.startsWith(BotConstants.CB_TZ)`

## Deleted Classes

| Class                     | Reason                                                                   |
|---------------------------|--------------------------------------------------------------------------|
| `CreateTaskRequest`       | Dead code — never instantiated or referenced outside its own declaration |
| `CalendarKeyboardBuilder` | Superseded by `CalendarKeyboardBuilderUtils`                             |
| `TimeParserService`       | Superseded by `TimeParserUtils`                                          |

## Invariants

- Every `SETTINGS_*` callback value in `BotConstants` must be handled in `SettingsCallbackHandler`;
  any unrecognised value falls through to the `UpdateDispatcher` warn log.
- `ConversationState.CONFIRMING_DELETE_ACCOUNT` must be added to the free-text rejection
  guard in `UpdateDispatcher.routeMessage()` (step 7), alongside `CONFIRMING_DELETE` and
  `SELECTING_REMINDER_DATE`.
- Persistent menu always shows 3 buttons after this branch; any test asserting 2 keyboard
  rows must be updated.
