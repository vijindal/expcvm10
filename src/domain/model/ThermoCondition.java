package domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable value object representing thermodynamic conditions.
 * Replaces direct dependency on calbince.Condition in domain layer.
 */
public final class ThermoCondition {

    private final double T;
    private final double P;
    private final List<List<Double>> x;
    private final int p; // number of phases
    private final int c; // number of components

    public ThermoCondition(double T, double P, List<List<Double>> x) {
        this.T = T;
        this.P = P;
        // Deep defensive copy for immutability
        List<List<Double>> copy = new ArrayList<>();
        for (List<Double> row : x) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        this.x = Collections.unmodifiableList(copy);
        this.p = x.size();
        this.c = x.isEmpty() ? 0 : x.get(0).size();
    }

    public double getT() {
        return T;
    }

    public double getP() {
        return P;
    }

    public List<List<Double>> getX() {
        return x;
    }

    public int getPhaseCount() {
        return p;
    }

    public int getComponentCount() {
        return c;
    }

    /**
     * Degree of freedom using phase rule: F = C + 2 - P
     */
    public int dof() {
        return c + 2 - p;
    }
}
