package system.model.cvm;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Parser for Mathematica {@code .nb} output files produced by the CVM pre-computation
 * pipeline. Converts the nested-list text format into a {@link CvmPhaseData} object.
 *
 * <h2>File format</h2>
 * The file contains a single Mathematica expression with three outer blocks:
 * <pre>
 *   {{block1...}, {block2...}, {block3...}}
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   CvmPhaseData data = CvmPhaseDataParser.parse("output_BCC_A2_bin.nb");
 * </pre>
 *
 * <h2>Supported files</h2>
 * Tested with {@code output_BCC_A2_bin.nb} (binary BCC_A2).
 * The parser is designed to handle binary, ternary, and quaternary files
 * with the same structural layout.
 */
public final class CvmPhaseDataParser {

    private CvmPhaseDataParser() {}

    /**
     * Parses a {@code .nb} file at the given path.
     *
     * @param filePath  path to the Mathematica output .nb file
     * @return          parsed {@link CvmPhaseData}
     * @throws IOException if the file cannot be read
     * @throws ParseException if the file format is unexpected
     */
    public static CvmPhaseData parse(String filePath) throws IOException {
        String content = Files.readString(Path.of(filePath));
        return parseContent(content);
    }

    /**
     * Parses a {@code .nb} file from a {@link File} reference.
     */
    public static CvmPhaseData parse(File file) throws IOException {
        String content = Files.readString(file.toPath());
        return parseContent(content);
    }

    /**
     * Parses the .nb content string.
     * Package-visible for testing.
     */
    static CvmPhaseData parseContent(String content) {
        // Normalise: remove Windows line endings, collapse whitespace
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        Tokenizer tok = new Tokenizer(content);
        List<Object> top = tok.parseList();  // outermost {{ ... }, { ... }, { ... }}

        // The top level is a list of 3 blocks
        @SuppressWarnings("unchecked")
        List<Object> block1 = (List<Object>) top.get(0);
        @SuppressWarnings("unchecked")
        List<Object> block2 = (List<Object>) top.get(1);
        @SuppressWarnings("unchecked")
        List<Object> block3 = (List<Object>) top.get(2);

        return buildPhaseData(block1, block2, block3);
    }

    // ------------------------------------------------------------------
    // Block extraction
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static CvmPhaseData buildPhaseData(
            List<Object> b1, List<Object> b2, List<Object> b3) {

        // ── Block 3: identifiers and variable rules (read first for sizes) ──
        List<Object> u2ListRaw  = (List<Object>) b3.get(0);  // {v4AB, v3AB, ...}
        List<Object> cfRulesRaw = (List<Object>) b3.get(1);  // {u[1]->..., ...}
        List<Object> randRules  = (List<Object>) b3.get(2);  // {v4AB->XA^2*XB^2, ...}
        List<Object> eListRaw   = (List<Object>) b3.get(3);  // {e4AB, e3AB, ...}
        int nComp     = intOf(b3.get(4));
        String phName = symbolOf(b3.get(5));

        String[] u2Names = u2ListRaw.stream().map(CvmPhaseDataParser::symbolOf)
                                    .toArray(String[]::new);
        String[] eNames  = eListRaw.stream().map(CvmPhaseDataParser::symbolOf)
                                    .toArray(String[]::new);
        int ncf      = eNames.length;
        int uListLen = u2Names.length;   // ncf + nComp

        // cfCoeffs: parse replaceCFRules into a coefficient matrix
        // Each rule is "u[i][1][1] -> linear_expression_in_u2List"
        double[][] cfCoeffs = parseCfCoeffs(cfRulesRaw, u2Names, uListLen);

        // ── Block 1: disordered cluster structure ──
        int    tcdis = intOf(b1.get(0));
        // b1.get(1) = nxcdis, b1.get(2) = ncdis — not needed numerically
        double[] mhdis   = doubleArray((List<Object>) b1.get(3));
        // b1.get(4) = rcdis — not needed
        // b1.get(5) = nijTable — not needed (used only in cluster algebra, not Gibbs)
        double[] kbdis   = parseKbdis((List<Object>) b1.get(6), mhdis);

        // ── Block 2: CVM structure ──
        int[] lc     = intArray((List<Object>) b2.get(0));
        // b2.get(1)=tc, get(2)=nxc, get(3)=nc, get(4)=rc, get(5)=mh_cluster, get(6)=lcf,
        // get(7)=tcf, get(8)=nxcf, get(9)=ncf_check, get(10)=rcf
        int ncfCheck = intOf(b2.get(9));
        if (ncfCheck != ncf) throw new ParseException(
                "ncf mismatch: block3 says " + ncf + " but block2[[10]] says " + ncfCheck);

        double[][] mh     = mhFromBlock2(b2, lc, tcdis);
        int[][]    lcv    = lcvFromBlock2(b2, lc, tcdis);     // b2.get(13)
        double[][][] wcv  = wcvFromBlock2(b2, lc, lcv, tcdis); // b2.get(12)
        double[][][][] cmat = cmatFromBlock2(b2, lc, lcv, tcdis, uListLen); // b2.get(14)

        return new CvmPhaseData(
                phName, nComp, ncf,
                tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs,
                u2Names, eNames);
    }

    // ------------------------------------------------------------------
    // kbdis: evaluate Boltzmann weight expressions substituting ms[i] → mhdis[i]
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static double[] parseKbdis(List<Object> kbRaw, double[] mhdis) {
        // kbRaw is a list of expressions; each may be a number or a symbolic rational
        // involving ms[1], ms[2], ... which we substitute with mhdis values
        // Since we can't evaluate symbolic expressions directly, we compute from the
        // known formula structure for BCC-type phases
        //
        // For the general case, the expressions are rational functions of ms[i].
        // The parser stores them as raw tokens; we evaluate them numerically by
        // substituting ms[i] = mhdis[i-1] into the rational expression string.

        double[] kb = new double[kbRaw.size()];
        for (int i = 0; i < kbRaw.size(); i++) {
            Object item = kbRaw.get(i);
            if (item instanceof Number) {
                kb[i] = ((Number) item).doubleValue();
            } else {
                // Symbolic expression like "1. - (4.*ms[1])/ms[2]"
                // Convert to double by substituting ms[k] = mhdis[k-1]
                kb[i] = evalMsExpr(item.toString(), mhdis);
            }
        }
        return kb;
    }

    /** Evaluates a kbdis expression by substituting ms[i] = mhdis[i-1]. */
    private static double evalMsExpr(String expr, double[] mhdis) {
        // Replace ms[k] tokens with numeric values, then evaluate
        String e = expr.trim();
        for (int k = mhdis.length; k >= 1; k--) {
            e = e.replace("ms[" + k + "]", Double.toString(mhdis[k - 1]));
        }
        return evalArith(e);
    }

    /** Simple arithmetic evaluator for expressions like "1. - (4.*6.0)/12.0". */
    static double evalArith(String expr) {
        expr = expr.trim();
        // Delegate to a recursive descent parser for +, -, *, /, ()
        return new ArithEval(expr).parse();
    }

    // ------------------------------------------------------------------
    // cfCoeffs: parse replaceCFRules into coefficient matrix
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static double[][] parseCfCoeffs(
            List<Object> rules, String[] u2Names, int uListLen) {
        // Each rule: "u[i][1][1] -> linear_expression_in_u2Names"
        double[][] coeffs = new double[uListLen][uListLen];
        for (Object rule : rules) {
            // rule is a RuleToken with lhs="u[i][1][1]" and rhs=expression
            RuleToken rt = (RuleToken) rule;
            int uIdx = parseUIndex(rt.lhs);  // extract i from u[i][1][1]
            // Parse rhs as linear combination of u2Names
            parseLinearExpr(rt.rhs, u2Names, coeffs[uIdx]);
        }
        return coeffs;
    }

    /** Extracts the first index from "u[i][1][1]". */
    private static int parseUIndex(String lhs) {
        // Format: u[i][1][1] — extract i (1-based in Mathematica, convert to 0-based)
        int start = lhs.indexOf('[') + 1;
        int end   = lhs.indexOf(']');
        return Integer.parseInt(lhs.substring(start, end).trim()) - 1;
    }

    /**
     * Parses a linear expression like "-16*v2AB1 + 8*v2AB2 + 16*v4AB + xA + xB"
     * and fills the coefficients array.
     */
    private static void parseLinearExpr(String expr, String[] varNames, double[] coeffs) {
        // Tokenise by '+' and '-', keeping sign
        // Handle multi-char variable names and '*' multiplier
        expr = expr.trim();
        // Split on + and - (keeping delimiter)
        List<String> terms = splitTerms(expr);
        for (String term : terms) {
            term = term.trim();
            if (term.isEmpty()) continue;
            double coeff = 1.0;
            String varName = null;

            if (term.contains("*")) {
                String[] parts = term.split("\\*", 2);
                coeff   = Double.parseDouble(parts[0].trim());
                varName = parts[1].trim();
            } else {
                // Just a variable (possibly with leading sign)
                // Check if it starts with a digit or sign+digit
                try {
                    coeff   = Double.parseDouble(term);
                    varName = null;  // pure number — shouldn't happen in valid expr
                } catch (NumberFormatException e) {
                    varName = term;
                }
            }

            if (varName != null) {
                for (int j = 0; j < varNames.length; j++) {
                    if (varName.equals(varNames[j])) {
                        coeffs[j] += coeff;
                        break;
                    }
                }
            }
        }
    }

    /** Splits an expression into signed terms, preserving sign on each term. */
    private static List<String> splitTerms(String expr) {
        List<String> terms = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if ((c == '+' || c == '-') && i > 0 && !isPartOfNumber(expr, i)) {
                terms.add(sb.toString().trim());
                sb = new StringBuilder();
            }
            sb.append(c);
        }
        if (sb.length() > 0) terms.add(sb.toString().trim());
        return terms;
    }

    private static boolean isPartOfNumber(String s, int pos) {
        // A '-' after 'e' or 'E' is part of a scientific notation number
        return pos > 0 && (s.charAt(pos - 1) == 'e' || s.charAt(pos - 1) == 'E');
    }

    // ------------------------------------------------------------------
    // Block 2 extraction helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static double[][] mhFromBlock2(List<Object> b2, int[] lc, int tcdis) {
        // b2.get(5) = mh (cluster multiplicities per cluster per type)
        List<Object> mhRaw = (List<Object>) b2.get(5);
        double[][] mh = new double[tcdis + 1][];
        for (int itc = 0; itc < mhRaw.size() && itc <= tcdis; itc++) {
            Object row = mhRaw.get(itc);
            if (row instanceof List) {
                mh[itc] = doubleArray((List<Object>) row);
            } else {
                mh[itc] = new double[]{doubleOf(row)};
            }
        }
        return mh;
    }

    @SuppressWarnings("unchecked")
    private static int[][] lcvFromBlock2(List<Object> b2, int[] lc, int tcdis) {
        // b2.get(13) = lcv: {{6},{6},{3},{3},{2}} for binary BCC
        List<Object> lcvRaw = (List<Object>) b2.get(13);
        int[][] lcv = new int[tcdis][];
        for (int itc = 0; itc < tcdis; itc++) {
            Object row = lcvRaw.get(itc);
            if (row instanceof List) {
                lcv[itc] = intArray((List<Object>) row);
            } else {
                lcv[itc] = new int[]{intOf(row)};
            }
        }
        return lcv;
    }

    @SuppressWarnings("unchecked")
    private static double[][][] wcvFromBlock2(
            List<Object> b2, int[] lc, int[][] lcv, int tcdis) {
        // b2.get(12) = mcf (same as wcv in calGmcecvm): {{{1,4,4,2,4,1}}, ...}
        List<Object> wcvRaw = (List<Object>) b2.get(12);
        double[][][] wcv = new double[tcdis][][];
        for (int itc = 0; itc < tcdis; itc++) {
            Object typeObj = wcvRaw.get(itc);
            List<Object> typeList = (List<Object>) typeObj;
            wcv[itc] = new double[typeList.size()][];
            for (int inc = 0; inc < typeList.size(); inc++) {
                Object clusterObj = typeList.get(inc);
                if (clusterObj instanceof List) {
                    wcv[itc][inc] = doubleArray((List<Object>) clusterObj);
                } else {
                    wcv[itc][inc] = new double[]{doubleOf(clusterObj)};
                }
            }
        }
        return wcv;
    }

    @SuppressWarnings("unchecked")
    private static double[][][][] cmatFromBlock2(
            List<Object> b2, int[] lc, int[][] lcv, int tcdis, int uListLen) {
        // b2.get(14) = cmat: list of matrices, one per cluster type
        List<Object> cmatRaw = (List<Object>) b2.get(14);
        double[][][][] cmat = new double[tcdis][][][];
        for (int itc = 0; itc < tcdis; itc++) {
            Object typeObj = cmatRaw.get(itc);
            List<Object> typeList = (List<Object>) typeObj;
            cmat[itc] = new double[typeList.size()][][];
            for (int inc = 0; inc < typeList.size(); inc++) {
                Object matObj = typeList.get(inc);
                List<Object> matRows = (List<Object>) matObj;
                cmat[itc][inc] = new double[matRows.size()][];
                for (int row = 0; row < matRows.size(); row++) {
                    Object rowObj = matRows.get(row);
                    if (rowObj instanceof List) {
                        cmat[itc][inc][row] = doubleArray((List<Object>) rowObj);
                    } else {
                        cmat[itc][inc][row] = new double[]{doubleOf(rowObj)};
                    }
                }
            }
        }
        return cmat;
    }

    // ------------------------------------------------------------------
    // Type conversion helpers
    // ------------------------------------------------------------------

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString().trim());
    }

    private static double doubleOf(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        String s = o.toString().trim();
        // Handle Mathematica rational fractions like "1/2"
        if (s.contains("/")) {
            String[] p = s.split("/");
            return Double.parseDouble(p[0].trim()) / Double.parseDouble(p[1].trim());
        }
        return Double.parseDouble(s);
    }

    private static String symbolOf(Object o) {
        return o.toString().trim();
    }

    private static double[] doubleArray(List<Object> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = doubleOf(list.get(i));
        return arr;
    }

    private static int[] intArray(List<Object> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = intOf(list.get(i));
        return arr;
    }

    // ------------------------------------------------------------------
    // Tokenizer: parses Mathematica nested-list syntax
    // ------------------------------------------------------------------

    /** Represents a Mathematica rule "lhs -> rhs". */
    static final class RuleToken {
        final String lhs;
        final String rhs;
        RuleToken(String lhs, String rhs) { this.lhs = lhs; this.rhs = rhs; }
        @Override public String toString() { return lhs + " -> " + rhs; }
    }

    /** Simple tokenizer for Mathematica list/expression syntax. */
    static final class Tokenizer {
        private final String src;
        private int pos;

        Tokenizer(String src) { this.src = src; this.pos = 0; }

        /** Parses a Mathematica list starting with '{' and ending with '}'. */
        List<Object> parseList() {
            skipWs();
            expect('{');
            List<Object> items = new ArrayList<>();
            skipWs();
            if (peek() == '}') { advance(); return items; }

            while (true) {
                items.add(parseValue());
                skipWs();
                if (peek() == '}') { advance(); break; }
                if (peek() == ',') { advance(); skipWs(); }
            }
            return items;
        }

        /** Parses any value: list, number, symbol, or rule. */
        Object parseValue() {
            skipWs();
            if (peek() == '{') return parseList();
            // Parse a raw token (symbol, number, or expression) up to , or }
            // Rules contain '->' so we scan for that
            String token = parseRawToken();
            skipWs();
            if (peek() == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '>') {
                advance(); advance();  // consume '->'
                skipWs();
                String rhs = parseRawToken();
                return new RuleToken(token.trim(), rhs.trim());
            }
            return parseTokenValue(token.trim());
        }

        /**
         * Parses a raw token: anything up to (unbalanced) ',' or '}',
         * respecting balanced '[]', '()', '{}'.
         */
        String parseRawToken() {
            skipWs();
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') {
                    if (depth == 0) break;
                    depth--;
                } else if ((c == ',' || c == '-') && depth == 0) {
                    // Stop at comma; stop at '->' only if next char is '>'
                    if (c == ',') break;
                    if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '>') break;
                }
                sb.append(c);
                pos++;
            }
            return sb.toString().trim();
        }

        /** Converts a raw token string to a typed value. */
        Object parseTokenValue(String token) {
            if (token.isEmpty()) return token;
            // Try integer
            try { return Integer.parseInt(token); } catch (NumberFormatException ignored) {}
            // Try double (including e-notation)
            try { return Double.parseDouble(token); } catch (NumberFormatException ignored) {}
            // Rational fraction
            if (token.matches("-?\\d+/\\d+")) {
                String[] p = token.split("/");
                return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            // Symbol or expression
            return token;
        }

        private char peek() { return pos < src.length() ? src.charAt(pos) : '\0'; }
        private void advance() { pos++; }
        private void skipWs() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }
        private void expect(char c) {
            skipWs();
            if (pos >= src.length() || src.charAt(pos) != c)
                throw new ParseException("Expected '" + c + "' at pos " + pos
                        + " but got '" + (pos < src.length() ? src.charAt(pos) : "EOF") + "'");
            pos++;
        }
    }

    // ------------------------------------------------------------------
    // Simple arithmetic expression evaluator (for kbdis substitution)
    // ------------------------------------------------------------------

    static final class ArithEval {
        private final String src;
        private int pos;
        ArithEval(String src) { this.src = src.replaceAll("\\s+",""); this.pos = 0; }

        double parse() { double v = addSub(); return v; }

        private double addSub() {
            double v = mulDiv();
            while (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                char op = src.charAt(pos++);
                double r = mulDiv();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        private double mulDiv() {
            double v = unary();
            while (pos < src.length() && (src.charAt(pos) == '*' || src.charAt(pos) == '/')) {
                char op = src.charAt(pos++);
                double r = unary();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }

        private double unary() {
            if (pos < src.length() && src.charAt(pos) == '-') { pos++; return -primary(); }
            if (pos < src.length() && src.charAt(pos) == '+') { pos++; return primary(); }
            return primary();
        }

        private double primary() {
            if (pos < src.length() && src.charAt(pos) == '(') {
                pos++;
                double v = addSub();
                if (pos < src.length() && src.charAt(pos) == ')') pos++;
                return v;
            }
            // Parse number
            int start = pos;
            if (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.'))  {
                while (pos < src.length() && (Character.isDigit(src.charAt(pos))
                        || src.charAt(pos) == '.' || src.charAt(pos) == 'e'
                        || src.charAt(pos) == 'E'
                        || ((src.charAt(pos) == '-' || src.charAt(pos) == '+')
                            && pos > 0 && (src.charAt(pos-1)=='e' || src.charAt(pos-1)=='E')))) {
                    pos++;
                }
                return Double.parseDouble(src.substring(start, pos));
            }
            throw new ParseException("Unexpected char at pos " + pos + " in: " + src);
        }
    }

    /** Unchecked exception for parse errors. */
    public static final class ParseException extends RuntimeException {
        public ParseException(String msg) { super(msg); }
    }
}
