package com.example.inventory.property

import com.example.inventory.domain.Quantity
import com.example.inventory.domain.Sku
import com.example.inventory.infrastructure.nativebridge.VerifiedReservationNative
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class ReservationPropertiesTest : FunSpec({
    val validInventory = Arb.long(0L..1_000_000L).flatMap { onHand ->
        Arb.element(
            0L,
            1L.coerceAtMost(onHand),
            onHand / 2,
            (onHand - 1).coerceAtLeast(0),
            onHand,
        ).map { reserved -> onHand to reserved }
    }

    test("available stays within zero and on-hand stock") {
        checkAll(iterations = 1_000, validInventory) { (onHand, reserved) ->
            val available = VerifiedReservationNative.available(onHand, reserved)

            available.shouldBeBetween(0L, onHand)
        }
    }

    test("a request at or below capacity preserves reservation capacity") {
        checkAll(iterations = 1_000, validInventory) { (onHand, reserved) ->
            // This expression is an independent arithmetic precondition, not a
            // call to the production `available` implementation.
            val capacity = onHand - reserved
            val requested = capacity / 2

            val newReserved = VerifiedReservationNative.reserve(onHand, reserved, requested)
            newReserved!!.shouldBeLessThanOrEqual(onHand)
            newReserved shouldBe reserved + requested
        }
    }

    test("a request one beyond capacity is rejected") {
        checkAll(iterations = 1_000, validInventory) { (onHand, reserved) ->
            val capacity = onHand - reserved
            if (capacity < 1_000_000L) {
                val tooLarge = capacity + 1
                VerifiedReservationNative.canReserve(onHand, reserved, tooLarge).shouldBeFalse()
                VerifiedReservationNative.reserve(onHand, reserved, tooLarge) shouldBe null
            }
        }
    }

    test("cancelling a reservation restores its prior availability") {
        checkAll(iterations = 1_000, validInventory) { (onHand, reserved) ->
            val capacity = onHand - reserved
            val requested = capacity / 2
            val before = onHand - reserved
            val afterReserve = VerifiedReservationNative.reserve(onHand, reserved, requested)!!
            val afterCancel = VerifiedReservationNative.cancel(afterReserve, requested)

            (onHand - afterCancel) shouldBe before
        }
    }

    test("confirmed and cancelled orders stay terminal across all application commands") {
        checkAll(iterations = 1_000, Arb.long(1L..1_000L), Arb.boolean()) { quantityValue, confirmFirst ->
            val sku = Sku.of("sku-terminal")!!
            val quantity = Quantity.of(quantityValue)!!
            val draft = com.example.inventory.domain.Order.create(
                com.example.inventory.domain.OrderId.of("terminal-$quantityValue-$confirmFirst")!!,
                sku,
                quantity,
            )
            val order = if (confirmFirst) {
                val reserved = (draft.reserve() as com.example.inventory.domain.DomainResult.Accepted).value
                (reserved.confirm() as com.example.inventory.domain.DomainResult.Accepted).value
            } else {
                (draft.cancel() as com.example.inventory.domain.DomainResult.Accepted).value
            }

            (order.reserve() is com.example.inventory.domain.DomainResult.Rejected) shouldBe true
            (order.confirm() is com.example.inventory.domain.DomainResult.Rejected) shouldBe true
            (order.cancel() is com.example.inventory.domain.DomainResult.Rejected) shouldBe true
        }
    }
})
