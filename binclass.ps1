# BinClass CLI launcher script for Windows PowerShell
# Usage: ./binclass.ps1 <command> [options] [filebase]

$ErrorActionPreference = "Stop"

Write-Host "Building BinClass..." -ForegroundColor Cyan
mvn clean package -DskipTests -q

$argString = ($args -join ' ')
Write-Host "Running: binclass $argString" -ForegroundColor Green
mvn exec:java -pl binclass-cli -Dexec.mainClass="org.binclass.cli.BinClass" -Dexec.args="binclass $argString" --quiet
