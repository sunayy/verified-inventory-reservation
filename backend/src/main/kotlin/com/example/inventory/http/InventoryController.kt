package com.example.inventory.http

import com.example.inventory.application.CancelOrder
import com.example.inventory.application.ConfirmOrder
import com.example.inventory.application.CreateOrder
import com.example.inventory.application.ReservationService
import com.example.inventory.application.ReserveOrder
import com.example.inventory.domain.DomainError
import com.example.inventory.domain.DomainResult
import com.example.inventory.domain.Inventory
import com.example.inventory.domain.Order
import com.example.inventory.domain.OrderId
import com.example.inventory.domain.Quantity
import com.example.inventory.domain.Sku
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

data class CreateOrderRequest(val orderId: String, val sku: String, val quantity: Long)
data class OrderResponse(val id: String, val sku: String, val quantity: Long, val status: String)
data class InventoryResponse(val sku: String, val onHand: Long, val reserved: Long, val sold: Long)
data class ApiError(val code: String, val message: String)

@RestController
@RequestMapping
class InventoryController(private val service: ReservationService) {
    @PostMapping("/orders")
    fun create(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            service.create(
                CreateOrder(
                    orderId = request.orderId.orderId(),
                    sku = request.sku.sku(),
                    quantity = request.quantity.quantity(),
                ),
            ).orThrow().toResponse(),
        )

    @PostMapping("/orders/{id}/reserve")
    fun reserve(@PathVariable id: String): OrderResponse =
        service.reserve(ReserveOrder(id.orderId())).orThrow().toResponse()

    @PostMapping("/orders/{id}/confirm")
    fun confirm(@PathVariable id: String): OrderResponse =
        service.confirm(ConfirmOrder(id.orderId())).orThrow().toResponse()

    @PostMapping("/orders/{id}/cancel")
    fun cancel(@PathVariable id: String): OrderResponse =
        service.cancel(CancelOrder(id.orderId())).orThrow().toResponse()

    @GetMapping("/orders/{id}")
    fun getOrder(@PathVariable id: String): OrderResponse =
        service.getOrder(id.orderId()).orThrow().toResponse()

    @GetMapping("/inventory/{sku}")
    fun getInventory(@PathVariable sku: String): InventoryResponse =
        service.getInventory(sku.sku()).orThrow().toResponse()

    private fun String.orderId(): OrderId = OrderId.of(this) ?: throw InvalidRequest("orderId must be non-blank")
    private fun String.sku(): Sku = Sku.of(this) ?: throw InvalidRequest("sku must be non-blank")
    private fun Long.quantity(): Quantity = Quantity.of(this) ?: throw InvalidRequest("quantity must be positive")
    private fun Order.toResponse() = OrderResponse(id.value, sku.value, quantity.value, status.name)
    private fun Inventory.toResponse() = InventoryResponse(sku.value, onHand, reserved, sold)
}

private fun <T> DomainResult<T>.orThrow(): T = when (this) {
    is DomainResult.Accepted -> value
    is DomainResult.Rejected -> throw DomainHttpFailure(error)
}

class DomainHttpFailure(val error: DomainError) : RuntimeException()
class InvalidRequest(message: String) : RuntimeException(message)

@RestControllerAdvice
class HttpErrorHandler {
    @ExceptionHandler(DomainHttpFailure::class)
    fun domainFailure(error: DomainHttpFailure): ResponseEntity<ApiError> = when (val domain = error.error) {
        is DomainError.OrderNotFound, is DomainError.InventoryNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError("not_found", domain.toString()))
        is DomainError.InsufficientStock -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError("insufficient_stock", domain.toString()))
        is DomainError.AlreadyReserved, is DomainError.InvalidTransition, is DomainError.AlreadyTerminal ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("invalid_transition", domain.toString()))
    }

    @ExceptionHandler(InvalidRequest::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidRequest(error: InvalidRequest) = ApiError("invalid_request", error.message ?: "invalid request")
}
