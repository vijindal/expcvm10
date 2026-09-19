"""
Generates docs/dataflow_target.png: the TARGET core data flow of expCVM10
with ApplicationLayer as the UI-agnostic coordinator (the "4th layer").

Kept deliberately simple -- a clean vertical stack matching the ASCII
diagram in README.md "Structure":

  UI  <->  ApplicationLayer  <->  { System Layer , Calculation Layer }

- UI <-> ApplicationLayer: one bidirectional arrow. The UI never talks
  to the System or Calculation layers directly. Both browsing (list a
  TDB's databases / elements / phases) and calculating go through the
  session; results and browse lists come back the same way.
- ApplicationLayer -> System Layer: setModel(...) builds the
  GibbsEnergyModel[] once, then it is reused.
- ApplicationLayer <-> System Layer: browse -- a lightweight, two-way
  query path (request down, lists back up), no model build.
- ApplicationLayer <-> Calculation Layer: calculate(...) down, result
  back to the session (which the UI then reads).
- System Layer <-> Calculation Layer: a real, repeated two-way loop
  within one calculate(...) call -- many (T, y) queries per solve.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 13.0, 12.0
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_UI, COL_SESSION, COL_SYS, COL_CALC = "#2d4a6b", "#4a2d6b", "#6b2d2d", "#2d6b3d"
CX = FIG_W / 2


def box(cx, cy, w, h, title, subtitle, color, title_fs=16, sub_fs=11):
    ax.add_patch(FancyBboxPatch(
        (cx - w/2, cy - h/2), w, h,
        boxstyle="round,pad=0.12,rounding_size=0.14",
        linewidth=2, edgecolor=color, facecolor=color, zorder=3))
    ax.text(cx, cy + h*0.20, title, ha="center", va="center",
            fontsize=title_fs, fontweight="bold", color="white", zorder=4)
    ax.text(cx, cy - h*0.18, subtitle, ha="center", va="center",
            fontsize=sub_fs, color="#e9e9e9", family="monospace",
            linespacing=1.55, zorder=4)
    return dict(cx=cx, cy=cy, top=cy + h/2, bot=cy - h/2, left=cx - w/2, right=cx + w/2)


def varrow(x, y1, y2, color, lw=2.6):
    ax.add_patch(FancyArrowPatch((x, y1), (x, y2), arrowstyle="-|>",
                 mutation_scale=24, linewidth=lw, color=color,
                 shrinkA=2, shrinkB=2, zorder=2))


def harrow(x1, x2, y, color, lw=2.6):
    ax.add_patch(FancyArrowPatch((x1, y), (x2, y), arrowstyle="-|>",
                 mutation_scale=22, linewidth=lw, color=color,
                 shrinkA=2, shrinkB=2, zorder=2))


def label(x, y, text, color="#333", fs=11, ha="center"):
    ax.text(x, y, text, fontsize=fs, ha=ha, va="center", color=color, zorder=5,
            bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none", alpha=0.9))


# ── Title ─────────────────────────────────────────────────────────
ax.text(CX, FIG_H - 0.45, "expCVM10 -- Target Core Data Flow",
        ha="center", fontsize=21, fontweight="bold")
ax.text(CX, FIG_H - 0.95, "UI  <->  ApplicationLayer  <->  System + Calculation Layers",
        ha="center", fontsize=13, style="italic", color="#444")

# ── Boxes: UI and ApplicationLayer stacked; System + Calculation side by side ──
b_ui = box(CX, 10.0, 9.2, 1.5, "UI  (GUI / CLI / API)",
           "browses databases + sends model / calculation details;\nreads results and status back",
           COL_UI, title_fs=15)
b_session = box(CX, 7.0, 9.2, 1.6, "ApplicationLayer   (4th layer)",
                "holds the current system AND the latest result;\n"
                "single point of contact for the UI, both ways", COL_SESSION, title_fs=15)
b_sys = box(CX - 3.0, 3.4, 5.4, 2.2, "Thermodynamic System Layer",
            "builds GibbsEnergyModel[] once;\nevaluates G, dG/dy, d2G/dy2;\n"
            "browsable database / element /\nphase metadata",
            COL_SYS, title_fs=13, sub_fs=9.5)
b_calc = box(CX + 3.0, 3.4, 5.4, 2.2, "Calculation Layer",
             "runs the solver,\nquerying the models\nmany times per solve",
             COL_CALC, title_fs=13, sub_fs=9.5)

# ── UI <-> ApplicationLayer : two phases, each a two-way channel ──
mid_uisess = (b_ui['bot'] + b_session['top']) / 2
# (a) PRE-CALCULATION: browse / setup -- left
varrow(CX - 2.5, b_ui['bot'], b_session['top'], "#888")
varrow(CX - 2.25, b_session['top'], b_ui['bot'], "#888")
ax.text(CX - 2.9, b_ui['bot'] - 0.18, "1. pre-calculation  (browse / setup)",
        fontsize=9.5, ha="left", va="top", color="#666", fontstyle="italic", zorder=5)
label(CX - 2.4, mid_uisess,
      "browse  (two-way)\npick a database ->\ndb / element /\nphase lists back",
      color="#555", fs=9)
# (b) CALCULATION: model + solve -- right
varrow(CX + 2.25, b_ui['bot'], b_session['top'], COL_UI)
varrow(CX + 2.5, b_session['top'], b_ui['bot'], COL_UI)
ax.text(CX + 2.9, b_ui['bot'] - 0.18, "2. calculation  (main flow)",
        fontsize=9.5, ha="right", va="top", color=COL_UI, fontstyle="italic", zorder=5)
label(CX + 2.4, mid_uisess,
      "setModel / calculate  (two-way)\nrequest ->  results + status back",
      color=COL_UI, fs=9)

# ── ApplicationLayer <-> System Layer : browse (pre-calc, TWO-WAY, no build) ──
mid_sesssys = (b_session['bot'] + b_sys['top']) / 2
varrow(b_sys['left'] + 1.0, b_session['bot'], b_sys['top'], "#888")
varrow(b_sys['left'] + 1.25, b_sys['top'], b_session['bot'], "#888")
label(b_sys['left'] + 1.1, mid_sesssys,
      "browse  (two-way)\nlist db / elements /\nphases -- no build",
      color="#555", fs=8.5)

# ── ApplicationLayer -> System Layer : setModel (main flow, ONE-WAY build) ──
varrow(b_sys['right'] - 1.1, b_session['bot'], b_sys['top'], COL_SESSION)
label(b_sys['right'] - 1.1, mid_sesssys,
      "setModel(...)\nbuild once,\nreuse  (one-way)", color=COL_SESSION, fs=9)

# ── ApplicationLayer <-> Calculation Layer : calculate / result ──
mid_sesscalc = (b_session['bot'] + b_calc['top']) / 2
varrow(b_calc['cx'] - 0.12, b_session['bot'], b_calc['top'], COL_SESSION)
varrow(b_calc['cx'] + 0.12, b_calc['top'], b_session['bot'], COL_CALC)
label(b_calc['cx'], mid_sesscalc, "calculate(...)   /   result", color="#333", fs=10)

# ── System Layer <-> Calculation Layer : repeated two-way loop ─────
loop_top = b_sys['cy'] + 0.35
loop_bot = b_sys['cy'] - 0.35
harrow(b_sys['right'], b_calc['left'], loop_top, "#222", lw=2.6)
harrow(b_calc['left'], b_sys['right'], loop_bot, "#222", lw=2.6)
ax.text(CX, loop_top + 0.34, "(T, y)  per iteration  ->", fontsize=10.5,
        ha="center", color="#222", fontweight="bold")
ax.text(CX, loop_bot - 0.34, "<-  G, dG/dy, d2G/dy2", fontsize=10.5,
        ha="center", color="#222", fontweight="bold")
ax.text(CX, b_sys['bot'] - 0.5,
        "repeated many times per calculate(...) call --\nthe solver needs fresh values at each trial point",
        ha="center", fontsize=10, style="italic", color="#555", linespacing=1.4)

# ── Legend ────────────────────────────────────────────────────────
legend = [
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_UI, markersize=16, label='UI Layer'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SESSION, markersize=16, label='ApplicationLayer (new, UI-agnostic)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SYS, markersize=16, label='Thermodynamic System Layer'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_CALC, markersize=16, label='Calculation Layer'),
]
ax.legend(handles=legend, loc="lower center", bbox_to_anchor=(0.5, -0.01),
          ncol=2, fontsize=11.5, frameon=False)

plt.subplots_adjust(left=0.03, right=0.97, top=0.97, bottom=0.06)
plt.savefig("docs/dataflow_target.png", dpi=170, facecolor="white")
print("Saved docs/dataflow_target.png")
