package infra;

import domain.DatasetPort;
import util.DataReader;

import java.io.IOException;

/**
 * Infrastructure adapter for reading experimental/phase data files.
 * Delegates counting to the legacy DataReader utility.
 */
public class ExptDataReader implements DatasetPort {

    @Override
    public int getDataPointCount(String filePath) throws IOException {
        return DataReader.getNData(filePath);
    }
}
