#!/usr/bin/env bash
set -euo pipefail
: "${SCALE_ID:?set SCALE_ID}" "${SCALE_TOKEN:?set SCALE_TOKEN}" "${PLATE:?set PLATE}"
weight=${WEIGHT_KG:-12000}
while true; do
  curl --silent --show-error --fail -X POST http://localhost:8080/api/readings \
    -H 'Content-Type: application/json' -H "X-Scale-Token: ${SCALE_TOKEN}" \
    -d "{\"id\":\"${SCALE_ID}\",\"plate\":\"${PLATE}\",\"weight\":${weight}}" >/dev/null
  sleep 0.1
done
