package system.model;

import system.database.tdb;
import system.database.tdb.Phase;
import system.database.tdb.Parameter;
import system.model.GibbsEnergyModel;
import system.model.cef.CefEndMember;
import system.model.cef.CefGibbs;
import system.model.cef.CefInteractionParam;
import system.model.cef.CefPhaseModelAdapter;
import system.model.cef.MagneticContribution;
import system.model.cef.SgtePolynomial;

import java.util.*;

public class PhaseModelFactory {

    /**
     * Result object returned by build().
     * Contains the CEF model and optionally a magnetic contribution.
     */
    public static class PhaseModel {
        public final String phaseName;
        public final CefGibbs gibbs;
        public final MagneticContribution magnetic; // null if not magnetic
        public final double aff;   // 0 if not magnetic
        public final double p;     // 0 if not magnetic

        public PhaseModel(String phaseName, CefGibbs gibbs,
                          MagneticContribution magnetic,
                          double aff, double p) {
            this.phaseName = phaseName;
            this.gibbs     = gibbs;
            this.magnetic  = magnetic;
            this.aff       = aff;
            this.p         = p;
        }

        public boolean hasMagnetic() { return magnetic != null; }
    }

    /**
     * Builds a PhaseModel for the named phase from the tdb.
     *
     * @param phaseName    e.g. "BCC_A2"
     * @param database     fully loaded and element-filtered tdb
     * @param elements     ordered list of elements in the system
     * @param affMap       map from phaseName -> aff value (from TYPE_DEFINITION)
     * @param pMap         map from phaseName -> p value (from TYPE_DEFINITION)
     * @return             PhaseModel containing CefGibbs + optional magnetic
     * @throws IllegalArgumentException if phase not found in database
     */
    public static PhaseModel build(String phaseName,
                                   tdb database,
                                   List<String> elements,
                                   Map<String, Double> affMap,
                                   Map<String, Double> pMap) {

        // 1. Get Phase record
        Phase phase = database.getPhase(phaseName);
        if (phase == null)
            throw new IllegalArgumentException("Phase not found: " + phaseName);

        // 2. Read sublattice structure
        int ns = phase.getNumSubLat();
        double[] numSites = phase.getNumSites();        // stoichiometric coefficients
        ArrayList<ArrayList<String>> constituentList = phase.getConstituentList();

        double[] a = new double[ns];
        int[] nc   = new int[ns];
        for (int s = 0; s < ns; s++) {
            a[s]  = numSites[s];
            nc[s] = constituentList.get(s).size();
        }

        // 3. Compute flat offsets and total end-member count
        int[] offset = new int[ns];
        offset[0] = 0;
        for (int s = 1; s < ns; s++)
            offset[s] = offset[s - 1] + nc[s - 1];
        int totalEM = 1;
        for (int n : nc) totalEM *= n;

        // 4. Build constituent index maps: name -> index per sublattice
        //    constituentIdx[s] maps constituent name to its index within sublattice s
        List<Map<String, Integer>> constituentIdx = new ArrayList<>();
        for (int s = 0; s < ns; s++) {
            Map<String, Integer> map = new LinkedHashMap<>();
            ArrayList<String> constNames = constituentList.get(s);
            for (int i = 0; i < constNames.size(); i++)
                map.put(constNames.get(i).toUpperCase(), i);
            constituentIdx.add(map);
        }

        // 5. Get all G parameters for this phase
        ArrayList<String> elArr = new ArrayList<>(elements);
        ArrayList<Parameter> params = database.getPhaseParam(elArr, phaseName);

        // 6. Build end-member array (size = totalEM, initialized to zero G)
        //    Mixed-radix order: sublattice 0 least significant
        //    stride[s] = product of nc[0..s-1]
        int[] stride = new int[ns];
        stride[0] = 1;
        for (int s = 1; s < ns; s++) stride[s] = stride[s-1] * nc[s-1];

        CefEndMember[] endMembers = new CefEndMember[totalEM];
        // Initialize all end members to zero
        for (int em = 0; em < totalEM; em++) {
            int[] idx = new int[ns];
            for (int s = 0; s < ns; s++) idx[s] = (em / stride[s]) % nc[s];
            endMembers[em] = new CefEndMember(idx, 0.0, 0.0);
        }

        // 7. Build interaction parameter list
        List<CefInteractionParam> interactions = new ArrayList<>();

        // 8. Process G parameters — separate end-members from interactions
        //    Also collect TC and BMAGN parameters for magnetic phases
        //    TC[em] and BMAGN[em] stored parallel to endMembers[]
        double[] tcA  = new double[totalEM];  // TC constant term per end-member
        double[] tcB  = new double[totalEM];  // TC linear term per end-member
        double[] bmA  = new double[totalEM];  // BMAGN constant per end-member
        double[] bmB  = new double[totalEM];  // BMAGN linear per end-member

        for (Parameter param : params) {
            String type = param.getType().toUpperCase();
            if (!type.equals("G") && !type.equals("TC") && !type.equals("BMAGN"))
                continue;

            ArrayList<ArrayList<String>> clist = param.getConstituentList();
            int order = param.getOrder();

            // Determine if this is an end-member or interaction parameter
            // End-member: exactly one constituent per sublattice in clist
            // Interaction: at least one sublattice has two constituents listed

            boolean isEndMember = true;
            for (ArrayList<String> sl : clist) {
                if (sl.size() != 1) { isEndMember = false; break; }
            }

            // Build full multi-range SGTE polynomial
            SgtePolynomial paramPoly = SgtePolynomial.fromExpList(param.getExpList());
            // Extract a and b at reference T=298.15 for fallback storage
            // CefEndMember stores the polynomial directly via constructor
            double coeffA = 0.0, coeffB = 0.0;
            if (paramPoly != null) {
                // Evaluate at two points to extract effective a + b*T
                // This is a temporary approximation —
                // CefEndMember will store the full polynomial when available
                double T1 = 298.15, T2 = 299.15;
                double G1 = paramPoly.G(T1), G2 = paramPoly.G(T2);
                coeffB = (G2 - G1) / (T2 - T1);
                coeffA = G1 - coeffB * T1;
            }

            if (isEndMember) {
                // Find the end-member index in mixed-radix order
                int em = 0;
                boolean valid = true;
                for (int s = 0; s < ns; s++) {
                    String constName = clist.get(s).get(0).toUpperCase();
                    Integer idx = constituentIdx.get(s).get(constName);
                    if (idx == null) { valid = false; break; }
                    em += idx * stride[s];
                }
                if (!valid) continue;

                if (type.equals("G")) {
                    int[] idxArr = new int[ns];
                    for (int s = 0; s < ns; s++)
                        idxArr[s] = (em / stride[s]) % nc[s];
                    SgtePolynomial poly =
                        SgtePolynomial.fromExpList(param.getExpList());
                    if (poly != null) {
                        endMembers[em] = new CefEndMember(idxArr, poly);
                    } else {
                        endMembers[em] = new CefEndMember(idxArr, coeffA, coeffB);
                    }
                } else if (type.equals("TC")) {
                    tcA[em] += coeffA; tcB[em] += coeffB;
                } else if (type.equals("BMAGN")) {
                    bmA[em] += coeffA; bmB[em] += coeffB;
                }
            } else {
                // Interaction parameter — only handle 2-sublattice interactions
                // Find which sublattice has the pair and which has the single
                if (type.equals("G") && ns >= 2) {
                    int pairSL = -1, singleSL = -1;
                    for (int s = 0; s < ns; s++) {
                        if (clist.get(s).size() == 2 && pairSL == -1) pairSL = s;
                        else if (clist.get(s).size() == 1 && singleSL == -1) singleSL = s;
                    }
                    if (pairSL >= 0 && singleSL >= 0) {
                        String nameA = clist.get(pairSL).get(0).toUpperCase();
                        String nameB = clist.get(pairSL).get(1).toUpperCase();
                        String nameK = clist.get(singleSL).get(0).toUpperCase();
                        Integer idxA = constituentIdx.get(pairSL).get(nameA);
                        Integer idxB = constituentIdx.get(pairSL).get(nameB);
                        Integer idxK = constituentIdx.get(singleSL).get(nameK);
                        if (idxA != null && idxB != null && idxK != null) {
                            interactions.add(new CefInteractionParam(
                                pairSL, idxA, idxB, idxK, coeffA, coeffB));
                        }
                    }
                }
            }
        }

        // 9. Build CefGibbs
        CefGibbs gibbs = new CefGibbs(a, nc, endMembers, interactions);

        // 10. Build MagneticContribution if this phase has MAGNETIC type definition
        MagneticContribution magnetic = null;
        double affVal = 0.0, pVal = 0.0;
        if (affMap.containsKey(phaseName) && pMap.containsKey(phaseName)) {
            affVal = affMap.get(phaseName);
            pVal   = pMap.get(phaseName);
            magnetic = MagneticContribution.fromTypeDefinition(affVal, pVal);
        }

        return new PhaseModel(phaseName, gibbs, magnetic, affVal, pVal);
    }

    /**
     * Convert a PhaseModel to a GibbsEnergyModel for use by EquilibriumSolver.
     *
     * @param pm       the PhaseModel result from build()
     * @param elements list of element symbols for this phase
     * @return CefPhaseModelAdapter wrapping the CEF model
     */
    public static GibbsEnergyModel toGibbsModel(PhaseModel pm,
                                                List<String> elements) {
        return new CefPhaseModelAdapter(
            pm.gibbs,
            pm.magnetic,
            pm.phaseName,
            new ArrayList<>(elements));
    }
}
