package system.model.cvm.gen;

import org.junit.jupiter.api.Test;

/**
 * Throwaway-turned-permanent smoke test: exercises the full generation
 * pipeline end to end and prints its dimensions, so a broken pipeline fails
 * loudly and immediately rather than only inside the more detailed
 * equivalence tests.
 */
class GeneratedCvmGeometrySmokeTest {

    @Test
    void buildsAndValidates() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2Binary(System.out::println);
        System.out.println(geo);
        geo.validate();
    }
}
