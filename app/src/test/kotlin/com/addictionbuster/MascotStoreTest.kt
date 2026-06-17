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
    fun voiceSlotsDefineFourDistinctScenes() {
        assertEquals(4, MascotVoiceSlot.values().size)
        assertEquals("拦截出现", MascotVoiceSlot.BLOCK_APPEARED.displayName())
        assertEquals("挑战通过", MascotVoiceSlot.CHALLENGE_PASSED.displayName())
        assertEquals("手机时长到", MascotVoiceSlot.PHONE_LIMIT_REACHED.displayName())
        assertEquals("权限异常", MascotVoiceSlot.PERMISSION_ISSUE.displayName())
    }
}
