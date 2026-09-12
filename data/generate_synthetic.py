#!/usr/bin/env python3
"""Generate a small synthetic binary-vector dataset for E2E CLI tests.

Produces ``test.data`` and ``test.header`` describing ~100 vectors of length 40
that fall into five well-separated ground-truth clusters of varying sizes
(12-28). Every vector in a cluster shares the same class name, while every
vector has a unique id. Vectors are built around distinct random centroids with
light bit-flip noise so that the clusters are cleanly recoverable by the regular
``classify`` command (which reliably yields 4-6 classes on this data) and can be
used to validate the cumulative classifier end-to-end.

The longer vectors (length 40) combined with low per-bit noise keep the
within-cluster Hamming spread (~4 bits, std ~2) small relative to the gap between
centroids (~20 bits), so GLA converges to a stable handful of clusters instead of
collapsing nondeterministically.

Re-run to regenerate deterministically (the RNG is seeded).
"""
from __future__ import annotations

import random

# --- layout constants -------------------------------------------------------
VECLEN = 40          # length of each binary vector
NAME_LEN = 9         # class-name field width (matches existing data files)
ID_OFFS = 15         # column where the id string starts
ID_LEN = 5           # width of each unique id
VEC_OFFS = 23        # column where the binary vector starts

# --- clusters ---------------------------------------------------------------
# Distinct class names, each padded to NAME_LEN. Sizes vary between 5 and ~17
# and sum to exactly 100 vectors.
CLUSTER_NAMES = ["AEROB", "ENTERO", "VIBRIO", "PSEUD", "SALMO"]
CLUSTER_SIZES = [12, 20, 22, 24, 22]
assert sum(CLUSTER_SIZES) == 100, "cluster sizes must sum to the vector count"

NOISE_P = 0.06     # per-bit flip probability around each centroid


def make_centroid(rng: random.Random) -> list[int]:
    return [rng.randint(0, 1) for _ in range(VECLEN)]


def perturb(rng: random.Random, centroid: list[int]) -> list[int]:
    return [bit if rng.random() > NOISE_P else 1 - bit for bit in centroid]


def hamming(a: list[int], b: list[int]) -> int:
    return sum(1 for x, y in zip(a, b) if x != y)


def main() -> None:
    rng = random.Random(20260908)

    # Generate well-separated centroids. Re-roll any pair that is closer than
    # 16 bits apart so the regular classifier reliably recovers all clusters.
    centroids: list[list[int]] = []
    while len(centroids) < len(CLUSTER_NAMES):
        c = make_centroid(rng)
        if all(hamming(c, existing) >= 16 for existing in centroids):
            centroids.append(c)

    # Sanity-check pairwise separation (informational only).
    for i in range(len(centroids)):
        for j in range(i + 1, len(centroids)):
            assert hamming(centroids[i], centroids[j]) >= 16, (i, j)

    lines: list[str] = []
    vector_index = 0
    for name, size, cluster_centroid in zip(CLUSTER_NAMES, CLUSTER_SIZES, centroids):
        for _ in range(size):
            vec = perturb(rng, cluster_centroid)
            vector_index += 1
            unique_id = f"{vector_index:0{ID_LEN}d}"
            name_field = name.ljust(NAME_LEN)[:NAME_LEN]
            id_field = unique_id.ljust(ID_LEN)[:ID_LEN]
            pad_name_to_id = " " * (ID_OFFS - NAME_LEN)
            pad_id_to_vec = " " * (VEC_OFFS - (ID_OFFS + ID_LEN))
            line = "".join([
                name_field,
                pad_name_to_id,
                id_field,
                pad_id_to_vec,
                "".join(str(b) for b in vec),
            ])
            assert len(line) == VEC_OFFS + VECLEN, (len(line), NAME_LEN, ID_OFFS, ID_LEN, VEC_OFFS)
            lines.append(line)

    header = "\n".join([
        f"vecoffs={VEC_OFFS}",
        f"veclen={VECLEN}",
        f"idlen={ID_LEN}",
        f"idoffs={ID_OFFS}",
        f"idord={''.join(str(i) for i in range(ID_LEN))}",
        f"namelen={NAME_LEN}",
    ]) + "\n"

    import os

    out_dir = os.path.dirname(os.path.abspath(__file__))
    data_path = os.path.join(out_dir, "test.data")
    header_path = os.path.join(out_dir, "test.header")

    with open(data_path, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    with open(header_path, "w") as fh:
        fh.write(header)

    print(f"wrote {len(lines)} vectors to test.data")
    print(f"wrote header to test.header")


if __name__ == "__main__":
    main()
