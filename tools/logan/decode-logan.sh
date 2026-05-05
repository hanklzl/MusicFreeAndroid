#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOWED_OUTPUT_BASE="${SCRIPT_DIR}/out"
OUTPUT_DIR="${2:-$ALLOWED_OUTPUT_BASE}"

if [[ -z "${INPUT}" ]]; then
  echo "Usage: tools/logan/decode-logan.sh <feedback-zip-or-logan-dir> [output-dir]" >&2
  exit 2
fi

echo "Decode input: ${INPUT}"
echo "Output dir: ${OUTPUT_DIR}"
echo "Release logs require LOGAN_AES_KEY and LOGAN_AES_IV in the environment."
echo "Debug logs use the repository development key."
echo "Use your Logan decode tooling with the extracted files in this directory."

if [[ -z "${OUTPUT_DIR}" ]]; then
  echo "Invalid output directory: ${OUTPUT_DIR}" >&2
  exit 1
fi

OUTPUT_DIR="$(realpath -m "${OUTPUT_DIR}")"
ALLOWED_OUTPUT_BASE="$(realpath -m "${ALLOWED_OUTPUT_BASE}")"

if [[ "${OUTPUT_DIR}" == "${ALLOWED_OUTPUT_BASE}" || "${OUTPUT_DIR}" == "${ALLOWED_OUTPUT_BASE}/"* ]]; then
  :
else
  echo "Output directory must be under ${ALLOWED_OUTPUT_BASE}. Received: ${OUTPUT_DIR}" >&2
  exit 1
fi

if [[ -d "${OUTPUT_DIR}" ]]; then
  find "${OUTPUT_DIR}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
fi

mkdir -p "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}/logan"

if [[ "${INPUT}" == *.zip ]]; then
  unzip -o "${INPUT}" -d "${OUTPUT_DIR}"
elif [[ -d "${INPUT}" ]]; then
  cp -R "${INPUT}"/* "${OUTPUT_DIR}/"
else
  echo "Input must be a feedback zip file or a directory." >&2
  exit 1
fi
