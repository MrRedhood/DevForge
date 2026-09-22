#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <label> <artifact> <max_mib> [elapsed_seconds] [max_elapsed_seconds]" >&2
  exit 2
}

[[ $# -ge 3 ]] || usage

LABEL="$1"
ARTIFACT="$2"
MAX_MIB="$3"
ELAPSED="${4:-}"
MAX_ELAPSED="${5:-}"

if [[ ! "$MAX_MIB" =~ ^[0-9]+$ ]]; then
  echo "Invalid max MiB budget: $MAX_MIB" >&2
  exit 2
fi

if [[ ! -f "$ARTIFACT" ]]; then
  echo "Budget artifact not found: $ARTIFACT" >&2
  exit 1
fi

BYTES="$(stat -c '%s' "$ARTIFACT")"
MAX_BYTES="$((MAX_MIB * 1024 * 1024))"
MIB="$(awk -v bytes="$BYTES" 'BEGIN { printf "%.2f", bytes / 1048576 }')"

echo "[$LABEL] size=${MIB} MiB budget=${MAX_MIB} MiB"
sha256sum "$ARTIFACT"

if (( BYTES > MAX_BYTES )); then
  echo "[$LABEL] artifact size budget exceeded by $((BYTES - MAX_BYTES)) bytes" >&2
  exit 1
fi

if [[ -n "$ELAPSED" || -n "$MAX_ELAPSED" ]]; then
  [[ "$ELAPSED" =~ ^[0-9]+$ && "$MAX_ELAPSED" =~ ^[0-9]+$ ]] || {
    echo "Invalid elapsed-time budget values" >&2
    exit 2
  }
  echo "[$LABEL] build_time=${ELAPSED}s budget=${MAX_ELAPSED}s"
  if (( ELAPSED > MAX_ELAPSED )); then
    echo "[$LABEL] build-time budget exceeded by $((ELAPSED - MAX_ELAPSED)) seconds" >&2
    exit 1
  fi
fi
