package service;

import java.util.List;

/**
 * DTO carrying parsed model metadata for GUI/CLI display.
 */
public class ModelInfo {
    private String filePath;
    private boolean fileExists;
    private long lastModifiedEpochMillis;
    private List<String> detectedElements;
    private List<String> availablePhases;
    private List<String> availableElements;
    private String error;
    public List<String> getAvailableElements() {
        return availableElements;
    }

    public void setAvailableElements(List<String> availableElements) {
        this.availableElements = availableElements;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isFileExists() {
        return fileExists;
    }

    public void setFileExists(boolean fileExists) {
        this.fileExists = fileExists;
    }

    public long getLastModifiedEpochMillis() {
        return lastModifiedEpochMillis;
    }

    public void setLastModifiedEpochMillis(long lastModifiedEpochMillis) {
        this.lastModifiedEpochMillis = lastModifiedEpochMillis;
    }

    public List<String> getDetectedElements() {
        return detectedElements;
    }

    public void setDetectedElements(List<String> detectedElements) {
        this.detectedElements = detectedElements;
    }

    public List<String> getAvailablePhases() {
        return availablePhases;
    }

    public void setAvailablePhases(List<String> availablePhases) {
        this.availablePhases = availablePhases;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
