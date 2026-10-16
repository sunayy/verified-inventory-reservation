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

export interface CreateOrderRequest {
  readonly orderId: string
  readonly sku: string
  readonly quantity: number
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
export const inventoryBySku = (sku: string) => query<InventoryQueryRequest, InventoryView>("inventory.bySku", { sku })
export const orderById = (orderId: string) => query<ReserveOrderRequest, OrderView>("orders.byId", { orderId })
export const inventoryQuery = inventoryBySku("sku-red")
export const createOrder = command<CreateOrderRequest, OrderView, ApiFailure>("orders.create")
export const reserveOrder = command<ReserveOrderRequest, OrderView, ApiFailure>("orders.reserve")
export const confirmOrder = command<ReserveOrderRequest, OrderView, ApiFailure>("orders.confirm")
export const cancelOrder = command<ReserveOrderRequest, OrderView, ApiFailure>("orders.cancel")
