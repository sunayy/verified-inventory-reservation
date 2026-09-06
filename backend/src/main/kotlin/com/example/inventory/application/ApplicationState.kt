package com.example.inventory.application

import com.example.inventory.domain.Inventory
import com.example.inventory.domain.Order
import com.example.inventory.mbt.ModelOrder
import com.example.inventory.mbt.ModelOrderStatus
import com.example.inventory.mbt.ModelState

data class ApplicationState(val inventory: Collection<Inventory>, val orders: Collection<Order>) {
    /** The only comparison representation shared with the Quint model. */
    fun toModelProjection(): ModelState = ModelState(
        onHand = inventory.associate { it.sku.value to it.onHand },
        reserved = inventory.associate { it.sku.value to it.reserved },
        sold = inventory.associate { it.sku.value to it.sold },
        orders = orders.associate { order ->
            order.id.value to ModelOrder(
                id = order.id.value,
                sku = order.sku.value,
                quantity = order.quantity.value,
                status = ModelOrderStatus.valueOf(order.status.name),
            )
        },
    )
}

fun ReservationService.snapshot(): ApplicationState = ApplicationState(
    inventory = inventorySnapshot(),
    orders = orderSnapshot(),
)
