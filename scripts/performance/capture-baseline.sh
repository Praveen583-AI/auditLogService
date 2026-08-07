#!/usr/bin/env sh
set -eu

: "${DATABASE_URL:?Set DATABASE_URL to a PostgreSQL connection URL}"
: "${TENANT_ID:?Set TENANT_ID}"
: "${ACTOR_ID:?Set ACTOR_ID}"
: "${RESOURCE_TYPE:?Set RESOURCE_TYPE}"
: "${RESOURCE_ID:?Set RESOURCE_ID}"
: "${CHAIN_ID:?Set CHAIN_ID}"

root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
output=${1:-"$root/build/performance/baseline-$(date -u +%Y%m%dT%H%M%SZ).txt"}
mkdir -p "$(dirname -- "$output")"

{
  echo "commit=$(git -C "$root" rev-parse HEAD)"
  echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "java=$(java -version 2>&1 | head -n 1)"
  echo "maven=$(mvn -version | head -n 1)"
  echo "os=$(uname -a)"
  echo "cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo unknown)"
  echo "tenant=$TENANT_ID chain=$CHAIN_ID"
  psql "$DATABASE_URL" \
    -v tenant_id="$TENANT_ID" -v actor_id="$ACTOR_ID" \
    -v resource_type="$RESOURCE_TYPE" -v resource_id="$RESOURCE_ID" \
    -v chain_id="$CHAIN_ID" \
    -f "$root/scripts/performance/query-plans.sql"
} | tee "$output"

echo "Baseline written to $output"
