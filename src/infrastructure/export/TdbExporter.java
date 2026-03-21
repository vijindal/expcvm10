package infrastructure.export;

import domain.port.ResultPort;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Infrastructure adapter for writing results to a file.
 */
public class TdbExporter implements ResultPort {

    private final FileWriter writer;

    public TdbExporter(String filePath) throws IOException {
        this.writer = new FileWriter(filePath);
    }

    @Override
    public void writeLine(String line) throws IOException {
        writer.write(line);
        writer.write(System.lineSeparator());
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
