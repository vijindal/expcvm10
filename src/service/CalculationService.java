package service;

import calbince.*;
import database.tdb;
import domain.ThermoCondition;
import domain.DatabasePort;
import infra.AppLevel;
import infra.Trace;
import infra.TdbParser;
import phase.calphad.RKPhaseGeneral;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application service facade for thermodynamic calculation workflows.
 * Wraps the existing calculate, CalModel, and Methods logic.
 */
public class CalculationService {

    private static final Logger LOG = Logger.getLogger(CalculationService.class.getName());
    private final DatabasePort databasePort;

    /**
     * Constructor with dependency injection.
     * @param databasePort port for loading TDB databases
     */
    public CalculationService(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /**
     * Execute a single-point or method-based calculation from a CalculationRequest.
     */
    public CalculationResult runCalculation(CalculationRequest request) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "runCalculation");
        LOG.log(AppLevel.RESULT, "runCalculation: method={0}, T={1}, P={2}",
                new Object[]{request.getMethod(), request.getT(), request.getP()});
        CalculationResult result = new CalculationResult();
        result.setMethod(request.getMethod());

        // Load database
        String currentDirectory = System.getProperty("user.dir");
        String tdbPath = request.getTdbFilePath();
        if (tdbPath == null || tdbPath.isEmpty()) {
            LOG.log(AppLevel.WARN, "No TDB file path specified in request.");
            result.setSuccess(false);
            result.setMessage("No TDB file path specified.");
            return result;
        }
        LOG.log(AppLevel.FLOW, "Loading TDB: {0}", tdbPath);
        databasePort.load(tdbPath);
        String[] elementArray = request.getElements().toArray(new String[0]);
        DatabasePort systemPort = databasePort.extractSystem(elementArray);
        // Temporary bridge: legacy code still expects tdb objects
        tdb systdb = ((TdbParser) systemPort).getUnderlyingTdb();

        // Build CalVars
        CalVars calvars = new CalVars(systdb);
        CalcSet calcSet = new CalcSet();
        calcSet.setElementNames(request.getElements());

        CalcType calcType = new CalcType();
        calcType.setMethod(request.getMethod());
        calcType.setPhases(request.getPhases());

        Condition condition = new Condition(request.getT(), request.getP(), request.getCompositions());
        calcType.addConditions(condition);
        calcSet.addCalcType(calcType);
        calvars.addcalcSet(calcSet);

        // Run calculation
        calculate cal = new calculate(calvars);
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "calculate.cal");
        cal.cal();
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "calculate.cal");

        // Map a numeric result into DTO for GUI/CLI display.
        // Support single-point RK calculations for LIQUID phase
        double computedValue = Double.NaN;
        if (request.getPhases() != null && !request.getPhases().isEmpty()) {
            String primaryPhase = request.getPhases().get(0);
            if ("LIQUID".equalsIgnoreCase(primaryPhase)) {
                try {
                    ArrayList<tdb.Parameter> params = systdb.getPhaseParam(request.getElements(), primaryPhase);
                    if (params != null && !params.isEmpty()) {
                        computedValue = calculateRKSinglePoint(
                            params,
                            request.getElements(),
                            request.getT(),
                            request.getP(),
                            request.getCompositions(),
                            request.getMethod()
                        );
                    }
                } catch (Exception ex) {
                    LOG.log(AppLevel.WARN, "RK calculation failed: {0}", ex.getMessage());
                    computedValue = Double.NaN;
                }
            }
        }
        LOG.log(AppLevel.RESULT, "Computed value: {0}", computedValue);

        result.setTemperature(request.getT());
        result.setPressure(request.getP());
        result.setValue(computedValue);
        if (request.getCompositions() != null && !request.getCompositions().isEmpty()) {
            ArrayList<Double> x = request.getCompositions().get(0);
            double[] xOut = new double[x.size()];
            for (int i = 0; i < x.size(); i++) {
                xOut[i] = x.get(i);
            }
            result.setCompositionResult(xOut);
        }
        result.setMessage(Double.isNaN(computedValue)
                ? "Calculation completed (numeric value unavailable for selected phase/method)."
                : "Calculation completed.");
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "runCalculation");
        return result;
    }

    /**
     * Inspect TDB and return model metadata for GUI/CLI.
     */
    public ModelInfo inspectModel(String tdbPath, String[] elements) {
                // DEBUG: Log all phase names from TDB after loading
                if (databasePort instanceof infra.TdbParser) {
                    java.util.List<String> debugPhases = ((infra.TdbParser) databasePort).getPhaseNames();
                    LOG.log(AppLevel.RESULT, "DEBUG: All phases in TDB: {0}", debugPhases);
                }
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "inspectModel");
        ModelInfo info = new ModelInfo();
        info.setFilePath(tdbPath);
        File file = new File(tdbPath);
        info.setFileExists(file.exists());
        info.setLastModifiedEpochMillis(info.isFileExists() ? file.lastModified() : 0L);
        info.setDetectedElements(java.util.Arrays.asList(elements));

        if (!info.isFileExists()) {
            info.setError("TDB file not found.");
            info.setAvailablePhases(Collections.<String>emptyList());
            return info;
        }

        try {
            databasePort.load(tdbPath);
            // Get all available elements from the loaded TDB (not just the input system)
            java.util.List<String> allElements = null;
            if (databasePort instanceof infra.TdbParser) {
                allElements = ((infra.TdbParser) databasePort).getElementNames();
            } else {
                allElements = java.util.Collections.emptyList();
            }
            info.setAvailableElements(allElements);
            if (elements != null && elements.length > 0) {
                DatabasePort systemPort = databasePort.extractSystem(elements);
                info.setAvailablePhases(systemPort.getPhaseNames());
                LOG.log(AppLevel.RESULT, "inspectModel found phases: {0}", info.getAvailablePhases());
            } else {
                // Show all phases in the TDB if no elements specified
                if (databasePort instanceof infra.TdbParser) {
                    info.setAvailablePhases(((infra.TdbParser) databasePort).getPhaseNames());
                } else {
                    info.setAvailablePhases(Collections.<String>emptyList());
                }
            }
        } catch (Exception ex) {
            info.setAvailablePhases(Collections.<String>emptyList());
            info.setAvailableElements(Collections.<String>emptyList());
            info.setError(ex.getMessage());
            LOG.log(AppLevel.WARN, "inspectModel error", ex);
        }
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "inspectModel");
        return info;
    }

    /**
     * Run the legacy CalModel-based calculation from data files.
     */
    public CalculationResult runCalModel(String exptDataFile, String phaseDataFile) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "runCalModel");
        CalculationResult result = new CalculationResult();

        PhaseData phasedata = new PhaseData(phaseDataFile);
        phasedata.readPhaseDataInputFile();

        ExptData exptdata = new ExptData(exptDataFile);
        exptdata.readExptDataFile();

        ExptData recordData = new ExptData();
        CalModel calmodel = new CalModel(exptdata, phasedata, recordData);
        calmodel.Run();

        result.setMessage("CalModel calculation completed.");
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "runCalModel");
        return result;
    }

    /**
     * Calculate RK property at a single point using RKPhaseGeneral.
     * Converts tdb.Parameter list to RKPhaseGeneral.Interaction format and computes
     * G (total Gibbs energy) or Gm (Gibbs energy of mixing) based on method.
     *
     * @param params List of tdb.Parameter (RK L0, L1, ... parameters)
     * @param elements Component names in order
     * @param T Temperature (K)
     * @param P Pressure (Pa)
     * @param compositions List of composition lists [[x0, x1, ...]]
     * @param method "G", "Gm", "HM", etc.
     * @return Computed property value, or NaN if calculation fails
     */
    private double calculateRKSinglePoint(ArrayList<tdb.Parameter> params,
                                          ArrayList<String> elements,
                                          double T, double P,
                                          List<? extends List<Double>> compositions,
                                          String method) throws IOException {
        if (params == null || params.isEmpty() || elements == null || elements.isEmpty()) {
            return Double.NaN;
        }
        if (compositions == null || compositions.isEmpty()) {
            return Double.NaN;
        }

        int numComp = elements.size();
        ArrayList<Double> xFull = new ArrayList<>(compositions.get(0));

        // Handle composition size mismatch - take first numComp entries
        ArrayList<Double> x = new ArrayList<>();
        if (xFull.size() < numComp) {
            LOG.log(AppLevel.WARN, "Composition size {0} insufficient for {1} components",
                    new Object[]{xFull.size(), numComp});
            return Double.NaN;
        }
        if (xFull.size() > numComp) {
            LOG.log(AppLevel.INFO, "Composition size {0} > components {1}; using first {1} entries",
                    new Object[]{xFull.size(), numComp});
            for (int i = 0; i < numComp; i++) {
                x.add(xFull.get(i));
            }
        } else {
            x = xFull;
        }

        // Verify composition sums to approximately 1.0
        double xSum = x.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(xSum - 1.0) > 0.01) {
            LOG.log(AppLevel.WARN, "Composition does not sum to 1.0: sum={0}", xSum);
        }

        // Convert tdb.Parameter (RK interaction terms) to RKPhaseGeneral.Interaction format
        List<RKPhaseGeneral.Interaction> interactions = convertParametersToInteractions(
            params, elements, T, numComp);

        if (interactions.isEmpty()) {
            LOG.log(AppLevel.INFO, "No RK L parameters found - using ideal solution (Gex=0)");
            // This is valid: no binary interactions means ideal solution
        }

        // Extract G0 values for pure components at temperature T
        double[] G0List = extractG0Values(params, elements, T);
        if (G0List == null || G0List.length < numComp) {
            LOG.log(AppLevel.WARN, "Could not extract G0 values for all components");
            return Double.NaN;
        }

        // Create RKPhaseGeneral model
        RKPhaseGeneral rkModel = new RKPhaseGeneral(numComp, interactions);
        rkModel.setT(T);
        rkModel.setP(P);
        rkModel.setX(x);
        rkModel.setG0List(G0List);

        // Compute and return property
        double result = Double.NaN;
        if ("Gm".equalsIgnoreCase(method)) {
            result = rkModel.calGm();
            LOG.log(AppLevel.RESULT, "RK Gm (mixing): T={0}, P={1}, x={2}, Gm={3}",
                    new Object[]{T, P, x, result});
        } else if ("G".equalsIgnoreCase(method)) {
            result = rkModel.calG();
            LOG.log(AppLevel.RESULT, "RK G (total): T={0}, P={1}, x={2}, G={3}",
                    new Object[]{T, P, x, result});
        } else if ("HM".equalsIgnoreCase(method)) {
            // HM (enthalpy of mixing) - approximated as Gm for now
            result = rkModel.calGm();
            LOG.log(AppLevel.RESULT, "RK HM (approx as Gm): T={0}, P={1}, x={2}, result={3}",
                    new Object[]{T, P, x, result});
        } else {
            result = rkModel.calG(); // default to G
            LOG.log(AppLevel.RESULT, "RK G (default): T={0}, P={1}, x={2}, G={3}",
                    new Object[]{T, P, x, result});
        }

        return result;
    }

    /**
     * Extract G0 (Gibbs energy of pure components) values from parameters at temperature T.
     * Looks for "G" type parameters with single-component constituents (e.g., [[Ti]], [[Zr]]).
     *
     * @param params List of tdb.Parameter objects
     * @param elements Component names in order (e.g., ["Ti", "Zr"])
     * @param T Temperature (K)
     * @return Array of G0 values indexed by component, or null if extraction fails
     */
    private double[] extractG0Values(ArrayList<tdb.Parameter> params,
                                     ArrayList<String> elements,
                                     double T) {
        int numComp = elements.size();
        double[] G0 = new double[numComp];
        java.util.Arrays.fill(G0, Double.NaN);

        for (tdb.Parameter param : params) {
            // Look for pure component G parameters (type "G", single component in constituent)
            if (!"G".equals(param.getType())) {
                continue;
            }

            ArrayList<ArrayList<String>> constituents = param.getConstituentList();
            if (constituents == null || constituents.isEmpty()) {
                continue;
            }

            // Check if this is a single-component parameter (pure component)
            ArrayList<String> firstSublat = constituents.get(0);
            if (firstSublat == null || firstSublat.isEmpty() || firstSublat.size() != 1) {
                continue; // Not a pure component parameter
            }

            String compName = firstSublat.get(0);
            int compIdx = elements.indexOf(compName);
            if (compIdx < 0) {
                continue; // Component not in our system
            }

            // Extract G0 value at temperature T from the Exp list
            ArrayList<tdb.Exp> expList = param.getExpList();
            if (expList != null && !expList.isEmpty()) {
                tdb.Exp exp = expList.get(0);
                ArrayList<Double> coeffs = exp.getSubCoeffList();
                if (coeffs != null && !coeffs.isEmpty()) {
                    // subCoeffList contains: [c0, c1, c2, c3, ...]
                    // where G0(T) = c0 + c1*T + c2*T^2 + c3*T^3 + ...
                    double G0val = coeffs.get(0);
                    for (int k = 1; k < coeffs.size(); k++) {
                        G0val += coeffs.get(k) * Math.pow(T, k);
                    }
                    G0[compIdx] = G0val;
                    LOG.log(AppLevel.RESULT, "  G0[{0}]({1}) = {2}",
                            new Object[]{compName, T, G0val});
                }
            }
        }

        // Verify all components have G0 values
        for (int i = 0; i < numComp; i++) {
            if (Double.isNaN(G0[i])) {
                LOG.log(AppLevel.WARN, "G0 not found for component {0}", elements.get(i));
                return null;
            }
        }

        return G0;
    }

    /**
     * Convert tdb.Parameter list (RK L0, L1, ... interaction parameters)
     * to RKPhaseGeneral.Interaction format.
     *
     * RK parameters in CALPHAD TDB:
     * - Parameter type "L", order 0/1/2... for L0/L1/L2...
     * - constituentList specifies component pairs (e.g., [[Ti], [Zr]] for binary)
     * - Each Exp in expList contains coefficients for a_k + b_k*T form
     *
     * @param params List of tdb.Parameter objects
     * @param elements Component names in order (e.g., ["Ti", "Zr"])
     * @param T Temperature (for reference, though interactions are T-dependent)
     * @param numComp Number of components
     * @return List of RKPhaseGeneral.Interaction objects
     */
    private List<RKPhaseGeneral.Interaction> convertParametersToInteractions(
            ArrayList<tdb.Parameter> params,
            ArrayList<String> elements,
            double T,
            int numComp) {

        List<RKPhaseGeneral.Interaction> interactions = new ArrayList<>();

        // Group parameters by (i, j) pair to collect L0, L1, L2, etc. for each interaction
        java.util.Map<String, InteractionData> interactionMap = new java.util.HashMap<>();

        LOG.log(AppLevel.RESULT, "convertParametersToInteractions: total params={0}, looking for type=L",
                params.size());

        for (tdb.Parameter param : params) {
            String paramType = param.getType();
            LOG.log(AppLevel.RESULT, "  param type={0}, order={1}, constituents={2}",
                    new Object[]{paramType, param.getOrder(), param.getConstituentList()});

            // Only process RK interaction parameters (type "L")
            if (!"L".equals(paramType)) {
                continue;
            }

            ArrayList<ArrayList<String>> constituents = param.getConstituentList();
            if (constituents == null || constituents.size() < 2) {
                continue; // Need at least 2 components for binary interaction
            }

            // Extract component names from first two sublattices
            String comp1Str = constituents.get(0).isEmpty() ? null : constituents.get(0).get(0);
            String comp2Str = constituents.get(1).isEmpty() ? null : constituents.get(1).get(0);

            if (comp1Str == null || comp2Str == null) {
                continue;
            }

            // Find component indices in the elements list
            int i = elements.indexOf(comp1Str);
            int j = elements.indexOf(comp2Str);

            if (i < 0 || j < 0 || i == j) {
                continue; // Invalid component reference or same component
            }

            // Ensure i < j for consistency
            if (i > j) {
                int tmp = i;
                i = j;
                j = tmp;
            }

            // Create final copies for lambda expression
            final int compI = i;
            final int compJ = j;
            String key = compI + ":" + compJ;

            // Get or create interaction data for this pair
            InteractionData intData = interactionMap.computeIfAbsent(key,
                k -> new InteractionData(compI, compJ));

            // Add coefficients from this parameter's expression
            // param.getOrder() indicates L0 (0), L1 (1), L2 (2), etc.
            // subCoeffList contains [a_0, b_0, a_1, b_1, ...]
            ArrayList<tdb.Exp> expList = param.getExpList();
            if (expList != null && !expList.isEmpty()) {
                tdb.Exp exp = expList.get(0);
                ArrayList<Double> coeffs = exp.getSubCoeffList();
                if (coeffs != null && !coeffs.isEmpty()) {
                    intData.addOrderCoefficients(param.getOrder(), coeffs);
                }
            }
        }

        // Convert InteractionData objects to RKPhaseGeneral.Interaction
        for (InteractionData intData : interactionMap.values()) {
            RKPhaseGeneral.Interaction inter = intData.buildInteraction();
            if (inter != null) {
                interactions.add(inter);
            }
        }

        return interactions;
    }

    /**
     * Helper class to accumulate RK coefficients for an interaction pair (i, j).
     * Collects a[] and b[] arrays from multiple parameters (L0, L1, L2, ...).
     */
    private static class InteractionData {
        int i, j;
        java.util.Map<Integer, ArrayList<Double>> orderCoefficients = new java.util.HashMap<>();

        InteractionData(int i, int j) {
            this.i = i;
            this.j = j;
        }

        void addOrderCoefficients(int order, ArrayList<Double> coeffs) {
            if (coeffs == null || coeffs.isEmpty()) {
                return;
            }
            // Store coefficients indexed by order (L0 → order 0, L1 → order 1, etc.)
            orderCoefficients.put(order, new ArrayList<>(coeffs));
        }

        RKPhaseGeneral.Interaction buildInteraction() {
            if (orderCoefficients.isEmpty()) {
                return null;
            }

            // Determine maximum order to size arrays
            int maxOrder = orderCoefficients.keySet().stream()
                .mapToInt(Integer::intValue).max().orElse(0);

            // Initialize a[] and b[] arrays
            double[] a = new double[maxOrder + 1];
            double[] b = new double[maxOrder + 1];

            // Fill a[] and b[] from coefficients
            // For each order k, coeffs list has [a_k, b_k, a_{k+1}, b_{k+1}, ...]
            // But we collect order by order, so coeffs[0] = a_k, coeffs[1] = b_k
            for (int order = 0; order <= maxOrder; order++) {
                ArrayList<Double> coeffs = orderCoefficients.get(order);
                if (coeffs != null && coeffs.size() >= 2) {
                    a[order] = coeffs.get(0);
                    b[order] = coeffs.get(1);
                } else if (coeffs != null && coeffs.size() == 1) {
                    // Single coefficient (treat as a_k, b_k = 0)
                    a[order] = coeffs.get(0);
                    b[order] = 0.0;
                }
            }

            return new RKPhaseGeneral.Interaction(i, j, a, b);
        }
    }
}
