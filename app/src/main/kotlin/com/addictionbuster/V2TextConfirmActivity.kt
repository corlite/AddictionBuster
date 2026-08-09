package com.addictionbuster

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.addictionbuster.enforcement.ActivePass
import com.addictionbuster.enforcement.storage.LocalPassRepository
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class V2TextConfirmActivity : Activity() {
    private lateinit var targetIdentityKey: String
    private lateinit var targetPackage: String
    private lateinit var confirmText: String
    private var passthroughMillis: Long = 0L
    private lateinit var confirmInput: EditText
    private lateinit var confirmButton: Button
    private lateinit var allowShortButton: Button
    private lateinit var allowFullButton: Button
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetIdentityKey = intent.getStringExtra(EXTRA_TARGET_IDENTITY_KEY).orEmpty()
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        confirmText = intent.getStringExtra(EXTRA_CONFIRM_TEXT).orEmpty()
        passthroughMillis = intent.getLongExtra(EXTRA_PASSTHROUGH_MILLIS, 0L)

        if (targetIdentityKey.isBlank() || targetPackage.isBlank() || confirmText.isBlank()) {
            V2DiagnosticBridge.log(this, "v2", "text confirm activity missing extras package=$targetPackage")
            finish()
            return
        }

        setContentView(buildContent())
        confirmInput.requestFocus()
        confirmInput.post {
            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputManager?.showSoftInput(confirmInput, InputMethodManager.SHOW_IMPLICIT)
        }
        V2DiagnosticBridge.log(
            this,
            "v2",
            "text confirm activity shown package=$targetPackage confirmLength=${confirmText.length}"
        )
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(46), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }

        root.addView(text(getString(R.string.challenge_eyebrow), 18, Color.rgb(37, 99, 235), true).apply {
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(text(getString(R.string.challenge_text_title), 28, Color.rgb(15, 23, 42), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(12))
        }, matchWrap())

        messageView = text(getString(R.string.challenge_text_body), 17, Color.rgb(71, 85, 105), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(messageView, matchWrap())

        confirmInput = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.challenge_confirm_prompt_format, confirmText)
        }
        root.addView(confirmInput, matchWrap())

        confirmButton = Button(this).apply {
            isAllCaps = false
            text = getString(R.string.challenge_confirm_button)
            setOnClickListener { validateConfirmText() }
        }
        root.addView(confirmButton, matchWrap())

        allowShortButton = Button(this).apply {
            isAllCaps = false
            isEnabled = false
            text = getString(R.string.challenge_confirm_complete_first)
        }
        root.addView(allowShortButton, matchWrap())

        allowFullButton = Button(this).apply {
            isAllCaps = false
            isEnabled = false
            text = getString(R.string.challenge_confirm_complete_first)
        }
        root.addView(allowFullButton, matchWrap())

        root.addView(Button(this).apply {
            isAllCaps = false
            text = getString(R.string.challenge_quit)
            setOnClickListener { goHome() }
        }, matchWrap())

        root.addView(text(getString(R.string.challenge_confirm_release_hint), 14, Color.rgb(100, 116, 139), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }, matchWrap())

        return root
    }

    private fun validateConfirmText() {
        val actual = confirmInput.text.toString().trim()
        if (actual != confirmText) {
            confirmInput.error = getString(R.string.challenge_confirm_error)
            V2DiagnosticBridge.log(this, "v2", "text confirm mismatch package=$targetPackage")
            return
        }
        confirmInput.visibility = View.GONE
        confirmButton.visibility = View.GONE
        enableContinueButtons()
        V2DiagnosticBridge.log(this, "v2", "text confirm matched package=$targetPackage")
    }

    private fun enableContinueButtons() {
        val sessionMinutes = max(1, ceil(passthroughMillis / 60_000.0).toInt())
        val shortMinutes = min(5, sessionMinutes)
        allowShortButton.isEnabled = true
        allowShortButton.text = getString(R.string.challenge_allow_minutes_format, shortMinutes)
        allowShortButton.setOnClickListener { continueToTarget(shortMinutes) }
        if (sessionMinutes > shortMinutes) {
            allowFullButton.visibility = View.VISIBLE
            allowFullButton.isEnabled = true
            allowFullButton.text = getString(R.string.challenge_allow_minutes_format, sessionMinutes)
            allowFullButton.setOnClickListener { continueToTarget(sessionMinutes) }
        } else {
            allowFullButton.visibility = View.GONE
        }
        messageView.text = getString(R.string.challenge_completed_decide_again)
    }

    private fun continueToTarget(minutes: Int) {
        val durationMillis = max(1, minutes).toLong() * 60_000L
        LocalPassRepository(this).save(
            ActivePass(
                identityKey = targetIdentityKey,
                untilMillis = System.currentTimeMillis() + durationMillis
            )
        )
        V2DiagnosticBridge.log(
            this,
            "v2",
            "text confirm granted activePass package=$targetPackage durationMillis=$durationMillis"
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(launchIntent)
        } else {
            V2DiagnosticBridge.log(this, "v2", "text confirm target launch intent missing package=$targetPackage")
        }
        finish()
    }

    private fun goHome() {
        V2DiagnosticBridge.log(this, "v2", "text confirm quit package=$targetPackage")
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
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

    companion object {
        private const val EXTRA_TARGET_IDENTITY_KEY = "target_identity_key"
        private const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val EXTRA_CONFIRM_TEXT = "confirm_text"
        private const val EXTRA_PASSTHROUGH_MILLIS = "passthrough_millis"

        fun intentFor(
            context: Context,
            targetIdentityKey: String,
            targetPackage: String,
            confirmText: String,
            passthroughMillis: Long
        ): Intent =
            Intent(context, V2TextConfirmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TARGET_IDENTITY_KEY, targetIdentityKey)
                .putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                .putExtra(EXTRA_CONFIRM_TEXT, confirmText)
                .putExtra(EXTRA_PASSTHROUGH_MILLIS, passthroughMillis)
    }
}
