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
