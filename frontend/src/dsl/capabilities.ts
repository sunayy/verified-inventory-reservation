declare const queryCapability: unique symbol
declare const commandCapability: unique symbol

export interface QueryCapability {
  readonly [queryCapability]: "query"
}

export interface CommandCapability {
  readonly [commandCapability]: "command"
}

export interface ReadOnlyCapability extends QueryCapability {
  readonly mode: "read-only"
}

export interface InteractiveCapability extends QueryCapability, CommandCapability {
  readonly mode: "interactive"
}

export const ReadOnly: ReadOnlyCapability = { mode: "read-only" } as ReadOnlyCapability
export const Interactive: InteractiveCapability = { mode: "interactive" } as InteractiveCapability
