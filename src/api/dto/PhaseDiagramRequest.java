package api.dto;

import java.util.List;

/** Request body for {@code POST /sessions/{id}/calculations/phase-diagram}. */
public final class PhaseDiagramRequest {

    public static final class AxisSpec {
        /** "TEMPERATURE", "PRESSURE", or "COMPOSITION". */
        public String type;
        /** Only used when type == "COMPOSITION": 0-based index into the elements list. */
        public Integer componentIndex;
        public String name;
        public double min;
        public double max;
        public double step;
    }

    public List<AxisSpec> axes;
    public double[] startAxes;
    public double fixedT;
    public double fixedP;
    public double[] composition;
}
