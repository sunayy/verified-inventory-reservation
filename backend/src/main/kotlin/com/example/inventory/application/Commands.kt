package com.example.inventory.application

import com.example.inventory.domain.OrderId
import com.example.inventory.domain.Quantity
import com.example.inventory.domain.Sku

data class CreateOrder(val orderId: OrderId, val sku: Sku, val quantity: Quantity)
data class ReserveOrder(val orderId: OrderId)
data class ConfirmOrder(val orderId: OrderId)
data class CancelOrder(val orderId: OrderId)
