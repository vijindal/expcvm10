"""
Generates docs/dataflow_current.png: the current (as-is) core data flow
of expCVM10, annotated with actual class/file names, following the
three-layer structure described in 3layer_architecture.md
(UI Layer / Thermodynamic System Layer / Calculation Layer).

Layout strategy: lay out every row using absolute, hand-picked y
coordinates from TOP to BOTTOM of the figure (no aspect-lock, no
tight_layout surprises), then draw layer background bands to fit
around the rows that belong to them -- band geometry is DERIVED from
row geometry, not the other way around, so nothing can drift out of
its band.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 16, 13.9
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_UI, COL_SYS, COL_CALC, COL_WARN = "#2d4a6b", "#6b2d2d", "#2d6b3d", "#555555"
TXT = "#eeeeee"

def box(x, y, w, h, title, lines, color, fontsize=7.6, title_fs=9.0, dashed=False, alpha=1.0):
    patch = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.09,rounding_size=0.07",
                            linewidth=1.3, edgecolor=color, facecolor=color,
                            alpha=alpha, linestyle="dashed" if dashed else "solid", zorder=3)
    ax.add_patch(patch)
    ax.text(x + w/2, y + h - 0.20, title, ha="center", va="top",
            fontsize=title_fs, fontweight="bold", color="white", zorder=4)
    if lines:
        ax.text(x + w/2, y + (h - 0.35)/2, "\n".join(lines), ha="center", va="center",
                fontsize=fontsize, color=TXT, linespacing=1.55, family="monospace", zorder=4)
    return dict(x=x, y=y, w=w, h=h, cx=x + w/2, cy=y + h/2, top=y + h, bot=y, left=x, right=x + w)

def arrow(p1, p2, color="#333", lw=1.4, cs="arc3,rad=0.0", label=None, lp=0.5, fs=6.8, dashed=False):
    a = FancyArrowPatch(p1, p2, arrowstyle="-|>", mutation_scale=12, linewidth=lw,
                         color=color, connectionstyle=cs, linestyle="dashed" if dashed else "solid",
                         shrinkA=3, shrinkB=3, zorder=2)
    ax.add_patch(a)
    if label:
        lx, ly = p1[0] + (p2[0]-p1[0])*lp, p1[1] + (p2[1]-p1[1])*lp
        ax.text(lx, ly, label, fontsize=fs, ha="center", va="center", color=color, zorder=5,
                bbox=dict(boxstyle="round,pad=0.12", fc="white", ec=color, lw=0.5, alpha=0.95))

def band(x0, x1, y0, y1, label, color):
    ax.add_patch(plt.Rectangle((x0, y0), x1 - x0, y1 - y0, facecolor=color, alpha=0.055,
                                edgecolor=color, linewidth=1.2, linestyle="dotted", zorder=0))
    ax.text(x0 + 0.15, y1 + 0.1, label, fontsize=11.5, fontweight="bold", color=color, zorder=1,
            ha="left", va="bottom")

# ── Title ──────────────────────────────────────────────────────────
ax.text(FIG_W/2, FIG_H - 0.3, "expCVM10 -- Core Data Flow (current / as-is)",
        ha="center", fontsize=16, fontweight="bold", zorder=6)
ax.text(FIG_W/2, FIG_H - 0.62,
        "UI -> Thermodynamic System Layer -> Calculation Layer -> UI   (per 3layer_architecture.md)",
        ha="center", fontsize=10, style="italic", color="#444", zorder=6)

# ══════════════════════════════════════════════════════════════════
# Absolute row y-positions, TOP -> BOTTOM
# ══════════════════════════════════════════════════════════════════
rowA_y = 11.35   # UI: MainController / requests / results
rowB_y = 10.35   # UI: use cases
rowC_y = 7.95    # SYS: TdbParser / PhaseModelFactory / Rk-Cef factories
rowD_y = 6.75    # SYS: candidates list
rowE_y = 4.55    # CALC: solver / tracer / stepper
rowF_y = 2.95    # CALC: two-way callout + V2 box

# ── Bands (derived to wrap their rows with margin) ──────────────────
band(0.15, FIG_W - 0.15, rowB_y - 0.25, rowA_y + 0.8 + 0.25, "1. UI LAYER   (src/ui/)", COL_UI)
band(0.15, FIG_W - 0.15, rowD_y - 0.25, rowC_y + 1.15 + 0.25, "2. THERMODYNAMIC SYSTEM LAYER   (src/system/)  -- rebuilt from scratch on EVERY call", COL_SYS)
band(0.15, FIG_W - 0.15, 0.9, rowE_y + 1.15 + 0.25, "3. CALCULATION LAYER   (src/calc/)", COL_CALC)

# ══════════════════════════════════════════════════════════════════
# ROW A -- UI entry + DTOs
# ══════════════════════════════════════════════════════════════════
b_gui  = box(0.5,  rowA_y, 3.5, 0.8, "ui/gui/MainController.java",
             ["runSinglePoint(...) /", "runPhaseDiagram(req)"], COL_UI)
b_req  = box(4.3,  rowA_y, 3.5, 0.8, "ui/request/*Request.java",
             ["CalculationRequest,", "PhaseDiagramRequest"], COL_UI)
b_res1 = box(8.1,  rowA_y, 3.5, 0.8, "system.ports.EquilibriumResult",
             ["returned to GUI AS-IS", "(no UI DTO)"], COL_UI)
b_res2 = box(11.9, rowA_y, 3.6, 0.8, "ui/result/PhaseDiagramResult.java",
             ["LineSegment, NodePoint", "(render-ready)"], COL_UI)

# ══════════════════════════════════════════════════════════════════
# ROW B -- Use cases
# ══════════════════════════════════════════════════════════════════
b_uc1 = box(0.5, rowB_y, 5.7, 0.55, "ui/layer/EquilibriumUseCase.java",
            ["execute(CalculationRequest)"], COL_UI, fontsize=7.4, title_fs=8.4)
b_uc2 = box(6.4, rowB_y, 5.7, 0.55, "ui/layer/PhaseDiagramUseCase.java",
            ["execute(PhaseDiagramRequest)"], COL_UI, fontsize=7.4, title_fs=8.4)
b_uc3 = box(12.3, rowB_y, 3.2, 0.55, "StepCalculationUseCase.java",
            ["(delegates)"], COL_UI, fontsize=6.8, title_fs=7.4)

# ══════════════════════════════════════════════════════════════════
# ROW C -- Database + factories
# ══════════════════════════════════════════════════════════════════
b_tdb = box(0.5,  rowC_y, 5.0, 1.15, "system/database/TdbParser.java",
            ["implements DatabasePort", "load(path) -> extractSystem(elements)"], COL_SYS)
b_pmf = box(5.8,  rowC_y, 5.0, 1.15, "system/model/PhaseModelFactory.java",
            ["build(phaseName, database, elements,...)", "-> PhaseModelFactory.PhaseModel"], COL_SYS)
b_facs = box(11.1, rowC_y, 4.4, 1.15, "RkPhaseModelFactory / CefPhaseModelAdapter",
             ["RkGibbs (1-sublattice)", "CefPhaseModelAdapter (multi-sublattice)"], COL_SYS, fontsize=7.2)

# ══════════════════════════════════════════════════════════════════
# ROW D -- candidates list
# ══════════════════════════════════════════════════════════════════
b_gem = box(3.0, rowD_y, 8.5, 0.8, "List<GibbsEnergyModel> candidates",
            ["bare ArrayList -- built inline in EACH use case, never cached, never shared"],
            COL_SYS, fontsize=7.6, title_fs=8.6)

# ══════════════════════════════════════════════════════════════════
# ROW E -- Calculation Layer
# ══════════════════════════════════════════════════════════════════
b_solver = box(0.5,  rowE_y, 5.0, 1.15, "calc/equil/EquilibriumSolver.java",
               ["solve(T, P, comp[], candidates)", "-> EquilibriumResult"], COL_CALC)
b_tracer = box(5.8,  rowE_y, 5.0, 1.15, "calc/diagram/DiagramTracer.java",
               ["calculate(candidates, axes, startAxes,", "fixedT, fixedP, comp) -> PhaseDiagram"], COL_CALC, fontsize=7.2)
b_stepper = box(11.1, rowE_y, 4.4, 1.15, "LineStepper / PhaseChangeHandler",
                ["reuse the SAME candidates list by", "reference across many solver calls"], COL_CALC, fontsize=7.2)

# ══════════════════════════════════════════════════════════════════
# ROW F -- two-way callout + V2 (off main path)
# ══════════════════════════════════════════════════════════════════
ax.add_patch(FancyBboxPatch((0.5, rowF_y), 9.9, 1.15, boxstyle="round,pad=0.08,rounding_size=0.07",
                             linewidth=1.4, edgecolor="#8a6d00", facecolor="#fff6d8", zorder=2))
ax.text(0.75, rowF_y + 0.90, "Two-way interaction -- Sundman et al. 2021, Algorithm A (Fig. 1), Eq. (6)-(7):",
        fontsize=8.2, fontweight="bold", color="#6b5400", zorder=3)
ax.text(0.75, rowF_y + 0.58,
        "solver needs per phase, per iteration: G^a_M(T,P,y), M^a_A(y) (moles of A per f.u., Eq. 2), and dG/dy, d2G/dy2, dM/dy",
        fontsize=7.0, color="#4a3b00", family="monospace", zorder=3)
ax.text(0.75, rowF_y + 0.24,
        "-> model.siteEnergy/siteGradient/siteHessian(T,y) + elementAmounts/elementAmountsJacobian(y)  [only EquilibriumSolverV2 uses this contract]",
        fontsize=6.7, color="#4a3b00", family="monospace", zorder=3)

b_v2 = box(11.1, rowF_y, 4.4, 0.95, "calc/equil/EquilibriumSolverV2.java",
           ["NOT wired to any use case", "(Sundman/V2, CEF-only, test-only)"], COL_WARN,
           fontsize=7.2, title_fs=8.2, dashed=True, alpha=0.8)

# ══════════════════════════════════════════════════════════════════
# ARROWS
# ══════════════════════════════════════════════════════════════════

# UI: MainController -> requests -> use cases
arrow((b_gui['right'], b_gui['cy']), (b_req['left'], b_req['cy']), COL_UI, label="builds")
arrow((b_req['cx']-0.8, b_req['bot']), (b_uc1['cx']+1.2, b_uc1['top']), COL_UI, cs="arc3,rad=0.15")
arrow((b_req['cx']+0.8, b_req['bot']), (b_uc2['cx']-1.2, b_uc2['top']), COL_UI, cs="arc3,rad=-0.15")
arrow((b_uc2['right']-0.3, b_uc2['cy']), (b_uc3['left'], b_uc3['cy']), COL_UI, lw=1.1,
      label="delegates to new\nPhaseDiagramUseCase()", fs=6.0)

# use cases -> TdbParser (step 1, showing duplication)
arrow((b_uc1['cx']-0.6, b_uc1['bot']), (b_tdb['cx']-0.9, b_tdb['top']), COL_SYS,
      label="1  new TdbParser();\nparser.load(path)", fs=6.4, lp=0.85)
arrow((b_uc2['cx']+0.3, b_uc2['bot']), (b_tdb['cx']+0.9, b_tdb['top']), COL_SYS, cs="arc3,rad=0.25",
      label="1  new TdbParser()\n(DUPLICATE)", fs=6.4, lp=0.8)

# TdbParser -> PhaseModelFactory -> Rk/Cef factories (steps 2,3)
arrow((b_tdb['right'], b_tdb['cy']), (b_pmf['left'], b_pmf['cy']), COL_SYS, label="2,3", fs=7.0)
arrow((b_pmf['right'], b_pmf['cy']), (b_facs['left'], b_facs['cy']), COL_SYS)

# factories -> candidates list
arrow((b_pmf['cx']-0.6, b_pmf['bot']), (b_gem['cx']-1.5, b_gem['top']), COL_SYS, cs="arc3,rad=0.1")
arrow((b_facs['cx']-0.5, b_facs['bot']), (b_gem['cx']+1.5, b_gem['top']), COL_SYS, cs="arc3,rad=-0.1")

# candidates -> calculation layer (step 4)
arrow((b_gem['cx']-2.0, b_gem['bot']), (b_solver['cx'], b_solver['top']), COL_CALC, label="4  candidates")
arrow((b_gem['cx']+1.5, b_gem['bot']), (b_tracer['cx'], b_tracer['top']), COL_CALC, label="4  candidates")

# tracer -> stepper -> solver (reuse)
arrow((b_tracer['right'], b_tracer['cy']+0.25), (b_stepper['left'], b_stepper['cy']+0.25), COL_CALC,
      label="calls per\nstep/iter", fs=6.0)
arrow((b_stepper['cx']-1.0, b_stepper['bot']), (b_solver['right']+0.1, b_solver['cy']-0.3), COL_CALC,
      cs="arc3,rad=0.2", label="solve() per iteration\n(legacy evaluateG/gradient/hessian(x,T) contract)", fs=5.8, lp=0.42)

# NOTE: the siteEnergy/siteGradient/elementAmounts Algorithm-A contract is
# used by EquilibriumSolverV2, NOT the legacy EquilibriumSolver -- draw the
# callout link from V2, dashed, matching V2's own "not wired in" styling.
arrow((b_v2['left'], b_v2['cy']), (0.5 + 9.9, rowF_y + 0.6), "#8a6d00", lw=1.2,
      cs="arc3,rad=0.15", dashed=True)

# solver -> result -> use case -> GUI (left spine)
spine_x = 0.35
arrow((b_solver['left'], b_solver['cy']), (spine_x, b_solver['cy']), COL_CALC, lw=1.2)
arrow((spine_x, b_solver['cy']), (spine_x, b_uc1['cy']), COL_CALC, lw=1.2,
      label="EquilibriumResult", fs=6.6)
arrow((spine_x, b_uc1['cy']), (b_uc1['left'], b_uc1['cy']), COL_CALC, lw=1.2)
arrow((b_uc1['cx']-0.5, b_uc1['top']), (b_res1['cx']-1.0, b_res1['bot']), COL_UI, cs="arc3,rad=-0.2")
arrow((b_res1['cx']-0.5, b_res1['top']), (b_gui['cx']+0.7, b_gui['bot']), COL_UI, cs="arc3,rad=-0.25",
      label="returned unconverted", fs=6.4)

# tracer -> PhaseDiagram -> use case -> convert -> PhaseDiagramResult -> GUI
arrow((b_tracer['cx']+0.8, b_tracer['top']), (b_uc2['cx']+0.3, b_uc2['bot']), COL_CALC,
      cs="arc3,rad=-0.35", label="PhaseDiagram", fs=6.2, lp=0.18)
arrow((b_uc2['cx']+0.5, b_uc2['top']), (b_res2['cx']-0.3, b_res2['bot']), COL_UI, cs="arc3,rad=0.25",
      label="5  convert(diagram,\nrequest, candidates)", fs=6.2, lp=0.6)
arrow((b_res2['cx'], b_res2['bot']), (b_uc2['cx']+1.5, b_uc2['top']), COL_UI, cs="arc3,rad=0.35",
      label="displayed by GUI", fs=6.2)

# stepper -> V2 (dashed, off main path)
arrow((b_stepper['cx'], b_stepper['bot']), (b_v2['cx'], b_v2['top']), COL_WARN, dashed=True, lw=1.0)

# ── Legend ───────────────────────────────────────────────────────
legend_items = [
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_UI, markersize=14, label='UI Layer (src/ui/)'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_SYS, markersize=14,
           label='Thermodynamic System Layer (src/system/) -- gap: rebuilt every call, never cached'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_CALC, markersize=14, label='Calculation Layer (src/calc/)'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_WARN, markersize=14, label='Exists but NOT wired into production flow (dashed)'),
]
ax.legend(handles=legend_items, loc="lower center", bbox_to_anchor=(0.5, -0.02),
          ncol=2, fontsize=8.6, frameon=False)

plt.subplots_adjust(left=0.01, right=0.99, top=0.99, bottom=0.06)
plt.savefig("docs/dataflow_current.png", dpi=170, facecolor="white")
print("Saved docs/dataflow_current.png")
