package com.example.inventory.http

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `creates reserves confirms and projects inventory through HTTP`() {
        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"orderId":"http-order-1","sku":"sku-red","quantity":3}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("DRAFT"))

        mockMvc.perform(post("/orders/http-order-1/reserve"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RESERVED"))

        mockMvc.perform(post("/orders/http-order-1/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))

        mockMvc.perform(get("/inventory/sku-red"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.onHand").value(2))
            .andExpect(jsonPath("$.reserved").value(0))
            .andExpect(jsonPath("$.sold").value(3))
    }

    @Test
    fun `maps insufficient stock to a closed HTTP conflict response`() {
        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"orderId":"http-order-too-large","sku":"sku-blue","quantity":5}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(post("/orders/http-order-too-large/reserve"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("insufficient_stock"))
    }
}
