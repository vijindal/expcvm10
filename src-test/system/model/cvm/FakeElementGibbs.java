package system.model.cvm;

import system.model.unary.ElementGibbs;

import java.util.Set;

/**
 * Minimal {@link ElementGibbs} test double: GHSER(T) = a + b*T, so
 * finite-difference and analytical GHSER-derivative checks have a known
 * closed form (dGHSER/dT = b exactly).
 */
final class FakeElementGibbs implements ElementGibbs {

    private final String symbol;
    private final double a;
    private final double b;

    FakeElementGibbs(String symbol, double a, double b) {
        this.symbol = symbol;
        this.a = a;
        this.b = b;
    }

    @Override public String elementSymbol() { return symbol; }

    @Override
    public double gibbs(String phaseName, double T) {
        return a + b * T;
    }

    @Override
    public double ghser(double T) {
        return a + b * T;
    }

    @Override
    public Set<String> availablePhases() {
        return Set.of("BCC_A2");
    }
}
