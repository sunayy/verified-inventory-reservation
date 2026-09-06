package com.example.inventory.domain

enum class OrderStatus { DRAFT, RESERVED, CONFIRMED, CANCELLED }

sealed interface DomainError {
    data class OrderNotFound(val orderId: OrderId) : DomainError
    data class InventoryNotFound(val sku: Sku) : DomainError
    data class AlreadyReserved(val orderId: OrderId) : DomainError
    data class InsufficientStock(val sku: Sku, val requested: Quantity) : DomainError
    data class InvalidTransition(val orderId: OrderId, val from: OrderStatus, val command: String) : DomainError
    data class AlreadyTerminal(val orderId: OrderId, val status: OrderStatus) : DomainError
}

sealed interface DomainResult<out T> {
    data class Accepted<T>(val value: T) : DomainResult<T>
    data class Rejected(val error: DomainError) : DomainResult<Nothing>
}

class Order private constructor(
    val id: OrderId,
    val sku: Sku,
    val quantity: Quantity,
    val status: OrderStatus,
) {
    fun reserve(): DomainResult<Order> = transition("reserve", OrderStatus.RESERVED)
    fun confirm(): DomainResult<Order> = transition("confirm", OrderStatus.CONFIRMED)
    fun cancel(): DomainResult<Order> = when (status) {
        OrderStatus.DRAFT, OrderStatus.RESERVED -> DomainResult.Accepted(withStatus(OrderStatus.CANCELLED))
        OrderStatus.CONFIRMED, OrderStatus.CANCELLED -> DomainResult.Rejected(DomainError.AlreadyTerminal(id, status))
    }

    private fun transition(command: String, target: OrderStatus): DomainResult<Order> = when (status) {
        OrderStatus.DRAFT -> when (target) {
            OrderStatus.RESERVED -> DomainResult.Accepted(withStatus(target))
            else -> DomainResult.Rejected(DomainError.InvalidTransition(id, status, command))
        }
        OrderStatus.RESERVED -> when (target) {
            OrderStatus.CONFIRMED -> DomainResult.Accepted(withStatus(target))
            else -> DomainResult.Rejected(DomainError.AlreadyReserved(id))
        }
        OrderStatus.CONFIRMED, OrderStatus.CANCELLED -> DomainResult.Rejected(DomainError.AlreadyTerminal(id, status))
    }

    private fun withStatus(status: OrderStatus): Order = Order(id, sku, quantity, status)

    companion object {
        fun create(id: OrderId, sku: Sku, quantity: Quantity): Order =
            Order(id, sku, quantity, OrderStatus.DRAFT)
    }
}
