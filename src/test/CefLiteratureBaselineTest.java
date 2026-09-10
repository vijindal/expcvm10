package test;

import session.CalculationSession;
import system.model.GibbsEnergyModel;
import system.model.cef.CefPhaseModelAdapter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference-agreement baseline for the general n-sublattice Compound
 * Energy Formalism evaluator ({@code CefGibbs} via
 * {@code CefPhaseModelAdapter.siteEnergy}). It checks the evaluator
 * against two INDEPENDENT references, not just this project's own
 * database:
 *
 * <ol>
 *   <li><b>Section A -- published literature.</b> V2ZR of the V-Zr system
 *       against the solid curve of Fig. 9 in J. Cui, C. Guo, L. Zou,
 *       C. Li and Z. Du, "Thermodynamic modeling of the V-Zr system
 *       supported by key experiments", CALPHAD 53 (2016) 122-129.
 *       Values digitized from the printed figure; tolerance set by
 *       digitization noise (~kJ/mol).</li>
 *
 *   <li><b>Section B -- pycalphad, multi-sublattice compounds.</b>
 *       Non-magnetic CEF compound phases of the Cr-Fe-Mo-Si-V-C steel
 *       database ({@code data/steel1.TDB}) at fixed constitutions,
 *       against Gibbs energies computed by <b>pycalphad</b> (0.11.1) --
 *       a production CALPHAD engine implementing the same CEF math. This
 *       decomposes the evaluator term by term: end-members, ideal
 *       configurational mixing per sublattice, Redlich-Kister excess,
 *       several mixing DOF at once, unequal site multiplicities, and
 *       temperature dependence.</li>
 *
 *   <li><b>Section C -- pycalphad, disordered solution phase.</b> The
 *       non-magnetic {@code CUB_A13} {@code (Cr,Fe,Si,V)(C,Va)} solution
 *       phase of the same database, vs. pycalphad. This is the "no
 *       minimization" case: for a {@code (metal)(Va)} phase the metal
 *       sublattice site fractions ARE the overall metal mole fractions,
 *       so the equilibrium G at a given (T, composition) is a DIRECT
 *       evaluation -- there is no internal degree of freedom for a
 *       solver to minimize. Section C therefore also serves as the
 *       "give T + overall composition, get the equilibrium G" check for
 *       a disordered phase (C4 rows). It further exercises the vacancy
 *       normalization: pycalphad's per-atom {@code GM} for a
 *       {@code (M)(Va)} end-member normalizes by 1, not by the sum of
 *       site ratios, and this project matches that.</li>
 *
 *   <li><b>Section D -- published literature, one-sublattice phase.</b>
 *       The V-Zr {@code LIQUID}, a plain one-sublattice substitutional
 *       (Redlich-Kister) solution, against its enthalpy-of-mixing curve
 *       in Fig. 10 of the same Cui et al. 2016 paper. A one-sublattice
 *       phase is the CEF 1-sublattice special case -- end members are the
 *       pure elements, interactions are {@code L(V,Zr;n)} on the single
 *       sublattice -- so {@code CefGibbs} must reproduce it. This section
 *       is what confirms the general CEF path handles substitutional
 *       solutions correctly, which is why this project no longer carries
 *       a separate Redlich-Kister model.</li>
 * </ol>
 *
 * <p>Sections B and C are <b>report-only</b>: pycalphad is the reference
 * of record; each row prints the difference {@code project G - pycalphad G}
 * and the section prints error summaries. They do not fail the build --
 * the differences are the regression signal. Sections A and D keep a real
 * pass/fail against their digitization tolerance.
 *
 * <p>Every phase model is sourced through {@link CalculationSession}
 * ({@code setModel(...)} then {@code currentSystem().phaseModels()}) --
 * the same coordinator every UI goes through (README "Structure" /
 * {@code docs/dataflow_target.png}) -- so nothing here reaches a
 * database/parser class directly. All three sections are MODEL-layer
 * checks: they call the CEF site-fraction evaluator {@code siteEnergy(T, y)}
 * at fixed constitutions, deliberately NOT running the solver.
 *
 * <p><b>Section B/C conventions</b> (verified 2026-09-11):
 * <ul>
 *   <li>The reference value in each row is pycalphad {@code GM} (J per
 *       mole of ATOMS) multiplied by pycalphad's
 *       {@code _site_ratio_normalization} evaluated at that constitution
 *       -- i.e. {@code sum_s siteRatio[s] * (atom-weighted site fraction
 *       on s)}. For a fully-occupied compound this is {@code sum(site
 *       ratios)}; for a {@code (M)(Va)} phase at a metal end-member it is
 *       just the metal sublattice's ratio. This matches what
 *       {@code CefGibbs.evaluate} returns.</li>
 *   <li>Site-fraction vector layout: sublattice-major, constituents
 *       ascending-alphabetical within each sublattice. This project
 *       preserves TDB file order; every phase used here already lists
 *       constituents alphabetically, so the layouts coincide.</li>
 *   <li>The requested element list for each phase includes EVERY element
 *       appearing in that phase's constituents, so this project and
 *       pycalphad build the same model (this project drops constituents
 *       whose element is not requested).</li>
 *   <li>Only NON-MAGNETIC phases (SIGMA, CHI_A12, CEMENTITE, M23C6,
 *       CUB_A13). BCC_A2/FCC_A1/HCP_A3 carry TC/BMAGN parameters this
 *       project does not yet evaluate ({@code computeTc}/{@code computeBeta}
 *       return 0 -- see README), so they would disagree for an unrelated
 *       reason.</li>
 * </ul>
 */
public class CefLiteratureBaselineTest {

    // ------------------------------------------------------------------
    // Section A -- V2ZR vs. Cui et al. 2016 Fig. 9
    // ------------------------------------------------------------------

    private static final String A_TDB = "data/VZR-re2.TDB";
    private static final String A_PHASE = "V2ZR";

    /*
     * Digitization noise on a steep, hand-read curve -- not a
     * floating-point tolerance. 5 kJ/mol covers the worst observed
     * deviation (~3.4 kJ/mol at 1950 K) with margin.
     */
    private static final double A_TOLERANCE_KJ = 5.0;

    /*
     * Digitized from Cui et al. 2016, Fig. 9 (solid curve). Points below
     * 298.15 K are excluded: the SGTE unary lattice stabilities the
     * V2ZR expression is built from are undefined there.
     */
    private static final double[][] FIG9_SOLID_CURVE = {
        // { T [K], G [kJ/mol formula unit] }
        {  300, -41.69 }, {  350, -46.61 }, {  400, -52.90 }, {  450, -58.80 },
        {  500, -65.68 }, {  550, -72.57 }, {  600, -79.84 }, {  650, -87.32 },
        {  700, -96.17 }, {  750, -104.03 }, {  800, -113.08 }, {  850, -121.73 },
        {  900, -131.17 }, {  950, -139.23 }, { 1000, -150.05 }, { 1050, -160.28 },
        { 1100, -170.70 }, { 1150, -181.51 }, { 1200, -192.33 }, { 1250, -203.74 },
        { 1300, -215.73 }, { 1350, -225.37 }, { 1400, -237.17 }, { 1450, -248.57 },
        { 1500, -260.77 }, { 1550, -272.37 }, { 1600, -285.35 }, { 1650, -297.54 },
        { 1700, -310.32 }, { 1750, -322.71 }, { 1800, -335.89 }, { 1850, -348.87 },
        { 1900, -362.83 }, { 1950, -376.40 },
    };

    // ------------------------------------------------------------------
    // Section B -- steel1.TDB phases vs. pycalphad
    // ------------------------------------------------------------------

    private static final String B_TDB = "data/steel1.TDB";

    /*
     * pycalphad is the reference of record here. Section B does NOT
     * pass/fail against a tolerance -- it computes G at each point with
     * this project's CEF evaluator and REPORTS the difference from the
     * pycalphad value, plus per-group and overall error summaries. A
     * regression (or a fix) shows up as a change in the reported
     * differences.
     *
     * Row format:
     *   { phase, elements CSV, T [K], site-fraction vector,
     *     pycalphad G [J / mol formula unit] }
     * where the reference = pycalphad 0.11.1 GM (J/mol-atom) * sum(site
     * ratios), computed from data/steel1.TDB.
     *
     * Phases and their (alphabetical) sublattice constituents:
     *   SIGMA     (FE)(CR,MO,V)(CR,FE,MO,V)          sites  8:4:18
     *   CHI_A12   (CR,FE)(CR,MO)(CR,FE,MO)           sites 24:10:24
     *   CEMENTITE (CR,FE,MO,V)(C)                    sites  3:1
     *   M23C6     (CR,FE,V)(CR,FE,MO,V)(C)           sites 20:3:6
     */
    private static final Object[][] PYCALPHAD_REF = {
        // --- B1  end-members (G_ref only: no mixing, no excess) -------------
        { "SIGMA", "CR,FE,MO,V", 1000.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -1146404.286825 },   // FE:CR:CR
        { "SIGMA", "CR,FE,MO,V", 1800.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -2935852.712094 },   // FE:CR:CR
        { "SIGMA", "CR,FE,MO,V", 1000.0, new double[]{1, 0, 1, 0, 0, 0, 1, 0}, -1235741.026772 },   // FE:MO:MO
        { "SIGMA", "CR,FE,MO,V", 1000.0, new double[]{1, 0, 0, 1, 0, 0, 0, 1}, -1574278.328183 },   // FE:V:V

        // --- B2  ideal configurational mixing on one sublattice ------------
        { "CHI_A12", "CR,FE,MO", 800.0,  new double[]{1, 0, 1, 0, 0.1, 0.9, 0}, -953301.896905 },
        { "CHI_A12", "CR,FE,MO", 1600.0, new double[]{1, 0, 1, 0, 0.1, 0.9, 0}, -4106735.739439 },
        { "CHI_A12", "CR,FE,MO", 800.0,  new double[]{1, 0, 1, 0, 0.25, 0.75, 0}, -999731.917562 },
        { "CHI_A12", "CR,FE,MO", 1600.0, new double[]{1, 0, 1, 0, 0.25, 0.75, 0}, -4135152.373931 },
        { "CHI_A12", "CR,FE,MO", 800.0,  new double[]{1, 0, 1, 0, 0.5, 0.5, 0}, -1034873.647756 },
        { "CHI_A12", "CR,FE,MO", 1600.0, new double[]{1, 0, 1, 0, 0.5, 0.5, 0}, -4098030.156285 },
        { "CHI_A12", "CR,FE,MO", 800.0,  new double[]{1, 0, 1, 0, 0.75, 0.25, 0}, -1028250.129714 },
        { "CHI_A12", "CR,FE,MO", 1600.0, new double[]{1, 0, 1, 0, 0.75, 0.25, 0}, -3977377.442165 },
        { "CHI_A12", "CR,FE,MO", 800.0,  new double[]{1, 0, 1, 0, 0.9, 0.1, 0}, -998931.036348 },
        { "CHI_A12", "CR,FE,MO", 1600.0, new double[]{1, 0, 1, 0, 0.9, 0.1, 0}, -3854295.848613 },

        // --- B3  Redlich-Kister excess (CHI_A12 subl1 CR,MO has L params) --
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{1, 0, 0.2, 0.8, 1, 0, 0}, -1825624.551685 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{1, 0, 0.2, 0.8, 1, 0, 0}, -4986924.357271 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{1, 0, 0.4, 0.6, 1, 0, 0}, -1778676.720368 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{1, 0, 0.4, 0.6, 1, 0, 0}, -4928328.251586 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{1, 0, 0.5, 0.5, 1, 0, 0}, -1749701.174209 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{1, 0, 0.5, 0.5, 1, 0, 0}, -4889127.263844 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{1, 0, 0.6, 0.4, 1, 0, 0}, -1717377.293502 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{1, 0, 0.6, 0.4, 1, 0, 0}, -4843899.273914 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{1, 0, 0.8, 0.2, 1, 0, 0}, -1641726.271088 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{1, 0, 0.8, 0.2, 1, 0, 0}, -4733637.424255 },

        // --- B4  several mixing DOF simultaneously (all 3 sublattices) -----
        { "CHI_A12", "CR,FE,MO", 1200.0, new double[]{0.5, 0.5, 0.5, 0.5, 0.34, 0.33, 0.33}, -3130218.167417 },
        { "CHI_A12", "CR,FE,MO", 1200.0, new double[]{0.3, 0.7, 0.7, 0.3, 0.5, 0.25, 0.25}, -3233390.732765 },
        { "CHI_A12", "CR,FE,MO", 1200.0, new double[]{0.8, 0.2, 0.4, 0.6, 0.2, 0.4, 0.4}, -2804261.896114 },

        // --- B5  two-sublattice compound, unequal site multiplicities -----
        { "CEMENTITE", "CR,FE,MO,V,C", 1000.0, new double[]{1, 0, 0, 0, 1}, -180030.981420 },   // CR:C
        { "CEMENTITE", "CR,FE,MO,V,C", 1000.0, new double[]{0, 1, 0, 0, 1}, -137777.853253 },   // FE:C
        { "CEMENTITE", "CR,FE,MO,V,C", 2000.0, new double[]{0.4, 0.4, 0.1, 0.1, 1}, -501133.732652 },

        // --- B6  three-sublattice compound, large multiplicities ----------
        { "M23C6", "CR,FE,MO,V,C", 1000.0, new double[]{1, 0, 0, 1, 0, 0, 0, 1}, -1315648.256813 },   // CR:CR:C
        { "M23C6", "CR,FE,MO,V,C", 1000.0, new double[]{0, 1, 0, 0, 1, 0, 0, 1}, -1008278.759510 },   // FE:FE:C
        { "M23C6", "CR,FE,MO,V,C", 1800.0, new double[]{0.6, 0.3, 0.1, 0.25, 0.25, 0.25, 0.25, 1}, -3101112.661483 },

        // --- B7  temperature dependence (SIGMA end-member G(T) curve) ------
        //     Spans the GHSERFE 1811 K and GHSERCR 2180 K SGTE sub-range
        //     breaks, so the report shows how the two-branch functions
        //     are handled above their first range.
        { "SIGMA", "CR,FE,MO,V", 400.0,  new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -215911.881068 },
        { "SIGMA", "CR,FE,MO,V", 600.0,  new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -473902.182087 },
        { "SIGMA", "CR,FE,MO,V", 800.0,  new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -787816.474908 },
        { "SIGMA", "CR,FE,MO,V", 1000.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -1146404.286825 },
        { "SIGMA", "CR,FE,MO,V", 1200.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -1543454.334938 },
        { "SIGMA", "CR,FE,MO,V", 1400.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -1975328.017119 },
        { "SIGMA", "CR,FE,MO,V", 1600.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -2439869.285777 },
        { "SIGMA", "CR,FE,MO,V", 1800.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -2935852.712094 },
        { "SIGMA", "CR,FE,MO,V", 2000.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -3462773.383203 },
        { "SIGMA", "CR,FE,MO,V", 2400.0, new double[]{1, 1, 0, 0, 1, 0, 0, 0}, -4608524.648774 },
    };

    /*
     * Section C -- disordered CEF solution phase CUB_A13 (Cr,Fe,Si,V)(C,Va),
     * non-magnetic, site ratios 1:1, vs. pycalphad 0.11.1, data/steel1.TDB.
     *
     * Same row format and report-only handling as PYCALPHAD_REF. Only
     * Cr/Fe/Si are used on the metal sublattice: G(CUB_A13,V:VA;0) and
     * the CUB_A13:C end-members are UN_ASS in this database.
     *
     * C1: metal end-members (M:VA)         -- G_ref term + vacancy normalization
     * C2: Fe-Si ideal mixing on subl 0     -- RT * 1 * sum(y ln y)
     * C3: Fe-Si (3 RK orders) + Fe-V (1)   -- disordered RK excess, no minimization
     * C4: ternary metal composition        -- "give T + overall x, get equilibrium G"
     *     (site fractions == metal mole fractions, nothing to minimize)
     */
    private static final Object[][] PYCALPHAD_REF_CUB_A13 = {
        // --- C1  metal end-members ---------------------------------------
        { "CUB_A13", "CR,FE,SI,V,C", 500.0,  new double[]{1, 0, 0, 0, 0, 1}, 3051.958894 },     // CR:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{1, 0, 0, 0, 0, 1}, -20167.829047 },   // CR:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{1, 0, 0, 0, 0, 1}, -51603.204180 },   // CR:VA
        { "CUB_A13", "CR,FE,SI,V,C", 500.0,  new double[]{0, 1, 0, 0, 0, 1}, -6980.407479 },    // FE:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 1, 0, 0, 0, 1}, -37705.417957 },   // FE:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{0, 1, 0, 0, 0, 1}, -76817.913181 },   // FE:VA
        { "CUB_A13", "CR,FE,SI,V,C", 500.0,  new double[]{0, 0, 1, 0, 0, 1}, 26470.985020 },    // SI:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0, 1, 0, 0, 1}, -3489.303386 },    // SI:VA
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{0, 0, 1, 0, 0, 1}, -40266.930060 },   // SI:VA

        // --- C2  Fe-Si ideal configurational mixing on the metal sublattice
        { "CUB_A13", "CR,FE,SI,V,C", 700.0,  new double[]{0, 0.1, 0.9, 0, 0, 1}, 9656.904812 },
        { "CUB_A13", "CR,FE,SI,V,C", 1300.0, new double[]{0, 0.1, 0.9, 0, 0, 1}, -30068.842145 },
        { "CUB_A13", "CR,FE,SI,V,C", 700.0,  new double[]{0, 0.25, 0.75, 0, 0, 1}, -7175.102280 },
        { "CUB_A13", "CR,FE,SI,V,C", 1300.0, new double[]{0, 0.25, 0.75, 0, 0, 1}, -45647.441113 },
        { "CUB_A13", "CR,FE,SI,V,C", 700.0,  new double[]{0, 0.5, 0.5, 0, 0, 1}, -35438.607732 },
        { "CUB_A13", "CR,FE,SI,V,C", 1300.0, new double[]{0, 0.5, 0.5, 0, 0, 1}, -73290.681850 },
        { "CUB_A13", "CR,FE,SI,V,C", 700.0,  new double[]{0, 0.75, 0.25, 0, 0, 1}, -41268.796841 },
        { "CUB_A13", "CR,FE,SI,V,C", 1300.0, new double[]{0, 0.75, 0.25, 0, 0, 1}, -80681.442237 },
        { "CUB_A13", "CR,FE,SI,V,C", 700.0,  new double[]{0, 0.9, 0.1, 0, 0, 1}, -30486.094485 },
        { "CUB_A13", "CR,FE,SI,V,C", 1300.0, new double[]{0, 0.9, 0.1, 0, 0, 1}, -71716.331943 },

        // --- C3  Redlich-Kister excess: Fe-Si (3 orders), Fe-V (1 order) --
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.2, 0.8, 0, 0, 1}, -19108.066251 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.4, 0.6, 0, 0, 1}, -43339.744720 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.5, 0.5, 0, 0, 1}, -53025.782904 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.6, 0.4, 0, 0, 1}, -59048.759634 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.8, 0.2, 0, 0, 1}, -57369.318993 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.3, 0, 0.7, 0, 1}, -18490.656626 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.5, 0, 0.5, 0, 1}, -27115.881211 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.7, 0, 0.3, 0, 1}, -33572.823809 },

        // --- C4  T + overall metal composition -> equilibrium G (no minimization)
        { "CUB_A13", "CR,FE,SI,V,C", 900.0,  new double[]{0.2, 0.5, 0.3, 0, 0, 1}, -44204.217272 },
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{0.2, 0.5, 0.3, 0, 0, 1}, -88561.521422 },
        { "CUB_A13", "CR,FE,SI,V,C", 900.0,  new double[]{0.34, 0.33, 0.33, 0, 0, 1}, -34489.316900 },
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{0.34, 0.33, 0.33, 0, 0, 1}, -78968.009322 },
        { "CUB_A13", "CR,FE,SI,V,C", 900.0,  new double[]{0.5, 0.3, 0.2, 0, 0, 1}, -30898.649595 },
        { "CUB_A13", "CR,FE,SI,V,C", 1500.0, new double[]{0.5, 0.3, 0.2, 0, 0, 1}, -75263.965035 },
    };

    // ------------------------------------------------------------------
    // Section D -- V-Zr LIQUID enthalpy of mixing vs. Cui et al. 2016 Fig. 10
    // ------------------------------------------------------------------

    private static final String D_TDB = "data/VZR-re2.TDB";
    private static final String D_PHASE = "LIQUID";
    private static final double D_T = 2400.0;   // Fig. 10 is calculated at 2400 K

    /*
     * CefGibbs' ideal term computes y*ln(y) with the standard
     * y > 0 guard, but evaluate exactly at the pure end members anyway
     * a hair off (0*ln 0 is mathematically 0 but not something to rely on
     * across implementations).
     */
    private static final double D_EPS = 1.0e-9;

    /*
     * Digitization noise on a hand-read curve -- not a floating-point
     * tolerance. The Fig. 10 y-axis spans 5000 J/mol over ~1980 px in the
     * source render (~2.5 J/mol/px); 20 J/mol covers the worst observed
     * deviation (~15.5 J/mol, at x_Zr = 0.5, where the nearby de Boer
     * marker makes the column noisiest) with margin.
     */
    private static final double D_TOLERANCE_J = 20.0;

    /*
     * Digitized from Cui et al. 2016, Fig. 10 (solid curve only -- the
     * de Boer/Miedema triangle marker near x_Zr = 0.5 is a different
     * model's prediction and is excluded), by pixel calibration against
     * the figure's own axis ticks (x_Zr: 0-1.0: x=791-2772 px; H: 0 to
     * -5000 J/mol: y=523-2503 px, on a 10x render of the source PDF).
     * Table 3 of the paper gives the liquid excess parameters
     *   0L(V,Zr) = -15937.91 + 12.5173*T
     *   1L(V,Zr) = -10361.01 +  7.1042*T
     *   2L(V,Zr) =  -5976.59
     * matching VZR-re2.TDB's G(LIQUID,V,ZR;0/1/2) exactly.
     *
     * Units: J/mol of atoms (matching the Fig. 10 y-axis).
     */
    private static final double[][] FIG10_SOLID_CURVE = {
        // { x_Zr, H_mix [J/mol of atoms] }
        { 0.02, -616.2 }, { 0.04, -1169.2 }, { 0.06, -1664.1 }, { 0.08, -2123.7 },
        { 0.10, -2520.2 }, { 0.12, -2871.2 }, { 0.14, -3179.3 }, { 0.16, -3454.5 },
        { 0.18, -3699.5 }, { 0.20, -3883.8 }, { 0.22, -4050.5 }, { 0.24, -4181.8 },
        { 0.26, -4287.9 }, { 0.28, -4366.2 }, { 0.30, -4414.1 }, { 0.32, -4449.5 },
        { 0.34, -4454.5 }, { 0.36, -4449.5 }, { 0.38, -4419.2 }, { 0.40, -4378.8 },
        { 0.42, -4323.2 }, { 0.44, -4252.5 }, { 0.46, -4179.3 }, { 0.48, -4083.3 },
        { 0.50, -4000.0 }, { 0.52, -3878.8 }, { 0.54, -3752.5 }, { 0.56, -3641.4 },
        { 0.58, -3515.2 }, { 0.60, -3381.3 }, { 0.62, -3252.5 }, { 0.64, -3108.6 },
        { 0.66, -2972.2 }, { 0.68, -2825.8 }, { 0.70, -2669.2 }, { 0.72, -2522.7 },
        { 0.74, -2368.7 }, { 0.76, -2214.6 }, { 0.78, -2060.6 }, { 0.80, -1891.4 },
        { 0.82, -1737.4 }, { 0.84, -1563.1 }, { 0.86, -1386.4 }, { 0.88, -1212.1 },
        { 0.90, -1030.3 }, { 0.92, -835.9 }, { 0.94, -641.4 }, { 0.96, -434.3 },
        { 0.98, -232.3 },
    };

    // ==================================================================

    public static void main(String[] args) throws Exception {
        // Section A -- literature: pass/fail against a digitization tolerance.
        boolean aPass = runSectionA();

        // Sections B and C -- pycalphad, report-only. pycalphad is the
        // reference of record; these print the difference at every point
        // and do not fail the build. Their numbers are the regression
        // signal -- watch them, don't gate on them.
        reportAgainstPycalphad(
                "Section B: steel1.TDB CEF compound phases vs. pycalphad 0.11.1",
                PYCALPHAD_REF);
        reportAgainstPycalphad(
                "Section C: steel1.TDB disordered CUB_A13 (Cr,Fe,Si,V)(C,Va) "
                        + "vs. pycalphad 0.11.1",
                PYCALPHAD_REF_CUB_A13);

        // Section D -- literature: pass/fail against a digitization tolerance.
        boolean dPass = runSectionD();

        System.out.println();
        if (aPass && dPass) {
            System.out.println("PASS: Sections A and D (literature Fig. 9 / Fig. 10) "
                    + "within tolerance. Sections B/C (pycalphad) are report-only -- "
                    + "see the tables above.");
        } else {
            throw new AssertionError(
                    "CefLiteratureBaselineTest -- Section A (Fig. 9): "
                    + (aPass ? "pass" : "FAIL")
                    + "; Section D (Fig. 10): " + (dPass ? "pass" : "FAIL")
                    + ". Sections B/C (pycalphad) are report-only and do not affect this.");
        }
    }

    // ------------------------------------------------------------------
    // Section A
    // ------------------------------------------------------------------

    private static boolean runSectionA() throws Exception {
        System.out.println("============================================================");
        System.out.println("Section A: V2ZR Gibbs energy vs. Cui et al. 2016, Fig. 9");
        System.out.println("============================================================");

        CefPhaseModelAdapter phase = buildCef(A_TDB, Arrays.asList("V", "ZR"), A_PHASE);
        final double[] y = { 1.0, 0.0, 0.0, 1.0 };   // V:ZR stoichiometric end member

        System.out.printf("%-10s %-16s %-16s %-12s %-8s%n",
                "T [K]", "CEF G [kJ/mol]", "Fig.9 [kJ/mol]", "Abs error", "Result");
        System.out.println("--------------------------------------------------------------------------");

        boolean allPass = true;
        double maxAbsError = 0.0;
        for (double[] point : FIG9_SOLID_CURVE) {
            double T = point[0];
            double gFig9 = point[1];
            double gCefKJ = phase.siteEnergy(T, y) / 1000.0;
            double error = Math.abs(gCefKJ - gFig9);
            maxAbsError = Math.max(maxAbsError, error);
            boolean pass = error <= A_TOLERANCE_KJ;
            allPass &= pass;
            System.out.printf("%-10.2f %-16.4f %-16.4f %-12.4f %-8s%n",
                    T, gCefKJ, gFig9, error, pass ? "PASS" : "FAIL");
        }
        System.out.println();
        System.out.printf("Section A max abs error = %.4f kJ/mol (tolerance = %.1f kJ/mol) -> %s%n",
                maxAbsError, A_TOLERANCE_KJ, allPass ? "PASS" : "FAIL");
        return allPass;
    }

    // ------------------------------------------------------------------
    // Section D -- V-Zr LIQUID enthalpy of mixing (one-sublattice CEF)
    // ------------------------------------------------------------------

    private static boolean runSectionD() throws Exception {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section D: V-Zr LIQUID (1-sublattice CEF) enthalpy of mixing");
        System.out.println("           at " + (int) D_T + " K vs. Cui et al. 2016, Fig. 10");
        System.out.println("============================================================");

        CefPhaseModelAdapter liquid = buildCef(D_TDB, Arrays.asList("V", "ZR"), D_PHASE);

        // Absolute enthalpy H = G - T dG/dT, via the model's own
        // evaluateG / evaluateGT (mole-fraction-facing surface: for a
        // one-sublattice phase the "internal vars" ARE the mole fractions).
        double hV  = pureEnthalpy(liquid, 1.0 - D_EPS, D_EPS);
        double hZr = pureEnthalpy(liquid, D_EPS, 1.0 - D_EPS);
        System.out.printf("  H_V(liquid,  %.0fK) = %.4f J/mol%n", D_T, hV);
        System.out.printf("  H_Zr(liquid, %.0fK) = %.4f J/mol%n", D_T, hZr);
        System.out.printf("  %-8s %-16s %-16s %-12s %-8s%n",
                "xZr", "H_mix code", "Fig.10", "Abs error", "Result");
        System.out.println("  ----------------------------------------------------------------------");

        boolean allPass = true;
        double maxAbs = 0.0;
        for (double[] point : FIG10_SOLID_CURVE) {
            double xZr = point[0];
            double hFig10 = point[1];
            double xV = 1.0 - xZr;
            double hAbs = pureEnthalpy(liquid, xV, xZr);
            double hMix = hAbs - (xV * hV + xZr * hZr);
            double err = Math.abs(hMix - hFig10);
            maxAbs = Math.max(maxAbs, err);
            boolean pass = err <= D_TOLERANCE_J;
            allPass &= pass;
            System.out.printf("  %-8.2f %-16.4f %-16.4f %-12.4f %-8s%n",
                    xZr, hMix, hFig10, err, pass ? "PASS" : "FAIL");
        }
        System.out.println();
        System.out.printf("Section D max abs error = %.4f J/mol (tolerance = %.1f J/mol) -> %s%n",
                maxAbs, D_TOLERANCE_J, allPass ? "PASS" : "FAIL");
        return allPass;
    }

    /**
     * Absolute molar enthalpy at {@link #D_T}: H = G - T dG/dT, computed
     * through the model's {@code evaluateG}/{@code evaluateGT} surface.
     * {@code (xV, xZr)} are the LIQUID's site fractions == mole fractions.
     */
    private static double pureEnthalpy(CefPhaseModelAdapter gm, double xV, double xZr) {
        double[] x = { xV, xZr };
        gm.setTemperature(D_T);
        gm.setInternalVars(x);
        double g = gm.evaluateG(x, D_T);
        double dgdt = gm.evaluateGT();
        return g - D_T * dgdt;
    }

    // ------------------------------------------------------------------
    // Sections B and C -- report-only comparison against pycalphad
    // ------------------------------------------------------------------

    private static void reportAgainstPycalphad(String title, Object[][] rows)
            throws Exception {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(title + "  (report-only)");
        System.out.println("============================================================");

        // Cache one CEF model per (phase, elements) so setModel is not
        // re-run for every row.
        Map<String, CefPhaseModelAdapter> cache = new LinkedHashMap<>();

        System.out.printf("%-11s %-14s %-8s %-20s %-20s %-16s %-12s%n",
                "Phase", "elements", "T [K]", "project G [J/f.u.]",
                "pycalphad G [J/f.u.]", "abs diff", "rel diff");
        System.out.println("--------------------------------------------------------------"
                + "----------------------------------------------------------");

        double sumAbs = 0.0, maxAbs = 0.0, sumRel = 0.0, maxRel = 0.0;
        String maxAbsWhere = "", maxRelWhere = "";
        int n = 0;

        for (Object[] row : rows) {
            String phaseName = (String) row[0];
            String elementsCsv = (String) row[1];
            double T = (Double) row[2];
            double[] y = (double[]) row[3];
            double gRef = (Double) row[4];

            String key = phaseName + "|" + elementsCsv;
            CefPhaseModelAdapter phase = cache.get(key);
            if (phase == null) {
                phase = buildCef(B_TDB, Arrays.asList(elementsCsv.split(",")), phaseName);
                cache.put(key, phase);
            }

            double gProj = phase.siteEnergy(T, y);
            double absDiff = gProj - gRef;
            double relDiff = Math.abs(gRef) > 0 ? absDiff / Math.abs(gRef) : absDiff;

            n++;
            sumAbs += Math.abs(absDiff);
            sumRel += Math.abs(relDiff);
            if (Math.abs(absDiff) > maxAbs) {
                maxAbs = Math.abs(absDiff);
                maxAbsWhere = phaseName + " T=" + (int) T;
            }
            if (Math.abs(relDiff) > maxRel) {
                maxRel = Math.abs(relDiff);
                maxRelWhere = phaseName + " T=" + (int) T;
            }

            System.out.printf("%-11s %-14s %-8.0f %-20.4f %-20.4f %-16.4f %-12.3e%n",
                    phaseName, elementsCsv, T, gProj, gRef, absDiff, relDiff);
        }

        System.out.println();
        System.out.printf("Summary over %d points:%n", n);
        System.out.printf("  mean |abs diff| = %.4f J/f.u.     mean |rel diff| = %.3e%n",
                sumAbs / n, sumRel / n);
        System.out.printf("  max  |abs diff| = %.4f J/f.u. (at %s)%n", maxAbs, maxAbsWhere);
        System.out.printf("  max  |rel diff| = %.3e     (at %s)%n", maxRel, maxRelWhere);
        System.out.println("  (report-only: pycalphad is the reference; these "
                + "differences are the regression signal)");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Build a phase through CalculationSession and assert it is CEF. */
    private static CefPhaseModelAdapter buildCef(String tdb, List<String> elements,
                                                 String phaseName) throws Exception {
        CalculationSession session = new CalculationSession();
        session.setModel(tdb, elements, Arrays.asList(phaseName));
        List<GibbsEnergyModel> models = session.currentSystem().phaseModels();
        if (models.size() != 1) {
            throw new IllegalStateException("Expected exactly one " + phaseName
                    + " model, but obtained " + models.size());
        }
        GibbsEnergyModel model = models.get(0);
        if (!(model instanceof CefPhaseModelAdapter)) {
            throw new IllegalStateException(phaseName
                    + " was not constructed as a CEF model (got "
                    + model.getClass().getSimpleName() + ").");
        }
        return (CefPhaseModelAdapter) model;
    }
}
