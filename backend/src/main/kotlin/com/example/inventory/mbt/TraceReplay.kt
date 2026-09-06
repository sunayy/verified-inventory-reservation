package com.example.inventory.mbt

import com.example.inventory.application.CancelOrder
import com.example.inventory.application.ConfirmOrder
import com.example.inventory.application.CreateOrder
import com.example.inventory.application.InMemoryInventoryRepository
import com.example.inventory.application.InMemoryOrderRepository
import com.example.inventory.application.ReservationService
import com.example.inventory.application.ReserveOrder
import com.example.inventory.application.snapshot
import com.example.inventory.domain.DomainResult
import com.example.inventory.domain.Inventory
import com.example.inventory.domain.OrderId
import com.example.inventory.domain.Quantity
import com.example.inventory.domain.ReservationArithmetic
import com.example.inventory.domain.Sku

class TraceReplayFailure(
    traceFile: String,
    seed: String,
    step: TraceStep,
    expected: ModelState,
    actual: ModelState,
) : AssertionError(
    """
    |Trace: $traceFile
    |Seed: $seed
    |Step: ${step.index}
    |Action: ${step.action}
    |
    |Expected:
    |$expected
    |
    |Actual:
    |$actual
    """.trimMargin(),
)

/** Replays trace data only; expected states always come from Quint ITF. */
class TraceReplayer(private val arithmetic: ReservationArithmetic) {
    fun replay(traceFile: String, seed: String, trace: ItfTrace) {
        val initial = trace.steps.first().expected
        val service = ReservationService(
            orders = InMemoryOrderRepository(),
            inventory = InMemoryInventoryRepository(initial.inventory()),
            arithmetic = arithmetic,
        )
        assertProjection(traceFile, seed, trace.steps.first(), service)

        trace.steps.drop(1).forEach { step ->
            val action = requireNotNull(step.action) { "Only the first trace step may be init" }
            val outcome = when (action) {
                is ModelAction.CreateOrder -> service.create(
                    CreateOrder(action.orderId.orderId(), action.sku.sku(), action.quantity.quantity()),
                )
                is ModelAction.Reserve -> service.reserve(ReserveOrder(action.orderId.orderId()))
                is ModelAction.Confirm -> service.confirm(ConfirmOrder(action.orderId.orderId()))
                is ModelAction.Cancel -> service.cancel(CancelOrder(action.orderId.orderId()))
            }
            if (outcome is DomainResult.Rejected) {
                throw AssertionError("Trace $traceFile step ${step.index} rejected $action: ${outcome.error}")
            }
            assertProjection(traceFile, seed, step, service)
        }
    }

    private fun assertProjection(traceFile: String, seed: String, step: TraceStep, service: ReservationService) {
        val actual = service.snapshot().toModelProjection()
        if (actual != step.expected) {
            throw TraceReplayFailure(traceFile, seed, step, step.expected, actual)
        }
    }

    private fun ModelState.inventory(): List<Inventory> = onHand.map { (sku, onHand) ->
        Inventory(
            sku = sku.sku(),
            onHand = onHand,
            reserved = reserved.getValue(sku),
            sold = sold.getValue(sku),
        )
    }

    private fun String.orderId(): OrderId = requireNotNull(OrderId.of(this))
    private fun String.sku(): Sku = requireNotNull(Sku.of(this))
    private fun Long.quantity(): Quantity = requireNotNull(Quantity.of(this))
}
