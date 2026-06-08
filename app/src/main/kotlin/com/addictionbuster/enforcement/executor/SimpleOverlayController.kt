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
import android.widget.LinearLayout
import android.widget.TextView
import com.addictionbuster.V2DiagnosticBridge
import com.addictionbuster.enforcement.EnforcementAction
import com.addictionbuster.enforcement.EnforcementContext
import com.addictionbuster.enforcement.EnforcementDecision
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class SimpleOverlayController(
    private val context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    private val actionHandler: OverlayActionHandler = OverlayActionHandler.NoOp
) : OverlayController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null

    override fun show(decision: EnforcementDecision, context: EnforcementContext): Boolean {
        val result = AtomicBoolean(false)
        runOnMainSynchronously {
            try {
                removeAllOnMain()
                val view = buildView(decision)
                windowManager.addView(view, layoutParams())
                currentView = view
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

    private fun removeAllOnMain() {
        val view = currentView ?: return
        try {
            windowManager.removeView(view)
        } finally {
            currentView = null
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

    private fun buildView(decision: EnforcementDecision): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        root.addView(text(titleFor(decision), 26, true), matchWrap())
        root.addView(text(decision.reasonText, 16, false).apply {
            setPadding(0, dp(18), 0, dp(18))
        }, matchWrap())
        val button = Button(context).apply {
            isAllCaps = false
            text = if (decision.action == EnforcementAction.SHOW_APP_CHALLENGE) {
                "完成挑战"
            } else {
                "回到桌面"
            }
            setOnClickListener {
                actionHandler.onPrimaryAction(decision)
                removeAll()
            }
        }
        root.addView(button, matchWrap())
        return root
    }

    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
}

interface OverlayActionHandler {
    fun onPrimaryAction(decision: EnforcementDecision)

    object NoOp : OverlayActionHandler {
        override fun onPrimaryAction(decision: EnforcementDecision) = Unit
    }
}
