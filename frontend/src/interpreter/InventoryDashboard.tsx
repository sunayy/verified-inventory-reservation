import { useCallback, useEffect, useState } from "react"
import type { FormEvent, ReactElement } from "react"
import {
  cancelOrder,
  confirmOrder,
  createOrder,
  inventoryBySku,
  orderById,
  reserveOrder,
  type InventoryView,
  type OrderView,
} from "../dsl/inventory-contracts"
import { ApiRequestError, executeCommand, executeQuery } from "./api"

const skus = ["sku-red", "sku-blue"] as const

type Notice = { readonly tone: "success" | "error"; readonly message: string } | null

const statusLabel: Record<OrderView["status"], string> = {
  DRAFT: "作成済み",
  RESERVED: "予約済み",
  CONFIRMED: "確定済み",
  CANCELLED: "取消済み",
}

const japaneseError = (failure: unknown): string => {
  if (failure instanceof ApiRequestError) {
    switch (failure.failure.code) {
      case "insufficient_stock": return "予約可能な在庫が不足しています。"
      case "invalid_transition": return "この注文は現在の状態では操作できません。"
      case "not_found": return "指定した注文または SKU が見つかりません。"
      case "invalid_request": return "入力内容を確認してください。"
      default: return failure.message
    }
  }
  return failure instanceof Error ? failure.message : "通信に失敗しました。"
}

export function InventoryDashboard(): ReactElement {
  const [sku, setSku] = useState<(typeof skus)[number]>("sku-red")
  const [inventory, setInventory] = useState<InventoryView | null>(null)
  const [order, setOrder] = useState<OrderView | null>(null)
  const [orderId, setOrderId] = useState("order-1001")
  const [quantity, setQuantity] = useState("1")
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)

  const refreshInventory = useCallback(async () => {
    const response = await executeQuery(inventoryBySku(sku))
    setInventory(response)
  }, [sku])

  useEffect(() => {
    void refreshInventory().catch((failure: unknown) => setNotice({ tone: "error", message: japaneseError(failure) }))
  }, [refreshInventory])

  const run = async (work: () => Promise<OrderView>, success: string): Promise<void> => {
    setBusy(true)
    setNotice(null)
    try {
      setOrder(await work())
      await refreshInventory()
      setNotice({ tone: "success", message: success })
    } catch (failure: unknown) {
      setNotice({ tone: "error", message: japaneseError(failure) })
    } finally {
      setBusy(false)
    }
  }

  const create = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const requested = Number(quantity)
    if (!Number.isSafeInteger(requested) || requested < 1 || orderId.trim() === "") {
      setNotice({ tone: "error", message: "注文番号と 1 以上の数量を入力してください。" })
      return
    }
    void run(
      () => executeCommand(createOrder, { orderId: orderId.trim(), sku, quantity: requested }),
      "注文を作成しました。続けて在庫を予約できます。",
    )
  }

  const findOrder = () => {
    if (orderId.trim() === "") return
    setBusy(true)
    setNotice(null)
    void executeQuery(orderById(orderId.trim()))
      .then((response) => {
        setOrder(response)
        setSku(response.sku as (typeof skus)[number])
        setNotice({ tone: "success", message: "注文を読み込みました。" })
      })
      .catch((failure: unknown) => setNotice({ tone: "error", message: japaneseError(failure) }))
      .finally(() => setBusy(false))
  }

  const available = inventory === null ? "—" : inventory.onHand - inventory.reserved
  const canReserve = order?.status === "DRAFT"
  const canConfirm = order?.status === "RESERVED"
  const canCancel = order?.status === "DRAFT" || order?.status === "RESERVED"

  return (
    <main className="app-shell">
      <header className="app-header">
        <div><p className="eyebrow">INVENTORY OPERATIONS</p><h1>在庫・注文管理</h1></div>
        <button className="secondary-button" type="button" disabled={busy} onClick={() => void refreshInventory()}>在庫を更新</button>
      </header>

      <section className="sku-picker" aria-label="SKU の選択">
        {skus.map((item) => <button key={item} type="button" className={sku === item ? "sku active" : "sku"} onClick={() => setSku(item)} disabled={busy}>{item}</button>)}
      </section>

      <section className="stock-grid" aria-label="在庫状況">
        <Metric label="現在庫" value={inventory?.onHand ?? "—"} />
        <Metric label="予約済み" value={inventory?.reserved ?? "—"} />
        <Metric label="販売済み" value={inventory?.sold ?? "—"} />
        <Metric label="予約可能" value={available} accent={typeof available === "number" && available <= 1 ? "warning" : "success"} />
      </section>

      {notice !== null && <p className={`notice ${notice.tone}`} role="status">{notice.message}</p>}

      <section className="workspace">
        <form className="panel" onSubmit={create}>
          <div className="panel-heading"><p className="eyebrow">NEW ORDER</p><h2>注文を作成</h2></div>
          <label>注文番号<input value={orderId} onChange={(event) => setOrderId(event.target.value)} placeholder="例: order-1001" disabled={busy} /></label>
          <label>SKU<input value={sku} readOnly aria-readonly="true" /></label>
          <label>数量<input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} disabled={busy} /></label>
          <button className="primary-button" type="submit" disabled={busy}>注文を作成</button>
          <button className="text-button" type="button" disabled={busy || orderId.trim() === ""} onClick={findOrder}>注文番号から検索</button>
        </form>

        <section className="panel order-panel" aria-live="polite">
          <div className="panel-heading"><p className="eyebrow">ORDER WORKFLOW</p><h2>注文の操作</h2></div>
          {order === null ? <p className="empty-state">注文を作成するか、注文番号から検索してください。</p> : <>
            <dl className="order-details">
              <div><dt>注文番号</dt><dd>{order.id}</dd></div>
              <div><dt>SKU</dt><dd>{order.sku}</dd></div>
              <div><dt>数量</dt><dd>{order.quantity}</dd></div>
              <div><dt>状態</dt><dd><span className={`status ${order.status.toLowerCase()}`}>{statusLabel[order.status]}</span></dd></div>
            </dl>
            <div className="action-row">
              <button className="primary-button" type="button" disabled={busy || !canReserve} onClick={() => void run(() => executeCommand(reserveOrder, { orderId: order.id }), "在庫を予約しました。")}>予約する</button>
              <button className="secondary-button" type="button" disabled={busy || !canConfirm} onClick={() => void run(() => executeCommand(confirmOrder, { orderId: order.id }), "注文を確定しました。")}>確定する</button>
              <button className="danger-button" type="button" disabled={busy || !canCancel} onClick={() => void run(() => executeCommand(cancelOrder, { orderId: order.id }), "注文を取り消しました。")}>取り消す</button>
            </div>
            <p className="helper">状態に応じて実行できる操作だけが有効になります。</p>
          </>}
        </section>
      </section>
    </main>
  )
}

function Metric({ label, value, accent }: { readonly label: string; readonly value: string | number; readonly accent?: "success" | "warning" }): ReactElement {
  return <article className={`metric ${accent ?? ""}`}><p>{label}</p><strong>{value}</strong><span>units</span></article>
}
