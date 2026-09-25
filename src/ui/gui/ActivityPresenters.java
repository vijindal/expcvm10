package ui.gui;

import calc.diagram.PhaseDiagramResult;
import system.ports.EquilibriumResult;
import ui.request.PhaseDiagramRequest;
import ui.request.PropertyScanRequest;
import ui.result.CoarseDiagramResult;
import ui.result.PropertyScanResult;

import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

/** Owns the per-activity SwingWorker lifecycle, calling {@link MainController} and reporting back to {@link MainFrame}. */
final class ActivityPresenters {

    private final MainController controller;
    private final MainFrame frame;

    private SwingWorker<?, ?> stepWorker;
    private SwingWorker<?, ?> mapWorker;

    ActivityPresenters(MainController controller, MainFrame frame) {
        this.controller = controller;
        this.frame = frame;
    }

    void runSinglePoint(String tdbPath, String[] elements, String method, String[] phases,
                         double t, double p, ArrayList<ArrayList<Double>> compositions, long t0) {
        new SwingWorker<EquilibriumResult, Void>() {
            @Override protected EquilibriumResult doInBackground() {
                return controller.runSinglePoint(tdbPath, elements, method, phases, t, p, compositions);
            }
            @Override protected void done() {
                long elapsed = System.currentTimeMillis() - t0;
                try {
                    frame.onSinglePointDone(get(), elapsed);
                } catch (Exception ex) {
                    frame.onSinglePointFailed(ex);
                }
            }
        }.execute();
    }

    void runStep(PropertyScanRequest req, PropertyCalcConfigPanel panel) {
        stepWorker = new SwingWorker<PropertyScanResult, String>() {
            @Override protected PropertyScanResult doInBackground() {
                req.setProgressCallback(this::publish);
                return controller.runPropertyScan(req);
            }
            @Override protected void process(java.util.List<String> chunks) {
                for (String s : chunks) frame.onStepProgress(s);
            }
            @Override protected void done() {
                stepWorker = null;
                try {
                    frame.onStepDone(get());
                } catch (CancellationException ex) {
                    frame.onStepAborted();
                } catch (Exception ex) {
                    frame.onStepFailed(ex);
                }
            }
        };
        panel.setAbortCallback(() -> { if (stepWorker != null) stepWorker.cancel(true); });
        stepWorker.execute();
    }

    void runMap(PropertyScanRequest req, PropertyCalcConfigPanel panel) {
        mapWorker = new SwingWorker<PhaseDiagramResult, Void>() {
            @Override protected PhaseDiagramResult doInBackground() {
                return controller.runMap(req);
            }
            @Override protected void done() {
                mapWorker = null;
                try {
                    frame.onMapDone(get());
                } catch (CancellationException ex) {
                    frame.onMapAborted();
                } catch (Exception ex) {
                    frame.onMapFailed(ex);
                }
            }
        };
        panel.setAbortCallback(() -> { if (mapWorker != null) mapWorker.cancel(true); });
        mapWorker.execute();
    }

    void runPhaseDiagram(PhaseDiagramRequest req, PhaseDiagramConfigPanel panel) {
        SwingWorker<PhaseDiagramResult, Void> worker = new SwingWorker<PhaseDiagramResult, Void>() {
            @Override protected PhaseDiagramResult doInBackground() {
                return controller.runPhaseDiagram(req);
            }
            @Override protected void done() {
                try {
                    frame.onPhaseDiagramDone(get());
                } catch (CancellationException ex) {
                    frame.onPhaseDiagramAborted();
                } catch (Exception e) {
                    frame.onPhaseDiagramFailed(e);
                }
            }
        };
        panel.setAbortCallback(() -> worker.cancel(true));
        worker.execute();
    }

    void runCoarseDiagram(PhaseDiagramRequest req, PhaseDiagramConfigPanel panel) {
        SwingWorker<CoarseDiagramResult, String> worker = new SwingWorker<CoarseDiagramResult, String>() {
            @Override protected CoarseDiagramResult doInBackground() {
                req.setProgressCallback(this::publish);
                return controller.runCoarseDiagram(req);
            }
            @Override protected void process(java.util.List<String> chunks) {
                for (String s : chunks) frame.onCoarseProgress(s);
            }
            @Override protected void done() {
                try {
                    frame.onCoarseDone(get());
                } catch (CancellationException ex) {
                    frame.onCoarseAborted();
                } catch (Exception e) {
                    frame.onCoarseFailed(e);
                }
            }
        };
        panel.setAbortCallback(() -> worker.cancel(true));
        worker.execute();
    }
}
