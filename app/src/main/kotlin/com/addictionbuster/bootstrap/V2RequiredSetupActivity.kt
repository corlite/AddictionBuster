package com.addictionbuster.bootstrap

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.addictionbuster.MainActivity
import com.addictionbuster.enforcement.storage.LocalSetupStateRepository

class V2RequiredSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.rgb(248, 250, 252))
                addView(buildContent())
            }
        )
    }

    override fun onBackPressed() {
        // Strong initialization gate: user must explicitly acknowledge the setup guide.
    }

    private fun buildContent(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        root.addView(
            text("先完成新引擎初始化", 26, Color.rgb(15, 23, 42), true),
            matchWrap()
        )
        root.addView(
            text(
                "v2 统一判定引擎不读取旧规则、不迁移旧数据。开始使用前，请先按需开启最小权限，再重新添加需要管控的 App 和规则。",
                16,
                Color.rgb(51, 65, 85),
                false
            ).apply {
                setPadding(0, dp(16), 0, dp(12))
            },
            matchWrap()
        )
        root.addView(
            sectionTitle("1. 最小必要权限"),
            matchWrap()
        )
        root.addView(
            body(
                "必须开启无障碍服务：用于识别当前前台 App、读取页面 class/关键词，并在拦截失败时执行回桌面。不开启时，新引擎无法可靠工作。"
            ),
            matchWrapWithTop(10)
        )
        root.addView(button("打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWrapWithTop(8))
        root.addView(
            body(
                "建议开启悬浮窗权限：用于显示拦截页和挑战页。不开启时，命中规则后会 fail-closed 回桌面，而不是显示覆盖层。"
            ),
            matchWrapWithTop(14)
        )
        root.addView(button("打开悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }, matchWrapWithTop(8))
        root.addView(
            sectionTitle("2. 可选权限"),
            matchWrapWithTop(20)
        )
        root.addView(
            body(
                "通知权限只用于显示权限失效、服务异常等提醒；通知使用权只在你需要后台媒体阻断/诊断时再开启。不要一开始就授予所有权限。"
            ),
            matchWrapWithTop(10)
        )
        root.addView(button("打开通知使用权") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, matchWrapWithTop(8))
        root.addView(
            sectionTitle("3. 如何使用"),
            matchWrapWithTop(20)
        )
        root.addView(
            body(
                "增加 App：回到主界面后进入“增加应用”，选择要管控的应用。\n\n增加规则：进入某个 App 的规则页，设置挑战、单 App 每日/本次/连续时长、打开次数等。手机总时长在“手机时长限制”里设置。\n\n注意：当前 v2 不兼容旧规则。旧页面若显示旧数据，只能作为参考；真正生效必须由后续 v2 规则配置写入。"
            ),
            matchWrapWithTop(10)
        )
        root.addView(button("我已完成权限设置，进入主界面") {
            LocalSetupStateRepository(applicationContext).markSetupCompleted(System.currentTimeMillis())
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }, matchWrapWithTop(22))
        return root
    }

    private fun sectionTitle(value: String): TextView =
        text(value, 18, Color.rgb(15, 23, 42), true)

    private fun body(value: String): TextView =
        text(value, 14, Color.rgb(51, 65, 85), false).apply {
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

    private fun button(value: String, onClick: () -> Unit): Button =
        Button(this).apply {
            isAllCaps = false
            text = value
            textSize = 15f
            setTextColor(Color.rgb(15, 23, 42))
            setOnClickListener { onClick() }
        }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color)
            if (bold) {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun matchWrapWithTop(topMarginDp: Int): LinearLayout.LayoutParams =
        matchWrap().apply {
            topMargin = dp(topMarginDp)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
