namespace Reservation

/-- Capacity not currently held by reservations. Natural subtraction clamps at
zero; callers use `canReserve` to require a valid inventory before reserving. -/
def available (onHand reserved : Nat) : Nat :=
  onHand - reserved

/-- True precisely when a request fits a valid inventory's remaining capacity. -/
def canReserve (onHand reserved requested : Nat) : Bool :=
  decide (reserved ≤ onHand ∧ requested ≤ available onHand reserved)

/-- The post-reservation reserved quantity, or `none` when the request does not
fit. This is the exact definition proved in `Proofs` and exported at runtime. -/
def reserve (onHand reserved requested : Nat) : Option Nat :=
  if canReserve onHand reserved requested then
    some (reserved + requested)
  else
    none

/-- Releasing a reservation. `Nat` makes the result non-negative by construction. -/
def cancel (reserved quantity : Nat) : Nat :=
  reserved - quantity

end Reservation
