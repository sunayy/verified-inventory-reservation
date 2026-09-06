package com.example.inventory.application

import com.example.inventory.domain.Inventory
import com.example.inventory.domain.Order
import com.example.inventory.domain.OrderId
import com.example.inventory.domain.Sku

interface OrderRepository {
    fun find(id: OrderId): Order?
    fun save(order: Order)
    fun all(): Collection<Order>
}

interface InventoryRepository {
    fun find(sku: Sku): Inventory?
    fun save(inventory: Inventory)
    fun all(): Collection<Inventory>
}

class InMemoryOrderRepository : OrderRepository {
    private val orders = linkedMapOf<OrderId, Order>()
    override fun find(id: OrderId): Order? = orders[id]
    override fun save(order: Order) {
        orders[order.id] = order
    }
    override fun all(): Collection<Order> = orders.values.toList()
}

class InMemoryInventoryRepository(initial: Iterable<Inventory>) : InventoryRepository {
    private val inventory = initial.associateByTo(linkedMapOf()) { it.sku }
    override fun find(sku: Sku): Inventory? = inventory[sku]
    override fun save(inventory: Inventory) {
        this.inventory[inventory.sku] = inventory
    }
    override fun all(): Collection<Inventory> = inventory.values.toList()
}
