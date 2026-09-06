// Inventory Reservation System — bounded executable specification.
//
// This module is the source of truth for reservation transition semantics. The
// finite ID/SKU/quantity domains make both simulation and bounded model checking
// tractable. `orders` stores the lifecycle; orderSku and orderQuantity are kept
// as total maps so the ITF trace can be decoded without partial-map handling.
module inventory {
  type OrderStatus = Missing | Draft | Reserved | Confirmed | Cancelled

  pure val SKUS = Set("sku-red", "sku-blue")
  pure val ORDER_IDS = Set("order-1", "order-2", "order-3")
  pure val QUANTITIES = 1.to(3)
  pure val INITIAL_STOCK = Map("sku-red" -> 5, "sku-blue" -> 4)

  var onHand: str -> int
  var reserved: str -> int
  var sold: str -> int
  var orders: str -> OrderStatus
  var orderSku: str -> str
  var orderQuantity: str -> int
  // History variable: once true, an order is required to remain terminal.
  // This makes the absorbing-state rule independently checkable as an invariant.
  var terminalOrders: str -> bool

  def available(sku) = onHand.get(sku) - reserved.get(sku)

  def reservedOrderQuantityFor(sku) =
    ORDER_IDS.fold(0, (total, orderId) =>
      if (orders.get(orderId) == Reserved and orderSku.get(orderId) == sku)
        total + orderQuantity.get(orderId)
      else
        total
    )

  action init = all {
    onHand' = INITIAL_STOCK,
    reserved' = SKUS.mapBy(_ => 0),
    sold' = SKUS.mapBy(_ => 0),
    orders' = ORDER_IDS.mapBy(_ => Missing),
    orderSku' = ORDER_IDS.mapBy(_ => "sku-red"),
    orderQuantity' = ORDER_IDS.mapBy(_ => 0),
    terminalOrders' = ORDER_IDS.mapBy(_ => false),
  }

  // Parameters are selected inside the named action. With `--mbt`, Quint emits
  // the selected values in `mbt::nondetPicks` alongside `mbt::actionTaken`.
  action createOrder = {
    nondet orderId = ORDER_IDS.filter(id => orders.get(id) == Missing).oneOf()
    nondet sku = SKUS.oneOf()
    nondet quantity = QUANTITIES.oneOf()
    all {
      onHand' = onHand,
      reserved' = reserved,
      sold' = sold,
      orders' = orders.set(orderId, Draft),
      orderSku' = orderSku.set(orderId, sku),
      orderQuantity' = orderQuantity.set(orderId, quantity),
      terminalOrders' = terminalOrders,
    }
  }

  action reserve = {
    nondet orderId = ORDER_IDS.filter(id => orders.get(id) == Draft).oneOf()
    val sku = orderSku.get(orderId)
    val quantity = orderQuantity.get(orderId)
    all {
      quantity <= available(sku),
      onHand' = onHand,
      reserved' = reserved.set(sku, reserved.get(sku) + quantity),
      sold' = sold,
      orders' = orders.set(orderId, Reserved),
      orderSku' = orderSku,
      orderQuantity' = orderQuantity,
      terminalOrders' = terminalOrders,
    }
  }

  action confirm = {
    nondet orderId = ORDER_IDS.filter(id => orders.get(id) == Reserved).oneOf()
    val sku = orderSku.get(orderId)
    val quantity = orderQuantity.get(orderId)
    all {
      onHand' = onHand.set(sku, onHand.get(sku) - quantity),
      reserved' = reserved.set(sku, reserved.get(sku) - quantity),
      sold' = sold.set(sku, sold.get(sku) + quantity),
      orders' = orders.set(orderId, Confirmed),
      orderSku' = orderSku,
      orderQuantity' = orderQuantity,
      terminalOrders' = terminalOrders.set(orderId, true),
    }
  }

  action cancel = {
    nondet orderId = ORDER_IDS.filter(id =>
      orders.get(id) == Draft or orders.get(id) == Reserved
    ).oneOf()
    val sku = orderSku.get(orderId)
    val quantity = orderQuantity.get(orderId)
    all {
      onHand' = onHand,
      reserved' = if (orders.get(orderId) == Reserved) {
        reserved.set(sku, reserved.get(sku) - quantity)
      } else {
        reserved
      },
      sold' = sold,
      orders' = orders.set(orderId, Cancelled),
      orderSku' = orderSku,
      orderQuantity' = orderQuantity,
      terminalOrders' = terminalOrders.set(orderId, true),
    }
  }

  action step = any {
    createOrder,
    reserve,
    confirm,
    cancel,
  }

  val StockNonNegative = SKUS.forall(sku => onHand.get(sku) >= 0)

  val ReservationWithinStock = SKUS.forall(sku =>
    reserved.get(sku) >= 0 and reserved.get(sku) <= onHand.get(sku)
  )

  val StockConservation = SKUS.forall(sku =>
    INITIAL_STOCK.get(sku) == onHand.get(sku) + sold.get(sku)
  )

  val ReservationConsistency = SKUS.forall(sku =>
    reserved.get(sku) == reservedOrderQuantityFor(sku)
  )

  val OrderWellFormed = ORDER_IDS.forall(orderId =>
    orders.get(orderId) == Missing or
      (SKUS.contains(orderSku.get(orderId)) and
        QUANTITIES.contains(orderQuantity.get(orderId)))
  )

  // Terminal orders are absent from every transition's candidate set. The
  // history variable means an accidental future transition out of a terminal
  // state violates this invariant instead of merely changing a trace.
  val TerminalState = ORDER_IDS.forall(orderId =>
    terminalOrders.get(orderId) == false or
      orders.get(orderId) == Confirmed or orders.get(orderId) == Cancelled
  )
}
