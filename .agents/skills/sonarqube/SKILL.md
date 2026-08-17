---
name: sonarqube
description: 'Run SonarQube analysis and interpret findings'
paths:
  - "**/*.java"
---

# SonarQube Analysis Skill

## Overview

SonarQube provides code quality and security analysis. This project uses it for static analysis but doesn't require 100% issue resolution due to known false positives.

## Available Tools

### `sonarqube_analyze_file`
- Runs SonarQube analysis on a specific file
- Results appear in VS Code's **PROBLEMS view**
- Use when you want fresh analysis after making changes

### `sonarqube_list_potential_security_issues`
- Lists Security Hotspots and Taint Vulnerabilities
- Requires **Connected Mode** (SonarQube Server or Cloud)
- Currently not configured in this workspace

### `sonarqube_setup_connected_mode`
- Guides setup of Connected Mode for security analysis
- Use when you need to check Security Hotspots/Vulnerabilities

## Strategy: Pragmatic Issue Resolution

**Goal:** Fix obvious issues, ignore noise.

### What to Fix (Obvious Issues)
- Unused variables and parameters
- Dead code
- Clear logic errors
- Missing null checks where `@Nullable` is used
- TODO comments that are incomplete

### What to Ignore (Known False Positives)
- Minor code smells in complex algorithms
- Cyclomatic complexity warnings in math-heavy methods
- Duplicate code in generated or template-like code
- Excessive parameter lists in constructors with many fields

## Workflow

1. **Before making changes:** Run `sonarqube_analyze_file` to see current state
2. **After changes:** Re-analyze to verify fixes and check for new issues
3. **Review findings:** Focus on obvious problems, skip noise
4. **Fix strategically:** Address high-impact issues first

## Example Usage

```
# Analyze a file after making changes
sonarqube_analyze_file(filePath: "/path/to/File.java")

# Check IDE-reported errors (faster than SonarQube)
get_errors(filePaths: ["/path/to/File.java"])
```

## When to Use This Skill

- After significant refactoring or bug fixes
- Before committing changes to verify quality
- When investigating why a test might be failing due to logic issues
- To get a fresh perspective on code quality after working on a file for a while

## Notes

- IDE `get_errors` is faster for quick checks
- SonarQube analysis takes longer but provides deeper insights
- Security findings require Connected Mode setup if needed
- Don't chase perfection - focus on correctness and clarity
