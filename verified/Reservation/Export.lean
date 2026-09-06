import Reservation.Logic

namespace Reservation

/-- C ABI export of the proved `available` definition. The C wrapper owns the
conversion between primitive unsigned values and Lean `Nat` objects. -/
@[export reservation_available]
def availableExport (onHand reserved : Nat) : Nat :=
  available onHand reserved

/-- C ABI export of the proved reservation predicate. -/
@[export reservation_can_reserve]
def canReserveExport (onHand reserved requested : Nat) : Bool :=
  canReserve onHand reserved requested

/-- C ABI export of the proved reservation transition. `none` represents an
insufficient-capacity domain result; it is not a native runtime failure. -/
@[export reservation_reserve]
def reserveExport (onHand reserved requested : Nat) : Option Nat :=
  reserve onHand reserved requested

/-- C ABI export of the same `cancel` definition proved in `Proofs`. -/
@[export reservation_cancel]
def cancelExport (reserved quantity : Nat) : Nat :=
  cancel reserved quantity

end Reservation
