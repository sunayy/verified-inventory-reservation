#include "reservation_jni.h"

#include <assert.h>

int main(void) {
  assert(reservation_runtime_initialize());
  assert(reservation_available_u64(10, 3) == 7);
  assert(reservation_can_reserve_u64(10, 3, 7));
  assert(!reservation_can_reserve_u64(10, 3, 8));

  uint64_t new_reserved = 0;
  assert(reservation_reserve_u64(10, 3, 7, &new_reserved));
  assert(new_reserved == 10);
  assert(!reservation_reserve_u64(10, 3, 8, &new_reserved));
  assert(reservation_cancel_u64(3, 2) == 1);
  assert(reservation_cancel_u64(2, 3) == 0);
  return 0;
}
