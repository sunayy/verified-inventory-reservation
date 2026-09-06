package com.example.inventory.infrastructure

import com.example.inventory.application.InMemoryInventoryRepository
import com.example.inventory.application.InMemoryOrderRepository
import com.example.inventory.application.InventoryRepository
import com.example.inventory.application.OrderRepository
import com.example.inventory.application.ReservationService
import com.example.inventory.domain.Inventory
import com.example.inventory.domain.ReservationArithmetic
import com.example.inventory.domain.Sku
import com.example.inventory.infrastructure.nativebridge.VerifiedReservationNative
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringConfiguration {
    @Bean fun reservationArithmetic(): ReservationArithmetic = VerifiedReservationNative

    @Bean fun orderRepository(): OrderRepository = InMemoryOrderRepository()

    @Bean fun inventoryRepository(): InventoryRepository = InMemoryInventoryRepository(
        listOf(
            Inventory(requireNotNull(Sku.of("sku-red")), onHand = 5),
            Inventory(requireNotNull(Sku.of("sku-blue")), onHand = 4),
        ),
    )

    @Bean fun reservationService(
        orders: OrderRepository,
        inventory: InventoryRepository,
        arithmetic: ReservationArithmetic,
    ) = ReservationService(orders, inventory, arithmetic)
}
