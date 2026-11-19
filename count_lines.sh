#!/usr/bin/env bash
# Count the total number of lines of code across the whole project.
# Blank lines and comment-only lines are skipped by default.
# Counts both Java (.java) and TypeScript (.ts) source files.
set -euo pipefail

# Root directory to scan (defaults to this script's location).
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Collect files matching the given name globs, skipping build output and
# generated frontend folders (target, node_modules, .git, frontend, generated).
collect_files() {
  local names=("$@")
  local args=("(")
  for i in "${!names[@]}"; do
    if (( i > 0 )); then args+=("-o"); fi
    args+=("-name" "${names[$i]}")
  done
  args+=(")")
  find "$ROOT_DIR" \
    \( -type d \( -name target -o -name node_modules -o -name .git -o -name frontend -o -name generated \) -prune \) -o \
    -type f "${args[@]}" -print
}

# Build the list of source files (.java and .ts).
mapfile -t SOURCE_FILES < <(collect_files '*.java' '*.ts')

# Build the list of test files (Java *Test.java and TS *.test.ts / *.spec.ts).
mapfile -t TEST_FILES < <(collect_files '*Test.java' '*.test.ts' '*.spec.ts')

# Count non-blank, non-comment-only lines in a single file. A line is a comment
# when its first non-space token is // (line), /* (block start) or * (Javadoc/
# code continuation). The pattern is intentionally NOT anchored at $ so that
# Javadoc continuation lines like " * description" are counted as comments.
count_lines() {
  grep -vE '^[[:space:]]*$' "$1" | grep -vcE '^[[:space:]]*(//|/\*|\*)'
}

TOTAL=0
GRANDTOTAL=0
TESTTOTAL=0
JAVA_TOTAL=0
TS_TOTAL=0
for file in "${SOURCE_FILES[@]}"; do
  count=$(count_lines "$file")
  grosscount=$(wc -l < "$file")
  TOTAL=$((TOTAL + count))
  GRANDTOTAL=$((GRANDTOTAL + grosscount))
  if [[ "$file" == *.ts ]]; then
    TS_TOTAL=$((TS_TOTAL + count))
  else
    JAVA_TOTAL=$((JAVA_TOTAL + count))
  fi
done

for file in "${TEST_FILES[@]}"; do
  testcount=$(count_lines "$file")
  TESTTOTAL=$((TESTTOTAL + testcount))
done

echo "Source files (.java + .ts) : ${#SOURCE_FILES[@]}"
echo "Test files                 : ${#TEST_FILES[@]}"
echo ""
echo "--- Non-blank, non-comment lines by language ---"
echo "Java        : $JAVA_TOTAL"
echo "TypeScript  : $TS_TOTAL"
echo "Total       : $TOTAL"
echo ""
echo "Test code (non-blank, non-comment) : $TESTTOTAL"
echo "Total lines of code (incl. blanks and comments): $GRANDTOTAL"
