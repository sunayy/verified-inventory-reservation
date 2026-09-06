import Init.Omega
import Reservation.Logic

namespace Reservation

theorem availableNeverExceedsOnHand (onHand reserved : Nat) :
    available onHand reserved ≤ onHand := by
  simp [available]

theorem canReserveTrueHasValidState {onHand reserved requested : Nat}
    (h : canReserve onHand reserved requested = true) :
    reserved ≤ onHand ∧ requested ≤ available onHand reserved := by
  simpa [canReserve] using h

theorem successfulReservationPreservesCapacity {onHand reserved requested newReserved : Nat}
    (h : reserve onHand reserved requested = some newReserved) :
    newReserved ≤ onHand := by
  unfold reserve at h
  split at h
  · rename_i reservable
    injection h with hReserved
    subst newReserved
    have capacity := canReserveTrueHasValidState reservable
    simp only [available] at capacity
    omega
  · simp at h

theorem noOverReservation {onHand reserved requested newReserved : Nat}
    (h : reserve onHand reserved requested = some newReserved) :
    newReserved ≤ onHand :=
  successfulReservationPreservesCapacity h

theorem cancellationPreservesNonNegativeReservation (reserved quantity : Nat) :
    0 ≤ cancel reserved quantity := by
  exact Nat.zero_le _

end Reservation
