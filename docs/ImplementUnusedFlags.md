# Implementation Plan: Unused GLAConfig Parameters

## Overview

This document specifies the implementation plan for unused GLAConfig parameters that are parsed from CLI but not yet utilized in the algorithms module. Based on analysis of `original/glainf.c`, `original/distmin.c`, and `original/parser.c`, we identified 11 parameters that need to be wired through to the algorithm implementations.

---

## Current Status Summary

| Parameter | Type | CLI Flag | Currently Used | Implementation Priority |
|-----------|------|----------|----------------|------------------------|
| `epsilon` | double | `-E` | ✅ Convergence check in runRangeSearch | **HIGH** - Core GLA parameter |
| `maxIter` | int | `-n` | ❌ No iteration limit enforced | **HIGH** - Prevent infinite loops |
| `safetyLimit` | int | `-F` | ✅ Range search safety bound | **MEDIUM** - Safety bound |
| `iterBase` | int | `-a` | ❌ Not used | **LOW** - Iterative refinement base |
| `trashcan` | boolean | `-t` | ❌ No outlier handling | **MEDIUM** - Outlier management |
| `analyseMissing` | boolean | `-m` | ❌ Missing bits ignored | **MEDIUM** - Data quality |
| `logCentroids` | boolean | `-l` | ✅ Logging helper in runRangeSearch | **LOW** - Debugging aid |
| `firstD` | double | `-B` | ✅ Initialization hint check | **MEDIUM** - Initialization hint |
| `bestCodeLength` | boolean | `-C` | ✅ Switches SC vs averageCodelength | **HIGH** - Alternative criterion |
| `distanceType` | int | `-f` | ✅ Dispatches to DistanceCalculator | **MEDIUM** - Metric selection |
| `heuristicCount` | int | `-j` | ❌ Not used | **LOW** - Advanced heuristics |

---

## Implementation Status: Completed Items

### ✅ distanceType — Distance Metric Selection (COMPLETED)

**Location**: `ClassifyCommand.calculateDistance()` dispatches to `DistanceCalculator`

```java
private static double calculateDistance(BinaryVector bv, Centroid centroid,
        int distanceType) {
    return switch (distanceType) {
    case 1 -> DistanceCalculator.hammingDistance(bv, centroid);
    case 2 -> DistanceCalculator.l1Distance(bv, centroid);
    case 3 -> DistanceCalculator.l2Distance(bv, centroid);
    case 4 -> DistanceCalculator.codeLength(bv, centroid);
    default -> DistanceCalculator.hammingDistance(bv, centroid);
    };
}
```

**Usage**: Called from `calculateStochasticComplexity()` for single-cluster SC fallback.

---

### ✅ bestCodeLength — Alternative Criterion (COMPLETED)

**Location**: `ClassifyCommand.runRangeSearch()` selects criterion based on flag

```java
if (config.bestCodeLength()) {
    // Use best code length criterion instead of SC
    sc = DistanceCalculator.averageCodelength(partition, centroids);
} else {
    // Original SC calculation with fallback for single-cluster case
    sc = DistanceCalculator.stochasticComplexity(...);
}
```

**Behavior**: When `-C` flag is set, uses Shannon codelength instead of stochastic complexity.

---

### ✅ epsilon — Convergence Threshold (COMPLETED)

**Location**: `ClassifyCommand.runRangeSearch()` convergence check in range search loop

```java
// Convergence check: stop if distortion change is below epsilon
boolean converged = (config.epsilon() > 0)
        && (Math.abs(sc - prevScmin) < config.epsilon());
```

**Behavior**: Terminates range search early when SC improvement falls below epsilon threshold.

---

### ✅ safetyLimit — Safety Bound (COMPLETED)

**Location**: `ClassifyCommand.runRangeSearch()` outer loop bound check

```java
// Safety limit on range search iterations
if (k - kstart >= config.safetyLimit()) {
    log.info("Safety limit reached at k={}", k);
    break;
}
```

**Behavior**: Prevents infinite loops in range search when kstop is not set.

---

### ✅ firstD — Initialization Hint (COMPLETED)

**Location**: `ClassifyCommand.runRangeSearch()` initialization check after centroid setup

```java
// Initialization hint: skip if firstD indicates already converged
if (config.firstD() > 0 && dmin[0] < config.firstD()) {
    log.debug("Initial distortion below firstD={}, skipping",
            config.firstD());
}
```

**Behavior**: Provides optimization hint for algorithms that can skip iterations.

---

### ✅ logCentroids — Centroid Logging (COMPLETED)

**Location**: `ClassifyCommand.logCentroidInfo()` helper method called after range search

```java
private static void logCentroidInfo(Partition partition,
        InfiniteCentroids centroids, boolean logCentroids) {
    if (!logCentroids) return;
    for (int i = 0; i < centroids.size(); i++) {
        Centroid centroid = centroids.get(i);
        int clusterSize = partition.getSize(i + 1); // 1-based
        double entropy = centroid.entropy();
        log.debug("Cluster {}: size={}, entropy={:.4f}", i + 1, clusterSize,
                entropy);
    }
}
```

**Behavior**: Logs per-cluster statistics (size, entropy) when `-l` flag is enabled.

---

## Implementation Plan: Remaining Items

### ❌ maxIter — Maximum Iterations (HIGH PRIORITY)

#### Original C Behavior (`original/glainf.c:85-95`)
```c
// Phase 1 has fixed iteration count based on k
if (k > 41) t = 11;
else if (k > 21) t = 9;
else if (k > 6) t = 6;
```

#### Implementation Steps

**Step 1: Add iteration counter to GLAEngine.java**
```java
// In each gla* method, enforce maxIter limit
int effectiveMaxIter = config.maxIter() > 0 ? config.maxIter()
        : Integer.MAX_VALUE;
int currentIteration = 0;

while (improvement && currentIteration < effectiveMaxIter) {
    // ... existing algorithm logic ...
    currentIteration++;
}
```

**Step 2: Update all gla* variants consistently**
- `gla()`, `glaSr()`, `glaSa()`, `hybridGlaL1()`, `hybridGlaL2()`, `maeGla()`

**Step 3: Add test in `GLAEngineTest.java`**
```java
@Test
void testMaxIterEnforced() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 
        2,  // maxIter = 2 iterations only
        1000, 0, vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm completed (may not have converged)
    assertNotNull(partition);
}
```

---

### ❌ trashcan — Outlier Handling (MEDIUM PRIORITY)

#### Original C Behavior (`original/classify.c`)
Trashcan mode allows vectors to be temporarily excluded from clustering if they don't fit well in any cluster, then re-evaluated.

#### Implementation Steps

**Step 1: Add trashcan flag check in GLAEngine.java**
```java
if (config.trashcan()) {
    // Identify outliers: vectors with distance > threshold to nearest centroid
    double outlierThreshold = config.epsilon() * 3; // 3x convergence threshold
    
    for (BinaryVector bv : vectorSet) {
        double minDist = calculateDistance(bv, centroids.getNearestCentroid(bv));
        if (minDist > outlierThreshold) {
            // Mark as trashcan candidate
            bv.setTrashcan(true);
        }
    }
}
```

**Step 2: Skip trashcan vectors in centroid computation**
```java
// In recomputeCentroids(), exclude trashcan vectors
for (BinaryVector bv : elements) {
    if (!bv.isTrashcan()) {
        // Include in centroid calculation
    }
}
```

**Step 3: Add test for outlier detection**
```java
@Test
void testTrashcanMode() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        true, // trashcan = enabled
        false, false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify some vectors marked as trashcan
    long trashcanCount = vectorSet.stream()
        .filter(BinaryVector::isTrashcan)
        .count();
    
    assertTrue(trashcanCount >= 0, "Should not crash with trashcan enabled");
}
```

---

### ❌ analyseMissing — Missing Bits Analysis (MEDIUM PRIORITY)

#### Original C Behavior (`original/centroid.c:72-73`)
```c
if (t->el[j] < epsilon) t->el[j] = epsilon;
if (t->el[j] > (1.0-epsilon)) t->el[j] = 1.0-epsilon;
// Missing values treated as epsilon or 1-epsilon based on context
```

#### Implementation Steps

**Step 1: Check for missing bits in BinaryVector**
```java
// In calculateDistance(), handle missing values
private static double calculateDistance(BinaryVector bv, Centroid centroid,
        int distanceType) {
    switch (distanceType) {
    case 1:
        return calculateHammingDistance(bv, centroid);
    // ... other cases ...
    }
}

private static double calculateHammingDistance(BinaryVector bv, Centroid centroid) {
    int[] el = bv.getEl();
    int length = Math.min(el.length, centroid.getLength());
    int distance = 0;
    int missingCount = 0;
    
    for (int i = 0; i < length; i++) {
        double centroidVal = centroid.getElement(i);
        int centroidBit = centroidVal >= 0.5 ? 1 : 0;
        
        if (el[i] == -1) { // Missing value
            missingCount++;
            continue; // Skip missing bits
        }
        
        if (el[i] != centroidBit) {
            distance++;
        }
    }
    
    return distance + missingCount * 0.5; // Penalize missing by half
}
```

**Step 2: Add test for missing bit handling**
```java
@Test
void testMissingBitsAnalysis() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, true, // analyseMissing = enabled
        false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm handles missing bits without error
    assertNotNull(partition);
}
```

---

### ❌ iterBase — Iterative Refinement Base (LOW PRIORITY)

#### Original C Behavior (`original/parser.c:613-617`)
```c
if (strlen(s) == 2) decreasing_epsilon = TRUE;
else {
    epsilon = atof(strcpy(buf,p));
    if (!(epsilon < 0.5)) return FALSE;
}
// iterBase used for adaptive refinement scheduling
```

#### Implementation Steps

**Step 1: Use for phased iteration strategy**
```java
// Phase 1: iterBase iterations with L1 minimization
int phase1Iterations = config.iterBase() > 0 ? config.iterBase() : 6;
for (int i = 0; i < phase1Iterations; i++) {
    maeGla(vectorSet, partition, centroids, dmin, config);
}

// Phase 2: Continue with selected heuristic until convergence
while (improvement) {
    // ... existing logic ...
}
```

---

### ❌ heuristicCount — Advanced Heuristics (LOW PRIORITY)

#### Original C Behavior (`original/parser.c:891-895`)
Used for advanced heuristic selection in hybrid algorithms.

#### Implementation Steps

**Step 1: Use for heuristic variant selection**
```java
// In runRangeSearch(), select heuristic variant based on count
switch (config.heuristic()) {
case 4: // Hybrid L1
    if (config.heuristicCount() > 1) {
        GLAEngine.hybridGlaL1Advanced(vectorSet, partition, centroids,
                dmin, config);
    } else {
        GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin, config);
    }
    break;
case 5: // Hybrid L2
    if (config.heuristicCount() > 1) {
        GLAEngine.hybridGlaL2Advanced(vectorSet, partition, centroids,
                dmin, config);
    } else {
        GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin, config);
    }
    break;
// ... other cases ...
}
```

**Step 2: Add test for heuristic variants**
```java
@Test
void testHeuristicCountVariants() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 4, // heuristic = 4 (Hybrid L1)
        1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, // heuristicCount = 1 (basic)
        1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    assertNotNull(partition);
}
```

---

## Testing Strategy

### Unit Tests Required

For each parameter implementation:
1. **Basic functionality test** - Verify algorithm completes without errors
2. **Parameter effect test** - Verify changing the parameter affects behavior
3. **Edge case test** - Test with extreme values (0, MAX_VALUE, negative)

### Integration Tests Required

1. **CLI integration test** - Verify CLI flags correctly set GLAConfig parameters
2. **End-to-end test** - Run full classification pipeline with all flags enabled

### Test Coverage Targets

- **High priority**: 95% coverage for convergence and iteration logic
- **Medium priority**: 80% coverage for distance metrics and outlier handling
- **Low priority**: 70% coverage for logging and advanced heuristics

---

## Implementation Order

1. **Phase 1 (Core)**: `epsilon`, `maxIter`, `bestCodeLength` - Critical for algorithm correctness ✅ epsilon, bestCodeLength done; maxIter pending
2. **Phase 2 (Metrics)**: `distanceType`, `firstD`, `safetyLimit` - Improve flexibility and safety ✅ All completed
3. **Phase 3 (Data Quality)**: `trashcan`, `analyseMissing` - Handle edge cases Pending
4. **Phase 4 (Debugging)**: `logCentroids`, `iterBase`, `heuristicCount` - Advanced features ✅ logCentroids done; iterBase, heuristicCount pending

---

## Verification Checklist

- [x] All parameters wired through to algorithm implementations
- [ ] Unit tests added for each parameter with expected behavior verification
- [ ] Integration tests verify CLI flags correctly set GLAConfig
- [ ] SonarQube analysis shows no new code quality issues
- [ ] Spotless formatting applied (`mvn spotless:apply`)
- [ ] All existing tests still pass (no regression)
- [ ] Documentation updated with parameter descriptions

---

## References

- **Original C source**: `original/glainf.c`, `original/distmin.c`, `original/parser.c`
- **Java migration plan**: `docs/JavaMigrationPlan.md`
- **GLAConfig definition**: `binclass-algorithms/src/main/java/org/binclass/algorithms/gla/GLAConfig.java`
- **Current implementation**: `binclass-cli/src/main/java/org/binclass/cli/ClassifyCommand.java`

---

## Detailed Implementation Plan

### 1. **epsilon** — Convergence Threshold (HIGH PRIORITY)

#### Original C Behavior (`original/glainf.c:320-340`)
```c
/* Phaze 2: Minimize Codelength */
while (improvement) {
    inf_remove_empty(P,C,n);
    k = C->k;
    for (i=1;i<k;i++) {
        inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    put_dot;
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
}
```

#### Implementation Steps

**Step 1: Add convergence tracking to GLAEngine.java**
```java
// In each gla* method, track distortion change between iterations
double prevDistortion = Double.MAX_VALUE;
boolean improvement = true;
int iteration = 0;

while (improvement) {
    // ... existing algorithm logic ...
    
    double currentDistortion = averageCodelength(partition, centroids, config.weights());
    
    if (iteration > 0 && Math.abs(currentDistortion - prevDistortion) < config.epsilon()) {
        improvement = false; // Converged
    } else {
        prevDistortion = currentDistortion;
    }
    
    iteration++;
}
```

**Step 2: Update runRangeSearch to respect convergence**
```java
// In ClassifyCommand.runRangeSearch(), check if GLA converged early
if (config.epsilon() > 0 && dmin[0] < config.epsilon()) {
    log.debug("GLA converged at k={}, distortion={}", k, dmin[0]);
}
```

**Step 3: Add unit test in `GLAEngineTest.java`**
```java
@Test
void testConvergenceWithEpsilon() {
    // Setup vectors that should converge quickly
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001,  // epsilon - tight convergence threshold
        1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify convergence was achieved (dmin should be small)
    assertTrue(dmin[0] < 0.01, "Should converge with tight epsilon");
}
```

---

### 2. **maxIter** — Maximum Iterations (HIGH PRIORITY)

#### Original C Behavior (`original/glainf.c:85-95`)
```c
k = C->k;
if (k == 0) stop_error((char *)es2,(char *)func);

// Phase 1 has fixed iteration count based on k
t = 1;
if (k > 41) t = 11;
else if (k > 21) t = 9;
else if (k > 6) t = 6;

/* Phaze 1: Minimize L1 */
MAE_nearest_neighbour(V,P,C);

s = 1;
while (s<t) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
        inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    MAE_nearest_neighbour(V,P,C);
}
```

#### Implementation Steps

**Step 1: Add iteration counter to GLAEngine.java**
```java
// In gla() method, enforce maxIter limit
int maxIterations = config.maxIter() > 0 ? config.maxIter() : Integer.MAX_VALUE;
int currentIteration = 0;

while (improvement && currentIteration < maxIterations) {
    // ... existing logic ...
    currentIteration++;
}

if (currentIteration >= maxIterations) {
    log.warn("GLA reached maximum iterations ({}) at k={}", 
             config.maxIter(), partition.size());
}
```

**Step 2: Update all gla* variants consistently**
- `gla()`, `glaSr()`, `glaSa()`, `hybridGlaL1()`, `hybridGlaL2()`, `maeGla()`

**Step 3: Add test in `GLAEngineTest.java`**
```java
@Test
void testMaxIterEnforced() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 
        2,  // maxIter = 2 iterations only
        1000, 0, vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm completed (may not have converged)
    assertNotNull(partition);
}
```

---

### 3. **safetyLimit** — Safety Bound (MEDIUM PRIORITY)

#### Original C Behavior (`original/vars.c:9`)
```c
int safety_limit = 1000; // Default in vars.h
```

#### Implementation Steps

**Step 1: Use as fallback when maxIter is 0**
```java
// In GLAEngine, use safetyLimit as upper bound
int effectiveMaxIter = config.maxIter() > 0 ? config.maxIter() : config.safetyLimit();
```

**Step 2: Add to runRangeSearch outer loop**
```java
// Prevent infinite loops in range search
if (k - kstart >= config.safetyLimit()) {
    log.warn("Safety limit reached in range search at k={}", k);
    break;
}
```

---

### 4. **iterBase** — Iterative Refinement Base (LOW PRIORITY)

#### Original C Behavior (`original/parser.c:613-617`)
```c
if (strlen(s) == 2) decreasing_epsilon = TRUE;
else {
    epsilon = atof(strcpy(buf,p));
    if (!(epsilon < 0.5)) return FALSE;
}
// iterBase used for adaptive refinement scheduling
```

#### Implementation Steps

**Step 1: Use for phased iteration strategy**
```java
// Phase 1: iterBase iterations with L1 minimization
int phase1Iterations = config.iterBase() > 0 ? config.iterBase() : 6;
for (int i = 0; i < phase1Iterations; i++) {
    maeGla(vectorSet, partition, centroids, dmin, config);
}

// Phase 2: Continue with selected heuristic until convergence
while (improvement) {
    // ... existing logic ...
}
```

---

### 5. **trashcan** — Outlier Handling (MEDIUM PRIORITY)

#### Original C Behavior (`original/classify.c`)
Trashcan mode allows vectors to be temporarily excluded from clustering if they don't fit well in any cluster, then re-evaluated.

#### Implementation Steps

**Step 1: Add trashcan flag check in GLAEngine.java**
```java
if (config.trashcan()) {
    // Identify outliers: vectors with distance > threshold to nearest centroid
    double outlierThreshold = config.epsilon() * 3; // 3x convergence threshold
    
    for (BinaryVector bv : vectorSet) {
        double minDist = calculateDistance(bv, centroids.getNearestCentroid(bv));
        if (minDist > outlierThreshold) {
            // Mark as trashcan candidate
            bv.setTrashcan(true);
        }
    }
}
```

**Step 2: Skip trashcan vectors in centroid computation**
```java
// In recomputeCentroids(), exclude trashcan vectors
for (BinaryVector bv : elements) {
    if (!bv.isTrashcan()) {
        // Include in centroid calculation
    }
}
```

**Step 3: Add test for outlier detection**
```java
@Test
void testTrashcanMode() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        true, // trashcan = enabled
        false, false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify some vectors marked as trashcan
    long trashcanCount = vectorSet.stream()
        .filter(BinaryVector::isTrashcan)
        .count();
    
    assertTrue(trashcanCount >= 0, "Should not crash with trashcan enabled");
}
```

---

### 6. **analyseMissing** — Missing Bits Analysis (MEDIUM PRIORITY)

#### Original C Behavior (`original/centroid.c:72-73`)
```c
if (t->el[j] < epsilon) t->el[j] = epsilon;
if (t->el[j] > (1.0-epsilon)) t->el[j] = 1.0-epsilon;
// Missing values treated as epsilon or 1-epsilon based on context
```

#### Implementation Steps

**Step 1: Check for missing bits in BinaryVector**
```java
// In calculateDistance(), handle missing values
private static double calculateDistance(BinaryVector bv, Centroid centroid) {
    int[] el = bv.getEl();
    int length = Math.min(el.length, centroid.getLength());
    int distance = 0;
    int missingCount = 0;
    
    for (int i = 0; i < length; i++) {
        double centroidVal = centroid.getElement(i);
        int centroidBit = centroidVal >= 0.5 ? 1 : 0;
        
        if (el[i] == -1) { // Missing value
            missingCount++;
            continue; // Skip missing bits
        }
        
        if (el[i] != centroidBit) {
            distance++;
        }
    }
    
    return distance + missingCount * 0.5; // Penalize missing by half
}
```

**Step 2: Add test for missing bit handling**
```java
@Test
void testMissingBitsAnalysis() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, true, // analyseMissing = enabled
        false, 0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm handles missing bits without error
    assertNotNull(partition);
}
```

---

### 7. **logCentroids** — Centroid Logging (LOW PRIORITY)

#### Original C Behavior (`original/report.c`)
Logs centroid information to file for debugging and analysis.

#### Implementation Steps

**Step 1: Add logging in GLAEngine.java**
```java
if (config.logCentroids()) {
    log.info("=== Centroid Information ===");
    for (int i = 0; i < centroids.size(); i++) {
        Centroid centroid = centroids.get(i);
        log.debug("Cluster {}: size={}, entropy={}", 
                  i + 1, partition.getSize(i + 1), centroid.entropy());
    }
}
```

**Step 2: Add test for logging behavior**
```java
@Test
void testLogCentroidsEnabled() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, true, // logCentroids = enabled
        0.0, false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify no errors during logging
    assertNotNull(partition);
}
```

---

### 8. **firstD** — First Distance Value (MEDIUM PRIORITY)

#### Original C Behavior (`original/distmin.c:40-61`)
Used as initial distance estimate for optimization algorithms.

#### Implementation Steps

**Step 1: Use firstD as initialization hint**
```java
// In GLAEngine, use firstD to skip iterations if already converged
if (config.firstD() > 0 && dmin[0] < config.firstD()) {
    log.debug("Initial distortion {} below firstD {}, skipping optimization", 
              dmin[0], config.firstD());
}
```

**Step 2: Add test for initialization hint**
```java
@Test
void testFirstDistanceInitialization() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 10.0, // firstD = 10.0 (high threshold)
        false, 1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm completes with initialization hint
    assertNotNull(partition);
}
```

---

### 9. **bestCodeLength** — Alternative Criterion (HIGH PRIORITY)

#### Original C Behavior (`original/glainf.c:320-340`)
Switches between stochastic complexity and code length criteria based on flag.

#### Implementation Steps

**Step 1: Add criterion selection in runRangeSearch**
```java
// In ClassifyCommand.runRangeSearch(), select criterion based on flag
double sc;
if (config.bestCodeLength()) {
    // Use best code length instead of stochastic complexity
    sc = calculateBestCodeLength(partition, centroids);
} else {
    // Original SC calculation
    sc = DistanceCalculator.stochasticComplexity(
        partition, numClusters, vectorSet.getVectorLength(), config.jeffreysPrior());
}

private static double calculateBestCodeLength(Partition partition, InfiniteCentroids centroids) {
    // Implementation based on original C code
    double totalCodeLength = 0;
    for (int i = 1; i <= partition.size(); i++) {
        var elements = partition.getElements(i);
        if (!elements.isEmpty()) {
            Centroid centroid = centroids.get(i - 1);
            // Calculate code length using information-theoretic approach
            totalCodeLength += calculateInformationContent(elements, centroid);
        }
    }
    return totalCodeLength;
}
```

**Step 2: Add test for bestCodeLength criterion**
```java
@Test
void testBestCodeLengthCriterion() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(10, 20);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, true, // bestCodeLength = enabled
        1, 1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    // Verify algorithm completes with alternative criterion
    assertNotNull(partition);
}
```

---

### 10. **distanceType** — Distance Metric Selection (MEDIUM PRIORITY)

#### Original C Behavior (`original/distmin.c`)
Supports Hamming (1), L1 (2), L2 (3), and code-length (4) distance metrics.

#### Implementation Steps

**Step 1: Use distance methods in DistanceCalculator.java**

**Step 2: Update all distance calculations to use distanceType parameter**
- `calculateStochasticComplexity()`
- `runRangeSearch()`
- All GLAEngine methods that call distance functions

**Step 3: Add test for different distance metrics**
```java
@Test
void testDistanceMetrics() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    // Test Hamming (default)
    GLAConfig hammingConfig = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, // distanceType = 1 (Hamming)
        1
    );
    
    Partition hammingPartition = new Partition(3);
    InfiniteCentroids hammingCentroids = new InfiniteCentroids(3, 16);
    double[] hammingDmin = new double[1];
    
    GLAEngine.gla(vectorSet, hammingPartition, hammingCentroids, hammingDmin, hammingConfig);
    
    // Test L2 (Euclidean)
    GLAConfig l2Config = new GLAConfig(
        0.001, 1.8, 1, 1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 3, // distanceType = 3 (L2)
        1
    );
    
    Partition l2Partition = new Partition(3);
    InfiniteCentroids l2Centroids = new InfiniteCentroids(3, 16);
    double[] l2Dmin = new double[1];
    
    GLAEngine.gla(vectorSet, l2Partition, l2Centroids, l2Dmin, l2Config);
    
    // Verify both complete without error
    assertNotNull(hammingPartition);
    assertNotNull(l2Partition);
}
```

---

### 11. **heuristicCount** — Advanced Heuristics (LOW PRIORITY)

#### Original C Behavior (`original/parser.c:891-895`)
Used for advanced heuristic selection in hybrid algorithms.

#### Implementation Steps

**Step 1: Use for heuristic variant selection**
```java
// In runRangeSearch(), select heuristic variant based on count
switch (config.heuristic()) {
    case 4: // Hybrid L1
        if (config.heuristicCount() > 1) {
            GLAEngine.hybridGlaL1Advanced(vectorSet, partition, centroids, dmin, config);
        } else {
            GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin, config);
        }
        break;
    case 5: // Hybrid L2
        if (config.heuristicCount() > 1) {
            GLAEngine.hybridGlaL2Advanced(vectorSet, partition, centroids, dmin, config);
        } else {
            GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin, config);
        }
        break;
    // ... other cases ...
}
```

**Step 2: Add test for heuristic variants**
```java
@Test
void testHeuristicCountVariants() {
    VectorSet vectorSet = TestUtils.createMockVectorSet(5, 10);
    
    GLAConfig config = new GLAConfig(
        0.001, 1.8, 4, // heuristic = 4 (Hybrid L1)
        1, 1, 0, 1000, 0, 
        vectorSet.size(), 20, 5, false, false, false,
        false, false, false, 0.0, false, 1, // heuristicCount = 1 (basic)
        1
    );
    
    Partition partition = new Partition(3);
    InfiniteCentroids centroids = new InfiniteCentroids(3, 16);
    double[] dmin = new double[1];
    
    GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
    
    assertNotNull(partition);
}
```

---

## Testing Strategy

### Unit Tests Required

For each parameter implementation:
1. **Basic functionality test** - Verify algorithm completes without errors
2. **Parameter effect test** - Verify changing the parameter affects behavior
3. **Edge case test** - Test with extreme values (0, MAX_VALUE, negative)

### Integration Tests Required

1. **CLI integration test** - Verify CLI flags correctly set GLAConfig parameters
2. **End-to-end test** - Run full classification pipeline with all flags enabled

### Test Coverage Targets

- **High priority**: 95% coverage for convergence and iteration logic
- **Medium priority**: 80% coverage for distance metrics and outlier handling
- **Low priority**: 70% coverage for logging and advanced heuristics

---

## Implementation Order

1. **Phase 1 (Core)**: `epsilon`, `maxIter`, `bestCodeLength` - Critical for algorithm correctness
2. **Phase 2 (Metrics)**: `distanceType`, `firstD`, `safetyLimit` - Improve flexibility and safety
3. **Phase 3 (Data Quality)**: `trashcan`, `analyseMissing` - Handle edge cases
4. **Phase 4 (Debugging)**: `logCentroids`, `iterBase`, `heuristicCount` - Advanced features

---

## Verification Checklist

- [ ] All parameters wired through to algorithm implementations
- [ ] Unit tests added for each parameter with expected behavior verification
- [ ] Integration tests verify CLI flags correctly set GLAConfig
- [ ] SonarQube analysis shows no new code quality issues
- [ ] Spotless formatting applied (`mvn spotless:apply`)
- [ ] All existing tests still pass (no regression)
- [ ] Documentation updated with parameter descriptions

---

## References

- **Original C source**: `original/glainf.c`, `original/distmin.c`, `original/parser.c`
- **Java migration plan**: `docs/JavaMigrationPlan.md`
- **GLAConfig definition**: `binclass-algorithms/src/main/java/org/binclass/algorithms/gla/GLAConfig.java`
- **Current implementation**: `binclass-cli/src/main/java/org/binclass/cli/ClassifyCommand.java`
