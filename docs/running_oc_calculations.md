# Running OpenCalphad (OC) calculations

How to generate fresh OpenCalphad reference data for cross-checking this
project's own equilibrium/phase-diagram code, on this machine.

## Where OC lives

- Source checkout with a working WSL2 build: `C:\Users\admin\codes\opencalphad`
  -- see that checkout's own `WSL_BUILD_README.md` for the build command
  (`make -f Makefile_Claude`) if the binary is missing or stale. The
  binary is `oc7C` (Linux ELF, x86-64, runs only inside WSL2 -- there is
  no native Windows binary in this checkout).
- A second checkout exists at `/mnt/c/tmp/opencalphad_check` (reachable
  from WSL2) but its `oc6P` binary is a **macOS ARM64 build** and will
  not run under WSL2/Linux (`Exec format error`). Don't use it unless
  you rebuild it for Linux first.
- TDB files and example macros live under
  `C:\Users\admin\codes\opencalphad\examples\macros\` (e.g. `agcu.TDB`,
  `cost507R.TDB`) -- OC's own macros and this project's own `.OCM` test
  macros both run from that directory (`r t ./agcu` reads
  `.../macros/agcu.TDB` relative to OC's cwd).
- This project's own saved transcripts and driver scripts:
  `docs/oc_reference_tests/` (see below).

## Quick recipe: run one `.OCM` macro non-interactively

This is the method that actually works reliably on this machine --
confirmed this session. No PTY driver needed for a scripted macro; OC
reads commands fine from a redirected file as long as the macro answers
every interactive prompt on its own line (see gotchas below).

```bash
wsl -d Ubuntu -- bash -c "cd /mnt/c/Users/admin/codes/opencalphad/examples/macros \
  && ../../oc7C <name>.OCM < /dev/null > <name>_output.txt 2>&1; echo EXIT:\$?"
```

Then read `<name>_output.txt` back (from Windows:
`C:\Users\admin\codes\opencalphad\examples\macros\<name>_output.txt`).

### Gotchas (each one cost real time to debug this session)

1. **`r t ./agcu` needs a blank line after it.** OC's "Select elements,
   finish with empty line" prompt follows the read-TDB command; if the
   macro's next line is already another command (`set cond ...`), OC
   consumes it as element names, prints `WARNING: No such element`
   repeatedly, and everything after silently desyncs. Always put an
   empty line right after `r t ./<name>`.
2. **`exit` needs its confirmation on the next line.** OC's `exit`
   prompts `Are you sure? /N/:` -- end every macro with:
   ```
   exit
   y
   ```
   Leaving this off makes OC block on stdin forever, or (if stdin is
   `/dev/null`) crash with a Fortran `End of file` runtime error at
   `metlib4.F90` instead of exiting cleanly.
3. **Don't `timeout`-kill the process while redirecting to a file.**
   SIGTERM discards buffered-but-unflushed Fortran stdout, so the log
   file comes back empty even though the run actually completed. Let
   the macro run to its own `exit` instead of imposing an external
   timeout; if a run hangs, it's almost always gotcha 1 or 2, not a
   genuinely stuck solve.
4. **A macro over-specifying conditions fails with "Degrees of freedom
   not zero"** (error 4144) rather than silently ignoring the extra
   condition -- e.g. `set status phase X=fixed 0` while T is *also*
   still a fixed condition is one too many constraints (fixing a phase
   sets an implicit condition on its own, per OC's manual: "By setting
   the status of a phase to fix you have also set a condition"). See
   `CALCULATE TRANSITION` below instead of doing this by hand.
5. **A TDB with more elements/phases than your macro selects can leave
   the system under-determined** (`Degrees of freedom` far from 0, or
   `NaN` G/N) if the interactive element-selection prompt isn't
   answered the way you expect for that database -- multi-component
   databases like `cost507R.TDB` need this checked carefully before
   trusting a result; not yet fully worked out for the ternary Al-Mg-Zn
   case (open item, see `docs/oc_reference_tests/` for what has and
   hasn't been validated this way).

## Getting the exact ZPF boundary equilibrium (Algorithm C2 ground truth)

The single most useful command for cross-checking this project's own
Algorithm C2 (`PhaseDiagramEngine.callAlgorithmC2`, boundary solve at a
stable-phase-set change) is **`CALCULATE TRANSITION`**, not `set status
phase ... = fixed 0` by hand. It fixes a named phase at zero amount,
releases one named *existing* condition, and solves for it directly --
exactly the boundary `solveZpf` computes internally.

Interactive dialogue (each line is what OC prompts for -- reproduce this
shape in a macro file, one answer per line):

```
c transition
<phase name, e.g. liquid>
<condition NUMBER to release, e.g. 1 for T -- NOT the letter "t">
```

Full worked example (from this session, Ag-Cu, LIQUID appearing at
x(Cu)=0.05 on cooling from the FCC_A1-only side):

```
new Y
set echo Y
r t ./agcu

set cond t=1150 p=1e5 n=1 x(cu)=.05
c e
c transition
liquid
1
l r 2
exit
y
```

Output includes the line `The transition occurs at   1.17612665E+03, set
as condition`, then `l r 2` dumps T, G/N, component chemical potentials,
and every phase's composition/amount at that exact boundary (LIQUID's
own amount reads `0.000E+00`). This transcript is saved as
`docs/oc_reference_tests/agcu_c2_transition_liquid_appears.OCM` /
`_output.txt` and is what
`src-test/calc/diagram/PhaseDiagramEngineAlgorithmC2Test.java` checks
this project's own C2 node against, point by point.

Caveat found this session: `c transition` performs a local Newton solve
seeded from the *current* equilibrium -- it does not search from
scratch. Starting already very close to (or past) the true boundary can
converge without T actually moving from the seed value, which reads as
suspiciously "too easy" and should not be trusted as the genuine
boundary without double-checking against a `step`/`map` trace's own
reported crossing T first (see below).

## Getting a `step`/`map` trace's own line-by-line log

For checking C1 (line-walking) rather than a single C2 node, run `step`
(1 axis) or `map` (2 axes) after `set ax ...`, e.g.:

```
new Y
set echo Y
r t ./agcu

set cond t=1150 p=1e5 n=1 x(cu)=.05
c e
set ax 1 t 1150 1230 5
step

exit
y
```

(`step`'s own `Step options? /NORMAL/:` prompt needs an empty-line
answer too.) The output reports each line's start point, equilibria
count, and `Creating a node at <T> where <phase> appears/disappear` --
useful for bracketing a crossing (see gotcha in the caveat above) before
trusting a `c transition` result, and for validating C1's own walk
behavior (`docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt` is a
worked example already checked into this project).

## The `run_pty*.py` driver scripts

`docs/oc_reference_tests/` also has three Python PTY drivers, kept from
earlier sessions where OC was being driven from a genuinely interactive
shell rather than a plain redirected macro file. **They were NOT needed
this session** -- the plain `oc7C <name>.OCM < /dev/null > out.txt 2>&1`
recipe above worked directly. Reach for one of these only if that plain
recipe stalls (e.g. OC or a sub-tool starts requiring real raw-terminal
interaction):

- **`run_pty_interval.py <oc-binary> <macro-file>`** -- sends the next
  input line every 1.5s on a fixed schedule, ignoring prompt detection
  entirely. The most robust of the three for a multi-command OC
  `step`/`map` macro; prompt-detection heuristics were found in earlier
  sessions to miss OC's actual prompt string or to arrive split across
  multiple `read()` chunks. Slower in principle, the only one of the
  three confirmed end-to-end reliable for a full macro sequence
  (including sub-prompts like `step`'s own "Step options?").
- **`run_pty.py <cmd> [args...] <inputs-file>`** -- prompt-detection
  heuristic (sends the next line when the trailing output looks like a
  prompt: ends in `:`, `/`, `OC6:`, or `>`). Can silently stall if a
  prompt doesn't land at the tail of a single read.
- **`run_pty_bulk.py <cmd> [args...] <inputs-file>`** -- pastes the
  entire input file as one `write()` right after fork, like a terminal
  paste. Found NOT to work for a real OC macro: OC's raw-mode reader
  can't keep up with an instant multi-line paste and just echoes the
  text back without processing it as commands.

## Other local codebases (read-only, do not modify)

- **OpenCalphad (Fortran)**: `C:\Users\admin\codes\opencalphad` (this
  session's working build) and `D:\codes\opencalphad` (referenced by
  older session notes; not confirmed reachable from this machine's
  WSL2 -- `/mnt/d` was not mounted when checked this session). The
  step/map engine is `src/stepmapplot/smp2A.F90` (`map_doallines`,
  `map_calcnode`, `map_step`/`map_step_old`/`map_step2`); phase/
  composition-set bookkeeping is in `src/models/gtp3Y.F90` and
  `gtp3_dd2.F90`.
- **pycalphad (Python)**: `D:\codes\pycalphad\pycalphad` (per older
  session notes, not re-verified this session) -- has its own
  `mapping/` module implementing ZPF-line stepping and phase-diagram
  tracing, independent of both OC and this project. A third
  independent implementation of the same paper-derived algorithms,
  worth reading for cross-reference.
- **This project**: `C:\Users\admin\codes\expcvm10`.
