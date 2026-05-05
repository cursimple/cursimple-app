# 单课提醒与系统闹钟清理设计

日期：2026-05-05
状态：已确认

## 目标

- 用户在课程详情里手动设置某门课为提醒时，当场选择提前分钟数和铃声。
- 确认后再写入提醒规则，并立即按当天窗口同步系统时钟闹钟。
- 用户删除某条提醒规则时，同时请求删除该规则已登记过的系统时钟闹钟。
- 新增系统闹钟的名称不再带 `#xxxx`。

## Android 接口边界

`AlarmClock.ACTION_SET_ALARM` 支持传入小时、分钟、消息、铃声和跳过 UI 等字段。`AlarmClock.ACTION_DISMISS_ALARM` 支持按搜索条件请求关闭闹钟。Android 没有标准 API 读取系统时钟 App 中的闹钟列表，所以删除只能基于本 App 自己保存的系统闹钟登记簿。

参考：

- https://developer.android.com/reference/android/provider/AlarmClock

## 方案

在课程详情页点击“设为提醒”后，不再只选中课程并要求用户去设置页，而是弹出单课提醒设置弹窗。弹窗包含提前分钟数、铃声选择、保存和取消。保存时直接调用 ViewModel 的单课提醒入口，入口创建 `ReminderRule` 并调用现有 `ReminderCoordinator.syncSystemClockAlarmsForWindow()`。

删除提醒规则继续走 `ReminderCoordinator.deleteRule()`。该方法先读取 `SystemAlarmRecord`，逐条调用 `SystemAlarmClockDismisser`，成功后删除登记记录，最后删除提醒规则。补充单元测试保证手动删除规则会触发系统闹钟删除。

系统闹钟名称改为 `课表提醒 · 课程名`。内部去重仍使用 `alarmKey`，登记簿继续保存创建时写入系统闹钟的 label，后续删除按同一个 label 请求系统时钟关闭闹钟。

## 明确不做

- 不新增系统闹钟读取或轮询能力。
- 不引入应用内响铃作为系统时钟失败时的回退方案。
- 不用隐藏 hash 以外的临时标记污染闹钟名称。

## 验证

- 单元测试覆盖删除提醒规则时会请求删除对应系统闹钟。
- 单元测试覆盖系统闹钟 label 不再包含 `#`。
- 编译验证 `:feature-schedule` 与 `:core-reminder`。
