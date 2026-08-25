# Augment ClassifyCommand — Plan to Bring Java on Par with C `classify.c` (Spec §4.1.1)

> Goal: analyse the gap between the original C **SC minimizer** (`classify.c`, `glainf.c`, `parser.c`, `vars.c`, `const.h`) described in spec section **4.1.1**, and the current Java implementation
> (`binclass-cli/.../ClassifyCommand.java` + `GLAConfig` + `GLAEngine`). Produce a concrete plan to bring the Java `classify` command on par with the C tool.

Scope is limited to §4.1.1 (the *SC minimizer — GLA and Local Search*). Sections 4.1.2/4.1.3 (Split / PNN) and 4.2.3 (statistical uncertainty) are out of scope here but noted at the end.

---

## 1. Specification recap (§4.1.1)

The classification tool minimizes **stochastic complexity (SC)** as a function of the number of classes $k$. It combines two building blocks:

* **GLA** — assigns vectors to nearest centroid by a chosen distance/error rule, then recomputes centroids. Six `-f` alternatives exist:
  1. Hamming-distance → HMO (Gower)
  2. L1-distance → MAE
  3. L2-distance → MSE
  4. Shannon-codelength
  5. Hybrid $L_1$ + Shannon-codelength *(default)*
  6. Hybrid $L_2$ + Shannon-codelength

* **Initial centroids** chosen by `-c`: 1 purely random, 2 statistically random (from input set), 3 statistically coin-tossed, 4 random input vectors *(default)*, 5 RPNN.

Centroids are floating point by default; `-R` rounds them to {0,1}.

### Search for the SC minimum — two methods

* **Non-automatic** (`-bXX … -sXX`, candidates limited by `-nXX`): scan a fixed range $k_{start}..k_{stop}$, GLA each $k$, keep least-SC (or least-error with `-C`).
* **Automatic SC-minimizer** (`-aXX`, stop after `-SXX` steps without improvement): forward scan from 1 class until no enhancement in `-S` steps, then a **ping-pong** enhancement phase:
  * Step 0 — minimum of first scan is $SC_{k^*}$.
  * Step 1 — for some $k<k^*$ with $SC_k > SC_{k-1}$, try more candidates until $SC_k < SC_{k-1}$ (and $SC_k<SC_{k^*}$); update $k^*$.
  * Step 2 — symmetric for $k>k^*$.
  * Step 3 — repeat Steps 1–2. A safety value `-FX` guarantees termination; `-aXX` sets the initial number of candidates.

### Local search (MOLS)

Single operator via `-r1..-r6`, or all operators via `-r7` (cycle) / `-r8` (adaptive). Iteration count `-jX`. Operators: SJ1, RWO, RSA, RS1/RSW, SJ2, CMO. Adaptive update uses success counts with parameters $\alpha$ (memory weight) and $\beta(t)$ (forgetting).

### Switches (authoritative list from §4.1.1)

`-q -EFF -nXX -aXX -Lfilename -l -C -R -BFF -bXX -sXX -SXX -cX -FX -fX -rX -jX -eX -w -J`

### I/O

inputs: `.data`, `.header`; outputs: `.output` (log), `.centroids` (if `-l`), `.partition` (best partition).

---

## 2. Original C implementation analysis

### 2.1 Entry & dispatch — `classify_vectors()` (`classify.c`)

```
read_set(f, hdrfile)            // read data + header
coin_tosh(o, V, misfile)        // coin-toss initial centroids (-c), builds log2 factorials
methods(stdout/o)               // print "Methods:" summary of all flags
switch (search_type):
  ST_AUTO   -> sca_main(...)     // automatic SC minimizer
  ST_NAUTO  -> search_classes_nonautomatic(...)
  ST_LCENT  -> search_loaded(...)
  ST_ADAP   -> search_loaded(...)
```

### 2.2 `methods(FILE*o)` — config summary (`classify.c:677`)

Prints a human-readable "Methods:" block derived from every flag: prior type, distance_type → method name (e.g. *Hybrid L1/Codelength minimization*), selection mode (SC vs codelength), class weights, filter_exact_k, alternate_empty_cell_fix+weights, require_better, rounded_centroids, LS strategy (cycler / SJ1 / RWO / …), search_type (automatic / range / adaptive / loaded), centroid_type, trashcan. **Java has no equivalent structured summary.**

### 2.3 Non-automatic search — `search_classes_nonautomatic()` (`classify.c:263`)

* `scmin = 10000.0`, `lasti = 1000.0`.
* Allocates `scs[mk]` (size `mk = kstop+1` capped at `maximum_class_number=200`), init to `1000.0`.
* **Cap check**: if `k > maximum_class_number` → print *"More classes requested than possible"* and `exit(1)`.
* Loop `while (k < mk)`:
  * `P = allocate_partition(k+1)`; `C = use_gla(V, P, k+1, outfile, lasti, require_better, filter_exact_k, -1.0)`.
  * `sc = calculate_criteria(o, P, C, &lasti)`.
  * Update `scs[(C->k)-1]` with the min seen for that $k$.
  * If `log_centroids`: `save_centroids(p, C)` appended.
  * New best (`sc < scmin`) → write `.partition`, reset `kc=0`; else `kc++` and log *"Tries since best classification: %d"*.
  * Log elapsed time (since start + for current classification) via `print_time`.
  * `k++`.
* End: print **"SC as function of k"** table — every `scs[i] < 1000.0` formatted `%3d: %2.4f`.

### 2.4 Automatic scan + ping-pong (`classify.c`)

* `sca_main()` — init `scs[]`/`scmin` to `unassigned_sc()`, run `sca_scanner` then `sca_pingpong`, then `log_function(o, scs, lastk)` which prints the same `"N: value"` table.
* `sca_scanner()` — forward scan; `max_iter = iter_base`; `lasti = first_d`; loop `while (kc < kstopwhen && k < maximum_class_number+1)`: allocate `k+2`, `use_gla(..., better=TRUE, filter_exact_k, -1.0)`, `calculate_criteria`, update scs/best (`*kmin`, `*scmin`), reset/increment `kc`, log time via `sca_messages`.
* `sca_pingpong()` — enhancement loop; `best_code_length = FALSE`; ping (rescan `kstart..kmin`) then pong (rescan `lastk..kmin`), each calling `use_gla(..., minsc=scs[k])`, updating `nkm`/`enh`. Terminates when no improvement.

### 2.5 `calculate_criteria()` (`classify.c:80`) — the scoring breakdown

Computes and logs **four** quantities per classification:
* `sc = C->SC` (stochastic complexity)
* `d` = MAE / MSE / distortion depending on distance_type; for codelength distances `d = i1`.
* `i1` = average codelength (`C->I`) or, when `distance_type > DT_L2`, `C->I`.
* `i2` = shannon_entropy (TRUE for codelength distances, FALSE otherwise).

Logs:
```
Results: ak  = %d \n sc  = %1.5f \n cl1 = %1.5f\n cl2 = %1.5f\n d   = %1.5f
ak = %2d, SC = %2.4f, I1 = %2.4f, Itp = %2.4f  (d = %2.4f)
```
and warns `WARNING: There might be too few trials!` when `*lasti < d`, then sets `*lasti = d`. **Java returns only `sc`; the `d / i1 / i2` breakdown and the "too few trials" warning are missing.**

### 2.6 GLA trial loop — `use_gla()` (`glainf.c:1476`)

Signature: `use_gla(ST*V, Partition*P, int k, char*outfile, double lasti, int better, int filter, double minsc)`.
Trial condition macro:
```c
#define do_trials(i,dmin,scmin,minsc,better) \
  ((i<(max_iter+1)) || \
   (better && (dmin > lasti) && (i < (safety_limit+1))) || \
   (filter && !(dmin < 10000.0)) || \
   ((minsc > 0.0) && (minsc < scmin) && (i < (safety_limit+1))))
```
Per trial: `random_centroids`, then pick GLA variant by `distance_type` (`hybrid_gla_l1/l2`, `gla_sa`, `gla_sr`, `gla`, `MAE_gla`, `MSE_gla`, `fast_gla`) — or `MSE_gla2` when an LS operator is active and `k>4`. After GLA, if `ls_heuristic != NONE && k > 3/4`, call `local_search(...)`, recompute SC. Restores best partition (`cmin`), returns it.

### 2.7 Local search — `local_search()` (`glainf.c`)

Six-operator MOLS with:
* `suc[6]` success counters, `w[6]` adaptive weights (init 0), `p[6]` probabilities (init `1/6`).
* Parameters `alfa = 2.5`, `beta = 0.2`.
* Cycling (`ls_heuristic_cycler`) rotates SJ1→RWO→RSA→RS1→SJ2→RS2→…; adaptive picks operator by cumulative `p[]` probabilities and updates them on success (Eq. 14/15 with $\alpha$) or forgetting weights (Eq. 16 with $\beta(t)$).
* After the loop, a final GLA pass and keeps the better of mod/best.

Operators (`glainf.c`): `split_and_join` (SJ1), `replace_worst` (RWO), `replace_smallest` (RSA), `random_swap` (RS1), `random_swap2` (RS2), `split_and_join2` (SJ2).

### 2.8 Parser — `parse_classify()` (`parser.c:124`) and defaults (`vars.c`, `const.h`)

Key mappings:
| Switch | C meaning | Java current mapping |
|---|---|---|
| `-bXX` | kstart | kstart ✓ |
| `-sXX` | kstop (0=auto) | kstop ✓ |
| `-SXX` | kstopwhen | kstopwhen ✓ |
| `-E[FF]` | epsilon / decreasing_epsilon | epsilon ✓ (`decreasing_epsilon` missing) |
| `-q` | verbose=false | setupVerboseMode ✓ |
| **-rX** | **ls_heuristic (LS operator)** | **heuristic → GLAEngine method** ⚠️ semantic mismatch |
| `-t` | trashcan | trashcan ✓ |
| `-eX` | alternate_worst_match / alternate_empty_cell_fix (4 combos) | alternateMode stored, effect weak/unwired ⚠️ |
| `-m` | analyse_missing | analyseMissing ✓ |
| `-cX` | centroid_type (1..5) | centroidType ✓ |
| `-l` | log_centroids | logCentroids ✓ |
| `-J` | jeffreys_prior | jeffreysPrior ✓ |
| `-w` | use_class_weights | weights ✓ |
| **-B[FF]** | **require_better + optional first_d** | firstD only; no separate require_better ⚠️ |
| `-C` | best_code_length | bestCodeLength ✓ |
| `-R` | rounded_centroids | rounded ✓ |
| **-fX** | distance_type (drives GLA variant in C) | distanceType stored, used for scoring only ⚠️ |
| **-nXX** | **search_type=ST_NAUTO + max_iter** | maxIter only; no search-type switch ⚠️ |
| `-jX` | ls_heuristic_count = X+1 | heuristicCount = X+1 ✓ (stored) |
| `-FX` | safety_limit | safetyLimit ✓ |
| **-aXX** | **iter_base (+ ST_AUTO)** | iterBase stored ✓ |
| **-Lfilename** | **ST_LCENT + load centroids** | centroidFile parsed but only *save* implemented ⚠️ |

Defaults (`vars.c`): `kstart=1, kstop=0, kstopwhen=10, max_iter=20, safety_limit=500, iter_base=1, epsilon=0.001, ls_heuristic_count=50, maximum_class_number=200`; `distance_type = DT_L1_CL` (=5), `search_type = ST_AUTO`, `centroid_type = CT_RAND`.

---

## 3. Current Java implementation analysis

### 3.1 `ClassifyCommand.execute()`
Parses options into primitives, builds a single `GLAConfig`, then calls **only** `runRangeSearch(vectorSet, kstart, kEnd, config)`. It never branches on search type (always range search), so the **automatic SC-minimizer / ping-pong path is absent**.

### 3.2 `runRangeSearch()`
Mirrors `search_classes_nonautomatic` but **simplified**:
* `scmin = Double.MAX_VALUE`, tracks `noImprovementCount`.
* Safety bound via `k - kstart >= config.safetyLimit()`.
* Termination via `shouldTerminate(noImprovementCount, config)` (uses `-S`).
* **Missing vs C:** `scs[]` table + "SC as function of k" output, `kc` "tries since best" logging to the log file, `maximum_class_number` cap check (`exit(1)`), elapsed-time messages, per-`k` `calculate_criteria` breakdown.

### 3.3 `initializePartition()`
Uses first $k$ input vectors as initial centroids (CT_RAND / `-c4`). Other centroid types (`-c1..3,5`) are parsed but **not implemented**.

### 3.4 `runGLAAndCalculateSC()`
Switch on `config.heuristic()`:
| heuristic | GLAEngine method | C `-r` operator | Spec `-r` operator |
|---|---|---|---|
| 1 | `gla` | SJ1 | Split & Join v1 |
| 2 | `glaSr` | RWO | Replace worst class |
| 3 | `glaSa` | RSA | Replace smallest class |
| 4 | `hybridGlaL1` | RS1 (Random swap) | Random swap |
| 5 | `hybridGlaL2` | SJ2 | Split & Join v2 |
| 6 | `maeGla` | CMO (Class move) | Class Move |

⚠️ **Two-layer semantic drift:**
1. Java `-r` selects a *GLA method variant*; C/spec `-r` selects a *local-search operator*. The mapping above is approximate and the names don't line up (e.g. Java `2 → glaSr`, spec `2 → Replace worst`).
2. In C, `distance_type` (`-f`) chooses the GLA variant inside `use_gla`; in Java `-f` only affects scoring (`DistanceCalculator`), while `-r` drives the GLA method. The coupling of distance-type → GLA algorithm is therefore different.

Scoring: if `bestCodeLength()` uses `averageCodelength`, else `stochasticComplexity(...)` with actual non-empty cluster count and Jeffreys flag. **Missing:** `d / i1 / i2` breakdown, "too few trials" warning.

### 3.5 `GLAConfig` (record)
Carries all parsed fields: `epsilon, pnnThreshold(1.8), heuristic, alternateMode, centroidType, maxIter, safetyLimit, iterBase, n, kstopwhen, kcStopWhen(5 hardcoded), weights, rounded, jeffreysPrior, trashcan, analyseMissing, logCentroids, firstD, bestCodeLength, distanceType, heuristicCount`. Notable: `require_better` is **absent** (folded into `firstD`); `filter_exact_k` is **absent**; `decreasing_epsilon` is **absent**.

### 3.6 `GLAEngine`
Static methods `gla / glaSr / glaSa / hybridGlaL1 / hybridGlaL2 / maeGla / mseGla / fastGla`, each a single-shot GLA (not the multi-trial `use_gla` loop). No dedicated **local-search operator** implementations exist in Java (`split_and_join`, `replace_worst`, … are C-only). `heuristicCount` is stored but not consumed by an LS loop.

### 3.7 I/O
* `CentroidWriter.save()` writes centroids (Java format: comma-separated, with header comments) — **differs from C `save_centroids`** which writes `%d\n%d\n` then space-separated `%1.5f` values + weight. So `-l` output and `-L` input are not mutually compatible with the C tool.
* `PartitionWriter.writePartition()` handles `.partition`. No `.output` log writer equivalent to C's append-mode logging, no "SC as function of k" table, no time-elapsed lines.

---

## 4. Gap analysis

| # | Area | C / spec behaviour | Java current state | Impact |
|---|------|--------------------|--------------------|--------|
| **G1** | Automatic SC-minimizer (`sca_main`/`sca_scanner`/`sca_pingpong`) | Two-phase: forward scan + ping-pong enhancement; `-aXX`, `-SXX`; `best_code_length=FALSE` in pong | Only range search (`runRangeSearch`); no automatic mode, no ping-pong | Largest functional gap; spec's default search method missing |
| **G2** | `calculate_criteria` breakdown | Computes/logs `sc`, `d` (MAE/MSE/distortion), `i1` (codelength), `i2` (Shannon entropy); "Results:" block + "ak = …" line; "too few trials" warning when `lasti < d` | Returns only `sc`; no `d/i1/i2`, no breakdown log, no warning | Loss of diagnostic output and the `lasti` mechanism used by `use_gla` trial condition |
| **G3** | Local search operators (MOLS) + `local_search()` | 6 operators (SJ1/RWO/RSA/RS1/SJ2/CMO), cycling + adaptive probability update (`alfa`/`beta`) | GLAEngine methods exist but no LS operator implementations; `heuristicCount` stored, unused in an LS loop | `-r1..-r8` semantics not faithfully implemented |
| **G4** | Switch-letter semantics (`-r`, `-f`, `-n`) | `-r`=LS operator; `-f`=distance_type drives GLA variant; `-n`=ST_NAUTO + max_iter | `-r`=GLA method; `-f`=scoring only; `-n`=maxIter only, no search-type switch | Confusing/inconsistent CLI vs spec; distance→GLA coupling differs |
| **G5** | `scs[]` table & "SC as function of k" output | Printed at end of non-auto scan and via `log_function` | Not produced | Missing summary report |
| **G6** | `kc` "tries since best classification" logging | Logged to `.output` each iteration in range search | `noImprovementCount` tracked but not logged to output file | Minor reporting gap |
| **G7** | `maximum_class_number` cap (200) + `exit(1)` "More classes requested than possible" | Checked at start of both scan loops | Not checked | Can request more classes than C allows; no error |
| **G8** | Time-elapsed messages (`print_time` / `sca_messages`) | Since start + for current classification, logged each iteration | Not produced | Reporting gap |
| **G9** | `methods()` config summary | Structured "Methods:" block of all flags | Ad-hoc `log.info(...)` lines in `execute()`; no unified summary | UX/traceability gap |
| **G10** | `-L` load predefined centroids (`search_loaded` / `use_gla_load_centroids`) | Loads centroids, validates `l == V->el->length`, writes partition | `centroidFile` parsed but only *save* implemented; no loader | `-L` read path missing |
| **G11** | Centroid file format compatibility (`save_centroids`/`load_centroids`) | Header `%d\n%d\n`; per-centroid space-separated `%1.5f` + weight | `CentroidWriter` comma-separated with comment header | C↔Java centroid files not interchangeable |
| **G12** | `-eX` empty-cell / orphaned-centroid fix (4 combos) | `alternate_worst_match` × `alternate_empty_cell_fix` | `alternateMode` stored; effect weakly/unwired in GLAEngine | Empty-centroid handling differs |
| **G13** | `require_better` (`-B`) and `filter_exact_k` flags | Passed to `use_gla`; drive trial condition + best-partition filter | `firstD` present, but no separate `require_better`/`filter_exact_k` in GLAConfig | Trial-management semantics differ from C |
| **G14** | `decreasing_epsilon` (`-E` with 2 chars) | Toggles epsilon decay during GLA | Only numeric `-E`; decreasing mode absent | Minor algorithmic difference |
| **G15** | Centroid types `-c1..3,5` (classic/semi/rand/coin-toss/PNN) | Parsed into `centroidType` | Parsed but only CT_RAND (`-c4`) effectively used via `initializePartition` | Other initializations unimplemented |
| **G16** | `kcStopWhen(-W)` / hardcoded `5` | C uses `kstopwhen` for auto stop; Java hardcodes `kcStopWhen=5` | Field exists, value 5 hardcoded | `-W` not wired (minor) |

Prioritisation: **P0 = G1, G2, G3, G4** (core SC-minimizer parity). **P1 = G5–G10** (reporting + I/O + cap). **P2 = G11–G16** (format compat, flags, centroid types).

---

## 5. Plan to close the gaps

### Phase 0 — Foundations & shared scoring (`binclass-algorithms`)
Target: **G2**, and support for G1/G3.

* Create a `Criteria` record in `org.binclass.algorithms.dist`:
  ```java
  public record Criteria(double sc, double d, double i1, double i2) {}
  ```
* Extend `DistanceCalculator` with a method that mirrors `calculate_criteria`:
  * compute `sc = stochasticComplexity(...)`, `d` (MAE/MSE/distortion for L1/L2/HAM; `i1` for codelength distances), `i1` (average codelength / `C->I`), `i2` (Shannon entropy, TRUE for codelength distances).
  * return the full `Criteria`.
* Add a small logger helper that reproduces C's output lines:
  ```
  Results: ak = %d | sc = %.5f | cl1 = %.5f | cl2 = %.5f | d = %.5f
  ak = %2d, SC = %2.4f, I1 = %2.4f, Itp = %2.4f (d = %2.4f)
  ```
  and the `WARNING: There might be too few trials!` when `lasti < d`. Keep it behind a flag so unit tests stay quiet.
* Add `filterExactK` and `requireBetter` to `GLAConfig`; thread a `lasti` double-holder through the search loop (G13).

### Phase 1 — Local search operators (`binclass-algorithms`)
Target: **G3**, feeds G4.

* Create `org.binclass.algorithms.gla.LocalSearchOperator` enum mapping to C `eHeuristic`:
  `REPLACESMALLEST(1), SPLITJOIN1(2), SPLITJOIN2(3), REPLACEWORST(4), RANDOMSWAP(5), RANDOMSWAP2(6), NONE`.
* Implement the six operators as static methods on a new `LocalSearch` class (mirroring `glainf.c`): `splitAndJoin`, `replaceWorst`, `replaceSmallest`, `randomSwap`, `randomSwap2`, plus CMO. Each takes `(k, l, n, centroids, partition)` and returns the modified partition/centroids.
* Implement `localSearch(...)` with `suc[6]`, `w[6]`, `p[6]`, `alfa=2.5`, `beta=0.2`, cycling + adaptive update (Eq. 14–16). Expose a single entry point used by the search loop.
* Add unit tests (`LocalSearchTest`) for each operator and for the adaptive probability update.

### Phase 2 — Automatic SC-minimizer (`binclass-cli` / `binclass-algorithms`)
Target: **G1**, **G4**.

* Introduce a `SearchType` enum (`AUTO`, `NAUTO`, `LCENT`, `ADAP`) and branch in `ClassifyCommand.execute()` (mirroring `classify_vectors`).
* Implement `scaScanner(...)`: forward scan with `max_iter = iter_base`, `lasti = first_d`, loop while `kc < kstopwhen && k <= maximum_class_number`; maintain the `scs[]` array and best (`kmin`, `scmin`).
* Implement `scaPingpong(...)`: enhancement phase, ping then pong, `best_code_length = FALSE`, `minsc=scs[k]`, update `nkm`/`enh` until no improvement.
* Wire `-aXX` → iter_base/auto mode and `-SXX` → kstopwhen; keep safety value `-FX`.
* Add tests for scan termination and ping-pong convergence on small synthetic data.

### Phase 3 — Range-search parity (`ClassifyCommand`)
Target: **G5, G6, G7, G8**.

* In `runRangeSearch`: allocate `scs[]` (size capped at `maximum_class_number`), print the **"SC as function of k"** table to the `.output` log; log `kc` "tries since best"; add the `maximum_class_number` cap check with an error message/exit code; emit elapsed-time messages (`print_time` helper).
* Use the new `Criteria` from Phase 0 for scoring (G2).

### Phase 4 — CLI semantics & summary (`ClassifyCommand`, `GLAConfig`)
Target: **G4, G9, G13, G15**.

* Reconcile `-r`: keep a mapping but document that Java uses it to select the GLA method while C/spec use it for LS operators; add explicit handling so `-r7` (cycle) / `-r8` (adaptive) route through `LocalSearch`.
* Add separate `requireBetter` and `filterExactK` flags; keep `firstD` for `-BFF`.
* Implement remaining centroid types (`-c1..3,5`) in an initializer switch.
* Add a `methods()`-style summary method that logs the full "Methods:" block (prior, distance→method, selection mode, weights, filter, rounded, LS strategy, search type, centroid type, trashcan).

### Phase 5 — I/O & file formats (`binclass-cli`, `binclass-algorithms/io`)
Target: **G10, G11**.

* Implement a `CentroidLoader` that reads C's `save_centroids` format (header `%d\n%d\n`, then space-separated values + weight) and validates length; wire `-Lfilename` → `search_loaded`.
* Optionally add a compatibility writer/reader pair so Java↔C centroid files interoperate, or align `CentroidWriter` with the C format. Document the current difference (G11).

### Phase 6 — Remaining flags (`binclass-algorithms`, `ClassifyCommand`)
Target: **G12, G14, G16**.

* Wire `-eX` into an empty-cell/orphaned-centroid fix with the four combos.
* Implement `decreasing_epsilon` for `-E` (two-char form).
* Remove the hardcoded `kcStopWhen=5`; wire `-W` if present.

---

## 6. Testing strategy

* **Unit tests** (`binclass-algorithms`, JUnit 6): `Criteria`/`calculate_criteria` breakdown + "too few trials" warning; each LS operator; adaptive probability update; scan termination; ping-pong convergence; centroid loader format round-trip.
* **CLI/integration tests** (`binclass-cli`): option parsing for every switch (extend `ClassifyCommandSwitchesTest`); search-type dispatch; cap check exit code; `.output`/`.centroids`/`.partition` artifacts.
* **Behavioural parity test**: run the same small dataset through C and Java with identical flags and compare SC values + partition within a tolerance, to validate G1–G4 end-to-end.
* Run `mvn spotless:apply`, then the "run-tests" skill (fall back to `mvn test`), and re-run SonarQube per the sonarqube skill after each phase.

---

## 7. Out of scope (noted for later)

* **§4.1.2 / §4.1.3** — Split and Pairwise-Nearest-Neighbor algorithms (Java has `SplitGLA`/`JoinGLA`; full parity tracked in `RemainingFunctionalities.md`).
* **§4.2.3** — statistical uncertainty tool over repeated GLA runs (`gla_statistics` prints mean/std/variance; partially present in C, needs Java port if required).

---

## 8. Key files reference

| Concern | C source | Java |
|---|---|---|
| Entry/dispatch | `original/classify.c` `classify_vectors` | `ClassifyCommand.execute` |
| Config summary | `methods()` | *(none — G9)* |
| Range search | `search_classes_nonautomatic()` | `ClassifyCommand.runRangeSearch` |
| Automatic scan/pong | `sca_main/sca_scanner/sca_pingpong()` | *(none — G1)* |
| Load centroids | `search_loaded()` / `use_gla_load_centroids()` | `CentroidWriter` (save only) |
| Scoring breakdown | `calculate_criteria()` | `ClassifyCommand.runGLAAndCalculateSC` (+ Phase 0 `Criteria`) |
| GLA trial loop | `use_gla()` (`glainf.c`) | `GLAEngine.*` (single-shot) |
| Local search | `local_search()` + operators (`glainf.c`) | *(none — G3)* |
| Config record | global vars (`vars.c`, `const.h`) | `GLAConfig` |
| Option parsing | `parse_classify()` (`parser.c`) | `ClassifyCommand.execute` / `BaseCommand` helpers |
| Centroid I/O | `save_centroids()/load_centroids()` (`centroid.c`) | `CentroidWriter` (+ Phase 5 loader) |
