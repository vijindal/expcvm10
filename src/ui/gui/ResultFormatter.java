package ui.gui;

import system.database.tdb.Exp;
import system.database.tdb.Parameter;

import java.util.ArrayList;
import java.util.List;

/** Formats calculation/inspector results into display text for MainFrame's result panels. */
final class ResultFormatter {

    private ResultFormatter() {}

    static String formatComposition(double[] x) {
        if (x == null || x.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < x.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", x[i]));
        }
        return sb.toString();
    }

    static String formatParamText(String phaseName, List<?> params) {
        if (params == null || params.isEmpty()) return "No parameters found for " + phaseName + ".";

        StringBuilder sb = new StringBuilder();
        sb.append("Parameters for ").append(phaseName).append("  (").append(params.size()).append(" total)\n");
        sb.append("─".repeat(80)).append("\n\n");

        for (Object p : params) {
            if (p instanceof Parameter param) {
                appendParameterDetail(sb, param);
            } else {
                sb.append("  ").append(p.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    private static void appendParameterDetail(StringBuilder sb, Parameter param) {
        sb.append("Type:        ").append(param.getType()).append("\n");

        if (param.getParameterId() != null) {
            sb.append("ID:          ").append(param.getParameterId()).append("\n");
        } else if (param.getOrder() > 0) {
            sb.append("Order:       ").append(param.getOrder()).append("\n");
        }

        ArrayList<ArrayList<String>> constList = param.getConstituentList();
        if (constList != null && !constList.isEmpty()) {
            sb.append("Constituents: ");
            for (int i = 0; i < constList.size(); i++) {
                if (i > 0) sb.append(" : ");
                sb.append(String.join(",", constList.get(i)));
            }
            sb.append("\n");
        }

        ArrayList<Exp> expList = param.getExpList();
        if (expList != null && !expList.isEmpty()) {
            sb.append("Temperature ranges and expressions:\n");
            for (Exp exp : expList) {
                if (exp.getTempRange() != null && exp.getTempRange().size() >= 2) {
                    double t1 = exp.getTempRange().get(0);
                    double t2 = exp.getTempRange().get(1);
                    sb.append(String.format("  %.2f – %.2f K: ", t1, t2));
                }
                if (exp.getExpStr() != null) {
                    sb.append(exp.getExpStr());
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
    }
}
