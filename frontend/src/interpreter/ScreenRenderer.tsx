import { useEffect, useMemo, useState } from "react"
import type { ReactElement } from "react"
import type { Screen, UiNode } from "../dsl/ast"
import type { QueryCapability } from "../dsl/capabilities"
import { createScreenDsl } from "../dsl/ast"
import { executeCommand, executeQuery } from "./api"

export function ScreenRenderer<Capability extends QueryCapability, Data>({
  screen,
  capability,
}: {
  readonly screen: Screen<Capability, Data>
  readonly capability: Capability
}): ReactElement {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const dsl = useMemo(() => createScreenDsl(capability), [capability])

  useEffect(() => {
    executeQuery(screen.query)
      .then((response) => setData(response))
      .catch((failure: unknown) => setError(failure instanceof Error ? failure.message : "Request failed"))
  }, [screen])

  if (error !== null) return <p role="alert">{error}</p>
  if (data === null) return <p>Loading…</p>
  return (
    <main>
      <h1>{screen.title}</h1>
      {screen.body(data, dsl).map((node, index) => <NodeRenderer key={index} node={node} />)}
    </main>
  )
}

function NodeRenderer({ node }: { readonly node: UiNode }): ReactElement {
  switch (node.kind) {
    case "text":
      return <p>{node.value}</p>
    case "section":
      return <section>{node.children.map((child, index) => <NodeRenderer key={index} node={child} />)}</section>
    case "field":
      return <label>{node.name}<input name={node.name} defaultValue={String(node.initial)} /></label>
    case "table":
      return (
        <table>
          <thead><tr>{node.columns.map((column) => <th key={column.header}>{column.header}</th>)}</tr></thead>
          <tbody>{node.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>{node.columns.map((column) => <td key={column.header}>{column.value(row)}</td>)}</tr>
          ))}</tbody>
        </table>
      )
    case "action":
      return <button onClick={() => { void executeCommand(node.command, node.request) }}>{node.label}</button>
    case "unsafe-custom":
      return <aside data-unsafe-custom={node.reason}>Unsupported custom UI</aside>
  }
}
