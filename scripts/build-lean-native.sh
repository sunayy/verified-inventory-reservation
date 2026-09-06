#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verified_root="$project_root/verified"

# `:shared` compiles the exported Lean definitions into the platform native
# library. The smoke binary invokes that library only through the primitive C
# shim; JNI joins this same shim in Phase 3.
(cd "$verified_root" && lake build Reservation:shared)

lean_prefix="$(cd "$verified_root" && lean --print-prefix)"
native_library="$verified_root/.lake/build/lib/libinventory_x2dreservation_Reservation.dylib"
smoke_binary="$verified_root/.lake/build/bin/reservation-smoke"
jni_library="$verified_root/.lake/build/lib/libreservation_jni.dylib"
jni_java_home="$(/usr/libexec/java_home)"
mkdir -p "$(dirname "$smoke_binary")"

# Match Lean's native target on macOS; the variable is ignored by non-Apple
# toolchains.
MACOSX_DEPLOYMENT_TARGET=99.0 clang -std=c11 \
  -I"$lean_prefix/include" \
  -I"$verified_root/native" \
  "$verified_root/native/reservation_jni.c" \
  "$verified_root/native/reservation_smoke.c" \
  -L"$verified_root/.lake/build/lib" \
  -linventory_x2dreservation_Reservation \
  -L"$lean_prefix/lib/lean" \
  -lInit_shared \
  -lleanshared \
  -Wl,-rpath,"$verified_root/.lake/build/lib" \
  -Wl,-rpath,"$lean_prefix/lib/lean" \
  -o "$smoke_binary"

"$smoke_binary"

MACOSX_DEPLOYMENT_TARGET=99.0 clang -std=c11 -dynamiclib \
  -I"$lean_prefix/include" \
  -I"$verified_root/native" \
  -I"$jni_java_home/include" \
  -I"$jni_java_home/include/darwin" \
  "$verified_root/native/reservation_jni.c" \
  "$verified_root/native/reservation_jni_bridge.c" \
  -L"$verified_root/.lake/build/lib" \
  -linventory_x2dreservation_Reservation \
  -L"$lean_prefix/lib/lean" \
  -lInit_shared \
  -lleanshared \
  -Wl,-rpath,"$verified_root/.lake/build/lib" \
  -Wl,-rpath,"$lean_prefix/lib/lean" \
  -o "$jni_library"
