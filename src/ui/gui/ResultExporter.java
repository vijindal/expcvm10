package ui.gui;

import ui.result.CalculationResult;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.Date;

/** Writes the last single-point run result to CSV/JSON files for MainFrame's export menu. */
final class ResultExporter {

    private ResultExporter() {}

    static void writeCsv(File file, CalculationResult result) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("Metric,Value,Unit\n");
            fw.write("Success," + result.isSuccess() + ",-\n");
            fw.write(csvEscape("Method") + "," + csvEscape(safe(result.getMethod())) + ",-\n");
            fw.write(csvEscape("Message") + "," + csvEscape(safe(result.getMessage())) + ",-\n");
            fw.write("Value," + result.getValue() + ",J/mol\n");
            fw.write("Temperature," + result.getTemperature() + ",K\n");
            fw.write("Pressure," + result.getPressure() + ",Pa\n");
            fw.write("Composition," + csvEscape(ResultFormatter.formatComposition(result.getCompositionResult())) + ",-\n");
        }
    }

    static void writeJson(File file, CalculationResult result, String requestSummary) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("{\n");
            fw.write("  \"timestamp\": \"" + escapeJson(new Date().toString()) + "\",\n");
            fw.write("  \"requestSummary\": \"" + escapeJson(requestSummary) + "\",\n");
            fw.write("  \"success\": " + result.isSuccess() + ",\n");
            fw.write("  \"method\": \"" + escapeJson(safe(result.getMethod())) + "\",\n");
            fw.write("  \"message\": \"" + escapeJson(safe(result.getMessage())) + "\",\n");
            fw.write("  \"value\": " + result.getValue() + ",\n");
            fw.write("  \"temperature\": " + result.getTemperature() + ",\n");
            fw.write("  \"pressure\": " + result.getPressure() + ",\n");
            fw.write("  \"composition\": \"" + escapeJson(ResultFormatter.formatComposition(result.getCompositionResult())) + "\"\n");
            fw.write("}\n");
        }
    }

    private static String csvEscape(Object val) {
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
