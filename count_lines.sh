#!/usr/bin/env bash
# Count the total number of lines of Java code across the whole project.
# Blank lines and comment-only lines are skipped by default.
set -euo pipefail

# Root directory to scan (defaults to this script's location).
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find all .java files, skipping build output directories.
mapfile -t JAVA_FILES < <(find "$ROOT_DIR" \
  -type d \( -name target -o -name node_modules -o -name .git \) -prune -o \
  -type f -name '*.java' -print)

TOTAL=0
for file in "${JAVA_FILES[@]}"; do
  # Count non-blank, non-comment-only lines. A line is a comment when its
  # first non-space token is // (line), /* (block start) or * (Javadoc/code
  # continuation). The pattern is intentionally NOT anchored at $ so that
  # Javadoc continuation lines like " * description" are counted as comments.
  count=$(grep -vE '^[[:space:]]*$' "$file" | grep -vcE '^[[:space:]]*(//|/\*|\*)')
  TOTAL=$((TOTAL + count))
done

echo "Total Java files : ${#JAVA_FILES[@]}"
echo "Total lines of code (non-blank, non-comment): $TOTAL"
