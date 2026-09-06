import { afterEach, describe, expect, it, vi } from "vitest"
import { executeCommand, executeQuery } from "./api"
import { inventoryQuery, reserveOrder } from "../dsl/inventory-contracts"

describe("network interpreter", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("maps opaque query and command identifiers to HTTP only in the interpreter", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ sku: "sku-red", onHand: 5, reserved: 0, sold: 0 }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "order-1", sku: "sku-red", quantity: 1, status: "RESERVED" }), { status: 200 }))
    vi.stubGlobal("fetch", fetchMock)

    await expect(executeQuery(inventoryQuery)).resolves.toMatchObject({ sku: "sku-red", onHand: 5 })
    await expect(executeCommand(reserveOrder, { orderId: "order-1" })).resolves.toMatchObject({ status: "RESERVED" })
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/inventory/sku-red", undefined)
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/orders/order-1/reserve", { method: "POST" })
  })
})
