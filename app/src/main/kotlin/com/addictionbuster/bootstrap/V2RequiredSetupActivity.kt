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
import com.addictionbuster.R
import com.addictionbuster.V2EnforcementForegroundService
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
            text(getString(R.string.setup_title), 26, Color.rgb(15, 23, 42), true),
            matchWrap()
        )
        root.addView(
            text(
                getString(R.string.setup_subtitle),
                16,
                Color.rgb(51, 65, 85),
                false
            ).apply {
                setPadding(0, dp(16), 0, dp(12))
            },
            matchWrap()
        )
        root.addView(
            sectionTitle(getString(R.string.setup_required_permissions_title)),
            matchWrap()
        )
        root.addView(
            body(
                getString(R.string.setup_accessibility_body)
            ),
            matchWrapWithTop(10)
        )
        root.addView(button(getString(R.string.setup_open_accessibility)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, matchWrapWithTop(8))
        root.addView(
            body(
                getString(R.string.setup_overlay_body)
            ),
            matchWrapWithTop(14)
        )
        root.addView(button(getString(R.string.setup_open_overlay)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }, matchWrapWithTop(8))
        root.addView(
            sectionTitle(getString(R.string.setup_optional_permissions_title)),
            matchWrapWithTop(20)
        )
        root.addView(
            body(
                getString(R.string.setup_optional_body)
            ),
            matchWrapWithTop(10)
        )
        root.addView(button(getString(R.string.setup_open_notification_access)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, matchWrapWithTop(8))
        root.addView(
            sectionTitle(getString(R.string.setup_how_to_use_title)),
            matchWrapWithTop(20)
        )
        root.addView(
            body(
                getString(R.string.setup_how_to_use_body)
            ),
            matchWrapWithTop(10)
        )
        root.addView(button(getString(R.string.setup_enter_main)) {
            LocalSetupStateRepository(applicationContext).markSetupCompleted(System.currentTimeMillis())
            V2EnforcementForegroundService.start(this)
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
