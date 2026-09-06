#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

quint="$project_root/node_modules/.bin/quint"
invariants=(
  StockNonNegative
  ReservationWithinStock
  StockConservation
  ReservationConsistency
  OrderWellFormed
  TerminalState
)

"$quint" typecheck spec/inventory.qnt

# Quint 0.31.0 no longer exposes a separate `lint` subcommand. Typechecking is
# the current static CLI gate; the simulation below checks all state invariants.
"$quint" run spec/inventory.qnt \
  --backend=typescript \
  --invariants "${invariants[@]}" \
  --max-samples=1000 \
  --max-steps=20 \
  --seed=20260906 \
  --verbosity=1

# The model is finite, so TLC exhaustively explores its reachable state space.
# QUINT_HOME is project-local to avoid an undeclared user-home dependency.
QUINT_HOME="$project_root/.quint" "$quint" verify spec/inventory.qnt \
  --backend=tlc \
  --invariant="$(IFS=,; echo "${invariants[*]}")" \
  --max-steps=12 \
  --verbosity=1
