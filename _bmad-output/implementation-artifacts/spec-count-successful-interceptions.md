---
title: '按用户退出结果统计今日成功拦截次数'
type: 'bugfix'
created: '2026-06-29'
status: 'done'
baseline_commit: '60603bbce4abb4aca30a70ff67ef1c1d1585ac25'
context:
  - 'README.md'
  - 'docs/测试清单与已知限制.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** “今日拦截事件”目前统计 `*_BLOCK` 决策，却不记录用户在受控 App 遮罩中点击取消/返回桌面的结果。重复 tick 可能误算，真实成功拦截反而为零。

**Approach:** 新增成功结果事件：受控 App 遮罩中用户选择取消/返回桌面时记录一次。主页和报告只统计该事件，不再统计引擎决策次数。

## Boundaries & Constraints

**Always:** 每次退出动作最多记录一次；保存目标、原始动作和原因；挑战通过、遮罩显示、窗口事件及 tick 不计数；历史事件文件可读取。

**Ask First:** 将全局手机时长、睡眠锁或非受控 App 阻断也定义为成功拦截。

**Never:** 不用停留时间或 `APP_CHALLENGE_REQUIRED` 次数代替结果；不清空统计或改变拦截规则。

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 成功拦截 | 已启用 AppPolicy 的 App 遮罩中点击取消/返回桌面 | 追加一个 `INTERCEPTION_SUCCEEDED`，次数 +1 | 写入失败记日志，不重复补记 |
| 挑战通过 | 用户完成挑战并选择临时放行 | 不记录成功拦截 | 保持现有 pass 流程 |
| 重复决策 | 遮罩期间产生多个 tick/window 决策 | 次数不变 | 保留决策记录 |
| 非受控全局阻断 | 无 AppPolicy 的 App 命中手机总时长等全局规则 | 不计入本指标 | 其他拦截行为不变 |
| 回桌面失败 | 用户点击退出，但系统拒绝或无法执行 Home 动作 | 不记录成功事件 | 记录诊断日志，避免把仍停留在目标 App 误报为成功 |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt` -- Quit 的唯一 FIFO 处理点。
- `app/src/main/kotlin/com/addictionbuster/enforcement/executor/SimpleOverlayController.kt` -- 创建唯一遮罩会话并冻结成功统计资格。
- `app/src/main/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterception.kt` -- 限定可计数的 AppPolicy 动作/原因及成功结果模型。
- `app/src/main/kotlin/com/addictionbuster/enforcement/stats/DecisionEventRecorder.kt` -- 封装成功拦截事件字段和持久化。
- `app/src/main/kotlin/com/addictionbuster/enforcement/stats/EnforcementStatsAggregator.kt` -- 今日成功次数的聚合口径。
- `app/src/main/kotlin/com/addictionbuster/enforcement/storage/LocalEventStore.kt` -- 按遮罩会话 ID 原子幂等写入。
- `app/src/main/kotlin/com/addictionbuster/enforcement/EnforcementModels.kt` -- 新结果事件类型。
- `app/src/androidTest/kotlin/com/addictionbuster/enforcement/AndroidEnforcementStorageInstrumentedTest.kt` -- 集成验证。
- `app/src/test/kotlin/com/addictionbuster/enforcement/stats/EnforcementStatsAggregatorTest.kt` -- 无设备环境下验证聚合口径。
- `app/src/test/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterceptionPolicyTest.kt` -- 验证 AppPolicy 与全局规则边界。

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/EnforcementModels.kt` -- 增加向后兼容的 `INTERCEPTION_SUCCEEDED` 事件类型。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/executor/SimpleOverlayController.kt` -- 为每次遮罩创建稳定会话 ID，并冻结创建时的计数资格。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterception.kt` -- 只允许受控 App 自身的挑战/限额/冷却决策成为成功结果。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/stats/DecisionEventRecorder.kt` -- 增加成功结果记录方法和审计字段。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt` -- 仅在受控 App 的 Quit FIFO 动作中调用结果记录。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/stats/EnforcementStatsAggregator.kt` -- `blockEvents` 只统计成功结果事件。
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/storage/LocalEventStore.kt` -- 对同一遮罩会话的成功事件进行幂等写入。
- [x] `app/src/androidTest/kotlin/com/addictionbuster/enforcement/AndroidEnforcementStorageInstrumentedTest.kt` -- 验证结果 +1，普通决策不增加。
- [x] `app/src/test/kotlin/com/addictionbuster/enforcement/stats/EnforcementStatsAggregatorTest.kt` -- 验证 block/challenge 决策不计数，显式结果只计一次。
- [x] `app/src/test/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterceptionPolicyTest.kt` -- 验证手机时长、睡眠锁及无 AppPolicy 不计数。
- [x] `README.md` -- 更新自动化测试现状说明。
- [x] `docs/测试清单与已知限制.md` -- 增加今日成功拦截次数的真机步骤。

**Acceptance Criteria:**
- Given 受控 App 出现遮罩，when 用户点击取消/返回桌面，then 当天成功拦截次数只增加一次。
- Given 遮罩持续产生 tick 或窗口决策，when 用户未退出或选择放行，then 次数不增加。
- Given 历史 `*_BLOCK` 决策记录存在，when 汇总今日报告，then 它们不再被当作成功拦截结果。
- Given 修改完成，when 运行单元测试、存储仪器测试及 Release 构建，then 全部通过。

## Spec Change Log

- 2026-06-29：实现显式成功结果事件；经三路独立审查补齐来源冻结、Home 成功门控、页面规则、会话幂等、遮罩生命周期和异常隔离。

### Review Findings

- [x] [Review][Patch] 统计口径改为显式 `INTERCEPTION_SUCCEEDED`，不再把 block/challenge 决策当作成功结果。
- [x] [Review][Patch] 遮罩创建时冻结 AppPolicy 来源，并按真实 action/reason 配对排除手机时长与睡眠锁全局规则。
- [x] [Review][Patch] 为遮罩分配唯一 session ID，在 UI、持久层和聚合层三重防重复。
- [x] [Review][Patch] Home 返回成功才记录；失败或队列拒绝时保留遮罩并允许重试。
- [x] [Review][Patch] 按 session 定向移除遮罩，避免旧 Quit 误删后来创建的新遮罩。
- [x] [Review][Patch] 清除 pass、执行 Home、写入事件异常互相隔离，最终始终正确完成或释放会话。
- [x] [Review][Patch] 补充 JVM 边界测试、Android 存储/统计仪器测试与手工测试清单。
- [x] [Review][Defer] 服务 `stop()` 与已接收 Quit 同时发生时，现有生命周期可能丢弃已缓冲动作 — deferred, pre-existing runtime lifecycle architecture。
- [x] [Review][Defer] Home 成功与事件落盘之间无法跨进程原子提交 — deferred, requires persistent PendingQuit transaction protocol。

## Design Notes

决策可因 tick 重复，不能代表用户结果。遮罩创建时冻结 AppPolicy 来源资格和唯一会话 ID，并用原子消费门确保 Primary/Quit 只能有一个结果。Quit 进入 FIFO 后才执行 Home；处理期间遮罩保持，队列拒绝或 Home 失败会释放消费门供用户重试。仅当系统确认 Home 动作已发出时，按会话 ID 幂等记录成功并移除遮罩。这样队列尚未处理时即使进程停止，也不会发生应用先裸露或“已经回桌面但结果未落盘”；前台或规则随后变化也不会误计或漏计。清除临时放行、执行 Home 或记录事件均分别隔离异常。AppPolicy 下的页面阻断/页面挑战属于受控 App 遮罩并纳入，全局手机时长和睡眠锁仍排除。聚合器只按显式结果事件统计，避免依赖原因字符串后缀。

## Verification

**Commands:**
- `.\gradlew.bat testDebugUnitTest` -- passed（2026-06-29）。
- `.\gradlew.bat connectedDebugAndroidTest` -- passed，API 36 模拟器 17/17，0 failed（2026-06-29）。
- `.\gradlew.bat assembleRelease` -- passed（2026-06-29）。

**Manual checks:**
- 清空日志，记录今日次数；打开受控 App 后退出遮罩，确认 +1；再次打开并完成放行，确认不增加。

## Suggested Review Order

**成功结果主路径**

- 从 Quit FIFO 理解 Home 成功门控、记录顺序与异常隔离。
  [`V2AccessibilityRuntime.kt:407`](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L407)

- 遮罩创建时冻结资格，失败时保持覆盖并允许重试。
  [`SimpleOverlayController.kt:50`](../../app/src/main/kotlin/com/addictionbuster/enforcement/executor/SimpleOverlayController.kt#L50)

- 会话原子消费并按 ID 定向移除，避免重复和错删。
  [`SimpleOverlayController.kt:484`](../../app/src/main/kotlin/com/addictionbuster/enforcement/executor/SimpleOverlayController.kt#L484)

**口径与持久化**

- 有效动作—原因配对限定 AppPolicy，显式排除全局规则。
  [`SuccessfulInterception.kt:28`](../../app/src/main/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterception.kt#L28)

- 同一遮罩 session 原子查重后写入结构化结果。
  [`LocalEventStore.kt:47`](../../app/src/main/kotlin/com/addictionbuster/enforcement/storage/LocalEventStore.kt#L47)

- 今日指标只聚合显式成功结果，兼容历史事件。
  [`EnforcementStatsAggregator.kt:49`](../../app/src/main/kotlin/com/addictionbuster/enforcement/stats/EnforcementStatsAggregator.kt#L49)

**验证**

- 单元测试覆盖 Home 失败、全局排除和页面规则。
  [`SuccessfulInterceptionPolicyTest.kt:71`](../../app/src/test/kotlin/com/addictionbuster/enforcement/stats/SuccessfulInterceptionPolicyTest.kt#L71)

- 设备测试验证幂等落盘及公开统计快照。
  [`AndroidEnforcementStorageInstrumentedTest.kt:131`](../../app/src/androidTest/kotlin/com/addictionbuster/enforcement/AndroidEnforcementStorageInstrumentedTest.kt#L131)

- 手工清单验证用户可见的今日次数变化。
  [`测试清单与已知限制.md:22`](../../docs/测试清单与已知限制.md#L22)
