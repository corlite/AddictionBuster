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
}
