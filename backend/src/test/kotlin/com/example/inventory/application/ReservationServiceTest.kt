package com.example.inventory.application

import com.example.inventory.domain.DomainResult
import com.example.inventory.domain.Inventory
import com.example.inventory.domain.OrderStatus
import com.example.inventory.domain.OrderId
import com.example.inventory.domain.Quantity
import com.example.inventory.domain.Sku
import com.example.inventory.infrastructure.nativebridge.VerifiedReservationNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReservationServiceTest {
    @Test
    fun `reserved order confirms through the verified reservation boundary`() {
        val sku = Sku.of("sku-red")!!
        val orderId = OrderId.of("order-1")!!
        val quantity = Quantity.of(3)!!
        val inventory = InMemoryInventoryRepository(listOf(Inventory(sku, onHand = 10)))
        val service = ReservationService(InMemoryOrderRepository(), inventory, VerifiedReservationNative)

        service.create(CreateOrder(orderId, sku, quantity))
        val reserved = assertIs<DomainResult.Accepted<*>>(service.reserve(ReserveOrder(orderId))).value
        assertEquals(OrderStatus.RESERVED, (reserved as com.example.inventory.domain.Order).status)
        assertEquals(3, inventory.find(sku)!!.reserved)

        val confirmed = assertIs<DomainResult.Accepted<*>>(service.confirm(ConfirmOrder(orderId))).value
        assertEquals(OrderStatus.CONFIRMED, (confirmed as com.example.inventory.domain.Order).status)
        assertEquals(7, inventory.find(sku)!!.onHand)
        assertEquals(0, inventory.find(sku)!!.reserved)
        assertEquals(3, inventory.find(sku)!!.sold)
    }
}
