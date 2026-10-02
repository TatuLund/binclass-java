# Classification Intersections (`intersect`) — C vs Java Analysis

This document records how the `intersection` command from section **4.3.5** of
*Binclass - Software Package For Classifying Binary Vectors* is implemented in
the original C code, and compares it against the current Java implementation so
the gap can be closed later.

Source of truth for the algorithm:
- Doc §2.4.2 *Maximal Intersection and Consensus Classification*.
- Doc §4.3.5 *Classification intersections* (command `intersection`).
- Original C source in `original/`: `cut.c`, `cut.h`, `parser.c`, `binclass.c`,
  `vars.h`, `const.h`.

---

## 1. Command surface (from doc §4.3.5)

**Command:** `intersection`

| Switch | Description |
|--------|-------------|
| `-q`   | No output (quiet) |
| `-r`   | Perform relative intersection operation |
| `-s`   | Perform minimal intersection operation |
| `-m`   | Perform maximal intersection operation |
| `-aXX` | Analyses data with the maximal intersection operation (use XX classes) |
| `-AXX` | Analyses the stability of the maximal intersection operation (use XX classes) |

**Inputs:** `.partition1`, `.partition2`, `.header`
**Output:** `.partition` — the intersection of partitions 1 and 2.

There are three core versions plus two analysis tools:

1. **Naive / relative** — include vectors that appear in both partitions.
2. **Minimal** (§2.4) — minimal intersection.
3. **Maximal** (§2.4) — maximal intersection.

Two analysis tools operate on the *data set* (not two partitions):

- `-aXX` (`analyse_int`) — iteratively intersect to find the most "stable" part
  of classifications [doc ref 28].
- `-AXX` (`analyse_int_stab`) — test that the intersection operation is
  relatively independent of order. The operation is *not* associative, so this
  can be verified empirically.

---

## 2. C implementation

### 2.1 Registration & parsing

- `const.h`: `MOD_INTERSECT` in `eModuleType`.
- `parser.c`: command string `"intersect"` → `MOD_INTERSECT` → `parse_cut(ac, av)`.
- `main()` in `binclass.c` builds file names from `filebase` + suffixes
  (`.partition1`, `.partition2`, `.header`, …).

`parse_cut()` (`parser.c:451`) sets flags:

| Switch | Flag set | Note |
|--------|----------|------|
| `-r`   | `relative_int = TRUE` | |
| `-s`   | `minimal_int = TRUE` | |
| `-m`   | `maximal_int = TRUE` | |
| `-q`   | `verbose = FALSE` | |
| `-Axx` | `analyse_int_stab = TRUE`, `kstart = xx + 1` | |
| `-axx` | `analyse_int = TRUE`, `kstart = xx + 1` | |

### 2.2 Dispatch — `int_partitions()` (`cut.c:342`)

```c
if (relative_int)        P = do_simple_int(P1, P2);   // -r
else if (minimal_int)    P = do_int_1(P1, P2);        // -s  (§2.4 minimal, two-pass)
else if (maximal_int)    P = do_int_2(P1, P2);        // -m  (§2.4 maximal, two-pass)
else if (analyse_int)    P = do_int_analyse1(X);      // -aXX
else if (analyse_int_stab) P = do_int_analyse2(X);    // -AXX
else                     P = do_min_int(P1, P2);      // default
```

The result is written with `inf_write_partition(f, P)` to `<filebase>.partition`.

### 2.3 Single-pass primitives (`cut.c`)

**`do_simple_int()`** — naive/relative: for every element of `P1`, copy it into
result cluster `i` if it belongs to *any* cluster of `P2`. Trim empties.

```c
for (i=1;i<k1;i++) {
  X1 = P1->el[i];
  while (elements_left(X1)) {
    for (j=1;j<k2;j++) if (is_in_set(get_element(X1),P2->el[j]))
      P->el[i] = add_element(P->el[i], get_element(X1));
    X1 = next_element(X1);
  }
}
trim_cluster(P);
```

**`do_min_int()`** — best-unique-match: for each `P1` cluster, count overlaps
`s[j]` with every `P2` cluster. Find the unique maximum; if exactly one cluster
ties at the max, copy the shared elements into that result cluster.

**`do_max_int()`** — all-maximum-match: same counting, but copies shared elements
for *every* cluster tied at the max (each tie → a fresh result cluster). `P->k = k+1`.

### 2.4 Two-pass §2.4 operations (`cut.c`)

**`do_int_1()`** — **minimal** intersection (`-s`). Mirrors doc §2.4.2:
```c
C1 = do_min_int(P1, P2);   // pass 1
C2 = do_min_int(P2, P1);   // pass 2
for (i=1;i<k1;i++) {
  X = C1->el[i];
  for (j=1;j<k2;j++) {
    x = X->el;
    if (is_in_set(x, C2->el[j])) P->el[i] = C2->el[j];   // result cluster becomes whole C2 cluster
  }
}
trim_cluster(P);
```

**`do_int_2()`** — **maximal** intersection (`-m`). Identical two-pass structure
but using `do_max_int` for both passes.

### 2.5 Analysis tools (`cut.c`)

**`do_int_analyse1()`** (`-aXX`, `analyse_int`):
```c
set_rand(time); use_class_weights = TRUE; ls_heuristic_cycler = TRUE;
ls_heuristic_count = 500; k = kstart; l = vec_len;
V1 = copy_set_fast(X); V2 = copy_set_fast(X);
// generate two partitions via random_centroids -> MSE_gla2 -> local_search
P1 = ...; g = MSE_gla2(V1, P1, C, &d, n); sc = stochastic_complexity(P1,k,l,n);
g += local_search(NULL, P1, C, sc, &d, k, l, n);
P2 = ...; (same)
P = do_int_2(P1, P2);   // maximal intersection
// iterate up to 48 times: re-run GLA+LS on previous result, intersect again
for (i=1; c && i<49; i++) {
  random_centroids(k,l,C,X); V2 = partition_to_set(P2);
  g = MSE_gla2(V2, P2, C, &d, n); sc = stochastic_complexity(P2,k,l,n);
  g += local_search(NULL, P2, C, sc, &d, k, l, n);
  P1 = copy_partition(P); P = do_int_2(P1, P2);
  tn = sum sizes; c = (tn != t); t = tn;   // stop when element count stabilizes
}
```

**`do_int_analyse2()`** (`-AXX`, `analyse_int_stab`):
```c
for i in 1..10: P[i] = generate via random_centroids -> MSE_gla2 -> local_search;
for j in 1..10:
  rerandomize_order(P);                       // shuffle order
  R = do_int_2(P[1], P[2]);
  for i in 3..10: R = do_int_2(copy(R), P[i]); // sequential intersection
  record element count;
```

---

## 3. Java implementation (current)

- `CommandRegistry`: `"cut"` → `CutCommand` (note: registered as **`cut`**, not
  `intersect`).
- `binclass-cli/.../cli/CutCommand.java` — parses switches, loads vectors via
  `DataLoader.loadVectors(filebase)`, runs a strategy, writes with
  `PartitionWriter.writePartition(result, outputFile)` to `<filebase>.partition`.
- `binclass-algorithms/.../cut/CutEngine.java` — the intersection primitives.

### 3.1 Switch mapping in `CutCommand.execute()`

| Java switch flag | Maps to engine method | C function it mirrors |
|------------------|-----------------------|------------------------|
| `-r` relativeInt | `simpleIntersection` | `do_simple_int` ✅ |
| `-s` minimalInt    | `minimalInterval`    | `do_min_int` (single-pass) ⚠️ |
| `-m` maximalInt    | `maximalInterval`    | `do_max_int` (single-pass) ⚠️ |
| default            | `performStandardCut` → `minimalInterval` | `do_min_int` ✅ semantics |
| `-a` analyseInt    | `performStandardCut`/simplified | `do_int_analyse1` ⚠️ simplified |
| `-A` analyseStab   | `performAnalyseIntStab` | `do_int_analyse2` ⚠️ simplified |

### 3.2 Engine methods (`CutEngine`)

- `simpleIntersection(p1, p2)` — mirrors `do_simple_int`. ✅
- `minimalInterval(p1, p2)` — mirrors `do_min_int` (best unique match). Single-pass.
- `maximalInterval(p1, p2)` — mirrors `do_max_int` (all max matches). Single-pass.
- Helpers: `bestUniqueMatch`, `maximumOverlap`, `maximalMatchClusters`,
  `countOverlap`, `belongsToAnyCluster`, `trim`.

### 3.3 Analysis helpers in `CutCommand`

- `performAnalyseIntStab(vectorSet, kstart1)` — generates a few partitions via
  nearest-centroid assignment and intersects them with `simpleIntersection`.
- `relativeIntervalAnalysis` / `minimalCut` / `maximalCut` — generate two
  partitions from the first-k centroids, intersect.
- `generatePartition(vectorSet, k)` — seeds centroids from the first k vectors,
  assigns each vector to nearest centroid (Hamming). **No GLA + local search.**

---

## 4. The gap

1. **`-s` / `-m` are wrong.** Doc §4.3.5 says `-s` = *minimal* and `-m` =
   *maximal* intersection, which per §2.4.2 are the **two-pass** `do_int_1` /
   `do_int_2`. Java maps them to the **single-pass** `do_min_int` / `do_max_int`
   helpers (`minimalInterval` / `maximalInterval`). The two-pass merge logic —
   compute both directions, then make each result cluster equal to the whole
   counterpart cluster of the other direction — is **missing**.

2. **Analysis modes are simplified.** C's `-aXX`/`-AXX` use full
   `random_centroids → MSE_gla2 → local_search` and iterate up to 48 / 10 times.
   Java generates partitions by nearest-centroid assignment (first-k seeds) with
   no local search, so results differ quantitatively.

3. **Command name.** C registers the command as `intersect`; Java registers it as
   `cut`. The doc's §4.3.5 header is "Classification intersections" / command
   `intersection`. Consider whether `CutCommand.getName()` should expose both.

4. **Default mode.** C default (no switch) = `do_min_int` (single-pass best match).
   Java default = `performStandardCut` → `minimalInterval` (= `do_min_int`). These
   actually agree, but the switch→function mapping is otherwise inconsistent with
   the doc.

---

## 5. What to implement to close the gap

1. Add two-pass methods to `CutEngine`:
   - `minimalIntersection(p1, p2)` mirroring `do_int_1`.
   - `maximalIntersection(p1, p2)` mirroring `do_int_2`.
   Reuse existing `simpleIntersection`/`minimalInterval`/`maximalInterval` for the
   per-direction pass.

2. Map `-s` → `minimalIntersection`, `-m` → `maximalIntersection` in `CutCommand`.

3. (Optional) enrich analysis modes with GLA + local search and the iteration loop,
   or at least document that they remain simplified.

4. Add JUnit tests mirroring C behavior for the two-pass operations.

5. Consider exposing command name `intersect`/`intersection` in addition to `cut`.

---

## 6. Reference: exact C source (`original/cut.c`)

```c
Partition *do_simple_int (Partition *P1, Partition *P2) {   // -r : naive/relative
  ... for each cluster i of P1, copy elements present in any cluster of P2; trim_cluster(P); }

Partition *do_min_int (Partition *P1, Partition *P2) {      // best unique match
  ... count s[j]; find max J; if unique maximum nm==1 copy shared elements into result i; trim_cluster(P); }

Partition *do_max_int (Partition *P1, Partition *P2) {      // all max matches
  ... count s[j]; for every j tied at max, add shared elements to fresh cluster k; P->k=k+1; trim_cluster(P); }

Partition *do_int_1 (Partition *P1, Partition *P2) {        // -s : §2.4 minimal (two-pass)
  C1 = do_min_int(P1,P2); C2 = do_min_int(P2,P1);
  for i: for j: if element of C1[i] in C2[j] then P->el[i] = C2->el[j]; trim_cluster(P); }

Partition *do_int_2 (Partition *P1, Partition *P2) {        // -m : §2.4 maximal (two-pass)
  C1 = do_max_int(P1,P2); C2 = do_max_int(P2,P1);
  for i: for j: if element of C1[i] in C2[j] then P->el[i] = C2->el[j]; trim_cluster(P); }

Partition *do_int_analyse1 (ST *X) {                        // -aXX : iterative stability on data set
  random_centroids -> MSE_gla2 -> local_search x2; P = do_int_2(P1,P2);
  iterate up to 48 times re-running GLA+LS and intersecting until element count stabilizes; }

Partition *do_int_analyse2 (ST *X) {                        // -AXX : order-independence stability on data set
  generate 10 partitions via GLA+LS; for each shuffle: sequential do_int_2 intersection; record counts; }

void int_partitions (...) {                                 // dispatch + write <filebase>.partition
  if relative_int -> do_simple_int; else if minimal_int -> do_int_1;
  else if maximal_int -> do_int_2; else if analyse_int -> do_int_analyse1;
  else if analyse_int_stab -> do_int_analyse2; else -> do_min_int;
  inf_write_partition(f, P); }
```
