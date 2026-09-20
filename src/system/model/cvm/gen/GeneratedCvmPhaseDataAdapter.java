package system.model.cvm.gen;

import system.model.cvm.CvmPhaseData;

/**
 * Converts a {@link GeneratedCvmGeometry} into a {@link CvmPhaseData}, so
 * the existing, validated {@code system.model.cvm.CvmGibbs} thermodynamic
 * evaluator (S/H/gradient/Hessian) can run against a generated geometry
 * without any change to that evaluator.
 *
 * <p>This is the same reshaping {@code CvmBinaryBccFixture} does by hand
 * for the binary case (see its javadoc: "reshaped into this project's
 * {@code CvmPhaseData}"), generalized to work for any K registered in
 * {@link CvCfBasis}. {@code cfCoeffs} is the identity matrix because this
 * project's generated CVCF basis already produces {@code u} directly --
 * there is no separate orthogonal-basis transform layered on top of it
 * here, exactly as noted in that fixture's javadoc.</p>
 */
public final class GeneratedCvmPhaseDataAdapter {

    private GeneratedCvmPhaseDataAdapter() {}

    public static CvmPhaseData toCvmPhaseData(GeneratedCvmGeometry geo) {
        int nComp = geo.numComponents;
        int ncf = geo.ncf;
        int tcdis = geo.tcdis;
        int uListLen = geo.tcf;

        int[][] lcv = geo.lcv;

        double[][][] wcv = new double[tcdis][][];
        for (int t = 0; t < tcdis; t++) {
            wcv[t] = new double[geo.lc[t]][];
            for (int j = 0; j < geo.lc[t]; j++) {
                int[] wRow = geo.wcv.get(t).get(j);
                double[] wDouble = new double[wRow.length];
                for (int v = 0; v < wRow.length; v++) wDouble[v] = wRow[v];
                wcv[t][j] = wDouble;
            }
        }

        double[][][][] cmat = new double[tcdis][][][];
        for (int t = 0; t < tcdis; t++) {
            cmat[t] = new double[geo.lc[t]][][];
            for (int j = 0; j < geo.lc[t]; j++) {
                cmat[t][j] = geo.cmat.get(t).get(j);
            }
        }

        double[][] cfCoeffs = new double[uListLen][uListLen];
        for (int i = 0; i < uListLen; i++) cfCoeffs[i][i] = 1.0;

        String[] u2Names = geo.basis.cfNames.toArray(new String[0]);
        String[] eNames = geo.basis.cfNames.subList(0, ncf).toArray(new String[0]);

        return new CvmPhaseData(
                geo.structure, nComp, ncf,
                tcdis, geo.mhdis, geo.kb,
                geo.lc, lcv, geo.mh, wcv, cmat,
                uListLen, cfCoeffs,
                u2Names, eNames);
    }
}
