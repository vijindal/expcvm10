package system.model.cvm.gen.structure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.SpaceGroup;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;
import system.model.cvm.gen.StructureFileLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A (structure definition) + symmetry/orbit generation groundwork:
 * verifies {@link SpaceGroupGenerator}, generating the BCC_A2 space group
 * from {@link StructureDefinition#bccA2()} lattice geometry alone,
 * reproduces the operation set of the previously verified
 * {@code inputs/sym/BCC_A2-SG.txt} file exactly (same 96 (rotation,
 * translation) pairs, order-independent).
 */
@Tag("exploratory")
class SpaceGroupGeneratorBccA2Test {

    @Test
    void bccA2StructureIsIndependentOfThermodynamics() {
        StructureDefinition bccA2 = StructureDefinition.bccA2();
        assertEquals("BCC_A2", bccA2.getName());
        assertEquals(1, bccA2.getBasisFractional().size());
        assertEquals(LatticeSystem.CUBIC_BODY_CENTERED, bccA2.getLatticeSystem());
        assertArrayEquals(new double[] { 0, 0, 0 }, bccA2.getBasisFractional().get(0), 1e-12);
    }

    @Test
    void cubicPointGroupHas48Operations() {
        assertEquals(48, SpaceGroupGenerator.cubicPointGroup().size());
    }

    @Test
    void generatedSpaceGroupMatchesFileOrderAndTranslations() {
        SpaceGroup generated = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        assertEquals(96, generated.order(), "48 point-group rotations x 2 BCC centering translations");

        // Identity/zero rotateMat/translateMat: BCC_A2 is its own parent.
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, generated.getTranslateMat()[i], 1e-12);
            for (int j = 0; j < 3; j++) {
                assertEquals(i == j ? 1.0 : 0.0, generated.getRotateMat()[i][j], 1e-12);
            }
        }
    }

    @Test
    void generatedOperationSetMatchesFileExactly() {
        SpaceGroup generated = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        SpaceGroup fromFile = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");

        assertEquals(fromFile.order(), generated.order());

        List<SymmetryOperation> remaining = new ArrayList<>(fromFile.getOperations());
        for (SymmetryOperation genOp : generated.getOperations()) {
            SymmetryOperation match = null;
            for (SymmetryOperation fileOp : remaining) {
                if (sameOperation(genOp, fileOp)) { match = fileOp; break; }
            }
            assertNotNull(match, "Generated operation not found in file-based space group: " + genOp);
            remaining.remove(match);
        }
        assertTrue(remaining.isEmpty(), "File-based space group has operations the generator did not produce: " + remaining);
    }

    private static boolean sameOperation(SymmetryOperation a, SymmetryOperation b) {
        for (int i = 0; i < 3; i++) {
            if (Math.abs(a.getTranslation()[i] - b.getTranslation()[i]) > 1e-9) return false;
            for (int j = 0; j < 3; j++) {
                if (Math.abs(a.getRotation()[i][j] - b.getRotation()[i][j]) > 1e-9) return false;
            }
        }
        return true;
    }
}
