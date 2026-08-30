# Automatic Search — `-a` Attempt Count

## Background

The `classify` command supports an automatic SC-minimizer (`-S`, no explicit
`kstop`) that mirrors the C functions `sca_main()`, `sca_scanner()` and
`sca_pingpong()` from `original/classify.c`. The search runs GLA for each cluster
count `k`, records the best stochastic complexity (SC) per `k`, and keeps the
global best.

## Problem: `-a` is stored but not consumed as an attempt count

The CLI option `-a N` maps to `GLAConfig.iterBase()`. In the original C code,
`-a` sets `iter_base`, which becomes `max_iter = iter_base` and drives how many
times GLA is **applied per cluster count** with different random starting
centroids.

In `use_gla()` (`original/glainf.c`) the loop condition is:

```c
#define do_trials(i,dmin,scmin,minsc,better) \
  ((i<(max_iter+1)) || (better && ...) || (filter && ...) || (...))
...
vs = max_iter + 1;
i = 1;
while (do_trials(i, ...)) {
    random_centroids(k, l, C, V);   // <-- different starting centroids each trial
    g = <configured GLA variant>(V, P, C, &d, n);
    sc = stochastic_complexity(P, C->k, l);
    if (sc < scmin) { scmin = sc; copy_centroids(cmin, C); }
    i++;
}
```

So `-a N` means: **run GLA up to `N` times per cluster count**, each time seeding
with a different random centroid set (`random_centroids`). The best SC across the
trials wins. This makes the search robust against bad local minima — a single run
could lock onto an unlucky starting point and report a poor clustering as "best".

The convergence **safety limit** (`-F`, `safety_limit`) is a *different* concept:
it only extends trials while SC keeps improving, bounding total work. It must not
be confused with the attempt count.

### Current Java gap

| Flag | GLAConfig field | Used by | Meaning in C |
|------|-----------------|---------|--------------|
| `-n` | `maxIter`       | `GLAEngine.gla()` internal convergence loop | max GLA iterations per call |
| `-a` | `iterBase`      | (currently unused for attempts) | number of GLA **attempts** per k |

Two symptoms:

1. In `AutomaticSearch.run()` the local variable
   `int maxIter = config.iterBase() > 0 ? config.iterBase() : 1;` is computed but
   never used — the forward scan, ping and pong each call `runStep(kk)` exactly
   once per cluster count.
2. In `ClassifyCommand.runRangeSearch()` (NAUTO / LCENT paths) each k calls
   `runGLAAndCalculateSC(...)` exactly once.

Both mean a single GLA run per k, so `-a N` has no effect on robustness.

## Plan

Make `-a N` an **attempt count**: for every cluster count, apply GLA up to `N`
times with different random starting centroids and keep the best SC (and its
partition/centroids). When `iterBase <= 0`, fall back to a single attempt so the
default behaviour is unchanged.

### 1. `AutomaticSearch` — AUTO path (`binclass-algorithms`)

- Add a private helper that runs GLA for one cluster count `kk` up to
  `attempts = config.iterBase() > 0 ? config.iterBase() : 1` times and returns the
  best `StepResult`. Each attempt re-seeds centroids with
  `CentroidInitializer.randomInit(vectorSet, kk)` (fresh `InfiniteCentroids` +
  `Partition`) so GLA starts from a different point. The first attempt keeps the
  existing "first k vectors as centroids" seeding to preserve determinism for tests;
  subsequent attempts use random init. Keep only finite SC results.
- Replace the three `runStep(kk)` call sites (forward scan, `ping`, `pong`) with a
  single new method that performs the attempts loop and returns the best result.
- Remove the dead local `maxIter` in `run()`.

### 2. `ClassifyCommand` — NAUTO / LCENT path (`binclass-cli`)

- In `runRangeSearch`, wrap the per-k work so it runs up to `attempts =
  config.iterBase() > 0 ? config.iterBase() : 1` times, keeping the best SC for
  that k. Attempt 0 uses the existing first-k-vectors seeding; later attempts use
  `CentroidInitializer.randomInit(vectorSet, k)`. Track the best partition and
  centroids per k (and globally).

### 3. Tests

- `AutomaticSearchTest`: keep `config(...)` at `iterBase = 0` so existing tests
  exercise single-attempt behaviour unchanged. Add a test with `iterBase > 1`
  verifying the search still returns valid results and terminates.
- Verify `ClassifyCommandTest` still passes (GLA calls use `atLeastOnce()`).

## Verification

```bash
mvn -q -pl binclass-algorithms compile
mvn -q -pl binclass-cli test
./binclass.sh classify -Ldata/entero.centroids -Pdata/entero.partition -S10 -a10 data/entero
```
