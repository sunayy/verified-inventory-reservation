package com.example.inventory.infrastructure.nativebridge

import com.example.inventory.domain.ReservationArithmetic

/**
 * The only Kotlin entry point to Lean reservation arithmetic. Signed JVM longs
 * are rejected before crossing into the Lean Nat domain; a native load/link
 * failure is intentionally an infrastructure exception, never a DomainError.
 */
object VerifiedReservationNative : ReservationArithmetic {
    init {
        System.loadLibrary("reservation_jni")
    }

    override fun available(onHand: Long, reserved: Long): Long {
        validate(onHand, reserved)
        return available0(onHand, reserved)
    }

    override fun canReserve(onHand: Long, reserved: Long, requested: Long): Boolean {
        validate(onHand, reserved, requested)
        return canReserve0(onHand, reserved, requested)
    }

    override fun reserve(onHand: Long, reserved: Long, requested: Long): Long? {
        validate(onHand, reserved, requested)
        return reserve0(onHand, reserved, requested).takeIf { it >= 0 }
    }

    override fun cancel(reserved: Long, quantity: Long): Long {
        validate(reserved, quantity)
        return cancel0(reserved, quantity)
    }

    private fun validate(vararg values: Long) {
        require(values.all { it >= 0 }) { "Lean Nat inputs must be non-negative" }
    }

    @JvmStatic private external fun available0(onHand: Long, reserved: Long): Long
    @JvmStatic private external fun canReserve0(onHand: Long, reserved: Long, requested: Long): Boolean
    @JvmStatic private external fun reserve0(onHand: Long, reserved: Long, requested: Long): Long
    @JvmStatic private external fun cancel0(reserved: Long, quantity: Long): Long
}
