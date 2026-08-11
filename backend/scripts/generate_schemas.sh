#!/bin/bash

# This script generates Pydantic models for the FastAPI backend from the OpenAPI spec.
# It uses the openapi-generator-cli via Docker.

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SPEC_FILE="$(dirname "$PROJECT_ROOT")/api/openapi.yaml"
OUTPUT_DIR="$PROJECT_ROOT/app/schemas/generated"

echo "Generating backend schemas from $SPEC_FILE..."

mkdir -p "$OUTPUT_DIR"

docker run --rm \
  -v "$SPEC_FILE":/local/openapi.yaml \
  -v "$OUTPUT_DIR":/local/out \
  openapitools/openapi-generator-cli generate \
  -i /local/openapi.yaml \
  -g python-pydantic-v2 \
  -o /local/out \
  --additional-properties=packageName=generated_schemas

echo "Done. Generated schemas are in $OUTPUT_DIR"
