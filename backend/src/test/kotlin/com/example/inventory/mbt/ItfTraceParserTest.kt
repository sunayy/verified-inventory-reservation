package com.example.inventory.mbt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ItfTraceParserTest {
    private val parser = ItfTraceParser()

    @Test
    fun `decodes Quint MBT actions and projection states without replaying model logic`() {
        val json = checkNotNull(javaClass.classLoader.getResource("itf/inventory-trace.itf.json"))
            .readText()
        val trace = parser.parse(json)

        assertEquals("spec/inventory.qnt", trace.source)
        assertEquals(3, trace.steps.size)
        assertNull(trace.steps[0].action)
        assertEquals(5, trace.steps[0].expected.onHand.getValue("sku-red"))

        val create = assertIs<ModelAction.CreateOrder>(trace.steps[1].action)
        assertEquals("order-1", create.orderId)
        assertEquals("sku-red", create.sku)
        assertEquals(3, create.quantity)
        assertEquals(ModelOrderStatus.DRAFT, trace.steps[1].expected.orders.getValue("order-1").status)

        val reserve = assertIs<ModelAction.Reserve>(trace.steps[2].action)
        assertEquals("order-1", reserve.orderId)
        assertEquals(3, trace.steps[2].expected.reserved.getValue("sku-red"))
        assertEquals(ModelOrderStatus.RESERVED, trace.steps[2].expected.orders.getValue("order-1").status)
    }
}
