#include <jni.h>

#include "reservation_jni.h"

static void throw_illegal_argument(JNIEnv *env, const char *message) {
  jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
  if (exception_class != NULL) {
    (*env)->ThrowNew(env, exception_class, message);
  }
}

static int is_negative(jlong value) { return value < 0; }

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;
  return reservation_runtime_initialize() ? JNI_VERSION_1_8 : JNI_ERR;
}

JNIEXPORT jlong JNICALL
Java_com_example_inventory_infrastructure_nativebridge_VerifiedReservationNative_available0(
    JNIEnv *env, jclass clazz, jlong on_hand, jlong reserved) {
  (void)clazz;
  if (is_negative(on_hand) || is_negative(reserved)) {
    throw_illegal_argument(env, "Lean Nat inputs must be non-negative");
    return 0;
  }
  return (jlong)reservation_available_u64((uint64_t)on_hand, (uint64_t)reserved);
}

JNIEXPORT jboolean JNICALL
Java_com_example_inventory_infrastructure_nativebridge_VerifiedReservationNative_canReserve0(
    JNIEnv *env, jclass clazz, jlong on_hand, jlong reserved, jlong requested) {
  (void)clazz;
  if (is_negative(on_hand) || is_negative(reserved) || is_negative(requested)) {
    throw_illegal_argument(env, "Lean Nat inputs must be non-negative");
    return JNI_FALSE;
  }
  return reservation_can_reserve_u64((uint64_t)on_hand, (uint64_t)reserved,
                                     (uint64_t)requested)
             ? JNI_TRUE
             : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_example_inventory_infrastructure_nativebridge_VerifiedReservationNative_reserve0(
    JNIEnv *env, jclass clazz, jlong on_hand, jlong reserved, jlong requested) {
  (void)clazz;
  if (is_negative(on_hand) || is_negative(reserved) || is_negative(requested)) {
    throw_illegal_argument(env, "Lean Nat inputs must be non-negative");
    return -1;
  }
  uint64_t new_reserved = 0;
  if (!reservation_reserve_u64((uint64_t)on_hand, (uint64_t)reserved,
                               (uint64_t)requested, &new_reserved)) {
    return -1;
  }
  return (jlong)new_reserved;
}

JNIEXPORT jlong JNICALL
Java_com_example_inventory_infrastructure_nativebridge_VerifiedReservationNative_cancel0(
    JNIEnv *env, jclass clazz, jlong reserved, jlong quantity) {
  (void)clazz;
  if (is_negative(reserved) || is_negative(quantity)) {
    throw_illegal_argument(env, "Lean Nat inputs must be non-negative");
    return 0;
  }
  return (jlong)reservation_cancel_u64((uint64_t)reserved, (uint64_t)quantity);
}
