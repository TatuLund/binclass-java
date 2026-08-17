#!/bin/bash
# BinClass CLI launcher script for Linux/macOS
# Usage: ./binclass.sh <command> [options] [filebase]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# echo "Building BinClass..."
# mvn clean package -DskipTests -q

echo "Running: binclass $*"
mvn exec:java -pl binclass-cli -Dexec.mainClass="org.binclass.cli.BinClass" -Dexec.args="binclass $*" --quiet
