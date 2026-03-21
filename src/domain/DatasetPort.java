package domain;

import java.io.IOException;

/**
 * Port interface for loading experimental and phase datasets.
 * Infrastructure layer provides the concrete implementation.
 */
public interface DatasetPort {

    /**
     * Count the number of data points in the given file.
     */
    int getDataPointCount(String filePath) throws IOException;
}
