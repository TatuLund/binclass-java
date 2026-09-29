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
mapfile -t TEST_FILES < <(find "$ROOT_DIR" \
  -type d \( -name target -o -name node_modules -o -name .git \) -prune -o \
  -type f -name '*Test.java' -print)

TOTAL=0
GRANDTOTAL=0
TESTTOTAL=0
for file in "${JAVA_FILES[@]}"; do
  # Count non-blank, non-comment-only lines. A line is a comment when its
  # first non-space token is // (line), /* (block start) or * (Javadoc/code
  # continuation). The pattern is intentionally NOT anchored at $ so that
  # Javadoc continuation lines like " * description" are counted as comments.
  count=$(grep -vE '^[[:space:]]*$' "$file" | grep -vcE '^[[:space:]]*(//|/\*|\*)')
  grosscount=$(wc -l < "$file")
  GRANDTOTAL=$((GRANDTOTAL + grosscount))
  TOTAL=$((TOTAL + count))
done

for file in "${TEST_FILES[@]}"; do
  testcount=$(grep -vE '^[[:space:]]*$' "$file" | grep -vcE '^[[:space:]]*(//|/\*|\*)')
  TESTTOTAL=$((TESTTOTAL + testcount))
done

echo "Total Java files : ${#JAVA_FILES[@]}"
echo "Total test Java files : ${#TEST_FILES[@]}"
echo "Total lines of code (non-blank, non-comment): $TOTAL"
echo "Total lines of test code (non-blank, non-comment): $TESTTOTAL"
echo "Total lines of code (including blanks and comments): $GRANDTOTAL"
