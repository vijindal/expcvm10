package system.model;

import system.database.tdb;
import system.database.tdb.Phase;
import system.database.tdb.Parameter;
import system.model.cef.CefEndMember;
import system.model.cef.CefGibbs;
import system.model.cef.CefInteractionParam;
import system.model.cef.CefPhaseModelAdapter;
import system.model.cef.MagneticContribution;
import system.model.cef.SgtePolynomial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Factory for constructing phase Gibbs-energy models from a TDB database.
 *
 * <p>Every phase is represented with the Compound Energy Formalism
 * ({@code CefGibbs}), regardless of sublattice count. A one-sublattice
 * substitutional (Redlich-Kister) solution is the CEF 1-sublattice
 * special case -- end members are the pure elements and interactions are
 * {@code L(A,B;n)} on the single sublattice -- so no separate RK model is
 * needed. The TDB constituent array is preserved when constructing CEF
 * interaction parameters.
 *
 * <p>{@link PhaseModelKind#CVM} is reserved for a future Cluster
 * Variation Method model and currently throws
 * {@link UnsupportedOperationException}.
 */
public class PhaseModelFactory {

    /**
     * Result object returned by build(). Always carries a
     * {@link CefGibbs}.
     */
    public static class PhaseModel {

        public final String phaseName;
        public final CefGibbs gibbs;
        public final MagneticContribution magnetic;
        public final double aff;
        public final double p;

        /**
         * Constituent names per sublattice.
         */
        public final ArrayList<ArrayList<String>> constituentNames;


        public PhaseModel(String phaseName,
                          CefGibbs gibbs,
                          MagneticContribution magnetic,
                          double aff,
                          double p,
                          ArrayList<ArrayList<String>> constituentNames) {

            this.phaseName = phaseName;
            this.gibbs = gibbs;
            this.magnetic = magnetic;
            this.aff = aff;
            this.p = p;
            this.constituentNames = constituentNames;
        }


        public boolean hasMagnetic() {
            return magnetic != null;
        }

        public GibbsEnergyModel toGibbsModel(List<String> elements) {
            return PhaseModelFactory.toGibbsModel(this, elements);
        }
    }


    /**
     * Builds a phase model from the supplied TDB.
     *
     * <p>Equivalent to {@link #build(String, tdb, List, Map, Map, PhaseModelKind)}
     * with {@link PhaseModelKind#AUTO}.
     */
    public static PhaseModel build(String phaseName,
                                   tdb database,
                                   List<String> elements,
                                   Map<String, Double> affMap,
                                   Map<String, Double> pMap) {
        return build(phaseName, database, elements, affMap, pMap, PhaseModelKind.AUTO);
    }

    /**
     * Builds a phase model from the supplied TDB.
     *
     * @param phaseName phase name
     * @param database loaded and element-filtered TDB
     * @param elements ordered system elements
     * @param affMap magnetic A-function map
     * @param pMap magnetic p-function map
     * @param kind which model to use. {@link PhaseModelKind#AUTO} and
     *        {@link PhaseModelKind#CEF} both build a {@code CefGibbs} for
     *        every phase, regardless of sublattice count (a one-sublattice
     *        substitutional phase is the CEF 1-sublattice special case).
     *        {@link PhaseModelKind#CVM} is not implemented and throws
     *        {@link UnsupportedOperationException}.
     * @return constructed phase model
     */
    public static PhaseModel build(String phaseName,
                                   tdb database,
                                   List<String> elements,
                                   Map<String, Double> affMap,
                                   Map<String, Double> pMap,
                                   PhaseModelKind kind) {

        if (database == null)
            throw new IllegalArgumentException("Database must not be null.");

        if (phaseName == null || phaseName.isBlank())
            throw new IllegalArgumentException("Phase name must not be blank.");

        if (elements == null)
            throw new IllegalArgumentException("Element list must not be null.");

        if (kind == PhaseModelKind.CVM)
            throw new UnsupportedOperationException(
                    "CVM model not yet implemented (phase " + phaseName + ")");


        /*
         * ---------------------------------------------------------------
         * 1. Locate phase
         * ---------------------------------------------------------------
         */

        Phase phase = database.getPhase(phaseName);

        if (phase == null)
            throw new IllegalArgumentException(
                    "Phase not found: " + phaseName);


        /*
         * ---------------------------------------------------------------
         * 2. Read CEF structure
         * ---------------------------------------------------------------
         *
         * The CEF path below is fully sublattice-count-generic. A
         * one-sublattice phase is the substitutional case: end members =
         * pure elements, interactions = L(A,B;n) on the single sublattice.
         */

        int ns = phase.getNumSubLat();

        double[] numSites = phase.getNumSites();

        ArrayList<ArrayList<String>> constituentList =
                phase.getConstituentList();

        if (numSites == null || numSites.length != ns)
            throw new IllegalArgumentException(
                    "Invalid site-ratio data for phase " + phaseName);

        if (constituentList == null ||
            constituentList.size() != ns) {

            throw new IllegalArgumentException(
                    "Invalid constituent-list data for phase "
                    + phaseName);
        }


        double[] a = new double[ns];
        int[] nc = new int[ns];

        for (int s = 0; s < ns; s++) {

            if (!Double.isFinite(numSites[s]) ||
                numSites[s] <= 0.0) {

                throw new IllegalArgumentException(
                        "Invalid site ratio for phase "
                        + phaseName +
                        ", sublattice " + s +
                        ": " + numSites[s]);
            }

            if (constituentList.get(s) == null ||
                constituentList.get(s).isEmpty()) {

                throw new IllegalArgumentException(
                        "Empty constituent list for phase "
                        + phaseName +
                        ", sublattice " + s);
            }

            a[s] = numSites[s];
            nc[s] = constituentList.get(s).size();
        }


        /*
         * ---------------------------------------------------------------
         * 4. Construct mixed-radix indexing
         * ---------------------------------------------------------------
         */

        int[] stride = new int[ns];

        stride[0] = 1;

        long totalEMLong = nc[0];

        for (int s = 1; s < ns; s++) {

            long nextStride =
                    (long) stride[s - 1] * nc[s - 1];

            if (nextStride > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "CEF indexing exceeds integer range for phase "
                        + phaseName);

            stride[s] = (int) nextStride;

            totalEMLong *= nc[s];

            if (totalEMLong > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "Too many CEF end members for phase "
                        + phaseName);
        }

        int totalEM = (int) totalEMLong;


        /*
         * ---------------------------------------------------------------
         * 5. Constituent name -> index maps
         * ---------------------------------------------------------------
         */

        List<Map<String, Integer>> constituentIdx =
                new ArrayList<>();

        for (int s = 0; s < ns; s++) {

            Map<String, Integer> map =
                    new java.util.LinkedHashMap<>();

            ArrayList<String> names =
                    constituentList.get(s);

            for (int i = 0; i < names.size(); i++) {

                String name = names.get(i);

                if (name == null || name.isBlank())
                    throw new IllegalArgumentException(
                            "Blank constituent name in phase "
                            + phaseName +
                            ", sublattice " + s);

                String key = name.trim().toUpperCase();

                if (map.put(key, i) != null)
                    throw new IllegalArgumentException(
                            "Duplicate constituent " + key +
                            " on sublattice " + s +
                            " of phase " + phaseName);
            }

            constituentIdx.add(map);
        }


        /*
         * ---------------------------------------------------------------
         * 6. Obtain phase parameters
         * ---------------------------------------------------------------
         */

        ArrayList<String> elementArray =
                new ArrayList<>(elements);

        ArrayList<Parameter> params =
                database.getPhaseParam(
                        elementArray,
                        phaseName);


        /*
         * ---------------------------------------------------------------
         * 7. Allocate end-member array
         * ---------------------------------------------------------------
         *
         * Do NOT initialize missing end members to zero.
         *
         * A missing zeroth-order CEF parameter represents an unassigned
         * end member and must not silently become G = 0.
         */
        CefEndMember[] endMembers =
                new CefEndMember[totalEM];


        /*
         * Magnetic parameters are retained here for compatibility with
         * the existing factory interface. Their integration remains
         * deliberately disabled at this stage.
         */
        double[] tcA = new double[totalEM];
        double[] tcB = new double[totalEM];

        double[] bmA = new double[totalEM];
        double[] bmB = new double[totalEM];


        /*
         * ---------------------------------------------------------------
         * 8. Construct CEF interaction parameters
         * ---------------------------------------------------------------
         */

        List<CefInteractionParam> interactions =
                new ArrayList<>();


        /*
         * ---------------------------------------------------------------
         * 9. Process all G / TC / BMAGN parameters
         * ---------------------------------------------------------------
         */

        for (Parameter param : params) {

            if (param == null)
                continue;

            String type =
                    param.getType() == null
                    ? ""
                    : param.getType().trim().toUpperCase();

            if (!type.equals("G") &&
                !type.equals("TC") &&
                !type.equals("BMAGN")) {

                continue;
            }


            ArrayList<ArrayList<String>> clist =
                    param.getConstituentList();

            if (clist == null || clist.size() != ns)
                throw new IllegalArgumentException(
                        "Parameter constituent array does not match "
                        + "number of sublattices for phase "
                        + phaseName);


            /*
             * Build the complete temperature polynomial.
             */
            SgtePolynomial poly =
                    SgtePolynomial.fromExpList(
                            param.getExpList());

            if (poly == null)
                throw new IllegalArgumentException(
                        "Unable to construct temperature polynomial "
                        + "for parameter in phase "
                        + phaseName);


            /*
             * Parameter order is the TDB RK order.
             *
             * It is NOT the CEF constituent-array order.
             */
            int rkOrder = param.getOrder();


            /*
             * Determine whether this is a zeroth-order end member.
             *
             * A zeroth-order constituent array contains exactly one
             * constituent on every sublattice.
             */
            boolean isEndMember = true;

            for (int s = 0; s < ns; s++) {

                if (clist.get(s) == null ||
                    clist.get(s).size() != 1) {

                    isEndMember = false;
                    break;
                }
            }


            /*
             * -----------------------------------------------------------
             * 9a. Zeroth-order G / TC / BMAGN
             * -----------------------------------------------------------
             */

            if (isEndMember) {

                int em = endMemberIndex(
                        clist,
                        constituentIdx,
                        stride,
                        nc,
                        ns);

                if (em < 0)
                    continue;


                if (type.equals("G")) {

                    endMembers[em] =
                            new CefEndMember(
                                    endMemberIndices(
                                            em,
                                            stride,
                                            nc,
                                            ns),
                                    poly);

                } else if (type.equals("TC")) {

                    double[] ab =
                            effectiveLinearCoefficients(poly);

                    tcA[em] += ab[0];
                    tcB[em] += ab[1];

                } else if (type.equals("BMAGN")) {

                    double[] ab =
                            effectiveLinearCoefficients(poly);

                    bmA[em] += ab[0];
                    bmB[em] += ab[1];
                }

                continue;
            }


            /*
             * -----------------------------------------------------------
             * 9b. Higher-order G parameter
             * -----------------------------------------------------------
             *
             * The complete TDB constituent array is preserved.
             *
             * Examples:
             *
             *   L(FE,V:C,VA;0)
             *
             * becomes factors
             *
             *   (SL0,FE) (SL0,V) (SL1,C) (SL1,VA)
             *
             * and
             *
             *   L(C,CR,FE;0)
             *
             * becomes
             *
             *   (SL0,C) (SL0,CR) (SL0,FE)
             *
             * The TDB order is retained independently as rkOrder.
             */
            if (type.equals("G")) {

                int factorCount = 0;

                for (int s = 0; s < ns; s++)
                    factorCount += clist.get(s).size();


                if (factorCount == 0)
                    throw new IllegalArgumentException(
                            "Empty interaction constituent array "
                            + "in phase " + phaseName);


                int[] factorSL =
                        new int[factorCount];

                int[] factorIdx =
                        new int[factorCount];


                int k = 0;

                for (int s = 0; s < ns; s++) {

                    ArrayList<String> names =
                            clist.get(s);

                    for (String name : names) {

                        if (name == null || name.isBlank())
                            throw new IllegalArgumentException(
                                    "Blank constituent in interaction "
                                    + "of phase " + phaseName);

                        String key =
                                name.trim().toUpperCase();

                        Integer idx =
                                constituentIdx.get(s).get(key);

                        if (idx == null)
                            throw new IllegalArgumentException(
                                    "Constituent " + key +
                                    " in parameter is not present "
                                    + "on sublattice " + s +
                                    " of phase " + phaseName);

                        factorSL[k] = s;
                        factorIdx[k] = idx;
                        k++;
                    }
                }


                interactions.add(
                        new CefInteractionParam(
                                factorSL,
                                factorIdx,
                                rkOrder,
                                poly));
            }
        }


        /*
         * ---------------------------------------------------------------
         * 10. Verify all zeroth-order G parameters
         * ---------------------------------------------------------------
         *
         * Missing end members are not silently assigned G = 0.
         */
        List<String> missingEndMembers =
                new ArrayList<>();

        for (int em = 0; em < totalEM; em++) {

            if (endMembers[em] == null) {

                int[] idx =
                        endMemberIndices(
                                em,
                                stride,
                                nc,
                                ns);

                missingEndMembers.add(
                        formatEndMember(
                                idx,
                                constituentList));
            }
        }

        if (!missingEndMembers.isEmpty()) {

            throw new IllegalArgumentException(
                    "Missing CEF G end-member parameter(s) for phase "
                    + phaseName + ": "
                    + missingEndMembers);
        }


        /*
         * ---------------------------------------------------------------
         * 11. Construct CEF Gibbs model
         * ---------------------------------------------------------------
         */

        CefGibbs gibbs =
                new CefGibbs(
                        a,
                        nc,
                        endMembers,
                        interactions);


        /*
         * ---------------------------------------------------------------
         * 12. Magnetic contribution
         * ---------------------------------------------------------------
         *
         * Deliberately disabled for this CEF exercise. The existing
         * magnetic infrastructure can be connected later without changing
         * the CEF representation.
         */
        MagneticContribution magnetic = null;

        double affVal = 0.0;
        double pVal = 0.0;


        /*
         * ---------------------------------------------------------------
         * 13. Return model
         * ---------------------------------------------------------------
         */

        return new PhaseModel(
                phaseName,
                gibbs,
                magnetic,
                affVal,
                pVal,
                deepCopyConstituentList(constituentList));
    }


    /**
     * Converts a PhaseModel to the GibbsEnergyModel used by the
     * equilibrium solver.
     */
    public static GibbsEnergyModel toGibbsModel(
            PhaseModel pm,
            List<String> elements) {

        if (pm == null)
            throw new IllegalArgumentException(
                    "PhaseModel must not be null.");

        return new CefPhaseModelAdapter(
                pm.gibbs,
                pm.magnetic,
                pm.phaseName,
                new ArrayList<>(elements),
                pm.constituentNames);
    }


    /*
     * =====================================================================
     * Helper methods
     * =====================================================================
     */


    /**
     * Converts a mixed-radix end-member number to constituent indices.
     */
    private static int[] endMemberIndices(
            int em,
            int[] stride,
            int[] nc,
            int ns) {

        int[] idx = new int[ns];

        for (int s = 0; s < ns; s++)
            idx[s] =
                    (em / stride[s]) % nc[s];

        return idx;
    }


    /**
     * Converts a complete TDB zeroth-order constituent array to its
     * mixed-radix end-member index.
     */
    private static int endMemberIndex(
            ArrayList<ArrayList<String>> clist,
            List<Map<String, Integer>> constituentIdx,
            int[] stride,
            int[] nc,
            int ns) {

        int em = 0;

        for (int s = 0; s < ns; s++) {

            if (clist.get(s).size() != 1)
                return -1;

            String name =
                    clist.get(s).get(0);

            if (name == null)
                return -1;

            Integer idx =
                    constituentIdx.get(s)
                            .get(name.trim().toUpperCase());

            if (idx == null)
                return -1;

            if (idx < 0 || idx >= nc[s])
                return -1;

            em += idx * stride[s];
        }

        return em;
    }


    /**
     * Makes an independent copy of the phase constituent structure.
     */
    private static ArrayList<ArrayList<String>>
    deepCopyConstituentList(
            ArrayList<ArrayList<String>> source) {

        ArrayList<ArrayList<String>> copy =
                new ArrayList<>();

        for (ArrayList<String> sl : source)
            copy.add(new ArrayList<>(sl));

        return copy;
    }


    /**
     * Formats an end-member constituent array for diagnostics.
     */
    private static String formatEndMember(
            int[] idx,
            ArrayList<ArrayList<String>> constituentList) {

        StringBuilder sb = new StringBuilder("(");

        for (int s = 0; s < idx.length; s++) {

            if (s > 0)
                sb.append(":");

            sb.append(
                    constituentList
                            .get(s)
                            .get(idx[s]));
        }

        sb.append(")");

        return sb.toString();
    }


    /**
     * Returns the first two effective coefficients of a polynomial.
     *
     * <p>This is used only for the presently unused magnetic arrays.
     * CEF G parameters retain their complete SgtePolynomial.</p>
     */
    private static double[] effectiveLinearCoefficients(
            SgtePolynomial poly) {

        double T1 = 298.15;
        double T2 = 299.15;

        double G1 = poly.G(T1);
        double G2 = poly.G(T2);

        double b = G2 - G1;
        double a = G1 - b * T1;

        return new double[] {a, b};
    }


}