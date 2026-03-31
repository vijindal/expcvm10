package ui.result;

/**
 * DTO carrying the results of a thermodynamic calculation.
 */
public class CalculationResult {

    private String method;
    private double value;
    private double[] compositionResult;
    private double temperature;
    private double pressure;
    private String message;
    private boolean success;

    public CalculationResult() {
        this.success = true;
    }

    // --- Getters / Setters ---

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public double[] getCompositionResult() { return compositionResult; }
    public void setCompositionResult(double[] compositionResult) { this.compositionResult = compositionResult; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getPressure() { return pressure; }
    public void setPressure(double pressure) { this.pressure = pressure; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
