package system.model.cvm.gen;

/**
 * Thrown when a precondition of the cluster/CVCF/C-matrix generation
 * pipeline is not met (missing/malformed inputs, degenerate intermediate
 * results).
 *
 * <p>Ported from CEWorkbench's
 * {@code org.ce.model.cluster.ClusterIdentificationException}, renamed to
 * match expcvm10 conventions. Distinguishes expected, diagnosable input
 * problems from programming bugs so callers get a clear message instead of
 * a raw stack trace.</p>
 */
public class ClusterGenerationException extends RuntimeException {

    public ClusterGenerationException(String stage, String message) {
        super("[" + stage + "] " + message);
    }

    public ClusterGenerationException(String stage, String message, Throwable cause) {
        super("[" + stage + "] " + message, cause);
    }
}
