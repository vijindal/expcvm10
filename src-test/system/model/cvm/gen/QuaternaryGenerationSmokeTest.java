package system.model.cvm.gen;

import org.junit.jupiter.api.Test;

/** Smoke test: the K=4 BCC_A2 CVCF basis builds and validates. */
class QuaternaryGenerationSmokeTest {

    @Test
    void buildsAndValidatesQuaternary() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2("Nb-Ti-V-Zr", 4, System.out::println);
        System.out.println(geo);
        geo.validate();
        System.out.println("ncf=" + geo.ncf + " tcf=" + geo.tcf);
        System.out.println("cfNames=" + geo.basis.cfNames);
    }
}
