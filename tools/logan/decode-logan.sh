#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:-}"
OUTPUT_DIR="${2:-tools/logan/out}"

if [[ -z "${INPUT}" ]]; then
  echo "Usage: tools/logan/decode-logan.sh <feedback-zip-or-logan-dir> [output-dir]" >&2
  exit 2
fi

echo "Decode input: ${INPUT}"
echo "Output dir: ${OUTPUT_DIR}"
echo "Release logs require LOGAN_AES_KEY and LOGAN_AES_IV in the environment."
echo "Debug logs use the repository development key."
echo "Use your Logan decode tooling with the extracted files in this directory."

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
