package com.addictionbuster.bootstrap

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class V2RequiredSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        setContentView(buildContent())
    }

    override fun onBackPressed() {
        // Strong initialization gate: v2 rules must exist before leaving this flow.
    }

    private fun buildContent(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        root.addView(
            text("需要重新配置新引擎", 26, Color.rgb(15, 23, 42), true),
            matchWrap()
        )
        root.addView(
            text(
                "瘾头破坏器已进入 v2 统一判定引擎。新引擎不读取旧规则、不迁移旧数据。为保证管控有效，请先完成新规则配置。",
                16,
                Color.rgb(51, 65, 85),
                false
            ).apply {
                setPadding(0, dp(16), 0, dp(12))
            },
            matchWrap()
        )
        root.addView(
            text(
                "当前版本只建立强制初始化入口；新规则配置界面将在后续切片接入。在配置完成前，请不要依赖旧规则进行管控。",
                14,
                Color.rgb(185, 28, 28),
                true
            ),
            matchWrap()
        )
        return root
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
