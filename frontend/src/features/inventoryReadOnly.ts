import { defineScreen } from "../dsl/ast"
import { ReadOnly } from "../dsl/capabilities"
import { inventoryQuery, type InventoryView } from "../dsl/inventory-contracts"

/** Feature modules export EDSL definitions; they never access the network. */
export const inventoryReadOnly = defineScreen(ReadOnly, {
  title: "Inventory",
  query: inventoryQuery,
  body: (inventory: InventoryView, ui) => [
    ui.section(
      ui.text(`SKU: ${inventory.sku}`),
      ui.table([inventory], [
        { header: "On hand", value: (row) => String(row.onHand) },
        { header: "Reserved", value: (row) => String(row.reserved) },
        { header: "Sold", value: (row) => String(row.sold) },
      ]),
    ),
  ],
})
