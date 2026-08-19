---
name: run-tests
description: 'Run unit tests using the VS Code test runner tool'
paths:
  -- "**/*.java"
---

# Running Unit Tests with runTests Tool

## Overview

The `runTests` tool provides direct access to VS Code's built-in test runner, allowing you to execute JUnit 6 tests without needing Maven. This is faster for quick test runs and integrates seamlessly with the editor.

## Usage

### Run all tests in a specific file
```
runTests(files: ["/path/to/TestClass.java"])
```

### Run specific test methods within a file
```
runTests(
  files: ["/path/to/TestClass.java"],
  testNames: ["testSpecificMethod1", "testSpecificMethod2"]
)
```

### Run all tests in a package/directory
```
runTests(files: ["/path/to/package"])
```

## Limitations & Workarounds

**Issue:** The `runTests` tool may not discover JUnit 6 tests when targeting specific files, even though Maven can run them successfully.

**Solution:** If `runTests` returns "No tests found" for a specific file:
1. Use the terminal to run Maven directly: `mvn test -Dtest=ClassName`
2. Or run all tests without specifying files: `runTests()` (no parameters)

## When to Use This Tool

- Quick validation of changes during development
- Running tests after making modifications to source code
- Verifying specific test cases before committing
- Checking test coverage with the `mode="coverage"` parameter

## Example Workflow

1. Make a change to `src/main/java/...`
2. Run targeted tests: `runTests(files: ["path/to/test/FileTest.java"])`
3. If tool doesn't find tests, fall back to Maven: `mvn test -Dtest=ClassName`
4. Review results and iterate

## Notes

- The tool works best with JUnit 5 but has known compatibility issues with JUnit 6 in some configurations
- For full project verification including integration tests, use `mvn verify -Pit` instead
- Always check test output for failures before proceeding with more changes
