# BinClass — Software Package for Classifying Binary Vectors

BinClass is a Java-based software toolkit for binary classification and clustering of binary data. It implements algorithms for partitioning binary vectors into clusters (classes), with support for multiple distance metrics, centroid computation, bootstrap resampling, mixture models, and hierarchical tree construction.

Originally developed in C by Tatu J. Lund at the University of Turku, this is a complete Java migration preserving all original functionality while leveraging modern Java 25 features.

## Features

- **Binary vector classification** using Generalized Lloyd Algorithm (GLA)
- **Multiple distance metrics**: Hamming, L1 (Manhattan), L2 (Euclidean), code-length
- **Centroid computation** using information-theoretic approaches
- **Bootstrap resampling** for cluster stability assessment
- **Mixture classification** via Expectation-Maximization (EM) algorithm
- **Hierarchical tree construction** from partitions
- **Cumulative classification analysis** with dynamic partition tracking
- **Synthetic data generation** (Bernoulli, Markov models)
- **Partition comparison and reporting**

## Project Structure

```
binclass_java/
├── binclass-algorithms/   # Core scientific algorithms
│   └── src/main/java/org/binclass/algorithms/
│       ├── classify/      # Classification routines (GLA, EM, etc.)
│       ├── core/          # Data structures (BinaryVector, Centroid, Partition)
│       ├── io/            # File I/O utilities
│       └── ...
├── binclass-cli/          # Command-line interface application
│   └── src/main/java/org/binclass/cli/
│       ├── BinClass.java  # Main entry point
│       ├── ClassifyCommand.java
│       ├── CumulativeCommand.java
│       ├── GenerateCommand.java
│       ├── ReportCommand.java
│       └── ...
├── binclass-components/   # Reusable UI components (Vaadin)
├── binclass-ui/           # Web application routes and views
├── data/                  # Sample datasets for testing
├── docs/                  # Documentation
└── original/              # Original C source code reference
```

## Quick Start

### Prerequisites

- **Java 25** or later
- **Maven 3.6+**
- (Optional) Node.js/npm for Vaadin UI development

### Build the Project

```bash
mvn clean package -DskipTests
```

### Run from Command Line

Use the convenience scripts in the project root:

**Linux/macOS:**
```bash
./binclass.sh <command> [options] [filebase]
```

**Windows (PowerShell):**
```powershell
.\binclass.ps1 <command> [options] [filebase]
```

Or run directly with Maven:
```bash
mvn exec:java -pl binclass-cli -Dexec.mainClass="org.binclass.cli.BinClass" -- <args>
```

### Available Commands

| Command | Description |
|---------|-------------|
| `identify` | Identify vectors by classification |
| `classify` | Classify vectors with GLA |
| `compare` | Compare two partitions |
| `report` | Generate statistical report |
| `generate` | Generate synthetic data |
| `bootstrap` | Bootstrap GLA trials |
| `cumulative` | Cumulative classification |
| `mixture` | Mixture classifier (EM) |
| `tree` | Build hierarchical tree |
| `centroids` | Save/load centroids |

### Example Usage

```bash
# Show help
./binclass.sh --help

# Classify vectors from data file
./binclass.sh classify data/entero.data

# Generate synthetic data with output file
./binclass.sh generate -n 100 -d 50 -o generated.dat

# Cumulative classification with partition output
./binclass.sh cumulative data/vibrio.data -P partitions.txt

# Generate report from classification results
./binclass.sh report result.par -o report.txt
```

### Classify: Automatic Search and Local Search

The `classify` command runs the automatic SC-minimizer by default (it scans from k=1 forward until no improvement in `kstopwhen` steps, keeping the best stochastic-complexity partition). Pass `-b`/`-s` (or `-n`) to force a fixed range search instead. A fixed k-range is honored automatically — you do not need `-n` for it.

**Automatic search with repeated GLA (`-a`)**

Run GLA several times per cluster count from different starting centroids, keeping the best SC so bad local minima are not counted (mirrors C `use_gla()` where `-a` sets the number of trials):

```bash
# Automatic scan; 5 GLA attempts per k value with different seeds
./binclass.sh classify -a 5 data/entero

# Restrict the scan to k = 2..10 and write the best partition out
./binclass.sh classify -b 2 -s 10 -a 3 -P entero.partition data/entero
```

**Local search (`-r7` / `-r8`)**

After GLA, a multi-operator local-search driver refines each cluster count. Choose the strategy with `-r`:

```bash
# Cycler: cycle through every operator (mirrors ls_heuristic_cycler = TRUE)
./binclass.sh classify -r 7 data/entero

# Adaptive: adaptively select operators by success probability
./binclass.sh classify -r 8 data/entero

# Combine local search with repeated GLA and a partition output file
./binclass.sh classify -r 8 -a 3 -P entero.partition data/entero
```

Both modes print `Using local search (...)` to the log when active. Local search only runs for cluster counts greater than three, matching C's `use_gla()` gate (`C->k > 3`).

**Local search within a fixed k-range (`-b` / `-s`)**

Passing `-b` and/or `-s` forces range search, so local search runs for each cluster count in that range. This is the way to combine local search with an explicit k-range — without it, the automatic scan starts at k=1 and ignores `-b`/`-s`:

```bash
# Local search (cycler) for every k from 60 to 75, 10 iterations per k
./binclass.sh classify -b 60 -s 75 -j 10 -r 7 data/entero

# Adaptive local search over a fixed range with repeated GLA and partition output
./binclass.sh classify -b 2 -s 10 -a 3 -j 5 -r 8 -P entero.partition data/entero
```

The `-j` value sets the number of local-search iterations per cluster count (the CLI adds one, so `-j 10` runs ten iterations). Local search still only activates for k > 3.

## Development

### Run Tests

```bash
mvn test
```

For full verification including integration tests:
```bash
mvn verify -Pit
```

### Code Formatting

```bash
mvn spotless:apply
```

### SonarQube Analysis

Run static analysis on specific files:
```bash
sonarqube_analyze_file <file-path>
```

## Architecture

The application follows a clean architecture pattern:

- **View → Presenter → Algorithms** flow
- Scientific algorithms isolated in `binclass-algorithms` module
- General components in `binclass-components`
- UI routes and views in `binclass-ui`
- CLI commands in `binclass-cli`

### Key Design Decisions

- Uses **JSpecify** annotations (`@NullMarked`, `@Nullable`) for null safety
- No Lombok, no Spring DI/CDI frameworks
- Records preferred for DTOs, EventBus events, and immutable data holders
- Entity equality based on `id` field
- Utility methods kept static

## Testing Strategy

- **Unit tests**: JUnit 6 for algorithmic logic
- **BrowserlessTests**: For view components
- **Playwright e2e**: Integration tests for custom components with client-side code

## Documentation

- [Original C Implementation](docs/BinclassOriginal.md) — Source documentation
- [Mathematical Foundations](docs/Binclass%20-%20Software%20Package%20For%20Classifying%20Binary%20Vectors.md) — Theory and algorithms
- [Java Migration Plan](docs/JavaMigrationPlan.md) — Migration details

## License

See [LICENSE.txt](LICENSE.txt) for license information.

## References

Original C implementation by Tatu J. Lund, University of Turku, Department of Applied Mathematics.
