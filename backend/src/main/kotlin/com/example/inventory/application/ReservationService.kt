package com.example.inventory.application

import com.example.inventory.domain.DomainError
import com.example.inventory.domain.DomainResult
import com.example.inventory.domain.Order
import com.example.inventory.domain.OrderStatus
import com.example.inventory.domain.ReservationArithmetic

class ReservationService(
    private val orders: OrderRepository,
    private val inventory: InventoryRepository,
    private val arithmetic: ReservationArithmetic,
) {
    internal fun inventorySnapshot() = inventory.all()
    internal fun orderSnapshot() = orders.all()

    fun getOrder(orderId: com.example.inventory.domain.OrderId): DomainResult<Order> =
        orders.find(orderId)?.let { DomainResult.Accepted(it) }
            ?: DomainResult.Rejected(DomainError.OrderNotFound(orderId))

    fun getInventory(sku: com.example.inventory.domain.Sku): DomainResult<com.example.inventory.domain.Inventory> =
        inventory.find(sku)?.let { DomainResult.Accepted(it) }
            ?: DomainResult.Rejected(DomainError.InventoryNotFound(sku))

    fun create(command: CreateOrder): DomainResult<Order> {
        val order = Order.create(command.orderId, command.sku, command.quantity)
        orders.save(order)
        return DomainResult.Accepted(order)
    }

    fun reserve(command: ReserveOrder): DomainResult<Order> {
        val order = orders.find(command.orderId) ?: return DomainResult.Rejected(DomainError.OrderNotFound(command.orderId))
        val reservedOrder = when (val transition = order.reserve()) {
            is DomainResult.Accepted -> transition.value
            is DomainResult.Rejected -> return transition
        }
        val currentInventory = inventory.find(order.sku)
            ?: return DomainResult.Rejected(DomainError.InsufficientStock(order.sku, order.quantity))
        val reservedInventory = when (val result = currentInventory.reserve(order.quantity, arithmetic)) {
            is DomainResult.Accepted -> result.value
            is DomainResult.Rejected -> return result
        }
        inventory.save(reservedInventory)
        orders.save(reservedOrder)
        return DomainResult.Accepted(reservedOrder)
    }

    fun confirm(command: ConfirmOrder): DomainResult<Order> {
        val order = orders.find(command.orderId) ?: return DomainResult.Rejected(DomainError.OrderNotFound(command.orderId))
        val confirmedOrder = when (val transition = order.confirm()) {
            is DomainResult.Accepted -> transition.value
            is DomainResult.Rejected -> return transition
        }
        val currentInventory = inventory.find(order.sku)
            ?: return DomainResult.Rejected(DomainError.InsufficientStock(order.sku, order.quantity))
        inventory.save(currentInventory.confirm(order.quantity, arithmetic))
        orders.save(confirmedOrder)
        return DomainResult.Accepted(confirmedOrder)
    }

    fun cancel(command: CancelOrder): DomainResult<Order> {
        val order = orders.find(command.orderId) ?: return DomainResult.Rejected(DomainError.OrderNotFound(command.orderId))
        val cancelledOrder = when (val transition = order.cancel()) {
            is DomainResult.Accepted -> transition.value
            is DomainResult.Rejected -> return transition
        }
        if (order.status == OrderStatus.RESERVED) {
            val currentInventory = inventory.find(order.sku)
                ?: return DomainResult.Rejected(DomainError.InsufficientStock(order.sku, order.quantity))
            inventory.save(currentInventory.cancel(order.quantity, arithmetic))
        }
        orders.save(cancelledOrder)
        return DomainResult.Accepted(cancelledOrder)
    }
}
