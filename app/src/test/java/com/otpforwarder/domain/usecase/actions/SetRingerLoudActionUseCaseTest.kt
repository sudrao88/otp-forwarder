package com.otpforwarder.domain.usecase.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetRingerLoudActionUseCaseTest {

    /**
     * Scriptable [RingerSystem] — each side-effecting call increments a counter and
     * returns the configured answer.
     */
    private class FakeRingerSystem(
        var dndActive: Boolean = false,
        var canBypass: Boolean = true,
        var bypassReturns: Boolean = true,
        var setRingerReturns: Boolean = true
    ) : RingerSystem {
        var bypassCalls = 0
        var setRingerCalls = 0
        var raiseVolumeCalls = 0

        override fun isDndActive(): Boolean = dndActive
        override fun canBypassDnd(): Boolean = canBypass
        override fun bypassDnd(): Boolean {
            bypassCalls++
            return bypassReturns
        }
        override fun setRingerModeNormal(): Boolean {
            setRingerCalls++
            return setRingerReturns
        }
        override fun raiseRingerVolumeToMax() {
            raiseVolumeCalls++
        }
    }

    @Test
    fun `DND off, ringer change succeeds — result is a truthful success`() {
        val system = FakeRingerSystem(dndActive = false, canBypass = true)
        val result = SetRingerLoudActionUseCase(system)()
        assertTrue(result.ringerChanged)
        assertFalse(result.dndWasActive)
        assertTrue(result.success)
        assertEquals(1, system.setRingerCalls)
        assertEquals(1, system.raiseVolumeCalls)
    }

    @Test
    fun `DND on, policy access granted, bypass succeeds — success`() {
        val system = FakeRingerSystem(dndActive = true, canBypass = true, bypassReturns = true)
        val result = SetRingerLoudActionUseCase(system)()
        assertTrue(result.dndWasActive)
        assertTrue(result.bypassedDnd)
        assertTrue(result.ringerChanged)
        assertTrue(result.success)
        assertEquals(1, system.bypassCalls)
    }

    @Test
    fun `DND on, policy access NOT granted — bypass skipped, success is false`() {
        val system = FakeRingerSystem(dndActive = true, canBypass = false)
        val result = SetRingerLoudActionUseCase(system)()
        assertTrue(result.dndWasActive)
        assertFalse(result.bypassedDnd)
        assertTrue(result.ringerChanged)
        // DND silenced the ringer — don't lie in the log.
        assertFalse(result.success)
        assertEquals(0, system.bypassCalls)
    }

    @Test
    fun `DND on, bypass attempted but fails — success is false`() {
        val system = FakeRingerSystem(dndActive = true, canBypass = true, bypassReturns = false)
        val result = SetRingerLoudActionUseCase(system)()
        assertTrue(result.dndWasActive)
        assertFalse(result.bypassedDnd)
        assertFalse(result.success)
        assertEquals(1, system.bypassCalls)
    }

    @Test
    fun `ringer mode flip fails — result is failure regardless of DND state`() {
        val system = FakeRingerSystem(dndActive = false, setRingerReturns = false)
        val result = SetRingerLoudActionUseCase(system)()
        assertFalse(result.ringerChanged)
        assertFalse(result.success)
    }

    @Test
    fun `ringer-mode flip and volume-raise run as independent calls`() {
        // The RingerSystem seam guarantees that a setStreamVolume failure inside
        // raiseRingerVolumeToMax can't flip ringerChanged back to false: the flags
        // are reported by setRingerModeNormal alone.
        val system = FakeRingerSystem(setRingerReturns = true)
        val result = SetRingerLoudActionUseCase(system)()
        assertTrue(result.ringerChanged)
        assertEquals(1, system.setRingerCalls)
        assertEquals(1, system.raiseVolumeCalls)
    }
}
