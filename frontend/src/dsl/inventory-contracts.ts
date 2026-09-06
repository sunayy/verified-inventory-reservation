import { command, query } from "./ast"

export interface InventoryView {
  readonly sku: string
  readonly onHand: number
  readonly reserved: number
  readonly sold: number
}

export interface InventoryQueryRequest {
  readonly sku: string
}

export interface ReserveOrderRequest {
  readonly orderId: string
}

export interface OrderView {
  readonly id: string
  readonly sku: string
  readonly quantity: number
  readonly status: "DRAFT" | "RESERVED" | "CONFIRMED" | "CANCELLED"
}

export interface ApiFailure {
  readonly code: string
  readonly message: string
}

// These opaque identifiers are interpreted only by the future network adapter.
export const inventoryQuery = query<InventoryQueryRequest, InventoryView>("inventory.bySku", { sku: "sku-red" })
export const reserveOrder = command<ReserveOrderRequest, OrderView, ApiFailure>("orders.reserve")
