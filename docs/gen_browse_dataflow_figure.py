"""
Generates docs/dataflow_browse_zoom.png: a zoomed-in view of the
"1. pre-calculation (browse / setup)" arrow in dataflow_target.png -- the
UI <-> CalculationSession <-> Thermodynamic System Layer interaction used
to list databases / elements / phases, BEFORE any Gibbs energy model is
built.

Unlike the calculation-side zoom (dataflow_ui_session_zoom.png), this path
is unchanged by session.calctype: browsing never goes through
CalculationCatalog, because there is nothing calculation-type-specific
about it -- it is the same "what exists in this file" query regardless of
which CalculationKind the user will eventually run, and it is intentionally
lighter weight than setModel() (no GibbsEnergyModel[] build). It is shown
here for completeness, to confirm this half of dataflow_target.png is
still exactly as documented there.

  UI  --(tdbFilePath [, elements])-->  ModelBrowseService
                                             |  (filters pseudo-elements
                                             |   /-, VA; else pass-through)
                                             v
                                     CalculationSession
                                     availableDatabases()
                                     availableElements(tdbFilePath)
                                     availablePhasesFor(tdbFilePath, elements)
                                             |
                                             v  (session's own private
                                                 browseDatabase field --
                                                 NOT currentSystem)
                                     TdbParser (DatabasePort)
                                     load(path) -- cached by path
                                     extractSystem(elements) -- scoped view
                                             |
                                             v
                                     tdb  (legacy parser, read-only)

No GibbsEnergyModel[] is ever built on this path -- that only happens in
setModel(), see the CalculatingType path in dataflow_ui_session_zoom.png.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from matplotlib.lines import Line2D

FIG_W, FIG_H = 13.4, 13.6
fig, ax = plt.subplots(figsize=(FIG_W, FIG_H))
ax.set_xlim(0, FIG_W)
ax.set_ylim(0, FIG_H)
ax.axis("off")

COL_UI, COL_BROWSE_SVC, COL_SESSION, COL_PARSER, COL_TDB, COL_CALCPATH = \
    "#2d4a6b", "#888888", "#4a2d6b", "#6b2d2d", "#8a5a3a", "#2d6b3d"
CX = FIG_W / 2


def box(cx, cy, w, h, title, subtitle, color, title_fs=14, sub_fs=10):
    ax.add_patch(FancyBboxPatch(
        (cx - w/2, cy - h/2), w, h,
        boxstyle="round,pad=0.12,rounding_size=0.14",
        linewidth=2, edgecolor=color, facecolor=color, zorder=3))
    ax.text(cx, cy + h*0.24, title, ha="center", va="center",
            fontsize=title_fs, fontweight="bold", color="white", zorder=4)
    ax.text(cx, cy - h*0.14, subtitle, ha="center", va="center",
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
ax.text(CX, FIG_H - 0.45, "expCVM10 -- UI <-> Session <-> System Layer, Browse Zoom",
        ha="center", fontsize=19, fontweight="bold")
ax.text(CX, FIG_H - 0.95,
        "Detail of dataflow_target.png's \"1. pre-calculation (browse / setup)\" arrow: unchanged by\n"
        "session.calctype -- browsing never goes through CalculationCatalog, since it is not tied to any CalculationKind.",
        ha="center", fontsize=10.3, style="italic", color="#444")

# ── UI box ───────────────────────────────────────────────────────
b_ui = box(CX, 12.2, 10.6, 1.3, "UI  (GUI / CLI / API)",
           "pick a database -> pick elements -> pick phases,\n"
           "before any calculation kind is chosen",
           COL_UI, title_fs=15, sub_fs=9.5)

# ── ModelBrowseService box ──────────────────────────────────────────
b_svc = box(CX, 10.15, 9.2, 1.3, "ModelBrowseService",
            "selectableElements(tdb)  /  selectablePhases(tdb, elements)\n"
            "filters reserved pseudo-elements  \"/-\", \"VA\"  -- else pass-through",
            COL_BROWSE_SVC, title_fs=13.5, sub_fs=9.3)

varrow(CX - 0.15, b_ui['bot'], b_svc['top'], COL_UI)
varrow(CX + 0.15, b_svc['top'], b_ui['bot'], COL_BROWSE_SVC)
label(CX + 3.3, (b_ui['bot'] + b_svc['top']) / 2,
      "same bridge class used\nidentically by all 3 UIs", color=COL_UI, fs=8.7)

# ── CalculationSession box (browse methods only) ─────────────────────
b_session = box(CX, 7.85, 9.6, 1.75, "CalculationSession  (browse methods)",
                "availableDatabases()  -- scans data/*.tdb\n"
                "availableElements(tdbFilePath)\n"
                "availablePhasesFor(tdbFilePath, elements)",
                COL_SESSION, title_fs=13.5, sub_fs=9.3)

varrow(CX - 0.15, b_svc['bot'], b_session['top'], COL_BROWSE_SVC)
varrow(CX + 0.15, b_session['top'], b_svc['bot'], COL_SESSION)

# ── side note: this is the SAME session as the calculation path, but a
#    separate, lighter internal field ──────────────────────────────
ax.text(b_ui['left'] - 0.1, b_session['cy'] + 0.05,
        "(the calculation path --\nCalculationCatalog ->\nCalculatingType ->\nsetModel() -- uses the SAME\nCalculationSession instance,\nsee dataflow_ui_session_zoom.png;\nnot shown here)",
        fontsize=8.2, ha="left", va="center", color=COL_CALCPATH, style="italic", linespacing=1.3,
        bbox=dict(boxstyle="round,pad=0.28", fc="#f0f6f1", ec=COL_CALCPATH, lw=1, alpha=0.92))

# ── CalculationSession's own private browseDatabase field -> TdbParser ─
b_parser = box(CX, 5.35, 8.2, 1.75, "TdbParser  (DatabasePort)",
               "load(tdbFilePath)  -- cached by path, no re-parse\n"
               "extractSystem(elements)  -- scoped sub-view for phase lookup\n"
               "getElementNames() / getPhaseNames()",
               COL_PARSER, title_fs=13, sub_fs=9)

varrow(CX - 0.15, b_session['bot'], b_parser['top'], COL_SESSION)
varrow(CX + 0.15, b_parser['top'], b_session['bot'], COL_PARSER)
label(CX + 3.55, (b_session['bot'] + b_parser['top']) / 2,
      "session's own private\n\"browseDatabase\" field --\nNOT currentSystem,\nno GibbsEnergyModel[] built", color=COL_SESSION, fs=8.4)

# ── legacy tdb parser ─────────────────────────────────────────────
b_tdb = box(CX, 2.95, 6.0, 1.4, "tdb  (legacy parser)",
            "reads the .TDB file's raw ELEMENT / PHASE /\nFUNCTION records, read-only",
            COL_TDB, title_fs=13, sub_fs=9.3)

varrow(CX, b_parser['bot'], b_tdb['top'], COL_PARSER)
label(CX + 2.6, (b_parser['bot'] + b_tdb['top']) / 2,
      "one-way\n(construct + read)", color=COL_PARSER, fs=8.7)

ax.text(CX, b_tdb['bot'] - 0.4,
        "no ThermodynamicSystem, no GibbsEnergyModel[] on this path --\n"
        "that build only happens in CalculationSession.setModel(), see dataflow_ui_session_zoom.png",
        ha="center", fontsize=9.3, style="italic", color="#555", linespacing=1.4)

# ── Legend ────────────────────────────────────────────────────────
legend = [
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_UI, markersize=15, label='UI Layer'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_BROWSE_SVC, markersize=15, label='ModelBrowseService (shared bridge)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_SESSION, markersize=15, label='CalculationSession (unchanged)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_PARSER, markersize=15, label='TdbParser (DatabasePort)'),
    Line2D([0], [0], marker='s', color='w', markerfacecolor=COL_TDB, markersize=15, label='tdb (legacy parser)'),
]
ax.legend(handles=legend, loc="lower center", bbox_to_anchor=(0.5, -0.015),
          ncol=2, fontsize=10.3, frameon=False)

plt.subplots_adjust(left=0.03, right=0.97, top=0.97, bottom=0.09)
plt.savefig("docs/dataflow_browse_zoom.png", dpi=170, facecolor="white")
print("Saved docs/dataflow_browse_zoom.png")
