package application.calctype;

import java.util.List;

/** Ordered list of {@link ParameterDescriptor}s a {@link CalculationType} needs. */
public record ParameterSpec(List<ParameterDescriptor> descriptors) {
}
