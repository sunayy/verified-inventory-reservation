import type { Command, Query } from "../dsl/ast"
import type {
  ApiFailure,
  InventoryQueryRequest,
  InventoryView,
  OrderView,
  ReserveOrderRequest,
} from "../dsl/inventory-contracts"

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly failure: ApiFailure,
  ) {
    super(failure.message)
  }
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ""

async function request<Response>(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(`${apiBaseUrl}${path}`, init)
  if (!response.ok) {
    const failure = (await response.json()) as ApiFailure
    throw new ApiRequestError(response.status, failure)
  }
  return response.json() as Promise<Response>
}

/** The sole frontend module allowed to turn opaque DSL IDs into HTTP calls. */
export const executeQuery = async <Request, Response>(query: Query<Request, Response>): Promise<Response> => {
  switch (query.id) {
    case "inventory.bySku": {
      const input = query.request as InventoryQueryRequest
      return request<InventoryView>(`/inventory/${encodeURIComponent(input.sku)}`) as Promise<Response>
    }
    default:
      throw new Error(`Unsupported query: ${query.id}`)
  }
}

export const executeCommand = async <Request, Response, Failure>(
  command: Command<Request, Response, Failure>,
  payload: Request,
): Promise<Response> => {
  switch (command.id) {
    case "orders.reserve": {
      const input = payload as ReserveOrderRequest
      return request<OrderView>(`/orders/${encodeURIComponent(input.orderId)}/reserve`, { method: "POST" }) as Promise<Response>
    }
    default:
      throw new Error(`Unsupported command: ${command.id}`)
  }
}
