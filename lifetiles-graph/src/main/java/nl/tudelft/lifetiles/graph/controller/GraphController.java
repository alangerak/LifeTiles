package nl.tudelft.lifetiles.graph.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import nl.tudelft.lifetiles.annotation.model.GeneAnnotation;
import nl.tudelft.lifetiles.annotation.model.GeneAnnotationMapper;
import nl.tudelft.lifetiles.annotation.model.GeneAnnotationParser;
import nl.tudelft.lifetiles.annotation.model.KnownMutation;
import nl.tudelft.lifetiles.annotation.model.KnownMutationMapper;
import nl.tudelft.lifetiles.annotation.model.KnownMutationParser;
import nl.tudelft.lifetiles.core.controller.AbstractController;
import nl.tudelft.lifetiles.core.controller.MenuController;
import nl.tudelft.lifetiles.core.util.Message;
import nl.tudelft.lifetiles.core.util.Timer;
import nl.tudelft.lifetiles.graph.model.DefaultGraphParser;
import nl.tudelft.lifetiles.graph.model.FactoryProducer;
import nl.tudelft.lifetiles.graph.model.Graph;
import nl.tudelft.lifetiles.graph.model.GraphContainer;
import nl.tudelft.lifetiles.graph.model.GraphFactory;
import nl.tudelft.lifetiles.graph.model.GraphParser;
import nl.tudelft.lifetiles.graph.model.StackedMutationContainer;
import nl.tudelft.lifetiles.graph.view.DiagramView;
import nl.tudelft.lifetiles.graph.view.TileView;
import nl.tudelft.lifetiles.graph.view.VertexView;
import nl.tudelft.lifetiles.notification.controller.NotificationController;
import nl.tudelft.lifetiles.notification.model.NotificationFactory;
import nl.tudelft.lifetiles.sequence.controller.SequenceController;
import nl.tudelft.lifetiles.sequence.model.SegmentStringCollapsed;
import nl.tudelft.lifetiles.sequence.model.Sequence;
import nl.tudelft.lifetiles.sequence.model.SequenceSegment;

/**
 * The controller of the graph view.
 *
 * @author Joren Hammudoglu
 * @author AC Langerak
 * @author Jos Winter
 * @author Albert Smit
 *
 */
public class GraphController extends AbstractController {

    /**
     * The message to display when operations are attempted without a graph
     * being loaded.
     */
    private static final String NOT_LOADED_MSG = "Graph not loaded"
            + " while attempting to add known mutations.";

    /**
     * The pane that will be used to draw the scrollpane and toolbar on the
     * screen.
     */
    @FXML
    private BorderPane wrapper;

    /**
     * The scrollPane element.
     */
    @FXML
    private ZoomScrollPane scrollPane;

    /**
     * The model of the graph.
     */
    private GraphContainer model;

    /**
     * The view of the graph.
     */
    private TileView view;

    /**
     * The model of the diagram.
     */
    private StackedMutationContainer diagram;

    /**
     * The view of the diagram.
     */
    private DiagramView diagramView;

    /**
     * graph model.
     */
    private Graph<SequenceSegment> graph;

    /**
     * the highest unified coordinate in the graph.
     */
    private long maxUnifiedEnd;

    /**
     * Current start position of a bucket.
     */
    private int currStartPosition = -1;

    /**
     * Current end position of a bucket.
     */
    private int currEndPosition = -1;

    /**
     * boolean to indicate if the controller must repaint the current position.
     */
    private boolean repaintNow;

    /**
     * The currently inserted known mutations.
     */
    private Map<SequenceSegment, List<KnownMutation>> knownMutations;

    /**
     * The currently inserted annotations.
     */
    private Map<SequenceSegment, List<GeneAnnotation>> mappedAnnotations;

    /**
     * Visible sequences in the graph.
     */
    private Set<Sequence> visibleSequences;

    /**
     * The current reference in the graph, shouted by the sequence control.
     */
    private Sequence reference;

    /**
     * The mini map controller.
     */
    private MiniMapController miniMapController;

    /**
     * Notification factory used to produce notifications in the graph
     * controller.
     */
    private NotificationFactory notifyFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        initListeners();
        repaintNow = false;
        scrollPane = new ZoomScrollPane(this, wrapper);
    }

    /**
     * @return the mini map controller
     */
    private MiniMapController getMiniMapController() {
        if (miniMapController == null) {
            miniMapController = new MiniMapController(scrollPane, model);
        }
        return miniMapController;
    }

    /**
     * Initialize the listeners.
     */
    @SuppressWarnings("checkstyle:genericwhitespace")
    private void initListeners() {

        notifyFactory = new NotificationFactory();
        listen(Message.OPENED, (controller, subject, args) -> {
            assert controller instanceof MenuController;
            switch (subject) {
            case "graph":
                openGraph(args);
                maxUnifiedEnd = getMaxUnifiedEnd(graph);
                break;
            case "known mutations":
                openKnownMutations(args);
                break;
            case "annotations":
                openAnnotations(args);
                break;
            default:
                return;
            }
            maxUnifiedEnd = getMaxUnifiedEnd(graph);
        });

        listen(Message.FILTERED, (controller, subject, args) -> {
            assert args.length == 1;
            assert args[0] instanceof Set<?>;
            // unfortunately java doesn't really let us typecheck generics :(
                @SuppressWarnings("unchecked")
                Set<Sequence> newSequences = (Set<Sequence>) args[0];
                visibleSequences = newSequences;
                model.setVisible(visibleSequences);
                diagram = new StackedMutationContainer(model.getBucketCache(),
                        visibleSequences);
                repaintNow();
            });

        listen(SequenceController.REFERENCE_SET,
                (controller, subject, args) -> {
                    assert args.length == 1;
                    assert args[0] instanceof Sequence;
                    reference = (Sequence) args[0];
                    model = new GraphContainer(graph, reference);
                    model.setVisible(visibleSequences);
                    diagram = new StackedMutationContainer(model
                            .getBucketCache(), visibleSequences);
                    repaintNow = true;
                    repaint();
                });

        listen(Message.GOTO,
                (controller, subject, args) -> {
                    assert args[0] instanceof Long;
                    Long position = (Long) args[0];
                    // calculate position on a 0 to 1 scale
                    double hValue = position.doubleValue()
                            / (double) maxUnifiedEnd;
                    scrollPane.setHvalue(hValue);
                });
    }

    /**
     * Function called if a graph file is opened.
     * Loads the graph into the graph controller.
     *
     * @param args
     *            The arguments passed by the opened listener.
     */
    private void openGraph(final Object... args) {
        assert args.length == 2;
        assert args[0] instanceof File && args[1] instanceof File;

        try {
            loadGraph((File) args[0], (File) args[1]);
        } catch (IOException exception) {
            shout(NotificationController.NOTIFY, "", notifyFactory
                    .getNotification(exception));
        }
    }

    /**
     * Function called if a known mutations file is opened.
     * Loads and inserts the known mutations into the graph controller.
     *
     * @param args
     *            The arguments passed by the opened listener.
     */
    private void openKnownMutations(final Object... args) {
        assert args[0] instanceof File;

        if (graph == null) {
            shout(NotificationController.NOTIFY, "", notifyFactory
                    .getNotification(new IllegalStateException(NOT_LOADED_MSG)));
        } else {
            try {
                insertKnownMutations((File) args[0]);
            } catch (IOException exception) {
                shout(NotificationController.NOTIFY, "", notifyFactory
                        .getNotification(exception));
            }
        }

    }

    /**
     * Function called if a annotations file is opened.
     * Loads and inserts the annotations into the graph controller.
     *
     * @param args
     *            The arguments passed by the opened listener.
     */
    private void openAnnotations(final Object... args) {
        assert args[0] instanceof File;

        if (graph == null) {
            shout(NotificationController.NOTIFY,
                    "",
                    notifyFactory
                            .getNotification(new IllegalStateException(
                                    "Graph not loaded while attempting to add annotations.")));
        } else {
            try {
                insertAnnotations((File) args[0]);
            } catch (IOException exception) {
                shout(NotificationController.NOTIFY, "", notifyFactory
                        .getNotification(exception));
            }
        }
    }

    /**
     * Load a new graph from the specified file.
     *
     *
     * @param vertexfile
     *            The file to get vertices for.
     * @param edgefile
     *            The file to get edges for.
     * @throws IOException
     *             When an IO error occurs while reading one of the files.
     */
    private void loadGraph(final File vertexfile, final File edgefile)
            throws IOException {
        // create the graph
        GraphFactory<SequenceSegment> factory = FactoryProducer.getFactory();
        GraphParser parser = new DefaultGraphParser();
        graph = parser.parseGraph(vertexfile, edgefile, factory);

        collapseGraph(graph, parser.getSequences().size());
        knownMutations = new HashMap<>();
        mappedAnnotations = new HashMap<>();

        model = new GraphContainer(graph, reference);
        diagram = new StackedMutationContainer(model.getBucketCache(),
                visibleSequences);

        shout(Message.LOADED, "sequences", parser.getSequences());
        repaintNow = true;
        repaint();
    }

    /**
     * Collapses the total segments in the graph.
     * Total segments contain all sequences in the graph.
     *
     * @param graph
     *            The graph to be collapsed.
     * @param sequences
     *            The amount of sequences in the graph.
     */
    private void collapseGraph(final Graph<SequenceSegment> graph,
            final int sequences) {
        for (SequenceSegment segment : graph.getAllVertices()) {
            if (segment.getSources().size() == sequences) {
                segment.setContent(new SegmentStringCollapsed(segment
                        .getContent()));
            }
        }
    }

    /**
     * Inserts a list of known mutations onto the graph from the specified file.
     *
     * @param file
     *            The file to get known mutations from.
     * @throws IOException
     *             When an IO error occurs while reading one of the files.
     */
    private void insertKnownMutations(final File file) throws IOException {
        Timer timer = Timer.getAndStart();
        List<KnownMutation> mutationsList = KnownMutationParser
                .parseKnownMutations(file);
        knownMutations = KnownMutationMapper.mapAnnotations(graph,
                mutationsList, reference);

        timer.stopAndLog("Inserting known mutations");
        shout(Message.LOADED, "known mutations", mutationsList);
        repaintNow = true;

        repaintPosition(scrollPane.hvalueProperty().doubleValue());
    }

    /**
     * Inserts a list of annotations onto the graph from the specified file.
     *
     * @param file
     *            The file to get annotations from.
     * @throws IOException
     *             When an IO error occurs while reading one of the files.
     */
    private void insertAnnotations(final File file) throws IOException {
        Timer timer = Timer.getAndStart();
        List<GeneAnnotation> annotations = GeneAnnotationParser
                .parseGeneAnnotations(file);
        shout(Message.LOADED, "annotations", annotations);
        mappedAnnotations = GeneAnnotationMapper.mapAnnotations(graph,
                annotations, reference);

        timer.stopAndLog("Inserting annotations");
        repaintNow = true;
        repaintPosition(scrollPane.hvalueProperty().doubleValue());
    }

    /**
     * Repaints the view.
     */
    void repaint() {
        wrapper.snapshot(new SnapshotParameters(), new WritableImage(5, 5));
        if (graph != null) {

            if (diagram == null) {
                diagram = new StackedMutationContainer(model.getBucketCache(),
                        visibleSequences);
            }

            view = new TileView(this,
                    wrapper.getBoundsInParent().getHeight() * 0.9);
            diagramView = new DiagramView();

            scrollPane.hvalueProperty().addListener(
                    (observable, oldValue, newValue) -> {
                        repaintPosition(newValue.doubleValue());
                    });

            repaintPosition(scrollPane.hvalueProperty().doubleValue());
        }
        getMiniMapController().drawMiniMap();
    }

    /**
     * Find the start and end bucket on the screenm given the position of the
     * scrollbar.
     *
     * @param position
     *            horizontal position of the scrollbar
     * @return an array where the first element is the start bucket and the last
     *         one is the end bucket
     */
    private int[] getStartandEndBucket(final double position) {
        if (miniMapController == null) {
            miniMapController = getMiniMapController();
        }
        double thumbSize = miniMapController.getMiniMap().getVisibleAmount();

        double leftHalf = position - thumbSize;
        double rightHalf = position + thumbSize;

        if (leftHalf < 0) {
            leftHalf /= 2;
        }
        if (rightHalf > scrollPane.getHmax()) {
            rightHalf /= 2;
        }

        int[] buckets = new int[] {
                getStartBucketPosition(leftHalf),
                Math.min(model.getBucketCache().getNumberBuckets(),
                        getEndBucketPosition(rightHalf) + 2)
        };

        return buckets;

    }

    /**
     * Return the start position in the bucket.
     *
     * @param position
     *            Position in the scrollPane.
     * @return position in the bucket.
     */
    private int getStartBucketPosition(final double position) {
        return Math.max(0, model.getBucketCache().getBucketPosition(position));
    }

    /**
     * Return the end position in the bucket.
     *
     * @param position
     *            Position in the scrollPane.
     * @return position in the bucket.
     */
    private int getEndBucketPosition(final double position) {
        return Math.min(model.getBucketCache().getNumberBuckets(), model
                .getBucketCache().getBucketPosition(position));
    }

    /**
     * Repaints the view indicated by the bucket in the given position.
     *
     * @param position
     *            Position in the scrollPane.
     */
    private void repaintPosition(final double position) {
        // int zoomSwitchLevel = scrollPane.MAX_ZOOM - diagram.getLevel();
        if (scrollPane.getZoomLevel() > scrollPane.SWITCHLEVEL) {
            drawDiagram(scrollPane.getZoomLevel());
        } else {
            drawGraph(position);
        }
    }

    /**
     * Draw a Diagram with the given zoom level.
     *
     * @param zoomLevel
     *            zoom level
     */
    private void drawDiagram(final int zoomLevel) {

        double width = maxUnifiedEnd * scrollPane.getScale()
                * VertexView.HORIZONTALSCALE;
        int diagramLevel = scrollPane.getZoomLevel() - zoomLevel;

        Group diagramDrawing = new Group();
        diagramDrawing.getChildren().add(
                diagramView.drawDiagram(diagram, diagramLevel, width));
        diagramDrawing.getChildren().add(new Rectangle(width, 0));
        scrollPane.setContent(diagramDrawing);
        wrapper.setCenter(scrollPane);

        repaintNow = false;
    }

    /**
     * Draw a graph at the given scrollbar position.
     *
     * @param position
     *            position of the scrollbar
     */
    private void drawGraph(final double position) {
        int[] bucketLocations = getStartandEndBucket(position);

        int startBucket = bucketLocations[0];
        int endBucket = bucketLocations[1];

        if (currEndPosition != endBucket && currStartPosition != startBucket
                || repaintNow) {
            Group graphDrawing = new Group();
            graphDrawing.setManaged(false);

            graphDrawing.getChildren().add(
                    view.drawGraph(model.getVisibleSegments(startBucket,
                            endBucket), knownMutations, mappedAnnotations,
                            scrollPane.getScale()));

            graphDrawing.getChildren().add(
                    new Rectangle(maxUnifiedEnd * scrollPane.getScale()
                            * VertexView.HORIZONTALSCALE, 0));

            scrollPane.setContent(graphDrawing);
            wrapper.setCenter(scrollPane);

            currEndPosition = endBucket;
            currStartPosition = startBucket;

            repaintNow = false;
        }
    }

    /**
     * Get the maximal unified end position based on the sinks of the graph.
     *
     * @param graph
     *            Graph for which the width must be calculated.
     * @return the maximal unified end position.
     */
    private long getMaxUnifiedEnd(final Graph<SequenceSegment> graph) {
        long max = 0;
        for (SequenceSegment vertex : graph.getSinks()) {
            if (max < vertex.getUnifiedEnd()) {
                max = vertex.getUnifiedEnd();
            }
        }
        return max;
    }

    /**
     * Set that this segment is selected and set those sequences visible.
     *
     * @param segment
     *            The selected segment
     */
    public void clicked(final SequenceSegment segment) {
        shout(Message.FILTERED, "", segment.getSources());
    }

    /**
     * Repaint the current screen immidiately.
     */
    public void repaintNow() {
        this.repaintNow = true;
        repaint();
    }

}
