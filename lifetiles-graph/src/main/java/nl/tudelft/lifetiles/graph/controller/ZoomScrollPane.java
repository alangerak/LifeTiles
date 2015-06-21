package nl.tudelft.lifetiles.graph.controller;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;

public class ZoomScrollPane extends ScrollPane {

    /**
     * The initial value for zoomlevel.
     */
    private static final int DEFAULTZOOMLEVEL = 10;

    /**
     * The current zoom level.
     */
    private int zoomLevel = DEFAULTZOOMLEVEL;

    /**
     * The zoom level to switch from diagram to graph and back.
     */
    static final int SWITCHLEVEL = 15;

    /**
     * The current scale for this zoomScrollPane.
     */
    private double scale = 1;

    private GraphController controller;

    /**
     * The factor that each zoom in step that updates the current scale.
     */
    private static final double ZOOM_IN_FACTOR = 2;

    /**
     * The factor that each zoom out step that updates the current scale.
     */
    private static final double ZOOM_OUT_FACTOR = 1 / ZOOM_IN_FACTOR;

    /**
     * Maximal zoom level.
     */
    static final int MAX_ZOOM = 20;

    /**
     * A zommbar for this zoomScrollPane.
     */
    private Zoombar toolbar;

    private BorderPane wrapper;

    public ZoomScrollPane(GraphController controller, BorderPane wrapper) {
        super();
        this.controller = controller;
        this.wrapper = wrapper;

        initZoomToolBar();

        this.setOnScroll(event -> {
            event.consume();
            if (event.getDeltaY() > 0) {
                toolbar.incrementZoom();
            } else {
                toolbar.decrementZoom();
            }
        });
    }

    /**
     * Initialize the zoom toolbar.
     */
    private void initZoomToolBar() {
        toolbar = new Zoombar(zoomLevel, MAX_ZOOM);

        wrapper.setRight(toolbar.getToolBar());

        toolbar.getZoomlevel().addListener((observeVal, oldVal, newVal) -> {
            int diffLevel = oldVal.intValue() - newVal.intValue();
            setZoomLevel(Math.abs(newVal.intValue()));
            if (diffLevel < 0) {
                zoomGraph(Math.pow(ZOOM_OUT_FACTOR, -1 * diffLevel));
            } else if (diffLevel > 0) {
                zoomGraph(Math.pow(ZOOM_IN_FACTOR, diffLevel));
            }
        });
    }

    /**
     * Zoom on the current graph given a zoomFactor.
     *
     * @param zoomFactor
     *            factor bigger than 1 makes the graph bigger
     *            between 0 and 1 makes the graph smaller
     */
    void zoomGraph(final double zoomFactor) {
        setScale(getScale() * zoomFactor);
        controller.repaintNow();
    }

    /**
     * @return the zoomLevel
     */
    public int getZoomLevel() {
        return zoomLevel;
    }

    /**
     * @param zoomLevel
     *            the zoomLevel to set
     */
    public void setZoomLevel(final int zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    /**
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    /**
     * @param scale
     *            the scale to set
     */
    public void setScale(final double scale) {
        this.scale = scale;
    }

}
