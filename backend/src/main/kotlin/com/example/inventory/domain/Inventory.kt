package com.example.inventory.domain

interface ReservationArithmetic {
    fun available(onHand: Long, reserved: Long): Long
    fun canReserve(onHand: Long, reserved: Long, requested: Long): Boolean
    fun reserve(onHand: Long, reserved: Long, requested: Long): Long?
    fun cancel(reserved: Long, quantity: Long): Long
}

data class Inventory(
    val sku: Sku,
    val onHand: Long,
    val reserved: Long = 0,
    val sold: Long = 0,
) {
    init {
        require(onHand >= 0 && reserved >= 0 && sold >= 0)
        require(reserved <= onHand)
        require(onHand + sold >= onHand)
    }

    fun reserve(quantity: Quantity, arithmetic: ReservationArithmetic): DomainResult<Inventory> {
        val newReserved = arithmetic.reserve(onHand, reserved, quantity.value)
            ?: return DomainResult.Rejected(DomainError.InsufficientStock(sku, quantity))
        return DomainResult.Accepted(copy(reserved = newReserved))
    }

    fun confirm(quantity: Quantity, arithmetic: ReservationArithmetic): Inventory {
        val released = arithmetic.cancel(reserved, quantity.value)
        return copy(onHand = onHand - quantity.value, reserved = released, sold = sold + quantity.value)
    }

    fun cancel(quantity: Quantity, arithmetic: ReservationArithmetic): Inventory =
        copy(reserved = arithmetic.cancel(reserved, quantity.value))
}
