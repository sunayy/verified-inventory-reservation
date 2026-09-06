namespace Reservation

/-- Inventory arithmetic is defined only over natural quantities. -/
structure Inventory where
  onHand : Nat
  reserved : Nat
deriving Repr, DecidableEq

/-- A state eligible for a reservation never holds more than physical stock. -/
def Inventory.Valid (inventory : Inventory) : Prop :=
  inventory.reserved ≤ inventory.onHand

end Reservation
