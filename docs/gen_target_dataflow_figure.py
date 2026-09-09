"""
Generates docs/dataflow_target.png: the TARGET core data flow of expCVM10
(with CalculationSession as the UI-agnostic coordinator), simplified for
clarity -- fewer boxes, bigger fonts, one clean pipeline.

Corrected topology (2026-09-09, per user):
- UI <-> CalculationSession is two-way (one box, bidirectional arrow) --
  the UI never talks to System/Calculation layers directly, in either
  direction. Results flow INTO the session, not straight back to the UI.
- CalculationSession -> System (setModel) and CalculationSession <->
  Calculation (calculate / result) are separate arrows; the result comes
  back to the session, which the UI then reads.
- System Layer <-> Calculation Layer is a real, repeated, two-way loop
  within one calculation (many (T,y) queries per solve), not a single
  handoff -- drawn as a double-headed loop, not a one-way arrow.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 13.5, 10.7
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_UI, COL_SESSION, COL_SYS, COL_CALC = "#2d4a6b", "#4a2d6b", "#6b2d2d", "#2d6b3d"

def box(cx, cy, w, h, title, subtitle, color, title_fs=17, sub_fs=12):
    patch = FancyBboxPatch((cx - w/2, cy - h/2), w, h,
                            boxstyle="round,pad=0.12,rounding_size=0.14",
                            linewidth=2, edgecolor=color, facecolor=color, zorder=3)
    ax.add_patch(patch)
    if subtitle:
        ax.text(cx, cy + h*0.16, title, ha="center", va="center",
                fontsize=title_fs, fontweight="bold", color="white", zorder=4)
        ax.text(cx, cy - h*0.24, subtitle, ha="center", va="center",
                fontsize=sub_fs, color="#e8e8e8", family="monospace", linespacing=1.6, zorder=4)
    else:
        ax.text(cx, cy, title, ha="center", va="center",
                fontsize=title_fs, fontweight="bold", color="white", zorder=4)
    return dict(cx=cx, cy=cy, w=w, h=h, top=cy+h/2, bot=cy-h/2, left=cx-w/2, right=cx+w/2)

def arrow(p1, p2, color="#333", lw=2.6, style="-|>", label=None, fs=12.5,
          ha="center", label_xy=None, cs="arc3,rad=0.0"):
    a = FancyArrowPatch(p1, p2, arrowstyle=style, mutation_scale=26, linewidth=lw,
                         color=color, shrinkA=3, shrinkB=3, zorder=2, connectionstyle=cs)
    ax.add_patch(a)
    if label:
        lx, ly = label_xy if label_xy else ((p1[0]+p2[0])/2, (p1[1]+p2[1])/2)
        ax.text(lx, ly, label, fontsize=fs, ha=ha, va="center", color=color, zorder=5,
                bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.88))

# ── Title ──────────────────────────────────────────────────────────
ax.text(FIG_W/2, FIG_H - 0.5, "expCVM10 -- Target Core Data Flow",
        ha="center", fontsize=22, fontweight="bold")
ax.text(FIG_W/2, FIG_H - 1.05, "UI  <->  CalculationSession  <->  System + Calculation Layers",
        ha="center", fontsize=13.5, style="italic", color="#444")

cx = FIG_W/2

b_ui = box(cx, 8.3, 8.6, 1.7, "UI",
           "sends model + calculation details;\nreads results and status back", COL_UI)
b_session = box(cx, 5.7, 9.8, 2.0, "CalculationSession",
                "holds the current system AND the latest result;\n"
                "single point of contact for the UI, both ways", COL_SESSION)
b_sys = box(cx - 3.3, 2.6, 5.2, 2.0, "Thermodynamic\nSystem Layer",
            "builds GibbsEnergyModel[];\nevaluates G, dG/dy, d2G/dy2", COL_SYS, title_fs=15, sub_fs=10.5)
b_calc = box(cx + 3.3, 2.6, 5.2, 2.0, "Calculation\nLayer",
             "runs the solver, querying\nthe models many times per solve", COL_CALC, title_fs=15, sub_fs=10.5)

# ── UI <-> CalculationSession: one bidirectional arrow ──────────────
arrow((cx-0.15, b_ui['bot']), (cx-0.15, b_session['top']), COL_UI, style="-|>")
arrow((cx+0.15, b_session['top']), (cx+0.15, b_ui['bot']), COL_UI, style="-|>")
ax.text(cx - 2.6, (b_ui['bot']+b_session['top'])/2, "model + calculation details",
        fontsize=12.5, ha="right", va="center", color=COL_UI,
        bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.88))
ax.text(cx + 2.6, (b_ui['bot']+b_session['top'])/2, "results + status",
        fontsize=12.5, ha="left", va="center", color=COL_UI,
        bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.88))

# ── CalculationSession -> System: setModel (one-way trigger) ────────
arrow((b_session['cx']-2.0, b_session['bot']), (b_sys['cx']+0.3, b_sys['top']), COL_SESSION,
      label="setModel(...)\nbuild once, reuse", label_xy=(b_session['cx']-3.6, (b_session['bot']+b_sys['top'])/2 + 0.15))

# ── CalculationSession <-> Calculation: calculate(...) / result (two-way) ──
arrow((b_session['cx']+0.9, b_session['bot']), (b_calc['cx']-1.0, b_calc['top']), COL_SESSION,
      label="calculate(...)", label_xy=(b_session['cx']+0.55, (b_session['bot']+b_calc['top'])/2 + 0.55))
arrow((b_calc['cx']+1.0, b_calc['top']), (b_session['cx']+2.6, b_session['bot']), COL_CALC,
      label="result", label_xy=(b_session['cx']+3.9, (b_session['bot']+b_calc['top'])/2 - 0.15))

# ── System <-> Calculation: real repeated two-way loop ──────────────
loop_y_top = b_sys['cy'] + 0.35
loop_y_bot = b_sys['cy'] - 0.35
arrow((b_sys['right'], loop_y_top), (b_calc['left'], loop_y_top), "#222", lw=2.8)
arrow((b_calc['left'], loop_y_bot), (b_sys['right'], loop_y_bot), "#222", lw=2.8)
ax.text(cx, loop_y_top + 0.42, "(T, y) per iteration ->", fontsize=11.5, ha="center", color="#222", fontweight="bold")
ax.text(cx, loop_y_bot - 0.42, "<- G, dG/dy, d2G/dy2", fontsize=11.5, ha="center", color="#222", fontweight="bold")
ax.text(cx, b_sys['bot'] - 0.55,
        "repeated many times per calculate(...) call -- the solver needs fresh values at each trial point",
        ha="center", fontsize=11, style="italic", color="#555")

# ── Legend ───────────────────────────────────────────────────────
legend_items = [
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_UI, markersize=18, label='UI Layer'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_SESSION, markersize=18, label='CalculationSession (new, UI-agnostic)'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_SYS, markersize=18, label='Thermodynamic System Layer'),
    Line2D([0],[0], marker='s', color='w', markerfacecolor=COL_CALC, markersize=18, label='Calculation Layer'),
]
ax.legend(handles=legend_items, loc="lower center", bbox_to_anchor=(0.5, -0.03),
          ncol=2, fontsize=12.5, frameon=False)

plt.subplots_adjust(left=0.03, right=0.97, top=0.98, bottom=0.07)
plt.savefig("docs/dataflow_target.png", dpi=170, facecolor="white")
print("Saved docs/dataflow_target.png")
