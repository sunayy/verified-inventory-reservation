import { ScreenRenderer } from "./interpreter/ScreenRenderer"
import { ReadOnly } from "./dsl/capabilities"
import { inventoryReadOnly } from "./features/inventoryReadOnly"

export const App = () => <ScreenRenderer screen={inventoryReadOnly} capability={ReadOnly} />
