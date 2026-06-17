package com.addictionbuster

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotStoreTest {
    @Test
    fun profileFromStoredValueFallsBackToNone() {
        assertEquals(MascotProfile.NONE, MascotProfile.fromStoredValue(null))
        assertEquals(MascotProfile.NONE, MascotProfile.fromStoredValue("missing"))
        assertEquals(MascotProfile.DORO, MascotProfile.fromStoredValue("DORO"))
    }

    @Test
    fun volumePercentIsClamped() {
        assertEquals(0, MascotStore.clampVolumePercent(-1))
        assertEquals(60, MascotStore.clampVolumePercent(60))
        assertEquals(100, MascotStore.clampVolumePercent(120))
    }

    @Test
    fun voiceSlotsDefineDistinctScenes() {
        assertEquals(9, MascotVoiceSlot.values().size)
        assertEquals("拦截出现", MascotVoiceSlot.BLOCK_APPEARED.displayName())
        assertEquals("挑战通过", MascotVoiceSlot.CHALLENGE_PASSED.displayName())
        assertEquals("手机时长到", MascotVoiceSlot.PHONE_LIMIT_REACHED.displayName())
        assertEquals("权限异常", MascotVoiceSlot.PERMISSION_ISSUE.displayName())
        assertEquals("管控应用", MascotVoiceSlot.CONTROL_APPS.displayName())
        assertEquals("已管控应用", MascotVoiceSlot.ACTIVE_APPS.displayName())
        assertEquals("添加应用", MascotVoiceSlot.ADD_APPS.displayName())
        assertEquals("今日报告", MascotVoiceSlot.TODAY_REPORT.displayName())
        assertEquals("拦截规则", MascotVoiceSlot.APP_RULE.displayName())
    }
}
