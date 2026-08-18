# Copilot Workspace Instructions

This file is the compact Copilot entrypoint for the workspace.

## Source of truth

- Read `docs/BinclassOriginal.md` which summarizes the original C code.
- The mathematical foundations are described in `docs/Binclass - Software Package For Classifyin Binary Vectors.md`.

## Quick rules

- Tech stack: Java 25, Vaadin 25, JUnit 6, Playwright, JSoup, SonarQube, JSpecify.
- Do not use Lombok.
- Do not use Spring DI or other CDI frameworks.
- Respect the application flow: `View -> Presenter -> Algorithms`.
- Keep scientific algorithms in `binclass-algorithms`.
- Place general use components in `binclass-components` and UI routes in `binclass-ui`.
- Use `@NullMarked` on public classes and `@Nullable` where null is allowed.
- Prefer records for DTOs, EventBus events, and other immutable data holders.
- Entity `equals()` and `hashCode()` must be based on `id`.
- Keep utility methods static.
- Sanitize displayed user HTML with JSoup content mode.
- Verify that all new code is covered by unit tests and integration tests.
. Do not repeat your self (DRY) in code or tests.

## Testing expectations

- New algorithmic logic needs JUnit 6 tests.
- New view components need BrowserlessTests.
- New custom components with client-side code need Playwright e2e integration tests.
- Do not remove or weaken existing tests without confirming the behavior change is intentional.
- Use the "run-tests" skill to run unit tests via VS Code's test runner tool when available; fall back to Maven (`mvn test`) if the tool doesn't discover JUnit 6 tests in a specific file.

## Quality expectations

- SonarQube analysis provides code quality insights but is known for false positives.
- Fix obvious issues (unused variables, dead code, missing null checks) but don't chase perfection.
- Use "sonarqube" skill after changes to verify fixes.
- Format code using `mvn spotless:apply` command
- Run tests using "run-tests" skill.

## Workflow

- Run `mvn spotless:apply` when formatting is needed.
- Improve code quality using "sonarqube" skill.
- Run tests using "run-tests" skill.
- Use `mvn verify -Pit` for the full verification path when a change affects integration-tested behavior.
