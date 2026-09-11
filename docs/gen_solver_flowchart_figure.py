"""
Generates docs/solver_flowchart.png: a Sundman-Figure-1-style flowchart of
the Calculation Layer's actual iteration -- calc.equil.EquilibriumSolverV2,
implementing Hillert's Lagrange-multiplier method (Sundman 2015 IMMI
Eq. 6-12; Sundman 2021 Calphad "Algorithm A").

This is a zoom INTO the green "Calculation Layer" box of
docs/dataflow_target.png: same box/arrow/label vocabulary and color
theme (COL_CALC), but now showing the solver's own internal steps
rather than its interface to the System Layer.

Traced directly from EquilibriumSolverV2.solve()'s actual numbered
STEP 1-9 loop (calc/equil/EquilibriumSolverV2.java), not just the paper:
  STEP 1  evaluateAllPhases()          -- G, G_Y, G_YY, G_YT, M_A, dM_A/dY
  STEP 2  buildPhaseResponses()        -- per-phase bordered Hessian +
                                           inverse -> e_ij, c_iG, c_iA
  STEP 3  buildEquilibriumMatrix()     -- global Sundman equilibrium matrix
  STEP 4  solveEquilibriumMatrix()     -- lambda_A, DeltaOmega_alpha
  STEP 5  calculateInternalCorrections() -- DeltaY_i = c_iG + sum_A c_iA*lambda_A
  STEP 6  updateState()                -- apply Y, omega, mu updates
  STEP 7  validateState()              -- sum(Y)=1, Y>=0, omega>=0, finite
  STEP 8  updateStablePhaseSet()       -- omega<0 remove; driving force>0 add
  STEP 9  checkConvergence()           -- small dmu, domega, dY -> done

Steps 1-2 are Hillert's "first step" (invert the phase matrix, Eq. 8);
steps 3-4 are the "second step" (global linear solve, Eq. 10-11); step 5
applies Eq. 9. Steps 6-9 are the update/validate/phase-management/
convergence loop back to step 1, matching Sundman 2015 Fig. 3-4's
external grid-minimizer start-value step feeding this same iteration.

Also shown: initialize() currently seeds every candidate phase from the
overall composition directly (not yet through GridMinimizer, which
exists in the same package but is not wired into this solver's start
values) -- drawn as a dashed "not yet wired" link, an honest reflection
of the codebase today rather than the aspirational Sundman picture.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 15.0, 21.5
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_CALC = "#2d6b3d"      # same green as the Calculation Layer box
COL_STEP1 = "#1f5c8a"     # Hillert's "first step" (phase matrix)
COL_STEP2 = "#7a4a1f"     # Hillert's "second step" (global system)
COL_UPDATE = "#5a2d6b"    # update / validate / phase-set / convergence
COL_START = "#555555"     # initialization
CX = FIG_W / 2 + 0.9      # shifted right, leaving a left margin for the
                          # feedback loop and GridMinimizer side-note


def box(cx, cy, w, h, title, subtitle, color, title_fs=13, sub_fs=9.5, dashed=False):
    style = dict(boxstyle="round,pad=0.10,rounding_size=0.12",
                 linewidth=2, edgecolor=color, facecolor=color, zorder=3)
    if dashed:
        style["linestyle"] = "dashed"
        style["facecolor"] = "white"
    ax.add_patch(FancyBboxPatch((cx - w/2, cy - h/2), w, h, **style))
    text_color = color if dashed else "white"
    ax.text(cx, cy + h*0.22, title, ha="center", va="center",
            fontsize=title_fs, fontweight="bold", color=text_color, zorder=4)
    if subtitle:
        ax.text(cx, cy - h*0.20, subtitle, ha="center", va="center",
                fontsize=sub_fs, color=("#444" if dashed else "#e9e9e9"),
                family="monospace", linespacing=1.5, zorder=4)
    return dict(cx=cx, cy=cy, top=cy + h/2, bot=cy - h/2, left=cx - w/2, right=cx + w/2)


def varrow(x, y1, y2, color, lw=2.4, dashed=False, style="-|>"):
    kw = dict(arrowstyle=style, mutation_scale=20, linewidth=lw, color=color,
              shrinkA=2, shrinkB=2, zorder=2)
    if dashed:
        kw["linestyle"] = "dashed"
    ax.add_patch(FancyArrowPatch((x, y1), (x, y2), **kw))


def elbow(x1, y1, x2, y2, color, lw=2.2, dashed=False):
    """Right-angle connector: down/up then across, for feedback loops."""
    kw = dict(arrowstyle="-|>", mutation_scale=18, linewidth=lw, color=color,
              shrinkA=2, shrinkB=2, zorder=2, connectionstyle="angle,angleA=0,angleB=90")
    if dashed:
        kw["linestyle"] = "dashed"
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), **kw))


def label(x, y, text, color="#333", fs=9.5, ha="center", style="normal"):
    ax.text(x, y, text, fontsize=fs, ha=ha, va="center", color=color, zorder=5,
            fontstyle=style,
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.9))


# ── Title ─────────────────────────────────────────────────────────
ax.text(CX, FIG_H - 0.5, "Calculation Layer -- Hillert/Sundman Equilibrium Iteration",
        ha="center", fontsize=18.5, fontweight="bold")
ax.text(CX, FIG_H - 1.0,
        "calc.equil.EquilibriumSolverV2 -- Sundman 2015 IMMI Eq. 6-12 / 2021 Calphad Algorithm A",
        ha="center", fontsize=11.5, style="italic", color="#444")

GAP = 0.75  # vertical clearance between boxes, center-to-edge, holding the arrow
y = FIG_H - 2.0


def place(w, h, title, subtitle, color, **kw):
    """Place a box with its top edge at the current cursor, then advance
    the cursor past its bottom edge. Returns the box's geometry dict."""
    global y
    cy = y - h / 2
    b = box(CX, cy, w, h, title, subtitle, color, **kw)
    y = b['bot']
    return b


def drop(color, gap=GAP, lw=2.4, dashed=False):
    """Draw a down-arrow spanning the current gap, then advance the
    cursor to the bottom of that gap (the top of the next box)."""
    global y
    top = y
    y = y - gap
    varrow(CX, top, y, color, lw=lw, dashed=dashed)


# ── Start values ─────────────────────────────────────────────────
b_start = place(8.6, 1.0, "START VALUES",
                "candidate phases seeded from overall composition",
                COL_START, title_fs=13, sub_fs=9)

grid_cx = 1.9
grid_cy = b_start['bot'] - 0.75
b_grid = box(grid_cx, grid_cy, 2.9, 1.15,
             "GridMinimizer",
             "exists (calc/equil) --\nnot yet wired into\nthese start values",
             COL_START, title_fs=10, sub_fs=8, dashed=True)
elbow(b_start['left'] + 0.25, b_start['bot'], b_grid['right'] + 0.05, b_grid['top'] + 0.05,
      "#999", lw=1.8, dashed=True)
label(grid_cx, b_grid['top'] + 0.4, "Sundman 2015 Fig. 3-4:\nstart-value grid search",
      color="#777", fs=8, style="italic")

drop("#333")

# ── Main iteration loop label ───────────────────────────────────
ax.text(CX + 6.1, y - 3.9, "MAIN\nITERATION\nLOOP", fontsize=11, fontweight="bold",
        ha="center", va="center", color=COL_CALC, linespacing=1.3)

# ── STEP 1: evaluate phases ──────────────────────────────────────
b1 = place(9.6, 1.15, "STEP 1 -- Evaluate every candidate phase",
           "G_M^a,  G_Y^a,  G_YY^a,  G_YT^a,  M_A^a,  dM_A^a/dY_i   (GibbsEnergyModel)",
           COL_STEP1)
drop(COL_STEP1)

# ── STEP 2: phase matrix ────────────────────────────────────────
b2 = place(9.6, 1.5, "STEP 2 -- Build & invert the phase matrix  (Hillert's step 1)",
           "[ G_YY  C^T ]\n[  C    0  ]^-1  ->  e_ij,  c_iG,  c_iA          (Eq. 8)",
           COL_STEP1)
label(CX + 6.1, (b1['bot'] + b2['top']) / 2, "per\nphase", color=COL_STEP1, fs=8.5, style="italic")
drop(COL_STEP1)

# ── STEP 3: global equilibrium matrix ───────────────────────────
b3 = place(9.6, 1.15, "STEP 3 -- Build the global equilibrium matrix  (Hillert's step 2)",
           "G_M^a - Sum_A M_A^a lambda_A = 0   and   N_A(target) - Sum_a omega^a M_A^a = 0",
           COL_STEP2)
drop(COL_STEP2)

# ── STEP 4: solve global system ─────────────────────────────────
b4 = place(9.6, 1.05, "STEP 4 -- Solve the global Newton system",
           "->  lambda_A  (new chemical potentials),   DeltaOmega^a          (Eq. 10-11)",
           COL_STEP2)
label(CX + 6.1, (b3['bot'] + b4['top']) / 2, "across all\nstable phases", color=COL_STEP2, fs=8.5, style="italic")
drop(COL_STEP2)

# ── STEP 5: internal corrections ────────────────────────────────
b5 = place(9.6, 1.05, "STEP 5 -- Site-fraction corrections",
           "DeltaY_i^a = c_iG^a + Sum_A c_iA^a * lambda_A                        (Eq. 9)",
           COL_STEP1)
drop(COL_UPDATE)

# ── STEP 6: update state ────────────────────────────────────────
b6 = place(9.6, 0.95, "STEP 6 -- Update Y, omega^a, mu_A",
           "apply the corrections from steps 4-5 to the current state",
           COL_UPDATE)
drop(COL_UPDATE)

# ── STEP 7: validate ─────────────────────────────────────────────
b7 = place(9.6, 0.95, "STEP 7 -- Validate the updated state",
           "Sum_i Y_i,s = 1,   Y_i >= 0,   omega^a >= 0,   all finite",
           COL_UPDATE)
drop(COL_UPDATE)

# ── STEP 8: phase set management ────────────────────────────────
b8 = place(9.6, 1.15, "STEP 8 -- Update the stable-phase set",
           "omega^a < 0  ->  remove a          driving force c_w > 0  ->  add w    (Eq. 12)",
           COL_UPDATE)
drop(COL_UPDATE)

# ── STEP 9: convergence check + branch ──────────────────────────
b9 = place(7.4, 1.15, "STEP 9 -- Converged?",
           "small  Delta(mu),  Delta(omega),  Delta(Y)  and residuals",
           COL_UPDATE, title_fs=13)

# feedback: not converged -> back to STEP 1. Routed at x=0.35, clear of
# both STEP 1's own left edge and the GridMinimizer side-note above it.
fb_x = 0.35
elbow(b9['left'], b9['cy'], fb_x, b9['cy'], COL_UPDATE, lw=2.2)
elbow(fb_x, b9['cy'], fb_x, b1['cy'], COL_UPDATE, lw=2.2)
ax.add_patch(FancyArrowPatch((fb_x, b1['cy']), (b1['left'], b1['cy']), arrowstyle="-|>",
             mutation_scale=18, linewidth=2.2, color=COL_UPDATE, shrinkA=2, shrinkB=2, zorder=2))
label(fb_x + 0.65, (b1['cy'] + b9['cy']) / 2 - 3.0, "no --\nnext\niteration",
      color=COL_UPDATE, fs=9, style="italic")

# converged -> result, down and out
drop(COL_CALC, gap=1.0)
label(CX + 2.0, y + 0.5, "yes", color=COL_CALC, fs=9.5)

b_out = place(8.6, 1.0, "RESULT",
              "phase amounts + constitutions + chemical potentials -> System/Session layers",
              COL_CALC, title_fs=13, sub_fs=9)

# ── side note: fixed T, P scope ─────────────────────────────────
ax.text(CX, b_out['bot'] - 0.75,
        "Current scope: fixed T, P, and overall composition per solve()\n"
        "(EquilibriumSolverV2's own class doc: variable T/P and additional\n"
        "external constraints are a planned extension, not yet implemented)",
        ha="center", fontsize=10, style="italic", color="#555", linespacing=1.5)

# Set the axes' y-limits now that we know the true content extent, so
# the legend (placed in axes-fraction coords via bbox_to_anchor) lands
# in the clear space actually reserved below the footnote, instead of
# a fixed-height guess that may over- or under-shoot.
bottom_y = b_out['bot'] - 2.3
ax.set_ylim(bottom_y, FIG_H)

# ── Legend ────────────────────────────────────────────────────────
legend = [
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_START, markersize=15, label='Start values / initialization'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_STEP1, markersize=15, label="Hillert's step 1 -- per-phase matrix"),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_STEP2, markersize=15, label="Hillert's step 2 -- global system"),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_UPDATE, markersize=15, label='Update / validate / phase-set / converge'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_CALC, markersize=15, label='Calculation Layer boundary (start / result)'),
]
ax.legend(handles=legend, loc="lower center", bbox_to_anchor=(0.5, 0.0),
          ncol=2, fontsize=10, frameon=False)

plt.subplots_adjust(left=0.04, right=0.96, top=0.985, bottom=0.02)
plt.savefig("docs/solver_flowchart.png", dpi=170, facecolor="white")
print("Saved docs/solver_flowchart.png")
