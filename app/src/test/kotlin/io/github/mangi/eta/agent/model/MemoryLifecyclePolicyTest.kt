package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLifecyclePolicyTest {

    @Test
    fun isExpired_usesTtl() {
        val now = 1_000_000L
        assertTrue(MemoryLifecyclePolicy.isExpired(now - MemoryLifecyclePolicy.DEFAULT_ATOM_TTL_MS - 1, now))
        assertFalse(MemoryLifecyclePolicy.isExpired(now - MemoryLifecyclePolicy.DEFAULT_ATOM_TTL_MS + 1, now))
    }

    @Test
    fun weightAfterUse_boostsAndClamps() {
        assertEquals(1.0, MemoryLifecyclePolicy.weightAfterUse(0.5), 1e-6)
        assertEquals(1.0, MemoryLifecyclePolicy.weightAfterUse(1.0), 1e-6)
    }

    @Test
    fun weightAfterDecay_decaysAndClamps() {
        assertEquals(0.5, MemoryLifecyclePolicy.weightAfterDecay(1.0, 10L * 24 * 60 * 60 * 1000), 1e-6)
        assertEquals(0.0, MemoryLifecyclePolicy.weightAfterDecay(0.1, 30L * 24 * 60 * 60 * 1000), 1e-6)
    }

    @Test
    fun isNoise_belowThreshold() {
        assertTrue(MemoryLifecyclePolicy.isNoise(0.04))
        assertFalse(MemoryLifecyclePolicy.isNoise(0.1))
    }
}
