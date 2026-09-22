package system.model.cvm.gen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;

/**
 * Loads CVM structure/symmetry input files in CEWorkbench's own Mathematica
 * nested-brace text format, from expcvm10's {@code inputs/} directory.
 *
 * <p>Trimmed, adapted port of CEWorkbench's
 * {@code org.ce.model.storage.InputLoader}: same file format and parsing
 * logic (so the copied {@code clus}/{@code sym} files under expcvm10's
 * {@code inputs/} directory -- verbatim copies of CEWorkbench's own,
 * already-verified {@code BCC_A2-T.txt}/{@code BCC_A2-SG.txt} -- parse
 * identically), with the {@code Workspace}/multi-root resolution machinery
 * dropped since expcvm10 has exactly one fixed {@code inputs/} directory
 * at the project root.</p>
 */
public final class StructureFileLoader {

    private StructureFileLoader() {}

    private static final Path INPUTS_DIR = Path.of("inputs");

    /** Parses a cluster file, e.g. {@code "clus/BCC_A2-T.txt"}. */
    public static List<Cluster> parseClusterFile(String relativePath) {
        Path filePath = INPUTS_DIR.resolve(relativePath);
        try {
            String content = Files.readString(filePath);
            return parseClusterContent(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load cluster file: " + filePath, e);
        }
    }

    /** Parses a space-group file, e.g. baseName {@code "BCC_A2-SG"} -> {@code sym/BCC_A2-SG.txt}. */
    public static SpaceGroup parseSpaceGroup(String baseName) {
        Path filePath = INPUTS_DIR.resolve("sym").resolve(baseName + ".txt");
        try {
            String content = Files.readString(filePath);
            return parseSpaceGroupContent(baseName, content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load space-group file: " + filePath, e);
        }
    }

    // =========================================================================
    // Cluster file parsing (Mathematica nested-brace format)
    // =========================================================================

    private static List<Cluster> parseClusterContent(String content) {
        content = content.trim();
        content = content.substring(1, content.length() - 1);

        List<Cluster> clusters = new ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            if (content.charAt(index) == '{') {
                int end = findMatchingBrace(content, index);
                clusters.add(parseSingleCluster(content.substring(index + 1, end)));
                index = end + 1;
            } else {
                index++;
            }
        }
        return clusters;
    }

    private static Cluster parseSingleCluster(String block) {
        List<Sublattice> sublattices = new ArrayList<>();
        int index = 0;
        while (index < block.length()) {
            if (block.charAt(index) == '{') {
                int end = findMatchingBrace(block, index);
                sublattices.add(parseSublattice(block.substring(index + 1, end)));
                index = end + 1;
            } else {
                index++;
            }
        }
        return new Cluster(sublattices);
    }

    private static Sublattice parseSublattice(String block) {
        List<Site> sites = new ArrayList<>();
        int index = 0;
        while (index < block.length()) {
            if (block.charAt(index) == '{') {
                int end = findMatchingBrace(block, index);
                sites.add(parseSite(block.substring(index + 1, end)));
                index = end + 1;
            } else {
                index++;
            }
        }
        return new Sublattice(sites);
    }

    private static Site parseSite(String block) {
        String[] tokens = block.split(",");
        double x = Double.parseDouble(tokens[0].trim());
        double y = Double.parseDouble(tokens[1].trim());
        double z = Double.parseDouble(tokens[2].trim());
        return new Site(new Position(x, y, z), "s1");
    }

    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            if (s.charAt(i) == '}') depth--;
            if (depth == 0) return i;
        }
        throw new RuntimeException("Unbalanced braces in cluster file.");
    }

    // =========================================================================
    // Space-group file parsing
    // =========================================================================

    private static SpaceGroup parseSpaceGroupContent(String baseName, String content) {
        String cleanContent = content.replace("{", "").replace("}", "");
        String[] tokens = cleanContent.split(",");

        List<Double> numbers = new ArrayList<>();
        for (String t : tokens) {
            if (!t.trim().isEmpty()) numbers.add(Double.parseDouble(t.trim()));
        }

        int matrixSize = 12;
        int totalNumbers = numbers.size();
        int numOps = (totalNumbers - matrixSize) / matrixSize;

        List<SymmetryOperation> ops = new ArrayList<>();
        for (int i = 0; i < numOps; i++) {
            double[][] rot = new double[3][3];
            double[] trans = new double[3];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    rot[r][c] = numbers.get(i * matrixSize + r * 4 + c);
                }
                trans[r] = numbers.get(i * matrixSize + r * 4 + 3);
            }
            ops.add(new SymmetryOperation(rot, trans));
        }

        int matStartIndex = numOps * matrixSize;
        double[][] rotateMat = new double[3][3];
        double[] translateMat = new double[3];
        for (int i = 0; i < 9; i++) {
            rotateMat[i / 3][i % 3] = numbers.get(matStartIndex + i);
        }
        for (int i = 0; i < 3; i++) {
            translateMat[i] = numbers.get(matStartIndex + 9 + i);
        }

        return new SpaceGroup(baseName, ops, rotateMat, translateMat);
    }
}
