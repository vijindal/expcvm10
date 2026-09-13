"""
Generates docs/dataflow_ui_session_zoom.png: a zoomed-in view of the
"2. calculation (main flow)" arrow in dataflow_target.png -- the UI <->
CalculationSession interaction, mediated by session.calctype
.CalculationInterface.

This does not change dataflow_target.png's 4-layer picture: UI still talks
only to "the 4th layer" for both browsing and calculating. What this
figure zooms into is that the calculation half of that interaction (the
right-hand "2. calculation" arrow in the target figure) is no longer UI
code calling CalculationSession.setModel()/calculate*() directly -- it is
UI code calling CalculationInterface.

CalculationInterface is drawn as ONE box, not a layer: its whole job is to
collect parameters from the UI (a ModelSelection + a CalculationKind's own
typed Params) and pass them into CalculationSession through one standard
protocol (runCalculating/runAssessing), then collect the result back from
CalculationSession and hand it to the UI as an ordinary return value.
Nothing more -- it does not itself do any calculation, hold state, or
decide UI behavior. runCalculating and runAssessing are its two methods
(both static), not separate boxes/classes/layers.

Browsing (the left-hand "1. pre-calculation" arrow in dataflow_target.png)
is unchanged and still goes straight from UI to CalculationSession via
ModelBrowseService, so it is shown as a side note, not routed through
CalculationInterface.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 13.0, 11.0
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_UI, COL_IFACE, COL_SESSION, COL_BROWSE = "#2d4a6b", "#6b4a2d", "#4a2d6b", "#888888"
CX = FIG_W / 2


def box(cx, cy, w, h, title, subtitle, color, title_fs=14, sub_fs=10):
    ax.add_patch(FancyBboxPatch(
        (cx - w/2, cy - h/2), w, h,
        boxstyle="round,pad=0.12,rounding_size=0.14",
        linewidth=2, edgecolor=color, facecolor=color, zorder=3))
    ax.text(cx, cy + h*0.30, title, ha="center", va="center",
            fontsize=title_fs, fontweight="bold", color="white", zorder=4)
    ax.text(cx, cy - h*0.10, subtitle, ha="center", va="center",
            fontsize=sub_fs, color="#e9e9e9", family="monospace",
            linespacing=1.5, zorder=4)
    return dict(cx=cx, cy=cy, top=cy + h/2, bot=cy - h/2, left=cx - w/2, right=cx + w/2)


def varrow(x, y1, y2, color, lw=2.6):
    ax.add_patch(FancyArrowPatch((x, y1), (x, y2), arrowstyle="-|>",
                 mutation_scale=22, linewidth=lw, color=color,
                 shrinkA=2, shrinkB=2, zorder=2))


def label(x, y, text, color="#333", fs=9.5, ha="center"):
    ax.text(x, y, text, fontsize=fs, ha=ha, va="center", color=color, zorder=5,
            bbox=dict(boxstyle="round,pad=0.18", fc="white", ec="none", alpha=0.92))


# ── Title ─────────────────────────────────────────────────────────
ax.text(CX, FIG_H - 0.5, "expCVM10 -- UI <-> Session Layer, Zoomed",
        ha="center", fontsize=19.5, fontweight="bold")
ax.text(CX, FIG_H - 1.0,
        "Detail of dataflow_target.png's \"2. calculation (main flow)\" arrow: CalculationInterface\n"
        "is a single pass-through -- collects params from UI, passes them to CalculationSession; collects the result, passes it back to UI.",
        ha="center", fontsize=10.2, style="italic", color="#444")

# ── Browse path (unchanged, bypasses CalculationInterface) -- caption note ──
ax.text(CX, FIG_H - 1.55,
        "(browsing -- \"1. pre-calculation\" -- still goes straight from UI to CalculationSession via\nModelBrowseService, unchanged; not shown here, see dataflow_target.png)",
        fontsize=8.6, ha="center", va="top", color=COL_BROWSE, style="italic", linespacing=1.35)

# ── UI box ───────────────────────────────────────────────────────
b_ui = box(CX, 8.0, 10.2, 1.3, "UI  (GUI / CLI / API)",
           "cal/opt landing choice (default cal) -> builds ModelSelection\n"
           "+ a CalculationKind's own typed Params",
           COL_UI, title_fs=15, sub_fs=9.5)

# ── CalculationInterface -- ONE box, its two methods named inside ────
b_iface = box(CX, 5.25, 10.6, 2.35, "CalculationInterface",
              "runCalculating(session, kind, model, params)  ->  result\n"
              "runAssessing(kind, params)  ->  CalculationOutcome.NotImplemented\n\n"
              "collects params from UI, passes them to CalculationSession\n"
              "through this one protocol; collects the result, returns it to UI",
              COL_IFACE, title_fs=16, sub_fs=9.7)

varrow(CX - 0.25, b_ui['bot'], b_iface['top'], COL_UI)
varrow(CX + 0.25, b_iface['top'], b_ui['bot'], COL_IFACE)
label(CX - 2.3, (b_ui['bot'] + b_iface['top']) / 2, "ModelSelection\n+ typed Params ->", color=COL_UI, fs=9)
label(CX + 2.55, (b_ui['bot'] + b_iface['top']) / 2, "<- result\n(or NotImplemented)", color=COL_IFACE, fs=9)

# ── CalculationInterface <-> CalculationSession ──────────────────────
b_session = box(CX, 2.25, 8.4, 1.6, "CalculationSession",
                "setModel(tdb, elements, phases, kind)  --  TDB parsing happens here\n"
                "calculate*(...)  ->  current*() result",
                COL_SESSION, title_fs=13.5, sub_fs=9.3)

varrow(CX - 0.25, b_iface['bot'], b_session['top'], COL_IFACE)
varrow(CX + 0.25, b_session['top'], b_iface['bot'], COL_SESSION)
label(CX - 2.55, (b_iface['bot'] + b_session['top']) / 2, "setModel(...)\ncalculate*(...) ->", color=COL_IFACE, fs=9)
label(CX + 2.55, (b_iface['bot'] + b_session['top']) / 2, "<- current*()\nresult", color=COL_SESSION, fs=9)

ax.text(CX, b_session['bot'] - 0.35,
        "(down into Thermodynamic System Layer / Calculation Layer -- see dataflow_target.png, unchanged)",
        ha="center", fontsize=9.2, style="italic", color="#555")

# ── Legend ────────────────────────────────────────────────────────
legend = [
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_UI, markersize=15, label='UI Layer'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_IFACE, markersize=15, label='CalculationInterface (one class, pass-through)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SESSION, markersize=15, label='CalculationSession (unchanged)'),
]
ax.legend(handles=legend, loc="lower center", bbox_to_anchor=(0.5, -0.06),
          ncol=1, fontsize=10.5, frameon=False)

plt.subplots_adjust(left=0.04, right=0.96, top=0.96, bottom=0.1)
plt.savefig("docs/dataflow_ui_session_zoom.png", dpi=170, facecolor="white")
print("Saved docs/dataflow_ui_session_zoom.png")
