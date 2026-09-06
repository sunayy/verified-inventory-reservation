package com.example.inventory.mbt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

enum class ModelOrderStatus { DRAFT, RESERVED, CONFIRMED, CANCELLED }

data class ModelOrder(
    val id: String,
    val sku: String,
    val quantity: Long,
    val status: ModelOrderStatus,
)

data class ModelState(
    val onHand: Map<String, Long>,
    val reserved: Map<String, Long>,
    val sold: Map<String, Long>,
    /** Missing Quint order IDs are omitted: production has no corresponding order. */
    val orders: Map<String, ModelOrder>,
)

sealed interface ModelAction {
    data class CreateOrder(val orderId: String, val sku: String, val quantity: Long) : ModelAction
    data class Reserve(val orderId: String) : ModelAction
    data class Confirm(val orderId: String) : ModelAction
    data class Cancel(val orderId: String) : ModelAction
}

data class TraceStep(
    val index: Int,
    /** Null only for the model's initial state. */
    val action: ModelAction?,
    val expected: ModelState,
)

data class ItfTrace(val source: String?, val steps: List<TraceStep>)

/**
 * Decoder for Quint ITF + `--mbt` output. It only decodes recorded data; it
 * does not contain a Kotlin copy of Quint transition rules.
 */
class ItfTraceParser(private val mapper: ObjectMapper = ObjectMapper()) {
    fun parse(json: String): ItfTrace = parse(mapper.readTree(json))

    fun parse(root: JsonNode): ItfTrace {
        require(root.path("#meta").path("format").asText() == "ITF") { "Not an ITF trace" }
        val source = root.path("#meta").path("source").takeIf(JsonNode::isTextual)?.asText()
        val steps = root.requiredArray("states").map { state ->
            TraceStep(
                index = state.path("#meta").path("index").asInt(),
                action = decodeAction(state),
                expected = decodeState(state),
            )
        }
        require(steps.isNotEmpty()) { "ITF trace has no states" }
        require(steps.first().action == null) { "First ITF state must be init" }
        return ItfTrace(source, steps)
    }

    private fun decodeAction(state: JsonNode): ModelAction? {
        val action = state.required("mbt::actionTaken").asText()
        if (action == "init") return null
        val picks = state.required("mbt::nondetPicks")
        return when (action) {
            "createOrder" -> ModelAction.CreateOrder(
                orderId = picks.requiredSomeText("orderId"),
                sku = picks.requiredSomeText("sku"),
                quantity = picks.requiredSomeBigInt("quantity"),
            )
            "reserve" -> ModelAction.Reserve(picks.requiredSomeText("orderId"))
            "confirm" -> ModelAction.Confirm(picks.requiredSomeText("orderId"))
            "cancel" -> ModelAction.Cancel(picks.requiredSomeText("orderId"))
            else -> error("Unsupported Quint action: $action")
        }
    }

    private fun decodeState(state: JsonNode): ModelState {
        val statuses = state.requiredMap("orders")
        val skus = state.requiredMap("orderSku")
        val quantities = state.requiredMap("orderQuantity")
        val orders = statuses.mapNotNull { (id, node) ->
            val tag = node.required("tag").asText()
            if (tag == "Missing") null else {
                val status = ModelOrderStatus.valueOf(tag.uppercase())
                id to ModelOrder(id, skus.getValue(id).asText(), quantities.getValue(id).itfLong(), status)
            }
        }.toMap()
        return ModelState(
            onHand = state.requiredLongMap("onHand"),
            reserved = state.requiredLongMap("reserved"),
            sold = state.requiredLongMap("sold"),
            orders = orders,
        )
    }

    private fun JsonNode.requiredMap(name: String): Map<String, JsonNode> =
        required(name).requiredArray("#map").associate { entry ->
            entry[0].asText() to entry[1]
        }

    private fun JsonNode.requiredLongMap(name: String): Map<String, Long> =
        requiredMap(name).mapValues { (_, value) -> value.itfLong() }

    private fun JsonNode.requiredSomeText(name: String): String =
        requiredSome(name).asText()

    private fun JsonNode.requiredSomeBigInt(name: String): Long =
        requiredSome(name).itfLong()

    private fun JsonNode.requiredSome(name: String): JsonNode {
        val pick = required(name)
        require(pick.required("tag").asText() == "Some") { "Action parameter $name was not selected" }
        return pick.required("value")
    }

    private fun JsonNode.itfLong(): Long = required("#bigint").asText().toLong()
    private fun JsonNode.required(name: String): JsonNode = get(name) ?: error("Missing ITF field: $name")
    private fun JsonNode.requiredArray(name: String): JsonNode = required(name).also {
        require(it.isArray) { "ITF field $name must be an array" }
    }
}
