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
import com.addictionbuster.V2DiagnosticBridge
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
            text = "回到桌面"
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
            text = "确认文字"
            visibility = View.GONE
        }
        val allowShortButton = Button(this.context).apply {
            isAllCaps = false
            text = "请先完成挑战"
            isEnabled = false
        }
        val allowFullButton = Button(this.context).apply {
            isAllCaps = false
            text = "请先完成挑战"
            isEnabled = false
        }

        root.addView(MascotUi.overlayHeader(this.context), matchWrap())
        root.addView(text("先停一下", 26, true), matchWrap())
        root.addView(text("APP challenge is required", 16, false).apply {
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
            text = "算了，回到桌面"
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
            actionButton.text = "点我，还差 $remaining 次"
            messageView.text = "按钮会移动，点的时候慢一点。"
        }

        fun enableContinueButtons() {
            val sessionMinutes = max(1, ceil(policy.passthroughMillis / 60_000.0).toInt())
            val shortMinutes = min(5, sessionMinutes)
            allowShortButton.isEnabled = true
            allowShortButton.text = "允许 $shortMinutes 分钟"
            allowShortButton.setOnClickListener {
                completeChallenge(decision, overlaySession, shortMinutes)
            }
            if (sessionMinutes > shortMinutes) {
                allowFullButton.visibility = View.VISIBLE
                allowFullButton.isEnabled = true
                allowFullButton.text = "允许 $sessionMinutes 分钟"
                allowFullButton.setOnClickListener {
                    completeChallenge(decision, overlaySession, sessionMinutes)
                }
            } else {
                allowFullButton.visibility = View.GONE
            }
            messageView.text = "挑战完成。现在再决定一次：你真的要打开它吗？"
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
            messageView.text = "输入确认文字，给自己一个清醒的停顿。"
            confirmInput.hint = "请输入：${policy.challengeConfirmText}"
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
            messageView.text = "按钮先藏一下，别急着点。"
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
                confirmInput.error = "请完整输入确认文字"
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
                    policy.challengeWaitMillis <= 0L -> "这次不等待，直接进入规则确认。"
                    remainingSeconds % 6 >= 4 -> "慢慢吸气"
                    remainingSeconds % 6 >= 2 -> "停一停，观察这个冲动"
                    else -> "慢慢呼气"
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
            EnforcementAction.SHOW_APP_CHALLENGE -> "先停一下"
            EnforcementAction.SHOW_PHONE_LIMIT_BLOCK -> "手机时长已到"
            EnforcementAction.SHOW_APP_LIMIT_BLOCK -> "应用时长已到"
            EnforcementAction.SHOW_SLEEP_LOCK -> "睡眠时间"
            EnforcementAction.SHOW_PAGE_BLOCK -> "页面已拦截"
            EnforcementAction.SHOW_CLONE_BLOCK -> "双开应用已拦截"
            EnforcementAction.SHOW_COOLDOWN_BLOCK -> "需要休息"
            else -> "已拦截"
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
