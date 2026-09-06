import type { CommandCapability, QueryCapability } from "./capabilities"

export interface Query<Request, Response> {
  readonly id: string
  readonly request: Request
  readonly __response?: Response
}

export interface Command<Request, Response, Failure> {
  readonly id: string
  readonly __request?: Request
  readonly __response?: Response
  readonly __failure?: Failure
}

export interface TextNode {
  readonly kind: "text"
  readonly value: string
}

export interface SectionNode {
  readonly kind: "section"
  readonly children: readonly UiNode[]
}

export interface FieldNode<Value> {
  readonly kind: "field"
  readonly name: string
  readonly initial: Value
}

export interface TableNode<Row> {
  readonly kind: "table"
  readonly rows: readonly Row[]
  readonly columns: readonly TableColumn<Row>[]
}

export interface TableColumn<Row> {
  readonly header: string
  value(row: Row): string
}

export interface ActionNode<Request, Response, Failure> {
  readonly kind: "action"
  readonly label: string
  readonly command: Command<Request, Response, Failure>
  readonly request: Request
}

export interface UnsafeCustomNode {
  readonly kind: "unsafe-custom"
  readonly reason: string
  readonly value: unknown
}

export type UiNode =
  | TextNode
  | SectionNode
  | FieldNode<unknown>
  | TableNode<unknown>
  | ActionNode<unknown, unknown, unknown>
  | UnsafeCustomNode

export const text = (value: string): TextNode => ({ kind: "text", value })
export const section = (...children: readonly UiNode[]): SectionNode => ({ kind: "section", children })
export const field = <Value>(name: string, initial: Value): FieldNode<Value> => ({ kind: "field", name, initial })
export const table = <Row>(rows: readonly Row[], columns: readonly TableColumn<Row>[]): TableNode<Row> => ({
  kind: "table",
  rows,
  columns,
})

export const action = <C extends CommandCapability, Request, Response, Failure>(
  _capability: C,
  label: string,
  command: Command<Request, Response, Failure>,
  request: Request,
): ActionNode<Request, Response, Failure> => ({ kind: "action", label, command, request })

/** Explicit, searchable escape hatch for UI that the AST cannot yet express. */
export const unsafeCustom = (reason: string, value: unknown): UnsafeCustomNode => ({
  kind: "unsafe-custom",
  reason,
  value,
})

export const query = <Request, Response>(id: string, request: Request): Query<Request, Response> => ({ id, request })
export const command = <Request, Response, Failure>(id: string): Command<Request, Response, Failure> => ({ id })

export type ScreenDsl<Capability> = {
  readonly section: typeof section
  readonly text: typeof text
  readonly field: typeof field
  readonly table: typeof table
  readonly unsafeCustom: typeof unsafeCustom
} & (Capability extends CommandCapability
  ? {
      readonly action: <Request, Response, Failure>(
        label: string,
        command: Command<Request, Response, Failure>,
        request: Request,
      ) => ActionNode<Request, Response, Failure>
    }
  : {})

export type Screen<Capability extends QueryCapability, Data> = {
  readonly title: string
  readonly query: Query<unknown, Data>
  readonly body: (data: Data, dsl: ScreenDsl<Capability>) => readonly UiNode[]
}

export const createScreenDsl = <Capability>(capability: Capability): ScreenDsl<Capability> => {
  const base = { section, text, field, table, unsafeCustom }
  if ((capability as { mode?: string }).mode === "interactive") {
    return {
      ...base,
      action: (label, selectedCommand, request) => action(capability as CommandCapability, label, selectedCommand, request),
    } as ScreenDsl<Capability>
  }
  return base as ScreenDsl<Capability>
}

export const defineScreen = <Capability extends QueryCapability, Data>(
  _capability: Capability,
  screen: Screen<Capability, Data>,
): Screen<Capability, Data> => screen
