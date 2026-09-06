import { createScreenDsl } from "./ast"
import { Interactive, ReadOnly } from "./capabilities"
import { reserveOrder } from "./inventory-contracts"

const readOnly = createScreenDsl(ReadOnly)
const interactive = createScreenDsl(Interactive)

// @ts-expect-error Read-only screens have no command/action capability.
readOnly.action("Reserve", reserveOrder, { orderId: "order-1" })

// @ts-expect-error The reserve command payload requires a string orderId.
interactive.action("Reserve", reserveOrder, { orderId: 42 })

interactive.action("Reserve", reserveOrder, { orderId: "order-1" })
