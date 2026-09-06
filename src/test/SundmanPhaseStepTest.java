package test;

import calc.equil.sundman.SundmanPhase;
import calc.equil.sundman.SundmanPhaseStep;
import system.database.TdbParser;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;

import java.util.Arrays;
import java.util.List;

/**
 * M3 Part B: unit test for SundmanPhaseStep, verifying (1) sublattice
 * constraints are respected by the Newton correction Δy, and (2) dMdMu and
 * dGdMu match finite-difference derivatives, for BCC_A2 and V2ZR.
 */
public class SundmanPhaseStepTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        List<String> elements = Arrays.asList("V", "ZR");

        checkPhase("BCC_A2", elements, 1200.0, new double[]{0.8, 0.2, 1.0});
        checkPhase("V2ZR", elements, 1200.0, new double[]{0.6, 0.4, 0.6, 0.4});

        System.out.println();
        System.out.println(failures == 0 ? "ALL SundmanPhaseStep CHECKS PASSED" : failures + " CHECK(S) FAILED");
    }

    private static void checkPhase(String phaseName, List<String> elements, double T, double[] y) throws Exception {
        System.out.println("=== SundmanPhaseStep check: " + phaseName + " y=" + Arrays.toString(y) + " ===");

        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");
        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, Arrays.asList(phaseName));
        PhaseModel pm = models.get(0);
        CefPhaseModelAdapter model = (CefPhaseModelAdapter) PhaseModelFactory.toGibbsModel(pm, elements);

        SundmanPhase phase = new SundmanPhase(model, y, 1.0, true);
        phase.evaluate(T);
        int[][] elMap = model.getElementIndexOnSublattice();
        SundmanPhaseStep step = SundmanPhaseStep.build(phase, elMap, new double[phase.nc]);

        System.out.println("singular=" + step.singular);
        assertTrue(phaseName + ": bordered matrix not singular", !step.singular);

        System.out.println("mA(y) = " + Arrays.toString(phase.mA));
        System.out.println("dMdMu = " + Arrays.deepToString(step.dMdMu));
        System.out.println("dGdMu = " + Arrays.toString(step.dGdMu));

        int nc = phase.nc;
        int[] offs = model.getGibbs().offsets();
        int[] ncSL = model.getGibbs().constituentsPerSublattice();

        // ---- Check 1: sublattice constraints preserved by Delta y ----
        double[] deltaMu = new double[nc];
        deltaMu[0] = 37.0;
        if (nc > 1) deltaMu[1] = -19.0;
        double[] deltaY = step.deltaY(deltaMu, elMap, offs, ncSL);
        System.out.println("deltaMu=" + Arrays.toString(deltaMu));
        System.out.println("deltaY=" + Arrays.toString(deltaY));
        for (int s = 0; s < phase.ns; s++) {
            double sum = 0.0;
            for (int i = 0; i < ncSL[s]; i++) sum += deltaY[offs[s] + i];
            System.out.println("  sublattice " + s + " sum(deltaY) = " + sum);
            assertTrue(phaseName + ": sublattice " + s + " sum(deltaY) ~ 0", Math.abs(sum) < 1e-9);
        }

        // ---- Check 2: dMdMu, dGdMu match finite differences ----
        double h = 1e-2;
        double maxErrM = 0.0, maxErrG = 0.0;
        for (int B = 0; B < nc; B++) {
            double[] dMuPlus = new double[nc];
            double[] dMuMinus = new double[nc];
            dMuPlus[B] = h;
            dMuMinus[B] = -h;
            // deltaY() now returns dyResidual + response(dMu). The
            // residual part is mu-independent, so subtract it to isolate
            // the pure dy/dmu response being tested here.
            double[] dyPlus = step.deltaY(dMuPlus, elMap, offs, ncSL);
            double[] dyMinus = step.deltaY(dMuMinus, elMap, offs, ncSL);
            for (int m = 0; m < dyPlus.length; m++) {
                dyPlus[m] -= step.dyResidual[m];
                dyMinus[m] -= step.dyResidual[m];
            }

            double[] yPlus = y.clone();
            double[] yMinus = y.clone();
            for (int m = 0; m < y.length; m++) {
                yPlus[m] = y[m] + dyPlus[m];
                yMinus[m] = y[m] + dyMinus[m];
            }
            double[] mAPlus = model.computeM(yPlus);
            double[] mAMinus = model.computeM(yMinus);
            double gPlus = model.getGibbs().evaluate(yPlus, T);
            double gMinus = model.getGibbs().evaluate(yMinus, T);

            double fdG = (gPlus - gMinus) / (2 * h);
            maxErrG = Math.max(maxErrG, Math.abs(fdG - step.dGdMu[B]));

            for (int A = 0; A < nc; A++) {
                double fdM = (mAPlus[A] - mAMinus[A]) / (2 * h);
                maxErrM = Math.max(maxErrM, Math.abs(fdM - step.dMdMu[A][B]));
            }
        }
        System.out.println("max |fd dM/dmu - dMdMu| = " + maxErrM);
        System.out.println("max |fd dG/dmu - dGdMu| = " + maxErrG);
        assertTrue(phaseName + ": dMdMu matches finite differences (tol 1e-6)", maxErrM < 1e-6);
        assertTrue(phaseName + ": dGdMu matches finite differences (tol 1e-6)", maxErrG < 1e-6);
        System.out.println();
    }

    private static void assertTrue(String label, boolean cond) {
        if (cond) System.out.println("  PASS: " + label);
        else { System.out.println("  FAIL: " + label); failures++; }
    }
}
