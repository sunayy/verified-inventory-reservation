package com.example.inventory.domain

@JvmInline
value class OrderId private constructor(val value: String) {
    companion object {
        fun of(value: String): OrderId? = value.takeIf { it.isNotBlank() }?.let(::OrderId)
    }
}

@JvmInline
value class Sku private constructor(val value: String) {
    companion object {
        fun of(value: String): Sku? = value.takeIf { it.isNotBlank() }?.let(::Sku)
    }
}

@JvmInline
value class Quantity private constructor(val value: Long) {
    companion object {
        fun of(value: Long): Quantity? = value.takeIf { it > 0 }?.let(::Quantity)
    }
}
