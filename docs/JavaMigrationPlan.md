# BinClass Java Migration Plan

## Executive Summary

This document describes the step-by-step migration of the original C-based BinClass software (30+ source files in `original/`) into a modern Java 25 multi-module Maven project. The algorithms live in `binclass-algorithms` as pure library code, while `binclass-cli` provides the command-line interface that wires everything together. Every algorithm class and data structure is backed by unit tests; the CLI module has parser-correctness tests for every subcommand.

---

## 1. Target Architecture

```
binclass_java/                              ← repo root (parent POM)
├── pom.xml                                 ← parent: Java 25, Spotless, JUnit 6, modules
│
├── binclass-algorithms/                    ← pure algorithm library (no I/O deps)
│   ├── src/main/java/org/binclass/algorithms/
│   │   ├── core/                           ← C structs → Java classes with methods
│   │   │   ├── BinaryVector.java           ← BV struct + operations
│   │   │   ├── VectorSet.java              ← ST linked-list set
│   │   │   ├── Partition.java              ← array of VectorSets indexed by class #
│   │   │   ├── DynamicPartition.java       ← DynPartition (freq/hamming tracking)
│   │   │   ├── Centroid.java               ← probability distribution centroid
│   │   │   ├── InfiniteCentroids.java      ← InfCentroid collection + SC/I/I2
│   │   │   ├── TreeNode.java               ← hierarchical tree node
│   │   │   └── FrequencyTable.java         ← Frequencies with linkage
│   │   ├── data/                           ← vector/matrix utilities
│   │   │   ├── DoubleVector.java           ← Vector (double[])
│   │   │   ├── IntVector.java              ← IntVector (int[])
│   │   │   ├── DoubleMatrix.java           ← Matrix (jagged 2D)
│   │   │   └── IntMatrix.java              ← IntMatrix (jagged int[][])
│   │   ├── dist/                           ← distance metrics & nearest-neighbor
│   │   │   ├── DistanceCalculator.java     ← all static distance functions
│   │   │   └── NearestNeighbor.java        ← inf_nn, fast_nn, MAE_nn
│   │   ├── centroid/                       ← centroid management
│   │   │   ├── CentroidManager.java        ← allocate/deallocate/load/save/mix
│   │   │   └── CentroidInitializer.java    ← random/pick/PNN2 initialization
│   │   ├── classify/                       ← classification algorithms
│   │   │   ├── Classifier.java             ← main classify_vectors + identify
│   │   │   ├── MixtureClassifier.java      ← EM algorithm (m_fx, m_fM)
│   │   │   └── CumulativeClassifier.java   ← cumulative + reidentification
│   │   ├── gla/                            ← Generalized Lloyd Algorithm
│   │   │   ├── GLAEngine.java              ← all 8+ variants
│   │   │   ├── SplitGLA.java               ← split-GLA for k search
│   │   │   └── JoinGLA.java                ← join-GLA algorithm
│   │   ├── tree/                           ← hierarchical trees
│   │   │   └── TreeBuilder.java            ← PNN tree construction
│   │   ├── report/                         ← statistical reports
│   │   │   └── ReportGenerator.java        ← class nearness, frequency analysis
│   │   ├── generate/                       ← synthetic data generation
│   │   │   └── DataGenerator.java          ← Bernoulli, Markov, random
│   │   ├── io/                             ← file I/O and format parsing
│   │   │   ├── FormatParser.java           ← header parsing (read_header)
│   │   │   ├── VectorReader.java           ← binary vector reading
│   │   │   └── VectorWriter.java           ← picture/empirical output formats
│   │   ├── info/                           ← information-theoretic functions
│   │   │   └── InfoFunctions.java          ← SC, Shannon entropy (static)
│   │   └── util/                           ← standalone utilities
│   │       ├── MathUtils.java              ← log2, gamma, factorial (from bottom.h)
│   │       └── LogUtils.java               ← log_profile, log_function
│   │
│   └── src/test/java/org/binclass/algorithms/
│       ├── core/BinaryVectorTest.java
│       ├── core/VectorSetTest.java
│       ├── core/PartitionTest.java
│       ├── core/DynamicPartitionTest.java
│       ├── core/CentroidTest.java
│       ├── core/InfiniteCentroidsTest.java
│       ├── data/DoubleVectorTest.java
│       ├── data/DoubleMatrixTest.java
│       ├── dist/DistanceCalculatorTest.java
│       ├── dist/NearestNeighborTest.java
│       ├── centroid/CentroidManagerTest.java
│       ├── centroid/CentroidInitializerTest.java
│       ├── classify/ClassifierTest.java
│       ├── classify/MixtureClassifierTest.java
│       ├── gla/GLAEngineTest.java
│       ├── gla/SplitGLATest.java
│       ├── tree/TreeBuilderTest.java
│       ├── report/ReportGeneratorTest.java
│       ├── generate/DataGeneratorTest.java
│       └── io/FormatParserTest.java
│
├── binclass-cli/                           ← command-line application
│   ├── src/main/java/org/binclass/cli/
│   │   ├── BinClassApp.java                ← main() entry point
│   │   ├── CliParser.java                  ← argument parsing & validation
│   │   ├── CommandRegistry.java            ← command name → handler mapping
│   │   └── commands/                       ← one class per subcommand
│   │       ├── IdentifyCommand.java        ← identify_vectors
│   │       ├── ClassifyCommand.java        ← classify_vectors
│   │       ├── CompareCommand.java         ← compare_partitions
│   │       ├── ReportCommand.java          ← generate_report
│   │       ├── GenerateCommand.java        ← data generation (bernoulli/markov/random)
│   │       ├── BootstrapCommand.java       ← bootstrap GLA trials
│   │       ├── FastClassifyCommand.java    ← fclassify_vectors (Split-GLA)
│   │       ├── CumulativeCommand.java      ← cumulative classification
│   │       ├── SemiCumulativeCommand.java  ← semi-cumulative classification
│   │       ├── TreeCommand.java            ← make_tree
│   │       ├── CentroidCommand.java        ← save/load centroids
│   │       ├── SortPartCommand.java        ← sort/partition operations
│   │       ├── MixtureCommand.java         ← mixture classifier (EM)
│   │       ├── CutCommand.java             ← cut/trim partitions
│   │       ├── FunctionCommand.java        ← render SC/Shannon functions vs k
│   │       └── TestAlgorithmsCommand.java  ← test alg1/al2 modules
│   │
│   └── src/test/java/org/binclass/cli/
│       ├── CliParserTest.java              ← argument parsing correctness
│       ├── CommandRegistryTest.java        ← command dispatch validation
│       └── commands/                       ← per-command unit tests
│           ├── IdentifyCommandTest.java
│           ├── ClassifyCommandTest.java
│           ├── CompareCommandTest.java
│           ├── ReportCommandTest.java
│           ├── GenerateCommandTest.java
│           ├── BootstrapCommandTest.java
│           ├── FastClassifyCommandTest.java
│           ├── CumulativeCommandTest.java
│           ├── TreeCommandTest.java
│           ├── CentroidCommandTest.java
│           ├── MixtureCommandTest.java
│           └── FunctionCommandTest.java
```

---

## 2. Module Dependencies

```
binclass-algorithms   ← no internal dependencies (pure library, UI-agnostic)
binclass-cli          → binclass-algorithms (uses all algorithm classes + I/O package)
```

The `binclass-components` and `binclass-ui` modules (already in the parent POM) are out of scope for this migration plan — they are a future reservation for a Vaadin-based web UI. The algorithms module must remain completely UI-agnostic with no Vaadin or web dependencies.

### I/O Separation of Concerns

The original C code mixes file I/O directly into algorithm functions (e.g., `classify_vectors` reads files internally). In the Java version, we separate concerns:

```
binclass-algorithms/io/          ← Isolated I/O package for format parsing & data reading/writing
├── FormatParser.java            ← Header parsing only (no file I/O — takes parsed values)
├── VectorReader.java             ← Reads binary vectors from a byte stream / byte array
└── VectorWriter.java             ← Writes binary vectors to a byte stream / byte array

binclass-cli/                    ← Only module that handles actual file system I/O
    └── commands/*               ← Opens files, reads headers, delegates to algorithms/io for parsing
```

- **`binclass-algorithms/io/`**: Pure functions operating on `byte[]`, `InputStream`, or `OutputStream`. No `java.io.File` dependencies. This keeps the package testable without file system access.
- **`binclass-cli/commands/*`**: Opens files, reads headers via `FormatParser`, delegates vector I/O to `io/` package, passes parsed data to algorithm classes.

No stdin/stdout piping — matches original C behavior (explicit file paths only).

---

## 3. Java 25 Feature Usage Plan

| Feature | Where Used | Rationale |
|---------|-----------|-----------|
| **Records** (`record`) | `BinaryVector`, `Centroid`, `TreeNode`, `FrequencyTable`, `CommandArgs` (immutable data carriers) | Concise value objects with auto-generated equals/hashCode/toString — all core data structures are immutable by design |
| **Sealed Classes** (`sealed`) | `eHeuristic`, `eDist`, `eDataGen`, `eSearch`, `eCentroidType` → sealed interface hierarchy for exhaustive pattern matching | Exhaustive pattern matching on algorithm state in GLA engine and classifier dispatch |
| **Pattern Matching for switch** | `GLAEngine`, `CliParser` (dispatch by distance type / command name) | Cleaner than traditional switch with fall-through |
| **Text Blocks** (`"""`) | Help text, error messages in CLI commands | Multi-line strings without escape hell |
| **Virtual Threads** (`Thread.ofVirtual()`) | Optional: parallel bootstrap trials in `BootstrapCommand` | Lightweight concurrency for I/O-bound file operations |
| **Records Patterns** | Decomposing algorithm results (e.g., GLA return values) | Clean destructuring of multi-value returns |
| **String Templates** (JEP 430 preview / JEP 496 final in Java 25) | Log messages, formatted output via SLF4J MDC | Type-safe string interpolation for logging |

### Logging — SLF4J Across All Modules

Both `binclass-algorithms` and `binclass-cli` use **SLF4J** (already in parent POM as `slf4j.version=2.0.9`) with **Logback** (`logback.version=1.5.35`) as the binding:

| Module | Logging Usage |
|--------|--------------|
| `binclass-algorithms` | Algorithm progress, convergence info, distance calculations — structured logs via SLF4J MDC (includes command context) |
| `binclass-cli` | CLI startup, command dispatch, file I/O events, error reporting — same SLF4J API, different Logback appender configuration |

The original C code uses `fprintf(stdout, ...)` for verbose output (`put_dot`, `put_mark`). In Java:
- Verbose progress dots → `logger.debug(".")` with `%.-1000s` pattern or custom encoder
- Statistical profiles → `logger.info()` with structured key-value format
- Error messages → `logger.error()` with exception context

**Note:** The original C code's `verbose` flag controls output. In Java, this maps to SLF4J log level (`DEBUG` = verbose, `INFO` = normal, `WARN`/`ERROR` = errors). No separate verbose mode needed — standard logging levels suffice.

---

## 4. C-to-Java Mapping — Detailed Module Breakdown

### 4.1 Core Data Structures (`core/`)

| C Source File | C Struct | Java Class | Migration Notes |
|---------------|----------|-----------|-----------------|
| `const.h` | `BV` (binary vector) | `BinaryVector` (record) | **Immutable record.** Fields: `int[] el`, `int[] miss`, `int length`, `String classname`, `String strain`. Methods: `hammingDistance(BinaryVector)`, `copy()`. The 1-indexed element array from C becomes a 0-indexed Java array. Presentation layer (CLI/report) uses 1-based indexing for display. |
| `const.h` | `ST` (vector set) | `VectorSet` | **Backed by `HashSet<BinaryVector>`.** Methods: `addElement(BinaryVector)`, `removeElement()`, `contains(BinaryVector)`, `size()`, `iterator()`. Standard Java Set semantics — no linked-list, no deletion-during-iteration concerns. |
| `const.h` | `Partition` (class partition) | `Partition` | Array of `VectorSet` indexed **0..k-1 internally**, presented as 1..k in CLI/report output. Methods: `allocate(int k)`, `addElement(int classIndex, BinaryVector)`, `size()` (total elements), `getElements(int classIndex)`. Internal 0-based indexing matches Java convention; presentation layer adds +1 for display. |
| `const.h` | `DynPartition` (dynamic partition) | `DynamicPartition` | Wraps a `Partition` plus frequency/hamming mask tracking. Methods: `initialize(BinaryVector)`, `extend(VectorSet)`, `putVector(BinaryVector, int i)`, `convert()` → `Partition`, `predictiveFit()`. I/O operations moved to `io/` package. |
| `const.h` | `Centroid` (probability centroid) | `Centroid` (record) | **Immutable record.** Fields: `double[] el` (prob of 1), `int l`, `double weight`. Precomputed `log0[]` and `log1[]` arrays available via accessor methods. No mutable state — all computation is derived from the probability array. |
| `const.h` | `InfCentroid` (infinite centroids) | `InfiniteCentroids` | Array of `Centroid` indexed 0..k-1 internally, presented as 1..k in output. Methods: `allocate(int k, int l)`, `copyFrom(InfiniteCentroids)`, `calculateSC()`, `getSC()`. I/O (load/save) moved to `io/` package. |
| `const.h` | `TreeNode` (tree node) | `TreeNode` (record) | **Immutable record.** Fields: `double sc`, `String name`, `TreeNode left`, `TreeNode right`. Methods for tree navigation (in-order traversal, etc.). |
| `const.h` | `Frequencies` (freq table) | `FrequencyTable` (record) | **Immutable record.** Fields: `IntVector freq`, `int size`, `boolean linked`, `FrequencyTable linkage`. Used by tree builder and report generator. |

### 4.2 Data Utilities (`data/`)

| C Source File | C Type | Java Class | Migration Notes |
|---------------|--------|-----------|-----------------|
| `vectors.c` | `Vector` (double array) | `DoubleVector` | Dynamic double array using `double[]` internally. Methods: `allocate(int n)`, `get(int i)`, `set(int i, double val)`. No explicit deallocation needed — Java GC handles it. |
| `vectors.c` | `IntVector` (int array) | `IntVector` | Dynamic int array using `int[]` internally. Methods: `allocate(int n)`, `get(int i)`, `set(int i, int val)`. No explicit deallocation needed — Java GC handles it. |
| `vectors.c` | `Matrix` (jagged double[][]) | `DoubleMatrix` | Jagged 2D matrix (`double[][]`). Static methods: `transpose(Matrix)`, `multiply(Matrix, Matrix)`, `inverse(Matrix)`, `pseudoInverse(Matrix)`, `matrixVectorMultiply(Matrix, Vector)`. All use Java native `double`/`Double`. |
| `vectors.c` | `IntMatrix` (jagged int[][]) | `IntMatrix` | Jagged 2D int matrix (`int[][]`). Static methods: `allocate(int x, int y)`, `get(int row, int col)`, `set(int row, int col, int val)`. All use Java native `int`. |

### 4.3 Distance & Nearest Neighbor (`dist/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `distmin.c` | `hamming_distance()`, `L1_distance()`, `L2_distance()`, `code_length()`, `code_length2()`, `class_code_length()`, `average_codelength()`, `class_distortion()`, `overall_distortion()`, `class_MAE()`, `overall_MAE()` | `DistanceCalculator` (static utility class) | All functions are pure computations — no state. Each takes appropriate inputs and returns a double/int. Methods mirror C function names with camelCase. |
| `distmin.c` | `inf_nearest_neighbour()`, `fast_nearest_neighbour()`, `MAE_nearest_neighbour()` | `NearestNeighbor` (static utility class) | Static methods operating on VectorSet, Partition, and InfiniteCentroids. |

### 4.4 Centroid Management (`centroid/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `centroid.c` | `allocate_centroid()`, `deallocate_centroid()`, `allocate_centroids()`, `deallocate_centroids()`, `load_centroids()`, `save_centroids()`, `random_centroids()`, `copy_centroids()`, `edistance_2()`, `pick_centroids()`, `add_centroid()` | `CentroidManager` (static) + `CentroidInitializer` (static) | Split into two classes: manager handles lifecycle I/O, initializer handles creation strategies. |

### 4.5 Classification (`classify/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `classify.c`, `adding.c` | `identify_vectors_by_classification()`, `identifier_by_class()`, `identify_vectors()` | `Classifier` | Main classification entry point. Methods: `identifyVectors(File, VectorSet, Partition, InfiniteCentroids)`, `classifyVectors(String datfile, String outfile, String parfile, String ctrfile, String misfile, String hdrfile)`. |
| `mixture.c` | `apply_mixture_classifier()`, `apply_mixture_classifier_once()`, `calculate_matrix()`, `m_fx()`, `m_fM()`, `random_weights()` | `MixtureClassifier` | EM algorithm implementation. Methods: `applyMixtureClassifier(String datfile, String outfile, String parfile1, String parfile2, String resfile, String hdrfile, int m)`. Internal E-step/M-step logic. |
| `cumulat.c` | `do_cumulative_classification()`, `analyse_cumulative()`, `reidentification_analysis()` | `CumulativeClassifier` | Cumulative classification with DynamicPartition quality evaluation. Methods: `doCumulativeClassification(String datfile, String basfile, String outfile, String parfile, String hdrfile)`. |

### 4.6 GLA Algorithms (`gla/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `glainf.c` | `use_gla()`, `use_gla_load_centroids()`, `special_gla()`, `gla()`, `gla_sr()`, `gla_sa()`, `hybrid_gla_l1()`, `hybrid_gla_l2()`, `MAE_gla()`, `MSE_gla()`, `fast_gla()` | `GLAEngine` | All 8+ variants share the same interface. Each variant differs in initialization heuristic and distance metric. Methods: `gla(VectorSet, Partition, InfiniteCentroids, double[] dmin, int n)`, etc. The `use_gla()` wrapper handles trial management and statistical output. |
| `splitgla.c` | `split_gla()`, `fclassify_vectors()`, `worst_matching_vectors()`, `abs_worst_matching_vectors()`, `set_first_centroids_pnn2()`, `bv_hamming_distance()`, `point_worst_class()`, `set_new_centroids()` | `SplitGLA` | Split-GLA hybrid for k search. Methods: `splitGLA(VectorSet, double[] scmin, double[] scs, String outfile, String parfile)`. Internal helpers for worst-matching vector identification and centroid initialization. |
| `joingla.c` | `use_join_gla()` | `JoinGLA` | Join-GLA hierarchical merging. Method: `useJoinGLA(String datfile, String outfile, String parfile, String hdrfile)`. |

### 4.7 Tree Construction (`tree/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `tree.c` | `make_tree_pnn()`, `make_tree_pnn2()`, `inf_average12()`, `hellinger_distance()`, `special_complexity()`, `special_complexity_u()`, `special_complexity_j()` | `TreeBuilder` | Builds dendrogram from partition data using information content and Hellinger distance. Methods: `makeTreePNN(File, InfiniteCentroids, Partition, Vector)`. Internal helpers for inf_loss calculation and frequency linkage. |

### 4.8 Report Generation (`report/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `report.c` | `generate_report()`, `class_nearness()` | `ReportGenerator` | Statistical report from classification output. Uses FList/CList structures for frequency tracking. Methods: `generateReport(File, File, String misfile, String hdrfile)`. |

### 4.9 Data Generation (`generate/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `gendat.c` | `bernouli_gen()`, `markov_gen()`, `vector_gen()`, `random_gen()` | `DataGenerator` | Synthetic binary data generation. Methods: `bernoulliGen(Partition, int amount)`, `markovGen(VectorSet, int amount)`, `vectorGen(VectorSet, int amount)`, `randomGen(int amount)`. Returns VectorSet of generated vectors. |

### 4.10 File I/O (`io/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `format.c` | `read_header()`, `parse_hdr_str()`, `conv_hex()` | `FormatParser` (static) | Header file parsing. Methods: `readHeader(String hdrfile)` — sets vec_len, vec_offs from header. |
| `binstuff.c` | `pic_write_bv()`, `emp_write_bv()`, `pic_read_bv()`, `bv_dist()` | `VectorWriter` / `VectorReader` (static) | Binary vector I/O in picture and empirical formats. Methods: `picWriteBv(File, BinaryVector)`, `empWriteBv(File, BinaryVector, int noPrintCl)`. |

### 4.11 Information Functions (`info/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `function.c` | `render_functions()`, `a1()`, `a2()`, `b1()`, `b2()` | `InfoFunctions` (static) | Information-theoretic calculations. Methods: `renderFunctions(String datfile, String outfile, String ctrfile, String hdrfile)` — computes SC/Shannon entropy as function of k. Internal harmonic sum helpers a1/a2/b1/b2. |

### 4.12 Utilities (`util/`)

| C Source File | Functions | Java Class | Migration Notes |
|---------------|-----------|-----------|-----------------|
| `bottom.h` | `log_2()`, `log_2e()`, `log2_factorial()`, `log2_gamma()` | `MathUtils` (static) | Low-level math utilities. Methods: `log2(double x)`, `log2e(double x)`, `log2Factorial(int x)`, `log2Gamma(double x)`. Platform-specific gamma implementation handled via Java's built-in `StrictMath.log()`. All use Java native `double`/`Double`. |
| `logfile.c` | `log_profile()`, `log_function()` | `LogUtils` (static) | Logging utilities. Methods: `logProfile(int l, int s)`, `logFunction(double[] scs, int lastk)`. **Note:** I/O moved to `io/` package — these methods operate on byte arrays or return formatted strings for the CLI layer to write. Uses SLF4J for all logging output across both modules. |

---

## 5. CLI Module — Command-to-Algorithm Mapping

| Subcommand | Java Class | C Source | Algorithm Delegation | Key Arguments |
|-----------|-----------|----------|---------------------|---------------|
| `identify` | `IdentifyCommand` | `parse_identify()` → `adding.c` | `Classifier.identifyVectors()` | datfile, vecfile, outfile, hdrfile |
| `classify` | `ClassifyCommand` | `parse_classify()` → `classify.c` | `Classifier.classifyVectors()` | datfile, outfile, parfile, ctrfile, misfile, hdrfile |
| `compare` | `CompareCommand` | `parse_compare()` → `compare.c` | Partition comparison utilities | datfile, parfile1, parfile2, resfile, hdrfile |
| `report` | `ReportCommand` | `parse_report()` → `report.c` | `ReportGenerator.generateReport()` | misfile, hdrfile |
| `generate` | `GenerateCommand` | `parse_generate()` → `gendat.c` | `DataGenerator` (Bernoulli/Markov/random) | datfile/genfile, amount, hdrfile |
| `bootstrap` | `BootstrapCommand` | `parse_bootstrap()` → `bootstra.c` | `GLAEngine` + `CentroidInitializer` | k, outfile, parfile, hdrfile |
| `fclassify` | `FastClassifyCommand` | `parse_fclassify()` → `splitgla.c` | `SplitGLA.fclassifyVectors()` | datfile, outfile, parfile, hdrfile |
| `cumulative` | `CumulativeCommand` | `parse_cumulative()` → `cumulat.c` | `CumulativeClassifier.doCumulativeClassification()` | datfile, basfile, outfile, parfile, hdrfile |
| `sclassify` | `SemiCumulativeCommand` | `parse_sclassify()` → `splitgla.c` | Semi-cumulative variant | datfile, outfile, parfile, hdrfile |
| `tree` | `TreeCommand` | `parse_tree()` → `tree.c` | `TreeBuilder.makeTreePNN()` | parfile, trefile1, trefil2, hdrfile |
| `centroids` | `CentroidCommand` | `parse_centroids()` → `centroid.c` | `InfiniteCentroids.load/save()` | ctrfile, hdrfile |
| `sortpart` | `SortPartCommand` | `parse_sortpart()` → partition ops | Partition sorting utilities | parfile, datfile, outfile, hdrfile |
| `mixture` | `MixtureCommand` | `parse_mixture()` → `mixture.c` | `MixtureClassifier.applyMixtureClassifier()` | datfile, outfile, parfile1, parfile2, resfile, hdrfile, m |
| `cut` | `CutCommand` | `parse_cut()` → `cut.c` | Partition cutting utilities | parfile, datfile, parfile1, parfile2, hdrfile |
| `function` | `FunctionCommand` | `parse_function()` → `function.c` | `InfoFunctions.renderFunctions()` | datfile, outfile, ctrfile, hdrfile |
| `test1` | `TestAlgorithmsCommand` | `parse_test1()` → `t_alg_1.c` | Test algorithm 1 (distortion minimizer) | datfile, outfile, parfile, hdrfile |
| `test2` | `TestAlgorithmsCommand` | `parse_test2()` → `t_alg_2.c` | Test algorithm 2 (semi-cumulative) | datfile, outfile, parfile, hdrfile |

---

## 6. CLI Architecture Details

### 6.1 CliParser — Custom Argument Parsing

```java
// Hand-rolled parser matching original C behavior — no external dependency
public class CliParser {
    public record CommandArgs(String command, Map<String, String> options) {}
    
    // Parse raw args into structured CommandArgs
    public CommandArgs parse(String[] args);
    
    // Validate required options per command
    public List<String> validate(CommandArgs args);  // returns error messages
    
    // Generate help text for a specific command or all commands
    public String helpText();
    public String helpTextForCommand(String command);
}
```

**Design decision:** Hand-rolled parsing using `String.split()` and manual tokenization — no external dependency (Picocli, Apache Commons CLI, etc.). This matches the original C parser's behavior closely. Each subcommand has its own validation rules defined in a registry map. The parser handles both `--option=value` and `--option value` syntax forms used by the original C application.

**Parsing approach:**
1. Tokenize input into command + option tokens (split on whitespace)
2. First non-option token is the subcommand name
3. Remaining tokens are parsed as `-flag`, `--long-flag=value`, or positional arguments
4. Each registered command declares its expected options in `CommandRegistry`
5. Validation checks required options present, types match, ranges valid

**Advantages over external library:**
- Zero additional dependencies — matches the lean original C build
- Exact behavioral parity with C parser (same error messages, same option handling)
- Full control over help text formatting to match original output

### 6.2 CommandRegistry

```java
public class CommandRegistry {
    private static final Map<String, Class<? extends BaseCommand>> COMMANDS = Map.of(
        "identify", IdentifyCommand.class,
        "classify", ClassifyCommand.class,
        // ... all 16+ commands
    );
    
    public Set<String> registeredCommands();
    public Class<? extends BaseCommand> getCommandClass(String name);
    public String helpTextFor(String command);
}
```

### 6.3 BaseCommand Interface

```java
public interface BaseCommand {
    String getName();
    String getDescription();
    String[] getRequiredOptions();
    String[] getOptionalOptions();
    int execute(CommandArgs args) throws Exception;
}
```

Each command implementation:
1. Validates its own arguments (returns error code if invalid)
2. Delegates to the appropriate algorithm class in `binclass-algorithms`
3. Handles file I/O and output formatting
4. Returns an exit code (0 = success, non-zero = error)

---

## 7. Testing Strategy

### 7.1 binclass-algorithms — Unit Tests

| Test Class | What It Verifies | Key Test Cases |
|-----------|-----------------|---------------|
| `BinaryVectorTest` | Vector creation, distance, copy | Hamming distance correctness, vector equality, length validation |
| `VectorSetTest` | HashSet-backed set operations | Add/remove/contains elements, size, iteration order (HashSet ordering) |
| `PartitionTest` | Partition management | Allocate/deallocate, add element to class, total size, 0-based internal indexing |
| `DynamicPartitionTest` | Dynamic partition with freq tracking | Initialize from vector, extend, convert to Partition, predictive fit |
| `CentroidTest` | Centroid probability computation | Log-probability precomputation, weight handling, allocation |
| `InfiniteCentroidsTest` | Centroid collection + SC/I/I2 | Allocate/deallocate, copy, SC calculation (I/O tests in io/ package) |
| `DoubleVectorTest` | Double vector operations | Allocation, indexing, set/get, Java native double precision |
| `DoubleMatrixTest` | Matrix operations | Transpose, multiply, inverse correctness (small matrices), Java native double |
| `DistanceCalculatorTest` | All distance metrics | Hamming/L1/L2/code-length against known values, class distortion, MAE — all using Java native `double`/`Double` |
| `NearestNeighborTest` | Nearest neighbor algorithms | inf_nn/fast_nn/MAE_nn assignment correctness |
| `CentroidManagerTest` | Centroid lifecycle (no I/O) | Allocate/deallocate, mix centroids, copy — pure computation tests |
| `CentroidInitializerTest` | Initialization strategies | Random, pick, PNN2 initialization produce valid centroids |
| `ClassifierTest` | Main classification | Identify/classify with known centroids, distance ranking output |
| `MixtureClassifierTest` | EM algorithm convergence | Log-likelihood increases, probability matrix sums to 1, convergence — uses fixed seed for reproducibility |
| `GLAEngineTest` | All GLA variants | Convergence on synthetic data (fixed seed), centroid update correctness, each variant produces valid partition |
| `SplitGLATest` | k search algorithm | Finds reasonable k on synthetic data (fixed seed), worst-matching vector identification |
| `TreeBuilderTest` | Tree construction | Valid hierarchy produced, inf_loss calculations correct, Hellinger distance — all using Java native `double`/`Double` |
| `ReportGeneratorTest` | Report generation | Frequency counting accuracy, class nearness computation |
| `DataGeneratorTest` | Synthetic data properties | Bernoulli/Markov/random produce expected distribution statistics (fixed seed) |
| `FormatParserTest` | Header parsing | Correct vec_len/vec_offs extraction from various header formats — operates on byte arrays, no file I/O |

**Coverage target:** ≥80% line coverage across all algorithm classes. Edge cases tested: empty sets, single vector, equal vectors, zero-length centroids, large k values.

**Note on floating-point precision (Q9):** All tests use Java native `double`/`Double`. Since both C and Java use IEEE 754 64-bit doubles, identical operations produce identical results. Tests use exact equality (`assertEquals`) for deterministic computations; epsilon-based comparison (`assertThat(actual).isCloseTo(expected, within(1e-10))`) only where the original C code uses `epsilon` (0.001) for numerical stability checks.

### 7.2 binclass-cli — Parser & Command Tests

| Test Class | What It Verifies | Key Test Cases |
|-----------|-----------------|---------------|
| `CliParserTest` | Argument tokenization & validation | Valid args for each command, missing required options, unknown commands, help flag, option=value vs space-separated formats |
| `CommandRegistryTest` | Command dispatch | All 16+ commands registered, correct class mapping, valid help text for each |
| Per-command tests | Correct delegation + output format | Each command with sample args produces expected algorithm call, error handling for invalid inputs |

**Coverage target:** 100% parser coverage. Each command tested with at least one valid and one invalid argument set.

### 7.3 Test Framework & Tools

| Tool | Purpose |
|------|---------|
| JUnit 6 (6.1.1) | Test framework (already in parent POM as `junit6.version`) |
| AssertJ | Fluent assertions |
| JaCoCo | Code coverage reporting |
| `@TempDir` (JUnit 5/6) | Temporary file/directory for I/O tests |

---

## 8. Build System Configuration

### Parent POM Updates Needed

```xml
<!-- Add to existing <properties> in parent pom.xml -->
<java.version>25</java.version>
<maven.compiler.source>25</maven.compiler.source>
<maven.compiler.target>25</maven.compiler.target>
<junit.jupiter.version>6.1.1</junit.jupiter.version>
```

### binclass-algorithms POM

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### binclass-cli POM

```xml
<dependencies>
    <dependency>
        <groupId>org.binclass</groupId>
        <artifactId>binclass-algorithms</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Executable JAR with main class -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <configuration>
                <archive>
                    <manifest>
                        <mainClass>org.binclass.cli.BinClassApp</mainClass>
                    </manifest>
                </archive>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 9. Migration Phases & Order

### Phase 1: Foundation — Core Data Structures + Utilities (Week 1-2)

**Deliverables:**
- All `core/` classes with methods extracted from C structs
- All `data/` utility classes
- `util/MathUtils.java` and `util/LogUtils.java`
- Unit tests for all above classes
- Parent POM updated with Java 25, module declarations, dependencies

**Why first:** Everything else depends on these building blocks. Tests verify correctness before any algorithm is built on top.

### Phase 2: Distance & Centroid Layer (Week 3)

**Deliverables:**
- `dist/DistanceCalculator.java` + tests
- `dist/NearestNeighbor.java` + tests
- `centroid/CentroidManager.java` + tests
- `centroid/CentroidInitializer.java` + tests

**Why second:** Classification and GLA algorithms need distance metrics and centroid management before they can be implemented.

### Phase 3: Core Algorithms (Week 4-5)

**Deliverables:**
- `classify/Classifier.java` + tests
- `classify/MixtureClassifier.java` + tests
- `classify/CumulativeClassifier.java` + tests
- `gla/GLAEngine.java` + tests (all 8+ variants)
- `gla/SplitGLA.java` + tests
- `gla/JoinGLA.java` + tests

**Why third:** These are the heart of the application. Built on top of phases 1-2, they form the complete algorithmic core.

### Phase 4: Supporting Algorithms (Week 6)

**Deliverables:**
- `tree/TreeBuilder.java` + tests
- `report/ReportGenerator.java` + tests
- `generate/DataGenerator.java` + tests
- `io/FormatParser.java`, `VectorReader.java`, `VectorWriter.java` + tests
- `info/InfoFunctions.java` + tests

**Why fourth:** These complete the algorithmic surface area. Tree building and reporting depend on core algorithms; data generation is independent but needed for CLI testing.

### Phase 5: CLI Module (Week 7)

**Deliverables:**
- `BinClassApp.java` (main entry point)
- `CliParser.java` + tests
- `CommandRegistry.java` + tests
- All 16+ command implementations + tests
- Integration smoke test (generate → classify → report pipeline)

**Why last:** CLI depends on all algorithm classes being complete and tested. Parser can be developed incrementally but full integration requires working algorithms.

---

## 10. Open Questions — Resolved

All questions have been answered by the user. Summary of decisions:

| # | Question | Answer | Decision |
|---|----------|--------|----------|
| Q1 | Should `BinaryVector` be a record or class? | **Record (immutable)** | All core data structures are immutable records — algorithms work well with value semantics |
| Q2 | External arg parser vs hand-rolled? | **Hand-rolled** | No external dependency; custom parsing matches original C behavior closely |
| Q3 | 0-based or 1-based indexing for `Partition`? | **0-based internally, 1-based on presentation** | Internal algorithms use Java convention (0..k-1); CLI/report output adds +1 for display |
| Q4 | Should `VectorSet` be a linked list or standard Set? | **Standard Java `HashSet<BinaryVector>`** | No need to preserve C linked-list semantics; HashSet gives O(1) lookup, clean iteration |
| Q5 | How to handle global `epsilon` variable? | **Global static field** | Keep as module-level config (e.g., `MathUtils.EPSILON = 0.001`) — matches original C behavior |
| Q6 | File format compatibility with original? | **Same binary file formats** | Backward compatible with existing C-generated datasets; header + raw binary data |
| Q7 | stdin/stdout piping needed? | **No** | Original uses explicit file paths only; algorithms module has isolated `io/` package for I/O-dependent operations, CLI handles actual file system I/O |
| Q8 | Golden test dataset for regression? | **Not applicable** | Algorithms use randomization with variable seed — byte-for-byte comparison won't work; tests verify algorithmic correctness (convergence, valid partitions) rather than exact output |
| Q9 | Floating-point precision tolerance? | **Java native `double`/`Double`** | Both C and Java use IEEE 754 — identical operations produce identical results; exact equality for deterministic computations, epsilon-based only where original uses `epsilon` (0.001) |
| Q10 | Are `binclass-components`/`binclass-ui` in scope? | **No — future Vaadin reservation** | Algorithms module must be UI-agnostic with no Vaadin/web dependencies; these modules handled separately later |
| Q11 | Output identical to C version? Logging? | **Output files identical, logging uses SLF4J** | File output (data, reports) byte-for-byte identical; all logging via SLF4J + Logback (already in parent POM); verbose mode maps to log levels (`DEBUG`=verbose, `INFO`=normal) |

---

## 11. Risk Register — Updated

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| HashSet iteration order differs from C linked-list traversal order | Medium | Low | Original code doesn't depend on iteration order for correctness (distance calculations are commutative); verify in tests |
| Floating-point divergence between C `double` and Java `double` | Low | Low | Both use IEEE 754 — identical operations produce identical results; verified by Q9 decision |
| I/O package boundary leak (file ops in algorithms module) | Medium | Medium | Strict separation: `io/` package takes byte arrays/streams, CLI handles file opening; enforced via code review |
| Missing edge cases in original C code (e.g., NULL checks) | Medium | Low | Original has `internal_error()` guards — replicate as `Objects.requireNonNull` or custom exceptions |
| 0-based vs 1-based indexing off-by-one at presentation boundary | Medium | Medium | All presentation-layer code (CLI output, report formatting) explicitly documented; tests verify both internal and displayed values |

---

## 12. Success Criteria

- [ ] All 30+ original C source files mapped to Java classes/modules
- [ ] `binclass-algorithms` compiles and all unit tests pass (≥80% coverage)
- [ ] `binclass-cli` compiles, parser handles all commands, all command tests pass
- [ ] Output files byte-for-byte identical to C version for test dataset
- [ ] Java 25 features used appropriately (records, sealed classes, pattern matching)
- [ ] SLF4J + Logback logging across both modules (verbose → DEBUG level)
- [ ] I/O separation of concerns enforced — algorithms `io/` package uses streams only
- [ ] binclass-algorithms is UI-agnostic (no Vaadin/web dependencies)
- [ ] Spotless code formatting applied consistently across both modules
