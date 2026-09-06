#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if rg -n '\bsorry\b|^\s*axiom\b|^\s*unsafe\b' \
  "$project_root/verified/Reservation" --glob '*.lean'; then
  echo "Lean proof-policy violation found." >&2
  exit 1
fi

(cd "$project_root/verified" && lake build)
