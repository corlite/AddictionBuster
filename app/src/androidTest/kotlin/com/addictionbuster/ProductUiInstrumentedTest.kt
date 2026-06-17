package com.addictionbuster

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductUiInstrumentedTest {
    @Test
    fun mainScreenRendersProductSections() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.window.decorView

                assertTrue(content.containsText("今日状态"))
                assertTrue(content.containsText("管控应用"))
                assertTrue(content.containsText("时长与报告"))
                assertTrue(content.containsText("系统"))
            }
        }
    }

    @Test
    fun settingsScreenRendersGroupedSections() {
        ActivityScenario.launch(AppSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.window.decorView

                assertTrue(content.containsText("必要权限"))
                assertTrue(content.containsText("可选能力"))
                assertTrue(content.containsText("角色与语音"))
                assertTrue(content.containsText("诊断"))
            }
        }
    }

    @Test
    fun mascotSettingsCanSelectProfileSlot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MascotStore.saveProfile(context, MascotProfile.NONE)

        ActivityScenario.launch(MascotSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.window.decorView

                assertTrue(content.containsText("角色与语音"))
                assertTrue(content.containsText("选择角色槽位"))
                assertTrue(content.containsText("拦截出现"))
                assertTrue(content.containsText("挑战通过"))
                assertTrue(content.containsText("手机时长到"))
                assertTrue(content.containsText("权限异常"))
                assertTrue(content.containsText("管控应用"))
                assertTrue(content.containsText("已管控应用"))
                assertTrue(content.containsText("添加应用"))
                assertTrue(content.containsText("今日报告"))
                assertTrue(content.containsText("拦截规则"))
                content.findTaggedView("profile_DORO")!!.performClick()
                assertTrue(MascotStore.getProfile(activity) == MascotProfile.DORO)
            }
        }
    }

    @Test
    fun ruleScreenRendersGroupedSections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, AppRuleActivity::class.java)
            .putExtra("package_name", "com.example.reader")
            .putExtra("label", "示例应用")

        ActivityScenario.launch<AppRuleActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.window.decorView

                assertTrue(content.containsText("使用额度"))
                assertTrue(content.containsText("挑战设置"))
                assertTrue(content.containsText("文字确认"))
                assertTrue(content.containsText("危险操作"))
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

    private fun View.findTaggedView(expected: String): View? {
        if (tag == expected) {
            return this
        }
        if (this !is ViewGroup) {
            return null
        }
        for (index in 0 until childCount) {
            val match = getChildAt(index).findTaggedView(expected)
            if (match != null) {
                return match
            }
        }
        return null
    }
}
