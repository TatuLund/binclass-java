# Remaining Functionalities — Implementation Plan

This document captures the gap analysis between the original C CLI (`original/parser.c`) and the current Java CLI commands in `binclass-cli`. It lists missing switches, unimplemented algorithms, and missing commands so that future work can be tracked.

---

## 1. Commands with Missing Switches

### 1.1 IdentifyCommand
**C parser switches:** `-E`, `-q`, `-t`, `-f`, `-J`, `-w`, `-M`, `-P`  
**Java currently supports:** `-E`, `-f`, `-J`, `-w`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode (verbose = FALSE) | **Missing** | Add `setupVerboseMode(opts)` call |
| `-t`   | trashcan mode | **Missing** | Pass to Classifier or store in config |
| `-M`   | exact matches only | **Missing** | Store as boolean option |
| `-P <file>` | store partition to file | **Missing** | Write partition after identification |

---

### 1.2 ClassifyCommand
**C parser switches:** `-b`, `-s`, `-S`, `-E`, `-q`, `-r`, `-t`, `-e`, `-m`, `-c`, `-l`, `-J`, `-w`, `-B`, `-C`, `-R`, `-f`, `-n`, `-j`, `-F`, `-a`, `-d`, `-L`  
**Java currently supports:** Most of the above (needs verification against full file)

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |
| `-t`   | trashcan mode | **Missing** | Pass to GLA engine |
| `-d <file>` | dump file path | **No-op** | Parsed but not used — needs implementation |
| `-L <file>` | centroid file path | **No-op** | Parsed but not used — needs implementation |

---

### 1.3 CompareCommand
**C parser switches:** `-M`, `-q`, `-V`  
**Java currently supports:** `-V`, `-M`, `-q`  

✅ All switches present. However, the command does not actually call any comparison algorithm — see Section 4.

---

### 1.4 ReportCommand
**C parser switches:** `-E`, `-q`, `-d`, `-a`, `-w`, `-h`, `-p`, `-l`  
**Java currently supports:** `-p` only  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-E <val>` | epsilon threshold | **Missing** | Add parsing and pass to report generator |
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |
| `-d`   | print digits | **Missing** | Boolean flag for ReportGenerator |
| `-a`   | affinity matrix | **Missing** | Boolean flag for ReportGenerator |
| `-w`   | class weights | **Missing** | Pass to report generator |
| `-h`   | Hellinger distance | **Missing** | Boolean flag for ReportGenerator |
| `-l`   | log centroids | **Missing** | Boolean flag for ReportGenerator |

---

### 1.5 GenerateCommand
**C parser switches:** `-v`, `-q`, `-u`, `-G`  
**Java currently supports:** `-v`, `-u`, `-G`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.6 BootstrapCommand
**C parser switches:** `-v`, `-P`, `-J`, `-r`, `-N`, `-K`, `-E`, `-I`, `-q`, `-c`  
**Java currently supports:** Most of the above  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.7 FastClassifyCommand (fclassify)
**C parser switches:** `-q`, `-A`, `-S`, `-J`, `-E`  
**Java currently supports:** `-S`, `-J`, `-E`, `-A`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.8 CumulativeCommand
**C parser switches:** `-q`, `-O`, `-I`, `-S`, `-F`, `-c`, `-n`, `-E`, `-N`, `-s`, `-D`, `-d`  
**Java currently supports:** Most of the above  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.9 SemiCumulativeCommand (sclassify)
**C parser switches:** `-q`, `-A`, `-E`, `-J`, `-j`, `-T`  
**Java currently supports:** All switches parsed, but values are **unused**

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |
| `-A`   | absolute match | **Parsed but unused** | Stored in local var, never passed to classifier |
| `-E`   | epsilon / decreasing | **Parsed but unused** | C supports both `decreasing_epsilon = TRUE` (no value) and `epsilon = atof(value)`; Java only parses double |
| `-J`   | Jeffreys prior | **Parsed but unused** | Stored in local var, never passed to classifier |
| `-j`   | join target | **Parsed but unused** | Stored as int, never passed to classifier |
| `-T`   | GLA threshold | **Parsed but unused** | Stored as double, never passed to classifier |

> **Critical issue:** The command parses all switches correctly but calls `CumulativeClassifier.doCumulativeClassification(vectorSet, CumulativeConfig.defaults())` with hardcoded defaults instead of building a config from the parsed options. This means `-A`, `-E`, `-J`, `-j`, and `-T` have no effect on the algorithm behavior.

---

### 1.10 MixtureCommand
**C parser switches:** `-q`, `-E`, `-k`, `-s`  
**Java currently supports:** `-E`, `-k`, `-s`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.11 TreeCommand
**C parser switches:** `-H`, `-J`, `-q`  
**Java currently supports:** `-H`, `-J`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.12 CutCommand
**C parser switches:** `-r`, `-s`, `-m`, `-q`, `-A`, `-a`  
**Java currently supports:** `-r`, `-A`, `-a`, `-D`, `-d`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-s`   | minimal interval | **Missing** | Boolean flag for Cut algorithm |
| `-m`   | maximal interval | **Missing** | Boolean flag for Cut algorithm |
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call (see SortPartCommand) |

> Note: C source uses `atoi()` for both `-D` and `-d` flags in CutCommand, so delta values are integers.

---

### 1.13 FunctionCommand
**C parser switches:** `-q`, `-w`, `-f`  
**Java currently supports:** `-w`, `-f`  

| Switch | C meaning | Status | Notes |
|--------|-----------|--------|-------|
| `-q`   | quiet mode | **Missing** | Add `setupVerboseMode(opts)` call |

---

### 1.14 CentroidCommand & SortPartCommand
Both commands only support `-q` in C and are minimal in Java — no missing switches, but both lack actual algorithm calls (see Section 4).

---

## 2. Missing Commands Entirely

### 2.1 Test Algorithm 1 (`t_alg_1`) — Distortion Minimizer
**C switches:** `-q`, `-k <k>`, `-r <count>`, `-t <trials>`, `-e`  
**Java status:** ✅ Already implemented in `TestAlgorithmsCommand.java`

| Switch | C meaning | Notes |
|--------|-----------|-------|
| `-k <k>` | number of clusters to test | kstart = value + 1 in C |
| `-r <count>` | replacement swap count | t1_rs_count |
| `-t <trials>` | number of trials | t1_trials |
| `-e`   | extra iterations flag | t1_extra_iter |

**Implementation notes:** This algorithm tests distortion minimization across different k values. It should call the GLA engine with varying k and record distortion metrics. See `original/t_alg_1.c`.

---

### 2.2 Test Algorithm 2 (`t_alg_2`) — Semi-Cumulative Module
**C switches:** `-q`, `-t <threshold>`  
**Java status:** ✅ Already implemented in `TestAlgorithmsCommand.java`

| Switch | C meaning | Notes |
|--------|-----------|-------|
| `-t <threshold>` | threshold value | t2_treshold |

**Implementation notes:** This is a semi-cumulative test module. See `original/t_alg_2.c`.

---

## 3. Commands with Incomplete Algorithm Calls

Several commands parse options correctly but do not fully invoke the underlying algorithms or produce meaningful output:

### 3.1 CompareCommand
- Parses `-M`, `-V`, `-q` correctly
- **Missing:** Actual call to `PartitionComparator.compare()` or equivalent — currently just logs and returns 0
- **C reference:** `compare.c` implements nearness metrics between two partitions

### 3.2 ReportCommand
- Parses `-p` but ignores most other switches (`-E`, `-d`, `-a`, `-w`, `-h`, `-l`)
- **Missing:** Call to `ReportGenerator.generate()` with full parameter set
- **C reference:** `report.c` generates detailed statistical reports
### 3.5 SortPartCommand
- Only logs the filebase and returns — no sorting/partitioning logic implemented
- **Missing:** Actual sort-partition algorithm call
- **C reference:** Sort-part operations in C codebase (likely uses binset.c utilities)

---

### 3.6 SemiCumulativeCommand
- Parses all switches correctly (`-A`, `-E`, `-J`, `-j`, `-T`) but stores them in local variables and **never passes them to the classifier**
- Calls `CumulativeClassifier.doCumulativeClassification(vectorSet, CumulativeConfig.defaults())` with hardcoded defaults — effectively ignores all user-provided options
- **Missing:** Build a proper config from parsed switches (`useAbsMatch`, `epsilon`, `jeffreysPrior`, `joinTarget`, `glaThreshold`) and pass to the classifier
- **C reference:** `joingla.c` implements Join-GLA with PNN merging; `-E` has dual behavior in C: when no value follows, sets `decreasing_epsilon = TRUE`; otherwise parses epsilon as double. Java should support both modes.
- **Priority:** High — currently the command is a no-op that ignores all user input

---

### 3.7 FunctionCommand

## 4. Summary Table

| # | Command | Missing Switches | Missing Algorithm Call | Priority |
|---|---------|-----------------|----------------------|----------|
| 1 | IdentifyCommand | `-q`, `-t`, `-M`, `-P` | Partial (calls Classifier) | Medium |
| 2 | ClassifyCommand | `-q`, `-t` | Partial (GLA logic present) | Low |
| 3 | CompareCommand | — | **Full** — no algorithm call, `-V` validation missing | High |
| 4 | ReportCommand | `-E`, `-q`, `-d`, `-a`, `-w`, `-h`, `-l` | **Full** — no report generation | High |
| 5 | GenerateCommand | `-q` | Partial (calls DataGenerator) | Low |
| 6 | BootstrapCommand | `-q` | Partial (calls SplitGLA) | Low |
| 7 | FastClassifyCommand | `-q` | Partial (calls SplitGLA) | Low |
| 8 | CumulativeCommand | `-q` | Partial (calls CumulativeClassifier) | Low |
| 9 | SemiCumulativeCommand | `-q` | **Full** — uses defaults instead of parsed config | High |
| 10 | MixtureCommand | `-q` | Partial (calls MixtureClassifier) | Low |
| 11 | TreeCommand | `-q` | Partial (calls TreeBuilder) | Low |
| 12 | CentroidCommand | — | **Full** — no file output | Medium |
| 13 | SortPartCommand | `-q` already in C | **Full** — no algorithm call | Medium |
| 14 | CutCommand | `-s`, `-m`, `-q` | Partial (has helper methods) | Medium |
| 15 | FunctionCommand | `-q` | **Partial** — manual SC computation | Medium |

---

## 5. Recommended Implementation Order

### Phase 1: Quick Wins (add `-q` to all commands + fix obvious gaps)
1. Add `setupVerboseMode(opts)` call to every command that's missing it (~10 files)
2. Fix SemiCumulativeCommand to build proper config from parsed options
3. Add missing `-s`, `-m` switches to CutCommand

### Phase 2: Missing Algorithm Calls (high-priority features)
4. Implement CompareCommand — call PartitionComparator with nearness metrics, validate `-V 1|2|3` constraint (reject invalid values)
5. Implement ReportCommand — call ReportGenerator with full parameter set
6. Implement CentroidCommand — save centroids to file after computation
7. Implement SortPartCommand — add sorting/partitioning logic

### Phase 3: Missing Commands (low-priority, new files)
8. Create TestAlgorithm1Command for distortion minimizer testing — **SKIP** — `TestAlgorithmsCommand` already covers both test algorithms in a single command
9. Verify TestAlgorithmsCommand handles all `-k`, `-r`, `-t`, `-e`, `-q` switches correctly

### Phase 4: Polish
10. Add missing switches to IdentifyCommand (`-t`, `-M`, `-P`)
11. Refactor FunctionCommand to use InfoFunctions properly
12. Verify ClassifyCommand passes all options through correctly (especially `-d` and `-L` which are no-ops)

### Phase 5: Tests — Unit & Integration Coverage

All new/modified commands need test coverage per AGENTS.md requirements. Use JUnit 6 for unit tests, BrowserlessTests for view components, and Playwright e2e for custom components with client-side code.

**Unit tests for algorithm calls (binclass-algorithms module):**
- `CompareCommandTest` — verify PartitionComparator is invoked with correct nearness metrics
- `ReportCommandTest` — verify ReportGenerator receives full parameter set from all switches
- `CentroidCommandTest` — verify centroid file output format matches `.centroids` spec
- `SortPartCommandTest` — verify sorting/partitioning logic produces expected partitions
- `SemiCumulativeCommandTest` — verify config is built from parsed options (not defaults)
- `CutCommandTest` — verify `-s`, `-m`, `-D`, `-d` flags work with integer delta values

**Integration tests for CLI switches (binclass-cli module):**
- `ClassifyCommandSwitchesTest` — verify `-d <file>` and `-L <file>` are not no-ops (files are actually used)
- `CompareCommandValidationTest` — verify `-V 1|2|3` constraint is enforced, reject invalid values
- `CutCommandDeltaTypeTest` — verify delta values are parsed as integers, reject doubles with clear error message
- `IdentifyCommandOutputTest` — verify `-P <file>` writes partition using format from Symja #3 (`.partition[X]` suffix)

**End-to-end smoke tests:**
- Run each command with synthetic data and verify non-zero exit codes on valid input
- Verify all commands respect `-q` quiet mode (no INFO/DEBUG output)
- Test file I/O round-trips: write partition → read back → compare

**Algorithmic correctness tests (binclass-algorithms module):**
- `ClassifierTest` — verify identifyVectors produces correct partitions for known inputs
- `GLAEngineTest` — verify GLA convergence and distortion minimization
- `SplitGLATest` — verify Split-GLA finds optimal k values
- `MixtureClassifierTest` — verify EM algorithm converges to correct mixture parameters
- `CumulativeClassifierTest` — verify cumulative classification with dynamic partitions
- `TreeBuilderTest` — verify dendrogram construction from partitions

**Performance tests:**
- Benchmark GLA convergence speed on large datasets (1000+ vectors)
- Verify memory usage stays bounded for bootstrap trials

---

## References

- C parser source: `original/parser.c` (full switch definitions)
- C variables header: `original/vars.h` (all global flags and their meanings)
- Algorithm implementations: `binclass-algorithms/src/main/java/org/binclass/algorithms/`
- Java CLI commands: `binclass-cli/src/main/java/org/binclass/cli/`
