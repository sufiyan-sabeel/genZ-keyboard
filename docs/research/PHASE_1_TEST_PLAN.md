# Phase 1 Test Plan — GenZ Keyboard v1.1

Grounded in what the repository actually has: **15 JVM unit tests, no `androidTest` source set,
no device or emulator available.** The plan is therefore split into what can be automated now
(JVM, runs in CI) and what needs hardware (explicitly marked, not claimed as done).

---

## 1. Test levels

| Level | Where | Runnable today | Covers |
| --- | --- | --- | --- |
| **L1 unit** | `app/src/test` (JVM) | **yes**, in CI | math parser, entity regex fallback, incognito decision table, undo state machine |
| **L2 service logic** | JVM with a fake `InputConnection` | yes, if a seam is added | commit/undo/abandon paths |
| **L3 instrumentation** | `app/src/androidTest` | **no — does not exist yet** | real IME binding, `InputConnection` against a real `EditText` |
| **L4 manual** | physical device | no device available | host-app compatibility, feel, latency |

L1 is the backbone: it is the only level that can run here, and it covers the logic most likely to
be wrong (parsing, classification, state transitions). L3 must be **created** in Phase 1 — the
repo has never had it.

## 2. Android version matrix

| API | Notes for these features |
| --- | --- |
| 26–29 | baseline; `IME_FLAG_NO_PERSONALIZED_LEARNING` available (26+); **no inline autofill**; no clipboard classification; no `EXTRA_IS_SENSITIVE` |
| 30–32 | inline autofill available (30+); `getClassificationStatus` available from 31 |
| 33 | `EXTRA_IS_SENSITIVE` available; clipboard preview suppression becomes testable |
| 34–35 | target range; behaviour should match 33 |

## 3. Per-feature cases

### #13 Zero-Trace Auto-Incognito

| ID | Case | Expected | Level |
| --- | --- | --- | --- |
| 13.1 | text password field | incognito on, reason `PASSWORD_FIELD` | L1 |
| 13.2 | visible password variation | on | L1 |
| 13.3 | web password variation | on | L1 |
| 13.4 | numeric password variation | on | L1 |
| 13.5 | `IME_FLAG_NO_PERSONALIZED_LEARNING` set on a plain text field | on, reason `NO_PERSONALIZED_LEARNING` | L1 |
| 13.6 | manual toggle off while in a password field | stays **on** (auto reason wins) | L1 |
| 13.7 | move password field → plain field | off, reasons cleared | L1 |
| 13.8 | same field restarted (`restarting = true`) | reasons preserved | L1 |
| 13.9 | `EditorInfo == null` | manual only; no crash | L1 |
| 13.10 | incognito on → type → leave → reopen | no learned words, no clipboard entries | L2 |
| 13.11 | incognito on with AI configured | AI path never invoked | L2 |
| 13.12 | indicator shows a reason, not just a state | UI assertion | L3 |

**Failure-safety case (required by the brief):** 13.13 — a field that *looks* safe but sets
`NO_PERSONALIZED_LEARNING` must still go incognito. Detection must fail toward privacy, never
toward learning.

### #3 Typo-Undo Pill

| ID | Case | Expected | Level |
| --- | --- | --- | --- |
| 3.1 | autocorrect applied → chip shown | chip present with original word | L1 |
| 3.2 | tap chip | original word restored | L2 |
| 3.3 | cursor moved before tap | chip gone; no edit attempted | L2 |
| 3.4 | field changed before tap | chip gone | L2 |
| 3.5 | backspace before tap | chip gone | L1 |
| 3.6 | expiry elapsed | chip gone | L1 |
| 3.7 | editor text changed underneath | **abandon silently**, no replacement | L2 |
| 3.8 | `InputConnection` returns false | no crash, state cleared | L2 |
| 3.9 | undo of a word containing punctuation/emoji | exact original restored | L1 |
| 3.10 | typing latency | no measurable change | L4 |

### #1 Inline Math Evaluator

| ID | Case | Expected | Level |
| --- | --- | --- | --- |
| 1.1 | `2+2` | `4` | L1 |
| 1.2 | precedence `2+3*4` | `14` | L1 |
| 1.3 | parentheses `(2+3)*4` | `20` | L1 |
| 1.4 | unary minus `-5+2` | `-3` | L1 |
| 1.5 | decimals `0.1+0.2` | `0.3` (formatted) | L1 |
| 1.6 | division by zero `1/0` | `DIVIDE_BY_ZERO`, no chip | L1 |
| 1.7 | overflow `9e308*10` | `OVERFLOW`, no chip | L1 |
| 1.8 | unbalanced `(2+3` | `UNBALANCED_PARENS`, no chip | L1 |
| 1.9 | invalid `2++3` | `SYNTAX`, no chip | L1 |
| 1.10 | trailing operator `12+` | no chip (still typing) | L1 |
| 1.11 | 65-character expression | `TOO_LONG`, bounded time | L1 |
| 1.12 | result **never** auto-inserted | text unchanged until tap | L2 |
| 1.13 | password field | no chip | L1 |
| 1.14 | incognito on | no chip | L1 |
| 1.15 | evaluation time ≤ 64 chars | < 1 ms | L1 benchmark |

### #4 Smart Entity Action Chips

| ID | Case | Expected | Level |
| --- | --- | --- | --- |
| 4.1 | URL copied | URL chip; action opens browser | L1 + L3 |
| 4.2 | email copied | mail chip | L1 |
| 4.3 | phone copied | dial chip | L1 |
| 4.4 | 6-digit OTP copied | "Paste code" chip; **digits never rendered** | L1 |
| 4.5 | `EXTRA_IS_SENSITIVE` set (33+) | preview suppressed | L1 |
| 4.6 | classification incomplete (31+) | falls back, no `IllegalStateException` | L2 |
| 4.7 | API 26–30 | regex fallback path used | L1 |
| 4.8 | 10,000-character clip | only first 512 inspected; bounded time | L1 |
| 4.9 | catastrophic-backtracking input | linear time, no hang | L1 |
| 4.10 | clipboard cleared between listener and read | no crash | L2 |
| 4.11 | rapid clip changes | stale result discarded | L2 |
| 4.12 | incognito on | no chip | L1 |
| 4.13 | nothing persisted or logged | inspection of prefs/files | L3 |

### #23 Inline Autofill

| ID | Case | Expected | Level |
| --- | --- | --- | --- |
| 23.1 | API 30+ with autofill service | chip appears | L3 |
| 23.2 | API 30+ without autofill service | no chip, no reserved space | L3 |
| 23.3 | API 26–29 | no inline suggestions; clipboard offer still works | L3 |
| 23.4 | request builder throws | keyboard renders normally | L2 |
| 23.5 | keyboard dismissed mid-request | state cleared, no leak | L3 |
| 23.6 | field change mid-request | stale response ignored | L2 |
| 23.7 | rendering | no frame drop attributed to the request | L4 |
| 23.8 | no call to a non-existent cleanup API | code review + compile | L1 |

## 4. Host application regression

**None of this can be run in the current environment.** Listed so the gap is explicit; results
must be recorded by hand on a device before v1.1 ships.

| Host | What to check |
| --- | --- |
| plain `EditText` | baseline typing, undo, math chip |
| Chrome (web input) | inline autofill, `InputConnection` quirks |
| WhatsApp | autocorrect + undo pill in a chat field |
| Termux | key events, no composing-text assumptions |
| a code editor | no autocorrect, math chip must not fire inside code |
| a password manager | incognito on, no suggestions, no clipboard offer |

## 5. Build-variant checks

| Check | standard | fdroid |
| --- | --- | --- |
| compiles | required | required |
| permissions unchanged (6) | required | required |
| no proprietary dependency on runtime classpath | — | required (CI gate) |
| anime artwork present / absent | 10 files | 0 files (CI gate) |
| existing features intact | required | required |

Both gates already exist in CI and were verified to fail when deliberately broken.

## 6. Acceptance criteria for v1.1

1. All L1 and L2 tests pass in CI on every push.
2. L3 instrumentation suite exists and passes on at least one API 30+ device and one API 26–29
   device.
3. Manual host matrix in §4 completed and recorded.
4. Both flavours compile; existing features unchanged.
5. No sensitive clipboard value persisted — verified by inspecting app storage after the §4 run.
6. Incognito prevents learning and retention — verified by inspecting learned-word storage.
7. No measurable typing-latency regression — Macrobenchmark, not impression.
8. Documentation matches implementation at release time.

**Criteria 2, 3, 5, 6 and 7 require hardware.** Until they are run, v1.1 is not releasable, and
this plan does not pretend otherwise.
