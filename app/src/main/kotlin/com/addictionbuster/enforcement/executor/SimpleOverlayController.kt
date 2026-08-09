package com.addictionbuster.enforcement.executor

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.addictionbuster.MascotSoundPlayer
import com.addictionbuster.MascotUi
import com.addictionbuster.R
import com.addictionbuster.V2DiagnosticBridge
import com.addictionbuster.V2TextConfirmActivity
import com.addictionbuster.enforcement.AppPolicy
import com.addictionbuster.enforcement.EnforcementAction
import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import com.addictionbuster.enforcement.stats.SuccessfulInterceptionPolicy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SimpleOverlayController(
    private val context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val actionHandler: OverlayActionHandler = OverlayActionHandler.NoOp
) : OverlayController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null
    private var currentSessionId: String? = null
    private var countdownRunnable: Runnable? = null
    private var unhideRunnable: Runnable? = null

    override fun show(decision: EnforcementDecision, context: EnforcementContext): Boolean {
        val result = AtomicBoolean(false)
        runOnMainSynchronously {
            try {
                removeAllOnMain()
                val overlaySession = OverlaySession(
                    id = UUID.randomUUID().toString(),
                    successfulInterceptionEligible =
                        SuccessfulInterceptionPolicy.isEligible(context, decision),
                    removeOverlay = ::removeSession
                )
                val view = if (decision.action == EnforcementAction.SHOW_APP_CHALLENGE) {
                    buildChallengeView(decision, context, overlaySession)
                } else {
                    buildBlockView(decision, overlaySession)
                }
                windowManager.addView(view, layoutParams())
                currentView = view
                currentSessionId = overlaySession.id
                MascotSoundPlayer.playForAction(this.context, decision.action)
                V2DiagnosticBridge.log(
                    this.context,
                    "v2",
                    "overlay shown action=${decision.action} package=${decision.targetIdentity.rawPackageName} type=$windowType"
                )
                result.set(true)
            } catch (throwable: Throwable) {
                V2DiagnosticBridge.log(
                    this.context,
                    "v2",
                    "overlay show failed action=${decision.action} package=${decision.targetIdentity.rawPackageName} type=$windowType error=${throwable.message}"
                )
                currentView = null
                result.set(false)
            }
        }
        return result.get()
    }

    override fun removeAll() {
        runOnMainSynchronously {
            removeAllOnMain()
        }
    }

    private fun removeSession(sessionId: String) {
        runOnMainSynchronously {
            if (currentSessionId == sessionId) {
                removeAllOnMain()
            }
        }
    }

    private fun removeAllOnMain() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        unhideRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        unhideRunnable = null
        val view = currentView ?: return
        try {
            windowManager.removeView(view)
        } finally {
            currentView = null
            currentSessionId = null
        }
    }

    private fun runOnMainSynchronously(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    private fun buildBlockView(decision: EnforcementDecision, overlaySession: OverlaySession): View {
        val root = rootLayout()
        root.addView(MascotUi.overlayHeader(context), matchWrap())
        root.addView(text(titleFor(decision), 26, true), matchWrap())
        root.addView(text(decision.reasonText, 16, false).apply {
            setPadding(0, dp(18), 0, dp(18))
        }, matchWrap())
        root.addView(Button(context).apply {
            isAllCaps = false
            text = context.getString(R.string.action_go_home)
            setOnClickListener {
                if (!overlaySession.tryConsume()) return@setOnClickListener
                val accepted = try {
                    actionHandler.onQuitAction(decision, overlaySession)
                } catch (_: Throwable) {
                    false
                }
                if (!accepted) {
                    overlaySession.release()
                }
            }
        }, matchWrap())
        return root
    }

    private fun buildChallengeView(
        decision: EnforcementDecision,
        context: EnforcementContext,
        overlaySession: OverlaySession
    ): View {
        val policy = context.ruleSnapshot.requireAppPolicyFor(decision.targetIdentity.identityKey)
        val state = ChallengeState(policy)
        val root = rootLayout()
        val timerView = text("", 42, true)
        val messageView = text("", 16, false)
        val actionArea = FrameLayout(this.context).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
        }
        val actionButton = Button(this.context).apply {
            isAllCaps = false
            visibility = View.GONE
        }
        val confirmInput = EditText(this.context).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(148, 163, 184))
            visibility = View.GONE
        }
        val confirmButton = Button(this.context).apply {
            isAllCaps = false
            text = this@SimpleOverlayController.context.getString(R.string.challenge_confirm_button)
            visibility = View.GONE
        }
        val allowShortButton = Button(this.context).apply {
            isAllCaps = false
            text = this@SimpleOverlayController.context.getString(R.string.challenge_complete_first)
            isEnabled = false
        }
        val allowFullButton = Button(this.context).apply {
            isAllCaps = false
            text = this@SimpleOverlayController.context.getString(R.string.challenge_complete_first)
            isEnabled = false
        }

        root.addView(MascotUi.overlayHeader(this.context), matchWrap())
        root.addView(text(this.context.getString(R.string.challenge_eyebrow), 26, true), matchWrap())
        root.addView(text(this.context.getString(R.string.challenge_app_required), 16, false).apply {
            setPadding(0, dp(8), 0, dp(16))
        }, matchWrap())
        root.addView(timerView, matchWrap())
        root.addView(messageView.apply {
            setPadding(0, dp(10), 0, dp(12))
        }, matchWrap())
        actionArea.addView(actionButton)
        root.addView(actionArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(120)
        ))
        root.addView(confirmInput, matchWrap())
        root.addView(confirmButton, matchWrap())
        root.addView(allowShortButton, matchWrap())
        root.addView(allowFullButton, matchWrap())
        root.addView(Button(this.context).apply {
            isAllCaps = false
            text = this@SimpleOverlayController.context.getString(R.string.challenge_quit)
            setOnClickListener {
                if (!overlaySession.tryConsume()) return@setOnClickListener
                val accepted = try {
                    actionHandler.onQuitAction(decision, overlaySession)
                } catch (_: Throwable) {
                    false
                }
                if (!accepted) {
                    overlaySession.release()
                }
            }
        }, matchWrap())

        fun moveActionButton() {
            actionArea.post {
                if (currentView == null) return@post
                val buttonWidth = max(dp(120), actionButton.measuredWidth)
                val buttonHeight = max(dp(48), actionButton.measuredHeight)
                val maxLeft = max(0, actionArea.width - buttonWidth)
                val maxTop = max(0, actionArea.height - buttonHeight)
                actionButton.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = if (maxLeft == 0) 0 else Random.nextInt(maxLeft + 1)
                    topMargin = if (maxTop == 0) 0 else Random.nextInt(maxTop + 1)
                }
            }
        }

        fun updateActionButton() {
            val remaining = max(0, policy.challengeRequiredTaps - state.tapCount)
            actionButton.text = this.context.getString(R.string.challenge_action_button_format, remaining)
            messageView.text = this.context.getString(R.string.challenge_action_move_hint)
        }

        fun enableContinueButtons() {
            val sessionMinutes = max(1, ceil(policy.passthroughMillis / 60_000.0).toInt())
            val shortMinutes = min(5, sessionMinutes)
            allowShortButton.isEnabled = true
            allowShortButton.text = this.context.getString(R.string.challenge_allow_minutes_format, shortMinutes)
            allowShortButton.setOnClickListener {
                completeChallenge(decision, overlaySession, shortMinutes)
            }
            if (sessionMinutes > shortMinutes) {
                allowFullButton.visibility = View.VISIBLE
                allowFullButton.isEnabled = true
                allowFullButton.text = this.context.getString(R.string.challenge_allow_minutes_format, sessionMinutes)
                allowFullButton.setOnClickListener {
                    completeChallenge(decision, overlaySession, sessionMinutes)
                }
            } else {
                allowFullButton.visibility = View.GONE
            }
            messageView.text = this.context.getString(R.string.challenge_completed_decide_again)
            V2DiagnosticBridge.log(
                this.context,
                "v2",
                "challenge complete package=${decision.targetIdentity.rawPackageName} sessionMinutes=$sessionMinutes"
            )
        }

        fun beginConfirmOrComplete() {
            if (policy.challengeConfirmText.isBlank()) {
                enableContinueButtons()
                return
            }
            if (tryLaunchTextConfirmActivity(decision, policy)) {
                removeSession(overlaySession.id)
                return
            }
            messageView.text = this.context.getString(R.string.challenge_text_prompt)
            confirmInput.hint = this.context.getString(R.string.challenge_confirm_prompt_format, policy.challengeConfirmText)
            confirmInput.visibility = View.VISIBLE
            confirmButton.visibility = View.VISIBLE
        }

        fun beginInteractions() {
            timerView.text = "0"
            if (policy.challengeRequiredTaps <= 0) {
                beginConfirmOrComplete()
                return
            }
            actionArea.visibility = View.VISIBLE
            actionButton.visibility = View.VISIBLE
            updateActionButton()
            moveActionButton()
        }

        fun hideActionButton() {
            state.hiddenCount += 1
            actionButton.visibility = View.INVISIBLE
            messageView.text = this.context.getString(R.string.challenge_action_hidden)
            unhideRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                if (currentView == null) return@Runnable
                actionButton.visibility = View.VISIBLE
                updateActionButton()
                moveActionButton()
            }
            unhideRunnable = runnable
            mainHandler.postDelayed(runnable, max(1L, policy.challengeHiddenMillis))
            V2DiagnosticBridge.log(
                this.context,
                "v2",
                "challenge action hidden package=${decision.targetIdentity.rawPackageName} hidden=${state.hiddenCount}/${policy.challengeHiddenCount}"
            )
        }

        actionButton.setOnClickListener {
            state.tapCount += 1
            V2DiagnosticBridge.log(
                this.context,
                "v2",
                "challenge tap package=${decision.targetIdentity.rawPackageName} taps=${state.tapCount}/${policy.challengeRequiredTaps}"
            )
            if (state.tapCount >= policy.challengeRequiredTaps) {
                actionArea.visibility = View.GONE
                beginConfirmOrComplete()
                return@setOnClickListener
            }
            updateActionButton()
            val hiddenRemaining = policy.challengeHiddenCount - state.hiddenCount
            val tapsRemaining = policy.challengeRequiredTaps - state.tapCount
            if (hiddenRemaining > 0 && (hiddenRemaining >= tapsRemaining || Random.nextBoolean())) {
                hideActionButton()
            } else {
                moveActionButton()
            }
        }

        confirmButton.setOnClickListener {
            val actual = confirmInput.text.toString().trim()
            if (actual == policy.challengeConfirmText) {
                confirmInput.visibility = View.GONE
                confirmButton.visibility = View.GONE
                V2DiagnosticBridge.log(
                    this.context,
                    "v2",
                    "challenge confirm matched package=${decision.targetIdentity.rawPackageName}"
                )
                enableContinueButtons()
            } else {
                confirmInput.error = this.context.getString(R.string.challenge_confirm_error)
                V2DiagnosticBridge.log(
                    this.context,
                    "v2",
                    "challenge confirm mismatch package=${decision.targetIdentity.rawPackageName}"
                )
            }
        }

        startCountdown(policy, timerView, messageView) {
            V2DiagnosticBridge.log(
                this.context,
                "v2",
                "challenge countdown complete package=${decision.targetIdentity.rawPackageName}"
            )
            beginInteractions()
        }
        return root
    }

    private fun tryLaunchTextConfirmActivity(
        decision: EnforcementDecision,
        policy: AppPolicy
    ): Boolean =
        try {
            context.startActivity(
                V2TextConfirmActivity.intentFor(
                    context = context,
                    targetIdentityKey = decision.targetIdentity.identityKey,
                    targetPackage = decision.targetIdentity.rawPackageName,
                    confirmText = policy.challengeConfirmText,
                    passthroughMillis = policy.passthroughMillis
                )
            )
            V2DiagnosticBridge.log(
                context,
                "v2",
                "challenge text confirm activity launched package=${decision.targetIdentity.rawPackageName}"
            )
            true
        } catch (throwable: Throwable) {
            V2DiagnosticBridge.log(
                context,
                "v2",
                "challenge text confirm activity launch failed package=${decision.targetIdentity.rawPackageName} error=${throwable.message}"
            )
            false
        }

    private fun startCountdown(
        policy: AppPolicy,
        timerView: TextView,
        messageView: TextView,
        onComplete: () -> Unit
    ) {
        var remainingSeconds = (policy.challengeWaitMillis / 1000L).toInt()
        val runnable = object : Runnable {
            override fun run() {
                timerView.text = remainingSeconds.toString()
                messageView.text = when {
                    policy.challengeWaitMillis <= 0L -> context.getString(R.string.challenge_no_wait)
                    remainingSeconds % 6 >= 4 -> context.getString(R.string.challenge_breathe_in)
                    remainingSeconds % 6 >= 2 -> context.getString(R.string.challenge_pause_impulse)
                    else -> context.getString(R.string.challenge_breathe_out)
                }
                if (remainingSeconds <= 0) {
                    countdownRunnable = null
                    onComplete()
                    return
                }
                remainingSeconds -= 1
                mainHandler.postDelayed(this, 1000L)
            }
        }
        countdownRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun completeChallenge(
        decision: EnforcementDecision,
        overlaySession: OverlaySession,
        minutes: Int
    ) {
        if (!overlaySession.tryConsume()) return
        val durationMillis = max(1, minutes).toLong() * 60_000L
        actionHandler.onPrimaryAction(decision.copy(durationMillis = durationMillis), overlaySession)
        removeAll()
    }

    private fun rootLayout(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 23, 42))
            isClickable = true
            isFocusable = true
        }

    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

    private fun titleFor(decision: EnforcementDecision): String =
        when (decision.action) {
            EnforcementAction.SHOW_APP_CHALLENGE -> context.getString(R.string.challenge_eyebrow)
            EnforcementAction.SHOW_PHONE_LIMIT_BLOCK -> context.getString(R.string.phone_limit_overlay_title)
            EnforcementAction.SHOW_APP_LIMIT_BLOCK -> context.getString(R.string.overlay_title_app_limit)
            EnforcementAction.SHOW_SLEEP_LOCK -> context.getString(R.string.overlay_title_sleep_lock)
            EnforcementAction.SHOW_PAGE_BLOCK -> context.getString(R.string.overlay_title_page_block)
            EnforcementAction.SHOW_CLONE_BLOCK -> context.getString(R.string.overlay_title_clone_block)
            EnforcementAction.SHOW_COOLDOWN_BLOCK -> context.getString(R.string.overlay_title_cooldown)
            else -> context.getString(R.string.overlay_title_blocked)
        }

    private fun text(value: String, sp: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private data class ChallengeState(
        val policy: AppPolicy,
        var tapCount: Int = 0,
        var hiddenCount: Int = 0
    )
}

interface OverlayActionHandler {
    fun onPrimaryAction(decision: EnforcementDecision, overlaySession: OverlaySession)
    fun onQuitAction(decision: EnforcementDecision, overlaySession: OverlaySession): Boolean

    object NoOp : OverlayActionHandler {
        override fun onPrimaryAction(decision: EnforcementDecision, overlaySession: OverlaySession) = Unit
        override fun onQuitAction(
            decision: EnforcementDecision,
            overlaySession: OverlaySession
        ): Boolean = false
    }
}

class OverlaySession(
    val id: String,
    val successfulInterceptionEligible: Boolean,
    private val removeOverlay: (String) -> Unit = {}
) {
    private val consumed = AtomicBoolean(false)

    fun tryConsume(): Boolean = consumed.compareAndSet(false, true)

    fun release() {
        consumed.set(false)
    }

    fun finishQuit(homeSent: Boolean) {
        if (homeSent) {
            removeOverlay(id)
        } else {
            release()
        }
    }
}
