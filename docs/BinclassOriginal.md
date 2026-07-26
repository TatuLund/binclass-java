# BinClass Original — Software Documentation

## Overview

**BinClass** is a C-based software tool for binary classification and clustering of binary data. It implements algorithms for partitioning binary vectors into clusters (classes), with support for multiple distance metrics, centroid computation, bootstrap resampling, mixture models, and hierarchical tree construction. The program was originally developed in Pascal by Martin Verlaan and ported to a portable C version by Tatu J. Lund.

The software is designed for bioinformatics applications — particularly protein sequence classification based on binary feature vectors (e.g., presence/absence of amino acid patterns). It supports:

- **Binary vector representation** with efficient bit-level operations
- **Multiple distance metrics**: Hamming, L1 (Manhattan), L2 (Euclidean), code-length
- **Centroid computation** using information-theoretic approaches
- **Generalized Lloyd Algorithm (GLA)** for iterative refinement of clusters
- **Bootstrap resampling** for cluster stability assessment
- **Mixture classification** via Expectation-Maximization (EM) algorithm
- **Hierarchical tree construction** from partitions
- **Cumulative classification analysis**

---

## Project Structure

```
original/
├── const.h          — Core type definitions and constants
├── dep.h            — Platform/compiler-dependent defines
├── vars.h / vars.c  — Global variables and configuration parameters
├── bottom.h         — Low-level macros (logarithms, gamma functions)
├── binset.h / binset.c — Binary set operations (ST, Partition, DynPartition)
├── binstuff.h / binstuff.c — I/O utilities for binary vectors
├── vectors.h / vectors.c — Vector and matrix operations
├── distmin.h / distmin.c — Distance metrics and nearest-neighbor algorithms
├── centroid.h / centroid.c — Centroid computation and management
├── classify.h / classify.c — Main classification routines
├── parser.h / parser.c — Command-line argument parsing
├── report.h / report.c — Report generation from classification output
├── adding.h / adding.c — Vector identification by closest match
├── bootstra.h / bootstra.c — Bootstrap resampling and GLA initialization
├── glainf.h / glainf.c — Generalized Lloyd Algorithm implementations
├── splitgla.h / splitgla.c — Split-GLA hybrid algorithm for k search
├── joingla.h / joingla.c — Join-GLA algorithm
├── mixture.h / mixture.c — Mixture classification with EM algorithm
├── cumulat.h / cumulat.c — Cumulative classification analysis
├── cut.h / cut.c — Partition cutting/trimming utilities
├── compare.h / compare.c — Partition comparison routines
├── format.h / format.c — File format interpretation and I/O
├── tree.h / tree.c — Hierarchical tree construction from partitions
├── function.h / function.c — Information-theoretic function calculations (SC, Shannon entropy)
├── gendat.h / gendat.c — Data generation utilities (Bernoulli, Markov, random)
├── logfile.h / logfile.c — Logging and profiling utilities
├── t_alg_1.h / t_alg_1.c — Test algorithm 1: distortion minimizer
├── t_alg_2.h / t_alg_2.c — Test algorithm 2: semi-cumulative module
├── test.c           — Simple test program
└── makefile.gcc     — Build configuration for GCC
```

---

## Core Data Structures (from `const.h`)

### Binary Vector (`BV`)

Represents a single binary vector with metadata:

```c
typedef struct {
    int *el;          // Element array (1-indexed, 0 or 1 values)
    int *miss;        // Missing value indicators
    int length;       // Length of the vector
    char *clasname;   // Class name label
    char *strain;     // Strain/organism name
    double dist;      // Distance to nearest centroid
    int hdist;        // Hamming distance to nearest centroid
    int num;          // Vector number/index
} BV;
```

### Set of Vectors (`ST`)

A linked-list based set (bag) of binary vectors:

```c
typedef struct {
    BV *el;           // Pointer to first element
    void *next;       // Next node in the set
    void *last;       // Last element pointer
} ST;
```

### Partition (`Partition`)

A collection of sets indexed by cluster number:

```c
typedef struct {
    ST **el;          // Array of sets (one per class)
    int k;            // Number of classes
} Partition;
```

### Dynamic Partition (`DynPartition`)

An extended partition with frequency tracking for information-theoretic computations:

```c
typedef struct {
    ST *el;           // Set of vectors
    IntVector *freq;  // Frequency vector
    IntVector *hmo;   // Hamming mask (for distance computation)
    IntVector *nij;   // Co-occurrence counts
    int size;         // Number of elements
    void *next;       // Next node
} DynPartition;
```

### Centroid (`Centroid`)

A centroid representing a cluster, with probability distributions for each bit position:

```c
typedef struct {
    double *el;           // Probability of 1 at each position (length l)
    double *log0;         // Log-probability of 0 at each position
    double *log1;         // Log-probability of 1 at each position
    int l;                // Length of the centroid
    double weight;        // Weight of this centroid in a mixture
} Centroid;
```

### Infinite Centroids (`InfCentroid`)

A collection of centroids with information-theoretic summaries:

```c
typedef struct {
    Centroid **el;      // Array of centroids (indexed 1..k)
    int k;              // Number of centroids
    double SC;          // Simplified Complexity (uniform prior)
    double I;           // Information content (Jeffreys prior)
    double I2;          // Alternative information content
} InfCentroid;
```

### Supporting Types

| Type | Description |
|------|-------------|
| `Vector` / `IntVector` | Dynamic arrays of `double` / `int` |
| `Matrix` / `IntMatrix` | 2D dynamic arrays (jagged) |
| `Automata` | Hidden Markov Model state representation |
| `TreeNode` | Node in a hierarchical tree |
| `Frequencies` | Frequency table with linkage information |

### Enumerations

```c
typedef enum {
    HEUR_REPLACESMALLEST=1, HEUR_SPLITJOIN1, HEUR_SPLITJOIN2,
    HEUR_REPLACEWORST, HEUR_RANDOMSWAP, HEUR_RANDOMSWAP2, HEUR_NONE
} eHeuristic;

typedef enum {
    DT_HAM=1, DT_L1, DT_L2, DT_CL, DT_L1_CL, DT_L2_CL, DT_SR, DT_SA
} eDist;  // Distance types: Hamming, L1, L2, Code-length variants

typedef enum {
    DG_RAND=1, DG_BERNOULLI, DG_MARKOV, DG_RVECTOR
} eDataGen;  // Data generation methods

typedef enum {
    ST_AUTO=1, ST_NAUTO, ST_LCENT, ST_ADAP
} eSearch;  // Search strategies for k determination

typedef enum {
    CT_CLASSIC=1, CT_SRAND, CT_SEMI, CT_RAND, CT_PNN
} eCentroidType;  // Centroid initialization types
```

---

## Global Configuration (from `vars.h` / `vars.c`)

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `epsilon` | double | 0.001 | Small number for numerical stability |
| `first_d` | double | 1000.0 | Initial distance threshold |
| `treshold` | double | — | Convergence threshold |
| `vec_len` | int | — | Length of binary vectors |
| `vec_offs` | int | — | Vector offset in input file |
| `id_offs` / `id_len` | int | — | Identifier field position and length |
| `name_len` | int | — | Name field length |
| `kstart` / `kstop` | int | 1, 0 | Range of cluster counts to test |
| `max_iter` | int | 20 | Maximum GLA iterations |
| `safety_limit` | int | 500 | Safety limit for iteration count |
| `vecs_to_gen` | int | 500 | Number of vectors to generate |
| `bootstrap_size` | int | 50 | Bootstrap sample size |
| `bootstrap_k` / `bootstrap_i` | int | 0, 100 | Bootstrap cluster count and iteration |
| `gla_treshold` | double | 1.1 | GLA convergence threshold |
| `real_delta_value` | int | 8000 | Delta value for information criteria |
| `mixture_classes` | int | 0 | Number of mixture classes |

---

## Module Descriptions

### 1. `const.h` — Core Type Definitions and Constants

Defines all fundamental data structures, enumerations, and compile-time constants used throughout the program:

- **Constants**: `MAX_LENGTH=2000`, `MAX_TEMP=16378`, `EPS=0.0000001`, `ILOGOF2=1.44269504`
- **Type definitions** for all data structures (BV, ST, Partition, Centroid, InfCentroid, etc.)
- **Enumeration types** for heuristics, distance metrics, data generation methods, search strategies, and centroid initialization

### 2. `dep.h` — Platform/Compiler Dependencies

Handles platform-specific definitions:

- Custom gamma function support (`USE_CUSTOM_GAMMA`)
- Debug mode flags (`_MY_DEBUG`)
- Special random number generator selection (`_SPECIAL_RANDOM`)
- Test algorithm inclusion flags (`_TEST_ALG1`, `_TEST_ALG2`)
- `RAND_MAX` definition for GCC on Sun systems

### 3. `bottom.h` — Low-Level Macros and Functions

Provides fundamental mathematical utilities:

- **Logarithm base 2**: `log_2(x)`, `log_2e(x)` (with epsilon check), `log2_factorial(x)`
- **Gamma function**: `log2_gamma(x)` using standard or custom implementation
- **Verbose output macros**: `put_dot`, `put_mark` for progress reporting

### 4. `vars.h` / `vars.c` — Global Variables and Configuration

Manages all global state variables:

- Reporting parameters (`RP_TOTALFREQ`, `RP_NEARNESS`, `RP_PARTITION`, etc.)
- File path variables (`filebase`, `dumpfile`, `centroidfile`, `new_parfile`)
- Vector metadata (length, offsets, identifier fields)
- Algorithm parameters (k range, iteration limits, bootstrap settings)

### 5. `binset.h` / `binset.c` — Binary Set Operations

Core data structure operations for binary vector sets:

- **Set manipulation**: `add_element`, `del_element`, `get_element`, `no_elements`, `elements_left`
- **Distance functions**: `hamming_distance`, `L1_distance`, `L2_distance`
- **Code-length computation**: `code_length`, `code_length2`, `class_code_length`, `average_codelength`
- **Centroid operations**: `inf_average`, `inf_average_12` (Bayes Posterior Predictive)
- **Nearest neighbor**: `inf_nearest_neighbour`, `fast_nearest_neighbour`, `MAE_nearest_neighbour`
- **Distortion metrics**: `class_distortion`, `overall_distortion`, `class_MAE`, `overall_MAE`

### 6. `binstuff.h` / `binstuff.c` — Binary Vector I/O Utilities

Handles reading and writing binary vectors in various formats:

- **Picture format** (`pic_write_bv`, `pic_read_bv`): Visual representation with '#' for 1, '.' for 0
- **Empirical format** (`emp_write_bv`): Compact output with class labels
- **Binary vector operations**: `bv_allocate`, `bv_copy`, `bv_deallocate`, `bv_set_name`, `bv_set_id`
- **Distance computation**: `bv_dist` (Hamming distance between BVs)

### 7. `vectors.h` / `vectors.c` — Vector and Matrix Operations

Linear algebra utilities for double and integer vectors/matrices:

- **Allocation/deallocation**: `allocate_ivector`, `allocate_dvector`, `allocate_imatrix`, `allocate_dmatrix`
- **Matrix operations**: transpose, multiply, inverse, pseudo-inverse
- **Sorting**: `indexed_qsort`, `qwik_sort` (quicksort implementation)
- **Vector-matrix multiplication**

### 8. `distmin.h` / `distmin.c` — Distance Metrics and Nearest Neighbor

Implements multiple distance measures and nearest-neighbor algorithms:

- **Distance types**: Hamming, L1, L2, code-length (with L1/L2 initialization)
- **Stochastic relaxation** (`DT_SR`) and **simulated annealing** (`DT_SA`) variants
- **Nearest neighbor variants**: standard, fast, MAE-based
- **Centroid averaging**: information-theoretic approaches for centroid update

### 9. `centroid.h` / `centroid.c` — Centroid Computation

Manages centroid creation, loading, saving, and manipulation:

- **Allocation**: `allocate_centroid`, `allocate_centroids` (InfCentroid)
- **Loading/saving**: `load_centroids`, `save_centroids` (file I/O)
- **Initialization**: `random_centroids`, `pick_centroids`
- **Distance computation**: `edistance_2` (Euclidean distance between centroids)
- **Mixture operations**: `mix_centroids`

### 10. `classify.h` / `classify.c` — Main Classification Routines

Primary classification entry point:

- **`classify_vectors`**: Main function that reads data, classifies vectors against centroids, and writes output
- Handles file I/O for data files, parameter files, centroid files, and header files

### 11. `parser.h` / `parser.c` — Command-Line Argument Parsing

Parses command-line arguments and dispatches to appropriate subcommands:

**Supported commands**:
| Command | Description |
|---------|-------------|
| `identify` | Identify vectors by closest centroid match |
| `classify` | Classify vectors using existing centroids |
| `compare` | Compare two partitions |
| `report` | Generate report from classification output |
| `generate` | Generate synthetic data (Bernoulli, Markov, random) |
| `bootstrap` | Bootstrap resampling for stability assessment |
| `fclassify` | Fast classification using Split-GLA |
| `cumulative` | Cumulative classification analysis |
| `sclassify` | Semi-cumulative classification |
| `tree` | Build hierarchical tree from partition |
| `centroids` | Save/load centroids |
| `sortpart` | Sort/partition operations |
| `mixture` | Mixture model classification (EM algorithm) |
| `cut` | Cut/trim partitions |
| `function` | Render information-theoretic functions vs k |

### 12. `report.h` / `report.c` — Report Generation

Generates statistical reports from classification output:

- **Class nearness**: `class_nearness(V1, V2)` — measures similarity between classes
- **Frequency analysis**: Tracks class frequencies and co-occurrences
- **Report types**: Total frequency, nearness, partition, match, neighbor, frequency, matrix reports

### 13. `adding.h` / `adding.c` — Vector Identification by Closest Match

Identifies vectors by finding their closest centroid match:

- **`identify_vectors_by_classification`**: Main identification routine
  - Finds nearest centroid for each vector using code-length or class-weighted distance
  - Reports Hamming distances and classification results
  - Supports exact matching against reference partitions
  - Outputs ranked list of matches per vector

### 14. `bootstra.h` / `bootstra.c` — Bootstrap Resampling

Implements bootstrap resampling for cluster stability assessment:

- **GLA initialization**: Sets initial centroids using various heuristics (PNN2, random, etc.)
- **Bootstrap trials**: Runs multiple GLA iterations on bootstrap samples
- **Statistical analysis**: Computes and reports trial statistics
- **Centroid loading**: `use_gla_load_centroids` — loads pre-computed centroids for bootstrapping

### 15. `glainf.h` / `glainf.c` — Generalized Lloyd Algorithm (GLA)

Implements multiple variants of the GLA for iterative cluster refinement:

| Algorithm | Description |
|-----------|-------------|
| `gla` | Standard GLA with code-length distance |
| `gla_sr` | GLA with stochastic relaxation |
| `gla_sa` | GLA with simulated annealing |
| `hybrid_gla_l1` | Hybrid GLA initialized with L1 nearest neighbor |
| `hybrid_gla_l2` | Hybrid GLA initialized with L2 nearest neighbor |
| `MAE_gla` | GLA using Mean Absolute Error distance |
| `MSE_gla` | GLA using Mean Squared Error distance |
| `fast_gla` | Fast variant of GLA |
| `special_gla` | Special version used by Bootstrapper |

**Key functions**:
- `use_gla`: Main entry point — runs trials with configurable GLA algorithm and heuristics
- `replace_worst`: Replaces worst-performing centroid in a cluster set

### 16. `splitgla.h` / `splitgla.c` — Split-GLA Hybrid Algorithm

Implements the Split-GLA hybrid for fast determination of optimal k:

- **`split_gla`**: Main algorithm that searches for optimal number of classes
- **Worst matching vectors**: `worst_matching_vectors`, `abs_worst_matching_vectors` — identifies poorly classified vectors
- **Centroid initialization**: `set_first_centroids_pnn2` — initializes from nearest pair of vectors
- **Point-wise classification**: `fclassify_vectors` — fast single-vector classification

### 17. `joingla.h` / `joingla.c` — Join-GLA Algorithm

Implements the Join-GLA algorithm for hierarchical merging:

- **`use_join_gla`**: Main entry point for join-based clustering
- Works in conjunction with Split-GLA to find optimal k through merge-split operations

### 18. `mixture.h` / `mixture.c` — Mixture Classification (EM Algorithm)

Implements mixture classification using the Expectation-Maximization algorithm:

- **`apply_mixture_classifier`**: Main EM classifier
  - Calculates probability of binary vectors under mixture model
  - Iteratively updates parameters (E-step and M-step)
  - Supports weighted class distributions
- **`calculate_matrix`**: Computes responsibility matrix P (n×m) from current parameters
- **Probability functions**: `m_fx` (single distribution), `m_fM` (weighted sum over all distributions)

### 19. `cumulat.h` / `cumulat.c` — Cumulative Classification Analysis

Implements cumulative classification for analyzing cluster stability across k values:

- **`do_cumulative_classification`**: Main cumulative classification routine
- **`analyse_cumulative`**: Analyzes and compares cumulative results
- **`reidentification_analysis`**: Re-identification analysis of clusters
- **Dynamic partition operations**: `dp_initialize`, `dp_read_set`, `dp_redraw`, `dp_extend`, `dp_convert`
- **Predictive fit**: `dp_predictive_fit`, `predictive_fit` — evaluates cluster quality

### 20. `cut.h` / `cut.c` — Partition Cutting/Trimming Utilities

Provides utilities for cutting and trimming partitions:

- **`cut_partitions`**: Main partition cutting function
- **`trim_cluster`**: Trims individual clusters in a partition

### 21. `compare.h` / `compare.c` — Partition Comparison Routines

Compares two partitions to assess similarity:

- **`compare_partitions`**: Compares two partitions and writes results
- **`calculate_distance`**: Computes distance between two partitions over a vector set

### 22. `format.h` / `format.c` — File Format Interpretation

Handles reading and writing of data files in various formats:

- **Header parsing**: `read_header`, `parse_hdr_str` — interprets file format headers
- **Hex conversion**: `conv_hex` — converts hex characters to binary values
- **Line reading**: Custom `read_line` function for flexible input parsing

### 23. `tree.h` / `tree.c` — Hierarchical Tree Construction

Builds hierarchical trees from partitions using nearest neighbor rules:

- **`make_tree_pnn`**: Builds tree using Pairwise Nearest Neighbor (PNN) rule
- **`make_tree_pnn2`**: Alternative PNN implementation
- **Information content**: `inf_content`, `inf_content_joint` — information-theoretic measures
- **Linkage functions**: `link_freqs` — links frequency tables for tree construction
- **Distance metrics**: `hellinger_distance` — Hellinger distance between probability distributions
- **Complexity calculations**: `special_complexity`, `special_complexity_u`, `special_complexity_j` — various complexity measures

### 24. `function.h` / `function.c` — Information-Theoretic Functions

Calculates information-theoretic functions as a function of k (number of classes):

- **Simplified Complexity (SC)**: Uniform prior, Jeffrey's prior variants
- **Shannon entropy**: Entropy calculations for binary distributions
- **Code-length**: Information content in bits
- **Harmonic sums**: `a1`, `a2`, `b1`, `b2` — mathematical functions used in SC calculations

### 25. `gendat.h` / `gendat.c` — Data Generation Utilities

Generates synthetic binary data for testing and validation:

| Generator | Method |
|-----------|--------|
| `bernouli_gen` | Bernoulli distribution from partition parameters |
| `markov_gen` | Markov chain model |
| `vector_gen` | Vector-based generation with two files |
| `random_gen` | Pure random binary vectors |

### 26. `logfile.h` / `logfile.c` — Logging and Profiling Utilities

Provides logging functionality:

- **`log_profile`**: Logs statistical profile of frequency distributions across vector lengths
- **`log_function`**: Logs function values (e.g., SC values) across cluster counts

### 27. `t_alg_1.h` / `t_alg_1.c` — Test Algorithm 1: Distortion Minimizer

Test/development algorithm for distortion minimization:

- **`alg1_init`**: Initializes partition with random vectors
- **`alg1_distance`**: Computes distance from a vector to a set of vectors

### 28. `t_alg_2.h` / `t_alg_2.c` — Test Algorithm 2: Semi-Cumulative Module

Test/development algorithm for semi-cumulative classification:

- **`alg2_distance`**: Hamming distance with mask-based optimization
- **`alg2_distortion`**: Distortion calculation for dynamic partitions

### 29. `test.c` — Simple Test Program

Minimal test program that demonstrates basic C formatting behavior (likely a leftover from development).

---

## Algorithm Summary

### Generalized Lloyd Algorithm (GLA)

The core clustering algorithm iteratively refines centroids:

1. **Initialize**: Set initial centroids (random, PNN2, or loaded from file)
2. **Assign**: Assign each vector to its nearest centroid using chosen distance metric
3. **Update**: Recalculate centroids as information-theoretic averages of assigned vectors
4. **Repeat**: Until convergence (distance change < threshold) or max iterations reached

### Split-GLA Hybrid for k Determination

Finds optimal number of classes:

1. Start with small k, run GLA
2. Find worst-matching vectors in each cluster
3. Split clusters using worst-matching pairs
4. Evaluate information criteria (SC, Shannon entropy) at each k
5. Select k that minimizes the chosen criterion

### Mixture Classification (EM Algorithm)

Classifies vectors using a mixture of Bernoulli distributions:

1. **E-step**: Calculate responsibility matrix P (probability of each vector belonging to each class)
2. **M-step**: Update centroid parameters and class weights using responsibilities
3. **Repeat** until convergence

### Hierarchical Tree Construction

Builds dendrogram from partition data:

1. Compute information content for all pairs of classes
2. Find nearest pair (minimum inf_loss)
3. Merge pairs, update frequencies
4. Repeat until single root node

---

## Distance Metrics

| Metric | Notation | Description |
|--------|----------|-------------|
| Hamming | `DT_HAM` | Number of differing bit positions |
| L1 (Manhattan) | `DT_L1` | Sum of absolute differences |
| L2 (Euclidean) | `DT_L2` | Square root of sum of squared differences |
| Code-length | `DT_CL` | Information-theoretic code length using centroid probabilities |
| L1 + CL | `DT_L1_CL` | Hybrid: L1 initialization, then code-length refinement |
| L2 + CL | `DT_L2_CL` | Hybrid: L2 initialization, then code-length refinement |
| Stochastic Relaxation | `DT_SR` | Probabilistic assignment with temperature parameter |
| Simulated Annealing | `DT_SA` | SA-based optimization of assignments |

---

## File Formats

### Header File Format

The header file defines the binary data format:

```
length=<vector_length>    # Number of bits per vector (stored as length-1)
offset=<byte_offset>      # Byte offset to start of binary data
```

### Data File Format

Binary vectors are stored in a compact binary format, with each bit representing a feature value (0 or 1).

---

## Platform Support

The software has been tested on:

| Platform | Compiler |
|----------|----------|
| Windows NT 3.5x | Borland C++ 4.52 / Cygnus GNU-CC v2.7.2 b17.1 |
| Solaris 2.1 | Native & GNU CC |
| Linux | GNU CC v2.7.0+ |
| Amiga OS 3.1 | SAS/C++ 6.57 |
| SGI IRIX | Native & GNU CC v2.7.0 |
| IBM AIX | Native & GNU CC v2.7.0 |
| Digital UNIX 4.0 | Native & GNU-CC v2.7.2.1 |

---

## Build System

The project uses a `makefile.gcc` for building with GCC. The build produces a single executable from all `.c` source files, linked against standard C libraries and `-lm` (math library).
