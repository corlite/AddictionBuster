package com.addictionbuster

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsActivityInstrumentedTest {
    @Test
    fun statsActivityRendersTodayStats() {
        ActivityScenario.launch(StatsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.window.decorView

                assertTrue(content.containsText("今日报告"))
                assertTrue(content.containsText("总览"))
                assertTrue(content.containsText("事件明细"))
                assertTrue(content.containsText("App 用量"))
            }
        }
    }

    private fun View.containsText(expected: String): Boolean {
        if (this is TextView && text?.toString() == expected) {
            return true
        }
        if (this !is ViewGroup) {
            return false
        }
        for (index in 0 until childCount) {
            if (getChildAt(index).containsText(expected)) {
                return true
            }
        }
        return false
    }
}
