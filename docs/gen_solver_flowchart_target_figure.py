"""
Generates docs/solver_flowchart_target.png: the TARGET design for the
Calculation Layer's equilibrium iteration -- calc.equil.EquilibriumSolverV2,
implementing Hillert's Lagrange-multiplier method (Sundman 2015 IMMI
Eq. 6-12; Sundman 2021 Calphad "Algorithm A") -- once initialization is
wired up properly, alongside its callers above (CalculationSession) and
the layer it queries every iteration (Thermodynamic System Layer /
GibbsEnergyModel).

Companion to docs/solver_flowchart_current.png (gen_solver_flowchart_
current_figure.py), which draws the SAME loop as it exists today,
including the gap this figure fixes: initialize() currently seeds every
candidate phase directly from the overall composition, bypassing
GridMinimizer entirely (drawn there as a dashed "not yet wired" note).
This figure instead draws the real target sequence, solid, as part of
the main flow:

  CalculationSession.calculateEquilibrium(T, P, compOverAll)
    -> EquilibriumSolverV2.solve(...)
         -> INITIALIZATION calls GridMinimizer.initialize(candidates, T, P, x)
              (Sundman 2015 IMMI Fig. 3-4 / 2021 Calphad Sec. 2.3.3:
               grid + lower-convex-hull search over all candidate phases)
            -> returns an EquilibriumState (stable/metastable PhaseRecords,
               each with an initial Y, amount, and stable flag)
         -> solver unpacks that EquilibriumState into its own
            stablePhases[] / phaseInternalVars[][] / phaseAmounts[]
         -> STEP 1 of the main iteration loop begins from there

This is a landscape zoom INTO the green "Calculation Layer" box of
docs/dataflow_target.png, reusing that figure's box/arrow vocabulary and
color theme, but now also drawing the two real layer boundaries either
side of it: CalculationSession feeding solve(...) in and reading the
stored EquilibriumResult back out (top), and the Thermodynamic System
Layer answering G/dG_dy/d2G_dy2/moles/dMoles_dy/isValid queries on the
GibbsEnergyModel instances, repeatedly, once per iteration (right).

The main iteration loop (STEP 1-9) is unchanged from the current-state
figure -- traced directly from the actual code:
  calc.equil.EquilibriumSolverV2.solve()'s numbered STEP 1-9 loop:
    STEP 1  evaluateAllPhases()          -- queries GibbsEnergyModel:
                                             G, dG_dy, d2G_dy2, moles, dMoles_dy
    STEP 2  buildPhaseResponses()        -- per-phase bordered Hessian +
                                             inverse -> e_ij, c_iG, c_iA
    STEP 3  buildEquilibriumMatrix()     -- global Sundman equilibrium matrix
    STEP 4  solveEquilibriumMatrix()     -- lambda_A, DeltaOmega_alpha
    STEP 5  calculateInternalCorrections() -- DeltaY_i = c_iG + sum_A c_iA*lambda_A
    STEP 6  updateState()                -- apply Y, omega, mu updates
    STEP 7  validateState()              -- queries GibbsEnergyModel.isValid(),
                                             plus sum(Y)=1, Y>=0, omega>=0, finite
    STEP 8  updateStablePhaseSet()       -- omega<0 remove; driving force>0 add
    STEP 9  checkConvergence()           -- small dmu, domega, dY -> done

Steps 1-2 are Hillert's "first step" (invert the phase matrix, Eq. 8);
steps 3-4 are the "second step" (global linear solve, Eq. 10-11); step 5
applies Eq. 9. Steps 6-9 are the update/validate/phase-management/
convergence loop back to step 1.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 21.0, 12.5
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.axis("off")

COL_SESSION = "#4a2d6b"   # same purple as CalculationSession in dataflow_target.png
COL_SYS = "#6b2d2d"       # same red as the Thermodynamic System Layer
COL_CALC = "#2d6b3d"      # same green as the Calculation Layer box
COL_STEP1 = "#1f5c8a"     # Hillert's "first step" (phase matrix)
COL_STEP2 = "#7a4a1f"     # Hillert's "second step" (global system)
COL_UPDATE = "#5a2d6b"    # update / validate / phase-set / convergence
COL_START = "#555555"     # initialization


def box(cx, cy, w, h, title, subtitle, color, title_fs=12, sub_fs=9, dashed=False):
    style = dict(boxstyle="round,pad=0.10,rounding_size=0.12",
                 linewidth=2, edgecolor=color, facecolor=color, zorder=3)
    if dashed:
        style["linestyle"] = "dashed"
        style["facecolor"] = "white"
    ax.add_patch(FancyBboxPatch((cx - w/2, cy - h/2), w, h, **style))
    text_color = color if dashed else "white"
    ax.text(cx, cy + h*0.24, title, ha="center", va="center",
            fontsize=title_fs, fontweight="bold", color=text_color, zorder=4)
    if subtitle:
        ax.text(cx, cy - h*0.18, subtitle, ha="center", va="center",
                fontsize=sub_fs, color=("#444" if dashed else "#e9e9e9"),
                family="monospace", linespacing=1.45, zorder=4)
    return dict(cx=cx, cy=cy, top=cy + h/2, bot=cy - h/2, left=cx - w/2, right=cx + w/2, w=w, h=h)


def arrow(x1, y1, x2, y2, color, lw=2.4, dashed=False, style="-|>", mscale=20):
    kw = dict(arrowstyle=style, mutation_scale=mscale, linewidth=lw, color=color,
              shrinkA=2, shrinkB=2, zorder=2)
    if dashed:
        kw["linestyle"] = "dashed"
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), **kw))


def elbow(x1, y1, x2, y2, color, lw=2.2, dashed=False, angleA=0, angleB=90):
    kw = dict(arrowstyle="-|>", mutation_scale=18, linewidth=lw, color=color,
              shrinkA=2, shrinkB=2, zorder=2,
              connectionstyle=f"angle,angleA={angleA},angleB={angleB}")
    if dashed:
        kw["linestyle"] = "dashed"
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), **kw))


def label(x, y, text, color="#333", fs=9, ha="center", style="normal"):
    ax.text(x, y, text, fontsize=fs, ha=ha, va="center", color=color, zorder=5,
            fontstyle=style,
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.9))


# ── Layout: two flanking layer columns (session top, system right),
#    the 9-step loop as a compact 3x3 grid in the middle ─────────────
LOOP_CX = 8.6      # center of the 3-column step grid
COL_W = 5.2        # width of one step-grid column
COL_GAP = 0.35
SYS_GAP = 2.2       # clear horizontal channel between the grid and System Layer
SYS_W = 4.2

# ── Title ─────────────────────────────────────────────────────────
ax.text(LOOP_CX, FIG_H - 0.4, "Calculation Layer -- Hillert/Sundman Equilibrium Iteration (TARGET)",
        ha="center", fontsize=18, fontweight="bold")
ax.text(LOOP_CX, FIG_H - 0.82,
        "calc.equil.EquilibriumSolverV2 -- Sundman 2015 IMMI Eq. 6-12 / 2021 Calphad Algorithm A "
        "-- with GridMinimizer initialization wired in",
        ha="center", fontsize=11, style="italic", color="#444")

# ── CalculationSession (top strip, spanning the loop's width) ──────
b_session = box(LOOP_CX, FIG_H - 1.7, 3 * COL_W + 2 * COL_GAP, 1.05,
                "CalculationSession",
                "calculateEquilibrium(T, P, compOverAll)",
                COL_SESSION, title_fs=13, sub_fs=9.5)

# ── INITIALIZATION row (solid, part of the real flow): GridMinimizer
#    wired into the solver's start values, replacing the naive per-
#    phase composition seed ─────────────────────────────────────────
col_x = [LOOP_CX - COL_W - COL_GAP, LOOP_CX, LOOP_CX + COL_W + COL_GAP]
init_row_h = 1.35
init_cy = b_session['bot'] - 0.75 - init_row_h / 2
b_init1 = box(col_x[0], init_cy, COL_W, init_row_h,
              "INITIALIZATION\nGridMinimizer.initialize()",
              "grid + lower convex hull over\nall candidates  (2015 Fig.3-4 /\n2021 Sec.2.3.3)",
              COL_START, title_fs=11, sub_fs=8.5)
b_init2 = box(col_x[1], init_cy, COL_W, init_row_h,
              "-> EquilibriumState",
              "stable/metastable PhaseRecords:\ninitial Y, amount, stable flag\nper candidate phase",
              COL_START, title_fs=11, sub_fs=8.5)
b_init3 = box(col_x[2], init_cy, COL_W, init_row_h,
              "Unpack into solver state",
              "stablePhases[],\nphaseInternalVars[][],\nphaseAmounts[]",
              COL_START, title_fs=11, sub_fs=8.5)
arrow(b_session['cx'], b_session['bot'], b_init1['cx'], b_init1['top'] + 0.05, COL_SESSION, lw=2.2)
arrow(b_init1['right'], init_cy, b_init2['left'], init_cy, COL_START)
arrow(b_init2['right'], init_cy, b_init3['left'], init_cy, COL_START)

# ── 3x3 step grid geometry (computed before placing System Layer,
#    which sits to its right) ───────────────────────────────────────
loop_top_y = b_init1['bot'] - 0.7
row_h = 1.9
row_gap = 0.55
row_y = [loop_top_y - row_h/2 - i * (row_h + row_gap) for i in range(3)]
grid_right_edge = col_x[2] + COL_W / 2

# ── Thermodynamic System Layer (right column, full loop height) ───
SYS_CX = grid_right_edge + SYS_GAP + SYS_W / 2
b_sys = box(SYS_CX, (row_y[0] + row_y[2]) / 2, SYS_W, row_y[0] - row_y[2] + row_h + 0.8,
            "Thermodynamic\nSystem Layer",
            "GibbsEnergyModel\ninstances (CefGibbs)\n\nG, dG/dy, d2G/dy2,\nmoles, dMoles/dy,\nisValid",
            COL_SYS, title_fs=12.5, sub_fs=9)

# solve(...) in is already drawn via the b_session -> b_init1 arrow above;
# result back out is a separate vertical arrow routed up the far right
# edge (past the INITIALIZATION row) to the top of CalculationSession
result_x = b_init3['right'] + 0.5
arrow(result_x, loop_top_y - 0.05, result_x, b_session['bot'] + 0.05, COL_CALC, lw=2.6)
arrow(result_x, b_session['bot'] + 0.05, b_init3['cx'] + 0.6, b_session['bot'], COL_CALC, lw=2.6)
label(result_x + 0.35, (b_session['bot'] + loop_top_y) / 2,
      "EquilibriumResult\n(stored, not\nreturned)", color=COL_CALC, fs=8.5, ha="left")
# init -> STEP 1 (col 0): drawn after `boxes` exists, see below

steps = [
    # (row, col, title, subtitle, color)
    (0, 0, "STEP 1\nEvaluate every\ncandidate phase",
     "G_M, G_Y, G_YY,\nM_A, dM_A/dY", COL_STEP1),
    (0, 1, "STEP 2\nBuild & invert the\nphase matrix",
     "[G_YY C^T; C 0]^-1\n-> e_ij, c_iG, c_iA  (Eq.8)", COL_STEP1),
    (0, 2, "STEP 3\nBuild the global\nequilibrium matrix",
     "G_M-Sum M_A*lam_A=0\nN_A(target)-Sum w*M_A=0", COL_STEP2),
    (1, 0, "STEP 6\nUpdate Y,\nomega, mu",
     "apply steps 4-5's\ncorrections", COL_UPDATE),
    (1, 1, "STEP 5\nSite-fraction\ncorrections",
     "DeltaY_i = c_iG\n+ Sum_A c_iA*lam_A  (Eq.9)", COL_STEP1),
    (1, 2, "STEP 4\nSolve the global\nNewton system",
     "-> lambda_A,\nDeltaOmega  (Eq.10-11)", COL_STEP2),
    (2, 0, "STEP 9\nConverged?",
     "small d(mu), d(omega),\nd(Y) and residuals", COL_UPDATE),
    (2, 1, "STEP 8\nUpdate the\nstable-phase set",
     "omega<0 -> remove\ndriving force>0 -> add  (Eq.12)", COL_UPDATE),
    (2, 2, "STEP 7\nValidate the\nupdated state",
     "Sum Y=1, Y>=0,\nomega>=0, finite", COL_UPDATE),
]

boxes = {}
for r, c, title, subtitle, color in steps:
    b = box(col_x[c], row_y[r], COL_W, row_h, title, subtitle, color,
            title_fs=11.5, sub_fs=8.5)
    boxes[(r, c)] = b

# init -> STEP 1: the real target hand-off, solid, into the main flow
arrow(b_init1['cx'], b_init1['bot'], boxes[(0, 0)]['cx'], boxes[(0, 0)]['top'], COL_START, lw=2.4)

# ── Flow arrows: the real path is a boustrophedon (snake) through the
#    numbered steps 1->2->3->4->5->6, then 6->7->8->9 down the right
#    edge before returning to 9->1. Laid out as:
#      row0:  [1] -> [2] -> [3]
#                              |
#      row1:  [6] <- [5] <- [4]
#       |
#      row2:  [9] -> [8] -> [7]
#    with 9 feeding back up to 1 (not converged) or out to the right
#    (converged).
top_h = row_h / 2 + 0.12
arrow(boxes[(0,0)]['right'], row_y[0], boxes[(0,1)]['left'], row_y[0], COL_STEP1)
arrow(boxes[(0,1)]['right'], row_y[0], boxes[(0,2)]['left'], row_y[0], COL_STEP2)
arrow(boxes[(0,2)]['cx'], boxes[(0,2)]['bot'], boxes[(1,2)]['cx'], boxes[(1,2)]['top'], COL_STEP2)
arrow(boxes[(1,2)]['left'], row_y[1], boxes[(1,1)]['right'], row_y[1], COL_STEP2)
arrow(boxes[(1,1)]['left'], row_y[1], boxes[(1,0)]['right'], row_y[1], COL_UPDATE)
arrow(boxes[(1,0)]['cx'], boxes[(1,0)]['bot'], boxes[(2,0)]['cx'], boxes[(2,0)]['top'], COL_UPDATE)
arrow(boxes[(2,0)]['right'], row_y[2], boxes[(2,1)]['left'], row_y[2], COL_UPDATE)
arrow(boxes[(2,1)]['right'], row_y[2], boxes[(2,2)]['left'], row_y[2], COL_UPDATE)

label(boxes[(0,1)]['cx'], row_y[0] + top_h + 0.25, "per phase", color=COL_STEP1, fs=8.5, style="italic")
label(boxes[(1,1)]['cx'], row_y[1] + top_h + 0.25, "across all stable phases", color=COL_STEP2, fs=8.5, style="italic")

# Note: the numbered flow already shows 4 -> 5 -> 6 -> 7 -> 8 -> 9 via
# the row arrows above/below; just caption the significance of 7 here.
label(boxes[(2,1)]['cx'], row_y[2] - row_h/2 - 0.35,
      "STEP 7 gates STEP 9: an invalid state (off the constraint manifold,\n"
      "negative Y, or omega<0) is a hard failure here, not damped",
      color="#777", fs=8, style="italic")

# STEP 9 -> STEP 1 (not converged): loop back up the far left edge
fb_x = col_x[0] - COL_W/2 - 0.9
elbow(boxes[(2,0)]['left'], row_y[2], fb_x, row_y[2], COL_UPDATE)
elbow(fb_x, row_y[2], fb_x, row_y[0], COL_UPDATE)
arrow(fb_x, row_y[0], boxes[(0,0)]['left'], row_y[0], COL_UPDATE)
label(fb_x - 0.05, (row_y[0] + row_y[2]) / 2, "no --\nnext\niteration", color=COL_UPDATE, fs=9, style="italic")

# (GridMinimizer is now drawn solid, as part of the main flow, in the
# INITIALIZATION row above STEP 1 -- see b_init1/b_init2/b_init3.)

# ── STEP 9 -> RESULT (converged): straight drop below STEP 9's own
#    column, then the RESULT box spans the full grid width ─────────
b_result_y = row_y[2] - row_h/2 - 1.65
b_result = box(LOOP_CX, b_result_y, 3 * COL_W + 2 * COL_GAP, 0.95,
               "RESULT", "converged=true/false, phase amounts, Y, mu -> CalculationSession",
               COL_CALC, title_fs=11.5, sub_fs=8.5)
arrow(boxes[(2,0)]['cx'], boxes[(2,0)]['bot'], boxes[(2,0)]['cx'], b_result['top'], COL_CALC)
label(boxes[(2,0)]['cx'] + 0.75, (boxes[(2,0)]['bot'] + b_result['top']) / 2,
      "yes", color=COL_CALC, fs=9.5)

# ── System Layer <-> STEP 1 / STEP 7 : the repeated model-query loop ─
q_y_hi = row_y[0] + 0.35
q_y_lo = row_y[2] - 0.35
arrow(boxes[(0,2)]['right'], row_y[0] + 0.35, b_sys['left'], row_y[0] + 0.35, COL_SYS, lw=2.6)
arrow(b_sys['left'], row_y[0] - 0.35, boxes[(0,2)]['right'], row_y[0] - 0.35, COL_STEP1, lw=2.6)
label((boxes[(0,2)]['right'] + b_sys['left']) / 2, row_y[0] + 0.75,
      "query per phase\n(T, y) ->", color=COL_SYS, fs=8)
label((boxes[(0,2)]['right'] + b_sys['left']) / 2, row_y[0] - 0.75,
      "<- G, dG/dy, d2G/dy2,\nmoles, dMoles/dy", color=COL_STEP1, fs=8)

arrow(boxes[(2,2)]['right'], row_y[2], b_sys['left'], row_y[2], COL_UPDATE, lw=2.2, dashed=True)
label((boxes[(2,2)]['right'] + b_sys['left']) / 2, row_y[2] + 0.4,
      "isValid(y) ->", color=COL_UPDATE, fs=8, style="italic")

ax.text(b_sys['cx'], b_sys['bot'] - 0.5,
        "queried many times per solve() call --\nonce per iteration, per candidate phase",
        ha="center", fontsize=8.5, style="italic", color="#555", linespacing=1.4)

# ── Scope footnote ───────────────────────────────────────────────
ax.text(LOOP_CX, b_result['bot'] - 0.55,
        "Current scope: fixed T, P, and overall composition per solve() "
        "(EquilibriumSolverV2's own class doc: variable T/P and additional external constraints are a planned extension, not yet implemented)",
        ha="center", fontsize=9, style="italic", color="#555", linespacing=1.4)

# ── Legend ────────────────────────────────────────────────────────
legend = [
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SESSION, markersize=14, label='CalculationSession'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SYS, markersize=14, label='Thermodynamic System Layer'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_START, markersize=14, label='Initialization (GridMinimizer)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_STEP1, markersize=14, label="Hillert's step 1 -- per-phase matrix"),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_STEP2, markersize=14, label="Hillert's step 2 -- global system"),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_UPDATE, markersize=14, label='Update / validate / phase-set / converge'),
]

# ── Fit the axes to actual content, then place the legend below it ──
all_y = [FIG_H - 0.15] + [b['bot'] for b in boxes.values()] + [b_sys['bot'], b_result['bot']]
all_x = [b_sys['right'] + 0.3, fb_x - 0.3]
ax.set_xlim(min(all_x) - 0.2, max(all_x) + 0.2)
ax.set_ylim(min(all_y) - 1.7, max(all_y) + 0.2)

ax.legend(handles=legend, loc="lower center", bbox_to_anchor=(0.5, 0.0),
          ncol=3, fontsize=9.5, frameon=False)

plt.subplots_adjust(left=0.02, right=0.99, top=0.99, bottom=0.02)
plt.savefig("docs/solver_flowchart_target.png", dpi=170, facecolor="white")
print("Saved docs/solver_flowchart_target.png")
