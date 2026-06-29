---
title: '修复 v2 时长重复统计与遮罩重复弹出'
type: 'bugfix'
created: '2026-06-28'
status: 'done'
baseline_commit: 'd91fb16ae1ea11d90658bad068e83f5e22f9a48d'
context:
  - 'README.md'
  - 'docs/测试清单与已知限制.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** v2 窗口事件、tick 和屏幕事件并发到达时会读取同一个旧 `lastContext`，再提交到仅负责决策的单线程队列。这会重复结算同一时间片，并让旧 tick 覆盖新前台状态；日志已显示回到桌面后旧的 `com.dragon.read` tick 重新显示遮罩。

**Approach:** 把读取旧上下文、构造新上下文、结算、执行、持久化及写回作为一个不可交错的事务串行化，保留现有规则和 fail-closed 行为，并增加并发回归测试。

## Boundaries & Constraints

**Always:** 三类 v2 入口共享同一串行门；异常或取消时可靠释放；遮罩回调不得死锁；安全区、白名单、挑战、退出回桌面及离线时长语义不变；测试覆盖互斥和异常恢复。

**Ask First:** 改变“限制关闭时仍统计非白名单 App”的语义，或清除已有统计数据。

**Never:** 不以忽略/延长 tick、遮罩冷却或包名硬编码规避；不恢复 legacy 统计；不修改无关 UI 和资源。

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 窗口事件与 tick 重叠 | 观察到同一旧时间片 | 后到事务读取新上下文，时间只结算一次 | 失败后释放串行门 |
| 退出挑战后立即回桌面 | 桌面事件紧随退出回调 | 桌面成为最新前台，旧 App 不再弹遮罩 | 保留既有回桌面结果 |
| 屏幕事件与 tick 重叠 | 熄屏/解锁同时触发 tick | 屏幕状态和会话重置不被旧 tick 覆盖 | 保留会话规则 |

</frozen-after-approval>

## Code Map

- `app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt` -- 三类并发入口及共享状态；根因所在。
- `app/src/main/kotlin/com/addictionbuster/enforcement/queue/SingleThreadEnforcementQueue.kt` -- 仅串行已构造的上下文，不能阻止旧状态重复入队。
- `app/src/main/kotlin/com/addictionbuster/enforcement/UsageSliceSettler.kt` -- 旧时间片重复入队会重复累计。
- `app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt` -- FIFO 与运行时状态事务回归测试。

## Tasks & Acceptance

**Execution:**
- [x] `app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt` -- 将窗口、屏幕、tick 与遮罩动作同步送入同一 FIFO，由唯一消费者完成事务。
- [x] `app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt` -- 验证 FIFO 顺序、桌面后 tick 状态、单次结算与异常后继续。
- [x] `docs/测试清单与已知限制.md` -- 增加遮罩回弹与墙钟时长真机回归步骤。

**Acceptance Criteria:**
- Given 遮罩退出并收到桌面事件，when 旧 tick 等待执行，then 它基于最新上下文且不能重弹旧 App 遮罩。
- Given 多事件观察到同一时间片，when 逐个处理，then 上下文读写不交错且时长不重复。
- Given 事务抛错或取消，when 后续事件到达，then 串行门已释放。
- Given 完成修改，when 运行验证，then 单元测试和 Debug/Release 构建通过。

## Spec Change Log

## Design Notes

`SingleThreadEnforcementQueue` 只保证处理器与存储写入顺序，`previousContext` 却曾在多个 IO 协程中提前读取。三个入口现在同步写入同一 FIFO，由唯一消费者完成上下文构建至写回；入口同步区同时确定顺序和时间戳。处理器保持无 Android 依赖，以便 JVM 测试。

## Verification

**Commands:**
- `.\gradlew.bat testDebugUnitTest` -- expected: JVM 测试通过。
- `.\gradlew.bat :app:compileDebugJavaWithJavac` -- expected: Debug 编译成功。
- `.\gradlew.bat assembleRelease` -- expected: Release 构建成功。

**Manual checks:**
- 快速打开并退出多个受控 App；桌面事件后不得再有旧包名 `overlay shown`。
- 连续使用非白名单 App 5 分钟，统计增量应接近 5 分钟。

## Suggested Review Order

**事件顺序与结算**

- 三类入口同步进入 FIFO，消除协程抢锁造成的旧状态回写。
  [V2AccessibilityRuntime.kt:136](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L136)

- 单消费者覆盖上下文构建、结算、执行、持久化与发布。
  [V2AccessibilityRuntime.kt:195](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L195)

- 通用顺序处理器隔离单事件失败并保持后续处理。
  [V2AccessibilityRuntime.kt:893](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L893)

**遮罩退出与通过**

- 遮罩按钮动作进入同一 FIFO，并在入口立即建立过渡保护。
  [V2AccessibilityRuntime.kt:160](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L160)

- pending 守卫保留正常结算，只抑制旧挑战的显示副作用。
  [V2AccessibilityRuntime.kt:484](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L484)

- 通过与退出动作按序更新 pass、逻辑遮罩和运行时检查点。
  [V2AccessibilityRuntime.kt:393](../../app/src/main/kotlin/com/addictionbuster/enforcement/runtime/V2AccessibilityRuntime.kt#L393)

**验证与操作手册**

- 回归测试覆盖 FIFO、桌面后 tick、单次计时与 pending 守卫。
  [OrderedRuntimeEventProcessorTest.kt:19](../../app/src/test/kotlin/com/addictionbuster/enforcement/runtime/OrderedRuntimeEventProcessorTest.kt#L19)

- 真机步骤明确重复次数、日志窗口与五分钟统计容差。
  [测试清单与已知限制.md:20](../../docs/测试清单与已知限制.md#L20)
