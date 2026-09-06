#include "reservation_jni.h"

#include <lean/lean.h>

/*
 * C shim only: Phase 3 adds JNI methods that validate Kotlin signed Long values
 * and call these functions. Lean object representation therefore stays here.
 */
extern void lean_initialize_runtime_module(void);
extern void lean_io_mark_end_initialization(void);
extern lean_object *initialize_inventory_x2dreservation_Reservation(uint8_t);

extern lean_object *reservation_available(lean_object *, lean_object *);
extern uint8_t reservation_can_reserve(lean_object *, lean_object *, lean_object *);
extern lean_object *reservation_reserve(lean_object *, lean_object *, lean_object *);
extern lean_object *reservation_cancel(lean_object *, lean_object *);

static bool initialized = false;

bool reservation_runtime_initialize(void) {
  if (initialized) {
    return true;
  }

  lean_initialize_runtime_module();
  lean_object *result = initialize_inventory_x2dreservation_Reservation(1);
  if (lean_io_result_is_error(result)) {
    lean_dec(result);
    return false;
  }
  lean_dec_ref(result);
  lean_io_mark_end_initialization();
  initialized = true;
  return true;
}

uint64_t reservation_available_u64(uint64_t on_hand, uint64_t reserved) {
  lean_object *result = reservation_available(lean_uint64_to_nat(on_hand),
                                              lean_uint64_to_nat(reserved));
  uint64_t available = lean_uint64_of_nat(result);
  lean_dec(result);
  return available;
}

bool reservation_can_reserve_u64(uint64_t on_hand, uint64_t reserved,
                                 uint64_t requested) {
  return reservation_can_reserve(lean_uint64_to_nat(on_hand),
                                 lean_uint64_to_nat(reserved),
                                 lean_uint64_to_nat(requested)) != 0;
}

bool reservation_reserve_u64(uint64_t on_hand, uint64_t reserved,
                             uint64_t requested, uint64_t *new_reserved) {
  lean_object *result = reservation_reserve(lean_uint64_to_nat(on_hand),
                                            lean_uint64_to_nat(reserved),
                                            lean_uint64_to_nat(requested));
  if (lean_is_scalar(result)) { /* Lean's `none` */
    return false;
  }

  *new_reserved = lean_uint64_of_nat(lean_ctor_get(result, 0));
  lean_dec(result);
  return true;
}

uint64_t reservation_cancel_u64(uint64_t reserved, uint64_t quantity) {
  lean_object *result = reservation_cancel(lean_uint64_to_nat(reserved),
                                           lean_uint64_to_nat(quantity));
  uint64_t remaining = lean_uint64_of_nat(result);
  lean_dec(result);
  return remaining;
}
