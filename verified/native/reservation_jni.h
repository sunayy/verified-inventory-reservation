#ifndef INVENTORY_RESERVATION_JNI_H
#define INVENTORY_RESERVATION_JNI_H

#include <stdbool.h>
#include <stdint.h>

/*
 * Primitive C boundary for the future JNI layer. All inputs are unsigned after
 * Kotlin has validated that its Long arguments are non-negative. Call
 * `reservation_runtime_initialize` once during native-library load. A false
 * return from reservation_can_reserve_u64 is a domain answer; initialization
 * failure is an infrastructure failure and must not be mapped to this result.
 */
bool reservation_runtime_initialize(void);
uint64_t reservation_available_u64(uint64_t on_hand, uint64_t reserved);
bool reservation_can_reserve_u64(uint64_t on_hand, uint64_t reserved,
                                 uint64_t requested);
bool reservation_reserve_u64(uint64_t on_hand, uint64_t reserved,
                             uint64_t requested, uint64_t *new_reserved);
uint64_t reservation_cancel_u64(uint64_t reserved, uint64_t quantity);

#endif
