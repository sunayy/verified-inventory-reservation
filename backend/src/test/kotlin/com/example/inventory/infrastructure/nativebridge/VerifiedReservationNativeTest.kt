package com.example.inventory.infrastructure.nativebridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VerifiedReservationNativeTest {
    @Test
    fun `executes exported Lean definitions through JNI`() {
        assertEquals(7, VerifiedReservationNative.available(10, 3))
        assertTrue(VerifiedReservationNative.canReserve(10, 3, 7))
        assertFalse(VerifiedReservationNative.canReserve(10, 3, 8))
        assertEquals(10, VerifiedReservationNative.reserve(10, 3, 7))
        assertNull(VerifiedReservationNative.reserve(10, 3, 8))
        assertEquals(1, VerifiedReservationNative.cancel(3, 2))
    }

    @Test
    fun `rejects negative inputs before JNI`() {
        assertFailsWith<IllegalArgumentException> {
            VerifiedReservationNative.available(-1, 0)
        }
    }
}
