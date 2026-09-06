#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

quint="$project_root/node_modules/.bin/quint"
trace_dir="$project_root/spec/traces"
seed="20260906"
invariants=(
  StockNonNegative
  ReservationWithinStock
  StockConservation
  ReservationConsistency
  OrderWellFormed
  TerminalState
)

mkdir -p "$trace_dir"
rm -f "$trace_dir"/*.itf.json "$trace_dir"/generation.json

"$quint" run spec/inventory.qnt \
  --backend=typescript \
  --mbt \
  --out-itf="$trace_dir/trace_{seq}.itf.json" \
  --n-traces=100 \
  --max-samples=100 \
  --max-steps=12 \
  --seed="$seed" \
  --invariants "${invariants[@]}" \
  --verbosity=1

printf '{\n  "quintVersion": "%s",\n  "seed": "%s",\n  "traceCount": 100,\n  "maxSteps": 12,\n  "mbt": true\n}\n' \
  "$("$quint" --version)" "$seed" > "$trace_dir/generation.json"
