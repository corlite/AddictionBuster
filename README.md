# 瘾头破坏器

AddictionBuster 的中文名是“瘾头破坏器”。这是一个最小版 Android 戒断工具，当前只做三个核心功能：

当前版本：0.1.1

1. 选择要限制的 App
2. 打开目标 App 时拦截
3. 完成 15 秒呼吸/延迟挑战后，本次临时允许进入

## 构建

```powershell
$env:ANDROID_HOME='E:\Dev\Android\Sdk'
.\gradlew.bat assembleDebug
```

APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 手机测试

1. 安装 APK。
2. 打开“瘾头破坏器”，勾选一个要限制的应用。
3. 点击“开启无障碍拦截服务”，在系统设置里开启“瘾头破坏器拦截服务”。
4. 打开刚才勾选的应用，应该先进入 15 秒呼吸挑战。
5. 点击“继续打开”，应该进入目标应用。
6. 回到桌面后再次打开目标应用，应该再次触发拦截。

## MVP 测试清单

- 构建：`.\gradlew.bat assembleDebug` 成功生成 debug APK。
- 安装：APK 可安装到 Android 模拟器或真机。
- 首次使用：主界面可列出 launcher apps，并能勾选要限制的 App。
- 无障碍：用户开启“瘾头破坏器拦截服务”后，打开被限制 App 会弹出挑战页。
- 放行：15 秒挑战结束后点击“继续打开”，只临时放行当前目标 App。
- 复拦截：离开目标 App 后再次打开，会重新触发挑战。

## 诊断日志

如果真机上无法拦截，先按下面流程取日志：

1. 打开“瘾头破坏器”。
2. 点击“查看诊断日志”，先清空日志。
3. 确认已经勾选要限制的 App，并且系统无障碍里已经开启“瘾头破坏器拦截服务”。
4. 打开目标 App，复现一次“没有被拦截”的问题。
5. 回到“瘾头破坏器”，点击“查看诊断日志”。
6. 复制或分享最后几十行日志，重点看 `service`、`event`、`challenge` 行。

也可以用 ADB 获取日志：

```powershell
adb logcat -d -s AddictionBuster:I AndroidRuntime:E ActivityTaskManager:W
adb shell run-as com.addictionbuster.app cat files/diagnostic.log
```

如果日志里完全没有 `[service] connected` 或 `[event] window type=... package=...`，通常说明无障碍服务没有真正运行，或系统/OEM 后台限制把服务停掉了。如果有 `event` 但 `blocked=false`，说明命中的包名和勾选保存的包名不一致。如果有 `launch challenge` 但没有看到挑战页，再看后面的错误行和系统 `ActivityTaskManager` 输出。

## 已知限制

- 目前只有 MVP，没有每日时长限制、统计、规则组、日程、计数器或网页拦截。
- 无障碍服务需要用户手动在系统设置里开启；调试时可用 ADB 写入 secure settings。
- 规则只保存在本机 SharedPreferences，没有云同步和账号体系。
- 选择 App 页面只列出 launcher apps，暂不支持搜索、分类或系统应用高级筛选。
- 目前没有自动化测试；主要验证方式是构建加模拟器手测。
