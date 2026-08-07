#!/usr/bin/env sh
set -eu
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repository_root"
mvn --batch-mode --no-transfer-progress \
  -Dtest=TamperDetectionIntegrationTest#repeatableArchiveAndUnauthorizedTamperDemonstration test
