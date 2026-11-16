# Feature Scoring — Corrected

## Status: cannot be completed from the material supplied

Stage 2 requires recalculating **26 feature scores** using "the original eight criteria and
supplied weights" and "the original criterion values from the research report".

**That research report is not in the repository and was not supplied with this brief.** A search
of the repository for it returns nothing; the attached brief contains only the expected top-10
table, not the 26 rows of criterion values.

The brief is explicit: *"Do not silently change criterion values to obtain preferred rankings"*
and *"If your independently calculated values disagree, identify the discrepancy instead of
forcing the expected values."* Inventing 26 × 8 = 208 criterion values would be exactly the
fabrication the brief forbids, and any ranking produced from invented inputs would be worthless.

So this document does three things instead: it states what can be verified from the supplied
table, it records a genuine arithmetic discrepancy in that table, and it supplies a ready
calculator so the work completes in one step once the source table is provided.

## 1. What is verified

### The weights are well formed

```
0.20 + 0.20 + 0.15 + 0.15 + 0.10 + 0.10 + 0.05 + 0.05 = 1.00   (exact, decimal arithmetic)
```

A weight set that sums to 1.00 means a score is a convex combination of the eight criteria, so
for criteria on a 0–10 scale every score must lie in [0, 10]. All ten published scores do.

### The published ordering is monotonic, with one tie

Descending order holds throughout **except** #11 and #20, which are both published as `9.025`:

| Rank | Feature | Published score |
| ---: | --- | ---: |
| 1 | #4 Smart Entity Action Chips | 9.425 |
| 2 | #13 Zero-Trace Auto-Incognito | 9.375 |
| 3 | #23 Inline Autofill | 9.350 |
| 4 | #3 Typo-Undo Pill | 9.275 |
| 5 | #1 Inline Math Evaluator | 9.200 |
| 6 | #21 Fuzzy Spell Engine | 9.125 |
| 7 | #5 Secret & OTP Auto-Shredder | 9.075 |
| 8 | #11 Parameterized Snippets | 9.025 |
| 9 | #20 Local N-Gram Prediction | 9.025 |
| 10 | #22 Local Voice Input Bridge | 8.975 |

The stated tie-break is *User Value, then Technical Feasibility*. Applying it needs the two
criterion values for #11 and #20, which are not available — so **the order of ranks 8 and 9
cannot be justified from the supplied data**. The table above keeps the published order and does
not claim to have verified it.

## 2. A real discrepancy in the supplied table

If every criterion is an integer on a 0–10 scale, then because every weight is a multiple of
0.05, every possible score must be a multiple of 0.05 — that is, `score × 20` must be a whole
number.

It is not, for eight of the ten rows:

| Feature | Score | score × 20 | Integer criteria possible? |
| --- | ---: | ---: | :--- |
| #4 Smart Entity Action Chips | 9.425 | 188.5 | **no** |
| #13 Zero-Trace Auto-Incognito | 9.375 | 187.5 | **no** |
| #23 Inline Autofill | 9.350 | 187.0 | yes |
| #3 Typo-Undo Pill | 9.275 | 185.5 | **no** |
| #1 Inline Math Evaluator | 9.200 | 184.0 | yes |
| #21 Fuzzy Spell Engine | 9.125 | 182.5 | **no** |
| #5 Secret & OTP Auto-Shredder | 9.075 | 181.5 | **no** |
| #11 Parameterized Snippets | 9.025 | 180.5 | **no** |
| #20 Local N-Gram Prediction | 9.025 | 180.5 | **no** |
| #22 Local Voice Input Bridge | 8.975 | 179.5 | **no** |

Every score is, however, a multiple of **0.025** (`score × 40` is always whole). That is the
signature of half-point criteria — e.g. a criterion scored 7.5 rather than 7 on one of the 0.05
or 0.10 weights — or of a weight set that differs from the one stated.

**One of these must be true, and only the original report can say which:**

1. criteria were scored in half points, not integers;
2. the weights actually used differ from `0.20/0.20/0.15/0.15/0.10/0.10/0.05/0.05`;
3. the scores were not produced by this formula.

Until that is settled, no set of values can be called a *correction* — the published numbers are
not reproducible from integer inputs under the stated formula, and a recalculation that produced
them from integers would be wrong.

## 3. What is needed to finish

Provide the 26 rows, each with its eight criterion values (and the scale, e.g. 0–10 integers or
0–10 in half points). Then the ranking follows mechanically.

## 4. Ready-to-run calculator

Drop the table in as `scores.csv` (`feature,val,daily,priv,feas,perf,oss,inn,maint`) beside this
file and run:

```python
#!/usr/bin/env python3
"""Recalculate feature scores exactly. Decimal arithmetic: no float drift, no silent rounding."""
import csv
from decimal import Decimal as D, ROUND_HALF_UP

W = dict(val=D("0.20"), daily=D("0.20"), priv=D("0.15"), feas=D("0.15"),
         perf=D("0.10"), oss=D("0.10"), inn=D("0.05"), maint=D("0.05"))
assert sum(W.values()) == D("1.00"), "weights must sum to exactly 1"

def score(row):
    terms = {k: W[k] * D(row[k]) for k in W}
    return sum(terms.values()), terms

rows = []
with open("scores.csv", newline="") as fh:
    for row in csv.DictReader(fh):
        total, terms = score(row)
        rows.append((total, D(row["val"]), D(row["feas"]), row["feature"], terms))

# descending score; ties broken by User Value, then Technical Feasibility
rows.sort(key=lambda r: (-r[0], -r[1], -r[2]))

print(f"{'#':>2}  {'score':>7}  {'display':>7}  feature")
for i, (total, _v, _f, name, _t) in enumerate(rows, 1):
    shown = total.quantize(D("0.001"), rounding=ROUND_HALF_UP)   # display only
    print(f"{i:>2}  {str(total):>7}  {str(shown):>7}  {name}")

print("\nEvery multiplication, top 10:")
for total, _v, _f, name, terms in rows[:10]:
    parts = " + ".join(f"{W[k]}({terms[k] / W[k]})" for k in W)
    shown = total.quantize(D("0.001"), rounding=ROUND_HALF_UP)
    print(f"\n{name}\n  = {parts}\n  = {total}  →  {shown}")
```

Notes on the method, matching the brief's requirements:

- **Every multiplication is shown** — `terms[k] / W[k]` recovers the criterion value, so each
  printed line reads `0.20(9) + 0.20(10) + …`.
- **Full precision internally.** `Decimal` is used throughout, so no binary-float drift and no
  intermediate rounding. `0.20 × 7.5` is exactly `1.500`, not `1.4999999999`.
- **Rounding only for display**, to three decimals, with `ROUND_HALF_UP` stated explicitly.
- **Sorting** is on the unrounded total; ties resolve on User Value then Feasibility.
- The top 10 are printed in full so they can be checked line by line against the report.

## 5. Ranking used for Phase 1 scope

The brief fixes the Phase 1 scope (five features: #13, #3, #1, #4, #23) independently of the
scores, so the design work in `PHASE_1_TECHNICAL_DESIGN.md` does not depend on resolving this.
The scoring table affects which features come *after* v1.1, not what v1.1 contains.
